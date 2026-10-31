// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.github.se.jdrnexus.ui.groupWorkSpace

import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.se.jdrnexus.model.personalSpace.CampaignGroup
import com.github.se.jdrnexus.model.personalSpace.DocumentType
import com.github.se.jdrnexus.model.personalSpace.GroupRepository
import com.github.se.jdrnexus.model.personalSpace.JDRFile
import com.github.se.jdrnexus.model.personalSpace.Placement
import com.github.se.jdrnexus.model.personalSpace.WorkspaceRepository
import com.github.se.jdrnexus.model.repository.AuthRepository
import com.github.se.jdrnexus.model.repository.AuthResult
import com.github.se.jdrnexus.model.repository.AuthUser
import com.github.se.jdrnexus.ui.theme.SampleAppTheme
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private fun rootPlacement(groupId: String) = mapOf(groupId to Placement(folderId = groupId))

@RunWith(AndroidJUnit4::class)
class GroupWorkSpaceScreenTest {
  @get:Rule val composeTestRule = createComposeRule()

  private var viewModelStore: ViewModelStore? = null

  @After
  fun clearViewModel() {
    viewModelStore?.clear()
  }

  // Verifies that root items render and update when the repository emits a changed file.
  // This protects the screen's real-time workspace synchronization.
  @Test
  fun displaysRootFoldersAndFilesAndReflectsRepositoryUpdates() {
    val folder =
        JDRFile(
            id = "root-folder",
            name = "Campaign notes",
            type = DocumentType.FOLDER,
            sharedGroupsIds = listOf(GROUP_ID),
            placements =
                mapOf(
                    GROUP_ID to
                        Placement(
                            folderId = GROUP_ID,
                            isHidden = false,
                        )
                ),
        )

    val file =
        JDRFile(
            id = "root-file",
            name = "Dragon's profile",
            type = DocumentType.CHARACTER,
            ownerId = OTHER_USER_ID,
            sharedGroupsIds = listOf(GROUP_ID),
            placements =
                mapOf(
                    GROUP_ID to
                        Placement(
                            folderId = GROUP_ID,
                            isHidden = false,
                        )
                ),
        )
    val repository = FakeWorkspaceRepository(listOf(folder, file))
    val viewModel = showScreen(repository, GroupWorkspaceRole.GAME_MASTER)

    composeTestRule.onNodeWithText("Campaign notes").assertIsDisplayed()
    composeTestRule.onNodeWithText("Dragon's profile").assertIsDisplayed()

    repository.replaceFile(file.copy(name = "Updated dragon profile"))
    composeTestRule.waitUntil(5_000) {
      composeTestRule
          .onAllNodesWithText("Updated dragon profile")
          .fetchSemanticsNodes()
          .isNotEmpty()
    }
    composeTestRule.onNodeWithText("Updated dragon profile").assertIsDisplayed()
    assertEquals(
        listOf(folder, file.copy(name = "Updated dragon profile")),
        viewModel.uiState.value.items,
    )
  }

  // Verifies that a successful workspace with no items shows the empty-state message.
  // This gives users clear guidance instead of presenting a blank content area.
  @Test
  fun displaysEmptyWorkspaceState() {
    val emptyViewModel = showScreen(FakeWorkspaceRepository(), GroupWorkspaceRole.PLAYER)
    composeTestRule.onNodeWithText("No files yet").assertIsDisplayed()
    assertTrue(emptyViewModel.uiState.value.items.isEmpty())
  }

  // Verifies that loading workspaces show progress until repository data arrives.
  // This prevents a delayed load from appearing to users as an empty workspace.
  @Test
  fun displaysLoadingStateUntilWorkspaceDataArrives() {
    val gate = CompletableDeferred<Unit>()
    val loadingRepository = FakeWorkspaceRepository(rootLoadGate = gate)
    val loadingViewModel = showScreen(loadingRepository, GroupWorkspaceRole.PLAYER)
    composeTestRule
        .onAllNodes(
            SemanticsMatcher.expectValue(
                SemanticsProperties.ProgressBarRangeInfo,
                ProgressBarRangeInfo.Indeterminate,
            )
        )
        .assertCountEquals(1)
    assertTrue(loadingViewModel.uiState.value.items.isEmpty())

    gate.complete(Unit)
    composeTestRule.waitUntil(5_000) {
      loadingViewModel.uiState.value.loadState == com.github.se.jdrnexus.viewmodel.LoadState.Success
    }
  }

