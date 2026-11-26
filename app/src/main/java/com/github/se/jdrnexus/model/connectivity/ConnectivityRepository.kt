// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.model.connectivity

import kotlinx.coroutines.flow.Flow

/** Tells whether the device can currently reach the internet. */
interface ConnectivityRepository {
  /**
   * True while the device has a network with working internet access, false otherwise. Emits the
   * current state first, then each change, never the same value twice in a row.
   */
  val isOnline: Flow<Boolean>
}
