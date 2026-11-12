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

  // ==========================================
  // INVITATIONS
  // ==========================================

  /**
   * Returns the invitation code of a group, creating one if the group has none yet. Calling it
   * again returns the same code: nothing is written when a code already exists.
   *
   * @param groupId The unique identifier of the group.
   * @param gameMasterId The id of the user making the request. It must be the group's Game Master.
   * @return A [Result] holding the code, or an [InvitationException] with [InvitationError]
   *   NOT_SIGNED_IN, GROUP_NOT_FOUND, NOT_GAME_MASTER or NETWORK.
   */
  suspend fun createInvitation(groupId: String, gameMasterId: String): Result<String>

  /**
   * Replaces the invitation code of a group by a new one. The old code stops working immediately.
   * If the group had no code, behaves like [createInvitation].
   *
   * @param groupId The unique identifier of the group.
   * @param gameMasterId The id of the user making the request. It must be the group's Game Master.
   * @return A [Result] holding the new code, or an [InvitationException] with [InvitationError]
   *   NOT_SIGNED_IN, GROUP_NOT_FOUND, NOT_GAME_MASTER or NETWORK.
   */
  suspend fun regenerateInvitation(groupId: String, gameMasterId: String): Result<String>

  /**
   * Adds [userId] to the group the [code] points to, then returns that group.
   *
   * The membership is written in one batch, then the group is read back. If that last read fails,
   * the user **is already a member** but the call returns NETWORK, and retrying returns
   * ALREADY_MEMBER. After a NETWORK or ALREADY_MEMBER failure, callers should rely on
   * [getUserGroups] to know whether the user joined, rather than on the error alone.
   *
   * @param code The invitation code, as typed by the user (case and surrounding spaces are
   *   ignored).
   * @param userId The id of the user who wants to join.
   * @return A [Result] holding the joined [CampaignGroup], or an [InvitationException] with
   *   [InvitationError] NOT_SIGNED_IN, INVALID_CODE, UNKNOWN_CODE, GROUP_NOT_FOUND, ALREADY_MEMBER
   *   or NETWORK.
   */
  suspend fun joinGroupWithCode(code: String, userId: String): Result<CampaignGroup>
}

/**
 * Why an invitation operation failed. Carried by [InvitationException], which every failed [Result]
 * of the invitation operations holds.
 */
enum class InvitationError {
  /** The caller's user id is blank: nobody is signed in. */
  NOT_SIGNED_IN,

  /** The group does not exist (or its id is blank). */
  GROUP_NOT_FOUND,

  /** The caller is not the Game Master of the group. */
  NOT_GAME_MASTER,

  /** The code is malformed (wrong length or forbidden characters). No network call was made. */
  INVALID_CODE,

  /** No invitation exists for this code: it never existed or was revoked by a regeneration. */
  UNKNOWN_CODE,

  /** The user already belongs to the group the code points to. */
  ALREADY_MEMBER,

  /** Any other failure: network, timeout, permission or unexpected error. */
  NETWORK,
}

/** Failure of an invitation operation. Every failed [Result] of those operations holds one. */
class InvitationException(val error: InvitationError) : Exception(error.name)
