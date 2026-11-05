// Co-authored-by: OpenAI Codex
package com.github.se.jdrnexus.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.se.jdrnexus.model.map.MapGame
import com.github.se.jdrnexus.model.map.MapLocation
import com.github.se.jdrnexus.model.map.MapLocationRepository
import com.github.se.jdrnexus.model.map.MapRepository
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Coordinates map data requests and exposes map screen state. */
class MapViewModel(
    private val mapRepository: MapRepository,
    private val locationRepository: MapLocationRepository,
) : ViewModel(), MapVM {

  private val _uiState = MutableStateFlow(MapUiState())
  override val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

  private var allGames: List<MapGameUiModel> = emptyList()
  private var hasLoadedGames = false
  private var gamesJob: Job? = null
  private var citySearchJob: Job? = null
  private var deviceLocationJob: Job? = null

  override fun loadNearbyGames() {
    val location = _uiState.value.searchedCity?.toModel() ?: _uiState.value.userLocation?.toModel()
    loadGamesNear(location)
  }

  override fun onCityQueryChange(query: String) {
    _uiState.update { state ->
      state.copy(
          cityQuery = query,
          status = if (state.status?.operation == MapOperation.CITY_SEARCH) null else state.status,
      )
    }
  }

  override fun searchCity() {
    val query = _uiState.value.cityQuery.trim()
    if (query.isEmpty()) {
      finishOperation(
          MapOperation.CITY_SEARCH,
          MapStatus(MapOperation.CITY_SEARCH, MapStatusType.ERROR, "Enter a city name."),
      )
      return
    }

    cancelLocationRequests()
    beginOperation(MapOperation.CITY_SEARCH)
    citySearchJob =
        launchOperation(MapOperation.CITY_SEARCH, CITY_SEARCH_ERROR) {
          val location = locationRepository.searchCity(query)
          currentCoroutineContext().ensureActive()
          if (location == null) {
            finishOperation(
                MapOperation.CITY_SEARCH,
                MapStatus(MapOperation.CITY_SEARCH, MapStatusType.ERROR, "City not found."),
            )
            return@launchOperation
          }

          _uiState.update {
            it.copy(
                searchedCity = location.toUi(),
                camera = MapCamera(location.toUi(), CITY_ZOOM),
            )
          }
          beginOperation(MapOperation.GAMES)
          finishOperation(MapOperation.CITY_SEARCH)
          loadGamesNear(location, operationAlreadyStarted = true)
        }
  }

  override fun selectGame(gameId: String?) {
    _uiState.update { state ->
      val selected = gameId?.let { id -> state.games.firstOrNull { it.id == id } }
      state.copy(
          selectedGame = selected,
          camera = selected?.let { MapCamera(it.coordinates, GAME_ZOOM) } ?: state.camera,
      )
    }
  }

  override fun onLocationPermissionResult(granted: Boolean) {
    cancelLocationRequests()
    if (!granted) {
      finishOperation(
          MapOperation.DEVICE_LOCATION,
          MapStatus(
              MapOperation.DEVICE_LOCATION,
              MapStatusType.PERMISSION_DENIED,
              PERMISSION_DENIED_MESSAGE,
          ),
      )
      return
    }

    beginOperation(MapOperation.DEVICE_LOCATION)
    deviceLocationJob =
        launchOperation(MapOperation.DEVICE_LOCATION, DEVICE_LOCATION_ERROR) {
          val location = locationRepository.getDeviceLocation()
          currentCoroutineContext().ensureActive()
          _uiState.update {
            it.copy(
                userLocation = location.toUi(),
                searchedCity = null,
                camera = MapCamera(location.toUi(), USER_LOCATION_ZOOM),
            )
          }
          beginOperation(MapOperation.GAMES)
          finishOperation(MapOperation.DEVICE_LOCATION)
          loadGamesNear(location, operationAlreadyStarted = true)
        }
  }

  override fun onFiltersChange(filters: MapFilters) {
    _uiState.update { it.copy(filters = filters) }
  }

  override fun applyFilters() {
    _uiState.update { state ->
      val updated = state.copy(appliedFilters = state.filters)
      updated.withVisibleGames(allGames).withEmptyStatus()
    }
  }

  private fun loadGamesNear(location: MapLocation?, operationAlreadyStarted: Boolean = false) {
    gamesJob?.cancel()
    if (!operationAlreadyStarted) beginOperation(MapOperation.GAMES)
    gamesJob =
        launchOperation(MapOperation.GAMES, GAMES_ERROR) {
          val loaded = mapRepository.loadGamesNear(location)
          currentCoroutineContext().ensureActive()
          allGames = loaded.map { it.toUi() }
          hasLoadedGames = true
          _uiState.update { state ->
            state.withVisibleGames(allGames).withEmptyStatus().finishLoading(MapOperation.GAMES)
          }
          if (_uiState.value.camera == null) {
            val firstGame = _uiState.value.games.firstOrNull()
            if (firstGame != null) {
              _uiState.update { it.copy(camera = MapCamera(firstGame.coordinates, CITY_ZOOM)) }
            }
          }
        }
  }

  /**
   * Runs [block] for [operation]. A revoked permission is shown as a permission denial, and any
   * other failure as a retryable error with [errorMessage].
   */
  private fun launchOperation(
      operation: MapOperation,
      errorMessage: String,
      block: suspend () -> Unit,
  ): Job = viewModelScope.launch {
    try {
      block()
    } catch (cancellation: CancellationException) {
      // A cancelled Firebase task can throw while this coroutine is still active.
      if (currentCoroutineContext().isActive) finishOperation(operation)
      throw cancellation
    } catch (revoked: SecurityException) {
      finishOperation(
          operation,
          MapStatus(operation, MapStatusType.PERMISSION_DENIED, PERMISSION_DENIED_MESSAGE),
      )
    } catch (failure: Exception) {
      // Device location errors are written for users; other messages (Firestore, Geocoder) are
      // technical, so the fixed message is shown instead.
      val userMessage =
          (failure as? IOException)?.message?.takeIf {
            operation == MapOperation.DEVICE_LOCATION && it.isNotBlank()
          }
      finishOperation(
          operation,
          MapStatus(operation, MapStatusType.ERROR, userMessage ?: errorMessage, canRetry = true),
      )
    }
  }

  private fun cancelLocationRequests() {
    citySearchJob?.cancel()
    deviceLocationJob?.cancel()
    gamesJob?.cancel()
    finishOperation(MapOperation.CITY_SEARCH)
    finishOperation(MapOperation.DEVICE_LOCATION)
    finishOperation(MapOperation.GAMES)
  }

  private fun beginOperation(operation: MapOperation) {
    _uiState.update {
      it.copy(
          loadingOperations = it.loadingOperations + operation,
          status = it.status?.takeUnless { status -> status.operation == operation },
      )
    }
  }

  private fun finishOperation(operation: MapOperation, status: MapStatus? = null) {
    _uiState.update { state ->
      state
          .finishLoading(operation)
          .copy(status = status ?: state.status?.takeUnless { it.operation == operation })
    }
  }

  private fun MapUiState.finishLoading(operation: MapOperation) =
      copy(loadingOperations = loadingOperations - operation)

  private fun MapUiState.withVisibleGames(source: List<MapGameUiModel>): MapUiState {
    val visibleGames = source.filter { game ->
      val matchesSystem =
          appliedFilters.gameSystem.isNullOrBlank() ||
              game.gameSystem.equals(appliedFilters.gameSystem.trim(), ignoreCase = true)
      val hasSeats = !appliedFilters.onlyGamesWithAvailableSeats || game.availableSeats > 0
      matchesSystem && hasSeats
    }
    val selected =
        selectedGame?.id?.let { selectedId -> visibleGames.firstOrNull { it.id == selectedId } }
    return copy(games = visibleGames, selectedGame = selected)
  }

  private fun MapUiState.withEmptyStatus(): MapUiState {
    // Never replace an error or permission message with an empty-state message.
    if (!hasLoadedGames || (status != null && status.type != MapStatusType.EMPTY)) return this
    val emptyMessage =
        when {
          allGames.isEmpty() -> "No games found."
          games.isEmpty() -> "No games match your filters."
          else -> null
        }
    return copy(
        status = emptyMessage?.let { MapStatus(MapOperation.GAMES, MapStatusType.EMPTY, it) }
    )
  }

  private fun MapGame.toUi() =
      MapGameUiModel(
          id = id,
          coordinates = MapCoordinates(latitude, longitude),
          title = title,
          gameMasterName = gameMasterName,
          gameSystem = gameSystem,
          dateTime = dateTime,
          availableSeats = availableSeats,
          distanceKm = distanceKm,
      )

  private fun MapLocation.toUi() = MapCoordinates(latitude, longitude, label)

  private fun MapCoordinates.toModel() = MapLocation(latitude, longitude, label)

  internal companion object {
    const val PERMISSION_DENIED_MESSAGE = "Location permission was denied."
    const val CITY_SEARCH_ERROR =
        "Unable to search for this city. Check your connection and try again."
    const val DEVICE_LOCATION_ERROR = "Unable to get your location. Try again."
    const val GAMES_ERROR = "Unable to load games. Check your connection and try again."

    private const val CITY_ZOOM = 12f
    private const val USER_LOCATION_ZOOM = 15f
    private const val GAME_ZOOM = 16f
  }
}
