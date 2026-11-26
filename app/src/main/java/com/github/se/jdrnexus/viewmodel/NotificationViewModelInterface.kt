// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.github.se.jdrnexus.viewmodel

import com.github.se.jdrnexus.model.notification.Notification
import kotlinx.coroutines.flow.StateFlow

/** Describes the result of subscribing to the current user's notifications. */
sealed interface NotificationLoadState {
  /** The subscription has not emitted its first value yet. */
  data object Loading : NotificationLoadState

  /** No authenticated user is available. */
  data object SignedOut : NotificationLoadState

  /** At least one notification is available. */
  data object Success : NotificationLoadState

  /** The authenticated user has no notifications. */
  data object Empty : NotificationLoadState

  /** The subscription failed and can be retried. */
  data class Error(val message: String) : NotificationLoadState
}

/** Immutable state rendered by notification screens. */
data class NotificationUiState(
    /** Notifications belonging to the authenticated user, newest first. */
    val notifications: List<Notification> = emptyList(),
    /** State of the active notification subscription. */
    val loadState: NotificationLoadState = NotificationLoadState.Loading,
    /** Error from the latest notification mutation, if one occurred. */
    val actionError: String? = null,
) {
  /** Number of notifications the user has not read yet, shown on the Home bell. */
  val unreadCount: Int
    get() = notifications.count { !it.isRead }
}

/**
 * Public contract exposed by the notification screen's ViewModel.
 *
 * Implementations observe notifications for the authenticated user and delegate mutations to a
 * repository. They never access Firebase directly. Authentication changes cancel the previous
 * user's subscription and clear the previous user's notifications.
 */
interface NotificationViewModelInterface {
  /** Current notification list and the state of its latest repository operation. */
  val notificationUiState: StateFlow<NotificationUiState>

  /** Marks one notification as read for the signed-in user. */
  fun markAsRead(notificationId: String)

  /** Marks every notification belonging to the signed-in user as read. */
  fun markAllAsRead()

  /** Permanently removes one notification belonging to the signed-in user. */
  fun deleteNotification(notificationId: String)

  /** Clears the latest notification-action error after the screen has displayed it. */
  fun clearActionError()

  /** Retries the active user's notification subscription after a loading failure. */
  fun retry()
}
