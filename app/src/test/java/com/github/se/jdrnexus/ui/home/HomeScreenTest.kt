// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.github.se.jdrnexus.ui.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.se.jdrnexus.ui.theme.SampleAppTheme
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeScreenTest {
  @get:Rule
  val composeTestRule = createComposeRule()

  @Test
  fun contentDisplaysTheHeadingsAndPlaceholderObjects() {
    composeTestRule.setContent { SampleAppTheme { HomeScreen() } }

    composeTestRule.onNodeWithTag(TestTags.title).assertIsDisplayed()
    composeTestRule.onNodeWithTag(TestTags.imagePlaceholder).assertIsDisplayed()
    composeTestRule.onNodeWithTag(TestTags.placeholderObjectsRow).assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(TestTags.placeholderObjects.first())
        .assertIsDisplayed()
    composeTestRule.onNodeWithTag(TestTags.localAdventures).assertIsDisplayed()
    composeTestRule.onNodeWithTag(TestTags.quicktools).assertIsDisplayed()
    TestTags.quicktoolButtons.forEach { testTag ->
      val button = composeTestRule.onNodeWithTag(testTag)
      button.assertIsDisplayed()
      val bounds = button.fetchSemanticsNode().boundsInRoot
      Assert.assertEquals(bounds.width, bounds.height, 1f)
    }
  }

  @Test
  fun logoutButtonInvokesOnLogout() {
    var loggedOut = false
    composeTestRule.setContent {
        SampleAppTheme { HomeScreen(onLogout = { loggedOut = true }) }
    }

    composeTestRule.onNodeWithText("Log out").assertIsDisplayed().performClick()

      Assert.assertTrue(loggedOut)
  }
}