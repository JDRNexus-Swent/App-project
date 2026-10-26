// Co-authored-by: Claude Sonnet 5.5
package com.github.se.jdrnexus.model.personalSpace

import kotlinx.coroutines.flow.Flow

/**
 * Interface defining the operations for managing the workspace repository in JDRNexus. It handles
 * both personal spaces and shared campaign groups, providing real-time synchronization and offline
 * capabilities via Cloud Firestore.
 */
interface WorkspaceRepository {
  // ==========================================
  // Document MANAGEMENT (JDRFile)
  // ==========================================
  /**
   * Generates a new, unique document ID. This is useful for initializing a [JDRFile] with a valid
   * ID before saving it to the database.
   *
   * @return A randomly generated unique string ID.
   */
  fun getNewUid(): String

  /**
   * Creates a new document (e.g., character sheet, lore) in the database.
   *
   * @param file The [JDRFile] object to be created.
   * @return A [Result] indicating success or failure.
   */
  suspend fun createFile(file: JDRFile): Result<Unit>

  /**
   * Updates an existing document in the database. This is used when a user or Game Master updates
   * stats, triggering real-time sync across devices.
   *
   * Only the document's own fields are written. Group sharing ([JDRFile.placements] and
   * [JDRFile.sharedGroupsIds]) is left untouched, so saving an edit made from an older snapshot
   * cannot undo a share made in the meantime.
   *
   * @param file The updated [JDRFile] object.
   * @return A [Result] indicating success or failure.
   */
  suspend fun updateFile(file: JDRFile): Result<Unit>

  /**
   * Permanently deletes a document from the database.
   *
   * @param fileId The unique identifier of the file to delete.
   * @return A [Result] indicating success or failure.
   */
  suspend fun deleteFile(fileId: String): Result<Unit>

  /**
   * Retrieves a single file asynchronously without observing future changes.
   *
   * @param fileId The unique identifier of the file.
   * @return A successful result containing the [JDRFile] if found, null if missing, or a failure
   *   for an actual retrieval error.
   */
  suspend fun getFile(fileId: String): Result<JDRFile?>

  /**
   * Removes an item from a group workspace.
   *
   * If the item is a folder, its descendants belonging to the group are also removed. The supplied
   * [parentFolderId] identifies the selected item's placement; descendants are detached from their
   * immediate parent in the removed subtree. Other parent references and Personal Space ownership
   * are preserved, and the underlying documents are not deleted.
   */
  suspend fun removeItemFromGroup(
      itemId: String,
      groupId: String,
      parentFolderId: String,
  ): Result<Unit>

  /**
   * Adds an existing item to a group workspace, inside [folderId].
   *
   * Only the item itself is added: a folder arrives without its content. The destination must be a
   * folder that already belongs to the group (the group id designates the group's root folder). An
   * item added inside a hidden folder is hidden too. Fails if the item is already in the group, and
   * for a group root. Other fields of the item, and its placements in other groups, are not
   * touched.
   *
   * @param isHidden whether the item starts hidden from the other members.
   */
  suspend fun addItemToGroup(
      itemId: String,
      groupId: String,
      folderId: String,
      isHidden: Boolean = false,
  ): Result<Unit>

  /**
   * Hides or reveals an item in a group workspace.
   *
   * Hiding a folder also hides everything inside it in that group. Revealing only reveals the item
   * itself, and is refused while the folder holding it is hidden. Fails for a group root or an item
   * that is not in the group, and refuses to hide more than 500 items at once.
   */
  suspend fun setItemHidden(itemId: String, groupId: String, isHidden: Boolean): Result<Unit>

  // ==========================================
  // REAL-TIME SYNCHRONIZATION (Flows)
  // ==========================================

  /**
   * Observes real-time changes to a specific file. Essential for updating stats instantly on all
   * devices during a session.
   *
   * @param fileId The unique identifier of the file.
   * @return A [Flow] emitting the updated [JDRFile] whenever it changes in the database.
   */
  fun getFileFlow(fileId: String): Flow<JDRFile?>

  /**
   * Observes real-time changes to all documents (folders and files) located at the root of a user's
   * personal space (where parentFolderIds is empty).
   *
   * @param ownerId The unique identifier of the user (Firebase Auth ID).
   * @return A [Flow] emitting the list of root [JDRFile]s.
   */
  fun getPersonalRootFiles(ownerId: String): Flow<List<JDRFile>>

  /**
   * Observes real-time changes to all documents (folders and files) located in a user's personal
   * folder.
   *
   * @param folderId The unique identifier of the parent folder.
   * @param ownerId The unique identifier of the user (Firebase Auth ID).
   * @return A [Flow] emitting the list of child [JDRFile]s.
   */
  fun getPersonalFolderFiles(folderId: String, ownerId: String): Flow<List<JDRFile>>

  /**
   * Observes real-time changes to all documents contained within a specific group folder.
   *
   * @param folderId The unique identifier of the parent folder.
   * @param groupId The unique identifier of the group.
   * @return A [Flow] emitting the list of child [JDRFile]s.
   */
  fun getGroupFolderFiles(folderId: String, groupId: String): Flow<List<JDRFile>>
}
