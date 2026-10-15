// Co-authored-by: OpenAI Codex
package com.github.se.jdrnexus.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.se.jdrnexus.model.personalSpace.CampaignGroup
import com.github.se.jdrnexus.model.personalSpace.DocumentType
import com.github.se.jdrnexus.model.personalSpace.GroupRepository
import com.github.se.jdrnexus.model.personalSpace.JDRFile
import com.github.se.jdrnexus.model.personalSpace.WorkspaceRepository
import com.github.se.jdrnexus.model.personalSpace.WorkspaceRepositoryProvider
import com.github.se.jdrnexus.model.repository.AuthRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The current user's permission level within one campaign group. */
enum class GroupRole {
  /** The user is the campaign's designated Game Master. */
  GAME_MASTER,

  /** The user participates in the campaign as a player. */
  PLAYER,
}

/** A campaign and the signed-in user's role in it, ready for presentation by the screen. */
data class UserGroup(val group: CampaignGroup, val role: GroupRole)

/** State of group creation or access to the invitation placeholder. */
sealed interface GroupActionState {
  data object Idle : GroupActionState

  data object Loading : GroupActionState

  /** Success may include a message for the screen to display. */
  data class Success(val groupId: String, val message: String? = null) : GroupActionState

  data class Error(val message: String) : GroupActionState
}

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
    val createState: GroupActionState = GroupActionState.Idle,
    val invitationState: GroupActionState = GroupActionState.Idle,
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
    private val workspaceRepository: WorkspaceRepository = WorkspaceRepositoryProvider.repository,
) : ViewModel() {

  private val _uiState = MutableStateFlow(GameManagementUiState())
  val uiState: StateFlow<GameManagementUiState> = _uiState.asStateFlow()

  /** UID used for the active subscription and retry, or null while signed out. */
  private var currentUserId: String? = null

  /** The one active group subscription; canceled when the user changes, signs out, or retries. */
  private var groupsJob: Job? = null

  private var createJob: Job? = null

  private fun cancelActions() {
    createJob?.cancel()
    createJob = null
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
      var groupCreated = false
      var rootFolderCreated = false
      var groupId: String? = null
      try {
        val newGroupId = workspaceRepository.getNewUid()
        groupId = newGroupId
        val group =
            CampaignGroup(
                id = newGroupId,
                name = cleanName,
                gameMasterId = requireNotNull(userId),
                participantIds = listOf(userId),
            )
        val rootFolder =
            JDRFile(
                id = group.id,
                name = group.name,
                ownerId = requireNotNull(userId),
                sharedGroupsIds = listOf(group.id),
                type = DocumentType.FOLDER,
            )
        workspaceRepository.createFile(rootFolder).getOrThrow()
        rootFolderCreated = true
        groupRepository.createGroup(group).getOrThrow()
        groupCreated = true
        currentCoroutineContext().ensureActive()
        _uiState.update {
          it.copy(
              createState = GroupActionState.Success(group.id, "${group.name} was created."),
              isCreateDialogOpen = false,
              groupName = "",
          )
        }
      } catch (cancellation: CancellationException) {
        if (rootFolderCreated && !groupCreated) {
          cleanupRootFolder(groupId, cancellation)
        }
        throw cancellation
      } catch (failure: Exception) {
        if (rootFolderCreated && !groupCreated) {
          cleanupRootFolder(groupId, failure)
        }
        currentCoroutineContext().ensureActive()
        val message = failure.message?.takeIf(String::isNotBlank) ?: "Unable to create the group."
        _uiState.update { it.copy(createState = GroupActionState.Error(message)) }
      }
    }
  }

  /** Removes a root folder if group creation failed after the folder write completed. */
  private suspend fun cleanupRootFolder(groupId: String?, failure: Throwable) {
    if (groupId == null) return
    withContext(NonCancellable) {
      workspaceRepository.deleteFile(groupId).exceptionOrNull()?.let(failure::addSuppressed)
    }
  }

  /**
   * Exposes the invitation placeholder for a group the current user manages.
   *
   * The live group list already contains each group's role, so this placeholder needs no repository
   * request. Success identifies the selected group so the UI can explain that invitation codes are
   * not available yet. No code is generated or shared. This client-side check is not a security
   * boundary; future invitation operations must be authorized by Firestore rules or trusted backend
   * logic.
   */
  fun requestInvitationPlaceholder(groupId: String) {
    val state = _uiState.value
    if (currentUserId == null) {
      _uiState.update {
        it.copy(
            invitationState = GroupActionState.Error("You must be signed in to manage invitations.")
        )
      }
      return
    }

    val selectedGroup = state.groups.firstOrNull { it.group.id == groupId }
    val result =
        when {
          groupId.isBlank() -> GroupActionState.Error("Select a group to manage invitations.")
          selectedGroup == null || selectedGroup.role != GroupRole.GAME_MASTER ->
              GroupActionState.Error("Only the group's Game Master can manage invitations.")
          else ->
              GroupActionState.Success(
                  groupId = groupId,
                  message = "Invitation management is coming soon.",
              )
        }
    _uiState.update { it.copy(invitationState = result) }
  }
}
