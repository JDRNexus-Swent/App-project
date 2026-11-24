// Co-authored-by: Claude Sonnet 5.5
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.model.personalSpace

import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

class WorkspaceRepositoryFirestore(private val firestore: FirebaseFirestore) : WorkspaceRepository {
  companion object {
    private const val MAX_BATCH_WRITES = 500
    internal const val COLLECTION_PATH = "documents"
    private const val FIELD_PLACEMENTS = "placements"
    private const val FIELD_SHARED_GROUPS_IDS = "sharedGroupsIds"
    private const val MAX_LATE_PASSES = 3
    private const val TAG = "WorkspaceRepository"

    /**
     * How long a personal-space write waits for the server's answer before it returns. Online the
     * answer comes well within this time, so errors such as NOT_FOUND are still returned. Offline
     * it never comes: the write is already in the local cache, and the call returns success.
     */
    internal const val SERVER_ACK_GRACE_MS = 500L

    /** How long a read waits for the server before it falls back to the local cache. */
    internal const val SERVER_READ_TIMEOUT_MS = 2_000L

    /** How long [hasPendingWrites] waits for the pending writes to sync before it answers. */
    internal const val PENDING_WRITES_CHECK_MS = 500L
  }

  private val collection = firestore.collection(COLLECTION_PATH)

  override fun getNewUid(): String {
    // Calling document() without arguments generates a new reference with a unique ID
    return collection.document().id
  }

  override suspend fun createFile(file: JDRFile): Result<Unit> = safeFirestoreRequest {
    awaitLocalWrite(collection.document(file.id).set(file), "createFile(${file.id})")
  }

  override suspend fun updateFile(edit: FileEdit): Result<Unit> = safeFirestoreRequest {
    // Offline, an update of a document missing from the cache is only rejected (NOT_FOUND) when it
    // syncs; that rejection is logged by awaitLocalWrite.
    awaitLocalWrite(collection.document(edit.id).update(edit.toMap()), "updateFile(${edit.id})")
  }

  override suspend fun deleteFile(fileId: String): Result<Unit> = safeFirestoreRequest {
    val file = getFile(fileId).getOrThrow() ?: throw IllegalArgumentException("Item not found.")
    check(file.type != DocumentType.GROUP_ROOT) { "A group root cannot be deleted." }

    // A file has no content below it: nothing to unshare or reattach.
    if (!file.isContainer()) {
      awaitLocalWrite(collection.document(fileId).delete(), "deleteFile($fileId)")
      return@safeFirestoreRequest
    }

    // The folder and its personal content are deleted, and leave every group they are in.
    // TODO(offline): offline, the listings below come from the local cache only. Content that was
    //  never loaded on this device is missing from them, so it is neither deleted nor reattached.
    val deleted = collectPersonalSubtree(file)
    val deletedIds = deleted.map { it.id }.toSet()
    val removals =
        planDeletion(
            deleted,
            isParentHidden = { folderId, groupId -> isHiddenIn(folderId, groupId) },
            childrenOf = { folder, groupId -> getGroupFolderFiles(folder.id, groupId).first() },
        )

    // Files that are deleted anyway do not need an update.
    val writes =
        deleted.size +
            removals.values.sumOf { removal ->
              removal.leaving.count { it.id !in deletedIds } + removal.reattached.size
            }
    check(writes <= MAX_BATCH_WRITES) {
      "Too many items to delete at once (maximum $MAX_BATCH_WRITES)."
    }

    val batch = firestore.batch()
    deleted.forEach { batch.delete(collection.document(it.id)) }
    removals.forEach { (groupId, removal) ->
      removal.leaving
          .filter { it.id !in deletedIds }
          .forEach { batch.update(collection.document(it.id), removePlacementUpdate(groupId)) }
      removal.reattached.forEach {
        batch.update(
            collection.document(it.id),
            addPlacementUpdate(groupId, it.folderId, it.isHidden),
        )
      }
    }
    awaitLocalWrite(batch.commit(), "deleteFile($fileId)")
  }

  override suspend fun getFile(fileId: String): Result<JDRFile?> = safeFirestoreRequest {
    val documentSnapshot = getWithCacheFallback(collection.document(fileId))

    if (!documentSnapshot.exists()) {
      throw Exception("Document not found in Firestore")
    }

    documentSnapshot.toObject(JDRFile::class.java)
  }

