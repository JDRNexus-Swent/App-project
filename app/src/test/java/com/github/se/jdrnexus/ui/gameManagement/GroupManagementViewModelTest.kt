// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.ui.gameManagement

import com.github.se.jdrnexus.model.personalSpace.CampaignGroup
import com.github.se.jdrnexus.model.personalSpace.GroupRepository
import com.github.se.jdrnexus.model.repository.AuthRepository
import com.github.se.jdrnexus.model.repository.AuthUser
import com.github.se.jdrnexus.utils.MainDispatcherRule
import com.github.se.jdrnexus.utils.NavigationGroupRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GroupManagementViewModelTest {
  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  private val gameMaster = AuthUser("gm", null, "Alice")
  private val group =
      CampaignGroup(
          id = "group",
          name = "The Sunken Crown",
          gameMasterId = "gm",
          participantIds = listOf("gm", "p1", "p2"),
      )
  private val groups = FakeGroupRepository(group)

  private fun viewModel(user: AuthUser? = gameMaster) =
      GroupManagementViewModel(group.id, groups, FakeAuthRepository(user))

  private val GroupManagementViewModel.state
    get() = uiState.value

  @Test
  fun showsTheGameMasterByNameThenThePlayersById() = runTest {
    val viewModel = viewModel()
    assertTrue(viewModel.state.isLoading)
    advanceUntilIdle()

    assertEquals(
        listOf(
            GroupMember("gm", "Alice", isGameMaster = true),
            GroupMember("p1", "p1", isGameMaster = false),
            GroupMember("p2", "p2", isGameMaster = false),
        ),
        viewModel.state.members,
    )
    assertEquals("The Sunken Crown", viewModel.state.groupName)
    assertFalse(viewModel.state.isLoading)
    assertTrue(viewModel.state.canManage)
  }

  @Test
  fun aGameMasterWithoutUsernameIsNamedById() = runTest {
    val viewModel = viewModel(AuthUser("gm", null, " "))
    advanceUntilIdle()

    assertEquals("gm", viewModel.state.members.first().label)
  }

  @Test
  fun aPlayerCannotManageTheGroup() = runTest {
    val viewModel = viewModel(AuthUser("p1", null, "Bob"))
    advanceUntilIdle()

    viewModel.removeMember("p2")
    viewModel.deleteGroup()
    advanceUntilIdle()

    assertFalse(viewModel.state.canManage)
    assertEquals("gm", viewModel.state.members.first().label)
    assertEquals(emptyList<String>(), groups.removed)
    assertEquals(0, groups.deleteCalls)
  }

  @Test
  fun theListFollowsTheGroup() = runTest {
    val viewModel = viewModel()
    advanceUntilIdle()

    groups.group.value = group.copy(participantIds = listOf("gm", "p3"))
    advanceUntilIdle()

    assertEquals(listOf("gm", "p3"), viewModel.state.members.map { it.uid })
  }

  @Test
  fun removeMemberRemovesThePlayer() = runTest {
    val viewModel = viewModel()
    advanceUntilIdle()

    viewModel.removeMember("p1")
    advanceUntilIdle()

    assertEquals(listOf("p1"), groups.removed)
    assertEquals(listOf("gm", "p2"), viewModel.state.members.map { it.uid })
    assertFalse(viewModel.state.isWorking)
    assertNull(viewModel.state.error)
  }

  @Test
  fun removeMemberIgnoresTheGameMasterAndUnknownIds() = runTest {
    val viewModel = viewModel()
    advanceUntilIdle()

    viewModel.removeMember("gm")
    viewModel.removeMember("stranger")
    advanceUntilIdle()

    assertEquals(emptyList<String>(), groups.removed)
  }

  @Test
  fun actionsAreIgnoredWhileOneRuns() = runTest {
    val viewModel = viewModel()
    advanceUntilIdle()
    val gate = CompletableDeferred<Unit>()
    groups.gate = gate

    viewModel.removeMember("p1")
    viewModel.removeMember("p2")
    viewModel.deleteGroup()
    runCurrent()

    assertTrue(viewModel.state.isWorking)
    assertEquals(listOf("p1"), groups.removed)
    assertEquals(0, groups.deleteCalls)
    gate.complete(Unit)
    advanceUntilIdle()
    assertFalse(viewModel.state.isWorking)
  }

  @Test
  fun aFailedRemovalReportsAnErrorAndCanBeRetried() = runTest {
    val viewModel = viewModel()
    advanceUntilIdle()
    groups.nextResult = Result.failure(Exception("offline"))

    viewModel.removeMember("p1")
    advanceUntilIdle()

    assertEquals(GroupManagementError.REMOVE_FAILED, viewModel.state.error)
    assertFalse(viewModel.state.isWorking)
    assertEquals(listOf("gm", "p1", "p2"), viewModel.state.members.map { it.uid })

    groups.nextResult = Result.success(Unit)
    viewModel.removeMember("p1")
    advanceUntilIdle()

    assertNull(viewModel.state.error)
    assertEquals(listOf("gm", "p2"), viewModel.state.members.map { it.uid })
  }

  @Test
  fun deleteGroupMarksTheGroupDeleted() = runTest {
    val viewModel = viewModel()
    advanceUntilIdle()

    viewModel.deleteGroup()
    advanceUntilIdle()

    assertEquals(1, groups.deleteCalls)
    assertTrue(viewModel.state.isGroupDeleted)
  }

  @Test
  fun aFailedDeletionReportsAnErrorAndKeepsTheGroup() = runTest {
    val viewModel = viewModel()
    advanceUntilIdle()
    groups.nextResult = Result.failure(Exception("denied"))

    viewModel.deleteGroup()
    advanceUntilIdle()

    assertEquals(GroupManagementError.DELETE_FAILED, viewModel.state.error)
    assertFalse(viewModel.state.isGroupDeleted)
  }

  @Test
  fun aGroupDeletedElsewhereIsReportedAsDeleted() = runTest {
    val viewModel = viewModel()
    advanceUntilIdle()

    groups.group.value = null
    advanceUntilIdle()

    assertTrue(viewModel.state.isGroupDeleted)
  }

  @Test
  fun aLoadFailureIsReported() = runTest {
    groups.loadError = Exception("permission denied")
    val viewModel = viewModel()
    advanceUntilIdle()

    assertEquals(GroupManagementError.LOAD_FAILED, viewModel.state.error)
    assertFalse(viewModel.state.isLoading)
  }

  @Test
  fun retryAfterALoadFailureFollowsTheGroupAgain() = runTest {
    groups.loadError = Exception("permission denied")
    val viewModel = viewModel()
    advanceUntilIdle()
    groups.loadError = null

    viewModel.retry()
    assertTrue(viewModel.state.isLoading)
    advanceUntilIdle()

    assertNull(viewModel.state.error)
    assertEquals(listOf("gm", "p1", "p2"), viewModel.state.members.map { it.uid })
    groups.group.value = group.copy(participantIds = listOf("gm", "p3"))
    advanceUntilIdle()
    assertEquals(listOf("gm", "p3"), viewModel.state.members.map { it.uid })
  }

  @Test
  fun retryIsIgnoredWhileTheGroupIsFollowed() = runTest {
    val viewModel = viewModel()
    advanceUntilIdle()

    viewModel.retry()
    advanceUntilIdle()

    assertEquals(1, groups.flowCalls)
  }

  /**
   * Holds one group in memory and changes it like Firestore does: a removal or a deletion that
   * succeeds updates [group]. Methods the screen does not use are delegated to the shared fake.
   */
  private class FakeGroupRepository(initial: CampaignGroup) :
      GroupRepository by NavigationGroupRepository(initial) {
    val group = MutableStateFlow<CampaignGroup?>(initial)
    val removed = mutableListOf<String>()
    var deleteCalls = 0
    var loadError: Exception? = null
    var nextResult: Result<Unit> = Result.success(Unit)
    /** When set, each removal or deletion waits for it before returning. */
    var gate: CompletableDeferred<Unit>? = null

    /** How many times the group was subscribed to, each like a new Firestore listener. */
    var flowCalls = 0

    override fun getGroupFlow(groupId: String): Flow<CampaignGroup?> {
      flowCalls++
      return loadError?.let { error -> flow { throw error } } ?: group
    }

    override suspend fun removeUserFromGroup(groupId: String, userId: String): Result<Unit> {
      removed += userId
      return finish {
        group.value = group.value?.let { it.copy(participantIds = it.participantIds - userId) }
      }
    }

    override suspend fun deleteGroup(groupId: String): Result<Unit> {
      deleteCalls++
      return finish { group.value = null }
    }

    private suspend fun finish(onSuccess: () -> Unit): Result<Unit> {
      gate?.await()
      return nextResult.onSuccess { onSuccess() }
    }
  }

  private class FakeAuthRepository(user: AuthUser?) : AuthRepository {
    override val authState = MutableStateFlow(user)

    override suspend fun signUpWithEmail(email: String, password: String, username: String) =
        error("Not used")

    override suspend fun signInWithEmail(email: String, password: String) = error("Not used")

    override suspend fun signInWithGoogle(idToken: String) = error("Not used")

    override suspend fun signOut() = error("Not used")
  }
}
