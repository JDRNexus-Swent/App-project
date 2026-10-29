// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.github.se.jdrnexus.ui.map

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerComposable
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

// ============ Internal functions and test tags ============
object MapScreenTestTags {
  const val MAP_SCREEN = "map_screen"
  const val GOOGLE_MAP = "google_map"
  const val CITY_SEARCH_FIELD = "map_city_search_field"
  const val CITY_SEARCH_BUTTON = "map_city_search_button"
  const val GAME_SYSTEM_FILTER = "map_game_system_filter"
  const val AVAILABLE_SEATS_FILTER = "map_available_seats_filter"
  const val APPLY_FILTERS_BUTTON = "map_apply_filters_button"
  const val FILTERS_TOGGLE = "map_filters_toggle"
  const val LOCATION_BUTTON = "map_location_button"
  const val RETRY_BUTTON = "map_retry_button"
  const val SELECTED_GAME = "map_selected_game"
  const val BOTTOM_NAVIGATION = "map_bottom_navigation"
  const val VIEW_GAME_DETAILS_BUTTON = "map_view_game_details_button"
  const val GAME_PIN = "map_game_pin"
  const val SELECTED_GAME_PIN = "map_selected_game_pin"
  const val USER_LOCATION_PIN = "map_user_location_pin"

  fun getTestTagForGameMarker(gameId: String): String = "gameMarker_$gameId"
}

internal fun mapStyleJson(isDarkTheme: Boolean): String =
    if (isDarkTheme) {
      """[{"elementType":"geometry","stylers":[{"color":"#211d19"}]},
        {"elementType":"labels.text.fill","stylers":[{"color":"#c9b99d"}]},
        {"featureType":"road","elementType":"geometry","stylers":[{"color":"#51483c"}]},
        {"featureType":"road","elementType":"geometry.stroke","stylers":[{"color":"#171411"}]},
        {"featureType":"water","elementType":"geometry","stylers":[{"color":"#183a3b"}]},
        {"featureType":"poi.park","elementType":"geometry","stylers":[{"color":"#293a30"}]}]"""
    } else {
      """[{"elementType":"geometry","stylers":[{"color":"#efe6da"}]},
        {"elementType":"labels.text.fill","stylers":[{"color":"#705d4b"}]},
        {"featureType":"road","elementType":"geometry","stylers":[{"color":"#c9b6a4"}]},
        {"featureType":"road","elementType":"geometry.stroke","stylers":[{"color":"#f8f2ea"}]},
        {"featureType":"water","elementType":"geometry","stylers":[{"color":"#c5dcd5"}]},
        {"featureType":"poi.park","elementType":"geometry","stylers":[{"color":"#d6e1d4"}]}]"""
    }

/** Shared look of the floating panels (search bar, status banner, game card). */
private val OverlayShape = RoundedCornerShape(16.dp)
private val OverlayElevation = 4.dp
private val HairlineBorderWidth = 0.5.dp

internal fun mapMarkerHue(isDarkTheme: Boolean): Float = if (isDarkTheme) 45f else 25f

/** Formats a session date like "Saturday, Oct 10 · 7:00 PM". */
internal fun formatGameDate(dateTime: LocalDateTime, locale: Locale = Locale.getDefault()): String =
    dateTime.format(DateTimeFormatter.ofPattern("EEEE, MMM d · h:mm a", locale))

// ============ Screen ============

/** Shows nearby campaign sessions and forwards map interactions to [mapVM]. */
@Composable
fun MapScreen(
    mapVM: MapVM,
    modifier: Modifier = Modifier,
    onTabSelected: (String) -> Unit = {},
    onViewGameDetails: (String) -> Unit = {},
) {
  val uiState by mapVM.uiState.collectAsState()
  // Returns immediately, without a dialog, when the permission is already granted.
  val locationPermissionLauncher =
      rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
          results ->
        mapVM.onLocationPermissionResult(results.values.any { it })
      }
  val cameraPositionState = rememberCameraPositionState {
    position = CameraPosition.fromLatLngZoom(LatLng(0.0, 0.0), 2f)
  }

  LaunchedEffect(mapVM) { mapVM.loadNearbyGames() }
  LaunchedEffect(uiState.camera) {
    uiState.camera?.let { camera ->
      cameraPositionState.position =
          CameraPosition.fromLatLngZoom(camera.target.toLatLng(), camera.zoom)
    }
  }

  Scaffold(
      modifier = modifier.testTag(MapScreenTestTags.MAP_SCREEN),
      bottomBar = { MapBottomNavigation(onTabSelected) },
  ) { contentPadding ->
    Box(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
      MapView(
          uiState = uiState,
          cameraPositionState = cameraPositionState,
          onSelectGame = mapVM::selectGame,
      )
      MapSearchBar(
          query = uiState.cityQuery,
          filters = uiState.filters,
          onQueryChange = mapVM::onCityQueryChange,
          onSearch = mapVM::searchCity,
          onFiltersChange = mapVM::onFiltersChange,
          onApplyFilters = mapVM::applyFilters,
          modifier = Modifier.fillMaxWidth().padding(12.dp),
      )
      Column(
          modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(12.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        LocationButton(
            onClick = { locationPermissionLauncher.launch(LOCATION_PERMISSIONS) },
            modifier = Modifier.align(Alignment.End),
        )
        if (uiState.isLoading || uiState.status != null) {
          MapStatusBanner(
              isLoading = uiState.isLoading,
              status = uiState.status,
              onRetry = mapVM::loadNearbyGames,
          )
        }
        uiState.selectedGame?.let { game ->
          SelectedGameCard(game = game, onViewDetails = { onViewGameDetails(game.id) })
        }
      }
    }
  }
}

