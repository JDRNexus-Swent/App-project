// Co-authored-by: Codex <codex@openai.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.ui.gameManagement.groups

import kotlinx.coroutines.flow.StateFlow

/**
 * One member of a campaign group, as shown by the group management screen.
 *
 * @property uid The member's user id.
 * @property label The text that names the member on screen.
 * @property isGameMaster Whether the member is the group's Game Master, who cannot be removed.
 */
data class GroupMember(val uid: String, val label: String, val isGameMaster: Boolean)

/** A failure the group management screen reports; the screen picks the matching text. */
enum class GroupManagementError {
  /** The group could not be read. */
  LOAD_FAILED,

  /** Removing a player failed; the group is unchanged. */
  REMOVE_FAILED,

  /** Deleting the group failed; the group is unchanged. */
  DELETE_FAILED,
}

/**
 * State of the group management screen.
 *
 * @property groupName The name of the group.
 * @property members The Game Master first, then the players.
 * @property isLoading True until the group is read for the first time.
 * @property canManage True when the signed-in user is the Game Master of the group.
 * @property isWorking True while a removal or the deletion of the group is running.
 * @property error The last failure, cleared when the next action starts.
 * @property isGroupDeleted True once the group no longer exists; the screen then leaves.
 */
data class GroupManagementUiState(
    val groupName: String = "",
    val members: List<GroupMember> = emptyList(),
    val isLoading: Boolean = true,
    val canManage: Boolean = false,
    val isWorking: Boolean = false,
    val error: GroupManagementError? = null,
    val isGroupDeleted: Boolean = false,
)

/** What the group management screen needs from its ViewModel. */
interface GroupManagementInterfaceVM {
  /** The current state of the screen. */
  val uiState: StateFlow<GroupManagementUiState>

  /**
   * Removes the player [uid] from the group. Ignored while another action runs, when the user is
   * not the Game Master, or when [uid] is the Game Master or not a player of the group.
   */
  fun removeMember(uid: String)

  /** Deletes the whole group. Ignored while another action runs or when the user is not the GM. */
  fun deleteGroup()

  /**
   * Follows the group again after it could not be read ([GroupManagementError.LOAD_FAILED]).
   * Ignored while the group is still followed.
   */
  fun retry()
}
