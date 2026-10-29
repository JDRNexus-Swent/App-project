// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.github.se.jdrnexus.ui.game

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.se.jdrnexus.model.filerepresentation.CharacterAction
import com.github.se.jdrnexus.model.filerepresentation.CharacterFile
import com.github.se.jdrnexus.model.filerepresentation.CharacterHealthPoints
import com.github.se.jdrnexus.model.filerepresentation.CharacterStat
import com.github.se.jdrnexus.model.personalSpace.JDRFile
import com.github.se.jdrnexus.ui.theme.SampleAppTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GameScreenPlayerTest {
  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun rendersDynamicCharacterDataAndRoutesDiceWorkspaceAndTabs() {
    val viewModel = FakeGamePlayerViewModel()
    var diceModifier: Int? = null
    var workspaceGroup: String? = null
    var selectedTab: String? = null
    setContent(
        viewModel = viewModel,
        onOpenDice = { diceModifier = it },
        onOpenGroupWorkspace = { workspaceGroup = it },
        onTabSelected = { selectedTab = it },
    )

    composeTestRule.onNodeWithText("Ilyra Vane").assertIsDisplayed()
    composeTestRule.onNodeWithText("Agility").assertIsDisplayed()
    composeTestRule.onNodeWithText("Value: 13.0").assertIsDisplayed()
    composeTestRule.onNodeWithText("Quickstep").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithText("Move with sudden speed.").assertIsDisplayed()
    composeTestRule.onNodeWithText("Take damage (−)").assertIsDisplayed()
    composeTestRule.onNodeWithText("Heal (+)").assertIsDisplayed()

    composeTestRule
        .onNodeWithTag(GameScreenPlayerTestTags.statRoll("stat-agility"))
        .performScrollTo()
        .performClick()
    composeTestRule.onNodeWithTag(GameScreenPlayerTestTags.WORKSPACE).performClick()
    composeTestRule.onNodeWithTag(GameScreenPlayerTestTags.navigationTab("Files")).performClick()

    assertEquals(0, diceModifier)
    assertEquals("group-1", workspaceGroup)
    assertEquals("Files", selectedTab)
    assertEquals(listOf("group-1"), viewModel.loadedGroups)
    assertEquals(1, viewModel.saveCalls)
    assertEquals(0, viewModel.statUpdates)
    assertEquals(0, viewModel.actionUpdates)
  }

  @Test
  fun hpButtonsUpdateViewModelAndInvalidInputIsExplained() {
    val viewModel = FakeGamePlayerViewModel()
    setContent(viewModel)

    composeTestRule.onNodeWithTag(GameScreenPlayerTestTags.HP_DECREASE).performClick()
    assertEquals(17, viewModel.selectedCharacter().character.hp.current)
    composeTestRule.onNodeWithTag(GameScreenPlayerTestTags.HP_INCREASE).performClick()
    assertEquals(18, viewModel.selectedCharacter().character.hp.current)
    assertEquals(listOf(17, 18), viewModel.hpUpdates)

    val hpInput = composeTestRule.onNodeWithTag(GameScreenPlayerTestTags.CURRENT_HP)
    hpInput.performTextClearance()
    hpInput.performTextInput("not-a-number")
    composeTestRule.onNodeWithText("Enter a whole number.").assertIsDisplayed()
    assertEquals(listOf(17, 18), viewModel.hpUpdates)
    assertEquals(18, viewModel.selectedCharacter().character.hp.current)
  }

  @Test
  fun savingDisplaysProgressErrorAndSuccessWithoutDiscardingEdits() {
    val viewModel = FakeGamePlayerViewModel()
    setContent(viewModel)

    composeTestRule.onNodeWithTag(GameScreenPlayerTestTags.HP_DECREASE).performClick()
    composeTestRule.onNodeWithTag(GameScreenPlayerTestTags.SAVE).performScrollTo().performClick()
    assertEquals(1, viewModel.saveCalls)

    composeTestRule.onNodeWithText("Saving changes...").performScrollTo().assertIsDisplayed()
    composeTestRule.onAllNodesWithTag(GameScreenPlayerTestTags.SAVE).assertCountEquals(0)

    viewModel.setState(viewModel.uiState.value.copy(isSaving = false, error = "Save failed."))
    composeTestRule.onNodeWithText("Save failed.").performScrollTo().assertIsDisplayed()
    assertEquals(17, viewModel.selectedCharacter().character.hp.current)

    composeTestRule.onNodeWithTag(GameScreenPlayerTestTags.SAVE).performScrollTo().performClick()
    assertEquals(2, viewModel.saveCalls)
    composeTestRule.waitForIdle()
    viewModel.setState(viewModel.uiState.value.copy(isSaving = false))
    composeTestRule
        .onNodeWithTag(GameScreenPlayerTestTags.SAVE_SUCCESS)
        .performScrollTo()
        .assertIsDisplayed()
  }

  @Test
  fun leavingScreenAlwaysSavesTheCharacter() {
    val viewModel = FakeGamePlayerViewModel()
    var backs = 0
    setContent(viewModel, onBack = { backs++ })
    composeTestRule.onNodeWithTag(GameScreenPlayerTestTags.BACK).performClick()
    assertEquals(1, viewModel.saveCalls)
    assertEquals(1, backs)
  }

  @Test
  fun leavingDuringSaveSubmitsHpEditsMadeAfterTheInFlightSnapshot() {
    val viewModel = FakeGamePlayerViewModel()
    setContent(viewModel)

    composeTestRule.onNodeWithTag(GameScreenPlayerTestTags.SAVE).performScrollTo().performClick()
    assertEquals(listOf(18), viewModel.savedHpValues)
    composeTestRule.onNodeWithTag(GameScreenPlayerTestTags.HP_INCREASE).performClick()
    assertEquals(19, viewModel.selectedCharacter().character.hp.current)

    composeTestRule.onNodeWithTag(GameScreenPlayerTestTags.BACK).performScrollTo().performClick()

    assertEquals(listOf(18, 19), viewModel.savedHpValues)
  }

  @Test
  fun loadingStateShowsProgress() {
    val loadingViewModel =
        FakeGamePlayerViewModel(GamePlayerUiState(groupId = "group-1", isLoading = true))
    setContent(loadingViewModel)
    composeTestRule.onNodeWithTag(GameScreenPlayerTestTags.LOADING).assertIsDisplayed()
  }

  @Test
  fun emptyStateShowsMessage() {
    val emptyViewModel = FakeGamePlayerViewModel(GamePlayerUiState(groupId = "group-1"))
    setContent(emptyViewModel)
    composeTestRule.onNodeWithTag(GameScreenPlayerTestTags.EMPTY).assertIsDisplayed()
  }

  private fun setContent(
      viewModel: FakeGamePlayerViewModel,
      onOpenDice: (Int) -> Unit = {},
      onOpenGroupWorkspace: (String) -> Unit = {},
      onTabSelected: (String) -> Unit = {},
      onBack: () -> Unit = {},
  ) {
    composeTestRule.setContent {
      SampleAppTheme {
        GameScreenPlayer(
            groupId = "group-1",
            viewModel = viewModel,
            onBack = onBack,
            onOpenDice = onOpenDice,
            onOpenGroupWorkspace = onOpenGroupWorkspace,
            onTabSelected = onTabSelected,
        )
      }
    }
  }

  private class FakeGamePlayerViewModel(
      initialState: GamePlayerUiState = initialGameState(),
  ) : GamePlayerInterfaceVM {
    override val uiState = MutableStateFlow(initialState)
    val loadedGroups = mutableListOf<String>()
    val hpUpdates = mutableListOf<Int>()
    val savedHpValues = mutableListOf<Int>()
    var saveCalls = 0
      private set

    var statUpdates = 0
      private set

    var actionUpdates = 0
      private set

    override fun loadSession(groupId: String) {
      loadedGroups += groupId
    }

    override fun selectCharacter(fileId: String) {
      uiState.value = uiState.value.copy(selectedCharacterId = fileId)
    }

    override fun updateCharacterStatValue(statID: String, value: Double) {
      statUpdates++
    }

    override fun updateCharacterActionName(actionID: String, name: String) {
      actionUpdates++
    }

    override fun updateCharacterActionDescription(actionID: String, description: String) {
      actionUpdates++
    }

    override fun updateCharacterHp(current: Int) {
      hpUpdates += current
      val selected = selectedCharacter()
      if (current < 0 || current > selected.character.hp.max) {
        uiState.value = uiState.value.copy(hpError = "HP must be between 0 and 24.")
      } else {
        val updatedCharacter =
            selected.copy(
                character =
                    selected.character.copy(
                        hp = selected.character.hp.copy(current = current),
                    ),
                hasUnsavedChanges = true,
            )
        uiState.value =
            uiState.value.copy(
                characters =
                    uiState.value.characters.map {
                      if (it.file.id == updatedCharacter.file.id) updatedCharacter else it
                    },
                hpError = null,
            )
      }
    }

    override fun updateCharacterMaxHp(max: Int) = Unit

    override fun saveCharacter() {
      saveCalls++
      savedHpValues += selectedCharacter().character.hp.current
      uiState.value = uiState.value.copy(isSaving = true, error = null)
    }

    override fun dismissError() {
      uiState.value = uiState.value.copy(error = null, hpError = null)
    }

    fun selectedCharacter() = requireNotNull(uiState.value.selectedCharacter)

    fun setState(state: GamePlayerUiState) {
      uiState.value = state
    }
  }

  companion object {
    private fun initialGameState(): GamePlayerUiState {
      val character =
          SessionCharacter(
              file = JDRFile(id = "character-1", name = "Ilyra Vane"),
              character =
                  CharacterFile(
                      name = "Ilyra Vane",
                      stats = listOf(CharacterStat("stat-agility", "Agility", 13.0)),
                      actions =
                          listOf(
                              CharacterAction(
                                  "action-quickstep",
                                  "Quickstep",
                                  "Move with sudden speed.",
                              )
                          ),
                      hp = CharacterHealthPoints(current = 18, max = 24),
                  ),
              isEditable = true,
          )
      return GamePlayerUiState(
          groupId = "group-1",
          characters = listOf(character),
          selectedCharacterId = character.file.id,
      )
    }
  }
}
