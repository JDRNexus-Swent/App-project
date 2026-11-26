// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.ui.gameManagement

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.se.jdrnexus.model.personalSpace.CampaignGroup
import com.github.se.jdrnexus.model.personalSpace.GroupRepository
import com.github.se.jdrnexus.model.repository.AuthRepository
import com.github.se.jdrnexus.model.repository.AuthUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Lets any member of the group [groupId] see its members, and its Game Master remove a player or
 * delete the group.
 *
 * The members come from [GroupRepository.getGroupFlow], so the list follows every change while the
 * screen is open. The signed-in user ([AuthRepository.authState]) may manage the group only as its
 * Game Master, and is named by their username (their user id if it is blank); every other member is
 * named by their user id. When the group stops existing, deleted here or elsewhere,
 * [GroupManagementUiState.isGroupDeleted] becomes true.
 */
class GroupManagementViewModel(
    private val groupId: String,
    private val groupRepository: GroupRepository,
    authRepository: AuthRepository,
) : ViewModel(), GroupManagementInterfaceVM {
  private val _uiState = MutableStateFlow(GroupManagementUiState())
  override val uiState: StateFlow<GroupManagementUiState> = _uiState.asStateFlow()

  init {
    combine(authRepository.authState, groupRepository.getGroupFlow(groupId)) { user, group ->
          showGroup(user, group)
        }
        .catch {
          _uiState.update { it.copy(isLoading = false, error = GroupManagementError.LOAD_FAILED) }
        }
        .launchIn(viewModelScope)
  }

  override fun removeMember(uid: String) {
    if (_uiState.value.members.none { !it.isGameMaster && it.uid == uid }) return
    runAction(GroupManagementError.REMOVE_FAILED) {
      groupRepository.removeUserFromGroup(groupId, uid)
    }
  }

  override fun deleteGroup() =
      runAction(GroupManagementError.DELETE_FAILED) {
        groupRepository.deleteGroup(groupId).onSuccess {
          _uiState.update { it.copy(isGroupDeleted = true) }
        }
      }

  private fun showGroup(user: AuthUser?, group: CampaignGroup?) {
    if (group == null) {
      _uiState.update { it.copy(isLoading = false, isGroupDeleted = true) }
      return
    }
    val gameMaster = group.gameMasterId
    // The signed-in user is named by their username; the others by their id until #329.
    fun label(uid: String) = user?.username?.takeIf { user.uid == uid && it.isNotBlank() } ?: uid
    val players = group.participantIds.filter { it != gameMaster }
    _uiState.update {
      it.copy(
          groupName = group.name,
          members =
              listOf(GroupMember(gameMaster, label(gameMaster), isGameMaster = true)) +
                  players.map { uid -> GroupMember(uid, label(uid), isGameMaster = false) },
          isLoading = false,
          canManage = user?.uid == gameMaster,
      )
    }
  }

  /**
   * Runs [action] when the user manages the group and no other action runs, and reports [failure]
   * if it fails. The check and the flag are set on the main thread before launching, so two taps in
   * the same frame start one action only.
   */
  private fun runAction(failure: GroupManagementError, action: suspend () -> Result<Unit>) {
    val state = _uiState.value
    if (!state.canManage || state.isWorking) return
    _uiState.update { it.copy(isWorking = true, error = null) }
    viewModelScope.launch {
      val failed = action().isFailure
      _uiState.update { it.copy(isWorking = false, error = if (failed) failure else it.error) }
    }
  }
}
