// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.model.sensor

import kotlinx.coroutines.flow.Flow

/** Source of "the user shook the phone" events. */
interface ShakeDetector {
  /** False on devices without the sensor this detector needs; [shakes] then never emits. */
  val isAvailable: Boolean

  /** Emits once per shake. The sensor is only listened to while this flow is collected. */
  val shakes: Flow<Unit>
}
