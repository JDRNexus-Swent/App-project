// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.ui.dice

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
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
import com.github.se.jdrnexus.model.repository.AuthError
import com.github.se.jdrnexus.model.repository.AuthRepository
import com.github.se.jdrnexus.model.repository.AuthResult
import com.github.se.jdrnexus.model.repository.AuthUser
import com.github.se.jdrnexus.model.repository.DiceLogic
import com.github.se.jdrnexus.model.repository.DiceRepositoryFirestore
import com.github.se.jdrnexus.resources.C
import com.github.se.jdrnexus.ui.theme.SampleAppTheme
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Signed-out user: any roll synced to a group fails before reaching Firestore. */
private object SignedOutAuthRepository : AuthRepository {
  override val authState: Flow<AuthUser?> = flowOf(null)

  override suspend fun signUpWithEmail(email: String, password: String, username: String) =
      AuthResult.Failure(AuthError.UNKNOWN)

  override suspend fun signInWithEmail(email: String, password: String) =
      AuthResult.Failure(AuthError.UNKNOWN)

  override suspend fun signInWithGoogle(idToken: String) = AuthResult.Failure(AuthError.UNKNOWN)

  override suspend fun signOut() = AuthResult.Failure(AuthError.UNKNOWN)
}

@RunWith(AndroidJUnit4::class)
class DiceOfflineScreenTest {
  @get:Rule val composeTestRule = createComposeRule()

  /** Builds the real ViewModel through its factory; [groupId] makes it sync rolls online. */
  private fun createViewModel(groupId: String? = null): DiceViewModel =
      DiceViewModelFactory(
              diceLogic = DiceLogic(),
              diceRepository =
                  DiceRepositoryFirestore(FirebaseFirestore.getInstance(), SignedOutAuthRepository),
              groupId = groupId,
          )
          .create(DiceViewModel::class.java)

  private fun setScreen(
      vm: DiceViewModel = createViewModel(),
      darkTheme: Boolean = true,
      onClose: () -> Unit = {},
  ): DiceViewModel {
    composeTestRule.setContent {
      SampleAppTheme(darkTheme = darkTheme) {
        DiceOfflineScreen(diceViewModel = vm, onClose = onClose)
      }
    }
    return vm
  }

  private fun node(tag: String) = composeTestRule.onNodeWithTag(tag, useUnmergedTree = true)

  /** Clicks Roll with the clock paused, so the test can look at the rolling state. */
  private fun startRoll() {
    composeTestRule.mainClock.autoAdvance = false
    node(C.Tag.dice_offline_roll).performScrollTo().performClick()
    composeTestRule.mainClock.advanceTimeByFrame()
  }

  /** Lets the roll animation run to its end and resumes the clock. */
  private fun finishRoll() {
    composeTestRule.mainClock.advanceTimeBy(ROLL_ANIMATION_MILLIS + 100)
    composeTestRule.mainClock.autoAdvance = true
    composeTestRule.waitForIdle()
  }

  private fun rollAndWait(vm: DiceViewModel): DiceRollResult {
    startRoll()
    finishRoll()
    val result = vm.uiState.value.currentResult
    assertNotNull(result)
    return result!!
  }

  @Test
  fun initialStateShowsD20WithItsRangeAndNoResult() {
    setScreen()

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
    val vm = setScreen(darkTheme = false)

    DiceType.entries.forEach { type ->
      node(C.Tag.diceOfflineSelect(type.name)).performScrollTo().performClick()

      assertEquals(type, vm.uiState.value.selectedDice)
      node(C.Tag.dice_offline_selected_type).assertTextEquals(type.name)
      node(C.Tag.dice_offline_range).assertTextEquals("Range: 1 – ${type.maxValue}")
      node(C.Tag.diceOfflineSelect(type.name)).assertIsSelected()
      DiceType.entries
          .filter { it != type }
          .forEach { node(C.Tag.diceOfflineSelect(it.name)).assertIsNotSelected() }
    }
  }

  @Test
  fun rangeIncludesTheModifier() {
    val vm = setScreen()
    vm.selectDice(DiceType.D6)
    vm.setModifier(-2)

    node(C.Tag.dice_offline_modifier_value).assertTextEquals("-2")
    node(C.Tag.dice_offline_range).assertTextEquals("Range: -1 – 4")
  }