  // Verifies that repository failures are rendered as an error rather than an empty state.
  // This makes load problems visible and preserves the diagnostic message for users.
  @Test
  fun displaysWorkspaceErrorWhenLoadingFails() {
    val errorViewModel =
        showScreen(
            FakeWorkspaceRepository(rootLoadFailure = IllegalStateException("Read denied")),
            GroupWorkspaceRole.PLAYER,
        )
    composeTestRule.waitUntil(5_000) {
      errorViewModel.uiState.value.loadState is com.github.se.jdrnexus.viewmodel.LoadState.Error
    }
    composeTestRule.onNodeWithText("Read denied").assertIsDisplayed()
    composeTestRule.onAllNodesWithText("No files yet").assertCountEquals(0)
  }

  // Verifies that nested folders open and the back action unwinds navigation to the root.
  // This ensures users can explore folders without losing their workspace context.
  @Test
  fun opensNestedFoldersAndBackReturnsThroughParentToRoot() {
    val rootFolder =
        JDRFile(
            id = "root-folder",
            name = "World",
            type = DocumentType.FOLDER,
            sharedGroupsIds = listOf(GROUP_ID),
        )
    val nestedFolder =
        JDRFile(
            id = "nested-folder",
            name = "Ruins",
            type = DocumentType.FOLDER,
            sharedGroupsIds = listOf(GROUP_ID),
            placements =
                mapOf(
                    GROUP_ID to
                        Placement(
                            folderId = rootFolder.id,
                            isHidden = false,
                        )
                ),
        )
    val nestedFile =
        JDRFile(
            id = "nested-file",
            name = "Old map",
            ownerId = OTHER_USER_ID,
            sharedGroupsIds = listOf(GROUP_ID),
            placements =
                mapOf(
                    GROUP_ID to
                        Placement(
                            folderId = nestedFolder.id,
                            isHidden = false,
                        )
                ),
        )
    val rootFile =
        JDRFile(
            id = "root-file",
            name = "Quest outline",
            ownerId = OTHER_USER_ID,
            sharedGroupsIds = listOf(GROUP_ID),
        )
    val repository = FakeWorkspaceRepository(listOf(rootFolder, nestedFolder, nestedFile, rootFile))
    val viewModel = showScreen(repository, GroupWorkspaceRole.GAME_MASTER)

    composeTestRule.onNodeWithText("World").performClick()
    composeTestRule.waitUntil(5_000) {
      viewModel.uiState.value.currentFolderId == rootFolder.id &&
          viewModel.uiState.value.loadState == com.github.se.jdrnexus.viewmodel.LoadState.Success
    }
    composeTestRule.onNodeWithText("Ruins").assertIsDisplayed()
    composeTestRule.onAllNodesWithText("Quest outline").assertCountEquals(0)

    composeTestRule.onNodeWithText("Ruins").performClick()
    composeTestRule.waitUntil(5_000) {
      viewModel.uiState.value.currentFolderId == nestedFolder.id &&
          viewModel.uiState.value.loadState == com.github.se.jdrnexus.viewmodel.LoadState.Success
    }
    composeTestRule.onNodeWithText("Old map").assertIsDisplayed()

    composeTestRule.onNodeWithContentDescription("Go back").performClick()
    composeTestRule.waitUntil(5_000) { viewModel.uiState.value.currentFolderId == rootFolder.id }
    composeTestRule.onNodeWithText("Ruins").assertIsDisplayed()

    composeTestRule.onNodeWithContentDescription("Go back").performClick()
    composeTestRule.waitUntil(5_000) {
      viewModel.uiState.value.currentFolderId.isEmpty() &&
          viewModel.uiState.value.loadState == com.github.se.jdrnexus.viewmodel.LoadState.Success
    }
    composeTestRule.onNodeWithText("World").assertIsDisplayed()
    composeTestRule.onNodeWithText("Quest outline").assertIsDisplayed()
  }

