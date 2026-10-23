// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.github.se.jdrnexus.screen

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.se.jdrnexus.model.personalSpace.DocumentType
import com.github.se.jdrnexus.model.personalSpace.JDRFile
import com.github.se.jdrnexus.model.personalSpace.WorkspaceRepository
import com.github.se.jdrnexus.ui.edition.EditFileCharacter
import com.github.se.jdrnexus.ui.edition.EditFileCharacterTestTags
import com.github.se.jdrnexus.ui.jdrfile.EditFileViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EditFileCharacterTests {
  @get:Rule val composeTestRule = createComposeRule()

  val timeOutConstant = 1000L

  @Test
  fun preservesDecimalPointWhileTypingStatValue() {
    val file =
        JDRFile(
            id = "decimal-character",
            name = "Decimal Test",
            type = DocumentType.CHARACTER,
            content =
                """{"version":1,"type":"character","character":{"name":"Decimal Test","stats":[{"statID":"decimal-stat","name":"Decimal stat","value":2}],"actions":[],"hp":{"current":10,"max":10}}}""",
        )
    val viewModel = EditFileViewModel(FakeWorkspaceRepository(file))
    viewModel.loadFile(file.id)
    composeTestRule.waitUntil(timeoutMillis = timeOutConstant) {
      viewModel.uiState.value.character != null
    }

    composeTestRule.setContent {
      EditFileCharacter(viewModel = viewModel, onBack = {}, onSaved = {})
    }

    val valueField = composeTestRule.onNodeWithTag("stat_value_decimal-stat")
    valueField.performTextClearance()
    valueField.performTextInput("1.")
    valueField.assertTextEquals("Value", "1.")
    assertEquals(1.0, viewModel.uiState.value.character!!.stats.single().value, 0.0)

    valueField.performTextInput("5")
    valueField.assertTextEquals("Value", "1.5")
    assertEquals(1.5, viewModel.uiState.value.character!!.stats.single().value, 0.0)
  }

  @Test
  fun displaysAndEditsCharacterHp() {
    val file =
        JDRFile(
            id = "hp-character",
            name = "HP Test",
            type = DocumentType.CHARACTER,
            content =
                """{"version":1,"type":"character","character":{"name":"HP Test","stats":[],"actions":[],"hp":{"current":15,"max":20}}}""",
        )
    val viewModel = EditFileViewModel(FakeWorkspaceRepository(file))
    viewModel.loadFile(file.id)
    composeTestRule.waitUntil(timeoutMillis = timeOutConstant) {
      viewModel.uiState.value.character != null
    }

    composeTestRule.setContent {
      EditFileCharacter(viewModel = viewModel, onBack = {}, onSaved = {})
    }

    val currentHpField = composeTestRule.onNodeWithTag(EditFileCharacterTestTags.CURRENT_HP)
    val maxHpField = composeTestRule.onNodeWithTag(EditFileCharacterTestTags.MAX_HP)

    currentHpField.assertIsDisplayed()
    maxHpField.assertIsDisplayed()
    currentHpField.assertTextEquals("Current HP", "15")
    maxHpField.assertTextEquals("Max HP", "20")

    currentHpField.performTextClearance()
    currentHpField.performTextInput("18")
    assertEquals(18, viewModel.uiState.value.character!!.hp.current)

    maxHpField.performTextClearance()
    maxHpField.performTextInput("25")
    assertEquals(25, viewModel.uiState.value.character!!.hp.max)
  }

  @Test
  fun editsAddsRemovesAndSavesCharacterStatsAndActions() {
    val file =
        JDRFile(
            id = "character-1",
            name = "Elara Moonwhisper",
            type = DocumentType.CHARACTER,
            content =
                """{"version":1,"type":"character","character":{"name":"Elara Moonwhisper","stats":[{"statID":"strength","name":"Strength","value":16},{"statID":"dexterity","name":"Dexterity","value":12}],"actions":[{"actionID":"longbow","name":"Longbow","description":"Ranged attack"}],"hp":{"current":18,"max":24}}}""",
        )
    val updateGate = CompletableDeferred<Unit>()
    val repository = FakeWorkspaceRepository(file, updateGate)
    val viewModel = EditFileViewModel(repository)
    var backClicked = false
    var savedClicked = false
    viewModel.loadFile(file.id)
    composeTestRule.waitUntil(timeoutMillis = timeOutConstant) {
      viewModel.uiState.value.character != null
    }

    composeTestRule.setContent {
      EditFileCharacter(
          viewModel = viewModel,
          onBack = { backClicked = true },
          onSaved = { savedClicked = true },
      )
    }
    composeTestRule.onNodeWithTag(EditFileCharacterTestTags.STATS_LIST).assertIsDisplayed()
    composeTestRule.onNodeWithTag(EditFileCharacterTestTags.ACTIONS_LIST).assertIsDisplayed()

    composeTestRule.onNodeWithTag("edit_character_name").performTextClearance()
    composeTestRule.onNodeWithTag("edit_character_name").performTextInput("Elara Starwhisper")
    composeTestRule.onNodeWithTag(EditFileCharacterTestTags.CURRENT_HP).performTextClearance()
    composeTestRule.onNodeWithTag(EditFileCharacterTestTags.CURRENT_HP).performTextInput("20")
    composeTestRule.onNodeWithTag(EditFileCharacterTestTags.MAX_HP).performTextClearance()
    composeTestRule.onNodeWithTag(EditFileCharacterTestTags.MAX_HP).performTextInput("30")
    composeTestRule.onNodeWithTag("stat_name_strength").performTextClearance()
    composeTestRule.onNodeWithTag("stat_name_strength").performTextInput("Wisdom")
    composeTestRule.onNodeWithTag("stat_value_strength").performTextClearance()
    composeTestRule.onNodeWithTag("stat_value_strength").performTextInput("18")
    composeTestRule.onNodeWithTag("action_name_longbow").performTextClearance()
    composeTestRule.onNodeWithTag("action_name_longbow").performTextInput("Arcane Longbow")
    composeTestRule.onNodeWithTag("action_description_longbow").performTextClearance()
    composeTestRule.onNodeWithTag("action_description_longbow").performTextInput("A magic shot")

    composeTestRule.onNodeWithTag("add_entry_name").performTextInput("Perception")
    composeTestRule.onNodeWithTag("add_entry_value").performTextInput("14")
    composeTestRule.onNodeWithTag("confirm_add_stat").performScrollTo().performClick()
    composeTestRule.onNodeWithText("Perception").assertIsDisplayed()
    composeTestRule.onNodeWithTag("add_entry_name").performTextInput("Intelligence")
    composeTestRule.onNodeWithTag("add_entry_value").performTextInput("15")
    composeTestRule.onNodeWithTag("confirm_add_stat").performScrollTo().performClick()
    composeTestRule.onNodeWithText("Intelligence").assertIsDisplayed()

    composeTestRule.onNodeWithTag("add_action_name").performTextInput("Hunter's Mark")
    composeTestRule.onNodeWithTag("add_action_description").performTextInput("Mark a target")
    composeTestRule.onNodeWithTag("confirm_add_action").performScrollTo().performClick()
    composeTestRule.onNodeWithText("Hunter's Mark").assertIsDisplayed()
    composeTestRule.onNodeWithTag("add_action_name").performTextInput("Healing Word")
    composeTestRule.onNodeWithTag("add_action_description").performTextInput("Restore hit points")
    composeTestRule.onNodeWithTag("confirm_add_action").performScrollTo().performClick()
    composeTestRule.onNodeWithText("Healing Word").assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(EditFileCharacterTestTags.LIST)
        .performScrollToKey("stat-dexterity")
    composeTestRule.onNodeWithTag("remove_stat_dexterity").performClick()
    composeTestRule
        .onNodeWithTag(EditFileCharacterTestTags.LIST)
        .performScrollToKey("action-longbow")
    composeTestRule.onNodeWithTag("remove_action_longbow").performClick()
    composeTestRule.onNodeWithText("Dexterity").assertIsNotDisplayed()
    composeTestRule.onNodeWithText("Arcane Longbow").assertIsNotDisplayed()

    composeTestRule.onNodeWithTag("edit_character_save").performScrollTo().performClick()
    composeTestRule.waitUntil(timeoutMillis = timeOutConstant) {
      repository.updatedFiles.isNotEmpty()
    }
    assertEquals(false, savedClicked)
    updateGate.complete(Unit)
    composeTestRule.waitUntil(timeoutMillis = timeOutConstant) {
      viewModel.uiState.value.isSaved && savedClicked
    }

    val saved = viewModel.uiState.value.character!!
    assertEquals("Elara Starwhisper", saved.name)
    assertEquals(20, saved.hp.current)
    assertEquals(30, saved.hp.max)
    assertEquals(listOf("Wisdom", "Perception", "Intelligence"), saved.stats.map { it.name })
    assertEquals(listOf(18.0, 14.0, 15.0), saved.stats.map { it.value })
    assertEquals(listOf("Hunter's Mark", "Healing Word"), saved.actions.map { it.name })
    assertEquals(
        listOf("Mark a target", "Restore hit points"),
        saved.actions.map { it.description },
    )

    composeTestRule.onNode(hasScrollToIndexAction()).performScrollToIndex(0)
    composeTestRule.onNodeWithTag(EditFileCharacterTestTags.BACK).performClick()
    assertEquals(true, backClicked)
  }

  @Test
  fun displaysErrorStateWhenCharacterIsNullAndAllowsBackNavigation() {
    val file =
        JDRFile(
            id = "character-1",
            name = "Elara Moonwhisper",
            type = DocumentType.CHARACTER,
            content = "{}",
        )
    val viewModel = EditFileViewModel(FakeWorkspaceRepository(file))
    var backClicked = false
    viewModel.loadFile("non-existent-id")
    composeTestRule.waitUntil(timeoutMillis = timeOutConstant) {
      !viewModel.uiState.value.isLoading
    }

    composeTestRule.setContent {
      EditFileCharacter(
          viewModel = viewModel,
          onBack = { backClicked = true },
          onSaved = {},
      )
    }

    composeTestRule.onNodeWithText("File not found.").assertIsDisplayed()
    composeTestRule.onNodeWithTag(EditFileCharacterTestTags.BACK).performClick()
    assertEquals(true, backClicked)
  }

  @Test
  fun displaysLoadingStateWhenFileIsLoading() {
    val file =
        JDRFile(
            id = "character-1",
            name = "Elara Moonwhisper",
            type = DocumentType.CHARACTER,
            content = "{}",
        )
    val loadGate = CompletableDeferred<Unit>()
    val repository = FakeWorkspaceRepository(file, loadGate = loadGate)
    val viewModel = EditFileViewModel(repository)
    viewModel.loadFile("character-1")

    composeTestRule.setContent {
      EditFileCharacter(
          viewModel = viewModel,
          onBack = {},
          onSaved = {},
      )
    }

    composeTestRule.onNodeWithText("Loading character...").assertIsDisplayed()
    loadGate.complete(Unit)
    composeTestRule.waitUntil(timeoutMillis = timeOutConstant) {
      !viewModel.uiState.value.isLoading
    }
  }

  @Test
  fun failingSaveWithInvalidFieldDoesNotAllowSubsequentSaveWithoutFixingField() {
    val file =
        JDRFile(
            id = "character-1",
            name = "Elara Moonwhisper",
            type = DocumentType.CHARACTER,
            content =
                """{"version":1,"type":"character","character":{"name":"Elara Moonwhisper","stats":[{"statID":"strength","name":"Strength","value":16}],"actions":[],"hp":{"current":18,"max":24}}}""",
        )
    val repository = FakeWorkspaceRepository(file)
    val viewModel = EditFileViewModel(repository)
    viewModel.loadFile(file.id)
    composeTestRule.waitUntil(timeoutMillis = timeOutConstant) {
      viewModel.uiState.value.character != null
    }

    composeTestRule.setContent {
      EditFileCharacter(viewModel = viewModel, onBack = {}, onSaved = {})
    }

    composeTestRule.onNodeWithTag("stat_name_strength").performTextClearance()

    composeTestRule.onNodeWithTag(EditFileCharacterTestTags.LIST).performScrollToKey("save")
    composeTestRule.onNodeWithTag("edit_character_save").performClick()
    assertEquals(0, repository.updatedFiles.size)
    assertEquals(false, viewModel.uiState.value.isSaved)

    composeTestRule.onNodeWithTag(EditFileCharacterTestTags.LIST).performScrollToKey("save")
    composeTestRule.onNodeWithTag("edit_character_save").performClick()
    assertEquals(0, repository.updatedFiles.size)
    assertEquals(false, viewModel.uiState.value.isSaved)
  }

  private class FakeWorkspaceRepository(
      private val file: JDRFile,
      private val updateGate: CompletableDeferred<Unit>? = null,
      private val loadGate: CompletableDeferred<Unit>? = null,
  ) : WorkspaceRepository {
    val updatedFiles = mutableListOf<JDRFile>()

    override fun getNewUid(): String = "generated-id"

    override suspend fun createFile(file: JDRFile): Result<Unit> = Result.success(Unit)

    override suspend fun updateFile(file: JDRFile): Result<Unit> {
      updatedFiles += file
      updateGate?.await()
      return Result.success(Unit)
    }

    override suspend fun deleteFile(fileId: String): Result<Unit> = Result.success(Unit)

    override suspend fun getFile(fileId: String): Result<JDRFile?> {
      loadGate?.await()
      return Result.success(file.takeIf { it.id == fileId })
    }

    override suspend fun removeItemFromGroup(
        itemId: String,
        groupId: String,
        parentFolderId: String,
    ): Result<Unit> {
      error("not used")
    }

    override fun getFileFlow(fileId: String): Flow<JDRFile?> = emptyFlow()

    override fun getPersonalRootFiles(ownerId: String): Flow<List<JDRFile>> = emptyFlow()

    override fun getGroupRootFiles(groupId: String): Flow<List<JDRFile>> = emptyFlow()

    override fun getDocumentsInFolder(folderId: String): Flow<List<JDRFile>> = emptyFlow()
  }
}
