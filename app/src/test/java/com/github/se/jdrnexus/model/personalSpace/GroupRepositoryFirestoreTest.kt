// Co-authored-by: AI Agent
package com.github.se.jdrnexus.model.personalSpace

import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.EventListener
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import io.mockk.every
import io.mockk.mockk
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * Those test use firebase emulator WITH TEST SETTING, make sure to activate it with "firebase
 * emulators:exec --config firebase.test.json"
 */
@RunWith(RobolectricTestRunner::class)
class GroupRepositoryFirestoreTest {

  private lateinit var repository: GroupRepository
  private lateinit var firestore: FirebaseFirestore

  companion object {
    // Defines the maximum time to wait for asynchronous Firebase operations in tests
    private const val DEFAULT_TIMEOUT_MS = 5000L
  }

  @Before
  fun setUp() {
    if (FirebaseApp.getApps(ApplicationProvider.getApplicationContext()).isEmpty()) {
      FirebaseApp.initializeApp(ApplicationProvider.getApplicationContext())
    }
    firestore = FirebaseFirestore.getInstance()
    try {
      firestore.useEmulator("127.0.0.1", 8080)
    } catch (e: IllegalStateException) {
      // If Firestore is already configured for production, its host won't be local.
      // We explicitly throw an exception here to avoid wiping out the production database
      // if someone runs tests without properly configuring the emulator instance first.
      if (
          !firestore.firestoreSettings.host.contains("127.0.0.1") &&
              !firestore.firestoreSettings.host.contains("10.0.2.2")
      ) {
        throw IllegalStateException(
            "Firestore is not using the emulator! Aborting to prevent data loss.",
            e,
        )
      }
    }
    repository = GroupRepositoryFirestore(firestore)
  }

  @After
  fun tearDown() {
    runBlocking {
      // Re-enable network in case it was disabled during tests
      try {
        firestore.enableNetwork().await()
      } catch (e: Exception) {
        // ignore
      }

      // Clean up the database for the next test to ensure a clean state
      try {
        val snapshot = firestore.collection("campaign_groups").get().await()
        for (doc in snapshot.documents) {
          doc.reference.delete().await()
        }
      } catch (e: Exception) {
        // ignore
      }
    }
  }

  /**
   * Helper function to collect a Flow in a background coroutine while actively pumping the
   * Robolectric MainLooper. This is required because Firebase SDK callbacks (including snapshot
   * listeners) dispatch to the Android Main thread, which gets blocked in typical runBlocking
   * tests.
   *
   * It uses thread-safe Atomic properties to guarantee memory visibility across Dispatchers.
   */
  private suspend fun <T> Flow<T>.waitFor(
      timeoutMs: Long = DEFAULT_TIMEOUT_MS,
      condition: (T) -> Boolean,
  ): T {
    // use of atomic value allows the real storage in memory (not in cache)
    val latestValue = AtomicReference<T?>(null)
    val hasValue = AtomicBoolean(false)
    val isReady = AtomicBoolean(false)
    val errorRef = AtomicReference<Throwable?>(null) // Capture any exceptions thrown by Flow

    val job =
        CoroutineScope(Dispatchers.Default).launch {
          try {
            this@waitFor.collect {
              latestValue.set(it)
              hasValue.set(true)
              if (condition(it)) {
                isReady.set(true)
              }
            }
          } catch (e: Throwable) {
            errorRef.set(e)
          }
        }

    val startTime = System.currentTimeMillis()
    while (System.currentTimeMillis() - startTime < timeoutMs) {
      // Pump Robolectric's main looper so Firebase callbacks can run!
      shadowOf(Looper.getMainLooper()).idle()

      // If the flow threw an error (like PERMISSION_DENIED), rethrow it immediately
      val flowError = errorRef.get()
      if (flowError != null) {
        job.cancel()
        throw flowError
      }

      if (isReady.get()) {
        job.cancel()
        @Suppress("UNCHECKED_CAST")
        return latestValue.get() as T
      }
      delay(50)
    }
    job.cancel()
    throw AssertionError(
        "Timeout waiting for condition. Last value emitted: ${latestValue.get()}, emitted at all: ${hasValue.get()}"
    )
  }

