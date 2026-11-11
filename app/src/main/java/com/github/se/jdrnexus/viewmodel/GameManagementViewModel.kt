// Co-authored-by: OpenAI Codex
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.se.jdrnexus.model.personalSpace.CampaignGroup
import com.github.se.jdrnexus.model.personalSpace.GroupRepository
import com.github.se.jdrnexus.model.personalSpace.InvitationCodes
import com.github.se.jdrnexus.model.personalSpace.InvitationError
import com.github.se.jdrnexus.model.personalSpace.InvitationException
import com.github.se.jdrnexus.model.repository.AuthRepository
import com.github.se.jdrnexus.ui.gameManagement.GroupInvitationVM
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
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

/** State of group creation. */
sealed interface GroupActionState {
  data object Idle : GroupActionState

  data object Loading : GroupActionState

  /** Success may include a message for the screen to display. */
  data class Success(val groupId: String, val message: String = "") : GroupActionState

  data class Error(val message: String) : GroupActionState
}

/** Progress of loading or regenerating the invite code of a group the user manages. */
sealed interface InvitationState {
  /** No request is running; [GameManagementUiState.inviteCode] holds the last code, if any. */
  data object Idle : InvitationState

  /** The code is being loaded or regenerated. */
  data object Loading : InvitationState

  /** The last request failed for the given [error]; the screen picks the matching text. */
  data class Error(val error: InvitationError) : InvitationState
}

/** Progress of joining a group with an invitation code. */
sealed interface JoinState {
  /** No join was requested, or the previous result was cleared. */
  data object Idle : JoinState

  /** The join request is running. */
  data object Joining : JoinState

  /** The user joined the group [groupId]; the group list shows it through its own subscription. */
  data class Joined(val groupId: String) : JoinState

  /** The join failed for the given [error]; the screen picks the matching text. */
  data class Error(val error: InvitationError) : JoinState
}

/** The current result of observing the signed-in user's campaign groups. */
sealed interface GroupLoadState {
  /** The group repository subscription has started and has not emitted a result yet. */
  data object Loading : GroupLoadState

  /** There is no authenticated user for whom campaign groups can be loaded. */
  data object SignedOut : GroupLoadState

  /** The subscription emitted one or more groups. */
  data object Success : GroupLoadState

  /** The subscription emitted an empty group list. */
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
    val createState: GroupActionState = GroupActionState.Idle,
    val inviteCode: String? = null,
    val invitationState: InvitationState = InvitationState.Idle,
    val joinState: JoinState = JoinState.Idle,
    val isCreateDialogOpen: Boolean = false,
    val groupName: String = "",
) {
  /** Compatibility state consumed by the Game Management screen. */
  val isCreatingGroup: Boolean
    get() = createState == GroupActionState.Loading

  val createError: String
    get() = (createState as? GroupActionState.Error)?.message.orEmpty()

  val successMessage: String
    get() = (createState as? GroupActionState.Success)?.message.orEmpty()
}

/**
 * Observes the signed-in user's campaign groups and exposes their roles to the group screen. It
 * also manages invitation codes ([GroupInvitationVM]): the Game Master gets and regenerates a
 * group's code, and any signed-in user joins a group with one.
 *
 * The user's UID comes from [AuthRepository.authState] and is passed to
 * [GroupRepository.getUserGroups]. Repository emissions update [uiState] continuously. Firebase
 * access remains in the repository implementation; this ViewModel depends only on repository
 * interfaces.
 *
 * @param groupRepository Provides the real-time group list for a user UID, and creates groups
 *   together with their root folder.
 * @param authRepository Provides the current signed-in user and account changes.
 */
