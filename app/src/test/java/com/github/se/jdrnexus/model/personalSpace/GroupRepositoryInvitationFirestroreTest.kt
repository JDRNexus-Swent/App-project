// Co-authored-by: Claude Sonnet 5.5
package com.github.se.jdrnexus.model.personalSpace

import com.github.se.jdrnexus.utils.FirebaseEmulatorTestUtils
import com.github.se.jdrnexus.utils.waitFor
import com.google.firebase.firestore.FirebaseFirestore
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Invitation part of [GroupRepositoryFirestore], against the Firestore emulator. Start it with
 * "firebase emulators:exec --config firebase.test.json".
 */
@RunWith(RobolectricTestRunner::class)
class GroupRepositoryInvitationsFirestoreTest {

  companion object {
    private const val GM_ID = "gm1"
    private const val PLAYER_ID = "player1"
    private const val OTHER_PLAYER_ID = "player2"
    // Well formed (valid alphabet and length) but never written.
    private const val UNUSED_CODE = "ABCDEFGH"
  }

  private lateinit var firestore: FirebaseFirestore
  private lateinit var repository: GroupRepository

  // Codes whose "redemptions" subcollection must be cleaned (deleting a parent keeps it).
  private val usedCodes = mutableListOf<String>()

  @Before
  fun setUp() {
    firestore = FirebaseEmulatorTestUtils.getFirestore()
    repository = GroupRepositoryFirestore(firestore)
  }

  @After
  fun tearDown() {
    runBlocking {
      val redemptionPaths = usedCodes.map { "invitations/$it/redemptions" }.toTypedArray()
      FirebaseEmulatorTestUtils.tearDown(
          firestore,
          *redemptionPaths,
          "invitations",
          "campaign_groups",
          "documents",
          "users",
      )
    }
  }

  // ==========================================
  // HELPERS
  // ==========================================

  private suspend fun createGroup(): String {
    val id = UUID.randomUUID().toString()
    repository.createGroup(
        CampaignGroup(
            id = id,
            name = "The Sunken Crown",
            gameMasterId = GM_ID,
            participantIds = listOf(GM_ID),
        )
    )
    return id
  }

  /** Creates a group and its invitation code, and registers the code for cleanup. */
  private suspend fun createGroupWithCode(): Pair<String, String> {
    val groupId = createGroup()
    val code = repository.createInvitation(groupId, GM_ID).getOrThrow()
    usedCodes += code
    return groupId to code
  }

  private fun <T> Result<T>.invitationError(): InvitationError? =
      (exceptionOrNull() as? InvitationException)?.error

  private suspend fun storedInviteCode(groupId: String): String? =
      firestore
          .collection("campaign_groups")
          .document(groupId)
          .get()
          .await()
          .getString("inviteCode")

  private suspend fun invitationData(code: String): Map<String, Any>? =
      firestore.collection("invitations").document(code).get().await().data

  private suspend fun invitationCount(): Int =
      firestore.collection("invitations").get().await().size()

  private suspend fun redemptionData(code: String, userId: String): Map<String, Any>? =
      firestore
          .collection("invitations")
          .document(code)
          .collection("redemptions")
          .document(userId)
          .get()
          .await()
          .data

  private suspend fun participantIds(groupId: String): List<String> =
      repository.getGroup(groupId).getOrThrow().participantIds

  private suspend fun userGroupIds(userId: String): Any? =
      firestore.collection("users").document(userId).get().await().get("groupIds")

  // ==========================================
  // createInvitation
  // ==========================================

  @Test
  fun createInvitationWritesTheInvitationAndTheGroupPointer() = runBlocking {
    val groupId = createGroup()

    val result = repository.createInvitation(groupId, GM_ID)

    assertTrue("createInvitation should succeed", result.isSuccess)
    val code = result.getOrThrow()
    usedCodes += code
    assertTrue("code should be valid", InvitationCodes.isValid(code))
    assertEquals(mapOf("groupId" to groupId, "createdBy" to GM_ID), invitationData(code))
    assertEquals(code, storedInviteCode(groupId))
  }

  @Test
  fun createInvitationTwiceReturnsTheSameCodeAndWritesNothingMore() = runBlocking {
    val groupId = createGroup()
    val first = repository.createInvitation(groupId, GM_ID).getOrThrow()
    usedCodes += first

    val second = repository.createInvitation(groupId, GM_ID).getOrThrow()

    assertEquals(first, second)
    assertEquals(1, invitationCount())
  }

  @Test
  fun createInvitationDoesNotChangeTheGroupFields() = runBlocking {
    val groupId = createGroup()
    val before = repository.getGroup(groupId).getOrThrow()

    usedCodes += repository.createInvitation(groupId, GM_ID).getOrThrow()

    assertEquals(before, repository.getGroup(groupId).getOrThrow())
  }

