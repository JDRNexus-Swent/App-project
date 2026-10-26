// Co-authored-by: Claude Sonnet 5.5
package com.github.se.jdrnexus.model.personalSpace

import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await

class WorkspaceRepositoryFirestore(private val firestore: FirebaseFirestore) : WorkspaceRepository {
  companion object {
    private const val MAX_BATCH_WRITES = 500
    internal const val COLLECTION_PATH = "documents"
  }

  private val collection = firestore.collection(COLLECTION_PATH)

  override fun getNewUid(): String {
    // Calling document() without arguments generates a new reference with a unique ID
    return collection.document().id
  }

  override suspend fun createFile(file: JDRFile): Result<Unit> {
    return try {
      collection.document(file.id).set(file).await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  override suspend fun updateFile(file: JDRFile): Result<Unit> {
    return try {
      // `file` is usually a snapshot read earlier (e.g. by the editor).
      // Writing every field would overwrite the document with that possibly stale copy,
      // so a share or unshare made in the meantime would be silently undone.
      // Group sharing is therefore excluded here: `placements`
      // and `sharedGroupsIds` only change through dedicated repository operations (e.g.
      // removeItemFromGroup), which update both fields together and leave the other fields
      // untouched.
      val fields = file.toMap() - "placements" - "sharedGroupsIds"
      collection.document(file.id).update(fields).await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  override suspend fun deleteFile(fileId: String): Result<Unit> {
    return try {
      collection.document(fileId).delete().await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  override suspend fun getFile(fileId: String): Result<JDRFile?> {
    return try {
      val documentSnapshot = collection.document(fileId).get().await()

      if (!documentSnapshot.exists()) {
        return Result.failure(Exception("Document not found in Firestore"))
      }

      val file = documentSnapshot.toObject(JDRFile::class.java)
      Result.success(file)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  override suspend fun removeItemFromGroup(
      itemId: String,
      groupId: String,
      parentFolderId: String,
  ): Result<Unit> {
    return try {
      require(itemId.isNotBlank()) { "Item ID cannot be empty." }
      require(groupId.isNotBlank()) { "Group ID cannot be empty." }
      require(parentFolderId.isNotBlank()) { "Parent folder ID cannot be empty." }

      val root = getFile(itemId).getOrThrow() ?: throw IllegalArgumentException("Item not found.")

      check(groupId in root.sharedGroupsIds) { "This item does not belong to the group." }
      check(root.type != DocumentType.GROUP_ROOT) {
        "A group root cannot be removed from its group."
      }

      // Collect the selected item and, for folders, descendants with their placement parent.
      val itemsToRemove = linkedMapOf<String, Pair<JDRFile, String>>()
      val visited = mutableSetOf<String>()

      suspend fun collectSubtree(
          file: JDRFile,
          parentId: String,
      ) {
        if (!visited.add(file.id)) return

        // Log each visited item to debug recursive removal and verify its
        // parent-folder relationships and group-sharing references.
        Log.d(
            "GroupWorkspace",
            "Visiting ${file.name} (${file.id}), " +
                "parentId=$parentId, placements=${file.placements}, " +
                "groups=${file.sharedGroupsIds}",
        )

        itemsToRemove[file.id] = file to parentId

        if (!file.isContainer()) return

        val children = getGroupFolderFiles(file.id, groupId).first()

        children
            .filter { groupId in it.sharedGroupsIds }
            .forEach { child ->
              collectSubtree(
                  file = child,
                  parentId = file.id,
              )
            }
      }

      collectSubtree(root, parentId = parentFolderId)

      // Remove group sharing without deleting the underlying documents.
      val batch = firestore.batch()

      itemsToRemove.values.forEach { (file, _) ->
        batch.update(collection.document(file.id), removePlacementUpdate(groupId))
      }

      batch.commit().await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  override suspend fun addItemToGroup(
      itemId: String,
      groupId: String,
      folderId: String,
      isHidden: Boolean,
  ): Result<Unit> {
    return try {
      require(itemId.isNotBlank()) { "Item ID cannot be empty." }
      require(groupId.isNotBlank()) { "Group ID cannot be empty." }
      require(folderId.isNotBlank()) { "Folder ID cannot be empty." }

      val item = getFile(itemId).getOrThrow() ?: throw IllegalArgumentException("Item not found.")
      check(item.type != DocumentType.GROUP_ROOT) { "A group root cannot be added to a group." }
      check(groupId !in item.sharedGroupsIds) { "This item is already in the group." }

      val destination =
          getFile(folderId).getOrThrow()
              ?: throw IllegalArgumentException("Destination folder not found.")
      check(destination.isContainer()) { "The destination is not a folder." }
      check(groupId in destination.sharedGroupsIds) {
        "The destination folder does not belong to the group."
      }

      // Something added inside a hidden folder is hidden too. The group root has no placement.
      val hiddenByFolder = destination.placements[groupId]?.isHidden == true

      collection
          .document(itemId)
          .update(addPlacementUpdate(groupId, folderId, isHidden || hiddenByFolder))
          .await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  override suspend fun setItemHidden(
      itemId: String,
      groupId: String,
      isHidden: Boolean,
  ): Result<Unit> {
    return try {
      require(itemId.isNotBlank()) { "Item ID cannot be empty." }
      require(groupId.isNotBlank()) { "Group ID cannot be empty." }

      val item = getFile(itemId).getOrThrow() ?: throw IllegalArgumentException("Item not found.")
      check(item.type != DocumentType.GROUP_ROOT) { "A group root cannot be hidden." }
      val placement =
          checkNotNull(item.placements[groupId]) { "This item does not belong to the group." }

      if (placement.isHidden == isHidden) return Result.success(Unit)

      val itemsToUpdate =
          if (isHidden) {
            // Hiding a folder hides everything inside it, otherwise a player could see files
            // whose folder is hidden.
            collectGroupSubtree(item, groupId).filter { it.placements[groupId]?.isHidden != true }
          } else {
            // Revealing only reveals this item, and only if the folder holding it is visible.
            if (placement.folderId != groupId) {
              val parent = getFile(placement.folderId).getOrThrow()
              check(parent?.placements?.get(groupId)?.isHidden != true) {
                "Reveal the parent folder first."
              }
            }
            listOf(item)
          }

      check(itemsToUpdate.size <= MAX_BATCH_WRITES) {
        "Too many items to update at once (maximum $MAX_BATCH_WRITES)."
      }

      val batch = firestore.batch()
      itemsToUpdate.forEach {
        batch.update(collection.document(it.id), hiddenUpdate(groupId, isHidden))
      }
      batch.commit().await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  /**
   * Targeted write that unshares a file from [groupId]: it deletes that group's placement and
   * removes the group from `sharedGroupsIds` in the same update, and leaves every other field
   * (including the placements of other groups) untouched.
   */
  private fun removePlacementUpdate(groupId: String): Map<String, Any> =
      mapOf(
          "placements.$groupId" to FieldValue.delete(),
          "sharedGroupsIds" to FieldValue.arrayRemove(groupId),
      )

  /** Targeted write that adds a file to [groupId]: its placement and the group in the list. */
  private fun addPlacementUpdate(
      groupId: String,
      folderId: String,
      isHidden: Boolean,
  ): Map<String, Any> =
      mapOf(
          "placements.$groupId" to mapOf("folderId" to folderId, "isHidden" to isHidden),
          "sharedGroupsIds" to FieldValue.arrayUnion(groupId),
      )

  /** Targeted write that changes only the visibility of a file in [groupId]. */
  private fun hiddenUpdate(groupId: String, isHidden: Boolean): Map<String, Any> =
      mapOf("placements.$groupId.isHidden" to isHidden)

  /** [root] and every file below it in the folder tree of [groupId], each listed once. */
  private suspend fun collectGroupSubtree(root: JDRFile, groupId: String): List<JDRFile> {
    val collected = linkedMapOf<String, JDRFile>()

    suspend fun visit(file: JDRFile) {
      if (collected.putIfAbsent(file.id, file) != null) return
      if (!file.isContainer()) return
      getGroupFolderFiles(file.id, groupId).first().forEach { visit(it) }
    }

    visit(root)
    return collected.values.toList()
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

  override fun getPersonalRootFiles(ownerId: String): Flow<List<JDRFile>> =
      // we get the file at the root defined by ""
      // we explicitly exclude DocumentType.GROUP_ROOT from appearing at the root
      getPersonalFolderFiles("", ownerId)

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
                  .whereArrayContains("sharedGroupsIds", groupId)
                  .whereEqualTo("placements.$groupId.folderId", folderId),
          errorMessageContext = "group folder",
          sortCondition = { it.sortBy { file -> file.name.lowercase() } },
      )
}
