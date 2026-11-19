// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.firestoreRules

import com.github.se.jdrnexus.model.personalSpace.DocumentType
import com.github.se.jdrnexus.model.personalSpace.JDRFile
import com.github.se.jdrnexus.utils.FirebaseEmulatorTestUtils
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Security rules of `campaign_groups/{groupId}`, on the Firestore emulator started with the project
 * rules ("firebase emulators:exec --only auth,firestore"). Participants read the group. Each write
 * is the batch the app sends (GroupRepositoryFirestore): creation with the root and the creator's
 * users document, metadata with the root, the invite code with its invitation, a participant added
 * or removed with their users document, a join with the redemption. Its sub-collections have their
 * own class (GroupSubcollectionsRulesTest).
 */
@RunWith(RobolectricTestRunner::class)
class CampaignGroupsRulesTest {

  companion object {
    private const val GM_ID = "gm"
    private const val PLAYER_ID = "player"
    private const val COMPANION_ID = "companion"
    // Signed in, member of no group.
    private const val OUTSIDER_ID = "outsider"
    // Has no users document and no client: only added by others.
    private const val NEWCOMER_ID = "newcomer"
    private const val NAME = "The Sunken Crown"
    // The group's current code, whose invitation exists.
    private const val CODE = "ABCD2345"
    // A code with no invitation yet.
    private const val NEW_CODE = "EFGH6789"
  }

  private val run = UUID.randomUUID().toString()
  // GM gm, participants gm, player and companion, invite code CODE.
  private val groupId = "$run-group"
  // A group created by a test.
  private val newGroupId = "$run-new"

  // Signed in as the GM; also used to seed and check data bypassing the rules.
  private lateinit var gm: FirebaseFirestore
  private lateinit var player: FirebaseFirestore
  private lateinit var outsider: FirebaseFirestore

  private fun FirebaseFirestore.group(id: String = groupId) =
      collection("campaign_groups").document(id)

  private fun FirebaseFirestore.root(id: String = groupId) = collection("documents").document(id)

  private fun FirebaseFirestore.user(userId: String) = collection("users").document(userId)

  private fun FirebaseFirestore.invitation(code: String) = collection("invitations").document(code)

  // The root the app writes with a group (GroupRepositoryFirestore.rootFileOf).
  private fun rootOf(id: String, ownerId: String, name: String) =
      JDRFile(
          id = id,
          name = name,
          ownerId = ownerId,
          sharedGroupsIds = listOf(id),
          type = DocumentType.GROUP_ROOT,
      )

  // The users writes of the app: arrayUnion / arrayRemove with merge.
  private fun added(id: String) = mapOf("groupIds" to FieldValue.arrayUnion(id))

  private fun removed(id: String) = mapOf("groupIds" to FieldValue.arrayRemove(id))

  @Before
  fun setUp() {
    gm = FirebaseEmulatorTestUtils.signInAs(GM_ID)
    player = FirebaseEmulatorTestUtils.signInAs(PLAYER_ID)
    outsider = FirebaseEmulatorTestUtils.signInAs(OUTSIDER_ID)
    runBlocking {
      // CampaignGroup has no inviteCode field, so the group is written as a map.
      FirebaseEmulatorTestUtils.writeBypassingRules(
          gm,
          "campaign_groups/$groupId",
          mapOf(
              "id" to groupId,
              "name" to NAME,
              "description" to "",
              "gameMasterId" to GM_ID,
              "participantIds" to listOf(GM_ID, PLAYER_ID, COMPANION_ID),
              "inviteCode" to CODE,
          ),
      )
      FirebaseEmulatorTestUtils.writeBypassingRules(
          gm,
          "documents/$groupId",
          rootOf(groupId, GM_ID, NAME),
      )
      FirebaseEmulatorTestUtils.writeBypassingRules(
          gm,
          "invitations/$CODE",
          mapOf("groupId" to groupId, "createdBy" to GM_ID),
      )
      listOf(GM_ID, PLAYER_ID, COMPANION_ID).forEach {
        FirebaseEmulatorTestUtils.writeUserGroupIdsBypassingRules(gm, it, groupId)
      }
    }
  }

  @After
  fun tearDown() = runBlocking {
    FirebaseEmulatorTestUtils.tearDown(
        gm,
        "invitations/$CODE/redemptions",
        "invitations",
        "campaign_groups",
        "documents",
        "users",
    )
  }

  private suspend fun storedGroupIds() =
      FirebaseEmulatorTestUtils.documentIdsBypassingRules(gm, "campaign_groups")

