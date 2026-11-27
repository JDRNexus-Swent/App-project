// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.model.connectivity

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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
 * last forever. Here, [action] starts only once [isOnline] has emitted true: if its first value is
 * false, [OfflineException] is thrown and [action] never runs, even when that first value arrives
 * late (a `callbackFlow` may suspend before sending it). If the connection drops while [action]
 * runs, [action] is cancelled and [OfflineException] is thrown. A write already in Firestore's
 * local cache is not undone by the cancellation.
 *
 * [isOnline] must emit its current state first, as [ConnectivityRepository.isOnline] does. If it
 * ends without reading false, the device counts as online until the action ends.
 */
suspend fun <T> Flow<Boolean>.failWhenOffline(action: suspend () -> T): T = coroutineScope {
  // Completed by the first value when it is true, or when the flow ends while online.
  val confirmedOnline = CompletableDeferred<Unit>()
  val watcher = launch {
    collect { online ->
      // Failing this child cancels the action, or the wait for the first value, and makes
      // coroutineScope throw this exception.
      if (!online) throw OfflineException()
      confirmedOnline.complete(Unit)
    }
    confirmedOnline.complete(Unit)
  }
  try {
    // The action makes no request before the device is known to be online.
    confirmedOnline.await()
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
