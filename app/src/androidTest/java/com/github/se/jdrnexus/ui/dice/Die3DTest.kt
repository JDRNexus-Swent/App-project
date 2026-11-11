// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.ui.dice

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Size of the die on screen: big enough for SceneView to render it. */
private val DIE_SIZE = 200.dp

/**
 * How long the model may take to load and show. Generous: CI renders 3D in software on the CPU,
 * which is far slower than a phone's GPU.
 */
private const val SHOWN_TIMEOUT_MILLIS = 20_000L

/** A value every die has, whose face the die is asked to turn to the viewer after the first one. */
private const val NEXT_FACING_VALUE = 3

/**
 * The real 3D die, rendered by SceneView. Instrumented only: Filament's native library cannot load
 * under Robolectric, where the screen tests use the flat die instead.
 */
@RunWith(AndroidJUnit4::class)
class Die3DTest {
  @get:Rule val composeTestRule = createComposeRule()

  private var diceType by mutableStateOf(DiceType.D20)

  /** Value whose face the die is asked to turn towards the viewer. */
  private var facingValue by mutableIntStateOf(1)

  /** The die [Die3D] last reported as shown. */
  private var shownDie: DiceType? = null

  /** How many times [Die3D] has read the rotation it was given. */
  private var rotationReads = 0

  private fun setDie() = composeTestRule.setContent {
    Die3D(
        diceType = diceType,
        rotation = {
          rotationReads++
          landingRotation(diceType, facingValue)
        },
        lift = { 0f },
        modifier = Modifier.size(DIE_SIZE),
        onShown = { shownDie = diceType },
    )
  }

  private fun waitUntilShown(type: DiceType) =
      composeTestRule.waitUntil(SHOWN_TIMEOUT_MILLIS) { shownDie == type }

  @Test
  fun everyDieOfTheModelCanBeShown() {
    setDie()
    DiceType.entries.forEach { type ->
      diceType = type
      waitUntilShown(type)
    }
  }

  @Test
  fun dieFollowsItsRotationWhenItChanges() {
    setDie()
    waitUntilShown(DiceType.D20)
    val readsBefore = rotationReads

    facingValue = NEXT_FACING_VALUE

    composeTestRule.waitUntil(SHOWN_TIMEOUT_MILLIS) { rotationReads > readsBefore }
  }
}
