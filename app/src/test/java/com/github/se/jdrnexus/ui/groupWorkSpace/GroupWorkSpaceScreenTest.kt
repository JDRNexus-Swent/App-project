// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.ui.groupWorkSpace

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.se.jdrnexus.codec.JDRFileJsonCodec
import com.github.se.jdrnexus.model.personalSpace.DocumentType
import com.github.se.jdrnexus.model.personalSpace.JDRFile
import com.github.se.jdrnexus.resources.C
import com.github.se.jdrnexus.ui.theme.SampleAppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Tests of the item-sharing picker filter and of the [WorkspaceCard] of [GroupWorkSpaceScreen]. */
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

  @Test
  fun imageCardShowsItsFileNameUnderTheTextLabel() {
    setCard(
        JDRFile(
            id = CARD_ID,
            name = "Dungeon map",
            type = DocumentType.IMAGE,
            content = "images/$USER_ID/$CARD_ID",
        )
    )

    assertTagText(C.Tag.workspaceItemTitle(CARD_ID), "Dungeon map")
    composeTestRule.onNodeWithText("TEXT", useUnmergedTree = true).assertIsDisplayed()
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

  private fun setCard(file: JDRFile) {
    composeTestRule.setContent {
      SampleAppTheme {
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
