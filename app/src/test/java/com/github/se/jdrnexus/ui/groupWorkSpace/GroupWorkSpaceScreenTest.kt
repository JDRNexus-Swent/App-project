// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.ui.groupWorkSpace

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.se.jdrnexus.codec.JDRFileJsonCodec
import com.github.se.jdrnexus.model.personalSpace.CampaignGroup
import com.github.se.jdrnexus.model.personalSpace.DocumentType
import com.github.se.jdrnexus.model.personalSpace.JDRFile
import com.github.se.jdrnexus.model.personalSpace.Placement
import com.github.se.jdrnexus.model.repository.AuthRepository
import com.github.se.jdrnexus.model.repository.AuthResult
import com.github.se.jdrnexus.model.repository.AuthUser
import com.github.se.jdrnexus.resources.C
import com.github.se.jdrnexus.ui.theme.SampleAppTheme
import com.github.se.jdrnexus.utils.FakeConnectivityRepository
import com.github.se.jdrnexus.utils.NavigationGroupRepository
import com.github.se.jdrnexus.utils.NavigationWorkspaceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

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

  /** Always signed in as [USER_ID]; the workspace never signs anyone in or out. */
  private object SignedInAuthRepository : AuthRepository {
    override val authState: Flow<AuthUser?> = flowOf(AuthUser(USER_ID, null, null))

    override suspend fun signUpWithEmail(
        email: String,
        password: String,
        username: String,
    ): AuthResult<AuthUser> = error("Not used")

    override suspend fun signInWithEmail(email: String, password: String): AuthResult<AuthUser> =
        error("Not used")

    override suspend fun signInWithGoogle(idToken: String): AuthResult<AuthUser> = error("Not used")

    override suspend fun signOut(): AuthResult<Unit> = error("Not used")
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
  fun cardActionsAreDisabledOfflineButTheCardStillOpens() {
    var opened = 0
    composeTestRule.setContent {
      SampleAppTheme {
        WorkspaceCard(
            file = JDRFile(id = CARD_ID, name = "Notes", ownerId = USER_ID),
            canEdit = true,
            canRemove = true,
            isHidden = false,
            canChangeVisibility = true,
            operationInProgress = false,
            onOpen = { opened++ },
            onEdit = {},
            onRemove = {},
            onToggleVisibility = {},
            isOnline = false,
        )
      }
    }

    composeTestRule.onNodeWithTag(C.Tag.workspaceEdit(CARD_ID)).assertIsNotEnabled()
    composeTestRule.onNodeWithTag(C.Tag.workspaceRemove(CARD_ID)).assertIsNotEnabled()
    composeTestRule.onNodeWithTag(C.Tag.workspaceVisibility(CARD_ID)).assertIsNotEnabled()
    composeTestRule.onNodeWithTag(C.Tag.workspaceItem(CARD_ID)).performClick()
    assertEquals(1, opened)
  }

  @Test
  fun disabledFabIsHiddenWithItsMenu() {
    composeTestRule.setContent {
      SampleAppTheme { WorkspaceFab(onCreateItem = {}, onAddFile = {}, enabled = false) }
    }

    composeTestRule.onAllNodesWithTag(C.Tag.workspace_actions_fab).assertCountEquals(0)
    composeTestRule.onAllNodesWithTag(C.Tag.workspace_create_item).assertCountEquals(0)
    composeTestRule.onAllNodesWithTag(C.Tag.workspace_add_file).assertCountEquals(0)
  }

  @Test
  fun offlineWorkspaceDisablesItsActionsUntilReconnect() {
    val connectivity = FakeConnectivityRepository(isOnline = false)
    val item =
        JDRFile(
            id = CARD_ID,
            name = "Notes",
            ownerId = USER_ID,
            sharedGroupsIds = listOf(GROUP_ID),
            placements = mapOf(GROUP_ID to Placement(GROUP_ID, false)),
        )
    val group =
        CampaignGroup(id = GROUP_ID, gameMasterId = USER_ID, participantIds = listOf(USER_ID))
    val viewModel =
        GroupWorkspaceViewModel(
            GROUP_ID,
            NavigationGroupRepository(group),
            NavigationWorkspaceRepository(listOf(item)),
            SignedInAuthRepository,
            connectivity,
        )
    composeTestRule.setContent {
      SampleAppTheme {
        GroupWorkSpaceScreen(gwViewModel = viewModel, groupId = GROUP_ID, groupName = "Crown")
      }
    }
    composeTestRule.waitUntil(5_000) {
      composeTestRule.onAllNodesWithTag(C.Tag.workspaceItem(CARD_ID)).fetchSemanticsNodes().size ==
          1
    }

    composeTestRule.onNodeWithTag(C.Tag.needs_connection).assertIsDisplayed()
    composeTestRule.onAllNodesWithTag(C.Tag.workspace_actions_fab).assertCountEquals(0)
    composeTestRule.onNodeWithTag(C.Tag.workspaceRemove(CARD_ID)).assertIsNotEnabled()

    connectivity.online.value = true

    composeTestRule.waitUntil(5_000) {
      composeTestRule.onAllNodesWithTag(C.Tag.needs_connection).fetchSemanticsNodes().isEmpty()
    }
    composeTestRule.onNodeWithTag(C.Tag.workspace_actions_fab).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.workspaceRemove(CARD_ID)).assertIsEnabled()
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
