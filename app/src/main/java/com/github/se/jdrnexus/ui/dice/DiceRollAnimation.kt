// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.ui.dice

import dev.romainguy.kotlin.math.Float3
import dev.romainguy.kotlin.math.Quaternion
import dev.romainguy.kotlin.math.normalize
import dev.romainguy.kotlin.math.slerp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/*
 * Motion of the roll animation, as pure functions of its progress (0 = roll starts, 1 = die lands)
 * so they can be tested without a clock. The screen only plays them on a frame clock.
 */

/**
 * How fast the die loses speed: progress p has covered 1 - (1 - p)^this of the motion. 2 makes it
 * start fast and settle like a thrown die.
 */
private const val DECELERATION_POWER = 2.0

/** Degrees in one full turn. */
private const val FULL_TURN_DEGREES = 360f

/**
 * Full turns of the tumble around its first and second axes. Whole numbers, so the tumble adds no
 * rotation at the start or the end of the roll: the die leaves its resting pose and lands on the
 * result without a jump.
 */
private const val FIRST_AXIS_TURNS = 3
private const val SECOND_AXIS_TURNS = 2

/** Highest point of the hop, as a fraction of the die's size. */
private const val MAX_LIFT_FRACTION = 0.15f

/** How much the landed die grows at the peak of its bounce. */
private const val LANDING_SCALE_GAIN = 0.15f

/** Share of the roll's motion done at [progress]: fast at first, slower and slower at the end. */
internal fun decelerate(progress: Float): Float =
    1f - (1f - progress.coerceIn(0f, 1f)).toDouble().pow(DECELERATION_POWER).toFloat()

/** A random direction, every direction being equally likely. */
private fun randomAxis(random: Random): Float3 {
  // Uniform on the sphere: a uniform height, then a uniform angle around the vertical.
  val z = random.nextFloat() * 2f - 1f
  val angle = random.nextFloat() * 2f * PI.toFloat()
  val radius = sqrt(1f - z * z)
  return Float3(radius * cos(angle), radius * sin(angle), z)
}

/**
 * One roll of the 3D die: it leaves the rotation [from], tumbles around two axes picked from [seed]
 * while slowing down, and lands on the rotation [to]. Two seeds give two different tumbles; the
 * same seed always gives the same one, so tests can replay it.
 */
internal class RollMotion(private val from: Quaternion, private val to: Quaternion, seed: Int) {
  private val random = Random(seed)
  private val firstAxis = randomAxis(random)
  private val secondAxis = randomAxis(random)

  /** Rotation of the die at [progress]: [from] at 0, [to] at 1, tumbling in between. */
  fun rotation(progress: Float): Quaternion {
    val travelled = decelerate(progress)
    // The turns still to go: all of them at the start, none once landed.
    val remaining = 1f - travelled
    // combine instead of kotlin-math's `*`: see DieMath.kt.
    val tumble =
        combine(
            Quaternion.fromAxisAngle(firstAxis, FIRST_AXIS_TURNS * FULL_TURN_DEGREES * remaining),
            Quaternion.fromAxisAngle(secondAxis, SECOND_AXIS_TURNS * FULL_TURN_DEGREES * remaining),
        )
    return normalize(combine(tumble, slerp(from, to, travelled)))
  }

  /** Height of the hop at [progress], as a fraction of the die's size: 0 at both ends. */
  fun lift(progress: Float): Float = MAX_LIFT_FRACTION * sin(PI * decelerate(progress)).toFloat()
}

/** Scale of the die at [progress] of its landing bounce: 1, up to a peak, then back to 1. */
internal fun landingScale(progress: Float): Float =
    1f + LANDING_SCALE_GAIN * sin(PI * progress.coerceIn(0f, 1f)).toFloat()