  @Test
  fun modifierButtonsChangeTheModifier() {
    val vm = setScreen()

    node(C.Tag.dice_offline_modifier_increase).performScrollTo().performClick()
    node(C.Tag.dice_offline_modifier_increase).performClick()
    node(C.Tag.dice_offline_modifier_decrease).performClick()

    node(C.Tag.dice_offline_modifier_value).assertTextEquals("+1")
    assertEquals(1, vm.uiState.value.modifier)
  }

  @Test
  fun modifierStopsAtItsLimit() {
    val vm = setScreen()
    vm.setModifier(20)

    node(C.Tag.dice_offline_modifier_increase).performScrollTo().performClick()

    node(C.Tag.dice_offline_modifier_value).assertTextEquals("+20")
  }

  @Test
  fun rollShowsLoadingThenRawValueOnDieAndTotalBelow() {
    val vm = setScreen()
    vm.selectDice(DiceType.D8)
    vm.setModifier(3)

    startRoll()

    // Controls are locked while the die is rolling.
    node(C.Tag.dice_offline_loading).assertIsDisplayed()
    node(C.Tag.dice_offline_result).assertDoesNotExist()
    node(C.Tag.dice_offline_roll).assertIsNotEnabled()
    node(C.Tag.dice_offline_modifier_increase).assertIsNotEnabled()
    node(C.Tag.dice_offline_modifier_decrease).assertIsNotEnabled()
    DiceType.entries.forEach { node(C.Tag.diceOfflineSelect(it.name)).assertIsNotEnabled() }

    finishRoll()

    val result = vm.uiState.value.currentResult!!
    assertFalse(vm.uiState.value.isRolling)
    assertEquals(DiceType.D8, result.diceType)
    assertTrue(result.rawValue in 1..8)
    node(C.Tag.dice_offline_loading).assertDoesNotExist()
    node(C.Tag.dice_offline_result).assertTextEquals(result.rawValue.toString())
    node(C.Tag.dice_offline_result_detail)
        .assertTextEquals("Total: ${result.rawValue} + 3 = ${result.rawValue + 3}")
    node(C.Tag.dice_offline_roll).assertIsEnabled()
    node(C.Tag.diceOfflineSelect("D4")).assertIsEnabled()
  }

  @Test
  fun negativeModifierIsShownAsSubtraction() {
    val vm = setScreen()
    vm.selectDice(DiceType.D4)
    vm.setModifier(-1)

    val result = rollAndWait(vm)

    node(C.Tag.dice_offline_result).assertTextEquals(result.rawValue.toString())
    node(C.Tag.dice_offline_result_detail)
        .assertTextEquals("Total: ${result.rawValue} − 1 = ${result.rawValue - 1}")
  }

  @Test
  fun zeroModifierShowsOnlyTheTotal() {
    val vm = setScreen()
    vm.selectDice(DiceType.D12)

    val result = rollAndWait(vm)

    node(C.Tag.dice_offline_result_detail).assertTextEquals("Total: ${result.rawValue}")
  }

  @Test
  fun resultOfAnotherDieIsNotShownOnTheSelectedDie() {
    val vm = setScreen()

    val result = rollAndWait(vm)
    node(C.Tag.dice_offline_result).assertTextEquals(result.rawValue.toString())

    node(C.Tag.diceOfflineSelect("D6")).performScrollTo().performClick()
    node(C.Tag.dice_offline_result).assertTextEquals("?")
    node(C.Tag.dice_offline_result_detail).assertTextEquals("Tap Roll to throw your D6")

    node(C.Tag.diceOfflineSelect("D20")).performClick()
    node(C.Tag.dice_offline_result).assertTextEquals(result.rawValue.toString())
  }

  @Test
  fun syncErrorIsShownAndCanBeDismissed() {
    val vm = setScreen(createViewModel(groupId = "group"))

    rollAndWait(vm)

    node(C.Tag.dice_offline_error).performScrollTo().assertIsDisplayed()
    node(C.Tag.dice_offline_error_dismiss).performClick()

    node(C.Tag.dice_offline_error).assertDoesNotExist()
    assertEquals(null, vm.uiState.value.errorMessage)
  }

  @Test
  fun closeButtonInvokesCallback() {
    var closed = false
    setScreen(onClose = { closed = true })

    node(C.Tag.dice_offline_close).performClick()

    assertTrue(closed)
  }

  @Test
  fun smallPhoneShrinksTheDieSoTheDiceSelectorFitsOnScreen() {
    val screenHeight = 640.dp
    val vm = createViewModel()
    composeTestRule.setContent {
      SampleAppTheme {
        Box(Modifier.size(width = 320.dp, height = screenHeight)) {
          DiceOfflineScreen(diceViewModel = vm)
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