  // Verifies that an accessible folder with no children uses the empty-state presentation.
  // This distinguishes an empty folder from a failed or still-loading folder.
  @Test
  fun emptyFolderDisplaysEmptyWorkspaceState() {
    val folder =
        JDRFile(
            id = "empty-folder",
            name = "Empty archive",
            type = DocumentType.FOLDER,
            sharedGroupsIds = listOf(GROUP_ID),
        )
    val viewModel = showScreen(FakeWorkspaceRepository(listOf(folder)), GroupWorkspaceRole.PLAYER)

    composeTestRule.onNodeWithText("Empty archive").performClick()
    composeTestRule.waitUntil(5_000) {
      viewModel.uiState.value.currentFolderId == folder.id &&
          viewModel.uiState.value.loadState == com.github.se.jdrnexus.viewmodel.LoadState.Success
    }

    composeTestRule.onNodeWithText("No files yet").assertIsDisplayed()
  }

  // Verifies that players do not receive edit or remove controls for another user's item.
  // This prevents the UI from exposing actions rejected by the ViewModel's permissions.
  @Test
  fun playerCannotEditOrRemoveOtherUsersItemsInTheScreen() {
    val foreignFile =
        JDRFile(
            id = "foreign-file",
            name = "Shared lore",
            ownerId = OTHER_USER_ID,
            sharedGroupsIds = listOf(GROUP_ID),
        )
    val repository = FakeWorkspaceRepository(listOf(foreignFile))
    val viewModel = showScreen(repository, GroupWorkspaceRole.PLAYER)
    composeTestRule.waitUntil(5_000) {
      viewModel.uiState.value.loadState == com.github.se.jdrnexus.viewmodel.LoadState.Success
    }
    composeTestRule.onNodeWithText("Shared lore").assertIsDisplayed()
    composeTestRule.onAllNodesWithContentDescription("Edit Shared lore").assertCountEquals(0)
    composeTestRule.onAllNodesWithContentDescription("Remove Shared lore").assertCountEquals(0)
    assertFalse(viewModel.uiState.value.canEditItems)
  }

  // Verifies that a player sees management controls for their own file.
  // This ensures ownership grants visibility and removal actions without broader permissions.
  @Test
  fun playerCanManageOwnFileFromTheScreen() {
    val file = groupFile("player-file", ownerId = CURRENT_USER_ID)
    val removableFile = groupFile("removable-file", ownerId = CURRENT_USER_ID)
    val repository = FakeWorkspaceRepository(listOf(file, removableFile))
    showScreen(repository, GroupWorkspaceRole.PLAYER)

    composeTestRule.onNodeWithContentDescription("Edit Player file").assertIsDisplayed()
    composeTestRule.onNodeWithContentDescription("Remove Player file").assertIsDisplayed()
    composeTestRule.onNodeWithContentDescription("Hide Player file").assertIsDisplayed()

    composeTestRule.onNodeWithContentDescription("Hide Player file").performClick()
    composeTestRule.waitUntil(5_000) {
      repository.files[file.id]?.placements?.get(GROUP_ID)?.isHidden == true
    }
    assertTrue(repository.files[file.id]?.placements?.get(GROUP_ID)?.isHidden == true)

    composeTestRule.onNodeWithContentDescription("Remove Removable file").performClick()
    composeTestRule.onNodeWithText("Remove").performClick()
    composeTestRule.waitUntil(5_000) {
      repository.files[removableFile.id]?.sharedGroupsIds?.contains(GROUP_ID) == false
    }
  }

  // Verifies that a player cannot access management actions for a foreign file.
  // This protects edit, removal, and visibility operations from unauthorized use.
  @Test
  fun playerCannotManageForeignFileFromTheScreen() {
    val file = groupFile("foreign-file", ownerId = OTHER_USER_ID)
    showScreen(FakeWorkspaceRepository(listOf(file)), GroupWorkspaceRole.PLAYER)

    composeTestRule.onAllNodesWithContentDescription("Edit Foreign file").assertCountEquals(0)
    composeTestRule.onAllNodesWithContentDescription("Remove Foreign file").assertCountEquals(0)
    composeTestRule.onAllNodesWithContentDescription("Hide Foreign file").assertCountEquals(0)
    composeTestRule.onAllNodesWithContentDescription("Show Foreign file").assertCountEquals(0)
  }

