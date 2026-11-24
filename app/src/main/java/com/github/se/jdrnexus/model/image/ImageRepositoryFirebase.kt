// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.model.image

import android.content.ContentResolver
import android.net.Uri
import com.github.se.jdrnexus.model.personalSpace.JDRFile
import com.github.se.jdrnexus.model.personalSpace.WorkspaceRepository
import com.google.firebase.storage.FirebaseStorage

// TODO(#289) stub for the failing-test commit.
class ImageRepositoryFirebase(
    private val storage: FirebaseStorage,
    private val contentResolver: ContentResolver,
    private val workspaceRepository: WorkspaceRepository,
) : ImageRepository {

  override suspend fun uploadImage(
      file: JDRFile,
      image: Uri,
      onProgress: (Float) -> Unit,
  ): Result<JDRFile> = Result.failure(ImageException(ImageError.UNKNOWN))

  override suspend fun getDownloadUrl(file: JDRFile): Result<String> =
      Result.failure(ImageException(ImageError.UNKNOWN))

  override suspend fun deleteImage(fileId: String): Result<Unit> =
      Result.failure(ImageException(ImageError.UNKNOWN))
}
