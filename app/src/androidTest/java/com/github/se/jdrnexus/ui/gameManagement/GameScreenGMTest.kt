// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.github.se.jdrnexus.ui.gameManagement

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.se.jdrnexus.model.filerepresentation.CharacterAction
import com.github.se.jdrnexus.model.filerepresentation.CharacterFile
import com.github.se.jdrnexus.model.filerepresentation.CharacterHealthPoints
import com.github.se.jdrnexus.model.filerepresentation.CharacterStat
import com.github.se.jdrnexus.model.filerepresentation.LoreFile
import com.github.se.jdrnexus.model.filerepresentation.LoreSection
import com.github.se.jdrnexus.resources.C
import com.github.se.jdrnexus.ui.theme.SampleAppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GameScreenGMTest {
  @get:Rule val composeTestRule = createComposeRule()

  // Verifies the empty combat state and dispatches the start-combat action.
  @Test
  fun emptyCombatShowsMessageAndStartsCombat() {
    var started = 0
    setContent(GameViewModelGMUiState(), onStartCombat = { started++ })

    composeTestRule.onNodeWithTag(C.Tag.game_gm_empty_combat).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.game_gm_start_combat).assertIsDisplayed().performClick()
    assertEquals(1, started)
  }

  // Verifies enemy details and the row callbacks for hit points and removal.
  @Test
  fun enemyDetailsAndCombatCallbacksAreHandled() {
    val enemy = testEnemy()
    var hpChanges = mutableListOf<Pair<String, Int>>()
    var removed = ""
    setContent(
        GameViewModelGMUiState(enemies = listOf(enemy)),
        onUpdateEnemyHp = { id, change -> hpChanges += id to change },
        onRemoveEnemy = { removed = it },
    )

    composeTestRule.onNodeWithTag(C.Tag.gameGmEnemy(enemy.id)).performScrollTo()
    composeTestRule.onNodeWithText("Goblin").assertIsDisplayed()
    composeTestRule.onNodeWithText("7").assertIsDisplayed()
    composeTestRule.onNodeWithText("/ 12 HP").assertIsDisplayed()
    composeTestRule.onNodeWithText("Poisoned · AC 14").assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.gameGmEnemyIncrease(enemy.id)).performClick()
    composeTestRule.onNodeWithTag(C.Tag.gameGmEnemyDecrease(enemy.id)).performClick()
    composeTestRule.onNodeWithTag(C.Tag.gameGmEnemyRemove(enemy.id)).performClick()
    assertEquals(listOf(enemy.id to 1, enemy.id to -1), hpChanges)
    assertEquals(enemy.id, removed)
  }

  // Verifies active combat exposes and dispatches next-round and end-combat actions.
  @Test
  fun activeCombatShowsRoundControls() {
    var nextRound = 0
    var ended = 0
    setContent(
        GameViewModelGMUiState(isCombatActive = true),
        onNextRound = { nextRound++ },
        onEndCombat = { ended++ },
    )
    composeTestRule.onNodeWithTag(C.Tag.game_gm_next_round).performClick()
    composeTestRule.onNodeWithTag(C.Tag.game_gm_end_combat).performClick()
    assertEquals(1, nextRound)
    assertEquals(1, ended)
  }

  // Verifies that the add-enemy dialog can be cancelled and submits valid values.
  @Test
  fun addEnemyDialogOpensClosesAndSubmitsValidEnemy() {
    var added: CombatNPC? = null
    setContent(GameViewModelGMUiState(), onAddEnemy = { added = it })

    composeTestRule.onNodeWithTag(C.Tag.game_gm_add_enemy).performClick()
    composeTestRule.onNodeWithTag(C.Tag.game_gm_add_enemy_dialog).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.game_gm_enemy_name).performTextInput("Orc")
    composeTestRule.onNodeWithText("Add").assertIsEnabled().performClick()
    assertEquals("Orc", added?.name)
    assertEquals(10, added?.hp)
    assertEquals(10, added?.maxHp)
    composeTestRule.onAllNodesWithTag(C.Tag.game_gm_add_enemy_dialog).assertCountEquals(0)

    composeTestRule.onNodeWithTag(C.Tag.game_gm_add_enemy).performClick()
    composeTestRule.onNodeWithText("Cancel").performClick()
    composeTestRule.onAllNodesWithTag(C.Tag.game_gm_add_enemy_dialog).assertCountEquals(0)
  }

  // Verifies invalid enemy input disables submission.
  @Test
  fun invalidEnemyInputDisablesAddButton() {
    setContent(GameViewModelGMUiState())

    composeTestRule.onNodeWithTag(C.Tag.game_gm_add_enemy).performClick()
    composeTestRule.onNodeWithText("Add").assertIsNotEnabled()
    composeTestRule.onNodeWithTag(C.Tag.game_gm_enemy_name).performTextInput("Orc")
    composeTestRule.onNodeWithTag(C.Tag.game_gm_enemy_max_hp).performTextClearance()
    composeTestRule.onNodeWithTag(C.Tag.game_gm_enemy_max_hp).performTextInput("0")
    composeTestRule.onNodeWithText("Add").assertIsNotEnabled()
    assertTrue(!isValidEnemyInput("Orc", 10, 0, 10))
  }

  // Verifies the loading state displays its progress indicator.
  @Test
  fun loadingStateShowsProgress() {
    setContent(GameViewModelGMUiState(isLoading = true))
    composeTestRule.onNodeWithTag(C.Tag.game_gm_loading).assertIsDisplayed()
  }

  // Verifies the error state displays its message and dispatches retry.
  @Test
  fun errorStateShowsMessageAndRetries() {
    var retries = 0
    setContent(GameViewModelGMUiState(error = "Unable to load"), onRetry = { retries++ })
    composeTestRule.onNodeWithTag(C.Tag.game_gm_error).assertIsDisplayed()
    composeTestRule.onNodeWithText("Unable to load").assertIsDisplayed()
    composeTestRule.onNodeWithText("Retry").performClick()
    assertEquals(1, retries)
  }

  // Verifies an empty activity feed displays its empty-state message.
  @Test
  fun emptyActivityFeedShowsMessage() {
    setContent(GameViewModelGMUiState())
    composeTestRule.onNodeWithText("No recent player activity.").assertIsDisplayed()
  }

  // Verifies activity rendering, dice results, and dismiss behavior.
  @Test
  fun activityFeedDisplaysEntriesAndCanBeDismissed() {
    setContent(
        GameViewModelGMUiState(
            activityFeed = listOf(ActivityItem("a1", "Mira", "rolled", "1d20 = 18"))
        )
    )

    composeTestRule.onNodeWithText("Mira · rolled (1d20 = 18)").assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.game_gm_hide_activity).performClick()
    composeTestRule.onAllNodesWithText("Activity Feed").assertCountEquals(0)
  }

  // Verifies that an empty NPC list displays its empty-state message.
  @Test
  fun emptyNpcListShowsMessage() {
    setContent(GameViewModelGMUiState())
    composeTestRule.onNodeWithTag(C.Tag.game_gm_empty_npcs).assertIsDisplayed()
  }

  // Verifies NPC names and the information dialog lifecycle.
  @Test
  fun npcListDisplaysNamesAndInformationDialog() {
    val npc = Npc("npc-1", "Eldrin", null, "A quiet guide.")
    setContent(GameViewModelGMUiState(npcs = listOf(npc)))
    composeTestRule.onNodeWithTag(C.Tag.gameGmNpc(npc.id)).performClick()
    composeTestRule.onAllNodesWithText("Eldrin").assertCountEquals(2)
    composeTestRule.onNodeWithText("A quiet guide.").assertIsDisplayed()
    composeTestRule.onNodeWithText("Close").performClick()
    composeTestRule.onAllNodesWithText("A quiet guide.").assertCountEquals(0)
  }

  // Verifies that an empty lore list displays its empty-state message.
  @Test
  fun emptyLoreListShowsMessage() {
    setContent(GameViewModelGMUiState())
    composeTestRule.onNodeWithTag(C.Tag.game_gm_empty_lore).assertIsDisplayed()
  }

  // Verifies lore expansion, collapse, and selection callback.
  @Test
  fun loreDocumentsExpandCollapseAndSelect() {
    val lore = testLore()
    var selected = ""
    setContent(
        GameViewModelGMUiState(loreDocuments = listOf(lore)),
        onSelectLore = { selected = it },
    )
    composeTestRule.onNodeWithText("Ancient Ruins").assertIsDisplayed()
    composeTestRule.onNodeWithText("Read").performClick()
    composeTestRule.onNodeWithText("History: Fallen kingdom").assertIsDisplayed()
    composeTestRule.onNodeWithText("Collapse").performClick()
    composeTestRule.onNodeWithText("Read").assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.gameGmLoreSelect(lore.id)).performClick()
    assertEquals(lore.id, selected)
  }

  // Verifies shared bottom navigation and dice FAB callback dispatch.
  @Test
  fun navigationBarAndDiceFabDispatchCallbacks() {
    var selectedTab = ""
    var diceClicks = 0
    setContent(
        GameViewModelGMUiState(),
        onTabSelected = { selectedTab = it },
        onDiceClick = { diceClicks++ },
    )

    composeTestRule.onNodeWithTag(C.Tag.shared_bottom_bar).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.bottomTab("map")).performClick()
    composeTestRule.onNodeWithTag(C.Tag.game_gm_dice).performClick()
    assertEquals("Map", selectedTab)
    assertEquals(1, diceClicks)
  }

  // Verifies every dashboard section can be hidden and restored through the shared area.
  @Test
  fun dashboardSectionsCanBeDismissedAndRestored() {
    setContent(GameViewModelGMUiState())

    composeTestRule.onNodeWithTag(C.Tag.game_gm_hide_combat).performClick()
    composeTestRule.onNodeWithTag(C.Tag.game_gm_restore_combat).performClick()
    composeTestRule.onNodeWithTag(C.Tag.game_gm_hide_activity).performClick()
    composeTestRule.onNodeWithTag(C.Tag.game_gm_restore_activity).performClick()
    composeTestRule.onNodeWithTag(C.Tag.game_gm_hide_npcs).performClick()
    composeTestRule.onNodeWithTag(C.Tag.game_gm_restore_npcs).performClick()
    composeTestRule.onNodeWithTag(C.Tag.game_gm_hide_lore).performClick()
    composeTestRule.onNodeWithTag(C.Tag.game_gm_hidden_sections).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.game_gm_restore_lore).performClick()
    composeTestRule.onAllNodesWithTag(C.Tag.game_gm_hidden_sections).assertCountEquals(0)
  }

  // Verifies hiding active combat preserves the current combat state and callbacks.
  @Test
  fun hidingCombatDoesNotStopCombat() {
    var nextRound = 0
    setContent(
        GameViewModelGMUiState(isCombatActive = true, currentRound = 3),
        onNextRound = { nextRound++ },
    )

    composeTestRule.onNodeWithTag(C.Tag.game_gm_hide_combat).performClick()
    composeTestRule.onNodeWithTag(C.Tag.game_gm_restore_combat).performClick()
    composeTestRule.onNodeWithText("ACTIVE").assertIsDisplayed()
    composeTestRule.onNodeWithText("Round 3").assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.game_gm_next_round).performClick()
    assertEquals(1, nextRound)
  }

  private fun setContent(
      uiState: GameViewModelGMUiState,
      onAddEnemy: (CombatNPC) -> Unit = {},
      onRemoveEnemy: (String) -> Unit = {},
      onUpdateEnemyHp: (String, Int) -> Unit = { _, _ -> },
      onStartCombat: () -> Unit = {},
      onNextRound: () -> Unit = {},
      onEndCombat: () -> Unit = {},
      onRetry: () -> Unit = {},
      onSelectLore: (String) -> Unit = {},
      onTabSelected: (String) -> Unit = {},
      onDiceClick: () -> Unit = {},
  ) {
    composeTestRule.setContent {
      SampleAppTheme {
        GameScreenGMContent(
            uiState = uiState,
            onAddEnemy = onAddEnemy,
            onRemoveEnemy = onRemoveEnemy,
            onUpdateEnemyHp = onUpdateEnemyHp,
            onStartCombat = onStartCombat,
            onNextRound = onNextRound,
            onEndCombat = onEndCombat,
            onRetry = onRetry,
            onSelectLore = onSelectLore,
            onTabSelected = onTabSelected,
            onDiceClick = onDiceClick,
        )
      }
    }
  }

  private fun testEnemy() =
      CombatNPC(
          id = "enemy-1",
          character =
              CharacterFile(
                  name = "Goblin",
                  stats = listOf(CharacterStat("armorClass", "Armor Class", 14.0)),
                  actions = listOf(CharacterAction("condition_poisoned", "Poisoned", "")),
                  hp = CharacterHealthPoints(7, 12),
              ),
      )

  private fun testLore() =
      LoreDocument(
          id = "lore-1",
          lore =
              LoreFile(
                  title = "Ancient Ruins",
                  sections = listOf(LoreSection("history", "History", "Fallen kingdom")),
              ),
          isShared = true,
      )
}
