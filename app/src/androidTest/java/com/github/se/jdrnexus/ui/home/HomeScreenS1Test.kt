package com.github.se.jdrnexus.ui.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
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
class HomeScreenS1Test {
  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun displaysTitleProfilePlaceholderSectionsAndAdventures() {
    composeTestRule.setContent { SampleAppTheme { HomeScreen() } }

    composeTestRule.onNodeWithTag(TestTags.TITLE).assertIsDisplayed()
    composeTestRule.onNodeWithTag(TestTags.PROFILE_PIC).assertIsDisplayed()
    composeTestRule.onNodeWithTag(TestTags.OBJECTSROW).assertExists()
    composeTestRule.onNodeWithTag(TestTags.ADVENTURES).assertIsDisplayed()
    composeTestRule.onNodeWithTag(TestTags.QUICKTOOLS).assertIsDisplayed()

    TestTags.placeholderObjects.forEachIndexed { index, ttag ->
      Assert.assertTrue(composeTestRule.onAllNodesWithTag(ttag).fetchSemanticsNodes().isNotEmpty())
      Assert.assertTrue(
          composeTestRule
              .onAllNodesWithText("Adventure ${index + 1}")
              .fetchSemanticsNodes()
              .isNotEmpty()
      )
    }
  }

  @Test
  fun quickToolButtonsAreSquareAndClickable() {
    composeTestRule.setContent { SampleAppTheme { HomeScreen() } }

    TestTags.BUTTONS.forEachIndexed { index, tag ->
      val button = composeTestRule.onNodeWithTag(tag)
      button.assertIsDisplayed()
      composeTestRule.onNodeWithText("Tool ${index + 1}").assertIsDisplayed()
      val bounds = button.fetchSemanticsNode().boundsInRoot
      Assert.assertEquals(bounds.width, bounds.height, 1f)
      button.performClick()
    }
  }

  @Test
  fun logoutButtonInvokesCallback() {
    var loggedOut = false
    composeTestRule.setContent { SampleAppTheme { HomeScreen(onLogout = { loggedOut = true }) } }

    composeTestRule.onNodeWithText("Log out").performClick()

    Assert.assertTrue(loggedOut)
  }
}
