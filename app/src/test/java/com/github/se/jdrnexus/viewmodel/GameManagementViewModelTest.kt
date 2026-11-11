// Co-authored-by: OpenAI Codex
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.viewmodel

import androidx.lifecycle.ViewModelStore
import com.github.se.jdrnexus.model.personalSpace.CampaignGroup
import com.github.se.jdrnexus.model.personalSpace.GroupRepository
import com.github.se.jdrnexus.model.personalSpace.InvitationError
import com.github.se.jdrnexus.model.personalSpace.InvitationException
import com.github.se.jdrnexus.model.repository.AuthRepository
import com.github.se.jdrnexus.model.repository.AuthResult
import com.github.se.jdrnexus.model.repository.AuthUser
import com.github.se.jdrnexus.utils.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.update
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
  ) = GameManagementViewModel(groupRepository, authRepository)

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
    assertEquals(GroupLoadState.SignedOut, viewModel.uiState.value.loadState)
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
    val repository =
        FakeGroupRepository(newGroupId = "created-group", groups = { flowOf(emptyList()) })
    val viewModel = createViewModel(repository, FakeAuthRepository(user))
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
    val viewModel = createViewModel(repository, FakeAuthRepository())
    advanceUntilIdle()

    viewModel.createGroup("   ")
    advanceUntilIdle()

    assertEquals(
        GroupActionState.Error("A group name is required."),
        viewModel.uiState.value.createState,
    )
    assertTrue(repository.createdGroups.isEmpty())
    assertEquals(0, repository.newGroupIdRequests)
  }

  @Test
  fun createGroupRequiresSignedInUser() = runTest {
    val repository = FakeGroupRepository { flowOf(emptyList()) }
    val viewModel = createViewModel(repository, FakeAuthRepository(user = null))
    advanceUntilIdle()

    viewModel.createGroup("Campaign")

    assertEquals(
        GroupActionState.Error("You must be signed in to create a group."),
        viewModel.uiState.value.createState,
    )
    assertTrue(repository.createdGroups.isEmpty())
  }

  @Test
  fun createGroupReportsRepositoryFailure() = runTest {
    val repository =
        FakeGroupRepository(
            createResult = Result.failure(IllegalStateException("Permission denied")),
            groups = { flowOf(emptyList()) },
        )
    val viewModel = createViewModel(repository, FakeAuthRepository())
    advanceUntilIdle()

    viewModel.createGroup("Campaign")
    advanceUntilIdle()

    assertEquals(
        GroupActionState.Error("Permission denied"),
        viewModel.uiState.value.createState,
    )
    assertEquals(1, repository.createdGroups.size)
  }

  @Test
  fun signingOutDuringGroupCreationCancelsTheWrite() = runTest {
    val createGate = CompletableDeferred<Unit>()
    val auth = FakeAuthRepository()
    val repository = FakeGroupRepository(createGate = createGate, groups = { flowOf(emptyList()) })
    val viewModel = createViewModel(repository, auth)
    advanceUntilIdle()

    viewModel.createGroup("Campaign")
    runCurrent()
    assertEquals(GroupActionState.Loading, viewModel.uiState.value.createState)

    auth.currentUser.value = null
    advanceUntilIdle()

    assertEquals(GroupLoadState.SignedOut, viewModel.uiState.value.loadState)
    assertEquals(GroupActionState.Idle, viewModel.uiState.value.createState)
    // The write is stuck on the gate. If it had not been cancelled, releasing the gate would let
    // it finish and report a success.
    createGate.complete(Unit)
    advanceUntilIdle()

    assertEquals(GroupActionState.Idle, viewModel.uiState.value.createState)
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

  // ==========================================
  // INVITE CODES (Game Master)
  // ==========================================

  @Test
  fun gameMasterLoadsTheGroupsInviteCode() = runTest {
    val gate = CompletableDeferred<Unit>()
    val repository = InvitationGroupRepository(listOf(GM_GROUP)).apply { inviteGate = gate }
    val viewModel = createViewModel(repository, FakeAuthRepository(GM))
    runCurrent()
    assertEquals(InvitationState.Idle, viewModel.uiState.value.invitationState)

    viewModel.loadInvitation(GM_GROUP.id)
    runCurrent()

    assertEquals(InvitationState.Loading, viewModel.uiState.value.invitationState)
    assertEquals(listOf(GM_GROUP.id to GM.uid), repository.createCalls)
    gate.complete(Unit)
    runCurrent()

    assertEquals(InvitationState.Idle, viewModel.uiState.value.invitationState)
    assertEquals(FIRST_CODE, viewModel.uiState.value.inviteCode)
  }

  @Test
  fun regeneratingReplacesTheCode() = runTest {
    val repository = InvitationGroupRepository(listOf(GM_GROUP))
    val viewModel = createViewModel(repository, FakeAuthRepository(GM))
    runCurrent()
    viewModel.loadInvitation(GM_GROUP.id)
    runCurrent()

    viewModel.regenerateInvitation(GM_GROUP.id)
    runCurrent()

    assertEquals(SECOND_CODE, viewModel.uiState.value.inviteCode)
    assertEquals(InvitationState.Idle, viewModel.uiState.value.invitationState)
    assertEquals(listOf(GM_GROUP.id to GM.uid), repository.regenerateCalls)
    assertEquals(listOf(GM_GROUP.id to GM.uid), repository.createCalls)
  }

  @Test
  fun playerCannotLoadOrRegenerateTheCode() = runTest {
    val repository = InvitationGroupRepository(listOf(PLAYER_GROUP))
    val viewModel = createViewModel(repository, FakeAuthRepository(PLAYER))
    runCurrent()

    viewModel.loadInvitation(PLAYER_GROUP.id)
    viewModel.regenerateInvitation(PLAYER_GROUP.id)
    runCurrent()

    assertEquals(
        InvitationState.Error(InvitationError.NOT_GAME_MASTER),
        viewModel.uiState.value.invitationState,
    )
    assertEquals(emptyList<Pair<String, String>>(), repository.createCalls)
    assertEquals(emptyList<Pair<String, String>>(), repository.regenerateCalls)
  }

  @Test
  fun groupMissingFromTheUsersListGivesNotGameMasterWithoutARequest() = runTest {
    val repository = InvitationGroupRepository(listOf(GM_GROUP))
    val viewModel = createViewModel(repository, FakeAuthRepository(GM))
    runCurrent()

    viewModel.loadInvitation("unknown-group")
    runCurrent()

    assertEquals(
        InvitationState.Error(InvitationError.NOT_GAME_MASTER),
        viewModel.uiState.value.invitationState,
    )
    assertEquals(emptyList<Pair<String, String>>(), repository.createCalls)
  }

  @Test
  fun signedOutUserCannotManageCodes() = runTest {
    val repository = InvitationGroupRepository(listOf(GM_GROUP))
    val viewModel = createViewModel(repository, FakeAuthRepository(user = null))
    runCurrent()

    viewModel.loadInvitation(GM_GROUP.id)
    runCurrent()

    assertEquals(
        InvitationState.Error(InvitationError.NOT_SIGNED_IN),
        viewModel.uiState.value.invitationState,
    )
    assertEquals(emptyList<Pair<String, String>>(), repository.createCalls)
  }

  @Test
  fun loadFailureExposesTheRepositoryError() = runTest {
    val repository =
        InvitationGroupRepository(listOf(GM_GROUP)).apply {
          inviteFailure = InvitationException(InvitationError.GROUP_NOT_FOUND)
        }
    val viewModel = createViewModel(repository, FakeAuthRepository(GM))
    runCurrent()

    viewModel.loadInvitation(GM_GROUP.id)
    runCurrent()

    assertEquals(
        InvitationState.Error(InvitationError.GROUP_NOT_FOUND),
        viewModel.uiState.value.invitationState,
    )
    assertEquals(null, viewModel.uiState.value.inviteCode)
  }

  @Test
  fun failedRegenerationKeepsTheStillValidCode() = runTest {
    val repository = InvitationGroupRepository(listOf(GM_GROUP))
    val viewModel = createViewModel(repository, FakeAuthRepository(GM))
    runCurrent()
    viewModel.loadInvitation(GM_GROUP.id)
    runCurrent()
    repository.inviteFailure = InvitationException(InvitationError.NETWORK)

    viewModel.regenerateInvitation(GM_GROUP.id)
    runCurrent()

    assertEquals(
        InvitationState.Error(InvitationError.NETWORK),
        viewModel.uiState.value.invitationState,
    )
    assertEquals(FIRST_CODE, viewModel.uiState.value.inviteCode)
  }

  @Test
  fun codeCanBeLoadedAgainAfterAnError() = runTest {
    val repository =
        InvitationGroupRepository(listOf(GM_GROUP)).apply {
          inviteFailure = InvitationException(InvitationError.NETWORK)
        }
    val viewModel = createViewModel(repository, FakeAuthRepository(GM))
    runCurrent()
    viewModel.loadInvitation(GM_GROUP.id)
    runCurrent()
    assertEquals(
        InvitationState.Error(InvitationError.NETWORK),
        viewModel.uiState.value.invitationState,
    )
    repository.inviteFailure = null

    viewModel.loadInvitation(GM_GROUP.id)
    runCurrent()

    assertEquals(InvitationState.Idle, viewModel.uiState.value.invitationState)
    assertEquals(FIRST_CODE, viewModel.uiState.value.inviteCode)
    assertEquals(2, repository.createCalls.size)
  }

  @Test
  fun codeRequestsWhileACodeIsLoadingAreIgnored() = runTest {
    val gate = CompletableDeferred<Unit>()
    val repository = InvitationGroupRepository(listOf(GM_GROUP)).apply { inviteGate = gate }
    val viewModel = createViewModel(repository, FakeAuthRepository(GM))
    runCurrent()

    viewModel.loadInvitation(GM_GROUP.id)
    viewModel.loadInvitation(GM_GROUP.id)
    viewModel.regenerateInvitation(GM_GROUP.id)
    runCurrent()

    assertEquals(listOf(GM_GROUP.id to GM.uid), repository.createCalls)
    assertEquals(emptyList<Pair<String, String>>(), repository.regenerateCalls)
    gate.complete(Unit)
    runCurrent()

    assertEquals(FIRST_CODE, viewModel.uiState.value.inviteCode)
  }

  @Test
  fun loadingAnotherGroupClearsThePreviousCode() = runTest {
    val repository = InvitationGroupRepository(listOf(GM_GROUP, SECOND_GM_GROUP))
    val viewModel = createViewModel(repository, FakeAuthRepository(GM))
    runCurrent()
    viewModel.loadInvitation(GM_GROUP.id)
    runCurrent()
    repository.inviteGate = CompletableDeferred()

    viewModel.loadInvitation(SECOND_GM_GROUP.id)
    runCurrent()

    assertEquals(InvitationState.Loading, viewModel.uiState.value.invitationState)
    assertEquals(null, viewModel.uiState.value.inviteCode)
  }

  @Test
  fun signingOutWhileACodeLoadsDropsTheResult() = runTest {
    val gate = CompletableDeferred<Unit>()
    val auth = FakeAuthRepository(GM)
    val repository = InvitationGroupRepository(listOf(GM_GROUP)).apply { inviteGate = gate }
    val viewModel = createViewModel(repository, auth)
    runCurrent()
    viewModel.loadInvitation(GM_GROUP.id)
    runCurrent()

    auth.currentUser.value = null
    runCurrent()
    // The request is stuck on the gate. If it had not been cancelled, releasing the gate would
    // expose the code to the signed-out screen.
    gate.complete(Unit)
    runCurrent()

    assertEquals(GroupLoadState.SignedOut, viewModel.uiState.value.loadState)
    assertEquals(InvitationState.Idle, viewModel.uiState.value.invitationState)
    assertEquals(null, viewModel.uiState.value.inviteCode)
  }

  // ==========================================
  // JOINING WITH A CODE
  // ==========================================

  @Test
  fun joinNormalizesTheCodeAndEndsJoined() = runTest {
    val gate = CompletableDeferred<Unit>()
    val repository =
        InvitationGroupRepository(listOf(JOINABLE_GROUP)).apply {
          addInvitation(FIRST_CODE, JOINABLE_GROUP.id)
          joinGate = gate
        }
    val viewModel = createViewModel(repository, FakeAuthRepository(PLAYER))
    runCurrent()
    assertEquals(JoinState.Idle, viewModel.uiState.value.joinState)

    viewModel.joinGroup("  ${FIRST_CODE.lowercase()} ")
    runCurrent()

    assertEquals(JoinState.Joining, viewModel.uiState.value.joinState)
    assertEquals(listOf(FIRST_CODE to PLAYER.uid), repository.joinCalls)
    gate.complete(Unit)
    runCurrent()

    assertEquals(JoinState.Joined(JOINABLE_GROUP.id), viewModel.uiState.value.joinState)
  }

  @Test
  fun joinedGroupAppearsInTheGroupList() = runTest {
    val repository =
        InvitationGroupRepository(listOf(JOINABLE_GROUP)).apply {
          addInvitation(FIRST_CODE, JOINABLE_GROUP.id)
        }
    val viewModel = createViewModel(repository, FakeAuthRepository(PLAYER))
    runCurrent()
    assertEquals(GroupLoadState.Empty, viewModel.uiState.value.loadState)

    viewModel.joinGroup(FIRST_CODE)
    runCurrent()

    val joinedGroup =
        JOINABLE_GROUP.copy(participantIds = JOINABLE_GROUP.participantIds + PLAYER.uid)
    assertEquals(listOf(UserGroup(joinedGroup, GroupRole.PLAYER)), viewModel.uiState.value.groups)
  }

  @Test
  fun malformedCodesFailWithoutARequest() = runTest {
    val repository = InvitationGroupRepository(listOf(JOINABLE_GROUP))
    val viewModel = createViewModel(repository, FakeAuthRepository(PLAYER))
    runCurrent()

    // The card's two cases. The format rules themselves are tested in InvitationCodeTest.
    for (code in listOf("   ", "ABC23")) {
      viewModel.joinGroup(code)
      runCurrent()

      assertEquals(
          "code \"$code\"",
          JoinState.Error(InvitationError.INVALID_CODE),
          viewModel.uiState.value.joinState,
      )
    }
    assertEquals(emptyList<Pair<String, String>>(), repository.joinCalls)
  }

  @Test
  fun signedOutUserCannotJoin() = runTest {
    val repository =
        InvitationGroupRepository(listOf(JOINABLE_GROUP)).apply {
          addInvitation(FIRST_CODE, JOINABLE_GROUP.id)
        }
    val viewModel = createViewModel(repository, FakeAuthRepository(user = null))
    runCurrent()

    viewModel.joinGroup(FIRST_CODE)
    runCurrent()

    assertEquals(
        JoinState.Error(InvitationError.NOT_SIGNED_IN),
        viewModel.uiState.value.joinState,
    )
    assertEquals(emptyList<Pair<String, String>>(), repository.joinCalls)
  }

  @Test
  fun eachRepositoryJoinErrorEndsInItsOwnErrorState() = runTest {
    for (error in
        listOf(
            InvitationError.UNKNOWN_CODE,
            InvitationError.ALREADY_MEMBER,
            InvitationError.GROUP_NOT_FOUND,
            InvitationError.NETWORK,
        )) {
      val repository =
          InvitationGroupRepository(listOf(JOINABLE_GROUP)).apply {
            joinFailure = InvitationException(error)
          }
      val viewModel = createViewModel(repository, FakeAuthRepository(PLAYER))
      runCurrent()

      viewModel.joinGroup(FIRST_CODE)
      runCurrent()

      assertEquals(error.name, JoinState.Error(error), viewModel.uiState.value.joinState)
    }
  }

  @Test
  fun joinCanBeResubmittedAfterAnError() = runTest {
    val repository =
        InvitationGroupRepository(listOf(JOINABLE_GROUP)).apply {
          addInvitation(FIRST_CODE, JOINABLE_GROUP.id)
        }
    val viewModel = createViewModel(repository, FakeAuthRepository(PLAYER))
    runCurrent()
    viewModel.joinGroup(SECOND_CODE)
    runCurrent()
    assertEquals(JoinState.Error(InvitationError.UNKNOWN_CODE), viewModel.uiState.value.joinState)

    viewModel.joinGroup(FIRST_CODE)
    runCurrent()

    assertEquals(JoinState.Joined(JOINABLE_GROUP.id), viewModel.uiState.value.joinState)
    assertEquals(
        listOf(SECOND_CODE to PLAYER.uid, FIRST_CODE to PLAYER.uid),
        repository.joinCalls,
    )
  }

  @Test
  fun secondJoinWhileJoiningIsIgnored() = runTest {
    val repository =
        InvitationGroupRepository(listOf(JOINABLE_GROUP)).apply {
          addInvitation(FIRST_CODE, JOINABLE_GROUP.id)
          joinGate = CompletableDeferred()
        }
    val viewModel = createViewModel(repository, FakeAuthRepository(PLAYER))
    runCurrent()

    viewModel.joinGroup(FIRST_CODE)
    viewModel.joinGroup(FIRST_CODE)
    runCurrent()

    assertEquals(listOf(FIRST_CODE to PLAYER.uid), repository.joinCalls)
  }

  @Test
  fun resetJoinStateClearsAFinishedJoin() = runTest {
    val repository = InvitationGroupRepository(listOf(JOINABLE_GROUP))
    val viewModel = createViewModel(repository, FakeAuthRepository(PLAYER))
    runCurrent()
    viewModel.joinGroup(FIRST_CODE)
    runCurrent()
    assertEquals(JoinState.Error(InvitationError.UNKNOWN_CODE), viewModel.uiState.value.joinState)

    viewModel.resetJoinState()

    assertEquals(JoinState.Idle, viewModel.uiState.value.joinState)
  }

  @Test
  fun resetJoinStateIsIgnoredWhileJoining() = runTest {
    val repository =
        InvitationGroupRepository(listOf(JOINABLE_GROUP)).apply {
          addInvitation(FIRST_CODE, JOINABLE_GROUP.id)
          joinGate = CompletableDeferred()
        }
    val viewModel = createViewModel(repository, FakeAuthRepository(PLAYER))
    runCurrent()
    viewModel.joinGroup(FIRST_CODE)
    runCurrent()

    viewModel.resetJoinState()
    viewModel.joinGroup(FIRST_CODE)
    runCurrent()

    assertEquals(JoinState.Joining, viewModel.uiState.value.joinState)
    assertEquals(listOf(FIRST_CODE to PLAYER.uid), repository.joinCalls)
  }

  @Test
  fun signingOutDuringAJoinDropsTheResult() = runTest {
    val gate = CompletableDeferred<Unit>()
    val auth = FakeAuthRepository(PLAYER)
    val repository =
        InvitationGroupRepository(listOf(JOINABLE_GROUP)).apply {
          addInvitation(FIRST_CODE, JOINABLE_GROUP.id)
          joinGate = gate
        }
    val viewModel = createViewModel(repository, auth)
    runCurrent()
    viewModel.joinGroup(FIRST_CODE)
    runCurrent()

    auth.currentUser.value = null
    runCurrent()
    gate.complete(Unit)
    runCurrent()

    assertEquals(GroupLoadState.SignedOut, viewModel.uiState.value.loadState)
    assertEquals(JoinState.Idle, viewModel.uiState.value.joinState)
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
      private val newGroupId: String = "new-group",
      private val groups: (String) -> Flow<List<CampaignGroup>>,
  ) : GroupRepository by UnusedGroupRepository {
    val createdGroups = mutableListOf<CampaignGroup>()
    var newGroupIdRequests = 0

    override fun getNewGroupId(): String {
      newGroupIdRequests += 1
      return newGroupId
    }

    override suspend fun createGroup(group: CampaignGroup): Result<Unit> {
      createdGroups += group
      createGate?.await()
      return createResult
    }

    override fun getUserGroups(userId: String): Flow<List<CampaignGroup>> = groups(userId)
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

  /**
   * In-memory invitations that behave like `GroupRepositoryFirestore`: one code per group, a
   * regeneration revokes the old code, joining adds the user to the group and therefore to their
   * [getUserGroups] list, and the same cases return the same [InvitationError].
   */
  private class InvitationGroupRepository(
      groups: List<CampaignGroup>,
      private val newCodes: ArrayDeque<String> = ArrayDeque(listOf(FIRST_CODE, SECOND_CODE)),
  ) : GroupRepository by UnusedGroupRepository {
    private val groups = MutableStateFlow(groups)
    private val groupIdByCode = mutableMapOf<String, String>()
    private val codeByGroupId = mutableMapOf<String, String>()

    /** When set, code requests wait until it completes. */
    var inviteGate: CompletableDeferred<Unit>? = null

    /** When set, code requests fail with it. */
    var inviteFailure: Throwable? = null

    /** When set, join requests wait until it completes. */
    var joinGate: CompletableDeferred<Unit>? = null

    /** When set, join requests fail with it. */
    var joinFailure: Throwable? = null

    val createCalls = mutableListOf<Pair<String, String>>()
    val regenerateCalls = mutableListOf<Pair<String, String>>()
    val joinCalls = mutableListOf<Pair<String, String>>()

    fun addInvitation(code: String, groupId: String) {
      groupIdByCode[code] = groupId
      codeByGroupId[groupId] = code
    }

    override suspend fun createInvitation(groupId: String, gameMasterId: String): Result<String> {
      createCalls += groupId to gameMasterId
      return codeRequest(groupId, gameMasterId) { codeByGroupId[groupId] ?: newCode(groupId) }
    }

    override suspend fun regenerateInvitation(
        groupId: String,
        gameMasterId: String,
    ): Result<String> {
      regenerateCalls += groupId to gameMasterId
      return codeRequest(groupId, gameMasterId) {
        codeByGroupId.remove(groupId)?.let(groupIdByCode::remove)
        newCode(groupId)
      }
    }

    override suspend fun joinGroupWithCode(code: String, userId: String): Result<CampaignGroup> {
      joinCalls += code to userId
      joinGate?.await()
      joinFailure?.let {
        return Result.failure(it)
      }
      val group =
          groups.value.firstOrNull { it.id == groupIdByCode[code] }
              ?: return failure(InvitationError.UNKNOWN_CODE)
      if (userId in group.participantIds) return failure(InvitationError.ALREADY_MEMBER)
      val joined = group.copy(participantIds = group.participantIds + userId)
      groups.update { all -> all.map { if (it.id == joined.id) joined else it } }
      return Result.success(joined)
    }

    override fun getUserGroups(userId: String): Flow<List<CampaignGroup>> = groups.map { all ->
      all.filter { userId in it.participantIds }
    }

    private suspend fun codeRequest(
        groupId: String,
        gameMasterId: String,
        produceCode: () -> String,
    ): Result<String> {
      inviteGate?.await()
      inviteFailure?.let {
        return Result.failure(it)
      }
      val group =
          groups.value.firstOrNull { it.id == groupId }
              ?: return failure(InvitationError.GROUP_NOT_FOUND)
      if (group.gameMasterId != gameMasterId) return failure(InvitationError.NOT_GAME_MASTER)
      return Result.success(produceCode())
    }

    private fun newCode(groupId: String): String =
        newCodes.removeFirst().also { addInvitation(it, groupId) }

    private fun <T> failure(error: InvitationError): Result<T> =
        Result.failure(InvitationException(error))
  }

  private companion object {
    const val FIRST_CODE = "ABCD2345"
    const val SECOND_CODE = "WXYZ6789"

    val GM = AuthUser("gm-1", null, null)
    val PLAYER = AuthUser("player-1", null, null)

    /** Managed by [GM]. */
    val GM_GROUP =
        CampaignGroup(
            id = "group-1",
            name = "The Sunken Crown",
            gameMasterId = GM.uid,
            participantIds = listOf(GM.uid),
        )

    /** A second group managed by [GM]. */
    val SECOND_GM_GROUP =
        CampaignGroup(
            id = "group-2",
            name = "Ashes of Myr",
            gameMasterId = GM.uid,
            participantIds = listOf(GM.uid),
        )

    /** [PLAYER] is already a member. */
    val PLAYER_GROUP =
        CampaignGroup(
            id = "group-3",
            name = "The Last Lantern",
            gameMasterId = "gm-2",
            participantIds = listOf("gm-2", PLAYER.uid),
        )

    /** [PLAYER] is not a member yet. */
    val JOINABLE_GROUP =
        CampaignGroup(
            id = "group-4",
            name = "Iron Wake",
            gameMasterId = "gm-2",
            participantIds = listOf("gm-2"),
        )

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
  }
}
