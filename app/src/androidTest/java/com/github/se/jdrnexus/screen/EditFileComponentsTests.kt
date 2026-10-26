// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.github.se.jdrnexus.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.se.jdrnexus.ui.edition.EditFileErrorMessage
import com.github.se.jdrnexus.ui.edition.EditFileSaveButton
import com.github.se.jdrnexus.ui.edition.EditFileScaffold
import com.github.se.jdrnexus.ui.edition.EditFileSectionHeading
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EditFileComponentsTests {
  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun scaffoldRendersEmptyMessageWhenThereIsNoError() {
    composeTestRule.setContent {
      MaterialTheme {
        EditFileScaffold(
            isLoading = false,
            loadingMessage = "Loading test...",
            hasContent = false,
            screenTitle = "Test",
            backTestTag = "back",
            error = null,
            emptyMessage = "Nothing loaded",
            onBack = {},
        ) {
          Text("Content")
        }
      }
    }

    composeTestRule.onNodeWithText("Nothing loaded").assertIsDisplayed()
    composeTestRule.onNodeWithText("Test").assertIsDisplayed()
    composeTestRule.onNodeWithText("Content").assertIsNotDisplayed()
  }

  @Test
  fun scaffoldKeepsHeaderVisibleWhileLoading() {
    composeTestRule.setContent {
      MaterialTheme {
        EditFileScaffold(
            isLoading = true,
            loadingMessage = "Loading test...",
            hasContent = true,
            screenTitle = "Test",
            backTestTag = "back",
            error = null,
            emptyMessage = "Nothing loaded",
            onBack = {},
        ) {
          Text("Content")
        }
      }
    }

    composeTestRule.onNodeWithText("Test").assertIsDisplayed()
    composeTestRule.onNodeWithText("Loading test...").assertIsDisplayed()
    composeTestRule.onNodeWithText("Content").assertIsNotDisplayed()
  }

  @Test
  fun sharedComponentsRenderTheirAlternateStates() {
    var saveClicks = 0
    composeTestRule.setContent {
      MaterialTheme {
        Column {
          EditFileSectionHeading(title = "Stats", count = 1)
          EditFileSectionHeading(title = "Stats", count = 2)
          EditFileErrorMessage(error = "Save failed", testTag = "error-message")
          EditFileSaveButton(isSaving = true, isSaved = false, testTag = "saving") { saveClicks++ }
          EditFileSaveButton(isSaving = false, isSaved = false, testTag = "save") { saveClicks++ }
          EditFileSaveButton(isSaving = false, isSaved = true, testTag = "saved") { saveClicks++ }
        }
      }
    }

    composeTestRule.onNodeWithText("1 stat").assertIsDisplayed()
    composeTestRule.onNodeWithText("2 stats").assertIsDisplayed()
    composeTestRule.onNodeWithTag("error-message").assertIsDisplayed()
    composeTestRule.onNodeWithTag("error-message").assertTextEquals("Save failed")
    composeTestRule
        .onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate))
        .assertIsDisplayed()
    composeTestRule.onNodeWithTag("saving").assertIsNotDisplayed()
    composeTestRule.onNodeWithTag("save").assertIsDisplayed()
    composeTestRule.onNodeWithTag("save").assertTextEquals("Save")
    composeTestRule.onNodeWithTag("saved").assertIsDisplayed()
    composeTestRule.onNodeWithTag("saved").assertTextEquals("Saved")
    composeTestRule.onNodeWithTag("save").performClick()
    composeTestRule.onNodeWithTag("saved").performClick()
    assertEquals(2, saveClicks)
  }
}
