// Co-authored-by: OpenAI Codex
package com.github.se.jdrnexus.ui.map

import com.github.se.jdrnexus.model.map.MapGame
import com.github.se.jdrnexus.model.map.MapLocation
import com.github.se.jdrnexus.model.map.MapLocationRepository
import com.github.se.jdrnexus.model.map.MapRepository
import com.github.se.jdrnexus.utils.MainDispatcherRule
import java.io.IOException
import java.time.LocalDateTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MapViewModelTest {
  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  @Test
  fun loadNearbyGamesMapsRepositoryGamesAndExposesSuccess() = runTest {
    val repository = FakeMapRepository(games = listOf(game("open")))
    val viewModel = MapViewModel(repository, FakeMapLocationRepository())

    viewModel.loadNearbyGames()
    advanceUntilIdle()

    assertNull(viewModel.uiState.value.status)
    assertEquals(listOf("open"), viewModel.uiState.value.games.map(MapGameUiModel::id))
    assertNull(repository.requestedLocations.single())
  }

  @Test
  fun loadNearbyGamesExposesEmptyResult() = runTest {
    val viewModel = MapViewModel(FakeMapRepository(), FakeMapLocationRepository())

    viewModel.loadNearbyGames()
    advanceUntilIdle()

    assertEquals(
        MapStatus(MapOperation.GAMES, MapStatusType.EMPTY, "No games found."),
        viewModel.uiState.value.status,
    )
    assertTrue(viewModel.uiState.value.games.isEmpty())
  }

  @Test
  fun applyingFiltersUsesTheDraftAndClearsASelectionThatIsFilteredOut() = runTest {
    val repository =
        FakeMapRepository(
            games = listOf(game("open", system = "D&D", seats = 2), game("full", seats = 0))
        )
    val viewModel = MapViewModel(repository, FakeMapLocationRepository())
    viewModel.loadNearbyGames()
    advanceUntilIdle()

    viewModel.selectGame("open")
    viewModel.onFiltersChange(MapFilters(gameSystem = "d&d", onlyGamesWithAvailableSeats = true))
    assertEquals(2, viewModel.uiState.value.games.size)
    viewModel.applyFilters()

    assertEquals(listOf("open"), viewModel.uiState.value.games.map(MapGameUiModel::id))
    assertEquals("open", viewModel.uiState.value.selectedGame?.id)

    viewModel.onFiltersChange(MapFilters(gameSystem = "Pathfinder"))
    viewModel.applyFilters()
    assertNull(viewModel.uiState.value.selectedGame)
  }

  @Test
  fun searchingCityTrimsQueryAndLoadsGamesAroundItsCoordinates() = runTest {
    val city = MapLocation(46.52, 6.63, "Lausanne")
    val locations = FakeMapLocationRepository(city = city)
    val repository = FakeMapRepository(games = listOf(game("nearby")))
    val viewModel = MapViewModel(repository, locations)

    viewModel.onCityQueryChange("  Lausanne  ")
    viewModel.searchCity()
    advanceUntilIdle()

    assertEquals(listOf("Lausanne"), locations.searchQueries)
    assertEquals(city.toUi(), viewModel.uiState.value.searchedCity)
    assertEquals("  Lausanne  ", viewModel.uiState.value.cityQuery)
    assertEquals(city, repository.requestedLocations.single())
    assertEquals(MapCamera(city.toUi(), MapViewModel.CITY_ZOOM), viewModel.uiState.value.camera)
    assertNull(viewModel.uiState.value.status)
  }

  @Test
  fun blankCitySearchDoesNotCallRepository() = runTest {
    val locations = FakeMapLocationRepository()
    val viewModel = MapViewModel(FakeMapRepository(), locations)

    viewModel.onCityQueryChange("  ")
    viewModel.searchCity()
    advanceUntilIdle()

    assertTrue(locations.searchQueries.isEmpty())
    assertEquals(
        MapStatus(MapOperation.CITY_SEARCH, MapStatusType.ERROR, "Enter a city name."),
        viewModel.uiState.value.status,
    )
  }

  @Test
  fun blankCitySearchDoesNotCancelPendingGamesLoad() = runTest {
    val response = CompletableDeferred<List<MapGame>>()
    val viewModel =
        MapViewModel(FakeMapRepository(response = response), FakeMapLocationRepository())
    viewModel.loadNearbyGames()
    runCurrent()

    viewModel.onCityQueryChange("  ")
    viewModel.searchCity()
    assertTrue(viewModel.uiState.value.isLoading)
    response.complete(listOf(game("pending")))
    advanceUntilIdle()

    assertEquals(listOf("pending"), viewModel.uiState.value.games.map(MapGameUiModel::id))
    assertFalse(viewModel.uiState.value.isLoading)
  }

  @Test
  fun citySearchWithoutMatchExposesError() = runTest {
    val viewModel = MapViewModel(FakeMapRepository(), FakeMapLocationRepository())

    viewModel.onCityQueryChange("Nowhere")
    viewModel.searchCity()
    advanceUntilIdle()

    assertEquals(
        MapStatus(MapOperation.CITY_SEARCH, MapStatusType.ERROR, "City not found."),
        viewModel.uiState.value.status,
    )
  }

  @Test
  fun deniedLocationDoesNotRequestDeviceCoordinates() = runTest {
    val locations = FakeMapLocationRepository()
    val viewModel = MapViewModel(FakeMapRepository(), locations)

    viewModel.onLocationPermissionResult(false)
    advanceUntilIdle()

    assertEquals(
        MapStatus(
            MapOperation.DEVICE_LOCATION,
            MapStatusType.PERMISSION_DENIED,
            MapViewModel.PERMISSION_DENIED_MESSAGE,
        ),
        viewModel.uiState.value.status,
    )
    assertEquals(0, locations.deviceLocationRequests)
  }

  @Test
  fun deniedPermissionDoesNotCancelPendingGamesLoad() = runTest {
    val response = CompletableDeferred<List<MapGame>>()
    val viewModel =
        MapViewModel(FakeMapRepository(response = response), FakeMapLocationRepository())
    viewModel.loadNearbyGames()
    runCurrent()

    viewModel.onLocationPermissionResult(false)
    response.complete(listOf(game("one")))
    advanceUntilIdle()

    assertEquals(listOf("one"), viewModel.uiState.value.games.map(MapGameUiModel::id))
    assertEquals(MapStatusType.PERMISSION_DENIED, viewModel.uiState.value.status?.type)
  }

  @Test
  fun deniedPermissionDoesNotCancelPendingCitySearch() = runTest {
    val cityResult = CompletableDeferred<MapLocation?>()
    val city = MapLocation(46.52, 6.63, "Lausanne")
    val repository = FakeMapRepository(games = listOf(game("nearby")))
    val viewModel = MapViewModel(repository, FakeMapLocationRepository(cityResponse = cityResult))
    viewModel.onCityQueryChange("Lausanne")
    viewModel.searchCity()
    runCurrent()

    viewModel.onLocationPermissionResult(false)
    cityResult.complete(city)
    advanceUntilIdle()

    assertEquals(city.toUi(), viewModel.uiState.value.searchedCity)
    assertEquals(city, repository.requestedLocations.single())
    assertEquals(MapStatusType.PERMISSION_DENIED, viewModel.uiState.value.status?.type)
  }

  @Test
  fun grantedLocationLoadsGamesAroundDeviceCoordinates() = runTest {
    val device = MapLocation(46.52, 6.63, "Current location")
    val locations = FakeMapLocationRepository(deviceLocation = device)
    val repository = FakeMapRepository(games = listOf(game("nearby")))
    val viewModel = MapViewModel(repository, locations)

    viewModel.onLocationPermissionResult(true)
    advanceUntilIdle()

    assertEquals(device.toUi(), viewModel.uiState.value.userLocation)
    assertEquals(device, repository.requestedLocations.single())
    assertEquals(
        MapCamera(device.toUi(), MapViewModel.USER_LOCATION_ZOOM),
        viewModel.uiState.value.camera,
    )
    assertNull(viewModel.uiState.value.status)
  }

  @Test
  fun deviceLocationFailureIsExposedToScreen() = runTest {
    val locations =
        FakeMapLocationRepository(deviceLocationFailure = IllegalStateException("offline"))
    val viewModel = MapViewModel(FakeMapRepository(), locations)

    viewModel.onLocationPermissionResult(true)
    advanceUntilIdle()

    assertEquals(
        MapStatus(
            MapOperation.DEVICE_LOCATION,
            MapStatusType.ERROR,
            MapViewModel.DEVICE_LOCATION_ERROR,
            canRetry = true,
        ),
        viewModel.uiState.value.status,
    )
  }

  @Test
  fun deviceLocationMessageWrittenForUsersIsShownAsIs() = runTest {
    val message = "Enable device location to find nearby games."
    val locations = FakeMapLocationRepository(deviceLocationFailure = IOException(message))
    val viewModel = MapViewModel(FakeMapRepository(), locations)

    viewModel.onLocationPermissionResult(true)
    advanceUntilIdle()

    assertEquals(message, viewModel.uiState.value.status?.message)
  }

  @Test
  fun revokedLocationPermissionIsShownAsPermissionDenied() = runTest {
    val locations =
        FakeMapLocationRepository(deviceLocationFailure = SecurityException("uid 10123 denied"))
    val viewModel = MapViewModel(FakeMapRepository(), locations)

    viewModel.onLocationPermissionResult(true)
    advanceUntilIdle()

    assertEquals(
        MapStatus(
            MapOperation.DEVICE_LOCATION,
            MapStatusType.PERMISSION_DENIED,
            MapViewModel.PERMISSION_DENIED_MESSAGE,
        ),
        viewModel.uiState.value.status,
    )
    assertFalse(viewModel.uiState.value.isLoading)
  }

  @Test
  fun technicalCitySearchErrorIsReplacedByFriendlyMessage() = runTest {
    val viewModel =
        MapViewModel(
            FakeMapRepository(),
            FakeMapLocationRepository(cityFailure = IOException("grpc failed")),
        )

    viewModel.onCityQueryChange("Lausanne")
    viewModel.searchCity()
    advanceUntilIdle()

    assertEquals(MapViewModel.CITY_SEARCH_ERROR, viewModel.uiState.value.status?.message)
  }

  @Test
  fun loadFailureIsExposedToScreen() = runTest {
    val viewModel =
        MapViewModel(
            FakeMapRepository(
                failure = IllegalStateException("PERMISSION_DENIED: Missing permissions.")
            ),
            FakeMapLocationRepository(),
        )

    viewModel.loadNearbyGames()
    advanceUntilIdle()

    assertEquals(
        MapStatus(
            MapOperation.GAMES,
            MapStatusType.ERROR,
            MapViewModel.GAMES_ERROR,
            canRetry = true,
        ),
        viewModel.uiState.value.status,
    )
  }

  @Test
  fun filtersDoNotReplacePermissionDenialWithEmptyStatus() = runTest {
    val viewModel = MapViewModel(FakeMapRepository(), FakeMapLocationRepository())
    viewModel.loadNearbyGames()
    advanceUntilIdle()
    viewModel.onLocationPermissionResult(false)

    viewModel.applyFilters()

    assertEquals(MapStatusType.PERMISSION_DENIED, viewModel.uiState.value.status?.type)
  }

  @Test
  fun latestLocationActionCancelsOlderCitySearch() = runTest {
    val cityResult = CompletableDeferred<MapLocation?>()
    val viewModel =
        MapViewModel(FakeMapRepository(), FakeMapLocationRepository(cityResponse = cityResult))
    viewModel.onCityQueryChange("Paris")
    viewModel.searchCity()
    runCurrent()

    viewModel.onLocationPermissionResult(true)
    runCurrent()
    cityResult.complete(MapLocation(48.85, 2.35, "Paris"))
    advanceUntilIdle()

    assertNull(viewModel.uiState.value.searchedCity)
    assertEquals(MapCoordinates(0.0, 0.0), viewModel.uiState.value.camera?.target)
    assertFalse(viewModel.uiState.value.isLoading)
  }

  @Test
  fun gamesLoadKeepsCityErrorWhileLoadingAndAfterCompletion() = runTest {
    val response = CompletableDeferred<List<MapGame>>()
    val viewModel =
        MapViewModel(FakeMapRepository(response = response), FakeMapLocationRepository())
    viewModel.searchCity()
    val error = viewModel.uiState.value.status

    viewModel.loadNearbyGames()
    runCurrent()

    assertTrue(viewModel.uiState.value.isLoading)
    assertEquals(error, viewModel.uiState.value.status)

    response.complete(listOf(game("one")))
    advanceUntilIdle()

    assertFalse(viewModel.uiState.value.isLoading)
    assertEquals(error, viewModel.uiState.value.status)
  }

  @Test
  fun cancelledRepositoryCallDoesNotLeaveLoadingStuck() = runTest {
    val viewModel =
        MapViewModel(
            FakeMapRepository(failure = CancellationException()),
            FakeMapLocationRepository(),
        )

    viewModel.loadNearbyGames()
    advanceUntilIdle()

    assertFalse(viewModel.uiState.value.isLoading)
    assertNull(viewModel.uiState.value.status)
  }

  @Test
  fun cityFailureExposesRetryableError() = runTest {
    val locations = FakeMapLocationRepository(cityFailure = IllegalStateException())
    val viewModel = MapViewModel(FakeMapRepository(), locations)
    viewModel.onCityQueryChange("Lausanne")

    viewModel.searchCity()
    advanceUntilIdle()

    assertEquals(
        MapStatus(
            MapOperation.CITY_SEARCH,
            MapStatusType.ERROR,
            MapViewModel.CITY_SEARCH_ERROR,
            canRetry = true,
        ),
        viewModel.uiState.value.status,
    )
  }

  @Test
  fun citySearchRetryAfterFailureShowsTheCity() = runTest {
    val locations =
        FakeMapLocationRepository(
            city = MapLocation(46.52, 6.63),
            cityFailure = IllegalStateException(),
        )
    val viewModel = MapViewModel(FakeMapRepository(games = listOf(game("one"))), locations)
    viewModel.onCityQueryChange("Lausanne")
    viewModel.searchCity()
    advanceUntilIdle()
    locations.cityFailure = null

    viewModel.searchCity()
    advanceUntilIdle()

    assertEquals(MapCoordinates(46.52, 6.63), viewModel.uiState.value.searchedCity)
    assertNull(viewModel.uiState.value.status)
    assertFalse(viewModel.uiState.value.isLoading)
  }

  @Test
  fun citySearchHandsLoadingOverToGamesWithoutAGap() = runTest {
    val games = CompletableDeferred<List<MapGame>>()
    val viewModel =
        MapViewModel(
            FakeMapRepository(response = games),
            FakeMapLocationRepository(city = MapLocation(46.52, 6.63)),
        )
    val loadingStates = mutableListOf<Boolean>()
    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
      viewModel.uiState.collect { loadingStates += it.isLoading }
    }
    viewModel.onCityQueryChange("Lausanne")

    viewModel.searchCity()
    runCurrent()
    games.complete(emptyList())
    advanceUntilIdle()

    // Idle, then loading (city search, then games) without interruption, then idle.
    assertEquals(listOf(false, true, false), loadingStates.distinctUntilChangedValues())
  }

  @Test
  fun refreshReloadsGamesAroundSearchedCity() = runTest {
    val city = MapLocation(46.52, 6.63, "Lausanne")
    val repository = FakeMapRepository(games = listOf(game("one")))
    val viewModel = MapViewModel(repository, FakeMapLocationRepository(city = city))
    viewModel.onCityQueryChange("Lausanne")
    viewModel.searchCity()
    advanceUntilIdle()

    viewModel.loadNearbyGames()
    advanceUntilIdle()

    assertEquals(listOf(city, city), repository.requestedLocations)
  }

  @Test
  fun loadedGameExposesEveryDisplayedField() = runTest {
    val repository = FakeMapRepository(games = listOf(game("one").copy(distanceKm = 2.5)))
    val viewModel = MapViewModel(repository, FakeMapLocationRepository())

    viewModel.loadNearbyGames()
    advanceUntilIdle()

    assertEquals(
        MapGameUiModel(
            "one",
            MapCoordinates(46.52, 6.63),
            "Campaign one",
            "GM",
            "Pathfinder",
            LocalDateTime.of(2026, 11, 1, 18, 30),
            1,
            2.5,
        ),
        viewModel.uiState.value.games.single(),
    )
  }

  @Test
  fun selectingGameCentersCameraOnIt() = runTest {
    val viewModel =
        MapViewModel(FakeMapRepository(games = listOf(game("one"))), FakeMapLocationRepository())
    viewModel.loadNearbyGames()
    advanceUntilIdle()

    viewModel.selectGame("one")

    assertEquals(viewModel.uiState.value.games.single(), viewModel.uiState.value.selectedGame)
    assertEquals(
        MapCamera(MapCoordinates(46.52, 6.63), MapViewModel.GAME_ZOOM),
        viewModel.uiState.value.camera,
    )
  }

  @Test
  fun selectingNoGameClearsSelection() = runTest {
    val viewModel =
        MapViewModel(FakeMapRepository(games = listOf(game("one"))), FakeMapLocationRepository())
    viewModel.loadNearbyGames()
    advanceUntilIdle()
    viewModel.selectGame("one")

    viewModel.selectGame(null)

    assertNull(viewModel.uiState.value.selectedGame)
  }

  @Test
  fun selectingUnknownGameClearsSelection() = runTest {
    val viewModel =
        MapViewModel(FakeMapRepository(games = listOf(game("one"))), FakeMapLocationRepository())
    viewModel.loadNearbyGames()
    advanceUntilIdle()
    viewModel.selectGame("one")

    viewModel.selectGame("missing")

    assertNull(viewModel.uiState.value.selectedGame)
  }

  @Test
  fun applyingFiltersBeforeFirstLoadDoesNotClaimEmptyResult() = runTest {
    val viewModel =
        MapViewModel(FakeMapRepository(games = listOf(game("one"))), FakeMapLocationRepository())
    viewModel.onFiltersChange(MapFilters(gameSystem = "Other"))

    viewModel.applyFilters()

    assertNull(viewModel.uiState.value.status)
  }

  @Test
  fun filtersMatchingNoLoadedGameExposeFilteredEmptyStatus() = runTest {
    val viewModel =
        MapViewModel(FakeMapRepository(games = listOf(game("one"))), FakeMapLocationRepository())
    viewModel.onFiltersChange(MapFilters(gameSystem = "Other"))
    viewModel.applyFilters()

    viewModel.loadNearbyGames()
    advanceUntilIdle()

    assertEquals(
        MapStatus(MapOperation.GAMES, MapStatusType.EMPTY, "No games match your filters."),
        viewModel.uiState.value.status,
    )
  }

  @Test
  fun blankGameSystemFilterRestoresAllGames() = runTest {
    val viewModel =
        MapViewModel(FakeMapRepository(games = listOf(game("one"))), FakeMapLocationRepository())
    viewModel.onFiltersChange(MapFilters(gameSystem = "Other"))
    viewModel.applyFilters()
    viewModel.loadNearbyGames()
    advanceUntilIdle()

    viewModel.onFiltersChange(MapFilters(gameSystem = "  "))
    viewModel.applyFilters()

    assertEquals(listOf("one"), viewModel.uiState.value.games.map(MapGameUiModel::id))
    assertNull(viewModel.uiState.value.status)
  }

  @Test
  fun refreshReloadsGamesAroundDeviceLocation() = runTest {
    val repository = FakeMapRepository(games = listOf(game("one")))
    val viewModel = MapViewModel(repository, FakeMapLocationRepository())
    viewModel.onLocationPermissionResult(true)
    advanceUntilIdle()

    viewModel.loadNearbyGames()
    advanceUntilIdle()

    assertEquals(MapLocation(0.0, 0.0), repository.requestedLocations.last())
  }

  @Test
  fun refreshKeepsCameraAndSelection() = runTest {
    val viewModel =
        MapViewModel(FakeMapRepository(games = listOf(game("one"))), FakeMapLocationRepository())
    viewModel.onLocationPermissionResult(true)
    advanceUntilIdle()
    viewModel.selectGame("one")
    val camera = viewModel.uiState.value.camera

    viewModel.loadNearbyGames()
    advanceUntilIdle()

    assertEquals(camera, viewModel.uiState.value.camera)
    assertEquals("one", viewModel.uiState.value.selectedGame?.id)
  }

  @Test
  fun gamesRetryAfterFailureRecoversGames() = runTest {
    val repository =
        FakeMapRepository(games = listOf(game("one")), failure = IllegalStateException())
    val viewModel = MapViewModel(repository, FakeMapLocationRepository())
    viewModel.loadNearbyGames()
    advanceUntilIdle()
    repository.failure = null

    viewModel.loadNearbyGames()
    advanceUntilIdle()

    assertEquals(listOf("one"), viewModel.uiState.value.games.map(MapGameUiModel::id))
    assertNull(viewModel.uiState.value.status)
    assertFalse(viewModel.uiState.value.isLoading)
  }

  @Test
  fun editingCityQueryClearsCitySearchError() = runTest {
    val viewModel =
        MapViewModel(
            FakeMapRepository(),
            FakeMapLocationRepository(cityFailure = IllegalStateException("offline")),
        )
    viewModel.onCityQueryChange("City")
    viewModel.searchCity()
    advanceUntilIdle()

    viewModel.onCityQueryChange("Next")

    assertNull(viewModel.uiState.value.status)
  }

  @Test
  fun editingCityQueryKeepsPermissionDenial() = runTest {
    val viewModel = MapViewModel(FakeMapRepository(), FakeMapLocationRepository())
    viewModel.onLocationPermissionResult(false)

    viewModel.onCityQueryChange("Another")

    assertEquals(MapStatusType.PERMISSION_DENIED, viewModel.uiState.value.status?.type)
  }

  @Test
  fun blankDeviceLocationMessageIsReplacedByFixedMessage() = runTest {
    val locations = FakeMapLocationRepository(deviceLocationFailure = IOException(" "))
    val viewModel = MapViewModel(FakeMapRepository(), locations)

    viewModel.onLocationPermissionResult(true)
    advanceUntilIdle()

    assertEquals(MapViewModel.DEVICE_LOCATION_ERROR, viewModel.uiState.value.status?.message)
  }

  @Test
  fun deviceLocationRetryAfterFailureLoadsGames() = runTest {
    val locations = FakeMapLocationRepository(deviceLocationFailure = IllegalStateException())
    val viewModel = MapViewModel(FakeMapRepository(games = listOf(game("one"))), locations)
    viewModel.onLocationPermissionResult(true)
    advanceUntilIdle()
    locations.deviceLocationFailure = null

    viewModel.onLocationPermissionResult(true)
    advanceUntilIdle()

    assertEquals(listOf("one"), viewModel.uiState.value.games.map(MapGameUiModel::id))
    assertNull(viewModel.uiState.value.status)
    assertFalse(viewModel.uiState.value.isLoading)
  }

  @Test
  fun cancelledCitySearchFinishesLoadingWithoutError() = runTest {
    val locations = FakeMapLocationRepository(cityFailure = CancellationException())
    val viewModel = MapViewModel(FakeMapRepository(), locations)
    viewModel.onCityQueryChange("City")

    viewModel.searchCity()
    advanceUntilIdle()

    assertFalse(viewModel.uiState.value.isLoading)
    assertNull(viewModel.uiState.value.status)
  }

  @Test
  fun cancelledDeviceLocationFinishesLoadingWithoutError() = runTest {
    val locations = FakeMapLocationRepository(deviceLocationFailure = CancellationException())
    val viewModel = MapViewModel(FakeMapRepository(), locations)

    viewModel.onLocationPermissionResult(true)
    advanceUntilIdle()

    assertFalse(viewModel.uiState.value.isLoading)
    assertNull(viewModel.uiState.value.status)
  }

  @Test
  fun replacementGameRequestKeepsLoadingUntilItsResultArrives() = runTest {
    val first = CompletableDeferred<List<MapGame>>()
    val second = CompletableDeferred<List<MapGame>>()
    val repository = FakeMapRepository(response = first)
    val viewModel = MapViewModel(repository, FakeMapLocationRepository())
    viewModel.loadNearbyGames()
    runCurrent()
    repository.response = second

    viewModel.loadNearbyGames()
    runCurrent()
    first.complete(listOf(game("old")))
    runCurrent()

    assertTrue(viewModel.uiState.value.isLoading)
    assertTrue(viewModel.uiState.value.games.isEmpty())

    second.complete(listOf(game("new")))
    advanceUntilIdle()

    assertEquals(listOf("new"), viewModel.uiState.value.games.map(MapGameUiModel::id))
    assertFalse(viewModel.uiState.value.isLoading)
  }

  @Test
  fun searchCancelsOlderDeviceLocationAndUsesNewestCity() = runTest {
    val response = CompletableDeferred<MapLocation>()
    val locations =
        FakeMapLocationRepository(city = MapLocation(46.52, 6.63), deviceResponse = response)
    val viewModel = MapViewModel(FakeMapRepository(), locations)
    viewModel.onLocationPermissionResult(true)
    runCurrent()

    viewModel.onCityQueryChange("Lausanne")
    viewModel.searchCity()
    runCurrent()
    response.complete(MapLocation(48.0, 2.0))
    advanceUntilIdle()

    assertNull(viewModel.uiState.value.userLocation)
    assertEquals(MapCoordinates(46.52, 6.63), viewModel.uiState.value.camera?.target)
    assertFalse(viewModel.uiState.value.isLoading)
  }

  @Test
  fun deniedPermissionCancelsPendingDeviceRequest() = runTest {
    val response = CompletableDeferred<MapLocation>()
    val viewModel =
        MapViewModel(FakeMapRepository(), FakeMapLocationRepository(deviceResponse = response))
    viewModel.onLocationPermissionResult(true)
    runCurrent()

    viewModel.onLocationPermissionResult(false)
    response.complete(MapLocation(48.0, 2.0))
    advanceUntilIdle()

    assertNull(viewModel.uiState.value.userLocation)
    assertEquals(MapStatusType.PERMISSION_DENIED, viewModel.uiState.value.status?.type)
    assertFalse(viewModel.uiState.value.isLoading)
  }

  @Test
  fun replacingCitySearchDoesNotPublishTheOldCity() = runTest {
    val first = CompletableDeferred<MapLocation?>()
    val second = CompletableDeferred<MapLocation?>()
    val locations = FakeMapLocationRepository(cityResponse = first)
    val viewModel = MapViewModel(FakeMapRepository(), locations)
    viewModel.onCityQueryChange("Old")
    viewModel.searchCity()
    runCurrent()
    locations.cityResponse = second

    viewModel.onCityQueryChange("New")
    viewModel.searchCity()
    runCurrent()
    first.complete(MapLocation(1.0, 1.0))
    runCurrent()

    assertTrue(viewModel.uiState.value.isLoading)

    second.complete(MapLocation(2.0, 2.0))
    advanceUntilIdle()

    assertEquals(MapCoordinates(2.0, 2.0), viewModel.uiState.value.searchedCity)
    assertFalse(viewModel.uiState.value.isLoading)
  }

  /** Drops consecutive duplicates, so the test checks transitions rather than emission count. */
  private fun List<Boolean>.distinctUntilChangedValues(): List<Boolean> =
      filterIndexed { index, value ->
        index == 0 || value != this[index - 1]
      }

  private fun game(id: String, system: String = "Pathfinder", seats: Int = 1) =
      MapGame(
          id = id,
          latitude = 46.52,
          longitude = 6.63,
          title = "Campaign $id",
          gameMasterName = "GM",
          gameSystem = system,
          dateTime = LocalDateTime.of(2026, 11, 1, 18, 30),
          availableSeats = seats,
      )

  private class FakeMapRepository(
      private val games: List<MapGame> = emptyList(),
      var failure: Exception? = null,
      var response: CompletableDeferred<List<MapGame>>? = null,
  ) : MapRepository {
    val requestedLocations = mutableListOf<MapLocation?>()

    override suspend fun loadGamesNear(location: MapLocation?): List<MapGame> {
      requestedLocations += location
      failure?.let { throw it }
      return response?.await() ?: games
    }
  }

  private class FakeMapLocationRepository(
      private val city: MapLocation? = null,
      private val deviceLocation: MapLocation = MapLocation(0.0, 0.0),
      var deviceLocationFailure: Exception? = null,
      var cityFailure: Exception? = null,
      var cityResponse: CompletableDeferred<MapLocation?>? = null,
      val deviceResponse: CompletableDeferred<MapLocation>? = null,
  ) : MapLocationRepository {
    val searchQueries = mutableListOf<String>()
    var deviceLocationRequests = 0

    override suspend fun searchCity(query: String): MapLocation? {
      searchQueries += query
      cityFailure?.let { throw it }
      return cityResponse?.await() ?: city
    }

    override suspend fun getDeviceLocation(): MapLocation {
      deviceLocationRequests += 1
      deviceLocationFailure?.let { throw it }
      return deviceResponse?.await() ?: deviceLocation
    }
  }

  private fun MapLocation.toUi() = MapCoordinates(latitude, longitude, label)
}
