// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.ui.edition

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.se.jdrnexus.model.personalSpace.DocumentType
import com.github.se.jdrnexus.model.personalSpace.JDRFile
import com.github.se.jdrnexus.resources.C
import com.github.se.jdrnexus.resources.LocalIsOnline
import com.github.se.jdrnexus.ui.jdrfile.EditFileViewModel
import com.github.se.jdrnexus.ui.theme.SampleAppTheme
import com.github.se.jdrnexus.utils.NavigationWorkspaceRepository
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EditFileLoreTest {
  @get:Rule val composeTestRule = createComposeRule()

  /** A lore note long enough that its save button is far below the header. */
  private val longLore =
      JDRFile(
          id = LORE_ID,
          name = "The Kingdom",
          type = DocumentType.TEXT,
          content =
              """{"version":1,"type":"lore","title":"The Kingdom","sections":[""" +
                  (1..SECTION_COUNT).joinToString(",") {
                    """{"sectionID":"s$it","title":"Chapter $it","content":"Text of chapter $it."}"""
                  } +
                  "]}",
      )

  @Test
  fun offlineBannerShowsWhileScrolledToTheBottomAndHidesOnReconnect() {
    val viewModel = EditFileViewModel(NavigationWorkspaceRepository(listOf(longLore)))
    viewModel.loadFile(LORE_ID)
    composeTestRule.waitUntil(TIMEOUT_MILLIS) { viewModel.uiState.value.lore != null }
    var isOnline by mutableStateOf(true)
    composeTestRule.setContent {
      SampleAppTheme {
        CompositionLocalProvider(LocalIsOnline provides isOnline) {
          EditFileLore(viewModel = viewModel, onBack = {}, onSaved = {})
        }
      }
    }
    composeTestRule.onNodeWithTag(EditFileLoreTestTags.LIST).performScrollToKey("save")

    composeTestRule.runOnIdle { isOnline = false }

    composeTestRule.onNodeWithTag(C.Tag.offline_banner, useUnmergedTree = true).assertIsDisplayed()
    composeTestRule.onNodeWithTag(EditFileLoreTestTags.BACK).assertIsDisplayed()

    composeTestRule.runOnIdle { isOnline = true }

    composeTestRule.onNodeWithTag(C.Tag.offline_banner, useUnmergedTree = true).assertDoesNotExist()
  }

  private companion object {
    const val LORE_ID = "lore-1"
    const val SECTION_COUNT = 12
    const val TIMEOUT_MILLIS = 2_000L
  }
}
