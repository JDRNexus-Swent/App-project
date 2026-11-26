// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.ui.groupWorkSpace

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.se.jdrnexus.codec.JDRFileJsonCodec
import com.github.se.jdrnexus.model.personalSpace.DocumentType
import com.github.se.jdrnexus.model.personalSpace.JDRFile
import com.github.se.jdrnexus.resources.C
import com.github.se.jdrnexus.ui.theme.SampleAppTheme
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/**
 * Tests of the item-sharing picker filter, of the [WorkspaceFab] actions menu and of the
 * [WorkspaceCard] of [GroupWorkSpaceScreen].
 */
@RunWith(AndroidJUnit4::class)
class GroupWorkSpaceScreenTest {
  @get:Rule val composeTestRule = createComposeRule()

  private companion object {
    const val GROUP_ID = "group-1"
    const val USER_ID = "user-1"
    const val OTHER_USER_ID = "user-2"
    const val CARD_ID = "card"
  }

  @Test
  fun availableItemsToShareKeepsOwnedFilesAndFoldersInOrder() {
    val file = JDRFile(id = "file", ownerId = USER_ID)
    val folder = JDRFile(id = "folder", ownerId = USER_ID, type = DocumentType.FOLDER)
    val emptyFolder = JDRFile(id = "empty", ownerId = USER_ID, type = DocumentType.FOLDER)
    val character = JDRFile(id = "hero", ownerId = USER_ID, type = DocumentType.CHARACTER)

    val available =
        availableItemsToShare(listOf(file, folder, emptyFolder, character), GROUP_ID, USER_ID)

    assertEquals(listOf(file, folder, emptyFolder, character), available)
  }

  @Test
  fun availableItemsToShareExcludesForeignAlreadySharedAndGroupRootItems() {
    val eligible = JDRFile(id = "eligible", ownerId = USER_ID)
    val foreign = JDRFile(id = "foreign", ownerId = OTHER_USER_ID, type = DocumentType.FOLDER)
    val sharedFolder =
        JDRFile(
            id = "shared",
            ownerId = USER_ID,
            type = DocumentType.FOLDER,
            sharedGroupsIds = listOf(GROUP_ID),
        )
    val groupRoot = JDRFile(id = "root", ownerId = USER_ID, type = DocumentType.GROUP_ROOT)

    val available =
        availableItemsToShare(listOf(eligible, foreign, sharedFolder, groupRoot), GROUP_ID, USER_ID)

    assertEquals(listOf(eligible), available)
  }

  @Test
  fun availableItemsToShareKeepsItemsSharedOnlyWithAnotherGroup() {
    val elsewhere = JDRFile(id = "elsewhere", ownerId = USER_ID, sharedGroupsIds = listOf("other"))

    val available = availableItemsToShare(listOf(elsewhere), GROUP_ID, USER_ID)

    assertEquals(listOf(elsewhere), available)
  }

  @Test
  fun characterCardShowsTheSheetNameRatherThanTheFileName() {
    setCard(character(fileName = "New character", sheetName = "Aria"))

    assertTagText(C.Tag.workspaceItemTitle(CARD_ID), "Aria")
  }

  @Test
  fun characterCardWithEmptySheetNameShowsNewCharacter() {
    setCard(character(fileName = "Aria", sheetName = ""))

    assertTagText(C.Tag.workspaceItemTitle(CARD_ID), "New character")
  }

  @Test
  fun characterCardWithWhitespaceOnlySheetNameShowsNewCharacter() {
    setCard(character(fileName = "Aria", sheetName = "   "))

    assertTagText(C.Tag.workspaceItemTitle(CARD_ID), "New character")
  }

  @Test
  fun characterCardShowsCurrentAndMaxHp() {
    setCard(character(sheetName = "Aria", hp = """{"current":25,"max":40}"""))

    assertTagText(C.Tag.workspaceItemDescription(CARD_ID), "HP: 25 / 40")
  }

  @Test
  fun characterCardWithoutHpShowsTheFallbackInsteadOfTheJson() {
    setCard(character(sheetName = "Aria"))

    assertTagText(
        C.Tag.workspaceItemDescription(CARD_ID),
        "No description available",
    )
  }

  @Test
  fun characterCardWithUnsetMaxHpShowsTheFallback() {
    setCard(character(sheetName = "Aria", hp = """{"current":0,"max":0}"""))

    assertTagText(
        C.Tag.workspaceItemDescription(CARD_ID),
        "No description available",
    )
  }

