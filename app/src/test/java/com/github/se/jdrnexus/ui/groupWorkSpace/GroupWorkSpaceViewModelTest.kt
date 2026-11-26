// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.ui.groupWorkSpace

import com.github.se.jdrnexus.codec.JDRFileJsonCodec
import com.github.se.jdrnexus.model.filerepresentation.CharacterAction
import com.github.se.jdrnexus.model.filerepresentation.CharacterFile
import com.github.se.jdrnexus.model.filerepresentation.CharacterHealthPoints
import com.github.se.jdrnexus.model.filerepresentation.CharacterStat
import com.github.se.jdrnexus.model.personalSpace.CampaignGroup
import com.github.se.jdrnexus.model.personalSpace.DocumentType
import com.github.se.jdrnexus.model.personalSpace.FileEdit
import com.github.se.jdrnexus.model.personalSpace.GroupRepository
import com.github.se.jdrnexus.model.personalSpace.JDRFile
import com.github.se.jdrnexus.model.personalSpace.PERSONAL_ROOT_ID
import com.github.se.jdrnexus.model.personalSpace.Placement
import com.github.se.jdrnexus.model.personalSpace.WorkspaceRepository
import com.github.se.jdrnexus.model.repository.AuthRepository
import com.github.se.jdrnexus.model.repository.AuthResult
import com.github.se.jdrnexus.model.repository.AuthUser
import com.github.se.jdrnexus.viewmodel.LoadState
import java.util.concurrent.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class GroupWorkSpaceViewModelTest {
  @get:Rule val mainDispatcherRule = GroupWorkspaceMainDispatcherRule()

  private fun groupFolder(
      id: String,
      parentId: String = GROUP_ID,
      ownerId: String = OTHER_USER_ID,
      hidden: Boolean = false,
      groups: List<String> = listOf(GROUP_ID),
  ) = groupItem(id, ownerId, DocumentType.FOLDER, parentId, hidden, groups)

  private fun groupFile(
      id: String,
      parentId: String = GROUP_ID,
      ownerId: String = OTHER_USER_ID,
      hidden: Boolean = false,
      groups: List<String> = listOf(GROUP_ID),
      type: DocumentType = DocumentType.TEXT,
      name: String = id,
      content: String = "",
  ) = groupItem(id, ownerId, type, parentId, hidden, groups, name, content)

  private fun groupItem(
      id: String,
      ownerId: String,
      type: DocumentType,
      parentId: String,
      hidden: Boolean,
      groups: List<String>,
      name: String = id,
      content: String = "",
  ) =
      JDRFile(
          id = id,
          name = name,
          ownerId = ownerId,
          type = type,
          content = content,
          sharedGroupsIds = groups,
          placements =
              groups.associateWith { group ->
                Placement(
                    folderId = if (group == GROUP_ID) parentId else group,
                    isHidden = hidden,
                )
              },
      )

  // Game Masters see hidden root items, while players see only visible items and have fewer
  // permissions.
  @Test
  fun loadsGroupRootAndExposesRolePermissions() = runTest {
    val visible = groupFile("visible")
    val hidden = groupFile("hidden", hidden = true)
    val repository = FakeWorkspaceRepository(listOf(visible, hidden))
    val gm = viewModel(repository, GroupWorkspaceRole.GAME_MASTER)
    val player = viewModel(repository, GroupWorkspaceRole.PLAYER)

    runCurrent()

    assertEquals(setOf(visible.id, hidden.id), gm.uiState.value.items.map { it.id }.toSet())
    assertEquals(listOf(visible.id), player.uiState.value.items.map { it.id })
    assertEquals(LoadState.Success, player.uiState.value.loadState)
    assertTrue(gm.uiState.value.isGameMaster)
    assertTrue(gm.uiState.value.canEditItems)
    assertFalse(player.uiState.value.isGameMaster)
    assertFalse(player.uiState.value.canEditItems)
  }

  @Test
  fun exposesGroupNameAndAllPersonalItemsWithEachFolderBeforeItsContent() = runTest {
    val rootFile = JDRFile(id = "root-file", ownerId = OWNER_ID)
    val folder = JDRFile(id = "personal-folder", ownerId = OWNER_ID, type = DocumentType.FOLDER)
    val secondFolder =
        JDRFile(
            id = "second-folder",
            ownerId = OWNER_ID,
            type = DocumentType.FOLDER,
        )
    val nestedFolder =
        JDRFile(
            id = "nested-folder",
            ownerId = OWNER_ID,
            type = DocumentType.FOLDER,
            personalParentId = folder.id,
        )
    val nestedFile =
        JDRFile(
            id = "nested-file",
            ownerId = OWNER_ID,
            personalParentId = nestedFolder.id,
        )
    val secondNestedFile =
        JDRFile(
            id = "second-nested-file",
            ownerId = OWNER_ID,
            personalParentId = secondFolder.id,
        )
    val otherUsersFile = JDRFile(id = "other-users-file", ownerId = OTHER_USER_ID)
    val repository =
        FakeWorkspaceRepository(
            listOf(
                rootFile,
                folder,
                secondFolder,
                nestedFolder,
                nestedFile,
                secondNestedFile,
                otherUsersFile,
            )
        )
    val groupRepository =
        FakeGroupRepository(
            CampaignGroup(
                id = GROUP_ID,
                name = "The Sunken Crown",
                gameMasterId = OWNER_ID,
                participantIds = listOf(OWNER_ID),
            )
        )

    val viewModel =
        GroupWorkspaceViewModel(GROUP_ID, groupRepository, repository, FakeAuthRepository())
    runCurrent()

    assertEquals("The Sunken Crown", viewModel.uiState.value.groupName)
    assertEquals(
        listOf(
            rootFile.id,
            folder.id,
            nestedFolder.id,
            nestedFile.id,
            secondFolder.id,
            secondNestedFile.id,
        ),
        viewModel.uiState.value.personalFiles.map { it.id },
    )
    assertEquals(LoadState.Success, viewModel.uiState.value.loadState)
  }

  @Test
  fun exposesPersonalFilesLoadingFailure() = runTest {
    val repository =
        FakeWorkspaceRepository(
            personalRootFailure = IllegalStateException("Personal Space unavailable")
        )
    val viewModel =
        GroupWorkspaceViewModel(
            GROUP_ID,
            FakeGroupRepository(groupForRole(GroupWorkspaceRole.GAME_MASTER)),
            repository,
            FakeAuthRepository(),
        )
    runCurrent()

    assertEquals(emptyList<JDRFile>(), viewModel.uiState.value.personalFiles)
    assertEquals("Personal Space unavailable", viewModel.uiState.value.personalFilesError)
    assertEquals("Personal Space unavailable", viewModel.uiState.value.errorMessage)
    assertEquals(LoadState.Success, viewModel.uiState.value.loadState)
  }

  @Test
  fun repeatedGroupUpdatesDoNotReloadWorkspace() = runTest {
    val groupRepository = FakeGroupRepository(groupForRole(GroupWorkspaceRole.GAME_MASTER))
    val repository = FakeWorkspaceRepository()
    val viewModel =
        viewModel(
            repository,
            GroupWorkspaceRole.GAME_MASTER,
            groupRepository = groupRepository,
        )
    runCurrent()

    val initialGroupLoads = repository.groupRootLoads
    val initialPersonalLoads = repository.personalRootLoads
    groupRepository.group.value =
        groupForRole(GroupWorkspaceRole.GAME_MASTER).copy(description = "updated")
    runCurrent()

    assertEquals(initialGroupLoads, repository.groupRootLoads)
    assertEquals(initialPersonalLoads, repository.personalRootLoads)
    assertEquals(GROUP_ID, viewModel.uiState.value.groupId)
  }

  @Test
  fun openFolderSuccessfullyOpensSelectedFolder() = runTest {
    val folder = groupFolder("folder")
    val child = groupFile("child", parentId = folder.id)
    val viewModel =
        viewModel(
            FakeWorkspaceRepository(listOf(folder, child)),
            GroupWorkspaceRole.GAME_MASTER,
        )
    runCurrent()

    viewModel.openFolder(folder.id)
    runCurrent()

    assertEquals(folder.id, viewModel.uiState.value.currentFolderId)
    assertEquals(listOf(""), viewModel.uiState.value.folderHistory)
    assertEquals(listOf(child), viewModel.uiState.value.items)
  }

  @Test
  fun navigateBackReturnsToPreviousFolder() = runTest {
    val parent = groupFolder("parent")
    val childFolder = groupFolder("child-folder", parentId = parent.id)
    val child = groupFile("child", parentId = childFolder.id)
    val viewModel =
        viewModel(
            FakeWorkspaceRepository(listOf(parent, childFolder, child)),
            GroupWorkspaceRole.GAME_MASTER,
        )
    runCurrent()

    viewModel.openFolder(parent.id)
    runCurrent()
    viewModel.openFolder(childFolder.id)
    runCurrent()

    assertTrue(viewModel.navigateBack())
    runCurrent()

    assertEquals(parent.id, viewModel.uiState.value.currentFolderId)
    assertEquals(listOf(""), viewModel.uiState.value.folderHistory)
    assertEquals(listOf(childFolder), viewModel.uiState.value.items)
  }

  @Test
  fun navigateBackRecoversWhenHistoryFolderNoLongerExists() = runTest {
    val parent = groupFolder("parent")
    val childFolder = groupFolder("child-folder", parentId = parent.id)
    val repository = FakeWorkspaceRepository(listOf(parent, childFolder))
    val viewModel = viewModel(repository, GroupWorkspaceRole.GAME_MASTER)
    runCurrent()

    viewModel.openFolder(parent.id)
    runCurrent()
    viewModel.openFolder(childFolder.id)
    runCurrent()
    repository.deleteFile(parent.id)

    assertTrue(viewModel.navigateBack())
    runCurrent()

    assertEquals("", viewModel.uiState.value.currentFolderId)
    assertTrue(viewModel.uiState.value.folderHistory.isEmpty())
    assertEquals("This folder is no longer available.", viewModel.uiState.value.errorMessage)
  }

  @Test
  fun disappearingFolderResetsHistoryAndAllowsRecoveryAtRoot() = runTest {
    val folder = groupFolder("folder")
    val replacement = groupFolder("replacement")
    val repository = FakeWorkspaceRepository(listOf(folder, replacement))
    val viewModel = viewModel(repository, GroupWorkspaceRole.GAME_MASTER)
    runCurrent()

    viewModel.openFolder(folder.id)
    runCurrent()
    repository.deleteFile(folder.id)
    runCurrent()

    assertEquals("", viewModel.uiState.value.currentFolderId)
    assertTrue(viewModel.uiState.value.folderHistory.isEmpty())
    assertEquals(listOf(replacement), viewModel.uiState.value.items)

    viewModel.openFolder(replacement.id)
    runCurrent()
    assertEquals(replacement.id, viewModel.uiState.value.currentFolderId)
    assertEquals(listOf(""), viewModel.uiState.value.folderHistory)
  }

  // A user outside the participant list and a deleted group both deny workspace access.
  @Test
  fun nonMemberAndDeletedGroupCannotAccessWorkspace() = runTest {
    val groupRepository =
        FakeGroupRepository(
            CampaignGroup(
                id = GROUP_ID,
                gameMasterId = OTHER_USER_ID,
                participantIds = listOf(OTHER_USER_ID),
            )
        )
    val repository = FakeWorkspaceRepository(listOf(groupFile("file")))
    val viewModel =
        viewModel(
            repository,
            GroupWorkspaceRole.PLAYER,
            groupRepository = groupRepository,
        )

    runCurrent()

    assertEquals(
        LoadState.Error("You are no longer a member of this group."),
        viewModel.uiState.value.loadState,
    )
    assertTrue(viewModel.uiState.value.items.isEmpty())
    assertFalse(viewModel.uiState.value.isGameMaster)
    assertFalse(viewModel.uiState.value.canEditItems)

    groupRepository.group.value = null
    runCurrent()

    assertEquals(
        LoadState.Error("You are no longer a member of this group."),
        viewModel.uiState.value.loadState,
    )
    assertTrue(viewModel.uiState.value.items.isEmpty())
  }

  // Changing the Game Master to a player reloads visibility and removes privileged permissions.
  @Test
  fun gameMasterLosesPermissionsWhenDemotedToPlayer() = runTest {
    val visible = groupFile("visible")
    val hidden = groupFile("hidden", hidden = true)
    val groupRepository = FakeGroupRepository(groupForRole(GroupWorkspaceRole.GAME_MASTER))
    val viewModel =
        viewModel(
            FakeWorkspaceRepository(listOf(visible, hidden)),
            GroupWorkspaceRole.GAME_MASTER,
            groupRepository = groupRepository,
        )

    runCurrent()
    assertEquals(setOf(visible.id, hidden.id), viewModel.uiState.value.items.map { it.id }.toSet())
    assertTrue(viewModel.uiState.value.isGameMaster)
    assertTrue(viewModel.uiState.value.canEditItems)

    groupRepository.group.value =
        CampaignGroup(
            id = GROUP_ID,
            gameMasterId = OTHER_USER_ID,
            participantIds = listOf(OWNER_ID),
        )
    runCurrent()

    assertEquals(listOf(visible.id), viewModel.uiState.value.items.map { it.id })
    assertEquals(LoadState.Success, viewModel.uiState.value.loadState)
    assertFalse(viewModel.uiState.value.isGameMaster)
    assertFalse(viewModel.uiState.value.canEditItems)
  }

  // Removing and re-adding a player clears access, then restores the workspace without GM access.
  @Test
  fun playerLosesAndRegainsAccessWhenMembershipChanges() = runTest {
    val file = groupFile("file")
    val groupRepository = FakeGroupRepository(groupForRole(GroupWorkspaceRole.PLAYER))
    val viewModel =
        viewModel(
            FakeWorkspaceRepository(listOf(file)),
            GroupWorkspaceRole.PLAYER,
            groupRepository = groupRepository,
        )

    runCurrent()
    assertEquals(listOf(file), viewModel.uiState.value.items)

    groupRepository.group.value = groupRepository.group.value!!.copy(participantIds = emptyList())
    runCurrent()

    assertEquals(
        LoadState.Error("You are no longer a member of this group."),
        viewModel.uiState.value.loadState,
    )
    assertTrue(viewModel.uiState.value.items.isEmpty())
    assertEquals("", viewModel.uiState.value.currentFolderId)
    assertFalse(viewModel.uiState.value.isGameMaster)
    assertFalse(viewModel.uiState.value.canEditItems)

    groupRepository.group.value = groupForRole(GroupWorkspaceRole.PLAYER)
    runCurrent()

    assertEquals(LoadState.Success, viewModel.uiState.value.loadState)
    assertEquals(listOf(file), viewModel.uiState.value.items)
    assertFalse(viewModel.uiState.value.isGameMaster)
    assertFalse(viewModel.uiState.value.canEditItems)
  }

  // Promoting a player to Game Master restores privileged permissions and access to hidden items.
  @Test
  fun playerReceivesGameMasterPermissionsAfterPromotion() = runTest {
    val visible = groupFile("visible")
    val hidden = groupFile("hidden", hidden = true)
    val groupRepository = FakeGroupRepository(groupForRole(GroupWorkspaceRole.PLAYER))
    val viewModel =
        viewModel(
            FakeWorkspaceRepository(listOf(visible, hidden)),
            GroupWorkspaceRole.PLAYER,
            groupRepository = groupRepository,
        )

    runCurrent()
    assertEquals(listOf(visible.id), viewModel.uiState.value.items.map { it.id })
    assertFalse(viewModel.uiState.value.isGameMaster)
    assertFalse(viewModel.uiState.value.canEditItems)

    groupRepository.group.value =
        CampaignGroup(
            id = GROUP_ID,
            gameMasterId = OWNER_ID,
            participantIds = listOf(OWNER_ID),
        )
    runCurrent()

    assertEquals(setOf(visible.id, hidden.id), viewModel.uiState.value.items.map { it.id }.toSet())
    assertEquals(LoadState.Success, viewModel.uiState.value.loadState)
    assertTrue(viewModel.uiState.value.isGameMaster)
    assertTrue(viewModel.uiState.value.canEditItems)
  }

  // A membership flow failure clears the selected workspace, identity, and permissions.
  @Test
  fun membershipFlowFailureClearsWorkspaceAccess() = runTest {
    val folder = groupFolder("selected", ownerId = OWNER_ID)
    val file = groupFile("file", parentId = folder.id, ownerId = OWNER_ID)
    val groupRepository = FakeGroupRepository(groupForRole(GroupWorkspaceRole.GAME_MASTER))
    val viewModel =
        viewModel(
            FakeWorkspaceRepository(listOf(folder, file)),
            GroupWorkspaceRole.GAME_MASTER,
            groupRepository = groupRepository,
        )
    runCurrent()
    viewModel.loadWorkspace(folder.id)
    runCurrent()
    assertEquals(listOf(file), viewModel.uiState.value.items)

    groupRepository.fail(IllegalStateException("membership unavailable"))
    runCurrent()

    assertEquals("", viewModel.uiState.value.currentUserId)
    assertTrue(viewModel.uiState.value.items.isEmpty())
    assertEquals("", viewModel.uiState.value.currentFolderId)
    assertEquals("", viewModel.uiState.value.currentFolderName)
    assertFalse(viewModel.uiState.value.isGameMaster)
    assertFalse(viewModel.uiState.value.canEditItems)
    assertEquals(
        LoadState.Error("membership unavailable"),
        viewModel.uiState.value.loadState,
    )
  }

  // Membership-flow cancellation stops observation without turning cancellation into an error.
  @Test
  fun membershipFlowCancellationIsNotReportedAsAnError() = runTest {
    val file = groupFile("file")
    val groupRepository = FakeGroupRepository(groupForRole(GroupWorkspaceRole.PLAYER))
    val viewModel =
        viewModel(
            FakeWorkspaceRepository(listOf(file)),
            GroupWorkspaceRole.PLAYER,
            groupRepository = groupRepository,
        )
    runCurrent()
    assertEquals(listOf(file), viewModel.uiState.value.items)

    groupRepository.fail(CancellationException("membership observation cancelled"))
    runCurrent()

    assertEquals(LoadState.Success, viewModel.uiState.value.loadState)
    assertEquals(listOf(file), viewModel.uiState.value.items)
    assertFalse(viewModel.uiState.value.isGameMaster)
    assertFalse(viewModel.uiState.value.canEditItems)
  }

  // Players retain access to their own hidden items and folders containing them, but not unrelated
  // hidden content.
  @Test
  fun playersSeeOwnedHiddenItemsAndFoldersWithOwnedDescendantsOnly() = runTest {
    val hiddenOwned = groupFile("hidden-owned", ownerId = OWNER_ID, hidden = true)
    val hiddenFolder = groupFolder("owned-folder", hidden = true)
    val ownedChild =
        groupFile("owned-child", parentId = hiddenFolder.id, ownerId = OWNER_ID, hidden = true)
    val unrelatedHiddenFolder = groupFolder("unrelated-hidden", hidden = true)
    val unrelatedHiddenChild =
        groupFile("unrelated-child", parentId = unrelatedHiddenFolder.id, hidden = true)
    val hiddenFile = groupFile("hidden-file", hidden = true)
    val repository =
        FakeWorkspaceRepository(
            listOf(
                hiddenOwned,
                hiddenFolder,
                ownedChild,
                unrelatedHiddenFolder,
                unrelatedHiddenChild,
                hiddenFile,
            )
        )
    val player = viewModel(repository, GroupWorkspaceRole.PLAYER)
    runCurrent()

    assertEquals(
        setOf(hiddenOwned.id, hiddenFolder.id),
        player.uiState.value.items.map { it.id }.toSet(),
    )
    player.loadWorkspace(hiddenFolder.id)
    runCurrent()
    assertEquals(listOf(ownedChild.id), player.uiState.value.items.map { it.id })

    player.loadWorkspace(unrelatedHiddenFolder.id)
    runCurrent()
    assertEquals(
        LoadState.Error("You cannot access this folder."),
        player.uiState.value.loadState,
    )
    assertTrue(player.uiState.value.items.isEmpty())
  }

  // A hidden ancestor conceals unowned descendants without concealing a player's own placed items.
  @Test
  fun hiddenAncestorHidesUnownedDescendantsButNotOwnedItems() = runTest {
    val hiddenParent = groupFolder("hidden-parent", hidden = true)
    val nested = groupFolder("nested", parentId = hiddenParent.id)
    val unowned = groupFile("unowned", parentId = nested.id)
    val owned = groupFile("owned", parentId = nested.id, ownerId = OWNER_ID)
    val nestedOwned = groupFile("nested-owned", parentId = nested.id, ownerId = OWNER_ID)
    val repository =
        FakeWorkspaceRepository(listOf(hiddenParent, nested, unowned, owned, nestedOwned))
    val player = viewModel(repository, GroupWorkspaceRole.PLAYER)
    runCurrent()

    player.loadWorkspace(nested.id)
    runCurrent()

    assertEquals(setOf(owned.id, nestedOwned.id), player.uiState.value.items.map { it.id }.toSet())
    assertEquals(LoadState.Success, player.uiState.value.loadState)
  }

  // Game Masters can enter hidden group folders, but folders outside the group are inaccessible to
  // either role.
  @Test
  fun gameMasterCanNavigateHiddenFoldersButNeitherRoleCanOpenUnsharedFolders() = runTest {
    val hidden = groupFolder("hidden", hidden = true)
    val hiddenChild = groupFile("child", parentId = hidden.id, hidden = true)
    val unshared =
        groupFolder(
            "unshared",
            groups = emptyList(),
        )
    val repository = FakeWorkspaceRepository(listOf(hidden, hiddenChild, unshared))
    val gm = viewModel(repository, GroupWorkspaceRole.GAME_MASTER)
    val player = viewModel(repository, GroupWorkspaceRole.PLAYER)
    runCurrent()

    gm.loadWorkspace(hidden.id)
    runCurrent()
    assertEquals(listOf(hiddenChild), gm.uiState.value.items)

    gm.loadWorkspace(unshared.id)
    runCurrent()
    assertTrue(gm.uiState.value.loadState is LoadState.Error)
    player.loadWorkspace(hidden.id)
    runCurrent()
    assertEquals(LoadState.Error("You cannot access this folder."), player.uiState.value.loadState)
  }

  // Ancestor visibility is rechecked on reload, not observed live while a player stays in a nested
  // folder.
  @Test
  fun reloadingRechecksAncestorVisibilityButDoesNotSubscribeToAncestorChanges() = runTest {
    val ancestor = groupFolder("ancestor")
    val nested = groupFolder("nested", parentId = ancestor.id)
    val child = groupFile("child", parentId = nested.id)
    val repository = FakeWorkspaceRepository(listOf(ancestor, nested, child))
    val player = viewModel(repository, GroupWorkspaceRole.PLAYER)
    runCurrent()
    player.loadWorkspace(nested.id)
    runCurrent()
    assertEquals(listOf(child), player.uiState.value.items)

    repository.replaceFile(
        ancestor.copy(placements = ancestor.placements + (GROUP_ID to Placement(GROUP_ID, true)))
    )
    runCurrent()

    // The selected folder flow is unchanged; ancestor metadata is checked on the next load.
    assertEquals(LoadState.Success, player.uiState.value.loadState)
    assertEquals(listOf(child), player.uiState.value.items)

    player.loadWorkspace("")
    runCurrent()
    assertTrue(player.uiState.value.items.isEmpty())

    player.loadWorkspace(nested.id)
    runCurrent()
    assertEquals(LoadState.Error("You cannot access this folder."), player.uiState.value.loadState)
    assertTrue(player.uiState.value.items.isEmpty())

    repository.replaceFile(ancestor.withoutPlacement(GROUP_ID))
    player.loadWorkspace(nested.id)
    runCurrent()
    assertEquals(LoadState.Error("You cannot access this folder."), player.uiState.value.loadState)
  }

  // An exception while evaluating visible folders becomes a workspace error with a clear fallback
  // message.
  @Test
  fun workspaceVisibilityFailureClearsItemsAndReportsFallbackMessage() = runTest {
    val folder = groupFolder("folder")
    val repository =
        FakeWorkspaceRepository(
            listOf(folder),
            folderFailures = mapOf(folder.id to IllegalStateException()),
        )
    val player = viewModel(repository, GroupWorkspaceRole.PLAYER)

    runCurrent()

    assertTrue(player.uiState.value.items.isEmpty())
    assertEquals(
        LoadState.Error("Unable to load workspace."),
        player.uiState.value.loadState,
    )
    assertEquals("Unable to load workspace.", player.uiState.value.errorMessage)
  }

  // Cancellation during workspace collection is propagated and does not become an error state.
  @Test
  fun workspaceLoadCancellationIsNotReportedAsAnError() = runTest {
    val cancellation = CancellationException("workspace collection cancelled")
    val repository =
        FakeWorkspaceRepository(
            listOf(groupFile("file")),
            folderFailures = mapOf(GROUP_ID to cancellation),
        )
    val player = viewModel(repository, GroupWorkspaceRole.PLAYER)

    runCurrent()

    assertEquals(LoadState.Loading, player.uiState.value.loadState)
    assertTrue(player.uiState.value.items.isEmpty())
    assertEquals("", player.uiState.value.errorMessage)
  }

  // A suspended older request is canceled, and its later folder emissions cannot replace current
  // content.
  @Test
  fun loadsCancelStaleRequestsAndKeepCurrentFolderItems() = runTest {
    val slowFolder = groupFolder("slow")
    val fastFolder = groupFolder("fast")
    val slow = groupFile("slow-item", parentId = slowFolder.id)
    val fast = groupFile("fast-item", parentId = fastFolder.id)
    val slowGate = CompletableDeferred<Unit>()
    val repository =
        FakeWorkspaceRepository(
            listOf(slowFolder, fastFolder, slow, fast),
            folderGates = mapOf(slowFolder.id to slowGate),
        )
    val viewModel = viewModel(repository, GroupWorkspaceRole.GAME_MASTER)
    runCurrent()

    viewModel.loadWorkspace(slowFolder.id)
    runCurrent()
    assertTrue(repository.folderStarts.getValue(slowFolder.id).isCompleted)
    assertTrue(viewModel.uiState.value.loadState is LoadState.Loading)

    viewModel.loadWorkspace(fastFolder.id)
    runCurrent()
    assertTrue(slowFolder.id in repository.cancelledFolderRequests)
    assertTrue(fastFolder.id in repository.folderRequests)
    assertEquals(listOf(fast), viewModel.uiState.value.items)

    // The old collection has been canceled, but its repository flow remains independently
    // writable.
    slowGate.complete(Unit)
    repository.emitFolderItems(
        slowFolder.id,
        listOf(groupFile("late-slow", parentId = slowFolder.id)),
    )
    runCurrent()

    assertEquals(fastFolder.id, viewModel.uiState.value.currentFolderId)
    assertEquals(listOf(fast), viewModel.uiState.value.items)
    assertEquals(LoadState.Success, viewModel.uiState.value.loadState)
  }

  // A Game Master's new folder uses the placement of the selected parent.
  @Test
  fun gameMasterCreatesFolderInSelectedWorkspace() = runTest {
    val parent = groupFolder("parent")
    val repository = FakeWorkspaceRepository(listOf(parent))
    val gm = viewModel(repository, GroupWorkspaceRole.GAME_MASTER)
    runCurrent()
    gm.loadWorkspace(parent.id)
    runCurrent()

    gm.createFolder(" New folder ")
    runCurrent()

    val created = repository.files.getValue("new-folder-1")
    assertEquals("New folder", created.name)
    assertEquals(OWNER_ID, created.ownerId)
    assertEquals(Placement(parent.id, false), created.placements[GROUP_ID])
    assertEquals(listOf(created), gm.uiState.value.items)
  }

  // A player's folder at the group root is stored with all its expected fields.
  @Test
  fun playerCreatesFolderInGroupRootWithExpectedFields() = runTest {
    val repository = FakeWorkspaceRepository()
    val player = viewModel(repository, GroupWorkspaceRole.PLAYER)
    runCurrent()

    player.createFolder(" Maps ")
    runCurrent()

    val expected =
        JDRFile(
            id = "new-folder-1",
            name = "Maps",
            ownerId = OWNER_ID,
            sharedGroupsIds = listOf(GROUP_ID),
            placements = mapOf(GROUP_ID to Placement(GROUP_ID, false)),
            personalParentId = PERSONAL_ROOT_ID,
            type = DocumentType.FOLDER,
            content = "",
        )
    assertEquals(expected, repository.files.getValue("new-folder-1"))
    assertEquals(listOf(expected), player.uiState.value.items)
    assertEquals("", player.uiState.value.errorMessage)
  }

  // Each created item gets its own document ID.
  @Test
  fun playerCreatedFoldersReceiveDistinctIds() = runTest {
    val repository = FakeWorkspaceRepository()
    val player = viewModel(repository, GroupWorkspaceRole.PLAYER)
    runCurrent()

    player.createFolder("First")
    player.createFolder("Second")
    runCurrent()

    assertEquals(
        mapOf("new-folder-1" to "First", "new-folder-2" to "Second"),
        repository.files.mapValues { it.value.name },
    )
  }

  // A folder created inside a visible folder stays visible.
  @Test
  fun playerCreatesVisibleFolderInsideVisibleFolder() = runTest {
    val parent = groupFolder("visible-parent")

    val created = createFolderAsPlayerIn(parent.id, listOf(parent))

    assertEquals(Placement(parent.id, false), created.placements[GROUP_ID])
  }

  // A player can create inside their own hidden folder, and the new folder is hidden.
  @Test
  fun playerCreatesHiddenFolderInsideOwnHiddenFolder() = runTest {
    val parent = groupFolder("own-hidden", ownerId = OWNER_ID, hidden = true)

    val created = createFolderAsPlayerIn(parent.id, listOf(parent))

    assertEquals(Placement(parent.id, true), created.placements[GROUP_ID])
  }

  // A foreign hidden folder directly holding the player's item accepts new folders.
  @Test
  fun playerCreatesHiddenFolderInsideHiddenFolderHoldingOwnedItem() = runTest {
    val parent = groupFolder("foreign-hidden", hidden = true)
    val owned = groupFile("owned", parentId = parent.id, ownerId = OWNER_ID, hidden = true)

    val created = createFolderAsPlayerIn(parent.id, listOf(parent, owned))

    assertEquals(Placement(parent.id, true), created.placements[GROUP_ID])
  }

  // An item owned deeper in the tree also grants access to the hidden ancestor.
  @Test
  fun playerCreatesHiddenFolderInsideHiddenFolderWithOwnedDescendant() = runTest {
    val parent = groupFolder("foreign-hidden", hidden = true)
    val child = groupFolder("foreign-child", parentId = parent.id, hidden = true)
    val owned = groupFile("owned", parentId = child.id, ownerId = OWNER_ID, hidden = true)

    val created = createFolderAsPlayerIn(parent.id, listOf(parent, child, owned))

    assertEquals(Placement(parent.id, true), created.placements[GROUP_ID])
  }

  // A player can also create a character inside a hidden folder they can access.
  @Test
  fun playerCreatesHiddenCharacterInsideAccessibleHiddenFolder() = runTest {
    val parent = groupFolder("own-hidden", ownerId = OWNER_ID, hidden = true)
    val repository = FakeWorkspaceRepository(listOf(parent))
    val player = viewModel(repository, GroupWorkspaceRole.PLAYER)
    runCurrent()
    player.loadWorkspace(parent.id)
    runCurrent()

    player.createCharacter {}
    runCurrent()

    assertEquals(
        Placement(parent.id, true),
        repository.files.getValue("new-folder-1").placements[GROUP_ID],
    )
  }

  // A hidden folder with nothing owned by the player can be neither opened nor filled.
  @Test
  fun playerCannotOpenOrCreateInsideForeignHiddenFolder() = runTest {
    val hidden = groupFolder("foreign-hidden", hidden = true)
    val foreignChild = groupFile("foreign-file", parentId = hidden.id, hidden = true)
    val repository = FakeWorkspaceRepository(listOf(hidden, foreignChild))
    val player = viewModel(repository, GroupWorkspaceRole.PLAYER)
    val createdIds = mutableListOf<String>()
    runCurrent()
    assertEquals(emptyList<JDRFile>(), player.uiState.value.items)

    player.loadWorkspace(hidden.id)
    runCurrent()
    assertEquals("You cannot access this folder.", player.uiState.value.errorMessage)
    assertEquals(emptyList<JDRFile>(), player.uiState.value.items)

    player.clearError()
    player.createFolder("Intruder")
    runCurrent()
    assertEquals("You cannot access this folder.", player.uiState.value.errorMessage)

    player.clearError()
    player.createCharacter { createdIds += it }
    runCurrent()
    assertEquals("You cannot access this folder.", player.uiState.value.errorMessage)
    assertEquals(emptyList<String>(), createdIds)
    assertEquals(setOf(hidden, foreignChild), repository.files.values.toSet())
  }

  // The Game Master can still create inside any hidden folder.
  @Test
  fun gameMasterCreatesHiddenFolderInsideForeignHiddenFolder() = runTest {
    val parent = groupFolder("foreign-hidden", hidden = true)
    val repository = FakeWorkspaceRepository(listOf(parent))
    val gm = viewModel(repository, GroupWorkspaceRole.GAME_MASTER)
    runCurrent()
    gm.loadWorkspace(parent.id)
    runCurrent()

    gm.createFolder("Secrets")
    runCurrent()

    assertEquals(
        Placement(parent.id, true),
        repository.files.getValue("new-folder-1").placements[GROUP_ID],
    )
  }

  // Owning a folder gives a player no rights over other members' files inside it.
  @Test
  fun playerFolderOwnershipDoesNotGrantManagementOfForeignFiles() = runTest {
    val folder = groupFolder("own-folder", ownerId = OWNER_ID)
    val foreign = groupFile("foreign", parentId = folder.id)
    val repository = FakeWorkspaceRepository(listOf(folder, foreign))
    val player = viewModel(repository, GroupWorkspaceRole.PLAYER)
    runCurrent()
    player.loadWorkspace(folder.id)
    runCurrent()

    player.editItem(foreign.copy(name = "Renamed"))
    runCurrent()
    assertEquals(
        "You cannot edit an item that you do not own.",
        player.uiState.value.errorMessage,
    )

    player.clearError()
    player.setItemHidden(foreign, true)
    runCurrent()
    assertEquals(
        "You are not allowed to perform this operation.",
        player.uiState.value.errorMessage,
    )

    player.clearError()
    player.removeItem(foreign)
    runCurrent()
    assertEquals(
        "You are not allowed to perform this operation.",
        player.uiState.value.errorMessage,
    )
    assertEquals(foreign, repository.files.getValue(foreign.id))
    assertTrue(repository.hiddenRequests.isEmpty())
    assertTrue(repository.removeRequests.isEmpty())
  }

  // A repository failure is shown to the player and leaves nothing behind.
  @Test
  fun failedFolderCreationReportsError() = runTest {
    val repository = FakeWorkspaceRepository().apply { createFailure = Exception("Create denied") }
    val player = viewModel(repository, GroupWorkspaceRole.PLAYER)
    runCurrent()

    player.createFolder("Maps")
    runCurrent()

    assertEquals("Create denied", player.uiState.value.errorMessage)
    assertFalse(player.uiState.value.isOperationInProgress)
    assertEquals(emptyMap<String, JDRFile>(), repository.files.toMap())
  }

  // Someone outside the group still cannot create a folder.
  @Test
  fun nonMemberCannotCreateFolder() = runTest {
    val repository = FakeWorkspaceRepository()
    val group =
        CampaignGroup(
            id = GROUP_ID,
            gameMasterId = OTHER_USER_ID,
            participantIds = listOf(OTHER_USER_ID),
        )
    val outsider =
        viewModel(
            repository,
            GroupWorkspaceRole.PLAYER,
            groupRepository = FakeGroupRepository(group),
        )
    runCurrent()

    outsider.createFolder("Maps")
    runCurrent()

    assertEquals("You are not a member of this group.", outsider.uiState.value.errorMessage)
    assertEquals(emptyMap<String, JDRFile>(), repository.files.toMap())
  }

  // Players may add their own files to an accessible folder using the requested placement.
  @Test
  fun playerAddsOwnedFileToSelectedFolder() = runTest {
    val hiddenParent = groupFolder("hidden-parent", hidden = true)
    val ownedAnchor = groupFile("owned-anchor", parentId = hiddenParent.id, ownerId = OWNER_ID)
    val file = JDRFile(id = "personal", ownerId = OWNER_ID, personalParentId = "personal-root")
    val repository = FakeWorkspaceRepository(listOf(hiddenParent, ownedAnchor, file))
    val player = viewModel(repository, GroupWorkspaceRole.PLAYER)
    runCurrent()
    player.loadWorkspace(hiddenParent.id)
    runCurrent()

    player.shareItem(file.id)
    runCurrent()

    assertEquals(
        listOf(AddRequest(file.id, GROUP_ID, hiddenParent.id, isHidden = false)),
        repository.addRequests,
    )
    val shared = repository.files.getValue(file.id)
    assertEquals(listOf(GROUP_ID), shared.sharedGroupsIds)
    assertEquals(Placement(hiddenParent.id, false), shared.placements[GROUP_ID])
    assertEquals("personal-root", shared.personalParentId)
    assertEquals(
        setOf(ownedAnchor.id, file.id),
        player.uiState.value.items.map { it.id }.toSet(),
    )
  }

  // Game Masters can add their own files but ownership still prevents adding another member's file.
  @Test
  fun gameMasterCanAddOwnedFileButCannotAddAnotherUsersFile() = runTest {
    val groupRoot =
        JDRFile(
            id = GROUP_ID,
            ownerId = OWNER_ID,
            type = DocumentType.GROUP_ROOT,
            sharedGroupsIds = listOf(GROUP_ID),
        )
    val owned = JDRFile(id = "owned", ownerId = OWNER_ID)
    val foreign = JDRFile(id = "foreign", ownerId = OTHER_USER_ID)
    val repository = FakeWorkspaceRepository(listOf(groupRoot, owned, foreign))
    val gm = viewModel(repository, GroupWorkspaceRole.GAME_MASTER)
    runCurrent()

    gm.shareItem(owned.id)
    runCurrent()
    assertEquals(
        Placement(GROUP_ID, false),
        repository.files.getValue(owned.id).placements[GROUP_ID],
    )
    assertEquals("", gm.uiState.value.errorMessage)

    gm.shareItem(foreign.id)
    runCurrent()
    assertEquals("You can only share items that you own.", gm.uiState.value.errorMessage)
    assertTrue(GROUP_ID !in repository.files.getValue(foreign.id).sharedGroupsIds)
    assertEquals(
        listOf(AddRequest(owned.id, GROUP_ID, GROUP_ID, isHidden = false)),
        repository.addRequests,
    )
  }

  // Players cannot add someone else's file or add a file that already has a placement in the group.
  @Test
  fun playersCannotAddFilesTheyDoNotOwnOrItemsAlreadyInGroup() = runTest {
    val groupRoot =
        JDRFile(
            id = GROUP_ID,
            ownerId = OWNER_ID,
            type = DocumentType.GROUP_ROOT,
            sharedGroupsIds = listOf(GROUP_ID),
        )
    val foreign = JDRFile(id = "foreign", ownerId = OTHER_USER_ID)
    val alreadyShared = groupFile("shared", ownerId = OWNER_ID)
    val repository = FakeWorkspaceRepository(listOf(groupRoot, foreign, alreadyShared))
    val player = viewModel(repository, GroupWorkspaceRole.PLAYER)
    runCurrent()

    player.shareItem(foreign.id)
    runCurrent()
    assertEquals("You can only share items that you own.", player.uiState.value.errorMessage)
    assertTrue(GROUP_ID !in repository.files.getValue(foreign.id).sharedGroupsIds)
    assertTrue(repository.addRequests.isEmpty())

    player.clearError()
    player.shareItem(alreadyShared.id)
    runCurrent()
    assertEquals("This item is already in the group.", player.uiState.value.errorMessage)
    assertEquals(alreadyShared, repository.files.getValue(alreadyShared.id))
    assertTrue(repository.addRequests.isEmpty())
  }

  // The ViewModel forwards hide/reveal requests and reflects the selected folder's visibility.
  @Test
  fun gameMasterCanHideAndRevealFolder() = runTest {
    val folder = groupFolder("folder")
    val nested = groupFolder("nested", parentId = folder.id)
    val child = groupFile("child", parentId = nested.id)
    val repository = FakeWorkspaceRepository(listOf(folder, nested, child))
    val gm = viewModel(repository, GroupWorkspaceRole.GAME_MASTER)
    val player = viewModel(repository, GroupWorkspaceRole.PLAYER)
    runCurrent()

    gm.setItemHidden(folder, true)
    runCurrent()
    assertTrue(repository.files.getValue(folder.id).placements.getValue(GROUP_ID).isHidden)
    assertFalse(repository.files.getValue(nested.id).placements.getValue(GROUP_ID).isHidden)
    assertFalse(repository.files.getValue(child.id).placements.getValue(GROUP_ID).isHidden)
    assertEquals(listOf(folder.id to true), repository.hiddenRequests)
    assertTrue(player.uiState.value.items.isEmpty())

    gm.setItemHidden(repository.files.getValue(folder.id), false)
    runCurrent()
    assertFalse(repository.files.getValue(folder.id).placements.getValue(GROUP_ID).isHidden)
    assertFalse(repository.files.getValue(nested.id).placements.getValue(GROUP_ID).isHidden)
    assertFalse(repository.files.getValue(child.id).placements.getValue(GROUP_ID).isHidden)
    assertEquals(listOf(folder.id to true, folder.id to false), repository.hiddenRequests)

    assertEquals(listOf(folder.id), player.uiState.value.items.map { it.id })
    player.loadWorkspace(nested.id)
    runCurrent()
    assertEquals(listOf(child.id), player.uiState.value.items.map { it.id })
  }

  // check that the game master can reveal any items inside a group
  @Test
  fun gameMasterCanRevealAnotherPlayersHiddenFile() = runTest {
    val foreignFile =
        groupFile(
            "foreign-file",
            ownerId = OTHER_USER_ID,
            hidden = true,
        )
    val repository = FakeWorkspaceRepository(listOf(foreignFile))
    val gm = viewModel(repository, GroupWorkspaceRole.GAME_MASTER)

    runCurrent()

    // Initially, the file is hidden.
    assertTrue(repository.files.getValue(foreignFile.id).placements.getValue(GROUP_ID).isHidden)

    // The GameMaster can reveal another player's file.
    gm.setItemHidden(foreignFile, false)
    runCurrent()

    assertFalse(repository.files.getValue(foreignFile.id).placements.getValue(GROUP_ID).isHidden)
    assertEquals(
        listOf(foreignFile.id to false),
        repository.hiddenRequests,
    )
    assertEquals("", gm.uiState.value.errorMessage)
  }

  // Players cannot hide or reveal another member's item.
  @Test
  fun playerCannotHideOrRevealForeignItem() = runTest {
    val item = groupFile("foreign")
    val hiddenItem = groupFile("hidden-foreign", hidden = true)
    val repository = FakeWorkspaceRepository(listOf(item, hiddenItem))
    val player = viewModel(repository, GroupWorkspaceRole.PLAYER)
    runCurrent()

    player.setItemHidden(item, true)
    runCurrent()
    assertEquals(
        "You are not allowed to perform this operation.",
        player.uiState.value.errorMessage,
    )
    assertFalse(repository.files.getValue(item.id).placements.getValue(GROUP_ID).isHidden)

    player.clearError()
    player.setItemHidden(item, false)
    runCurrent()
    assertEquals(
        "You are not allowed to perform this operation.",
        player.uiState.value.errorMessage,
    )
    assertFalse(repository.files.getValue(item.id).placements.getValue(GROUP_ID).isHidden)

    player.clearError()
    player.setItemHidden(hiddenItem, false)
    runCurrent()
    assertEquals("You cannot access this item.", player.uiState.value.errorMessage)
    assertTrue(repository.files.getValue(hiddenItem.id).placements.getValue(GROUP_ID).isHidden)
    assertTrue(repository.hiddenRequests.isEmpty())
  }

  // Players can hide and reveal their own group items.
  @Test
  fun playerCanHideAndRevealOwnItem() = runTest {
    val item = groupFile("owned", ownerId = OWNER_ID)
    val repository = FakeWorkspaceRepository(listOf(item))
    val player = viewModel(repository, GroupWorkspaceRole.PLAYER)
    runCurrent()

    player.setItemHidden(item, true)
    runCurrent()
    assertEquals("", player.uiState.value.errorMessage)
    assertTrue(repository.files.getValue(item.id).placements.getValue(GROUP_ID).isHidden)

    player.setItemHidden(repository.files.getValue(item.id), false)
    runCurrent()
    assertEquals("", player.uiState.value.errorMessage)
    assertFalse(repository.files.getValue(item.id).placements.getValue(GROUP_ID).isHidden)
    assertEquals(listOf(item.id to true, item.id to false), repository.hiddenRequests)
  }

  // A visibility repository failure is exposed through the shared workspace error state.
  @Test
  fun visibilityChangeFailureSetsError() = runTest {
    val item = groupFile("owned", ownerId = OWNER_ID)
    val repository = FakeWorkspaceRepository(listOf(item))
    repository.hiddenFailure = IllegalStateException("visibility update failed")
    val player = viewModel(repository, GroupWorkspaceRole.PLAYER)
    runCurrent()

    player.setItemHidden(item, true)
    runCurrent()

    assertEquals("visibility update failed", player.uiState.value.errorMessage)
    assertFalse(player.uiState.value.isOperationInProgress)
    assertFalse(repository.files.getValue(item.id).placements.getValue(GROUP_ID).isHidden)
  }

  // A missing selected folder redirects to the root while preserving its transient error.
  @Test
  fun missingFolderErrorSurvivesRedirectToRoot() = runTest {
    val folder = groupFolder("folder")
    val repository = FakeWorkspaceRepository(listOf(folder))
    val gm = viewModel(repository, GroupWorkspaceRole.GAME_MASTER)
    runCurrent()

    gm.loadWorkspace(folder.id)
    runCurrent()
    repository.deleteFile(folder.id)
    runCurrent()

    assertEquals("", gm.uiState.value.currentFolderId)
    assertEquals("This folder is no longer available.", gm.uiState.value.errorMessage)
    assertEquals(LoadState.Success, gm.uiState.value.loadState)
  }

  // Revealing an owned item is rejected while its parent folder remains hidden.
  @Test
  fun cannotRevealOwnedItemInsideHiddenParent() = runTest {
    val parent = groupFolder("hidden-parent", ownerId = OWNER_ID, hidden = true)
    val item = groupFile("owned-child", parentId = parent.id, ownerId = OWNER_ID, hidden = true)
    val repository = FakeWorkspaceRepository(listOf(parent, item))
    val player = viewModel(repository, GroupWorkspaceRole.PLAYER)
    runCurrent()
    player.loadWorkspace(parent.id)
    runCurrent()

    player.setItemHidden(item, false)
    runCurrent()

    assertEquals("Reveal the parent folder first.", player.uiState.value.errorMessage)
    assertTrue(repository.files.getValue(item.id).placements.getValue(GROUP_ID).isHidden)
    assertEquals(listOf(item.id to false), repository.hiddenRequests)
  }

  // Players can edit and remove their own group items, but cannot use a forged owner field.
  @Test
  fun playersMayEditAndRemoveOnlyTheirOwnGroupItems() = runTest {
    val folder = groupFolder("folder")
    val owned = groupFile("owned", parentId = folder.id, ownerId = OWNER_ID, content = "old")
    val foreign = groupFile("foreign", parentId = folder.id)
    val repository = FakeWorkspaceRepository(listOf(folder, owned, foreign))
    val player = viewModel(repository, GroupWorkspaceRole.PLAYER)
    runCurrent()
    player.loadWorkspace(folder.id)
    runCurrent()

    player.editItem(foreign.copy(name = "forged", ownerId = OWNER_ID))
    runCurrent()
    assertEquals("You cannot edit an item that you do not own.", player.uiState.value.errorMessage)
    assertEquals(foreign, repository.files.getValue(foreign.id))

    player.clearError()
    player.removeItem(foreign)
    runCurrent()
    assertEquals(
        "You are not allowed to perform this operation.",
        player.uiState.value.errorMessage,
    )
    assertEquals(foreign, repository.files.getValue(foreign.id))

    player.clearError()
    player.editItem(owned.copy(name = " edited ", content = "new"))
    runCurrent()
    assertEquals("edited", repository.files.getValue(owned.id).name)
    assertEquals("new", repository.files.getValue(owned.id).content)

    player.removeItem(owned)
    runCurrent()
    assertFalse(GROUP_ID in repository.files.getValue(owned.id).sharedGroupsIds)
    assertTrue(owned.id in repository.files)
    assertTrue(GROUP_ID in repository.files.getValue(foreign.id).sharedGroupsIds)
  }

  // Game Masters and players can edit only the items they own.
  @Test
  fun gameMastersAndPlayersCanEditOnlyTheirOwnItems() = runTest {
    val folder = groupFolder("folder")
    val file = groupFile("file", parentId = folder.id)
    val gmFile = groupFile("gm-file", parentId = folder.id, ownerId = OWNER_ID)
    val repository = FakeWorkspaceRepository(listOf(folder, file, gmFile))
    val gm = viewModel(repository, GroupWorkspaceRole.GAME_MASTER)
    runCurrent()
    gm.loadWorkspace(folder.id)
    runCurrent()
    gm.editItem(file.copy(name = "Updated"))
    runCurrent()
    assertEquals("You cannot edit an item that you do not own.", gm.uiState.value.errorMessage)
    assertEquals(file, repository.files.getValue(file.id))

    gm.clearError()
    gm.editItem(gmFile.copy(name = "GM updated"))
    runCurrent()
    assertEquals("", gm.uiState.value.errorMessage)
    assertEquals("GM updated", repository.files.getValue(gmFile.id).name)

    val player = viewModel(repository, GroupWorkspaceRole.PLAYER)
    runCurrent()
    player.loadWorkspace(folder.id)
    runCurrent()
    player.editItem(file.copy(name = "Not allowed"))
    runCurrent()
    assertEquals("You cannot edit an item that you do not own.", player.uiState.value.errorMessage)
    assertEquals(file, repository.files.getValue(file.id))
  }

  // Folder removal is delegated with the selected parent and group; repository tests cover subtree
  // effects.
  @Test
  fun removingFolderPassesItsPlacementToRepository() = runTest {
    val folder =
        groupFolder(
            "folder",
            groups = listOf(GROUP_ID, OTHER_GROUP_ID),
        )
    val child =
        groupFile(
            "child",
            parentId = folder.id,
            groups = listOf(GROUP_ID, OTHER_GROUP_ID),
        )
    val repository = FakeWorkspaceRepository(listOf(folder, child))
    val gm = viewModel(repository, GroupWorkspaceRole.GAME_MASTER)
    runCurrent()

    gm.removeItem(folder)
    runCurrent()

    assertEquals(
        listOf(RemovalRequest(folder.id, GROUP_ID)),
        repository.removeRequests,
    )
    assertEquals(listOf(OTHER_GROUP_ID), repository.files.getValue(folder.id).sharedGroupsIds)
    assertEquals(
        listOf(GROUP_ID, OTHER_GROUP_ID),
        repository.files.getValue(child.id).sharedGroupsIds,
    )
    assertEquals(
        Placement(OTHER_GROUP_ID, false),
        repository.files.getValue(child.id).placements[OTHER_GROUP_ID],
    )
    assertTrue(folder.id in repository.files)
    assertTrue(child.id in repository.files)
  }

  // A failed repository update reports an error without changing the item or leaving the operation
  // busy.
  @Test
  fun operationErrorsDoNotMutateItemsAndOperationStateRecovers() = runTest {
    val folder = groupFolder("folder")
    // The GM owns the file, so the edit passes the ownership check and reaches the repository.
    val file = groupFile("file", parentId = folder.id, ownerId = OWNER_ID)
    val repository = FakeWorkspaceRepository(listOf(folder, file))
    repository.updateFailure = IllegalStateException("write failed")
    val gm = viewModel(repository, GroupWorkspaceRole.GAME_MASTER)
    runCurrent()
    gm.loadWorkspace(folder.id)
    runCurrent()

    gm.editItem(file.copy(name = "Changed"))
    runCurrent()

    assertEquals(file, repository.files.getValue(file.id))
    assertEquals("write failed", gm.uiState.value.errorMessage)
    assertFalse(gm.uiState.value.isOperationInProgress)
  }

  // Blank inputs are rejected up front, before any repository call.
  @Test
  fun blankInputsAreRejectedWithoutRepositoryCalls() = runTest {
    val file = groupFile("file", ownerId = OWNER_ID)
    val repository = FakeWorkspaceRepository(listOf(file))
    val gm = viewModel(repository, GroupWorkspaceRole.GAME_MASTER)
    runCurrent()

    assertEquals(null, gm.createFolder("   "))
    assertEquals("Folder name cannot be empty.", gm.uiState.value.errorMessage)
    assertEquals(null, gm.shareItem(" "))
    assertEquals("File ID cannot be empty.", gm.uiState.value.errorMessage)
    assertEquals(null, gm.editItem(file.copy(name = " ")))
    assertEquals("The item name cannot be blank.", gm.uiState.value.errorMessage)
    assertEquals(listOf(file), repository.files.values.toList())
    assertThrows(IllegalArgumentException::class.java) {
      GroupWorkspaceViewModel(" ", FakeGroupRepository(null), repository, FakeAuthRepository())
    }
  }

  // A signed-out user gets a signed-out state and later loads are ignored.
  @Test
  fun signedOutUserCannotLoadWorkspace() = runTest {
    val folder = groupFolder("folder")
    val viewModel =
        viewModel(
            FakeWorkspaceRepository(listOf(folder)),
            GroupWorkspaceRole.PLAYER,
            authRepository = FakeAuthRepository(user = null),
        )
    runCurrent()

    assertEquals(LoadState.SignedOut, viewModel.uiState.value.loadState)

    viewModel.loadWorkspace(folder.id)
    runCurrent()

    assertEquals("", viewModel.uiState.value.currentFolderId)
    assertEquals(LoadState.SignedOut, viewModel.uiState.value.loadState)
  }

  @Test
  fun signingOutClearsLoadedWorkspaceAndPermissions() = runTest {
    val authRepository = FakeAuthRepository()
    val viewModel =
        viewModel(
            FakeWorkspaceRepository(listOf(groupFile("notes"))),
            GroupWorkspaceRole.GAME_MASTER,
            authRepository = authRepository,
        )
    runCurrent()

    assertEquals(LoadState.Success, viewModel.uiState.value.loadState)
    assertEquals(listOf(groupFile("notes")), viewModel.uiState.value.items)
    assertTrue(viewModel.uiState.value.isGameMaster)

    authRepository.setUser(null)
    runCurrent()

    assertEquals(LoadState.SignedOut, viewModel.uiState.value.loadState)
    assertEquals(emptyList<JDRFile>(), viewModel.uiState.value.items)
    assertFalse(viewModel.uiState.value.isGameMaster)
    assertFalse(viewModel.uiState.value.canEditItems)
  }

  // Root folders are placed under the group, and folders inherit a hidden parent's visibility.
  @Test
  fun createdFoldersUseRootOrInheritHiddenParent() = runTest {
    val hiddenParent = groupFolder("hidden-parent", hidden = true)
    val repository = FakeWorkspaceRepository(listOf(hiddenParent))
    val gm = viewModel(repository, GroupWorkspaceRole.GAME_MASTER)
    runCurrent()

    gm.createFolder("Root folder")
    runCurrent()
    assertEquals(
        Placement(GROUP_ID, false),
        repository.files.getValue("new-folder-1").placements[GROUP_ID],
    )

    gm.loadWorkspace(hiddenParent.id)
    runCurrent()
    gm.createFolder("Nested folder")
    runCurrent()
    assertEquals(
        Placement(hiddenParent.id, true),
        repository.files.getValue("new-folder-2").placements[GROUP_ID],
    )
  }

  // A player can create a character at the group root and receives its ID.
  @Test
  fun playerCreatesCharacterInGroupRootAndReceivesItsId() = runTest {
    val repository = FakeWorkspaceRepository()
    val player = viewModel(repository, GroupWorkspaceRole.PLAYER)
    val createdIds = mutableListOf<String>()
    runCurrent()

    player.createCharacter { createdIds += it }
    runCurrent()

    val created = repository.files.getValue("new-folder-1")
    assertEquals(listOf("new-folder-1"), createdIds)
    assertEquals("New character", created.name)
    assertEquals(DocumentType.CHARACTER, created.type)
    assertEquals(OWNER_ID, created.ownerId)
    assertEquals(JDRFileJsonCodec.serializeCharacter(null), created.content)
    assertEquals(listOf(GROUP_ID), created.sharedGroupsIds)
    assertEquals(Placement(GROUP_ID, false), created.placements[GROUP_ID])
    assertEquals(listOf(created), player.uiState.value.items)
  }

  // A character created in a hidden folder is hidden too.
  @Test
  fun createdCharacterInheritsCurrentHiddenFolder() = runTest {
    val hiddenParent = groupFolder("hidden-parent", hidden = true)
    val repository = FakeWorkspaceRepository(listOf(hiddenParent))
    val gm = viewModel(repository, GroupWorkspaceRole.GAME_MASTER)
    runCurrent()
    gm.loadWorkspace(hiddenParent.id)
    runCurrent()

    gm.createCharacter {}
    runCurrent()

    assertEquals(
        Placement(hiddenParent.id, true),
        repository.files.getValue("new-folder-1").placements[GROUP_ID],
    )
  }

  // A failed creation exposes the error, stores nothing and skips the callback.
  @Test
  fun failedCharacterCreationReportsErrorWithoutCallback() = runTest {
    val repository = FakeWorkspaceRepository().apply { createFailure = Exception("Create denied") }
    val gm = viewModel(repository, GroupWorkspaceRole.GAME_MASTER)
    val createdIds = mutableListOf<String>()
    runCurrent()

    gm.createCharacter { createdIds += it }
    runCurrent()

    assertEquals(emptyList<String>(), createdIds)
    assertEquals("Create denied", gm.uiState.value.errorMessage)
    assertFalse(gm.uiState.value.isOperationInProgress)
    assertEquals(emptyMap<String, JDRFile>(), repository.files.toMap())
  }

  // Group roots and missing items cannot be shared to the workspace.
  @Test
  fun shareItemRejectsGroupRootsAndMissingItems() = runTest {
    val root = JDRFile(id = "root", ownerId = OWNER_ID, type = DocumentType.GROUP_ROOT)
    val repository = FakeWorkspaceRepository(listOf(root))
    val gm = viewModel(repository, GroupWorkspaceRole.GAME_MASTER)
    runCurrent()

    gm.shareItem(root.id)
    runCurrent()
    assertEquals("A group root cannot be added here.", gm.uiState.value.errorMessage)
    gm.shareItem("missing")
    runCurrent()
    assertEquals("Document not found in Firestore", gm.uiState.value.errorMessage)
    assertTrue(repository.addRequests.isEmpty())
  }

  // A folder is shared into the current folder and asks the repository to include its content.
  @Test
  fun shareItemSharesAFolderWithItsContentInTheCurrentFolder() = runTest {
    val destination = groupFolder("destination")
    val folder = JDRFile(id = "personal-folder", ownerId = OWNER_ID, type = DocumentType.FOLDER)
    val child = JDRFile(id = "child", ownerId = OWNER_ID, personalParentId = folder.id)
    val repository = FakeWorkspaceRepository(listOf(destination, folder, child))
    val gm = viewModel(repository, GroupWorkspaceRole.GAME_MASTER)
    runCurrent()
    gm.loadWorkspace(destination.id)
    runCurrent()

    gm.shareItem(folder.id)
    runCurrent()

    assertEquals("", gm.uiState.value.errorMessage)
    assertEquals(
        listOf(
            AddRequest(
                folder.id,
                GROUP_ID,
                destination.id,
                isHidden = false,
                includeContent = true,
            )
        ),
        repository.addRequests,
    )
  }

  // A repository failure is shown and the item stays out of the group.
  @Test
  fun shareItemShowsTheRepositoryFailure() = runTest {
    val groupRoot =
        JDRFile(
            id = GROUP_ID,
            ownerId = OWNER_ID,
            type = DocumentType.GROUP_ROOT,
            sharedGroupsIds = listOf(GROUP_ID),
        )
    val folder = JDRFile(id = "personal-folder", ownerId = OWNER_ID, type = DocumentType.FOLDER)
    val repository = FakeWorkspaceRepository(listOf(groupRoot, folder))
    repository.addFailure = IllegalStateException("Too many items to add at once (maximum 500).")
    val gm = viewModel(repository, GroupWorkspaceRole.GAME_MASTER)
    runCurrent()

    gm.shareItem(folder.id)
    runCurrent()

    assertEquals(
        "Too many items to add at once (maximum 500).",
        gm.uiState.value.errorMessage,
    )
    assertEquals(folder, repository.files.getValue(folder.id))
  }

  // Deleting the selected folder sends the user back to the group root.
  @Test
  fun deletedSelectedFolderReturnsToRootWithError() = runTest {
    val folder = groupFolder("folder")
    val rootFile = groupFile("root-file")
    val repository = FakeWorkspaceRepository(listOf(folder, rootFile))
    val gm = viewModel(repository, GroupWorkspaceRole.GAME_MASTER)
    runCurrent()
    gm.loadWorkspace(folder.id)
    runCurrent()

    repository.deleteFile(folder.id)
    runCurrent()

    assertEquals("", gm.uiState.value.currentFolderId)
    assertEquals(listOf(rootFile), gm.uiState.value.items)
    assertEquals(LoadState.Success, gm.uiState.value.loadState)
  }

  // Opening a file as a folder, or a folder with a broken ancestor path, is refused.
  @Test
  fun invalidFolderPathsCannotBeOpened() = runTest {
    val file = groupFile("file")
    val blankParent = groupFolder("blank-parent", parentId = "")
    val cycleA = groupFolder("cycle-a", parentId = "cycle-b")
    val cycleB = groupFolder("cycle-b", parentId = "cycle-a")
    val underFile = groupFolder("under-file", parentId = file.id)
    val unplacedParent = groupFolder("unplaced").copy(placements = emptyMap())
    val underUnplaced = groupFolder("under-unplaced", parentId = unplacedParent.id)
    val repository =
        FakeWorkspaceRepository(
            listOf(file, blankParent, cycleA, cycleB, underFile, unplacedParent, underUnplaced)
        )
    val gm = viewModel(repository, GroupWorkspaceRole.GAME_MASTER)
    val player = viewModel(repository, GroupWorkspaceRole.PLAYER)
    runCurrent()

    gm.loadWorkspace(file.id)
    runCurrent()
    assertEquals(
        LoadState.Error("The requested item is not a folder."),
        gm.uiState.value.loadState,
    )

    for (folder in listOf(blankParent, cycleA, underFile, underUnplaced)) {
      player.loadWorkspace(folder.id)
      runCurrent()
      assertEquals(
          LoadState.Error("You cannot access this folder."),
          player.uiState.value.loadState,
      )
    }
  }

  // Items outside the current folder or outside the group cannot be modified.
  @Test
  fun itemsOutsideCurrentWorkspaceCannotBeModified() = runTest {
    val folder = groupFolder("folder")
    val nested = groupFile("nested", parentId = folder.id)
    val unshared = JDRFile(id = "unshared", ownerId = OWNER_ID)
    val repository = FakeWorkspaceRepository(listOf(folder, nested, unshared))
    val gm = viewModel(repository, GroupWorkspaceRole.GAME_MASTER)
    runCurrent()

    gm.removeItem(nested)
    runCurrent()
    assertEquals(
        "This item is not in the current workspace folder.",
        gm.uiState.value.errorMessage,
    )

    gm.editItem(unshared.copy(name = "renamed"))
    runCurrent()
    assertEquals("This item does not belong to the current group.", gm.uiState.value.errorMessage)
    assertTrue(repository.removeRequests.isEmpty())
    assertEquals(unshared, repository.files.getValue(unshared.id))
  }

  // A failure without a message falls back to a generic operation error.
  @Test
  fun operationFailureWithoutMessageUsesFallbackError() = runTest {
    // The GM owns the file, so the edit passes the ownership check and reaches the repository.
    val file = groupFile("file", ownerId = OWNER_ID)
    val repository = FakeWorkspaceRepository(listOf(file))
    repository.updateFailure = IllegalStateException()
    val gm = viewModel(repository, GroupWorkspaceRole.GAME_MASTER)
    runCurrent()

    gm.editItem(file.copy(name = "Changed"))
    runCurrent()

    assertEquals("The workspace operation failed", gm.uiState.value.errorMessage)
    assertFalse(gm.uiState.value.isOperationInProgress)
  }

  private fun TestScope.createFolderAsPlayerIn(
      folderId: String,
      files: List<JDRFile>,
  ): JDRFile {
    val repository = FakeWorkspaceRepository(files)
    val player = viewModel(repository, GroupWorkspaceRole.PLAYER)
    runCurrent()
    player.loadWorkspace(folderId)
    runCurrent()

    player.createFolder("New folder")
    runCurrent()

    assertEquals("", player.uiState.value.errorMessage)
    return repository.files.getValue("new-folder-1")
  }

  // Renaming a character from the workspace renames it in its sheet too, keeping the rest of it.
  @Test
  fun renamingCharacterUpdatesTheNameInItsSheet() = runTest {
    val character =
        groupFile(
            "character",
            ownerId = OWNER_ID,
            type = DocumentType.CHARACTER,
            name = "Aria",
            content = CHARACTER_SHEET_JSON,
        )
    val repository = FakeWorkspaceRepository(listOf(character))
    val player = viewModel(repository, GroupWorkspaceRole.PLAYER)
    runCurrent()

    player.editItem(character.copy(name = "  Brann  "))
    runCurrent()

    val stored = repository.files.getValue(character.id)
    assertEquals("", player.uiState.value.errorMessage)
    assertEquals("Brann", stored.name)
    assertEquals(
        CharacterFile(
            "Brann",
            listOf(CharacterStat("strength", "Strength", 16.0)),
            listOf(CharacterAction("slash", "Slash", "Swing the sword")),
            CharacterHealthPoints(25, 40),
        ),
        JDRFileJsonCodec.deserializeCharacter(stored.content),
    )
  }

  // The new name is stored, so a workspace opened later shows it in both the file and the sheet.
  @Test
  fun renamedCharacterKeepsItsNameAfterReopeningTheWorkspace() = runTest {
    val character =
        groupFile(
            "character",
            ownerId = OWNER_ID,
            type = DocumentType.CHARACTER,
            name = "New character",
            content = JDRFileJsonCodec.serializeCharacter(null),
        )
    val repository = FakeWorkspaceRepository(listOf(character))
    val player = viewModel(repository, GroupWorkspaceRole.PLAYER)
    runCurrent()
    player.editItem(character.copy(name = "Brann"))
    runCurrent()

    val reopened = viewModel(repository, GroupWorkspaceRole.PLAYER)
    runCurrent()

    val reloaded = reopened.uiState.value.items.single()
    assertEquals("Brann", reloaded.name)
    assertEquals("Brann", JDRFileJsonCodec.deserializeCharacter(reloaded.content).name)
  }

  // A character whose sheet cannot be read is not renamed, so file and sheet never disagree.
  @Test
  fun renamingCharacterWithUnreadableSheetFailsWithoutWriting() = runTest {
    val character =
        groupFile(
            "character",
            ownerId = OWNER_ID,
            type = DocumentType.CHARACTER,
            name = "Aria",
            content = "not json",
        )
    val repository = FakeWorkspaceRepository(listOf(character))
    val player = viewModel(repository, GroupWorkspaceRole.PLAYER)
    runCurrent()

    player.editItem(character.copy(name = "Brann"))
    runCurrent()

    assertEquals(
        GroupWorkspaceViewModel.CHARACTER_SHEET_UNREADABLE,
        player.uiState.value.errorMessage,
    )
    assertEquals(character, repository.files.getValue(character.id))
  }

  // Folders and text documents are renamed as before: their content is left untouched.
  @Test
  fun renamingFolderOrTextDocumentKeepsItsContentAsIs() = runTest {
    val folder = groupFolder("folder", ownerId = OWNER_ID)
    val text = groupFile("text", ownerId = OWNER_ID, content = "Some notes")
    val repository = FakeWorkspaceRepository(listOf(folder, text))
    val player = viewModel(repository, GroupWorkspaceRole.PLAYER)
    runCurrent()

    player.editItem(folder.copy(name = "Maps"))
    runCurrent()
    player.editItem(text.copy(name = "Journal"))
    runCurrent()

    assertEquals("", player.uiState.value.errorMessage)
    assertEquals(folder.copy(name = "Maps"), repository.files.getValue(folder.id))
    assertEquals(text.copy(name = "Journal"), repository.files.getValue(text.id))
  }

  // The Group Workspace only works online: its saves wait for the server, so a refusal is shown.
  @Test
  fun createdItemWaitsForTheServer() = runTest {
    val repository = FakeWorkspaceRepository()
    val player = viewModel(repository, GroupWorkspaceRole.PLAYER)
    runCurrent()

    player.createFolder("Maps")
    runCurrent()

    assertEquals(listOf(true), repository.createWaits)
  }

  @Test
  fun editedItemWaitsForTheServer() = runTest {
    val folder = groupFolder("maps", ownerId = OWNER_ID).copy(name = "Maps")
    val repository = FakeWorkspaceRepository(listOf(folder))
    val gm = viewModel(repository, GroupWorkspaceRole.GAME_MASTER)
    runCurrent()

    gm.editItem(folder.copy(name = "World maps"))
    runCurrent()

    assertEquals(listOf(true), repository.updateWaits)
  }

  // A renamed folder shows its new name in the folder that holds it straight away.
  @Test
  fun renamedFolderAppearsWithItsNewNameInItsParent() = runTest {
    val folder = groupFolder("maps", ownerId = OWNER_ID).copy(name = "Maps")
    val repository = FakeWorkspaceRepository(listOf(folder))
    val gm = viewModel(repository, GroupWorkspaceRole.GAME_MASTER)
    runCurrent()

    gm.editItem(folder.copy(name = "  World maps  "))
    runCurrent()

    assertEquals("", gm.uiState.value.errorMessage)
    assertEquals(listOf("World maps"), gm.uiState.value.items.map { it.name })
  }

  // Renaming a folder changes only its name: placement, visibility, content and children stay.
  @Test
  fun renamingFolderKeepsItsPlacementAndContentsUnchanged() = runTest {
    val folder = groupFolder("maps", ownerId = OWNER_ID, hidden = true).copy(name = "Maps")
    val child = groupFile("atlas", parentId = folder.id)
    val nested = groupFolder("regions", parentId = folder.id)
    val nestedChild = groupFile("north", parentId = nested.id)
    val repository = FakeWorkspaceRepository(listOf(folder, child, nested, nestedChild))
    val gm = viewModel(repository, GroupWorkspaceRole.GAME_MASTER)
    runCurrent()

    gm.editItem(folder.copy(name = "World maps"))
    runCurrent()
    gm.openFolder(folder.id)
    runCurrent()

    assertEquals(folder.copy(name = "World maps"), repository.files.getValue(folder.id))
    assertEquals(child, repository.files.getValue(child.id))
    assertEquals(nested, repository.files.getValue(nested.id))
    assertEquals(nestedChild, repository.files.getValue(nestedChild.id))
    assertEquals(setOf(child.id, nested.id), gm.uiState.value.items.map { it.id }.toSet())
    assertEquals("World maps", gm.uiState.value.currentFolderName)
  }

  // The new name is stored, so a workspace opened later shows it on the card and in the header.
  @Test
  fun renamedFolderKeepsItsNameAfterReopeningTheWorkspace() = runTest {
    val folder = groupFolder("maps", ownerId = OWNER_ID).copy(name = "Maps")
    val repository = FakeWorkspaceRepository(listOf(folder))
    val player = viewModel(repository, GroupWorkspaceRole.PLAYER)
    runCurrent()
    player.editItem(folder.copy(name = "World maps"))
    runCurrent()

    val reopened = viewModel(repository, GroupWorkspaceRole.PLAYER)
    runCurrent()
    val cardName = reopened.uiState.value.items.single().name
    reopened.openFolder(folder.id)
    runCurrent()

    assertEquals("World maps", cardName)
    assertEquals("World maps", reopened.uiState.value.currentFolderName)
  }

  // The header of the open folder follows a rename made from another session.
  @Test
  fun openFolderHeaderFollowsARenameMadeElsewhere() = runTest {
    val folder = groupFolder("maps", ownerId = OWNER_ID).copy(name = "Maps")
    val repository = FakeWorkspaceRepository(listOf(folder))
    val gm = viewModel(repository, GroupWorkspaceRole.GAME_MASTER)
    runCurrent()
    gm.openFolder(folder.id)
    runCurrent()
    assertEquals("Maps", gm.uiState.value.currentFolderName)

    repository.updateFile(FileEdit(folder.id, "World maps", ""))
    runCurrent()

    assertEquals("World maps", gm.uiState.value.currentFolderName)
    assertEquals(folder.id, gm.uiState.value.currentFolderId)
  }

  // Only a folder's owner may rename it, whatever their role.
  @Test
  fun onlyTheOwnerCanRenameAFolder() = runTest {
    val gmFolder = groupFolder("gm-folder", ownerId = OWNER_ID)
    val playerFolder = groupFolder("player-folder", ownerId = OTHER_USER_ID)
    val repository = FakeWorkspaceRepository(listOf(gmFolder, playerFolder))
    val gm = viewModel(repository, GroupWorkspaceRole.GAME_MASTER)
    val player =
        viewModel(
            repository,
            GroupWorkspaceRole.PLAYER,
            authRepository = FakeAuthRepository(AuthUser(OTHER_USER_ID, null, null)),
            groupRepository =
                FakeGroupRepository(
                    CampaignGroup(
                        id = GROUP_ID,
                        gameMasterId = OWNER_ID,
                        participantIds = listOf(OTHER_USER_ID),
                    )
                ),
        )
    runCurrent()

    gm.editItem(playerFolder.copy(name = "Taken"))
    runCurrent()
    player.editItem(gmFolder.copy(name = "Taken"))
    runCurrent()

    assertEquals("You cannot edit an item that you do not own.", gm.uiState.value.errorMessage)
    assertEquals("You cannot edit an item that you do not own.", player.uiState.value.errorMessage)
    assertEquals(gmFolder, repository.files.getValue(gmFolder.id))
    assertEquals(playerFolder, repository.files.getValue(playerFolder.id))
  }

  private fun viewModel(
      repository: FakeWorkspaceRepository,
      role: GroupWorkspaceRole,
      authRepository: FakeAuthRepository = FakeAuthRepository(),
      groupRepository: FakeGroupRepository = FakeGroupRepository(groupForRole(role)),
  ) = GroupWorkspaceViewModel(GROUP_ID, groupRepository, repository, authRepository)

  private fun groupForRole(role: GroupWorkspaceRole) =
      CampaignGroup(
          id = GROUP_ID,
          gameMasterId = if (role == GroupWorkspaceRole.GAME_MASTER) OWNER_ID else OTHER_USER_ID,
          participantIds = listOf(OWNER_ID),
      )

  private class FakeGroupRepository(group: CampaignGroup?) :
      GroupRepository by unusedGroupRepository {
    val group = MutableStateFlow(group)
    private val failures = MutableSharedFlow<Exception>(extraBufferCapacity = 1)

    override fun getGroupFlow(groupId: String): Flow<CampaignGroup?> {
      require(groupId == GROUP_ID)
      return merge(group.asStateFlow(), failures.map { throw it })
    }

    fun fail(error: Exception) {
      check(failures.tryEmit(error))
    }
  }

  private data class AddRequest(
      val itemId: String,
      val groupId: String,
      val folderId: String,
      val isHidden: Boolean,
      val includeContent: Boolean = false,
  )

  private data class RemovalRequest(
      val itemId: String,
      val groupId: String,
  )

  private class FakeWorkspaceRepository(
      initialFiles: List<JDRFile> = emptyList(),
      private val folderGates: Map<String, CompletableDeferred<Unit>> = emptyMap(),
      private val folderFailures: Map<String, Exception> = emptyMap(),
      private val personalRootFailure: Exception? = null,
  ) : WorkspaceRepository {
    val files = initialFiles.associateByTo(linkedMapOf(), JDRFile::id)
    private val folderFlows = mutableMapOf<Pair<String, String>, MutableStateFlow<List<JDRFile>>>()
    private val fileFlows = mutableMapOf<String, MutableStateFlow<JDRFile?>>()
    private val personalFlows =
        mutableMapOf<Pair<String, String>, MutableStateFlow<List<JDRFile>>>()
    private var nextId = 0
    var createFailure: Exception? = null
    var updateFailure: Exception? = null
    /** The `waitForServer` argument of each createFile / updateFile call, in order. */
    val createWaits = mutableListOf<Boolean>()
    val updateWaits = mutableListOf<Boolean>()
    var hiddenFailure: Exception? = null
    var addFailure: Exception? = null
    var groupRootLoads = 0
    var personalRootLoads = 0
    val addRequests = mutableListOf<AddRequest>()
    val removeRequests = mutableListOf<RemovalRequest>()
    val hiddenRequests = mutableListOf<Pair<String, Boolean>>()
    val folderRequests = mutableListOf<String>()
    val folderStarts =
        folderGates.keys.associateWithTo(mutableMapOf()) { CompletableDeferred<Unit>() }
    val cancelledFolderRequests = mutableListOf<String>()

    override fun getNewUid(): String = "new-folder-${++nextId}"

    override suspend fun createFile(file: JDRFile, waitForServer: Boolean): Result<Unit> {
      createWaits += waitForServer
      createFailure?.let {
        return Result.failure(it)
      }
      if (file.id in files)
          return Result.failure(IllegalStateException("A document with this ID already exists."))
      files[file.id] = file
      refresh()
      return Result.success(Unit)
    }

    override suspend fun updateFile(edit: FileEdit, waitForServer: Boolean): Result<Unit> {
      updateWaits += waitForServer
      updateFailure?.let {
        return Result.failure(it)
      }
      val existing =
          files[edit.id] ?: return Result.failure(IllegalStateException("Document not found"))
      files[edit.id] = existing.copy(name = edit.name, content = edit.content)
      refresh()
      return Result.success(Unit)
    }

    override suspend fun deleteFile(fileId: String): Result<Unit> {
      files.remove(fileId)
      refresh()
      return Result.success(Unit)
    }

    override suspend fun getFile(fileId: String): Result<JDRFile?> {
      val file = files[fileId]

      return if (file != null) {
        Result.success(file)
      } else {
        Result.failure(Exception("Document not found in Firestore"))
      }
    }

    override suspend fun removeItemFromGroup(
        itemId: String,
        groupId: String,
    ): Result<Unit> = runCatching {
      removeRequests += RemovalRequest(itemId, groupId)
      val item = requireNotNull(files[itemId])
      files[itemId] = item.withoutPlacement(groupId)
      refresh()
    }

    override suspend fun addItemToGroup(
        itemId: String,
        groupId: String,
        folderId: String,
        isHidden: Boolean,
        includeContent: Boolean,
    ): Result<Unit> = runCatching {
      addRequests += AddRequest(itemId, groupId, folderId, isHidden, includeContent)
      addFailure?.let { throw it }
      val item = requireNotNull(files[itemId])
      val folder = requireNotNull(files[folderId])
      check(folder.type == DocumentType.GROUP_ROOT || folder.type == DocumentType.FOLDER)
      check(groupId in folder.sharedGroupsIds)
      files[itemId] = item.withPlacement(groupId, Placement(folderId, isHidden))
      refresh()
    }

    override suspend fun hasPendingWrites(): Boolean = false

    override suspend fun setItemHidden(
        itemId: String,
        groupId: String,
        isHidden: Boolean,
    ): Result<Unit> = runCatching {
      hiddenFailure?.let { throw it }
      hiddenRequests += itemId to isHidden
      val item = requireNotNull(files[itemId])
      val parentId = item.placements[groupId]?.folderId
      if (!isHidden && parentId != groupId) {
        check(files[parentId]?.placements?.get(groupId)?.isHidden != true) {
          "Reveal the parent folder first."
        }
      }
      files[itemId] =
          item.copy(
              placements =
                  item.placements +
                      (groupId to item.placements.getValue(groupId).copy(isHidden = isHidden))
          )
      refresh()
    }

    override fun getFileFlow(fileId: String): Flow<JDRFile?> =
        fileFlows.getOrPut(fileId) { MutableStateFlow(files[fileId]) }.asStateFlow()

    override fun getPersonalFolderFiles(folderId: String, ownerId: String): Flow<List<JDRFile>> {
      if (folderId == PERSONAL_ROOT_ID) {
        personalRootLoads++
        personalRootFailure?.let { failure ->
          return flow { throw failure }
        }
      }
      return personalFlows
          .getOrPut(folderId to ownerId) {
            MutableStateFlow(
                files.values.filter {
                  it.ownerId == ownerId &&
                      it.personalParentId == folderId &&
                      it.type != DocumentType.GROUP_ROOT
                }
            )
          }
          .asStateFlow()
    }

    override fun getGroupFolderFiles(folderId: String, groupId: String): Flow<List<JDRFile>> {
      if (folderId == groupId) groupRootLoads++
      folderRequests += folderId
      val source =
          folderFlows
              .getOrPut(folderId to groupId) { MutableStateFlow(children(folderId, groupId)) }
              .asStateFlow()
      return source
          .onStart {
            folderStarts.getOrPut(folderId) { CompletableDeferred() }.complete(Unit)
            folderGates[folderId]?.await()
            folderFailures[folderId]?.let { throw it }
          }
          .onCompletion { cause ->
            if (cause is CancellationException) cancelledFolderRequests += folderId
          }
    }

    fun emitFolderItems(folderId: String, items: List<JDRFile>) {
      folderFlows
          .getOrPut(folderId to GROUP_ID) { MutableStateFlow(children(folderId, GROUP_ID)) }
          .value = items
    }

    fun replaceFile(file: JDRFile) {
      files[file.id] = file
      refresh()
    }

    private fun children(folderId: String, groupId: String): List<JDRFile> =
        files.values
            .filter {
              groupId in it.sharedGroupsIds && it.placements[groupId]?.folderId == folderId
            }
            .sortedBy { it.name.lowercase() }

    private fun refresh() {
      folderFlows.forEach { (key, flow) -> flow.value = children(key.first, key.second) }
      fileFlows.forEach { (id, flow) -> flow.value = files[id] }
      personalFlows.forEach { (key, flow) ->
        flow.value =
            files.values.filter {
              it.ownerId == key.second &&
                  it.personalParentId == key.first &&
                  it.type != DocumentType.GROUP_ROOT
            }
      }
    }
  }

  private class FakeAuthRepository(
      user: AuthUser? = AuthUser(OWNER_ID, "owner@example.com", "Owner"),
  ) : AuthRepository {
    private val mutableAuthState = MutableStateFlow(user)
    override val authState: StateFlow<AuthUser?> = mutableAuthState.asStateFlow()

    fun setUser(user: AuthUser?) {
      mutableAuthState.value = user
    }

    override suspend fun signUpWithEmail(
        email: String,
        password: String,
        username: String,
    ): AuthResult<AuthUser> = AuthResult.Success(requireNotNull(authState.value))

    override suspend fun signInWithEmail(email: String, password: String): AuthResult<AuthUser> =
        AuthResult.Success(requireNotNull(authState.value))

    override suspend fun signInWithGoogle(idToken: String): AuthResult<AuthUser> =
        AuthResult.Success(requireNotNull(authState.value))

    override suspend fun signOut(): AuthResult<Unit> = AuthResult.Success(Unit)
  }

  private companion object {
    val unusedGroupRepository =
        object : GroupRepository {
          override fun getNewGroupId() = error("Not used")

          override suspend fun createGroup(group: CampaignGroup) = error("Not used")

          override suspend fun updateGroup(group: CampaignGroup) = error("Not used")

          override suspend fun deleteGroup(groupId: String) = error("Not used")

          override suspend fun getGroup(groupId: String) = error("Not used")

          override suspend fun addUserToGroup(groupId: String, userId: String) = error("Not used")

          override suspend fun removeUserFromGroup(groupId: String, userId: String) =
              error("Not used")

          override suspend fun createInvitation(
              groupId: String,
              gameMasterId: String,
          ): Result<String> = error("Not used")

          override suspend fun regenerateInvitation(
              groupId: String,
              gameMasterId: String,
          ): Result<String> = error("Not used")

          override suspend fun joinGroupWithCode(
              code: String,
              userId: String,
          ): Result<CampaignGroup> = error("Not used")

          override fun getGroupFlow(groupId: String) = error("Not used")

          override fun getUserGroups(userId: String) = error("Not used")
        }

    const val GROUP_ID = "group-7"
    const val OTHER_GROUP_ID = "group-8"
    const val OWNER_ID = "authenticated-user-42"
    const val OTHER_USER_ID = "another-user"
    const val CHARACTER_SHEET_JSON =
        """{"version":1,"type":"character","character":{"name":"Aria","stats":[{"statID":"strength","name":"Strength","value":16}],"actions":[{"actionID":"slash","name":"Slash","description":"Swing the sword"}],"hp":{"current":25,"max":40}}}"""
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
