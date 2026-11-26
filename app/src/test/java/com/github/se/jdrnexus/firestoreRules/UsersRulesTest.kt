// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.firestoreRules

import com.github.se.jdrnexus.utils.FirebaseEmulatorTestUtils
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Security rules of `users/{userId}` (`{ groupIds }`, the groups a user belongs to), on the
 * Firestore emulator started with the project rules ("firebase emulators:exec --only
 * auth,firestore"). A user reads only their own document, and `groupIds` may only follow the
 * `participantIds` of the groups: one group added when the user is a participant after the write,
 * one removed when they no longer are.
 */
@RunWith(RobolectricTestRunner::class)
class UsersRulesTest {

  companion object {
    private const val GM_ID = "gm"
    private const val PLAYER_ID = "player"
    private const val OUTSIDER_ID = "outsider"
  }

  private val run = UUID.randomUUID().toString()
  // The player's group, in their groupIds.
  private val groupId = "$run-group"
  // Groups that list the player as a participant but are not in their groupIds yet.
  private val newGroupId = "$run-new"
  private val otherNewGroupId = "$run-other-new"
  // A group the player is not a participant of.
  private val foreignGroupId = "$run-foreign"

  // Signed in as the GM; also used to seed data bypassing the rules.
  private lateinit var gm: FirebaseFirestore
  private lateinit var player: FirebaseFirestore

  private fun FirebaseFirestore.user(userId: String) = collection("users").document(userId)

  // The write the app makes (GroupRepositoryFirestore): arrayUnion / arrayRemove with merge.
  private fun added(vararg groupIds: String) = mapOf("groupIds" to FieldValue.arrayUnion(*groupIds))

  private fun removed(vararg groupIds: String) =
      mapOf("groupIds" to FieldValue.arrayRemove(*groupIds))

  private suspend fun writeGroup(id: String, vararg participantIds: String) =
      FirebaseEmulatorTestUtils.writeGroupBypassingRules(gm, id, GM_ID, participantIds.toList())

  @Before
  fun setUp() {
    gm = FirebaseEmulatorTestUtils.signInAs(GM_ID)
    player = FirebaseEmulatorTestUtils.signInAs(PLAYER_ID)
    runBlocking {
      writeGroup(groupId, GM_ID, PLAYER_ID)
      writeGroup(newGroupId, GM_ID, PLAYER_ID)
      writeGroup(otherNewGroupId, GM_ID, PLAYER_ID)
      writeGroup(foreignGroupId, GM_ID)
      FirebaseEmulatorTestUtils.writeUserGroupIdsBypassingRules(gm, GM_ID, groupId)
      FirebaseEmulatorTestUtils.writeUserGroupIdsBypassingRules(gm, PLAYER_ID, groupId)
    }
  }

  @After
  fun tearDown() = runBlocking {
    FirebaseEmulatorTestUtils.tearDown(gm, "campaign_groups", "users")
  }

  // ========================================== read

  @Test
  fun aUserCanReadOnlyTheirOwnDocument() = runBlocking {
    assertEquals(listOf(groupId), player.user(PLAYER_ID).get().await().get("groupIds"))

    FirebaseEmulatorTestUtils.assertPermissionDenied("another user's document") {
      player.user(GM_ID).get().await()
    }
    val signedOut = FirebaseEmulatorTestUtils.signedOutFirestore()
    FirebaseEmulatorTestUtils.assertPermissionDenied("signed out") {
      signedOut.user(PLAYER_ID).get().await()
    }
  }

  // ========================================== add a group

  @Test
  fun aUserCanAddAGroupTheyAreAParticipantOf() = runBlocking {
    player.user(PLAYER_ID).set(added(newGroupId), SetOptions.merge()).await()

    assertEquals(listOf(groupId, newGroupId), FirebaseEmulatorTestUtils.userGroupIds(PLAYER_ID))
  }

  @Test
  fun theFirstGroupCreatesTheDocument() = runBlocking {
    val newcomer = FirebaseEmulatorTestUtils.signInAs(OUTSIDER_ID)
    writeGroup(newGroupId, GM_ID, OUTSIDER_ID)

    newcomer.user(OUTSIDER_ID).set(added(newGroupId), SetOptions.merge()).await()

    assertEquals(listOf(newGroupId), FirebaseEmulatorTestUtils.userGroupIds(OUTSIDER_ID))
  }

  @Test
  fun invalidGroupIdsChangesAreDenied() = runBlocking {
    listOf(
            "a group the user is not a participant of" to added(foreignGroupId),
            "two groups at once" to added(newGroupId, otherNewGroupId),
            "an extra field" to added(newGroupId) + ("nickname" to "Aria"),
            "groupIds not a list" to mapOf("groupIds" to newGroupId),
            "removing a group the user is still in" to removed(groupId),
        )
        .forEach { (case, write) ->
          FirebaseEmulatorTestUtils.assertPermissionDenied(case) {
            player.user(PLAYER_ID).set(write, SetOptions.merge()).await()
          }
        }
    // One more group, but groupId is dropped: the old groups must all stay.
    FirebaseEmulatorTestUtils.assertPermissionDenied("replacing a group") {
      player.user(PLAYER_ID).set(mapOf("groupIds" to listOf(newGroupId, otherNewGroupId))).await()
    }
    // The write itself never reads uid(): signedIn() is the only guard against signed-out clients.
    val signedOut = FirebaseEmulatorTestUtils.signedOutFirestore()
    FirebaseEmulatorTestUtils.assertPermissionDenied("signed out") {
      signedOut.user(PLAYER_ID).set(added(newGroupId), SetOptions.merge()).await()
    }

    assertEquals(listOf(groupId), FirebaseEmulatorTestUtils.userGroupIds(PLAYER_ID))
  }

  @Test
  fun invalidGroupRemovalsAreDenied() = runBlocking {
    // The player left foreignGroupId but is still in groupId: only foreignGroupId may go.
    FirebaseEmulatorTestUtils.writeUserGroupIdsBypassingRules(
        gm,
        PLAYER_ID,
        foreignGroupId,
        groupId,
    )

    FirebaseEmulatorTestUtils.assertPermissionDenied("removing two groups at once") {
      player.user(PLAYER_ID).set(removed(foreignGroupId, groupId), SetOptions.merge()).await()
    }
    // One group less, but groupId is swapped for another one: the kept groups must all be old ones.
    FirebaseEmulatorTestUtils.assertPermissionDenied("replacing a group while removing one") {
      player.user(PLAYER_ID).set(mapOf("groupIds" to listOf(newGroupId))).await()
    }

    assertEquals(
        listOf(foreignGroupId, groupId),
        FirebaseEmulatorTestUtils.userGroupIds(PLAYER_ID),
    )
  }

  // Accepted rule (DATA_ARCHITECTURE.md §12): users/{userId} does not check who writes, because the
  // GM writes the users document of the player they add. Anyone may write it, but only to follow
  // participantIds.
  @Test
  fun anotherUserCanWriteAUsersDocumentOnlyToFollowParticipantIds() = runBlocking {
    player.user(GM_ID).set(added(newGroupId), SetOptions.merge()).await()
    assertEquals(listOf(groupId, newGroupId), FirebaseEmulatorTestUtils.userGroupIds(GM_ID))

    FirebaseEmulatorTestUtils.assertPermissionDenied("a group the user is not a participant of") {
      player.user(OUTSIDER_ID).set(added(groupId), SetOptions.merge()).await()
    }
    assertEquals(null, FirebaseEmulatorTestUtils.userGroupIds(OUTSIDER_ID))
  }

  // ========================================== remove a group

  @Test
  fun aUserCanRemoveAGroupTheyLeftOrThatNoLongerExists() = runBlocking {
    val deletedGroupId = "$run-deleted"
    FirebaseEmulatorTestUtils.writeUserGroupIdsBypassingRules(
        gm,
        PLAYER_ID,
        groupId,
        foreignGroupId,
        deletedGroupId,
    )

    player.user(PLAYER_ID).set(removed(foreignGroupId), SetOptions.merge()).await()
    player.user(PLAYER_ID).set(removed(deletedGroupId), SetOptions.merge()).await()

    assertEquals(listOf(groupId), FirebaseEmulatorTestUtils.userGroupIds(PLAYER_ID))
  }

  @Test
  fun aUsersDocumentCannotBeDeleted() = runBlocking {
    FirebaseEmulatorTestUtils.assertPermissionDenied { player.user(PLAYER_ID).delete().await() }

    assertEquals(listOf(groupId), FirebaseEmulatorTestUtils.userGroupIds(PLAYER_ID))
  }
}