  @Test
  fun newlyCreatedCharacterCardShowsNewCharacterAndTheFallback() {
    setCard(
        JDRFile(
            id = CARD_ID,
            name = "New character",
            type = DocumentType.CHARACTER,
            content = JDRFileJsonCodec.serializeCharacter(null),
        )
    )

    assertTagText(C.Tag.workspaceItemTitle(CARD_ID), "New character")
    assertTagText(
        C.Tag.workspaceItemDescription(CARD_ID),
        "No description available",
    )
  }

  @Test
  fun characterCardWithUnreadableSheetShowsNeitherTheJsonNorTheFileName() {
    setCard(
        JDRFile(id = CARD_ID, name = "Aria", type = DocumentType.CHARACTER, content = "{broken")
    )

    assertTagText(C.Tag.workspaceItemTitle(CARD_ID), "New character")
    assertTagText(
        C.Tag.workspaceItemDescription(CARD_ID),
        "No description available",
    )
  }

  @Test
  fun textCardKeepsItsFileNameAndContent() {
    setCard(JDRFile(id = CARD_ID, name = "Journal", type = DocumentType.TEXT, content = "Day one"))

    assertTagText(C.Tag.workspaceItemTitle(CARD_ID), "Journal")
    assertTagText(C.Tag.workspaceItemDescription(CARD_ID), "Day one")
  }

  @Test
  fun folderCardKeepsItsFileNameAndOpenFolderDescription() {
    setCard(JDRFile(id = CARD_ID, name = "Maps", type = DocumentType.FOLDER))

    assertTagText(C.Tag.workspaceItemTitle(CARD_ID), value = "Maps")
    assertTagText(
        C.Tag.workspaceItemDescription(CARD_ID),
        "Open folder to view its contents",
    )
  }

  // Same name and content, so the two cards can differ only by their icon and type label.
  @Test
  @GraphicsMode(GraphicsMode.Mode.NATIVE)
  fun imageCardShowsTheTextIconAndLabel() {
    val text = JDRFile(id = "text", name = "Map", type = DocumentType.TEXT, content = "North")
    val image = text.copy(id = "image", type = DocumentType.IMAGE)
    setCards(text, image)

    assertArrayEquals(cardPixels(text.id), cardPixels(image.id))
  }

  private fun cardPixels(id: String): IntArray =
      composeTestRule.onNodeWithTag(C.Tag.workspaceItem(id)).captureToImage().toPixelMap().buffer

  @Test
  fun actionsMenuLabelsTheSharingActionShareAndOpensTheSharingDialog() {
    var shareRequests = 0
    composeTestRule.setContent {
      SampleAppTheme { WorkspaceFab(onCreateItem = {}, onAddFile = { shareRequests++ }) }
    }
    composeTestRule.onNodeWithTag(C.Tag.workspace_actions_fab).performClick()
    composeTestRule.waitForIdle()

    // The extended FAB clears its label's semantics, so the text sits in the unmerged tree.
    composeTestRule
        .onNode(
            hasAnyAncestor(hasTestTag(C.Tag.workspace_add_file)) and hasText("Share"),
            useUnmergedTree = true,
        )
        .assertExists()
    composeTestRule.onAllNodesWithText("Item", useUnmergedTree = true).assertCountEquals(0)
    composeTestRule.onNodeWithTag(C.Tag.workspace_add_file).performClick()

    assertEquals(1, shareRequests)
  }

  private fun assertTagText(tag: String, value: String, useUnmergedTree: Boolean = true) {
    composeTestRule.onNodeWithTag(tag, useUnmergedTree).assertTextEquals(value)
  }

  private fun character(fileName: String = "Aria", sheetName: String, hp: String? = null) =
      JDRFile(
          id = CARD_ID,
          name = fileName,
          type = DocumentType.CHARACTER,
          content =
              """{"version":1,"type":"character","character":{"name":"$sheetName"""" +
                  (hp?.let { ""","hp":$it""" } ?: "") +
                  "}}",
      )

  private fun setCard(file: JDRFile) = setCards(file)

  private fun setCards(vararg files: JDRFile) {
    composeTestRule.setContent {
      SampleAppTheme {
        Column {
          files.forEach { file ->
            WorkspaceCard(
                file = file,
                canEdit = false,
                canRemove = false,
                isHidden = false,
                canChangeVisibility = false,
                operationInProgress = false,
                onOpen = {},
                onEdit = {},
                onRemove = {},
                onToggleVisibility = {},
            )
          }
        }
      }
    }
  }
}
