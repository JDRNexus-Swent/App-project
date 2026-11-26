// Co-authored-by: Claude Sonnet 5.5
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.model.personalSpace

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * Interface defining the operations for managing the workspace repository in JDRNexus. It handles
 * both personal spaces and shared campaign groups, providing real-time synchronization and offline
 * capabilities via Cloud Firestore.
 *
 * Offline, [createFile], [updateFile] and [deleteFile] succeed as soon as the change is in the
 * local cache, where the reads of this repository see it at once; it reaches the server when the
 * device reconnects. See [hasPendingWrites].
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
   * @param waitForServer whether to wait for the server's answer, so that a refusal (e.g. by the
   *   security rules) is always returned. The Group Workspace sets it: it only works online. When
   *   it is false (the personal space), the call returns once the change is in the local cache, see
   *   [WorkspaceRepository].
   * @return A [Result] indicating success or failure.
   */
  suspend fun createFile(file: JDRFile, waitForServer: Boolean = false): Result<Unit>

  /**
   * Updates the editable fields of an existing document (see [FileEdit]). This is used when a user
   * or Game Master updates stats, triggering real-time sync across devices.
   *
   * Group sharing is not part of [FileEdit], so saving an edit made from an older snapshot cannot
   * undo a share made in the meantime.
   *
   * @param waitForServer whether to wait for the server's answer, as for [createFile].
   * @return A [Result] indicating success or failure.
   */
  suspend fun updateFile(edit: FileEdit, waitForServer: Boolean = false): Result<Unit>

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
   * The item's placement is read from the document. If the item is a folder, its descendants
   * belonging to the group are also removed, and are detached from their immediate parent in the
   * removed subtree. Other parent references and Personal Space ownership are preserved, and the
   * underlying documents are not deleted.
   */
  suspend fun removeItemFromGroup(itemId: String, groupId: String): Result<Unit>

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
   * @param includeContent for a folder, also adds everything inside it in its owner's personal
   *   space, keeping the same structure.
   */
  suspend fun addItemToGroup(
      itemId: String,
      groupId: String,
      folderId: String,
      isHidden: Boolean = false,
      includeContent: Boolean = false,
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
  // OFFLINE SYNC
  // ==========================================

  /**
   * Whether some writes of the signed-in user exist only on this device, not yet synced to the
   * server (e.g. made offline). Signing out would lose them, so the UI can warn first.
   *
   * Returns within about half a second: `true` if the writes have not synced by then (always the
   * case offline) or if their state cannot be checked, `false` if every write made so far has
   * reached the server.
   */
  suspend fun hasPendingWrites(): Boolean

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

  // ==========================================
  // ONE-SHOT READS
  // ==========================================

  /**
   * Reads the documents of a personal folder once, without observing changes. Use it instead of the
   * first value of [getPersonalFolderFiles], which can come from the local cache alone and miss the
   * documents this device has not cached yet. This read waits for the server while it is reachable,
   * and reads the local cache once the device is known to be offline.
   *
   * The default implementation takes the first value of [getPersonalFolderFiles], which is exact
   * for repositories without a cache.
   */
  suspend fun fetchPersonalFolderFiles(folderId: String, ownerId: String): List<JDRFile> =
      getPersonalFolderFiles(folderId, ownerId).first()

  /**
   * Reads the documents of a group folder once, without observing changes, with the same source
   * rules as [fetchPersonalFolderFiles].
   */
  suspend fun fetchGroupFolderFiles(folderId: String, groupId: String): List<JDRFile> =
      getGroupFolderFiles(folderId, groupId).first()
}
