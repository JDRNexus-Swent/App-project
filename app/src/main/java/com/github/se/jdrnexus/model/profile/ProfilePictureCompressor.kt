// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.model.profile

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Turns a picked image into a small JPEG profile picture: at most [MAX_SIZE_PX] pixels on its
 * longer side and never enlarged, so uploads and downloads stay far below the free Storage quota
 * (DATA_ARCHITECTURE.md, section 1.8).
 */
class ProfilePictureCompressor(private val contentResolver: ContentResolver) {

  /** Reads, shrinks and compresses [image] off the main thread. Throws if it is not an image. */
  suspend fun compress(image: Uri): ByteArray =
      withContext(Dispatchers.IO) {
        val source = ImageDecoder.createSource(contentResolver, image)
        // The decoder shrinks while reading, so a large photo is never fully loaded in memory. It
        // also applies the photo's rotation (EXIF).
        val bitmap =
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
              val scale = min(1f, MAX_SIZE_PX.toFloat() / max(info.size.width, info.size.height))
              decoder.setTargetSize(
                  (info.size.width * scale).roundToInt().coerceAtLeast(1),
                  (info.size.height * scale).roundToInt().coerceAtLeast(1),
              )
              // A software bitmap, so it can be compressed.
              decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        ByteArrayOutputStream().use { output ->
          bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
          output.toByteArray()
        }
      }

  companion object {
    const val MAX_SIZE_PX = 256
    const val JPEG_QUALITY = 80
  }
}
