// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.github.se.jdrnexus.ui.gameManagement

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.se.jdrnexus.model.personalSpace.CampaignGroup
import com.github.se.jdrnexus.ui.theme.SampleAppTheme
import com.github.se.jdrnexus.viewmodel.GameManagementUiState
import com.github.se.jdrnexus.viewmodel.GroupActionState
import com.github.se.jdrnexus.viewmodel.GroupLoadState
import com.github.se.jdrnexus.viewmodel.GroupRole
import com.github.se.jdrnexus.viewmodel.UserGroup
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GameManagementScreenTest {
  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun gameMasterSeesManageAndDisabledInviteActions() {
    setContent(
        uiState =
            stateFor(
                CampaignGroup(
                    id = "gm-group",
                    name = "The Sunken Crown",
                    gameMasterId = "user-1",
                    participantIds = listOf("user-1", "player-1"),
                )
            )
    )

    composeTestRule.onNodeWithText("Manage group").assertIsDisplayed()
    composeTestRule.onNodeWithText("Invite · Coming soon").assertIsDisplayed().assertIsNotEnabled()
  }

  @Test
  fun playerOnlySeesWorkspaceAction() {
    setContent(
        uiState =
            stateFor(
                CampaignGroup(
                    id = "player-group",
                    name = "Clockwork Dominion",
                    gameMasterId = "another-user",
                    participantIds = listOf("user-1", "another-user"),
                )
            )
    )

    composeTestRule.onNodeWithText("Enter workspace").assertIsDisplayed().assertIsEnabled()
    composeTestRule.onAllNodesWithText("Manage group").assertCountEquals(0)
    composeTestRule.onAllNodesWithText("Invite · Coming soon").assertCountEquals(0)
  }

  @Test
  fun enterWorkspacePassesSelectedGroupIdToNavigation() {
    var navigatedTo = ""
    setContent(
        uiState =
            stateFor(
                CampaignGroup(
                    id = "specific-group",
                    name = "The Sunken Crown",
                    gameMasterId = "user-1",
                )
            ),
        onEnterWorkspace = { navigatedTo = it },
    )

    composeTestRule.onNodeWithText("Enter workspace").performClick()

    assertEquals("specific-group", navigatedTo)
  }

  @Test
  fun emptyGroupsShowCreateFirstGroupCallToAction() {
    setContent(uiState = stateFor())

    composeTestRule.onNodeWithText("Your party awaits").assertIsDisplayed()
    composeTestRule.onNodeWithText("Create your first group").assertIsDisplayed()
  }

  @Test
  fun loadingStateShowsProgressInsteadOfEmptyState() {
    setContent(uiState = GameManagementUiState(loadState = GroupLoadState.Loading))

    composeTestRule.onNodeWithText("Loading your groups...").assertIsDisplayed()
    composeTestRule.onAllNodesWithText("Your party awaits").assertCountEquals(0)
  }

  @Test
  fun loadErrorShowsMessageAndRetryInvokesCallback() {
    var retries = 0
    setContent(
        uiState = GameManagementUiState(loadState = GroupLoadState.Error("No connection")),
        onRetryLoad = { retries++ },
    )

    composeTestRule.onNodeWithText("Couldn't load groups").assertIsDisplayed()
    composeTestRule.onNodeWithText("No connection").assertIsDisplayed()
    composeTestRule.onNodeWithText("Retry loading").performClick()

    assertEquals(1, retries)
  }

  @Test
  fun successfulMultipleGroupsShowPluralAndFallbackDetails() {
    setContent(
        uiState =
            stateFor(
                CampaignGroup(
                    id = "first",
                    name = "First campaign",
                    gameMasterId = "user-1",
                    participantIds = listOf("user-1"),
                ),
                CampaignGroup(
                    id = "second",
                    name = "Second campaign",
                    gameMasterId = "another-user",
                    participantIds = listOf("user-1", "another-user"),
                ),
            )
    )

    composeTestRule.onNodeWithText("2 groups · Your next chapter, together.").assertIsDisplayed()
    composeTestRule
        .onNodeWithText("Campaign system not set · 1 member")
        .performScrollTo()
        .assertIsDisplayed()
    composeTestRule.onNodeWithText("Player").performScrollTo().assertIsDisplayed()
  }

  @Test
  fun createDialogShowsValidationForTouchedBlankNameAndDismisses() {
    var dismissals = 0
    setContent(
        uiState = stateFor().copy(isCreateDialogOpen = true),
        onDismissCreateDialog = { dismissals++ },
    )

    val nameField = composeTestRule.onNodeWithTag(GameManagementTestTags.GROUP_NAME_FIELD)
    nameField.performTextInput("Campaign")
    composeTestRule.mainClock.advanceTimeBy(100)
    nameField.performTextClearance()
    composeTestRule.mainClock.advanceTimeBy(100)

    composeTestRule.onNodeWithText("A group name is required.").assertIsDisplayed()
    composeTestRule.onNodeWithTag(GameManagementTestTags.CREATE_GROUP_CONFIRM).assertIsNotEnabled()
    composeTestRule.onNodeWithText("Cancel").performClick()

    assertEquals(1, dismissals)
  }

  @Test
  fun createDialogShowsFailureAndAllowsRetry() {
    var attempts = 0
    setContent(
        uiState =
            stateFor()
                .copy(
                    isCreateDialogOpen = true,
                    groupName = "Keep this name",
                    createState = GroupActionState.Error("Offline"),
                ),
        onCreateGroup = { attempts++ },
    )

    composeTestRule.onNodeWithText("Couldn't create your group.").assertIsDisplayed()
    composeTestRule.onNodeWithText("Retry creation").performClick()

    assertEquals(1, attempts)
  }

  @Test
  fun creatingGroupDisablesDialogActions() {
    setContent(
        uiState =
            stateFor()
                .copy(
                    isCreateDialogOpen = true,
                    groupName = "In progress",
                    createState = GroupActionState.Loading,
                )
    )

    composeTestRule.onNodeWithTag(GameManagementTestTags.CREATE_GROUP_CONFIRM).assertIsNotEnabled()
    composeTestRule.onNodeWithText("Cancel").assertIsNotEnabled()
    composeTestRule.onNodeWithText("Creating your group. Please wait...").assertIsDisplayed()
  }

  @Test
  fun successfulCreationMessageCanBeDismissed() {
    var dismissals = 0
    setContent(
        uiState =
            stateFor()
                .copy(
                    createState =
                        GroupActionState.Success(
                            groupId = "new-campaign",
                            message = "New campaign was created.",
                        )
                ),
        onDismissSuccess = { dismissals++ },
    )

    composeTestRule.onNodeWithText("New campaign was created.").assertIsDisplayed()
    composeTestRule.onNodeWithText("Dismiss").performClick()

    assertEquals(1, dismissals)
  }

  private fun setContent(
      uiState: GameManagementUiState,
      onEnterWorkspace: (String) -> Unit = {},
      onCreateGroup: () -> Unit = {},
      onDismissCreateDialog: () -> Unit = {},
      onRetryLoad: () -> Unit = {},
      onDismissSuccess: () -> Unit = {},
  ) {
    if (uiState.isCreateDialogOpen) {
      composeTestRule.mainClock.autoAdvance = false
    }
    composeTestRule.setContent {
      SampleAppTheme(darkTheme = false) {
        val currentUiState = remember { mutableStateOf(uiState) }
        GameManagementScreenContent(
            uiState = currentUiState.value,
            onCreateGroupClick = {},
            onGroupNameChanged = { name ->
              currentUiState.value = currentUiState.value.copy(groupName = name)
            },
            onCreateGroup = onCreateGroup,
            onDismissCreateDialog = onDismissCreateDialog,
            onRetryLoad = onRetryLoad,
            onDismissSuccess = onDismissSuccess,
            onEnterWorkspace = onEnterWorkspace,
            onManageGroup = {},
            onInvite = {},
            onTabSelected = {},
            darkTheme = false,
            renderCreateDialogAsWindow = false,
        )
      }
    }
  }

  private fun stateFor(vararg groups: CampaignGroup) =
      GameManagementUiState(
          groups =
              groups.map { group ->
                UserGroup(
                    group = group,
                    role =
                        if (group.gameMasterId == "user-1") {
                          GroupRole.GAME_MASTER
                        } else {
                          GroupRole.PLAYER
                        },
                )
              },
          loadState = if (groups.isEmpty()) GroupLoadState.Empty else GroupLoadState.Success,
      )
}
