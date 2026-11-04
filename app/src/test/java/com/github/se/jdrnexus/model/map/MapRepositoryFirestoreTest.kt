// Co-authored-by: OpenAI Codex
package com.github.se.jdrnexus.model.map

import com.github.se.jdrnexus.model.map.MapRepositoryFirestore.Companion.MAP_SESSIONS_COLLECTION
import com.github.se.jdrnexus.utils.FirebaseEmulatorTestUtils
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Date
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Those tests use the Firestore emulator WITH TEST SETTINGS, make sure to activate it with
 * "firebase emulators:exec --config firebase.test.json".
 */
@RunWith(RobolectricTestRunner::class)
class MapRepositoryFirestoreTest {

  private lateinit var firestore: FirebaseFirestore
  private lateinit var repository: MapRepositoryFirestore

  @Before
  fun setUp() {
    firestore = FirebaseEmulatorTestUtils.getFirestore()
    repository = MapRepositoryFirestore(firestore, ZoneOffset.UTC)
    runBlocking { FirebaseEmulatorTestUtils.clearCollection(firestore, MAP_SESSIONS_COLLECTION) }
  }

  @After
  fun tearDown() {
    runBlocking { FirebaseEmulatorTestUtils.tearDown(firestore, MAP_SESSIONS_COLLECTION) }
  }

  private fun session(
      coordinates: GeoPoint = GeoPoint(0.0, 0.0),
      date: String = "2026-11-05T18:30:00Z",
      overrides: Map<String, Any?> = emptyMap(),
  ): Map<String, Any?> =
      mapOf(
          "coordinates" to coordinates,
          "title" to "Campaign",
          "gameMasterName" to "Alex",
          "gameSystem" to "Pathfinder",
          "dateTime" to Timestamp(Date.from(Instant.parse(date))),
          "availableSeats" to 2L,
      ) + overrides

  private fun seed(vararg sessions: Pair<String, Map<String, Any?>>) = runBlocking {
    sessions.forEach { (id, data) ->
      firestore.collection(MAP_SESSIONS_COLLECTION).document(id).set(data).await()
    }
  }

  @Test
  fun loadsAllFieldsWithoutDistanceWhenNoLocationIsGiven() = runBlocking {
    seed("one" to session())

    val expected =
        MapGame(
            id = "one",
            latitude = 0.0,
            longitude = 0.0,
            title = "Campaign",
            gameMasterName = "Alex",
            gameSystem = "Pathfinder",
            dateTime = LocalDateTime.of(2026, 11, 5, 18, 30),
            availableSeats = 2,
        )
    assertEquals(listOf(expected), repository.loadGamesNear(null))
  }

  @Test
  fun withoutLocationGamesAreSortedByDate() = runBlocking {
    seed(
        "later" to session(date = "2026-12-01T10:00:00Z"),
        "sooner" to session(date = "2026-11-10T10:00:00Z"),
    )

    assertEquals(listOf("sooner", "later"), repository.loadGamesNear(null).map { it.id })
  }

  @Test
  fun withLocationGamesAreSortedNearestFirstWithDistance() = runBlocking {
    seed("far" to session(GeoPoint(0.0, 90.0)), "near" to session(GeoPoint(0.0, 0.0)))

    val games = repository.loadGamesNear(MapLocation(0.0, 0.0))

    assertEquals(listOf("near", "far"), games.map { it.id })
    assertEquals(0.0, games[0].distanceKm!!, 0.001)
    assertEquals(10007.56, games[1].distanceKm!!, 0.1)
  }

  @Test
  fun distanceCrossesTheDateLine() = runBlocking {
    seed("across" to session(GeoPoint(0.0, -179.0)))

    assertEquals(
        222.39,
        repository.loadGamesNear(MapLocation(0.0, 179.0)).single().distanceKm!!,
        0.1,
    )
  }

  @Test
  fun emptyCollectionReturnsNoGames() = runBlocking {
    assertTrue(repository.loadGamesNear(null).isEmpty())
  }

  @Test
  fun malformedListingsAreSkipped() = runBlocking {
    val invalidFields =
        listOf(
            mapOf("coordinates" to null),
            mapOf("title" to " "),
            mapOf("gameMasterName" to 1L),
            mapOf("gameSystem" to null),
            mapOf("dateTime" to "tomorrow"),
            mapOf("availableSeats" to -1L),
        )
    seed(
        "valid" to session(overrides = mapOf("availableSeats" to 0L)),
        *invalidFields
            .mapIndexed { index, fields -> "invalid$index" to session(overrides = fields) }
            .toTypedArray(),
    )

    val game = repository.loadGamesNear(null).single()
    assertEquals("valid", game.id)
    assertEquals(0, game.availableSeats)
    assertNull(game.distanceKm)
  }
}
