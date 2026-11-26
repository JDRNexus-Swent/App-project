// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.resources

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.runtime.Composable

/**
 * Remembers the Android photo picker, limited to images. Calling the returned function opens the
 * picker; [onPicked] receives the chosen picture, and nothing happens when the user closes the
 * picker without choosing one.
 */
@Composable
fun rememberImagePicker(onPicked: (Uri) -> Unit): () -> Unit {
  val launcher = rememberLauncherForActivityResult(PickVisualMedia()) { uri -> uri?.let(onPicked) }
  return { launcher.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly)) }
}

/**
 * Returns the display name of the picture at [image] without its extension (`"map.png"` gives
 * `"map"`), or [fallback] when [contentResolver] does not know a non-blank name for it.
 */
fun pickedImageName(contentResolver: ContentResolver, image: Uri, fallback: String): String {
  val displayName =
      try {
        contentResolver.query(image, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
          if (it.moveToFirst() && !it.isNull(0)) it.getString(0) else null
        }
      } catch (_: Exception) {
        // A provider that refuses the query only loses the name: the upload still checks the file.
        null
      }
  val name = displayName?.substringBeforeLast('.')?.trim()
  return if (name.isNullOrEmpty()) fallback else name
}
