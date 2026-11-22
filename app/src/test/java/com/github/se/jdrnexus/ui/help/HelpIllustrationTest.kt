// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.ui.help

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.se.jdrnexus.R
import com.github.se.jdrnexus.resources.C
import com.github.se.jdrnexus.ui.theme.SampleAppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HelpIllustrationTest {
  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun tapOnTheIllustrationReachesNothingUnderIt() {
    var clicks = 0
    setIllustration { Button(onClick = { clicks++ }) { Text("Play") } }

    composeTestRule.onNodeWithTag(C.Tag.help_illustration).performClick()

    assertEquals(0, clicks)
  }

  @Test
  fun screenReadersSkipTheIllustration() {
    setIllustration { Text("Example card") }

    composeTestRule.onNodeWithTag(C.Tag.help_illustration).assertIsDisplayed()
    // Default merged tree: the one screen readers read.
    composeTestRule.onAllNodesWithText("Example card").assertCountEquals(0)
  }

  @Test
  fun tallContentIsShrunkToAFifthOfTheWindowHeight() {
    var windowHeightDp = 0
    composeTestRule.setContent {
      windowHeightDp = LocalConfiguration.current.screenHeightDp
      SampleAppTheme(darkTheme = false) {
        HelpIllustration(content = { Box(Modifier.fillMaxWidth().height(2000.dp)) })
      }
    }

    val bounds = composeTestRule.onNodeWithTag(C.Tag.help_illustration).getUnclippedBoundsInRoot()
    val height = bounds.bottom - bounds.top

    assertTrue(height > 0.dp)
    assertTrue(height <= (windowHeightDp / 5f + 1).dp)
  }

  @Test
  fun largerFractionLetsTheIllustrationTakeMoreHeight() {
    var windowHeightDp = 0
    composeTestRule.setContent {
      windowHeightDp = LocalConfiguration.current.screenHeightDp
      SampleAppTheme(darkTheme = false) {
        HelpIllustration(
            content = { Box(Modifier.fillMaxWidth().height(2000.dp)) },
            maxHeightFraction = 0.5f,
        )
      }
    }

    val height = illustrationHeight()

    assertTrue(height > (windowHeightDp / 5f + 1).dp)
    assertTrue(height <= (windowHeightDp / 2f + 1).dp)
  }

  @Test
  fun pageFractionReachesItsIllustration() {
    var windowHeightDp = 0
    val page =
        HelpPage(
            R.string.campaign_help_start_title,
            R.string.campaign_help_start_body,
            illustration = { Box(Modifier.fillMaxWidth().height(2000.dp)) },
            illustrationMaxHeightFraction = 0.5f,
        )
    composeTestRule.setContent {
      windowHeightDp = LocalConfiguration.current.screenHeightDp
      SampleAppTheme(darkTheme = false) { HelpPagerContent(pages = listOf(page), onDone = {}) }
    }

    assertTrue(illustrationHeight() > (windowHeightDp / 5f + 1).dp)
  }

  @Test
  fun smallContentKeepsItsSize() {
    setIllustration { Box(Modifier.fillMaxWidth().height(40.dp)) }

    composeTestRule.onNodeWithTag(C.Tag.help_illustration).assertHeightIsEqualTo(40.dp)
  }

  @Test
  fun swipeOnTheIllustrationStillChangesPage() {
    val illustrated =
        listOf(
            HelpPage(
                R.string.campaign_help_start_title,
                R.string.campaign_help_start_body,
                illustration = { Button(onClick = {}) { Text("Play") } },
            ),
            HelpPage(R.string.campaign_help_roles_title, R.string.campaign_help_roles_body),
        )
    composeTestRule.setContent {
      SampleAppTheme(darkTheme = false) { HelpPagerContent(pages = illustrated, onDone = {}) }
    }

    composeTestRule.onNodeWithTag(C.Tag.help_illustration).performTouchInput { swipeLeft() }
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag(C.Tag.helpPage(1)).assertIsDisplayed()
  }

  private fun illustrationHeight() =
      composeTestRule.onNodeWithTag(C.Tag.help_illustration).getUnclippedBoundsInRoot().let {
        it.bottom - it.top
      }

  private fun setIllustration(content: @androidx.compose.runtime.Composable () -> Unit) {
    composeTestRule.setContent {
      SampleAppTheme(darkTheme = false) { HelpIllustration(content = content) }
    }
  }
}
