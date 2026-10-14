package com.github.se.jdrnexus.viewmodel

import androidx.lifecycle.ViewModel

/**
 * Entry points for the group-management UI.
 *
 * These methods are empty stubs for UI wiring; group management will be implemented separately.
 */
class GameManagementViewModel : ViewModel() {

  /**
   * Loads the groups the current user belongs to, including their role in each group.
   *
   * This stub does not load or expose data yet.
   */
  fun loadGroups() {}

  /**
   * Creates a group with the current user as its GameMaster.
   *
   * This stub does not create a group or assign a role yet.
   *
   * @param name The name of the group to create.
   */
  fun createGroup(name: String) {}

  /**
   * Requests the invitation placeholder for a selected group.
   *
   * This stub does not generate or share invitation codes yet.
   *
   * @param groupId The identifier of the selected group.
   */
  fun requestInvitationPlaceholder(groupId: String) {}
}
