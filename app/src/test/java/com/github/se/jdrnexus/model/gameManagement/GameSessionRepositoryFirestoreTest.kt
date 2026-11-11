// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.model.gameManagement

import com.github.se.jdrnexus.model.filerepresentation.CharacterFile
import com.github.se.jdrnexus.model.filerepresentation.CharacterHealthPoints
import com.github.se.jdrnexus.model.filerepresentation.CharacterStat
import com.github.se.jdrnexus.model.filerepresentation.LoreFile
import com.github.se.jdrnexus.model.filerepresentation.LoreSection
import com.github.se.jdrnexus.model.personalSpace.CampaignGroup
import com.github.se.jdrnexus.model.repository.DiceRepositoryFirestore
import com.github.se.jdrnexus.model.repository.DiceRollEntity
import com.github.se.jdrnexus.utils.FirebaseEmulatorTestUtils
import com.github.se.jdrnexus.utils.waitFor
import com.google.firebase.firestore.FirebaseFirestore
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.util.Date
import java.util.UUID
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GameSessionRepositoryFirestoreTest {
  private lateinit var firestore: FirebaseFirestore
  private lateinit var repository: GameSessionRepositoryFirestore
  private lateinit var diceRepository: DiceRepositoryFirestore
  private val groupId = UUID.randomUUID().toString()

  @Before
  fun setUp() {
    firestore = FirebaseEmulatorTestUtils.getFirestore()
    diceRepository = mockk()
    coEvery { diceRepository.getDiceRollsHistory(any()) } returns Result.success(emptyList())
    every { diceRepository.getDiceRollsStream(any()) } returns flowOf(emptyList())
    repository = GameSessionRepositoryFirestore(firestore, diceRepository)
  }

  @After
  fun tearDown() {
    runBlocking {
      FirebaseEmulatorTestUtils.tearDown(
          firestore,
          "campaign_groups/$groupId/gm_data",
          "campaign_groups/$groupId/gm_lore",
          "campaign_groups/$groupId/activities",
      )
    }
  }

  @Test
  fun saveSession_persistsAndObservesSnapshot_andRejectsInvalidRound() = runBlocking {
    val session =
        GameSessionSnapshot(
            enemies =
                listOf(
                    GameCombatNPC(
                        id = "enemy-1",
                        character =
                            CharacterFile(
                                "Goblin",
                                emptyList(),
                                emptyList(),
                                CharacterHealthPoints(6, 8),
                            ),
                        initiative = 12,
                    )
                ),
            isCombatActive = true,
            currentRound = 3,
            npcs = listOf(GameNpc("npc-1", "Guide", null, "Helpful")),
        )

    assertTrue(repository.saveSession(groupId, session).isSuccess)
    assertEquals(session, repository.observeSession(groupId).waitFor { it == session })

    val result = repository.saveSession(groupId, session.copy(currentRound = 0))
    assertTrue(result.isFailure)
    assertEquals("Combat round must be at least one.", result.exceptionOrNull()?.message)
  }

  @Test
  fun saveSession_rejectsDuplicateIdsInvalidEnemyStatsAndInvalidNpcs() = runBlocking {
    val enemy =
        GameCombatNPC(
            "enemy",
            CharacterFile("Goblin", emptyList(), emptyList(), CharacterHealthPoints(6, 8)),
            12,
        )
    val duplicateEnemies = GameSessionSnapshot(enemies = listOf(enemy, enemy))
    val invalidHp =
        GameSessionSnapshot(
            enemies =
                listOf(
                    enemy.copy(character = enemy.character.copy(hp = CharacterHealthPoints(9, 8)))
                )
        )
    val invalidArmorClass =
        GameSessionSnapshot(
            enemies =
                listOf(
                    enemy.copy(
                        character =
                            enemy.character.copy(
                                stats = listOf(CharacterStat("armorClass", "Armor Class", -1.0))
                            )
                    )
                )
        )
    val duplicateNpcs =
        GameSessionSnapshot(
            npcs = listOf(GameNpc("npc", "Guide", null, ""), GameNpc("npc", "Twin", null, ""))
        )
    val invalidGroup = repository.saveSession("", GameSessionSnapshot())

    assertEquals(
        "Enemy IDs must be unique.",
        repository.saveSession(groupId, duplicateEnemies).exceptionOrNull()?.message,
    )
    assertEquals(
        "Enemy HP must be between zero and max HP.",
        repository.saveSession(groupId, invalidHp).exceptionOrNull()?.message,
    )
    assertEquals(
        "Enemy AC must be finite and non-negative.",
        repository.saveSession(groupId, invalidArmorClass).exceptionOrNull()?.message,
    )
    assertEquals(
        "NPC IDs must be unique.",
        repository.saveSession(groupId, duplicateNpcs).exceptionOrNull()?.message,
    )
    assertEquals("Group ID cannot be empty.", invalidGroup.exceptionOrNull()?.message)
  }

  @Test
  fun observeSession_emitsDefaultsForMissingDocument() = runBlocking {
    assertEquals(GameSessionSnapshot(), repository.observeSession(groupId).waitFor { true })
  }

  @Test
  fun observeLore_filtersSharedEntries_andVisibilityUpdates() = runBlocking {
    val sharedLore = GameLoreEntry("shared", LoreFile("Shared", emptyList()), true)
    val privateLore =
        GameLoreEntry(
            "private",
            LoreFile("Private", listOf(LoreSection("s1", "History", "Secret"))),
            false,
        )
    assertTrue(repository.saveLore(groupId, sharedLore).isSuccess)
    assertTrue(repository.saveLore(groupId, privateLore).isSuccess)

    assertEquals(2, repository.observeLore(groupId).waitFor { it.size == 2 }.size)
    assertEquals(listOf(sharedLore), repository.observeSharedLore(groupId).waitFor { it.size == 1 })

    assertTrue(repository.setLoreVisibility(groupId, privateLore.id, true).isSuccess)
    assertEquals(
        setOf(sharedLore.id, privateLore.id),
        repository.observeSharedLore(groupId).waitFor { it.size == 2 }.map { it.id }.toSet(),
    )
  }

  @Test
  fun loreWrites_rejectBlankIdsAndTitles() = runBlocking {
    val lore = GameLoreEntry("lore", LoreFile("Title", emptyList()), false)

    assertEquals(
        "Group ID cannot be empty.",
        repository.saveLore("", lore).exceptionOrNull()?.message,
    )
    assertEquals(
        "Lore document ID cannot be empty.",
        repository.saveLore(groupId, lore.copy(id = " ")).exceptionOrNull()?.message,
    )
    assertEquals(
        "Lore document title cannot be empty.",
        repository
            .saveLore(groupId, lore.copy(lore = LoreFile(" ", emptyList())))
            .exceptionOrNull()
            ?.message,
    )
    assertEquals(
        "Group ID cannot be empty.",
        repository.setLoreVisibility("", lore.id, true).exceptionOrNull()?.message,
    )
    assertEquals(
        "Lore document ID cannot be empty.",
        repository.setLoreVisibility(groupId, " ", true).exceptionOrNull()?.message,
    )
  }

  @Test
  fun recordActivity_loadsAndObservesPlayerActivity() = runBlocking {
    firestore
        .collection("campaign_groups")
        .document(groupId)
        .set(
            CampaignGroup(
                id = groupId,
                name = "Quest",
                gameMasterId = "gm-1",
                participantIds = listOf("player-1"),
            )
        )
        .await()

    assertTrue(
        repository
            .recordActivity(groupId, "player-1", "Ari", "Opened the door", "D20: 17")
            .isSuccess
    )

    val activity = repository.loadActivities(groupId).getOrThrow().single()
    assertEquals("player-1", activity.userId)
    assertEquals("Ari", activity.playerName)
    assertEquals("Opened the door", activity.action)
    assertEquals("D20: 17", activity.diceResult)
    assertFalse(activity.id.isBlank())
    assertEquals(activity, repository.observeActivities(groupId).waitFor { it.size == 1 }.single())
  }

  @Test
  fun activityMethods_rejectBlankRequiredValuesAndReturnDiceHistoryFailure() = runBlocking {
    assertEquals(
        "Group ID cannot be empty.",
        repository.loadActivities("").exceptionOrNull()?.message,
    )
    assertEquals(
        "Group ID cannot be empty.",
        repository.recordActivity("", "player", "Name", "Action").exceptionOrNull()?.message,
    )
    assertEquals(
        "User ID cannot be empty.",
        repository.recordActivity(groupId, " ", "Name", "Action").exceptionOrNull()?.message,
    )
    assertEquals(
        "Player name cannot be empty.",
        repository.recordActivity(groupId, "player", " ", "Action").exceptionOrNull()?.message,
    )
    assertEquals(
        "Activity action cannot be empty.",
        repository.recordActivity(groupId, "player", "Name", " ").exceptionOrNull()?.message,
    )

    firestore
        .collection("campaign_groups")
        .document(groupId)
        .set(
            CampaignGroup(
                id = groupId,
                name = "Quest",
                gameMasterId = "gm-1",
                participantIds = listOf("player-1"),
            )
        )
        .await()

    assertEquals(
        "User is not a member of this campaign group.",
        repository.recordActivity(groupId, "outsider", "Name", "Action").exceptionOrNull()?.message,
    )

    coEvery { diceRepository.getDiceRollsHistory(groupId) } returns
        Result.failure(IllegalStateException("Dice history unavailable"))
    assertEquals(
        "Dice history unavailable",
        repository.loadActivities(groupId).exceptionOrNull()?.message,
    )
  }

  @Test
  fun activityFeed_ignoresCorruptedDocuments() = runBlocking {
    firestore
        .collection("campaign_groups")
        .document(groupId)
        .set(
            CampaignGroup(
                id = groupId,
                name = "Quest",
                gameMasterId = "gm-1",
                participantIds = listOf("player-1"),
            )
        )
        .await()

    firestore
        .collection("campaign_groups")
        .document(groupId)
        .collection("activities")
        .document("broken")
        .set(mapOf("playerName" to "Broken"))
        .await()

    firestore
        .collection("campaign_groups")
        .document(groupId)
        .collection("activities")
        .document("valid")
        .set(
            mapOf(
                "userId" to "player-1",
                "playerName" to "Ari",
                "action" to "Opened the door",
                "diceResult" to "D20: 17",
                "timestamp" to Date(2000),
            )
        )
        .await()

    assertEquals(
        listOf("player-1"),
        repository.loadActivities(groupId).getOrThrow().map { it.userId },
    )
    assertEquals(
        listOf("player-1"),
        repository.observeActivities(groupId).waitFor { it.size == 1 }.map { it.userId },
    )
  }

  @Test
  fun activityMethods_mapDiceRollsIntoRecentActivityFeed() = runBlocking {
    val roll =
        DiceRollEntity(
            documentId = "roll-1",
            userId = "player-1",
            username = "Ari",
            diceType = "D20",
            modifier = 2,
            totalValue = 17,
            timestamp = Date(1234),
        )
    coEvery { diceRepository.getDiceRollsHistory(groupId) } returns Result.success(listOf(roll))
    every { diceRepository.getDiceRollsStream(groupId) } returns flowOf(listOf(roll))

    val expected =
        GameActivityEntry(
            "roll-1",
            "player-1",
            "Ari",
            "Rolled D20 with modifier 2",
            "17",
            1234,
        )
    assertEquals(expected, repository.loadActivities(groupId).getOrThrow().single())
    assertEquals(expected, repository.observeActivities(groupId).waitFor { it.size == 1 }.single())
  }

  @Test
  fun corruptedSessionFailsTheSessionStream() = runBlocking {
    val corruptedSessions =
        listOf(
            mapOf("enemies" to listOf("not an enemy")),
            mapOf("enemies" to listOf(mapOf("id" to "enemy-1", "initiative" to 3))),
            mapOf("npcs" to listOf(42)),
            mapOf("currentRound" to 0),
        )

    val failures = corruptedSessions.map { observeSessionFailure(it) }

    assertEquals(
        listOf(
            "Invalid enemy data.",
            "Invalid enemy character.",
            "Invalid NPC data.",
            "Combat round must be at least one.",
        ),
        failures,
    )
  }

  @Test
  fun sessionWithMissingFieldsUsesDefaults() = runBlocking {
    val npc =
        mapOf(
            "id" to "npc-1",
            "name" to "Guide",
            "portraitUrl" to "https://example.com/guide.png",
            "information" to "Helpful",
        )
    sessionDocument().set(mapOf("npcs" to listOf(npc))).await()

    val session = repository.observeSession(groupId).waitFor { it.npcs.isNotEmpty() }

    assertEquals(
        GameSessionSnapshot(
            isCombatActive = false,
            currentRound = 1,
            npcs = listOf(GameNpc("npc-1", "Guide", "https://example.com/guide.png", "Helpful")),
        ),
        session,
    )
  }

  @Test
  fun loreWithoutVisibilityIsPrivate() = runBlocking {
    firestore
        .collection("campaign_groups/$groupId/gm_lore")
        .document("old-map")
        .set(mapOf("content" to """{"version":1,"type":"lore","title":"Old Map"}"""))
        .await()

    val lore = repository.observeLore(groupId).waitFor { it.size == 1 }

    assertEquals(listOf(GameLoreEntry("old-map", LoreFile("Old Map", emptyList()), false)), lore)
    assertTrue(repository.observeSharedLore(groupId).waitFor { true }.isEmpty())
  }

  @Test
  fun activityWithoutTimestampIsShownFirst() = runBlocking {
    val activities = firestore.collection("campaign_groups/$groupId/activities")
    activities
        .document("older")
        .set(
            mapOf(
                "userId" to "player-1",
                "playerName" to "Ari",
                "action" to "Opened the door",
                "timestamp" to Date(2000),
            )
        )
        .await()
    activities
        .document("pending")
        .set(mapOf("userId" to "player-1", "playerName" to "Ari", "action" to "Drew a sword"))
        .await()

    val loaded = repository.loadActivities(groupId).getOrThrow()
    val observed = repository.observeActivities(groupId).waitFor { it.size == 2 }

    assertEquals(listOf("pending", "older"), loaded.map { it.id })
    assertEquals(listOf("pending", "older"), observed.map { it.id })
  }

  @Test
  fun gameMasterCanRecordActivityWithoutDice() = runBlocking {
    firestore
        .collection("campaign_groups")
        .document(groupId)
        .set(CampaignGroup(id = groupId, name = "Quest", gameMasterId = "gm-1"))
        .await()

    val result = repository.recordActivity(groupId, "gm-1", "Mira", "Started combat")

    assertTrue(result.isSuccess)
    val activity = repository.loadActivities(groupId).getOrThrow().single()
    assertEquals("gm-1", activity.userId)
    assertEquals(null, activity.diceResult)
  }

  private fun sessionDocument() =
      firestore.collection("campaign_groups/$groupId/gm_data").document("session")

  /** Stores [data] as the session document and returns the error the session stream fails with. */
  private suspend fun observeSessionFailure(data: Map<String, Any>): String? {
    sessionDocument().set(data).await()
    return runCatching { repository.observeSession(groupId).waitFor { true } }
        .exceptionOrNull()
        ?.message
  }
}
