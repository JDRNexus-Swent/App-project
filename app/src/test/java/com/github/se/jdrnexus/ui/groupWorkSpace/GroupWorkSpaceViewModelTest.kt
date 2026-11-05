// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.github.se.jdrnexus.ui.groupWorkSpace

import com.github.se.jdrnexus.model.personalSpace.CampaignGroup
import com.github.se.jdrnexus.model.personalSpace.DocumentType
import com.github.se.jdrnexus.model.personalSpace.GroupRepository
import com.github.se.jdrnexus.model.personalSpace.JDRFile
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    assertTrue(gm.uiState.value.canCreateFolder)
    assertTrue(gm.uiState.value.canEditItems)
    assertFalse(player.uiState.value.canCreateFolder)
    assertFalse(player.uiState.value.canEditItems)
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
    assertFalse(viewModel.uiState.value.canCreateFolder)
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
    assertTrue(viewModel.uiState.value.canCreateFolder)
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
    assertFalse(viewModel.uiState.value.canCreateFolder)
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
    assertFalse(viewModel.uiState.value.canCreateFolder)
    assertFalse(viewModel.uiState.value.canEditItems)

    groupRepository.group.value = groupForRole(GroupWorkspaceRole.PLAYER)
    runCurrent()

    assertEquals(LoadState.Success, viewModel.uiState.value.loadState)
    assertEquals(listOf(file), viewModel.uiState.value.items)
    assertFalse(viewModel.uiState.value.canCreateFolder)
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
    assertFalse(viewModel.uiState.value.canCreateFolder)
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
    assertTrue(viewModel.uiState.value.canCreateFolder)
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
    assertFalse(viewModel.uiState.value.canCreateFolder)
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
    assertFalse(viewModel.uiState.value.canCreateFolder)
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

  // A suspended older request is cancelled, and its later folder emissions cannot replace current
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

    // The old collection has been cancelled, but its repository flow remains independently
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

  // Only a Game Master can create a folder, and the new folder uses the selected parent placement.
  @Test
  fun gameMasterCreatesFolderInSelectedWorkspaceAndPlayersCannotCreateOne() = runTest {
    val parent = groupFolder("parent")
    val repository = FakeWorkspaceRepository(listOf(parent))
    val gm = viewModel(repository, GroupWorkspaceRole.GAME_MASTER)
    val player = viewModel(repository, GroupWorkspaceRole.PLAYER)
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

    player.createFolder("Forbidden")
    runCurrent()
    assertFalse(repository.files.values.any { it.name == "Forbidden" })
    assertEquals(
        "Only the GameMaster can perform this operation.",
        player.uiState.value.errorMessage,
    )
    assertEquals(setOf(parent, created), repository.files.values.toSet())
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

    player.addFile(file.id)
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

    gm.addFile(owned.id)
    runCurrent()
    assertEquals(
        Placement(GROUP_ID, false),
        repository.files.getValue(owned.id).placements[GROUP_ID],
    )
    assertEquals("", gm.uiState.value.errorMessage)

    gm.addFile(foreign.id)
    runCurrent()
    assertEquals("You can only add files that you own.", gm.uiState.value.errorMessage)
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

    player.addFile(foreign.id)
    runCurrent()
    assertEquals("You can only add files that you own.", player.uiState.value.errorMessage)
    assertTrue(GROUP_ID !in repository.files.getValue(foreign.id).sharedGroupsIds)
    assertTrue(repository.addRequests.isEmpty())

    player.clearError()
    player.addFile(alreadyShared.id)
    runCurrent()
    assertEquals("This file is already in the group.", player.uiState.value.errorMessage)
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
    assertEquals("You cannot edit an item that you do not own.", player.uiState.value.errorMessage)
    assertFalse(repository.files.getValue(item.id).placements.getValue(GROUP_ID).isHidden)

    player.clearError()
    player.setItemHidden(item, false)
    runCurrent()
    assertEquals("You cannot edit an item that you do not own.", player.uiState.value.errorMessage)
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
    assertEquals("You cannot edit an item that you do not own.", player.uiState.value.errorMessage)
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

  // Game Masters may edit another member's item, while players may not.
  @Test
  fun gameMasterCanEditForeignItemsButPlayersCannotEditForeignItems() = runTest {
    val folder = groupFolder("folder")
    val file = groupFile("file", parentId = folder.id)
    val repository = FakeWorkspaceRepository(listOf(folder, file))
    val gm = viewModel(repository, GroupWorkspaceRole.GAME_MASTER)
    runCurrent()
    gm.loadWorkspace(folder.id)
    runCurrent()
    gm.editItem(file.copy(name = "Updated"))
    runCurrent()
    assertEquals("Updated", repository.files.getValue(file.id).name)

    val player = viewModel(repository, GroupWorkspaceRole.PLAYER)
    runCurrent()
    player.loadWorkspace(folder.id)
    runCurrent()
    player.editItem(file.copy(name = "Not allowed"))
    runCurrent()
    assertEquals("You cannot edit an item that you do not own.", player.uiState.value.errorMessage)
    assertEquals("Updated", repository.files.getValue(file.id).name)
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
        listOf(RemovalRequest(folder.id, GROUP_ID, GROUP_ID)),
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
    val file = groupFile("file", parentId = folder.id)
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
      val parentFolderId: String,
  )

  private class FakeWorkspaceRepository(
      initialFiles: List<JDRFile> = emptyList(),
      private val folderGates: Map<String, CompletableDeferred<Unit>> = emptyMap(),
      private val folderFailures: Map<String, Exception> = emptyMap(),
  ) : WorkspaceRepository {
    val files = initialFiles.associateByTo(linkedMapOf(), JDRFile::id)
    private val folderFlows = mutableMapOf<Pair<String, String>, MutableStateFlow<List<JDRFile>>>()
    private val fileFlows = mutableMapOf<String, MutableStateFlow<JDRFile?>>()
    private val personalFlows =
        mutableMapOf<Pair<String, String>, MutableStateFlow<List<JDRFile>>>()
    private var nextId = 0
    var updateFailure: Exception? = null
    val addRequests = mutableListOf<AddRequest>()
    val removeRequests = mutableListOf<RemovalRequest>()
    val hiddenRequests = mutableListOf<Pair<String, Boolean>>()
    val folderRequests = mutableListOf<String>()
    val folderStarts =
        folderGates.keys.associateWithTo(mutableMapOf()) { CompletableDeferred<Unit>() }
    val cancelledFolderRequests = mutableListOf<String>()

    override fun getNewUid(): String = "new-folder-${++nextId}"

    override suspend fun createFile(file: JDRFile): Result<Unit> {
      if (file.id in files)
          return Result.failure(IllegalStateException("A document with this ID already exists."))
      files[file.id] = file
      refresh()
      return Result.success(Unit)
    }

    override suspend fun updateFile(file: JDRFile): Result<Unit> {
      updateFailure?.let {
        return Result.failure(it)
      }
      val existing =
          files[file.id] ?: return Result.failure(IllegalStateException("Document not found"))
      files[file.id] =
          file.copy(sharedGroupsIds = existing.sharedGroupsIds, placements = existing.placements)
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
        parentFolderId: String,
    ): Result<Unit> = runCatching {
      removeRequests += RemovalRequest(itemId, groupId, parentFolderId)
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
      val item = requireNotNull(files[itemId])
      val folder = requireNotNull(files[folderId])
      check(folder.type == DocumentType.GROUP_ROOT || folder.type == DocumentType.FOLDER)
      check(groupId in folder.sharedGroupsIds)
      files[itemId] = item.withPlacement(groupId, Placement(folderId, isHidden))
      refresh()
    }

    override suspend fun setItemHidden(
        itemId: String,
        groupId: String,
        isHidden: Boolean,
    ): Result<Unit> = runCatching {
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

    override fun getPersonalRootFiles(ownerId: String): Flow<List<JDRFile>> =
        getPersonalFolderFiles("", ownerId)

    override fun getPersonalFolderFiles(folderId: String, ownerId: String): Flow<List<JDRFile>> =
        personalFlows
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

    override fun getGroupFolderFiles(folderId: String, groupId: String): Flow<List<JDRFile>> {
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
    override val authState: StateFlow<AuthUser?> = MutableStateFlow(user).asStateFlow()

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

          override fun getGroupFlow(groupId: String) = error("Not used")

          override fun getUserGroups(userId: String) = error("Not used")
        }

    const val GROUP_ID = "group-7"
    const val OTHER_GROUP_ID = "group-8"
    const val OWNER_ID = "authenticated-user-42"
    const val OTHER_USER_ID = "another-user"
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
