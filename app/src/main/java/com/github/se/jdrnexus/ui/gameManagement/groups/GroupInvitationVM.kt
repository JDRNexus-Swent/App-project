// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.ui.gameManagement

/**
 * The invitation actions the game-management screen needs from its ViewModel. These functions
 * return nothing: their results appear in `GameManagementUiState` (`inviteCode`, `invitationState`,
 * `joinState`), exposed by `GameManagementViewModel.uiState`.
 */
interface GroupInvitationVM {
  /** Creates or loads the invite code of a group the user manages. */
  fun loadInvitation(groupId: String)

  /** Replaces the group's code; the old one stops working. The screen asks for confirmation. */
  fun regenerateInvitation(groupId: String)

  /** Joins the group with [code], as typed. */
  fun joinGroup(code: String)

  /** Clears a previous join result before the join dialog opens again. */
  fun resetJoinState()
}
