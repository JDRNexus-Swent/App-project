// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.utils

import com.github.se.jdrnexus.model.notification.Notification
import com.github.se.jdrnexus.model.notification.NotificationType
import com.github.se.jdrnexus.viewmodel.NotificationLoadState
import com.github.se.jdrnexus.viewmodel.NotificationUiState
import com.github.se.jdrnexus.viewmodel.NotificationViewModelInterface
import java.util.Date
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * Records the calls made by the notification UI. Apart from [clearActionError], it does not change
 * any state: each test sets the [NotificationUiState] it checks through [notificationUiState].
 */
internal class FakeNotificationViewModel(
    initialState: NotificationUiState = NotificationUiState()
) : NotificationViewModelInterface {
  override val notificationUiState = MutableStateFlow(initialState)
  val markedAsRead = mutableListOf<String>()
  var markAllCalls = 0
  var clearActionErrorCalls = 0
  var retryCalls = 0

  override fun markAsRead(notificationId: String) {
    markedAsRead += notificationId
  }

  override fun markAllAsRead() {
    markAllCalls++
  }

  /** The notification UI has no delete action yet. */
  override fun deleteNotification(notificationId: String) = Unit

  /** Clears the error like the real ViewModel, so the screen can show the next one. */
  override fun clearActionError() {
    clearActionErrorCalls++
    notificationUiState.update { it.copy(actionError = null) }
  }

  override fun retry() {
    retryCalls++
  }
}

/** Loaded state listing [notifications]. */
internal fun loadedNotifications(vararg notifications: Notification) =
    NotificationUiState(
        notifications = notifications.toList(),
        loadState = NotificationLoadState.Success,
    )

/** A notification with readable defaults, so each test only states what matters to it. */
internal fun testNotification(
    id: String,
    isRead: Boolean = false,
    type: NotificationType = NotificationType.CAMPAIGN_UPDATE,
    groupId: String? = null,
    title: String = "Title of $id",
    createdAt: Date? = Date(),
) =
    Notification(
        id = id,
        userId = "user",
        senderId = "sender",
        type = type,
        title = title,
        message = "Message of $id",
        groupId = groupId,
        createdAt = createdAt,
        isRead = isRead,
    )
