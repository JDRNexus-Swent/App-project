// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.se.jdrnexus.model.personalSpace.DocumentType
import com.github.se.jdrnexus.model.personalSpace.JDRFile
import com.github.se.jdrnexus.resources.C
import com.github.se.jdrnexus.ui.theme.SampleAppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests of the rename action of the Personal Space [FolderItem]. The rename dialog itself is the
 * Group Workspace's [com.github.se.jdrnexus.ui.groupWorkSpace.EditItemDialog]; it is tested on a
 * device in `PersonalSpaceScreenTests`, since a dialog never settles under Robolectric.
 */
@RunWith(AndroidJUnit4::class)
class PersonalSpaceScreenTest {
  @get:Rule val composeTestRule = createComposeRule()

  private val folder = JDRFile(id = "maps", name = "Maps", type = DocumentType.FOLDER)

  @Test
  fun folderWithRenameActionShowsTheEditButton() {
    setItem(folder, onRename = {})

    composeTestRule
        .onNodeWithTag(C.Tag.personalSpaceRename("maps"), useUnmergedTree = true)
        .assertIsDisplayed()
    composeTestRule
        .onNodeWithContentDescription("Edit Maps", useUnmergedTree = true)
        .assertIsDisplayed()
  }

  @Test
  fun itemWithoutRenameActionHasNoEditButton() {
    setItem(folder, onRename = null)

    composeTestRule
        .onAllNodesWithTag(C.Tag.personalSpaceRename("maps"), useUnmergedTree = true)
        .assertCountEquals(0)
  }

  @Test
  fun clickingTheEditButtonRenamesWithoutOpeningTheFolder() {
    var renameClicks = 0
    var openClicks = 0
    setItem(folder, onRename = { renameClicks++ }, onClick = { openClicks++ })

    composeTestRule
        .onNodeWithTag(C.Tag.personalSpaceRename("maps"), useUnmergedTree = true)
        .performClick()

    assertEquals(1, renameClicks)
    assertEquals(0, openClicks)
  }

  private fun setItem(
      item: JDRFile,
      onRename: (() -> Unit)?,
      onClick: () -> Unit = {},
  ) {
    composeTestRule.setContent {
      SampleAppTheme { FolderItem(folder = item, onFolderClicked = onClick, onRename = onRename) }
    }
  }
}
