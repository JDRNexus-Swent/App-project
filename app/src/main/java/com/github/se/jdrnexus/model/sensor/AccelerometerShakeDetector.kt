// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.model.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.github.se.jdrnexus.model.repository.ShakeDetectionLogic
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

private const val NANOS_PER_MILLI = 1_000_000L

/**
 * [ShakeDetector] backed by the device accelerometer through [SensorManager]. The listener is
 * registered when [shakes] starts being collected and unregistered when the collection stops.
 */
class AccelerometerShakeDetector(context: Context) : ShakeDetector {
  private val sensorManager = context.getSystemService(SensorManager::class.java)
  private val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

  override val isAvailable: Boolean = accelerometer != null

  override val shakes: Flow<Unit> = callbackFlow {
    if (sensorManager == null || accelerometer == null) {
      close()
      return@callbackFlow
    }
    val logic = ShakeDetectionLogic()
    val listener =
        object : SensorEventListener {
          override fun onSensorChanged(event: SensorEvent) {
            val (x, y, z) = event.values
            if (logic.onAcceleration(x, y, z, event.timestamp / NANOS_PER_MILLI)) trySend(Unit)
          }

          override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
    sensorManager.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_UI)
    awaitClose { sensorManager.unregisterListener(listener) }
  }
}
