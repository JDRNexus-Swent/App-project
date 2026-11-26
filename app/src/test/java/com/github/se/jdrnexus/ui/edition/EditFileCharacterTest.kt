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
class EditFileCharacterTest {
  @get:Rule val composeTestRule = createComposeRule()

  /** A character sheet long enough that its save button is far below the header. */
  private val longCharacter =
      JDRFile(
          id = CHARACTER_ID,
          name = "Ilyra Vane",
          type = DocumentType.CHARACTER,
          content =
              """{"version":1,"type":"character","character":{"name":"Ilyra Vane","stats":[""" +
                  (1..STAT_COUNT).joinToString(",") {
                    """{"statID":"s$it","name":"Stat $it","value":$it}"""
                  } +
                  """],"actions":[],"hp":{"current":10,"max":10}}}""",
      )

  @Test
  fun offlineBannerShowsWhileScrolledToTheBottomAndHidesOnReconnect() {
    val viewModel = EditFileViewModel(NavigationWorkspaceRepository(listOf(longCharacter)))
    viewModel.loadFile(CHARACTER_ID)
    composeTestRule.waitUntil(TIMEOUT_MILLIS) { viewModel.uiState.value.character != null }
    var isOnline by mutableStateOf(true)
    composeTestRule.setContent {
      SampleAppTheme {
        CompositionLocalProvider(LocalIsOnline provides isOnline) {
          EditFileCharacter(viewModel = viewModel, onBack = {}, onSaved = {})
        }
      }
    }
    composeTestRule.onNodeWithTag(EditFileCharacterTestTags.LIST).performScrollToKey("save")

    composeTestRule.runOnIdle { isOnline = false }

    composeTestRule.onNodeWithTag(C.Tag.offline_banner, useUnmergedTree = true).assertIsDisplayed()
    composeTestRule.onNodeWithTag(EditFileCharacterTestTags.BACK).assertIsDisplayed()

    composeTestRule.runOnIdle { isOnline = true }

    composeTestRule.onNodeWithTag(C.Tag.offline_banner, useUnmergedTree = true).assertDoesNotExist()
  }

  private companion object {
    const val CHARACTER_ID = "character-1"
    const val STAT_COUNT = 12
    const val TIMEOUT_MILLIS = 2_000L
  }
}