  // Read as the GM, a participant of the group.
  private suspend fun stored(field: String) = gm.group().get().await().get(field)

  // ========================================== read

  @Test
  fun onlyParticipantsCanGetTheGroup() = runBlocking {
    assertEquals(NAME, player.group().get().await().getString("name"))

    FirebaseEmulatorTestUtils.assertPermissionDenied("outsider") { outsider.group().get().await() }
    val signedOut = FirebaseEmulatorTestUtils.signedOutFirestore()
    FirebaseEmulatorTestUtils.assertPermissionDenied("signed out") {
      signedOut.group().get().await()
    }
  }

  @Test
  fun aMissingGroupIdIsNotFound() = runBlocking {
    assertFalse(outsider.group("$run-missing").get().await().exists())
  }

  @Test
  fun aUserCanListTheGroupsTheyParticipateIn() = runBlocking {
    val groups =
        player
            .collection("campaign_groups")
            .whereArrayContains("participantIds", PLAYER_ID)
            .get()
            .await()

    assertEquals(listOf(groupId), groups.documents.map { it.id })
  }

  @Test
  fun queriesThatCouldReturnOtherGroupsAreDenied() = runBlocking {
    FirebaseEmulatorTestUtils.assertPermissionDenied("no filter") {
      player.collection("campaign_groups").get().await()
    }
    FirebaseEmulatorTestUtils.assertPermissionDenied("filtered on gameMasterId only") {
      gm.collection("campaign_groups").whereEqualTo("gameMasterId", GM_ID).get().await()
    }
    FirebaseEmulatorTestUtils.assertPermissionDenied("another user's groups") {
      outsider
          .collection("campaign_groups")
          .whereArrayContains("participantIds", PLAYER_ID)
          .get()
          .await()
    }
  }

  // ========================================== create

  private fun newGroup(
      name: String = "The Drowned Keep",
      gameMasterId: String = OUTSIDER_ID,
      participantIds: List<String> = listOf(OUTSIDER_ID),
  ) =
      mapOf(
          "id" to newGroupId,
          "name" to name,
          "description" to "",
          "gameMasterId" to gameMasterId,
          "participantIds" to participantIds,
      )

  // GroupRepositoryFirestore.createGroup, by OUTSIDER_ID.
  private suspend fun create(
      group: Map<String, Any>,
      client: FirebaseFirestore = outsider,
      withRoot: Boolean = true,
      withMembership: Boolean = true,
  ) {
    val batch = client.batch()
    batch.set(client.group(newGroupId), group)
    if (withRoot) {
      batch.set(client.root(newGroupId), rootOf(newGroupId, OUTSIDER_ID, group["name"] as String))
    }
    if (withMembership) batch.set(client.user(OUTSIDER_ID), added(newGroupId), SetOptions.merge())
    batch.commit().await()
  }

  @Test
  fun aUserCanCreateAGroupWithItsRootAndTheirMembership() = runBlocking {
    create(newGroup())

    assertEquals(listOf(groupId, newGroupId), storedGroupIds())
    assertEquals(
        listOf(groupId, newGroupId),
        FirebaseEmulatorTestUtils.documentIdsBypassingRules(gm, "documents"),
    )
    assertEquals(listOf(newGroupId), FirebaseEmulatorTestUtils.userGroupIds(OUTSIDER_ID))
  }

  @Test
  fun invalidGroupCreationsAreDenied() = runBlocking {
    FirebaseEmulatorTestUtils.assertPermissionDenied("without the root") {
      create(newGroup(), withRoot = false)
    }
    FirebaseEmulatorTestUtils.assertPermissionDenied("without the creator's membership") {
      create(newGroup(), withMembership = false)
    }
    listOf(
            "another participant" to newGroup(participantIds = listOf(OUTSIDER_ID, PLAYER_ID)),
            "another user as GM" to newGroup(gameMasterId = GM_ID),
            "an empty name" to newGroup(name = ""),
            // A new group has only its five fields: the GM sets the code later (isInviteCodeEdit).
            "an extra field" to newGroup() + ("inviteCode" to NEW_CODE),
        )
        .forEach { (case, group) ->
          FirebaseEmulatorTestUtils.assertPermissionDenied(case) { create(group) }
        }
    val signedOut = FirebaseEmulatorTestUtils.signedOutFirestore()
    FirebaseEmulatorTestUtils.assertPermissionDenied("signed out") {
      create(newGroup(), client = signedOut)
    }

    assertEquals(listOf(groupId), storedGroupIds())
    assertEquals(null, FirebaseEmulatorTestUtils.userGroupIds(OUTSIDER_ID))
  }