  @Test
  fun createInvitationFailsWithNotGameMasterForAnotherUser() = runBlocking {
    val groupId = createGroup()

    val result = repository.createInvitation(groupId, PLAYER_ID)

    assertEquals(InvitationError.NOT_GAME_MASTER, result.invitationError())
    assertEquals(0, invitationCount())
    assertNull(storedInviteCode(groupId))
  }

  @Test
  fun createInvitationFailsWithNotSignedInForBlankUser() = runBlocking {
    val groupId = createGroup()

    val result = repository.createInvitation(groupId, "")

    assertEquals(InvitationError.NOT_SIGNED_IN, result.invitationError())
    assertEquals(0, invitationCount())
  }

  @Test
  fun createInvitationFailsWithGroupNotFoundForUnknownGroup() = runBlocking {
    val result = repository.createInvitation("unknown_group", GM_ID)

    assertEquals(InvitationError.GROUP_NOT_FOUND, result.invitationError())
    assertEquals(0, invitationCount())
  }

  @Test
  fun createInvitationFailsWithGroupNotFoundForBlankGroupId() = runBlocking {
    val result = repository.createInvitation("", GM_ID)

    assertEquals(InvitationError.GROUP_NOT_FOUND, result.invitationError())
  }

  // ==========================================
  // regenerateInvitation
  // ==========================================

  @Test
  fun regenerateInvitationReplacesTheOldCodeByANewOne() = runBlocking {
    val (groupId, oldCode) = createGroupWithCode()

    val result = repository.regenerateInvitation(groupId, GM_ID)

    assertTrue("regenerateInvitation should succeed", result.isSuccess)
    val newCode = result.getOrThrow()
    usedCodes += newCode
    assertNotEquals(oldCode, newCode)
    assertNull("old invitation must be deleted", invitationData(oldCode))
    assertEquals(mapOf("groupId" to groupId, "createdBy" to GM_ID), invitationData(newCode))
    assertEquals(newCode, storedInviteCode(groupId))
    assertEquals(1, invitationCount())
  }

  @Test
  fun oldCodeStopsWorkingAfterRegenerationAndNewCodeWorks() = runBlocking {
    val (groupId, oldCode) = createGroupWithCode()
    val newCode = repository.regenerateInvitation(groupId, GM_ID).getOrThrow()
    usedCodes += newCode

    val withOld = repository.joinGroupWithCode(oldCode, PLAYER_ID)
    val withNew = repository.joinGroupWithCode(newCode, PLAYER_ID)

    assertEquals(InvitationError.UNKNOWN_CODE, withOld.invitationError())
    assertTrue("the new code should work", withNew.isSuccess)
    assertEquals(listOf(GM_ID, PLAYER_ID), participantIds(groupId))
  }

  @Test
  fun regenerateInvitationWithoutExistingCodeActsLikeCreate() = runBlocking {
    val groupId = createGroup()

    val result = repository.regenerateInvitation(groupId, GM_ID)

    assertTrue(result.isSuccess)
    val code = result.getOrThrow()
    usedCodes += code
    assertEquals(code, storedInviteCode(groupId))
    assertEquals(mapOf("groupId" to groupId, "createdBy" to GM_ID), invitationData(code))
  }

  @Test
  fun regenerateInvitationFailsWithNotGameMasterAndKeepsTheOldCode() = runBlocking {
    val (groupId, oldCode) = createGroupWithCode()

    val result = repository.regenerateInvitation(groupId, PLAYER_ID)

    assertEquals(InvitationError.NOT_GAME_MASTER, result.invitationError())
    assertEquals(oldCode, storedInviteCode(groupId))
    assertEquals(mapOf("groupId" to groupId, "createdBy" to GM_ID), invitationData(oldCode))
    assertEquals(1, invitationCount())
  }

  @Test
  fun regenerateInvitationFailsWithNotSignedInForBlankUser() = runBlocking {
    val (groupId, oldCode) = createGroupWithCode()

    val result = repository.regenerateInvitation(groupId, "")

    assertEquals(InvitationError.NOT_SIGNED_IN, result.invitationError())
    assertEquals(oldCode, storedInviteCode(groupId))
  }

  @Test
  fun regenerateInvitationFailsWithGroupNotFoundForUnknownGroup() = runBlocking {
    val result = repository.regenerateInvitation("unknown_group", GM_ID)

    assertEquals(InvitationError.GROUP_NOT_FOUND, result.invitationError())
    assertEquals(0, invitationCount())
  }

  // ==========================================
  // joinGroupWithCode
  // ==========================================

  @Test
  fun joinGroupWithCodeAddsTheUserEverywhereAndReturnsTheGroup() = runBlocking {
    val (groupId, code) = createGroupWithCode()

    val result = repository.joinGroupWithCode(code, PLAYER_ID)

    assertTrue("joinGroupWithCode should succeed", result.isSuccess)
    assertEquals(groupId, result.getOrThrow().id)
    assertEquals(listOf(GM_ID, PLAYER_ID), result.getOrThrow().participantIds)
    assertEquals(listOf(GM_ID, PLAYER_ID), participantIds(groupId))
    assertEquals(listOf(groupId), userGroupIds(PLAYER_ID))
    assertEquals(mapOf("groupId" to groupId), redemptionData(code, PLAYER_ID))
  }

