// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.model.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.se.jdrnexus.model.repository.DEFAULT_SHAKE_SETTLE_MILLIS
import com.github.se.jdrnexus.model.repository.STANDARD_GRAVITY
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowSensor
import org.robolectric.shadows.ShadowSensorManager

/** An accelerometer event carries one value per axis: x, y and z. */
private const val AXIS_COUNT = 3

/** Position of the z axis in an accelerometer event's values (x = 0, y = 1, z = 2). */
private const val Z_AXIS_INDEX = 2

/** Sensor event timestamps are in nanoseconds; the tests think in milliseconds. */
private const val NANOS_PER_MILLI = 1_000_000L

/** Movement of a hard shake, in g: well above the 2.5 g threshold. */
private const val SHAKE_MOVEMENT_G = 3f

/** What the sensor reports during that shake, in m/s²: the movement plus 1 g of gravity. */
private const val SHAKE_READING = (SHAKE_MOVEMENT_G + 1f) * STANDARD_GRAVITY

/** What the sensor reports for a phone lying still: gravity only. */
private const val STILL_READING = STANDARD_GRAVITY

/** Approximate time between two readings at SENSOR_DELAY_UI. */
private const val READING_INTERVAL_MILLIS = 60L

/** When the test shakes the phone. */
private const val SHAKE_MILLIS = 0L

/** First calm reading after the shake has settled, so the one that should roll. */
private const val SETTLED_MILLIS = SHAKE_MILLIS + DEFAULT_SHAKE_SETTLE_MILLIS + 100

/** The calm reading after that one, which must not roll a second time. */
private const val NEXT_READING_MILLIS = SETTLED_MILLIS + READING_INTERVAL_MILLIS

/** Virtual time after which a flow that should already have completed counts as hung. */
private const val HANG_TIMEOUT_MILLIS = 1_000L

/** Every accuracy level Android can report for a sensor, from best to worst. */
private val ALL_ACCURACY_LEVELS =
    listOf(
        SensorManager.SENSOR_STATUS_ACCURACY_HIGH,
        SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM,
        SensorManager.SENSOR_STATUS_ACCURACY_LOW,
        SensorManager.SENSOR_STATUS_UNRELIABLE,
    )

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class AccelerometerShakeDetectorTest {
  private val context: Context = RuntimeEnvironment.getApplication()
  private val shadowSensorManager = shadowOf(context.getSystemService(SensorManager::class.java))

  /** The failure switch is shared by all tests; never let one leak into the next. */
  @After fun allowListenerRegistrationAgain() = shadowSensorManager.setForceListenersToFail(false)

  /** Gives the simulated phone an accelerometer, and returns it. */
  private fun addAccelerometer(): Sensor =
      ShadowSensor.newInstance(Sensor.TYPE_ACCELEROMETER).also { shadowSensorManager.addSensor(it) }

  /** Delivers one reading along the z axis to every registered listener. */
  private fun sendReading(z: Float, timestampMillis: Long) {
    val event = ShadowSensorManager.createSensorEvent(AXIS_COUNT, Sensor.TYPE_ACCELEROMETER)
    event.values[Z_AXIS_INDEX] = z
    event.timestamp = timestampMillis * NANOS_PER_MILLI
    shadowSensorManager.sendSensorEventToListeners(event)
  }

  @Test
  fun unavailableWithoutAccelerometerAndShakesCompletesEmpty() = runTest {
    val detector = AccelerometerShakeDetector(context)

    assertFalse(detector.isAvailable)
    assertEquals(emptyList<Unit>(), detector.shakes.toList())
  }

  @Test
  fun availableWithAccelerometer() {
    addAccelerometer()

    assertTrue(AccelerometerShakeDetector(context).isAvailable)
  }

  @Test
  fun shakesCompletesEmptyWhenTheListenerCannotBeRegistered() = runTest {
    addAccelerometer()
    shadowSensorManager.setForceListenersToFail(true)
    val detector = AccelerometerShakeDetector(context)

    assertTrue(detector.isAvailable)
    assertEquals(emptyList<Unit>(), withTimeout(HANG_TIMEOUT_MILLIS) { detector.shakes.toList() })
  }

  @Test
  fun listenerIsRegisteredOnlyWhileCollected() = runTest {
    addAccelerometer()
    val detector = AccelerometerShakeDetector(context)
    assertTrue(shadowSensorManager.listeners.isEmpty())

    val job = launch(UnconfinedTestDispatcher(testScheduler)) { detector.shakes.collect {} }
    assertEquals(1, shadowSensorManager.listeners.size)

    job.cancel()
    advanceUntilIdle()
    assertTrue(shadowSensorManager.listeners.isEmpty())
  }

  @Test
  fun emitsOnceWhenTheShakeStops() = runTest {
    val accelerometer = addAccelerometer()
    val detector = AccelerometerShakeDetector(context)
    val shakes = mutableListOf<Unit>()
    val job =
        launch(UnconfinedTestDispatcher(testScheduler)) { detector.shakes.collect { shakes += it } }

    sendReading(SHAKE_READING, SHAKE_MILLIS)
    assertTrue("Still shaking: no roll yet", shakes.isEmpty())

    // Accuracy changes in the middle of a shake must neither roll nor stop the listening.
    val listener = shadowSensorManager.listeners.single()
    for (accuracy in ALL_ACCURACY_LEVELS) listener.onAccuracyChanged(accelerometer, accuracy)
    assertTrue("An accuracy change is not a shake", shakes.isEmpty())

    sendReading(STILL_READING, SETTLED_MILLIS)
    sendReading(STILL_READING, NEXT_READING_MILLIS)
    assertEquals("Exactly one roll", 1, shakes.size)

    job.cancel()
  }
}
