package com.github.se.jdrnexus.model.personalSpace

import kotlinx.coroutines.flow.Flow

/**
 * Interface defining the operations for managing the workspace repository in JDRNexus. It handles
 * both personal spaces and shared campaign groups, providing real-time synchronization and offline
 * capabilities via Cloud Firestore.
 */
interface PersonalSpaceRepository {
  // ==========================================
  // FILE MANAGEMENT (JdrFile)
  // ==========================================

  /**
   * Creates a new document (e.g., character sheet, lore) in the database.
   *
   * @param file The [JdrFile] object to be created.
   * @return A [Result] indicating success or failure.
   */
  suspend fun createFile(file: JdrFile): Result<Unit>

  /**
   * Updates an existing document in the database. This is used when a user or Game Master updates
   * stats, triggering real-time sync across devices.
   *
   * @param file The updated [JdrFile] object.
   * @return A [Result] indicating success or failure.
   */
  suspend fun updateFile(file: JdrFile): Result<Unit>

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
   * @return A [Result] containing the [JdrFile] if found, or null/failure otherwise.
   */
  suspend fun getFile(fileId: String): Result<JdrFile?>

  // ==========================================
  // FOLDER MANAGEMENT (JdrFolder)
  // ==========================================

  /**
   * Creates a new folder in either the personal space or a shared group.
   *
   * @param folder The [JdrFolder] object to be created.
   * @return A [Result] indicating success or failure.
   */
  suspend fun createFolder(folder: JdrFolder): Result<Unit>

  /**
   * Updates an existing folder (e.g., renaming, or adding/removing file IDs inside it).
   *
   * @param folder The updated [JdrFolder] object.
   * @return A [Result] indicating success or failure.
   */
  suspend fun updateFolder(folder: JdrFolder): Result<Unit>

  /**
   * Permanently deletes a folder from the database.
   *
   * @param folderId The unique identifier of the folder to delete.
   * @return A [Result] indicating success or failure.
   */
  suspend fun deleteFolder(folderId: String): Result<Unit>

  /**
   * Retrieves a single folder asynchronously without observing future changes.
   *
   * @param folderId The unique identifier of the folder.
   * @return A [Result] containing the [JdrFolder] if found, or null/failure otherwise.
   */
  suspend fun getFolder(folderId: String): Result<JdrFolder?>

  // ==========================================
  // REAL-TIME SYNCHRONIZATION (Flows)
  // ==========================================

  /**
   * Observes real-time changes to a specific file. Essential for updating stats instantly on all
   * devices during a session.
   *
   * @param fileId The unique identifier of the file.
   * @return A [Flow] emitting the updated [JdrFile] whenever it changes in the database.
   */
  fun getFileFlow(fileId: String): Flow<JdrFile?>

  /**
   * Observes real-time changes to the folders located at the root of a user's personal space.
   *
   * @param ownerId The unique identifier of the user (Firebase Auth ID).
   * @return A [Flow] emitting the list of root [JdrFolder]s.
   */
  fun getPersonalRootFolders(ownerId: String): Flow<List<JdrFolder>>

  /**
   * Observes real-time changes to the folders located at the root of a shared campaign group.
   *
   * @param groupId The unique identifier of the group.
   * @return A [Flow] emitting the list of root [JdrFolder]s for the group.
   */
  fun getGroupRootFolders(groupId: String): Flow<List<JdrFolder>>

  /**
   * Observes real-time changes to the subfolders contained within a specific parent folder.
   *
   * @param parentFolderId The unique identifier of the parent folder.
   * @return A [Flow] emitting the list of child [JdrFolder]s.
   */
  fun getSubFolders(parentFolderId: String): Flow<List<JdrFolder>>

  /**
   * Observes real-time changes to the files contained within a specific folder.
   *
   * @param folderId The unique identifier of the folder.
   * @return A [Flow] emitting the list of [JdrFile]s assigned to this folder.
   */
  fun getFilesInFolder(folderId: String): Flow<List<JdrFile>>

  /**
   * Observes real-time changes to the files located at the root of a user's personal space (files
   * that have not been placed inside any folder).
   *
   * @param ownerId The unique identifier of the user.
   * @return A [Flow] emitting the list of unassigned [JdrFile]s in the personal space.
   */
  fun getPersonalRootFiles(ownerId: String): Flow<List<JdrFile>>

  /**
   * Observes real-time changes to the files located at the root of a shared campaign group (files
   * that have not been placed inside any folder).
   *
   * @param groupId The unique identifier of the group.
   * @return A [Flow] emitting the list of unassigned [JdrFile]s in the group workspace.
   */
  fun getGroupRootFiles(groupId: String): Flow<List<JdrFile>>
}
