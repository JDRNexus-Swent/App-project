// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.utils

import com.github.se.jdrnexus.model.gameManagement.GameActivityEntry
import com.github.se.jdrnexus.model.gameManagement.GameLoreEntry
import com.github.se.jdrnexus.model.gameManagement.GameSessionRepository
import com.github.se.jdrnexus.model.gameManagement.GameSessionSnapshot
import com.github.se.jdrnexus.model.personalSpace.CampaignGroup
import com.github.se.jdrnexus.model.personalSpace.DocumentType
import com.github.se.jdrnexus.model.personalSpace.FileEdit
import com.github.se.jdrnexus.model.personalSpace.GroupRepository
import com.github.se.jdrnexus.model.personalSpace.JDRFile
import com.github.se.jdrnexus.model.personalSpace.WorkspaceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf

/** Holds one game session in memory, with no lore or activity, for navigation tests. */
class NavigationGameSessionRepository : GameSessionRepository {
  val session = MutableStateFlow(GameSessionSnapshot())
  var lastSavedGroupId: String? = null

  override fun observeSession(groupId: String): Flow<GameSessionSnapshot> = session

  override suspend fun saveSession(groupId: String, session: GameSessionSnapshot): Result<Unit> =
      runCatching {
        session.validate()
        lastSavedGroupId = groupId
        this.session.value = session
      }

  override fun observeLore(groupId: String): Flow<List<GameLoreEntry>> = flowOf(emptyList())

  override fun observeSharedLore(groupId: String): Flow<List<GameLoreEntry>> = flowOf(emptyList())

  override suspend fun saveLore(groupId: String, lore: GameLoreEntry): Result<Unit> =
      error("Not used")

  override suspend fun setLoreVisibility(
      groupId: String,
      documentId: String,
      isShared: Boolean,
  ): Result<Unit> = error("Not used")

  override suspend fun loadActivities(groupId: String): Result<List<GameActivityEntry>> =
      Result.success(emptyList())

  override fun observeActivities(groupId: String): Flow<List<GameActivityEntry>> =
      flowOf(emptyList())

  override suspend fun recordActivity(
      groupId: String,
      userId: String,
      playerName: String,
      action: String,
      diceResult: String?,
  ): Result<Unit> = error("Not used")
}

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
