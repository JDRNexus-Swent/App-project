// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.ui.profile

// Co-authored-by: Codex <codex@openai.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties.EditableText
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasAnySibling
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.AnnotatedString
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.se.jdrnexus.model.profile.UserProfile
import com.github.se.jdrnexus.resources.C
import com.github.se.jdrnexus.ui.theme.SampleAppTheme
import com.github.se.jdrnexus.utils.LOADED_PROFILE
import com.github.se.jdrnexus.utils.LOADED_PROFILE_STATE
import com.github.se.jdrnexus.utils.RecordingPickerOwner
import com.github.se.jdrnexus.utils.RecordingProfileViewModel
import com.github.se.jdrnexus.viewmodel.ProfileUiState
import com.github.se.jdrnexus.viewmodel.ProfileViewModelInterface
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProfileScreenTest {
  @get:Rule val composeTestRule = createComposeRule()

  private val profile = LOADED_PROFILE
  private val loadedState = LOADED_PROFILE_STATE
  /** A picture upload is running: leaving would cancel it. */
  private val savingState = loadedState.copy(isSaving = true)

  @Test
  fun enteringScreenRequestsProfileAndShowsLoading() {
    val viewModel = RecordingProfileViewModel(ProfileUiState())

    setContent(viewModel)

    composeTestRule.onNodeWithTag(C.Tag.profile_loading).assertIsDisplayed()
    composeTestRule.onAllNodesWithTag(C.Tag.profile_username).assertCountEquals(0)
    composeTestRule.onAllNodesWithTag(C.Tag.profile_bio).assertCountEquals(0)
    assertEquals(1, viewModel.loadCalls)
  }

  @Test
  fun loadedProfileShowsSavedFieldsAndActivityStats() {
    setContent(RecordingProfileViewModel(loadedState))

    composeTestRule.onNodeWithTag(C.Tag.profile_picture).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.profile_username).assertTextContains("Rowan")
    composeTestRule.onNodeWithTag(C.Tag.profile_bio).assertTextContains("Dungeon guide")
    composeTestRule.onNodeWithTag(C.Tag.profile_stats).performScrollTo().assertIsDisplayed()
    for ((label, count) in
        listOf("Campaigns created" to "2", "Groups joined" to "5", "Files created" to "8")) {
      composeTestRule
          .onNode(hasText(label) and hasAnySibling(hasText(count)), useUnmergedTree = true)
          .assertIsDisplayed()
    }
  }

  @Test
  fun profileWithPictureUrlShowsAccessibleProfilePicture() {
    val state = loadedState.copy(profile = profile.copy(pictureUrl = "file:///profile-test.png"))

    setContent(RecordingProfileViewModel(state))

    composeTestRule
        .onNodeWithTag(C.Tag.profile_picture)
        .assertIsDisplayed()
        .assertContentDescriptionEquals("Profile picture")
  }

  @Test
  fun missingUsernameShowsFallbackAlongsideSavedBio() {
    val state = loadedState.copy(profile = profile.copy(username = ""))

    setContent(RecordingProfileViewModel(state))

    composeTestRule
        .onNodeWithTag(C.Tag.profile_username)
        .assertIsDisplayed()
        .assertTextContains("No username")
    composeTestRule.onNodeWithTag(C.Tag.profile_bio).assertTextContains("Dungeon guide")
  }

  @Test
  fun missingBioShowsFallbackAlongsideSavedUsername() {
    val state = loadedState.copy(profile = profile.copy(bio = ""))

    setContent(RecordingProfileViewModel(state))

    composeTestRule.onNodeWithTag(C.Tag.profile_username).assertTextContains("Rowan")
    composeTestRule
        .onNodeWithTag(C.Tag.profile_bio)
        .assertIsDisplayed()
        .assertTextContains("No description")
  }

  @Test
  fun editingEmptyProfileShowsPlaceholdersWithEmptyEditableValues() {
    setContent(RecordingProfileViewModel(ProfileUiState(isLoading = false)))

    composeTestRule.onNodeWithTag(C.Tag.profile_edit).performScrollTo().performClick()

    val emptyEditableText = SemanticsMatcher.expectValue(EditableText, AnnotatedString(""))
    for ((tag, hint) in
        listOf(
            C.Tag.profile_username to "Choose a username",
            C.Tag.profile_bio to "Tell other players about yourself",
        )) {
      val field = composeTestRule.onNodeWithTag(tag).performScrollTo()
      field.performSemanticsAction(SemanticsActions.RequestFocus)
      field.assertIsDisplayed().assertTextContains(hint).assert(emptyEditableText)
    }
  }

  @Test
  fun loadFailureShowsInlineErrorAndRetryRequestsAnotherLoad() {
    val viewModel =
        RecordingProfileViewModel(
            ProfileUiState(isLoading = false, errorMessage = "Could not load profile")
        )
    setContent(viewModel)

    composeTestRule
        .onNodeWithTag(C.Tag.profile_error)
        .assertIsDisplayed()
        .assertTextContains("Could not load profile")
    composeTestRule.onNodeWithTag(C.Tag.profile_retry).performClick()

    assertEquals(2, viewModel.loadCalls)
  }

  @Test
  fun errorOnLoadedBlankProfileShowsNoRetry() {
    val state =
        loadedState.copy(
            profile = UserProfile(userId = profile.userId),
            errorMessage = "Could not update profile picture",
        )

    setContent(RecordingProfileViewModel(state))

    composeTestRule
        .onNodeWithTag(C.Tag.profile_error)
        .assertTextContains("Could not update profile picture")
    composeTestRule.onAllNodesWithTag(C.Tag.profile_retry).assertCountEquals(0)
  }

  @Test
  fun savingShowsProgressAndDisablesEditingActions() {
    val state = loadedState.copy(isEditing = true, isSaving = true, canSave = true)

    setContent(RecordingProfileViewModel(state))

    composeTestRule.onNodeWithTag(C.Tag.profile_saving).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.profile_save).assertIsNotEnabled()
    composeTestRule.onNodeWithTag(C.Tag.profile_cancel).assertIsNotEnabled()
    composeTestRule.onNodeWithTag(C.Tag.profile_pick_picture).assertIsNotEnabled()
  }

  @Test
  fun invalidUsernameShowsErrorInField() {
    val state = loadedState.copy(isEditing = true, usernameError = "Username is too long")

    setContent(RecordingProfileViewModel(state))

    composeTestRule
        .onNodeWithTag(C.Tag.profile_username)
        .performScrollTo()
        .assertTextContains("Username is too long")
  }

  @Test
  fun editActionShowsDraftsWithSaveAndCancelControls() {
    val viewModel = RecordingProfileViewModel(loadedState)
    setContent(viewModel)

    composeTestRule.onNodeWithTag(C.Tag.profile_edit).performScrollTo().performClick()

    assertEquals(1, viewModel.startEditingCalls)
    composeTestRule.onNodeWithTag(C.Tag.profile_username).assertTextContains("Rowan")
    composeTestRule.onNodeWithTag(C.Tag.profile_bio).assertTextContains("Dungeon guide")
    composeTestRule.onNodeWithTag(C.Tag.profile_cancel).assertExists()
    composeTestRule.onNodeWithTag(C.Tag.profile_save).assertIsNotEnabled()
  }

  @Test
  fun saveForwardsEditedUsernameAndBioToViewModel() {
    val viewModel = RecordingProfileViewModel(loadedState)
    setContent(viewModel)
    composeTestRule.onNodeWithTag(C.Tag.profile_edit).performScrollTo().performClick()

    composeTestRule.onNodeWithTag(C.Tag.profile_username).performTextReplacement("Morgan")
    composeTestRule.onNodeWithTag(C.Tag.profile_bio).performTextReplacement("Maps the ruins")
    composeTestRule.onNodeWithTag(C.Tag.profile_save).performScrollTo().performClick()

    assertEquals(listOf("Morgan" to "Maps the ruins"), viewModel.savedDrafts)
  }

  @Test
  fun cancelReturnsToSavedProfileWithoutSavingDraft() {
    val viewModel = RecordingProfileViewModel(loadedState)
    setContent(viewModel)
    composeTestRule.onNodeWithTag(C.Tag.profile_edit).performScrollTo().performClick()
    composeTestRule.onNodeWithTag(C.Tag.profile_username).performTextReplacement("Morgan")

    composeTestRule.onNodeWithTag(C.Tag.profile_cancel).performScrollTo().performClick()

    assertEquals(1, viewModel.cancelEditingCalls)
    assertEquals(emptyList<Pair<String, String>>(), viewModel.savedDrafts)
    composeTestRule.onNodeWithTag(C.Tag.profile_username).assertTextContains("Rowan")
    composeTestRule.onAllNodesWithTag(C.Tag.profile_save).assertCountEquals(0)
  }

  @Test
  fun editOperationFailureShowsInlineError() {
    val viewModel =
        RecordingProfileViewModel(
            loadedState.copy(
                isEditing = true,
                usernameDraft = "Morgan",
                bioDraft = "Dungeon guide",
                errorMessage = "Could not save profile",
            )
        )

    setContent(viewModel)

    composeTestRule
        .onNodeWithTag(C.Tag.profile_error)
        .performScrollTo()
        .assertIsDisplayed()
        .assertTextContains("Could not save profile")
  }

  @Test
  fun avatarPickerIsClickableOnlyWhileEditing() {
    setContent(RecordingProfileViewModel(loadedState))
    composeTestRule.onNodeWithTag(C.Tag.profile_picture).assertHasNoClickAction()
    composeTestRule.onAllNodesWithTag(C.Tag.profile_pick_picture).assertCountEquals(0)

    composeTestRule.onNodeWithTag(C.Tag.profile_edit).performScrollTo().performClick()

    composeTestRule
        .onNodeWithTag(C.Tag.profile_pick_picture)
        .performScrollTo()
        .assertIsDisplayed()
        .assertHasClickAction()
        .assertIsEnabled()

    composeTestRule.onNodeWithTag(C.Tag.profile_cancel).performScrollTo().performClick()

    composeTestRule.onNodeWithTag(C.Tag.profile_picture).assertHasNoClickAction()
    composeTestRule.onAllNodesWithTag(C.Tag.profile_pick_picture).assertCountEquals(0)
  }

  @Test
  fun picturePickerForwardsSelectedUriToViewModel() {
    val image = Uri.parse("content://profile-test/avatar")
    val picker = RecordingPickerOwner()
    val viewModel = RecordingProfileViewModel(loadedState)
    composeTestRule.setContent {
      CompositionLocalProvider(LocalActivityResultRegistryOwner provides picker) {
        SampleAppTheme { ProfileScreen(viewModel = viewModel, onBack = {}, onTabSelected = {}) }
      }
    }
    composeTestRule.onNodeWithTag(C.Tag.profile_edit).performScrollTo().performClick()

    composeTestRule.onNodeWithTag(C.Tag.profile_pick_picture).performScrollTo().performClick()
    composeTestRule.runOnIdle {
      val requestCode = picker.requestCodes.single()
      picker.activityResultRegistry.dispatchResult(
          requestCode,
          Activity.RESULT_OK,
          Intent().setData(image),
      )
    }

    assertEquals(listOf(image), viewModel.pickedImages)
  }

  @Test
  fun backButtonInvokesBackCallback() {
    var backCalls = 0
    setContent(RecordingProfileViewModel(loadedState), onBack = { backCalls++ })

    composeTestRule.onNodeWithTag(C.Tag.shared_back_button).performClick()

    assertEquals(1, backCalls)
  }

  @Test
  fun systemBackCancelsEditingWithoutLeavingProfile() {
    var backCalls = 0
    val viewModel = RecordingProfileViewModel(loadedState)
    setContent(viewModel, onBack = { backCalls++ })
    composeTestRule.onNodeWithTag(C.Tag.profile_edit).performScrollTo().performClick()

    Espresso.pressBack()

    assertEquals(1, viewModel.cancelEditingCalls)
    assertEquals(0, backCalls)
    composeTestRule.onAllNodesWithTag(C.Tag.profile_save).assertCountEquals(0)
  }

  @Test
  fun bottomBarSelectsHomeByDefaultAndForwardsTappedTab() {
    val selectedTabs = mutableListOf<String>()
    setContent(RecordingProfileViewModel(loadedState), onTabSelected = { selectedTabs.add(it) })

    composeTestRule.onNodeWithTag(C.Tag.bottomTab("home")).assertIsSelected()
    composeTestRule.onNodeWithTag(C.Tag.bottomTab("files")).performClick()

    assertEquals(listOf("Files"), selectedTabs)
  }

  @Test
  fun bottomBarReflectsProvidedSelectedTab() {
    composeTestRule.setContent {
      SampleAppTheme {
        ProfileScreen(
            viewModel = RecordingProfileViewModel(loadedState),
            onBack = {},
            onTabSelected = {},
            selectedTab = "Map",
        )
      }
    }

    composeTestRule.onNodeWithTag(C.Tag.bottomTab("map")).assertIsSelected()
  }

  @Test
  fun backWhileSavingLeavesOnlyAfterConfirmation() {
    var backCalls = 0
    setContent(RecordingProfileViewModel(savingState), onBack = { backCalls++ })

    composeTestRule.onNodeWithTag(C.Tag.shared_back_button).performClick()

    composeTestRule.onNodeWithTag(C.Tag.profile_leave_dialog).assertIsDisplayed()
    assertEquals(0, backCalls)
    composeTestRule.onNodeWithTag(C.Tag.profile_leave_confirm).performClick()
    assertEquals(1, backCalls)
    composeTestRule.onAllNodesWithTag(C.Tag.profile_leave_dialog).assertCountEquals(0)
  }

  @Test
  fun stayingAfterSystemBackWhileSavingKeepsTheProfileOpen() {
    var backCalls = 0
    setContent(RecordingProfileViewModel(savingState), onBack = { backCalls++ })

    Espresso.pressBack()
    composeTestRule.onNodeWithTag(C.Tag.profile_leave_stay).performClick()

    assertEquals(0, backCalls)
    composeTestRule.onAllNodesWithTag(C.Tag.profile_leave_dialog).assertCountEquals(0)
  }

  @Test
  fun systemBackDuringAnEditorSaveAsksInsteadOfCancellingTheEdit() {
    var backCalls = 0
    val viewModel = RecordingProfileViewModel(savingState.copy(isEditing = true))
    setContent(viewModel, onBack = { backCalls++ })

    Espresso.pressBack()

    composeTestRule.onNodeWithTag(C.Tag.profile_leave_dialog).assertIsDisplayed()
    assertEquals(0, viewModel.cancelEditingCalls)
    assertEquals(0, backCalls)
  }

  @Test
  fun tabWhileSavingIsForwardedOnlyAfterConfirmation() {
    val selectedTabs = mutableListOf<String>()
    setContent(RecordingProfileViewModel(savingState), onTabSelected = { selectedTabs.add(it) })

    composeTestRule.onNodeWithTag(C.Tag.bottomTab("files")).performClick()

    assertEquals(emptyList<String>(), selectedTabs)
    composeTestRule.onNodeWithTag(C.Tag.profile_leave_confirm).performClick()
    assertEquals(listOf("Files"), selectedTabs)
  }

  @Test
  fun leaveDialogClosesWhenTheSaveEnds() {
    var backCalls = 0
    val viewModel = RecordingProfileViewModel(savingState)
    setContent(viewModel, onBack = { backCalls++ })
    composeTestRule.onNodeWithTag(C.Tag.shared_back_button).performClick()

    viewModel.uiState.value = loadedState

    composeTestRule.onAllNodesWithTag(C.Tag.profile_leave_dialog).assertCountEquals(0)
    assertEquals(0, backCalls)
  }

  @Test
  fun leaveDialogSurvivesRecreation() {
    val restorationTester = StateRestorationTester(composeTestRule)
    restorationTester.setContent {
      SampleAppTheme {
        ProfileScreen(
            viewModel = RecordingProfileViewModel(savingState),
            onBack = {},
            onTabSelected = {},
        )
      }
    }
    composeTestRule.onNodeWithTag(C.Tag.shared_back_button).performClick()

    restorationTester.emulateSavedInstanceStateRestore()

    composeTestRule.onNodeWithTag(C.Tag.profile_leave_dialog).assertIsDisplayed()
  }

  private fun setContent(
      viewModel: ProfileViewModelInterface,
      onBack: () -> Unit = {},
      onTabSelected: (String) -> Unit = {},
  ) {
    composeTestRule.setContent {
      SampleAppTheme {
        ProfileScreen(viewModel = viewModel, onBack = onBack, onTabSelected = onTabSelected)
      }
    }
  }
}
