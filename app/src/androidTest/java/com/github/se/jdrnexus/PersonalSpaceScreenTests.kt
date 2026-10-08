package com.github.se.jdrnexus

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PersonalSpaceScreenTests {
  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun addButtonInvokesCallback() {
    var addButtonClicked = false
    composeTestRule.setContent {
      PersonalSpaceScreen(
          onAddButton = { addButtonClicked = true },
          onBackButton = {},
          onFolderClicked = {},
      )
    }

    composeTestRule.onNodeWithContentDescription("Add item").assertHasClickAction().performClick()

    assertTrue(addButtonClicked)
  }

  @Test
  fun backButtonInvokesCallback() {
    var backButtonClicked = false
    composeTestRule.setContent {
      PersonalSpaceScreen(
          onAddButton = {},
          onBackButton = { backButtonClicked = true },
          onFolderClicked = {},
      )
    }

    composeTestRule.onNodeWithContentDescription("Back").performClick()

    assertTrue(backButtonClicked)
  }

  @Test
  fun fileTypesShowTheirVisibleLabelsAndIcons() {
    composeTestRule.setContent {
      PersonalSpaceScreen(onAddButton = {}, onBackButton = {}, onFolderClicked = {})
    }

    composeTestRule.onNodeWithText("The Ashen Realms").assertIsDisplayed()
    composeTestRule.onAllNodesWithText("FOLDER").assertCountEquals(2)
    composeTestRule
        .onAllNodesWithContentDescription("Folder", useUnmergedTree = true)
        .assertCountEquals(2)

    composeTestRule.onNodeWithText("Elara Moonwhisper").assertIsDisplayed()
    composeTestRule.onNodeWithText("CHARACTER SHEET").assertIsDisplayed()
    composeTestRule
        .onNodeWithContentDescription("Character", useUnmergedTree = true)
        .assertIsDisplayed()

    composeTestRule.onNodeWithText("The Sunken Citadel").assertIsDisplayed()
    composeTestRule.onNodeWithText("TEXT DOCUMENT").assertIsDisplayed()
    composeTestRule
        .onNodeWithContentDescription("Text document", useUnmergedTree = true)
        .assertIsDisplayed()
  }

  @Test
  fun clickingFolderInvokesCallback() {
    var folderClicked = false
    composeTestRule.setContent {
      PersonalSpaceScreen(
          onAddButton = {},
          onBackButton = {},
          onFolderClicked = { folderClicked = true },
      )
    }

    composeTestRule.onNodeWithText("The Ashen Realms").performClick()

    assertTrue(folderClicked)
  }
}
