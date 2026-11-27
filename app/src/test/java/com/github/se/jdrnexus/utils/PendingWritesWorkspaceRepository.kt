// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.utils

import com.github.se.jdrnexus.model.personalSpace.FileEdit
import com.github.se.jdrnexus.model.personalSpace.JDRFile
import com.github.se.jdrnexus.model.personalSpace.WorkspaceRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow

/**
 * [WorkspaceRepository] for the sign-out tests: it only answers [hasPendingWrites], with
 * [pendingWrites], and counts the calls in [pendingWritesChecks]. Any other call fails the test.
 */
class PendingWritesWorkspaceRepository(var pendingWrites: Boolean = false) : WorkspaceRepository {
  var pendingWritesChecks = 0
    private set

  /** When set, [hasPendingWrites] waits for it before answering. */
  var checkGate: CompletableDeferred<Unit>? = null

  override suspend fun hasPendingWrites(): Boolean {
    pendingWritesChecks++
    checkGate?.await()
    return pendingWrites
  }

  override fun getNewUid(): String = error("Not used")

  override suspend fun createFile(file: JDRFile, waitForServer: Boolean): Result<Unit> =
      error("Not used")

  override suspend fun updateFile(edit: FileEdit, waitForServer: Boolean): Result<Unit> =
      error("Not used")

  override suspend fun deleteFile(fileId: String): Result<Unit> = error("Not used")

  override suspend fun getFile(fileId: String): Result<JDRFile?> = error("Not used")

  override suspend fun removeItemFromGroup(itemId: String, groupId: String): Result<Unit> =
      error("Not used")

  override suspend fun addItemToGroup(
      itemId: String,
      groupId: String,
      folderId: String,
      isHidden: Boolean,
      includeContent: Boolean,
  ): Result<Unit> = error("Not used")

  override suspend fun setItemHidden(
      itemId: String,
      groupId: String,
      isHidden: Boolean,
  ): Result<Unit> = error("Not used")

  override fun getFileFlow(fileId: String): Flow<JDRFile?> = error("Not used")

  override fun getPersonalFolderFiles(folderId: String, ownerId: String): Flow<List<JDRFile>> =
      error("Not used")

  override fun getGroupFolderFiles(folderId: String, groupId: String): Flow<List<JDRFile>> =
      error("Not used")
}
