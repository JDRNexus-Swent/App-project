// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.utils

import android.net.Uri
import com.github.se.jdrnexus.model.profile.ProfileRepository
import com.github.se.jdrnexus.model.profile.ProfileStats
import com.github.se.jdrnexus.model.profile.UserProfile
import kotlinx.coroutines.CompletableDeferred

/**
 * Fake [ProfileRepository] for ViewModel and navigation tests. Returns the configured results,
 * starting from [savedProfile], [stats] and [pictureUrl], and records every request; a non-null
 * [gate] holds the operation until completed.
 */
internal class FakeProfileRepository(
    savedProfile: UserProfile = LOADED_PROFILE,
    stats: ProfileStats = LOADED_PROFILE_STATE.stats,
    pictureUrl: String = "https://example.com/profile.png",
) : ProfileRepository {
  var profileResult: Result<UserProfile> = Result.success(savedProfile)
  var statsResult: Result<ProfileStats> = Result.success(stats)
  var updateResult: Result<Unit> = Result.success(Unit)
  var uploadResult: Result<String> = Result.success(pictureUrl)
  var gate: CompletableDeferred<Unit>? = null

  val profileRequests = mutableListOf<String>()
  val updates = mutableListOf<Triple<String, String, String>>()
  val uploads = mutableListOf<Pair<String, Uri>>()

  override suspend fun getProfile(userId: String): Result<UserProfile> {
    profileRequests.add(userId)
    gate?.await()
    return profileResult
  }

  override suspend fun updateProfile(
      userId: String,
      username: String,
      bio: String,
  ): Result<Unit> {
    updates.add(Triple(userId, username, bio))
    gate?.await()
    return updateResult
  }

  override suspend fun uploadProfilePicture(userId: String, image: Uri): Result<String> {
    uploads.add(userId to image)
    gate?.await()
    return uploadResult
  }

  override suspend fun getProfileStats(userId: String): Result<ProfileStats> = statsResult
}
