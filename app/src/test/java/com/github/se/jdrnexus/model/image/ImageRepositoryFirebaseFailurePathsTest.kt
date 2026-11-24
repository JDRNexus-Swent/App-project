// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.model.image

import android.content.ContentResolver
import android.database.MatrixCursor
import android.net.Uri
import android.provider.OpenableColumns
import com.github.se.jdrnexus.model.personalSpace.JDRFile
import com.github.se.jdrnexus.model.personalSpace.WorkspaceRepository
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import com.google.firebase.storage.UploadTask
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Failures the emulators cannot produce (a stalled upload, a failed clean-up), with mocks as
 * TESTING.md section 4.2 allows. The rest is tested on the emulators by
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

  @Test
  fun stalledUploadIsCancelledCleanedUpAndReportsNetworkAfter30Seconds() =
      runTest(timeout = 5.seconds) {
        every { picture.delete() } returns Tasks.forResult(null)

        val result = repository.uploadImage(file, image)

        assertEquals(ImageError.NETWORK, errorOf(result))
        assertEquals(30_000L, currentTime)
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
}
