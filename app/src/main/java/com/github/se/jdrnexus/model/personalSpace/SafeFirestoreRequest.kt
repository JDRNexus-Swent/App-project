package com.github.se.jdrnexus.model.personalSpace

import kotlin.coroutines.cancellation.CancellationException

/**
 * Runs [action] and wraps its outcome in a [Result].
 *
 * Unlike `runCatching`, a [CancellationException] is rethrown: swallowing it would break structured
 * concurrency (a cancelled coroutine would keep running as if nothing happened).
 */
internal suspend fun <T> safeFirestoreRequest(action: suspend () -> T): Result<T> =
    try {
      Result.success(action())
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Result.failure(e)
    }
