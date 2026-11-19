// Co-authored-by: Claude Sonnet 5.5
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.utils

import android.content.Context
import android.os.Looper
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import com.google.firebase.FirebaseApp
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.util.CustomClassMapper
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.util.Base64
import java.util.Date
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.robolectric.Shadows.shadowOf

/**
 * Shared helpers for every test class that talks to the Firestore emulator.
 *
 * Those tests need the emulator started with: firebase emulators:exec --only auth,firestore
 */
object FirebaseEmulatorTestUtils {

  /** Maximum time to wait for asynchronous Firebase operations in tests. */
  const val DEFAULT_TIMEOUT_MS = 5000L

  private const val EMULATOR_APP_NAME = "EmulatorApp"
  private const val EMULATOR_HOST = "127.0.0.1"
  private const val EMULATOR_PORT = 8080
  private const val AUTH_EMULATOR_PORT = 9099

  /** Firestore refuses batches of more than 500 writes. */
  private const val MAX_BATCH_SIZE = 500

  /** Page size asked when listing a collection (the emulator may return smaller pages). */
  private const val MAX_LISTED_DOCUMENTS = 10_000

  /** Claims the Auth emulator expects in a custom token (its signature is not checked). */
  private const val CUSTOM_TOKEN_AUDIENCE =
      "https://identitytoolkit.googleapis.com/google.identity.identitytoolkit.v1.IdentityToolkit"
  private const val CUSTOM_TOKEN_ISSUER = "test@emulator.iam.gserviceaccount.com"

  private const val TAG = "FirebaseEmulatorTestUtils"

  /**
   * Returns the default app's Firestore pointed to the emulator. Its requests come from the user
   * signed in through [getAuth] (the same default app the app code uses), so it is for tests that
   * sign in through the UI. Repository tests use [signInAs] instead.
   */
  fun getFirestore(): FirebaseFirestore {
    val context = ApplicationProvider.getApplicationContext<Context>()
    if (FirebaseApp.getApps(context).isEmpty()) FirebaseApp.initializeApp(context)
    return emulatorFirestore(FirebaseApp.getInstance())
  }

  // Points the Firestore instance of [app] to the emulator, or aborts if it already uses a server.
  private fun emulatorFirestore(app: FirebaseApp): FirebaseFirestore {
    val firestore = FirebaseFirestore.getInstance(app)

    try {
      firestore.useEmulator(EMULATOR_HOST, EMULATOR_PORT)
    } catch (e: IllegalStateException) {
      // Safety net: abort if, for any reason, this instance is not pointing to the emulator.
      if (!firestore.firestoreSettings.host.contains(EMULATOR_HOST)) {
        throw IllegalStateException(
            "Firestore is not using the emulator! Aborting to prevent data loss.",
            e,
        )
      }
    }
    return firestore
  }

  /**
   * Returns a Firestore instance on the emulator whose requests come from the user [uid], so the
   * security rules see `request.auth.uid == uid`. Each user gets their own FirebaseApp (and so
   * their own Auth and Firestore), which lets one test act as several users at once.
   */
  fun signInAs(uid: String): FirebaseFirestore {
    val context = ApplicationProvider.getApplicationContext<Context>()
    if (FirebaseApp.getApps(context).isEmpty()) FirebaseApp.initializeApp(context)
    val appName = "$EMULATOR_APP_NAME-$uid"
    val app =
        FirebaseApp.getApps(context).firstOrNull { it.name == appName }
            ?: FirebaseApp.initializeApp(context, FirebaseApp.getInstance().options, appName)

    val auth =
        FirebaseAuth.getInstance(app).apply { useEmulator(EMULATOR_HOST, AUTH_EMULATOR_PORT) }
    if (auth.currentUser?.uid != uid) {
      val signIn = auth.signInWithCustomToken(unsignedCustomToken(uid))
      // Auth completes on the main looper, which this (test) thread must pump.
      runBlocking { pumpMainLooperUntil { signIn.isComplete } }
      check(signIn.isSuccessful) {
        "Sign-in of '$uid' on the Auth emulator failed: ${signIn.exception}"
      }
    }
    return emulatorFirestore(app)
  }

