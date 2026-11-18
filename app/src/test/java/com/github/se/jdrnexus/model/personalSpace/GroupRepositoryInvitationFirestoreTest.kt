// Co-authored-by: Claude Sonnet 5.5
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.model.personalSpace

import com.github.se.jdrnexus.utils.FirebaseEmulatorTestUtils
import com.github.se.jdrnexus.utils.waitFor
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import io.mockk.every
import io.mockk.mockk
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Invitation part of [GroupRepositoryFirestore], against the Firestore emulator. Start it with
 * "firebase emulators:exec --only auth,firestore".
 */
@RunWith(RobolectricTestRunner::class)
class GroupRepositoryInvitationFirestoreTest {

  companion object {
    private const val GM_ID = "gm1"
    private const val PLAYER_ID = "player1"
    private const val OTHER_PLAYER_ID = "player2"
    // Well formed (valid alphabet and length) but never written.
    private const val UNUSED_CODE = "ABCDEFGH"
  }

  private lateinit var firestore: FirebaseFirestore
  // Signed in as the game master.
  private lateinit var repository: GroupRepository
  // Signed in as PLAYER_ID: the user who joins with a code.
  private lateinit var playerRepository: GroupRepository

  // Codes whose "redemptions" subcollection must be cleaned (deleting a parent keeps it).
  private val usedCodes = mutableListOf<String>()

  @Before
  fun setUp() {
    firestore = FirebaseEmulatorTestUtils.signInAs(GM_ID)
    repository = GroupRepositoryFirestore(firestore)
    playerRepository = GroupRepositoryFirestore(FirebaseEmulatorTestUtils.signInAs(PLAYER_ID))
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

  // No client may list invitations, so the check bypasses the rules.
  private suspend fun invitationCount(): Int =
      FirebaseEmulatorTestUtils.documentIdsBypassingRules(firestore, "invitations").size

  // Users who redeemed [code]. No client may read redemptions, so the check bypasses the rules.
  // Their content ({groupId}) is checked by the rules: a wrong one makes the whole join fail.
  private suspend fun redeemers(code: String): List<String> =
      FirebaseEmulatorTestUtils.documentIdsBypassingRules(
          firestore,
          "invitations/$code/redemptions",
      )

  private suspend fun participantIds(groupId: String): List<String> =
      repository.getGroup(groupId).getOrThrow().participantIds

  /** A repository whose first read of an invitation fails with [error]. No emulator involved. */
  private fun repositoryFailingOnInvitationRead(error: Exception): GroupRepository {
    val firestoreMock = mockk<FirebaseFirestore>()
    val invitationsMock = mockk<CollectionReference>()
    val invitationDocMock = mockk<DocumentReference>()
    every { firestoreMock.collection("invitations") } returns invitationsMock
    every { invitationsMock.document(any()) } returns invitationDocMock
    every { invitationDocMock.get() } returns Tasks.forException(error)
    every { firestoreMock.collection("campaign_groups") } returns mockk(relaxed = true)
    return GroupRepositoryFirestore(firestoreMock)
  }

  /** A repository whose first read of a group fails with [error]. No emulator involved. */
  private fun repositoryFailingOnGroupRead(error: Exception): GroupRepository {
    val firestoreMock = mockk<FirebaseFirestore>()
    val groupsMock = mockk<CollectionReference>()
    val groupDocMock = mockk<DocumentReference>()
    every { firestoreMock.collection("campaign_groups") } returns groupsMock
    every { groupsMock.document(any()) } returns groupDocMock
    every { groupDocMock.get() } returns Tasks.forException(error)
    return GroupRepositoryFirestore(firestoreMock)
  }

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

    val result = playerRepository.createInvitation(groupId, PLAYER_ID)

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

    val withOld = playerRepository.joinGroupWithCode(oldCode, PLAYER_ID)
    val withNew = playerRepository.joinGroupWithCode(newCode, PLAYER_ID)

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

    val result = playerRepository.regenerateInvitation(groupId, PLAYER_ID)

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

  @Test
  fun regenerateInvitationFailsWithGroupNotFoundForBlankGroupId() = runBlocking {
    val result = repository.regenerateInvitation("", GM_ID)

    assertEquals(InvitationError.GROUP_NOT_FOUND, result.invitationError())
  }

  // ==========================================
  // joinGroupWithCode
  // ==========================================

  @Test
  fun joinGroupWithCodeAddsTheUserEverywhereAndReturnsTheGroup() = runBlocking {
    val (groupId, code) = createGroupWithCode()

    val result = playerRepository.joinGroupWithCode(code, PLAYER_ID)

    assertTrue("joinGroupWithCode should succeed", result.isSuccess)
    assertEquals(groupId, result.getOrThrow().id)
    assertEquals(listOf(GM_ID, PLAYER_ID), result.getOrThrow().participantIds)
    assertEquals(listOf(GM_ID, PLAYER_ID), participantIds(groupId))
    assertEquals(listOf(groupId), FirebaseEmulatorTestUtils.userGroupIds(PLAYER_ID))
    assertEquals(listOf(PLAYER_ID), redeemers(code))
  }

  @Test
  fun joinedGroupAppearsInGetUserGroups() = runBlocking {
    val (groupId, code) = createGroupWithCode()

    assertTrue(playerRepository.joinGroupWithCode(code, PLAYER_ID).isSuccess)

    val groups =
        playerRepository.getUserGroups(PLAYER_ID).waitFor { list -> list.any { it.id == groupId } }
    assertEquals(listOf(groupId), groups.map { it.id })
  }

  @Test
  fun joinGroupWithCodeIgnoresCaseAndSurroundingSpaces() = runBlocking {
    val (groupId, code) = createGroupWithCode()

    val result = playerRepository.joinGroupWithCode("  ${code.lowercase()} ", PLAYER_ID)

    assertTrue(result.isSuccess)
    assertEquals(listOf(GM_ID, PLAYER_ID), participantIds(groupId))
  }

  @Test
  fun twoUsersJoiningKeepEachOther() = runBlocking {
    val (groupId, code) = createGroupWithCode()

    playerRepository.joinGroupWithCode(code, PLAYER_ID)
    GroupRepositoryFirestore(FirebaseEmulatorTestUtils.signInAs(OTHER_PLAYER_ID))
        .joinGroupWithCode(code, OTHER_PLAYER_ID)

    assertEquals(listOf(GM_ID, PLAYER_ID, OTHER_PLAYER_ID), participantIds(groupId))
    assertEquals(listOf(PLAYER_ID, OTHER_PLAYER_ID), redeemers(code))
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
      val result = playerRepository.joinGroupWithCode(bad, PLAYER_ID)

      assertEquals("code '$bad'", InvitationError.INVALID_CODE, result.invitationError())
    }
    assertEquals(listOf(GM_ID), participantIds(groupId))
    assertNull(FirebaseEmulatorTestUtils.userGroupIds(PLAYER_ID))
  }

