// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.github.se.jdrnexus.viewmodel

import kotlinx.coroutines.flow.StateFlow

/**
 * Public contract exposed by the notification screen's ViewModel.
 *
 * Implementations observe notifications for the authenticated user and delegate mutations to a
 * repository. They never access Firebase directly. Authentication changes cancel the previous
 * user's subscription and clear the previous user's notifications.
 */
interface NotificationViewModelInterface {
  /** Current notification list and the state of its latest repository operation. */
  val uiState: StateFlow<NotificationUiState>

  /** Marks one notification as read for the signed-in user. */
  fun markAsRead(notificationId: String)

  /** Marks every notification belonging to the signed-in user as read. */
  fun markAllAsRead()

  /** Permanently removes one notification belonging to the signed-in user. */
  fun deleteNotification(notificationId: String)

  /** Retries the active user's notification subscription after a loading failure. */
  fun retry()
}
