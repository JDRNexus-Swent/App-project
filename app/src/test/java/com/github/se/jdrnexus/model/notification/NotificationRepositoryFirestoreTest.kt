// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.github.se.jdrnexus.model.notification

// Emulator-independent notification repository tests use MockK below.
import com.github.se.jdrnexus.utils.FirebaseEmulatorTestUtils
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.EventListener
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.WriteBatch
import io.mockk.every
import io.mockk.mockk
import java.util.Date
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
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
    firestore = FirebaseEmulatorTestUtils.getFirestore()
    repository = NotificationRepositoryFirestore(firestore)
  }

  @After
  fun tearDown() {
    runBlocking { FirebaseEmulatorTestUtils.tearDown(firestore, "notifications") }
  }

  /** Mocks a notification listener for one [userId] and returns its first repository emission. */
  private suspend fun notificationsFromMockListener(
      userId: String,
      documents: List<DocumentSnapshot>,
      firestoreMock: FirebaseFirestore = mockk(),
  ): List<Notification> {
    val notificationsMock = mockk<CollectionReference>()
    val queryMock = mockk<Query>()
    val snapshotMock = mockk<QuerySnapshot>()
    val listenerRegistration = mockk<ListenerRegistration>(relaxed = true)
    every { firestoreMock.collection(NotificationRepositoryFirestore.COLLECTION_PATH) } returns
        notificationsMock
    every { notificationsMock.whereEqualTo("userId", userId) } returns queryMock
    every { snapshotMock.documents } returns documents
    every { queryMock.addSnapshotListener(any()) } answers
        {
          val listener = firstArg<EventListener<QuerySnapshot>>()
          listener.onEvent(snapshotMock, null)
          listenerRegistration
        }
    return NotificationRepositoryFirestore(firestoreMock).getUserNotifications(userId).first()
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
  fun getUserNotificationsHandlesNullFirestoreSnapshot(): Unit = runBlocking {
    val firestoreMock = mockk<FirebaseFirestore>()
    val notificationsMock = mockk<CollectionReference>()
    val queryMock = mockk<Query>()
    val listenerRegistration = mockk<ListenerRegistration>(relaxed = true)
    every { firestoreMock.collection(NotificationRepositoryFirestore.COLLECTION_PATH) } returns
        notificationsMock
    every { notificationsMock.whereEqualTo("userId", "target") } returns queryMock
    every { queryMock.addSnapshotListener(any()) } answers
        {
          val listener = firstArg<EventListener<QuerySnapshot>>()
          listener.onEvent(null, null)
          listenerRegistration
        }

    val result =
        withTimeoutOrNull(100.milliseconds) {
          NotificationRepositoryFirestore(firestoreMock).getUserNotifications("target").first()
        }

    assertEquals(result, null)
  }

  @Test
  fun getUserNotificationsPropagatesFirestoreListenerError(): Unit = runBlocking {
    val firestoreMock = mockk<FirebaseFirestore>()
    val notificationsMock = mockk<CollectionReference>()
    val queryMock = mockk<Query>()
    val listenerRegistration = mockk<ListenerRegistration>(relaxed = true)
    val listenerError = mockk<FirebaseFirestoreException>(relaxed = true)
    every { firestoreMock.collection(NotificationRepositoryFirestore.COLLECTION_PATH) } returns
        notificationsMock
    every { notificationsMock.whereEqualTo("userId", "target") } returns queryMock
    every { queryMock.addSnapshotListener(any()) } answers
        {
          val listener = firstArg<EventListener<QuerySnapshot>>()
          listener.onEvent(null, listenerError)
          listenerRegistration
        }

    assertThrows(FirebaseFirestoreException::class.java) {
      runBlocking {
        NotificationRepositoryFirestore(firestoreMock).getUserNotifications("target").first()
      }
    }
  }

  @Test
  fun getUserNotificationsIgnoresNotificationWithMissingUserId() = runBlocking {
    val malformed = mockk<DocumentSnapshot>()
    every { malformed.id } returns "missing-user"
    every { malformed.getString("userId") } returns null
    every { malformed.getString("type") } returns "CAMPAIGN_UPDATE"
    val notifications = notificationsFromMockListener("target", listOf(malformed))

    assertTrue(notifications.isEmpty())
  }

  @Test
  fun getUserNotificationsIgnoresUnknownNotificationTypes() = runBlocking {
    val malformed = mockk<DocumentSnapshot>()
    every { malformed.id } returns "unknown-type"
    every { malformed.getString("userId") } returns "target"
    every { malformed.getString("type") } returns "UNKNOWN_TYPE"
    val notifications = notificationsFromMockListener("target", listOf(malformed))

    assertTrue(notifications.isEmpty())
  }

  @Test
  fun createsAndRetrievesNotificationsForOnlyTheTargetUser() = runBlocking {
    val firestoreMock = mockk<FirebaseFirestore>()
    val notificationsMock = mockk<CollectionReference>()
    val documentMock = mockk<DocumentReference>()
    every { firestoreMock.collection(NotificationRepositoryFirestore.COLLECTION_PATH) } returns
        notificationsMock
    every { notificationsMock.document("target-notification") } returns documentMock
    every { documentMock.set(any<Map<String, Any?>>()) } returns Tasks.forResult(null)
    val mockRepository = NotificationRepositoryFirestore(firestoreMock)

    val createResult =
        mockRepository.createNotification(
            Notification(
                id = "target-notification",
                userId = "target",
                type = NotificationType.GROUP_INVITATION,
                title = "Invitation",
                message = "You were invited.",
                groupId = "group-1",
            )
        )
    assertTrue(createResult.isSuccess)

    val target = mockk<DocumentSnapshot>()
    every { target.id } returns "target-notification"
    every { target.getString("userId") } returns "target"
    every { target.getString("type") } returns "GROUP_INVITATION"
    every { target.getString("title") } returns "Invitation"
    every { target.getString("message") } returns "You were invited."
    every { target.getString("groupId") } returns "group-1"
    every { target.getDate("createdAt") } returns Date(1000)
    every { target.getBoolean("isRead") } returns false
    val notifications = notificationsFromMockListener("target", listOf(target), firestoreMock)

    assertEquals(listOf("target-notification"), notifications.map { it.id })
    assertEquals(NotificationType.GROUP_INVITATION, notifications.single().type)
    assertFalse(notifications.single().isRead)
  }

  @Test
  fun managesReadStateAndRejectsAnotherUsersNotification() = runBlocking {
    val firestoreMock = mockk<FirebaseFirestore>()
    val notificationsMock = mockk<CollectionReference>()
    val documentMock = mockk<DocumentReference>()
    val snapshotMock = mockk<DocumentSnapshot>()
    every { firestoreMock.collection(NotificationRepositoryFirestore.COLLECTION_PATH) } returns
        notificationsMock
    every { notificationsMock.document("notification-1") } returns documentMock
    every { documentMock.get() } returns Tasks.forResult(snapshotMock)
    every { snapshotMock.exists() } returns true
    every { snapshotMock.getString("userId") } returns "target"
    every { documentMock.update("isRead", true) } returns Tasks.forResult(null)
    every { documentMock.delete() } returns Tasks.forResult(null)
    val mockRepository = NotificationRepositoryFirestore(firestoreMock)

    val wrongOwner = mockRepository.markAsRead("notification-1", "other")
    assertTrue(wrongOwner.isFailure)
    assertEquals("Notification does not belong to user.", wrongOwner.exceptionOrNull()?.message)

    mockRepository.markAsRead("notification-1", "target").getOrThrow()
    mockRepository.deleteNotification("notification-1", "target").getOrThrow()

    io.mockk.verify { documentMock.update("isRead", true) }
    io.mockk.verify { documentMock.delete() }
  }

  @Test
  fun markAllAsReadUpdatesEveryUnreadNotification() = runBlocking {
    val firestoreMock = mockk<FirebaseFirestore>()
    val notificationsMock = mockk<CollectionReference>()
    val userQuery = mockk<Query>()
    val unreadQuery = mockk<Query>()
    val unreadSnapshot = mockk<QuerySnapshot>()
    val batch = mockk<WriteBatch>()
    val first = mockk<DocumentSnapshot>()
    val second = mockk<DocumentSnapshot>()
    val firstRef = mockk<DocumentReference>()
    val secondRef = mockk<DocumentReference>()
    every { firestoreMock.collection(NotificationRepositoryFirestore.COLLECTION_PATH) } returns
        notificationsMock
    every { notificationsMock.whereEqualTo("userId", "target") } returns userQuery
    every { userQuery.whereEqualTo("isRead", false) } returns unreadQuery
    every { unreadQuery.get() } returns Tasks.forResult(unreadSnapshot)
    every { unreadSnapshot.documents } returns listOf(first, second)
    every { first.reference } returns firstRef
    every { second.reference } returns secondRef
    every { firestoreMock.batch() } returns batch
    every { batch.update(firstRef, "isRead", true) } returns batch
    every { batch.update(secondRef, "isRead", true) } returns batch
    every { batch.commit() } returns Tasks.forResult(null)

    val result = NotificationRepositoryFirestore(firestoreMock).markAllAsRead("target")

    assertTrue(result.isSuccess)
    io.mockk.verify { batch.update(firstRef, "isRead", true) }
    io.mockk.verify { batch.update(secondRef, "isRead", true) }
    io.mockk.verify { batch.commit() }
  }

  @Test
  fun rejectsNotificationsWithoutATargetUser() = runBlocking {
    val result =
        repository.createNotification(Notification(title = "Missing target", message = "Message"))

    assertTrue(result.isFailure)
    assertEquals("Notification user id cannot be blank.", result.exceptionOrNull()?.message)
  }
}
