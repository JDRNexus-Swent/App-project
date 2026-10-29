// Co-authored-by: OpenAI Codex
package com.github.se.jdrnexus.ui.map

import java.time.LocalDateTime
import kotlinx.coroutines.flow.StateFlow

/** Coordinates and an optional label for a map location. */
data class MapCoordinates(
    val latitude: Double,
    val longitude: Double,
    val label: String? = null,
)

/** A campaign session, ready for display as a map marker or selected-game detail. */
data class MapGameUiModel(
    val id: String,
    val coordinates: MapCoordinates,
    val title: String,
    val gameMasterName: String,
    val gameSystem: String,
    val dateTime: LocalDateTime,
    val availableSeats: Int,
    val distanceKm: Double? = null,
)

/** Filters selected in the map screen. */
data class MapFilters(
    val gameSystem: String? = null,
    val onlyGamesWithAvailableSeats: Boolean = false,
)

/** Camera target and zoom selected by the ViewModel. */
data class MapCamera(val target: MapCoordinates, val zoom: Float)

/** The operation associated with a map status or active request. */
enum class MapOperation {
  GAMES,
  CITY_SEARCH,
  DEVICE_LOCATION,
}

/** Kind of message shown over the map. */
enum class MapStatusType {
  EMPTY,
  ERROR,
  PERMISSION_DENIED,
}

/** A typed message shown over the map, with retry support when appropriate. */
data class MapStatus(
    val operation: MapOperation,
    val type: MapStatusType,
    val message: String,
    val canRetry: Boolean = false,
)

/** Immutable state consumed by the map screen. */
data class MapUiState(
    val games: List<MapGameUiModel> = emptyList(),
    val selectedGame: MapGameUiModel? = null,
    val searchedCity: MapCoordinates? = null,
    val userLocation: MapCoordinates? = null,
    val camera: MapCamera? = null,
    val cityQuery: String = "",
    /** Filter values currently shown in the filter controls. */
    val filters: MapFilters = MapFilters(),
    /** Filters applied to [games]; [filters] can be edited before applying them. */
    val appliedFilters: MapFilters = MapFilters(),
    val loadingOperations: Set<MapOperation> = emptySet(),
    val status: MapStatus? = null,
) {
  /** Whether any map request is active. */
  val isLoading: Boolean
    get() = loadingOperations.isNotEmpty()
}

/** UI-facing contract for discovering and selecting nearby campaign sessions. */
interface MapVM {
  val uiState: StateFlow<MapUiState>

  fun loadNearbyGames()

  fun onCityQueryChange(query: String)

  /** Searches the city currently stored in [MapUiState.cityQuery]. */
  fun searchCity()

  fun selectGame(gameId: String?)

  /** Fetches the device location when granted, otherwise reports permission denial. */
  fun onLocationPermissionResult(granted: Boolean)

  /** Updates filter controls without changing the visible games until [applyFilters] is called. */
  fun onFiltersChange(filters: MapFilters)

  /** Applies the current filter values to the loaded games. */
  fun applyFilters()
}
