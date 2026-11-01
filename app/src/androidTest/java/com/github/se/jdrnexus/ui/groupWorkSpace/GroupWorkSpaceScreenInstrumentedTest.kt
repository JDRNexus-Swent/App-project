// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.github.se.jdrnexus.ui.groupWorkSpace

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
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

    composeTestRule.onNodeWithText("Campaigns").assertIsDisplayed()
    composeTestRule.onNodeWithContentDescription("Campaigns").assertIsDisplayed()
    composeTestRule.onNodeWithText("PLAYER").assertIsDisplayed()
    composeTestRule.onNodeWithText("No files yet").assertIsDisplayed()
  }

  // Verifies that every navigation destination is visible and can be selected.
  @Test
  fun bottomNavigationTabsCanBeSelected() {
    showScreen(WorkspaceFakeRepository(), Role.PLAYER)

    listOf("Home", "Campaigns", "Files", "Map").forEach { label ->
      composeTestRule.onNodeWithText(label).performClick()
      composeTestRule.onNodeWithContentDescription(label).assertIsDisplayed()
    }
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

    composeTestRule.onNodeWithTag("workspace_item_folder", true).performClick()
    composeTestRule.waitUntil(5_000) { viewModel.uiState.value.currentFolderId == folder.id }
    composeTestRule.onNodeWithTag("workspace_item_nested", true).assertIsDisplayed()
    composeTestRule.onNodeWithContentDescription("Go back").performClick()
    composeTestRule.waitUntil(5_000) { viewModel.uiState.value.currentFolderId.isEmpty() }
    composeTestRule.onNodeWithTag("workspace_item_folder", true).assertIsDisplayed()
  }

  // Verifies a loading workspace is shown instead of an empty state.
  @Test
  fun loadingStateIsRendered() {
    val gate = CompletableDeferred<Unit>()
    val loading = showScreen(WorkspaceFakeRepository(rootGate = gate), Role.PLAYER)
    composeTestRule.onNodeWithText("No files yet").assertDoesNotExist()
    assertEquals(LoadState.Loading, loading.uiState.value.loadState)
    gate.complete(Unit)
    composeTestRule.waitUntil(5_000) { loading.uiState.value.loadState == LoadState.Success }
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
    composeTestRule.onNodeWithTag("workspace_add_folder", true).performClick()
    composeTestRule.onNodeWithTag("create_folder_dialog", true).assertIsDisplayed()
    composeTestRule.onNodeWithTag("create_folder_name_input", true).performTextInput("Treasure")
    composeTestRule.onNodeWithTag("create_folder_confirm", true).performClick()

    composeTestRule.waitUntil(5_000) { repository.files.values.any { it.name == "Treasure" } }
    composeTestRule.onNodeWithTag("workspace_item_created-1", true).assertIsDisplayed()
  }

  // Verifies players can add files but are not offered folder creation.
  @Test
  fun playerActionsHideFolderCreation() {
    showScreen(WorkspaceFakeRepository(), Role.PLAYER)

    openActions()
    composeTestRule.onAllNodesWithTag("workspace_add_folder", true).assertCountEquals(0)
    composeTestRule.onNodeWithTag("workspace_add_file", true).assertIsDisplayed()
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
    composeTestRule.onNodeWithTag("workspace_add_file", true).performClick()
    composeTestRule.onNodeWithTag("add_files_dialog", true).assertIsDisplayed()
    composeTestRule.onNodeWithTag("add_files_list", true).assertIsDisplayed()
    composeTestRule.onNodeWithText("Notes").assertIsDisplayed()
    composeTestRule.onAllNodesWithText("Folder").assertCountEquals(0)
    composeTestRule.onAllNodesWithText("Shared").assertCountEquals(0)
    composeTestRule.onAllNodesWithText("Foreign").assertCountEquals(0)
    composeTestRule.onNodeWithTag("add_files_done", true).performClick()
    composeTestRule.onAllNodesWithTag("add_files_dialog", true).assertCountEquals(0)
  }

  // Verifies selecting a personal file shares it and updates the visible workspace.
  @Test
  fun selectingPersonalFileAddsItToWorkspace() {
    val personal = JDRFile("personal", "Notes", ownerId = USER)
    val repository = WorkspaceFakeRepository(listOf(personal))
    showScreen(repository, Role.PLAYER, personalItems = listOf(personal))

    openActions()
    composeTestRule.onNodeWithTag("workspace_add_file", true).performClick()
    composeTestRule.onNodeWithTag("add_files_dialog", true).assertIsDisplayed()
    composeTestRule.onNodeWithText("Notes").performClick()

    composeTestRule.waitUntil(5_000) {
      GROUP in repository.files.getValue("personal").sharedGroupsIds
    }
    composeTestRule.onNodeWithTag("workspace_item_personal", true).assertIsDisplayed()
  }

  // Verifies players see controls for their own files but not another player's files.
  @Test
  fun playerControlsRespectOwnership() {
    val own = file("own", "Own", DocumentType.TEXT, ownerId = USER)
    val foreign = file("foreign", "Foreign", DocumentType.TEXT, ownerId = OTHER)
    showScreen(WorkspaceFakeRepository(listOf(own, foreign)), Role.PLAYER)

    composeTestRule.onNodeWithTag("workspace_edit_own", true).assertIsDisplayed()
    composeTestRule.onNodeWithTag("workspace_remove_own", true).assertIsDisplayed()
    composeTestRule.onNodeWithTag("workspace_visibility_own", true).assertIsDisplayed()
    composeTestRule.onAllNodesWithTag("workspace_edit_foreign", true).assertCountEquals(0)
    composeTestRule.onAllNodesWithTag("workspace_remove_foreign", true).assertCountEquals(0)
    composeTestRule.onAllNodesWithTag("workspace_visibility_foreign", true).assertCountEquals(0)
  }

  // Verifies a player can edit, hide, and remove their own file through tagged controls.
  @Test
  fun playerCanManageOwnFileUsingTaggedActions() {
    val editable =
        file("own-edit", "Own", DocumentType.TEXT, ownerId = USER).copy(content = "Original")
    val removable = file("own-remove", "Remove me", DocumentType.TEXT, ownerId = USER)
    val repository = WorkspaceFakeRepository(listOf(editable, removable))
    showScreen(repository, Role.PLAYER)

    composeTestRule.onNodeWithTag("workspace_edit_own-edit", true).performClick()
    composeTestRule.onNodeWithTag("edit_item_dialog", true).assertIsDisplayed()
    composeTestRule.onNodeWithTag("edit_item_name_input", true).performTextClearance()
    composeTestRule.onNodeWithTag("edit_item_name_input", true).performTextInput("Updated own")
    composeTestRule.onNodeWithTag("edit_item_content_input", true).performTextClearance()
    composeTestRule
        .onNodeWithTag("edit_item_content_input", true)
        .performTextInput("Updated content")
    composeTestRule.onNodeWithTag("edit_item_save", true).performClick()
    composeTestRule.waitUntil(5_000) {
      repository.files.getValue("own-edit").name == "Updated own" &&
          repository.files.getValue("own-edit").content == "Updated content"
    }

    composeTestRule.onNodeWithTag("workspace_visibility_own-edit", true).performClick()
    composeTestRule.waitUntil(5_000) {
      repository.files.getValue("own-edit").placements.getValue(GROUP).isHidden
    }

    composeTestRule.onNodeWithTag("workspace_remove_own-remove", true).performClick()
    composeTestRule.onNodeWithTag("remove_item_dialog", true).assertIsDisplayed()
    composeTestRule.onNodeWithTag("remove_item_confirm", true).performClick()
    composeTestRule.waitUntil(5_000) {
      GROUP !in repository.files.getValue("own-remove").sharedGroupsIds
    }
    composeTestRule.onAllNodesWithTag("workspace_item_own-remove", true).assertCountEquals(0)
  }

  // Verifies Game Masters can edit, hide, and remove another player's file.
  @Test
  fun gameMasterCanManageForeignFile() {
    val editable = file("foreign-edit", "Foreign", DocumentType.TEXT, ownerId = OTHER)
    val removable = file("foreign-remove", "Remove me", DocumentType.TEXT, ownerId = OTHER)
    val repository = WorkspaceFakeRepository(listOf(editable, removable))
    showScreen(repository, Role.GAME_MASTER)

    composeTestRule.onNodeWithTag("workspace_edit_foreign-edit", true).performClick()
    composeTestRule.onNodeWithTag("edit_item_dialog", true).assertIsDisplayed()
    composeTestRule.onNodeWithTag("edit_item_name_input", true).performTextClearance()
    composeTestRule.onNodeWithTag("edit_item_name_input", true).performTextInput("Updated foreign")
    composeTestRule.onNodeWithTag("edit_item_save", true).performClick()
    composeTestRule.waitUntil(5_000) {
      repository.files.getValue("foreign-edit").name == "Updated foreign"
    }
    composeTestRule.onNodeWithTag("workspace_item_foreign-edit", true).assertIsDisplayed()

    composeTestRule.onNodeWithTag("workspace_visibility_foreign-edit", true).performClick()
    composeTestRule.waitUntil(5_000) {
      repository.files.getValue("foreign-edit").placements.getValue(GROUP).isHidden
    }
    composeTestRule.onNodeWithTag("workspace_remove_foreign-remove", true).performClick()
    composeTestRule.onNodeWithTag("remove_item_dialog", true).assertIsDisplayed()
    composeTestRule.onNodeWithTag("remove_item_confirm", true).performClick()
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

    composeTestRule.onNodeWithTag("workspace_edit_notes", true).performClick()
    composeTestRule.onNodeWithTag("edit_item_name_input", true).performTextClearance()
    composeTestRule.onNodeWithTag("edit_item_save", true).performClick()
    composeTestRule.onNodeWithText("The item name cannot be blank.").assertIsDisplayed()
    composeTestRule.onNodeWithTag("edit_item_name_input", true).performTextInput("Updated")
    composeTestRule.onNodeWithTag("edit_item_save", true).performClick()
    composeTestRule.waitUntil(5_000) { repository.files.getValue("notes").name == "Updated" }
    composeTestRule.onNodeWithText("Updated").assertIsDisplayed()
  }

  // Verifies failed operations keep dialogs open and expose the repository error.
  @Test
  fun failedEditDisplaysErrorAndKeepsDialogOpen() {
    val original = file("notes", "Notes", DocumentType.TEXT, ownerId = USER)
    val repository = WorkspaceFakeRepository(listOf(original), operationFailure = "Write denied")
    showScreen(repository, Role.PLAYER)

    composeTestRule.onNodeWithTag("workspace_edit_notes", true).performClick()
    composeTestRule.onNodeWithTag("edit_item_save", true).performClick()
    composeTestRule.waitUntil(5_000) { repository.lastError == "Write denied" }
    composeTestRule.onNodeWithText("Write denied").assertIsDisplayed()
    composeTestRule.onNodeWithText("Edit file").assertIsDisplayed()
  }

  // Verifies operation progress disables dialog controls until the repository completes.
  @Test
  fun operationProgressDisablesEditControls() {
    val original = file("notes", "Notes", DocumentType.TEXT, ownerId = USER)
    val gate = CompletableDeferred<Unit>()
    val repository = WorkspaceFakeRepository(listOf(original), operationGate = gate)
    val viewModel = showScreen(repository, Role.PLAYER)

    composeTestRule.onNodeWithTag("workspace_edit_notes", true).performClick()
    composeTestRule.onNodeWithTag("edit_item_save", true).performClick()
    composeTestRule.waitUntil(5_000) { repository.operationStarted.get() }
    composeTestRule.onNodeWithTag("edit_item_save", true).assertIsNotEnabled()
    composeTestRule.onNodeWithTag("edit_item_cancel", true).assertIsNotEnabled()
    gate.complete(Unit)
    composeTestRule.waitUntil(5_000) {
      !repository.operationStarted.get() &&
          viewModel.uiState.value.loadState == LoadState.Success &&
          viewModel.uiState.value.items.any { it.id == original.id && it.name == original.name }
    }
    composeTestRule.onNodeWithText("Edit file").assertDoesNotExist()
    composeTestRule.onNodeWithText("Notes").assertIsDisplayed()
  }

  private fun openActions() {
    composeTestRule.onNodeWithTag("workspace_actions_fab", true).performClick()
  }

  private fun showScreen(
      repository: WorkspaceFakeRepository,
      role: Role,
      personalItems: List<JDRFile> = emptyList(),
      onBack: () -> Unit = {},
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
    composeTestRule.setContent {
      SampleAppTheme {
        GroupWorkSpaceScreen(
            gwViewModel = viewModel,
            groupId = GROUP,
            groupName = "Ashen Realms",
            onBack = onBack,
            onItemClicked = { _, _ -> },
            personalItems = personalItems,
        )
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
                groupId in it.sharedGroupsIds &&
                    it.placements[groupId]?.folderId == folderId &&
                    !it.placements.getValue(groupId).isHidden
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
