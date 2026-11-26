// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus

import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.se.jdrnexus.model.image.ImageError
import com.github.se.jdrnexus.model.personalSpace.DocumentType
import com.github.se.jdrnexus.model.personalSpace.JDRFile
import com.github.se.jdrnexus.model.repository.AuthRepository
import com.github.se.jdrnexus.model.repository.AuthUser
import com.github.se.jdrnexus.resources.C
import com.github.se.jdrnexus.ui.theme.SampleAppTheme
import com.github.se.jdrnexus.utils.FakeImagePicker
import com.github.se.jdrnexus.utils.FakeImageRepository
import com.github.se.jdrnexus.utils.NavigationWorkspaceRepository
import com.github.se.jdrnexus.viewmodel.PersonalSpaceViewModel
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Tests of adding an image from the "+" menu of [PersonalSpaceScreen], and of image items. */
@RunWith(AndroidJUnit4::class)
class PersonalSpaceImageUploadTest {
  @get:Rule val composeTestRule = createComposeRule()

  private val picture = Uri.parse("content://media/picker/0/42")
  private val picker = FakeImagePicker(picked = picture)

  @Test
  fun newImageOpensThePickerAndUploadsThePickedPictureToTheRoot() {
    val images = FakeImageRepository()
    showScreen(images)

    chooseNewImage()

    assertEquals(1, picker.requests.size)
    val (file, image) = images.uploads.single()
    assertEquals(picture, image)
    assertEquals("Image", file.name)
    assertEquals(USER_ID, file.ownerId)
    assertEquals("", file.personalParentId)
  }

  @Test
  fun runningUploadShowsItsProgressAndDisablesNewImage() {
    showScreen(FakeImageRepository(progressSteps = listOf(0.5f), gate = CompletableDeferred()))

    chooseNewImage()

    composeTestRule.onNodeWithText("Uploading image… 50%").assertIsDisplayed()
    composeTestRule.onNodeWithContentDescription("Add item").performClick()
    composeTestRule.onNodeWithTag(C.Tag.personal_new_image).assertIsNotEnabled()
  }

  @Test
  fun uploadFailureIsShownInASnackbar() {
    showScreen(FakeImageRepository(failure = ImageError.UNSUPPORTED_FORMAT))

    chooseNewImage()

    composeTestRule
        .onNodeWithText("Only JPEG, PNG and WebP images can be added.")
        .assertIsDisplayed()
  }

  @Test
  fun imageItemShowsItsOwnLabelAndNotItsStoragePath() {
    val image =
        JDRFile(
            id = "image-1",
            name = "Kingdom map",
            ownerId = USER_ID,
            type = DocumentType.IMAGE,
            content = "images/$USER_ID/image-1",
        )
    showScreen(FakeImageRepository(), listOf(image))

    composeTestRule.onNodeWithText("Kingdom map").assertIsDisplayed()
    composeTestRule.onNodeWithText("IMAGE").assertIsDisplayed()
    composeTestRule.onNodeWithContentDescription("Image").assertIsDisplayed()
    composeTestRule.onNodeWithText(image.content).assertDoesNotExist()
  }

  private fun showScreen(images: FakeImageRepository, files: List<JDRFile> = emptyList()) {
    val auth = mockk<AuthRepository>()
    every { auth.authState } returns flowOf(AuthUser(USER_ID, "player@example.com", "Player"))
    val viewModel = PersonalSpaceViewModel(NavigationWorkspaceRepository(files), auth, images)
    composeTestRule.setContent {
      SampleAppTheme {
        picker.Provide {
          PersonalSpaceScreen(
              onBackButton = {},
              personalSpaceViewModel = viewModel,
              onDocumentClicked = {},
              onCreateFile = {},
              onTabSelected = {},
          )
        }
      }
    }
    composeTestRule.waitForIdle()
  }

  private fun chooseNewImage() {
    composeTestRule.onNodeWithContentDescription("Add item").performClick()
    composeTestRule.onNodeWithTag(C.Tag.personal_new_image).performClick()
    composeTestRule.waitForIdle()
  }

  private companion object {
    const val USER_ID = "user-1"
  }
}
