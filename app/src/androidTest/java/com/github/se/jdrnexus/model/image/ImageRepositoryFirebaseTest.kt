// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.model.image

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.github.se.jdrnexus.model.personalSpace.DocumentType
import com.github.se.jdrnexus.model.personalSpace.JDRFile
import com.github.se.jdrnexus.model.personalSpace.WorkspaceRepositoryFirestore
import com.github.se.jdrnexus.utils.StorageEmulatorTestUtils.TIMEOUT_MS
import com.github.se.jdrnexus.utils.StorageEmulatorTestUtils.awaitWithTimeout
import com.github.se.jdrnexus.utils.StorageEmulatorTestUtils.firestoreAs
import com.github.se.jdrnexus.utils.StorageEmulatorTestUtils.storageAs
import com.github.se.jdrnexus.utils.StorageEmulatorTestUtils.storageErrorCode
import com.google.firebase.storage.StorageException
import java.io.ByteArrayOutputStream
import java.net.URL
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * [ImageRepositoryFirebase] on the Firebase emulators. Instrumented because Storage cannot run
 * under Robolectric (TESTING.md 4.2); pictures go through the media store to get photo-picker-like
 * uris (API 29+).
 */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 29)
class ImageRepositoryFirebaseTest {

  private val resolver = ApplicationProvider.getApplicationContext<Context>().contentResolver
  private val run = UUID.randomUUID().toString()
  private val ownerId = "$run-owner"
  private val fileId = "$run-image"
  private val owner = repositoryAs(ownerId)
  private val picked = mutableListOf<Uri>()
  private val png =
      ByteArrayOutputStream().use {
        Bitmap.createBitmap(64, 32, Bitmap.Config.ARGB_8888)
            .compress(Bitmap.CompressFormat.PNG, 100, it)
        it.toByteArray()
      }

  @After fun deletePicked() = picked.forEach { resolver.delete(it, null, null) }

  private fun repositoryAs(uid: String) =
      ImageRepositoryFirebase(
          storageAs(uid),
          resolver,
          WorkspaceRepositoryFirestore(firestoreAs(uid)),
      )

  // Adds [bytes] to the media store; IS_PENDING 1 then 0 makes it record the size.
  private fun pick(bytes: ByteArray = png, type: String = "image/png", ext: String = "png"): Uri {
    val values =
        ContentValues().apply {
          put(MediaStore.MediaColumns.DISPLAY_NAME, "$run.$ext")
          put(MediaStore.MediaColumns.MIME_TYPE, type)
          put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
    val uri = checkNotNull(resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values))
    picked += uri
    checkNotNull(resolver.openOutputStream(uri)).use { it.write(bytes) }
    values.clear()
    values.put(MediaStore.MediaColumns.IS_PENDING, 0)
    resolver.update(uri, values, null, null)
    return uri
  }

  private fun imageFile(parentId: String = "") =
      JDRFile(id = fileId, name = "Dungeon map", ownerId = ownerId, personalParentId = parentId)

  private suspend fun <T> timed(call: suspend () -> T): T = withTimeout(TIMEOUT_MS) { call() }

  private suspend fun upload(file: JDRFile = imageFile(), uri: Uri = pick()) = timed {
    owner.uploadImage(file, uri)
  }

  private fun storedPicture() = storageAs(ownerId).reference.child("images/$ownerId/$fileId")

  private suspend fun storedBytes() = storedPicture().getBytes(MAX_BYTES).awaitWithTimeout()

  private suspend fun storedRecord(): JDRFile? =
      firestoreAs(ownerId)
          .collection("documents")
          .document(fileId)
          .get()
          .awaitWithTimeout()
          .toObject(JDRFile::class.java)

  private fun errorOf(result: Result<*>) = (result.exceptionOrNull() as? ImageException)?.error

  // Fails unless [result] is [error] and neither the picture nor the record is stored.
  private suspend fun assertRefused(case: String, error: ImageError, result: Result<*>) {
    assertEquals(case, error, errorOf(result))
    assertEquals(case, StorageException.ERROR_OBJECT_NOT_FOUND, storageErrorCode { storedBytes() })
    assertNull(case, storedRecord())
  }

