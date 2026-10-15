// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.ui.dice

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.se.jdrnexus.resources.C
import com.github.se.jdrnexus.ui.theme.SampleAppTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Fake ViewModel with a fixed raw roll that records every call made by the screen. */
private class FakeDiceViewModel(
    initial: DiceUiState = DiceUiState(),
    private val nextRawValue: Int = 7,
) : DiceInterfaceVM {
  val state = MutableStateFlow(initial)
  val calls = mutableListOf<String>()

  override fun selectDice(diceType: DiceType) {
    calls += "select:$diceType"
    state.value = state.value.copy(selectedDice = diceType)
  }

  override fun setModifier(modifier: Int) {
    calls += "set:$modifier"
    state.value = state.value.copy(modifier = modifier)
  }

  override fun increaseModifier(modifier: Int) {
    calls += "increase:$modifier"
    state.value = state.value.copy(modifier = state.value.modifier + modifier)
  }

  override fun rollDice() {
    calls += "roll"
    val s = state.value
    state.value =
        s.copy(
            currentResult =
                DiceRollResult(s.selectedDice, nextRawValue, s.modifier, nextRawValue + s.modifier)
        )
  }

  override fun dismissError() {
    calls += "dismiss"
    state.value = state.value.copy(errorMessage = null)
  }
}

@RunWith(AndroidJUnit4::class)
class DiceOfflineScreenTest {
  @get:Rule val composeTestRule = createComposeRule()

  private fun setScreen(
      vm: FakeDiceViewModel,
      darkTheme: Boolean = true,
      onClose: () -> Unit = {},
  ) {
    composeTestRule.setContent {
      val uiState by vm.state.collectAsState()
      SampleAppTheme(darkTheme = darkTheme) {
        DiceOfflineScreen(uiState = uiState, diceViewModel = vm, onClose = onClose)
      }
    }
  }

  private fun node(tag: String) = composeTestRule.onNodeWithTag(tag, useUnmergedTree = true)

  @Test
  fun initialStateShowsD20WithItsRangeAndNoResult() {
    setScreen(FakeDiceViewModel())

    node(C.Tag.dice_offline_screen).assertIsDisplayed()
    node(C.Tag.dice_offline_die).assertIsDisplayed()
    node(C.Tag.dice_offline_selected_type).assertTextEquals("D20")
    node(C.Tag.dice_offline_range).assertTextEquals("Range: 1 – 20")
    node(C.Tag.dice_offline_result).assertTextEquals("?")
    node(C.Tag.dice_offline_result_detail).assertTextEquals("Tap Roll to throw your D20")
    node(C.Tag.dice_offline_modifier_value).assertTextEquals("+0")
    node(C.Tag.diceOfflineSelect("D20")).assertIsSelected()
    node(C.Tag.dice_offline_loading).assertDoesNotExist()
    node(C.Tag.dice_offline_error).assertDoesNotExist()
  }

  @Test
  fun allSixDiceCanBeSelectedAndOnlyTheSelectedOneIsHighlighted() {
    val vm = FakeDiceViewModel()
    setScreen(vm, darkTheme = false)

    DiceType.entries.forEach { type ->
      node(C.Tag.diceOfflineSelect(type.name)).performScrollTo().performClick()

      node(C.Tag.dice_offline_selected_type).assertTextEquals(type.name)
      node(C.Tag.dice_offline_range).assertTextEquals("Range: 1 – ${type.maxValue}")
      node(C.Tag.diceOfflineSelect(type.name)).assertIsSelected()
      DiceType.entries
          .filter { it != type }
          .forEach { node(C.Tag.diceOfflineSelect(it.name)).assertIsNotSelected() }
    }
    assertEquals(DiceType.entries.map { "select:$it" }, vm.calls)
  }

  @Test
  fun rangeIncludesTheModifier() {
    setScreen(FakeDiceViewModel(DiceUiState(selectedDice = DiceType.D6, modifier = -2)))

    node(C.Tag.dice_offline_modifier_value).assertTextEquals("-2")
    node(C.Tag.dice_offline_range).assertTextEquals("Range: -1 – 4")
  }

  @Test
  fun modifierButtonsForwardIncrements() {
    val vm = FakeDiceViewModel()
    setScreen(vm)

    node(C.Tag.dice_offline_modifier_increase).performScrollTo().performClick()
    node(C.Tag.dice_offline_modifier_increase).performClick()
    node(C.Tag.dice_offline_modifier_decrease).performClick()

    node(C.Tag.dice_offline_modifier_value).assertTextEquals("+1")
    assertEquals(listOf("increase:1", "increase:1", "increase:-1"), vm.calls)
  }

  @Test
  fun rollButtonShowsRawValueOnDieAndTotalBelow() {
    val vm = FakeDiceViewModel(DiceUiState(selectedDice = DiceType.D8, modifier = 3), 5)
    setScreen(vm)

    node(C.Tag.dice_offline_roll).performScrollTo().performClick()

    node(C.Tag.dice_offline_result).assertTextEquals("5")
    node(C.Tag.dice_offline_result_detail).assertTextEquals("Total: 5 + 3 = 8")
    node(C.Tag.dice_offline_selected_type).assertTextEquals("D8")
    assertEquals(listOf("roll"), vm.calls)
  }