  // The Auth emulator accepts unsigned custom tokens, which lets a test choose the uid.
  private fun unsignedCustomToken(uid: String): String {
    val encoder = Base64.getUrlEncoder().withoutPadding()
    // A JWT part is base64url-encoded JSON without padding.
    fun encode(json: JSONObject) = encoder.encodeToString(json.toString().toByteArray())
    val now = System.currentTimeMillis() / 1000
    val header = JSONObject().put("alg", "none").put("typ", "JWT")
    val payload =
        JSONObject()
            .put("uid", uid)
            .put("iat", now)
            .put("exp", now + 3600)
            .put("aud", CUSTOM_TOKEN_AUDIENCE)
            .put("iss", CUSTOM_TOKEN_ISSUER)
            .put("sub", CUSTOM_TOKEN_ISSUER)
    return "${encode(header)}.${encode(payload)}."
  }

  /**
   * Writes [data] (a map or a data class, converted like `DocumentReference.set` does) at the
   * document [documentPath], bypassing the security rules. For test setup only: the code under test
   * must go through a [signInAs] instance so the rules apply to it.
   */
  suspend fun writeBypassingRules(firestore: FirebaseFirestore, documentPath: String, data: Any) {
    val fields =
        toRestValue(CustomClassMapper.convertToPlainJavaTypes(data)).getJSONObject("mapValue")
    val write =
        JSONObject().put("update", fields.put("name", documentName(firestore, documentPath)))
    commitAsOwner(firestore, listOf(write))
  }

  /**
   * Makes [userId] a member of [groupIds] for the rules, which read `users/{uid}.groupIds`, by
   * writing that document bypassing the rules. For test setup only.
   */
  suspend fun writeUserGroupIdsBypassingRules(
      firestore: FirebaseFirestore,
      userId: String,
      vararg groupIds: String,
  ) = writeBypassingRules(firestore, "users/$userId", mapOf("groupIds" to groupIds.toList()))

  /**
   * The `groupIds` field of `users/[userId]`, read as that user so the rules apply; null when the
   * document or the field is missing.
   */
  suspend fun userGroupIds(userId: String): Any? =
      signInAs(userId).collection("users").document(userId).get().await().get("groupIds")

  /**
   * Runs [action] (a Firestore request made through a [signInAs] instance, or through the
   * [signedOutFirestore] instance) and fails unless the security rules reject it with
   * PERMISSION_DENIED. Any other outcome fails: success, another Firestore error code (e.g.
   * NOT_FOUND), or another exception (e.g. an invalid id), so a request that fails for an unrelated
   * reason never passes as a rules denial. [case] names the request in the failure message, for
   * tests that check several requests in a row.
   */
  suspend fun assertPermissionDenied(case: String = "request", action: suspend () -> Unit) {
    val failure =
        try {
          action()
          null
        } catch (e: CancellationException) {
          throw e
        } catch (e: Exception) {
          e
        }
    if (
        failure !is FirebaseFirestoreException ||
            failure.code != FirebaseFirestoreException.Code.PERMISSION_DENIED
    ) {
      throw AssertionError(
          "$case: expected PERMISSION_DENIED from the security rules, got: ${failure ?: "success"}",
          failure,
      )
    }
  }

  /**
   * Ids of the documents of [collectionPath], in id order, bypassing the security rules. For test
   * checks on data that no client may list (e.g. invitations, redemptions).
   */
  suspend fun documentIdsBypassingRules(
      firestore: FirebaseFirestore,
      collectionPath: String,
  ): List<String> = documentNames(firestore, collectionPath).map { it.substringAfterLast('/') }

