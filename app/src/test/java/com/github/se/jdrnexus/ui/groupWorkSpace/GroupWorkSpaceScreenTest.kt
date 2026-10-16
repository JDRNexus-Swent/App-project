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
import com.github.se.jdrnexus.model.personalSpace.DocumentType
import com.github.se.jdrnexus.model.personalSpace.JDRFile
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

@RunWith(AndroidJUnit4::class)
class GroupWorkSpaceScreenTest {
  @get:Rule val composeTestRule = createComposeRule()

  private var viewModelStore: ViewModelStore? = null

  @After
  fun clearViewModel() {
    viewModelStore?.clear()
  }

  @Test
  fun displaysRootFoldersAndFilesAndReflectsRepositoryUpdates() {
    val folder =
        JDRFile(
            id = "root-folder",
            name = "Campaign notes",
            type = DocumentType.FOLDER,
            sharedGroupsIds = listOf(GROUP_ID),
        )
    val file =
        JDRFile(
            id = "root-file",
            name = "Dragon's profile",
            type = DocumentType.CHARACTER,
            ownerId = OTHER_USER_ID,
            sharedGroupsIds = listOf(GROUP_ID),
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

  @Test
  fun displaysEmptyWorkspaceState() {
    val emptyViewModel = showScreen(FakeWorkspaceRepository(), GroupWorkspaceRole.PLAYER)
    composeTestRule.onNodeWithText("No files yet").assertIsDisplayed()
    assertTrue(emptyViewModel.uiState.value.items.isEmpty())
  }

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
    composeTestRule.onNodeWithText("Unable to load workspace").assertIsDisplayed()
    composeTestRule.onAllNodesWithText("No files yet").assertCountEquals(0)
  }

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
            parentFolderIds = listOf(rootFolder.id),
        )
    val nestedFile =
        JDRFile(
            id = "nested-file",
            name = "Old map",
            ownerId = OTHER_USER_ID,
            sharedGroupsIds = listOf(GROUP_ID),
            parentFolderIds = listOf(nestedFolder.id),
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
    assertFalse(viewModel.canEditItem(foreignFile, CURRENT_USER_ID))
  }

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
    assertEquals(file, openedFile)
    assertEquals(true, openedForEditing)
    assertTrue(gameMaster.uiState.value.canEditItems)
  }

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
    assertEquals(file, openedFile)
    assertEquals(false, openedForEditing)
    assertFalse(player.uiState.value.canEditItems)
  }

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

  private fun showScreen(
      repository: FakeWorkspaceRepository,
      role: GroupWorkspaceRole,
      personalItems: List<JDRFile> = emptyList(),
      onItemClicked: (JDRFile, Boolean) -> Unit = { _, _ -> },
  ): GroupWorkspaceViewModel {
    viewModelStore?.clear()
    val viewModel =
        GroupWorkspaceViewModel(
            groupId = GROUP_ID,
            role = role,
            repository = repository,
            authRepository = FakeAuthRepository(),
        )
    viewModelStore = ViewModelStore().also { it.put("workspace", viewModel) }
    composeTestRule.setContent {
      SampleAppTheme {
        GroupWorkSpaceScreen(
            gwViewModel = viewModel,
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

  private class FakeWorkspaceRepository(
      initialFiles: List<JDRFile> = emptyList(),
      private val rootLoadGate: CompletableDeferred<Unit>? = null,
      private val rootLoadFailure: Exception? = null,
  ) : WorkspaceRepository {
    private val fileState = MutableStateFlow(initialFiles.associateBy(JDRFile::id))
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
        parentFolderId: String?,
    ): Result<Unit> {
      val file =
          fileState.value[itemId] ?: return Result.failure(IllegalArgumentException("Missing"))
      fileState.value =
          fileState.value + (itemId to file.copy(sharedGroupsIds = file.sharedGroupsIds - groupId))
      return Result.success(Unit)
    }

    override fun getFileFlow(fileId: String): Flow<JDRFile?> = fileState.map { it[fileId] }

    override fun getPersonalRootFiles(ownerId: String): Flow<List<JDRFile>> =
        fileState.map { files ->
          files.values.filter { it.ownerId == ownerId && it.personalParentId.isEmpty() }
        }

    override fun getGroupRootFiles(groupId: String): Flow<List<JDRFile>> =
        fileState
            .map { files ->
              files.values.filter { file ->
                groupId in file.sharedGroupsIds &&
                    file.parentFolderIds.none { parentId ->
                      files[parentId]?.let { parent ->
                        parent.type == DocumentType.FOLDER && groupId in parent.sharedGroupsIds
                      } == true
                    }
              }
            }
            .onStart {
              rootLoadGate?.await()
              rootLoadFailure?.let { throw it }
            }

    override fun getDocumentsInFolder(folderId: String): Flow<List<JDRFile>> =
        fileState.map { files ->
          files.values.filter { folderId in it.parentFolderIds }
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

  private companion object {
    const val GROUP_ID = "group-ui"
    const val CURRENT_USER_ID = "current-user"
    const val OTHER_USER_ID = "other-user"
  }
}
