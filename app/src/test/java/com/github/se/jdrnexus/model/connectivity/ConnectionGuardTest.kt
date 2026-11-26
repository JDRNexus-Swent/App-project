// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.model.connectivity

import com.github.se.jdrnexus.utils.FakeConnectivityRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConnectionGuardTest {

  @Test
  fun failWhenOfflineReturnsTheActionResultWhileOnline() = runTest {
    val connectivity = FakeConnectivityRepository(isOnline = true)

    val result = connectivity.isOnline.failWhenOffline { "saved" }

    assertEquals("saved", result)
  }

  @Test
  fun failWhenOfflineNeverRunsTheActionWhenAlreadyOffline() = runTest {
    val connectivity = FakeConnectivityRepository(isOnline = false)
    var actionRan = false

    val failure = runCatching {
      connectivity.isOnline.failWhenOffline { actionRan = true }
    }
        .exceptionOrNull()

    assertEquals(OfflineException::class, failure?.let { it::class })
    assertEquals(NEEDS_CONNECTION, failure?.message)
    assertFalse(actionRan)
  }

  @Test
  fun failWhenOfflineStopsTheActionWhenTheConnectionDrops() = runTest {
    val connectivity = FakeConnectivityRepository(isOnline = true)
    val serverAnswer = CompletableDeferred<String>()
    val call = async {
      runCatching { connectivity.isOnline.failWhenOffline { serverAnswer.await() } }
    }
    runCurrent()

    connectivity.online.value = false
    runCurrent()

    assertTrue(call.isCompleted)
    val failure = call.await().exceptionOrNull()
    assertEquals(OfflineException::class, failure?.let { it::class })
  }

  @Test
  fun failWhenOfflineKeepsRunningWhenTheConnectionFlowEndsWhileOnline() = runTest {
    val serverAnswer = CompletableDeferred<String>()
    val call = async { runCatching { flowOf(true).failWhenOffline { serverAnswer.await() } } }
    runCurrent()

    serverAnswer.complete("saved")
    runCurrent()

    assertEquals(Result.success("saved"), call.await())
  }

  @Test
  fun failWhenOfflinePropagatesTheActionFailure() = runTest {
    val connectivity = FakeConnectivityRepository(isOnline = true)

    val failure = runCatching {
      connectivity.isOnline.failWhenOffline { throw IllegalArgumentException("Refused") }
    }
        .exceptionOrNull()

    assertEquals(IllegalArgumentException::class, failure?.let { it::class })
    assertEquals("Refused", failure?.message)
  }

  @Test
  fun failWhenOfflineIgnoresADropAfterTheActionEnded() = runTest {
    val connectivity = FakeConnectivityRepository(isOnline = true)

    val result = connectivity.isOnline.failWhenOffline { 42 }
    connectivity.online.value = false
    runCurrent()

    assertEquals(42, result)
  }

  @Test
  fun isOnlineInFollowsTheConnectionWhileCollected() = runTest {
    val connectivity = FakeConnectivityRepository(isOnline = true)
    val isOnline = connectivity.isOnlineIn(backgroundScope)
    backgroundScope.launch { isOnline.collect {} }
    runCurrent()

    connectivity.online.value = false
    runCurrent()
    val offline = isOnline.value
    connectivity.online.value = true
    runCurrent()

    assertEquals(listOf(false, true), listOf(offline, isOnline.value))
  }

  @Test
  fun isOnlineInStartsOnlineBeforeTheFirstValue() = runTest {
    val connectivity = FakeConnectivityRepository(isOnline = false)

    val isOnline = connectivity.isOnlineIn(backgroundScope)

    assertTrue(isOnline.value)
  }
}