/** Fine and coarse location, so the user may grant either precision. */
private val LOCATION_PERMISSIONS =
    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

private fun MapCoordinates.toLatLng() = LatLng(latitude, longitude)

// ============ Map layer ============

/** Marker state recreated only when [coordinates] change. */
@Composable
private fun rememberMarkerStateAt(coordinates: MapCoordinates): MarkerState =
    remember(coordinates) { MarkerState(position = coordinates.toLatLng()) }

/** Themed Google map with a pin per game, the searched city and the user's position. */
@Composable
private fun MapView(
    uiState: MapUiState,
    cameraPositionState: CameraPositionState,
    onSelectGame: (String) -> Unit,
) {
  val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
  GoogleMap(
      modifier = Modifier.fillMaxSize().testTag(MapScreenTestTags.GOOGLE_MAP),
      cameraPositionState = cameraPositionState,
      properties = MapProperties(mapStyleOptions = MapStyleOptions(mapStyleJson(isDarkTheme))),
  ) {
    uiState.games.forEach { game ->
      val selected = game.id == uiState.selectedGame?.id
      MarkerComposable(
          game.id,
          selected,
          state = rememberMarkerStateAt(game.coordinates),
          title = game.title,
          snippet = "${game.gameSystem} · ${game.gameMasterName}",
          onClick = {
            onSelectGame(game.id)
            true
          },
          tag = MapScreenTestTags.getTestTagForGameMarker(game.id),
      ) {
        GamePin(selected = selected)
      }
    }
    uiState.searchedCity?.let { city ->
      Marker(
          state = rememberMarkerStateAt(city),
          title = city.label ?: "Searched city",
          icon = BitmapDescriptorFactory.defaultMarker(mapMarkerHue(isDarkTheme)),
          tag = "searchedCityMarker",
      )
    }
    uiState.userLocation?.let { location ->
      MarkerComposable(
          state = rememberMarkerStateAt(location),
          anchor = Offset(0.5f, 0.3f),
          tag = "userLocationMarker",
      ) {
        UserLocationPin()
      }
    }
  }
}

/** Gold map pin for a campaign session; larger and filled, with a halo, when [selected]. */
@Composable
internal fun GamePin(selected: Boolean, modifier: Modifier = Modifier) {
  val gold = MaterialTheme.colorScheme.primary
  Box(
      modifier =
          modifier
              .testTag(
                  if (selected) MapScreenTestTags.SELECTED_GAME_PIN else MapScreenTestTags.GAME_PIN
              )
              .size(if (selected) 56.dp else 36.dp)
              .background(
                  if (selected) gold.copy(alpha = 0.2f) else Color.Transparent,
                  CircleShape,
              ),
      contentAlignment = Alignment.Center,
  ) {
    Box(
        modifier =
            Modifier.size(if (selected) 40.dp else 32.dp)
                .background(
                    if (selected) gold else MaterialTheme.colorScheme.surface,
                    CircleShape,
                )
                .border(2.dp, gold, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
      Icon(
          Icons.Default.LocationOn,
          contentDescription = null,
          tint = if (selected) MaterialTheme.colorScheme.onPrimary else gold,
          modifier = Modifier.size(if (selected) 22.dp else 18.dp),
      )
    }
  }
}

/** Device-location dot with a translucent halo and a "You" label. */
@Composable
internal fun UserLocationPin(modifier: Modifier = Modifier) {
  Column(
      modifier = modifier.testTag(MapScreenTestTags.USER_LOCATION_PIN),
      horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Box(
        modifier =
            Modifier.size(48.dp)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
      Box(
          modifier =
              Modifier.size(16.dp)
                  .background(MaterialTheme.colorScheme.onSurface, CircleShape)
                  .border(3.dp, MaterialTheme.colorScheme.surface, CircleShape)
      )
    }
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(HairlineBorderWidth, MaterialTheme.colorScheme.outlineVariant),
    ) {
      Text(
          "You",
          modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
          color = MaterialTheme.colorScheme.onSurface,
          style = MaterialTheme.typography.labelSmall,
      )
    }
  }
}

