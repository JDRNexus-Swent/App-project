// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.github.se.jdrnexus.viewmodel

import androidx.lifecycle.ViewModel
import com.github.se.jdrnexus.model.filerepresentation.CharacterAction
import com.github.se.jdrnexus.model.filerepresentation.CharacterFile
import com.github.se.jdrnexus.model.filerepresentation.CharacterHealthPoints
import com.github.se.jdrnexus.model.filerepresentation.CharacterStat
import com.github.se.jdrnexus.model.filerepresentation.LoreFile
import com.github.se.jdrnexus.model.filerepresentation.LoreSection
import com.github.se.jdrnexus.model.gameManagement.GameActivityEntry
import com.github.se.jdrnexus.model.gameManagement.GameEnemy
import com.github.se.jdrnexus.model.gameManagement.GameLoreEntry
import com.github.se.jdrnexus.model.gameManagement.GameNpc
import com.github.se.jdrnexus.model.gameManagement.GameSessionRepository
import com.github.se.jdrnexus.model.gameManagement.GameSessionSnapshot
import com.github.se.jdrnexus.ui.gameManagement.Enemy
import com.github.se.jdrnexus.ui.gameManagement.GameViewModelGMViewModel
import com.github.se.jdrnexus.ui.gameManagement.GameViewModelGMViewModelFactory
import com.github.se.jdrnexus.ui.gameManagement.LoreDocument
import com.github.se.jdrnexus.ui.gameManagement.Npc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GameGMViewModelTest {
  private val testDispatcher = StandardTestDispatcher()
  private lateinit var repository: FakeGameSessionRepository
  private val groupId = "group_123"

  @Before
  fun setup() {
    Dispatchers.setMain(testDispatcher)
    repository = FakeGameSessionRepository()
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  // Loads initial combat, NPC, and lore state from the repositories.
  fun initialStateLoadsFromRepositories() = runTest {
    repository.session.value =
        GameSessionSnapshot(
            enemies =
                listOf(
                    GameEnemy(
                        id = "e1",
                        character =
                            CharacterFile(
                                name = "Goblin",
                                stats =
                                    listOf(
                                        CharacterStat("armorClass", "Armor Class", 13.0),
                                        CharacterStat("strength", "Strength", 8.0),
                                    ),
                                actions =
                                    listOf(
                                        CharacterAction("bite", "Bite", "Deals damage"),
                                        CharacterAction("condition_poisoned", "Poisoned", ""),
                                    ),
                                hp = CharacterHealthPoints(8, 10),
                            ),
                        initiative = 12,
                    )
                ),
            isCombatActive = true,
            currentRound = 3,
            npcs = listOf(GameNpc("n1", "Innkeeper", null, "Friendly")),
        )
    val loreFile =
        LoreFile(
            "The Crypt",
            listOf(
                LoreSection("overview", "Overview", "Darkness"),
                LoreSection("history", "History", "An ancient place"),
            ),
        )
    repository.lore.value = listOf(GameLoreEntry("l1", loreFile, true))

    val viewModel = createViewModel()
    advanceUntilIdle()

    val state = viewModel.uiState.value
    assertEquals(
        repository.session.value.enemies.single().let { Enemy(it.id, it.character, it.initiative) },
        state.enemies.single(),
    )
    assertEquals("Poisoned", state.enemies.single().character.actions.last().name)
    assertEquals(13.0, state.enemies.single().character.stats.first().value, 0.0)
    assertTrue(state.isCombatActive)
    assertEquals(3, state.currentRound)
    assertEquals(Npc("n1", "Innkeeper", null, "Friendly"), state.npcs.single())
    assertEquals(LoreDocument("l1", loreFile, true), state.loreDocuments.single())
    assertFalse(state.isLoading)
    Assert.assertNull(state.error)
  }

  @Test
  // Creates the GM ViewModel and rejects unsupported ViewModel types.
  fun factoryCreatesViewModelAndRejectsUnknownTypes() {
    val factory = GameViewModelGMViewModelFactory(repository, groupId)
    Assert.assertNotNull(factory.create(GameViewModelGMViewModel::class.java))

    class DummyViewModel : ViewModel()
    try {
      factory.create(DummyViewModel::class.java)
      Assert.fail("An IllegalArgumentException should have been thrown")
    } catch (failure: IllegalArgumentException) {
      assertTrue(failure.message.orEmpty().contains("Unknown ViewModel class"))
    }
  }

  @Test
  // Persists combat changes and reflects them in the ViewModel state.
  fun combatChangesPersistAndSynchronizeFromRepository() = runTest {
    val viewModel = createViewModel()
    advanceUntilIdle()
    val enemy =
        Enemy(
            id = "e1",
            character =
                CharacterFile(
                    name = "Goblin",
                    stats = listOf(CharacterStat("armorClass", "Armor Class", 14.0)),
                    actions =
                        listOf(
                            CharacterAction("bite", "Bite", "Deals damage"),
                            CharacterAction("condition_prone", "Prone", ""),
                        ),
                    hp = CharacterHealthPoints(10, 10),
                ),
            initiative = 12,
        )

    viewModel.addEnemy(enemy)
    advanceUntilIdle()
    assertEquals(enemy, viewModel.uiState.value.enemies.single())
    assertEquals(enemy.toSnapshotEnemy(), repository.session.value.enemies.single())
    assertEquals(
        enemy.character.actions,
        repository.session.value.enemies.single().character.actions,
    )

    viewModel.updateEnemyHP("e1", -4)
    advanceUntilIdle()
    assertEquals(6, viewModel.uiState.value.enemies.single().hp)
    assertEquals("Prone", viewModel.uiState.value.enemies.single().character.actions.last().name)

    viewModel.updateEnemyHP("e1", 100)
    advanceUntilIdle()
    assertEquals(10, viewModel.uiState.value.enemies.single().hp)

    viewModel.startCombat()
    viewModel.nextRound()
    advanceUntilIdle()
    assertTrue(viewModel.uiState.value.isCombatActive)
    assertEquals(2, viewModel.uiState.value.currentRound)

    viewModel.endCombat()
    viewModel.removeEnemy("e1")
    advanceUntilIdle()
    assertFalse(viewModel.uiState.value.isCombatActive)
    assertTrue(viewModel.uiState.value.enemies.isEmpty())
    assertTrue(repository.savedSessions.isNotEmpty())
  }

  @Test
  // Rejects invalid combat changes without persisting them.
  fun invalidCombatOperationsAreRejected() = runTest {
    val viewModel = createViewModel()
    advanceUntilIdle()
    viewModel.addEnemy(
        Enemy(
            id = "bad",
            character =
                CharacterFile(
                    "Goblin",
                    emptyList(),
                    emptyList(),
                    CharacterHealthPoints(12, 10),
                ),
        )
    )
    advanceUntilIdle()
    assertTrue(viewModel.uiState.value.enemies.isEmpty())
    assertTrue(viewModel.uiState.value.error.orEmpty().contains("HP"))
    assertTrue(repository.savedSessions.isEmpty())

    viewModel.startCombat()
    advanceUntilIdle()
    viewModel.nextRound()
    advanceUntilIdle()
    viewModel.startCombat()
    advanceUntilIdle()
    assertEquals(2, viewModel.uiState.value.currentRound)
    assertEquals("Combat is already active.", viewModel.uiState.value.error)
  }

  @Test
  // Rejects enemies with a negative armor class.
  fun negativeArmorClassIsRejected() = runTest {
    val viewModel = createViewModel()
    advanceUntilIdle()

    viewModel.addEnemy(
        Enemy(
            id = "bad",
            character =
                CharacterFile(
                    name = "Goblin",
                    stats = listOf(CharacterStat("armorClass", "Armor Class", -1.0)),
                    actions = emptyList(),
                    hp = CharacterHealthPoints(10, 10),
                ),
        )
    )
    advanceUntilIdle()

    assertTrue(viewModel.uiState.value.enemies.isEmpty())
    assertEquals("Enemy AC cannot be negative.", viewModel.uiState.value.error)
  }

  @Test
  // Keeps combat state unchanged and exposes errors when session writes fail.
  fun failedSessionWritePreservesStateAndExposesError() = runTest {
    val viewModel = createViewModel()
    advanceUntilIdle()
    repository.sessionWriteFailure = IllegalStateException("Permission denied")

    viewModel.startCombat()
    advanceUntilIdle()

    assertFalse(viewModel.uiState.value.isCombatActive)
    assertEquals("Permission denied", viewModel.uiState.value.error)
  }

  @Test
  // Exposes session observation failures and ends the loading state.
  fun sessionObservationFailureIsExposedAndLoadingFinishes() = runTest {
    repository.sessionObservationFailure = IllegalStateException("Session unavailable")

    val viewModel = createViewModel()
    advanceUntilIdle()

    assertFalse(viewModel.uiState.value.isLoading)
    assertEquals("Session unavailable", viewModel.uiState.value.error)
  }

  @Test
  // Retries session observation through loadNPCs and loads the NPC list.
  fun loadNPCsRetriesObservationAndLoadsNPCs() = runTest {
    repository.sessionObservationFailure = IllegalStateException("Session unavailable")
    val viewModel = createViewModel()
    advanceUntilIdle()
    assertEquals("Session unavailable", viewModel.uiState.value.error)

    repository.sessionObservationFailure = null
    repository.session.value =
        GameSessionSnapshot(npcs = listOf(GameNpc("n1", "Innkeeper", null, "Friendly")))
    viewModel.loadNPCs()
    advanceUntilIdle()

    assertEquals(listOf(Npc("n1", "Innkeeper", null, "Friendly")), viewModel.uiState.value.npcs)
    assertFalse(viewModel.uiState.value.isLoading)
  }

  @Test
  // Persists NPC additions, updates, and removals through the session flow.
  fun npcChangesPersistThroughSessionFlow() = runTest {
    val viewModel = createViewModel()
    advanceUntilIdle()
    val npc = Npc("n1", "Innkeeper", "portrait.png", "Friendly")

    viewModel.addNPC(npc)
    advanceUntilIdle()
    assertEquals(npc, viewModel.uiState.value.npcs.single())
    assertEquals(npc.toSnapshotNpc(), repository.session.value.npcs.single())

    viewModel.updateNPC("n1", npc.copy(information = "Suspicious"))
    advanceUntilIdle()
    assertEquals("Suspicious", viewModel.uiState.value.npcs.single().information)

    viewModel.removeNPC("n1")
    advanceUntilIdle()
    assertTrue(viewModel.uiState.value.npcs.isEmpty())
  }

  @Test
  // Reports invalid and duplicate NPC additions without saving them.
  fun invalidAndDuplicateNpcAdditionsAreRejected() = runTest {
    val viewModel = createViewModel()
    advanceUntilIdle()

    viewModel.addNPC(Npc("", "Innkeeper", null, "Friendly"))
    advanceUntilIdle()
    assertEquals("NPC ID cannot be empty.", viewModel.uiState.value.error)

    viewModel.addNPC(Npc("n1", " ", null, "Friendly"))
    advanceUntilIdle()
    assertEquals("NPC name cannot be empty.", viewModel.uiState.value.error)

    val npc = Npc("n1", "Innkeeper", null, "Friendly")
    viewModel.addNPC(npc)
    advanceUntilIdle()
    viewModel.addNPC(npc)
    advanceUntilIdle()

    assertEquals("NPC already exists.", viewModel.uiState.value.error)
    assertEquals(listOf(npc), viewModel.uiState.value.npcs)
    assertEquals(1, repository.savedSessions.size)
  }

  @Test
  // Reports invalid and missing NPC updates without changing the saved NPC.
  fun invalidAndMissingNpcUpdatesAreRejected() = runTest {
    val viewModel = createViewModel()
    advanceUntilIdle()
    val npc = Npc("n1", "Innkeeper", null, "Friendly")
    viewModel.addNPC(npc)
    advanceUntilIdle()

    viewModel.updateNPC("n1", npc.copy(id = "n2"))
    advanceUntilIdle()
    assertEquals("NPC ID cannot be changed.", viewModel.uiState.value.error)

    viewModel.updateNPC("n1", npc.copy(name = " "))
    advanceUntilIdle()
    assertEquals("NPC name cannot be empty.", viewModel.uiState.value.error)

    viewModel.updateNPC("missing", npc.copy(id = "missing"))
    advanceUntilIdle()

    assertEquals("NPC was not found.", viewModel.uiState.value.error)
    assertEquals(listOf(npc), viewModel.uiState.value.npcs)
    assertEquals(listOf(npc.toSnapshotNpc()), repository.session.value.npcs)
    assertEquals(1, repository.savedSessions.size)
  }

  @Test
  // Persists lore documents and synchronizes their visibility and selection.
  fun loreSavesVisibilityAndSelectionAfterPersistence() = runTest {
    val viewModel = createViewModel()
    advanceUntilIdle()
    val lore =
        LoreDocument(
            "l1",
            LoreFile(
                "The Crypt",
                listOf(
                    LoreSection("overview", "Overview", "Darkness"),
                    LoreSection("history", "History", "An ancient place"),
                ),
            ),
            false,
        )

    viewModel.saveLoreDocument(lore)
    advanceUntilIdle()
    assertEquals(lore, viewModel.uiState.value.loreDocuments.single())
    assertEquals(lore.toRepositoryLore(), repository.lore.value.single())
    assertEquals(lore.lore.sections, repository.lore.value.single().lore.sections)

    viewModel.selectLoreDocument("l1")
    viewModel.setLoreVisibility("l1", true)
    advanceUntilIdle()
    assertTrue(viewModel.uiState.value.selectedLoreDocument?.isShared == true)
    assertTrue(repository.lore.value.single().isShared)
  }

  @Test
  // Preserves lore state and exposes the error when a lore write fails.
  fun loreWriteFailurePreservesStateAndExposesError() = runTest {
    val viewModel = createViewModel()
    advanceUntilIdle()
    repository.loreWriteFailure = IllegalStateException("Lore write rejected")

    viewModel.saveLoreDocument(
        LoreDocument(
            "l1",
            LoreFile("The Crypt", listOf(LoreSection("overview", "Overview", "Darkness"))),
            false,
        )
    )
    advanceUntilIdle()

    assertTrue(viewModel.uiState.value.loreDocuments.isEmpty())
    assertEquals("Lore write rejected", viewModel.uiState.value.error)
  }

  @Test
  // Preserves sharing state and exposes the error when visibility updates fail.
  fun loreVisibilityFailurePreservesSharingState() = runTest {
    repository.lore.value =
        listOf(
            GameLoreEntry(
                "l1",
                LoreFile("The Crypt", listOf(LoreSection("overview", "Overview", "Darkness"))),
                false,
            )
        )
    val viewModel = createViewModel()
    advanceUntilIdle()
    repository.loreWriteFailure = IllegalStateException("Visibility update rejected")

    viewModel.setLoreVisibility("l1", true)
    advanceUntilIdle()

    assertFalse(viewModel.uiState.value.loreDocuments.single().isShared)
    assertEquals("Visibility update rejected", viewModel.uiState.value.error)
  }

  @Test
  // Loads dice rolls and observes new player actions in the activity feed.
  fun activityFeedLoadsDiceRollsAndObservesPlayerActions() = runTest {
    val viewModel = createViewModel()
    repository.activityHistory =
        listOf(
            GameActivityEntry(
                id = "roll-1",
                userId = "player-1",
                playerName = "Player",
                action = "Rolled D20 with modifier 2",
                diceResult = "17",
                timestamp = 1234,
            )
        )
    repository.activityEntries.value = repository.activityHistory
    advanceUntilIdle()

    viewModel.loadActivityFeed()
    advanceUntilIdle()
    assertEquals("Player", viewModel.uiState.value.activityFeed.single().playerName)
    assertEquals("17", viewModel.uiState.value.activityFeed.single().diceResult)

    repository.recordActivity(
        groupId = groupId,
        userId = "player-1",
        playerName = "Player",
        action = "Opened the ancient door",
    )
    advanceUntilIdle()
    assertEquals(2, viewModel.uiState.value.activityFeed.size)
    val playerAction =
        viewModel.uiState.value.activityFeed.single { it.action == "Opened the ancient door" }
    Assert.assertNull(playerAction.diceResult)
  }

  @Test
  // Exposes repository failures encountered while loading activity history.
  fun activityFeedLoadFailureIsExposed() = runTest {
    val viewModel = createViewModel()
    advanceUntilIdle()
    repository.activityFailure = IllegalStateException("Dice history unavailable")

    viewModel.loadActivityFeed()
    advanceUntilIdle()

    assertEquals("Dice history unavailable", viewModel.uiState.value.error)
  }

  private fun createViewModel() = GameViewModelGMViewModel(repository, groupId)

  private class FakeGameSessionRepository : GameSessionRepository {
    val session = MutableStateFlow(GameSessionSnapshot())
    val lore = MutableStateFlow<List<GameLoreEntry>>(emptyList())
    val activityEntries = MutableStateFlow<List<GameActivityEntry>>(emptyList())
    val savedSessions = mutableListOf<GameSessionSnapshot>()
    var sessionWriteFailure: Throwable? = null
    var loreWriteFailure: Throwable? = null
    var activityFailure: Throwable? = null
    var sessionObservationFailure: Throwable? = null
    var activityHistory: List<GameActivityEntry> = emptyList()

    override fun observeSession(groupId: String): Flow<GameSessionSnapshot> =
        sessionObservationFailure?.let { failure -> flow { throw failure } }
            ?: session.asStateFlow()

    override suspend fun saveSession(
        groupId: String,
        session: GameSessionSnapshot,
    ): Result<Unit> {
      sessionWriteFailure?.let {
        return Result.failure(it)
      }
      savedSessions += session
      this.session.value = session
      return Result.success(Unit)
    }

    override fun observeLore(groupId: String): Flow<List<GameLoreEntry>> = lore.asStateFlow()

    override fun observeSharedLore(groupId: String): Flow<List<GameLoreEntry>> =
        lore.asStateFlow().map { entries -> entries.filter { it.isShared } }

    override suspend fun saveLore(groupId: String, lore: GameLoreEntry): Result<Unit> {
      loreWriteFailure?.let {
        return Result.failure(it)
      }
      this.lore.value = this.lore.value.filterNot { it.id == lore.id } + lore
      return Result.success(Unit)
    }

    override suspend fun setLoreVisibility(
        groupId: String,
        documentId: String,
        isShared: Boolean,
    ): Result<Unit> {
      loreWriteFailure?.let {
        return Result.failure(it)
      }
      lore.value = lore.value.map { if (it.id == documentId) it.copy(isShared = isShared) else it }
      return Result.success(Unit)
    }

    override suspend fun loadActivities(groupId: String): Result<List<GameActivityEntry>> =
        activityFailure?.let { Result.failure(it) } ?: Result.success(activityHistory)

    override fun observeActivities(groupId: String): Flow<List<GameActivityEntry>> =
        activityEntries.asStateFlow()

    override suspend fun recordActivity(
        groupId: String,
        userId: String,
        playerName: String,
        action: String,
        diceResult: String?,
    ): Result<Unit> {
      if (userId.isBlank() || playerName.isBlank() || action.isBlank()) {
        return Result.failure(IllegalArgumentException("Invalid activity."))
      }
      val entry =
          GameActivityEntry(
              id = "action-${activityEntries.value.size + 1}",
              userId = userId,
              playerName = playerName,
              action = action,
              diceResult = diceResult,
              timestamp = activityEntries.value.size.toLong(),
          )
      activityEntries.value = activityEntries.value + entry
      return Result.success(Unit)
    }
  }
}

private fun Enemy.toSnapshotEnemy() = GameEnemy(id, character, initiative)

private fun Npc.toSnapshotNpc() = GameNpc(id, name, portraitUrl, information)

private fun LoreDocument.toRepositoryLore() = GameLoreEntry(id, lore, isShared)
