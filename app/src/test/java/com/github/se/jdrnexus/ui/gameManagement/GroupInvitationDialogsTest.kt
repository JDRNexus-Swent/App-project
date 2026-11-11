// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.ui.gameManagement

import android.content.ClipboardManager
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.ComponentDialog
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.semantics.SemanticsProperties.EditableText
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertAll
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.isNotEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.AnnotatedString
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.se.jdrnexus.R
import com.github.se.jdrnexus.model.personalSpace.CampaignGroup
import com.github.se.jdrnexus.model.personalSpace.InvitationError
import com.github.se.jdrnexus.resources.C
import com.github.se.jdrnexus.ui.theme.SampleAppTheme
import com.github.se.jdrnexus.utils.RecordingInvitationVM
import com.github.se.jdrnexus.viewmodel.GameManagementUiState
import com.github.se.jdrnexus.viewmodel.GroupLoadState
import com.github.se.jdrnexus.viewmodel.GroupRole
import com.github.se.jdrnexus.viewmodel.InvitationState
import com.github.se.jdrnexus.viewmodel.JoinState
import com.github.se.jdrnexus.viewmodel.UserGroup
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowDialog

@RunWith(AndroidJUnit4::class)
class GroupInvitationDialogsTest {
  // An activity rule, so the clipboard and the started share intent can be read back.
  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  private val uiState = MutableStateFlow(GameManagementUiState())
  private val invitationVM = RecordingInvitationVM()

  // --- Entry points on the group list ---

  @Test
  fun joinButtonIsShownToGameMastersAndPlayers() {
    setContent(gameMasterGroup(), playerGroup())

    composeTestRule.onNodeWithTag(C.Tag.join_group_button).assertIsDisplayed().assertIsEnabled()
  }

  @Test
  fun signedOutUsersSeeNoJoinButton() {
    setContent(loadState = GroupLoadState.SignedOut)

    composeTestRule.onAllNodesWithTag(C.Tag.join_group_button).assertCountEquals(0)
  }

  @Test
  fun inviteIsOnlyOnGameMasterCards() {
    setContent(gameMasterGroup(), playerGroup())

    composeTestRule.onAllNodesWithTag(C.Tag.invite_button).assertCountEquals(1)
  }

  @Test
  fun inviteButtonsAreDisabledOnlyWhileACodeLoads() {
    setContent(gameMasterGroup(), gameMasterGroup(id = OTHER_GROUP_ID, name = OTHER_GROUP_NAME))
    composeTestRule
        .onAllNodesWithTag(C.Tag.invite_button)
        .assertCountEquals(2)
        .assertAll(isEnabled())

    uiState.update { it.copy(invitationState = InvitationState.Loading) }
    composeTestRule
        .onAllNodesWithTag(C.Tag.invite_button)
        .assertCountEquals(2)
        .assertAll(isNotEnabled())

    uiState.update { it.copy(invitationState = InvitationState.Error(InvitationError.NETWORK)) }
    composeTestRule
        .onAllNodesWithTag(C.Tag.invite_button)
        .assertCountEquals(2)
        .assertAll(isEnabled())
  }

  @Test
  fun invitingOpensTheDialogForThatGroupAndLoadsItsCode() {
    setContent(gameMasterGroup(), gameMasterGroup(id = OTHER_GROUP_ID, name = OTHER_GROUP_NAME))

    composeTestRule.onAllNodesWithTag(C.Tag.invite_button)[1].performScrollTo().performClick()

    assertEquals(listOf(OTHER_GROUP_ID), invitationVM.loadedGroups)
    composeTestRule
        .onNodeWithText(string(R.string.invite_dialog_subtitle, OTHER_GROUP_NAME))
        .assertIsDisplayed()
  }

  @Test
  fun openingTheJoinDialogCallsResetJoinState() {
    setContent(playerGroup())

    composeTestRule.onNodeWithTag(C.Tag.join_group_button).performClick()

    assertEquals(1, invitationVM.joinResets)
    composeTestRule.onNodeWithTag(C.Tag.join_dialog).assertIsDisplayed()
  }

  // --- Invite dialog ---

  @Test
  fun inviteDialogShowsTheCode() {
    openInviteDialog()

    composeTestRule.onNodeWithTag(C.Tag.invite_code).assertTextEquals(CODE)
  }