  /** Tests the creation of a new uid and verifies that it not empty and unique */
  @Test
  fun testGetNewUid() {
    val uid1 = repository.getNewGroupId()
    val uid2 = repository.getNewGroupId()

    assertTrue("Generated UID should not be empty", uid1.isNotEmpty())
    assertNotEquals("Generated UIDs should be unique", uid1, uid2)
  }

  /** Tests the creation of a new group and verifies that it can be retrieved successfully. */
  @Test
  fun testCreateAndGetGroup() = runBlocking {
    val groupId = UUID.randomUUID().toString()
    val group =
        CampaignGroup(
            id = groupId,
            name = "Test Campaign",
            description = "Epic Adventure",
            gameMasterId = "gm123",
            participantIds = listOf("player1", "player2"),
        )

    val createResult = repository.createGroup(group)
    assertTrue("createGroup should succeed", createResult.isSuccess)

    val getResult = repository.getGroup(groupId)
    assertTrue("getGroup should succeed", getResult.isSuccess)
    assertEquals("Fetched group should match created group", group, getResult.getOrNull())
  }

  /** Verifies that retrieving a non-existent group fails correctly. */
  @Test
  fun testGetGroup_NotFound() = runBlocking {
    val result = repository.getGroup("non_existent_group")
    assertTrue("getGroup should fail when group doesn't exist", result.isFailure)
    assertEquals("CampaignGroup not found in Firestore", result.exceptionOrNull()?.message)
  }

  /** Tests updating an existing group's metadata. */
  @Test
  fun testUpdateGroup() = runBlocking {
    val groupId = UUID.randomUUID().toString()
    val group = CampaignGroup(id = groupId, name = "Old Name", gameMasterId = "gm1")
    repository.createGroup(group)

    val updatedGroup = group.copy(name = "New Name", description = "New Desc")
    val updateResult = repository.updateGroup(updatedGroup)
    assertTrue("updateGroup should succeed", updateResult.isSuccess)

    val getResult = repository.getGroup(groupId)
    assertEquals("Group name should be updated", "New Name", getResult.getOrNull()?.name)
    assertEquals(
        "Group description should be updated",
        "New Desc",
        getResult.getOrNull()?.description,
    )
  }

  /** Tests deleting an existing campaign group. */
  @Test
  fun testDeleteGroup() = runBlocking {
    val groupId = UUID.randomUUID().toString()
    val group = CampaignGroup(id = groupId, name = "To Delete")
    repository.createGroup(group)

    val deleteResult = repository.deleteGroup(groupId)
    assertTrue("deleteGroup should succeed", deleteResult.isSuccess)

    val getResult = repository.getGroup(groupId)
    assertTrue("getGroup should fail after deletion", getResult.isFailure)
  }

  /** Tests adding and removing users from a campaign group using the specific methods. */
  @Test
  fun testAddAndRemoveUserFromGroup() = runBlocking {
    val groupId = UUID.randomUUID().toString()
    val group = CampaignGroup(id = groupId, name = "Party", participantIds = listOf("player1"))
    repository.createGroup(group)

    // Add user
    val addResult = repository.addUserToGroup(groupId, "player2")
    assertTrue("addUserToGroup should succeed", addResult.isSuccess)

    var fetchedGroup = repository.getGroup(groupId).getOrNull()
    assertTrue(
        "player2 should be in participantIds",
        fetchedGroup?.participantIds?.contains("player2") == true,
    )
    assertTrue(
        "player1 should still be in participantIds",
        fetchedGroup?.participantIds?.contains("player1") == true,
    )
    assertEquals("Should have exactly 2 participants", 2, fetchedGroup?.participantIds?.size)

    // Remove user
    val removeResult = repository.removeUserFromGroup(groupId, "player1")
    assertTrue("removeUserFromGroup should succeed", removeResult.isSuccess)

    fetchedGroup = repository.getGroup(groupId).getOrNull()
    assertTrue(
        "player1 should be removed",
        fetchedGroup?.participantIds?.contains("player1") == false,
    )
    assertTrue("player2 should remain", fetchedGroup?.participantIds?.contains("player2") == true)
    assertEquals("Should have exactly 1 participant", 1, fetchedGroup?.participantIds?.size)
  }

