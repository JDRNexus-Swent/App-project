// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.firestoreRules

import com.github.se.jdrnexus.model.personalSpace.CampaignGroup
import com.github.se.jdrnexus.utils.FirebaseEmulatorTestUtils
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
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
 * Security rules of the sub-collections of `campaign_groups/{groupId}` (`dice_rolls`, `activities`,
 * `gm_data`, `gm_lore`), on the Firestore emulator started with the project rules ("firebase
 * emulators:exec --only auth,firestore"). Each test makes requests as signed-in users (or signed
 * out) and checks that the rules allow them, or deny them and leave the stored data unchanged.
 */
@RunWith(RobolectricTestRunner::class)
class GroupSubcollectionsRulesTest {

  companion object {
    private const val GM_ID = "gm"
    private const val PLAYER_ID = "player"
    private const val OUTSIDER_ID = "outsider"
    private const val SESSION_ID = "session"
    private const val ROLL_ID = "roll"
    private const val ACTIVITY_ID = "activity"
    private const val SHARED_LORE_ID = "shared-lore"
    private const val SECRET_LORE_ID = "secret-lore"
    private val SHARED_LORE = mapOf("title" to "The old map", "isShared" to true)
    private val SECRET_LORE = mapOf("title" to "The traitor", "isShared" to false)
  }

  private val groupId = UUID.randomUUID().toString()

  // Signed in as the GM; also used to seed and check data bypassing the rules.
  private lateinit var gm: FirebaseFirestore
  private lateinit var player: FirebaseFirestore
  private lateinit var outsider: FirebaseFirestore

  private fun FirebaseFirestore.sub(name: String) =
      collection("campaign_groups").document(groupId).collection(name)

  private fun validRoll(userId: String = PLAYER_ID): Map<String, Any> =
      mapOf(
          "userId" to userId,
          "username" to "Aria",
          "diceType" to "D20",
          "modifier" to 2,
          "totalValue" to 17,
          "timestamp" to FieldValue.serverTimestamp(),
      )

  private fun validActivity(userId: String = PLAYER_ID): Map<String, Any?> =
      mapOf(
          "userId" to userId,
          "playerName" to "Aria",
          "action" to "Opens the door",
          "diceResult" to null,
          "timestamp" to FieldValue.serverTimestamp(),
      )

  @Before
  fun setUp() {
    gm = FirebaseEmulatorTestUtils.signInAs(GM_ID)
    player = FirebaseEmulatorTestUtils.signInAs(PLAYER_ID)
    outsider = FirebaseEmulatorTestUtils.signInAs(OUTSIDER_ID)
    runBlocking {
      // The rules read the group (isGm) and users/{uid}.groupIds (isMember).
      FirebaseEmulatorTestUtils.writeBypassingRules(
          gm,
          "campaign_groups/$groupId",
          CampaignGroup(
              id = groupId,
              name = "The Sunken Crown",
              gameMasterId = GM_ID,
              participantIds = listOf(GM_ID, PLAYER_ID),
          ),
      )
      FirebaseEmulatorTestUtils.writeUserGroupIdsBypassingRules(gm, GM_ID, groupId)
      FirebaseEmulatorTestUtils.writeUserGroupIdsBypassingRules(gm, PLAYER_ID, groupId)
    }
  }

  @After
  fun tearDown() = runBlocking {
    FirebaseEmulatorTestUtils.tearDown(
        gm,
        "campaign_groups/$groupId/dice_rolls",
        "campaign_groups/$groupId/activities",
        "campaign_groups/$groupId/gm_data",
        "campaign_groups/$groupId/gm_lore",
        "campaign_groups",
        "users",
    )
  }

  private suspend fun storedIds(subCollection: String) =
      FirebaseEmulatorTestUtils.documentIdsBypassingRules(
          gm,
          "campaign_groups/$groupId/$subCollection",
      )

  // Read bypassing the rules, so the check does not depend on them; null if missing.
  private suspend fun stored(document: DocumentReference): Map<*, *>? =
      FirebaseEmulatorTestUtils.readBypassingRules(gm, document.path, Map::class.java)

  // ========================================== dice_rolls

