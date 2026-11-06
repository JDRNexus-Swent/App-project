// Co-authored-by: OpenAI Codex
package com.github.se.jdrnexus.model.map

import android.annotation.SuppressLint
import android.location.Geocoder
import android.location.LocationManager
import android.os.CancellationSignal
import androidx.core.location.LocationManagerCompat
import java.io.IOException
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Platform geocoding and cancellable device location, called after the screen requests permission.
 */
class MapLocationRepositoryAndroid(
    private val geocoder: Geocoder,
    private val locationManager: LocationManager,
    private val callbackExecutor: Executor,
) : MapLocationRepository {
  @Suppress("DEPRECATION") // Supports API 28; the blocking platform call runs on the IO dispatcher.
  override suspend fun searchCity(query: String): MapLocation? {
    val city = query.trim()
    if (city.isEmpty()) return null
    return withContext(Dispatchers.IO) {
      // Reading the coordinates of an Address that has none throws, so such results are skipped.
      geocoder
          .getFromLocationName(city, 1)
          ?.firstOrNull { it.hasLatitude() && it.hasLongitude() }
          ?.let { MapLocation(it.latitude, it.longitude, it.locality ?: city) }
    }
  }

  override suspend fun getDeviceLocation(): MapLocation {
    val providers = PROVIDERS.filter(locationManager::isProviderEnabled)
    if (providers.isEmpty()) throw IOException("Enable device location to find nearby games.")
    // An enabled provider can still fail to get a fix, so fall back to the next one.
    for (provider in providers) {
      currentLocation(provider)?.let {
        return it
      }
    }
    throw IOException("Device location is unavailable. Try again.")
  }

  /** The current location from [provider], or null if it has none or times out. */
  @SuppressLint(
      "MissingPermission"
  ) // Permission is requested by the screen; revocation propagates.
  private suspend fun currentLocation(provider: String): MapLocation? =
      withTimeoutOrNull(LOCATION_TIMEOUT_MS) {
        suspendCancellableCoroutine { continuation ->
          val signal = CancellationSignal()
          continuation.invokeOnCancellation { signal.cancel() }
          LocationManagerCompat.getCurrentLocation(
              locationManager,
              provider,
              signal,
              callbackExecutor,
          ) { location ->
            continuation.resume(location?.let { MapLocation(it.latitude, it.longitude) })
          }
        }
      }

  private companion object {
    const val LOCATION_TIMEOUT_MS = 15_000L
    val PROVIDERS = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
  }
}
