// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.ui.dice

import android.content.Context
import android.provider.Settings
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.se.jdrnexus.model.repository.DiceLogic
import com.github.se.jdrnexus.model.sensor.ShakeDetector
import com.github.se.jdrnexus.resources.C
import com.github.se.jdrnexus.ui.theme.SampleAppTheme
import dev.romainguy.kotlin.math.Quaternion
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.flow.update
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Rolls the selected die to [ROLLED_VALUE] every time, so the animation runs on a known roll. */
private class FixedRollViewModel : DiceInterfaceVM {
  val state = MutableStateFlow(DiceUiState())

  override fun selectDice(diceType: DiceType) = state.update { it.copy(selectedDice = diceType) }

  override fun setModifier(modifier: Int) = Unit

  override fun increaseModifier(modifier: Int) = Unit

  override fun rollDice() = state.update {
    val roll =
        DiceRollResult(it.selectedDice, ROLLED_VALUE, modifier = 0, totalValue = ROLLED_VALUE)
    it.copy(isRolling = true, currentResult = roll)
  }

  override fun onRollAnimationFinished() = state.update { it.copy(isRolling = false) }

  override fun dismissError() = Unit
}

/**
 * Shake source driven by hand. [handledShakes] counts the shakes the screen has finished handling,
 * so a test can tell "ignored" from "not read yet".
 */
private class ManualShakeDetector : ShakeDetector {
  val source = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
  private val handled = AtomicInteger(0)
  val handledShakes: Int
    get() = handled.get()

  override val isAvailable = true

  // emit() returns once the screen's collect block has run, so counting after it means "handled".
  override val shakes: Flow<Unit> = source.transform {
    emit(it)
    handled.incrementAndGet()
  }
}

/** A value every die has, from the D4 up. */
private const val ROLLED_VALUE = 3

/**
 * The roll animation of [DiceOfflineScreen]: the die tumbles, and only shows the result once it
 * lands. The 3D die cannot render under Robolectric, so the screen draws the flat die, and the
 * tests read the rotation it would give the 3D one. The test clock is driven by hand (`autoAdvance
 * = false`), so the animation never depends on the real clock.
 */
@RunWith(AndroidJUnit4::class)
class DiceOfflineScreenRollAnimationTest {
  @get:Rule val composeTestRule = createComposeRule()

  private val clock
    get() = composeTestRule.mainClock

  private var darkTheme by mutableStateOf(true)

  /** What the screen last asked the die to draw. */
  private var lastDie: DieViewState? = null

  /** Draws the flat die and keeps what it was asked to draw. */
  private val recordingDieView: DieView = { state ->
    lastDie = state
    FlatDieView(state)
  }

  /** The rotation the 3D die would have now. */
  private fun dieRotation(): Quaternion = lastDie!!.rotation()

  /** Shows the screen around a [FixedRollViewModel] and a fixed seed, with the clock stopped. */
  private fun setFixedRollScreen(): FixedRollViewModel {
    val vm = FixedRollViewModel()
    composeTestRule.setContent {
      val uiState by vm.state.collectAsState()
      SampleAppTheme(darkTheme = darkTheme) {
        DiceOfflineContent(
            uiState = uiState,
            diceViewModel = vm,
            onClose = {},
            firstRollSeed = FIXED_SEED,
            dieView = recordingDieView,
        )
      }
    }
    clock.autoAdvance = false
    return vm
  }

  private fun node(tag: String) = composeTestRule.onNodeWithTag(tag, useUnmergedTree = true)

  private fun clickRoll() {
    node(C.Tag.dice_offline_roll).performScrollTo().performClick()
    clock.advanceTimeByFrame()
  }

