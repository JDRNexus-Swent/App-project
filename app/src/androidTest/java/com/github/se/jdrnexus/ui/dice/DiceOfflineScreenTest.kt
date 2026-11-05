// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.ui.dice

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.se.jdrnexus.model.repository.AuthError
import com.github.se.jdrnexus.model.repository.AuthRepository
import com.github.se.jdrnexus.model.repository.AuthResult
import com.github.se.jdrnexus.model.repository.AuthUser
import com.github.se.jdrnexus.model.repository.DiceLogic
import com.github.se.jdrnexus.model.repository.DiceRepositoryFirestore
import com.github.se.jdrnexus.model.sensor.ShakeDetector
import com.github.se.jdrnexus.resources.C
import com.github.se.jdrnexus.ui.theme.SampleAppTheme
import com.google.firebase.firestore.FirebaseFirestore
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.transform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
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

/** Upper bound for a roll to end after a click, far above the screen's roll animation. */
private const val WAIT_TIMEOUT_MILLIS = 5_000L

/**
 * Shake source driven by hand; room for one event, so [sendShake] never drops a shake.
 * [handledShakes] counts the shakes the screen has finished handling, so a test can tell "ignored"
 * from "not read yet".
 */
private class FakeShakeDetector(override val isAvailable: Boolean = true) : ShakeDetector {
  val source = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
  private val handled = AtomicInteger(0)
  val handledShakes: Int
    get() = handled.get()

  // emit() returns once the screen's collect block has run, so counting after it means "handled".
  override val shakes: Flow<Unit> = source.transform {
    emit(it)
    handled.incrementAndGet()
  }

  fun sendShake(): Boolean = source.tryEmit(Unit)
}

/** Lifecycle the tests move by hand, to pause and resume the screen. */
private class FakeLifecycleOwner : LifecycleOwner {
  val registry =
      LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.RESUMED }
  override val lifecycle: Lifecycle
    get() = registry
}

@RunWith(AndroidJUnit4::class)
class DiceOfflineScreenTest {
  @get:Rule val composeTestRule = createComposeRule()

