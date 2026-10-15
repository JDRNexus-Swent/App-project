// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.github.se.jdrnexus.screen

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.se.jdrnexus.model.personalSpace.DocumentType
import com.github.se.jdrnexus.model.personalSpace.JDRFile
import com.github.se.jdrnexus.model.personalSpace.WorkspaceRepository
import com.github.se.jdrnexus.ui.edition.EditFileLore
import com.github.se.jdrnexus.ui.edition.EditFileLoreTestTags
import com.github.se.jdrnexus.ui.jdrfile.EditFileViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EditFileLoreTests {
  @get:Rule val composeTestRule = createComposeRule()
  private val testTimeoutMillis = 2_000L

  @Test
  fun editsAddsRemovesAndSavesLoreSections() {
    val file =
        JDRFile(
            id = "lore-1",
            name = "The Kingdom",
            type = DocumentType.TEXT,
            content =
                """{"version":1,"type":"lore","title":"The Kingdom","sections":[{"sectionID":"history","title":"History","content":"Founded long ago."},{"sectionID":"king","title":"The King","content":"Rules today."}]}""",
        )
    val repository = FakeWorkspaceRepository(file)
    val viewModel = EditFileViewModel(repository)
    var backClicked = false
    var savedClicked = false
    viewModel.loadFile(file.id)
    composeTestRule.waitUntil(timeoutMillis = testTimeoutMillis) {
      viewModel.uiState.value.lore != null
    }

    composeTestRule.setContent {
      EditFileLore(
          viewModel = viewModel,
          onBack = { backClicked = true },
          onSaved = { savedClicked = true },
      )
    }
    composeTestRule.onNodeWithTag(EditFileLoreTestTags.SECTIONS_LIST).assertIsDisplayed()

    composeTestRule.onNodeWithTag("edit_lore_title").performTextClearance()
    composeTestRule.onNodeWithTag("edit_lore_title").performTextInput("The Lost Kingdom")
    composeTestRule.onNodeWithTag("section_title_history").performTextClearance()
    composeTestRule.onNodeWithTag("section_title_history").performTextInput("Ancient history")
    composeTestRule.onNodeWithTag("section_description_history").performTextClearance()
    composeTestRule
        .onNodeWithTag("section_description_history")
        .performTextInput("Founded before the first age.")

    composeTestRule.onNodeWithTag(EditFileLoreTestTags.LIST).performScrollToKey("add-section")
    composeTestRule.onNodeWithTag("add_section_title").performTextInput("The prophecy")
    composeTestRule.onNodeWithTag("add_section_description").performTextInput("A warning.")
    composeTestRule.onNodeWithTag(EditFileLoreTestTags.LIST).performScrollToKey("add-section")
    composeTestRule.onNodeWithTag("confirm_add_section").performClick()
    composeTestRule.onNodeWithText("The prophecy").assertIsDisplayed()

    composeTestRule.onNodeWithTag(EditFileLoreTestTags.LIST).performScrollToKey("section-king")
    composeTestRule.onNodeWithContentDescription("Remove The King").performClick()
    composeTestRule.onNodeWithText("The King").assertIsNotDisplayed()

    composeTestRule.onNodeWithTag(EditFileLoreTestTags.LIST).performScrollToKey("save")
    composeTestRule.onNodeWithTag("edit_lore_save").performClick()
    composeTestRule.waitUntil(timeoutMillis = testTimeoutMillis) {
      viewModel.uiState.value.isSaved && savedClicked
    }

    assertEquals("The Lost Kingdom", viewModel.uiState.value.lore!!.title)
    assertEquals(
        listOf("Ancient history", "The prophecy"),
        viewModel.uiState.value.lore!!.sections.map { it.title },
    )
    assertEquals(
        listOf("Founded before the first age.", "A warning."),
        viewModel.uiState.value.lore!!.sections.map { it.description },
    )
    assertEquals(
        "The Lost Kingdom",
        repository.updatedFiles.single().let {
          com.github.se.jdrnexus.codec.JDRFileJsonCodec.deserializeLore(it.content).title
        },
    )

    composeTestRule.onNodeWithTag(EditFileLoreTestTags.LIST).performScrollToKey("top-bar")
    composeTestRule.onNodeWithTag(EditFileLoreTestTags.BACK).performClick()
    assertEquals(true, backClicked)
  }

  @Test
  fun displaysValidationErrorForBlankSectionTitle() {
    val file =
        JDRFile(
            id = "lore-invalid",
            name = "The Kingdom",
            type = DocumentType.TEXT,
            content =
                """{"version":1,"type":"lore","title":"The Kingdom","sections":[{"sectionID":"history","title":"History","content":"Founded long ago."}]}""",
        )
    val viewModel = EditFileViewModel(FakeWorkspaceRepository(file))
    viewModel.loadFile(file.id)
    composeTestRule.waitUntil(timeoutMillis = testTimeoutMillis) {
      viewModel.uiState.value.lore != null
    }

    composeTestRule.setContent { EditFileLore(viewModel = viewModel, onBack = {}, onSaved = {}) }
    val sectionTitle = composeTestRule.onNodeWithTag("section_title_history")
    sectionTitle.performTextClearance()
    composeTestRule.onNodeWithText("Section title cannot be empty.").assertIsDisplayed()

    composeTestRule.onNodeWithTag(EditFileLoreTestTags.LIST).performScrollToKey("save")
    composeTestRule.onNodeWithTag("edit_lore_save").performClick()
    composeTestRule.onNodeWithText("Section title cannot be empty.").assertIsDisplayed()
    assertEquals(false, viewModel.uiState.value.isSaved)
  }

  @Test
  fun displaysErrorStateWhenLoreIsNullAndAllowsBackNavigation() {
    val file =
        JDRFile(
            id = "lore-1",
            name = "The Kingdom",
            type = DocumentType.TEXT,
            content = "{}",
        )
    val viewModel = EditFileViewModel(FakeWorkspaceRepository(file))
    var backClicked = false
    viewModel.loadFile("non-existent-id")
    composeTestRule.waitUntil(timeoutMillis = testTimeoutMillis) {
      !viewModel.uiState.value.isLoading
    }

    composeTestRule.setContent {
      EditFileLore(
          viewModel = viewModel,
          onBack = { backClicked = true },
          onSaved = {},
      )
    }

    composeTestRule.onNodeWithText("File not found.").assertIsDisplayed()
    composeTestRule.onNodeWithTag(EditFileLoreTestTags.BACK).performClick()
    assertEquals(true, backClicked)
  }

  private class FakeWorkspaceRepository(private val file: JDRFile) : WorkspaceRepository {
    val updatedFiles = mutableListOf<JDRFile>()

    override fun getNewUid(): String = "generated-id"

    override suspend fun createFile(file: JDRFile): Result<Unit> = Result.success(Unit)

    override suspend fun updateFile(file: JDRFile): Result<Unit> {
      updatedFiles += file
      return Result.success(Unit)
    }

    override suspend fun deleteFile(fileId: String): Result<Unit> = Result.success(Unit)

    override suspend fun getFile(fileId: String): Result<JDRFile?> =
        Result.success(file.takeIf { it.id == fileId })

    override fun getFileFlow(fileId: String): Flow<JDRFile?> = emptyFlow()

    override fun getPersonalRootFiles(ownerId: String): Flow<List<JDRFile>> = emptyFlow()

    override fun getGroupRootFiles(groupId: String): Flow<List<JDRFile>> = emptyFlow()

    override fun getDocumentsInFolder(folderId: String): Flow<List<JDRFile>> = emptyFlow()
  }
}