  // Verifies that a Game Master sees management controls for another user's file.
  // This ensures Game Master management applies regardless of file ownership.
  @Test
  fun gameMasterCanEditAndToggleForeignFile() {
    val file = groupFile("foreign-file", ownerId = OTHER_USER_ID)
    val repository = FakeWorkspaceRepository(listOf(file))
    showScreen(repository, GroupWorkspaceRole.GAME_MASTER)

    composeTestRule.onNodeWithContentDescription("Hide Foreign file").performClick()
    composeTestRule.waitUntil(5_000) {
      repository.files[file.id]?.placements?.get(GROUP_ID)?.isHidden == true
    }
    assertTrue(repository.files[file.id]?.placements?.get(GROUP_ID)?.isHidden == true)
  }

  // Verifies that a Game Master can remove a file owned by another user.
  // This protects the group's moderation workflow and its confirmation dialog.
  @Test
  fun gameMasterCanRemoveForeignFile() {
    val file = groupFile("foreign-file", ownerId = OTHER_USER_ID)
    val repository = FakeWorkspaceRepository(listOf(file))
    showScreen(repository, GroupWorkspaceRole.GAME_MASTER)

    composeTestRule.onNodeWithContentDescription("Remove Foreign file").performClick()
    composeTestRule.onNodeWithText("Remove from workspace?").assertIsDisplayed()
    composeTestRule.onNodeWithText("Remove").performClick()

    composeTestRule.waitUntil(5_000) {
      repository.files[file.id]?.sharedGroupsIds?.contains(GROUP_ID) == false
    }
    composeTestRule.onAllNodesWithText("foreign-file").assertCountEquals(0)
  }

  // Verifies that the add-file dialog is empty when no eligible personal files exist.
  // This gives users an explicit explanation instead of an empty selectable list.
  @Test
  fun addFileDialogShowsEmptyStateWhenNoFilesAreEligible() {
    showScreen(
        repository = FakeWorkspaceRepository(),
        role = GroupWorkspaceRole.PLAYER,
        personalItems =
            listOf(
                JDRFile(
                    id = "foreign",
                    name = "Foreign file",
                    ownerId = OTHER_USER_ID,
                ),
                JDRFile(
                    id = "folder",
                    name = "Personal folder",
                    ownerId = CURRENT_USER_ID,
                    type = DocumentType.FOLDER,
                ),
            ),
    )

    clickActionsFab()
    composeTestRule.mainClock.advanceTimeBy(1_000)
    composeTestRule.onNodeWithTag("workspace_add_file", useUnmergedTree = true).performClick()
    composeTestRule.onNodeWithText("No personal files available to add.").assertIsDisplayed()
  }

  // Verifies that dismissing the add-file dialog leaves the workspace unchanged.
  // This protects a non-destructive exit path from accidentally sharing a file.
  @Test
  fun addFileDialogCanBeDismissedWithoutAddingFile() {
    val personalFile = JDRFile(id = "personal", name = "Personal", ownerId = CURRENT_USER_ID)
    val repository = FakeWorkspaceRepository(listOf(personalFile))
    val viewModel =
        showScreen(
            repository = repository,
            role = GroupWorkspaceRole.PLAYER,
            personalItems = listOf(personalFile),
        )

    clickActionsFab()
    composeTestRule.mainClock.advanceTimeBy(1_000)
    composeTestRule.onNodeWithTag("workspace_add_file", useUnmergedTree = true).performClick()
    composeTestRule.onNodeWithText("Done").performClick()

    composeTestRule.onAllNodesWithText("Add files to workspace").assertCountEquals(0)
    assertFalse(repository.files[personalFile.id]?.sharedGroupsIds?.contains(GROUP_ID) == true)
    assertTrue(viewModel.uiState.value.items.isEmpty())
  }

  // Verifies that a Game Master can add an owned Personal Space file through the same dialog.
  // This ensures sharing is available to every role, not only players.
  @Test
  fun gameMasterCanAddOwnedPersonalFile() {
    val personalFile = JDRFile(id = "personal", name = "GM notes", ownerId = CURRENT_USER_ID)
    val repository = FakeWorkspaceRepository(listOf(personalFile))
    showScreen(
        repository = repository,
        role = GroupWorkspaceRole.GAME_MASTER,
        personalItems = listOf(personalFile),
    )

    clickActionsFab()
    composeTestRule.mainClock.advanceTimeBy(1_000)
    composeTestRule.onNodeWithTag("workspace_add_file", useUnmergedTree = true).performClick()
    composeTestRule.onNodeWithText("GM notes").performClick()

    composeTestRule.waitUntil(5_000) {
      repository.files[personalFile.id]?.sharedGroupsIds?.contains(GROUP_ID) == true
    }
    composeTestRule.onNodeWithText("GM notes").assertIsDisplayed()
  }