// ============ Top overlay: search and filters ============

/** Floating city search field with a filter toggle revealing the game filters. */
@Composable
private fun MapSearchBar(
    query: String,
    filters: MapFilters,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onFiltersChange: (MapFilters) -> Unit,
    onApplyFilters: () -> Unit,
    modifier: Modifier = Modifier,
) {
  // Purely visual toggle: nothing outside this bar needs to know about it.
  var filtersExpanded by rememberSaveable { mutableStateOf(false) }
  Surface(
      modifier = modifier,
      shape = OverlayShape,
      color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
      border = BorderStroke(HairlineBorderWidth, MaterialTheme.colorScheme.outlineVariant),
      shadowElevation = OverlayElevation,
  ) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f).testTag(MapScreenTestTags.CITY_SEARCH_FIELD),
            label = { Text("Search a city") },
            leadingIcon = {
              IconButton(
                  onClick = onSearch,
                  modifier = Modifier.testTag(MapScreenTestTags.CITY_SEARCH_BUTTON),
                  enabled = query.isNotBlank(),
              ) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = "Search city",
                    tint = MaterialTheme.colorScheme.primary,
                )
              }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { if (query.isNotBlank()) onSearch() }),
            colors =
                OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = Color.Transparent,
                    focusedBorderColor = Color.Transparent,
                ),
        )
        VerticalDivider(modifier = Modifier.height(32.dp))
        IconButton(
            onClick = { filtersExpanded = !filtersExpanded },
            modifier = Modifier.testTag(MapScreenTestTags.FILTERS_TOGGLE),
        ) {
          Icon(
              Icons.Default.Tune,
              contentDescription = "Show filters",
              tint = MaterialTheme.colorScheme.primary,
          )
        }
      }
      if (filtersExpanded) {
        MapFiltersPanel(
            filters = filters,
            onFiltersChange = onFiltersChange,
            onApply = onApplyFilters,
        )
      }
    }
  }
}

/** Game-system field, open-seats switch and the button applying them. */
@Composable
private fun MapFiltersPanel(
    filters: MapFilters,
    onFiltersChange: (MapFilters) -> Unit,
    onApply: () -> Unit,
) {
  Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    OutlinedTextField(
        value = filters.gameSystem.orEmpty(),
        onValueChange = { onFiltersChange(filters.copy(gameSystem = it.ifBlank { null })) },
        modifier = Modifier.weight(1f).testTag(MapScreenTestTags.GAME_SYSTEM_FILTER),
        label = { Text("Game system") },
        placeholder = { Text("Any") },
        singleLine = true,
    )
    TextButton(
        onClick = onApply,
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
        checked = filters.onlyGamesWithAvailableSeats,
        onCheckedChange = { onFiltersChange(filters.copy(onlyGamesWithAvailableSeats = it)) },
        modifier = Modifier.testTag(MapScreenTestTags.AVAILABLE_SEATS_FILTER),
    )
  }
}

// ============ Bottom overlay: location, status and selected game ============

/** Round button asking for the device location. */
@Composable
private fun LocationButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
  Surface(
      modifier = modifier,
      shape = CircleShape,
      color = MaterialTheme.colorScheme.surface,
      border = BorderStroke(HairlineBorderWidth, MaterialTheme.colorScheme.outlineVariant),
      shadowElevation = OverlayElevation,
  ) {
    IconButton(onClick = onClick, modifier = Modifier.testTag(MapScreenTestTags.LOCATION_BUTTON)) {
      Icon(
          Icons.Default.MyLocation,
          contentDescription = "Use device location",
          tint = MaterialTheme.colorScheme.primary,
      )
    }
  }
}

/** Loading indicator and/or status message, with a retry action when [MapStatus.canRetry]. */
@Composable
private fun MapStatusBanner(isLoading: Boolean, status: MapStatus?, onRetry: () -> Unit) {
  Surface(
      modifier = Modifier.fillMaxWidth(),
      shape = OverlayShape,
      color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
      border = BorderStroke(HairlineBorderWidth, MaterialTheme.colorScheme.outlineVariant),
      shadowElevation = OverlayElevation,
  ) {
    Row(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      if (isLoading) {
        CircularProgressIndicator(
            modifier = Modifier.size(24.dp),
            color = MaterialTheme.colorScheme.primary,
        )
      }
      status?.let {
        Text(it.message, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.error)
        if (it.canRetry) {
          TextButton(
              onClick = onRetry,
              modifier = Modifier.testTag(MapScreenTestTags.RETRY_BUTTON),
          ) {
            Text("Retry")
          }
        }
      }
    }
  }
}

