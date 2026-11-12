// Co-authored-by: OpenAI Codex
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
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

/**
 * A typed message shown over the map, with retry support when appropriate.
 *
 * @property canRetry whether the screen should offer a retry. [MapVM] has no generic retry: the
 *   screen repeats the action of [operation]. [MapOperation.GAMES] calls [MapVM.loadNearbyGames],
 *   [MapOperation.CITY_SEARCH] calls [MapVM.searchCity], and [MapOperation.DEVICE_LOCATION]
 *   launches the permission request again. That request returns at once when the permission is
 *   already granted, so [MapVM.onLocationPermissionResult] always receives the real permission.
 */
data class MapStatus(
    val operation: MapOperation,
    val type: MapStatusType,
    val message: String,
    val canRetry: Boolean = false,
)

/** Immutable state consumed by the map screen. */
data class MapUiState(
    /** Games loaded for the map that match [appliedFilters]. */
    val games: List<MapGameUiModel> = emptyList(),
    /** The selected game, or null if none is selected or it no longer matches the filters. */
    val selectedGame: MapGameUiModel? = null,
    /**
     * Coordinates of the last successful city search; cleared when the device location is fetched.
     */
    val searchedCity: MapCoordinates? = null,
    /** Last device location fetched, or null if none yet. */
    val userLocation: MapCoordinates? = null,
    /**
     * Camera target and zoom; null until a search, device location, selection or loaded game sets
     * it.
     */
    val camera: MapCamera? = null,
    /** Text currently typed in the city search field. */
    val cityQuery: String = "",
    /** Filter values currently shown in the filter controls. */
    val filters: MapFilters = MapFilters(),
    /** Filters applied to [games]; [filters] can be edited before applying them. */
    val appliedFilters: MapFilters = MapFilters(),
    /** Whether the filter controls are shown below the city search. */
    val filtersExpanded: Boolean = false,
    /** Operations currently in progress. */
    val loadingOperations: Set<MapOperation> = emptySet(),
    /** Message shown over the map, or null when there is none. */
    val status: MapStatus? = null,
) {
  /** Whether any map request is active. */
  val isLoading: Boolean
    get() = loadingOperations.isNotEmpty()
}

/** UI-facing contract for discovering and selecting nearby campaign sessions. */
interface MapVM {
  /** The current state of the map screen. */
  val uiState: StateFlow<MapUiState>

  /**
   * Loads games around the searched city, otherwise around the device location, otherwise without a
   * center, replacing any games request in progress.
   */
  fun loadNearbyGames()

  /** Stores [query] as the city text and clears a pending city search status message. */
  fun onCityQueryChange(query: String)

  /** Searches the city currently stored in [MapUiState.cityQuery]. */
  fun searchCity()

  /**
   * Selects the visible game with [gameId] and moves the camera to it. A null or unknown id clears
   * the selection and leaves the camera where it is.
   */
  fun selectGame(gameId: String?)

  /** Fetches the device location when granted, otherwise reports permission denial. */
  fun onLocationPermissionResult(granted: Boolean)

  /** Shows or hides the filter controls; does not change the filters themselves. */
  fun onFiltersExpandedChange(expanded: Boolean)

  /** Updates filter controls without changing the visible games until [applyFilters] is called. */
  fun onFiltersChange(filters: MapFilters)

  /** Applies the current filter values to the loaded games. */
  fun applyFilters()
}
