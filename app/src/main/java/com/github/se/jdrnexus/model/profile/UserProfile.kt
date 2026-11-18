// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.model.profile

/**
 * Profile information a user chooses to show, stored in `profiles/{userId}`.
 *
 * No field is ever null (DATA_ARCHITECTURE.md, section 1.3): an empty string means "not set".
 */
data class UserProfile(
    val userId: String = "",
    val username: String = "",
    val bio: String = "",
    val pictureUrl: String = "",
)

/** Activity counters of a user. Computed from existing data when requested, never stored. */
data class ProfileStats(
    /** Campaign groups the user created, as their game master. */
    val campaignsCreated: Int = 0,
    /** Campaign groups the user belongs to, including the ones they created. */
    val groupsJoined: Int = 0,
    /** Text files and character sheets the user owns. Folders are not counted. */
    val filesCreated: Int = 0,
)
