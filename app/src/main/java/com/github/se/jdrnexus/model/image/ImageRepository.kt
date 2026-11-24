// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.model.image

import android.net.Uri
import com.github.se.jdrnexus.model.personalSpace.JDRFile

/**
 * Stores the pictures of image files: the picture in Firebase Storage at
 * `images/<ownerId>/<fileId>`, and a [JDRFile] of type IMAGE whose `content` is that path
 * (DATA_ARCHITECTURE.md, section 1.9). Failures are a [Result.failure] holding an [ImageException];
 * cancellation is rethrown.
 */
interface ImageRepository {

  /**
   * Uploads the picture at [image] (a JPEG, PNG or WebP of at most 10 MiB) and creates its image
   * file from [file]. Fails with [ImageError.NETWORK] after 60 seconds without progress. On any
   * failure the uploaded picture is deleted again; if that delete gets no answer within 10 seconds,
   * the picture stays in Storage, readable only by its owner.
   *
   * @param onProgress receives the fraction uploaded, from 0 to 1.
   */
  suspend fun uploadImage(
      file: JDRFile,
      image: Uri,
      onProgress: (Float) -> Unit = {},
  ): Result<JDRFile>

  /** Returns a download link to the picture of [file], for its owner and its groups' members. */
  suspend fun getDownloadUrl(file: JDRFile): Result<String>

  /**
   * Deletes the picture of the image file [fileId], then its record. Use it instead of
   * `WorkspaceRepository.deleteFile`, which would leave the picture in Storage.
   */
  suspend fun deleteImage(fileId: String): Result<Unit>
}

/** Why an image operation failed. */
enum class ImageError {
  NOT_SIGNED_IN,
  TOO_LARGE,
  UNSUPPORTED_FORMAT,
  NOT_AN_IMAGE,
  NOT_FOUND,
  PERMISSION_DENIED,
  NETWORK,
  UNKNOWN,
}

/** Failure of an [ImageRepository] operation. */
class ImageException(val error: ImageError, cause: Throwable? = null) : Exception(error.name, cause)
