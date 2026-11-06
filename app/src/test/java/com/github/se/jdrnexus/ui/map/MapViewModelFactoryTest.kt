package com.github.se.jdrnexus.ui.map

import android.app.Application
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.test.core.app.ApplicationProvider
import com.github.se.jdrnexus.model.map.MapGame
import com.github.se.jdrnexus.model.map.MapLocation
import com.github.se.jdrnexus.model.map.MapRepository
import com.github.se.jdrnexus.model.map.MapRepositoryProvider
import com.github.se.jdrnexus.ui.authentication.MainDispatcherRule
import com.google.firebase.FirebaseApp
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class MapViewModelFactoryTest {
  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  private val application = ApplicationProvider.getApplicationContext<Application>()
  private lateinit var defaultRepository: MapRepository

  @Before
  fun setUp() {
    if (FirebaseApp.getApps(application).isEmpty()) FirebaseApp.initializeApp(application)
    defaultRepository = MapRepositoryProvider.mapRepository
  }

  @After
  fun tearDown() {
    MapRepositoryProvider.mapRepository = defaultRepository
  }

  @Test
  fun factoryBuildsViewModelOnTheProvidedMapRepository() = runTest {
    val repository = RecordingMapRepository()
    MapRepositoryProvider.mapRepository = repository
    val extras = MutableCreationExtras().apply { set(APPLICATION_KEY, application) }
    val viewModel = MapViewModel.Factory.create(MapViewModel::class.java, extras)

    viewModel.loadNearbyGames()
    advanceUntilIdle()

    assertEquals(listOf<MapLocation?>(null), repository.requestedLocations)
  }

  private class RecordingMapRepository : MapRepository {
    val requestedLocations = mutableListOf<MapLocation?>()

    override suspend fun loadGamesNear(location: MapLocation?): List<MapGame> {
      requestedLocations += location
      return emptyList()
    }
  }
}
