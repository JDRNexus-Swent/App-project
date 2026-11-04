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
import kotlin.coroutines.resumeWithException
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
      geocoder.getFromLocationName(city, 1)?.firstOrNull()?.let {
        MapLocation(it.latitude, it.longitude, it.locality ?: city)
      }
    }
  }

  @SuppressLint(
      "MissingPermission"
  ) // Permission is requested by the screen; revocation propagates.
  override suspend fun getDeviceLocation(): MapLocation {
    val provider =
        when {
          locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) ->
              LocationManager.NETWORK_PROVIDER
          locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ->
              LocationManager.GPS_PROVIDER
          else -> throw IOException("Enable device location to find nearby games.")
        }
    return withTimeoutOrNull(LOCATION_TIMEOUT_MS) {
      suspendCancellableCoroutine { continuation ->
        val signal = CancellationSignal()
        continuation.invokeOnCancellation { signal.cancel() }
        LocationManagerCompat.getCurrentLocation(
            locationManager,
            provider,
            signal,
            callbackExecutor,
        ) { location ->
          if (location == null)
              continuation.resumeWithException(IOException("Device location is unavailable."))
          else continuation.resume(MapLocation(location.latitude, location.longitude))
        }
      }
    } ?: throw IOException("Device location timed out. Try again.")
  }

  private companion object {
    const val LOCATION_TIMEOUT_MS = 15_000L
  }
}
