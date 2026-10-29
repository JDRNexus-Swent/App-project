// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.github.se.jdrnexus.ui.map

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

object MapScreenTestTags {
  const val MAP_SCREEN = "map_screen"
  const val GOOGLE_MAP = "google_map"
  const val CITY_SEARCH_FIELD = "map_city_search_field"
  const val CITY_SEARCH_BUTTON = "map_city_search_button"
  const val GAME_SYSTEM_FILTER = "map_game_system_filter"
  const val AVAILABLE_SEATS_FILTER = "map_available_seats_filter"
  const val APPLY_FILTERS_BUTTON = "map_apply_filters_button"
  const val LOCATION_BUTTON = "map_location_button"
  const val RETRY_BUTTON = "map_retry_button"
  const val SELECTED_GAME = "map_selected_game"

  fun getTestTagForGameMarker(gameId: String): String = "gameMarker_$gameId"
}

/** Shows nearby campaign sessions and forwards map interactions to [mapVM]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(mapVM: MapVM, modifier: Modifier = Modifier) {
  val uiState by mapVM.uiState.collectAsState()
  val context = LocalContext.current

  val locationPermissionLauncher =
      rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
          permissionResults ->
        val granted = permissionResults.values.any { it }
        mapVM.onLocationPermissionResult(granted)
        if (granted) mapVM.updateUserLocation()
      }

  LaunchedEffect(mapVM) { mapVM.loadNearbyGames() }

  val cameraPositionState = rememberCameraPositionState {
    position = CameraPosition.fromLatLngZoom(LatLng(0.0, 0.0), 2f)
  }

  LaunchedEffect(
      uiState.games,
      uiState.searchedCity,
      uiState.selectedGame,
      uiState.deviceLocationState,
  ) {
    val target =
        uiState.selectedGame?.coordinates
            ?: uiState.searchedCity
            ?: (uiState.deviceLocationState as? DeviceLocationState.Available)?.coordinates
            ?: uiState.games.firstOrNull()?.coordinates
    if (target != null) {
      val zoom = if (uiState.selectedGame != null || uiState.searchedCity != null) 12f else 10f
      cameraPositionState.position =
          CameraPosition.fromLatLngZoom(LatLng(target.latitude, target.longitude), zoom)
    }
  }

  MapScreenContent(
      uiState = uiState,
      modifier = modifier,
      cameraPositionState = cameraPositionState,
      onSearchCity = mapVM::searchCity,
      onSelectGame = mapVM::selectGame,
      onApplyFilters = mapVM::applyFilters,
      onRetryLoad = mapVM::loadNearbyGames,
      onRequestLocation = {
        val hasPermission =
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                ) == PackageManager.PERMISSION_GRANTED
        if (hasPermission) {
          mapVM.onLocationPermissionResult(true)
          mapVM.updateUserLocation()
        } else {
          locationPermissionLauncher.launch(
              arrayOf(
                  Manifest.permission.ACCESS_FINE_LOCATION,
                  Manifest.permission.ACCESS_COARSE_LOCATION,
              )
          )
        }
      },
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MapScreenContent(
    uiState: MapUiState,
    modifier: Modifier,
    cameraPositionState: CameraPositionState,
    onSearchCity: (String) -> Unit,
    onSelectGame: (String?) -> Unit,
    onApplyFilters: (MapFilters) -> Unit,
    onRetryLoad: () -> Unit,
    onRequestLocation: () -> Unit,
) {
  var cityQuery by rememberSaveable { mutableStateOf("") }
  var gameSystem by rememberSaveable { mutableStateOf(uiState.filters.gameSystem.orEmpty()) }
  var onlyAvailableSeats by rememberSaveable {
    mutableStateOf(uiState.filters.onlyGamesWithAvailableSeats)
  }

  LaunchedEffect(uiState.filters) {
    gameSystem = uiState.filters.gameSystem.orEmpty()
    onlyAvailableSeats = uiState.filters.onlyGamesWithAvailableSeats
  }

  Scaffold(
      modifier = modifier.testTag(MapScreenTestTags.MAP_SCREEN),
      topBar = { TopAppBar(title = { Text("Map") }) },
  ) { contentPadding ->
    Box(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
      GoogleMap(
          modifier = Modifier.fillMaxSize().testTag(MapScreenTestTags.GOOGLE_MAP),
          cameraPositionState = cameraPositionState,
      ) {
        uiState.games.forEach { game ->
          val markerPosition = LatLng(game.coordinates.latitude, game.coordinates.longitude)
          Marker(
              state =
                  remember(game.id, game.coordinates) { MarkerState(position = markerPosition) },
              title = game.title,
              snippet = "${game.gameSystem} · ${game.gameMasterName}",
              onClick = {
                onSelectGame(game.id)
                true
              },
              tag = MapScreenTestTags.getTestTagForGameMarker(game.id),
          )
        }
        uiState.searchedCity?.let { city ->
          val markerPosition = LatLng(city.latitude, city.longitude)
          Marker(
              state = remember(city) { MarkerState(position = markerPosition) },
              title = city.label ?: "Searched city",
              tag = "searchedCityMarker",
          )
        }
        (uiState.deviceLocationState as? DeviceLocationState.Available)?.coordinates?.let { location
          ->
          val markerPosition = LatLng(location.latitude, location.longitude)
          Marker(
              state = remember(location) { MarkerState(position = markerPosition) },
              title = "Your location",
              tag = "userLocationMarker",
          )
        }
      }

      Column(
          modifier = Modifier.fillMaxWidth().padding(12.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
            shadowElevation = 4.dp,
        ) {
          Column(
              modifier = Modifier.fillMaxWidth().padding(12.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              OutlinedTextField(
                  value = cityQuery,
                  onValueChange = { cityQuery = it },
                  modifier = Modifier.weight(1f).testTag(MapScreenTestTags.CITY_SEARCH_FIELD),
                  label = { Text("Search city") },
                  singleLine = true,
                  keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                  keyboardActions =
                      KeyboardActions(
                          onSearch = { if (cityQuery.isNotBlank()) onSearchCity(cityQuery.trim()) }
                      ),
              )
              IconButton(
                  onClick = { if (cityQuery.isNotBlank()) onSearchCity(cityQuery.trim()) },
                  modifier = Modifier.testTag(MapScreenTestTags.CITY_SEARCH_BUTTON),
                  enabled = cityQuery.isNotBlank(),
              ) {
                Icon(Icons.Default.Search, contentDescription = "Search city")
              }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
              OutlinedTextField(
                  value = gameSystem,
                  onValueChange = { gameSystem = it },
                  modifier = Modifier.weight(1f).testTag(MapScreenTestTags.GAME_SYSTEM_FILTER),
                  label = { Text("Game system") },
                  placeholder = { Text("Any") },
                  singleLine = true,
              )
              TextButton(
                  onClick = {
                    onApplyFilters(
                        MapFilters(
                            gameSystem = gameSystem.trim().ifBlank { null },
                            onlyGamesWithAvailableSeats = onlyAvailableSeats,
                        )
                    )
                  },
                  modifier = Modifier.testTag(MapScreenTestTags.APPLY_FILTERS_BUTTON),
              ) {
                Text("Apply")
              }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Text("Only games with open seats")
              Switch(
                  checked = onlyAvailableSeats,
                  onCheckedChange = { checked ->
                    onlyAvailableSeats = checked
                    onApplyFilters(
                        MapFilters(
                            gameSystem = gameSystem.trim().ifBlank { null },
                            onlyGamesWithAvailableSeats = checked,
                        )
                    )
                  },
                  modifier = Modifier.testTag(MapScreenTestTags.AVAILABLE_SEATS_FILTER),
              )
            }
          }
        }
      }

      Column(
          modifier = Modifier.align(Alignment.CenterEnd).padding(12.dp),
          horizontalAlignment = Alignment.End,
          verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        IconButton(
            onClick = onRequestLocation,
            modifier = Modifier.testTag(MapScreenTestTags.LOCATION_BUTTON),
        ) {
          Icon(
              Icons.Default.LocationOn,
              contentDescription = "Use device location",
              modifier = Modifier.size(24.dp),
          )
        }
      }

      MapStatusPanel(
          uiState = uiState,
          modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(12.dp),
          onRetryLoad = onRetryLoad,
          onClearSelection = { onSelectGame(null) },
      )
    }
  }
}

@Composable
private fun MapStatusPanel(
    uiState: MapUiState,
    modifier: Modifier,
    onRetryLoad: () -> Unit,
    onClearSelection: () -> Unit,
) {
  val loadError = (uiState.loadState as? MapLoadState.Error)?.message
  val cityError = (uiState.citySearchState as? CitySearchState.Error)?.message
  val locationError = (uiState.deviceLocationState as? DeviceLocationState.Error)?.message
  val messages = listOfNotNull(loadError, cityError, locationError)
  val loading =
      uiState.loadState is MapLoadState.Loading ||
          uiState.citySearchState is CitySearchState.Loading ||
          uiState.deviceLocationState is DeviceLocationState.Loading
  val statusMessages = buildList {
    if (uiState.deviceLocationState is DeviceLocationState.PermissionDenied) {
      add("Location permission denied")
    }
    if (uiState.loadState is MapLoadState.Empty) add("No nearby games found")
    addAll(messages)
  }

  if (uiState.selectedGame != null || loading || statusMessages.isNotEmpty()) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        shadowElevation = 4.dp,
    ) {
      Column(modifier = Modifier.padding(16.dp)) {
        uiState.selectedGame?.let { game ->
          Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Text(
                text = game.title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f).testTag(MapScreenTestTags.SELECTED_GAME),
            )
            TextButton(onClick = onClearSelection) { Text("Close") }
          }
          Text("Game Master: ${game.gameMasterName}")
          Text(
              "${game.gameSystem} · ${game.date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))}"
          )
          Text(
              buildString {
                append("${game.availableSeats} seats available")
                game.distanceKm?.let { append(" · ${"%.1f".format(it)} km away") }
              }
          )
        }
        if (loading) {
          CircularProgressIndicator(modifier = Modifier.size(24.dp))
        }
        statusMessages.forEach { Text(it, color = MaterialTheme.colorScheme.error) }
        if (loadError != null) {
          TextButton(
              onClick = onRetryLoad,
              modifier = Modifier.testTag(MapScreenTestTags.RETRY_BUTTON),
          ) {
            Text("Retry")
          }
        }
      }
    }
  }
}
