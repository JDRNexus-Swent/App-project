// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.model.connectivity

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowNetwork
import org.robolectric.shadows.ShadowNetworkCapabilities

/**
 * [ConnectivityRepositoryAndroid] on Robolectric's [ConnectivityManager]: the tests set the active
 * network's capabilities and call the registered callback by hand, as Android would.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class ConnectivityRepositoryAndroidTest {
  private lateinit var connectivityManager: ConnectivityManager
  private lateinit var repository: ConnectivityRepositoryAndroid

  @Before
  fun setUp() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    connectivityManager = context.getSystemService(ConnectivityManager::class.java)
    repository = ConnectivityRepositoryAndroid(connectivityManager)
  }

  /** Capabilities of a network with internet access, validated by Android when [validated]. */
  private fun capabilities(validated: Boolean): NetworkCapabilities {
    val capabilities = ShadowNetworkCapabilities.newInstance()
    shadowOf(capabilities).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    if (validated) {
      shadowOf(capabilities).addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
    return capabilities
  }

  /** Gives the active network [capabilities], or removes the active network when null. */
  private fun setActiveNetwork(capabilities: NetworkCapabilities?) {
    if (capabilities == null) {
      shadowOf(connectivityManager).setActiveNetworkInfo(null)
    } else {
      shadowOf(connectivityManager)
          .setNetworkCapabilities(connectivityManager.activeNetwork, capabilities)
    }
  }

  private fun registeredCallbacks() = shadowOf(connectivityManager).networkCallbacks

  @Test
  fun startsOnlineWhenTheActiveNetworkIsValidated() = runTest {
    setActiveNetwork(capabilities(validated = true))

    assertTrue(repository.isOnline.first())
  }

  @Test
  fun startsOfflineWhenTheActiveNetworkIsNotValidated() = runTest {
    setActiveNetwork(capabilities(validated = false))

    assertFalse(repository.isOnline.first())
  }

  @Test
  fun startsOfflineWithoutAnActiveNetwork() = runTest {
    setActiveNetwork(null)

    assertFalse(repository.isOnline.first())
  }

  @Test
  fun followsTheDefaultNetworkAndIgnoresRepeatedValues() =
      runTest(UnconfinedTestDispatcher()) {
        setActiveNetwork(capabilities(validated = true))
        val values = mutableListOf<Boolean>()
        val job =
            launch(start = CoroutineStart.UNDISPATCHED) { repository.isOnline.collect(values::add) }
        val callback = registeredCallbacks().single()
        val network = ShadowNetwork.newInstance(1)

        callback.onLost(network)
        callback.onLost(network)
        callback.onCapabilitiesChanged(network, capabilities(validated = false))
        callback.onCapabilitiesChanged(network, capabilities(validated = true))
        callback.onCapabilitiesChanged(network, capabilities(validated = true))

        assertEquals(listOf(true, false, true), values)
        job.cancel()
      }

  @Test
  fun unregistersItsCallbackWhenCollectionStops() =
      runTest(UnconfinedTestDispatcher()) {
        setActiveNetwork(capabilities(validated = true))
        val job = launch(start = CoroutineStart.UNDISPATCHED) { repository.isOnline.collect {} }
        assertEquals(1, registeredCallbacks().size)

        job.cancel()

        assertTrue(registeredCallbacks().isEmpty())
      }
}