  // Verifies that opening a file forwards the file and Game Master edit permission.
  // This keeps downstream editors aligned with the current workspace role.
  @Test
  fun openingFilePassesTheFileAndRoleAppropriateEditPermission() {
    val file =
        JDRFile(
            id = "shared-character",
            name = "Hero",
            ownerId = OTHER_USER_ID,
            type = DocumentType.CHARACTER,
            sharedGroupsIds = listOf(GROUP_ID),
        )
    val repository = FakeWorkspaceRepository(listOf(file))
    var openedFile: JDRFile? = null
    var openedForEditing: Boolean? = null
    val gameMaster =
        showScreen(repository, GroupWorkspaceRole.GAME_MASTER) { opened, canEdit ->
          openedFile = opened
          openedForEditing = canEdit
        }

    composeTestRule.onNodeWithText("Hero").performClick()
    assertEquals(file.copy(placements = rootPlacement(GROUP_ID)), openedFile)
    assertEquals(true, openedForEditing)
    assertTrue(gameMaster.uiState.value.canEditItems)
  }

  // Verifies that players open another user's file in read-only mode.
  // This prevents player access from escalating into unauthorized editing.
  @Test
  fun playerOpensFilesInReadOnlyMode() {
    val file =
        JDRFile(
            id = "shared-character",
            name = "Hero",
            ownerId = OTHER_USER_ID,
            type = DocumentType.CHARACTER,
            sharedGroupsIds = listOf(GROUP_ID),
        )
    var openedFile: JDRFile? = null
    var openedForEditing: Boolean? = null
    val player =
        showScreen(FakeWorkspaceRepository(listOf(file)), GroupWorkspaceRole.PLAYER) {
            opened,
            canEdit ->
          openedFile = opened
          openedForEditing = canEdit
        }
    composeTestRule.onNodeWithText("Hero").performClick()
    assertEquals(file.copy(placements = rootPlacement(GROUP_ID)), openedFile)
    assertEquals(false, openedForEditing)
    assertFalse(player.uiState.value.canEditItems)
  }

  // Verifies that Game Masters receive both folder-creation and file-add actions.
  // This protects the privileged workspace workflow from disappearing or being miswired.
  @Test
  fun gameMasterActionsOfferFolderAndFileWorkflows() {
    var createFolderInvoked = false
    var addFileInvoked = false
    composeTestRule.setContent {
      SampleAppTheme {
        WorkspaceFab(
            canCreateFolder = true,
            onAddFolder = { createFolderInvoked = true },
            onAddFile = { addFileInvoked = true },
        )
      }
    }

    clickActionsFab()
    composeTestRule.mainClock.advanceTimeBy(1_000)
    composeTestRule.onNodeWithText("Folder", useUnmergedTree = true).assertIsDisplayed()
    composeTestRule.onNodeWithText("File", useUnmergedTree = true).assertIsDisplayed()
    composeTestRule.onNodeWithTag("workspace_add_folder", useUnmergedTree = true).performClick()
    assertTrue(createFolderInvoked)
    composeTestRule.onNodeWithTag("workspace_add_file", useUnmergedTree = true).performClick()
    assertTrue(addFileInvoked)
  }

  // Verifies that players can add files but cannot see folder creation.
  // This keeps the action menu consistent with the role restrictions.
  @Test
  fun playerActionsOfferFilesButHideFolderCreation() {
    var addFileInvoked = false
    composeTestRule.setContent {
      SampleAppTheme {
        WorkspaceFab(
            canCreateFolder = false,
            onAddFolder = { error("Players must not be offered folder creation.") },
            onAddFile = { addFileInvoked = true },
        )
      }
    }

    clickActionsFab()
    composeTestRule.mainClock.advanceTimeBy(1_000)
    composeTestRule.onAllNodesWithText("Folder", useUnmergedTree = true).assertCountEquals(0)
    composeTestRule.onNodeWithText("File", useUnmergedTree = true).assertIsDisplayed()
    composeTestRule.onNodeWithTag("workspace_add_file", useUnmergedTree = true).performClick()
    assertTrue(addFileInvoked)
  }

