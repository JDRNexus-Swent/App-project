// Co-authored-by: Codex <codex@openai.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.ui.gameManagement.gm

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.se.jdrnexus.resources.C
import com.github.se.jdrnexus.ui.theme.SampleAppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Covers the top bar of the Game Master screen. */
@RunWith(AndroidJUnit4::class)
class GameScreenGMTopBarTest {
  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun topBarShowsTheHeaderAndBackLeavesTheScreen() {
    var backs = 0
    setContent(onBack = { backs++ })

    composeTestRule.onNodeWithText("Campaign \u00b7 Game Master").assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.shared_back_button).performClick()

    assertEquals(1, backs)
  }

  @Test
  fun noBackButtonWithoutABackAction() {
    setContent(onBack = null)

    composeTestRule.onAllNodesWithTag(C.Tag.shared_back_button).assertCountEquals(0)
  }

  private fun setContent(onBack: (() -> Unit)?) {
    composeTestRule.setContent {
      SampleAppTheme(darkTheme = false) {
        GameScreenGMContent(uiState = GameViewModelGMUiState(), onBack = onBack)
      }
    }
  }
}
