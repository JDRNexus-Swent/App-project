// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.model.repository

import kotlin.math.sqrt

/** Standard gravity in m/s², the unit Android reports accelerations in. */
const val STANDARD_GRAVITY = 9.80665f

/** Default net acceleration (beyond gravity), in g, that counts as shaking. Tune on device. */
const val DEFAULT_SHAKE_THRESHOLD_G = 2.5f

/** Default calm time after the last strong reading before the shake counts as over. */
const val DEFAULT_SHAKE_SETTLE_MILLIS = 400L

/** Default time after a roll during which new shaking is ignored. */
const val DEFAULT_SHAKE_DEBOUNCE_MILLIS = 2_000L

/**
 * Turns raw accelerometer readings into "the user shook and stopped" events, like shaking dice in a
 * hand before throwing them.
 *
 * A reading whose magnitude minus gravity exceeds [thresholdG] starts or extends a shake. The shake
 * ends, and [onAcceleration] returns true once, on the first reading that comes at least
 * [settleMillis] after the last strong one. The short dips between two swings of the same shake are
 * shorter than that, so they do not end it. For [debounceMillis] after that roll, strong readings
 * are ignored, so one shake gives one roll.
 */
class ShakeDetectionLogic(
    private val thresholdG: Float = DEFAULT_SHAKE_THRESHOLD_G,
    private val settleMillis: Long = DEFAULT_SHAKE_SETTLE_MILLIS,
    private val debounceMillis: Long = DEFAULT_SHAKE_DEBOUNCE_MILLIS,
) {
  /** Time of the last strong reading of the ongoing shake, or null when not shaking. */
  private var lastStrongReadingMillis: Long? = null

  /** Time of the last roll, or null before the first one. */
  private var lastRollMillis: Long? = null

  /**
   * Feeds the latest reading (m/s² per axis, taken at [latestReadingMillis]); returns true when a
   * shake has just ended.
   */
  fun onAcceleration(x: Float, y: Float, z: Float, latestReadingMillis: Long): Boolean {
    val netG = sqrt(x * x + y * y + z * z) / STANDARD_GRAVITY - 1f
    if (netG > thresholdG) {
      val lastRoll = lastRollMillis
      if (lastRoll == null || latestReadingMillis - lastRoll >= debounceMillis) {
        lastStrongReadingMillis = latestReadingMillis
      }
      return false
    }
    val lastStrongReading = lastStrongReadingMillis ?: return false
    if (latestReadingMillis - lastStrongReading < settleMillis) return false
    lastStrongReadingMillis = null
    lastRollMillis = latestReadingMillis
    return true
  }
}
