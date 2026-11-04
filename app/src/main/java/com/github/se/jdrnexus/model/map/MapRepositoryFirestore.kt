// Co-authored-by: OpenAI Codex
package com.github.se.jdrnexus.model.map

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.tasks.await

/**
 * Reads public session listings from [MAP_SESSIONS_COLLECTION].
 *
 * Campaign groups are readable only by their members, so nearby players discover sessions through
 * this separate listing collection. Malformed listings are skipped rather than failing the map.
 */
class MapRepositoryFirestore(
    private val firestore: FirebaseFirestore,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) : MapRepository {

  override suspend fun loadGamesNear(location: MapLocation?): List<MapGame> {
    val games =
        firestore.collection(MAP_SESSIONS_COLLECTION).get().await().documents.mapNotNull {
          it.toMapGame(location)
        }
    // The card defines no radius: order by proximity instead of silently excluding sessions.
    return if (location == null) games.sortedBy { it.dateTime }
    else games.sortedBy { it.distanceKm }
  }

  private fun DocumentSnapshot.toMapGame(origin: MapLocation?): MapGame? {
    val point = get("coordinates") as? GeoPoint ?: return null
    val date = get("dateTime") as? Timestamp ?: return null
    val seats = (get("availableSeats") as? Long)?.takeIf { it in 0..Int.MAX_VALUE } ?: return null
    return MapGame(
        id = id,
        latitude = point.latitude,
        longitude = point.longitude,
        title = text("title") ?: return null,
        gameMasterName = text("gameMasterName") ?: return null,
        gameSystem = text("gameSystem") ?: return null,
        dateTime = LocalDateTime.ofInstant(date.toDate().toInstant(), zoneId),
        availableSeats = seats.toInt(),
        distanceKm = origin?.let { distanceKm(it, point) },
    )
  }

  private fun DocumentSnapshot.text(field: String): String? =
      (get(field) as? String)?.takeIf { it.isNotBlank() }

  /** Great-circle distance using the haversine formula. */
  private fun distanceKm(origin: MapLocation, destination: GeoPoint): Double {
    val latitudeSine = sin(Math.toRadians(destination.latitude - origin.latitude) / 2)
    val longitudeSine = sin(Math.toRadians(destination.longitude - origin.longitude) / 2)
    val haversine =
        latitudeSine * latitudeSine +
            cos(Math.toRadians(origin.latitude)) *
                cos(Math.toRadians(destination.latitude)) *
                longitudeSine *
                longitudeSine
    return 2 * EARTH_RADIUS_KM * asin(sqrt(haversine.coerceIn(0.0, 1.0)))
  }

  companion object {
    const val MAP_SESSIONS_COLLECTION = "map_sessions"
    private const val EARTH_RADIUS_KM = 6371.0088
  }
}
