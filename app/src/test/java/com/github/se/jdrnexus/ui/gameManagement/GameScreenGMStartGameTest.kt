// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.ui.gameManagement

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.se.jdrnexus.resources.C
import com.github.se.jdrnexus.ui.theme.SampleAppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Covers the Start game control of the Game Master screen. */
@RunWith(AndroidJUnit4::class)
class GameScreenGMStartGameTest {
  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun readyButtonStartsTheGameOnce() {
    var starts = 0
    setContent(GameViewModelGMUiState(), onStartGame = { starts++ })

    composeTestRule.onNodeWithText("Start game").assertIsDisplayed()
    startGameButton().assertIsEnabled().performClick()

    assertEquals(1, starts)
  }

  @Test
  fun startGameSitsAboveCombat() {
    setContent(GameViewModelGMUiState())

    val startGame = startGameButton().getUnclippedBoundsInRoot()
    val startCombat =
        composeTestRule.onNodeWithTag(C.Tag.game_gm_start_combat).getUnclippedBoundsInRoot()

    assertTrue(startGame.bottom <= startCombat.top)
  }

  @Test
  fun buttonIsDisabledWhileTheScreenLoads() {
    setContentWithSpinner(GameViewModelGMUiState(isLoading = true))

    startGameButton().assertIsNotEnabled()
    composeTestRule.onAllNodesWithTag(C.Tag.game_gm_start_game_progress).assertCountEquals(0)
  }

  @Test
  fun buttonIsDisabledAndShowsProgressWhileSaving() {
    setContentWithSpinner(GameViewModelGMUiState(isStartingGame = true))

    startGameButton().assertIsNotEnabled()
    composeTestRule.onNodeWithTag(C.Tag.game_gm_start_game_progress).assertExists()
  }

  @Test
  fun startedGameShowsTheLabelInsteadOfTheButton() {
    setContent(GameViewModelGMUiState(isGameStarted = true))

    composeTestRule.onNodeWithTag(C.Tag.game_gm_game_in_progress).assertIsDisplayed()
    composeTestRule.onNodeWithText("Game in progress").assertIsDisplayed()
    composeTestRule.onAllNodesWithTag(C.Tag.game_gm_start_game).assertCountEquals(0)
  }

  private fun startGameButton() = composeTestRule.onNodeWithTag(C.Tag.game_gm_start_game)

  private fun setContent(
      uiState: GameViewModelGMUiState,
      onStartGame: () -> Unit = {},
  ) {
    composeTestRule.setContent {
      SampleAppTheme(darkTheme = false) {
        GameScreenGMContent(uiState = uiState, onStartGame = onStartGame)
      }
    }
  }

  /** Renders [uiState] with the clock paused, because an endless spinner never lets it idle. */
  private fun setContentWithSpinner(uiState: GameViewModelGMUiState) {
    composeTestRule.mainClock.autoAdvance = false
    setContent(uiState)
    composeTestRule.mainClock.advanceTimeBy(100)
  }
}
