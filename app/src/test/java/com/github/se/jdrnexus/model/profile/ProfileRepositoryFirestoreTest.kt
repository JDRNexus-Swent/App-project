// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.model.profile

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.github.se.jdrnexus.model.personalSpace.CampaignGroup
import com.github.se.jdrnexus.model.profile.ProfileRepositoryFirestore.Companion.BLANK_USER_ID_MESSAGE
import com.github.se.jdrnexus.model.profile.ProfileRepositoryFirestore.Companion.PROFILES_COLLECTION_PATH
import com.github.se.jdrnexus.utils.FirebaseEmulatorTestUtils
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Those tests use the Firestore emulator with the project security rules; start it with "firebase
 * emulators:exec --only auth,firestore".
 */
@RunWith(RobolectricTestRunner::class)
class ProfileRepositoryFirestoreTest {

  private val context = ApplicationProvider.getApplicationContext<Context>()
  private lateinit var firestore: FirebaseFirestore
  private lateinit var storage: FirebaseStorage
  private lateinit var repository: ProfileRepositoryFirestore

  @Before
  fun setUp() {
    firestore = FirebaseEmulatorTestUtils.signInAs(USER_ID)
    storage = FirebaseEmulatorTestUtils.storageAs(USER_ID)
    repository =
        ProfileRepositoryFirestore(
            firestore,
            storage,
            ProfilePictureCompressor(context.contentResolver),
        )
  }

  @After
  fun tearDown() {
    runBlocking {
      FirebaseEmulatorTestUtils.tearDown(
          firestore,
          PROFILES_COLLECTION_PATH,
          GROUPS_COLLECTION,
          DOCUMENTS_COLLECTION,
      )
    }
  }

  // ============ Helpers ============

  private suspend fun seedProfile(userId: String, fields: Map<String, String>) =
      FirebaseEmulatorTestUtils.writeBypassingRules(
          firestore,
          "$PROFILES_COLLECTION_PATH/$userId",
          fields,
      )

  // Shape of a stored profile document. Nullable, so a field that was never written stays null.
  private data class StoredProfile(
      val username: String? = null,
      val bio: String? = null,
      val pictureUrl: String? = null,
  )

  private suspend fun storedProfile(userId: String): StoredProfile? =
      FirebaseEmulatorTestUtils.readBypassingRules(
          firestore,
          "$PROFILES_COLLECTION_PATH/$userId",
          StoredProfile::class.java,
      )

  private suspend fun seedGroup(id: String, gameMasterId: String, participantIds: List<String>) =
      FirebaseEmulatorTestUtils.writeBypassingRules(
          firestore,
          "$GROUPS_COLLECTION/$id",
          CampaignGroup(
              id = id,
              name = "Group $id",
              gameMasterId = gameMasterId,
              participantIds = participantIds,
          ),
      )

  private suspend fun seedDocument(id: String, ownerId: String, type: String) =
      FirebaseEmulatorTestUtils.writeBypassingRules(
          firestore,
          "$DOCUMENTS_COLLECTION/$id",
          mapOf(
              "id" to id,
              "ownerId" to ownerId,
              "name" to "Doc $id",
              "type" to type,
              "personalParentId" to "",
              "placements" to emptyMap<String, Any>(),
              "sharedGroupsIds" to emptyList<String>(),
              "content" to "",
          ),
      )

  // count() delivers its result on the main looper, which this test thread must pump.
  private suspend fun statsOf(userId: String): Result<ProfileStats> = coroutineScope {
    val stats = async(Dispatchers.IO) { repository.getProfileStats(userId) }
    FirebaseEmulatorTestUtils.pumpMainLooperUntil { stats.isCompleted }
    stats.await()
  }

