// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.resources

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
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

    banner().assertIsDisplayed().assertTextContains("You're offline")
    composeTestRule.runOnIdle { darkTheme = true }
    banner().assertIsDisplayed()

    composeTestRule.runOnIdle { isOnline = true }
    banner().assertDoesNotExist()
  }
}
