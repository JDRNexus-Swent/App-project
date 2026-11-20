// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.firestoreRules

import com.github.se.jdrnexus.utils.FirebaseEmulatorTestUtils
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Security rules of `map_sessions` (read-only for signed-in clients) and of the final `match
 * /{document=**}` (everything not matched above is denied), on the Firestore emulator started with
 * the project rules ("firebase emulators:exec --only auth,firestore").
 */
@RunWith(RobolectricTestRunner::class)
class MapSessionsAndDefaultRulesTest {

  companion object {
    private const val USER_ID = "map-user"
    private const val SESSION_ID = "session"
    private const val UNKNOWN_COLLECTION = "unknown_collection"
    private val SESSION = mapOf("title" to "Friday one-shot")
  }

  private lateinit var user: FirebaseFirestore

  @Before
  fun setUp() {
    user = FirebaseEmulatorTestUtils.signInAs(USER_ID)
  }

  @After
  fun tearDown() = runBlocking {
    FirebaseEmulatorTestUtils.tearDown(user, "map_sessions", UNKNOWN_COLLECTION)
  }

  @Test
  fun signedInUsersCanReadMapSessions() = runBlocking {
    FirebaseEmulatorTestUtils.writeBypassingRules(user, "map_sessions/$SESSION_ID", SESSION)

    assertEquals(
        "Friday one-shot",
        user.collection("map_sessions").document(SESSION_ID).get().await().getString("title"),
    )
    assertEquals(
        listOf(SESSION_ID),
        user.collection("map_sessions").get().await().documents.map { it.id },
    )
  }

  @Test
  fun signedOutUsersCannotReadMapSessions() = runBlocking {
    FirebaseEmulatorTestUtils.writeBypassingRules(user, "map_sessions/$SESSION_ID", SESSION)
    val signedOut = FirebaseEmulatorTestUtils.signedOutFirestore()

    FirebaseEmulatorTestUtils.assertPermissionDenied {
      signedOut.collection("map_sessions").get().await()
    }
  }

  @Test
  fun clientsCannotWriteMapSessions() = runBlocking {
    FirebaseEmulatorTestUtils.writeBypassingRules(user, "map_sessions/$SESSION_ID", SESSION)
    val session = user.collection("map_sessions").document(SESSION_ID)

    FirebaseEmulatorTestUtils.assertPermissionDenied("create") {
      user.collection("map_sessions").document("new").set(SESSION).await()
    }
    FirebaseEmulatorTestUtils.assertPermissionDenied("update") {
      session.update("title", "Cancelled").await()
    }
    FirebaseEmulatorTestUtils.assertPermissionDenied("delete") { session.delete().await() }

    assertEquals(
        listOf(SESSION_ID),
        FirebaseEmulatorTestUtils.documentIdsBypassingRules(user, "map_sessions"),
    )
    assertEquals("Friday one-shot", session.get().await().getString("title"))
  }

  @Test
  fun anyOtherCollectionIsDenied() = runBlocking {
    FirebaseEmulatorTestUtils.writeBypassingRules(user, "$UNKNOWN_COLLECTION/existing", SESSION)
    val collection = user.collection(UNKNOWN_COLLECTION)

    FirebaseEmulatorTestUtils.assertPermissionDenied("get") {
      collection.document("existing").get().await()
    }
    FirebaseEmulatorTestUtils.assertPermissionDenied("list") { collection.get().await() }
    FirebaseEmulatorTestUtils.assertPermissionDenied("create") {
      collection.document("new").set(SESSION).await()
    }

    assertEquals(
        listOf("existing"),
        FirebaseEmulatorTestUtils.documentIdsBypassingRules(user, UNKNOWN_COLLECTION),
    )
  }
}