  // Full resource names of the documents of a collection, listed as the emulator's owner.
  private suspend fun documentNames(
      firestore: FirebaseFirestore,
      collectionPath: String,
  ): List<String> {
    val names = mutableListOf<String>()
    var pageToken = ""
    // The emulator returns the listing in pages, whatever the page size asked: follow them all.
    do {
      val url =
          "${restBase(firestore)}/$collectionPath?pageSize=$MAX_LISTED_DOCUMENTS" +
              if (pageToken.isEmpty()) "" else "&pageToken=$pageToken"
      val listing = JSONObject(checkNotNull(ownerRequest("GET", url)))
      val documents = listing.optJSONArray("documents") ?: JSONArray()
      (0 until documents.length()).mapTo(names) { documents.getJSONObject(it).getString("name") }
      pageToken = listing.optString("nextPageToken")
    } while (pageToken.isNotEmpty())
    return names
  }

  // Full resource name of a document, as the REST API expects it.
  private fun documentName(firestore: FirebaseFirestore, documentPath: String) =
      "projects/${firestore.app.options.projectId}/databases/(default)/documents/$documentPath"

  // Converts a plain Java value (output of CustomClassMapper) to a Firestore REST typed value.
  private fun toRestValue(value: Any?): JSONObject =
      when (value) {
        null -> JSONObject().put("nullValue", JSONObject.NULL)
        is Boolean -> JSONObject().put("booleanValue", value)
        is Int,
        is Long -> JSONObject().put("integerValue", value.toString())
        is Float,
        is Double -> {
          val number = (value as Number).toDouble()
          // JSON has no NaN or Infinity: the REST API takes them as strings ("NaN", "Infinity").
          JSONObject().put("doubleValue", if (number.isFinite()) number else number.toString())
        }
        is String -> JSONObject().put("stringValue", value)
        is Timestamp ->
            JSONObject()
                .put(
                    "timestampValue",
                    Instant.ofEpochSecond(value.seconds, value.nanoseconds.toLong()).toString(),
                )
        is Date -> JSONObject().put("timestampValue", value.toInstant().toString())
        is GeoPoint ->
            JSONObject()
                .put(
                    "geoPointValue",
                    JSONObject().put("latitude", value.latitude).put("longitude", value.longitude),
                )
        is List<*> ->
            JSONObject()
                .put("arrayValue", JSONObject().put("values", JSONArray(value.map(::toRestValue))))
        is Map<*, *> -> {
          val fields = JSONObject()
          value.forEach { (key, item) -> fields.put(key as String, toRestValue(item)) }
          JSONObject().put("mapValue", JSONObject().put("fields", fields))
        }
        else -> throw IllegalArgumentException("Cannot write a value of type ${value::class}")
      }

  /**
   * Reads the document [documentPath] as [type] (a map or a data class, converted like
   * `DocumentSnapshot.toObject` does), bypassing the security rules, or null if it does not exist.
   * For test checks on what is really stored, whoever may read it.
   */
  suspend fun <T> readBypassingRules(
      firestore: FirebaseFirestore,
      documentPath: String,
      type: Class<T>,
  ): T? {
    val response =
        ownerRequest("GET", "${restBase(firestore)}/$documentPath", allowNotFound = true)
            ?: return null
    val fields = JSONObject(response).optJSONObject("fields") ?: JSONObject()
    val data = fields.keys().asSequence().associateWith { fromRestValue(fields.getJSONObject(it)) }
    // CustomClassMapper refuses the raw Map class, so a map is returned as it is.
    @Suppress("UNCHECKED_CAST") if (type == Map::class.java) return data as T
    return CustomClassMapper.convertToCustomClass(data, type, firestore.document(documentPath))
  }

