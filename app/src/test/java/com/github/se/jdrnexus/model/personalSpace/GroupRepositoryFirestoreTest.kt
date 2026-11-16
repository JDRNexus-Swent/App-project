// Co-authored-by: Claude Sonnet 5.5
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.model.personalSpace

import android.os.Looper
import com.github.se.jdrnexus.utils.FirebaseEmulatorTestUtils
import com.github.se.jdrnexus.utils.waitFor
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.EventListener
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.WriteBatch
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * Those tests use the Firestore emulator with the project security rules; start it with "firebase
 * emulators:exec --only auth,firestore"
 */
@RunWith(RobolectricTestRunner::class)
class GroupRepositoryFirestoreTest {

  private lateinit var repository: GroupRepository
  private lateinit var firestore: FirebaseFirestore

  companion object {
    // Defines the maximum time to wait for asynchronous Firebase operations in tests
    private const val DEFAULT_TIMEOUT_MS = FirebaseEmulatorTestUtils.DEFAULT_TIMEOUT_MS

    // The game master who creates the groups: the repository of most tests is signed in as them.
    private const val GM_ID = "gm1"
  }

  @Before
  fun setUp() {
    firestore = FirebaseEmulatorTestUtils.signInAs(GM_ID)
    repository = GroupRepositoryFirestore(firestore)
  }

