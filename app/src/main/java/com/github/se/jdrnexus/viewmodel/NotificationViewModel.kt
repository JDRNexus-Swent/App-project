// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.github.se.jdrnexus.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.se.jdrnexus.model.notification.NotificationRepository
import com.github.se.jdrnexus.model.repository.AuthRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class NotificationViewModel(
    private val repository: NotificationRepository,
    private val authRepository: AuthRepository,
) : ViewModel(), NotificationViewModelInterface {

  private val _uiState = MutableStateFlow(NotificationUiState())
  override val notificationUiState: StateFlow<NotificationUiState> = _uiState.asStateFlow()
  /** Compatibility alias for callers using the conventional ViewModel state name. */
  val uiState: StateFlow<NotificationUiState>
    get() = notificationUiState

  private var notificationsJob: Job? = null
  private var currentUserId: String? = null

  init {
    viewModelScope.launch {
      authRepository.authState
          .map { it?.uid }
          .distinctUntilChanged()
          .catch { failure ->
            notificationsJob?.cancel()
            currentUserId = null
            _uiState.value =
                NotificationUiState(loadState = NotificationLoadState.Error(message(failure)))
          }
          .collect { userId ->
            notificationsJob?.cancel()
            currentUserId = userId
            _uiState.value =
                if (userId == null) NotificationUiState(loadState = NotificationLoadState.SignedOut)
                else NotificationUiState()
            if (userId != null) observeNotifications(userId)
          }
    }
  }

  override fun markAsRead(notificationId: String) {
    performAction { repository.markAsRead(notificationId, requireUserId()) }
  }

  override fun markAllAsRead() {
    performAction { repository.markAllAsRead(requireUserId()) }
  }

  override fun deleteNotification(notificationId: String) {
    performAction { repository.deleteNotification(notificationId, requireUserId()) }
  }

  override fun clearActionError() {
    _uiState.update { it.copy(actionError = null) }
  }

  override fun retry() {
    currentUserId?.let(::observeNotifications)
  }

  private fun observeNotifications(userId: String) {
    notificationsJob?.cancel()
    _uiState.update {
      it.copy(
          notifications = emptyList(),
          loadState = NotificationLoadState.Loading,
          actionError = null,
      )
    }
    notificationsJob = viewModelScope.launch {
      try {
        repository.getUserNotifications(userId).collect { notifications ->
          _uiState.update {
            it.copy(
                notifications = notifications,
                loadState =
                    if (notifications.isEmpty()) NotificationLoadState.Empty
                    else NotificationLoadState.Success,
            )
          }
        }
      } catch (cancellation: CancellationException) {
        throw cancellation
      } catch (failure: Exception) {
        _uiState.update { it.copy(loadState = NotificationLoadState.Error(message(failure))) }
      }
    }
  }

  private fun performAction(action: suspend () -> Result<Unit>) {
    viewModelScope.launch {
      _uiState.update { it.copy(actionError = null) }
      try {
        action().onFailure { failure ->
          _uiState.update { it.copy(actionError = message(failure)) }
        }
      } catch (cancellation: CancellationException) {
        throw cancellation
      } catch (failure: Exception) {
        _uiState.update { it.copy(actionError = message(failure)) }
      }
    }
  }

  private suspend fun requireUserId(): String =
      currentUserId ?: throw IllegalStateException("You must be signed in.")

  private fun message(failure: Throwable): String =
      failure.message?.takeIf(String::isNotBlank) ?: "Unable to load notifications."
}
