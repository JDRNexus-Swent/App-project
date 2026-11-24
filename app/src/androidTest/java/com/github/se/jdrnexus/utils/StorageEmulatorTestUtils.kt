// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.utils

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.util.CustomClassMapper
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
 * Helpers for the instrumented tests (`app/src/androidTest`) of the Firebase Storage security
 * rules, on the emulators started by `firebase emulators:exec --only auth,firestore,storage …
 * --project demo-jdrnexus` (ci.yml).
 *
 * It mirrors the parts of the unit-test helper `FirebaseEmulatorTestUtils`
 * (`app/src/test/.../utils`) that these tests need: a Storage client signed in as a chosen user,
 * and Firestore writes that bypass the security rules. Instrumented tests cannot call that helper
 * because they cannot see the `app/src/test` source set, and it runs under Robolectric, which the
 * Storage SDK does not support (TESTING.md, section 4.2).
 */
object StorageEmulatorTestUtils {

  /** Longest wait for one Firebase call, so a test fails instead of hanging. */
  const val TIMEOUT_MS = 20_000L

  /**
   * Project whose Firestore data the Storage emulator reads when a rule calls `firestore.get` or
   * `firestore.exists`: the project given to `--project`, not the project of google-services.json
   * that the app's Firestore client uses. Data the Storage rules must see is written here.
   */
  private const val RULES_PROJECT_ID = "demo-jdrnexus"

  // The Android emulator reaches the computer's localhost at 10.0.2.2.
  private const val EMULATOR_HOST = "10.0.2.2"
  private const val AUTH_PORT = 9099
  private const val FIRESTORE_PORT = 8080
  private const val STORAGE_PORT = 9199
  private const val APP_NAME_PREFIX = "StorageRulesApp"

  /** Claims the Auth emulator expects in a custom token (its signature is not checked). */
  private const val CUSTOM_TOKEN_AUDIENCE =
      "https://identitytoolkit.googleapis.com/google.identity.identitytoolkit.v1.IdentityToolkit"
  private const val CUSTOM_TOKEN_ISSUER = "test@emulator.iam.gserviceaccount.com"

  private val storages = mutableMapOf<String, FirebaseStorage>()

  /**
   * A Storage client on the emulator whose requests come from the user [uid], so the rules see
   * `request.auth.uid == uid`. Each user has their own FirebaseApp, which lets one test act as
   * several users at once.
   */
  @Synchronized
  fun storageAs(uid: String): FirebaseStorage =
      storages.getOrPut(uid) {
        val app = emulatorApp("$APP_NAME_PREFIX-$uid")
        val auth = FirebaseAuth.getInstance(app).apply { useEmulator(EMULATOR_HOST, AUTH_PORT) }
        runBlocking { auth.signInWithCustomToken(unsignedCustomToken(uid)).awaitWithTimeout() }
        emulatorStorage(app)
      }

  /** A Storage client on the emulator with no user signed in. */
  @Synchronized
  fun signedOutStorage(): FirebaseStorage =
      storages.getOrPut("") { emulatorStorage(emulatorApp("$APP_NAME_PREFIX-signed-out")) }

  private fun emulatorApp(name: String): FirebaseApp {
    val context = ApplicationProvider.getApplicationContext<Context>()
    if (FirebaseApp.getApps(context).isEmpty()) FirebaseApp.initializeApp(context)
    return FirebaseApp.getApps(context).firstOrNull { it.name == name }
        ?: FirebaseApp.initializeApp(context, FirebaseApp.getInstance().options, name)
  }

  private fun emulatorStorage(app: FirebaseApp) =
      FirebaseStorage.getInstance(app).apply { useEmulator(EMULATOR_HOST, STORAGE_PORT) }

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

  /** Awaits this task, failing after [TIMEOUT_MS] instead of hanging the test run. */
  suspend fun <T> Task<T>.awaitWithTimeout(): T = withTimeout(TIMEOUT_MS) { await() }

  /**
   * The [StorageException.getErrorCode] thrown by [action], or null if it succeeded or threw
   * something else.
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
   * Writes [data] (a map or a data class, converted like `DocumentReference.set` does) at the
   * Firestore document [documentPath] of the project the Storage rules read, bypassing the
   * Firestore rules. For test setup only.
   */
  suspend fun writeBypassingRules(documentPath: String, data: Any) {
    val fields =
        toRestValue(CustomClassMapper.convertToPlainJavaTypes(data)).getJSONObject("mapValue")
    val name = "projects/$RULES_PROJECT_ID/databases/(default)/documents/$documentPath"
    val write = JSONObject().put("update", fields.put("name", name))
    val body = JSONObject().put("writes", JSONArray(listOf(write))).toString()
    ownerPost(
        "http://$EMULATOR_HOST:$FIRESTORE_PORT/v1/projects/$RULES_PROJECT_ID/databases/(default)/documents:commit",
        body,
    )
  }

  /**
   * Makes [userId] a member of [groupIds] for the Storage rules, which read `users/{uid}.groupIds`,
   * by writing that document bypassing the rules. For test setup only.
   */
  suspend fun writeUserGroupIdsBypassingRules(userId: String, vararg groupIds: String) =
      writeBypassingRules("users/$userId", mapOf("groupIds" to groupIds.toList()))

  // Converts a plain Java value (output of CustomClassMapper) to a Firestore REST typed value.
  private fun toRestValue(value: Any?): JSONObject =
      when (value) {
        null -> JSONObject().put("nullValue", JSONObject.NULL)
        is Boolean -> JSONObject().put("booleanValue", value)
        is Int,
        is Long -> JSONObject().put("integerValue", value.toString())
        is String -> JSONObject().put("stringValue", value)
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

  // Sends one POST as the emulator's "owner", which bypasses the rules; throws on error.
  private suspend fun ownerPost(url: String, body: String) =
      withContext(Dispatchers.IO) {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
          connection.requestMethod = "POST"
          connection.connectTimeout = TIMEOUT_MS.toInt()
          connection.readTimeout = TIMEOUT_MS.toInt()
          connection.setRequestProperty("Authorization", "Bearer owner")
          connection.setRequestProperty("Content-Type", "application/json")
          connection.doOutput = true
          connection.outputStream.use { it.write(body.toByteArray()) }
          val code = connection.responseCode
          val stream = if (code in 200..299) connection.inputStream else connection.errorStream
          val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
          check(code in 200..299) { "Emulator request POST $url failed ($code): $response" }
        } finally {
          connection.disconnect()
        }
      }
}
