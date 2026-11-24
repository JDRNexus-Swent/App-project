// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.model.image

import android.content.ContentResolver
import android.database.MatrixCursor
import android.net.Uri
import android.provider.OpenableColumns
import com.github.se.jdrnexus.model.personalSpace.DocumentType
import com.github.se.jdrnexus.model.personalSpace.JDRFile
import com.github.se.jdrnexus.model.personalSpace.WorkspaceRepository
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.OnProgressListener
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.StorageReference
import com.google.firebase.storage.UploadTask
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Paths the emulators cannot produce (stalled or slow uploads, failed clean-ups, offline errors),
 * with mocks as TESTING.md section 4.2 allows. The rest is tested on the emulators by
 * `ImageRepositoryFirebaseTest`.
 */
@RunWith(RobolectricTestRunner::class)
class ImageRepositoryFirebaseFailurePathsTest {

  private val image = Uri.parse("content://media/picker/0/42")
  private val file = JDRFile(id = "map", name = "Dungeon map", ownerId = "owner")
  private val picture = mockk<StorageReference>()
  private val upload = mockk<UploadTask>(relaxed = true)
  private val storage =
      mockk<FirebaseStorage> { every { reference.child("images/owner/map") } returns picture }
  private val contentResolver =
      mockk<ContentResolver> {
        every { getType(image) } returns "image/png"
        every { query(image, any(), any(), any(), any()) } returns
            MatrixCursor(arrayOf(OpenableColumns.SIZE)).apply { addRow(arrayOf(100L)) }
      }
  private val workspace = mockk<WorkspaceRepository>()
  private val repository = ImageRepositoryFirebase(storage, contentResolver, workspace)

  init {
    every { picture.putFile(image, any()) } returns upload
  }

  private fun errorOf(result: Result<*>) = (result.exceptionOrNull() as? ImageException)?.error

  private fun storageFailure(httpCode: Int) =
      checkNotNull(StorageException.fromExceptionAndHttpCode(null, httpCode))

  @Test
  fun stalledUploadIsCancelledCleanedUpAndReportsNetworkAfter60Seconds() =
      runTest(timeout = 5.seconds) {
        // Offline, the SDK retries the clean-up for 2 minutes; the repository gives up after 10 s.
        val cleanup = TaskCompletionSource<Void>()
        every { picture.delete() } returns cleanup.task
        backgroundScope.launch {
          delay(120_000)
          cleanup.setResult(null)
        }

        val result = repository.uploadImage(file, image)

        assertEquals(ImageError.NETWORK, errorOf(result))
        assertEquals(70_000L, currentTime)
        verify { upload.cancel() }
        verify { picture.delete() }
      }

  @Test
  fun failedCleanupStillReturnsTheRecordError() = runTest {
    every { upload.isComplete } returns true
    every { upload.exception } returns null
    every { upload.result } returns mockk(relaxed = true)
    val refused =
        FirebaseFirestoreException("refused", FirebaseFirestoreException.Code.PERMISSION_DENIED)
    coEvery { workspace.createFile(any()) } returns Result.failure(refused)
    every { picture.delete() } returns Tasks.forException(IllegalStateException("offline"))

    val result = repository.uploadImage(file, image)

    assertEquals(ImageError.PERMISSION_DENIED, errorOf(result))
    verify { picture.delete() }
  }

  // Progress every 20 seconds restarts the 60-second countdown, so a slow upload keeps going.
  @Test
  fun progressKeepsASlowUploadRunningPast60Seconds() =
      runTest(timeout = 5.seconds) {
        val listener = slot<OnProgressListener<UploadTask.TaskSnapshot>>()
        every { upload.addOnProgressListener(capture(listener)) } returns upload
        every { picture.delete() } returns Tasks.forResult(null)
        val snapshot = mockk<UploadTask.TaskSnapshot>(relaxed = true)
        val pending = async { repository.uploadImage(file, image) }

        repeat(4) {
          advanceTimeBy(20_000)
          listener.captured.onProgress(snapshot)
        }
        runCurrent()

        assertTrue(pending.isActive)
        verify(exactly = 0) { upload.cancel() }
        pending.cancel()
      }

  @Test
  fun offlineAndUnexpectedFailuresBecomeNetworkOrUnknown() = runTest {
    val stored = file.copy(type = DocumentType.IMAGE, content = "images/owner/map")
    every { picture.downloadUrl } returnsMany
        listOf(
            Tasks.forException(storageFailure(-2)),
            Tasks.forException(storageFailure(500)),
            Tasks.forException(IllegalStateException("unexpected")),
        )
    coEvery { workspace.getFile("map") } returnsMany
        listOf(
            Result.failure(
                FirebaseFirestoreException("offline", FirebaseFirestoreException.Code.UNAVAILABLE)
            ),
            Result.success(stored),
        )
    every { picture.delete() } returnsMany
        listOf(Tasks.forException(storageFailure(403)), Tasks.forResult(null))
    coEvery { workspace.deleteFile("map") } returns
        Result.failure(
            FirebaseFirestoreException("aborted", FirebaseFirestoreException.Code.ABORTED)
        )

    assertEquals(ImageError.NETWORK, errorOf(repository.getDownloadUrl(stored)))
    assertEquals(ImageError.UNKNOWN, errorOf(repository.getDownloadUrl(stored)))
    assertEquals(ImageError.UNKNOWN, errorOf(repository.getDownloadUrl(stored)))
    assertEquals(ImageError.NETWORK, errorOf(repository.deleteImage("map")))
    // A refused picture delete keeps the record; a record that fails after the picture is gone
    // reports its own error.
    assertEquals(ImageError.PERMISSION_DENIED, errorOf(repository.deleteImage("map")))
    assertEquals(ImageError.UNKNOWN, errorOf(repository.deleteImage("map")))
    coVerify(exactly = 1) { workspace.deleteFile("map") }
  }
}
