// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.github.se.jdrnexus.screen

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.github.se.jdrnexus.model.personalSpace.DocumentType
import com.github.se.jdrnexus.model.personalSpace.JDRFile
import com.github.se.jdrnexus.resources.C
import com.github.se.jdrnexus.ui.search.DocumentSearchInterfaceVM
import com.github.se.jdrnexus.ui.search.DocumentSearchResult
import com.github.se.jdrnexus.ui.search.DocumentSearchScreen
import com.github.se.jdrnexus.ui.search.DocumentSearchStatus
import com.github.se.jdrnexus.ui.search.DocumentSearchUiState
import com.github.se.jdrnexus.ui.search.SearchError
import com.github.se.jdrnexus.ui.theme.SampleAppTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DocumentSearchScreenTest {
  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun typingUpdatesTheSearchViewModel() {
    val viewModel = FakeDocumentSearchViewModel()
    setScreen(viewModel)

    composeTestRule.onNodeWithTag(C.Tag.search_query).performTextInput("Dragon")

    assertEquals("Dragon", viewModel.uiState.value.query)
    composeTestRule.onNodeWithTag(C.Tag.search_query).assertTextContains("Dragon")
  }

  @Test
  fun clearButtonClearsTheQueryAndResults() {
    val viewModel =
        FakeDocumentSearchViewModel(
            DocumentSearchUiState(
                query = "Dragon",
                results = listOf(result),
                status = DocumentSearchStatus.Results,
            )
        )
    setScreen(viewModel)

    composeTestRule.onNodeWithTag(C.Tag.searchResult(result.file.id)).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.search_clear).performClick()

    assertEquals("", viewModel.uiState.value.query)
    assertEquals(DocumentSearchStatus.Blank, viewModel.uiState.value.status)
    composeTestRule.onAllNodesWithTag(C.Tag.searchResult(result.file.id)).assertCountEquals(0)
    composeTestRule.onNodeWithText("Enter a document name to search.").assertIsDisplayed()
  }

  @Test
  fun tappingResultOpensItsDocumentAndShowsItsFolderPath() {
    val viewModel =
        FakeDocumentSearchViewModel(
            DocumentSearchUiState(
                query = "Dragon",
                results = listOf(result),
                status = DocumentSearchStatus.Results,
            )
        )
    var openedFile: JDRFile? = null
    setScreen(viewModel, onDocumentClicked = { openedFile = it })

    composeTestRule.onNodeWithText("Campaign").assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.searchResult(result.file.id)).performClick()

    assertEquals(result.file, openedFile)
  }

  @Test
  fun blankLoadingAndNoResultsStatesAreRendered() {
    val viewModel = FakeDocumentSearchViewModel()
    setScreen(viewModel)
    composeTestRule.onNodeWithText("Enter a document name to search.").assertIsDisplayed()

    viewModel.setState(
        DocumentSearchUiState(query = "Dragon", status = DocumentSearchStatus.Loading)
    )
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag(C.Tag.search_loading).assertIsDisplayed()

    viewModel.setState(
        DocumentSearchUiState(query = "Dragon", status = DocumentSearchStatus.NoResults)
    )
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("No matching documents found.").assertIsDisplayed()
  }

  @Test
  fun errorStateShowsLocalizedMessageAndRetries() {
    val viewModel =
        FakeDocumentSearchViewModel(
            DocumentSearchUiState(
                query = "Dragon",
                status = DocumentSearchStatus.Error(SearchError.GROUP_ACCESS_DENIED),
            )
        )
    setScreen(viewModel)

    composeTestRule
        .onNodeWithText("You do not have access to search this workspace.")
        .assertIsDisplayed()
    composeTestRule.onNodeWithText("Retry").performClick()

    assertEquals(1, viewModel.retryCount)

    viewModel.setState(
        DocumentSearchUiState(
            query = "Dragon",
            status = DocumentSearchStatus.Error(SearchError.NOT_SIGNED_IN),
        )
    )
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("Sign in to search documents.").assertIsDisplayed()

    viewModel.setState(
        DocumentSearchUiState(
            query = "Dragon",
            status = DocumentSearchStatus.Error(SearchError.SEARCH_FAILED),
        )
    )
    composeTestRule.waitForIdle()
    composeTestRule
        .onNodeWithText("Search could not be completed. Please try again.")
        .assertIsDisplayed()
  }

  private fun setScreen(
      viewModel: FakeDocumentSearchViewModel,
      onDocumentClicked: (JDRFile) -> Unit = {},
  ) {
    composeTestRule.setContent {
      SampleAppTheme {
        DocumentSearchScreen(
            onBack = {},
            onDocumentClicked = onDocumentClicked,
            searchViewModel = viewModel,
        )
      }
    }
  }

  private class FakeDocumentSearchViewModel(
      initialState: DocumentSearchUiState = DocumentSearchUiState()
  ) : DocumentSearchInterfaceVM {
    private val mutableUiState = MutableStateFlow(initialState)
    override val uiState: StateFlow<DocumentSearchUiState> = mutableUiState
    var retryCount = 0
      private set

    override fun onQueryChanged(query: String) {
      mutableUiState.value =
          DocumentSearchUiState(query = query, status = DocumentSearchStatus.Loading)
    }

    override fun clearQuery() {
      mutableUiState.value = DocumentSearchUiState()
    }

    override fun retry() {
      retryCount++
    }

    fun setState(state: DocumentSearchUiState) {
      mutableUiState.value = state
    }
  }

  private companion object {
    val result =
        DocumentSearchResult(
            file =
                JDRFile(
                    id = "dragon-lore",
                    name = "Dragon",
                    type = DocumentType.TEXT,
                ),
            folderPath = listOf("Campaign"),
        )
  }
}
