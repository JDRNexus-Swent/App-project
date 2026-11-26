// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.resources

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.se.jdrnexus.ui.theme.SampleAppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OfflineBannerTest {
  @get:Rule val composeTestRule = createComposeRule()

  private fun banner() = composeTestRule.onNodeWithTag(C.Tag.offline_banner, useUnmergedTree = true)

  @Test
  fun showsNothingWhenNoConnectivityStateIsProvided() {
    composeTestRule.setContent { SampleAppTheme { OfflineBanner() } }

    banner().assertDoesNotExist()
  }

  @Test
  fun appearsWhileOfflineAndDisappearsOnReconnectInBothThemes() {
    var isOnline by mutableStateOf(false)
    var darkTheme by mutableStateOf(false)
    composeTestRule.setContent {
      SampleAppTheme(darkTheme = darkTheme) {
        CompositionLocalProvider(LocalIsOnline provides isOnline) { OfflineBanner() }
      }
    }

    // The text is a child of the tagged row, so it is checked as one of the banner's descendants.
    banner().assertIsDisplayed().assert(hasAnyDescendant(hasText("You're offline")))
    composeTestRule.runOnIdle { darkTheme = true }
    banner().assertIsDisplayed()

    composeTestRule.runOnIdle { isOnline = true }
    banner().assertDoesNotExist()
  }

  /**
   * Screen readers such as TalkBack only read what the user moves onto, unless a node is marked as
   * a "live region": then they announce it by themselves when it appears or changes. Robolectric
   * has no screen reader, so the test checks the marker the reader relies on: the banner's
   * semantics carry `LiveRegionMode.Polite` (announced after the reader finishes its current
   * sentence, rather than interrupting it).
   */
  @Test
  fun isAnnouncedByScreenReadersWhenItAppears() {
    composeTestRule.setContent {
      SampleAppTheme { CompositionLocalProvider(LocalIsOnline provides false) { OfflineBanner() } }
    }

    banner()
        .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
  }
}