  @After
  fun tearDown() {
    runBlocking {
      FirebaseEmulatorTestUtils.tearDown(firestore, "campaign_groups", "documents", "users")
    }
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
            gameMasterId = GM_ID,
            // A new group has its game master as only participant (security rules).
            participantIds = listOf(GM_ID),
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
    val group =
        CampaignGroup(
            id = groupId,
            name = "Old Name",
            gameMasterId = GM_ID,
            participantIds = listOf(GM_ID),
        )
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

  /** Creating a group also creates its root folder. */
  @Test
  fun testCreateGroup_alsoCreatesItsRootFile() = runBlocking {
    val group =
        CampaignGroup(
            id = UUID.randomUUID().toString(),
            name = "Campaign",
            gameMasterId = "gm1",
            participantIds = listOf("gm1"),
        )

    val result = repository.createGroup(group)

    assertTrue("createGroup should succeed", result.isSuccess)
    val root =
        firestore
            .collection("documents")
            .document(group.id)
            .get()
            .await()
            .toObject(JDRFile::class.java)
    assertEquals(
        JDRFile(
            id = group.id,
            name = "Campaign",
            ownerId = "gm1",
            sharedGroupsIds = listOf(group.id),
            type = DocumentType.GROUP_ROOT,
        ),
        root,
    )
  }

  /** Renaming a group renames its root folder too. */
  @Test
  fun testUpdateGroup_alsoRenamesItsRootFile() = runBlocking {
    val group =
        CampaignGroup(
            id = UUID.randomUUID().toString(),
            name = "Old Name",
            gameMasterId = GM_ID,
            participantIds = listOf(GM_ID),
        )
    repository.createGroup(group)

    val result = repository.updateGroup(group.copy(name = "New Name"))

    assertTrue("updateGroup should succeed", result.isSuccess)
    val root =
        firestore
            .collection("documents")
            .document(group.id)
            .get()
            .await()
            .toObject(JDRFile::class.java)
    assertEquals("New Name", root?.name)
    assertEquals(DocumentType.GROUP_ROOT, root?.type)
  }

  /** If the root folder is missing, the whole update fails and the group keeps its old name. */
  @Test
  fun testUpdateGroup_withoutRootFile_changesNothing() = runBlocking {
    val group =
        CampaignGroup(
            id = UUID.randomUUID().toString(),
            name = "Old Name",
            gameMasterId = GM_ID,
            participantIds = listOf(GM_ID),
        )
    // Written directly, without the root folder that createGroup would have added.
    FirebaseEmulatorTestUtils.writeBypassingRules(firestore, "campaign_groups/${group.id}", group)

    val result = repository.updateGroup(group.copy(name = "New Name"))

    assertTrue("updateGroup should fail without a root folder", result.isFailure)
    assertEquals("Old Name", repository.getGroup(group.id).getOrNull()?.name)
  }

  private class BatchMocks(
      val firestore: FirebaseFirestore,
      val groupDoc: DocumentReference,
      val rootDoc: DocumentReference,
      val userDoc: DocumentReference,
      val batch: WriteBatch,
  )

  private fun buildBatchMocks(groupId: String, gameMasterId: String): BatchMocks {
    val firestoreMock = mockk<FirebaseFirestore>()
    val groupsMock = mockk<CollectionReference>()
    val documentsMock = mockk<CollectionReference>()
    val usersMock = mockk<CollectionReference>()
    val groupDocMock = mockk<DocumentReference>()
    val rootDocMock = mockk<DocumentReference>()
    val userDocMock = mockk<DocumentReference>()
    val batchMock = mockk<WriteBatch>()
    every { firestoreMock.collection("campaign_groups") } returns groupsMock
    every { firestoreMock.collection("documents") } returns documentsMock
    every { firestoreMock.collection("users") } returns usersMock
    every { groupsMock.document(groupId) } returns groupDocMock
    every { documentsMock.document(groupId) } returns rootDocMock
    every { usersMock.document(gameMasterId) } returns userDocMock
    every { firestoreMock.batch() } returns batchMock
    every { batchMock.commit() } returns Tasks.forResult(null)
    return BatchMocks(firestoreMock, groupDocMock, rootDocMock, userDocMock, batchMock)
  }

  /** The group, its root folder and the GM's user document go into one batch, one commit. */
  @Test
  fun testCreateGroup_writesGroupAndRootInOneBatch() = runBlocking {
    val group =
        CampaignGroup(
            id = "g1",
            name = "Campaign",
            gameMasterId = "gm1",
            participantIds = listOf("gm1"),
        )
    val mocks = buildBatchMocks(group.id, group.gameMasterId)
    every { mocks.batch.set(any(), any()) } returns mocks.batch
    every { mocks.batch.set(any(), any(), any<SetOptions>()) } returns mocks.batch

    val result = GroupRepositoryFirestore(mocks.firestore).createGroup(group)

    assertTrue(result.isSuccess)
    verify(exactly = 1) { mocks.batch.set(mocks.groupDoc, group) }
    verify(exactly = 1) {
      mocks.batch.set(
          mocks.rootDoc,
          JDRFile(
              id = "g1",
              name = "Campaign",
              ownerId = "gm1",
              sharedGroupsIds = listOf("g1"),
              type = DocumentType.GROUP_ROOT,
          ),
      )
    }
    verify(exactly = 1) { mocks.batch.set(mocks.userDoc, any(), any<SetOptions>()) }
    verify(exactly = 1) { mocks.batch.commit() }
    confirmVerified(mocks.batch)
  }

  /** The group update and the root rename go into one batch with a single commit. */
  @Test
  fun testUpdateGroup_updatesGroupAndRootInOneBatch() = runBlocking {
    val group = CampaignGroup(id = "g1", name = "New Name", gameMasterId = "gm1")
    val mocks = buildBatchMocks(group.id, group.gameMasterId)
    every { mocks.batch.update(any(), any()) } returns mocks.batch

    val result = GroupRepositoryFirestore(mocks.firestore).updateGroup(group)

    assertTrue(result.isSuccess)
    verify(exactly = 1) { mocks.batch.update(mocks.groupDoc, group.toMap()) }
    verify(exactly = 1) { mocks.batch.update(mocks.rootDoc, mapOf("name" to "New Name")) }
    verify(exactly = 1) { mocks.batch.commit() }
    confirmVerified(mocks.batch)
  }

  /** Tests deleting an existing campaign group. */
  @Test
  fun testDeleteGroup() = runBlocking {
    val groupId = UUID.randomUUID().toString()
    val group =
        CampaignGroup(
            id = groupId,
            name = "To Delete",
            gameMasterId = GM_ID,
            participantIds = listOf(GM_ID),
        )
    repository.createGroup(group)

    val deleteResult = repository.deleteGroup(groupId)
    assertTrue("deleteGroup should succeed", deleteResult.isSuccess)

    val getResult = repository.getGroup(groupId)
    // The group is gone, not merely unreadable.
    assertEquals("CampaignGroup not found in Firestore", getResult.exceptionOrNull()?.message)
  }

  /** Deleting a group removes it from the groupIds of every member, and deletes its root. */
  @Test
  fun testDeleteGroup_removesItFromEveryMembersGroupIds() = runBlocking {
    val groupId = UUID.randomUUID().toString()
    repository.createGroup(
        CampaignGroup(
            id = groupId,
            name = "Party",
            gameMasterId = "gm1",
            participantIds = listOf("gm1"),
        )
    )
    assertEquals(Result.success(Unit), repository.addUserToGroup(groupId, "player1"))

    val result = repository.deleteGroup(groupId)

    assertTrue("deleteGroup should succeed", result.isSuccess)
    for (userId in listOf("gm1", "player1")) {
      assertEquals(
          emptyList<String>(),
          FirebaseEmulatorTestUtils.signInAs(userId)
              .collection("users")
              .document(userId)
              .get()
              .await()
              .get("groupIds"),
      )
    }
    assertFalse(firestore.collection("documents").document(groupId).get().await().exists())
  }

  /** Tests adding and removing users from a campaign group using the specific methods. */
  @Test
  fun testAddAndRemoveUserFromGroup() = runBlocking {
    val groupId = UUID.randomUUID().toString()
    val group =
        CampaignGroup(
            id = groupId,
            name = "Party",
            gameMasterId = GM_ID,
            participantIds = listOf(GM_ID),
        )
    repository.createGroup(group)

    // Add user: the group and the user's own list of groups change together.
    val addResult = repository.addUserToGroup(groupId, "player1")
    assertTrue("addUserToGroup should succeed", addResult.isSuccess)
    assertEquals(listOf(GM_ID, "player1"), repository.getGroup(groupId).getOrNull()?.participantIds)
    assertEquals(
        listOf(groupId),
        FirebaseEmulatorTestUtils.signInAs("player1")
            .collection("users")
            .document("player1")
            .get()
            .await()
            .get("groupIds"),
    )

    // Remove user: both lists lose them, the game master stays.
    val removeResult = repository.removeUserFromGroup(groupId, "player1")
    assertTrue("removeUserFromGroup should succeed", removeResult.isSuccess)
    assertEquals(listOf(GM_ID), repository.getGroup(groupId).getOrNull()?.participantIds)
    assertEquals(
        emptyList<String>(),
        FirebaseEmulatorTestUtils.signInAs("player1")
            .collection("users")
            .document("player1")
            .get()
            .await()
            .get("groupIds"),
    )
  }

  /** Tests real-time updates for a single campaign group. */
  @Test
  fun testGetGroupFlow() = runBlocking {
    val groupId = UUID.randomUUID().toString()
    val group =
        CampaignGroup(
            id = groupId,
            name = "Flow Group",
            gameMasterId = GM_ID,
            participantIds = listOf(GM_ID),
        )
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
            gameMasterId = "gm1",
            participantIds = listOf(userId, "other"),
        )
    val group2 =
        CampaignGroup(
            id = UUID.randomUUID().toString(),
            name = "Group 2",
            gameMasterId = "gm1",
            participantIds = listOf(userId),
        )
    val groupOther =
        CampaignGroup(
            id = UUID.randomUUID().toString(),
            name = "Group 3",
            gameMasterId = "gm1",
            participantIds = listOf("other"),
        )

    // getUserGroups is under test, not the creation: the groups are written as they are stored.
    listOf(group1, group2, groupOther).forEach {
      FirebaseEmulatorTestUtils.writeBypassingRules(firestore, "campaign_groups/${it.id}", it)
    }

    val userGroups =
        GroupRepositoryFirestore(FirebaseEmulatorTestUtils.signInAs(userId))
            .getUserGroups(userId)
            .waitFor { it.size == 2 }

    assertEquals(setOf(group1, group2), userGroups.toSet())
  }

