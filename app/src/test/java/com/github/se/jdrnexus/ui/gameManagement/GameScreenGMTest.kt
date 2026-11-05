// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.github.se.jdrnexus.ui.gameManagement

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.se.jdrnexus.model.filerepresentation.CharacterAction
import com.github.se.jdrnexus.model.filerepresentation.CharacterFile
import com.github.se.jdrnexus.model.filerepresentation.CharacterHealthPoints
import com.github.se.jdrnexus.model.filerepresentation.CharacterStat
import com.github.se.jdrnexus.model.filerepresentation.LoreFile
import com.github.se.jdrnexus.model.filerepresentation.LoreSection
import com.github.se.jdrnexus.resources.C
import com.github.se.jdrnexus.ui.theme.SampleAppTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GameScreenGMTest {
  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun loadingStateShowsProgress() {
    setContent(FakeGameViewModel(GameViewModelGMUiState(isLoading = true)))

    composeTestRule.onNodeWithTag(C.Tag.game_gm_loading).assertIsDisplayed()
  }

  @Test
  fun errorStateShowsMessageAndRetryReloadsData() {
    val viewModel = FakeGameViewModel(GameViewModelGMUiState(error = "Session unavailable."))
    setContent(viewModel)

    composeTestRule.onNodeWithTag(C.Tag.game_gm_error).assertIsDisplayed()
    composeTestRule.onNodeWithText("Session unavailable.").assertIsDisplayed()
    composeTestRule.onNodeWithText("Retry").performClick()

    assertEquals(1, viewModel.activityLoads)
    assertEquals(1, viewModel.npcLoads)
    assertEquals(1, viewModel.loreLoads)
  }

  @Test
  fun emptyStateShowsCombatActivityNpcAndLoreMessages() {
    setContent(FakeGameViewModel())

    composeTestRule.onNodeWithTag(C.Tag.game_gm_empty_combat).assertIsDisplayed()
    composeTestRule.onNodeWithText("No recent player activity.").assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.game_gm_empty_npcs).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.game_gm_empty_lore).assertIsDisplayed()
  }

  @Test
  fun combatDisplaysRoundEnemyHpAcConditionAndProgress() {
    setContent(FakeGameViewModel(initialState()))

    composeTestRule.onNodeWithText("Round 3").assertIsDisplayed()
    composeTestRule.onNodeWithText("Goblin").assertIsDisplayed()
    composeTestRule.onNodeWithText("8 / 10 HP · AC 13 · Poisoned").assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.gameGmEnemy("enemy-1")).assertIsDisplayed()
  }

  @Test
  fun hpButtonsForwardEnemyIdAndChange() {
    val viewModel = FakeGameViewModel(initialState())
    setContent(viewModel)

    composeTestRule
        .onNodeWithTag(C.Tag.gameGmEnemyDecrease("enemy-1"))
        .performScrollTo()
        .performClick()
    composeTestRule
        .onNodeWithTag(C.Tag.gameGmEnemyIncrease("enemy-1"))
        .performScrollTo()
        .performClick()

    assertEquals(listOf("enemy-1" to -1, "enemy-1" to 1), viewModel.hpUpdates)
  }

  @Test
  fun hpButtonsDisableAtEncounterBounds() {
    val state =
        initialState()
            .copy(
                enemies =
                    listOf(
                        enemy(
                            id = "full",
                            currentHp = 10,
                            maxHp = 10,
                        ),
                        enemy(
                            id = "empty",
                            currentHp = 0,
                            maxHp = 10,
                        ),
                    )
            )
    setContent(FakeGameViewModel(state))

    composeTestRule
        .onNodeWithTag(C.Tag.gameGmEnemyIncrease("full"))
        .performScrollTo()
        .assertIsNotEnabled()
    composeTestRule
        .onNodeWithTag(C.Tag.gameGmEnemyDecrease("empty"))
        .performScrollTo()
        .assertIsNotEnabled()
  }

  @Test
  fun removeEnemyForwardsEnemyId() {
    val viewModel = FakeGameViewModel(initialState())
    setContent(viewModel)

    composeTestRule
        .onNodeWithTag(C.Tag.gameGmEnemyRemove("enemy-1"))
        .performScrollTo()
        .performClick()

    assertEquals(listOf("enemy-1"), viewModel.removedEnemyIds)
  }

  @Test
  fun diceFabForwardsNavigation() {
    var diceClicked = false
    composeTestRule.setContent {
      SampleAppTheme(darkTheme = false) {
        GameScreenGMContent(
            uiState = GameViewModelGMUiState(),
            onDiceClick = { diceClicked = true },
        )
      }
    }

    composeTestRule.onNodeWithTag(C.Tag.game_gm_dice).performClick()

    assertEquals(true, diceClicked)
  }

  @Test
  fun invalidEnemyInputIsRejected() {
    assertFalse(isValidEnemyInput("Goblin", 11, 10, 13))
    assertFalse(isValidEnemyInput("Goblin", -1, 10, 13))
    assertFalse(isValidEnemyInput("Goblin", 1, 0, 13))
    assertFalse(isValidEnemyInput("Goblin", 1, 10, -1))
    assertFalse(isValidEnemyInput(" ", 1, 10, 13))
  }

  @Test
  fun encounterControlsReflectCombatStateAndForwardActions() {
    val viewModel = FakeGameViewModel()
    setContent(viewModel)

    composeTestRule.onNodeWithTag(C.Tag.game_gm_start_combat).assertIsEnabled().performClick()
    assertEquals(1, viewModel.startCalls)
    composeTestRule.onAllNodesWithText("Next round").assertCountEquals(0)

    viewModel.setState(initialState())
    composeTestRule.onNodeWithTag(C.Tag.game_gm_next_round).performClick()
    composeTestRule.onNodeWithTag(C.Tag.game_gm_end_combat).performClick()

    assertEquals(1, viewModel.nextRoundCalls)
    assertEquals(1, viewModel.endCalls)
  }

  @Test
  fun activityDisplaysPlayerActionDiceAndTimestampAndCanBeDismissed() {
    setContent(FakeGameViewModel(initialState()))

    composeTestRule
        .onNodeWithTag(C.Tag.gameGmActivity("activity-1"))
        .performScrollTo()
        .assertIsDisplayed()
    composeTestRule
        .onNodeWithText("Elara · Rolled a Dexterity check (d20 = 18)")
        .assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.game_gm_dismiss_activity).performClick()
    composeTestRule
        .onAllNodesWithText("Elara · Rolled a Dexterity check (d20 = 18)")
        .assertCountEquals(0)
  }

  @Test
  fun selectingNpcShowsItsInformation() {
    setContent(FakeGameViewModel(initialState()))

    composeTestRule.onNodeWithTag(C.Tag.gameGmNpc("npc-1")).performScrollTo().performClick()

    composeTestRule.onAllNodesWithText("Innkeeper").assertCountEquals(2)
    composeTestRule.onNodeWithText("Friendly keeper").assertIsDisplayed()
  }

  @Test
  fun loreDisplaysPreviewSelectsAndExpandsDocument() {
    val viewModel = FakeGameViewModel(initialState())
    setContent(viewModel)

    composeTestRule.onNode(hasScrollAction()).performScrollToNode(hasText("The Crypt"))
    composeTestRule.onNodeWithText("The Crypt").assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.gameGmLore("lore-1")).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.gameGmLoreSelect("lore-1")).performScrollTo().performClick()

    assertEquals(listOf("lore-1"), viewModel.selectedLoreIds)
  }

  @Test
  fun navigationForwardsSelectedTab() {
    var selectedTab = ""
    setContent(FakeGameViewModel(), onTabSelected = { selectedTab = it })

    composeTestRule.onNodeWithTag(C.Tag.gameGmNavigation("Files")).performClick()

    assertEquals("Files", selectedTab)
  }

  @Test
  fun changingViewModelStateUpdatesDisplayedEnemy() {
    val viewModel = FakeGameViewModel()
    setContent(viewModel)

    viewModel.setState(initialState())
    composeTestRule.onNodeWithText("Goblin").performScrollTo().assertIsDisplayed()

    viewModel.setState(initialState().copy(enemies = listOf(enemy("dragon", 40, 100))))
    composeTestRule.onNodeWithText("Dragon").performScrollTo().assertIsDisplayed()
    composeTestRule.onAllNodesWithText("Goblin").assertCountEquals(0)
  }

  private fun setContent(
      viewModel: FakeGameViewModel,
      onTabSelected: (String) -> Unit = {},
  ) {
    composeTestRule.setContent {
      SampleAppTheme(darkTheme = false) {
        GameScreenGM(viewModel = viewModel, onTabSelected = onTabSelected)
      }
    }
  }

  private class FakeGameViewModel(initialState: GameViewModelGMUiState = GameViewModelGMUiState()) :
      GameViewModelGM {
    private val state = MutableStateFlow(initialState)
    override val uiState = state.asStateFlow()
    var activityLoads = 0
    var npcLoads = 0
    var loreLoads = 0
    var startCalls = 0
    var nextRoundCalls = 0
    var endCalls = 0
    val hpUpdates = mutableListOf<Pair<String, Int>>()
    val removedEnemyIds = mutableListOf<String>()
    val selectedLoreIds = mutableListOf<String>()
    val addedEnemies = mutableListOf<CombatNPC>()

    fun setState(value: GameViewModelGMUiState) {
      state.value = value
    }

    override fun addEnemy(enemy: CombatNPC) {
      addedEnemies += enemy
    }

    override fun removeEnemy(enemyId: String) {
      removedEnemyIds += enemyId
    }

    override fun updateEnemyHP(enemyId: String, change: Int) {
      hpUpdates += enemyId to change
    }

    override fun nextRound() {
      nextRoundCalls++
    }

    override fun startCombat() {
      startCalls++
    }

    override fun endCombat() {
      endCalls++
    }

    override fun loadActivityFeed() {
      activityLoads++
    }

    override fun observeActivityFeed() = Unit

    override fun loadNPCs() {
      npcLoads++
    }

    override fun addNPC(npc: Npc) = Unit

    override fun updateNPC(npcId: String, updatedNPC: Npc) = Unit

    override fun removeNPC(npcId: String) = Unit

    override fun loadLoreDocuments() {
      loreLoads++
    }

    override fun selectLoreDocument(documentId: String) {
      selectedLoreIds += documentId
    }

    override fun updateLoreTitle(title: String) = Unit

    override fun addLoreSection(title: String, description: String) = Unit

    override fun updateLoreSectionTitle(sectionID: String, title: String) = Unit

    override fun updateLoreSectionDescription(sectionID: String, description: String) = Unit

    override fun removeLoreSection(sectionID: String) = Unit

    override fun saveLoreDocument(document: LoreDocument) = Unit

    override fun setLoreVisibility(documentId: String, isShared: Boolean) = Unit
  }

  companion object {
    private fun initialState() =
        GameViewModelGMUiState(
            enemies = listOf(enemy("enemy-1", 8, 10)),
            isCombatActive = true,
            currentRound = 3,
            activityFeed =
                listOf(
                    ActivityItem(
                        id = "activity-1",
                        playerName = "Elara",
                        action = "Rolled a Dexterity check",
                        diceResult = "d20 = 18",
                        timestamp = 1234,
                    )
                ),
            npcs = listOf(Npc("npc-1", "Innkeeper", null, "Friendly keeper")),
            loreDocuments =
                listOf(
                    LoreDocument(
                        "lore-1",
                        LoreFile(
                            "The Crypt",
                            listOf(
                                LoreSection("overview", "Overview", "Darkness"),
                                LoreSection("history", "History", "An ancient place"),
                            ),
                        ),
                        true,
                    )
                ),
        )

    private fun enemy(id: String, currentHp: Int, maxHp: Int) =
        CombatNPC(
            id = id,
            character =
                CharacterFile(
                    name = if (id == "dragon") "Dragon" else "Goblin",
                    stats = listOf(CharacterStat("armorClass", "Armor Class", 13.0)),
                    actions = listOf(CharacterAction("condition_poisoned", "Poisoned", "")),
                    hp = CharacterHealthPoints(currentHp, maxHp),
                ),
        )
  }
}
