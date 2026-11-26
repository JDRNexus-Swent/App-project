// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.model.image

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import com.github.se.jdrnexus.model.personalSpace.DocumentType
import com.github.se.jdrnexus.model.personalSpace.JDRFile
import com.github.se.jdrnexus.model.personalSpace.WorkspaceRepository
import com.github.se.jdrnexus.model.personalSpace.safeFirestoreRequest
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.StorageMetadata
import com.google.firebase.storage.StorageReference
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * [ImageRepository] on Firebase Storage. The image file's record is written, read and deleted
 * through [workspaceRepository]; the picked picture's type and size are read through
 * [contentResolver].
 */
class ImageRepositoryFirebase(
    private val storage: FirebaseStorage,
    private val contentResolver: ContentResolver,
    private val workspaceRepository: WorkspaceRepository,
) : ImageRepository {

  override suspend fun uploadImage(
      file: JDRFile,
      image: Uri,
      onProgress: (Float) -> Unit,
  ): Result<JDRFile> = imageRequest {
    if (file.ownerId.isBlank()) throw ImageException(ImageError.NOT_SIGNED_IN)
    val type = contentResolver.getType(image)
    if (type !in IMAGE_TYPES) throw ImageException(ImageError.UNSUPPORTED_FORMAT)
    // An unknown size (-1) is left to the Storage rule, which also refuses over 10 MiB.
    if (sizeOf(image) > MAX_BYTES) throw ImageException(ImageError.TOO_LARGE)

    val path = "images/${file.ownerId}/${file.id}"
    val picture = storage.reference.child(path)
    val imageFile = file.copy(type = DocumentType.IMAGE, content = path)
    var mayHaveStoredPicture = true
    try {
      try {
        putPicture(picture, image, checkNotNull(type), onProgress)
      } catch (e: StorageException) {
        // A refusal by the Storage rules means this upload stored nothing: a picture already at
        // this path belongs to an earlier upload with the same id and must stay. Other failures
        // (a lost connection) may come after the last chunk was saved, so they still clean up.
        if (e.errorCode == StorageException.ERROR_NOT_AUTHORIZED) mayHaveStoredPicture = false
        throw e
      }
      workspaceRepository.createFile(imageFile).getOrThrow()
    } catch (e: Exception) {
      // After a stall, a cancellation or a refused record, the picture this call may have stored
      // must not stay without its record. A failed clean-up is ignored so that the original error
      // is the one reported, and it is cut short offline, where the SDK would retry for 2 minutes
      // before the error reaches the user.
      if (mayHaveStoredPicture) {
        withContext(NonCancellable) {
          withTimeoutOrNull(CLEANUP_LIMIT_MS) { safeFirestoreRequest { picture.delete().await() } }
        }
      }
      throw e
    }
    imageFile
  }

  override suspend fun getDownloadUrl(file: JDRFile): Result<String> = imageRequest {
    requireImage(file)
    storage.reference.child(file.content).downloadUrl.await().toString()
  }

  override suspend fun deleteImage(fileId: String): Result<Unit> = imageRequest {
    val file =
        workspaceRepository.getFile(fileId).getOrThrow()
            ?: throw ImageException(ImageError.NOT_FOUND)
    requireImage(file)
    try {
      storage.reference.child(file.content).delete().await()
    } catch (e: StorageException) {
      // Already gone counts as deleted, so calling again completes a half-finished delete.
      if (e.errorCode != StorageException.ERROR_OBJECT_NOT_FOUND) throw e
    }
    workspaceRepository.deleteFile(fileId).getOrThrow()
  }

  private fun requireImage(file: JDRFile) {
    if (file.type != DocumentType.IMAGE) throw ImageException(ImageError.NOT_AN_IMAGE)
  }

  private fun sizeOf(image: Uri): Long =
      contentResolver.query(image, arrayOf(OpenableColumns.SIZE), null, null, null)?.use {
        if (it.moveToFirst() && !it.isNull(0)) it.getLong(0) else null
      } ?: -1

  /**
   * Uploads [image] to [picture], reporting progress. When no progress arrives for
   * [STALL_LIMIT_MS], cancels the upload and fails with [ImageError.NETWORK]. The SDK reports
   * progress once per chunk, so a large chunk on a connection that suddenly slows down can trip the
   * limit too.
   */
  private suspend fun putPicture(
      picture: StorageReference,
      image: Uri,
      type: String,
      onProgress: (Float) -> Unit,
  ) = coroutineScope {
    val metadata =
        StorageMetadata.Builder().setContentType(type).setCacheControl(CACHE_CONTROL).build()
    val upload = picture.putFile(image, metadata)
    val progress = Channel<Unit>(Channel.CONFLATED)
    upload.addOnProgressListener { snapshot ->
      progress.trySend(Unit)
      if (snapshot.totalByteCount > 0) {
        onProgress(snapshot.bytesTransferred.toFloat() / snapshot.totalByteCount)
      }
    }
    val stalled = AtomicBoolean(false)
    val done = async { upload.await() }
    val watchdog = launch {
      // Each progress event restarts the countdown.
      while (withTimeoutOrNull(STALL_LIMIT_MS) { progress.receive() } != null) Unit
      stalled.set(true)
      done.cancel()
    }
    try {
      done.await()
      // The upload may finish before the listener is attached.
      onProgress(1f)
    } catch (e: CancellationException) {
      // Awaiting does not stop the SDK's upload: cancel it explicitly.
      upload.cancel()
      if (stalled.get()) throw ImageException(ImageError.NETWORK)
      throw e
    } finally {
      watchdog.cancel()
    }
  }

  // Runs [action]; a failure becomes an ImageException chosen from its error code, never its text.
  private suspend fun <T> imageRequest(action: suspend () -> T): Result<T> =
      safeFirestoreRequest(action)
          .fold({ Result.success(it) }, { Result.failure(it.toImageException()) })

  private fun Throwable.toImageException(): ImageException =
      when (this) {
        is ImageException -> this
        is StorageException -> ImageException(storageError(errorCode), this)
        is FirebaseFirestoreException -> ImageException(firestoreError(code), this)
        else -> ImageException(ImageError.UNKNOWN, this)
      }

  private fun storageError(code: Int): ImageError =
      when (code) {
        StorageException.ERROR_OBJECT_NOT_FOUND -> ImageError.NOT_FOUND
        StorageException.ERROR_NOT_AUTHORIZED -> ImageError.PERMISSION_DENIED
        StorageException.ERROR_RETRY_LIMIT_EXCEEDED -> ImageError.NETWORK
        else -> ImageError.UNKNOWN
      }

  private fun firestoreError(code: FirebaseFirestoreException.Code): ImageError =
      when (code) {
        FirebaseFirestoreException.Code.PERMISSION_DENIED -> ImageError.PERMISSION_DENIED
        FirebaseFirestoreException.Code.UNAVAILABLE -> ImageError.NETWORK
        else -> ImageError.UNKNOWN
      }

  private companion object {
    val IMAGE_TYPES = setOf("image/jpeg", "image/png", "image/webp")
    // The same limit as the Storage rule (<=).
    const val MAX_BYTES = 10L * 1024 * 1024
    // Progress comes once per chunk, and a 4 MiB chunk takes about 34 seconds at 1 Mbit/s.
    const val STALL_LIMIT_MS = 60_000L
    const val CLEANUP_LIMIT_MS = 10_000L
    // A picture's path never changes, so phones may cache it for 30 days.
    const val CACHE_CONTROL = "public, max-age=2592000"
  }
}
