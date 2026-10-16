// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.github.se.jdrnexus.ui.gameManagement

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.se.jdrnexus.model.personalSpace.CampaignGroup
import com.github.se.jdrnexus.ui.theme.SampleAppTheme
import com.github.se.jdrnexus.viewmodel.GameManagementUiState
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

  private fun setContent(
      uiState: GameManagementUiState,
      onEnterWorkspace: (String) -> Unit = {},
  ) {
    composeTestRule.setContent {
      SampleAppTheme(darkTheme = false) {
        GameManagementScreenContent(
            uiState = uiState,
            onCreateGroupClick = {},
            onGroupNameChanged = {},
            onCreateGroup = {},
            onDismissCreateDialog = {},
            onRetryLoad = {},
            onDismissSuccess = {},
            onEnterWorkspace = onEnterWorkspace,
            onManageGroup = {},
            onInvite = {},
            onTabSelected = {},
            darkTheme = false,
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
