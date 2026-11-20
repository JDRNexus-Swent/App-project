// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.ui.groupWorkSpace

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.se.jdrnexus.model.personalSpace.DocumentType
import com.github.se.jdrnexus.model.personalSpace.JDRFile
import com.github.se.jdrnexus.resources.C
import com.github.se.jdrnexus.ui.theme.SampleAppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Tests of the [FolderCard] shared by the Group Workspace and the Personal Space. */
@RunWith(AndroidJUnit4::class)
class FolderCardTest {
  @get:Rule val composeTestRule = createComposeRule()

  private val folder = JDRFile(id = "maps", name = "Maps", type = DocumentType.FOLDER)

  @Test
  fun folderCardShowsLabelNameDescriptionAndOpenArrow() {
    setCard { FolderCard(folder = folder, onOpen = {}) }

    composeTestRule.onNodeWithText("FOLDER", useUnmergedTree = true).assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(C.Tag.workspaceItemTitle("maps"), useUnmergedTree = true)
        .assertTextEquals("Maps")
    composeTestRule
        .onNodeWithTag(C.Tag.workspaceItemDescription("maps"), useUnmergedTree = true)
        .assertTextEquals("Open folder to view its contents")
    composeTestRule
        .onNodeWithContentDescription("Open Maps", useUnmergedTree = true)
        .assertIsDisplayed()
  }

  @Test
  fun folderContentIsShownAsItsDescription() {
    setCard { FolderCard(folder = folder.copy(content = "World\nmaps"), onOpen = {}) }

    composeTestRule
        .onNodeWithTag(C.Tag.workspaceItemDescription("maps"), useUnmergedTree = true)
        .assertTextEquals("World maps")
  }

  @Test
  fun clickingTheCardOpensTheFolder() {
    var opened = 0
    setCard { FolderCard(folder = folder, onOpen = { opened++ }) }

    composeTestRule.onNodeWithTag(C.Tag.workspaceItem("maps")).performClick()

    assertEquals(1, opened)
  }

  @Test
  fun withoutCallbacksNoActionIsShown() {
    setCard { FolderCard(folder = folder, onOpen = {}) }

    assertActions(edit = 0, remove = 0, visibility = 0)
  }

  @Test
  fun onlyTheGivenActionsAreShown() {
    setCard { FolderCard(folder = folder, onOpen = {}, onEdit = {}) }

    assertActions(edit = 1, remove = 0, visibility = 0)
  }

  @Test
  fun eachActionCallsItsOwnCallbackWithoutOpeningTheFolder() {
    val calls = mutableListOf<String>()
    setCard {
      FolderCard(
          folder = folder,
          onOpen = { calls += "open" },
          onEdit = { calls += "edit" },
          onRemove = { calls += "remove" },
          onToggleVisibility = { calls += "visibility" },
      )
    }

    composeTestRule
        .onNodeWithTag(C.Tag.workspaceEdit("maps"), useUnmergedTree = true)
        .performClick()
    composeTestRule
        .onNodeWithTag(C.Tag.workspaceRemove("maps"), useUnmergedTree = true)
        .performClick()
    composeTestRule
        .onNodeWithTag(C.Tag.workspaceVisibility("maps"), useUnmergedTree = true)
        .performClick()

    assertEquals(listOf("edit", "remove", "visibility"), calls)
  }

  @Test
  fun actionsAreDisabledWhileAnOperationRuns() {
    setCard {
      FolderCard(
          folder = folder,
          onOpen = {},
          onEdit = {},
          onRemove = {},
          onToggleVisibility = {},
          actionsEnabled = false,
      )
    }

    listOf(
            C.Tag.workspaceEdit("maps"),
            C.Tag.workspaceRemove("maps"),
            C.Tag.workspaceVisibility("maps"),
        )
        .forEach { tag ->
          composeTestRule.onNodeWithTag(tag, useUnmergedTree = true).assertIsNotEnabled()
        }
  }

  @Test
  fun visibilityActionOffersToHideAVisibleFolderAndShowAHiddenOne() {
    setCard {
      Column {
        FolderCard(folder = folder, onOpen = {}, onToggleVisibility = {})
        FolderCard(
            folder = folder.copy(id = "secret", name = "Secret"),
            onOpen = {},
            onToggleVisibility = {},
            isHidden = true,
        )
      }
    }

    composeTestRule
        .onNodeWithContentDescription("Hide Maps", useUnmergedTree = true)
        .assertIsDisplayed()
    composeTestRule
        .onNodeWithContentDescription("Show Secret", useUnmergedTree = true)
        .assertIsDisplayed()
  }

  // The Group Workspace card of a folder is the shared FolderCard, node for node.
  @Test
  fun workspaceCardShowsAFolderExactlyAsTheSharedFolderCard() {
    setCard {
      Column {
        FolderCard(
            folder = folder,
            onOpen = {},
            onEdit = {},
            onRemove = {},
            onToggleVisibility = {},
        )
        WorkspaceCard(
            file = folder.copy(id = "workspace-folder"),
            canEdit = true,
            canRemove = true,
            isHidden = false,
            canChangeVisibility = true,
            operationInProgress = false,
            onOpen = {},
            onEdit = {},
            onRemove = {},
            onToggleVisibility = {},
        )
      }
    }

    assertEquals(cardStructure("maps"), cardStructure("workspace-folder"))
  }

  // Permissions decide the actions; the card stays the same.
  @Test
  fun workspaceCardHidesTheFolderActionsTheUserMayNotTake() {
    setCard {
      WorkspaceCard(
          file = folder,
          canEdit = false,
          canRemove = true,
          isHidden = false,
          canChangeVisibility = false,
          operationInProgress = false,
          onOpen = {},
          onEdit = {},
          onRemove = {},
          onToggleVisibility = {},
      )
    }

    assertActions(edit = 0, remove = 1, visibility = 0)
    composeTestRule
        .onNodeWithContentDescription("Open Maps", useUnmergedTree = true)
        .assertIsDisplayed()
  }

  /** Tag, text and content description of every node inside card [id], with the id masked. */
  private fun cardStructure(id: String): List<String> =
      composeTestRule
          .onAllNodes(hasAnyAncestor(hasTestTag(C.Tag.workspaceItem(id))), useUnmergedTree = true)
          .fetchSemanticsNodes()
          .map { node ->
            listOf(
                    node.config.getOrNull(SemanticsProperties.TestTag)?.replace(id, "<id>"),
                    node.config.getOrNull(SemanticsProperties.Text)?.joinToString(),
                    node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(),
                )
                .joinToString(" | ")
          }
          .also { assertTrue("Card $id not found", it.size > 5) }

  private fun assertActions(edit: Int, remove: Int, visibility: Int) {
    composeTestRule
        .onAllNodesWithTag(C.Tag.workspaceEdit("maps"), useUnmergedTree = true)
        .assertCountEquals(edit)
    composeTestRule
        .onAllNodesWithTag(C.Tag.workspaceRemove("maps"), useUnmergedTree = true)
        .assertCountEquals(remove)
    composeTestRule
        .onAllNodesWithTag(C.Tag.workspaceVisibility("maps"), useUnmergedTree = true)
        .assertCountEquals(visibility)
  }

  private fun setCard(content: @Composable () -> Unit) {
    composeTestRule.setContent { SampleAppTheme { content() } }
  }
}