  /** Ensures that an empty list is emitted if a user has no active campaign groups. */
  @Test
  fun testGetUserGroups_Empty() = runBlocking {
    // Another user's group exists: the query must not return it.
    val dummy =
        CampaignGroup(
            id = "dummy",
            name = "dummy",
            gameMasterId = GM_ID,
            participantIds = listOf(GM_ID),
        )
    FirebaseEmulatorTestUtils.writeBypassingRules(firestore, "campaign_groups/${dummy.id}", dummy)

    val emptyUserId = "empty_user_id"
    val userGroups =
        GroupRepositoryFirestore(FirebaseEmulatorTestUtils.signInAs(emptyUserId))
            .getUserGroups(emptyUserId)
            .waitFor { true }
    assertEquals(emptyList<CampaignGroup>(), userGroups)
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
    // The failure is the invalid path, not any other error (e.g. a security rule).
    assertEquals(IllegalArgumentException::class.java, result.exceptionOrNull()?.javaClass)
  }

  /** Verifies that updating a group with an invalid ID returns a failure correctly. */
  @Test
  fun testUpdateGroup_Exception() = runBlocking {
    val result = repository.updateGroup(CampaignGroup(id = ""))
    // The failure is the invalid path, not any other error (e.g. a security rule).
    assertEquals(IllegalArgumentException::class.java, result.exceptionOrNull()?.javaClass)
  }

