// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.model.connectivity

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * [ConnectivityRepository] backed by Android's [ConnectivityManager].
 *
 * The device counts as online when its default network has internet access that Android has
 * validated: a Wi-Fi without internet, or behind a login page, counts as offline. The network
 * callback is registered only while [isOnline] is collected.
 */
class ConnectivityRepositoryAndroid(private val connectivityManager: ConnectivityManager) :
    ConnectivityRepository {

  override val isOnline: Flow<Boolean> = callbackFlow {
    val callback =
        object : ConnectivityManager.NetworkCallback() {
          override fun onCapabilitiesChanged(
              network: Network,
              capabilities: NetworkCapabilities,
          ) {
            trySend(capabilities.hasValidatedInternet())
          }

          override fun onLost(network: Network) {
            trySend(false)
          }
        }
    // The callback only reports changes, so the current state is sent first.
    trySend(isOnlineNow())
    connectivityManager.registerDefaultNetworkCallback(callback)
    awaitClose { connectivityManager.unregisterNetworkCallback(callback) }
  }
      .distinctUntilChanged()

  /** Whether the current default network has validated internet access. */
  private fun isOnlineNow(): Boolean {
    val network = connectivityManager.activeNetwork ?: return false
    return connectivityManager.getNetworkCapabilities(network)?.hasValidatedInternet() == true
  }

  private fun NetworkCapabilities.hasValidatedInternet(): Boolean =
      hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
          hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}