  private fun assertPermissionDenied(result: Result<*>) {
    val error = result.exceptionOrNull() as? FirebaseFirestoreException
    assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, error?.code)
  }

  // ============ getProfile ============

  @Test
  fun getProfileReturnsStoredFields() = runBlocking {
    seedProfile(USER_ID, mapOf("username" to USERNAME, "bio" to BIO, "pictureUrl" to PICTURE_URL))

    val result = repository.getProfile(USER_ID)

    assertEquals(UserProfile(USER_ID, USERNAME, BIO, PICTURE_URL), result.getOrNull())
  }

  @Test
  fun getProfileReturnsEmptyProfileWhenNoneWasSaved() = runBlocking {
    val result = repository.getProfile(USER_ID)

    assertEquals(UserProfile(userId = USER_ID), result.getOrNull())
  }

  @Test
  fun getProfileFailsForBlankUserId() = runBlocking {
    val result = repository.getProfile(" ")

    assertEquals(BLANK_USER_ID_MESSAGE, result.exceptionOrNull()?.message)
  }

  @Test
  fun getProfileIsDeniedForAnotherUser() = runBlocking {
    seedProfile(OTHER_USER_ID, mapOf("username" to "Morgane", "bio" to "", "pictureUrl" to ""))

    val result = repository.getProfile(OTHER_USER_ID)

    assertPermissionDenied(result)
  }

  // ============ updateProfile ============

  @Test
  fun updateProfileCreatesProfileOnFirstSave() = runBlocking {
    val result = repository.updateProfile(USER_ID, USERNAME, BIO)

    assertEquals(Unit, result.getOrNull())
    // Only username and bio are written: no pictureUrl.
    assertEquals(StoredProfile(username = USERNAME, bio = BIO), storedProfile(USER_ID))
  }

  @Test
  fun updateProfileKeepsExistingPictureUrl() = runBlocking {
    seedProfile(USER_ID, mapOf("username" to "OldName", "bio" to "", "pictureUrl" to PICTURE_URL))

    val result = repository.updateProfile(USER_ID, USERNAME, BIO)

    assertEquals(Unit, result.getOrNull())
    assertEquals(StoredProfile(USERNAME, BIO, PICTURE_URL), storedProfile(USER_ID))
  }

  @Test
  fun updateProfileFailsForBlankUserId() = runBlocking {
    val result = repository.updateProfile("", USERNAME, BIO)

    assertEquals(BLANK_USER_ID_MESSAGE, result.exceptionOrNull()?.message)
  }

  @Test
  fun updateProfileIsDeniedForAnotherUser() = runBlocking {
    val result = repository.updateProfile(OTHER_USER_ID, USERNAME, BIO)

    assertPermissionDenied(result)
    assertNull(storedProfile(OTHER_USER_ID))
  }

  @Test
  fun updateProfileIsDeniedWhenUsernameIsTooShort() = runBlocking {
    val result = repository.updateProfile(USER_ID, "Al", BIO)

    assertPermissionDenied(result)
    assertNull(storedProfile(USER_ID))
  }

  @Test
  fun updateProfileIsDeniedWhenUsernameIsTooLong() = runBlocking {
    val result = repository.updateProfile(USER_ID, "a".repeat(31), BIO)

    assertPermissionDenied(result)
    assertNull(storedProfile(USER_ID))
  }

  @Test
  fun updateProfileIsDeniedWhenUsernameIsNotTrimmed() = runBlocking {
    val result = repository.updateProfile(USER_ID, " $USERNAME", BIO)

    assertPermissionDenied(result)
    assertNull(storedProfile(USER_ID))
  }

  @Test
  fun updateProfileIsDeniedWhenBioIsTooLong() = runBlocking {
    val result = repository.updateProfile(USER_ID, USERNAME, "b".repeat(301))

    assertPermissionDenied(result)
    assertNull(storedProfile(USER_ID))
  }

  @Test
  fun updateProfileAcceptsValuesAtTheLimits() = runBlocking {
    val shortest = "a".repeat(3)
    val longest = "a".repeat(30)
    val longestBio = "b".repeat(300)

    assertEquals(Unit, repository.updateProfile(USER_ID, shortest, longestBio).getOrNull())
    assertEquals(StoredProfile(shortest, longestBio), storedProfile(USER_ID))
    assertEquals(Unit, repository.updateProfile(USER_ID, longest, BIO).getOrNull())
    assertEquals(StoredProfile(longest, BIO), storedProfile(USER_ID))
  }

  @Test
  fun profileWithoutUsernameIsDenied() = runBlocking {
    // The repository always sends a username, so this checks the rule directly.
    val profile = firestore.collection(PROFILES_COLLECTION_PATH).document(USER_ID)

    val result = runCatching { profile.set(mapOf("bio" to BIO)).await() }

    assertPermissionDenied(result)
    assertNull(storedProfile(USER_ID))
  }

  @Test
  fun profileWithExtraFieldIsDenied() = runBlocking {
    val profile = firestore.collection(PROFILES_COLLECTION_PATH).document(USER_ID)

    val result = runCatching {
      profile.set(mapOf("username" to USERNAME, "bio" to BIO, "role" to "admin")).await()
    }

    assertPermissionDenied(result)
    assertNull(storedProfile(USER_ID))
  }

  @Test
  fun profileWithNonStringPictureUrlIsDenied() = runBlocking {
    val profile = firestore.collection(PROFILES_COLLECTION_PATH).document(USER_ID)

    val result = runCatching {
      profile.set(mapOf("username" to USERNAME, "bio" to BIO, "pictureUrl" to 42)).await()
    }

    assertPermissionDenied(result)
    assertNull(storedProfile(USER_ID))
  }

  @Test
  fun profileDeletionIsDenied() = runBlocking {
    seedProfile(USER_ID, mapOf("username" to USERNAME, "bio" to BIO, "pictureUrl" to PICTURE_URL))
    val profile = firestore.collection(PROFILES_COLLECTION_PATH).document(USER_ID)

    val result = runCatching { profile.delete().await() }

    assertPermissionDenied(result)
    assertEquals(StoredProfile(USERNAME, BIO, PICTURE_URL), storedProfile(USER_ID))
  }

  // ============ uploadProfilePicture ============

  // The upload itself is tested on an Android emulator: androidTest ProfilePictureUploadTest.

  @Test
  fun uploadProfilePictureFailsForBlankUserId() = runBlocking {
    val result = repository.uploadProfilePicture(" ", Uri.EMPTY)

    assertEquals(BLANK_USER_ID_MESSAGE, result.exceptionOrNull()?.message)
  }

  // ============ getProfileStats ============

  @Test
  fun getProfileStatsCountsCreatedAndJoinedGroupsAndOwnFiles() = runBlocking {
    seedGroup("created", gameMasterId = USER_ID, participantIds = listOf(USER_ID))
    seedGroup(
        "joined",
        gameMasterId = OTHER_USER_ID,
        participantIds = listOf(OTHER_USER_ID, USER_ID),
    )
    seedGroup("unrelated", gameMasterId = OTHER_USER_ID, participantIds = listOf(OTHER_USER_ID))
    seedDocument("text", USER_ID, "TEXT")
    seedDocument("character", USER_ID, "CHARACTER")
    seedDocument("folder", USER_ID, "FOLDER")
    seedDocument("created-root", USER_ID, "GROUP_ROOT")
    seedDocument("other-text", OTHER_USER_ID, "TEXT")

    val result = statsOf(USER_ID)

    assertEquals(
        ProfileStats(campaignsCreated = 1, groupsJoined = 2, filesCreated = 2),
        result.getOrNull(),
    )
  }

  @Test
  fun getProfileStatsReturnsZerosForNewUser() = runBlocking {
    val result = statsOf(USER_ID)

    assertEquals(ProfileStats(), result.getOrNull())
  }

  @Test
  fun getProfileStatsFailsForBlankUserId() = runBlocking {
    val result = repository.getProfileStats("")

    assertEquals(BLANK_USER_ID_MESSAGE, result.exceptionOrNull()?.message)
  }

  @Test
  fun getProfileStatsIsDeniedForAnotherUser() = runBlocking {
    assertPermissionDenied(statsOf(OTHER_USER_ID))
  }

  companion object {
    private const val USER_ID = "profile-user"
    private const val OTHER_USER_ID = "profile-other"
    private const val USERNAME = "Aelis"
    private const val BIO = "Bard of the Sunken Crown"
    private const val PICTURE_URL = "https://example.com/aelis.png"
    private const val GROUPS_COLLECTION = "campaign_groups"
    private const val DOCUMENTS_COLLECTION = "documents"
  }
}
