// Co-authored-by: OpenAI Codex
package com.github.se.jdrnexus.model.map

import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import androidx.core.location.LocationManagerCompat
import androidx.core.util.Consumer
import io.mockk.*
import java.io.IOException
import java.util.Locale
import java.util.concurrent.Executor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class MapLocationRepositoryAndroidTest {
  private val geocoder = mockk<Geocoder>()
  private val manager = mockk<LocationManager>()
  private val executor = Executor { it.run() }
  private val repository by lazy { MapLocationRepositoryAndroid(geocoder, manager, executor) }

  @Before
  fun setUp() {
    mockkStatic(LocationManagerCompat::class)
  }

  @After
  fun tearDown() {
    unmockkStatic(LocationManagerCompat::class)
  }

  @Test
  fun citySearchTrimsQueryAndReturnsCoordinates() = runTest {
    val address =
        Address(Locale.ENGLISH).apply {
          latitude = 46.52
          longitude = 6.63
          locality = "Lausanne"
        }
    every { geocoder.getFromLocationName("Lausanne", 1) } returns listOf(address)
    assertEquals(MapLocation(46.52, 6.63, "Lausanne"), repository.searchCity("  Lausanne  "))
  }

  @Test
  fun blankAndUnmatchedCitiesReturnNoLocation() = runTest {
    assertNull(repository.searchCity(" "))
    every { geocoder.getFromLocationName("missing", 1) } returns emptyList()
    assertNull(repository.searchCity("missing"))
    every { geocoder.getFromLocationName("missing", 1) } returns null
    assertNull(repository.searchCity("missing"))
  }

  @Test
  fun cityUsesQueryWhenProviderHasNoLabel() = runTest {
    val address =
        Address(Locale.ENGLISH).apply {
          latitude = 1.0
          longitude = 2.0
        }
    every { geocoder.getFromLocationName("City", 1) } returns listOf(address)
    assertEquals("City", repository.searchCity("City")?.label)
  }

  @Test
  fun cityWithoutCoordinatesReturnsNoLocation() = runTest {
    val address = Address(Locale.ENGLISH).apply { locality = "Nowhere" }
    every { geocoder.getFromLocationName("Nowhere", 1) } returns listOf(address)
    assertNull(repository.searchCity("Nowhere"))
  }

  @Test
  fun cityProviderFailureIsPropagated() = runTest {
    every { geocoder.getFromLocationName(any(), 1) } throws IOException("offline")
    assertEquals("offline", assertFails<IOException> { repository.searchCity("City") }.message)
  }

  private fun providers(network: Boolean = true, gps: Boolean = true) {
    every { manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) } returns network
    every { manager.isProviderEnabled(LocationManager.GPS_PROVIDER) } returns gps
  }

  private fun locationResult(provider: String, location: Location?) {
    every {
      LocationManagerCompat.getCurrentLocation(
          manager,
          provider,
          any<CancellationSignal>(),
          executor,
          any(),
      )
    } answers { lastArg<Consumer<Location?>>().accept(location) }
  }

  @Test
  fun currentNetworkLocationIsReturned() = runTest {
    providers()
    locationResult(
        LocationManager.NETWORK_PROVIDER,
        Location("network").apply {
          latitude = 46.5
          longitude = 6.6
        },
    )
    assertEquals(MapLocation(46.5, 6.6), repository.getDeviceLocation())
  }

  @Test
  fun gpsIsUsedWhenNetworkIsDisabled() = runTest {
    providers(network = false)
    locationResult(
        LocationManager.GPS_PROVIDER,
        Location("gps").apply {
          latitude = 1.0
          longitude = 2.0
        },
    )
    assertEquals(MapLocation(1.0, 2.0), repository.getDeviceLocation())
  }

  @Test
  fun gpsIsUsedWhenNetworkHasNoFix() = runTest {
    providers()
    locationResult(LocationManager.NETWORK_PROVIDER, null)
    locationResult(
        LocationManager.GPS_PROVIDER,
        Location("gps").apply {
          latitude = 1.0
          longitude = 2.0
        },
    )
    assertEquals(MapLocation(1.0, 2.0), repository.getDeviceLocation())
  }

  @Test
  fun disabledProvidersAreAnError() = runTest {
    providers(false, false)
    assertEquals(
        "Enable device location to find nearby games.",
        assertFails<IOException> { repository.getDeviceLocation() }.message,
    )
  }

  @Test
  fun noFixFromAnyProviderIsAnError() = runTest {
    providers()
    locationResult(LocationManager.NETWORK_PROVIDER, null)
    locationResult(LocationManager.GPS_PROVIDER, null)
    assertEquals(
        UNAVAILABLE_MESSAGE,
        assertFails<IOException> { repository.getDeviceLocation() }.message,
    )
  }

  @Test
  fun revokedPermissionIsPropagated() = runTest {
    providers()
    every {
      LocationManagerCompat.getCurrentLocation(
          manager,
          any(),
          any<CancellationSignal>(),
          executor,
          any(),
      )
    } throws SecurityException("denied")
    assertEquals(
        "denied",
        assertFails<SecurityException> { repository.getDeviceLocation() }.message,
    )
  }

  @Test
  fun cancellingRequestCancelsNativeLocationLookup() = runTest {
    providers()
    val signal = slot<CancellationSignal>()
    every {
      LocationManagerCompat.getCurrentLocation(manager, any(), capture(signal), executor, any())
    } just Runs
    val request = async { repository.getDeviceLocation() }
    runCurrent()
    request.cancel()
    request.join()
    assertTrue(signal.captured.isCanceled)
  }

  @Test
  fun missingCallbacksTimeOutOnEveryProviderAndCancelNativeRequests() = runTest {
    providers()
    val signals = mutableListOf<CancellationSignal>()
    every {
      LocationManagerCompat.getCurrentLocation(manager, any(), capture(signals), executor, any())
    } just Runs
    assertEquals(
        UNAVAILABLE_MESSAGE,
        assertFails<IOException> { repository.getDeviceLocation() }.message,
    )
    assertEquals(2, signals.size)
    assertTrue(signals.all { it.isCanceled })
  }

  private companion object {
    const val UNAVAILABLE_MESSAGE = "Device location is unavailable. Try again."
  }

  /** Returns the [T] thrown by [block]; inline so [block] may call suspend functions. */
  private inline fun <reified T : Throwable> assertFails(block: () -> Unit): T {
    val error = runCatching(block).exceptionOrNull()
    assertTrue("Expected ${T::class.simpleName} but got $error", error is T)
    return error as T
  }
}