  // ========================================== name and description

  // GroupRepositoryFirestore.updateGroup: the group and the root's name in one batch.
  private suspend fun rename(client: FirebaseFirestore, name: String) {
    val batch = client.batch()
    batch.update(client.group(), mapOf("name" to name, "description" to "A drowned city."))
    batch.update(client.root(), "name", name)
    batch.commit().await()
  }

  @Test
  fun theGameMasterCanRenameTheGroupWithItsRoot() = runBlocking {
    rename(gm, "The Risen Crown")

    assertEquals("The Risen Crown", stored("name"))
    assertEquals("A drowned city.", stored("description"))
    assertEquals("The Risen Crown", gm.root().get().await().getString("name"))
  }

  @Test
  fun invalidGroupEditsAreDenied() = runBlocking {
    FirebaseEmulatorTestUtils.assertPermissionDenied("renamed without the root") {
      gm.group().update("name", "The Risen Crown").await()
    }
    FirebaseEmulatorTestUtils.assertPermissionDenied("an empty name") { rename(gm, "") }
    // The name does not change, so the root needs no update: only the GM check refuses.
    FirebaseEmulatorTestUtils.assertPermissionDenied("description edited by a player") {
      player.group().update("description", "Ours now.").await()
    }
    FirebaseEmulatorTestUtils.assertPermissionDenied("another GM") {
      gm.group().update("gameMasterId", PLAYER_ID).await()
    }

    assertEquals(NAME, stored("name"))
    assertEquals("", stored("description"))
    assertEquals(GM_ID, stored("gameMasterId"))
    assertEquals(NAME, gm.root().get().await().getString("name"))
  }

  // ========================================== invite code

  // GroupRepositoryFirestore.regenerateInvitation: the old invitation goes, the new one comes, and
  // the group points to it, in one batch.
  @Test
  fun theGameMasterCanReplaceTheInviteCodeWithItsInvitation() = runBlocking {
    val batch = gm.batch()
    batch.delete(gm.invitation(CODE))
    batch.set(gm.invitation(NEW_CODE), mapOf("groupId" to groupId, "createdBy" to GM_ID))
    batch.update(gm.group(), "inviteCode", NEW_CODE)
    batch.commit().await()

    assertEquals(NEW_CODE, stored("inviteCode"))
    assertEquals(
        listOf(NEW_CODE),
        FirebaseEmulatorTestUtils.documentIdsBypassingRules(gm, "invitations"),
    )
  }

  @Test
  fun invalidInviteCodeEditsAreDenied() = runBlocking {
    FirebaseEmulatorTestUtils.assertPermissionDenied("without its invitation") {
      gm.group().update("inviteCode", NEW_CODE).await()
    }
    FirebaseEmulatorTestUtils.assertPermissionDenied("an empty code") {
      gm.group().update("inviteCode", "").await()
    }
    FirebaseEmulatorTestUtils.assertPermissionDenied("with another field") {
      val batch = gm.batch()
      batch.set(gm.invitation(NEW_CODE), mapOf("groupId" to groupId, "createdBy" to GM_ID))
      batch.update(gm.group(), mapOf("inviteCode" to NEW_CODE, "description" to "Join us."))
      batch.commit().await()
    }
    // Existing invitations, so that only the group's own rule can refuse.
    FirebaseEmulatorTestUtils.writeBypassingRules(
        gm,
        "invitations/$NEW_CODE",
        mapOf("groupId" to "$run-other", "createdBy" to GM_ID),
    )
    FirebaseEmulatorTestUtils.assertPermissionDenied("the invitation of another group") {
      gm.group().update("inviteCode", NEW_CODE).await()
    }
    FirebaseEmulatorTestUtils.writeBypassingRules(
        gm,
        "invitations/$NEW_CODE",
        mapOf("groupId" to groupId, "createdBy" to GM_ID),
    )
    FirebaseEmulatorTestUtils.assertPermissionDenied("not the game master") {
      player.group().update("inviteCode", NEW_CODE).await()
    }

    assertEquals(CODE, stored("inviteCode"))
    assertEquals("", stored("description"))
  }

  // ========================================== participant added by the GM

  // GroupRepositoryFirestore.addUserToGroup: participantIds and the users documents of
  // membershipOf, in one batch.
  private suspend fun addParticipants(
      client: FirebaseFirestore,
      participantIds: Any,
      membershipOf: List<String>,
  ) {
    val batch = client.batch()
    batch.update(client.group(), "participantIds", participantIds)
    membershipOf.forEach { batch.set(client.user(it), added(groupId), SetOptions.merge()) }
    batch.commit().await()
  }