  // Turns a hung test into a failure naming it, instead of blocking CI until it is cancelled.
  @get:Rule val timeout: Timeout = Timeout.seconds(30)

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
      shakeDetector: ShakeDetector = FakeShakeDetector(),
      darkTheme: Boolean = true,
      onClose: () -> Unit = {},
  ): DiceViewModel {
    composeTestRule.setContent {
      SampleAppTheme(darkTheme = darkTheme) {
        DiceOfflineScreen(diceViewModel = vm, shakeDetector = shakeDetector, onClose = onClose)
      }
    }
    return vm
  }

  private fun node(tag: String) = composeTestRule.onNodeWithTag(tag, useUnmergedTree = true)

  private fun FakeShakeDetector.isListening() = source.subscriptionCount.value > 0

  private fun FakeShakeDetector.awaitListening() =
      composeTestRule.waitUntil(WAIT_TIMEOUT_MILLIS) { isListening() }

  private fun FakeShakeDetector.awaitNotListening() =
      composeTestRule.waitUntil(WAIT_TIMEOUT_MILLIS) { !isListening() }

  /** Waits until the screen listens to [detector], then shakes once. */
  private fun shake(detector: FakeShakeDetector) {
    detector.awaitListening()
    assertTrue(detector.sendShake())
  }

  private fun awaitRollFinished(vm: DiceViewModel) =
      composeTestRule.waitUntil(WAIT_TIMEOUT_MILLIS) {
        vm.uiState.value.currentResult != null && !vm.uiState.value.isRolling
      }

  /**
   * Clicks Roll and waits until the screen ends the roll itself, once its animation calls
   * [DiceViewModel.onRollAnimationFinished]. The test clock runs freely, so the animation delay
   * passes in virtual time.
   */
  private fun rollAndWait(vm: DiceViewModel): DiceRollResult {
    node(C.Tag.dice_offline_roll).performScrollTo().performClick()
    composeTestRule.waitUntil(WAIT_TIMEOUT_MILLIS) {
      vm.uiState.value.currentResult != null && !vm.uiState.value.isRolling
    }
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
  fun rollShowsRawValueOnDieAndTotalBelowThenUnlocksControls() {
    val vm = setScreen()
    vm.selectDice(DiceType.D8)
    vm.setModifier(3)

    rollAndWait(vm)

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
          DiceOfflineScreen(diceViewModel = vm, shakeDetector = FakeShakeDetector())
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

  @Test
  fun defaultViewModelFromTheFactoryDrivesTheScreen() {
    // No ViewModel passed: the screen builds its own through DiceViewModelFactory, as in the app.
    // No roll here, so the test never depends on the roll animation.
    composeTestRule.setContent { SampleAppTheme { DiceOfflineScreen() } }

    node(C.Tag.dice_offline_selected_type).assertTextEquals("D20")
    node(C.Tag.dice_offline_result).assertTextEquals("?")

    node(C.Tag.diceOfflineSelect("D6")).performScrollTo().performClick()
    node(C.Tag.dice_offline_modifier_increase).performScrollTo().performClick()

    node(C.Tag.dice_offline_selected_type).assertTextEquals("D6")
    node(C.Tag.dice_offline_modifier_value).assertTextEquals("+1")
    node(C.Tag.dice_offline_range).assertTextEquals("Range: 2 – 7")
  }

  @Test
  fun shakeRollsTheSelectedDieWithTheModifierLikeTheButton() {
    val shaker = FakeShakeDetector()
    val vm = setScreen(shakeDetector = shaker)
    vm.selectDice(DiceType.D8)
    vm.setModifier(3)

    shake(shaker)
    awaitRollFinished(vm)

    val result = vm.uiState.value.currentResult!!
    assertEquals(DiceType.D8, result.diceType)
    assertEquals(3, result.modifier)
    node(C.Tag.dice_offline_result).assertTextEquals(result.rawValue.toString())
    node(C.Tag.dice_offline_result_detail)
        .assertTextEquals("Total: ${result.rawValue} + 3 = ${result.rawValue + 3}")
  }

  @Test
  fun shakeDuringTheRollAnimationDoesNothing() {
    val shaker = FakeShakeDetector()
    val vm = setScreen(shakeDetector = shaker)
    shaker.awaitListening()
    // Freeze virtual time so the 1 s roll animation cannot end during the test.
    composeTestRule.mainClock.autoAdvance = false

    assertTrue(shaker.sendShake())
    composeTestRule.mainClock.advanceTimeByFrame()
    assertTrue(vm.uiState.value.isRolling)
    val firstRoll = vm.uiState.value.currentResult

    assertTrue(shaker.sendShake())
    composeTestRule.mainClock.advanceTimeByFrame()
    assertEquals("The screen must have handled the second shake", 2, shaker.handledShakes)

    assertSame("A second shake must not reroll", firstRoll, vm.uiState.value.currentResult)
    composeTestRule.mainClock.autoAdvance = true
    awaitRollFinished(vm)
  }

  @Test
  fun shakeToggleIsOnByDefaultAndTurningItOffStopsListening() {
    val shaker = FakeShakeDetector()
    val vm = setScreen(shakeDetector = shaker)
    shaker.awaitListening()
    node(C.Tag.dice_offline_shake_toggle).performScrollTo().assertIsOn()

    node(C.Tag.dice_offline_shake_toggle).performClick().assertIsOff()
    shaker.awaitNotListening()
    shaker.sendShake() // Nobody listens: the shake is dropped.
    composeTestRule.waitForIdle()
    assertNull(vm.uiState.value.currentResult)

    node(C.Tag.dice_offline_shake_toggle).performClick().assertIsOn()
    shake(shaker)
    awaitRollFinished(vm)
  }

  @Test
  fun withoutAccelerometerTheToggleIsHiddenAndTheButtonStillRolls() {
    val shaker = FakeShakeDetector(isAvailable = false)
    val vm = setScreen(shakeDetector = shaker)

    node(C.Tag.dice_offline_shake_toggle).assertDoesNotExist()
    assertFalse(shaker.isListening())
    rollAndWait(vm)
  }

  @Test
  fun pausingTheScreenStopsListeningAndResumingListensAgain() {
    val shaker = FakeShakeDetector()
    val owner = FakeLifecycleOwner()
    composeTestRule.setContent {
      CompositionLocalProvider(LocalLifecycleOwner provides owner) {
        SampleAppTheme {
          DiceOfflineScreen(diceViewModel = createViewModel(), shakeDetector = shaker)
        }
      }
    }
    shaker.awaitListening()

    composeTestRule.runOnUiThread { owner.registry.currentState = Lifecycle.State.STARTED }
    shaker.awaitNotListening()

    composeTestRule.runOnUiThread { owner.registry.currentState = Lifecycle.State.RESUMED }
    shaker.awaitListening()
  }
}
