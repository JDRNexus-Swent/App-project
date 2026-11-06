// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.model.map

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.firebase.FirebaseApp
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MapRepositoryProviderTest {
  private val context = ApplicationProvider.getApplicationContext<Context>()

  @Before
  fun setUp() {
    if (FirebaseApp.getApps(context).isEmpty()) FirebaseApp.initializeApp(context)
  }

  @Test
  fun defaultMapRepositoryReadsFromFirestore() {
    val repository = MapRepositoryProvider.mapRepository

    assertEquals(MapRepositoryFirestore::class, repository::class)
  }

  @Test
  fun locationRepositoryUsesPlatformServices() {
    val repository = MapRepositoryProvider.locationRepository(context)

    assertEquals(MapLocationRepositoryAndroid::class, repository::class)
  }
}
