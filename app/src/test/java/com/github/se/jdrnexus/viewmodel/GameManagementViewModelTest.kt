// Co-authored-by: OpenAI Codex
package com.github.se.jdrnexus.viewmodel

import androidx.lifecycle.ViewModelStore
import com.github.se.jdrnexus.model.personalSpace.CampaignGroup
import com.github.se.jdrnexus.model.personalSpace.GroupRepository
import com.github.se.jdrnexus.model.repository.AuthRepository
import com.github.se.jdrnexus.model.repository.AuthResult
import com.github.se.jdrnexus.model.repository.AuthUser
import com.github.se.jdrnexus.ui.authentication.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GameManagementViewModelTest {
  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  @Test
  fun loadsGroupsAndExposesEachUsersRole() = runTest {
    val user = AuthUser("user-1", "user@example.com", "User")
    val group = CampaignGroup(id = "group-1", name = "Campaign", gameMasterId = user.uid)
    val repository = FakeGroupRepository { flowOf(listOf(group)) }
    val viewModel = GameManagementViewModel(repository, FakeAuthRepository(user))

    assertEquals(GroupLoadState.Loading, viewModel.uiState.value.loadState)
    advanceUntilIdle()

    assertEquals(GroupLoadState.Success, viewModel.uiState.value.loadState)
    assertEquals(listOf(UserGroup(group, GroupRole.GAME_MASTER)), viewModel.uiState.value.groups)
  }

  @Test
  fun exposesEmptyWhenRepositoryReturnsNoGroups() = runTest {
    val viewModel =
        GameManagementViewModel(FakeGroupRepository { flowOf(emptyList()) }, FakeAuthRepository())

    advanceUntilIdle()

    assertEquals(GroupLoadState.Empty, viewModel.uiState.value.loadState)
    assertTrue(viewModel.uiState.value.groups.isEmpty())
  }

  @Test
  fun reflectsLiveGroupUpdatesAndPlayerRole() = runTest {
    val user = AuthUser("player-1", null, null)
    val groups = MutableStateFlow(listOf(CampaignGroup(id = "one", gameMasterId = "gm")))
    val viewModel =
        GameManagementViewModel(FakeGroupRepository { groups }, FakeAuthRepository(user))
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
    val viewModel = GameManagementViewModel(repository, FakeAuthRepository())
    advanceUntilIdle()

    assertEquals(GroupLoadState.Error("offline"), viewModel.uiState.value.loadState)
    viewModel.loadGroups()
    advanceUntilIdle()

    assertEquals(listOf("user-1", "user-1"), requests)
    assertEquals("recovered", viewModel.uiState.value.groups.single().group.id)
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
    val viewModel = GameManagementViewModel(repository, FakeAuthRepository())
    advanceUntilIdle()

    viewModel.loadGroups()
    advanceUntilIdle()

    assertEquals(1, subscriptions)
  }

  @Test
  fun accountSwitchCancelsOldSubscriptionAndClearsOldGroups() = runTest {
    val auth = FakeAuthRepository(AuthUser("first", null, null))
    val repository = TrackingGroupRepository()
    val viewModel = GameManagementViewModel(repository, auth)
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
    val viewModel = GameManagementViewModel(repository, auth)
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
    val viewModel = GameManagementViewModel(repository, FakeAuthRepository())
    val store = ViewModelStore().also { it.put("game-management", viewModel) }
    advanceUntilIdle()
    assertEquals(setOf("user-1"), repository.activeUsers)

    store.clear()
    advanceUntilIdle()

    assertTrue(repository.activeUsers.isEmpty())
  }

  private class FakeAuthRepository(user: AuthUser? = AuthUser("user-1", null, null)) :
      AuthRepository {
    val currentUser = MutableStateFlow(user)
    override val authState: Flow<AuthUser?> = currentUser

    override suspend fun signUpWithEmail(email: String, password: String, username: String) =
        AuthResult.Failure(com.github.se.jdrnexus.model.repository.AuthError.UNKNOWN)

    override suspend fun signInWithEmail(email: String, password: String) =
        AuthResult.Failure(com.github.se.jdrnexus.model.repository.AuthError.UNKNOWN)

    override suspend fun signInWithGoogle(idToken: String) =
        AuthResult.Failure(com.github.se.jdrnexus.model.repository.AuthError.UNKNOWN)

    override suspend fun signOut() = AuthResult.Success(Unit)
  }

  private class FakeGroupRepository(
      private val groups: (String) -> Flow<List<CampaignGroup>>,
  ) : GroupRepository by UnusedGroupRepository {
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
