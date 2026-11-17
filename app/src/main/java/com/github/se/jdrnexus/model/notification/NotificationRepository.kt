// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.github.se.jdrnexus.model.notification

import kotlinx.coroutines.flow.Flow

/**
 * Repository contract for notification creation and management.
 *
 * Implementations persist notifications for one authenticated user and enforce ownership on
 * read-state and deletion operations. Firebase access is an implementation detail of this
 * interface.
 */
interface NotificationRepository {
  /** Creates a new, unused notification document ID. */
  fun getNewNotificationId(): String

  /**
   * Persists [notification].
   *
   * @return [Result.success] after the write completes, or [Result.failure] when validation or
   *   persistence fails.
   */
  suspend fun createNotification(notification: Notification): Result<Unit>

  /**
   * Observes notifications belonging to [userId], newest first.
   *
   * @return A real-time flow that emits the user's current notifications or closes with an error.
   */
  fun getUserNotifications(userId: String): Flow<List<Notification>>

  /**
   * Marks [notificationId] as read if it belongs to [userId].
   *
   * @return [Result.success] when the update succeeds, or [Result.failure] for invalid ids, missing
   *   documents, ownership violations, or persistence errors.
   */
  suspend fun markAsRead(notificationId: String, userId: String): Result<Unit>

  /**
   * Marks every unread notification belonging to [userId] as read.
   *
   * @return [Result.success] after all unread notifications are updated, or [Result.failure] when
   *   validation or persistence fails.
   */
  suspend fun markAllAsRead(userId: String): Result<Unit>

  /**
   * Permanently deletes [notificationId] if it belongs to [userId].
   *
   * @return [Result.success] after deletion, or [Result.failure] for invalid ids, missing
   *   documents, ownership violations, or persistence errors.
   */
  suspend fun deleteNotification(notificationId: String, userId: String): Result<Unit>
}