  @Test
  fun joinGroupWithCodeFailsWithUnknownCodeForUnusedCode() = runBlocking {
    val (groupId, _) = createGroupWithCode()

    val result = playerRepository.joinGroupWithCode(UNUSED_CODE, PLAYER_ID)

    assertEquals(InvitationError.UNKNOWN_CODE, result.invitationError())
    assertEquals(listOf(GM_ID), participantIds(groupId))
    assertNull(FirebaseEmulatorTestUtils.userGroupIds(PLAYER_ID))
  }

  @Test
  fun joinGroupWithCodeFailsWithAlreadyMemberForTheGameMaster() = runBlocking {
    val (groupId, code) = createGroupWithCode()

    val result = repository.joinGroupWithCode(code, GM_ID)

    assertEquals(InvitationError.ALREADY_MEMBER, result.invitationError())
    assertEquals(listOf(GM_ID), participantIds(groupId))
    assertEquals("no redemption must be written", emptyList<String>(), redeemers(code))
  }

  @Test
  fun joinGroupWithCodeFailsWithAlreadyMemberOnSecondJoin() = runBlocking {
    val (groupId, code) = createGroupWithCode()
    playerRepository.joinGroupWithCode(code, PLAYER_ID)

    val result = playerRepository.joinGroupWithCode(code, PLAYER_ID)

    assertEquals(InvitationError.ALREADY_MEMBER, result.invitationError())
    assertEquals(listOf(GM_ID, PLAYER_ID), participantIds(groupId))
    assertEquals(listOf(groupId), FirebaseEmulatorTestUtils.userGroupIds(PLAYER_ID))
  }

