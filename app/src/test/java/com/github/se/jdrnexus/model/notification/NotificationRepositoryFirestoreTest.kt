// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.github.se.jdrnexus.model.notification

import com.github.se.jdrnexus.utils.FirebaseEmulatorTestUtils
import com.github.se.jdrnexus.utils.waitFor
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Date
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
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
    firestore = FirebaseEmulatorTestUtils.signInAs("target")
    repository = NotificationRepositoryFirestore(firestore)
  }

  @After
  fun tearDown() {
    runBlocking { FirebaseEmulatorTestUtils.tearDown(firestore, "notifications") }
  }

  @Test
  fun getNewNotificationIdReturnsAUniqueNonBlankId() {
    val first = repository.getNewNotificationId()
    val second = repository.getNewNotificationId()

    assertTrue(first.isNotBlank())
    assertTrue(second.isNotBlank())
    assertFalse(first == second)
  }

  @Test
  fun getUserNotificationsRejectsBlankUserId() {
    val failure =
        assertThrows(IllegalArgumentException::class.java) {
          runBlocking { repository.getUserNotifications("").first() }
        }

    assertEquals("Notification user id cannot be blank.", failure.message)
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
            )
        )
        .getOrThrow()

    FirebaseEmulatorTestUtils.writeBypassingRules(
        firestore,
        "notifications/other-notification",
        mapOf(
            "userId" to "other",
            "type" to "CAMPAIGN_UPDATE",
            "title" to "Update",
            "message" to "Campaign updated.",
            "groupId" to null,
            "createdAt" to com.google.firebase.Timestamp(Date(2000)),
            "isRead" to false,
        ),
    )

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
                type = NotificationType.MEMBERSHIP_CHANGE,
                title = "Membership",
                message = "A member joined.",
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
        "notification-1" in
            FirebaseEmulatorTestUtils.documentIdsBypassingRules(
                firestore,
                NotificationRepositoryFirestore.COLLECTION_PATH,
            )
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
              )
          )
          .getOrThrow()
    }

    repository.markAllAsRead("target").getOrThrow()

    val notifications =
        repository.getUserNotifications("target").waitFor {
          it.size == 2 && it.all(Notification::isRead)
        }
    assertTrue(notifications.all(Notification::isRead))
  }

  @Test
  fun getUserNotificationsIgnoresUnknownNotificationTypes() = runBlocking {
    FirebaseEmulatorTestUtils.writeBypassingRules(
        firestore,
        "notifications/unknown-type",
        mapOf(
            "userId" to "target",
            "type" to "UNKNOWN_TYPE",
            "title" to "Malformed",
            "message" to "Ignored",
            "groupId" to null,
            "createdAt" to com.google.firebase.Timestamp(Date(1000)),
            "isRead" to false,
        ),
    )

    val notifications = repository.getUserNotifications("target").waitFor { it.isEmpty() }
    assertTrue(notifications.isEmpty())
  }

  @Test
  fun getUserNotificationsIgnoresNotificationWithMissingUserId() = runBlocking {
    FirebaseEmulatorTestUtils.writeBypassingRules(
        firestore,
        "notifications/missing-user",
        mapOf(
            "type" to "CAMPAIGN_UPDATE",
            "title" to "Malformed",
            "message" to "Ignored",
            "groupId" to null,
            "createdAt" to com.google.firebase.Timestamp(Date(1000)),
            "isRead" to false,
        ),
    )

    val notifications = repository.getUserNotifications("target").waitFor { it.isEmpty() }
    assertTrue(notifications.isEmpty())
  }

  @Test
  fun rejectsNotificationsWithoutATargetUser() = runBlocking {
    val result =
        repository.createNotification(Notification(title = "Missing target", message = "Message"))

    assertTrue(result.isFailure)
    assertEquals("Notification user id cannot be blank.", result.exceptionOrNull()?.message)
  }
}
