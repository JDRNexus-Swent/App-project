// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.github.se.jdrnexus.model.notification

import com.github.se.jdrnexus.utils.FirebaseEmulatorTestUtils
import com.github.se.jdrnexus.utils.waitFor
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Date
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NotificationRepositoryFirestoreTest {
  private lateinit var firestore: FirebaseFirestore
  private lateinit var repository: NotificationRepository

  @Before
  fun setUp() {
    firestore = FirebaseEmulatorTestUtils.getFirestore()
    repository = NotificationRepositoryFirestore(firestore)
  }

  @After
  fun tearDown() {
    runBlocking { FirebaseEmulatorTestUtils.tearDown(firestore, "notifications") }
  }

  @Test
  fun createsAndRetrievesNotificationsForOnlyTheTargetUser() = runBlocking {
    repository
        .createNotification(
            Notification(
                id = "target-notification",
                userId = "target",
                type = NotificationType.GROUP_INVITATION,
                title = "Invitation",
                message = "You were invited.",
                groupId = "group-1",
                createdAt = Date(1000),
            )
        )
        .getOrThrow()
    repository
        .createNotification(
            Notification(
                id = "other-notification",
                userId = "other",
                type = NotificationType.CAMPAIGN_UPDATE,
                title = "Update",
                message = "Campaign updated.",
                createdAt = Date(2000),
            )
        )
        .getOrThrow()

    val notifications = repository.getUserNotifications("target").waitFor { it.isNotEmpty() }

    assertEquals(listOf("target-notification"), notifications.map { it.id })
    assertEquals(NotificationType.GROUP_INVITATION, notifications.single().type)
    assertFalse(notifications.single().isRead)
  }

  @Test
  fun managesReadStateAndRejectsAnotherUsersNotification() = runBlocking {
    repository
        .createNotification(
            Notification(
                id = "notification-1",
                userId = "target",
                title = "Membership",
                message = "A member joined.",
                type = NotificationType.MEMBERSHIP_CHANGE,
                createdAt = Date(1000),
            )
        )
        .getOrThrow()

    val wrongOwner = repository.markAsRead("notification-1", "other")
    assertTrue(wrongOwner.isFailure)
    assertEquals("Notification does not belong to user.", wrongOwner.exceptionOrNull()?.message)

    repository.markAsRead("notification-1", "target").getOrThrow()
    FirebaseEmulatorTestUtils.pumpMainLooperUntil {
      firestore
          .collection(NotificationRepositoryFirestore.COLLECTION_PATH)
          .document("notification-1")
          .get()
          .await()
          .getBoolean("isRead") == true
    }
    repository.deleteNotification("notification-1", "target").getOrThrow()

    assertFalse(
        firestore
            .collection(NotificationRepositoryFirestore.COLLECTION_PATH)
            .document("notification-1")
            .get()
            .await()
            .exists()
    )
  }

  @Test
  fun markAllAsReadUpdatesEveryUnreadNotification() = runBlocking {
    listOf("one", "two").forEach { id ->
      repository
          .createNotification(
              Notification(
                  id = id,
                  userId = "target",
                  title = id,
                  message = "Message",
                  createdAt = Date(id.length.toLong()),
              )
          )
          .getOrThrow()
    }

    repository.markAllAsRead("target").getOrThrow()
    val notifications =
        repository.getUserNotifications("target").waitFor {
          it.size == 2 && it.all { item -> item.isRead }
        }

    assertTrue(notifications.all { it.isRead })
  }

  @Test
  fun rejectsNotificationsWithoutATargetUser() = runBlocking {
    val result =
        repository.createNotification(Notification(title = "Missing target", message = "Message"))

    assertTrue(result.isFailure)
    assertEquals("Notification user id cannot be blank.", result.exceptionOrNull()?.message)
  }
}
