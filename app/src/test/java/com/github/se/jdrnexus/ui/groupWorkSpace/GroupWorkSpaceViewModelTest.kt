// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.github.se.jdrnexus.ui.groupWorkSpace

import com.github.se.jdrnexus.model.personalSpace.DocumentType
import com.github.se.jdrnexus.model.personalSpace.JDRFile
import com.github.se.jdrnexus.model.personalSpace.WorkspaceRepository
import com.github.se.jdrnexus.model.repository.AuthRepository
import com.github.se.jdrnexus.model.repository.AuthResult
import com.github.se.jdrnexus.model.repository.AuthUser
import com.github.se.jdrnexus.viewmodel.LoadState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class GroupWorkSpaceViewModelTest {
  @get:Rule val mainDispatcherRule = GroupWorkspaceMainDispatcherRule()

  @Test
  fun initializesWithGroupRootItemsAndPermissions() = runTest {
    val folder =
        JDRFile(
            id = "folder-1",
            name = "Folder",
            ownerId = OWNER_ID,
            type = DocumentType.FOLDER,
            sharedGroupsIds = listOf(GROUP_ID),
        )
    val file =
        JDRFile(
            id = "file-1",
            name = "File",
            ownerId = OWNER_ID,
            sharedGroupsIds = listOf(GROUP_ID),
        )
    val repository = FakeWorkspaceRepository(listOf(folder, file))

    val viewModel = viewModel(repository, GroupWorkspaceRole.GAME_MASTER)
    assertEquals(LoadState.Loading, viewModel.uiState.value.loadState)
    assertEquals(emptyList<JDRFile>(), viewModel.uiState.value.items)
    assertTrue(viewModel.uiState.value.canCreateFolder)
    assertTrue(viewModel.uiState.value.canAddFile)
    assertTrue(viewModel.uiState.value.canEditItems)

    runCurrent()

    assertEquals(listOf(GROUP_ID), repository.groupRootRequests)
    assertEquals(listOf(folder, file), viewModel.uiState.value.items)
    assertEquals(LoadState.Success, viewModel.uiState.value.loadState)
    assertFalse(viewModel.uiState.value.isOperationInProgress)
    assertNull(viewModel.uiState.value.errorMessage)
  }

  @Test
  fun emptyWorkspaceLoadsAsSuccessfulEmptyList() = runTest {
    val viewModel = viewModel(FakeWorkspaceRepository(), GroupWorkspaceRole.PLAYER)

    runCurrent()

    assertEquals(emptyList<JDRFile>(), viewModel.uiState.value.items)
    assertEquals(LoadState.Success, viewModel.uiState.value.loadState)
    assertFalse(viewModel.uiState.value.canCreateFolder)
    assertTrue(viewModel.uiState.value.canAddFile)
    assertFalse(viewModel.uiState.value.canEditItems)
  }

  @Test
  fun loadingFolderClearsPreviousItemsAndDisplaysFolderReferences() = runTest {
    val rootFile = JDRFile(id = "root", name = "Root", sharedGroupsIds = listOf(GROUP_ID))
    val folder =
        JDRFile(id = "folder", type = DocumentType.FOLDER, sharedGroupsIds = listOf(GROUP_ID))
    val child =
        JDRFile(
            id = "child",
            name = "Child",
            sharedGroupsIds = listOf(GROUP_ID),
            parentFolderIds = listOf(folder.id),
        )
    val repository = FakeWorkspaceRepository(listOf(rootFile, folder), listOf(child))
    val viewModel = viewModel(repository, GroupWorkspaceRole.GAME_MASTER)
    runCurrent()

    viewModel.loadWorkspace("folder")
    assertEquals("folder", viewModel.uiState.value.currentFolderId)
    assertEquals(emptyList<JDRFile>(), viewModel.uiState.value.items)
    assertEquals(LoadState.Loading, viewModel.uiState.value.loadState)
    runCurrent()

    assertEquals(listOf("folder"), repository.folderRequests)
    assertEquals(listOf(child), viewModel.uiState.value.items)
    assertEquals(LoadState.Success, viewModel.uiState.value.loadState)
  }

  @Test
  fun folderChangeCancelsOldLoadAndDoesNotRestoreStaleItems() = runTest {
    val slowFolder =
        JDRFile(id = "slow", type = DocumentType.FOLDER, sharedGroupsIds = listOf(GROUP_ID))
    val fastFolder =
        JDRFile(id = "fast", type = DocumentType.FOLDER, sharedGroupsIds = listOf(GROUP_ID))
    val slowItem =
        JDRFile(
            id = "slow-item",
            sharedGroupsIds = listOf(GROUP_ID),
            parentFolderIds = listOf("slow"),
        )
    val fastItem =
        JDRFile(
            id = "fast-item",
            sharedGroupsIds = listOf(GROUP_ID),
            parentFolderIds = listOf("fast"),
        )
    val slowGate = CompletableDeferred<Unit>()
    val repository =
        FakeWorkspaceRepository(
            initialFiles = listOf(slowFolder, fastFolder),
            folderItems = listOf(slowItem, fastItem),
            folderGates = mapOf("slow" to slowGate),
        )
    val viewModel = viewModel(repository, GroupWorkspaceRole.PLAYER)
    runCurrent()

    viewModel.loadWorkspace("slow")
    runCurrent()
    viewModel.loadWorkspace("fast")
    runCurrent()
    assertEquals(listOf(fastItem), viewModel.uiState.value.items)

    slowGate.complete(Unit)
    runCurrent()
    assertEquals("fast", viewModel.uiState.value.currentFolderId)
    assertEquals(listOf(fastItem), viewModel.uiState.value.items)
    assertEquals(LoadState.Success, viewModel.uiState.value.loadState)
  }

  @Test
  fun staleLoadFailureCannotOverwriteNewerSuccessfulLoad() = runTest {
    val staleGate = CompletableDeferred<Unit>()
    val staleFolder =
        JDRFile(id = "stale-folder", type = DocumentType.FOLDER, sharedGroupsIds = listOf(GROUP_ID))
    val activeFolder =
        JDRFile(
            id = "active-folder",
            type = DocumentType.FOLDER,
            sharedGroupsIds = listOf(GROUP_ID),
        )
    val staleItem =
        JDRFile(
            id = "stale",
            sharedGroupsIds = listOf(GROUP_ID),
            parentFolderIds = listOf("stale-folder"),
        )
    val activeItem =
        JDRFile(
            id = "active",
            sharedGroupsIds = listOf(GROUP_ID),
            parentFolderIds = listOf("active-folder"),
        )
    val repository =
        FakeWorkspaceRepository(
            initialFiles = listOf(staleFolder, activeFolder),
            folderItems = listOf(staleItem, activeItem),
            folderFailureGates = mapOf("stale-folder" to staleGate),
            folderFailures = mapOf("stale-folder" to IllegalStateException("Stale failure")),
        )
    val viewModel = viewModel(repository, GroupWorkspaceRole.PLAYER)
    runCurrent()

    viewModel.loadWorkspace("stale-folder")
    runCurrent()
    viewModel.loadWorkspace("active-folder")
    runCurrent()
    assertEquals(listOf(activeItem), viewModel.uiState.value.items)

    staleGate.complete(Unit)
    runCurrent()

    assertEquals("active-folder", viewModel.uiState.value.currentFolderId)
    assertEquals(listOf(activeItem), viewModel.uiState.value.items)
    assertEquals(LoadState.Success, viewModel.uiState.value.loadState)
  }

  @Test
  fun loadFailureIsVisibleAndRetryClearsIt() = runTest {
    val repository =
        FakeWorkspaceRepository(
            initialFiles = emptyList(),
            rootFailure = IllegalStateException("Load denied"),
        )
    val viewModel = viewModel(repository, GroupWorkspaceRole.PLAYER)
    runCurrent()

    assertEquals(LoadState.Error("Load denied"), viewModel.uiState.value.loadState)
    assertEquals(emptyList<JDRFile>(), viewModel.uiState.value.items)

    repository.rootFailure = null
    repository.replaceFiles(listOf(JDRFile(id = "recovered", sharedGroupsIds = listOf(GROUP_ID))))
    viewModel.loadWorkspace()
    runCurrent()

    assertEquals(LoadState.Success, viewModel.uiState.value.loadState)
    assertEquals(listOf("recovered"), viewModel.uiState.value.items.map(JDRFile::id))
  }

  @Test
  fun createFolderPersistsTrimmedFolderInCurrentGroupFolder() = runTest {
    val parent =
        JDRFile(
            id = "parent",
            type = DocumentType.FOLDER,
            sharedGroupsIds = listOf(GROUP_ID),
            parentFolderIds = emptyList(),
        )
    val repository = FakeWorkspaceRepository(listOf(parent))
    val viewModel = viewModel(repository, GroupWorkspaceRole.GAME_MASTER)
    runCurrent()
    viewModel.loadWorkspace(parent.id)
    runCurrent()

    viewModel.createFolder("  New folder  ")
    runCurrent()

    val created = repository.createdFiles.single()
    assertEquals("new-folder-1", created.id)
    assertEquals("New folder", created.name)
    assertEquals(OWNER_ID, created.ownerId)
    assertEquals(listOf(GROUP_ID), created.sharedGroupsIds)
    assertEquals(listOf(parent.id), created.parentFolderIds)
    assertEquals("", created.personalParentId)
    assertEquals(DocumentType.FOLDER, created.type)
    assertEquals(listOf(created), viewModel.uiState.value.items)
    assertFalse(viewModel.uiState.value.isOperationInProgress)
    assertNull(viewModel.uiState.value.errorMessage)
  }

  @Test
  fun createFolderAtRootHasNoParentFolderReference() = runTest {
    val repository = FakeWorkspaceRepository()
    val viewModel = viewModel(repository, GroupWorkspaceRole.GAME_MASTER)
    runCurrent()

    viewModel.createFolder("Root folder")
    runCurrent()

    assertEquals(emptyList<String>(), repository.createdFiles.single().parentFolderIds)
    assertEquals(listOf(GROUP_ID), repository.createdFiles.single().sharedGroupsIds)
    assertEquals(listOf(repository.createdFiles.single()), viewModel.uiState.value.items)
  }

  @Test
  fun queuedFolderCreationUsesFolderSelectedWhenRequested() = runTest {
    val folderA =
        JDRFile(id = "folder-a", type = DocumentType.FOLDER, sharedGroupsIds = listOf(GROUP_ID))
    val folderB =
        JDRFile(id = "folder-b", type = DocumentType.FOLDER, sharedGroupsIds = listOf(GROUP_ID))
    val blocker = JDRFile(id = "blocker", ownerId = OWNER_ID)
    val repository = FakeWorkspaceRepository(listOf(folderA, folderB, blocker))
    val operationGate = CompletableDeferred<Unit>()
    repository.updateGate = { if (it.id == blocker.id) operationGate.await() }
    val viewModel = viewModel(repository, GroupWorkspaceRole.GAME_MASTER)
    runCurrent()

    viewModel.loadWorkspace(folderA.id)
    runCurrent()

    viewModel.addFile(blocker.id)
    runCurrent()
    assertEquals(listOf(blocker.id), repository.updatedFiles.map(JDRFile::id))

    viewModel.createFolder("Nested folder")
    runCurrent()
    assertTrue(viewModel.uiState.value.isOperationInProgress)
    assertTrue(repository.createdFiles.isEmpty())

    viewModel.loadWorkspace(folderB.id)
    runCurrent()

    operationGate.complete(Unit)
    runCurrent()

    val createdFolder = repository.createdFiles.single()
    assertEquals(listOf(folderA.id), createdFolder.parentFolderIds)
    assertEquals(folderB.id, viewModel.uiState.value.currentFolderId)
    assertTrue(viewModel.uiState.value.items.isEmpty())
    assertFalse(viewModel.uiState.value.isOperationInProgress)
  }

  @Test
  fun blankFolderNameIsRejectedWithoutRepositoryMutation() = runTest {
    val repository = FakeWorkspaceRepository()
    val viewModel = viewModel(repository, GroupWorkspaceRole.GAME_MASTER)
    runCurrent()

    viewModel.createFolder("   ")
    runCurrent()

    assertTrue(repository.createdFiles.isEmpty())
    assertTrue(viewModel.uiState.value.errorMessage.orEmpty().isNotBlank())
    assertFalse(viewModel.uiState.value.isOperationInProgress)
  }

  @Test
  fun playerCannotCreateFolder() = runTest {
    val repository = FakeWorkspaceRepository()
    val viewModel = viewModel(repository, GroupWorkspaceRole.PLAYER)
    runCurrent()

    viewModel.createFolder("Forbidden")
    runCurrent()

    assertTrue(repository.createdFiles.isEmpty())
    assertTrue(viewModel.uiState.value.errorMessage.orEmpty().isNotBlank())
    assertFalse(viewModel.uiState.value.isOperationInProgress)
  }

  @Test
  fun gameMasterAddsFileAndPreservesExistingReferences() = runTest {
    val targetFolder =
        JDRFile(
            id = "target-folder",
            type = DocumentType.FOLDER,
            sharedGroupsIds = listOf(GROUP_ID),
        )
    val file =
        JDRFile(
            id = "file",
            name = "Document",
            ownerId = "another-owner",
            sharedGroupsIds = listOf("other-group", GROUP_ID),
            parentFolderIds = listOf("other-folder"),
            type = DocumentType.CHARACTER,
            content = "sheet",
        )
    val repository = FakeWorkspaceRepository(listOf(file, targetFolder))
    val viewModel = viewModel(repository, GroupWorkspaceRole.GAME_MASTER)
    runCurrent()
    viewModel.loadWorkspace("target-folder")
    runCurrent()

    viewModel.addFile(file.id)
    runCurrent()

    assertEquals(
        file.copy(
            sharedGroupsIds = listOf("other-group", GROUP_ID),
            parentFolderIds = listOf("other-folder", "target-folder"),
        ),
        repository.updatedFiles.single(),
    )
    assertEquals(listOf(file.id), viewModel.uiState.value.items.map(JDRFile::id))
    assertFalse(viewModel.uiState.value.isOperationInProgress)
    assertNull(viewModel.uiState.value.errorMessage)
  }

  @Test
  fun playerCanViewAndAddOwnedFile() = runTest {
    val file = JDRFile(id = "owned", ownerId = OWNER_ID)
    val repository = FakeWorkspaceRepository(listOf(file))
    val viewModel = viewModel(repository, GroupWorkspaceRole.PLAYER)
    runCurrent()

    assertTrue(viewModel.uiState.value.items.isEmpty())
    viewModel.addFile(file.id)
    runCurrent()

    assertEquals(1, repository.updatedFiles.size)
    assertEquals(listOf(GROUP_ID), repository.updatedFiles.single().sharedGroupsIds)
    assertEquals(listOf(file.id), viewModel.uiState.value.items.map(JDRFile::id))
    assertNull(viewModel.uiState.value.errorMessage)
  }

  @Test
  fun playerCannotAddAnotherUsersFileOrFolder() = runTest {
    val foreignFile = JDRFile(id = "foreign", ownerId = "another-user")
    val folder = JDRFile(id = "folder", ownerId = OWNER_ID, type = DocumentType.FOLDER)
    val repository = FakeWorkspaceRepository(listOf(foreignFile, folder))
    val viewModel = viewModel(repository, GroupWorkspaceRole.PLAYER)
    runCurrent()

    viewModel.addFile(foreignFile.id)
    runCurrent()
    assertTrue(repository.updatedFiles.isEmpty())
    assertTrue(viewModel.uiState.value.errorMessage.orEmpty().isNotBlank())

    viewModel.clearError()
    viewModel.addFile(folder.id)
    runCurrent()
    assertTrue(repository.updatedFiles.isEmpty())
    assertTrue(viewModel.uiState.value.errorMessage.orEmpty().isNotBlank())
    assertFalse(viewModel.uiState.value.isOperationInProgress)
  }

  @Test
  fun blankOrMissingFileIdsAreRejectedWithoutUpdate() = runTest {
    val repository = FakeWorkspaceRepository()
    val viewModel = viewModel(repository, GroupWorkspaceRole.GAME_MASTER)
    runCurrent()

    viewModel.addFile(" ")
    assertTrue(repository.fileRequests.isEmpty())
    assertTrue(viewModel.uiState.value.errorMessage.orEmpty().isNotBlank())

    viewModel.clearError()
    viewModel.addFile("missing")
    runCurrent()
    assertTrue(repository.updatedFiles.isEmpty())
    assertTrue(viewModel.uiState.value.errorMessage.orEmpty().isNotBlank())
  }

  @Test
  fun removeAtRootDetachesOnlyGroupSharingReference() = runTest {
    val file =
        JDRFile(
            id = "file",
            ownerId = OWNER_ID,
            sharedGroupsIds = listOf("other-group", GROUP_ID),
        )
    val repository = FakeWorkspaceRepository(listOf(file))
    val viewModel = viewModel(repository, GroupWorkspaceRole.GAME_MASTER)
    runCurrent()

    viewModel.removeItem(file)
    runCurrent()

    assertEquals(
        file.copy(sharedGroupsIds = listOf("other-group")),
        repository.updatedFiles.single(),
    )
    assertTrue(viewModel.uiState.value.items.isEmpty())
    assertFalse(viewModel.uiState.value.isOperationInProgress)
  }

  @Test
  fun gameMasterCanRemoveFolderWithoutDeletingItsDocument() = runTest {
    val folder =
        JDRFile(
            id = "folder",
            name = "Folder",
            ownerId = OWNER_ID,
            sharedGroupsIds = listOf(GROUP_ID),
            type = DocumentType.FOLDER,
        )
    val repository = FakeWorkspaceRepository(listOf(folder))
    val viewModel = viewModel(repository, GroupWorkspaceRole.GAME_MASTER)
    runCurrent()

    viewModel.removeItem(folder)
    runCurrent()

    assertEquals(folder.copy(sharedGroupsIds = emptyList()), repository.updatedFiles.single())
    assertEquals(folder.copy(sharedGroupsIds = emptyList()), repository.files[folder.id])
    assertTrue(repository.deletedFileIds.isEmpty())
    assertTrue(viewModel.uiState.value.items.isEmpty())
  }

  @Test
  fun removeFromFolderDetachesOnlyCurrentFolderReference() = runTest {
    val file =
        JDRFile(
            id = "file",
            ownerId = OWNER_ID,
            sharedGroupsIds = listOf(GROUP_ID),
            parentFolderIds = listOf("other-folder", "current-folder"),
        )
    val folder =
        JDRFile(
            id = "current-folder",
            type = DocumentType.FOLDER,
            sharedGroupsIds = listOf(GROUP_ID),
        )
    val repository = FakeWorkspaceRepository(listOf(file, folder))
    val viewModel = viewModel(repository, GroupWorkspaceRole.GAME_MASTER)
    runCurrent()
    viewModel.loadWorkspace("current-folder")
    runCurrent()

    viewModel.removeItem(file)
    runCurrent()

    assertEquals(
        file.copy(parentFolderIds = listOf("other-folder")),
        repository.updatedFiles.single(),
    )
    assertTrue(viewModel.uiState.value.items.isEmpty())
  }

  @Test
  fun playerCannotRemoveAnotherUsersItem() = runTest {
    val foreignFile =
        JDRFile(
            id = "foreign",
            ownerId = "another-user",
            sharedGroupsIds = listOf(GROUP_ID),
        )
    val repository = FakeWorkspaceRepository(listOf(foreignFile))
    val viewModel = viewModel(repository, GroupWorkspaceRole.PLAYER)
    runCurrent()

    viewModel.removeItem(foreignFile)
    runCurrent()

    assertTrue(repository.updatedFiles.isEmpty())
    assertTrue(viewModel.uiState.value.errorMessage.orEmpty().isNotBlank())
  }

  @Test
  fun playerCannotRemoveForeignItemUsingForgedOwnership() = runTest {
    val foreignFile =
        JDRFile(
            id = "foreign",
            ownerId = "another-user",
            sharedGroupsIds = listOf(GROUP_ID),
        )
    val repository = FakeWorkspaceRepository(listOf(foreignFile))
    val viewModel = viewModel(repository, GroupWorkspaceRole.PLAYER)
    runCurrent()
    val forgedItem = foreignFile.copy(ownerId = OWNER_ID)

    viewModel.removeItem(forgedItem)
    runCurrent()

    assertTrue(repository.updatedFiles.isEmpty())
    assertEquals(listOf(foreignFile.id), repository.fileRequests)
    assertEquals(foreignFile, repository.files[foreignFile.id])
    assertTrue(viewModel.uiState.value.errorMessage.orEmpty().isNotBlank())
  }

  @Test
  fun playerCanEditAndRemoveOwnItems() = runTest {
    val ownedFile =
        JDRFile(
            id = "owned",
            ownerId = OWNER_ID,
            sharedGroupsIds = listOf(GROUP_ID),
            content = "old",
        )
    val repository = FakeWorkspaceRepository(listOf(ownedFile))
    val viewModel = viewModel(repository, GroupWorkspaceRole.PLAYER)
    runCurrent()

    viewModel.editItem(ownedFile.copy(name = "Changed", content = "new"))
    runCurrent()

    val editedFile = ownedFile.copy(name = "Changed", content = "new")
    assertEquals(editedFile, repository.updatedFiles.last())
    viewModel.removeItem(ownedFile)
    runCurrent()
    assertEquals(editedFile.copy(sharedGroupsIds = emptyList()), repository.updatedFiles.last())
  }

  @Test
  fun gameMasterCannotEditOrRemoveItemOutsideCurrentGroup() = runTest {
    val foreignFile =
        JDRFile(id = "foreign", ownerId = OWNER_ID, sharedGroupsIds = listOf("other-group"))
    val repository = FakeWorkspaceRepository(listOf(foreignFile))
    val viewModel = viewModel(repository, GroupWorkspaceRole.GAME_MASTER)
    runCurrent()

    viewModel.editItem(foreignFile.copy(name = "Changed"))
    runCurrent()
    assertTrue(repository.updatedFiles.isEmpty())
    assertEquals(foreignFile, repository.files[foreignFile.id])
    assertTrue(viewModel.uiState.value.errorMessage.orEmpty().isNotBlank())

    viewModel.clearError()
    viewModel.removeItem(foreignFile)
    runCurrent()
    assertTrue(repository.updatedFiles.isEmpty())
    assertEquals(foreignFile, repository.files[foreignFile.id])
    assertTrue(viewModel.uiState.value.errorMessage.orEmpty().isNotBlank())
  }

  @Test
  fun playerCannotEditOrRemoveOwnDocumentOutsideCurrentGroup() = runTest {
    val privateFile =
        JDRFile(id = "private", ownerId = OWNER_ID, sharedGroupsIds = listOf("other-group"))
    val repository = FakeWorkspaceRepository(listOf(privateFile))
    val viewModel = viewModel(repository, GroupWorkspaceRole.PLAYER)
    runCurrent()

    viewModel.editItem(privateFile.copy(name = "Changed"))
    runCurrent()
    assertTrue(repository.updatedFiles.isEmpty())
    assertEquals(privateFile, repository.files[privateFile.id])
    assertTrue(viewModel.uiState.value.errorMessage.orEmpty().isNotBlank())

    viewModel.clearError()
    viewModel.removeItem(privateFile)
    runCurrent()
    assertTrue(repository.updatedFiles.isEmpty())
    assertEquals(privateFile, repository.files[privateFile.id])
    assertTrue(viewModel.uiState.value.errorMessage.orEmpty().isNotBlank())
  }

  @Test
  fun playerCannotEditOrRemoveOwnedFolderInGroupWorkspace() = runTest {
    val folder =
        JDRFile(
            id = "owned-folder",
            ownerId = OWNER_ID,
            sharedGroupsIds = listOf(GROUP_ID),
            type = DocumentType.FOLDER,
        )
    val repository = FakeWorkspaceRepository(listOf(folder))
    val viewModel = viewModel(repository, GroupWorkspaceRole.PLAYER)
    runCurrent()

    viewModel.editItem(folder.copy(name = "Changed"))
    runCurrent()
    assertTrue(repository.updatedFiles.isEmpty())
    assertEquals(folder, repository.files[folder.id])
    assertTrue(viewModel.uiState.value.errorMessage.orEmpty().isNotBlank())

    viewModel.clearError()
    viewModel.removeItem(folder)
    runCurrent()
    assertTrue(repository.updatedFiles.isEmpty())
    assertEquals(folder, repository.files[folder.id])
    assertTrue(viewModel.uiState.value.errorMessage.orEmpty().isNotBlank())
  }

  @Test
  fun cannotLoadFolderOutsideGroupAndFolderObservationFiltersForeignGroupItems() = runTest {
    val foreignFolder =
        JDRFile(
            id = "foreign-folder",
            ownerId = "another-user",
            sharedGroupsIds = listOf("other-group"),
            type = DocumentType.FOLDER,
        )
    val hiddenFile =
        JDRFile(
            id = "hidden",
            ownerId = "another-user",
            sharedGroupsIds = listOf("other-group"),
            parentFolderIds = listOf(foreignFolder.id),
        )
    val visibleFolder =
        JDRFile(
            id = "visible-folder",
            ownerId = OWNER_ID,
            sharedGroupsIds = listOf(GROUP_ID),
            type = DocumentType.FOLDER,
        )
    val visibleFile =
        JDRFile(
            id = "visible",
            ownerId = "another-user",
            sharedGroupsIds = listOf(GROUP_ID),
            parentFolderIds = listOf(visibleFolder.id),
        )
    val crossGroupFile =
        JDRFile(
            id = "cross-group",
            ownerId = "another-user",
            sharedGroupsIds = listOf("other-group"),
            parentFolderIds = listOf(visibleFolder.id),
        )
    val repository =
        FakeWorkspaceRepository(
            listOf(foreignFolder, hiddenFile, visibleFolder, visibleFile, crossGroupFile)
        )
    val viewModel = viewModel(repository, GroupWorkspaceRole.GAME_MASTER)
    runCurrent()

    viewModel.loadWorkspace(foreignFolder.id)
    runCurrent()
    assertTrue(viewModel.uiState.value.items.isEmpty())
    assertTrue(viewModel.uiState.value.loadState is LoadState.Error)
    assertTrue(repository.folderRequests.isEmpty())

    viewModel.loadWorkspace(visibleFolder.id)
    runCurrent()
    assertEquals(listOf(visibleFile), viewModel.uiState.value.items)
    assertFalse(crossGroupFile in viewModel.uiState.value.items)
    assertFalse(hiddenFile in viewModel.uiState.value.items)
  }

  @Test
  fun playerCannotEditAnotherUsersItemEvenIfSubmittedCopyClaimsOwnership() = runTest {
    val foreignFile =
        JDRFile(
            id = "foreign",
            ownerId = "another-user",
            sharedGroupsIds = listOf(GROUP_ID),
        )
    val repository = FakeWorkspaceRepository(listOf(foreignFile))
    val viewModel = viewModel(repository, GroupWorkspaceRole.PLAYER)
    runCurrent()

    viewModel.editItem(foreignFile.copy(ownerId = OWNER_ID, name = "Forged"))
    runCurrent()

    assertTrue(repository.updatedFiles.isEmpty())
    assertEquals(foreignFile, repository.files[foreignFile.id])
    assertTrue(viewModel.uiState.value.errorMessage.orEmpty().isNotBlank())
  }

  @Test
  fun gameMasterCanEditFileMetadataWithoutChangingOwnershipOrReferences() = runTest {
    val existing =
        JDRFile(
            id = "file",
            name = "Old name",
            ownerId = "player",
            sharedGroupsIds = listOf(GROUP_ID),
            parentFolderIds = listOf("folder"),
            type = DocumentType.CHARACTER,
            content = "old",
        )
    val folder =
        JDRFile(id = "folder", type = DocumentType.FOLDER, sharedGroupsIds = listOf(GROUP_ID))
    val repository = FakeWorkspaceRepository(listOf(existing, folder))
    val viewModel = viewModel(repository, GroupWorkspaceRole.GAME_MASTER)
    runCurrent()
    viewModel.loadWorkspace(folder.id)
    runCurrent()

    viewModel.editItem(existing.copy(name = "New name", content = "new"))
    runCurrent()

    assertEquals(
        existing.copy(name = "New name", content = "new"),
        repository.updatedFiles.single(),
    )
    assertEquals(repository.updatedFiles.single(), repository.files[existing.id])
  }

  @Test
  fun gameMasterCanEditFolderMetadata() = runTest {
    val folder =
        JDRFile(
            id = "folder",
            name = "Old name",
            ownerId = OWNER_ID,
            sharedGroupsIds = listOf(GROUP_ID),
            type = DocumentType.FOLDER,
        )
    val repository = FakeWorkspaceRepository(listOf(folder))
    val viewModel = viewModel(repository, GroupWorkspaceRole.GAME_MASTER)
    runCurrent()

    viewModel.editItem(folder.copy(name = "New name"))
    runCurrent()

    assertEquals(folder.copy(name = "New name"), repository.updatedFiles.single())
    assertEquals(folder.copy(name = "New name"), repository.files[folder.id])
  }

  @Test
  fun failedCreateAndUpdateOperationsExposeErrorsWithoutPersistingChanges() = runTest {
    val existing =
        JDRFile(
            id = "file",
            ownerId = OWNER_ID,
            sharedGroupsIds = listOf(GROUP_ID),
            content = "original",
        )
    val repository =
        FakeWorkspaceRepository(
            listOf(existing),
            createFailures = mutableMapOf("new-folder-1" to IllegalStateException("Create failed")),
            updateFailures = mutableMapOf("file" to IllegalStateException("Update failed")),
        )
    val viewModel = viewModel(repository, GroupWorkspaceRole.GAME_MASTER)
    runCurrent()

    viewModel.createFolder("Failed folder")
    runCurrent()
    assertTrue(viewModel.uiState.value.errorMessage.orEmpty().isNotBlank())
    assertEquals(listOf(existing), viewModel.uiState.value.items)
    assertFalse(viewModel.uiState.value.isOperationInProgress)

    viewModel.clearError()
    viewModel.addFile(existing.id)
    runCurrent()
    assertTrue(viewModel.uiState.value.errorMessage.orEmpty().isNotBlank())
    assertEquals(listOf(existing), viewModel.uiState.value.items)
    assertEquals(existing, repository.files[existing.id])
    assertFalse(viewModel.uiState.value.isOperationInProgress)

    repository.updateFailures.clear()
    viewModel.clearError()
    viewModel.addFile(existing.id)
    runCurrent()
    assertNull(viewModel.uiState.value.errorMessage)
    assertEquals(listOf(GROUP_ID), repository.files[existing.id]?.sharedGroupsIds)
  }

  @Test
  fun failedRemoveAndEditKeepPreviouslyPersistedItemVisible() = runTest {
    val existing =
        JDRFile(
            id = "file",
            name = "Original",
            ownerId = OWNER_ID,
            sharedGroupsIds = listOf(GROUP_ID),
            content = "old",
        )
    val repository =
        FakeWorkspaceRepository(
            listOf(existing),
            updateFailures = mutableMapOf("file" to IllegalStateException("Write failed")),
        )
    val viewModel = viewModel(repository, GroupWorkspaceRole.GAME_MASTER)
    runCurrent()

    viewModel.removeItem(existing)
    runCurrent()
    assertTrue(viewModel.uiState.value.errorMessage.orEmpty().isNotBlank())
    assertEquals(listOf(existing), viewModel.uiState.value.items)
    assertEquals(existing, repository.files[existing.id])

    viewModel.clearError()
    viewModel.editItem(existing.copy(name = "Unpersisted"))
    runCurrent()
    assertTrue(viewModel.uiState.value.errorMessage.orEmpty().isNotBlank())
    assertEquals(existing, repository.files[existing.id])
    assertEquals(listOf(existing), viewModel.uiState.value.items)
    assertFalse(viewModel.uiState.value.isOperationInProgress)
  }

  @Test
  fun operationFailureDoesNotPreventLaterSuccess() = runTest {
    val existing = JDRFile(id = "file", ownerId = OWNER_ID)
    val repository =
        FakeWorkspaceRepository(
            listOf(existing),
            updateFailures = mutableMapOf("file" to IllegalStateException("Temporary failure")),
        )
    val viewModel = viewModel(repository, GroupWorkspaceRole.GAME_MASTER)
    runCurrent()

    viewModel.addFile(existing.id)
    runCurrent()
    assertTrue(viewModel.uiState.value.errorMessage.orEmpty().isNotBlank())
    assertFalse(viewModel.uiState.value.isOperationInProgress)

    repository.updateFailures.clear()
    viewModel.addFile(existing.id)
    runCurrent()
    assertNull(viewModel.uiState.value.errorMessage)
    assertEquals(listOf(GROUP_ID), repository.files[existing.id]?.sharedGroupsIds)
  }

  @Test
  fun pendingOperationsKeepBusyStateUntilAllQueuedOperationsFinish() = runTest {
    val first = JDRFile(id = "first", ownerId = OWNER_ID)
    val second = JDRFile(id = "second", ownerId = OWNER_ID)
    val repository = FakeWorkspaceRepository(listOf(first, second))
    val firstGate = CompletableDeferred<Unit>()
    val secondGate = CompletableDeferred<Unit>()
    repository.updateGate = { file ->
      when (file.id) {
        first.id -> firstGate.await()
        second.id -> secondGate.await()
      }
    }
    val viewModel = viewModel(repository, GroupWorkspaceRole.GAME_MASTER)
    runCurrent()

    viewModel.addFile(first.id)
    viewModel.addFile(second.id)
    runCurrent()
    assertTrue(viewModel.uiState.value.isOperationInProgress)
    assertEquals(listOf(first.id), repository.updatedFiles.map(JDRFile::id))

    firstGate.complete(Unit)
    runCurrent()
    assertTrue(viewModel.uiState.value.isOperationInProgress)
    assertEquals(listOf(first.id, second.id), repository.updatedFiles.map(JDRFile::id))

    secondGate.complete(Unit)
    runCurrent()
    assertFalse(viewModel.uiState.value.isOperationInProgress)
    assertEquals(listOf(GROUP_ID), repository.files[first.id]?.sharedGroupsIds)
    assertEquals(listOf(GROUP_ID), repository.files[second.id]?.sharedGroupsIds)
  }

  @Test
  fun concurrentAdditionsOfSameFileDoNotDuplicateReferencesOrOverwrite() = runTest {
    val file = JDRFile(id = "file", ownerId = OWNER_ID, sharedGroupsIds = listOf("other-group"))
    val repository = FakeWorkspaceRepository(listOf(file))
    val gate = CompletableDeferred<Unit>()
    repository.updateGate = { if (repository.updatedFiles.size == 1) gate.await() }
    val viewModel = viewModel(repository, GroupWorkspaceRole.GAME_MASTER)
    runCurrent()

    viewModel.addFile(file.id)
    viewModel.addFile(file.id)
    runCurrent()
    assertEquals(1, repository.updatedFiles.size)
    assertTrue(viewModel.uiState.value.isOperationInProgress)

    gate.complete(Unit)
    runCurrent()

    assertEquals(2, repository.updatedFiles.size)
    assertEquals(listOf("other-group", GROUP_ID), repository.files[file.id]?.sharedGroupsIds)
    assertFalse(viewModel.uiState.value.isOperationInProgress)
  }

  @Test
  fun queuedOperationCanSucceedAfterEarlierOperationFails() = runTest {
    val failed = JDRFile(id = "failed", ownerId = OWNER_ID)
    val succeeded = JDRFile(id = "succeeded", ownerId = OWNER_ID)
    val repository =
        FakeWorkspaceRepository(
            listOf(failed, succeeded),
            updateFailures = mutableMapOf("failed" to IllegalStateException("First failed")),
        )
    val failedGate = CompletableDeferred<Unit>()
    val succeededGate = CompletableDeferred<Unit>()
    repository.updateGate = {
      when (it.id) {
        failed.id -> failedGate.await()
        succeeded.id -> succeededGate.await()
      }
    }
    val viewModel = viewModel(repository, GroupWorkspaceRole.GAME_MASTER)
    runCurrent()

    viewModel.addFile(failed.id)
    viewModel.addFile(succeeded.id)
    runCurrent()
    assertTrue(viewModel.uiState.value.isOperationInProgress)

    failedGate.complete(Unit)
    runCurrent()

    assertTrue(viewModel.uiState.value.errorMessage.orEmpty().isNotBlank())
    assertTrue(viewModel.uiState.value.isOperationInProgress)
    assertNull(repository.files[failed.id]?.sharedGroupsIds?.takeIf { GROUP_ID in it })

    succeededGate.complete(Unit)
    runCurrent()

    assertFalse(viewModel.uiState.value.isOperationInProgress)
    assertEquals(listOf(GROUP_ID), repository.files[succeeded.id]?.sharedGroupsIds)
  }

  @Test
  fun queuedAddUsesFolderWhereItWasRequested() = runTest {
    val requestedFolder =
        JDRFile(
            id = "requested-folder",
            type = DocumentType.FOLDER,
            sharedGroupsIds = listOf(GROUP_ID),
        )
    val newCurrentFolder =
        JDRFile(
            id = "new-current-folder",
            type = DocumentType.FOLDER,
            sharedGroupsIds = listOf(GROUP_ID),
        )
    val blocker = JDRFile(id = "blocker", ownerId = OWNER_ID)
    val target = JDRFile(id = "target", ownerId = OWNER_ID)
    val repository =
        FakeWorkspaceRepository(listOf(requestedFolder, newCurrentFolder, blocker, target))
    val operationGate = CompletableDeferred<Unit>()
    repository.updateGate = { if (it.id == blocker.id) operationGate.await() }
    val viewModel = viewModel(repository, GroupWorkspaceRole.GAME_MASTER)
    runCurrent()

    viewModel.addFile(blocker.id)
    runCurrent()
    viewModel.loadWorkspace("requested-folder")
    runCurrent()
    viewModel.addFile(target.id)
    viewModel.loadWorkspace("new-current-folder")
    runCurrent()

    operationGate.complete(Unit)
    runCurrent()

    assertEquals(listOf("requested-folder"), repository.files[target.id]?.parentFolderIds)
    assertEquals("new-current-folder", viewModel.uiState.value.currentFolderId)
  }

  @Test
  fun queuedRemoveUsesFolderWhereItWasRequested() = runTest {
    val requestedFolder =
        JDRFile(
            id = "requested-folder",
            type = DocumentType.FOLDER,
            sharedGroupsIds = listOf(GROUP_ID),
        )
    val newCurrentFolder =
        JDRFile(
            id = "new-current-folder",
            type = DocumentType.FOLDER,
            sharedGroupsIds = listOf(GROUP_ID),
        )
    val blocker = JDRFile(id = "blocker", ownerId = OWNER_ID)
    val target =
        JDRFile(
            id = "target",
            ownerId = OWNER_ID,
            sharedGroupsIds = listOf(GROUP_ID),
            parentFolderIds = listOf("requested-folder", "new-current-folder"),
        )
    val repository =
        FakeWorkspaceRepository(listOf(requestedFolder, newCurrentFolder, blocker, target))
    val operationGate = CompletableDeferred<Unit>()
    repository.updateGate = { if (it.id == blocker.id) operationGate.await() }
    val viewModel = viewModel(repository, GroupWorkspaceRole.GAME_MASTER)
    runCurrent()

    viewModel.addFile(blocker.id)
    runCurrent()
    viewModel.loadWorkspace("requested-folder")
    runCurrent()
    viewModel.removeItem(target)
    viewModel.loadWorkspace("new-current-folder")
    runCurrent()

    operationGate.complete(Unit)
    runCurrent()

    assertEquals(listOf("new-current-folder"), repository.files[target.id]?.parentFolderIds)
    assertEquals("new-current-folder", viewModel.uiState.value.currentFolderId)
  }

  @Test
  fun unauthenticatedAddAndRemoveExposeErrorsWithoutUpdatingFiles() = runTest {
    val file =
        JDRFile(
            id = "file",
            ownerId = OWNER_ID,
            sharedGroupsIds = listOf(GROUP_ID),
        )
    val repository = FakeWorkspaceRepository(listOf(file))
    val viewModel =
        viewModel(
            repository,
            GroupWorkspaceRole.GAME_MASTER,
            FakeAuthRepository(user = null),
        )
    runCurrent()

    viewModel.addFile(file.id)
    runCurrent()
    assertTrue(viewModel.uiState.value.errorMessage.orEmpty().isNotBlank())
    assertTrue(repository.updatedFiles.isEmpty())
    assertFalse(viewModel.uiState.value.isOperationInProgress)

    viewModel.clearError()
    viewModel.removeItem(file)
    runCurrent()
    assertTrue(viewModel.uiState.value.errorMessage.orEmpty().isNotBlank())
    assertTrue(repository.updatedFiles.isEmpty())
    assertEquals(file, repository.files[file.id])
    assertFalse(viewModel.uiState.value.isOperationInProgress)
  }

  @Test
  fun mutationCancellationIsNotReportedAsOrdinaryFailureAndLaterWorkCanRun() = runTest {
    val file = JDRFile(id = "file", ownerId = OWNER_ID)
    val repository = FakeWorkspaceRepository(listOf(file))
    repository.updateCancellation = CancellationException("cancelled")
    val viewModel = viewModel(repository, GroupWorkspaceRole.GAME_MASTER)
    runCurrent()

    viewModel.addFile(file.id)
    runCurrent()

    assertTrue(repository.lastUpdateJob?.isCancelled == true)
    assertNull(viewModel.uiState.value.errorMessage)
    assertFalse(viewModel.uiState.value.isOperationInProgress)

    repository.updateCancellation = null
    viewModel.addFile(file.id)
    runCurrent()
    assertEquals(listOf(GROUP_ID), repository.files[file.id]?.sharedGroupsIds)
    assertFalse(viewModel.uiState.value.isOperationInProgress)
  }

  private fun viewModel(
      repository: FakeWorkspaceRepository,
      role: GroupWorkspaceRole,
      authRepository: FakeAuthRepository = FakeAuthRepository(),
  ) = GroupWorkspaceViewModel(GROUP_ID, role, repository, authRepository)

  private class FakeWorkspaceRepository(
      initialFiles: List<JDRFile> = emptyList(),
      folderItems: List<JDRFile> = emptyList(),
      private val folderGates: Map<String, CompletableDeferred<Unit>> = emptyMap(),
      var rootFailure: Exception? = null,
      private val folderFailures: Map<String, Exception> = emptyMap(),
      private val folderFailureGates: Map<String, CompletableDeferred<Unit>> = emptyMap(),
      val createFailures: MutableMap<String, Exception> = mutableMapOf(),
      val updateFailures: MutableMap<String, Exception> = mutableMapOf(),
  ) : WorkspaceRepository {
    val files = initialFiles.associateByTo(linkedMapOf(), JDRFile::id)
    val groupRootRequests = mutableListOf<String>()
    val folderRequests = mutableListOf<String>()
    val fileRequests = mutableListOf<String>()
    val createdFiles = mutableListOf<JDRFile>()
    val updatedFiles = mutableListOf<JDRFile>()
    val deletedFileIds = mutableListOf<String>()
    var updateGate: (suspend (JDRFile) -> Unit)? = null
    var updateCancellation: CancellationException? = null
    var lastUpdateJob: Job? = null
      private set

    private val rootItems = MutableStateFlow(rootItems())
    private val folderFlows = mutableMapOf<String, MutableStateFlow<List<JDRFile>>>()
    private var nextUid = 0

    init {
      folderItems.forEach { files[it.id] = it }
    }

    override fun getNewUid(): String = "new-folder-${++nextUid}"

    override suspend fun createFile(file: JDRFile): Result<Unit> {
      createdFiles += file
      createFailures[file.id]?.let {
        return Result.failure(it)
      }
      files[file.id] = file
      refreshFlows()
      return Result.success(Unit)
    }

    override suspend fun updateFile(file: JDRFile): Result<Unit> {
      updatedFiles += file
      lastUpdateJob = currentCoroutineContext()[Job]
      updateGate?.invoke(file)
      updateCancellation?.let { throw it }
      updateFailures[file.id]?.let {
        return Result.failure(it)
      }
      files[file.id] = file
      refreshFlows()
      return Result.success(Unit)
    }

    override suspend fun deleteFile(fileId: String): Result<Unit> {
      deletedFileIds += fileId
      files.remove(fileId)
      refreshFlows()
      return Result.success(Unit)
    }

    override suspend fun getFile(fileId: String): Result<JDRFile?> {
      fileRequests += fileId
      return Result.success(files[fileId])
    }

    override fun getFileFlow(fileId: String): Flow<JDRFile?> = flow { emit(files[fileId]) }

    override fun getPersonalRootFiles(ownerId: String): Flow<List<JDRFile>> =
        MutableStateFlow(emptyList())

    override fun getGroupRootFiles(groupId: String): Flow<List<JDRFile>> {
      groupRootRequests += groupId
      return rootItems.asStateFlow().gated(rootFailure)
    }

    override fun getDocumentsInFolder(folderId: String): Flow<List<JDRFile>> {
      folderRequests += folderId
      val state = folderFlows.getOrPut(folderId) { MutableStateFlow(folderItems(folderId)) }
      return state
          .asStateFlow()
          .gated(
              folderFailures[folderId],
              folderFailureGates[folderId] ?: folderGates[folderId],
              ignoreGateCancellation = folderFailureGates.containsKey(folderId),
          )
    }

    fun replaceFiles(replacements: List<JDRFile>) {
      files.clear()
      replacements.forEach { files[it.id] = it }
      refreshFlows()
    }

    private fun rootItems() =
        files.values.filter { GROUP_ID in it.sharedGroupsIds && it.parentFolderIds.isEmpty() }

    private fun folderItems(folderId: String) =
        files.values.filter { folderId in it.parentFolderIds }

    private fun refreshFlows() {
      rootItems.value = rootItems()
      folderFlows.forEach { (folderId, state) -> state.value = folderItems(folderId) }
    }

    private fun <T> Flow<T>.gated(
        failure: Exception?,
        gate: CompletableDeferred<Unit>? = null,
        ignoreGateCancellation: Boolean = false,
    ): Flow<T> = onStart {
      if (ignoreGateCancellation) {
        withContext(NonCancellable) { gate?.await() }
      } else {
        gate?.await()
      }
      failure?.let { throw it }
    }
  }

  private class FakeAuthRepository(
      user: AuthUser? = AuthUser(OWNER_ID, "owner@example.com", "Owner"),
  ) : AuthRepository {
    private val state = MutableStateFlow(user)
    override val authState: StateFlow<AuthUser?> = state.asStateFlow()

    override suspend fun signUpWithEmail(
        email: String,
        password: String,
        username: String,
    ): AuthResult<AuthUser> = AuthResult.Success(requireNotNull(state.value))

    override suspend fun signInWithEmail(email: String, password: String): AuthResult<AuthUser> =
        AuthResult.Success(requireNotNull(state.value))

    override suspend fun signInWithGoogle(idToken: String): AuthResult<AuthUser> =
        AuthResult.Success(requireNotNull(state.value))

    override suspend fun signOut(): AuthResult<Unit> = AuthResult.Success(Unit)
  }

  private companion object {
    const val GROUP_ID = "group-7"
    const val OWNER_ID = "authenticated-user-42"
  }
}

@OptIn(ExperimentalCoroutinesApi::class)
class GroupWorkspaceMainDispatcherRule : TestWatcher() {
  private val dispatcher = StandardTestDispatcher()

  override fun starting(description: Description) {
    Dispatchers.setMain(dispatcher)
  }

  override fun finished(description: Description) {
    Dispatchers.resetMain()
  }
}
