package com.github.se.jdrnexus.model.map

import android.content.Context
import android.location.Geocoder
import android.location.LocationManager
import androidx.core.content.ContextCompat
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore

/**
 * Provides the map repositories used by the app. [mapRepository] is mutable for testing purposes.
 */
object MapRepositoryProvider {
  private val _mapRepository: MapRepository by lazy { MapRepositoryFirestore(Firebase.firestore) }

  /** Campaign sessions shown on the map, read from Firestore. */
  var mapRepository: MapRepository = _mapRepository

  /**
   * City search and device location backed by the platform services of [context].
   *
   * Unlike [mapRepository], this one needs a [Context], so it is built on request instead of being
   * stored. The application context is used so that the repository never holds an Activity.
   */
  fun locationRepository(context: Context): MapLocationRepository {
    val appContext = context.applicationContext
    return MapLocationRepositoryAndroid(
        geocoder = Geocoder(appContext),
        locationManager = checkNotNull(appContext.getSystemService(LocationManager::class.java)),
        callbackExecutor = ContextCompat.getMainExecutor(appContext),
    )
  }
}