  @Test
  fun theGameMasterCanAddAParticipantWithTheirMembership() = runBlocking {
    addParticipants(gm, FieldValue.arrayUnion(OUTSIDER_ID), listOf(OUTSIDER_ID))

    assertEquals(listOf(GM_ID, PLAYER_ID, COMPANION_ID, OUTSIDER_ID), stored("participantIds"))
    assertEquals(listOf(groupId), FirebaseEmulatorTestUtils.userGroupIds(OUTSIDER_ID))
  }

  @Test
  fun invalidParticipantAdditionsAreDenied() = runBlocking {
    FirebaseEmulatorTestUtils.assertPermissionDenied("without their membership") {
      addParticipants(gm, FieldValue.arrayUnion(OUTSIDER_ID), emptyList())
    }
    // The player may write the outsider's users document (DATA_ARCHITECTURE.md §12), so only the
    // group's rule refuses.
    FirebaseEmulatorTestUtils.assertPermissionDenied("added by a player") {
      addParticipants(player, FieldValue.arrayUnion(OUTSIDER_ID), listOf(OUTSIDER_ID))
    }
    FirebaseEmulatorTestUtils.assertPermissionDenied("two at once") {
      addParticipants(
          gm,
          FieldValue.arrayUnion(OUTSIDER_ID, NEWCOMER_ID),
          listOf(OUTSIDER_ID, NEWCOMER_ID),
      )
    }
    // One id more, but the companion is dropped: the old participants must all stay.
    FirebaseEmulatorTestUtils.assertPermissionDenied("replacing a participant") {
      addParticipants(gm, listOf(GM_ID, PLAYER_ID, OUTSIDER_ID, NEWCOMER_ID), listOf(NEWCOMER_ID))
    }

    assertEquals(listOf(GM_ID, PLAYER_ID, COMPANION_ID), stored("participantIds"))
    assertEquals(null, FirebaseEmulatorTestUtils.userGroupIds(OUTSIDER_ID))
  }

  // ========================================== join with the invite code

  // GroupRepositoryFirestore.joinGroupWithCode, by the outsider: the redemption, participantIds
  // and the users document, in one batch.
  private suspend fun join(
      joinerId: String = OUTSIDER_ID,
      withRedemption: Boolean = true,
      withMembership: Boolean = true,
  ) {
    val batch = outsider.batch()
    if (withRedemption) {
      batch.set(
          outsider.invitation(CODE).collection("redemptions").document(OUTSIDER_ID),
          mapOf("groupId" to groupId),
      )
    }
    batch.update(outsider.group(), "participantIds", FieldValue.arrayUnion(joinerId))
    if (withMembership) batch.set(outsider.user(joinerId), added(groupId), SetOptions.merge())
    batch.commit().await()
  }

  private suspend fun redeemers() =
      FirebaseEmulatorTestUtils.documentIdsBypassingRules(gm, "invitations/$CODE/redemptions")

  @Test
  fun aUserCanJoinWithTheCurrentCode() = runBlocking {
    join()

    assertEquals(listOf(GM_ID, PLAYER_ID, COMPANION_ID, OUTSIDER_ID), stored("participantIds"))
    assertEquals(listOf(groupId), FirebaseEmulatorTestUtils.userGroupIds(OUTSIDER_ID))
    assertEquals(listOf(OUTSIDER_ID), redeemers())
  }

  @Test
  fun invalidJoinsAreDenied() = runBlocking {
    FirebaseEmulatorTestUtils.assertPermissionDenied("without the redemption") {
      join(withRedemption = false)
    }
    FirebaseEmulatorTestUtils.assertPermissionDenied("without their membership") {
      join(withMembership = false)
    }
    FirebaseEmulatorTestUtils.assertPermissionDenied("adding another user") {
      join(joinerId = NEWCOMER_ID)
    }

    assertEquals(listOf(GM_ID, PLAYER_ID, COMPANION_ID), stored("participantIds"))
    assertEquals(null, FirebaseEmulatorTestUtils.userGroupIds(OUTSIDER_ID))
    assertEquals(emptyList<String>(), redeemers())
  }

  // ========================================== participant removed

  // GroupRepositoryFirestore.removeUserFromGroup: participantIds and the users documents.
  private suspend fun removeParticipants(
      client: FirebaseFirestore,
      participantIds: Any,
      membershipOf: List<String>,
  ) {
    val batch = client.batch()
    batch.update(client.group(), "participantIds", participantIds)
    membershipOf.forEach { batch.set(client.user(it), removed(groupId), SetOptions.merge()) }
    batch.commit().await()
  }

