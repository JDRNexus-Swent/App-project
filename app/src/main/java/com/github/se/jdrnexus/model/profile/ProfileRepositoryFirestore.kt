// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.model.profile

import android.net.Uri
import com.github.se.jdrnexus.model.personalSpace.DocumentType
import com.github.se.jdrnexus.model.personalSpace.safeFirestoreRequest
import com.google.firebase.firestore.AggregateSource
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await

/**
 * Stores profiles in [PROFILES_COLLECTION_PATH], one document per user with the user id as document
 * id, and their pictures in Storage under [PICTURES_PATH] (DATA_ARCHITECTURE.md, section 1.8).
 * Stats are counted on the server from the existing groups and documents, never stored.
 */
class ProfileRepositoryFirestore(
    private val firestore: FirebaseFirestore,
    private val storage: FirebaseStorage,
    private val compressor: ProfilePictureCompressor,
) : ProfileRepository {

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

  override suspend fun uploadProfilePicture(userId: String, image: Uri): Result<String> =
      safeFirestoreRequest {
        requireUserId(userId)
        val profile = profiles.document(userId)
        // The rules require a username in every profile, so a picture needs a saved profile.
        require(profile.get().await().exists()) { NO_PROFILE_MESSAGE }
        // Fixed path: a new picture replaces the old one, so each user has at most one file.
        val picture = storage.reference.child("$PICTURES_PATH/$userId.jpg")
        picture.putBytes(compressor.compress(image), JPEG_METADATA).await()
        val url = picture.downloadUrl.await().toString()
        profile.update(PICTURE_URL_FIELD, url).await()
        url
      }

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
                .collection(DOCUMENTS_COLLECTION_PATH)
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

    const val PICTURES_PATH = "profilePictures"

    const val BLANK_USER_ID_MESSAGE = "User id must not be blank."
    const val NO_PROFILE_MESSAGE = "Save your profile before adding a picture."

    private const val GROUPS_COLLECTION_PATH = "campaign_groups"
    private const val DOCUMENTS_COLLECTION_PATH = "documents"

    // Each upload gets a new download URL, so phones may keep a downloaded picture for 30 days.
    private val JPEG_METADATA =
        StorageMetadata.Builder()
            .setContentType("image/jpeg")
            .setCacheControl("public, max-age=2592000")
            .build()
  }
}
