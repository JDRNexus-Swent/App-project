// Co-authored-by: OpenAI Codex
package com.github.se.jdrnexus.ui.map

import com.github.se.jdrnexus.model.map.MapGame
import com.github.se.jdrnexus.model.map.MapLocation
import com.github.se.jdrnexus.model.map.MapLocationRepository
import com.github.se.jdrnexus.model.map.MapRepository
import com.github.se.jdrnexus.ui.authentication.MainDispatcherRule
import java.io.IOException
import java.time.LocalDateTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
    assertEquals(MapCamera(city.toUi(), 12f), viewModel.uiState.value.camera)
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
            "Location permission was denied.",
        ),
        viewModel.uiState.value.status,
    )
    assertEquals(0, locations.deviceLocationRequests)
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
    assertEquals(MapCamera(device.toUi(), 15f), viewModel.uiState.value.camera)
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
    val vm = MapViewModel(FakeMapRepository(), FakeMapLocationRepository())
    vm.loadNearbyGames()
    advanceUntilIdle()
    vm.onLocationPermissionResult(false)
    vm.applyFilters()
    assertEquals(MapStatusType.PERMISSION_DENIED, vm.uiState.value.status?.type)
  }

  @Test
  fun latestLocationActionCancelsOlderCitySearch() = runTest {
    val cityResult = CompletableDeferred<MapLocation?>()
    val locations = FakeMapLocationRepository(cityResponse = cityResult)
    val vm = MapViewModel(FakeMapRepository(), locations)
    vm.onCityQueryChange("Paris")
    vm.searchCity()
    runCurrent()
    assertTrue(vm.uiState.value.isLoading)
    vm.onLocationPermissionResult(true)
    runCurrent()
    cityResult.complete(MapLocation(48.85, 2.35, "Paris"))
    advanceUntilIdle()
    assertNull(vm.uiState.value.searchedCity)
    assertEquals(MapCoordinates(0.0, 0.0), vm.uiState.value.camera?.target)
    assertFalse(vm.uiState.value.isLoading)
  }

  @Test
  fun gamesLoadDoesNotClearCityErrorAndExposesLoadingUntilCompletion() = runTest {
    val response = CompletableDeferred<List<MapGame>>()
    val vm = MapViewModel(FakeMapRepository(response = response), FakeMapLocationRepository())
    vm.searchCity()
    val error = vm.uiState.value.status
    vm.loadNearbyGames()
    runCurrent()
    assertTrue(vm.uiState.value.isLoading)
    assertEquals(error, vm.uiState.value.status)
    response.complete(listOf(game("one")))
    advanceUntilIdle()
    assertEquals(error, vm.uiState.value.status)
    assertFalse(vm.uiState.value.isLoading)
  }

  @Test
  fun cancelledRepositoryCallDoesNotLeaveLoadingStuck() = runTest {
    val vm =
        MapViewModel(
            FakeMapRepository(failure = CancellationException()),
            FakeMapLocationRepository(),
        )
    vm.loadNearbyGames()
    advanceUntilIdle()
    assertFalse(vm.uiState.value.isLoading)
    assertNull(vm.uiState.value.status)
  }

  @Test
  fun cityFailureAndRetryExposeErrorThenRecoveredLocation() = runTest {
    val locations =
        FakeMapLocationRepository(
            city = MapLocation(46.52, 6.63),
            cityFailure = IllegalStateException(),
        )
    val vm = MapViewModel(FakeMapRepository(games = listOf(game("one"))), locations)
    vm.onCityQueryChange("Lausanne")
    vm.searchCity()
    advanceUntilIdle()
    assertEquals(MapOperation.CITY_SEARCH, vm.uiState.value.status?.operation)
    assertTrue(vm.uiState.value.status!!.canRetry)
    assertFalse(vm.uiState.value.status!!.message.isBlank())
    locations.cityFailure = null
    vm.searchCity()
    advanceUntilIdle()
    assertNull(vm.uiState.value.status)
    assertFalse(vm.uiState.value.isLoading)
    assertEquals(MapCoordinates(46.52, 6.63), vm.uiState.value.searchedCity)
  }

  @Test
  fun refreshUsesSelectedCityAndMapsEveryDisplayedField() = runTest {
    val item = game("one").copy(distanceKm = 2.5)
    val repo = FakeMapRepository(games = listOf(item))
    val vm =
        MapViewModel(repo, FakeMapLocationRepository(city = MapLocation(46.52, 6.63, "Lausanne")))
    vm.onCityQueryChange("Lausanne")
    vm.searchCity()
    advanceUntilIdle()
    vm.loadNearbyGames()
    advanceUntilIdle()
    assertEquals(MapLocation(46.52, 6.63, "Lausanne"), repo.requestedLocations.last())
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
        vm.uiState.value.games.single(),
    )
    vm.selectGame("one")
    assertEquals(vm.uiState.value.games.single(), vm.uiState.value.selectedGame)
    assertEquals(MapCoordinates(46.52, 6.63), vm.uiState.value.camera?.target)
    vm.selectGame(null)
    assertNull(vm.uiState.value.selectedGame)
    vm.selectGame("missing")
    assertNull(vm.uiState.value.selectedGame)
  }

  @Test
  fun applyingFiltersBeforeLoadingDoesNotClaimEmptyAndResetRestoresGames() = runTest {
    val vm =
        MapViewModel(FakeMapRepository(games = listOf(game("one"))), FakeMapLocationRepository())
    vm.onFiltersChange(MapFilters(gameSystem = "Other"))
    vm.applyFilters()
    assertNull(vm.uiState.value.status)
    vm.loadNearbyGames()
    advanceUntilIdle()
    assertEquals(MapStatusType.EMPTY, vm.uiState.value.status?.type)
    vm.onFiltersChange(MapFilters(gameSystem = "  "))
    vm.applyFilters()
    assertEquals(1, vm.uiState.value.games.size)
    assertNull(vm.uiState.value.status)
  }

  @Test
  fun refreshUsesDeviceLocationAndRetainsCamera() = runTest {
    val repo = FakeMapRepository(games = listOf(game("one")))
    val vm = MapViewModel(repo, FakeMapLocationRepository())
    vm.onLocationPermissionResult(true)
    advanceUntilIdle()
    vm.selectGame("one")
    val camera = vm.uiState.value.camera
    vm.loadNearbyGames()
    advanceUntilIdle()
    assertEquals(MapLocation(0.0, 0.0), repo.requestedLocations.last())
    assertEquals(camera, vm.uiState.value.camera)
    assertEquals("one", vm.uiState.value.selectedGame?.id)
  }

  @Test
  fun failureWithBlankMessageCanRetryAndRecoverGames() = runTest {
    val repo = FakeMapRepository(games = listOf(game("one")), failure = IllegalStateException(" "))
    val vm = MapViewModel(repo, FakeMapLocationRepository())
    vm.loadNearbyGames()
    advanceUntilIdle()
    assertFalse(vm.uiState.value.status!!.message.isBlank())
    assertTrue(vm.uiState.value.status!!.canRetry)
    repo.failure = null
    vm.loadNearbyGames()
    advanceUntilIdle()
    assertNull(vm.uiState.value.status)
    assertEquals(1, vm.uiState.value.games.size)
    assertFalse(vm.uiState.value.isLoading)
  }

  @Test
  fun cityQueryEditingClearsOnlyItsOwnError() = runTest {
    val vm =
        MapViewModel(
            FakeMapRepository(),
            FakeMapLocationRepository(cityFailure = IllegalStateException("offline")),
        )
    vm.onCityQueryChange("City")
    vm.searchCity()
    advanceUntilIdle()
    assertEquals(MapViewModel.CITY_SEARCH_ERROR, vm.uiState.value.status?.message)
    vm.onCityQueryChange("Next")
    assertNull(vm.uiState.value.status)
    vm.onLocationPermissionResult(false)
    vm.onCityQueryChange("Another")
    assertEquals(MapStatusType.PERMISSION_DENIED, vm.uiState.value.status?.type)
  }

  @Test
  fun deviceFailureWithNoMessageRecoversOnRetry() = runTest {
    val locations = FakeMapLocationRepository(deviceLocationFailure = IllegalStateException())
    val vm = MapViewModel(FakeMapRepository(games = listOf(game("one"))), locations)
    vm.onLocationPermissionResult(true)
    advanceUntilIdle()
    assertFalse(vm.uiState.value.status!!.message.isBlank())
    assertFalse(vm.uiState.value.isLoading)
    locations.deviceLocationFailure = null
    vm.onLocationPermissionResult(true)
    advanceUntilIdle()
    assertNull(vm.uiState.value.status)
    assertEquals(1, vm.uiState.value.games.size)
  }

  @Test
  fun cancelledLocationRepositoriesFinishLoadingWithoutError() = runTest {
    val locations =
        FakeMapLocationRepository(
            cityFailure = CancellationException(),
            deviceLocationFailure = CancellationException(),
        )
    val vm = MapViewModel(FakeMapRepository(), locations)
    vm.onCityQueryChange("City")
    vm.searchCity()
    advanceUntilIdle()
    assertFalse(vm.uiState.value.isLoading)
    assertNull(vm.uiState.value.status)
    vm.onLocationPermissionResult(true)
    advanceUntilIdle()
    assertFalse(vm.uiState.value.isLoading)
    assertNull(vm.uiState.value.status)
  }

  @Test
  fun replacementGameRequestKeepsLoadingUntilItsResultArrives() = runTest {
    val first = CompletableDeferred<List<MapGame>>()
    val second = CompletableDeferred<List<MapGame>>()
    val repo = FakeMapRepository(response = first)
    val vm = MapViewModel(repo, FakeMapLocationRepository())
    vm.loadNearbyGames()
    runCurrent()
    repo.response = second
    vm.loadNearbyGames()
    runCurrent()
    first.complete(listOf(game("old")))
    runCurrent()
    assertTrue(vm.uiState.value.isLoading)
    assertTrue(vm.uiState.value.games.isEmpty())
    second.complete(listOf(game("new")))
    advanceUntilIdle()
    assertEquals("new", vm.uiState.value.games.single().id)
    assertFalse(vm.uiState.value.isLoading)
  }

  @Test
  fun searchCancelsOlderDeviceLocationAndUsesNewestCity() = runTest {
    val response = CompletableDeferred<MapLocation>()
    val locations =
        FakeMapLocationRepository(city = MapLocation(46.52, 6.63), deviceResponse = response)
    val vm = MapViewModel(FakeMapRepository(), locations)
    vm.onLocationPermissionResult(true)
    runCurrent()
    vm.onCityQueryChange("Lausanne")
    vm.searchCity()
    runCurrent()
    response.complete(MapLocation(48.0, 2.0))
    advanceUntilIdle()
    assertNull(vm.uiState.value.userLocation)
    assertEquals(MapCoordinates(46.52, 6.63), vm.uiState.value.camera?.target)
    assertFalse(vm.uiState.value.isLoading)
  }

  @Test
  fun deniedPermissionCancelsPendingDeviceRequest() = runTest {
    val response = CompletableDeferred<MapLocation>()
    val vm = MapViewModel(FakeMapRepository(), FakeMapLocationRepository(deviceResponse = response))
    vm.onLocationPermissionResult(true)
    runCurrent()
    vm.onLocationPermissionResult(false)
    response.complete(MapLocation(48.0, 2.0))
    advanceUntilIdle()
    assertNull(vm.uiState.value.userLocation)
    assertEquals(MapStatusType.PERMISSION_DENIED, vm.uiState.value.status?.type)
    assertFalse(vm.uiState.value.isLoading)
  }

  @Test
  fun replacingCitySearchDoesNotPublishTheOldCity() = runTest {
    val first = CompletableDeferred<MapLocation?>()
    val second = CompletableDeferred<MapLocation?>()
    val locations = FakeMapLocationRepository(cityResponse = first)
    val vm = MapViewModel(FakeMapRepository(), locations)
    vm.onCityQueryChange("Old")
    vm.searchCity()
    runCurrent()
    locations.cityResponse = second
    vm.onCityQueryChange("New")
    vm.searchCity()
    runCurrent()
    first.complete(MapLocation(1.0, 1.0))
    runCurrent()
    assertTrue(vm.uiState.value.isLoading)
    second.complete(MapLocation(2.0, 2.0))
    advanceUntilIdle()
    assertEquals(MapCoordinates(2.0, 2.0), vm.uiState.value.searchedCity)
    assertFalse(vm.uiState.value.isLoading)
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