  @Test
  fun negativeModifierIsShownAsSubtraction() {
    val result = DiceRollResult(DiceType.D4, rawValue = 3, modifier = -1, totalValue = 2)
    setScreen(
        FakeDiceViewModel(
            DiceUiState(selectedDice = DiceType.D4, modifier = -1, currentResult = result)
        )
    )

    node(C.Tag.dice_offline_result).assertTextEquals("3")
    node(C.Tag.dice_offline_result_detail).assertTextEquals("Total: 3 − 1 = 2")
  }

  @Test
  fun zeroModifierShowsOnlyTheTotal() {
    val result = DiceRollResult(DiceType.D12, rawValue = 11, modifier = 0, totalValue = 11)
    setScreen(FakeDiceViewModel(DiceUiState(selectedDice = DiceType.D12, currentResult = result)))

    node(C.Tag.dice_offline_result_detail).assertTextEquals("Total: 11")
  }

  @Test
  fun resultOfAnotherDieIsNotShownOnTheSelectedDie() {
    val vm = FakeDiceViewModel(DiceUiState(selectedDice = DiceType.D20), 13)
    setScreen(vm)

    node(C.Tag.dice_offline_roll).performScrollTo().performClick()
    node(C.Tag.dice_offline_result).assertTextEquals("13")

    node(C.Tag.diceOfflineSelect("D6")).performScrollTo().performClick()
    node(C.Tag.dice_offline_result).assertTextEquals("?")
    node(C.Tag.dice_offline_result_detail).assertTextEquals("Tap Roll to throw your D6")

    node(C.Tag.diceOfflineSelect("D20")).performClick()
    node(C.Tag.dice_offline_result).assertTextEquals("13")
  }

  @Test
  fun whileRollingControlsAreDisabledAndLoadingIsShown() {
    val vm = FakeDiceViewModel(DiceUiState(isRolling = true))
    setScreen(vm, darkTheme = false)

    node(C.Tag.dice_offline_loading).assertIsDisplayed()
    node(C.Tag.dice_offline_result).assertDoesNotExist()
    node(C.Tag.dice_offline_roll).assertIsNotEnabled()
    node(C.Tag.dice_offline_modifier_increase).assertIsNotEnabled()
    node(C.Tag.dice_offline_modifier_decrease).assertIsNotEnabled()
    DiceType.entries.forEach { node(C.Tag.diceOfflineSelect(it.name)).assertIsNotEnabled() }

    node(C.Tag.dice_offline_roll).performScrollTo().performClick()
    node(C.Tag.diceOfflineSelect("D4")).performClick()
    node(C.Tag.dice_offline_modifier_increase).performClick()
    assertTrue(vm.calls.isEmpty())

    vm.state.value = vm.state.value.copy(isRolling = false)
    node(C.Tag.dice_offline_loading).assertDoesNotExist()
    node(C.Tag.dice_offline_roll).assertIsEnabled()
    node(C.Tag.diceOfflineSelect("D4")).assertIsEnabled()
  }

  @Test
  fun errorIsShownAndCanBeDismissed() {
    val vm = FakeDiceViewModel(DiceUiState(errorMessage = "Something went wrong"))
    setScreen(vm)

    node(C.Tag.dice_offline_error).performScrollTo().assertIsDisplayed()
    node(C.Tag.dice_offline_error_dismiss).performClick()

    node(C.Tag.dice_offline_error).assertDoesNotExist()
    assertEquals(listOf("dismiss"), vm.calls)
  }

  @Test
  fun closeButtonInvokesCallback() {
    var closed = false
    setScreen(FakeDiceViewModel(), onClose = { closed = true })

    node(C.Tag.dice_offline_close).performClick()

    assertTrue(closed)
  }

  @Test
  fun smallPhoneShrinksTheDieSoTheDiceSelectorFitsOnScreen() {
    val screenHeight = 640.dp
    composeTestRule.setContent {
      SampleAppTheme {
        Box(Modifier.size(width = 320.dp, height = screenHeight)) {
          DiceOfflineScreen(uiState = DiceUiState(), diceViewModel = FakeDiceViewModel())
        }
      }
    }

    val dieHeight = node(C.Tag.dice_offline_die).getUnclippedBoundsInRoot().height
    assertTrue("Die should shrink, was $dieHeight", dieHeight < 270.dp)
    DiceType.entries.forEach { type ->
      val tile = node(C.Tag.diceOfflineSelect(type.name))
      tile.assertIsDisplayed()
      val bottom = tile.getUnclippedBoundsInRoot().bottom
      assertTrue("$type tile ends at $bottom, off screen", bottom <= screenHeight)
    }
  }
}
