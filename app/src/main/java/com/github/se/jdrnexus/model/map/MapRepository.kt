// Co-authored-by: OpenAI Codex
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
  suspend fun loadGamesNear(location: MapLocation?): List<MapGame>
}

/** Resolves city queries and the device's current location. */
interface MapLocationRepository {
  suspend fun searchCity(query: String): MapLocation?

  suspend fun getDeviceLocation(): MapLocation
}
