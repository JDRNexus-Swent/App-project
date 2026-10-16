// Co-authored-by: OpenAI Codex
package com.github.se.jdrnexus.viewmodel

import androidx.lifecycle.ViewModelStore
import com.github.se.jdrnexus.model.personalSpace.CampaignGroup
import com.github.se.jdrnexus.model.personalSpace.DocumentType
import com.github.se.jdrnexus.model.personalSpace.GroupRepository
import com.github.se.jdrnexus.model.personalSpace.JDRFile
import com.github.se.jdrnexus.model.personalSpace.WorkspaceRepository
import com.github.se.jdrnexus.model.repository.AuthRepository
import com.github.se.jdrnexus.model.repository.AuthResult
import com.github.se.jdrnexus.model.repository.AuthUser
import com.github.se.jdrnexus.ui.authentication.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GameManagementViewModelTest {
  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  private fun createViewModel(
      groupRepository: GroupRepository,
      authRepository: AuthRepository,
      workspaceRepository: WorkspaceRepository = FakeWorkspaceRepository(),
  ) = GameManagementViewModel(groupRepository, authRepository, workspaceRepository)

  @Test
  fun loadsGroupsAndExposesEachUsersRole() = runTest {
    val user = AuthUser("user-1", "user@example.com", "User")
    val group = CampaignGroup(id = "group-1", name = "Campaign", gameMasterId = user.uid)
    val repository = FakeGroupRepository { flowOf(listOf(group)) }
    val viewModel = createViewModel(repository, FakeAuthRepository(user))

    assertEquals(GroupLoadState.Loading, viewModel.uiState.value.loadState)
    assertTrue(viewModel.uiState.value.groups.isEmpty())
    advanceUntilIdle()

    assertEquals(GroupLoadState.Success, viewModel.uiState.value.loadState)
    assertEquals(listOf(UserGroup(group, GroupRole.GAME_MASTER)), viewModel.uiState.value.groups)
  }

  @Test
  fun exposesEmptyWhenRepositoryReturnsNoGroups() = runTest {
    val viewModel =
        createViewModel(FakeGroupRepository { flowOf(emptyList()) }, FakeAuthRepository())

    advanceUntilIdle()

    assertEquals(GroupLoadState.Empty, viewModel.uiState.value.loadState)
    assertTrue(viewModel.uiState.value.groups.isEmpty())
  }

  @Test
  fun reflectsLiveGroupUpdatesAndPlayerRole() = runTest {
    val user = AuthUser("player-1", null, null)
    val groups = MutableStateFlow(listOf(CampaignGroup(id = "one", gameMasterId = "gm")))
    val viewModel = createViewModel(FakeGroupRepository { groups }, FakeAuthRepository(user))
    advanceUntilIdle()

    assertEquals(GroupRole.PLAYER, viewModel.uiState.value.groups.single().role)
    val updatedGroup = CampaignGroup(id = "two", name = "Updated", gameMasterId = user.uid)
    groups.value = listOf(updatedGroup)
    advanceUntilIdle()

    assertEquals(
        listOf(UserGroup(updatedGroup, GroupRole.GAME_MASTER)),
        viewModel.uiState.value.groups,
    )
  }

  @Test
  fun exposesFailureAndRetryStartsANewSubscription() = runTest {
    val requests = mutableListOf<String>()
    val repository = FakeGroupRepository { uid ->
      requests += uid
      if (requests.size == 1) flow { throw IllegalStateException("offline") }
      else flowOf(listOf(CampaignGroup(id = "recovered", gameMasterId = uid)))
    }
    val viewModel = createViewModel(repository, FakeAuthRepository())
    advanceUntilIdle()

    assertEquals(GroupLoadState.Error("offline"), viewModel.uiState.value.loadState)
    viewModel.loadGroups()
    advanceUntilIdle()

    assertEquals(listOf("user-1", "user-1"), requests)
    assertEquals("recovered", viewModel.uiState.value.groups.single().group.id)
    assertEquals(GroupLoadState.Success, viewModel.uiState.value.loadState)
  }

  @Test
  fun exposesFailureWhenAuthStateCannotBeObserved() = runTest {
    val authState = flow<AuthUser?> { throw IllegalStateException("auth offline") }
    val viewModel =
        createViewModel(
            FakeGroupRepository { flowOf(emptyList()) },
            FakeAuthRepository(authState = authState),
        )

    advanceUntilIdle()

    assertEquals(GroupLoadState.Error("auth offline"), viewModel.uiState.value.loadState)
    assertTrue(viewModel.uiState.value.groups.isEmpty())
  }

  @Test
  fun loadGroupsDoesNotDuplicateAnActiveSubscription() = runTest {
    var subscriptions = 0
    val repository = FakeGroupRepository {
      flow {
        subscriptions += 1
        emit(emptyList())
        awaitCancellation()
      }
    }
    val viewModel = createViewModel(repository, FakeAuthRepository())
    advanceUntilIdle()

    viewModel.loadGroups()
    advanceUntilIdle()

    assertEquals(1, subscriptions)
  }

  @Test
  fun loadGroupsWhileFirstEmissionIsPendingKeepsTheSameSubscription() = runTest {
    var subscriptions = 0
    val repository = FakeGroupRepository {
      flow {
        subscriptions += 1
        awaitCancellation()
      }
    }
    val viewModel = createViewModel(repository, FakeAuthRepository())
    runCurrent()

    viewModel.loadGroups()
    runCurrent()

    assertEquals(GroupLoadState.Loading, viewModel.uiState.value.loadState)
    assertEquals(1, subscriptions)
  }

  @Test
  fun accountSwitchCancelsOldSubscriptionAndClearsOldGroups() = runTest {
    val auth = FakeAuthRepository(AuthUser("first", null, null))
    val repository = TrackingGroupRepository()
    val viewModel = createViewModel(repository, auth)
    advanceUntilIdle()
    assertEquals("first", viewModel.uiState.value.groups.single().group.id)

    auth.currentUser.value = AuthUser("second", null, null)
    advanceUntilIdle()

    assertEquals(setOf("second"), repository.activeUsers)
    assertEquals("second", viewModel.uiState.value.groups.single().group.id)
  }

  @Test
  fun signOutClearsGroupsAndCancelsSubscription() = runTest {
    val auth = FakeAuthRepository(AuthUser("first", null, null))
    val repository = TrackingGroupRepository()
    val viewModel = createViewModel(repository, auth)
    advanceUntilIdle()

    auth.currentUser.value = null
    advanceUntilIdle()

    assertTrue(viewModel.uiState.value.groups.isEmpty())
    assertEquals(GroupLoadState.Empty, viewModel.uiState.value.loadState)
    assertTrue(repository.activeUsers.isEmpty())
  }

  @Test
  fun clearingViewModelCancelsGroupSubscription() = runTest {
    val repository = TrackingGroupRepository()
    val viewModel = createViewModel(repository, FakeAuthRepository())
    val store = ViewModelStore().also { it.put("game-management", viewModel) }
    advanceUntilIdle()
    assertEquals(setOf("user-1"), repository.activeUsers)

    store.clear()
    advanceUntilIdle()

    assertTrue(repository.activeUsers.isEmpty())
  }

  @Test
  fun createGroupTrimsNameAndAssignsTheSignedInUserAsGameMasterAndParticipant() = runTest {
    val user = AuthUser("gm-1", "gm@example.com", "GM")
    val repository = FakeGroupRepository { flowOf(emptyList()) }
    val workspaceRepository = FakeWorkspaceRepository(newUid = "created-group")
    val viewModel = createViewModel(repository, FakeAuthRepository(user), workspaceRepository)
    advanceUntilIdle()

    viewModel.createGroup("  The Adventure  ")
    assertEquals(GroupActionState.Loading, viewModel.uiState.value.createState)
    advanceUntilIdle()

    assertEquals(
        listOf(
            CampaignGroup(
                id = "created-group",
                name = "The Adventure",
                gameMasterId = user.uid,
                participantIds = listOf(user.uid),
            )
        ),
        repository.createdGroups,
    )
    assertEquals(
        listOf(
            JDRFile(
                id = "created-group",
                name = "The Adventure",
                ownerId = user.uid,
                sharedGroupsIds = listOf("created-group"),
                type = DocumentType.FOLDER,
            )
        ),
        workspaceRepository.createdFiles,
    )
    assertEquals(
        GroupActionState.Success("created-group", "The Adventure was created."),
        viewModel.uiState.value.createState,
    )
    assertFalse(viewModel.uiState.value.isCreateDialogOpen)
  }

  @Test
  fun createDialogClearsValidationErrorAndUsesTheEditedName() = runTest {
    val repository = FakeGroupRepository { flowOf(emptyList()) }
    val viewModel = createViewModel(repository, FakeAuthRepository())
    advanceUntilIdle()

    viewModel.openCreateDialog()
    viewModel.createGroup()
    assertEquals(
        GroupActionState.Error("A group name is required."),
        viewModel.uiState.value.createState,
    )

    viewModel.onGroupNameChanged("New campaign")
    assertEquals(GroupActionState.Idle, viewModel.uiState.value.createState)
    viewModel.createGroup()
    advanceUntilIdle()

    assertEquals("New campaign", repository.createdGroups.single().name)
    assertEquals(
        GroupActionState.Success("new-group", "New campaign was created."),
        viewModel.uiState.value.createState,
    )
    assertFalse(viewModel.uiState.value.isCreateDialogOpen)
    assertEquals("", viewModel.uiState.value.groupName)

    viewModel.dismissSuccessMessage()
    assertEquals(GroupActionState.Idle, viewModel.uiState.value.createState)
  }

  @Test
  fun createDialogCannotCloseWhileGroupWriteIsPending() = runTest {
    val createGate = CompletableDeferred<Unit>()
    val repository = FakeGroupRepository(createGate = createGate, groups = { flowOf(emptyList()) })
    val viewModel = createViewModel(repository, FakeAuthRepository())
    advanceUntilIdle()

    viewModel.openCreateDialog()
    viewModel.onGroupNameChanged("Campaign")
    viewModel.createGroup()
    runCurrent()
    viewModel.closeCreateDialog()

    assertTrue(viewModel.uiState.value.isCreateDialogOpen)
    assertEquals(GroupActionState.Loading, viewModel.uiState.value.createState)
    createGate.complete(Unit)
    advanceUntilIdle()
    assertFalse(viewModel.uiState.value.isCreateDialogOpen)
  }

  @Test
  fun createGroupRejectsBlankNameWithoutCallingRepository() = runTest {
    val repository = FakeGroupRepository { flowOf(emptyList()) }
    val workspaceRepository = FakeWorkspaceRepository()
    val viewModel = createViewModel(repository, FakeAuthRepository(), workspaceRepository)
    advanceUntilIdle()

    viewModel.createGroup("   ")
    advanceUntilIdle()

    assertEquals(
        GroupActionState.Error("A group name is required."),
        viewModel.uiState.value.createState,
    )
    assertTrue(repository.createdGroups.isEmpty())
    assertTrue(workspaceRepository.createdFiles.isEmpty())
    assertEquals(0, workspaceRepository.newUidRequests)
  }

  @Test
  fun createGroupRequiresSignedInUser() = runTest {
    val repository = FakeGroupRepository { flowOf(emptyList()) }
    val workspaceRepository = FakeWorkspaceRepository()
    val viewModel =
        createViewModel(repository, FakeAuthRepository(user = null), workspaceRepository)
    advanceUntilIdle()

    viewModel.createGroup("Campaign")

    assertEquals(
        GroupActionState.Error("You must be signed in to create a group."),
        viewModel.uiState.value.createState,
    )
    assertTrue(repository.createdGroups.isEmpty())
    assertTrue(workspaceRepository.createdFiles.isEmpty())
    assertEquals(0, workspaceRepository.newUidRequests)
  }

  @Test
  fun createGroupReportsRepositoryFailure() = runTest {
    val repository =
        FakeGroupRepository(
            createResult = Result.failure(IllegalStateException("Permission denied")),
            groups = { flowOf(emptyList()) },
        )
    val workspaceRepository = FakeWorkspaceRepository()
    val viewModel = createViewModel(repository, FakeAuthRepository(), workspaceRepository)
    advanceUntilIdle()

    viewModel.createGroup("Campaign")
    advanceUntilIdle()

    assertEquals(
        GroupActionState.Error("Permission denied"),
        viewModel.uiState.value.createState,
    )
    assertEquals(1, repository.createdGroups.size)
    assertEquals(listOf("new-group"), workspaceRepository.deletedFiles)
  }

  @Test
  fun failedRootFolderWritePreventsGroupCreationAndReportsFailure() = runTest {
    val repository = FakeGroupRepository { flowOf(emptyList()) }
    val workspaceRepository =
        FakeWorkspaceRepository(
            createResult = Result.failure(IllegalStateException("Disk offline"))
        )
    val viewModel = createViewModel(repository, FakeAuthRepository(), workspaceRepository)
    advanceUntilIdle()

    viewModel.createGroup("Campaign")
    advanceUntilIdle()

    assertEquals(GroupActionState.Error("Disk offline"), viewModel.uiState.value.createState)
    assertTrue(repository.createdGroups.isEmpty())
    assertTrue(workspaceRepository.deletedFiles.isEmpty())
  }

  @Test
  fun signingOutDuringGroupCreationCancelsTheWriteAndRemovesItsRootFolder() = runTest {
    val createGate = CompletableDeferred<Unit>()
    val auth = FakeAuthRepository()
    val repository = FakeGroupRepository(createGate = createGate, groups = { flowOf(emptyList()) })
    val workspaceRepository = FakeWorkspaceRepository()
    val viewModel = createViewModel(repository, auth, workspaceRepository)
    advanceUntilIdle()

    viewModel.createGroup("Campaign")
    runCurrent()
    assertEquals(GroupActionState.Loading, viewModel.uiState.value.createState)

    auth.currentUser.value = null
    advanceUntilIdle()

    assertEquals(GroupLoadState.Empty, viewModel.uiState.value.loadState)
    assertEquals(GroupActionState.Idle, viewModel.uiState.value.createState)
    assertEquals(listOf("new-group"), workspaceRepository.deletedFiles)
  }

  @Test
  fun duplicateCreateCallWhileLoadingIsIgnored() = runTest {
    val createGate = CompletableDeferred<Unit>()
    val repository = FakeGroupRepository(createGate = createGate, groups = { flowOf(emptyList()) })
    val viewModel = createViewModel(repository, FakeAuthRepository())
    advanceUntilIdle()

    viewModel.createGroup("Campaign")
    runCurrent()
    assertEquals(GroupActionState.Loading, viewModel.uiState.value.createState)

    viewModel.createGroup("Another campaign")
    assertEquals(1, repository.createdGroups.size)
    assertEquals("Campaign", repository.createdGroups.single().name)

    createGate.complete(Unit)
    advanceUntilIdle()
    assertEquals(
        GroupActionState.Success("new-group", "Campaign was created."),
        viewModel.uiState.value.createState,
    )
  }

  @Test
  fun gameMasterInvitationRequestReturnsComingSoonMessage() = runTest {
    val user = AuthUser("gm-1", null, null)
    val group = CampaignGroup(id = "group-1", name = "Campaign", gameMasterId = user.uid)
    val viewModel =
        createViewModel(
            FakeGroupRepository { flowOf(listOf(group)) },
            FakeAuthRepository(user),
        )
    advanceUntilIdle()

    viewModel.requestInvitationPlaceholder(group.id)

    assertEquals(
        GroupActionState.Success(group.id, "Invitation management is coming soon."),
        viewModel.uiState.value.invitationState,
    )
  }

  @Test
  fun playerCannotRequestInvitationManagement() = runTest {
    val user = AuthUser("player-1", null, null)
    val group = CampaignGroup(id = "group-1", name = "Campaign", gameMasterId = "gm-1")
    val viewModel =
        createViewModel(
            FakeGroupRepository { flowOf(listOf(group)) },
            FakeAuthRepository(user),
        )
    advanceUntilIdle()

    viewModel.requestInvitationPlaceholder(group.id)

    assertEquals(
        GroupActionState.Error("Only the group's Game Master can manage invitations."),
        viewModel.uiState.value.invitationState,
    )
  }

  @Test
  fun cannotRequestInvitationManagementForGroupNotInUsersList() = runTest {
    val user = AuthUser("gm-1", null, null)
    val viewModel =
        createViewModel(
            FakeGroupRepository { flowOf(emptyList()) },
            FakeAuthRepository(user),
        )
    advanceUntilIdle()

    viewModel.requestInvitationPlaceholder("unknown-group")

    assertEquals(
        GroupActionState.Error("Only the group's Game Master can manage invitations."),
        viewModel.uiState.value.invitationState,
    )
  }

  @Test
  fun signedOutUserCannotRequestInvitationManagement() = runTest {
    val viewModel =
        createViewModel(
            FakeGroupRepository { flowOf(emptyList()) },
            FakeAuthRepository(user = null),
        )
    advanceUntilIdle()

    viewModel.requestInvitationPlaceholder("group-1")

    assertEquals(
        GroupActionState.Error("You must be signed in to manage invitations."),
        viewModel.uiState.value.invitationState,
    )
  }

  @Test
  fun blankInvitationSelectionShowsAnActionableError() = runTest {
    val viewModel =
        createViewModel(FakeGroupRepository { flowOf(emptyList()) }, FakeAuthRepository())
    advanceUntilIdle()

    viewModel.requestInvitationPlaceholder(" ")

    assertEquals(
        GroupActionState.Error("Select a group to manage invitations."),
        viewModel.uiState.value.invitationState,
    )
  }

  private class FakeAuthRepository(
      user: AuthUser? = AuthUser("user-1", null, null),
      authState: Flow<AuthUser?>? = null,
  ) : AuthRepository {
    val currentUser = MutableStateFlow(user)
    override val authState: Flow<AuthUser?> = authState ?: currentUser

    override suspend fun signUpWithEmail(email: String, password: String, username: String) =
        AuthResult.Failure(com.github.se.jdrnexus.model.repository.AuthError.UNKNOWN)

    override suspend fun signInWithEmail(email: String, password: String) =
        AuthResult.Failure(com.github.se.jdrnexus.model.repository.AuthError.UNKNOWN)

    override suspend fun signInWithGoogle(idToken: String) =
        AuthResult.Failure(com.github.se.jdrnexus.model.repository.AuthError.UNKNOWN)

    override suspend fun signOut() = AuthResult.Success(Unit)
  }

  private class FakeGroupRepository(
      private val createResult: Result<Unit> = Result.success(Unit),
      private val createGate: CompletableDeferred<Unit>? = null,
      private val groups: (String) -> Flow<List<CampaignGroup>>,
  ) : GroupRepository by UnusedGroupRepository {
    val createdGroups = mutableListOf<CampaignGroup>()

    override suspend fun createGroup(group: CampaignGroup): Result<Unit> {
      createdGroups += group
      createGate?.await()
      return createResult
    }

    override fun getUserGroups(userId: String): Flow<List<CampaignGroup>> = groups(userId)
  }

  private class FakeWorkspaceRepository(
      private val newUid: String = "new-group",
      private val createResult: Result<Unit> = Result.success(Unit),
  ) : WorkspaceRepository {
    val createdFiles = mutableListOf<JDRFile>()
    val deletedFiles = mutableListOf<String>()
    var newUidRequests = 0

    override fun getNewUid(): String {
      newUidRequests += 1
      return newUid
    }

    override suspend fun createFile(file: JDRFile): Result<Unit> {
      createdFiles += file
      return createResult
    }

    override suspend fun updateFile(file: JDRFile) = error("Not used")

    override suspend fun deleteFile(fileId: String): Result<Unit> {
      deletedFiles += fileId
      return Result.success(Unit)
    }

    override suspend fun getFile(fileId: String): Result<JDRFile?> = error("Not used")

    override suspend fun removeItemFromGroup(
        itemId: String,
        groupId: String,
        parentFolderId: String?,
    ): Result<Unit> {
      error("not used")
    }

    override fun getFileFlow(fileId: String): Flow<JDRFile?> = error("Not used")

    override fun getPersonalRootFiles(ownerId: String): Flow<List<JDRFile>> = error("Not used")

    override fun getGroupRootFiles(groupId: String): Flow<List<JDRFile>> = error("Not used")

    override fun getDocumentsInFolder(folderId: String): Flow<List<JDRFile>> = error("Not used")
  }

  private class TrackingGroupRepository : GroupRepository by UnusedGroupRepository {
    val activeUsers = mutableSetOf<String>()

    override fun getUserGroups(userId: String): Flow<List<CampaignGroup>> = flow {
      activeUsers += userId
      emit(listOf(CampaignGroup(id = userId, gameMasterId = userId)))
      awaitCancellation()
    }
        .onCompletion { activeUsers -= userId }
  }

  private companion object {
    val UnusedGroupRepository =
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
  }
}