  @Test
  fun onlyMembersCanReadTheDiceRolls() = runBlocking {
    FirebaseEmulatorTestUtils.writeBypassingRules(
        gm,
        "campaign_groups/$groupId/dice_rolls/$ROLL_ID",
        mapOf("userId" to GM_ID),
    )

    assertEquals(listOf(ROLL_ID), player.sub("dice_rolls").get().await().documents.map { it.id })

    FirebaseEmulatorTestUtils.assertPermissionDenied("outsider") {
      outsider.sub("dice_rolls").get().await()
    }
    val signedOut = FirebaseEmulatorTestUtils.signedOutFirestore()
    FirebaseEmulatorTestUtils.assertPermissionDenied("signed out") {
      signedOut.sub("dice_rolls").get().await()
    }
  }

  @Test
  fun aMemberCanAddTheirOwnRoll() = runBlocking {
    player.sub("dice_rolls").document(ROLL_ID).set(validRoll()).await()

    assertEquals(listOf(ROLL_ID), storedIds("dice_rolls"))
  }

  @Test
  fun invalidRollsAreDenied() = runBlocking {
    listOf(
            Triple("another user's id", player, validRoll(GM_ID)),
            Triple("client timestamp", player, validRoll() + ("timestamp" to Timestamp.now())),
            Triple("extra field", player, validRoll() + ("isCritical" to true)),
            Triple("missing field", player, validRoll() - "modifier"),
            Triple("username above 50", player, validRoll() + ("username" to "a".repeat(51))),
            Triple("outsider", outsider, validRoll(OUTSIDER_ID)),
        )
        .forEach { (case, client, roll) ->
          FirebaseEmulatorTestUtils.assertPermissionDenied(case) {
            client.sub("dice_rolls").document(ROLL_ID).set(roll).await()
          }
        }

    assertEquals(emptyList<String>(), storedIds("dice_rolls"))
  }

  @Test
  fun aRollCannotBeEditedOrDeletedEvenByItsAuthor() = runBlocking {
    val roll = player.sub("dice_rolls").document(ROLL_ID)
    roll.set(validRoll()).await()
    val before = stored(roll)

    FirebaseEmulatorTestUtils.assertPermissionDenied("update") {
      roll.update("totalValue", 20).await()
    }
    FirebaseEmulatorTestUtils.assertPermissionDenied("delete") { roll.delete().await() }

    assertEquals(before, stored(roll))
  }

  // ========================================== activities

  @Test
  fun onlyMembersCanReadTheActivities() = runBlocking {
    FirebaseEmulatorTestUtils.writeBypassingRules(
        gm,
        "campaign_groups/$groupId/activities/$ACTIVITY_ID",
        mapOf("userId" to GM_ID),
    )

    assertEquals(
        listOf(ACTIVITY_ID),
        player.sub("activities").get().await().documents.map { it.id },
    )
    FirebaseEmulatorTestUtils.assertPermissionDenied("outsider") {
      outsider.sub("activities").get().await()
    }
  }

  @Test
  fun aMemberCanRecordTheirOwnActivity() = runBlocking {
    player.sub("activities").document(ACTIVITY_ID).set(validActivity()).await()

    assertEquals(listOf(ACTIVITY_ID), storedIds("activities"))
  }

  @Test
  fun invalidActivitiesAreDenied() = runBlocking {
    listOf(
            Triple("another user's id", player, validActivity(GM_ID)),
            Triple(
                "client timestamp",
                player,
                validActivity() + ("timestamp" to Timestamp.now()),
            ),
            Triple("empty action", player, validActivity() + ("action" to "")),
            Triple("extra field", player, validActivity() + ("isCritical" to true)),
            // The nullable field: its key must be there even when its value is null.
            Triple("missing field", player, validActivity() - "diceResult"),
            Triple("outsider", outsider, validActivity(OUTSIDER_ID)),
        )
        .forEach { (case, client, activity) ->
          FirebaseEmulatorTestUtils.assertPermissionDenied(case) {
            client.sub("activities").document(ACTIVITY_ID).set(activity).await()
          }
        }

    assertEquals(emptyList<String>(), storedIds("activities"))
  }

  @Test
  fun anActivityCannotBeEditedOrDeletedEvenByItsAuthor() = runBlocking {
    val activity = player.sub("activities").document(ACTIVITY_ID)
    activity.set(validActivity()).await()
    val before = stored(activity)

    FirebaseEmulatorTestUtils.assertPermissionDenied("update") {
      activity.update("action", "Closes the door").await()
    }
    FirebaseEmulatorTestUtils.assertPermissionDenied("delete") { activity.delete().await() }

    assertEquals(before, stored(activity))
  }

