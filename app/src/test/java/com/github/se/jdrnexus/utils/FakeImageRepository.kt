// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.utils

import android.net.Uri
import com.github.se.jdrnexus.model.image.ImageError
import com.github.se.jdrnexus.model.image.ImageException
import com.github.se.jdrnexus.model.image.ImageRepository
import com.github.se.jdrnexus.model.personalSpace.DocumentType
import com.github.se.jdrnexus.model.personalSpace.JDRFile
import com.github.se.jdrnexus.model.personalSpace.WorkspaceRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred

/**
 * In-memory [ImageRepository] for ViewModel and screen tests. Like `ImageRepositoryFirebase`, an
 * upload fails with [ImageError.NOT_SIGNED_IN] for a file without owner and otherwise returns the
 * file as an IMAGE whose content is `images/<ownerId>/<fileId>`, created through
 * [workspaceRepository] when one is given.
 *
 * @param failure When set, every upload fails with this error after reporting its progress.
 * @param progressSteps Fractions reported to `onProgress` before the upload waits on [gate].
 * @param gate When set, an upload suspends on it after its progress, until it is completed.
 * @param workspaceRepository Where a successful upload creates its image file, like the real one.
 */
class FakeImageRepository(
    var failure: ImageError? = null,
    var progressSteps: List<Float> = emptyList(),
    var gate: CompletableDeferred<Unit>? = null,
    private val workspaceRepository: WorkspaceRepository? = null,
) : ImageRepository {
  /** Every upload request, in call order: the file and the picture passed. */
  val uploads = mutableListOf<Pair<JDRFile, Uri>>()

  /** Whether an upload was cancelled while it waited on [gate]. */
  var uploadCancelled = false
    private set

  override suspend fun uploadImage(
      file: JDRFile,
      image: Uri,
      onProgress: (Float) -> Unit,
  ): Result<JDRFile> {
    uploads += file to image
    if (file.ownerId.isBlank()) return Result.failure(ImageException(ImageError.NOT_SIGNED_IN))
    progressSteps.forEach(onProgress)
    try {
      gate?.await()
    } catch (e: CancellationException) {
      uploadCancelled = true
      throw e
    }
    failure?.let {
      return Result.failure(ImageException(it))
    }
    val imageFile =
        file.copy(type = DocumentType.IMAGE, content = "images/${file.ownerId}/${file.id}")
    workspaceRepository?.createFile(imageFile)?.onFailure {
      return Result.failure(ImageException(ImageError.UNKNOWN, it))
    }
    return Result.success(imageFile)
  }

  override suspend fun getDownloadUrl(file: JDRFile): Result<String> = error("Not used")

  override suspend fun deleteImage(fileId: String): Result<Unit> = error("Not used")
}
