// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.ui.image

import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.se.jdrnexus.R
import com.github.se.jdrnexus.model.image.ImageError
import com.github.se.jdrnexus.model.image.ImageException
import com.github.se.jdrnexus.resources.C
import com.github.se.jdrnexus.resources.pickedImageName
import com.github.se.jdrnexus.resources.rememberImagePicker
import kotlin.math.roundToInt

/**
 * Upload of a picked image, as shown by the personal space and the group workspace.
 *
 * @property progress Fraction uploaded, from 0 to 1, while an upload runs; null when none runs.
 * @property error Why the last upload failed, kept until the screen has shown it.
 */
data class ImageUploadState(val progress: Float? = null, val error: ImageError? = null) {
  /** Whether an upload is running; a new one is refused until it ends. */
  val isUploading: Boolean
    get() = progress != null
}

/** The [ImageError] of a failed `ImageRepository` call, or [ImageError.UNKNOWN] for any other. */
fun Throwable.toImageError(): ImageError = (this as? ImageException)?.error ?: ImageError.UNKNOWN

/** Text shown to the user when an image upload fails with this error. */
@get:StringRes
val ImageError.messageRes: Int
  get() =
      when (this) {
        ImageError.TOO_LARGE -> R.string.image_error_too_large
        ImageError.UNSUPPORTED_FORMAT -> R.string.image_error_format
        ImageError.NETWORK -> R.string.image_error_network
        ImageError.NOT_SIGNED_IN -> R.string.image_error_not_signed_in
        ImageError.PERMISSION_DENIED -> R.string.image_error_permission
        // An upload never reads an existing file, so these only come from an unexpected state.
        ImageError.NOT_AN_IMAGE,
        ImageError.NOT_FOUND,
        ImageError.UNKNOWN -> R.string.image_error_unknown
      }

/**
 * Remembers the photo picker used to add an image file. [onPicked] receives the chosen picture and
 * the name of its file, which is its display name without extension, or "Image" when unknown.
 */
@Composable
fun rememberImageFilePicker(onPicked: (image: Uri, name: String) -> Unit): () -> Unit {
  val contentResolver = LocalContext.current.contentResolver
  val defaultName = stringResource(R.string.image_default_name)
  return rememberImagePicker { image ->
    onPicked(image, pickedImageName(contentResolver, image, defaultName))
  }
}

/** Shows the [progress] (from 0 to 1) of a running image upload as a bar and a percentage. */
@Composable
fun ImageUploadProgress(progress: Float, modifier: Modifier = Modifier) {
  Column(
      modifier = modifier.fillMaxWidth().testTag(C.Tag.image_upload_progress),
      verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    Text(
        text = stringResource(R.string.image_uploading, (progress * 100).roundToInt()),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
  }
}

/**
 * Shows the message of [error] in [snackbarHostState] once, then calls [onShown] so the ViewModel
 * forgets the error. Nothing happens while [error] is null. While [enabled] is false (a dialog is
 * open and would hide the snackbar), the error waits and is shown once [enabled] is true again.
 */
@Composable
fun ImageUploadErrorEffect(
    error: ImageError?,
    snackbarHostState: SnackbarHostState,
    enabled: Boolean = true,
    onShown: () -> Unit,
) {
  val message = error?.let { stringResource(it.messageRes) }
  val currentOnShown by rememberUpdatedState(onShown)
  LaunchedEffect(error, enabled) {
    if (message != null && enabled) {
      // Cleared only after the snackbar: clearing changes the key, which would cancel it.
      snackbarHostState.showSnackbar(message)
      currentOnShown()
    }
  }
}
