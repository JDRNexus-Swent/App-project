// Co-authored-by: Claude Sonnet 5.5
package com.github.se.jdrnexus.utils

import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.robolectric.Shadows.shadowOf

/**
 * Shared helpers for every test class that talks to the Firestore emulator.
 *
 * Those tests need the emulator started with: firebase emulators:exec --config firebase.test.json
 */
object FirebaseEmulatorTestUtils {

  /** Maximum time to wait for asynchronous Firebase operations in tests. */
  const val DEFAULT_TIMEOUT_MS = 5000L

  private const val EMULATOR_APP_NAME = "EmulatorApp"
  private const val EMULATOR_HOST = "127.0.0.1"
  private const val EMULATOR_PORT = 8080

  /** Firestore refuses batches of more than 500 writes. */
  private const val MAX_BATCH_SIZE = 500

  /**
   * Returns a Firestore instance that is guaranteed to point to the emulator. Safe to call from
   * every @Before: the FirebaseApp and its configuration are created only once per JVM.
   */
  fun getFirestore(): FirebaseFirestore {
    val context = ApplicationProvider.getApplicationContext<Context>()

    // Make sure the default Firebase app exists, we need its options to clone it
    if (FirebaseApp.getApps(context).isEmpty()) {
      FirebaseApp.initializeApp(context)
    }

    val emulatorApp =
        FirebaseApp.getApps(context).firstOrNull { it.name == EMULATOR_APP_NAME }
            ?: FirebaseApp.initializeApp(
                context,
                FirebaseApp.getInstance().options,
                EMULATOR_APP_NAME,
            )

    val firestore = FirebaseFirestore.getInstance(emulatorApp)

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
   * To call from @After. Re-enables the network (a test may have disabled it), then empties the
   * given collections. Paths can be nested, e.g. "campaign_groups/<id>/dice_rolls".
   */
  suspend fun tearDown(firestore: FirebaseFirestore, vararg collectionPaths: String) {
    try {
      firestore.enableNetwork().await()
    } catch (_: Exception) {
      // ignore
    }
    collectionPaths.forEach { clearCollection(firestore, it) }
  }

  /** Deletes every document of a collection (in batches). Never throws: cleanup is best effort. */
  suspend fun clearCollection(firestore: FirebaseFirestore, collectionPath: String) {
    try {
      val snapshot = firestore.collection(collectionPath).get().await()
      snapshot.documents.chunked(MAX_BATCH_SIZE).forEach { chunk ->
        val batch = firestore.batch()
        chunk.forEach { batch.delete(it.reference) }
        batch.commit().await()
      }
    } catch (e: Exception) {
      println("Cleanup of '$collectionPath' failed: $e")
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
  val hasValue = AtomicBoolean(false)
  val isReady = AtomicBoolean(false)
  val errorRef = AtomicReference<Throwable?>(null)

  val job =
      CoroutineScope(Dispatchers.Default).launch {
        try {
          this@waitFor.collect {
            latestValue.set(it)
            hasValue.set(true)
            if (condition(it)) isReady.set(true)
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
    return latestValue.get() as T
  } finally {
    job.cancel()
  }
}