  @Test
  fun loadingWithoutACodeShowsASpinnerDisablesTheActionsButStillCloses() {
    openInviteDialog(code = null)
    // The spinner animates forever, so the test clock must not wait for the UI to settle.
    composeTestRule.mainClock.autoAdvance = false
    uiState.update { it.copy(invitationState = InvitationState.Loading) }
    composeTestRule.mainClock.advanceTimeBy(FRAME_MS)

    composeTestRule.onNodeWithTag(C.Tag.invite_loading).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.invite_copy).assertIsNotEnabled()
    composeTestRule.onNodeWithTag(C.Tag.invite_share).assertIsNotEnabled()
    composeTestRule.onNodeWithTag(C.Tag.invite_regenerate).assertIsNotEnabled()
    closeInviteDialogWhileTheClockIsPaused()
  }

  @Test
  fun noCodeAndNothingLoadingShowsTryAgainForThatGroup() {
    openInviteDialog(code = null)

    composeTestRule
        .onAllNodesWithTag(C.Tag.invite_error, useUnmergedTree = true)
        .assertCountEquals(0)
    composeTestRule.onNodeWithTag(C.Tag.invite_retry).performClick()

    // The first load comes from opening the dialog.
    assertEquals(listOf(GROUP_ID, GROUP_ID), invitationVM.loadedGroups)
  }

  @Test
  fun loadErrorShowsItsReasonAndTryAgain() {
    openInviteDialog(code = null)

    uiState.update { it.copy(invitationState = InvitationState.Error(InvitationError.NETWORK)) }

    composeTestRule
        .onNodeWithTag(C.Tag.invite_error, useUnmergedTree = true)
        .assertTextEquals(string(R.string.invitation_error_network))
    composeTestRule.onNodeWithTag(C.Tag.invite_retry).assertIsDisplayed()
  }

  @Test
  fun tryAgainReloadsTheSameGroup() {
    openInviteDialog(code = null)
    uiState.update { it.copy(invitationState = InvitationState.Error(InvitationError.NETWORK)) }

    composeTestRule.onNodeWithTag(C.Tag.invite_retry).performClick()

    // The first load comes from opening the dialog.
    assertEquals(listOf(GROUP_ID, GROUP_ID), invitationVM.loadedGroups)
  }

  @Test
  fun copyPutsTheCodeOnTheClipboard() {
    openInviteDialog()

    composeTestRule.onNodeWithTag(C.Tag.invite_copy).performClick()
    composeTestRule.waitForIdle()

    val clipboard = composeTestRule.activity.getSystemService(ClipboardManager::class.java)
    assertEquals(CODE, clipboard.primaryClip?.getItemAt(0)?.text.toString())
  }

  @Test
  fun shareOpensTheShareSheetWithTheInviteMessage() {
    openInviteDialog()

    composeTestRule.onNodeWithTag(C.Tag.invite_share).performClick()

    val chooser = shadowOf(composeTestRule.activity).nextStartedActivity
    assertEquals(Intent.ACTION_CHOOSER, chooser.action)
    @Suppress("DEPRECATION") val send = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
    assertEquals(Intent.ACTION_SEND, send.action)
    assertEquals(string(R.string.invite_share_text, CODE), send.getStringExtra(Intent.EXTRA_TEXT))
  }

  @Test
  fun regenerateOnlyHappensAfterConfirmation() {
    openInviteDialog()

    composeTestRule.onNodeWithTag(C.Tag.invite_regenerate).performClick()
    composeTestRule.onNodeWithText(string(R.string.invite_regenerate_title)).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.invite_regenerate_cancel).performClick()
    assertTrue(invitationVM.regeneratedGroups.isEmpty())
    composeTestRule.onAllNodesWithTag(C.Tag.invite_regenerate_confirm).assertCountEquals(0)

    composeTestRule.onNodeWithTag(C.Tag.invite_regenerate).performClick()
    composeTestRule.onNodeWithTag(C.Tag.invite_regenerate_confirm).performClick()

    assertEquals(listOf(GROUP_ID), invitationVM.regeneratedGroups)
    composeTestRule.onAllNodesWithTag(C.Tag.invite_regenerate_confirm).assertCountEquals(0)
    composeTestRule.onNodeWithTag(C.Tag.invite_regenerate).assertIsDisplayed()
  }

  @Test
  fun regeneratingKeepsTheOldCodeDisablesTheActionsButStillCloses() {
    openInviteDialog()
    composeTestRule.mainClock.autoAdvance = false
    uiState.update { it.copy(invitationState = InvitationState.Loading) }
    composeTestRule.mainClock.advanceTimeBy(FRAME_MS)

    composeTestRule.onNodeWithTag(C.Tag.invite_code).assertTextEquals(CODE)
    composeTestRule.onNodeWithTag(C.Tag.invite_copy).assertIsNotEnabled()
    composeTestRule.onNodeWithTag(C.Tag.invite_share).assertIsNotEnabled()
    composeTestRule.onNodeWithTag(C.Tag.invite_regenerate).assertIsNotEnabled()
    closeInviteDialogWhileTheClockIsPaused()
  }

  @Test
  fun failedRegenerationShowsTheErrorAndKeepsTheCodeUsable() {
    openInviteDialog()
    uiState.update { it.copy(invitationState = InvitationState.Error(InvitationError.NETWORK)) }

    composeTestRule.onNodeWithTag(C.Tag.invite_code).assertTextEquals(CODE)
    composeTestRule
        .onNodeWithTag(C.Tag.invite_error, useUnmergedTree = true)
        .assertTextEquals(string(R.string.invitation_error_network))
    composeTestRule.onNodeWithTag(C.Tag.invite_copy).assertIsEnabled()
    composeTestRule.onAllNodesWithTag(C.Tag.invite_retry).assertCountEquals(0)
  }

  @Test
  fun inviteDialogClosesWithItsCloseButton() {
    openInviteDialog()

    composeTestRule.onNodeWithTag(C.Tag.invite_close).performClick()

    composeTestRule.onAllNodesWithTag(C.Tag.invite_dialog).assertCountEquals(0)
  }

  @Test
  fun inviteDialogClosesWhenItsGroupDisappears() {
    openInviteDialog()

    uiState.update { it.copy(groups = emptyList()) }

    composeTestRule.onAllNodesWithTag(C.Tag.invite_dialog).assertCountEquals(0)
  }

  @Test
  fun regenerateConfirmationIsGoneWhenTheDialogIsReopened() {
    openInviteDialog()
    composeTestRule.onNodeWithTag(C.Tag.invite_regenerate).performClick()

    composeTestRule.onNodeWithTag(C.Tag.invite_close).performClick()
    composeTestRule.onNodeWithTag(C.Tag.invite_button).performClick()

    composeTestRule.onAllNodesWithTag(C.Tag.invite_regenerate_confirm).assertCountEquals(0)
    composeTestRule.onNodeWithTag(C.Tag.invite_regenerate).assertIsDisplayed()
  }

  // --- Join dialog ---

  @Test
  fun codeFieldUppercasesDropsInvalidCharactersAndKeepsEight() {
    openJoinDialog()

    composeTestRule.onNodeWithTag(C.Tag.join_code_field).performTextInput("ab0o1il-c2 3456789")

    composeTestRule
        .onNodeWithTag(C.Tag.join_code_field)
        .assert(SemanticsMatcher.expectValue(EditableText, AnnotatedString("ABC23456")))
  }

  @Test
  fun joinButtonIsDisabledUntilACodeIsTyped() {
    openJoinDialog()
    composeTestRule.onNodeWithTag(C.Tag.join_submit).assertIsNotEnabled()

    composeTestRule.onNodeWithTag(C.Tag.join_code_field).performTextInput("a")

    composeTestRule.onNodeWithTag(C.Tag.join_submit).assertIsEnabled()
  }

  @Test
  fun joinSendsTheTypedCode() {
    openJoinDialog()
    composeTestRule.onNodeWithTag(C.Tag.join_code_field).performTextInput("abc234")

    composeTestRule.onNodeWithTag(C.Tag.join_submit).performClick()

    assertEquals(listOf("ABC234"), invitationVM.joinedCodes)
  }

  @Test
  fun joiningDisablesTheFieldAndTheJoinButton() {
    openJoinDialog()
    composeTestRule.onNodeWithTag(C.Tag.join_code_field).performTextInput("ABC234")
    composeTestRule.mainClock.autoAdvance = false
    uiState.update { it.copy(joinState = JoinState.Joining) }
    composeTestRule.mainClock.advanceTimeBy(FRAME_MS)

    composeTestRule.onNodeWithTag(C.Tag.join_loading, useUnmergedTree = true).assertIsDisplayed()
    composeTestRule.onNodeWithText(string(R.string.join_in_progress)).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.join_code_field).assertIsNotEnabled()
    composeTestRule.onNodeWithTag(C.Tag.join_submit).assertIsNotEnabled()
  }

  @Test
  fun joinErrorShowsItsMessageAndKeepsTheTypedCode() {
    openJoinDialog()
    composeTestRule.onNodeWithTag(C.Tag.join_code_field).performTextInput("ABC234")

    uiState.update { it.copy(joinState = JoinState.Error(InvitationError.UNKNOWN_CODE)) }

    composeTestRule
        .onNodeWithTag(C.Tag.join_error, useUnmergedTree = true)
        .assertTextEquals(string(R.string.invitation_error_unknown_code))
    composeTestRule.onNodeWithTag(C.Tag.join_code_field).assertTextContains("ABC234")
    composeTestRule.onNodeWithTag(C.Tag.join_submit).assertIsEnabled()
  }

  @Test
  fun editingTheCodeAfterAnErrorClearsIt() {
    openJoinDialog()
    uiState.update { it.copy(joinState = JoinState.Error(InvitationError.UNKNOWN_CODE)) }

    composeTestRule.onNodeWithTag(C.Tag.join_code_field).performTextInput("A")

    // The first reset comes from opening the dialog.
    assertEquals(2, invitationVM.joinResets)
  }

  @Test
  fun editingTheCodeWithoutAnErrorDoesNotResetTheJoinState() {
    openJoinDialog()

    composeTestRule.onNodeWithTag(C.Tag.join_code_field).performTextInput("A")

    assertEquals(1, invitationVM.joinResets)
  }

  @Test
  fun typingOnlyInvalidCharactersKeepsTheError() {
    openJoinDialog()
    uiState.update { it.copy(joinState = JoinState.Error(InvitationError.UNKNOWN_CODE)) }

    composeTestRule.onNodeWithTag(C.Tag.join_code_field).performTextInput("0O1IL")

    // The code did not change, so the error stays and only the opening reset happened.
    assertEquals(1, invitationVM.joinResets)
    composeTestRule.onNodeWithTag(C.Tag.join_error, useUnmergedTree = true).assertIsDisplayed()
  }

  @Test
  fun joinedClosesTheDialog() {
    openJoinDialog()

    uiState.update { it.copy(joinState = JoinState.Joined(GROUP_ID)) }

    composeTestRule.onAllNodesWithTag(C.Tag.join_dialog).assertCountEquals(0)
  }

  @Test
  fun joinDialogClosesWithItsCloseButton() {
    openJoinDialog()

    composeTestRule.onNodeWithTag(C.Tag.join_close).performClick()

    composeTestRule.onAllNodesWithTag(C.Tag.join_dialog).assertCountEquals(0)
  }

  @Test
  fun cancelClosesTheJoinDialogAndClearsTheTypedCode() {
    openJoinDialog()
    composeTestRule.onNodeWithTag(C.Tag.join_code_field).performTextInput("ABC234")

    composeTestRule.onNodeWithTag(C.Tag.join_cancel).performClick()
    composeTestRule.onAllNodesWithTag(C.Tag.join_dialog).assertCountEquals(0)
    composeTestRule.onNodeWithTag(C.Tag.join_group_button).performClick()

    composeTestRule.onNodeWithTag(C.Tag.join_dialog).assertIsDisplayed()
    composeTestRule.onAllNodesWithText("ABC234").assertCountEquals(0)
  }

  // --- Dialogs rendered in their own window ---

  @Test
  fun inviteDialogInAWindowClosesOnBack() {
    openInviteDialog(renderDialogsAsWindow = true)
    composeTestRule.onNodeWithTag(C.Tag.invite_code).assertTextEquals(CODE)

    pressBackOnDialogWindow()

    composeTestRule.onAllNodesWithTag(C.Tag.invite_dialog).assertCountEquals(0)
  }

  // --- Error texts ---

  @Test
  fun eachInvitationErrorMapsToItsOwnStringResource() {
    assertEquals(
        R.string.invitation_error_not_signed_in,
        invitationErrorMessage(InvitationError.NOT_SIGNED_IN),
    )
    assertEquals(
        R.string.invitation_error_group_not_found,
        invitationErrorMessage(InvitationError.GROUP_NOT_FOUND),
    )
    assertEquals(
        R.string.invitation_error_not_game_master,
        invitationErrorMessage(InvitationError.NOT_GAME_MASTER),
    )
    assertEquals(
        R.string.invitation_error_invalid_code,
        invitationErrorMessage(InvitationError.INVALID_CODE),
    )
    assertEquals(
        R.string.invitation_error_unknown_code,
        invitationErrorMessage(InvitationError.UNKNOWN_CODE),
    )
    assertEquals(
        R.string.invitation_error_already_member,
        invitationErrorMessage(InvitationError.ALREADY_MEMBER),
    )
    assertEquals(R.string.invitation_error_network, invitationErrorMessage(InvitationError.NETWORK))
  }

  // --- Helpers ---

  /**
   * Taps the invite dialog's close button and checks it closed, for tests that paused the clock.
   */
  private fun closeInviteDialogWhileTheClockIsPaused() {
    composeTestRule.onNodeWithTag(C.Tag.invite_close).performClick()
    composeTestRule.mainClock.advanceTimeBy(FRAME_MS)

    composeTestRule.onAllNodesWithTag(C.Tag.invite_dialog).assertCountEquals(0)
  }

  /** Sends a back press to the window of the dialog shown last, as the system back button does. */
  private fun pressBackOnDialogWindow() {
    composeTestRule.waitForIdle()
    val dialog = ShadowDialog.getLatestDialog() as ComponentDialog
    composeTestRule.runOnUiThread { dialog.onBackPressedDispatcher.onBackPressed() }
    composeTestRule.waitForIdle()
  }

  private fun openInviteDialog(code: String? = CODE, renderDialogsAsWindow: Boolean = false) {
    setContent(gameMasterGroup(), renderDialogsAsWindow = renderDialogsAsWindow)
    composeTestRule.onNodeWithTag(C.Tag.invite_button).performClick()
    uiState.update { it.copy(inviteCode = code) }
  }

  private fun openJoinDialog(renderDialogsAsWindow: Boolean = false) {
    setContent(playerGroup(), renderDialogsAsWindow = renderDialogsAsWindow)
    composeTestRule.onNodeWithTag(C.Tag.join_group_button).performClick()
  }

  private fun setContent(
      vararg groups: UserGroup,
      loadState: GroupLoadState = GroupLoadState.Success,
      renderDialogsAsWindow: Boolean = false,
  ) {
    uiState.value = GameManagementUiState(groups = groups.toList(), loadState = loadState)
    composeTestRule.setContent {
      SampleAppTheme(darkTheme = true) {
        val state by uiState.collectAsState()
        GameManagementScreenContent(
            uiState = state,
            onCreateGroupClick = {},
            onGroupNameChanged = {},
            onCreateGroup = {},
            onDismissCreateDialog = {},
            onRetryLoad = {},
            onDismissSuccess = {},
            onEnterWorkspace = {},
            onManageGroup = {},
            invitationVM = invitationVM,
            onTabSelected = {},
            renderDialogsAsWindow = renderDialogsAsWindow,
        )
      }
    }
  }

  private fun string(id: Int, vararg args: Any): String =
      composeTestRule.activity.getString(id, *args)

  private fun gameMasterGroup(id: String = GROUP_ID, name: String = GROUP_NAME) =
      UserGroup(CampaignGroup(id = id, name = name, gameMasterId = "user-1"), GroupRole.GAME_MASTER)

  private fun playerGroup() =
      UserGroup(
          CampaignGroup(id = "player-group", name = "Clockwork Dominion", gameMasterId = "gm"),
          GroupRole.PLAYER,
      )

  private companion object {
    const val GROUP_ID = "campaign-1"
    const val GROUP_NAME = "The Sunken Crown"
    const val OTHER_GROUP_ID = "campaign-2"
    const val OTHER_GROUP_NAME = "Tides of the Black Depths"
    const val CODE = "ABCD2345"
    const val FRAME_MS = 100L
  }
}
