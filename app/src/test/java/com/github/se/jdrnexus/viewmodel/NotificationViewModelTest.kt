// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.github.se.jdrnexus.viewmodel

import com.github.se.jdrnexus.model.notification.Notification
import com.github.se.jdrnexus.model.notification.NotificationRepository
import com.github.se.jdrnexus.model.notification.NotificationType
import com.github.se.jdrnexus.model.repository.AuthRepository
import com.github.se.jdrnexus.model.repository.AuthResult
import com.github.se.jdrnexus.model.repository.AuthUser
import com.github.se.jdrnexus.utils.MainDispatcherRule
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class NotificationViewModelTest {
  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  @Test
  fun loadsNotificationsForTheAuthenticatedUser() = runTest {
    val notification =
        Notification(
            id = "notification-1",
            userId = "user-1",
            type = NotificationType.CAMPAIGN_UPDATE,
            title = "Update",
            message = "Campaign updated.",
        )
    val repository = FakeNotificationRepository(flowOf(listOf(notification)))
    val viewModel =
        NotificationViewModel(repository, FakeAuthRepository(AuthUser("user-1", null, null)))

    advanceUntilIdle()

    assertEquals(NotificationLoadState.Success, viewModel.uiState.value.loadState)
    assertEquals(listOf(notification), viewModel.uiState.value.notifications)
  }

  @Test
  fun startsSignedOutWhenNoUserIsAuthenticated() = runTest {
    val viewModel =
        NotificationViewModel(
            FakeNotificationRepository(flowOf(emptyList())),
            FakeAuthRepository(null),
        )

    advanceUntilIdle()

    assertEquals(NotificationLoadState.SignedOut, viewModel.uiState.value.loadState)
    assertEquals(emptyList<Notification>(), viewModel.uiState.value.notifications)
  }

  @Test
  fun reportsRepositoryFailureAndRetryRecovers() = runTest {
    var attempts = 0
    val repository =
        FakeNotificationRepository(
            notifications =
                flow {
                  attempts++
                  if (attempts == 1) throw IllegalStateException("offline")
                  emit(emptyList())
                },
        )
    val viewModel =
        NotificationViewModel(repository, FakeAuthRepository(AuthUser("user-1", null, null)))

    advanceUntilIdle()
    assertEquals(NotificationLoadState.Error("offline"), viewModel.uiState.value.loadState)

    viewModel.retry()
    advanceUntilIdle()

    assertEquals(NotificationLoadState.Empty, viewModel.uiState.value.loadState)
    assertEquals(2, attempts)
  }

  @Test
  fun sendsReadAndDeleteActionsWithTheAuthenticatedUser() = runTest {
    val repository = FakeNotificationRepository(flowOf(emptyList()))
    val viewModel =
        NotificationViewModel(repository, FakeAuthRepository(AuthUser("user-1", null, null)))
    advanceUntilIdle()

    viewModel.markAsRead("notification-1")
    viewModel.deleteNotification("notification-2")
    advanceUntilIdle()

    assertEquals(listOf("notification-1" to "user-1"), repository.readRequests)
    assertEquals(listOf("notification-2" to "user-1"), repository.deleteRequests)
    assertTrue(viewModel.uiState.value.actionError == null)
  }

  @Test
  fun clearActionErrorRemovesADisplayedMutationError() = runTest {
    val repository =
        FakeNotificationRepository(
            flowOf(emptyList()),
            markAsReadResult = Result.failure(IllegalStateException("offline")),
        )
    val viewModel =
        NotificationViewModel(repository, FakeAuthRepository(AuthUser("user-1", null, null)))
    advanceUntilIdle()

    viewModel.markAsRead("notification-1")
    advanceUntilIdle()
    assertEquals("offline", viewModel.uiState.value.actionError)

    viewModel.clearActionError()

    assertTrue(viewModel.uiState.value.actionError == null)
  }

  @Test
  fun signOutClearsNotificationsAndCancelsSubscription() = runTest {
    val notification =
        Notification(
            id = "notification-1",
            userId = "user-1",
            type = NotificationType.CAMPAIGN_UPDATE,
            title = "Update",
            message = "Campaign updated.",
        )
    val authRepository = FakeAuthRepository(AuthUser("user-1", "user@example.com", "User"))
    val repository =
        FakeNotificationRepository(
            notifications =
                flow {
                  emit(listOf(notification))
                  awaitCancellation()
                },
            trackActiveUsers = true,
        )
    val viewModel = NotificationViewModel(repository, authRepository)
    advanceUntilIdle()
    assertEquals(NotificationLoadState.Success, viewModel.uiState.value.loadState)
    assertEquals(listOf(notification), viewModel.uiState.value.notifications)
    assertEquals(setOf("user-1"), repository.activeUsers)

    authRepository.currentUser.value = null
    advanceUntilIdle()

    assertEquals(NotificationLoadState.SignedOut, viewModel.uiState.value.loadState)
    assertTrue(viewModel.uiState.value.notifications.isEmpty())
    assertTrue(repository.activeUsers.isEmpty())
  }

  private class FakeNotificationRepository(
      private val notifications: Flow<List<Notification>>,
      private val markAsReadResult: Result<Unit> = Result.success(Unit),
      private val trackActiveUsers: Boolean = false,
  ) : NotificationRepository {
    val readRequests = mutableListOf<Pair<String, String>>()
    val deleteRequests = mutableListOf<Pair<String, String>>()
    val activeUsers = mutableSetOf<String>()

    override fun getNewNotificationId() = "new-notification"

    override suspend fun createNotification(notification: Notification) = Result.success(Unit)

    override fun getUserNotifications(userId: String): Flow<List<Notification>> = flow {
      if (trackActiveUsers) activeUsers += userId
      try {
        notifications.collect { emit(it) }
      } finally {
        if (trackActiveUsers) activeUsers -= userId
      }
    }

    override suspend fun markAsRead(notificationId: String, userId: String): Result<Unit> {
      readRequests += notificationId to userId
      return markAsReadResult
    }

    override suspend fun markAllAsRead(userId: String) = Result.success(Unit)

    override suspend fun deleteNotification(notificationId: String, userId: String): Result<Unit> {
      deleteRequests += notificationId to userId
      return Result.success(Unit)
    }
  }

  private class FakeAuthRepository(user: AuthUser?) : AuthRepository {
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
}