  /** Tests real-time updates for a single campaign group. */
  @Test
  fun testGetGroupFlow() = runBlocking {
    val groupId = UUID.randomUUID().toString()
    val group = CampaignGroup(id = groupId, name = "Flow Group")
    repository.createGroup(group)

    val emittedGroup = repository.getGroupFlow(groupId).waitFor { it != null }
    assertEquals("Flow should emit the created group", group, emittedGroup)
  }

  /** Tests real-time synchronization of all groups a user is participating in. */
  @Test
  fun testGetUserGroups() = runBlocking {
    val userId = "user123"

    val group1 =
        CampaignGroup(
            id = UUID.randomUUID().toString(),
            name = "Group 1",
            participantIds = listOf(userId, "other"),
        )
    val group2 =
        CampaignGroup(
            id = UUID.randomUUID().toString(),
            name = "Group 2",
            participantIds = listOf(userId),
        )
    val groupOther =
        CampaignGroup(
            id = UUID.randomUUID().toString(),
            name = "Group 3",
            participantIds = listOf("other"),
        )

    repository.createGroup(group1)
    repository.createGroup(group2)
    repository.createGroup(groupOther)

    val userGroups = repository.getUserGroups(userId).waitFor { it.size == 2 }

    assertEquals("Should retrieve exactly 2 groups for the user", 2, userGroups.size)
    assertTrue(userGroups.contains(group1))
    assertTrue(userGroups.contains(group2))
  }

  /** Ensures that an empty list is emitted if a user has no active campaign groups. */
  @Test
  fun testGetUserGroups_Empty() = runBlocking {
    // Insert a dummy to avoid Null value error in Firestore Emulator rules on completely empty
    // collections
    repository.createGroup(CampaignGroup(id = "dummy", name = "dummy"))

    val emptyUserId = "empty_user_id"
    val userGroups = repository.getUserGroups(emptyUserId).waitFor { it.isEmpty() }
    assertTrue("Should retrieve empty list when user has no groups", userGroups.isEmpty())
  }

  // ==========================================
  // EXCEPTION & ERROR HANDLING FOR GROUPS
  // ==========================================

  /**
   * Verifies that creating a group with invalid arguments (e.g. empty ID) is correctly caught and
   * returned as a failure, avoiding a hard crash.
   */
  @Test
  fun testCreateGroup_Exception() = runBlocking {
    // Empty ID throws IllegalArgumentException in Firebase
    val result = repository.createGroup(CampaignGroup(id = ""))
    assertTrue("createGroup should fail with empty ID", result.isFailure)
  }

  /** Verifies that updating a group with an invalid ID returns a failure correctly. */
  @Test
  fun testUpdateGroup_Exception() = runBlocking {
    val result = repository.updateGroup(CampaignGroup(id = ""))
    assertTrue("updateGroup should fail with empty ID", result.isFailure)
  }

  /** Verifies that attempting to delete a group with an invalid ID is caught properly. */
  @Test
  fun testDeleteGroup_Exception() = runBlocking {
    val result = repository.deleteGroup("")
    assertTrue("deleteGroup should fail with empty ID", result.isFailure)
  }

