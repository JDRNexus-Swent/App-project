package com.github.se.jdrnexus.ui.dice

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.se.jdrnexus.model.repository.DiceLogic
import com.github.se.jdrnexus.resources.C
import com.github.se.jdrnexus.resources.MAX_LANDSCAPE_CONTENT_WIDTH
import com.github.se.jdrnexus.ui.theme.SampleAppTheme
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Layout of [DiceOfflineScreen] on short, wide screens. The screen is forced to phone sizes inside
 * a large window, so the tests do not depend on the device orientation. Native graphics make
 * Robolectric measure text for real, so text overlaps and overflow are visible to the tests.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w900dp-h900dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DiceOfflineScreenLandscapeTest {
  @get:Rule val composeTestRule = createComposeRule()

  private var screenSize by mutableStateOf(LANDSCAPE_SIZES.first())

  private fun setScreen(
      vm: DiceViewModel = DiceViewModel(DiceLogic(), diceRepository = null),
      onClose: () -> Unit = {},
  ) = vm.also {
    composeTestRule.setContent {
      SampleAppTheme(darkTheme = true) {
        Box(Modifier.size(screenSize)) { DiceOfflineScreen(diceViewModel = it, onClose = onClose) }
      }
    }
  }

  private fun node(tag: String) = composeTestRule.onNodeWithTag(tag, useUnmergedTree = true)

  private fun bounds(tag: String) =
      composeTestRule.onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot()

  /** Whether some part of the screen can scroll vertically, i.e. its content does not fit. */
  private fun anythingScrollsVertically(): Boolean =
      composeTestRule
          .onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
          .fetchSemanticsNodes()
          .any { it.config[SemanticsProperties.VerticalScrollAxisRange].maxValue() > 0f }

  /** Landscape puts the controls right of the die. */
  private fun assertLandscapeLayout(size: DpSize) {
    val die = bounds(C.Tag.dice_offline_die)
    PANEL_CONTROLS.forEach { tag ->
      assertTrue("$tag is not right of the die at $size", bounds(tag).left >= die.right)
    }
  }

  /** Portrait stacks the die above the controls: the roll button sits under it, not beside it. */
  private fun assertPortraitLayout(size: DpSize) {
    val die = bounds(C.Tag.dice_offline_die)
    val roll = bounds(C.Tag.dice_offline_roll)
    assertTrue(
        "the roll button is not under the die at $size: $roll, die $die",
        die.bottom <= roll.top && roll.left < die.right && roll.right > die.left,
    )
  }

  @Test
  fun landscapeShowsTheDieInTheLeftThirdAndEveryControlOnTheRightWithoutScrolling() {
    setScreen()
    val dieShares = mutableListOf<Float>()

    LANDSCAPE_SIZES.forEach { size ->
      screenSize = size
      composeTestRule.waitForIdle()

      assertFalse("the dice screen scrolls at $size", anythingScrollsVertically())
      val screen = bounds(C.Tag.dice_offline_screen)
      CONTROLS.forEach { tag -> assertInside(tag, bounds(tag), screen) }
      assertLandscapeLayout(size)

      val dieShare = bounds(C.Tag.dice_offline_die).width / size.width
      assertTrue(
          "the die takes $dieShare of the $size screen, not about a third",
          dieShare in MIN_DIE_SHARE..MAX_DIE_SHARE,
      )
      dieShares += dieShare

      val title =
          composeTestRule.onNodeWithText(TITLE, useUnmergedTree = true).getUnclippedBoundsInRoot()
      assertTrue(
          "the title runs into the back button at $size",
          title.left >= bounds(C.Tag.dice_offline_close).right,
      )
      DiceType.entries.forEach { type ->
        val tile = bounds(C.Tag.diceOfflineSelect(type.name))
        assertTrue("${type.name} is only ${tile.width} wide at $size", tile.width >= 48.dp)
      }
    }
    assertTrue(
        "the die's share of the screen changes between phones: $dieShares",
        dieShares.max() - dieShares.min() <= MAX_DIE_SHARE_SPREAD,
    )
  }

  /** On a tablet the panels stop growing and are centered, so the controls are not stretched. */
  @Test
  @Config(qualifiers = TABLET_WINDOW)
  fun tabletCentersTheLandscapeLayoutAtThePhoneProportions() {
    screenSize = TABLET_SIZE
    setScreen()
    assertLandscapeLayout(TABLET_SIZE)

    val screen = bounds(C.Tag.dice_offline_screen)
    val contentLeft = screen.left + (TABLET_SIZE.width - MAX_LANDSCAPE_CONTENT_WIDTH) / 2
    val contentRight = contentLeft + MAX_LANDSCAPE_CONTENT_WIDTH
    val die = bounds(C.Tag.dice_offline_die)
    assertTrue(
        "the die is ${die.width} wide, not a third of the capped content",
        abs((die.width - MAX_LANDSCAPE_CONTENT_WIDTH / 3).value) <= 1f,
    )
    assertTrue("the die is not centered with the content: $die", die.left >= contentLeft - 1.dp)
    PANEL_CONTROLS.forEach { tag ->
      assertTrue("$tag stretches past the capped content", bounds(tag).right <= contentRight + 1.dp)
    }
  }

  @Test
  fun splitScreenKeepsThePortraitLayout() {
    screenSize = SPLIT_SCREEN_SIZE
    setScreen()

    assertPortraitLayout(SPLIT_SCREEN_SIZE)
  }

  @Test
  fun rollingInLandscapeShowsTheResultOnTheDie() {
    val vm = setScreen()
    assertLandscapeLayout(screenSize)
    composeTestRule.mainClock.autoAdvance = false

    composeTestRule.onNodeWithTag(C.Tag.dice_offline_roll).performClick()
    // Past the roll animation; the clock is driven by hand so the rolling spinner cannot keep the
    // test from going idle.
    composeTestRule.mainClock.advanceTimeBy(ROLL_ANIMATION_END_MILLIS)

    val result = vm.uiState.value.currentResult
    assertNotNull("tapping Roll in landscape rolls the die", result)
    assertFalse(vm.uiState.value.isRolling)
    composeTestRule
        .onNodeWithTag(C.Tag.dice_offline_result, useUnmergedTree = true)
        .assertTextEquals(result!!.rawValue.toString())
  }

  /**
   * The die, modifier and result live in the ViewModel, so switching layout keeps them. A real
   * rotation also keeps the ViewModel, which is scoped to the navigation entry.
   */
  @Test
  fun switchingLayoutKeepsDieModifierAndResult() {
    screenSize = PORTRAIT_SIZE
    val vm = setScreen()
    composeTestRule
        .onNodeWithTag(C.Tag.diceOfflineSelect("D8"), useUnmergedTree = true)
        .performClick()
    repeat(2) {
      composeTestRule
          .onNodeWithTag(C.Tag.dice_offline_modifier_increase, useUnmergedTree = true)
          .performClick()
    }
    vm.rollDice()
    vm.onRollAnimationFinished()
    val rolled = vm.uiState.value.currentResult!!

    listOf(LANDSCAPE_SIZES.first(), PORTRAIT_SIZE).forEach { size ->
      screenSize = size
      composeTestRule.waitForIdle()

      if (size == PORTRAIT_SIZE) {
        assertPortraitLayout(size)
      } else {
        assertLandscapeLayout(size)
      }
      composeTestRule
          .onNodeWithTag(C.Tag.diceOfflineSelect("D8"), useUnmergedTree = true)
          .assertIsSelected()
      composeTestRule
          .onNodeWithTag(C.Tag.dice_offline_modifier_value, useUnmergedTree = true)
          .assertTextEquals("+2")
      composeTestRule
          .onNodeWithTag(C.Tag.dice_offline_result, useUnmergedTree = true)
          .assertTextEquals(rolled.rawValue.toString())
    }
    assertEquals(rolled, vm.uiState.value.currentResult)
  }

  @Test
  fun errorBannerInLandscapeLetsTheControlsScrollSoRollStaysReachable() {
    var dismissCount = 0
    screenSize = LANDSCAPE_SIZES.last()
    composeTestRule.setContent {
      SampleAppTheme(darkTheme = true) {
        Box(Modifier.size(screenSize)) {
          DiceOfflineContent(
              uiState = DiceUiState(errorMessage = "Failed to sync roll"),
              diceViewModel = DismissCountingDiceVM(onDismissError = { dismissCount++ }),
              onClose = {},
          )
        }
      }
    }

    composeTestRule.onNodeWithTag(C.Tag.dice_offline_error).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.dice_offline_roll).performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.dice_offline_error_dismiss).performScrollTo().performClick()

    assertEquals(1, dismissCount)
  }

  @Test
  fun backArrowInLandscapeClosesTheRoller() {
    var closeCount = 0
    setScreen(onClose = { closeCount++ })
    assertLandscapeLayout(screenSize)

    node(C.Tag.dice_offline_close).performClick()

    assertEquals(1, closeCount)
  }

  /**
   * The roller is a secondary screen opened from Home: it shows the shared top bar but none of the
   * tab navigation, in either orientation.
   */
  @Test
  fun showsTheTopBarButNoTabNavigationInEitherOrientation() {
    setScreen()

    listOf(LANDSCAPE_SIZES.first(), PORTRAIT_SIZE).forEach { size ->
      screenSize = size
      composeTestRule.waitForIdle()

      node(C.Tag.shared_top_bar).assertIsDisplayed()
      node(C.Tag.shared_bottom_bar).assertDoesNotExist()
      node(C.Tag.shared_navigation_rail).assertDoesNotExist()
    }
  }

  private fun assertInside(tag: String, inner: DpRect, outer: DpRect) {
    assertTrue("$tag goes past the screen: $inner in $outer", inner.right <= outer.right)
    assertTrue("$tag goes below the screen: $inner in $outer", inner.bottom <= outer.bottom)
  }

  /** Dice ViewModel that ignores every intent except dismissing the error. */
  private class DismissCountingDiceVM(private val onDismissError: () -> Unit) : DiceInterfaceVM {
    override fun selectDice(diceType: DiceType) = Unit

    override fun setModifier(modifier: Int) = Unit

    override fun increaseModifier(modifier: Int) = Unit

    override fun rollDice() = Unit

    override fun onRollAnimationFinished() = Unit

    override fun dismissError() = onDismissError()
  }

  private companion object {
    const val TITLE = "Dice Roller"

    /** A typical phone in landscape, a smaller one, and the smallest that gets two panes. */
    val LANDSCAPE_SIZES =
        listOf(DpSize(844.dp, 390.dp), DpSize(720.dp, 360.dp), DpSize(640.dp, 360.dp))

    val PORTRAIT_SIZE = DpSize(390.dp, 844.dp)

    /** Half of a landscape phone in split screen: wider than tall, but too narrow for two panes. */
    val SPLIT_SCREEN_SIZE = DpSize(420.dp, 390.dp)

    /** A tablet in landscape, wider than [MAX_LANDSCAPE_CONTENT_WIDTH]. */
    val TABLET_SIZE = DpSize(1280.dp, 800.dp)

    /** Window large enough to hold [TABLET_SIZE]. */
    const val TABLET_WINDOW = "w1300dp-h900dp-xhdpi"

    /**
     * The die fills a third of the card, which is a little narrower than the screen because of the
     * card's padding.
     */
    const val MIN_DIE_SHARE = 0.30f
    const val MAX_DIE_SHARE = 1f / 3f

    /** How much the die's share of the screen width may differ between phone sizes. */
    const val MAX_DIE_SHARE_SPREAD = 0.01f

    /** Just after the screen's one-second roll animation. */
    const val ROLL_ANIMATION_END_MILLIS = 1_100L

    val CONTROLS =
        listOf(
            C.Tag.dice_offline_close,
            C.Tag.dice_offline_die,
            C.Tag.dice_offline_selected_type,
            C.Tag.dice_offline_range,
            C.Tag.dice_offline_result_detail,
            C.Tag.dice_offline_modifier_decrease,
            C.Tag.dice_offline_modifier_value,
            C.Tag.dice_offline_modifier_increase,
            C.Tag.diceOfflineSelect("D4"),
            C.Tag.diceOfflineSelect("D20"),
            C.Tag.dice_offline_roll,
        )

    /** Controls of the right-hand panel; the back button is in the top bar above the card. */
    val PANEL_CONTROLS = CONTROLS - C.Tag.dice_offline_close - C.Tag.dice_offline_die
  }
}
