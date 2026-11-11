// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.utils

import com.github.se.jdrnexus.ui.gameManagement.GroupInvitationVM

/**
 * Records the invitation calls made by the game-management screen. It does not change any state:
 * each test sets the resulting `GameManagementUiState` itself.
 */
internal class RecordingInvitationVM : GroupInvitationVM {
  val loadedGroups = mutableListOf<String>()
  val regeneratedGroups = mutableListOf<String>()
  val joinedCodes = mutableListOf<String>()
  var joinResets = 0

  override fun loadInvitation(groupId: String) {
    loadedGroups += groupId
  }

  override fun regenerateInvitation(groupId: String) {
    regeneratedGroups += groupId
  }

  override fun joinGroup(code: String) {
    joinedCodes += code
  }

  override fun resetJoinState() {
    joinResets++
  }
}
