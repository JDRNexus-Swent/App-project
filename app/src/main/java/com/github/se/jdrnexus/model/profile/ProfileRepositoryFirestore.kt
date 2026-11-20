// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.model.profile

import android.net.Uri
import com.github.se.jdrnexus.model.personalSpace.DocumentType
import com.github.se.jdrnexus.model.personalSpace.WorkspaceRepositoryFirestore
import com.github.se.jdrnexus.model.personalSpace.safeFirestoreRequest
import com.google.firebase.firestore.AggregateSource
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await

/**
 * Stores profiles in [PROFILES_COLLECTION_PATH], one document per user with the user id as document
 * id (DATA_ARCHITECTURE.md, section 1.8). Stats are counted on the server from the existing groups
 * and documents, never stored.
 */
class ProfileRepositoryFirestore(private val firestore: FirebaseFirestore) : ProfileRepository {

  private val profiles = firestore.collection(PROFILES_COLLECTION_PATH)

  override suspend fun getProfile(userId: String): Result<UserProfile> = safeFirestoreRequest {
    requireUserId(userId)
    val snapshot = profiles.document(userId).get().await()
    // A user who never saved a profile gets empty fields: no null (DATA_ARCHITECTURE.md, 1.3).
    UserProfile(
        userId = userId,
        username = snapshot.getString(USERNAME_FIELD) ?: "",
        bio = snapshot.getString(BIO_FIELD) ?: "",
        pictureUrl = snapshot.getString(PICTURE_URL_FIELD) ?: "",
    )
  }

  override suspend fun updateProfile(userId: String, username: String, bio: String): Result<Unit> =
      safeFirestoreRequest {
        requireUserId(userId)
        // Merge: creates the profile on the first save and never touches the picture URL.
        profiles
            .document(userId)
            .set(mapOf(USERNAME_FIELD to username, BIO_FIELD to bio), SetOptions.merge())
            .await()
        Unit
      }

  // Needs Firebase Storage, which comes in a follow-up PR.
  override suspend fun uploadProfilePicture(userId: String, image: Uri): Result<String> =
      Result.failure(UnsupportedOperationException(UPLOAD_NOT_AVAILABLE_MESSAGE))

  override suspend fun getProfileStats(userId: String): Result<ProfileStats> =
      safeFirestoreRequest {
        requireUserId(userId)
        // Every query filters on the caller (participantIds / ownerId): the rules require it.
        val memberGroups =
            firestore
                .collection(GROUPS_COLLECTION_PATH)
                .whereArrayContains("participantIds", userId)
        val ownFiles =
            firestore
                .collection(WorkspaceRepositoryFirestore.COLLECTION_PATH)
                .whereEqualTo("ownerId", userId)
                .whereIn("type", listOf(DocumentType.TEXT.name, DocumentType.CHARACTER.name))
        coroutineScope {
          val created = async { memberGroups.whereEqualTo("gameMasterId", userId).countOnServer() }
          val joined = async { memberGroups.countOnServer() }
          val files = async { ownFiles.countOnServer() }
          ProfileStats(
              campaignsCreated = created.await(),
              groupsJoined = joined.await(),
              filesCreated = files.await(),
          )
        }
      }

  // The server counts the matching documents and returns only the number.
  private suspend fun Query.countOnServer(): Int =
      count().get(AggregateSource.SERVER).await().count.toInt()

  private fun requireUserId(userId: String) = require(userId.isNotBlank()) { BLANK_USER_ID_MESSAGE }

  companion object {
    const val PROFILES_COLLECTION_PATH = "profiles"
    const val USERNAME_FIELD = "username"
    const val BIO_FIELD = "bio"
    const val PICTURE_URL_FIELD = "pictureUrl"

    const val BLANK_USER_ID_MESSAGE = "User id must not be blank."
    const val UPLOAD_NOT_AVAILABLE_MESSAGE = "Profile picture upload is not available yet."

    private const val GROUPS_COLLECTION_PATH = "campaign_groups"
  }
}