  override suspend fun removeItemFromGroup(
      itemId: String,
      groupId: String,
  ): Result<Unit> = safeFirestoreRequest {
    require(itemId.isNotBlank()) { "Item ID cannot be empty." }
    require(groupId.isNotBlank()) { "Group ID cannot be empty." }

    val root = getFile(itemId).getOrThrow() ?: throw IllegalArgumentException("Item not found.")
    check(root.type != DocumentType.GROUP_ROOT) { "A group root cannot be removed from its group." }
    val placement =
        checkNotNull(root.placements[groupId]) { "This item does not belong to the group." }

    val parentHidden = isHiddenIn(placement.folderId, groupId)

    val removal =
        planRemoval(root, groupId, parentHidden) { getGroupFolderFiles(it.id, groupId).first() }
    check(removal.leaving.size + removal.reattached.size <= MAX_BATCH_WRITES) {
      "Too many items to update at once (maximum $MAX_BATCH_WRITES)."
    }

    val batch = firestore.batch()
    removal.leaving.forEach {
      batch.update(collection.document(it.id), removePlacementUpdate(groupId))
    }
    removal.reattached.forEach {
      batch.update(
          collection.document(it.id),
          addPlacementUpdate(groupId, it.folderId, it.isHidden),
      )
    }
    batch.commit().await()
  }

  override suspend fun addItemToGroup(
      itemId: String,
      groupId: String,
      folderId: String,
      isHidden: Boolean,
      includeContent: Boolean,
  ): Result<Unit> = safeFirestoreRequest {
    require(itemId.isNotBlank()) { "Item ID cannot be empty." }
    require(groupId.isNotBlank()) { "Group ID cannot be empty." }
    require(folderId.isNotBlank()) { "Folder ID cannot be empty." }

    val item = getFile(itemId).getOrThrow() ?: throw IllegalArgumentException("Item not found.")
    check(item.type != DocumentType.GROUP_ROOT) { "A group root cannot be added to a group." }

    // Pre-calculate the personal subtree. This doesn't depend on group visibility.
    val subtree =
        if (includeContent && item.isContainer()) collectPersonalSubtree(item) else emptyList()

    // TODO(offline): transactions fail without the network. Out of the personal-space scope.
    firestore
        .runTransaction { transaction ->
          val currentItem =
              transaction.get(collection.document(itemId)).toObject(JDRFile::class.java)
                  ?: throw IllegalArgumentException("Item not found.")
          check(groupId !in currentItem.sharedGroupsIds) { "This item is already in the group." }
          val destinationSnapshot = transaction.get(collection.document(folderId))
          val destination =
              destinationSnapshot.toObject(JDRFile::class.java)
                  ?: throw IllegalArgumentException("Destination folder not found.")

          check(destination.isContainer()) { "The destination is not a folder." }
          check(groupId in destination.sharedGroupsIds) {
            "The destination folder does not belong to the group."
          }

          // Something added inside a hidden folder is hidden too. The group root has no placement.
          val destinationHidden = destination.placements[groupId]?.isHidden == true

          val additions =
              if (includeContent && item.isContainer()) {
                planAddition(
                    subtree = subtree,
                    groupId = groupId,
                    destinationFolderId = folderId,
                    hideNew = isHidden,
                    destinationHidden = destinationHidden,
                )
              } else {
                listOf(GroupPlacement(itemId, folderId, isHidden || destinationHidden))
              }
          check(additions.size <= MAX_BATCH_WRITES) {
            "Too many items to add at once (maximum $MAX_BATCH_WRITES)."
          }

          additions.forEach {
            transaction.update(
                collection.document(it.id),
                addPlacementUpdate(groupId, it.folderId, it.isHidden),
            )
          }
        }
        .await()
  }

  override suspend fun setItemHidden(
      itemId: String,
      groupId: String,
      isHidden: Boolean,
  ): Result<Unit> = safeFirestoreRequest {
    require(itemId.isNotBlank()) { "Item ID cannot be empty." }
    require(groupId.isNotBlank()) { "Group ID cannot be empty." }

    val item = getFile(itemId).getOrThrow() ?: throw IllegalArgumentException("Item not found.")
    check(item.type != DocumentType.GROUP_ROOT) { "A group root cannot be hidden." }
    // Pre-calculate the group subtree if we're hiding, because it needs queries
    val subtree = if (isHidden) collectGroupSubtree(item, groupId) else emptyList()

    // TODO(offline): transactions fail without the network. Out of the personal-space scope.
    firestore
        .runTransaction { transaction ->
          val currentItemSnapshot = transaction.get(collection.document(itemId))
          val currentItem =
              currentItemSnapshot.toObject(JDRFile::class.java)
                  ?: throw IllegalArgumentException("Item not found.")

          val currentPlacement =
              checkNotNull(currentItem.placements[groupId]) {
                "This item does not belong to the group."
              }

          if (currentPlacement.isHidden == isHidden) return@runTransaction

          val itemsToUpdate =
              if (isHidden) {
                // Hiding a folder hides everything inside it, otherwise a player could see files
                // whose folder is hidden.
                subtree.filter { it.placements[groupId]?.isHidden != true }
              } else {
                // Revealing only reveals this item, and only if the folder holding it is visible.
                if (currentPlacement.folderId != groupId) {
                  val parentSnapshot =
                      transaction.get(collection.document(currentPlacement.folderId))
                  val parent =
                      parentSnapshot.toObject(JDRFile::class.java)
                          ?: throw IllegalArgumentException("Placement not found.")
                  check(parent.placements[groupId]?.isHidden != true) {
                    "Reveal the parent folder first."
                  }
                }
                listOf(currentItem)
              }

          check(itemsToUpdate.size <= MAX_BATCH_WRITES) {
            "Too many items to update at once (maximum $MAX_BATCH_WRITES)."
          }

          itemsToUpdate.forEach {
            transaction.update(collection.document(it.id), hiddenUpdate(groupId, isHidden))
          }
        }
        .await()
    if (isHidden) {
      try {
        hideLateAdditions(item, groupId)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        throw IllegalStateException(
            "The folder is hidden, but some recently added items may still be visible. " +
                "Try again.",
            e,
        )
      }
    }
  }