  /** Verifies that attempting to fetch a group with an invalid ID is caught properly. */
  @Test
  fun testGetGroup_Exception() = runBlocking {
    val result = repository.getGroup("")
    assertTrue("getGroup should fail with empty ID", result.isFailure)
  }

  /**
   * Verifies that attempting to add a user to a group with an invalid group ID is caught properly.
   */
  @Test
  fun testAddUserToGroup_Exception() = runBlocking {
    val result = repository.addUserToGroup("", "user1")
    assertTrue("addUserToGroup should fail with empty group ID", result.isFailure)
  }

  /**
   * Verifies that attempting to remove a user from a group with an invalid group ID is caught
   * properly.
   */
  @Test
  fun testRemoveUserFromGroup_Exception() = runBlocking {
    val result = repository.removeUserFromGroup("", "user1")
    assertTrue("removeUserFromGroup should fail with empty group ID", result.isFailure)
  }

  /**
   * Tests that if a campaign group is corrupted in Firestore, the application does not crash. The
   * corrupt document is simply ignored in the emitted list.
   */
  @Test
  fun testGroupDeserializationError_IsCaught() = runBlocking {
    val corruptedData =
        hashMapOf(
            "name" to mapOf("invalid" to "type"), // Name should be a String, not a Map
            "participantIds" to listOf("corrupted_user"),
        )
    firestore.collection("campaign_groups").document("corrupted_group").set(corruptedData).await()

    // Listen to the groups. The catch block will log the error and ignore the corrupt group.
    val groups = repository.getUserGroups("corrupted_user").waitFor { true }

    assertTrue(
        "List should be empty due to parsing failure of the corrupt group",
        groups.isEmpty(),
    )
  }

  /**
   * Tests that single-group flow listeners correctly close the Flow and propagate Permission Denied
   * errors.
   */
  @Test
  fun testGroupFlow_PermissionDenied_WithMockK() = runBlocking {
    val firestoreMock = mockk<FirebaseFirestore>()
    val collectionMock = mockk<CollectionReference>()
    val documentMock = mockk<com.google.firebase.firestore.DocumentReference>()
    val registrationMock = mockk<ListenerRegistration>(relaxed = true)

    // Set up the default routing for the campaign_groups collection
    every { firestoreMock.collection("campaign_groups") } returns collectionMock
    every { collectionMock.document("access_denied_test") } returns documentMock

    // Intercept 'addSnapshotListener' and instantly trigger a PERMISSION_DENIED error
    every { documentMock.addSnapshotListener(any()) } answers
        {
          val listener = firstArg<EventListener<com.google.firebase.firestore.DocumentSnapshot>>()
          listener.onEvent(
              null, // No snapshot
              FirebaseFirestoreException(
                  "Simulated Permission Denied",
                  FirebaseFirestoreException.Code.PERMISSION_DENIED,
              ),
          )
          registrationMock
        }

    val mockRepository = GroupRepositoryFirestore(firestoreMock)

    try {
      mockRepository.getGroupFlow("access_denied_test").collect {}
      fail("Should have thrown an exception")
    } catch (e: Exception) {
      assertTrue(
          "Should throw permission denied",
          e.message?.contains("Simulated Permission Denied") == true,
      )
    }
  }

