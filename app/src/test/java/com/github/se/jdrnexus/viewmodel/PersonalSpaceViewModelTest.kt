// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.github.se.jdrnexus.viewmodel

import android.os.Looper
import com.github.se.jdrnexus.model.personalSpace.DocumentType
import com.github.se.jdrnexus.model.personalSpace.JDRFile
import com.github.se.jdrnexus.model.personalSpace.WorkspaceRepository
import com.github.se.jdrnexus.model.repository.AuthRepository
import com.github.se.jdrnexus.model.repository.AuthResult
import com.github.se.jdrnexus.model.repository.AuthUser
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onStart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class PersonalSpaceViewModelTest {
  // ============ Shared test data ============
  private val ownerId = "authenticated-user-42"
  private val rootFolder = JDRFile("folder-1", "Folder", type = DocumentType.FOLDER)
  private val rootFile = JDRFile("file-1", "File")
  private val characterFile = JDRFile("character-1", "Character", type = DocumentType.CHARACTER)
  private val nestedFolder = JDRFile("folder-2", "Nested folder", type = DocumentType.FOLDER)
  private val nestedFile = JDRFile("file-2", "Nested file")

  // ============ Test coroutine utility ============

  /** Runs queued main-looper work so ViewModel coroutine updates are observable in assertions. */
  private fun idleMainLooper() {
    shadowOf(Looper.getMainLooper()).idle()
  }

  // ============ Workspace repository fake ============
  private class FakePersonalSpaceRepository(
      private val rootItems: List<JDRFile> = emptyList(),
      private val itemsByFolder: Map<String, List<JDRFile>> = emptyMap(),
      private val failure: Exception? = null,
      private val createFailure: Exception? = null,
      private val fetchGate: CompletableDeferred<Unit>? = null,
      private val folderFetchGate: CompletableDeferred<Unit>? = null,
  ) : WorkspaceRepository {
    val requestedRootOwners = mutableListOf<String>()
    val folderFetchRequests = mutableListOf<String>()
    val createdFiles = mutableListOf<JDRFile>()
    val rootItemsFlow = MutableStateFlow(rootItems)

    /** Delays collection when configured; then keeps emitting repository updates. */
    private fun <T> gatedFlow(
        flow: Flow<T>,
        gate: CompletableDeferred<Unit>? = fetchGate,
    ): Flow<T> = flow.onStart {
      gate?.await()
      failure?.let { throw it }
    }

    // ============ WorkspaceRepository overrides ============
    /** Records root requests and emits the configured root contents. */
    override fun getPersonalRootFiles(ownerId: String): Flow<List<JDRFile>> {
      requestedRootOwners.add(ownerId)
      return gatedFlow(rootItemsFlow)
    }

    /** Records the requested folder ID and emits only that folder's configured contents. */
    override fun getDocumentsInFolder(folderId: String): Flow<List<JDRFile>> {
      folderFetchRequests.add(folderId)
      return gatedFlow(MutableStateFlow(itemsByFolder[folderId].orEmpty()), folderFetchGate)
    }

    override fun getNewUid(): String = "created-folder-id"

    /** Group-root data is outside these tests, so the fake emits an empty list. */
    override fun getGroupRootFiles(groupId: String): Flow<List<JDRFile>> = flowOf(emptyList())

    /** File-detail lookups are not exercised by Personal Space list behavior. */
    override fun getFileFlow(fileId: String): Flow<JDRFile?> = flowOf(null)

    /** Captures the exact file submitted and can simulate a create failure. */
    override suspend fun createFile(file: JDRFile): Result<Unit> {
      createdFiles.add(file)
      return createFailure?.let { Result.failure(it) } ?: Result.success(Unit)
    }

    // ============ Unused repository overrides ============
    /** These operations are unrelated to the tested list and folder-creation interactions. */
    override suspend fun updateFile(file: JDRFile): Result<Unit> = Result.success(Unit)

    override suspend fun deleteFile(fileId: String): Result<Unit> = Result.success(Unit)

    override suspend fun getFile(fileId: String): Result<JDRFile?> = Result.success(null)
  }

  private class FakeAuthRepository(
      private val user: AuthUser = AuthUser("authenticated-user-42", "player@example.com", "Player")
  ) : AuthRepository {
    override val authState: StateFlow<AuthUser?> = MutableStateFlow(user)

    override suspend fun signUpWithEmail(
        email: String,
        password: String,
        username: String,
    ): AuthResult<AuthUser> = AuthResult.Success(user)

    override suspend fun signInWithEmail(email: String, password: String): AuthResult<AuthUser> =
        AuthResult.Success(user)

    override suspend fun signInWithGoogle(idToken: String): AuthResult<AuthUser> =
        AuthResult.Success(user)

    override suspend fun signOut(): AuthResult<Unit> = AuthResult.Success(Unit)
  }

  // ============ ViewModel behavior tests ============

  /** Verifies the initial Loading state and the exact root results after collection completes. */
  @Test
  fun initializesWithRootItems() {
    val fetchGate = CompletableDeferred<Unit>()
    val repository =
        FakePersonalSpaceRepository(
            rootItems = listOf(rootFolder, rootFile),
            fetchGate = fetchGate,
        )

    val viewModel = PersonalSpaceViewModel(repository, FakeAuthRepository())
    assertEquals(LoadState.Loading, viewModel.uiState.value.loadState)
    assertEquals("", viewModel.uiState.value.currentFolderId)
    assertEquals(emptyList<JDRFile>(), viewModel.uiState.value.items)
    assertFalse(viewModel.uiState.value.canNavigateUp)

    idleMainLooper()
    assertEquals(listOf(ownerId), repository.requestedRootOwners)
    assertTrue(repository.folderFetchRequests.isEmpty())

    fetchGate.complete(Unit)
    idleMainLooper()

    assertEquals(
        PersonalSpaceUiState(
            items = listOf(rootFolder, rootFile),
            currentFolderId = "",
            loadState = LoadState.Success,
        ),
        viewModel.uiState.value,
    )

    repository.rootItemsFlow.value = listOf(rootFile)
    idleMainLooper()
    assertEquals(listOf(rootFile), viewModel.uiState.value.items)
  }

  /** Verifies folder entry, nested back navigation, and the exact repository fetch sequence. */
  @Test
  fun folderClickLoadsItsItemsAndNavigateUpReturnsToParent() {
    val folderFetchGate = CompletableDeferred<Unit>()
    val repository =
        FakePersonalSpaceRepository(
            rootItems = listOf(rootFolder, rootFile),
            itemsByFolder =
                mapOf(
                    rootFolder.id to listOf(nestedFolder),
                    nestedFolder.id to listOf(nestedFile),
                ),
            folderFetchGate = folderFetchGate,
        )
    val viewModel = PersonalSpaceViewModel(repository, FakeAuthRepository())
    idleMainLooper()

    viewModel.onItemClicked(rootFolder) {}
    assertEquals(rootFolder.id, viewModel.uiState.value.currentFolderId)
    assertTrue(viewModel.uiState.value.canNavigateUp)
    assertEquals(LoadState.Loading, viewModel.uiState.value.loadState)
    idleMainLooper()
    assertEquals(LoadState.Loading, viewModel.uiState.value.loadState)
    folderFetchGate.complete(Unit)
    idleMainLooper()

    assertEquals(rootFolder.id, viewModel.uiState.value.currentFolderId)
    assertEquals(listOf(nestedFolder), viewModel.uiState.value.items)
    assertEquals(LoadState.Success, viewModel.uiState.value.loadState)
    assertEquals(listOf(rootFolder.id), repository.folderFetchRequests)

    viewModel.onItemClicked(nestedFolder) {}
    idleMainLooper()

    assertEquals(nestedFolder.id, viewModel.uiState.value.currentFolderId)
    assertEquals(listOf(nestedFile), viewModel.uiState.value.items)
    assertEquals(listOf(rootFolder.id, nestedFolder.id), repository.folderFetchRequests)

    viewModel.onNavigateUp()
    idleMainLooper()

    assertEquals(rootFolder.id, viewModel.uiState.value.currentFolderId)
    assertEquals(listOf(nestedFolder), viewModel.uiState.value.items)
    assertTrue(viewModel.uiState.value.canNavigateUp)
    assertEquals(LoadState.Success, viewModel.uiState.value.loadState)

    viewModel.onNavigateUp()
    idleMainLooper()

    assertEquals("", viewModel.uiState.value.currentFolderId)
    assertEquals(listOf(rootFolder, rootFile), viewModel.uiState.value.items)
    assertFalse(viewModel.uiState.value.canNavigateUp)
    assertEquals(LoadState.Success, viewModel.uiState.value.loadState)
    viewModel.onNavigateUp()
    assertEquals("", viewModel.uiState.value.currentFolderId)
    assertEquals(listOf(rootFolder, rootFile), viewModel.uiState.value.items)
    assertEquals(listOf(ownerId, ownerId), repository.requestedRootOwners)
    assertEquals(
        listOf(rootFolder.id, nestedFolder.id, rootFolder.id),
        repository.folderFetchRequests,
    )
  }

  /** Verifies that all non-folder file types are sent to document navigation, not folder fetch. */
  @Test
  fun clickingAnyNonFolderNavigatesToItsDocument() {
    val repository = FakePersonalSpaceRepository(rootItems = listOf(rootFile, characterFile))
    val viewModel = PersonalSpaceViewModel(repository, FakeAuthRepository())
    idleMainLooper()

    val openedDocuments = mutableListOf<String>()
    viewModel.onItemClicked(rootFile) { openedDocuments.add(it) }
    viewModel.onItemClicked(characterFile) { openedDocuments.add(it) }

    assertEquals("", viewModel.uiState.value.currentFolderId)
    assertEquals(listOf(rootFile.id, characterFile.id), openedDocuments)
    assertEquals(listOf(ownerId), repository.requestedRootOwners)
  }

  /** Verifies new-file navigation receives the current folder and closes the add menu. */
  @Test
  fun createFileCallbackCarriesCurrentFolder() {
    val repository =
        FakePersonalSpaceRepository(
            rootItems = listOf(rootFolder),
            itemsByFolder = mapOf(rootFolder.id to listOf(nestedFile)),
        )
    val viewModel = PersonalSpaceViewModel(repository, FakeAuthRepository())
    idleMainLooper()
    viewModel.onItemClicked(rootFolder) {}
    idleMainLooper()

    var selectedParentFolderId = ""
    viewModel.onAddClicked()
    viewModel.onNewFileSelected { selectedParentFolderId = it }

    assertEquals(rootFolder.id, selectedParentFolderId)
    assertFalse(viewModel.uiState.value.isAddMenuExpanded)
  }

  /** Verifies add-menu, new-folder dialog, draft-name, and dismissal state transitions. */
  @Test
  fun addMenuAndFolderDialogUpdateUiState() {
    val viewModel = PersonalSpaceViewModel(FakePersonalSpaceRepository(), FakeAuthRepository())
    idleMainLooper()

    viewModel.onAddClicked()
    assertTrue(viewModel.uiState.value.isAddMenuExpanded)
    viewModel.onAddMenuDismissed()
    assertFalse(viewModel.uiState.value.isAddMenuExpanded)
    viewModel.onAddClicked()

    viewModel.onNewFolderSelected()
    assertFalse(viewModel.uiState.value.isAddMenuExpanded)
    assertTrue(viewModel.uiState.value.isNewFolderDialogOpen)

    viewModel.onNewFolderNameChanged("New folder")
    assertEquals("New folder", viewModel.uiState.value.newFolderName)
    viewModel.onNewFolderDialogDismissed()
    assertFalse(viewModel.uiState.value.isNewFolderDialogOpen)
    assertEquals("", viewModel.uiState.value.newFolderName)
    assertEquals("", viewModel.uiState.value.newFolderError)
  }

  /** Verifies the saved folder's full data, including its trimmed name and current parent. */
  @Test
  fun createFolderUsesCurrentFolderAndTrimsName() {
    val repository = FakePersonalSpaceRepository(rootItems = listOf(rootFolder))
    val viewModel = PersonalSpaceViewModel(repository, FakeAuthRepository())
    idleMainLooper()
    viewModel.onItemClicked(rootFolder) {}
    idleMainLooper()
    viewModel.onNewFolderSelected()
    viewModel.onNewFolderNameChanged("  New folder  ")

    viewModel.createFolder()
    idleMainLooper()

    assertEquals(1, repository.createdFiles.size)
    assertNotNull(repository.createdFiles.single().id)
    assertNotEquals("", repository.createdFiles.single().id)
    assertEquals("created-folder-id", repository.createdFiles.single().id)
    assertEquals("New folder", repository.createdFiles.single().name)
    assertEquals(ownerId, repository.createdFiles.single().ownerId)
    assertEquals(listOf(rootFolder.id), repository.createdFiles.single().parentFolderIds)
    assertEquals(DocumentType.FOLDER, repository.createdFiles.single().type)
    assertFalse(viewModel.uiState.value.isNewFolderDialogOpen)
    assertEquals("", viewModel.uiState.value.newFolderError)
  }

  @Test
  fun createFolderAtRootHasNoParentFolders() {
    val repository = FakePersonalSpaceRepository()
    val viewModel = PersonalSpaceViewModel(repository, FakeAuthRepository())
    idleMainLooper()
    viewModel.onNewFolderSelected()
    viewModel.onNewFolderNameChanged("Root folder")

    viewModel.createFolder()
    idleMainLooper()

    assertEquals(1, repository.createdFiles.size)
    assertNotNull(repository.createdFiles.single().id)
    assertNotEquals("", repository.createdFiles.single().id)
    assertEquals("created-folder-id", repository.createdFiles.single().id)
    assertEquals("Root folder", repository.createdFiles.single().name)
    assertEquals(ownerId, repository.createdFiles.single().ownerId)
    assertEquals(emptyList<String>(), repository.createdFiles.single().parentFolderIds)
    assertEquals(DocumentType.FOLDER, repository.createdFiles.single().type)
    assertFalse(viewModel.uiState.value.isNewFolderDialogOpen)
    assertEquals("", viewModel.uiState.value.newFolderError)
  }

  /** Verifies blank-name validation and that repository failures remain visible in the dialog. */
  @Test
  fun createFolderShowsValidationAndRepositoryErrors() {
    val repository =
        FakePersonalSpaceRepository(createFailure = IllegalStateException("Create denied"))
    val viewModel = PersonalSpaceViewModel(repository, FakeAuthRepository())
    idleMainLooper()
    viewModel.onNewFolderSelected()

    viewModel.createFolder()
    assertEquals("Folder name cannot be empty.", viewModel.uiState.value.newFolderError)

    viewModel.onNewFolderNameChanged("Folder")
    assertEquals("", viewModel.uiState.value.newFolderError)
    viewModel.createFolder()
    idleMainLooper()

    assertEquals("Create denied", viewModel.uiState.value.newFolderError)
    assertTrue(viewModel.uiState.value.isNewFolderDialogOpen)
  }

  /** Verifies fetch failures expose their message and leave the failed directory's items empty. */
  @Test
  fun fetchFailureSetsErrorState() {
    val fetchGate = CompletableDeferred<Unit>()
    val repository =
        FakePersonalSpaceRepository(
            failure = IllegalStateException("Permission denied"),
            fetchGate = fetchGate,
        )
    val viewModel = PersonalSpaceViewModel(repository, FakeAuthRepository())

    assertEquals(LoadState.Loading, viewModel.uiState.value.loadState)
    idleMainLooper()
    assertEquals(listOf(ownerId), repository.requestedRootOwners)
    assertTrue(repository.folderFetchRequests.isEmpty())

    fetchGate.complete(Unit)
    idleMainLooper()

    assertEquals(LoadState.Error("Permission denied"), viewModel.uiState.value.loadState)
    assertEquals(emptyList<JDRFile>(), viewModel.uiState.value.items)
    assertEquals("", viewModel.uiState.value.currentFolderId)
    assertFalse(viewModel.uiState.value.canNavigateUp)
  }
}
