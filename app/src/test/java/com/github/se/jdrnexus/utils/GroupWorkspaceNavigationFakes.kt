// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.github.se.jdrnexus.utils

import com.github.se.jdrnexus.model.personalSpace.CampaignGroup
import com.github.se.jdrnexus.model.personalSpace.DocumentType
import com.github.se.jdrnexus.model.personalSpace.FileEdit
import com.github.se.jdrnexus.model.personalSpace.GroupRepository
import com.github.se.jdrnexus.model.personalSpace.JDRFile
import com.github.se.jdrnexus.model.personalSpace.WorkspaceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class NavigationGroupRepository(private val group: CampaignGroup) : GroupRepository {
  var groupFlowLoads = 0
  var lastGroupFlowId: String? = null

  override fun getNewGroupId(): String = error("Not used")

  override suspend fun createGroup(group: CampaignGroup): Result<Unit> = error("Not used")

  override suspend fun updateGroup(group: CampaignGroup): Result<Unit> = error("Not used")

  override suspend fun deleteGroup(groupId: String): Result<Unit> = error("Not used")

  override suspend fun getGroup(groupId: String): Result<CampaignGroup> = error("Not used")

  override suspend fun addUserToGroup(groupId: String, userId: String): Result<Unit> =
      error("Not used")

  override suspend fun removeUserFromGroup(groupId: String, userId: String): Result<Unit> =
      error("Not used")

  override suspend fun createInvitation(groupId: String, gameMasterId: String): Result<String> =
      error("Not used")

  override suspend fun regenerateInvitation(groupId: String, gameMasterId: String): Result<String> =
      error("Not used")

  override suspend fun joinGroupWithCode(code: String, userId: String): Result<CampaignGroup> =
      error("Not used")

  override fun getGroupFlow(groupId: String): Flow<CampaignGroup?> {
    groupFlowLoads++
    lastGroupFlowId = groupId
    return flowOf(group.takeIf { it.id == groupId })
  }

  override fun getUserGroups(userId: String): Flow<List<CampaignGroup>> =
      flowOf(listOf(group).filter { userId in it.participantIds })
}

class NavigationWorkspaceRepository(
    private val files: List<JDRFile> = emptyList(),
) : WorkspaceRepository {
  var groupRootLoads = 0

  override fun getNewUid(): String = error("Not used")

  override suspend fun createFile(file: JDRFile): Result<Unit> = error("Not used")

  override suspend fun updateFile(edit: FileEdit): Result<Unit> = error("Not used")

  override suspend fun deleteFile(fileId: String): Result<Unit> = error("Not used")

  override suspend fun getFile(fileId: String): Result<JDRFile?> =
      Result.success(files.firstOrNull { it.id == fileId })

  override suspend fun removeItemFromGroup(
      itemId: String,
      groupId: String,
  ): Result<Unit> = error("Not used")

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

  override fun getFileFlow(fileId: String): Flow<JDRFile?> =
      flowOf(files.firstOrNull { it.id == fileId })

  override fun getPersonalFolderFiles(folderId: String, ownerId: String): Flow<List<JDRFile>> =
      flowOf(files.filter { it.ownerId == ownerId && it.personalParentId == folderId })

  override fun getGroupFolderFiles(folderId: String, groupId: String): Flow<List<JDRFile>> {
    if (folderId == groupId) groupRootLoads++
    return flowOf(
        files.filter {
          it.type != DocumentType.GROUP_ROOT &&
              groupId in it.sharedGroupsIds &&
              it.placements[groupId]?.folderId == folderId
        }
    )
  }
}
