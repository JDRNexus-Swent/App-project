// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.github.se.jdrnexus.model.notification

import com.github.se.jdrnexus.model.personalSpace.safeFirestoreRequest
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Firestore implementation of [NotificationRepository].
 *
 * Notification documents are stored in [COLLECTION_PATH]. Live user queries are ordered by creation
 * time, and mutations verify that the notification belongs to the requesting user before writing.
 */
class NotificationRepositoryFirestore(private val firestore: FirebaseFirestore) :
    NotificationRepository {

  companion object {
    const val COLLECTION_PATH = "notifications"
  }

  private val notifications = firestore.collection(COLLECTION_PATH)

  override fun getNewNotificationId(): String = notifications.document().id

  override suspend fun createNotification(notification: Notification): Result<Unit> =
      safeFirestoreRequest {
        require(notification.userId.isNotBlank()) { "Notification user id cannot be blank." }
        require(notification.title.isNotBlank()) { "Notification title cannot be blank." }
        require(notification.message.isNotBlank()) { "Notification message cannot be blank." }
        val data =
            mapOf(
                "userId" to notification.userId,
                "type" to notification.type.name,
                "title" to notification.title,
                "message" to notification.message,
                "groupId" to notification.groupId,
                "createdAt" to (notification.createdAt ?: FieldValue.serverTimestamp()),
                "isRead" to notification.isRead,
            )
        notifications.document(notification.id.ifBlank { getNewNotificationId() }).set(data).await()
      }

  override fun getUserNotifications(userId: String): Flow<List<Notification>> = callbackFlow {
    if (userId.isBlank()) {
      close(IllegalArgumentException("Notification user id cannot be blank."))
      return@callbackFlow
    }
    val registration =
        notifications
            .whereEqualTo("userId", userId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
              if (error != null) {
                close(error)
              } else if (snapshot != null) {
                trySend(snapshot.documents.mapNotNull(::toNotification))
              }
            }
    awaitClose { registration.remove() }
  }

  override suspend fun markAsRead(notificationId: String, userId: String): Result<Unit> =
      safeFirestoreRequest {
        ownedNotificationReference(notificationId, userId).update("isRead", true).await()
      }

  override suspend fun markAllAsRead(userId: String): Result<Unit> = safeFirestoreRequest {
    require(userId.isNotBlank()) { "Notification user id cannot be blank." }
    val unread =
        notifications.whereEqualTo("userId", userId).whereEqualTo("isRead", false).get().await()
    unread.documents.chunked(500).forEach { chunk ->
      val batch = firestore.batch()
      chunk.forEach { batch.update(it.reference, "isRead", true) }
      batch.commit().await()
    }
  }

  override suspend fun deleteNotification(notificationId: String, userId: String): Result<Unit> =
      safeFirestoreRequest {
        ownedNotificationReference(notificationId, userId).delete().await()
      }

  private suspend fun ownedNotificationReference(
      notificationId: String,
      userId: String,
  ): DocumentReference {
    require(notificationId.isNotBlank()) { "Notification id cannot be blank." }
    require(userId.isNotBlank()) { "Notification user id cannot be blank." }
    val reference = notifications.document(notificationId)
    val snapshot = reference.get().await()
    require(snapshot.exists()) { "Notification not found." }
    require(snapshot.getString("userId") == userId) { "Notification does not belong to user." }
    return reference
  }

  private fun toNotification(document: DocumentSnapshot): Notification? {
    val type =
        document.getString("type")?.let { value ->
          runCatching { NotificationType.valueOf(value) }.getOrNull()
        } ?: return null
    return Notification(
        id = document.id,
        userId = document.getString("userId") ?: return null,
        type = type,
        title = document.getString("title") ?: return null,
        message = document.getString("message") ?: return null,
        groupId = document.getString("groupId"),
        createdAt = document.getDate("createdAt"),
        isRead = document.getBoolean("isRead") ?: false,
    )
  }
}