  private fun turnOffSystemAnimations() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
  }

  @Test
  fun beforeAnyRollTheDieRestsOnItsHighestFace() {
    setFixedRollScreen()

    assertSameRotation(landingRotation(DiceType.D20, DiceType.D20.maxValue), dieRotation())
  }

  @Test
  fun dieTumblesAndHidesTheResultAndTotalWhileRolling() {
    setFixedRollScreen()
    val rotations = mutableListOf<Quaternion>()

    clickRoll()
    repeat(SAMPLES) {
      rotations += dieRotation()
      node(C.Tag.dice_offline_result).assertDoesNotExist()
      node(C.Tag.dice_offline_result_detail).assertTextEquals("Rolling your D20…")
      node(C.Tag.dice_offline_roll).assertIsNotEnabled()
      clock.advanceTimeBy(SAMPLE_STEP_MILLIS)
    }

    // Catches a die that stays still while the screen says it is rolling.
    rotations.zipWithNext().forEach { (before, after) ->
      assertFalse("the die should keep turning", sameRotation(before, after))
    }
  }

  @Test
  fun resultAppearsOnlyOnceTheDieHasLandedOnIt() {
    val vm = setFixedRollScreen()

    clickRoll()
    clock.advanceTimeBy(JUST_BEFORE_LANDING_MILLIS)
    assertTrue("still rolling just before landing", vm.state.value.isRolling)
    node(C.Tag.dice_offline_result).assertDoesNotExist()

    clock.advanceTimeBy(LANDING_MARGIN_MILLIS * 2)

    assertFalse(vm.state.value.isRolling)
    assertSameRotation(landingRotation(DiceType.D20, ROLLED_VALUE), dieRotation())
    node(C.Tag.dice_offline_result).assertTextEquals("$ROLLED_VALUE")
    node(C.Tag.dice_offline_result_detail).assertTextEquals("Total: $ROLLED_VALUE")
    // The landing bounce is decorative: the next roll is possible at once.
    node(C.Tag.dice_offline_roll).assertIsEnabled()
  }

  @Test
  fun everyDieTumblesAndLandsOnTheRolledValueInLightAndDarkMode() {
    val vm = setFixedRollScreen()

    listOf(true, false).forEach { dark ->
      darkTheme = dark
      DiceType.entries.forEach { type ->
        vm.selectDice(type)
        clock.advanceTimeByFrame()

        clickRoll()
        assertTrue("$type, dark=$dark is not rolling", vm.state.value.isRolling)
        node(C.Tag.dice_offline_result).assertDoesNotExist()
        clock.advanceTimeBy(ROLL_ANIMATION_END_MILLIS)

        assertSameRotation(landingRotation(type, ROLLED_VALUE), dieRotation(), "$type:")
        node(C.Tag.dice_offline_result).assertTextEquals("$ROLLED_VALUE")
      }
    }
  }

  /**
   * Shows the whole screen around the real ViewModel and [shaker], waits until it listens to the
   * shakes, then stops the clock.
   */
  private fun setShakeScreen(shaker: ManualShakeDetector): DiceViewModel {
    val vm = DiceViewModel(DiceLogic(), diceRepository = null)
    composeTestRule.setContent {
      SampleAppTheme {
        DiceOfflineScreen(diceViewModel = vm, shakeDetector = shaker, dieView = recordingDieView)
      }
    }
    composeTestRule.waitUntil { shaker.source.subscriptionCount.value > 0 }
    clock.autoAdvance = false
    return vm
  }

  @Test
  fun shakeDuringTheRollDoesNothing() {
    val shaker = ManualShakeDetector()
    val vm = setShakeScreen(shaker)

    assertTrue(shaker.source.tryEmit(Unit))
    clock.advanceTimeByFrame()
    assertTrue("a shake should start the roll", vm.uiState.value.isRolling)
    val firstRoll = vm.uiState.value.currentResult

    assertTrue(shaker.source.tryEmit(Unit))
    clock.advanceTimeByFrame()

    assertEquals("the screen must have handled the second shake", 2, shaker.handledShakes)
    assertSame("a second shake must not reroll", firstRoll, vm.uiState.value.currentResult)
  }

  @Test
  fun shakeRollPlaysTheSameAnimationAsTheButton() {
    val shaker = ManualShakeDetector()
    val vm = setShakeScreen(shaker)

    assertTrue(shaker.source.tryEmit(Unit))
    clock.advanceTimeByFrame()
    assertTrue("a shake should start the roll", vm.uiState.value.isRolling)
    node(C.Tag.dice_offline_result).assertDoesNotExist()
    clock.advanceTimeBy(ROLL_ANIMATION_END_MILLIS)

    val result = vm.uiState.value.currentResult!!
    assertFalse(vm.uiState.value.isRolling)
    assertSameRotation(landingRotation(result.diceType, result.rawValue), dieRotation())
    node(C.Tag.dice_offline_result).assertTextEquals("${result.rawValue}")
  }

  @Test
  fun withSystemAnimationsOffTheDieTurnsToTheResultAtOnce() {
    turnOffSystemAnimations()
    val vm = setFixedRollScreen()

    clickRoll()
    clock.advanceTimeBy(FEW_FRAMES_MILLIS)

    assertFalse("the roll should not wait for an animation", vm.state.value.isRolling)
    assertSameRotation(landingRotation(DiceType.D20, ROLLED_VALUE), dieRotation())
    node(C.Tag.dice_offline_result).assertTextEquals("$ROLLED_VALUE")
  }

  private companion object {
    /** Seed of the tumble, fixed so every run tumbles the same way. */
    const val FIXED_SEED = 0

    /** Margin around the landing time, a few frames wide, for the frames before the tumble. */
    const val LANDING_MARGIN_MILLIS = 100L

    /** Just after the die has landed, whatever the animation's duration. */
    const val ROLL_ANIMATION_END_MILLIS = ROLL_ANIMATION_MILLIS + LANDING_MARGIN_MILLIS

    /** Still in the air: the result must not be shown yet. */
    const val JUST_BEFORE_LANDING_MILLIS = ROLL_ANIMATION_MILLIS - LANDING_MARGIN_MILLIS

    /** How often the die's rotation is read, and how many times, all before the die lands. */
    const val SAMPLE_STEP_MILLIS = 100L
    const val SAMPLES = 10

    /** Far less than the roll animation: with animations off, the roll is already over. */
    const val FEW_FRAMES_MILLIS = 50L
  }
}
