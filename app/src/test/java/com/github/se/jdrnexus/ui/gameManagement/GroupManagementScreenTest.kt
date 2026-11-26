// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.ui.gameManagement

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.se.jdrnexus.resources.C
import com.github.se.jdrnexus.ui.theme.SampleAppTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Covers the group management screen with a fake ViewModel. */
@RunWith(AndroidJUnit4::class)
class GroupManagementScreenTest {
  @get:Rule val composeTestRule = createComposeRule()

  private val managed =
      GroupManagementUiState(
          groupName = "The Sunken Crown",
          members =
              listOf(
                  GroupMember("gm", "Alice", isGameMaster = true),
                  GroupMember("p1", "p1", isGameMaster = false),
              ),
          isLoading = false,
          canManage = true,
      )
  private val viewModel = FakeGroupManagementVM(managed)
  private var backs = 0
  private var groupDeletedCalls = 0
  private val tabs = mutableListOf<String>()

  @Test
  fun showsTheMembersWithARemoveButtonOnPlayersOnly() {
    setContent()

    composeTestRule.onNodeWithText("The Sunken Crown").assertIsDisplayed()
    composeTestRule.onNodeWithText("2 members").assertIsDisplayed()
    composeTestRule.onNodeWithText("Alice").assertIsDisplayed()
    composeTestRule.onNodeWithText("GameMaster").assertIsDisplayed()
    composeTestRule.onNodeWithText("Player").assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.groupManagementMember("p1")).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.groupManagementRemove("p1")).assertIsDisplayed()
    composeTestRule.onAllNodesWithTag(C.Tag.groupManagementRemove("gm")).assertCountEquals(0)
    composeTestRule.onNodeWithTag(C.Tag.group_management_delete).assertIsDisplayed()
  }

  @Test
  fun removingAPlayerAsksForConfirmationFirst() {
    setContent()

    composeTestRule.onNodeWithTag(C.Tag.groupManagementRemove("p1")).performClick()
    composeTestRule
        .onNodeWithText("p1 will lose access to the group and its workspace.")
        .assertIsDisplayed()
    assertTrue(viewModel.removed.isEmpty())
    composeTestRule.onNodeWithTag(C.Tag.group_management_confirm).performClick()

    assertEquals(listOf("p1"), viewModel.removed)
    composeTestRule.onAllNodesWithTag(C.Tag.group_management_confirm).assertCountEquals(0)
  }

  @Test
  fun cancellingKeepsThePlayer() {
    setContent()

    composeTestRule.onNodeWithTag(C.Tag.groupManagementRemove("p1")).performClick()
    composeTestRule.onNodeWithTag(C.Tag.group_management_cancel).performClick()

    assertTrue(viewModel.removed.isEmpty())
    composeTestRule.onAllNodesWithTag(C.Tag.group_management_confirm).assertCountEquals(0)
  }

  @Test
  fun deletingTheGroupNamesItAndAsksForConfirmation() {
    setContent()

    composeTestRule.onNodeWithTag(C.Tag.group_management_delete).performClick()
    composeTestRule.onNodeWithText("Delete \"The Sunken Crown\"?").assertIsDisplayed()
    assertEquals(0, viewModel.deleteCalls)
    composeTestRule.onNodeWithTag(C.Tag.group_management_confirm).performClick()

    assertEquals(1, viewModel.deleteCalls)
  }

  @Test
  fun anOpenDialogSurvivesARotation() {
    val restorationTester = StateRestorationTester(composeTestRule)
    restorationTester.setContent { Screen() }

    composeTestRule.onNodeWithTag(C.Tag.groupManagementRemove("p1")).performClick()
    restorationTester.emulateSavedInstanceStateRestore()

    composeTestRule.onNodeWithTag(C.Tag.group_management_confirm).assertIsDisplayed()
  }

  @Test
  fun aPlayerSeesNoManagementButtons() {
    viewModel.uiState.value = managed.copy(canManage = false)
    setContent()

    composeTestRule.onAllNodesWithTag(C.Tag.groupManagementRemove("p1")).assertCountEquals(0)
    composeTestRule.onAllNodesWithTag(C.Tag.group_management_delete).assertCountEquals(0)
  }

  @Test
  fun buttonsAreDisabledWhileAnActionRuns() {
    viewModel.uiState.value = managed.copy(isWorking = true)
    setContent()

    composeTestRule.onNodeWithTag(C.Tag.groupManagementRemove("p1")).assertIsNotEnabled()
    composeTestRule.onNodeWithTag(C.Tag.group_management_delete).assertIsNotEnabled()
  }

  @Test
  fun showsTheLoadingStateAndEachError() {
    viewModel.uiState.value = GroupManagementUiState()
    setContent()
    composeTestRule.onNodeWithTag(C.Tag.group_management_loading).assertIsDisplayed()
    composeTestRule.onAllNodesWithText("0 members").assertCountEquals(0)

    mapOf(
            GroupManagementError.LOAD_FAILED to "Couldn't load the group.",
            GroupManagementError.REMOVE_FAILED to "Couldn't remove the player. Try again.",
            GroupManagementError.DELETE_FAILED to "Couldn't delete the group. Try again.",
        )
        .forEach { (error, text) ->
          viewModel.uiState.value = managed.copy(error = error)
          composeTestRule.onNodeWithText(text).assertIsDisplayed()
        }
  }

  @Test
  fun leavesWhenTheGroupIsDeletedAndForwardsNavigation() {
    setContent()
    composeTestRule.onNodeWithTag(C.Tag.shared_back_button).performClick()
    composeTestRule.onNodeWithTag(C.Tag.bottomTab("home")).performClick()
    assertEquals(0, groupDeletedCalls)

    viewModel.uiState.value = managed.copy(isGroupDeleted = true)
    composeTestRule.waitForIdle()

    assertEquals(1, backs)
    assertEquals(listOf("Home"), tabs)
    assertEquals(1, groupDeletedCalls)
  }

  private fun setContent() = composeTestRule.setContent { Screen() }

  @Composable
  private fun Screen() {
    SampleAppTheme(darkTheme = false) {
      GroupManagementScreen(
          viewModel = viewModel,
          onBack = { backs++ },
          onGroupDeleted = { groupDeletedCalls++ },
          onTabSelected = { tabs += it },
      )
    }
  }

  private class FakeGroupManagementVM(state: GroupManagementUiState) : GroupManagementInterfaceVM {
    override val uiState = MutableStateFlow(state)
    val removed = mutableListOf<String>()
    var deleteCalls = 0

    override fun removeMember(uid: String) {
      removed += uid
    }

    override fun deleteGroup() {
      deleteCalls++
    }
  }
}