  // Verifies that only owned, unshared files are offered in the add-file workflow.
  // This prevents invalid or unauthorized personal-space items from being selectable.
  @Test
  fun personalFileCandidatesExcludeFoldersAlreadySharedFilesAndForeignOwners() {
    val eligible = JDRFile(id = "eligible", ownerId = CURRENT_USER_ID)
    val folder = eligible.copy(id = "folder", type = DocumentType.FOLDER)
    val alreadyShared = eligible.copy(id = "shared", sharedGroupsIds = listOf(GROUP_ID))
    val foreign = eligible.copy(id = "foreign", ownerId = OTHER_USER_ID)

    assertEquals(
        listOf(eligible),
        availableFilesToAdd(
            listOf(eligible, folder, alreadyShared, foreign),
            GROUP_ID,
            CURRENT_USER_ID,
        ),
    )
  }

  // Verifies that pressing back at the workspace root invokes the navigation callback.
  // This ensures the screen can return to its parent destination after folder navigation ends.
  @Test
  fun backAtRootInvokesNavigationCallback() {
    var backInvoked = false
    showScreen(
        repository = FakeWorkspaceRepository(),
        role = GroupWorkspaceRole.PLAYER,
        onBack = { backInvoked = true },
    )

    composeTestRule.onNodeWithContentDescription("Go back").performClick()

    assertTrue(backInvoked)
  }