  // Converts a Firestore REST typed value back to a plain Java value (inverse of toRestValue).
  private fun fromRestValue(value: JSONObject): Any? =
      when {
        value.has("booleanValue") -> value.getBoolean("booleanValue")
        value.has("integerValue") -> value.getString("integerValue").toLong()
        value.has("doubleValue") -> value.get("doubleValue").toString().toDouble()
        value.has("stringValue") -> value.getString("stringValue")
        value.has("timestampValue") ->
            Instant.parse(value.getString("timestampValue")).let {
              Timestamp(it.epochSecond, it.nano)
            }
        value.has("geoPointValue") ->
            value.getJSONObject("geoPointValue").let {
              GeoPoint(it.optDouble("latitude", 0.0), it.optDouble("longitude", 0.0))
            }
        value.has("arrayValue") -> {
          val values = value.getJSONObject("arrayValue").optJSONArray("values") ?: JSONArray()
          (0 until values.length()).map { fromRestValue(values.getJSONObject(it)) }
        }
        value.has("mapValue") -> {
          val fields = value.getJSONObject("mapValue").optJSONObject("fields") ?: JSONObject()
          fields.keys().asSequence().associateWith { fromRestValue(fields.getJSONObject(it)) }
        }
        else -> null // nullValue
      }

  // Sends [writes] in one commit with the emulator's "owner" token, which bypasses the rules.
  private suspend fun commitAsOwner(firestore: FirebaseFirestore, writes: List<JSONObject>) {
    if (writes.isEmpty()) return
    val body = JSONObject().put("writes", JSONArray(writes)).toString()
    ownerRequest("POST", "${restBase(firestore)}:commit", body)
  }

  // Base URL of the REST API of the emulator for the project of [firestore].
  private fun restBase(firestore: FirebaseFirestore) =
      "http://$EMULATOR_HOST:$EMULATOR_PORT/v1/${documentName(firestore, "").trimEnd('/')}"

  // Sends one HTTP request as the emulator's "owner" and returns the response body; throws on
  // error, except a 404 when [allowNotFound], which returns null.
  private suspend fun ownerRequest(
      method: String,
      url: String,
      body: String? = null,
      allowNotFound: Boolean = false,
  ): String? =
      withContext(Dispatchers.IO) {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
          connection.requestMethod = method
          connection.setRequestProperty("Authorization", "Bearer owner")
          if (body != null) {
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.use { it.write(body.toByteArray()) }
          }
          val code = connection.responseCode
          if (allowNotFound && code == HttpURLConnection.HTTP_NOT_FOUND) return@withContext null
          val stream = if (code in 200..299) connection.inputStream else connection.errorStream
          val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
          check(code in 200..299) { "Emulator request $method $url failed ($code): $response" }
          response
        } finally {
          connection.disconnect()
        }
      }

  /**
   * Returns the default app's Firestore on the emulator with no user signed in, for tests of what
   * the rules refuse to signed-out clients. FirebaseAuth tells Firestore about the sign-out through
   * the main looper, which a runBlocking test does not run: the looper is run here, otherwise the
   * next request could still carry the token of a user signed in by an earlier test.
   */
  fun signedOutFirestore(): FirebaseFirestore {
    getAuth()
    shadowOf(Looper.getMainLooper()).idle()
    return getFirestore()
  }

  /**
   * Returns the default app's FirebaseAuth pointed to the Auth emulator and signed out. The default
   * app is used because the app code reads FirebaseAuth.getInstance(). The default app's Firestore
   * is pointed to the emulator too, so app code started by the test (e.g. JDRNexusApp) never
   * reaches the real project.
   */
  fun getAuth(): FirebaseAuth {
    val context = ApplicationProvider.getApplicationContext<Context>()
    if (FirebaseApp.getApps(context).isEmpty()) FirebaseApp.initializeApp(context)
    emulatorFirestore(FirebaseApp.getInstance())
    return FirebaseAuth.getInstance().apply {
      useEmulator(EMULATOR_HOST, AUTH_EMULATOR_PORT)
      signOut()
    }
  }

  /**
   * To call from @After. Re-enables the network (a test may have disabled it), then empties the
   * given collections. Paths can be nested, e.g. "campaign_groups/<id>/dice_rolls". Throws if the
   * network cannot be re-enabled, so that the failure is visible here and not in the next tests of
   * the suite. Finally empties the local cache of every test client (see [clearClientCaches]).
   */
  suspend fun tearDown(firestore: FirebaseFirestore, vararg collectionPaths: String) {
    firestore.enableNetwork().await()
    collectionPaths.forEach { clearCollection(firestore, it) }
    clearClientCaches()
  }

  /**
   * Empties the local cache of every emulator client. The clients are reused from one test to the
   * next, and data written or deleted bypassing the rules never reaches their cache, so a listener
   * could first emit documents of an earlier test. The next [signInAs] gets a fresh instance.
   */
  private suspend fun clearClientCaches() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    FirebaseApp.getApps(context)
        .filter { it.name.startsWith(EMULATOR_APP_NAME) }
        .forEach { app ->
          val instance = FirebaseFirestore.getInstance(app)
          instance.terminate().await()
          instance.clearPersistence().await()
        }
  }

  /**
   * Deletes every document of a collection (in batches), bypassing the security rules so a test can
   * clean data that its users may not delete. Never throws: cleanup is best effort.
   */
  suspend fun clearCollection(firestore: FirebaseFirestore, collectionPath: String) {
    try {
      val deletes = documentNames(firestore, collectionPath).map { JSONObject().put("delete", it) }
      deletes.chunked(MAX_BATCH_SIZE).forEach { commitAsOwner(firestore, it) }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.w(TAG, "Cleanup of '$collectionPath' failed", e)
    }
  }

  /**
   * Suspends until [condition] is true, while actively pumping the Robolectric main looper.
   * Firebase callbacks are dispatched on the Android main thread, which would otherwise stay
   * blocked in a runBlocking test. Throws an AssertionError after [timeoutMs].
   */
  suspend fun pumpMainLooperUntil(
      timeoutMs: Long = DEFAULT_TIMEOUT_MS,
      timeoutMessage: () -> String = { "Timeout waiting for condition." },
      condition: suspend () -> Boolean,
  ) {
    val startTime = System.currentTimeMillis()
    while (System.currentTimeMillis() - startTime < timeoutMs) {
      shadowOf(Looper.getMainLooper()).idle()
      if (condition()) return
      delay(50)
    }
    throw AssertionError(timeoutMessage())
  }
}