  // ========================================== gm_data (game session)

  @Test
  fun onlyMembersCanReadTheSession() = runBlocking {
    FirebaseEmulatorTestUtils.writeBypassingRules(
        gm,
        "campaign_groups/$groupId/gm_data/$SESSION_ID",
        mapOf("round" to 3),
    )

    assertEquals(3L, player.sub("gm_data").document(SESSION_ID).get().await().getLong("round"))
    FirebaseEmulatorTestUtils.assertPermissionDenied("outsider") {
      outsider.sub("gm_data").document(SESSION_ID).get().await()
    }
  }

  @Test
  fun onlyTheGameMasterCanWriteTheSession() = runBlocking {
    FirebaseEmulatorTestUtils.assertPermissionDenied("player") {
      player.sub("gm_data").document(SESSION_ID).set(mapOf("round" to 1)).await()
    }
    assertEquals(null, stored(gm.sub("gm_data").document(SESSION_ID)))

    gm.sub("gm_data").document(SESSION_ID).set(mapOf("round" to 1)).await()

    assertEquals(mapOf("round" to 1L), stored(gm.sub("gm_data").document(SESSION_ID)))
  }

  // ========================================== gm_lore

  private suspend fun seedLore() {
    FirebaseEmulatorTestUtils.writeBypassingRules(
        gm,
        "campaign_groups/$groupId/gm_lore/$SHARED_LORE_ID",
        SHARED_LORE,
    )
    FirebaseEmulatorTestUtils.writeBypassingRules(
        gm,
        "campaign_groups/$groupId/gm_lore/$SECRET_LORE_ID",
        SECRET_LORE,
    )
  }

  @Test
  fun theGameMasterCanReadAllTheLore() = runBlocking {
    seedLore()

    val lore = gm.sub("gm_lore").get().await()
    val secretById = gm.sub("gm_lore").document(SECRET_LORE_ID).get().await()

    assertEquals(setOf(SHARED_LORE_ID, SECRET_LORE_ID), lore.documents.map { it.id }.toSet())
    assertEquals("The traitor", secretById.getString("title"))
  }

  @Test
  fun aPlayerCanReadOnlySharedLore() = runBlocking {
    seedLore()

    val byId = player.sub("gm_lore").document(SHARED_LORE_ID).get().await()
    val byQuery = player.sub("gm_lore").whereEqualTo("isShared", true).get().await()
    assertEquals("The old map", byId.getString("title"))
    assertEquals(listOf(SHARED_LORE_ID), byQuery.documents.map { it.id })

    FirebaseEmulatorTestUtils.assertPermissionDenied("secret lore by id") {
      player.sub("gm_lore").document(SECRET_LORE_ID).get().await()
    }
    // A query that could return secret lore is denied as a whole.
    FirebaseEmulatorTestUtils.assertPermissionDenied("query without the isShared filter") {
      player.sub("gm_lore").get().await()
    }
  }

  @Test
  fun aSignedInUserReadingAMissingLoreIdGetsNotFound() = runBlocking {
    assertFalse(player.sub("gm_lore").document("missing").get().await().exists())
    // Not only members: any signed-in user learns the id is missing (DATA_ARCHITECTURE.md §8).
    assertFalse(outsider.sub("gm_lore").document("missing").get().await().exists())
  }

  @Test
  fun anOutsiderCannotReadSharedLore() = runBlocking {
    seedLore()

    FirebaseEmulatorTestUtils.assertPermissionDenied {
      outsider.sub("gm_lore").document(SHARED_LORE_ID).get().await()
    }
  }

  @Test
  fun onlyTheGameMasterCanWriteLore() = runBlocking {
    FirebaseEmulatorTestUtils.assertPermissionDenied("player") {
      player.sub("gm_lore").document(SHARED_LORE_ID).set(SHARED_LORE).await()
    }
    assertEquals(null, stored(gm.sub("gm_lore").document(SHARED_LORE_ID)))

    gm.sub("gm_lore").document(SHARED_LORE_ID).set(SHARED_LORE).await()

    assertEquals(SHARED_LORE, stored(gm.sub("gm_lore").document(SHARED_LORE_ID)))
  }
}
