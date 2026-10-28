// Co-authored-by: OpenAI Codex
package com.github.se.jdrnexus.ui.map

import java.time.LocalDate
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
    val date: LocalDate,
    val availableSeats: Int,
    val distanceKm: Double? = null,
)

/** Filters selected by the user for the visible campaign sessions. */
data class MapFilters(
    val gameSystem: String? = null,
    val onlyGamesWithAvailableSeats: Boolean = false,
)

/** Result of loading campaign sessions for the map. */
sealed interface MapLoadState {
  /** No load has been requested yet. */
  data object Idle : MapLoadState

  /** The ViewModel is retrieving or refreshing map sessions. */
  data object Loading : MapLoadState

  /** The request completed successfully and found sessions. */
  data object Success : MapLoadState

  /** The request completed successfully but found no sessions. */
  data object Empty : MapLoadState

  /** The request failed; the message can be shown by the screen. */
  data class Error(val message: String) : MapLoadState
}

/** Status for an independent city-search request. */
sealed interface CitySearchState {
  data object Idle : CitySearchState

  data object Loading : CitySearchState

  data object Success : CitySearchState

  data class Error(val message: String) : CitySearchState
}

/** Device-location status, including permission and retrieval outcomes. */
sealed interface DeviceLocationState {
  data object NotRequested : DeviceLocationState

  data object PermissionDenied : DeviceLocationState

  data object Loading : DeviceLocationState

  data class Available(val coordinates: MapCoordinates) : DeviceLocationState

  data class Error(val message: String) : DeviceLocationState
}

/** Immutable state consumed by the map screen. */
data class MapUiState(
    val games: List<MapGameUiModel> = emptyList(),
    val selectedGame: MapGameUiModel? = null,
    val searchedCity: MapCoordinates? = null,
    val filters: MapFilters = MapFilters(),
    val loadState: MapLoadState = MapLoadState.Idle,
    val citySearchState: CitySearchState = CitySearchState.Idle,
    val deviceLocationState: DeviceLocationState = DeviceLocationState.NotRequested,
)

/** UI-facing contract for discovering and selecting nearby campaign sessions. */
interface MapVM {
  /** Read-only state used to render map markers, details, search, filters, and operation status. */
  val uiState: StateFlow<MapUiState>

  /** Loads available campaign sessions and their locations. */
  fun loadNearbyGames()

  /** Searches for [query], exposing progress, result, and errors through [MapUiState]. */
  fun searchCity(query: String)

  /** Selects the session with [gameId], or clears the selection when null. */
  fun selectGame(gameId: String?)

  /**
   * Retrieves the device location and exposes its outcome through [MapUiState.deviceLocationState].
   */
  fun updateUserLocation()

  /** Applies the screen's platform permission result before [updateUserLocation] is called. */
  fun onLocationPermissionResult(granted: Boolean)

  /** Applies [filters] to the sessions exposed in [MapUiState.games]. */
  fun applyFilters(filters: MapFilters)
}