  /**
   * Waits for [write] until the server answers or [SERVER_ACK_GRACE_MS] has passed, whichever comes
   * first. A Firestore write is applied to the local cache as soon as it is made, but its Task only
   * completes when the server acknowledges it, which never happens offline.
   * - The server answers in time (online): a rejection is thrown, as when the Task was awaited.
   * - It does not (offline, or a slow network): the call returns, and Firestore syncs the write
   *   when the device reconnects. A later rejection (e.g. by the security rules) is logged, since
   *   the caller already has its result.
   */
  private suspend fun awaitLocalWrite(write: Task<Void>, operation: String) {
    val answered =
        withTimeoutOrNull(SERVER_ACK_GRACE_MS) {
          write.await()
          true
        }
    if (answered == null) {
      write.addOnFailureListener { e -> Log.e(TAG, "$operation was rejected when syncing", e) }
    }
  }

  /**
   * Reads [document] from the default source, and from the local cache when the server cannot be
   * reached: the read fails with UNAVAILABLE, or gives no answer within [SERVER_READ_TIMEOUT_MS]. A
   * cache miss offline fails with UNAVAILABLE; a document known to be deleted gives a snapshot that
   * does not exist. Other errors (e.g. PERMISSION_DENIED) are thrown as they are.
   */
  private suspend fun getWithCacheFallback(document: DocumentReference): DocumentSnapshot {
    val fromDefaultSource =
        try {
          withTimeoutOrNull(SERVER_READ_TIMEOUT_MS) { document.get().await() }
        } catch (e: FirebaseFirestoreException) {
          if (e.code != FirebaseFirestoreException.Code.UNAVAILABLE) throw e
          null
        }
    return fromDefaultSource ?: document.get(Source.CACHE).await()
  }

  override suspend fun hasPendingWrites(): Boolean =
      try {
        withTimeoutOrNull(PENDING_WRITES_CHECK_MS) {
          firestore.waitForPendingWrites().await()
          false
        } ?: true
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        // The writes could not be confirmed as synced (e.g. the user changed): warn.
        Log.w(TAG, "Could not check the pending writes", e)
        true
      }

  /**
   * Targeted write that unshares a file from [groupId]: it deletes that group's placement and
   * removes the group from `sharedGroupsIds` in the same update, and leaves every other field
   * (including the placements of other groups) untouched.
   */
  private fun removePlacementUpdate(groupId: String): Map<String, Any> =
      mapOf(
          "$FIELD_PLACEMENTS.$groupId" to FieldValue.delete(),
          FIELD_SHARED_GROUPS_IDS to FieldValue.arrayRemove(groupId),
      )

  /** Targeted write that adds a file to [groupId]: its placement and the group in the list. */
  private fun addPlacementUpdate(
      groupId: String,
      folderId: String,
      isHidden: Boolean,
  ): Map<String, Any> =
      mapOf(
          "$FIELD_PLACEMENTS.$groupId" to mapOf("folderId" to folderId, "isHidden" to isHidden),
          FIELD_SHARED_GROUPS_IDS to FieldValue.arrayUnion(groupId),
      )

  /** Targeted write that changes only the visibility of a file in [groupId]. */
  private fun hiddenUpdate(groupId: String, isHidden: Boolean): Map<String, Any> =
      mapOf("$FIELD_PLACEMENTS.$groupId.isHidden" to isHidden)

  /** Whether [folderId] is hidden in [groupId]. The group root has no placement, so never. */
  private suspend fun isHiddenIn(folderId: String, groupId: String): Boolean =
      folderId != groupId &&
          getFile(folderId).getOrThrow()?.placements?.get(groupId)?.isHidden == true

  /** [root] and everything below it in its owner's personal space. */
  private suspend fun collectPersonalSubtree(root: JDRFile): List<JDRFile> =
      subtreeOf(root) { getPersonalFolderFiles(it.id, root.ownerId).first() }

