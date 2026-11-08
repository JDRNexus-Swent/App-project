// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.github.se.jdrnexus.ui.search

import com.github.se.jdrnexus.model.personalSpace.CampaignGroup
import com.github.se.jdrnexus.model.personalSpace.DocumentType
import com.github.se.jdrnexus.model.personalSpace.GroupRepository
import com.github.se.jdrnexus.model.personalSpace.JDRFile
import com.github.se.jdrnexus.model.personalSpace.Placement
import com.github.se.jdrnexus.model.personalSpace.WorkspaceRepository
import com.github.se.jdrnexus.model.repository.AuthRepository
import com.github.se.jdrnexus.model.repository.AuthResult
import com.github.se.jdrnexus.model.repository.AuthUser
import com.github.se.jdrnexus.ui.authentication.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DocumentSearchViewModelTest {
  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  private val debounceMillis = 250L
  private val personalFolder = JDRFile("folder", "Campaign", type = DocumentType.FOLDER)
  private val personalFile = JDRFile("personal-file", "Dragon Lore")
  private val nestedCharacter = JDRFile("character", "Dragon Slayer", type = DocumentType.CHARACTER)

  @Test
  fun searchesNamesRecursivelyAndClearsQueryAndResults() = runTest {
    val viewModel =
        DocumentSearchViewModel(
            repository =
                FakeWorkspaceRepository(
                    rootItems = listOf(personalFolder, personalFile),
                    itemsByFolder = mapOf(personalFolder.id to listOf(nestedCharacter)),
                ),
            authRepository = FakeAuthRepository(),
        )

    viewModel.onQueryChanged("dRaGoN")
    assertEquals(DocumentSearchStatus.Loading, viewModel.uiState.value.status)
    advanceTimeBy(debounceMillis)
    runCurrent()

    assertEquals(listOf(personalFile, nestedCharacter), viewModel.uiState.value.results)
    assertEquals(DocumentSearchStatus.Results, viewModel.uiState.value.status)

    viewModel.clearQuery()
    assertEquals("", viewModel.uiState.value.query)
    assertTrue(viewModel.uiState.value.results.isEmpty())
    assertEquals(DocumentSearchStatus.Blank, viewModel.uiState.value.status)
  }

  @Test
  fun reportsNoResultsAndSearchFailure() = runTest {
    val viewModel =
        DocumentSearchViewModel(
            repository = FakeWorkspaceRepository(rootItems = listOf(personalFile)),
            authRepository = FakeAuthRepository(),
        )

    viewModel.onQueryChanged("missing")
    advanceTimeBy(debounceMillis)
    runCurrent()
    assertEquals(DocumentSearchStatus.NoResults, viewModel.uiState.value.status)

    val failingViewModel =
        DocumentSearchViewModel(
            repository = FakeWorkspaceRepository(rootFailure = IllegalStateException("offline")),
            authRepository = FakeAuthRepository(),
        )
    failingViewModel.onQueryChanged("Dragon")
    advanceTimeBy(debounceMillis)
    runCurrent()

    assertEquals(
        DocumentSearchStatus.Error(SearchError.SEARCH_FAILED),
        failingViewModel.uiState.value.status,
    )
  }

  @Test
  fun groupSearchExcludesHiddenDocumentsButRetainsPlayersOwnDocuments() = runTest {
    val groupId = "group"
    val hiddenFolder =
        groupFile("hidden-folder", groupId, "gm", groupId, DocumentType.FOLDER, hidden = true)
    val ownHiddenFile =
        groupFile("own-hidden", groupId, "player", hiddenFolder.id, DocumentType.TEXT)
    val otherHiddenFile =
        groupFile("other-hidden", groupId, "gm", groupId, DocumentType.TEXT, hidden = true)
    val visibleFile = groupFile("visible", groupId, "gm", groupId, DocumentType.TEXT)
    val repository =
        FakeWorkspaceRepository(
            groupItems =
                mapOf(
                    groupId to listOf(hiddenFolder, otherHiddenFile, visibleFile),
                    hiddenFolder.id to listOf(ownHiddenFile),
                )
        )
    val viewModel =
        DocumentSearchViewModel(
            scope = DocumentSearchScope.Group(groupId),
            repository = repository,
            authRepository = FakeAuthRepository(AuthUser("player", null, null)),
            groupRepository =
                FakeGroupRepository(
                    CampaignGroup(
                        id = groupId,
                        gameMasterId = "gm",
                        participantIds = listOf("player"),
                    )
                ),
        )

    viewModel.onQueryChanged("hidden")
    advanceTimeBy(debounceMillis)
    runCurrent()

    assertEquals(listOf(ownHiddenFile), viewModel.uiState.value.results)
  }

  @Test
  fun groupSearchDeniesNonMembers() = runTest {
    val viewModel =
        DocumentSearchViewModel(
            scope = DocumentSearchScope.Group("group"),
            repository = FakeWorkspaceRepository(),
            authRepository = FakeAuthRepository(),
            groupRepository = FakeGroupRepository(CampaignGroup(id = "group")),
        )

    viewModel.onQueryChanged("file")
    advanceTimeBy(debounceMillis)
    runCurrent()

    assertEquals(
        DocumentSearchStatus.Error(SearchError.GROUP_ACCESS_DENIED),
        viewModel.uiState.value.status,
    )
  }

  private fun groupFile(
      id: String,
      groupId: String,
      ownerId: String,
      parentId: String,
      type: DocumentType,
      hidden: Boolean = false,
  ) =
      JDRFile(
          id = id,
          name = id,
          ownerId = ownerId,
          type = type,
          sharedGroupsIds = listOf(groupId),
          placements = mapOf(groupId to Placement(parentId, hidden)),
      )

  private class FakeWorkspaceRepository(
      private val rootItems: List<JDRFile> = emptyList(),
      private val itemsByFolder: Map<String, List<JDRFile>> = emptyMap(),
      private val groupItems: Map<String, List<JDRFile>> = emptyMap(),
      private val rootFailure: Exception? = null,
  ) : WorkspaceRepository {
    override fun getNewUid(): String = "new-id"

    override suspend fun createFile(file: JDRFile): Result<Unit> = Result.success(Unit)

    override suspend fun updateFile(file: JDRFile): Result<Unit> = Result.success(Unit)

    override suspend fun deleteFile(fileId: String): Result<Unit> = Result.success(Unit)

    override suspend fun getFile(fileId: String): Result<JDRFile?> = Result.success(null)

    override suspend fun removeItemFromGroup(
        itemId: String,
        groupId: String,
        parentFolderId: String,
    ): Result<Unit> = Result.success(Unit)

    override suspend fun addItemToGroup(
        itemId: String,
        groupId: String,
        folderId: String,
        isHidden: Boolean,
        includeContent: Boolean,
    ): Result<Unit> = Result.success(Unit)

    override suspend fun setItemHidden(
        itemId: String,
        groupId: String,
        isHidden: Boolean,
    ): Result<Unit> = Result.success(Unit)

    override fun getFileFlow(fileId: String): Flow<JDRFile?> = flowOf(null)

    override fun getPersonalRootFiles(ownerId: String): Flow<List<JDRFile>> =
        rootFailure?.let { failure -> flow { throw failure } } ?: flowOf(rootItems)

    override fun getPersonalFolderFiles(folderId: String, ownerId: String): Flow<List<JDRFile>> =
        flowOf(itemsByFolder[folderId].orEmpty())

    override fun getGroupFolderFiles(folderId: String, groupId: String): Flow<List<JDRFile>> =
        flowOf(groupItems[folderId].orEmpty())
  }

  private class FakeGroupRepository(private val group: CampaignGroup?) : GroupRepository {
    override fun getNewGroupId(): String = "new-group-id"

    override suspend fun createGroup(group: CampaignGroup): Result<Unit> = Result.success(Unit)

    override suspend fun updateGroup(group: CampaignGroup): Result<Unit> = Result.success(Unit)

    override suspend fun deleteGroup(groupId: String): Result<Unit> = Result.success(Unit)

    override suspend fun getGroup(groupId: String): Result<CampaignGroup> =
        group?.let { Result.success(it) } ?: Result.failure(IllegalStateException())

    override suspend fun addUserToGroup(groupId: String, userId: String): Result<Unit> =
        Result.success(Unit)

    override suspend fun removeUserFromGroup(groupId: String, userId: String): Result<Unit> =
        Result.success(Unit)

    override fun getGroupFlow(groupId: String): Flow<CampaignGroup?> = flowOf(group)

    override fun getUserGroups(userId: String): Flow<List<CampaignGroup>> =
        flowOf(listOfNotNull(group))
  }

  private class FakeAuthRepository(user: AuthUser? = AuthUser("player", null, null)) :
      AuthRepository {
    override val authState = MutableStateFlow(user)

    override suspend fun signUpWithEmail(
        email: String,
        password: String,
        username: String,
    ): AuthResult<AuthUser> = error("Not used")

    override suspend fun signInWithEmail(
        email: String,
        password: String,
    ): AuthResult<AuthUser> = error("Not used")

    override suspend fun signInWithGoogle(idToken: String): AuthResult<AuthUser> = error("Not used")

    override suspend fun signOut(): AuthResult<Unit> = error("Not used")
  }
}
