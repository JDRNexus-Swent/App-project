// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.model.profile

import android.net.Uri

/**
 * Reads and updates user profiles. Every failure (blank id, network, permission denied) is returned
 * as a [Result.failure], never thrown.
 */
interface ProfileRepository {
  /**
   * Returns the profile of [userId]. A user who never saved a profile gets a [UserProfile] with
   * this id and empty fields, not a failure.
   */
  suspend fun getProfile(userId: String): Result<UserProfile>

  /**
   * Saves the [username] and [bio] of [userId]. The picture is left unchanged, so a save can never
   * overwrite a picture uploaded in the meantime.
   */
  suspend fun updateProfile(userId: String, username: String, bio: String): Result<Unit>

  /**
   * Uploads the image at [image] as the profile picture of [userId], stores its URL in the profile
   * and returns that URL.
   */
  suspend fun uploadProfilePicture(userId: String, image: Uri): Result<String>

  /** Computes the activity counters of [userId]. */
  suspend fun getProfileStats(userId: String): Result<ProfileStats>
}