  @Test
  fun joinedGroupAppearsInGetUserGroups() = runBlocking {
    val (groupId, code) = createGroupWithCode()

    repository.joinGroupWithCode(code, PLAYER_ID)

    val groups =
        repository.getUserGroups(PLAYER_ID).waitFor { list -> list.any { it.id == groupId } }
    assertEquals(listOf(groupId), groups.map { it.id })
  }

  @Test
  fun joinGroupWithCodeIgnoresCaseAndSurroundingSpaces() = runBlocking {
    val (groupId, code) = createGroupWithCode()

    val result = repository.joinGroupWithCode("  ${code.lowercase()} ", PLAYER_ID)

    assertTrue(result.isSuccess)
    assertEquals(listOf(GM_ID, PLAYER_ID), participantIds(groupId))
  }

  @Test
  fun twoUsersJoiningKeepEachOther() = runBlocking {
    val (groupId, code) = createGroupWithCode()

    repository.joinGroupWithCode(code, PLAYER_ID)
    repository.joinGroupWithCode(code, OTHER_PLAYER_ID)

    assertEquals(listOf(GM_ID, PLAYER_ID, OTHER_PLAYER_ID), participantIds(groupId))
    assertEquals(mapOf("groupId" to groupId), redemptionData(code, PLAYER_ID))
    assertEquals(mapOf("groupId" to groupId), redemptionData(code, OTHER_PLAYER_ID))
  }

  @Test
  fun joinGroupWithCodeFailsWithNotSignedInForBlankUser() = runBlocking {
    val (groupId, code) = createGroupWithCode()

    val result = repository.joinGroupWithCode(code, "")

    assertEquals(InvitationError.NOT_SIGNED_IN, result.invitationError())
    assertEquals(listOf(GM_ID), participantIds(groupId))
  }

  @Test
  fun joinGroupWithCodeFailsWithInvalidCodeForMalformedCode() = runBlocking {
    val (groupId, _) = createGroupWithCode()

    for (bad in listOf("", "ABC", "K7M2QXP0", "ABCDEFGHIJ")) {
      val result = repository.joinGroupWithCode(bad, PLAYER_ID)

      assertEquals("code '$bad'", InvitationError.INVALID_CODE, result.invitationError())
    }
    assertEquals(listOf(GM_ID), participantIds(groupId))
    assertNull(userGroupIds(PLAYER_ID))
  }

  @Test
  fun joinGroupWithCodeFailsWithUnknownCodeForUnusedCode() = runBlocking {
    val (groupId, _) = createGroupWithCode()

    val result = repository.joinGroupWithCode(UNUSED_CODE, PLAYER_ID)

    assertEquals(InvitationError.UNKNOWN_CODE, result.invitationError())
    assertEquals(listOf(GM_ID), participantIds(groupId))
    assertNull(userGroupIds(PLAYER_ID))
  }

  @Test
  fun joinGroupWithCodeFailsWithAlreadyMemberForTheGameMaster() = runBlocking {
    val (groupId, code) = createGroupWithCode()

    val result = repository.joinGroupWithCode(code, GM_ID)

    assertEquals(InvitationError.ALREADY_MEMBER, result.invitationError())
    assertEquals(listOf(GM_ID), participantIds(groupId))
    assertNull("no redemption must be written", redemptionData(code, GM_ID))
  }

  @Test
  fun joinGroupWithCodeFailsWithAlreadyMemberOnSecondJoin() = runBlocking {
    val (groupId, code) = createGroupWithCode()
    repository.joinGroupWithCode(code, PLAYER_ID)

    val result = repository.joinGroupWithCode(code, PLAYER_ID)

    assertEquals(InvitationError.ALREADY_MEMBER, result.invitationError())
    assertEquals(listOf(GM_ID, PLAYER_ID), participantIds(groupId))
    assertEquals(listOf(groupId), userGroupIds(PLAYER_ID))
  }

  @Test
  fun joinGroupWithCodeFailsWithGroupNotFoundAndWritesNothingWhenGroupWasDeleted() = runBlocking {
    val (groupId, code) = createGroupWithCode()
    // Deleted directly: deleteGroup does not touch the invitation either.
    firestore.collection("campaign_groups").document(groupId).delete().await()

    val result = repository.joinGroupWithCode(code, PLAYER_ID)

    assertEquals(InvitationError.GROUP_NOT_FOUND, result.invitationError())
    assertNull("no redemption must be written", redemptionData(code, PLAYER_ID))
    assertFalse(firestore.collection("users").document(PLAYER_ID).get().await().exists())
  }
}
