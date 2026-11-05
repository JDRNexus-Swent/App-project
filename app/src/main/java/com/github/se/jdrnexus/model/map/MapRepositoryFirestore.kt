// Co-authored-by: OpenAI Codex
package com.github.se.jdrnexus.model.map

import com.firebase.geofire.GeoFireUtils
import com.firebase.geofire.GeoLocation
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.tasks.await

/**
 * Reads public session listings from [MAP_SESSIONS_COLLECTION].
 *
 * Campaign groups are readable only by their members, so nearby players discover sessions through
 * this separate listing collection. Malformed listings are skipped rather than failing the map.
 *
 * Firestore cannot query a radius, so each listing must store, next to its `coordinates`, a
 * [GEOHASH_FIELD] computed with `GeoFireUtils.getGeoHashForLocation`. Listings without it never
 * appear around a location.
 */
class MapRepositoryFirestore(
    private val firestore: FirebaseFirestore,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
    private val now: () -> Instant = Instant::now,
) : MapRepository {

  override suspend fun loadGamesNear(location: MapLocation?): List<MapGame> {
    val sessions = firestore.collection(MAP_SESSIONS_COLLECTION)
    val now = now()
    if (location == null) {
      // Without a center there is no radius, so show every upcoming session, soonest first.
      return sessions
          .whereGreaterThanOrEqualTo(DATE_FIELD, Timestamp(now.epochSecond, now.nano))
          .get()
          .await()
          .documents
          .mapNotNull { it.toMapGame(center = null, now = now) }
          .sortedBy { it.dateTime }
    }

    // Each bound is a geohash range covering part of the circle; together they cover all of it.
    // The queries start together and run in parallel.
    val center = GeoLocation(location.latitude, location.longitude)
    val queries =
        GeoFireUtils.getGeoHashQueryBounds(center, RADIUS_KM * 1000).map { bound ->
          sessions.orderBy(GEOHASH_FIELD).startAt(bound.startHash).endAt(bound.endHash).get()
        }
    return queries
        .flatMap { it.await().documents }
        .distinctBy { it.id }
        .mapNotNull { it.toMapGame(center, now) }
        .sortedBy { it.distanceKm }
  }

  /**
   * The listing as a [MapGame], or null if it is malformed, has already started, or lies outside
   * the radius around [center]. The geohash ranges reach slightly past the circle, and Firestore
   * cannot combine them with a date range, so both are checked here.
   */
  private fun DocumentSnapshot.toMapGame(center: GeoLocation?, now: Instant): MapGame? {
    val point = get("coordinates") as? GeoPoint ?: return null
    val start = (get(DATE_FIELD) as? Timestamp)?.toDate()?.toInstant() ?: return null
    if (start.isBefore(now)) return null
    val distanceKm = center?.let {
      GeoFireUtils.getDistanceBetween(GeoLocation(point.latitude, point.longitude), it) / 1000
    }
    if (distanceKm != null && distanceKm > RADIUS_KM) return null
    val seats =
        (get("availableSeats") as? Number)?.toLong()?.takeIf { it in 0..Int.MAX_VALUE }
            ?: return null
    return MapGame(
        id = id,
        latitude = point.latitude,
        longitude = point.longitude,
        title = text("title") ?: return null,
        gameMasterName = text("gameMasterName") ?: return null,
        gameSystem = text("gameSystem") ?: return null,
        dateTime = LocalDateTime.ofInstant(start, zoneId),
        availableSeats = seats.toInt(),
        distanceKm = distanceKm,
    )
  }

  private fun DocumentSnapshot.text(field: String): String? =
      (get(field) as? String)?.takeIf { it.isNotBlank() }

  companion object {
    const val MAP_SESSIONS_COLLECTION = "map_sessions"
    const val GEOHASH_FIELD = "geohash"

    /** Sessions further than this from the searched city or the user are not shown. */
    const val RADIUS_KM = 50.0

    private const val DATE_FIELD = "dateTime"
  }
}
