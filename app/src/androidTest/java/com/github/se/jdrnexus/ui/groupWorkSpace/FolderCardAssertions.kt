// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.ui.groupWorkSpace

import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import com.github.se.jdrnexus.resources.C

/** The actions a [FolderCard] can offer. */
enum class FolderCardAction(val tag: (String) -> String) {
  EDIT(C.Tag::workspaceEdit),
  REMOVE(C.Tag::workspaceRemove),
  VISIBILITY(C.Tag::workspaceVisibility),
}

/**
 * Asserts that the folder [id] is shown with the shared [FolderCard]: the folder label, [name],
 * [description] and open arrow inside the card, and exactly the [actions] given, no other.
 *
 * Used by both the Group Workspace and the Personal Space screen tests, so both are held to the
 * same card.
 */
fun SemanticsNodeInteractionsProvider.assertSharedFolderCard(
    id: String,
    name: String,
    description: String = "Open folder to view its contents",
    actions: Set<FolderCardAction> = emptySet(),
) {
  val insideCard = hasAnyAncestor(hasTestTag(C.Tag.workspaceItem(id)))
  onNodeWithTag(C.Tag.workspaceItem(id), useUnmergedTree = true).assertIsDisplayed()
  onNode(insideCard and hasText("FOLDER"), useUnmergedTree = true).assertIsDisplayed()
  onNodeWithTag(C.Tag.workspaceItemTitle(id), useUnmergedTree = true).assertTextEquals(name)
  onNodeWithTag(C.Tag.workspaceItemDescription(id), useUnmergedTree = true)
      .assertTextEquals(description)
  onNode(insideCard and hasContentDescription("Open $name"), useUnmergedTree = true)
      .assertIsDisplayed()
  FolderCardAction.entries.forEach { action ->
    onAllNodesWithTag(action.tag(id), useUnmergedTree = true)
        .assertCountEquals(if (action in actions) 1 else 0)
  }
}
