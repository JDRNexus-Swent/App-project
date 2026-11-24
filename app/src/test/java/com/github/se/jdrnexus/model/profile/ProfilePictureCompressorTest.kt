// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.model.profile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

// Native graphics: Robolectric then really decodes and encodes images.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
class ProfilePictureCompressorTest {

  private val context = ApplicationProvider.getApplicationContext<Context>()
  private val compressor = ProfilePictureCompressor(context.contentResolver)

  // Writes a [width] x [height] PNG to the cache directory and returns its uri.
  private fun imageUri(width: Int, height: Int): Uri {
    val file = File(context.cacheDir, "test-image-${width}x$height.png")
    file.outputStream().use {
      Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
          .compress(Bitmap.CompressFormat.PNG, 100, it)
    }
    return Uri.fromFile(file)
  }

  // Width and height of the JPEG in [bytes], or -1 each if they are not a JPEG.
  private fun jpegSize(bytes: ByteArray): Pair<Int, Int> {
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    return if (options.outMimeType == "image/jpeg") options.outWidth to options.outHeight
    else -1 to -1
  }

  @Test
  fun largeImageIsShrunkToJpegKeepingItsProportions() = runBlocking {
    val picture = compressor.compress(imageUri(width = 1024, height = 512))

    assertEquals(256 to 128, jpegSize(picture))
  }

  @Test
  fun smallImageIsNotEnlarged() = runBlocking {
    val picture = compressor.compress(imageUri(width = 100, height = 50))

    assertEquals(100 to 50, jpegSize(picture))
  }
}
