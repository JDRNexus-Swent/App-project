// Co-authored-by: OpenAI Codex
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.model.map

import java.time.LocalDateTime

/** A campaign session's location and details needed by the map feature. */
data class MapGame(
    val id: String,
    val latitude: Double,
    val longitude: Double,
    val title: String,
    val gameMasterName: String,
    val gameSystem: String,
    val dateTime: LocalDateTime,
    val availableSeats: Int,
    val distanceKm: Double? = null,
)

/** A geographic point returned by city search or device location. */
data class MapLocation(
    val latitude: Double,
    val longitude: Double,
    val label: String? = null,
)

/** Supplies the campaign sessions to display around a map center. */
interface MapRepository {
  /**
   * Returns the sessions to show around [location], with their distance when [location] is given.
   * With a null [location] there is no center, so no distance is set. Failures are thrown to the
   * caller.
   */
  suspend fun loadGamesNear(location: MapLocation?): List<MapGame>
}

/** Resolves city queries and the device's current location. */
interface MapLocationRepository {
  /** Returns the coordinates of the best match for the city [query], or null if none is found. */
  suspend fun searchCity(query: String): MapLocation?

  /**
   * Returns the current device location. The Android implementation throws an `IOException` with a
   * user-readable message when device location is disabled or unavailable.
   */
  suspend fun getDeviceLocation(): MapLocation
}
