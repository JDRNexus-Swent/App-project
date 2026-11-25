// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.utils

import com.github.se.jdrnexus.model.connectivity.ConnectivityRepository
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Connectivity driven by hand: tests set [online] to simulate losing and regaining the connection.
 * Like the Android repository, it emits the current state first and never repeats a value.
 */
class FakeConnectivityRepository(isOnline: Boolean = true) : ConnectivityRepository {
  val online = MutableStateFlow(isOnline)

  override val isOnline = online
}
