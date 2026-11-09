// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.github.se.jdrnexus.resources

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.se.jdrnexus.ui.theme.SampleAppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScreenBarsTest {
  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun sharedBarsRenderInBothThemesAndDispatchActions() {
    var darkTheme by mutableStateOf(false)
    var backClicks = 0
    var selectedTab = ""

    composeTestRule.setContent {
      SampleAppTheme(darkTheme = darkTheme) {
        Column {
          AppTopBar(title = "Screen", onBack = { backClicks++ })
          ScreenBottomBar(selectedTab = "Files", onTabSelected = { selectedTab = it })
        }
      }
    }

    composeTestRule.onNodeWithTag(C.Tag.shared_top_bar).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.shared_back_button).performClick()
    composeTestRule.onNodeWithTag(C.Tag.shared_bottom_bar).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.bottomTab("files")).assertIsSelected()
    composeTestRule.onNodeWithTag(C.Tag.bottomTab("map")).performClick()
    assertEquals(1, backClicks)
    assertEquals("Map", selectedTab)

    composeTestRule.runOnIdle { darkTheme = true }
    composeTestRule.onNodeWithTag(C.Tag.shared_top_bar).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.shared_bottom_bar).assertIsDisplayed()
  }

  @Test
  fun disabledBottomBarDoesNotDispatchTabActions() {
    var tabSelected = false
    composeTestRule.setContent {
      SampleAppTheme {
        ScreenBottomBar(
            selectedTab = "Campaigns",
            onTabSelected = { tabSelected = true },
            enabled = false,
        )
      }
    }

    listOf("home", "campaigns", "files", "map").forEach { tab ->
      composeTestRule.onNodeWithTag(C.Tag.bottomTab(tab)).assertIsNotEnabled()
    }
    composeTestRule.onNodeWithTag(C.Tag.bottomTab("campaigns")).assertIsSelected()
    composeTestRule.onNodeWithTag(C.Tag.bottomTab("home")).performClick()
    assertEquals(false, tabSelected)
  }
}