  @Test
  fun aPlayerCanLeaveAndTheGameMasterCanRemoveOne() = runBlocking {
    removeParticipants(player, FieldValue.arrayRemove(PLAYER_ID), listOf(PLAYER_ID))
    removeParticipants(gm, FieldValue.arrayRemove(COMPANION_ID), listOf(COMPANION_ID))

    assertEquals(listOf(GM_ID), stored("participantIds"))
    assertEquals(emptyList<String>(), FirebaseEmulatorTestUtils.userGroupIds(PLAYER_ID))
    assertEquals(emptyList<String>(), FirebaseEmulatorTestUtils.userGroupIds(COMPANION_ID))
  }

  @Test
  fun invalidParticipantRemovalsAreDenied() = runBlocking {
    FirebaseEmulatorTestUtils.assertPermissionDenied("without their membership") {
      removeParticipants(gm, FieldValue.arrayRemove(PLAYER_ID), emptyList())
    }
    // The player may write the companion's users document (DATA_ARCHITECTURE.md §12), so only the
    // group's rule refuses.
    FirebaseEmulatorTestUtils.assertPermissionDenied("a player removing another") {
      removeParticipants(player, FieldValue.arrayRemove(COMPANION_ID), listOf(COMPANION_ID))
    }
    FirebaseEmulatorTestUtils.assertPermissionDenied("removing the game master") {
      removeParticipants(gm, FieldValue.arrayRemove(GM_ID), listOf(GM_ID))
    }
    FirebaseEmulatorTestUtils.assertPermissionDenied("two at once") {
      removeParticipants(
          gm,
          FieldValue.arrayRemove(PLAYER_ID, COMPANION_ID),
          listOf(PLAYER_ID, COMPANION_ID),
      )
    }
    // One id less, but the companion is swapped for the outsider: the kept ids must all be old
    // ones.
    FirebaseEmulatorTestUtils.assertPermissionDenied("replacing a participant") {
      removeParticipants(gm, listOf(GM_ID, OUTSIDER_ID), listOf(PLAYER_ID))
    }

    assertEquals(listOf(GM_ID, PLAYER_ID, COMPANION_ID), stored("participantIds"))
    listOf(GM_ID, PLAYER_ID, COMPANION_ID).forEach {
      assertEquals(listOf(groupId), FirebaseEmulatorTestUtils.userGroupIds(it))
    }
  }

  // ========================================== delete

  @Test
  fun onlyTheGameMasterCanDeleteTheGroup() = runBlocking {
    FirebaseEmulatorTestUtils.assertPermissionDenied("player") { player.group().delete().await() }
    assertEquals(listOf(groupId), storedGroupIds())

    // GroupRepositoryFirestore.deleteGroup: the group, its root, its invitation and its id in the
    // members' groupIds.
    val batch = gm.batch()
    batch.delete(gm.group())
    batch.delete(gm.root())
    batch.delete(gm.invitation(CODE))
    listOf(GM_ID, PLAYER_ID, COMPANION_ID).forEach {
      batch.update(gm.user(it), "groupIds", FieldValue.arrayRemove(groupId))
    }
    batch.commit().await()

    assertEquals(emptyList<String>(), storedGroupIds())
    assertEquals(
        emptyList<String>(),
        FirebaseEmulatorTestUtils.documentIdsBypassingRules(gm, "documents"),
    )
    assertEquals(
        emptyList<String>(),
        FirebaseEmulatorTestUtils.documentIdsBypassingRules(gm, "invitations"),
    )
    listOf(GM_ID, PLAYER_ID, COMPANION_ID).forEach {
      assertEquals(emptyList<String>(), FirebaseEmulatorTestUtils.userGroupIds(it))
    }
  }

  // Known gap (DATA_ARCHITECTURE.md §12), to fix with the group deletion: the rules let the GM
  // delete the group alone, which leaves its root (undeletable without the group) and the members'
  // groupIds behind.
  @Test
  fun theGroupCanStillBeDeletedWithoutItsRootAndMemberships() = runBlocking {
    gm.group().delete().await()

    assertEquals(emptyList<String>(), storedGroupIds())
    assertEquals(
        listOf(groupId),
        FirebaseEmulatorTestUtils.documentIdsBypassingRules(gm, "documents"),
    )
    assertEquals(listOf(groupId), FirebaseEmulatorTestUtils.userGroupIds(GM_ID))
  }
}
