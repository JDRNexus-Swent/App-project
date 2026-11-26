// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.model.connectivity

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Message of an action that needs the server, refused or stopped because the device is offline. */
const val NEEDS_CONNECTION = "Needs a connection."

/** A server-only action could not run, or was stopped, because the device is offline. */
class OfflineException : IllegalStateException(NEEDS_CONNECTION)

/**
 * Runs [action], an action that needs the server, while [isOnline] stays true.
 *
 * Offline, a Firestore request that waits for the server never returns, so its loading state would
 * last forever. Here, the action is cancelled and [OfflineException] is thrown as soon as
 * [isOnline] reads false: at once if the device is already offline, or when the connection drops
 * while the action runs. A write already in Firestore's local cache is not undone by the
 * cancellation.
 *
 * [isOnline] must emit its current state first, as [ConnectivityRepository.isOnline] does. If it
 * ends without reading false, the device counts as online until the action ends.
 */
suspend fun <T> Flow<Boolean>.failWhenOffline(action: suspend () -> T): T = coroutineScope {
  // Started before the action, so an offline device fails without the action ever running.
  val watcher =
      launch(start = CoroutineStart.UNDISPATCHED) {
        // Null when the flow ends while online: the action then runs to its end.
        if (firstOrNull { online -> !online } != null) {
          // Failing this child cancels the action and makes coroutineScope throw this exception.
          throw OfflineException()
        }
      }
  try {
    // Throws if the watcher has already failed, before the action makes any request.
    ensureActive()
    action()
  } finally {
    watcher.cancel()
  }
}

/**
 * [ConnectivityRepository.isOnline] as a [StateFlow] for a ViewModel's screen, so it can disable
 * the actions that need the server. It starts as online, so nothing flashes as disabled before the
 * first value, and it follows the connection only while the screen collects it.
 */
fun ConnectivityRepository.isOnlineIn(scope: CoroutineScope): StateFlow<Boolean> =
    isOnline.stateIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), true)

/** Keeps the network callback across a configuration change, which briefly drops collectors. */
private const val STOP_TIMEOUT_MILLIS = 5_000L