  /** Verifies that attempting to delete a group with an invalid ID is caught properly. */
  @Test
  fun testDeleteGroup_Exception() = runBlocking {
    val result = repository.deleteGroup("")
    // The failure is the invalid path, not any other error (e.g. a security rule).
    assertEquals(IllegalArgumentException::class.java, result.exceptionOrNull()?.javaClass)
  }

  /** Verifies that attempting to fetch a group with an invalid ID is caught properly. */
  @Test
  fun testGetGroup_Exception() = runBlocking {
    val result = repository.getGroup("")
    // The failure is the invalid path, not any other error (e.g. a security rule).
    assertEquals(IllegalArgumentException::class.java, result.exceptionOrNull()?.javaClass)
  }

  /**
   * Verifies that attempting to add a user to a group with an invalid group ID is caught properly.
   */
  @Test
  fun testAddUserToGroup_Exception() = runBlocking {
    val result = repository.addUserToGroup("", "user1")
    // The failure is the invalid path, not any other error (e.g. a security rule).
    assertEquals(IllegalArgumentException::class.java, result.exceptionOrNull()?.javaClass)
  }

  /**
   * Verifies that attempting to remove a user from a group with an invalid group ID is caught
   * properly.
   */
  @Test
  fun testRemoveUserFromGroup_Exception() = runBlocking {
    val result = repository.removeUserFromGroup("", "user1")
    // The failure is the invalid path, not any other error (e.g. a security rule).
    assertEquals(IllegalArgumentException::class.java, result.exceptionOrNull()?.javaClass)
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
    // A client can no longer write such a document (security rules): it stands for old data.
    FirebaseEmulatorTestUtils.writeBypassingRules(
        firestore,
        "campaign_groups/corrupted_group",
        corruptedData,
    )

    // Listen to the groups. The catch block will log the error and ignore the corrupt group.
    val groups =
        GroupRepositoryFirestore(FirebaseEmulatorTestUtils.signInAs("corrupted_user"))
            .getUserGroups("corrupted_user")
            .waitFor { true }

    assertEquals(emptyList<CampaignGroup>(), groups)
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
            gameMasterId = GM_ID,
            participantIds = listOf(GM_ID, userId),
        )
    val group2Id = UUID.randomUUID().toString()
    val group2 =
        CampaignGroup(
            id = group2Id,
            name = "Secret Campaign",
            gameMasterId = GM_ID,
            participantIds = listOf(GM_ID),
        )
    // Starting state: the user is in group1 only, and their users document says so.
    FirebaseEmulatorTestUtils.writeBypassingRules(firestore, "campaign_groups/${group1.id}", group1)
    FirebaseEmulatorTestUtils.writeBypassingRules(firestore, "campaign_groups/${group2.id}", group2)
    FirebaseEmulatorTestUtils.writeBypassingRules(
        firestore,
        "users/$userId",
        mapOf("groupIds" to listOf(group1.id)),
    )

    val latestList = AtomicReference(emptyList<CampaignGroup>())
    val hasEmitted = AtomicBoolean(false)

    // 2. Start collecting the flow in a background job, as the user whose groups change
    val userRepository = GroupRepositoryFirestore(FirebaseEmulatorTestUtils.signInAs(userId))
    val job =
        launch(Dispatchers.Default) {
          userRepository.getUserGroups(userId).collect {
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

    // 3. Action: the game master adds the user to the second group
    assertEquals(Result.success(Unit), repository.addUserToGroup(group2Id, userId))

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

    // 4. Action: the game master removes the user from the first group
    assertEquals(Result.success(Unit), repository.removeUserFromGroup(group1.id, userId))

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