  // Verifies that selecting a personal file adds it to the workspace and closes the dialog.
  // This protects the primary player-to-group sharing workflow exposed by the screen.
  @Test
  fun addFileDialogSharesSelectedPersonalFile() {
    val personalFile =
        JDRFile(
            id = "personal-file",
            name = "Personal notes",
            ownerId = CURRENT_USER_ID,
            type = DocumentType.TEXT,
        )
    val repository = FakeWorkspaceRepository(listOf(personalFile))
    val viewModel =
        showScreen(
            repository = repository,
            role = GroupWorkspaceRole.PLAYER,
            personalItems = listOf(personalFile),
        )

    clickActionsFab()
    composeTestRule.onNodeWithTag("workspace_add_file", useUnmergedTree = true).performClick()
    composeTestRule.onNodeWithText("Add files to workspace").assertIsDisplayed()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("Personal notes").performClick()

    composeTestRule.waitUntil(5_000) {
      repository.files[personalFile.id]?.sharedGroupsIds?.contains(GROUP_ID) == true
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("Personal notes").assertIsDisplayed()
    composeTestRule.onAllNodesWithText("Add files to workspace").assertCountEquals(0)
  }

  // Verifies that confirming removal detaches an item from the visible workspace.
  // This ensures the destructive action requires confirmation and updates the rendered list.
  @Test
  fun removeDialogConfirmsAndRemovesItem() {
    val file =
        JDRFile(
            id = "removable-file",
            name = "Temporary notes",
            ownerId = OTHER_USER_ID,
            type = DocumentType.TEXT,
            sharedGroupsIds = listOf(GROUP_ID),
        )
    val repository = FakeWorkspaceRepository(listOf(file))
    val viewModel = showScreen(repository, GroupWorkspaceRole.GAME_MASTER)

    composeTestRule.onNodeWithContentDescription("Remove Temporary notes").performClick()
    composeTestRule.onNodeWithText("Remove from workspace?").assertIsDisplayed()
    composeTestRule.onNodeWithText("Remove").performClick()

    composeTestRule.waitUntil(5_000) { viewModel.uiState.value.items.none { it.id == file.id } }
    composeTestRule.onAllNodesWithText("Temporary notes").assertCountEquals(0)
  }

  private fun showScreen(
      repository: FakeWorkspaceRepository,
      role: GroupWorkspaceRole,
      personalItems: List<JDRFile> = emptyList(),
      onBack: () -> Unit = {},
      onItemClicked: (JDRFile, Boolean) -> Unit = { _, _ -> },
  ): GroupWorkspaceViewModel {
    viewModelStore?.clear()

    val group =
        CampaignGroup(
            id = GROUP_ID,
            name = GROUP_NAME,
            gameMasterId =
                if (role == GroupWorkspaceRole.GAME_MASTER) {
                  CURRENT_USER_ID
                } else {
                  OTHER_USER_ID
                },
            participantIds =
                if (role == GroupWorkspaceRole.PLAYER) {
                  listOf(CURRENT_USER_ID)
                } else {
                  listOf(OTHER_USER_ID)
                },
        )

    val viewModel =
        GroupWorkspaceViewModel(
            groupId = GROUP_ID,
            groupRepository = FakeGroupRepository(group),
            repository = repository,
            authRepository = FakeAuthRepository(),
        )

    viewModelStore = ViewModelStore().also { it.put("workspace", viewModel) }

    composeTestRule.setContent {
      SampleAppTheme {
        GroupWorkSpaceScreen(
            gwViewModel = viewModel,
            groupId = GROUP_ID,
            groupName = GROUP_NAME,
            onBack = onBack,
            onItemClicked = onItemClicked,
            personalItems = personalItems,
        )
      }
    }

    return viewModel
  }

  private fun clickActionsFab() {
    composeTestRule.onNodeWithTag("workspace_actions_fab", useUnmergedTree = true).performClick()
  }

  private fun groupFile(
      id: String,
      ownerId: String,
      type: DocumentType = DocumentType.TEXT,
  ) =
      JDRFile(
          id = id,
          name = id.replaceFirstChar { it.uppercase() }.replace("-", " "),
          ownerId = ownerId,
          type = type,
          sharedGroupsIds = listOf(GROUP_ID),
          placements = rootPlacement(GROUP_ID),
      )

  private class FakeWorkspaceRepository(
      initialFiles: List<JDRFile> = emptyList(),
      private val rootLoadGate: CompletableDeferred<Unit>? = null,
      private val rootLoadFailure: Exception? = null,
  ) : WorkspaceRepository {
    private val fileState =
        MutableStateFlow(
            initialFiles
                .map { file ->
                  if (GROUP_ID in file.sharedGroupsIds && GROUP_ID !in file.placements) {
                    file.copy(
                        placements = rootPlacement(GROUP_ID),
                    )
                  } else {
                    file
                  }
                }
                .associateBy(JDRFile::id),
        )
    val files: Map<String, JDRFile>
      get() = fileState.value

    private var nextId = 0

    override fun getNewUid(): String = "created-${++nextId}"

    override suspend fun createFile(file: JDRFile): Result<Unit> {
      fileState.value = fileState.value + (file.id to file)
      return Result.success(Unit)
    }

    override suspend fun updateFile(file: JDRFile): Result<Unit> {
      fileState.value = fileState.value + (file.id to file)
      return Result.success(Unit)
    }

    override suspend fun deleteFile(fileId: String): Result<Unit> {
      fileState.value = fileState.value - fileId
      return Result.success(Unit)
    }

    override suspend fun getFile(fileId: String): Result<JDRFile?> =
        Result.success(fileState.value[fileId])

    override suspend fun removeItemFromGroup(
        itemId: String,
        groupId: String,
        parentFolderId: String,
    ): Result<Unit> {
      val file =
          fileState.value[itemId] ?: return Result.failure(IllegalArgumentException("Missing"))
      fileState.value +=
          (itemId to
              file.copy(
                  sharedGroupsIds = file.sharedGroupsIds - groupId,
                  placements = file.placements - groupId,
              ))
      return Result.success(Unit)
    }

    override suspend fun addItemToGroup(
        itemId: String,
        groupId: String,
        folderId: String,
        isHidden: Boolean,
        includeContent: Boolean,
    ): Result<Unit> {
      val file =
          fileState.value[itemId] ?: return Result.failure(IllegalArgumentException("Missing"))
      if (groupId in file.sharedGroupsIds) {
        return Result.failure(IllegalArgumentException("Already shared"))
      }
      fileState.value +=
          (itemId to
              file.copy(
                  sharedGroupsIds = file.sharedGroupsIds + groupId,
                  placements = file.placements + (groupId to Placement(folderId, isHidden)),
              ))
      return Result.success(Unit)
    }

    override suspend fun setItemHidden(
        itemId: String,
        groupId: String,
        isHidden: Boolean,
    ): Result<Unit> {
      val file =
          fileState.value[itemId] ?: return Result.failure(IllegalArgumentException("Missing"))
      val placement =
          file.placements[groupId] ?: return Result.failure(IllegalArgumentException("Not shared"))
      fileState.value +=
          (itemId to
              file.copy(
                  placements = file.placements + (groupId to placement.copy(isHidden = isHidden)),
              ))
      return Result.success(Unit)
    }

    override fun getFileFlow(fileId: String): Flow<JDRFile?> = fileState.map { it[fileId] }

    override fun getPersonalFolderFiles(
        folderId: String,
        ownerId: String,
    ): Flow<List<JDRFile>> = fileState.map { files ->
      files.values.filter { it.ownerId == ownerId && it.personalParentId == folderId }
    }

    override fun getGroupFolderFiles(
        folderId: String,
        groupId: String,
    ): Flow<List<JDRFile>> =
        fileState
            .map { files ->
              files.values.filter { file ->
                groupId in file.sharedGroupsIds &&
                    file.placements[groupId]?.folderId == folderId &&
                    !file.placements[groupId]!!.isHidden
              }
            }
            .onStart {
              if (folderId == groupId) {
                rootLoadGate?.await()
                rootLoadFailure?.let { throw it }
              }
            }

    override fun getPersonalRootFiles(
        ownerId: String,
    ): Flow<List<JDRFile>> = fileState.map { files ->
      files.values.filter { file ->
        file.ownerId == ownerId && file.sharedGroupsIds.isEmpty() && file.personalParentId.isEmpty()
      }
    }

    fun replaceFile(file: JDRFile) {
      fileState.value = fileState.value + (file.id to file)
    }
  }

  private class FakeAuthRepository : AuthRepository {
    override val authState =
        MutableStateFlow(AuthUser(CURRENT_USER_ID, "hero@example.com", "Hero")).asStateFlow()

    override suspend fun signUpWithEmail(
        email: String,
        password: String,
        username: String,
    ): AuthResult<AuthUser> =
        AuthResult.Failure(com.github.se.jdrnexus.model.repository.AuthError.UNKNOWN)

    override suspend fun signInWithEmail(
        email: String,
        password: String,
    ): AuthResult<AuthUser> =
        AuthResult.Failure(com.github.se.jdrnexus.model.repository.AuthError.UNKNOWN)

    override suspend fun signInWithGoogle(
        idToken: String,
    ): AuthResult<AuthUser> =
        AuthResult.Failure(com.github.se.jdrnexus.model.repository.AuthError.UNKNOWN)

    override suspend fun signOut(): AuthResult<Unit> =
        AuthResult.Failure(com.github.se.jdrnexus.model.repository.AuthError.UNKNOWN)
  }

  private class FakeGroupRepository(
      private val group: CampaignGroup,
  ) : GroupRepository {

    override fun getNewGroupId(): String = "created-group"

    override suspend fun createGroup(group: CampaignGroup): Result<Unit> = Result.success(Unit)

    override suspend fun updateGroup(group: CampaignGroup): Result<Unit> = Result.success(Unit)

    override suspend fun deleteGroup(groupId: String): Result<Unit> = Result.success(Unit)

    override suspend fun getGroup(groupId: String): Result<CampaignGroup> =
        if (groupId == group.id) {
          Result.success(group)
        } else {
          Result.failure(IllegalArgumentException("Group not found."))
        }

    override suspend fun addUserToGroup(
        groupId: String,
        userId: String,
    ): Result<Unit> = Result.success(Unit)

    override suspend fun removeUserFromGroup(
        groupId: String,
        userId: String,
    ): Result<Unit> = Result.success(Unit)

    override fun getGroupFlow(groupId: String): Flow<CampaignGroup?> =
        MutableStateFlow(if (groupId == group.id) group else null)

    override fun getUserGroups(userId: String): Flow<List<CampaignGroup>> =
        MutableStateFlow(
            if (userId == group.gameMasterId || userId in group.participantIds) {
              listOf(group)
            } else {
              emptyList()
            },
        )
  }

  private companion object {
    const val GROUP_ID = "group-ui"
    const val CURRENT_USER_ID = "current-user"
    const val OTHER_USER_ID = "other-user"
    const val GROUP_NAME = "The Ashen Realms"
  }
}