  @Test
  fun uploadStoresThePictureAndCreatesItsImageFile() = runBlocking {
    var progress = 0f

    val created = timed { owner.uploadImage(imageFile(), pick()) { progress = it } }.getOrThrow()

    val expected = imageFile().copy(type = DocumentType.IMAGE, content = "images/$ownerId/$fileId")
    assertEquals(expected, created)
    assertEquals(expected, storedRecord())
    assertArrayEquals(png, storedBytes())
    assertEquals("image/png", storedPicture().metadata.awaitWithTimeout().contentType)
    assertEquals(1f, progress)
  }

  @Test
  fun downloadUrlLoadsTheUploadedPicture() = runBlocking {
    val url = timed { owner.getDownloadUrl(upload().getOrThrow()) }.getOrThrow()

    val bytes =
        withContext(Dispatchers.IO) {
          val connection = URL(url).openConnection()
          connection.connectTimeout = TIMEOUT_MS.toInt()
          connection.readTimeout = TIMEOUT_MS.toInt()
          connection.getInputStream().use { it.readBytes() }
        }

    assertArrayEquals(png, bytes)
  }

  @Test
  fun deleteImageRemovesThePictureAndTheFile() = runBlocking {
    val created = upload().getOrThrow()

    timed { owner.deleteImage(fileId) }.getOrThrow()

    assertRefused(
        "link after delete",
        ImageError.NOT_FOUND,
        timed { owner.getDownloadUrl(created) },
    )
  }

  @Test
  fun deleteImageCompletesWhenThePictureIsAlreadyGone() = runBlocking {
    upload().getOrThrow()
    storedPicture().delete().awaitWithTimeout()

    timed { owner.deleteImage(fileId) }.getOrThrow()

    assertNull(storedRecord())
  }

  @Test
  fun refusedUploadsLeaveNothingBehind() = runBlocking {
    val tooLarge = pick(ByteArray(MAX_BYTES.toInt() + 1), "image/jpeg", "jpg")
    val gif = pick("GIF89a".toByteArray(), "image/gif", "gif")

    assertRefused("too large", ImageError.TOO_LARGE, upload(uri = tooLarge))
    assertRefused("GIF", ImageError.UNSUPPORTED_FORMAT, upload(uri = gif))
    assertRefused("blank owner", ImageError.NOT_SIGNED_IN, upload(imageFile().copy(ownerId = "")))
    // The Firestore rules refuse a file in a folder that does not exist: the picture is deleted.
    val missingFolder = imageFile(parentId = "$run-none")
    assertRefused("record refused", ImageError.PERMISSION_DENIED, upload(missingFolder))
  }

  @Test
  fun anotherUserCanNeitherReadNorDeleteTheImage() = runBlocking {
    val created = upload().getOrThrow()
    val other = repositoryAs("$run-other")

    assertEquals(ImageError.PERMISSION_DENIED, errorOf(timed { other.getDownloadUrl(created) }))
    assertEquals(ImageError.PERMISSION_DENIED, errorOf(timed { other.deleteImage(fileId) }))
    assertArrayEquals(png, storedBytes())
    assertEquals(created, storedRecord())
  }

  @Test
  fun missingOrNonImageFilesAreRefused() = runBlocking {
    val text = imageFile().copy(type = DocumentType.TEXT, content = "Notes")
    timed { WorkspaceRepositoryFirestore(firestoreAs(ownerId)).createFile(text) }.getOrThrow()

    assertEquals(ImageError.NOT_FOUND, errorOf(timed { owner.deleteImage("$run-missing") }))
    assertEquals(ImageError.NOT_AN_IMAGE, errorOf(timed { owner.getDownloadUrl(text) }))
    assertEquals(ImageError.NOT_AN_IMAGE, errorOf(timed { owner.deleteImage(fileId) }))
    assertEquals(text, storedRecord())
  }

  private companion object {
    const val MAX_BYTES = 10L * 1024 * 1024
  }
}
