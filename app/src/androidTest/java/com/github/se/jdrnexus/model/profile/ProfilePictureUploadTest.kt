// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.model.profile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.se.jdrnexus.endToEnd.TestFirebaseEmulators
import com.github.se.jdrnexus.model.profile.ProfileRepositoryFirestore.Companion.NO_PROFILE_MESSAGE
import com.github.se.jdrnexus.model.profile.ProfileRepositoryFirestore.Companion.PICTURES_PATH
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.StorageMetadata
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Profile picture upload against the Storage emulator, with the project security rules.
 * Instrumented because the Storage SDK cannot read the emulator's responses under Robolectric
 * (TESTING.md, section 4.2). Each test signs in a new anonymous user and the emulators drop their
 * data at the end of the run, so no cleanup is needed.
 */
@RunWith(AndroidJUnit4::class)
class ProfilePictureUploadTest {

  private val context = ApplicationProvider.getApplicationContext<Context>()
  private val auth = FirebaseAuth.getInstance()
  private val storage = FirebaseStorage.getInstance()
  private val jpeg = StorageMetadata.Builder().setContentType("image/jpeg").build()
  private val repository =
      ProfileRepositoryFirestore(
          FirebaseFirestore.getInstance(),
          storage,
          ProfilePictureCompressor(context.contentResolver),
      )
  private lateinit var userId: String

  @Before fun setUp() = runBlocking { userId = signInNewUser() }

  @After fun tearDown() = auth.signOut()

  // ============ Helpers ============

  private suspend fun signInNewUser(): String {
    auth.signOut()
    return checkNotNull(auth.signInAnonymously().await().user).uid
  }

  // Writes a [width] x [height] PNG to the cache directory and returns its uri.
  private fun imageUri(width: Int, height: Int): Uri {
    val file = File(context.cacheDir, "test-image-${width}x$height.png")
    file.outputStream().use {
      Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
          .compress(Bitmap.CompressFormat.PNG, 100, it)
    }
    return Uri.fromFile(file)
  }

  // Width and height of the JPEG in [bytes], or -1 each if they are not a JPEG.
  private fun jpegSize(bytes: ByteArray): Pair<Int, Int> {
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    return if (options.outMimeType == "image/jpeg") options.outWidth to options.outHeight
    else -1 to -1
  }

  private fun pictureOf(userId: String) = storage.reference.child("$PICTURES_PATH/$userId.jpg")

  // Bytes of the stored picture of [userId], read as the signed-in user.
  private suspend fun storedPicture(userId: String): ByteArray =
      pictureOf(userId).getBytes(1024 * 1024).await()

  // Error code of the StorageException thrown by [action], or null if it did not throw one.
  private suspend fun storageError(action: suspend () -> Unit): Int? =
      (runCatching { action() }.exceptionOrNull() as? StorageException)?.errorCode

  // ============ uploadProfilePicture ============

  @Test
  fun uploadProfilePictureStoresSmallJpegAndSavesItsUrl() = runBlocking {
    repository.updateProfile(userId, USERNAME, BIO).getOrThrow()

    val url = repository.uploadProfilePicture(userId, imageUri(1024, 512)).getOrThrow()

    assertEquals(256 to 128, jpegSize(storedPicture(userId)))
    assertEquals(
        UserProfile(userId, USERNAME, BIO, url),
        repository.getProfile(userId).getOrThrow(),
    )
  }

  @Test
  fun uploadProfilePictureReplacesPreviousPicture() = runBlocking {
    repository.updateProfile(userId, USERNAME, BIO).getOrThrow()
    val firstUrl = repository.uploadProfilePicture(userId, imageUri(100, 50)).getOrThrow()

    val url = repository.uploadProfilePicture(userId, imageUri(50, 100)).getOrThrow()

    assertEquals(50 to 100, jpegSize(storedPicture(userId)))
    assertEquals(url, repository.getProfile(userId).getOrThrow().pictureUrl)
    // A new URL on each upload, so phones never show a cached old picture.
    assertNotEquals(firstUrl, url)
  }

  @Test
  fun uploadProfilePictureFailsWithoutSavedProfile() = runBlocking {
    val result = repository.uploadProfilePicture(userId, imageUri(100, 50))

    assertEquals(NO_PROFILE_MESSAGE, result.exceptionOrNull()?.message)
    assertEquals(StorageException.ERROR_OBJECT_NOT_FOUND, storageError { storedPicture(userId) })
  }

  // The repository only uploads small JPEGs to the caller's path, so these check the rules
  // directly.

  @Test
  fun pictureOfAnotherUserCannotBeWritten() = runBlocking {
    val error = storageError { pictureOf(OTHER_USER_ID).putBytes(JPEG_BYTES, jpeg).await() }

    assertEquals(StorageException.ERROR_NOT_AUTHORIZED, error)
  }

  @Test
  fun pictureOfAnotherUserCannotBeRead() = runBlocking {
    pictureOf(userId).putBytes(JPEG_BYTES, jpeg).await()
    val ownerId = userId
    signInNewUser()

    val error = storageError { storedPicture(ownerId) }

    assertEquals(StorageException.ERROR_NOT_AUTHORIZED, error)
  }

  @Test
  fun pictureThatIsNotJpegIsDenied() = runBlocking {
    val text = StorageMetadata.Builder().setContentType("text/plain").build()

    val error = storageError { pictureOf(userId).putBytes(JPEG_BYTES, text).await() }

    assertEquals(StorageException.ERROR_NOT_AUTHORIZED, error)
  }

  @Test
  fun pictureOf1MbOrMoreIsDenied() = runBlocking {
    val error = storageError { pictureOf(userId).putBytes(ByteArray(1024 * 1024), jpeg).await() }

    assertEquals(StorageException.ERROR_NOT_AUTHORIZED, error)
  }

  companion object {
    private const val OTHER_USER_ID = "profile-other"
    private const val USERNAME = "Aelis"
    private const val BIO = "Bard of the Sunken Crown"
    // The rules check the declared type and the size, not the content.
    private val JPEG_BYTES = ByteArray(100)

    @BeforeClass @JvmStatic fun configureFirebaseEmulators() = TestFirebaseEmulators.configure()
  }
}