  /** [root] and everything below it in the folder tree of [groupId], read with one query. */
  private suspend fun collectGroupSubtree(root: JDRFile, groupId: String): List<JDRFile> {
    val allGroupFiles =
        collection
            .whereArrayContains(FIELD_SHARED_GROUPS_IDS, groupId)
            .get()
            .await()
            .toObjects(JDRFile::class.java)
    return subtreeOf(root) { folder ->
      allGroupFiles.filter { it.placements[groupId]?.folderId == folder.id }
    }
  }

  /**
   * Hides what was added to the subtree of [root] while it was being hidden. Once [root] is hidden,
   * new additions inherit the hidden state, so a few passes are enough to catch what was missed.
   */
  internal suspend fun hideLateAdditions(root: JDRFile, groupId: String) {
    repeat(MAX_LATE_PASSES) {
      val stillVisible =
          collectGroupSubtree(root, groupId).filter { it.placements[groupId]?.isHidden != true }
      if (stillVisible.isEmpty()) return
      check(stillVisible.size <= MAX_BATCH_WRITES) {
        "Too many items to update at once (maximum $MAX_BATCH_WRITES)."
      }
      val batch = firestore.batch()
      stillVisible.forEach { batch.update(collection.document(it.id), hiddenUpdate(groupId, true)) }
      batch.commit().await()
    }
  }

  override fun getFileFlow(fileId: String): Flow<JDRFile?> = callbackFlow {
    // callbackFlow bridges Firestore's callback API with Kotlin Coroutines Flow.
    // It allows us to emit values asynchronously whenever Firestore triggers an update.
    val listenerRegistration =
        // addSnapshotListener acts like a live connection (similar to a web socket).
        // It triggers immediately with the current state, and then again every time the document
        // changes in the DB.
        collection.document(fileId).addSnapshotListener { snapshot, error ->
          if (error != null) {
            // If an error occurs (e.g., permission denied), we close the Flow with the error.
            close(error)
            // Return from the listener callback so we don't process a null snapshot.
            return@addSnapshotListener
          }
          if (snapshot != null) {
            // trySend safely pushes the newly updated document into the Flow for observers to
            // collect.
            trySend(snapshot.toObject(JDRFile::class.java))
          } else {
            // If the document doesn't exist or was deleted, we emit null.
            trySend(null)
          }
        }

    // awaitClose suspends the coroutine until the Flow collector stops collecting (e.g., ViewModel
    // is cleared).
    // It is CRITICAL to remove the listener here to prevent memory leaks and unnecessary network
    // usage.
    awaitClose { listenerRegistration.remove() }
  }

  /**
   * Helper function to observe a Firestore query and emit a list of [JDRFile]s.
   *
   * @param query The Firestore query to observe.
   * @param filterCondition An optional condition to filter the parsed [JDRFile]s.
   * @param sortCondition An optional block to sort the parsed [JDRFile]s.
   * @param errorMessageContext A string describing the context of the query for error logging.
   */
  private fun observeFilesQuery(
      query: Query,
      errorMessageContext: String,
      filterCondition: (JDRFile) -> Boolean = { true },
      sortCondition: (MutableList<JDRFile>) -> Unit = {},
  ): Flow<List<JDRFile>> = callbackFlow {
    val listenerRegistration = query.addSnapshotListener { snapshot, error ->
      if (error != null) {
        close(error)
        return@addSnapshotListener
      }

      if (snapshot != null) {
        val files = mutableListOf<JDRFile>()

        for (document in snapshot.documents) {
          try {
            val file = document.toObject(JDRFile::class.java)
            if (file != null && filterCondition(file)) {
              files.add(file)
            }
          } catch (e: Exception) {
            Log.e(
                "WorkspaceRepository",
                "Error in the deserialization of $errorMessageContext doc ${document.id}",
                e,
            )
          }
        }
        sortCondition(files)
        trySend(files)
      }
    }
    awaitClose { listenerRegistration.remove() }
  }

  override fun getPersonalFolderFiles(folderId: String, ownerId: String): Flow<List<JDRFile>> =
      observeFilesQuery(
          query =
              collection
                  .whereEqualTo("ownerId", ownerId)
                  .whereEqualTo("personalParentId", folderId),
          errorMessageContext = "personal folder",
          filterCondition = { it.type != DocumentType.GROUP_ROOT },
      )

  override fun getGroupFolderFiles(folderId: String, groupId: String): Flow<List<JDRFile>> =
      observeFilesQuery(
          query =
              collection
                  .whereArrayContains(FIELD_SHARED_GROUPS_IDS, groupId)
                  .whereEqualTo("$FIELD_PLACEMENTS.$groupId.folderId", folderId),
          errorMessageContext = "group folder",
          sortCondition = { it.sortBy { file -> file.name.lowercase() } },
      )
}
