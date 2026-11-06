// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.github.se.jdrnexus.ui.groupWorkSpace

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.ViewModelStore
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.se.jdrnexus.model.personalSpace.CampaignGroup
import com.github.se.jdrnexus.model.personalSpace.DocumentType
import com.github.se.jdrnexus.model.personalSpace.GroupRepository
import com.github.se.jdrnexus.model.personalSpace.JDRFile
import com.github.se.jdrnexus.model.personalSpace.Placement
import com.github.se.jdrnexus.model.personalSpace.WorkspaceRepository
import com.github.se.jdrnexus.model.repository.AuthError
import com.github.se.jdrnexus.model.repository.AuthRepository
import com.github.se.jdrnexus.model.repository.AuthResult
import com.github.se.jdrnexus.model.repository.AuthUser
import com.github.se.jdrnexus.resources.C
import com.github.se.jdrnexus.ui.theme.SampleAppTheme
import com.github.se.jdrnexus.viewmodel.LoadState
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GroupWorkSpaceScreenInstrumentedTest {
  @get:Rule val composeTestRule = createComposeRule()

  private var viewModelStore: ViewModelStore? = null

  @After
  fun releaseViewModel() {
    viewModelStore?.clear()
  }

  // Verifies the initial screen, default tab, role label, and empty workspace presentation.
  @Test
  fun initialScreenShowsDefaultCampaignsTabAndEmptyState() {
    showScreen(WorkspaceFakeRepository(), Role.PLAYER)

    // Verify the default Campaigns tab is displayed and selected.
    composeTestRule.onNodeWithText("Campaigns").assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(C.Tag.bottomTab("campaigns"))
        .assertIsDisplayed()
        .assertIsSelected()

    // Verify the player role and empty workspace state.
    composeTestRule.onNodeWithText("PLAYER").assertIsDisplayed()
    composeTestRule.onNodeWithText("No files yet").assertIsDisplayed()

    // Verify the breadcrumb keeps the spaces around its separator.
    composeTestRule.onNodeWithText("  ›  Ashen Realms").assertIsDisplayed()
  }

  // Verifies the shared bottom navigation destinations and selection behavior.
  @Test
  fun bottomNavigationShowsSharedTabsAndUpdatesSelection() {
    val selectedTabs = mutableListOf<String>()
    showScreen(
        WorkspaceFakeRepository(),
        Role.PLAYER,
        onTabSelected = { selectedTabs += it },
    )

    composeTestRule.onNodeWithTag(C.Tag.shared_bottom_bar).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.bottomTab("campaigns")).assertIsSelected()
    composeTestRule.onNodeWithTag(C.Tag.bottomTab("home")).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.bottomTab("files")).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.bottomTab("map")).assertIsDisplayed()

    composeTestRule.onNodeWithTag(C.Tag.bottomTab("files")).performClick()
    assertEquals(listOf("Files"), selectedTabs)
    composeTestRule.onNodeWithTag(C.Tag.bottomTab("campaigns")).assertIsSelected()
  }

  // Verifies root back navigation invokes the parent navigation callback.
  @Test
  fun backAtRootNavigatesToParent() {
    var navigatedBack = false
    showScreen(WorkspaceFakeRepository(), Role.PLAYER, onBack = { navigatedBack = true })

    composeTestRule.onNodeWithContentDescription("Go back").performClick()

    assertTrue(navigatedBack)
  }

  // Verifies folders, nested files, and navigation back to the root.
  @Test
  fun folderNavigationShowsChildrenAndReturnsToRoot() {
    val folder = file("folder", "World", DocumentType.FOLDER)
    val nested = file("nested", "Map", DocumentType.TEXT, folder.id)
    val repository = WorkspaceFakeRepository(listOf(folder, nested))
    val viewModel = showScreen(repository, Role.GAME_MASTER)

    composeTestRule.onNodeWithTag(C.Tag.workspaceItem("folder"), true).performClick()
    composeTestRule.waitUntil(5_000) { viewModel.uiState.value.currentFolderId == folder.id }
    composeTestRule.onNodeWithTag(C.Tag.workspaceItem("nested"), true).assertIsDisplayed()
    composeTestRule.onNodeWithContentDescription("Go back").performClick()
    composeTestRule.waitUntil(5_000) { viewModel.uiState.value.currentFolderId.isEmpty() }
    composeTestRule.onNodeWithTag(C.Tag.workspaceItem("folder"), true).assertIsDisplayed()
  }

  // Verifies Android system back navigates from a subfolder instead of exiting the workspace.
  @Test
  fun systemBackFromSubfolderNavigatesToParent() {
    val folder = file("folder", "World", DocumentType.FOLDER)
    val nested = file("nested", "Map", DocumentType.TEXT, folder.id)
    val viewModel =
        showScreen(
            WorkspaceFakeRepository(listOf(folder, nested)),
            Role.GAME_MASTER,
        )

    composeTestRule.onNodeWithTag(C.Tag.workspaceItem("folder"), true).performClick()

    composeTestRule.waitUntil(5_000) { viewModel.uiState.value.currentFolderId == folder.id }

    Espresso.pressBack()

    composeTestRule.waitUntil(5_000) { viewModel.uiState.value.currentFolderId.isEmpty() }

    composeTestRule.onNodeWithTag(C.Tag.workspaceItem("folder"), true).assertIsDisplayed()
  }

  // Verifies folder history survives saved-state restoration and still supports back navigation.
  @Test
  fun backNavigationWorksAfterStateRestoration() {
    val folder = file("folder", "World", DocumentType.FOLDER)
    val nested = file("nested", "Map", DocumentType.TEXT, folder.id)
    val viewModel =
        showScreen(
            WorkspaceFakeRepository(listOf(folder, nested)),
            Role.GAME_MASTER,
            setContent = false,
        )
    val restorationTester = StateRestorationTester(composeTestRule)

    restorationTester.setContent {
      SampleAppTheme {
        GroupWorkSpaceScreen(
            gwViewModel = viewModel,
            groupId = GROUP,
            groupName = "Ashen Realms",
        )
      }
    }

    composeTestRule.onNodeWithTag(C.Tag.workspaceItem("folder"), true).performClick()
    composeTestRule.waitUntil(5_000) { viewModel.uiState.value.currentFolderId == folder.id }
    restorationTester.emulateSavedInstanceStateRestore()
    composeTestRule.onNodeWithContentDescription("Go back").performClick()

    composeTestRule.waitUntil(5_000) { viewModel.uiState.value.currentFolderId.isEmpty() }
    composeTestRule.onNodeWithTag(C.Tag.workspaceItem("folder"), true).assertIsDisplayed()
  }

  // Verifies opening a file without a navigation callback shows the placeholder dialog.
  @Test
  fun clickingFileShowsViewingPlaceholder() {
    val notes = file("notes", "Notes", DocumentType.TEXT)
    showScreen(WorkspaceFakeRepository(listOf(notes)), Role.PLAYER)

    composeTestRule.onNodeWithTag(C.Tag.workspaceItem("notes"), true).performClick()

    composeTestRule.onNodeWithText("File viewing unavailable").assertIsDisplayed()
    composeTestRule
        .onNodeWithText("Viewing files from the group workspace is not available yet.")
        .assertIsDisplayed()
  }

  // Verifies a loading workspace is shown instead of an empty state.
  @Test
  fun loadingStateIsRendered() {
    val gate = CompletableDeferred<Unit>()
    val loading = showScreen(WorkspaceFakeRepository(rootGate = gate), Role.PLAYER)
    composeTestRule.onNodeWithText("No files yet").assertDoesNotExist()
    composeTestRule.onNodeWithTag(C.Tag.workspace_loading).assertIsDisplayed()
    gate.complete(Unit)
    composeTestRule.waitUntil(5_000) { loading.uiState.value.loadState == LoadState.Success }
    composeTestRule.onAllNodesWithTag(C.Tag.workspace_loading).assertCountEquals(0)
  }

  @Test
  fun gameMasterSeesHiddenItemAndCanShowIt() {
    val hidden =
        file("hidden", "Hidden", DocumentType.TEXT)
            .copy(placements = mapOf(GROUP to Placement(GROUP, true)))
    showScreen(WorkspaceFakeRepository(listOf(hidden)), Role.GAME_MASTER)

    composeTestRule.onNodeWithTag(C.Tag.workspaceItem("hidden"), true).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.workspaceVisibility("hidden"), true).assertIsDisplayed()
    composeTestRule.onNodeWithContentDescription("Show Hidden").assertIsDisplayed()
  }

  @Test
  fun playerDoesNotSeeAnotherMembersHiddenItem() {
    val hidden =
        file("hidden", "Hidden", DocumentType.TEXT)
            .copy(placements = mapOf(GROUP to Placement(GROUP, true)))
    val visible = file("visible", "Visible", DocumentType.TEXT)
    showScreen(WorkspaceFakeRepository(listOf(hidden, visible)), Role.PLAYER)

    // Wait for the list to load, so the count below cannot pass while still loading.
    composeTestRule.onNodeWithTag(C.Tag.workspaceItem("visible"), true).assertIsDisplayed()
    composeTestRule.onAllNodesWithTag(C.Tag.workspaceItem("hidden"), true).assertCountEquals(0)
  }

  // Verifies a repository failure is shown instead of an empty state.
  @Test
  fun errorStateIsRendered() {
    val error = showScreen(WorkspaceFakeRepository(rootFailure = "Read denied"), Role.PLAYER)
    composeTestRule.waitUntil(5_000) { error.uiState.value.loadState is LoadState.Error }
    composeTestRule.onNodeWithText("Read denied").assertIsDisplayed()
  }

  // Verifies Game Masters can create folders through the visible dialog workflow.
  @Test
  fun gameMasterCreatesFolder() {
    val repository = WorkspaceFakeRepository()
    showScreen(repository, Role.GAME_MASTER)

    openActions()
    composeTestRule.onNodeWithTag(C.Tag.workspace_add_folder, true).performClick()
    composeTestRule.onNodeWithTag(C.Tag.workspace_create_folder_dialog, true).assertIsDisplayed()
    composeTestRule.onAllNodesWithTag(C.Tag.workspace_add_folder, true).assertCountEquals(0)
    composeTestRule
        .onNodeWithTag(C.Tag.workspace_create_folder_name_input, true)
        .performTextInput("Treasure")
    composeTestRule.onNodeWithTag(C.Tag.workspace_create_folder_confirm, true).performClick()

    composeTestRule.waitUntil(5_000) { repository.files.values.any { it.name == "Treasure" } }
    composeTestRule.onNodeWithTag(C.Tag.workspaceItem("created-1"), true).assertIsDisplayed()
  }

  // Verifies players can add files but are not offered folder creation.
  @Test
  fun playerActionsHideFolderCreation() {
    showScreen(WorkspaceFakeRepository(), Role.PLAYER)

    openActions()
    composeTestRule.onAllNodesWithTag(C.Tag.workspace_add_folder, true).assertCountEquals(0)
    composeTestRule.onNodeWithTag(C.Tag.workspace_add_file, true).assertIsDisplayed()
  }

  // Verifies the add-file dialog filters folders, foreign files, and already-shared files.
  @Test
  fun addFileDialogShowsOnlyEligiblePersonalFiles() {
    val eligible = JDRFile("eligible", "Notes", ownerId = USER)
    val folder = eligible.copy(id = "folder", name = "Folder", type = DocumentType.FOLDER)
    val shared = eligible.copy(id = "shared", name = "Shared", sharedGroupsIds = listOf(GROUP))
    val foreign = eligible.copy(id = "foreign", name = "Foreign", ownerId = OTHER)
    showScreen(
        WorkspaceFakeRepository(),
        Role.PLAYER,
        personalItems = listOf(eligible, folder, shared, foreign),
    )

    openActions()
    composeTestRule.onNodeWithTag(C.Tag.workspace_add_file, true).performClick()
    composeTestRule.onNodeWithTag(C.Tag.workspace_add_files_dialog, true).assertIsDisplayed()
    composeTestRule.onAllNodesWithTag(C.Tag.workspace_add_file, true).assertCountEquals(0)
    composeTestRule.onNodeWithTag(C.Tag.workspace_add_files_list, true).assertIsDisplayed()
    composeTestRule.onNodeWithText("Notes").assertIsDisplayed()
    composeTestRule.onAllNodesWithText("Folder").assertCountEquals(0)
    composeTestRule.onAllNodesWithText("Shared").assertCountEquals(0)
    composeTestRule.onAllNodesWithText("Foreign").assertCountEquals(0)
    composeTestRule.onNodeWithTag(C.Tag.workspace_add_files_done, true).performClick()
    composeTestRule.onAllNodesWithTag(C.Tag.workspace_add_files_dialog, true).assertCountEquals(0)
  }

  // Verifies selecting a personal file shares it and updates the visible workspace.
  @Test
  fun selectingPersonalFileAddsItToWorkspace() {
    val personal = JDRFile("personal", "Notes", ownerId = USER)
    val repository = WorkspaceFakeRepository(listOf(personal))
    showScreen(repository, Role.PLAYER, personalItems = listOf(personal))

    openActions()
    composeTestRule.onNodeWithTag(C.Tag.workspace_add_file, true).performClick()
    composeTestRule.onNodeWithTag(C.Tag.workspace_add_files_dialog, true).assertIsDisplayed()
    composeTestRule.onNodeWithText("Notes").performClick()

    composeTestRule.waitUntil(5_000) {
      GROUP in repository.files.getValue("personal").sharedGroupsIds
    }
    composeTestRule.onNodeWithTag(C.Tag.workspaceItem("personal"), true).assertIsDisplayed()
  }

  // Verifies an add-file error does not hide the available file list.
  @Test
  fun addFilesDialogShowsErrorAndAvailableFilesTogether() {
    val personal = JDRFile("personal", "Notes", ownerId = USER)
    val repository =
        WorkspaceFakeRepository(
            initialFiles = listOf(personal),
            operationFailure = "Write denied",
        )
    showScreen(repository, Role.PLAYER, personalItems = listOf(personal))

    openActions()
    composeTestRule.onNodeWithTag(C.Tag.workspace_add_file, true).performClick()
    composeTestRule.onNodeWithTag(C.Tag.workspace_add_files_list, true).assertIsDisplayed()
    composeTestRule.onNodeWithText("Notes").performClick()

    composeTestRule.waitUntil(5_000) { repository.lastError == "Write denied" }
    composeTestRule.onNodeWithText("Write denied").assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.workspace_add_files_list, true).assertIsDisplayed()
    composeTestRule.onNodeWithText("Notes").assertIsDisplayed()
  }

  // Verifies players see controls for their own files but not another player's files.
  @Test
  fun playerControlsRespectOwnership() {
    val own = file("own", "Own", DocumentType.TEXT, ownerId = USER)
    val foreign = file("foreign", "Foreign", DocumentType.TEXT, ownerId = OTHER)
    showScreen(WorkspaceFakeRepository(listOf(own, foreign)), Role.PLAYER)

    composeTestRule.onNodeWithTag(C.Tag.workspaceEdit("own"), true).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.workspaceRemove("own"), true).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.workspaceVisibility("own"), true).assertIsDisplayed()
    composeTestRule.onAllNodesWithTag(C.Tag.workspaceEdit("foreign"), true).assertCountEquals(0)
    composeTestRule.onAllNodesWithTag(C.Tag.workspaceRemove("foreign"), true).assertCountEquals(0)
    composeTestRule
        .onAllNodesWithTag(C.Tag.workspaceVisibility("foreign"), true)
        .assertCountEquals(0)
  }

  // Verifies a player can edit, hide, and remove their own file through tagged controls.
  @Test
  fun playerCanManageOwnFileUsingTaggedActions() {
    val editable =
        file("own-edit", "Own", DocumentType.TEXT, ownerId = USER).copy(content = "Original")
    val removable = file("own-remove", "Remove me", DocumentType.TEXT, ownerId = USER)
    val repository = WorkspaceFakeRepository(listOf(editable, removable))
    showScreen(repository, Role.PLAYER)

    composeTestRule.onNodeWithTag(C.Tag.workspaceEdit("own-edit"), true).performClick()
    composeTestRule.onNodeWithTag(C.Tag.workspace_edit_item_dialog, true).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.workspace_edit_item_name_input, true).performTextClearance()
    composeTestRule
        .onNodeWithTag(C.Tag.workspace_edit_item_name_input, true)
        .performTextInput("Updated own")
    composeTestRule
        .onNodeWithTag(C.Tag.workspace_edit_item_content_input, true)
        .performTextClearance()
    composeTestRule
        .onNodeWithTag(C.Tag.workspace_edit_item_content_input, true)
        .performTextInput("Updated content")
    composeTestRule.onNodeWithTag(C.Tag.workspace_edit_item_save, true).performClick()
    composeTestRule.waitUntil(5_000) {
      repository.files.getValue("own-edit").name == "Updated own" &&
          repository.files.getValue("own-edit").content == "Updated content"
    }

    composeTestRule.onNodeWithTag(C.Tag.workspaceVisibility("own-edit"), true).performClick()
    composeTestRule.waitUntil(5_000) {
      repository.files.getValue("own-edit").placements.getValue(GROUP).isHidden
    }

    composeTestRule.onNodeWithTag(C.Tag.workspaceRemove("own-remove"), true).performClick()
    composeTestRule.onNodeWithTag(C.Tag.workspace_remove_item_dialog, true).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.workspace_remove_item_confirm, true).performClick()
    composeTestRule.waitUntil(5_000) {
      GROUP !in repository.files.getValue("own-remove").sharedGroupsIds
    }
    composeTestRule.onAllNodesWithTag(C.Tag.workspaceItem("own-remove"), true).assertCountEquals(0)
  }

  // Verifies Game Masters can edit, hide, and remove another player's file.
  @Test
  fun gameMasterCanManageForeignFile() {
    val editable = file("foreign-edit", "Foreign", DocumentType.TEXT, ownerId = OTHER)
    val removable = file("foreign-remove", "Remove me", DocumentType.TEXT, ownerId = OTHER)
    val repository = WorkspaceFakeRepository(listOf(editable, removable))
    showScreen(repository, Role.GAME_MASTER)

    composeTestRule.onNodeWithTag(C.Tag.workspaceEdit("foreign-edit"), true).performClick()
    composeTestRule.onNodeWithTag(C.Tag.workspace_edit_item_dialog, true).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.workspace_edit_item_name_input, true).performTextClearance()
    composeTestRule
        .onNodeWithTag(C.Tag.workspace_edit_item_name_input, true)
        .performTextInput("Updated foreign")
    composeTestRule.onNodeWithTag(C.Tag.workspace_edit_item_save, true).performClick()
    composeTestRule.waitUntil(5_000) {
      repository.files.getValue("foreign-edit").name == "Updated foreign"
    }
    composeTestRule.onNodeWithTag(C.Tag.workspaceItem("foreign-edit"), true).assertIsDisplayed()

    composeTestRule.onNodeWithTag(C.Tag.workspaceVisibility("foreign-edit"), true).performClick()
    composeTestRule.waitUntil(5_000) {
      repository.files.getValue("foreign-edit").placements.getValue(GROUP).isHidden
    }
    composeTestRule.onNodeWithTag(C.Tag.workspaceRemove("foreign-remove"), true).performClick()
    composeTestRule.onNodeWithTag(C.Tag.workspace_remove_item_dialog, true).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.workspace_remove_item_confirm, true).performClick()
    composeTestRule.waitUntil(5_000) {
      GROUP !in repository.files.getValue("foreign-remove").sharedGroupsIds
    }
  }

  // Verifies edit validation, cancellation, and successful visible updates.
  @Test
  fun editDialogValidatesAndUpdatesFile() {
    val original = file("notes", "Notes", DocumentType.TEXT, ownerId = USER)
    val repository = WorkspaceFakeRepository(listOf(original))
    showScreen(repository, Role.PLAYER)

    composeTestRule.onNodeWithTag(C.Tag.workspaceEdit("notes"), true).performClick()
    composeTestRule.onNodeWithTag(C.Tag.workspace_edit_item_name_input, true).performTextClearance()
    composeTestRule.onNodeWithTag(C.Tag.workspace_edit_item_save, true).performClick()
    composeTestRule.onNodeWithText("The item name cannot be blank.").assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(C.Tag.workspace_edit_item_name_input, true)
        .performTextInput("Updated")
    composeTestRule.onNodeWithTag(C.Tag.workspace_edit_item_save, true).performClick()
    composeTestRule.waitUntil(5_000) { repository.files.getValue("notes").name == "Updated" }
    composeTestRule.onNodeWithText("Updated").assertIsDisplayed()
  }

  // Verifies the edit dialog and entered text survive saved-state restoration.
  @Test
  fun editDialogRestoresVisibilityAndText() {
    val original = file("notes", "Notes", DocumentType.TEXT, ownerId = USER)
    val viewModel =
        showScreen(
            WorkspaceFakeRepository(listOf(original)),
            Role.PLAYER,
            setContent = false,
        )
    val restorationTester = StateRestorationTester(composeTestRule)

    restorationTester.setContent {
      SampleAppTheme {
        GroupWorkSpaceScreen(
            gwViewModel = viewModel,
            groupId = GROUP,
            groupName = "Ashen Realms",
        )
      }
    }

    composeTestRule.onNodeWithTag(C.Tag.workspaceEdit("notes"), true).performClick()
    composeTestRule.onNodeWithTag(C.Tag.workspace_edit_item_name_input, true).performTextClearance()
    composeTestRule
        .onNodeWithTag(C.Tag.workspace_edit_item_name_input, true)
        .performTextInput("Restored name")

    restorationTester.emulateSavedInstanceStateRestore()

    composeTestRule.onNodeWithTag(C.Tag.workspace_edit_item_dialog, true).assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(C.Tag.workspace_edit_item_name_input, true)
        .assertTextEquals("Restored name")
  }

  // Verifies failed operations keep dialogs open and expose the repository error.
  @Test
  fun failedEditDisplaysErrorAndKeepsDialogOpen() {
    val original = file("notes", "Notes", DocumentType.TEXT, ownerId = USER)
    val repository = WorkspaceFakeRepository(listOf(original), operationFailure = "Write denied")
    showScreen(repository, Role.PLAYER)

    composeTestRule.onNodeWithTag(C.Tag.workspaceEdit("notes"), true).performClick()
    composeTestRule.onNodeWithTag(C.Tag.workspace_edit_item_save, true).performClick()
    composeTestRule.waitUntil(5_000) { repository.lastError == "Write denied" }
    composeTestRule.onNodeWithText("Write denied").assertIsDisplayed()
    composeTestRule.onNodeWithText("Edit file").assertIsDisplayed()
  }

  // Verifies failed removals keep the dialog open, show the error, and retain the item.
  @Test
  fun failedRemoveDisplaysErrorAndKeepsItemInWorkspace() {
    val original = file("notes", "Notes", DocumentType.TEXT, ownerId = USER)
    val repository = WorkspaceFakeRepository(listOf(original), operationFailure = "Remove denied")
    showScreen(repository, Role.PLAYER)

    composeTestRule.onNodeWithTag(C.Tag.workspaceRemove("notes"), true).performClick()
    composeTestRule.onNodeWithTag(C.Tag.workspace_remove_item_confirm, true).performClick()
    composeTestRule.waitUntil(5_000) { repository.lastError == "Remove denied" }

    composeTestRule.onNodeWithText("Remove denied").assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.workspace_remove_item_dialog, true).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.workspaceItem("notes"), true).assertIsDisplayed()
    composeTestRule
        .onNodeWithText(
            "Notes will be removed from this group. The underlying personal file remains owned by its owner."
        )
        .assertIsDisplayed()
  }

  // Verifies operation progress disables dialog controls until the repository completes.
  @Test
  fun operationProgressDisablesEditControls() {
    val original = file("notes", "Notes", DocumentType.TEXT, ownerId = USER)
    val gate = CompletableDeferred<Unit>()
    val repository = WorkspaceFakeRepository(listOf(original), operationGate = gate)
    val viewModel = showScreen(repository, Role.PLAYER)

    composeTestRule.onNodeWithTag(C.Tag.workspaceEdit("notes"), true).performClick()
    composeTestRule.onNodeWithTag(C.Tag.workspace_edit_item_save, true).performClick()
    composeTestRule.waitUntil(5_000) { repository.operationStarted.get() }
    composeTestRule.onNodeWithTag(C.Tag.workspace_edit_item_save, true).assertIsNotEnabled()
    composeTestRule.onNodeWithTag(C.Tag.workspace_edit_item_cancel, true).assertIsNotEnabled()
    composeTestRule.onNodeWithTag(C.Tag.workspace_edit_item_progress, true).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.workspace_operation_progress, true).assertIsDisplayed()
    gate.complete(Unit)
    composeTestRule.waitUntil(5_000) {
      !repository.operationStarted.get() &&
          viewModel.uiState.value.loadState == LoadState.Success &&
          viewModel.uiState.value.items.any { it.id == original.id && it.name == original.name }
    }
    composeTestRule.onNodeWithText("Edit file").assertDoesNotExist()
    composeTestRule.onNodeWithText("Notes").assertIsDisplayed()
  }

  // Verifies visibility operations show progress feedback and disable the source action.
  @Test
  fun visibilityOperationShowsProgressAndDisablesVisibilityAction() {
    val original = file("notes", "Notes", DocumentType.TEXT, ownerId = USER)
    val gate = CompletableDeferred<Unit>()
    val repository = WorkspaceFakeRepository(listOf(original), operationGate = gate)
    showScreen(repository, Role.PLAYER)

    composeTestRule.onNodeWithTag(C.Tag.workspaceVisibility("notes"), true).performClick()
    composeTestRule.waitUntil(5_000) { repository.operationStarted.get() }

    composeTestRule.onNodeWithTag(C.Tag.workspaceVisibility("notes"), true).assertIsNotEnabled()
    composeTestRule.onNodeWithTag(C.Tag.workspace_operation_progress, true).assertIsDisplayed()

    gate.complete(Unit)
    composeTestRule.waitUntil(5_000) {
      repository.files.getValue("notes").placements.getValue(GROUP).isHidden
    }
    composeTestRule.onAllNodesWithTag(C.Tag.workspace_operation_progress, true).assertCountEquals(0)
  }

  // Verifies visibility failures are shown outside dialogs without changing the item.
  @Test
  fun visibilityFailureShowsErrorToUser() {
    val original = file("notes", "Notes", DocumentType.TEXT, ownerId = USER)
    val repository =
        WorkspaceFakeRepository(listOf(original), operationFailure = "Visibility denied")
    showScreen(repository, Role.PLAYER)

    composeTestRule.onNodeWithTag(C.Tag.workspaceVisibility("notes"), true).performClick()
    composeTestRule.waitUntil(5_000) { repository.lastError == "Visibility denied" }

    composeTestRule.onNodeWithText("Visibility denied").assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.workspaceItem("notes"), true).assertIsDisplayed()
  }

  // Verifies closing a failed dialog clears its error state.
  @Test
  fun closingFailedRemoveDialogClearsError() {
    val original = file("notes", "Notes", DocumentType.TEXT, ownerId = USER)
    val repository = WorkspaceFakeRepository(listOf(original), operationFailure = "Remove denied")
    val viewModel = showScreen(repository, Role.PLAYER)

    composeTestRule.onNodeWithTag(C.Tag.workspaceRemove("notes"), true).performClick()
    composeTestRule.onNodeWithTag(C.Tag.workspace_remove_item_confirm, true).performClick()
    composeTestRule.waitUntil(5_000) { repository.lastError == "Remove denied" }
    composeTestRule.onNodeWithText("Remove denied").assertIsDisplayed()

    composeTestRule.onNodeWithTag(C.Tag.workspace_remove_item_cancel, true).performClick()
    composeTestRule.waitUntil(5_000) { viewModel.uiState.value.errorMessage.isEmpty() }
  }

  private fun openActions() {
    composeTestRule.onNodeWithTag(C.Tag.workspace_actions_fab, true).performClick()
  }

  private fun showScreen(
      repository: WorkspaceFakeRepository,
      role: Role,
      personalItems: List<JDRFile> = emptyList(),
      onBack: () -> Unit = {},
      onTabSelected: (String) -> Unit = {},
      onItemClicked: ((JDRFile, Boolean) -> Unit)? = null,
      setContent: Boolean = true,
  ): GroupWorkspaceViewModel {
    viewModelStore?.clear()
    val group =
        CampaignGroup(
            id = GROUP,
            name = "Ashen Realms",
            gameMasterId = if (role == Role.GAME_MASTER) USER else OTHER,
            participantIds = if (role == Role.PLAYER) listOf(USER) else listOf(OTHER),
        )
    val viewModel =
        GroupWorkspaceViewModel(
            GROUP,
            FakeGroupRepository(group),
            repository,
            FakeAuthRepository(),
        )
    viewModelStore = ViewModelStore().also { it.put("workspace", viewModel) }
    if (setContent) {
      composeTestRule.setContent {
        SampleAppTheme {
          GroupWorkSpaceScreen(
              gwViewModel = viewModel,
              groupId = GROUP,
              groupName = "Ashen Realms",
              onBack = onBack,
              onTabSelected = onTabSelected,
              onItemClicked = onItemClicked,
              personalItems = personalItems,
          )
        }
      }
    }
    return viewModel
  }

  private fun file(
      id: String,
      name: String,
      type: DocumentType,
      parent: String = GROUP,
      ownerId: String = OTHER,
  ) =
      JDRFile(
          id = id,
          name = name,
          ownerId = ownerId,
          type = type,
          sharedGroupsIds = listOf(GROUP),
          placements = mapOf(GROUP to Placement(parent, false)),
      )

  private enum class Role {
    GAME_MASTER,
    PLAYER,
  }

  private class WorkspaceFakeRepository(
      initialFiles: List<JDRFile> = emptyList(),
      private val rootGate: CompletableDeferred<Unit>? = null,
      private val rootFailure: String? = null,
      private val operationFailure: String? = null,
      private val operationGate: CompletableDeferred<Unit>? = null,
  ) : WorkspaceRepository {
    private val state =
        MutableStateFlow(
            initialFiles
                .map {
                  if (GROUP in it.sharedGroupsIds && GROUP !in it.placements) {
                    it.copy(placements = mapOf(GROUP to Placement(GROUP, false)))
                  } else it
                }
                .associateBy(JDRFile::id),
        )
    val files: Map<String, JDRFile>
      get() = state.value

    val operationStarted = AtomicBoolean(false)
    var lastError: String? = null
      private set

    private var nextId = 0

    private suspend fun operation() {
      operationStarted.set(true)
      operationGate?.await()
      operationFailure?.let {
        lastError = it
        operationStarted.set(false)
        throw IllegalStateException(it)
      }
    }

    override fun getNewUid() = "created-${++nextId}"

    override suspend fun createFile(file: JDRFile): Result<Unit> = runOperation {
      state.value += file.id to file
    }

    override suspend fun updateFile(file: JDRFile): Result<Unit> = runOperation {
      state.value += file.id to file
    }

    override suspend fun deleteFile(fileId: String): Result<Unit> = runOperation {
      state.value -= fileId
    }

    override suspend fun getFile(fileId: String) = Result.success(state.value[fileId])

    override suspend fun removeItemFromGroup(
        itemId: String,
        groupId: String,
        parentFolderId: String,
    ) = runOperation {
      val item = state.value.getValue(itemId)
      state.value +=
          itemId to
              item.copy(
                  sharedGroupsIds = item.sharedGroupsIds - groupId,
                  placements = item.placements - groupId,
              )
    }

    override suspend fun addItemToGroup(
        itemId: String,
        groupId: String,
        folderId: String,
        isHidden: Boolean,
        includeContent: Boolean,
    ) = runOperation {
      val item = state.value.getValue(itemId)
      state.value +=
          itemId to
              item.copy(
                  sharedGroupsIds = item.sharedGroupsIds + groupId,
                  placements = item.placements + (groupId to Placement(folderId, isHidden)),
              )
    }

    override suspend fun setItemHidden(itemId: String, groupId: String, isHidden: Boolean) =
        runOperation {
          val item = state.value.getValue(itemId)
          val placement = item.placements.getValue(groupId)
          state.value +=
              itemId to
                  item.copy(
                      placements =
                          item.placements + (groupId to placement.copy(isHidden = isHidden))
                  )
        }

    override fun getFileFlow(fileId: String): Flow<JDRFile?> = state.map { it[fileId] }

    override fun getPersonalFolderFiles(folderId: String, ownerId: String): Flow<List<JDRFile>> =
        state.map { files ->
          files.values.filter { it.ownerId == ownerId && it.personalParentId == folderId }
        }

    override fun getGroupFolderFiles(folderId: String, groupId: String): Flow<List<JDRFile>> =
        state
            .map { files ->
              files.values.filter {
                groupId in it.sharedGroupsIds && it.placements[groupId]?.folderId == folderId
              }
            }
            .onStart {
              if (folderId == GROUP) {
                rootGate?.await()
                rootFailure?.let { throw IllegalStateException(it) }
              }
            }

    override fun getPersonalRootFiles(ownerId: String): Flow<List<JDRFile>> = state.map { files ->
      files.values.filter { it.ownerId == ownerId && it.sharedGroupsIds.isEmpty() }
    }

    private suspend fun runOperation(block: () -> Unit): Result<Unit> =
        try {
          operation()
          block()
          operationStarted.set(false)
          Result.success(Unit)
        } catch (error: Exception) {
          operationStarted.set(false)
          Result.failure(error)
        }
  }

  private class FakeAuthRepository : AuthRepository {
    override val authState =
        MutableStateFlow<AuthUser?>(AuthUser(USER, "user@example.com", "User")).asStateFlow()

    override suspend fun signUpWithEmail(email: String, password: String, username: String) =
        AuthResult.Failure(AuthError.UNKNOWN)

    override suspend fun signInWithEmail(email: String, password: String) =
        AuthResult.Failure(AuthError.UNKNOWN)

    override suspend fun signInWithGoogle(idToken: String) = AuthResult.Failure(AuthError.UNKNOWN)

    override suspend fun signOut() = AuthResult.Failure(AuthError.UNKNOWN)
  }

  private class FakeGroupRepository(private val group: CampaignGroup) : GroupRepository {
    override fun getNewGroupId() = "new-group"

    override suspend fun createGroup(group: CampaignGroup) = Result.success(Unit)

    override suspend fun updateGroup(group: CampaignGroup) = Result.success(Unit)

    override suspend fun deleteGroup(groupId: String) = Result.success(Unit)

    override suspend fun getGroup(groupId: String) = Result.success(group)

    override suspend fun addUserToGroup(groupId: String, userId: String) = Result.success(Unit)

    override suspend fun removeUserFromGroup(groupId: String, userId: String) = Result.success(Unit)

    override fun getGroupFlow(groupId: String): Flow<CampaignGroup?> = MutableStateFlow(group)

    override fun getUserGroups(userId: String): Flow<List<CampaignGroup>> =
        MutableStateFlow(listOf(group))
  }

  private companion object {
    const val GROUP = "instrumented-group"
    const val USER = "current-user"
    const val OTHER = "other-user"
  }
}
