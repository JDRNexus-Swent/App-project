// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.ui.image

import android.content.Context
import android.net.Uri
import androidx.compose.material3.Button
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.se.jdrnexus.R
import com.github.se.jdrnexus.model.image.ImageError
import com.github.se.jdrnexus.model.image.ImageException
import com.github.se.jdrnexus.resources.C
import com.github.se.jdrnexus.utils.FakeImagePicker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Tests of the shared image-upload pieces in `ImageUpload.kt`. */
@RunWith(AndroidJUnit4::class)
class ImageUploadTest {
  @get:Rule val composeTestRule = createComposeRule()

  private val context = ApplicationProvider.getApplicationContext<Context>()

  @Test
  fun stateIsUploadingOnlyWhileItHasAProgress() {
    assertFalse(ImageUploadState().isUploading)
    assertFalse(ImageUploadState(error = ImageError.NETWORK).isUploading)
    assertTrue(ImageUploadState(progress = 0f).isUploading)
  }

  @Test
  fun imageExceptionKeepsItsErrorAndAnyOtherFailureIsUnknown() {
    assertEquals(ImageError.TOO_LARGE, ImageException(ImageError.TOO_LARGE).toImageError())
    assertEquals(ImageError.UNKNOWN, IllegalStateException("boom").toImageError())
  }

  @Test
  fun eachErrorHasItsMessage() {
    val expected =
        mapOf(
            ImageError.TOO_LARGE to "This image is over 10 MB.",
            ImageError.UNSUPPORTED_FORMAT to "Only JPEG, PNG and WebP images can be added.",
            ImageError.NETWORK to "The upload failed: check your connection and try again.",
            ImageError.NOT_SIGNED_IN to "You must be signed in to add an image.",
            ImageError.PERMISSION_DENIED to "You are not allowed to add an image here.",
            ImageError.NOT_AN_IMAGE to "The image could not be added.",
            ImageError.NOT_FOUND to "The image could not be added.",
            ImageError.UNKNOWN to "The image could not be added.",
        )

    assertEquals(expected, ImageError.entries.associateWith { context.getString(it.messageRes) })
  }

  @Test
  fun progressShowsItsPercentage() {
    composeTestRule.setContent { ImageUploadProgress(progress = 0.426f) }

    composeTestRule.onNodeWithTag(C.Tag.image_upload_progress).assertIsDisplayed()
    composeTestRule.onNodeWithText("Uploading image… 43%").assertIsDisplayed()
  }

  @Test
  fun errorIsShownInTheSnackbarThenReportedAsShown() {
    var error by mutableStateOf<ImageError?>(null)
    var shown = 0
    composeTestRule.setContent {
      val snackbarHostState = remember { SnackbarHostState() }
      ImageUploadErrorEffect(error, snackbarHostState) {
        shown++
        error = null
      }
      SnackbarHost(snackbarHostState)
    }
    composeTestRule.waitForIdle()
    assertEquals(0, shown)

    error = ImageError.UNSUPPORTED_FORMAT
    composeTestRule
        .onNodeWithText("Only JPEG, PNG and WebP images can be added.")
        .assertIsDisplayed()
    composeTestRule.mainClock.advanceTimeBy(SNACKBAR_SHORT_MS)
    composeTestRule.waitForIdle()

    assertEquals(1, shown)
  }

  @Test
  fun errorWaitsWhileDisabledAndIsShownOnceEnabled() {
    var enabled by mutableStateOf(false)
    var shown = 0
    composeTestRule.setContent {
      val snackbarHostState = remember { SnackbarHostState() }
      ImageUploadErrorEffect(ImageError.NETWORK, snackbarHostState, enabled) { shown++ }
      SnackbarHost(snackbarHostState)
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText(NETWORK_MESSAGE).assertDoesNotExist()

    enabled = true

    composeTestRule.onNodeWithText(NETWORK_MESSAGE).assertIsDisplayed()
    composeTestRule.mainClock.advanceTimeBy(SNACKBAR_SHORT_MS)
    composeTestRule.waitForIdle()
    assertEquals(1, shown)
  }

  @Test
  fun filePickerNamesAPictureWithoutKnownNameImage() {
    val picture = Uri.parse("content://media/picker/0/42")
    val picker = FakeImagePicker(picked = picture)
    val picked = mutableListOf<Pair<Uri, String>>()
    composeTestRule.setContent {
      picker.Provide {
        val pick = rememberImageFilePicker { image, name -> picked += image to name }
        Button(onClick = pick, modifier = Modifier.testTag(PICK)) { Text("Pick") }
      }
    }

    composeTestRule.onNodeWithTag(PICK).performClick()
    composeTestRule.waitForIdle()

    assertEquals(listOf(picture to context.getString(R.string.image_default_name)), picked)
  }

  private companion object {
    const val PICK = "pick"
    const val NETWORK_MESSAGE = "The upload failed: check your connection and try again."
    // SnackbarDuration.Short lasts 4 seconds.
    const val SNACKBAR_SHORT_MS = 4_500L
  }
}
