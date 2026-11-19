// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
// Co-authored-by: Codex <noreply@openai.com>
package com.github.se.jdrnexus.ui.game

import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.se.jdrnexus.model.filerepresentation.CharacterAction
import com.github.se.jdrnexus.model.filerepresentation.CharacterFile
import com.github.se.jdrnexus.model.filerepresentation.CharacterHealthPoints
import com.github.se.jdrnexus.model.filerepresentation.CharacterStat
import com.github.se.jdrnexus.model.personalSpace.JDRFile
import com.github.se.jdrnexus.resources.C
import com.github.se.jdrnexus.ui.theme.SampleAppTheme
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
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
        .onNodeWithTag(C.Tag.gamePlayerStatRoll("stat-agility"))
        .performScrollTo()
        .performClick()
    composeTestRule.onNodeWithTag(C.Tag.game_player_workspace).performClick()
    composeTestRule.onNodeWithTag(C.Tag.bottomTab("files")).performClick()

    composeTestRule.waitForIdle()
    assertEquals(0, diceModifier)
    assertEquals("group-1", workspaceGroup)
    assertEquals("Files", selectedTab)
    assertEquals(listOf("group-1"), viewModel.loadedGroups)
    assertEquals(0, viewModel.saveCalls)
  }

  @Test
  fun hpButtonsUpdateViewModelAndInvalidInputIsExplained() {
    val viewModel = FakeGamePlayerViewModel()
    setContent(viewModel)

    composeTestRule.onNodeWithTag(C.Tag.game_player_hp_decrease).performClick()
    assertEquals(17, viewModel.selectedCharacter().character.hp.current)
    composeTestRule.onNodeWithTag(C.Tag.game_player_hp_increase).performClick()
    assertEquals(18, viewModel.selectedCharacter().character.hp.current)
    assertEquals(listOf(17, 18), viewModel.hpUpdates)

    val hpInput = composeTestRule.onNodeWithTag(C.Tag.game_player_current_hp)
    hpInput.performTextClearance()
    hpInput.performTextInput("not-a-number")
    composeTestRule.onNodeWithText("Enter a whole number.").assertIsDisplayed()
    assertEquals(listOf(17, 18), viewModel.hpUpdates)
    assertEquals(18, viewModel.selectedCharacter().character.hp.current)
  }

  @Test
  fun hpMinimumDisablesDamageAndDisplaysValidationErrorOnce() {
    val initial = initialGameState()
    val character = requireNotNull(initial.character)
    val state =
        initial.copy(
            character =
                character.copy(
                    character =
                        character.character.copy(hp = CharacterHealthPoints(current = 0, max = 24))
                ),
            hpError = "HP must be between 0 and 24.",
        )
    setContent(FakeGamePlayerViewModel(state))

    composeTestRule.onNodeWithTag(C.Tag.game_player_hp_decrease).assertIsNotEnabled()
    composeTestRule
        .onAllNodesWithText("HP must be between 0 and 24.")
        .assertCountEquals(1) // not very relevant, just show an hp error is displayed
  }

  @Test
  fun hpRangeErrorIsShownOnceAndClearsAfterValidInput() {
    val viewModel = FakeGamePlayerViewModel()
    setContent(viewModel)

    val hpInput = composeTestRule.onNodeWithTag(C.Tag.game_player_current_hp)
    hpInput.performTextClearance()
    hpInput.performTextInput("25")

    composeTestRule.onAllNodesWithText("HP must be between 0 and 24.").assertCountEquals(1)
    assertEquals(18, viewModel.selectedCharacter().character.hp.current)

    hpInput.performTextClearance()
    hpInput.performTextInput("24")

    composeTestRule.onAllNodesWithText("HP must be between 0 and 24.").assertCountEquals(0)
    assertEquals(24, viewModel.selectedCharacter().character.hp.current)
  }

  @Test
  fun fullHealthDisablesHealing() {
    val viewModel = FakeGamePlayerViewModel()
    viewModel.updateCharacterHp(24)
    setContent(viewModel)

    composeTestRule.onNodeWithTag(C.Tag.game_player_hp_increase).assertIsNotEnabled()
  }

  @Test
  fun focusedHpFieldPreservesLeadingZero() {
    val viewModel = FakeGamePlayerViewModel()
    setContent(viewModel)
    val hpInput = composeTestRule.onNodeWithTag(C.Tag.game_player_current_hp)

    hpInput.performTextClearance()
    hpInput.performTextInput("05")

    hpInput.assert(hasText("05"))
    assertEquals(listOf(5), viewModel.hpUpdates)
  }

  @Test
  fun focusedHpFieldUpdatesAfterTakingDamage() {
    val viewModel = FakeGamePlayerViewModel()
    setContent(viewModel)
    val hpInput = composeTestRule.onNodeWithTag(C.Tag.game_player_current_hp)
    val damage = composeTestRule.onNodeWithTag(C.Tag.game_player_hp_decrease)

    hpInput.performClick()
    repeat(3) { damage.performClick() }

    hpInput.assertIsFocused().assert(hasText("15"))
    assertEquals(listOf(17, 16, 15), viewModel.hpUpdates)
  }

  @Test
  fun maximumHpDisablesHealingWithoutOverflow() {
    val initial = initialGameState()
    val character = requireNotNull(initial.character)
    val state =
        initial.copy(
            character =
                character.copy(
                    character =
                        character.character.copy(
                            hp = CharacterHealthPoints(current = Int.MAX_VALUE, max = Int.MAX_VALUE)
                        )
                )
        )
    setContent(FakeGamePlayerViewModel(state))

    composeTestRule.onNodeWithTag(C.Tag.game_player_hp_increase).assertIsNotEnabled()
    composeTestRule.onNodeWithText("Max HP: ${Int.MAX_VALUE}").assertIsDisplayed()
  }

  @Test
  fun savingDisplaysProgressErrorAndSuccessWithoutDiscardingEdits() {
    val viewModel = FakeGamePlayerViewModel()
    setContent(viewModel)

    composeTestRule.onNodeWithTag(C.Tag.game_player_hp_decrease).performClick()
    composeTestRule.onNodeWithTag(C.Tag.game_player_save).performScrollTo().performClick()
    assertEquals(1, viewModel.saveCalls)

    composeTestRule.onNodeWithText("Saving changes...").performScrollTo().assertIsDisplayed()
    composeTestRule.onAllNodesWithTag(C.Tag.game_player_save).assertCountEquals(0)

    viewModel.setState(viewModel.uiState.value.copy(isSaving = false, error = "Save failed."))
    composeTestRule.onNodeWithText("Save failed.").performScrollTo().assertIsDisplayed()
    assertEquals(17, viewModel.selectedCharacter().character.hp.current)

    composeTestRule.onNodeWithTag(C.Tag.game_player_save).performScrollTo().performClick()
    assertEquals(2, viewModel.saveCalls)
    composeTestRule.waitForIdle()
    viewModel.completeSave()
    composeTestRule
        .onNodeWithTag(C.Tag.game_player_save_success)
        .performScrollTo()
        .assertIsDisplayed()
  }

  @Test
  fun newHpEditClearsSaveConfirmation() {
    val viewModel = FakeGamePlayerViewModel()
    setContent(viewModel)
    val damage = composeTestRule.onNodeWithTag(C.Tag.game_player_hp_decrease)
    damage.performClick()
    composeTestRule.onNodeWithTag(C.Tag.game_player_save).performScrollTo().performClick()
    composeTestRule.waitForIdle()
    viewModel.completeSave()
    composeTestRule
        .onNodeWithTag(C.Tag.game_player_save_success)
        .performScrollTo()
        .assertIsDisplayed()

    damage.performScrollTo().performClick()

    composeTestRule.onNodeWithTag(C.Tag.game_player_save).performScrollTo()
    composeTestRule.onAllNodesWithTag(C.Tag.game_player_save_success).assertCountEquals(0)
  }

  @Test
  fun leavingScreenWaitsForSuccessfulSave() {
    val pendingSave = CompletableDeferred<Result<Unit>>()
    val viewModel = FakeGamePlayerViewModel(pendingSave = pendingSave)
    var backs = 0
    setContent(viewModel, onBack = { backs++ })

    composeTestRule.onNodeWithTag(C.Tag.game_player_back).performClick()

    composeTestRule.waitForIdle()
    assertEquals(0, backs)
    pendingSave.complete(Result.success(Unit))
    composeTestRule.waitForIdle()
    assertEquals(1, backs)
  }

  @Test
  fun selectingFilesWaitsForSuccessfulSave() {
    val pendingSave = CompletableDeferred<Result<Unit>>()
    val viewModel = FakeGamePlayerViewModel(pendingSave = pendingSave)
    var selectedTab: String? = null
    setContent(viewModel, onTabSelected = { selectedTab = it })
    composeTestRule.onNodeWithTag(C.Tag.game_player_hp_decrease).performClick()

    composeTestRule.onNodeWithTag(C.Tag.bottomTab("files")).performClick()
    composeTestRule.waitForIdle()
    assertEquals(null, selectedTab)

    pendingSave.complete(Result.success(Unit))
    composeTestRule.waitForIdle()
    assertEquals("Files", selectedTab)
  }

  @Test
  fun stayingAfterFailedTabSaveKeepsCampaignsSelected() {
    val pendingSave = CompletableDeferred<Result<Unit>>()
    val viewModel = FakeGamePlayerViewModel(pendingSave = pendingSave)
    var selectedTab: String? = null
    setContent(viewModel, onTabSelected = { selectedTab = it })
    composeTestRule.onNodeWithTag(C.Tag.game_player_hp_decrease).performClick()

    composeTestRule.onNodeWithTag(C.Tag.bottomTab("files")).performClick()
    composeTestRule.onNodeWithTag(C.Tag.bottomTab("campaigns")).assertIsSelected()
    pendingSave.complete(Result.failure(IllegalStateException("Save failed.")))
    composeTestRule.onNodeWithTag(C.Tag.game_player_leave_error).assertTextEquals("Save failed.")
    composeTestRule.onNodeWithTag(C.Tag.game_player_stay).performClick()

    composeTestRule.onNodeWithTag(C.Tag.bottomTab("campaigns")).assertIsSelected()
    assertEquals(null, selectedTab)
  }

  @Test
  fun recreatedScreenShowsFailedSaveAndCanOpenWorkspace() {
    val pendingSave = CompletableDeferred<Result<Unit>>()
    val viewModel = FakeGamePlayerViewModel(pendingSave = pendingSave)
    var workspaceGroup: String? = null
    val screenInstance = mutableStateOf(0)
    setContent(
        viewModel,
        onOpenGroupWorkspace = { workspaceGroup = it },
        screenInstance = { screenInstance.value },
    )
    composeTestRule.onNodeWithTag(C.Tag.game_player_hp_decrease).performClick()

    composeTestRule.onNodeWithTag(C.Tag.game_player_workspace).performClick()
    assertEquals(null, workspaceGroup)
    composeTestRule.onNodeWithTag(C.Tag.game_player_leave_error).assertDoesNotExist()
    pendingSave.complete(Result.failure(IllegalStateException("Save failed.")))
    composeTestRule.onNodeWithTag(C.Tag.game_player_leave_error).assertTextEquals("Save failed.")
    assertEquals(null, workspaceGroup)
    composeTestRule.runOnIdle { screenInstance.value++ }
    composeTestRule.onNodeWithTag(C.Tag.game_player_leave_error).assertTextEquals("Save failed.")

    composeTestRule.onNodeWithTag(C.Tag.game_player_leave_anyway).performClick()
    composeTestRule.waitForIdle()
    assertEquals("group-1", workspaceGroup)
  }

  @Test
  fun recreatedScreenOpensDiceAfterSuccessfulSave() {
    val pendingSave = CompletableDeferred<Result<Unit>>()
    val viewModel = FakeGamePlayerViewModel(pendingSave = pendingSave)
    val screenInstance = mutableStateOf(0)
    val openedDice = mutableListOf<Pair<Int, Int>>()
    setContent(
        viewModel,
        onOpenDiceForInstance = { instance, modifier -> openedDice += instance to modifier },
        screenInstance = { screenInstance.value },
    )
    composeTestRule.onNodeWithTag(C.Tag.game_player_hp_decrease).performClick()

    composeTestRule
        .onNodeWithTag(C.Tag.gamePlayerStatRoll("stat-agility"))
        .performScrollTo()
        .performClick()
    composeTestRule.waitForIdle()
    assertEquals(emptyList<Pair<Int, Int>>(), openedDice)
    composeTestRule.runOnIdle { screenInstance.value++ }
    composeTestRule.waitForIdle()

    pendingSave.complete(Result.success(Unit))
    composeTestRule.waitForIdle()
    assertEquals(listOf(1 to 0), openedDice)
  }

  @Test
  fun stayingAfterFailedAutoSavePreservesUnsavedHp() {
    val pendingSave = CompletableDeferred<Result<Unit>>()
    val viewModel = FakeGamePlayerViewModel(pendingSave = pendingSave)
    var backs = 0
    setContent(viewModel, onBack = { backs++ })
    composeTestRule.onNodeWithTag(C.Tag.game_player_hp_decrease).performClick()
    failSaveOnBack(pendingSave)

    composeTestRule.onNodeWithTag(C.Tag.game_player_stay).performClick()

    composeTestRule.onAllNodesWithTag(C.Tag.game_player_leave_error).assertCountEquals(0)
    assertEquals(null, viewModel.uiState.value.navigation)
    assertEquals(0, backs)
    assertEquals(17, viewModel.selectedCharacter().character.hp.current)
    assertEquals(true, viewModel.selectedCharacter().hasUnsavedChanges)
  }

  @Test
  fun confirmingLeaveAfterFailedAutoSaveNavigatesBack() {
    val pendingSave = CompletableDeferred<Result<Unit>>()
    val viewModel = FakeGamePlayerViewModel(pendingSave = pendingSave)
    var backs = 0
    setContent(viewModel, onBack = { backs++ })
    failSaveOnBack(pendingSave)
    assertEquals(0, backs)

    composeTestRule.onNodeWithTag(C.Tag.game_player_leave_anyway).performClick()

    composeTestRule.waitForIdle()
    assertEquals(1, backs)
  }

  @Test
  fun loadingStateShowsProgress() {
    val loadingViewModel =
        FakeGamePlayerViewModel(GamePlayerUiState(groupId = "group-1", isLoading = true))
    setContent(loadingViewModel)
    composeTestRule.onNodeWithTag(C.Tag.game_player_loading).assertIsDisplayed()
  }

  @Test
  fun emptyStateShowsMessage() {
    val emptyViewModel = FakeGamePlayerViewModel(GamePlayerUiState(groupId = "group-1"))
    setContent(emptyViewModel)
    composeTestRule.onNodeWithTag(C.Tag.game_player_empty).assertIsDisplayed()
  }

  @Test
  fun errorWithoutCharacterDisablesWorkspaceAndBackDoesNotSave() {
    val viewModel = FakeGamePlayerViewModel(GamePlayerUiState(error = "Session unavailable."))
    var backs = 0
    setContent(viewModel, onBack = { backs++ })

    composeTestRule.onNodeWithTag(C.Tag.game_player_error).assertIsDisplayed()
    composeTestRule.onNodeWithText("Session unavailable.").assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.game_player_workspace).assertIsNotEnabled()
    composeTestRule.onNodeWithTag(C.Tag.game_player_back).performClick()

    composeTestRule.waitForIdle()
    assertEquals(0, viewModel.saveCalls)
    assertEquals(1, backs)
  }

  @Test
  fun groupChangeReloadsSession() {
    val viewModel = FakeGamePlayerViewModel()
    val groupId = mutableStateOf("group-1")
    composeTestRule.setContent {
      SampleAppTheme {
        GameScreenPlayer(
            groupId = groupId.value,
            viewModel = viewModel,
            onBack = {},
            onOpenDice = {},
            onOpenGroupWorkspace = {},
            onTabSelected = {},
        )
      }
    }

    assertEquals(listOf("group-1"), viewModel.loadedGroups)
    composeTestRule.runOnIdle { groupId.value = "group-2" }
    composeTestRule.waitForIdle()
    composeTestRule.runOnIdle { groupId.value = "group-3" }
    composeTestRule.waitForIdle()

    assertEquals(listOf("group-1", "group-2", "group-3"), viewModel.loadedGroups)
  }

  private fun failSaveOnBack(pendingSave: CompletableDeferred<Result<Unit>>) {
    composeTestRule.onNodeWithTag(C.Tag.game_player_back).performClick()
    pendingSave.complete(Result.failure(IllegalStateException("Save failed.")))
    composeTestRule
        .onNodeWithTag(C.Tag.game_player_leave_error)
        .assertIsDisplayed()
        .assertTextEquals("Save failed.")
  }

  private fun setContent(
      viewModel: FakeGamePlayerViewModel,
      onOpenDice: (Int) -> Unit = {},
      onOpenGroupWorkspace: (String) -> Unit = {},
      onTabSelected: (String) -> Unit = {},
      onBack: () -> Unit = {},
      screenInstance: () -> Int = { 0 },
      onOpenDiceForInstance: (Int, Int) -> Unit = { _, modifier -> onOpenDice(modifier) },
  ) {
    composeTestRule.setContent {
      SampleAppTheme {
        val instance = screenInstance()
        key(instance) {
          GameScreenPlayer(
              groupId = "group-1",
              viewModel = viewModel,
              onBack = onBack,
              onOpenDice = { onOpenDiceForInstance(instance, it) },
              onOpenGroupWorkspace = onOpenGroupWorkspace,
              onTabSelected = onTabSelected,
          )
        }
      }
    }
  }

  private class FakeGamePlayerViewModel(
      initialState: GamePlayerUiState = initialGameState(),
      private val pendingSave: CompletableDeferred<Result<Unit>>? = null,
  ) : ViewModel(), GamePlayerInterfaceVM {
    override val uiState = MutableStateFlow(initialState)
    val loadedGroups = mutableListOf<String>()
    val hpUpdates = mutableListOf<Int>()
    var saveCalls = 0
      private set

    override fun loadSession(groupId: String) {
      loadedGroups += groupId
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
                character = updatedCharacter,
                hpError = null,
            )
      }
    }

    override fun saveCharacter() {
      saveCalls++
      uiState.value = uiState.value.copy(isSaving = true, error = null)
    }

    override fun requestNavigation(destination: GamePlayerDestination) {
      if (uiState.value.navigation != null) return
      uiState.value = uiState.value.copy(navigation = GamePlayerNavigation.Saving(destination))
      viewModelScope.launch {
        val failure = pendingSave?.await()?.exceptionOrNull()
        val navigation =
            if (failure == null) GamePlayerNavigation.Ready(destination)
            else GamePlayerNavigation.SaveFailed(destination, requireNotNull(failure.message))
        uiState.value = uiState.value.copy(navigation = navigation)
      }
    }

    override fun stayOnScreen() {
      uiState.value = uiState.value.copy(navigation = null)
    }

    override fun leaveAnyway() {
      val failed = uiState.value.navigation as GamePlayerNavigation.SaveFailed
      uiState.value =
          uiState.value.copy(navigation = GamePlayerNavigation.Ready(failed.destination))
    }

    override fun consumeNavigation(): GamePlayerDestination? {
      val ready = uiState.value.navigation as? GamePlayerNavigation.Ready ?: return null
      uiState.value = uiState.value.copy(navigation = null)
      return ready.destination
    }

    override fun dismissError() {
      uiState.value = uiState.value.copy(error = null, hpError = null)
    }

    fun selectedCharacter() = requireNotNull(uiState.value.character)

    fun completeSave() {
      uiState.value =
          uiState.value.copy(
              isSaving = false,
              character = selectedCharacter().copy(hasUnsavedChanges = false),
          )
    }

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
          )
      return GamePlayerUiState(
          groupId = "group-1",
          character = character,
      )
    }
  }
}