  /**
   * Tests that list-based snapshot listeners for groups correctly close the Flow and propagate
   * Permission Denied errors. We use MockK here because Firestore security rules cannot securely
   * target specific list queries without breaking normal behavior.
   */
  @Test
  fun testGroupListListeners_PermissionDenied_WithMockK() = runBlocking {
    val firestoreMock = mockk<FirebaseFirestore>()
    val collectionMock = mockk<CollectionReference>()
    val queryMock = mockk<Query>()
    val registrationMock = mockk<ListenerRegistration>(relaxed = true)

    // Set up the default routing for the campaign_groups collection
    every { firestoreMock.collection("campaign_groups") } returns collectionMock

    // Mocking the 'getUserGroups' query chain
    every { collectionMock.whereArrayContains("participantIds", "error_user_test") } returns
        queryMock

    // Intercept 'addSnapshotListener' and instantly trigger a PERMISSION_DENIED error
    every { queryMock.addSnapshotListener(any()) } answers
        {
          val listener = firstArg<EventListener<QuerySnapshot>>()
          listener.onEvent(
              null, // No snapshot
              FirebaseFirestoreException(
                  "Simulated Permission Denied",
                  FirebaseFirestoreException.Code.PERMISSION_DENIED,
              ),
          )
          registrationMock
        }

    // Initialize a separate repository instance injected with our mock
    val mockRepository = GroupRepositoryFirestore(firestoreMock)

    // Assert that getUserGroups correctly catches and propagates the error
    try {
      mockRepository.getUserGroups("error_user_test").collect {}
      fail("getUserGroups should have thrown an exception")
    } catch (e: Exception) {
      assertTrue(e.message?.contains("Simulated Permission Denied") == true)
    }
  }

  /**
   * Tests the reactivity of the user groups list. If a user is added to or removed from a campaign
   * group dynamically, the flow should instantly emit the updated list.
   */
  @Test
  fun testGetUserGroups_MembershipUpdates() = runBlocking {
    val userId = "dynamic_user"
    val group1 =
        CampaignGroup(
            id = UUID.randomUUID().toString(),
            name = "Initial Campaign",
            participantIds = listOf(userId),
        )
    repository.createGroup(group1)

    val group2Id = UUID.randomUUID().toString()
    val group2 =
        CampaignGroup(
            id = group2Id,
            name = "Secret Campaign",
            participantIds = listOf("game_master_only"),
        )
    repository.createGroup(group2)

    val latestList = AtomicReference(emptyList<CampaignGroup>())
    val hasEmitted = AtomicBoolean(false)

    // 2. Start collecting the flow in a background job
    val job =
        launch(Dispatchers.Default) {
          repository.getUserGroups(userId).collect {
            latestList.set(it)
            hasEmitted.set(true)
          }
        }

    // Wait for the initial emission (User should only see group1)
    var startTime = System.currentTimeMillis()
    while (
        (!hasEmitted.get() || latestList.get().size != 1) &&
            System.currentTimeMillis() - startTime < DEFAULT_TIMEOUT_MS
    ) {
      shadowOf(Looper.getMainLooper()).idle()
      delay(50)
    }
    assertEquals("List should initially contain 1 group", 1, latestList.get().size)
    assertEquals("The group should be group1", group1.id, latestList.get().first().id)

    // 3. Action: Add the user to the second group
    repository.addUserToGroup(group2Id, userId)

    // Wait for the flow to react and emit the new list of size 2
    startTime = System.currentTimeMillis()
    while (
        latestList.get().size != 2 && System.currentTimeMillis() - startTime < DEFAULT_TIMEOUT_MS
    ) {
      shadowOf(Looper.getMainLooper()).idle()
      delay(50)
    }
    assertEquals("List should update to contain 2 groups", 2, latestList.get().size)
    assertTrue("Group 2 should now be present", latestList.get().any { it.id == group2Id })

    // 4. Action: Remove the user from the first group
    repository.removeUserFromGroup(group1.id, userId)

    // Wait for the flow to react and emit the list of size 1 (only group2 remaining)
    startTime = System.currentTimeMillis()
    while (
        (latestList.get().size != 1 || latestList.get().first().id != group2Id) &&
            System.currentTimeMillis() - startTime < DEFAULT_TIMEOUT_MS
    ) {
      shadowOf(Looper.getMainLooper()).idle()
      delay(50)
    }
    assertEquals("List should drop to 1 group after removal", 1, latestList.get().size)
    assertEquals("The remaining group should be group2", group2Id, latestList.get().first().id)

    // Cleanup
    job.cancel()
  }
}