  @Test
  fun joinGroupWithCodeFailsWithUnknownCodeAndWritesNothingWhenGroupWasDeleted() = runBlocking {
    val (groupId, code) = createGroupWithCode()
    assertEquals(Result.success(Unit), repository.deleteGroup(groupId))

    val result = playerRepository.joinGroupWithCode(code, PLAYER_ID)

    // deleteGroup deleted the invitation with the group.
    assertNull(invitationData(code))
    assertEquals(InvitationError.UNKNOWN_CODE, result.invitationError())
    assertEquals("no redemption must be written", emptyList<String>(), redeemers(code))
    assertNull(FirebaseEmulatorTestUtils.userGroupIds(PLAYER_ID))
  }

  @Test
  fun joinGroupWithCodeFailsWithUnknownCodeWhenInvitationHasNoGroupId() = runBlocking {
    val groupId = createGroup()
    // A malformed invitation (no groupId): the rules refuse it, so it is written bypassing them.
    FirebaseEmulatorTestUtils.writeBypassingRules(
        firestore,
        "invitations/$UNUSED_CODE",
        mapOf("createdBy" to GM_ID),
    )
    usedCodes += UNUSED_CODE

    val result = playerRepository.joinGroupWithCode(UNUSED_CODE, PLAYER_ID)

    assertEquals(InvitationError.UNKNOWN_CODE, result.invitationError())
    assertEquals(listOf(GM_ID), participantIds(groupId))
    assertNull(FirebaseEmulatorTestUtils.userGroupIds(PLAYER_ID))
  }

  // ==========================================
  // ERROR MAPPING (mockk: the emulator cannot produce each of these errors on demand)
  // ==========================================

  @Test
  fun joinMapsPermissionDeniedToTheConfiguredError() = runBlocking {
    val repo =
        repositoryFailingOnInvitationRead(
            FirebaseFirestoreException("denied", FirebaseFirestoreException.Code.PERMISSION_DENIED)
        )

    val result = repo.joinGroupWithCode(UNUSED_CODE, PLAYER_ID)

    // Must match the onPermissionDenied value written in joinGroupWithCode.
    assertEquals(InvitationError.NETWORK, result.invitationError())
  }

  @Test
  fun joinMapsNotFoundToGroupNotFound() = runBlocking {
    val repo =
        repositoryFailingOnInvitationRead(
            FirebaseFirestoreException("missing", FirebaseFirestoreException.Code.NOT_FOUND)
        )

    val result = repo.joinGroupWithCode(UNUSED_CODE, PLAYER_ID)

    assertEquals(InvitationError.GROUP_NOT_FOUND, result.invitationError())
  }

  @Test
  fun joinMapsOtherFirestoreCodesToNetwork() = runBlocking {
    val repo =
        repositoryFailingOnInvitationRead(
            FirebaseFirestoreException("offline", FirebaseFirestoreException.Code.UNAVAILABLE)
        )

    val result = repo.joinGroupWithCode(UNUSED_CODE, PLAYER_ID)

    assertEquals(InvitationError.NETWORK, result.invitationError())
  }

  @Test
  fun joinMapsUnexpectedExceptionToNetwork() = runBlocking {
    val repo = repositoryFailingOnInvitationRead(IllegalStateException("Kaboom"))

    val result = repo.joinGroupWithCode(UNUSED_CODE, PLAYER_ID)

    assertEquals(InvitationError.NETWORK, result.invitationError())
  }

  @Test
  fun joinDoesNotSwallowCancellation() = runBlocking {
    val repo = repositoryFailingOnInvitationRead(CancellationException("cancelled"))

    try {
      repo.joinGroupWithCode(UNUSED_CODE, PLAYER_ID)
      fail("a cancellation must be rethrown, not turned into a failed Result")
    } catch (e: CancellationException) {
      assertEquals("cancelled", e.message)
    }
  }

  @Test
  fun regenerateMapsPermissionDeniedToNotGameMaster() = runBlocking {
    val repo =
        repositoryFailingOnGroupRead(
            FirebaseFirestoreException("denied", FirebaseFirestoreException.Code.PERMISSION_DENIED)
        )

    val result = repo.regenerateInvitation("g1", GM_ID)

    // Must match the onPermissionDenied value written in regenerateInvitation.
    assertEquals(InvitationError.NOT_GAME_MASTER, result.invitationError())
  }
}
