// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.utils

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject

/**
 * Helpers for instrumented Storage tests on the emulators of ci.yml: Storage clients signed in as
 * any user, and Firestore writes that bypass the rules. A small mirror of
 * `FirebaseEmulatorTestUtils`, which androidTest cannot see (TESTING.md 4.2).
 */
object StorageEmulatorTestUtils {

  /** Longest wait for one Firebase call, so a test fails instead of hanging. */
  const val TIMEOUT_MS = 20_000L

  // The Storage rules read the Firestore data of the --project id, not of google-services.json.
  private const val RULES_PROJECT_ID = "demo-jdrnexus"
  // The Android emulator reaches the computer's localhost at 10.0.2.2.
  private const val HOST = "10.0.2.2"
  private const val ISSUER = "test@emulator.iam.gserviceaccount.com"
  private const val AUDIENCE =
      "https://identitytoolkit.googleapis.com/google.identity.identitytoolkit.v1.IdentityToolkit"

  private val storages = mutableMapOf<String, FirebaseStorage>()

  /** A Storage client signed in as [uid]; each user has their own FirebaseApp. */
  @Synchronized
  fun storageAs(uid: String): FirebaseStorage =
      storages.getOrPut(uid) {
        val app = emulatorApp("storage-$uid")
        val auth = FirebaseAuth.getInstance(app).apply { useEmulator(HOST, 9099) }
        runBlocking { auth.signInWithCustomToken(unsignedToken(uid)).awaitWithTimeout() }
        emulatorStorage(app)
      }

  /** A Storage client with nobody signed in. */
  @Synchronized
  fun signedOutStorage(): FirebaseStorage =
      storages.getOrPut("") { emulatorStorage(emulatorApp("storage-signed-out")) }

  private fun emulatorApp(name: String): FirebaseApp {
    val context = ApplicationProvider.getApplicationContext<Context>()
    if (FirebaseApp.getApps(context).isEmpty()) FirebaseApp.initializeApp(context)
    return FirebaseApp.getApps(context).firstOrNull { it.name == name }
        ?: FirebaseApp.initializeApp(context, FirebaseApp.getInstance().options, name)
  }

  private fun emulatorStorage(app: FirebaseApp) =
      FirebaseStorage.getInstance(app).apply { useEmulator(HOST, 9199) }

  // The Auth emulator accepts unsigned custom tokens, so a test can choose the uid.
  private fun unsignedToken(uid: String): String {
    fun part(json: JSONObject) =
        Base64.getUrlEncoder().withoutPadding().encodeToString(json.toString().toByteArray())
    val now = System.currentTimeMillis() / 1000
    val claims =
        JSONObject()
            .put("uid", uid)
            .put("iat", now)
            .put("exp", now + 3600)
            .put("aud", AUDIENCE)
            .put("iss", ISSUER)
            .put("sub", ISSUER)
    return "${part(JSONObject().put("alg", "none").put("typ", "JWT"))}.${part(claims)}."
  }

  /** Awaits this task, failing after [TIMEOUT_MS] instead of hanging the test run. */
  suspend fun <T> Task<T>.awaitWithTimeout(): T = withTimeout(TIMEOUT_MS) { await() }

  /**
   * The [StorageException] code thrown by [action], or null if it threw nothing or another type.
   */
  suspend fun storageErrorCode(action: suspend () -> Any?): Int? =
      try {
        action()
        null
      } catch (e: CancellationException) {
        throw e
      } catch (e: StorageException) {
        e.errorCode
      } catch (_: Exception) {
        null
      }

  /**
   * Writes the document [path] with [fields] (strings or string lists), bypassing the rules, in the
   * project the Storage rules read. For test setup only.
   */
  suspend fun writeBypassingRules(path: String, fields: Map<String, Any>) {
    val project = "projects/$RULES_PROJECT_ID/databases/(default)/documents"
    val values = JSONObject()
    fields.forEach { (key, value) -> values.put(key, restValue(value)) }
    val write =
        JSONObject().put("update", JSONObject().put("name", "$project/$path").put("fields", values))
    ownerPost(
        "http://$HOST:8080/v1/$project:commit",
        JSONObject().put("writes", JSONArray(listOf(write))),
    )
  }

  /** Makes [userId] a member of [groupIds] for the Storage rules (`users/{uid}.groupIds`). */
  suspend fun writeUserGroupIdsBypassingRules(userId: String, vararg groupIds: String) =
      writeBypassingRules("users/$userId", mapOf("groupIds" to groupIds.toList()))

  private fun restValue(value: Any): JSONObject =
      when (value) {
        is String -> JSONObject().put("stringValue", value)
        is List<*> ->
            JSONObject()
                .put(
                    "arrayValue",
                    JSONObject().put("values", JSONArray(value.map { restValue(it!!) })),
                )
        else -> throw IllegalArgumentException("Unsupported value $value")
      }

  // One POST as the emulator's "owner", which bypasses the rules; throws on error.
  private suspend fun ownerPost(url: String, body: JSONObject) =
      withContext(Dispatchers.IO) {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
          connection.requestMethod = "POST"
          connection.connectTimeout = TIMEOUT_MS.toInt()
          connection.readTimeout = TIMEOUT_MS.toInt()
          connection.setRequestProperty("Authorization", "Bearer owner")
          connection.setRequestProperty("Content-Type", "application/json")
          connection.doOutput = true
          connection.outputStream.use { it.write(body.toString().toByteArray()) }
          val code = connection.responseCode
          check(code in 200..299) { "Emulator POST $url failed ($code)" }
        } finally {
          connection.disconnect()
        }
      }
}
