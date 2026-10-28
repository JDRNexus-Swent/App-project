// Co-authored-by: Claude Sonnet 5.5
package com.github.se.jdrnexus.model.personalSpace

import kotlinx.coroutines.flow.Flow

interface GroupRepository {
  // ==========================================
  // GROUP MANAGEMENT
  // ==========================================
  /**
   * Generates a new, unique ID for a campaign group. This is useful for initializing a
   * [CampaignGroup] before saving it to the database.
   *
   * @return A randomly generated unique string ID.
   */
  fun getNewGroupId(): String

  /**
   * Creates a new campaign group in the database. This initializes the shared workspace for all
   * participants of the campaign.
   *
   * The group's root folder (a [JDRFile] of type GROUP_ROOT with the same id) is created in the
   * same batch, so a group never exists without its root.
   *
   * @param group The [CampaignGroup] object containing the group's metadata and participants.
   * @return A [Result] indicating success or failure.
   */
  suspend fun createGroup(group: CampaignGroup): Result<Unit>

  /**
   * Updates an existing campaign group in the database. This is used when a Game Master changes the
   * group's settings, name, or metadata, triggering real-time sync.
   *
   * The group's root folder is renamed in the same batch, so both names always match. Fails if the
   * group or its root folder does not exist.
   *
   * @param group The updated [CampaignGroup] object.
   * @return A [Result] indicating success or failure.
   */
  suspend fun updateGroup(group: CampaignGroup): Result<Unit>

  /**
   * Deletes an existing campaign group from the database. Note: This removes access to the shared
   * workspace for all participants.
   *
   * @param groupId The unique identifier of the group to delete.
   * @return A [Result] indicating success or failure.
   */
  suspend fun deleteGroup(groupId: String): Result<Unit>

  /**
   * Fetches a campaign group from the database once. Useful for one-time checks, such as verifying
   * group existence before joining.
   *
   * @param groupId The unique identifier of the group.
   * @return A [Result] containing the [CampaignGroup] if found, or a failure if it does not exist.
   */
  suspend fun getGroup(groupId: String): Result<CampaignGroup>

  /**
   * Adds a specific user to an existing campaign group. This grants the user access to the group's
   * shared workspace and triggers synchronization across all devices of the participants.
   *
   * @param groupId The unique identifier of the group.
   * @param userId The unique identifier of the user to add.
   * @return A [Result] indicating success or failure.
   */
  suspend fun addUserToGroup(groupId: String, userId: String): Result<Unit>

  /**
   * Removes a specific user from an existing campaign group. This revokes the user's access to the
   * shared workspace. The Game Master, acting as an admin, can trigger this action.
   *
   * @param groupId The unique identifier of the group.
   * @param userId The unique identifier of the user to remove.
   * @return A [Result] indicating success or failure.
   */
  suspend fun removeUserFromGroup(groupId: String, userId: String): Result<Unit>

  /**
   * Subscribes to real-time updates for a specific campaign group. It allows the UI to react
   * instantly whenever the group's metadata changes.
   *
   * @param groupId The unique identifier of the group to observe.
   * @return A [Flow] emitting the updated [CampaignGroup], or null if the group is deleted.
   */
  fun getGroupFlow(groupId: String): Flow<CampaignGroup?>

  /**
   * Subscribes to real-time updates for all campaign groups a specific user is a part of. This is
   * essential for dynamically displaying the user's available shared workspaces on their home
   * screen.
   *
   * @param userId The unique identifier of the user.
   * @return A [Flow] emitting a list of [CampaignGroup] objects the user has access to.
   */
  fun getUserGroups(userId: String): Flow<List<CampaignGroup>>
}