/**
 * Collects this Flow in a background coroutine while pumping the Robolectric main looper, and
 * returns the first value that satisfies [condition]. If the Flow throws (e.g. PERMISSION_DENIED),
 * the error is rethrown immediately.
 *
 * Atomic properties guarantee memory visibility between the collector (Dispatchers.Default) and the
 * test thread.
 */
suspend fun <T> Flow<T>.waitFor(
    timeoutMs: Long = FirebaseEmulatorTestUtils.DEFAULT_TIMEOUT_MS,
    condition: (T) -> Boolean,
): T {
  val latestValue = AtomicReference<T?>(null)
  val matchedValue = AtomicReference<T?>(null)
  val hasValue = AtomicBoolean(false)
  val isReady = AtomicBoolean(false)
  val errorRef = AtomicReference<Throwable?>(null)

  val job =
      CoroutineScope(Dispatchers.Default).launch {
        try {
          this@waitFor.collect {
            latestValue.set(it)
            hasValue.set(true)
            if (!isReady.get() && condition(it)) {
              matchedValue.set(it)
              isReady.set(true)
            }
          }
        } catch (e: Throwable) {
          errorRef.set(e)
        }
      }
  try {
    FirebaseEmulatorTestUtils.pumpMainLooperUntil(
        timeoutMs = timeoutMs,
        timeoutMessage = {
          "Timeout waiting for condition. Last value emitted: ${latestValue.get()}, " +
              "emitted at all: ${hasValue.get()}"
        },
    ) {
      errorRef.get()?.let { throw it }
      isReady.get()
    }
    @Suppress("UNCHECKED_CAST")
    return matchedValue.get() as T
  } finally {
    job.cancel()
  }
}
