/** Co-authored-by: AI Agent */
package com.github.se.jdrnexus.model.personalSpace

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class WorkspaceRepositoryFirestore(private val firestore: FirebaseFirestore) : WorkspaceRepository {

  override fun getNewUid(): String {
    // Calling document() without arguments generates a new reference with a unique ID
    return collection.document().id
  }

  private val collection = firestore.collection("documents")

  override suspend fun createFile(file: JDRFile): Result<Unit> {
    return try {
      collection.document(file.id).set(file).await()
      Result.success(Unit)
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  override suspend fun updateFile(file: JDRFile): Result<Unit> {
    return try {
      collection
          .document(file.id)
          .update(
              mapOf(
                  "name" to file.name,
                  "ownerId" to file.ownerId,
                  "sharedGroupsIds" to file.sharedGroupsIds,
                  "parentFolderIds" to file.parentFolderIds,
                  "type" to file.type,
                  "content" to file.content,
              )
          )
          .await()
      Result.success(Unit)
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  override suspend fun deleteFile(fileId: String): Result<Unit> {
    return try {
      collection.document(fileId).delete().await()
      Result.success(Unit)
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  override suspend fun getFile(fileId: String): Result<JDRFile?> {
    return try {
      val documentSnapshot = collection.document(fileId).get().await()
      val file = documentSnapshot.toObject(JDRFile::class.java)
      Result.success(file)
    } catch (e: Exception) {
      Result.failure(e)
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

  override fun getPersonalRootFiles(ownerId: String): Flow<List<JDRFile>> = callbackFlow {
    // Listen for changes on all documents owned by this user where parentFolderIds is empty
    // (meaning they are at the root level).
    val listenerRegistration =
        collection
            .whereEqualTo("ownerId", ownerId)
            .whereEqualTo("parentFolderIds", emptyList<String>())
            .addSnapshotListener { snapshot, error ->
              if (error != null) {
                close(error)
                return@addSnapshotListener
              }
              // Transform the Firestore documents into JDRFile objects.
              // We use mapNotNull to safely ignore any documents that fail to deserialize.
              // We also filter again on the client side (`parentFolderIds.isEmpty()`) as a safety
              // measure,
              // which can sometimes help bypass Firestore index limitations on empty arrays.
              val files =
                  snapshot
                      ?.documents
                      ?.mapNotNull { it.toObject(JDRFile::class.java) }
                      ?.filter { it.parentFolderIds.isEmpty() } ?: emptyList()

              // Emit the updated list of files to the Flow.
              trySend(files)
            }

    // Clean up the listener when the Flow is cancelled.
    awaitClose { listenerRegistration.remove() }
  }

  override fun getGroupRootFiles(groupId: String): Flow<List<JDRFile>> = callbackFlow {
    // Listen for changes on documents shared with this group.
    // 'whereArrayContains' checks if the 'groupId' exists inside the 'sharedGroupsIds' list.
    val listenerRegistration =
        collection.whereArrayContains("sharedGroupsIds", groupId).addSnapshotListener {
            snapshot,
            error ->
          if (error != null) {
            close(error)
            return@addSnapshotListener
          }

          // Similar to personal root files, we parse the documents and enforce the root-level
          // condition
          // (parentFolderIds.isEmpty()) on the client side since Firestore doesn't easily support
          // multiple array/inequality filters in a single query without complex composite indexes.
          val files =
              snapshot
                  ?.documents
                  ?.mapNotNull { it.toObject(JDRFile::class.java) }
                  ?.filter { it.parentFolderIds.isEmpty() } ?: emptyList()

          // Emit the updated list.
          trySend(files)
        }

    // Clean up the listener when the Flow is cancelled.
    awaitClose { listenerRegistration.remove() }
  }

  override fun getDocumentsInFolder(folderId: String): Flow<List<JDRFile>> = callbackFlow {
    // Listen for changes on all documents that have 'folderId' inside their 'parentFolderIds' list.
    // This allows us to observe the contents of a specific folder in real-time.
    val listenerRegistration =
        collection.whereArrayContains("parentFolderIds", folderId).addSnapshotListener {
            snapshot,
            error ->
          if (error != null) {
            close(error)
            return@addSnapshotListener
          }

          // Map the raw Firestore documents to our JDRFile data class.
          val documents =
              snapshot?.documents?.mapNotNull { it.toObject(JDRFile::class.java) } ?: emptyList()

          // Emit the updated list of folder contents.
          trySend(documents)
        }

    // Clean up the listener when the Flow is cancelled.
    awaitClose { listenerRegistration.remove() }
  }
}
