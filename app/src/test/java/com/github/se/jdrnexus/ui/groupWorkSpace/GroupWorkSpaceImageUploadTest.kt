// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.ui.groupWorkSpace

import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.se.jdrnexus.model.image.ImageError
import com.github.se.jdrnexus.model.personalSpace.CampaignGroup
import com.github.se.jdrnexus.model.personalSpace.DocumentType
import com.github.se.jdrnexus.model.repository.AuthRepository
import com.github.se.jdrnexus.model.repository.AuthUser
import com.github.se.jdrnexus.resources.C
import com.github.se.jdrnexus.ui.theme.SampleAppTheme
import com.github.se.jdrnexus.utils.FakeImagePicker
import com.github.se.jdrnexus.utils.FakeImageRepository
import com.github.se.jdrnexus.utils.NavigationGroupRepository
import com.github.se.jdrnexus.utils.NavigationWorkspaceRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Tests of adding an image from the item-creation dialog of [GroupWorkSpaceScreen]. */
@RunWith(AndroidJUnit4::class)
class GroupWorkSpaceImageUploadTest {
  @get:Rule val composeTestRule = createComposeRule()

  private val picture = Uri.parse("content://media/picker/0/42")
  private val picker = FakeImagePicker(picked = picture)

  @Test
  fun imageOptionOpensThePickerAndUploadsThePickedPictureAsAnImage() {
    val images = FakeImageRepository()
    showScreen(images)

    chooseImageOption()

    assertEquals(1, picker.requests.size)
    val (file, image) = images.uploads.single()
    assertEquals(picture, image)
    assertEquals("Image", file.name)
    assertEquals(DocumentType.IMAGE, file.type)
    assertEquals(GROUP_ID, file.placements.getValue(GROUP_ID).folderId)
  }

  @Test
  fun closingThePickerUploadsNothing() {
    picker.picked = null
    val images = FakeImageRepository()
    showScreen(images)

    chooseImageOption()

    assertEquals(1, picker.requests.size)
    assertEquals(0, images.uploads.size)
  }

  @Test
  fun runningUploadShowsItsProgressAndDisablesTheImageOption() {
    val images = FakeImageRepository(progressSteps = listOf(0.5f), gate = CompletableDeferred())
    showScreen(images)

    chooseImageOption()

    composeTestRule.onNodeWithText("Uploading image… 50%").assertIsDisplayed()
    openCreateItemDialog()
    composeTestRule.onNodeWithTag(C.Tag.workspace_create_item_image).assertIsNotEnabled()
  }

  @Test
  fun uploadFailureIsShownInASnackbar() {
    showScreen(FakeImageRepository(failure = ImageError.TOO_LARGE))

    chooseImageOption()

    composeTestRule.onNodeWithText("This image is over 10 MB.").assertIsDisplayed()
  }

  @Test
  fun failureWhileADialogIsOpenIsShownOnceItCloses() {
    val gate = CompletableDeferred<Unit>()
    val images = FakeImageRepository(failure = ImageError.NETWORK, gate = gate)
    showScreen(images)
    chooseImageOption()
    openCreateItemDialog()

    gate.complete(Unit)
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText(NETWORK_MESSAGE).assertDoesNotExist()

    composeTestRule.onNodeWithTag(C.Tag.workspace_create_item_cancel).performClick()
    composeTestRule.onNodeWithText(NETWORK_MESSAGE).assertIsDisplayed()
  }

  private fun showScreen(images: FakeImageRepository) {
    val auth = mockk<AuthRepository>()
    every { auth.authState } returns flowOf(AuthUser(USER_ID, "player@example.com", "Player"))
    val viewModel =
        GroupWorkspaceViewModel(
            groupId = GROUP_ID,
            groupRepository =
                NavigationGroupRepository(
                    CampaignGroup(
                        id = GROUP_ID,
                        gameMasterId = "game-master",
                        participantIds = listOf(USER_ID),
                    )
                ),
            repository = NavigationWorkspaceRepository(),
            authRepository = auth,
            imageRepository = images,
        )
    composeTestRule.setContent {
      SampleAppTheme {
        picker.Provide {
          GroupWorkSpaceScreen(gwViewModel = viewModel, groupId = GROUP_ID, groupName = "Crown")
        }
      }
    }
    composeTestRule.waitForIdle()
  }

  private fun openCreateItemDialog() {
    composeTestRule.onNodeWithTag(C.Tag.workspace_actions_fab).performClick()
    composeTestRule.onNodeWithTag(C.Tag.workspace_create_item).performClick()
    composeTestRule.waitForIdle()
  }

  private fun chooseImageOption() {
    openCreateItemDialog()
    composeTestRule.onNodeWithTag(C.Tag.workspace_create_item_image).performClick()
    composeTestRule.waitForIdle()
  }

  private companion object {
    const val GROUP_ID = "group-1"
    const val USER_ID = "user-1"
    const val NETWORK_MESSAGE = "The upload failed: check your connection and try again."
  }
}
