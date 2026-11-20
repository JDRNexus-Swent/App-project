// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.ui.groupWorkSpace

import com.github.se.jdrnexus.model.personalSpace.DocumentType
import com.github.se.jdrnexus.model.personalSpace.JDRFile
import org.junit.Assert.assertEquals
import org.junit.Test

/** Pure-logic tests for the item-sharing picker filter of [GroupWorkSpaceScreen]. */
class GroupWorkSpaceScreenTest {

  private companion object {
    const val GROUP_ID = "group-1"
    const val USER_ID = "user-1"
    const val OTHER_USER_ID = "user-2"
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
}
