// Co-authored-by: OpenAI Codex
package com.github.se.jdrnexus.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.se.jdrnexus.model.personalSpace.CampaignGroup
import com.github.se.jdrnexus.model.personalSpace.GroupRepository
import com.github.se.jdrnexus.model.repository.AuthRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** The current user's permission level within one campaign group. */
enum class GroupRole {
  /** The user is the campaign's designated Game Master. */
  GAME_MASTER,

  /** The user participates in the campaign as a player. */
  PLAYER,
}

/** A campaign and the signed-in user's role in it, ready for presentation by the screen. */
data class UserGroup(val group: CampaignGroup, val role: GroupRole)

/** The current result of observing the signed-in user's campaign groups. */
sealed interface GroupLoadState {
  /** The group repository subscription has started and has not emitted a result yet. */
  data object Loading : GroupLoadState

  /** The subscription emitted one or more groups. */
  data object Success : GroupLoadState

  /** The subscription emitted an empty group list, or the user is signed out. */
  data object Empty : GroupLoadState

  /**
   * The subscription failed; [message] is displayable and [GameManagementViewModel.loadGroups]
   * retries.
   */
  data class Error(val message: String) : GroupLoadState
}

/**
 * Read-only state consumed by the group-management screen.
 *
 * The screen can collect [GameManagementViewModel.uiState] and render [loadState] alongside the
 * [groups] list. Each row includes its [UserGroup.role].
 */
data class GameManagementUiState(
    val groups: List<UserGroup> = emptyList(),
    val loadState: GroupLoadState = GroupLoadState.Loading,
)

/**
 * Observes the signed-in user's campaign groups and exposes their roles to the group screen.
 *
 * The user's UID comes from [AuthRepository.authState] and is passed to
 * [GroupRepository.getUserGroups]. Repository emissions update [uiState] continuously. Firebase
 * access remains in the repository implementation; this ViewModel depends only on repository
 * interfaces.
 *
 * @param groupRepository Provides the real-time group list for a user UID.
 * @param authRepository Provides the current signed-in user and account changes.
 */
class GameManagementViewModel(
    private val groupRepository: GroupRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

  private val _uiState = MutableStateFlow(GameManagementUiState())
  val uiState: StateFlow<GameManagementUiState> = _uiState.asStateFlow()

  /** UID used for the active subscription and retry, or null while signed out. */
  private var currentUserId: String? = null

  /** The one active group subscription; canceled when the user changes, signs out, or retries. */
  private var groupsJob: Job? = null

  init {
    // Keep exactly one subscription for the current UID and discard data on sign-out or switch.
    viewModelScope.launch {
      authRepository.authState
          .map { it?.uid }
          .distinctUntilChanged()
          .catch { failure ->
            groupsJob?.cancel()
            groupsJob = null
            currentUserId = null
            val message = failure.message?.takeIf(String::isNotBlank) ?: "Unable to load groups."
            _uiState.value = GameManagementUiState(loadState = GroupLoadState.Error(message))
          }
          .collect { userId ->
            currentUserId = userId
            if (userId == null) {
              groupsJob?.cancel()
              groupsJob = null
              _uiState.value = GameManagementUiState(loadState = GroupLoadState.Empty)
            } else {
              observeGroups(userId)
            }
          }
    }
  }

  /**
   * Retries loading after a failure.
   *
   * The initial subscription starts when the ViewModel observes [AuthRepository.authState]. Calls
   * while loading, after a successful emission, or while signed out are ignored, so a screen may
   * safely use this as its retry action without creating duplicate listeners.
   */
  fun loadGroups() {
    val userId = currentUserId ?: return
    if (_uiState.value.loadState is GroupLoadState.Error) {
      observeGroups(userId)
    }
  }

  /** Replaces the prior subscription and maps each repository emission into screen state. */
  private fun observeGroups(userId: String) {
    groupsJob?.cancel()
    _uiState.value = GameManagementUiState(loadState = GroupLoadState.Loading)
    groupsJob = viewModelScope.launch {
      groupRepository
          .getUserGroups(userId)
          .catch { failure ->
            if (currentUserId == userId) {
              val message = failure.message?.takeIf(String::isNotBlank) ?: "Unable to load groups."
              _uiState.value = GameManagementUiState(loadState = GroupLoadState.Error(message))
            }
          }
          .collect { groups -> _uiState.value = groups.toUiState(userId) }
    }
  }

  /** Converts a repository emission into the screen's list and loading state. */
  private fun List<CampaignGroup>.toUiState(userId: String): GameManagementUiState {
    val userGroups = map { group ->
      UserGroup(
          group = group,
          role = if (group.gameMasterId == userId) GroupRole.GAME_MASTER else GroupRole.PLAYER,
      )
    }
    return GameManagementUiState(
        groups = userGroups,
        loadState = if (isEmpty()) GroupLoadState.Empty else GroupLoadState.Success,
    )
  }

  /**
   * Reserved entry point for group creation, which is outside this change.
   *
   * @param name Name requested by the screen; no repository operation is performed yet.
   */
  fun createGroup(name: String) = Unit

  /**
   * Reserved entry point for the invitation placeholder, which is outside this change.
   *
   * @param groupId Selected group ID; no invitation operation is performed yet.
   */
  fun requestInvitationPlaceholder(groupId: String) = Unit
}
