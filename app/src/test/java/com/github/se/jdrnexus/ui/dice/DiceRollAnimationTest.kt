// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.ui.dice

import dev.romainguy.kotlin.math.slerp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Motion of the 3D roll, as pure functions of its progress: no clock, no Compose. */
class DiceRollAnimationTest {

  private val from = landingRotation(DiceType.D20, RESTING_VALUE)
  private val to = landingRotation(DiceType.D20, ROLLED_VALUE)
  private val motion = RollMotion(from, to, seed = FIXED_SEED)

  @Test
  fun rollStartsWhereTheDieRestedSoItDoesNotJump() {
    // Catches tumble turns that are not whole: the die would jump at the first frame.
    assertSameRotation(from, motion.rotation(0f))
  }

  @Test
  fun rollLandsWithTheRolledFaceTowardsTheViewer() {
    val landed = motion.rotation(1f)

    assertSameVector(TOWARDS_VIEWER, rotate(landed, faceDirection(DiceType.D20, ROLLED_VALUE)))
  }

  @Test
  fun everyDieLandsOnEveryValueWhateverTheSeed() {
    DiceType.entries.forEach { type ->
      (1..type.maxValue).forEach { value ->
        val roll =
            RollMotion(
                from = landingRotation(type, type.maxValue),
                to = landingRotation(type, value),
                seed = value,
            )
        assertSameRotation(landingRotation(type, value), roll.rotation(1f), "$type $value:")
      }
    }
  }

  @Test
  fun dieTumblesInsteadOfSlidingStraightToTheResult() {
    // Catches a roll without its tumble: the die would only slide along the shortest path from
    // its resting face to the result, which is what slerp alone gives. Only the first half is
    // sampled: near the end the tumble has almost stopped, as it should, and both paths meet.
    val moments = (1..SAMPLES).map { it * TUMBLING_PART / SAMPLES }

    moments.forEach { progress ->
      val straightPath = slerp(from, to, decelerate(progress))
      assertFalse("at $progress", sameRotation(straightPath, motion.rotation(progress)))
    }
  }

  @Test
  fun sameSeedReplaysTheSameRollAndAnotherSeedTumblesDifferently() {
    val replay = RollMotion(from, to, seed = FIXED_SEED)
    val other = RollMotion(from, to, seed = FIXED_SEED + 1)

    assertSameRotation(motion.rotation(MID_ROLL), replay.rotation(MID_ROLL))
    assertFalse(sameRotation(motion.rotation(MID_ROLL), other.rotation(MID_ROLL)))
  }

  @Test
  fun dieHopsOffTheTableAndLandsBackOnIt() {
    assertEquals(0f, motion.lift(0f), EPSILON)
    assertTrue("lift was ${motion.lift(MID_ROLL)}", motion.lift(MID_ROLL) > 0f)
    assertEquals(0f, motion.lift(1f), EPSILON)
  }

  @Test
  fun motionSlowsDownTowardsTheEnd() {
    // Halfway through the time, 1 - 0.5^2 = 75 % of the motion is done.
    assertEquals(THREE_QUARTERS, decelerate(HALF_TIME), EPSILON)
  }

  @Test
  fun progressOutsideTheRollIsClampedToItsEnds() {
    assertSameRotation(motion.rotation(0f), motion.rotation(-1f))
    assertSameRotation(motion.rotation(1f), motion.rotation(2f))
  }

  @Test
  fun landingBounceGrowsThenReturnsToNormalSize() {
    assertEquals(1f, landingScale(0f), EPSILON)
    assertEquals(1.15f, landingScale(HALF_TIME), EPSILON)
    assertEquals(1f, landingScale(1f), EPSILON)
  }

  private companion object {
    /** Float tolerance for values computed with sin and pow. */
    const val EPSILON = 1e-4f

    /** The d20 rests on its 20 before the roll and lands on a 7. */
    const val RESTING_VALUE = 20
    const val ROLLED_VALUE = 7

    /** Seed of the tumble, fixed so every run tumbles the same way. */
    const val FIXED_SEED = 42

    /** A moment where the die is tumbling fast, a fifth of the way through the roll. */
    const val MID_ROLL = 0.2f

    /** How many moments of the roll are sampled to look for the tumble. */
    const val SAMPLES = 20

    /** Share of the roll, from its start, where the die still visibly tumbles. */
    const val TUMBLING_PART = 0.5f

    const val HALF_TIME = 0.5f
    const val THREE_QUARTERS = 0.75f
  }
}