class GameManagementViewModel(
    private val groupRepository: GroupRepository,
    private val authRepository: AuthRepository,
) : ViewModel(), GroupInvitationVM {

  private val _uiState = MutableStateFlow(GameManagementUiState())
  val uiState: StateFlow<GameManagementUiState> = _uiState.asStateFlow()

  /** UID used for the active subscription and retry, or null while signed out. */
  private var currentUserId: String? = null

  /** The one active group subscription; canceled when the user changes, signs out, or retries. */
  private var groupsJob: Job? = null

  private var createJob: Job? = null

  /** The running load or regeneration of an invite code; both share [InvitationState.Loading]. */
  private var invitationJob: Job? = null

  private var joinJob: Job? = null

  private fun cancelActions() {
    createJob?.cancel()
    createJob = null
    invitationJob?.cancel()
    invitationJob = null
    joinJob?.cancel()
    joinJob = null
  }

  init {
    // Keep exactly one subscription for the current UID and discard data on sign-out or switch.
    viewModelScope.launch {
      authRepository.authState
          .map { it?.uid }
          .distinctUntilChanged()
          .catch { failure ->
            cancelActions()
            groupsJob?.cancel()
            groupsJob = null
            currentUserId = null
            val message = failure.message?.takeIf(String::isNotBlank) ?: "Unable to load groups."
            _uiState.value = GameManagementUiState(loadState = GroupLoadState.Error(message))
          }
          .collect { userId ->
            cancelActions()
            currentUserId = userId
            _uiState.value = GameManagementUiState()
            if (userId == null) {
              groupsJob?.cancel()
              groupsJob = null
              _uiState.value = GameManagementUiState(loadState = GroupLoadState.SignedOut)
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

  /** Opens a fresh create-group dialog and clears the previous result. */
  fun openCreateDialog() {
    _uiState.update {
      it.copy(
          isCreateDialogOpen = true,
          groupName = "",
          createState = GroupActionState.Idle,
      )
    }
  }

  /** Updates the dialog name and clears a previous validation or creation error. */
  fun onGroupNameChanged(name: String) {
    _uiState.update {
      it.copy(
          groupName = name,
          createState =
              if (it.createState is GroupActionState.Error) GroupActionState.Idle
              else it.createState,
      )
    }
  }

  /** Closes the dialog unless a group creation request is still in progress. */
  fun closeCreateDialog() {
    if (_uiState.value.isCreatingGroup) return
    _uiState.update { it.copy(isCreateDialogOpen = false, groupName = "") }
  }

  /** Clears a success message after the screen has displayed it. */
  fun dismissSuccessMessage() {
    _uiState.update {
      if (it.createState is GroupActionState.Success) it.copy(createState = GroupActionState.Idle)
      else it
    }
  }

  /** Replaces the prior subscription and maps each repository emission into screen state. */
  private fun observeGroups(userId: String) {
    groupsJob?.cancel()
    _uiState.update { it.copy(groups = emptyList(), loadState = GroupLoadState.Loading) }
    groupsJob = viewModelScope.launch {
      try {
        groupRepository.getUserGroups(userId).collect { groups ->
          currentCoroutineContext().ensureActive()
          val groupState = groups.toUiState(userId)
          _uiState.update { it.copy(groups = groupState.groups, loadState = groupState.loadState) }
        }
      } catch (cancellation: CancellationException) {
        throw cancellation
      } catch (failure: Exception) {
        currentCoroutineContext().ensureActive()
        val message = failure.message?.takeIf(String::isNotBlank) ?: "Unable to load groups."
        _uiState.update { it.copy(groups = emptyList(), loadState = GroupLoadState.Error(message)) }
      }
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
   * Creates a campaign owned by the signed-in user, who is also its first participant.
   *
   * Duplicate calls while creating are ignored. The group subscription supplies the updated list;
   * [GameManagementUiState.createState] reports the write independently of group loading.
   */
  fun createGroup(name: String = _uiState.value.groupName) {
    if (_uiState.value.createState == GroupActionState.Loading) return
    val userId = currentUserId
    val cleanName = name.trim()
    val error =
        when {
          userId == null -> "You must be signed in to create a group."
          cleanName.isEmpty() -> "A group name is required."
          else -> null
        }
    if (error != null) {
      _uiState.update { it.copy(groupName = name, createState = GroupActionState.Error(error)) }
      return
    }

    _uiState.update { it.copy(groupName = name, createState = GroupActionState.Loading) }
    createJob = viewModelScope.launch {
      try {
        val newGroupId = groupRepository.getNewGroupId()
        val group =
            CampaignGroup(
                id = newGroupId,
                name = cleanName,
                gameMasterId = requireNotNull(userId),
                participantIds = listOf(userId),
            )
        groupRepository.createGroup(group).getOrThrow()
        currentCoroutineContext().ensureActive()
        _uiState.update {
          it.copy(
              createState = GroupActionState.Success(group.id, "${group.name} was created."),
              isCreateDialogOpen = false,
              groupName = "",
          )
        }
      } catch (cancellation: CancellationException) {
        throw cancellation
      } catch (failure: Exception) {
        currentCoroutineContext().ensureActive()
        val message = failure.message?.takeIf(String::isNotBlank) ?: "Unable to create the group."
        _uiState.update { it.copy(createState = GroupActionState.Error(message)) }
      }
    }
  }

  /**
   * Loads the invite code of [groupId], creating one if the group has none. The previous code is
   * cleared first, so the screen never shows another group's code while this one loads.
   *
   * Only the group's Game Master may manage its code. Calls while a code is loading or regenerating
   * are ignored.
   */
  override fun loadInvitation(groupId: String) {
    requestInviteCode(groupId, clearPreviousCode = true) { gameMasterId ->
      groupRepository.createInvitation(groupId, gameMasterId)
    }
  }

  /**
   * Replaces the invite code of [groupId]; the old code stops working. The current code stays
   * visible until the new one arrives, because a failed regeneration leaves the old code valid.
   * This expects the code of the same group to be loaded first with [loadInvitation], as the invite
   * dialog does.
   *
   * Same checks as [loadInvitation]. The screen asks for confirmation before calling this.
   */
  override fun regenerateInvitation(groupId: String) {
    requestInviteCode(groupId, clearPreviousCode = false) { gameMasterId ->
      groupRepository.regenerateInvitation(groupId, gameMasterId)
    }
  }

  /**
   * Joins the group that [code] points to. The code is trimmed and uppercased; a code with the
   * wrong format fails with [InvitationError.INVALID_CODE] without any request.
   *
   * Calls while a join is running are ignored. On success the group list updates through the
   * existing [GroupRepository.getUserGroups] subscription.
   *
   * A NETWORK error does not always mean the join failed: the repository writes the membership,
   * then reads the group back, and only that read may have failed. This function does not check
   * which case happened. If the membership was written, the group still appears in [uiState]'s
   * group list through the same subscription, without another request.
   */
  override fun joinGroup(code: String) {
    if (_uiState.value.joinState == JoinState.Joining) return
    val userId = currentUserId
    val normalizedCode = InvitationCodes.normalize(code)
    val error =
        when {
          userId == null -> InvitationError.NOT_SIGNED_IN
          !InvitationCodes.isValid(normalizedCode) -> InvitationError.INVALID_CODE
          else -> null
        }
    if (error != null) {
      _uiState.update { it.copy(joinState = JoinState.Error(error)) }
      return
    }

    _uiState.update { it.copy(joinState = JoinState.Joining) }
    joinJob = viewModelScope.launch {
      val result = groupRepository.joinGroupWithCode(normalizedCode, requireNotNull(userId))
      val joinState =
          result.fold(
              onSuccess = { group -> JoinState.Joined(group.id) },
              onFailure = { failure -> JoinState.Error(failure.toInvitationError()) },
          )
      _uiState.update { it.copy(joinState = joinState) }
    }
  }

  /**
   * Clears a previous join result before the join dialog opens again. Ignored while a join is
   * running: clearing [JoinState.Joining] would let a second join start before the first ends.
   */
  override fun resetJoinState() {
    _uiState.update {
      if (it.joinState == JoinState.Joining) it else it.copy(joinState = JoinState.Idle)
    }
  }

  /**
   * Shared flow of [loadInvitation] and [regenerateInvitation]: checks the caller locally, then
   * runs [request] with the signed-in user's id and exposes the code or the error.
   *
   * A group the user plays in as a Player fails with [InvitationError.NOT_GAME_MASTER] without any
   * request. A group missing from the user's list is sent to the repository, because only it can
   * tell a deleted group ([InvitationError.GROUP_NOT_FOUND]) from someone else's group
   * ([InvitationError.NOT_GAME_MASTER]). The local check only saves a request; the repository and
   * the Firestore rules enforce the Game Master rule.
   */
  private fun requestInviteCode(
      groupId: String,
      clearPreviousCode: Boolean,
      request: suspend (gameMasterId: String) -> Result<String>,
  ) {
    val state = _uiState.value
    if (state.invitationState == InvitationState.Loading) return
    val userId = currentUserId
    val role = state.groups.firstOrNull { it.group.id == groupId }?.role
    val error =
        when {
          userId == null -> InvitationError.NOT_SIGNED_IN
          role == GroupRole.PLAYER -> InvitationError.NOT_GAME_MASTER
          else -> null
        }
    val code = if (clearPreviousCode) null else state.inviteCode
    if (error != null) {
      _uiState.update { it.copy(inviteCode = code, invitationState = InvitationState.Error(error)) }
      return
    }

    _uiState.update { it.copy(inviteCode = code, invitationState = InvitationState.Loading) }
    invitationJob = viewModelScope.launch {
      val result = request(requireNotNull(userId))
      _uiState.update { current ->
        result.fold(
            onSuccess = { newCode ->
              current.copy(inviteCode = newCode, invitationState = InvitationState.Idle)
            },
            onFailure = { failure ->
              current.copy(invitationState = InvitationState.Error(failure.toInvitationError()))
            },
        )
      }
    }
  }

  /** Every repository failure carries an [InvitationError]; anything else counts as NETWORK. */
  private fun Throwable.toInvitationError(): InvitationError =
      (this as? InvitationException)?.error ?: InvitationError.NETWORK
}
