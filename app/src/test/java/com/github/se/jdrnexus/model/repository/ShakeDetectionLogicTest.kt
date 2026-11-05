// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.model.repository

import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShakeDetectionLogicTest {

  /** A phone lying flat: gravity only, 1 g. */
  private val still = STANDARD_GRAVITY

  /** A hard shake: 4 g in total, 3 g beyond gravity. */
  private val strong = STANDARD_GRAVITY * 4f

  private val logic = ShakeDetectionLogic()

  /** Feeds a reading along the z axis only. */
  private fun ShakeDetectionLogic.read(z: Float, latestReadingMillis: Long) =
      onAcceleration(0f, 0f, z, latestReadingMillis)

  @Test
  fun `phone lying still never rolls`() {
    for (t in 0L..10_000L step 60) assertFalse(logic.read(still, t))
  }

  @Test
  fun `strong reading alone does not roll`() {
    assertFalse(logic.read(strong, 0))
  }

  @Test
  fun `rolls on the first calm reading once the shake has settled`() {
    logic.read(strong, 0)

    assertFalse(logic.read(still, DEFAULT_SHAKE_SETTLE_MILLIS - 1))
    assertTrue(logic.read(still, DEFAULT_SHAKE_SETTLE_MILLIS))
  }

  @Test
  fun `one shake rolls only once`() {
    logic.read(strong, 0)
    assertTrue(logic.read(still, DEFAULT_SHAKE_SETTLE_MILLIS))

    for (t in DEFAULT_SHAKE_SETTLE_MILLIS + 60..10_000L step 60) assertFalse(logic.read(still, t))
  }

  @Test
  fun `long shake with dips between swings gives exactly one roll after it stops`() {
    var rolls = 0
    // Three seconds of swings: a strong reading every 100 ms, with a calm dip 50 ms after each,
    // like a real hand changing direction. The dips are what make this test useful: a logic that
    // rolled on the first calm reading would roll on every dip here, while without dips it would
    // pass.
    for (t in 0L until 3_000L step 100) {
      if (logic.read(strong, t)) rolls++
      if (logic.read(still, t + 50)) rolls++
    }
    assertEquals("No roll while still shaking", 0, rolls)

    for (t in 3_000L..4_000L step 60) if (logic.read(still, t)) rolls++
    assertEquals("Exactly one roll once the shake stops", 1, rolls)
  }

  @Test
  fun `reading just below the threshold never starts a shake`() {
    val movementG = DEFAULT_SHAKE_THRESHOLD_G - 0.01f // 2.49 g of movement: just below 2.5 g
    val totalG = movementG + 1f // what the sensor measures: movement plus 1 g of gravity
    val reading = totalG * STANDARD_GRAVITY
    logic.read(reading, 0)

    assertFalse(logic.read(still, 1_000))
  }

  @Test
  fun `reading just above the threshold starts a shake`() {
    val movementG = DEFAULT_SHAKE_THRESHOLD_G + 0.01f // 2.51 g of movement: just above 2.5 g
    val totalG = movementG + 1f // what the sensor measures: movement plus 1 g of gravity
    val reading = totalG * STANDARD_GRAVITY
    logic.read(reading, 0)

    assertTrue(logic.read(still, 1_000))
  }

  @Test
  fun `acceleration on negative and mixed axes counts`() {
    val axis = -strong / sqrt(3f) // Same 4 g magnitude, spread over the three axes.
    logic.onAcceleration(axis, axis, axis, 0)

    assertTrue(logic.read(still, DEFAULT_SHAKE_SETTLE_MILLIS))
  }

  @Test
  fun `shaking within the debounce after a roll is ignored`() {
    val rollTime = DEFAULT_SHAKE_SETTLE_MILLIS
    logic.read(strong, 0)
    assertTrue(logic.read(still, rollTime))

    val tooSoon = rollTime + DEFAULT_SHAKE_DEBOUNCE_MILLIS - 1
    logic.read(strong, tooSoon)
    assertFalse(logic.read(still, tooSoon + DEFAULT_SHAKE_SETTLE_MILLIS))
  }

  @Test
  fun `shaking after the debounce rolls again once it settles`() {
    val rollTime = DEFAULT_SHAKE_SETTLE_MILLIS
    logic.read(strong, 0)
    assertTrue(logic.read(still, rollTime))

    val next = rollTime + DEFAULT_SHAKE_DEBOUNCE_MILLIS
    logic.read(strong, next)
    assertFalse(logic.read(still, next + DEFAULT_SHAKE_SETTLE_MILLIS - 1))
    assertTrue(logic.read(still, next + DEFAULT_SHAKE_SETTLE_MILLIS))
  }

  @Test
  fun `custom threshold, settle and debounce are used`() {
    val custom = ShakeDetectionLogic(thresholdG = 1f, settleMillis = 100, debounceMillis = 500)
    val medium = STANDARD_GRAVITY * 2.5f // 1.5 g net: a shake for this threshold only.

    custom.read(medium, 0)
    assertFalse(custom.read(still, 99))
    assertTrue(custom.read(still, 100))

    custom.read(medium, 599) // 499 ms after the roll: ignored.
    assertFalse(custom.read(still, 750))

    custom.read(medium, 800) // 700 ms after the roll: a new shake.
    assertTrue(custom.read(still, 900))
  }
}
