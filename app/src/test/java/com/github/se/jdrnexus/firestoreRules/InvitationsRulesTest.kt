// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.firestoreRules

import com.github.se.jdrnexus.utils.FirebaseEmulatorTestUtils
import com.google.firebase.firestore.FirebaseFirestore
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
 * Security rules of `invitations/{code}` (`{ groupId, createdBy }`) and of its sub-collection
 * `redemptions/{userId}` (`{ groupId }`, the proof that a user joined with the code), on the
 * Firestore emulator started with the project rules ("firebase emulators:exec --only
 * auth,firestore"). The join batch itself (redemption + participantIds + users) belongs to the
 * campaign_groups rules tests.
 */
@RunWith(RobolectricTestRunner::class)
class InvitationsRulesTest {

  companion object {
    private const val GM_ID = "gm"
    private const val PLAYER_ID = "player"
    private const val OUTSIDER_ID = "outsider"
    // The group's current code, whose invitation exists.
    private const val CODE = "ABCD2345"
    // A code the group points to but whose invitation is not written yet.
    private const val NEW_CODE = "EFGH6789"
    // An invitation of the group that is no longer its current code.
    private const val OLD_CODE = "JKLM2345"
  }

  private val groupId = UUID.randomUUID().toString()

  // Signed in as the GM; also used to seed and check data bypassing the rules.
  private lateinit var gm: FirebaseFirestore
  private lateinit var player: FirebaseFirestore
  private lateinit var outsider: FirebaseFirestore

  private fun FirebaseFirestore.invitation(code: String) = collection("invitations").document(code)

  private fun FirebaseFirestore.redemption(code: String, userId: String) =
      invitation(code).collection("redemptions").document(userId)

  private fun invitationData(createdBy: String = GM_ID) =
      mapOf("groupId" to groupId, "createdBy" to createdBy)

  private suspend fun writeGroupWithCode(code: String) =
      FirebaseEmulatorTestUtils.writeGroupBypassingRules(
          gm,
          groupId,
          GM_ID,
          listOf(GM_ID, PLAYER_ID),
          inviteCode = code,
      )

  @Before
  fun setUp() {
    gm = FirebaseEmulatorTestUtils.signInAs(GM_ID)
    player = FirebaseEmulatorTestUtils.signInAs(PLAYER_ID)
    outsider = FirebaseEmulatorTestUtils.signInAs(OUTSIDER_ID)
    runBlocking {
      writeGroupWithCode(CODE)
      FirebaseEmulatorTestUtils.writeBypassingRules(gm, "invitations/$CODE", invitationData())
      FirebaseEmulatorTestUtils.writeBypassingRules(gm, "invitations/$OLD_CODE", invitationData())
    }
  }

  @After
  fun tearDown() = runBlocking {
    FirebaseEmulatorTestUtils.tearDown(
        gm,
        "invitations/$CODE/redemptions",
        "invitations/$OLD_CODE/redemptions",
        "invitations",
        "campaign_groups",
    )
  }

  private suspend fun storedInvitations() =
      FirebaseEmulatorTestUtils.documentIdsBypassingRules(gm, "invitations")

  private suspend fun redeemers(code: String) =
      FirebaseEmulatorTestUtils.documentIdsBypassingRules(gm, "invitations/$code/redemptions")

  // ========================================== invitations: read

  @Test
  fun anySignedInUserCanGetAnInvitationByItsCode() = runBlocking {
    assertEquals(groupId, outsider.invitation(CODE).get().await().getString("groupId"))

    val signedOut = FirebaseEmulatorTestUtils.signedOutFirestore()
    FirebaseEmulatorTestUtils.assertPermissionDenied("signed out") {
      signedOut.invitation(CODE).get().await()
    }
  }

  @Test
  fun nobodyCanListInvitations() = runBlocking {
    FirebaseEmulatorTestUtils.assertPermissionDenied("game master") {
      gm.collection("invitations").get().await()
    }
    FirebaseEmulatorTestUtils.assertPermissionDenied("filtered on the GM's group") {
      gm.collection("invitations").whereEqualTo("groupId", groupId).get().await()
    }
    FirebaseEmulatorTestUtils.assertPermissionDenied("outsider") {
      outsider.collection("invitations").get().await()
    }
  }

  // ========================================== invitations: write

  @Test
  fun theGameMasterCanCreateTheInvitationOfTheGroupsCode() = runBlocking {
    writeGroupWithCode(NEW_CODE)

    gm.invitation(NEW_CODE).set(invitationData()).await()

    assertEquals(listOf(CODE, NEW_CODE, OLD_CODE), storedInvitations())
  }

  @Test
  fun invalidInvitationsAreDenied() = runBlocking {
    writeGroupWithCode(NEW_CODE)

    listOf(
            Triple("not the game master", player to NEW_CODE, invitationData(PLAYER_ID)),
            Triple("created by another user", gm to NEW_CODE, invitationData(PLAYER_ID)),
            Triple("not the group's code", gm to "NPQR6789", invitationData()),
            Triple("an extra field", gm to NEW_CODE, invitationData() + ("uses" to 0)),
        )
        .forEach { (case, target, data) ->
          val (client, code) = target
          FirebaseEmulatorTestUtils.assertPermissionDenied(case) {
            client.invitation(code).set(data).await()
          }
        }

    assertEquals(listOf(CODE, OLD_CODE), storedInvitations())
  }

  @Test
  fun anInvitationCannotBeEdited() = runBlocking {
    FirebaseEmulatorTestUtils.assertPermissionDenied {
      gm.invitation(CODE).update("createdBy", PLAYER_ID).await()
    }

    assertEquals(GM_ID, gm.invitation(CODE).get().await().getString("createdBy"))
  }

  @Test
  fun onlyTheGameMasterCanDeleteAnInvitation() = runBlocking {
    FirebaseEmulatorTestUtils.assertPermissionDenied("player") {
      player.invitation(CODE).delete().await()
    }
    assertEquals(listOf(CODE, OLD_CODE), storedInvitations())

    gm.invitation(CODE).delete().await()

    assertEquals(listOf(OLD_CODE), storedInvitations())
  }

  // ========================================== redemptions

  @Test
  fun aUserCanRedeemTheCurrentCodeForThemselves() = runBlocking {
    outsider.redemption(CODE, OUTSIDER_ID).set(mapOf("groupId" to groupId)).await()

    assertEquals(listOf(OUTSIDER_ID), redeemers(CODE))
  }

  @Test
  fun invalidRedemptionsAreDenied() = runBlocking {
    listOf(
            Triple("for another user", CODE to PLAYER_ID, mapOf("groupId" to groupId)),
            Triple("another group", CODE to OUTSIDER_ID, mapOf("groupId" to "other-group")),
            Triple(
                "an extra field",
                CODE to OUTSIDER_ID,
                mapOf("groupId" to groupId, "at" to 0),
            ),
            Triple("an old code", OLD_CODE to OUTSIDER_ID, mapOf("groupId" to groupId)),
        )
        .forEach { (case, target, data) ->
          val (code, userId) = target
          FirebaseEmulatorTestUtils.assertPermissionDenied(case) {
            outsider.redemption(code, userId).set(data).await()
          }
        }

    assertEquals(emptyList<String>(), redeemers(CODE))
    assertEquals(emptyList<String>(), redeemers(OLD_CODE))
  }

  // Accepted rule: a redemption may be written again (allow create, update), with the same content
  // since the rules fix it.
  @Test
  fun aRedemptionCanBeWrittenAgain() = runBlocking {
    val redemption = outsider.redemption(CODE, OUTSIDER_ID)
    redemption.set(mapOf("groupId" to groupId)).await()

    redemption.set(mapOf("groupId" to groupId)).await()

    assertEquals(listOf(OUTSIDER_ID), redeemers(CODE))
  }

  @Test
  fun redemptionsCannotBeReadOrDeleted() = runBlocking {
    val redemption = outsider.redemption(CODE, OUTSIDER_ID)
    redemption.set(mapOf("groupId" to groupId)).await()

    FirebaseEmulatorTestUtils.assertPermissionDenied("get by the redeemer") {
      redemption.get().await()
    }
    FirebaseEmulatorTestUtils.assertPermissionDenied("list by the game master") {
      gm.invitation(CODE).collection("redemptions").get().await()
    }
    FirebaseEmulatorTestUtils.assertPermissionDenied("delete by the redeemer") {
      redemption.delete().await()
    }

    assertEquals(listOf(OUTSIDER_ID), redeemers(CODE))
  }
}
