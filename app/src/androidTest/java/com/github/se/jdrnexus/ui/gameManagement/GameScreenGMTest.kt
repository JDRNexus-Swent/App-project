// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.ui.gameManagement

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
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
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GameScreenGMTest {
  @get:Rule val composeTestRule = createComposeRule()

  private companion object {
    // Dashboard item indexes when nothing is hidden: header, combat, activity, NPCs, lore.
    const val COMBAT_ITEM = 1
    const val ACTIVITY_ITEM = 2
    const val NPCS_ITEM = 3
    const val LORE_ITEM = 4
    // The Hidden Sections card is inserted right after the header while a section is hidden.
    const val HIDDEN_ITEM = 1
  }

  // Verifies the empty combat state and dispatches the start-combat action.
  @Test
  fun emptyCombatShowsMessageAndStartsCombat() {
    var started = 0
    setContent { GameScreenGMContent(GameViewModelGMUiState(), onStartCombat = { started++ }) }

    composeTestRule.onNodeWithTag(C.Tag.game_gm_empty_combat).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.game_gm_start_combat).assertIsDisplayed().performClick()
    assertEquals(1, started)
  }

  // Verifies enemy details and the row callbacks for hit points and removal.
  @Test
  fun enemyDetailsAndCombatCallbacksAreHandled() {
    val enemy = testEnemy()
    val hpChanges = mutableListOf<Pair<String, Int>>()
    var removed = ""
    setContent {
      GameScreenGMContent(
          GameViewModelGMUiState(enemies = listOf(enemy)),
          onUpdateEnemyHp = { id, change -> hpChanges += id to change },
          onRemoveEnemy = { removed = it },
      )
    }

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
    setContent {
      GameScreenGMContent(
          GameViewModelGMUiState(isCombatActive = true),
          onNextRound = { nextRound++ },
          onEndCombat = { ended++ },
      )
    }
    composeTestRule.onNodeWithTag(C.Tag.game_gm_next_round).performClick()
    composeTestRule.onNodeWithTag(C.Tag.game_gm_end_combat).performClick()
    assertEquals(1, nextRound)
    assertEquals(1, ended)
  }

  // Verifies that the add-enemy dialog can be cancelled and submits valid values.
  @Test
  fun addEnemyDialogOpensClosesAndSubmitsValidEnemy() {
    var added: CombatNPC? = null
    setContent {
      GameScreenGMContent(
          GameViewModelGMUiState(),
          onAddEnemy = { added = it },
          renderAddEnemyDialogAsWindow = false,
      )
    }

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
    setContent {
      GameScreenGMContent(GameViewModelGMUiState(), renderAddEnemyDialogAsWindow = false)
    }

    composeTestRule.onNodeWithTag(C.Tag.game_gm_add_enemy).performClick()
    composeTestRule.onNodeWithText("Add").assertIsNotEnabled()
    composeTestRule.onNodeWithTag(C.Tag.game_gm_enemy_name).performTextInput("Orc")
    composeTestRule.onNodeWithTag(C.Tag.game_gm_enemy_max_hp).performTextClearance()
    composeTestRule.onNodeWithTag(C.Tag.game_gm_enemy_max_hp).performTextInput("0")
    composeTestRule.onNodeWithText("Add").assertIsNotEnabled()
    assertFalse(isValidEnemyInput("Orc", 10, 0, 10))
  }

  // Verifies the loading state displays its progress indicator.
  @Test
  fun loadingStateShowsProgress() {
    setContent { GameScreenGMContent(GameViewModelGMUiState(isLoading = true)) }
    composeTestRule.onNodeWithTag(C.Tag.game_gm_loading).assertIsDisplayed()
  }

  // Verifies the error state displays its message and dispatches retry.
  @Test
  fun errorStateShowsMessageAndRetries() {
    var retries = 0
    setContent {
      GameScreenGMContent(GameViewModelGMUiState(error = "Unable to load"), onRetry = { retries++ })
    }
    composeTestRule.onNodeWithTag(C.Tag.game_gm_error).assertIsDisplayed()
    composeTestRule.onNodeWithText("Unable to load").assertIsDisplayed()
    composeTestRule.onNodeWithText("Retry").performClick()
    assertEquals(1, retries)
  }

  // Verifies an empty session shows the activity, NPC, and lore empty-state messages.
  @Test
  fun emptySessionShowsSectionMessages() {
    setContent { GameScreenGMContent(GameViewModelGMUiState()) }
    scrollToItem(ACTIVITY_ITEM)
    composeTestRule.onNodeWithText("No recent player activity.").assertIsDisplayed()
    scrollToItem(NPCS_ITEM)
    composeTestRule.onNodeWithTag(C.Tag.game_gm_empty_npcs).assertIsDisplayed()
    scrollToItem(LORE_ITEM)
    composeTestRule.onNodeWithTag(C.Tag.game_gm_empty_lore).assertIsDisplayed()
  }

  // Verifies the feed shows at most the 8 newest entries, in the newest-first repository order.
  @Test
  fun activityFeedShowsNewestEightEntries() {
    val feed = (9 downTo 1).map { ActivityItem("a$it", "Mira", "rolled $it", null) }
    setContent { GameScreenGMContent(GameViewModelGMUiState(activityFeed = feed)) }
    composeTestRule.onNodeWithTag(C.Tag.gameGmActivity("a9")).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.gameGmActivity("a2")).assertIsDisplayed()
    composeTestRule.onAllNodesWithTag(C.Tag.gameGmActivity("a1")).assertCountEquals(0)
  }

  // Verifies activity rendering, dice results, and dismiss behavior.
  @Test
  fun activityFeedDisplaysEntriesAndCanBeDismissed() {
    setContent {
      GameScreenGMContent(
          GameViewModelGMUiState(
              activityFeed = listOf(ActivityItem("a1", "Mira", "rolled", "1d20 = 18"))
          )
      )
    }

    composeTestRule.onNodeWithText("Mira · rolled (1d20 = 18)").assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.gameGmHide("activity")).performClick()
    composeTestRule.onAllNodesWithText("Activity Feed").assertCountEquals(0)
  }

  // Verifies NPC names and the information dialog lifecycle.
  @Test
  fun npcListDisplaysNamesAndInformationDialog() {
    val npc = Npc("npc-1", "Eldrin", null, "A quiet guide.")
    setContent { GameScreenGMContent(GameViewModelGMUiState(npcs = listOf(npc))) }
    composeTestRule.onNodeWithTag(C.Tag.gameGmNpc(npc.id)).performClick()
    composeTestRule.onAllNodesWithText("Eldrin").assertCountEquals(2)
    composeTestRule.onNodeWithText("A quiet guide.").assertIsDisplayed()
    composeTestRule.onNodeWithText("Close").performClick()
    composeTestRule.onAllNodesWithText("A quiet guide.").assertCountEquals(0)
  }

  // Verifies lore expansion and collapse.
  @Test
  fun loreDocumentsExpandAndCollapse() {
    val lore = testLore()
    setContent { GameScreenGMContent(GameViewModelGMUiState(loreDocuments = listOf(lore))) }
    // Lore is the last item (index 4); scrolling to the end lifts its buttons above the dice FAB.
    val list = composeTestRule.onNode(hasScrollToIndexAction())
    list.performScrollToIndex(4)
    composeTestRule.onNodeWithText("Ancient Ruins").assertIsDisplayed()
    composeTestRule.onNodeWithText("Read").performClick()
    composeTestRule.onNodeWithText("History: Fallen kingdom").performScrollTo().assertIsDisplayed()
    list.performScrollToIndex(4)
    composeTestRule.onNodeWithText("Collapse").performClick()
    composeTestRule.onNodeWithText("Read").assertIsDisplayed()
  }

  // Verifies shared bottom navigation and dice FAB callback dispatch.
  @Test
  fun navigationBarAndDiceFabDispatchCallbacks() {
    var selectedTab = ""
    var diceClicks = 0
    setContent {
      GameScreenGMContent(
          GameViewModelGMUiState(),
          onTabSelected = { selectedTab = it },
          onDiceClick = { diceClicks++ },
      )
    }

    composeTestRule.onNodeWithTag(C.Tag.shared_bottom_bar).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.bottomTab("map")).performClick()
    composeTestRule.onNodeWithTag(C.Tag.game_gm_dice).performClick()
    assertEquals("Map", selectedTab)
    assertEquals(1, diceClicks)
  }

  // Verifies every dashboard section can be hidden and restored through the shared area.
  @Test
  fun dashboardSectionsCanBeDismissedAndRestored() {
    setContent { GameScreenGMContent(GameViewModelGMUiState()) }

    composeTestRule.onNodeWithTag(C.Tag.gameGmHide("combat")).performClick()
    composeTestRule.onNodeWithTag(C.Tag.gameGmRestore("combat")).performClick()
    composeTestRule.onNodeWithTag(C.Tag.gameGmHide("activity")).performClick()
    composeTestRule.onNodeWithTag(C.Tag.gameGmRestore("activity")).performClick()
    composeTestRule.onNodeWithTag(C.Tag.gameGmHide("npcs")).performClick()
    composeTestRule.onNodeWithTag(C.Tag.gameGmRestore("npcs")).performClick()
    // Lore is the last item (index 4); scrolling to the end lifts its close button above the FAB.
    composeTestRule.onNode(hasScrollToIndexAction()).performScrollToIndex(4)
    composeTestRule.onNodeWithTag(C.Tag.gameGmHide("lore")).performClick()
    composeTestRule.onNodeWithTag(C.Tag.game_gm_hidden_sections).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.gameGmRestore("lore")).performClick()
    composeTestRule.onAllNodesWithTag(C.Tag.game_gm_hidden_sections).assertCountEquals(0)
  }

  // Verifies hiding active combat preserves the current combat state and callbacks.
  @Test
  fun hidingCombatDoesNotStopCombat() {
    var nextRound = 0
    setContent {
      GameScreenGMContent(
          GameViewModelGMUiState(isCombatActive = true, currentRound = 3),
          onNextRound = { nextRound++ },
      )
    }

    composeTestRule.onNodeWithTag(C.Tag.gameGmHide("combat")).performClick()
    composeTestRule.onNodeWithTag(C.Tag.gameGmRestore("combat")).performClick()
    composeTestRule.onNodeWithText("ACTIVE").assertIsDisplayed()
    composeTestRule.onNodeWithText("Round 3").assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.game_gm_next_round).performClick()
    assertEquals(1, nextRound)
  }

  private fun setContent(content: @Composable () -> Unit) {
    composeTestRule.setContent { SampleAppTheme { content() } }
  }

  /**
   * Scrolls the dashboard so the item at [index] starts at the top. Lazy items off screen are not
   * composed, so a test must scroll to an item before it can find or click anything inside it; and
   * aligning the item to the top keeps its buttons clear of the dice FAB on any screen size.
   */
  private fun scrollToItem(index: Int) {
    composeTestRule.onNodeWithTag(C.Tag.game_gm_list).performScrollToIndex(index)
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