/** Bottom sheet-like card describing the selected [game]. */
@Composable
private fun SelectedGameCard(game: MapGameUiModel, onViewDetails: () -> Unit) {
  Surface(
      modifier = Modifier.fillMaxWidth(),
      shape = OverlayShape,
      color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
      shadowElevation = OverlayElevation,
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Surface(
          shape = RoundedCornerShape(4.dp),
          color = MaterialTheme.colorScheme.outlineVariant,
          modifier = Modifier.align(Alignment.CenterHorizontally).width(32.dp).height(4.dp),
      ) {}
      Spacer(Modifier.height(12.dp))
      Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Text(
            "NEARBY ADVENTURE",
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            "${game.availableSeats} seats open",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall,
        )
      }
      Text(
          text = game.title,
          style =
              MaterialTheme.typography.titleLarge.copy(
                  fontFamily = FontFamily.Serif,
                  fontWeight = FontWeight.Medium,
              ),
          modifier = Modifier.testTag(MapScreenTestTags.SELECTED_GAME).padding(top = 2.dp),
      )
      GameInfo(Icons.Default.Person, "GM ${game.gameMasterName}")
      Row(
          modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
      ) {
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
        ) {
          GameInfo(
              Icons.Default.Casino,
              game.gameSystem,
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
          )
        }
        game.distanceKm?.let {
          GameInfo(Icons.Default.LocationOn, "${"%.1f".format(it)} km from you")
        }
      }
      GameInfo(
          Icons.Default.CalendarToday,
          formatGameDate(game.dateTime),
          modifier = Modifier.padding(top = 8.dp),
      )
      Button(
          onClick = onViewDetails,
          modifier =
              Modifier.fillMaxWidth()
                  .padding(top = 12.dp)
                  .testTag(MapScreenTestTags.VIEW_GAME_DETAILS_BUTTON),
          colors =
              ButtonDefaults.buttonColors(
                  containerColor = MaterialTheme.colorScheme.primary,
                  contentColor = MaterialTheme.colorScheme.onPrimary,
              ),
          shape = RoundedCornerShape(10.dp),
      ) {
        Text("View game details")
        Spacer(Modifier.width(8.dp))
        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
      }
    }
  }
}

/** Small gold icon followed by a line of game information. */
@Composable
private fun GameInfo(icon: ImageVector, text: String, modifier: Modifier = Modifier) {
  Row(
      modifier = modifier,
      horizontalArrangement = Arrangement.spacedBy(6.dp),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(
        icon,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(14.dp),
    )
    Text(text, style = MaterialTheme.typography.bodySmall)
  }
}

// ============ Bottom navigation ============

/** App bottom bar with the Map tab highlighted. */
@Composable
private fun MapBottomNavigation(onTabSelected: (String) -> Unit) {
  Surface(
      modifier = Modifier.fillMaxWidth().testTag(MapScreenTestTags.BOTTOM_NAVIGATION),
      color = MaterialTheme.colorScheme.surface,
      border = BorderStroke(HairlineBorderWidth, MaterialTheme.colorScheme.outlineVariant),
      shadowElevation = OverlayElevation,
  ) {
    Row(
        modifier = Modifier.fillMaxWidth().height(64.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically,
    ) {
      MapBottomTab("Home", Icons.Default.Home, onTabSelected)
      MapBottomTab("Campaigns", Icons.AutoMirrored.Outlined.MenuBook, onTabSelected)
      MapBottomTab("Files", Icons.Outlined.Folder, onTabSelected)
      MapBottomTab("Map", Icons.Outlined.Map, onTabSelected, selected = true)
    }
  }
}

/** One bottom bar tab, gold and bold when [selected]. */
@Composable
private fun MapBottomTab(
    label: String,
    icon: ImageVector,
    onTabSelected: (String) -> Unit,
    selected: Boolean = false,
) {
  val tint =
      if (selected) MaterialTheme.colorScheme.primary
      else MaterialTheme.colorScheme.onSurfaceVariant
  TextButton(
      onClick = { onTabSelected(label) },
      modifier = Modifier.testTag("map_tab_$label"),
  ) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(19.dp))
      Text(
          label,
          color = tint,
          style = MaterialTheme.typography.labelSmall,
          fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
      )
    }
  }
}
