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
   * @return A [Result] containing the [JDRFile] if found, or null/failure otherwise.
   */
  suspend fun getFile(fileId: String): Result<JDRFile?>

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
   * Observes real-time changes to all documents (folders and files) located at the root of a shared
   * campaign group.
   *
   * @param groupId The unique identifier of the group.
   * @return A [Flow] emitting the list of root [JDRFile]s for the group.
   */
  fun getGroupRootFiles(groupId: String): Flow<List<JDRFile>>

  /**
   * Observes real-time changes to all documents contained within a specific parent folder.
   *
   * @param folderId The unique identifier of the parent folder.
   * @return A [Flow] emitting the list of child [JDRFile]s.
   */
  fun getDocumentsInFolder(folderId: String): Flow<List<JDRFile>>
}
