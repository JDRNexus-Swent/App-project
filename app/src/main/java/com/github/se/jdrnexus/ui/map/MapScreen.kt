// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.ui.map

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
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
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.se.jdrnexus.R
import com.github.se.jdrnexus.resources.C
import com.github.se.jdrnexus.resources.ScreenBottomBar
import com.github.se.jdrnexus.ui.theme.LocalAppUiColors
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.MarkerComposable
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

// ============ Screen ============

/** Label of this screen's tab in the shared [ScreenBottomBar]. */
private const val MAP_TAB = "Map"

/** Space between the screen edges and the panels floating over the map. */
private val OverlayInset = 12.dp

/** Fine and coarse location, so that the user may grant either precision. */
private val LOCATION_PERMISSIONS =
    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

/**
 * Shows nearby campaign sessions and forwards every map interaction to [mapVM].
 *
 * Loads the games once when first shown. The location-permission request lives here because it
 * needs an Activity result launcher, which a ViewModel cannot hold; its outcome is reported to
 * [MapVM.onLocationPermissionResult].
 *
 * @param onTabSelected receives the label of the bottom-bar tab the user pressed.
 * @param onViewGameDetails receives the id of the selected game when the user opens it.
 */
@Composable
fun MapScreen(
    mapVM: MapVM,
    modifier: Modifier = Modifier,
    onTabSelected: (String) -> Unit = {},
    onViewGameDetails: (String) -> Unit = {},
) {
  val uiState by mapVM.uiState.collectAsState()
  // Returns at once, without a dialog, when the permission is already granted. A dismissed dialog
  // returns no result, which counts as a denial.
  val locationPermissionLauncher =
      rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
          results ->
        mapVM.onLocationPermissionResult(results.values.any { it })
      }
  val requestLocation = { locationPermissionLauncher.launch(LOCATION_PERMISSIONS) }

  // Height of the bottom overlay, kept clear on the map so Google's logo stays visible above it.
  val density = LocalDensity.current
  var bottomOverlayHeight by remember { mutableStateOf(0.dp) }

  LaunchedEffect(mapVM) { mapVM.loadNearbyGames() }

  Scaffold(
      modifier = modifier.testTag(C.Tag.map_screen),
      bottomBar = { ScreenBottomBar(selectedTab = MAP_TAB, onTabSelected = onTabSelected) },
  ) { innerPadding ->
    Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
      MapLayer(
          uiState = uiState,
          onSelectGame = mapVM::selectGame,
          bottomPadding = bottomOverlayHeight,
      )
      MapSearchBar(
          query = uiState.cityQuery,
          filters = uiState.filters,
          onQueryChange = mapVM::onCityQueryChange,
          onSearch = mapVM::searchCity,
          onFiltersChange = mapVM::onFiltersChange,
          onApplyFilters = mapVM::applyFilters,
          filtersExpanded = uiState.filtersExpanded,
          onToggleFilters = { mapVM.onFiltersExpandedChange(!uiState.filtersExpanded) },
          modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(OverlayInset),
      )
      MapBottomOverlay(
          uiState = uiState,
          onRequestLocation = requestLocation,
          // Repeats the operation that failed, as described on MapStatus.canRetry.
          onRetry = { operation ->
            when (operation) {
              MapOperation.GAMES -> mapVM.loadNearbyGames()
              MapOperation.CITY_SEARCH -> mapVM.searchCity()
              MapOperation.DEVICE_LOCATION -> requestLocation()
            }
          },
          onViewGameDetails = onViewGameDetails,
          modifier =
              Modifier.align(Alignment.BottomCenter)
                  .fillMaxWidth()
                  .onSizeChanged { bottomOverlayHeight = with(density) { it.height.toDp() } }
                  .padding(OverlayInset),
      )
    }
  }
}

/** Common look of the panels floating over the map: search bar, status banner and game card. */
@Composable
private fun MapPanel(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    borderColor: Color = LocalAppUiColors.current.outline,
    content: @Composable () -> Unit,
) {
  Surface(
      modifier = modifier,
      shape = shape,
      color = LocalAppUiColors.current.raisedSurface,
      border = BorderStroke(1.dp, borderColor),
      shadowElevation = 4.dp,
      content = content,
  )
}

// ============ Map layer ============

/** Camera shown before the ViewModel picks a target: the whole world. */
private val INITIAL_CAMERA = CameraPosition.fromLatLngZoom(LatLng(0.0, 0.0), 2f)

/**
 * Google map styled for the current theme, with a pin per game and the user's position, as in the
 * mockups (a searched city only moves the camera). Tapping a game pin selects that game.
 *
 * The camera follows [MapUiState.camera], which the ViewModel moves after a city search, a device
 * location or a game selection.
 *
 * @param bottomPadding height covered by the bottom overlay: the map keeps Google's logo above it
 *   and centers the camera in the part left visible.
 */
@Composable
private fun MapLayer(uiState: MapUiState, onSelectGame: (String) -> Unit, bottomPadding: Dp) {
  val colors = LocalAppUiColors.current
  val context = LocalContext.current
  val cameraPositionState = rememberCameraPositionState { position = INITIAL_CAMERA }
  val mapProperties =
      remember(colors.isDarkTheme) {
        val style = if (colors.isDarkTheme) R.raw.map_style_dark else R.raw.map_style_light
        MapProperties(mapStyleOptions = MapStyleOptions.loadRawResourceStyle(context, style))
      }

  LaunchedEffect(uiState.camera) {
    uiState.camera?.let { camera ->
      cameraPositionState.position =
          CameraPosition.fromLatLngZoom(camera.target.toLatLng(), camera.zoom)
    }
  }

  GoogleMap(
      modifier = Modifier.fillMaxSize().testTag(C.Tag.map_google_map),
      cameraPositionState = cameraPositionState,
      properties = mapProperties,
      // The mockups have no zoom buttons; pinch-to-zoom still works.
      uiSettings = MapUiSettings(zoomControlsEnabled = false),
      contentPadding = PaddingValues(bottom = bottomPadding),
  ) {
    uiState.games.forEach { game ->
      // One stable marker and marker state per game, even when filters or a reload change the list.
      key(game.id) {
        val selected = game.id == uiState.selectedGame?.id
        // The pin is drawn once into a bitmap and redrawn only when these keys change.
        MarkerComposable(
            selected,
            colors.isDarkTheme,
            state = rememberMarkerStateAt(game.coordinates),
            onClick = {
              onSelectGame(game.id)
              // Handled: the ViewModel moves the camera and the card shows the game, so the SDK's
              // default camera move and info window are skipped.
              true
            },
        ) {
          GamePin(selected = selected)
        }
      }
    }
    uiState.userLocation?.let { location ->
      // Anchored on the dot rather than on the "You" label below it.
      MarkerComposable(state = rememberMarkerStateAt(location), anchor = Offset(0.5f, 0.3f)) {
        UserLocationPin()
      }
    }
  }
}

/** Marker state recreated only when [coordinates] change. */
@Composable
private fun rememberMarkerStateAt(coordinates: MapCoordinates): MarkerState =
    remember(coordinates) { MarkerState(position = coordinates.toLatLng()) }

private fun MapCoordinates.toLatLng() = LatLng(latitude, longitude)

/**
 * Accent-colored pin for a campaign session: larger and filled, with a halo, when [selected].
 *
 * Internal for tests only: Robolectric never draws inside the Google map, so tests render it alone.
 */
@VisibleForTesting
@Composable
internal fun GamePin(selected: Boolean) {
  val colors = LocalAppUiColors.current
  Box(
      modifier =
          Modifier.testTag(C.Tag.map_game_pin)
              .size(if (selected) 56.dp else 36.dp)
              .background(
                  if (selected) colors.accent.copy(alpha = 0.2f) else Color.Transparent,
                  CircleShape,
              ),
      contentAlignment = Alignment.Center,
  ) {
    Box(
        modifier =
            Modifier.size(if (selected) 40.dp else 32.dp)
                .background(if (selected) colors.accent else colors.raisedSurface, CircleShape)
                .border(2.dp, colors.accent, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
      Icon(
          Icons.Default.LocationOn,
          contentDescription = null,
          tint = if (selected) colors.accentContent else colors.accent,
          modifier = Modifier.size(if (selected) 22.dp else 18.dp),
      )
    }
  }
}

/**
 * Device-location dot with a translucent halo and a "You" label. Internal for tests, as [GamePin].
 */
@VisibleForTesting
@Composable
internal fun UserLocationPin() {
  val colors = LocalAppUiColors.current
  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Box(
        modifier =
            Modifier.size(48.dp).background(colors.primaryText.copy(alpha = 0.15f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
      Box(
          modifier =
              Modifier.size(16.dp)
                  .background(colors.primaryText, CircleShape)
                  .border(3.dp, colors.raisedSurface, CircleShape)
      )
    }
    MapPanel(shape = RoundedCornerShape(8.dp)) {
      Text(
          stringResource(R.string.map_you),
          modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
          color = colors.primaryText,
          style = MaterialTheme.typography.labelSmall,
      )
    }
  }
}

// ============ Top overlay: search and filters ============

/**
 * Floating city search field, with a toggle revealing the game filters.
 *
 * The search icon and the keyboard action always forward to [onSearch]: the ViewModel validates the
 * query and reports a blank one in its status. Filter edits go to [onFiltersChange] and change the
 * visible games only once applied.
 *
 * @param filtersExpanded whether the filter controls are shown, from [MapUiState.filtersExpanded].
 * @param onToggleFilters shows or hides the filter controls.
 */
@Composable
private fun MapSearchBar(
    query: String,
    filters: MapFilters,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onFiltersChange: (MapFilters) -> Unit,
    onApplyFilters: () -> Unit,
    filtersExpanded: Boolean,
    onToggleFilters: () -> Unit,
    modifier: Modifier = Modifier,
) {
  val colors = LocalAppUiColors.current

  MapPanel(modifier = modifier) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f).testTag(C.Tag.map_city_search_field),
            label = { Text(stringResource(R.string.map_search_city_label)) },
            leadingIcon = {
              IconButton(
                  onClick = onSearch,
                  modifier = Modifier.testTag(C.Tag.map_city_search_button),
              ) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = stringResource(R.string.map_search_city_description),
                    tint = colors.accent,
                )
              }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            colors =
                OutlinedTextFieldDefaults.colors(
                    focusedTextColor = colors.primaryText,
                    unfocusedTextColor = colors.primaryText,
                    focusedLabelColor = colors.secondaryText,
                    unfocusedLabelColor = colors.secondaryText,
                    cursorColor = colors.accent,
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                ),
        )
        VerticalDivider(modifier = Modifier.height(32.dp), color = colors.outline)
        IconButton(
            onClick = onToggleFilters,
            modifier = Modifier.testTag(C.Tag.map_filters_toggle),
        ) {
          Icon(
              Icons.Default.Tune,
              contentDescription = stringResource(R.string.map_show_filters_description),
              tint = colors.accent,
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

/** Game-system field, open-seats switch, and the button applying them to the visible games. */
@Composable
private fun MapFiltersPanel(
    filters: MapFilters,
    onFiltersChange: (MapFilters) -> Unit,
    onApply: () -> Unit,
) {
  val colors = LocalAppUiColors.current
  Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    OutlinedTextField(
        value = filters.gameSystem.orEmpty(),
        onValueChange = { onFiltersChange(filters.copy(gameSystem = it.ifBlank { null })) },
        modifier = Modifier.weight(1f).testTag(C.Tag.map_game_system_filter),
        label = { Text(stringResource(R.string.map_game_system_label)) },
        placeholder = { Text(stringResource(R.string.map_game_system_any)) },
        singleLine = true,
        colors =
            OutlinedTextFieldDefaults.colors(
                focusedTextColor = colors.primaryText,
                unfocusedTextColor = colors.primaryText,
                focusedBorderColor = colors.accent,
                unfocusedBorderColor = colors.outline,
                focusedLabelColor = colors.accent,
                unfocusedLabelColor = colors.secondaryText,
                cursorColor = colors.accent,
            ),
    )
    TextButton(onClick = onApply, modifier = Modifier.testTag(C.Tag.map_apply_filters)) {
      Text(stringResource(R.string.map_apply_filters), color = colors.accent)
    }
  }
  Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Text(stringResource(R.string.map_open_seats_only), color = colors.primaryText)
    Switch(
        checked = filters.onlyGamesWithAvailableSeats,
        onCheckedChange = { onFiltersChange(filters.copy(onlyGamesWithAvailableSeats = it)) },
        modifier = Modifier.testTag(C.Tag.map_available_seats_filter),
        colors =
            SwitchDefaults.colors(
                checkedThumbColor = colors.accentContent,
                checkedTrackColor = colors.accent,
            ),
    )
  }
}

// ============ Bottom overlay: location, status and selected game ============

/**
 * Bottom of the map, from top to bottom: the device-location button, the loading/status banner when
 * there is one, and the card of the selected game when one is selected.
 *
 * @param onRetry receives the operation of the failed [MapStatus], so that it can be repeated.
 */
@Composable
private fun MapBottomOverlay(
    uiState: MapUiState,
    onRequestLocation: () -> Unit,
    onRetry: (MapOperation) -> Unit,
    onViewGameDetails: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
  val colors = LocalAppUiColors.current
  Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
    MapPanel(modifier = Modifier.align(Alignment.End), shape = CircleShape) {
      IconButton(
          onClick = onRequestLocation,
          modifier = Modifier.testTag(C.Tag.map_location_button),
      ) {
        Icon(
            Icons.Default.MyLocation,
            contentDescription = stringResource(R.string.map_use_device_location_description),
            tint = colors.accent,
        )
      }
    }
    if (uiState.isLoading || uiState.status != null) {
      MapStatusBanner(isLoading = uiState.isLoading, status = uiState.status, onRetry = onRetry)
    }
    uiState.selectedGame?.let { game ->
      SelectedGameCard(
          game = game,
          searchedCity = uiState.searchedCity,
          onViewDetails = { onViewGameDetails(game.id) },
      )
    }
  }
}

/**
 * Loading indicator and/or status message. Errors and permission denials use the error color, an
 * empty result does not; a retry action is shown when [MapStatus.canRetry].
 */
@Composable
private fun MapStatusBanner(
    isLoading: Boolean,
    status: MapStatus?,
    onRetry: (MapOperation) -> Unit,
) {
  val colors = LocalAppUiColors.current
  MapPanel(modifier = Modifier.fillMaxWidth()) {
    Row(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      if (isLoading) {
        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = colors.accent)
      }
      status?.let {
        Text(
            it.message,
            modifier = Modifier.weight(1f).testTag(C.Tag.map_status_message),
            color =
                if (it.type == MapStatusType.EMPTY) colors.secondaryText
                else MaterialTheme.colorScheme.error,
        )
        if (it.canRetry) {
          TextButton(
              onClick = { onRetry(it.operation) },
              modifier = Modifier.testTag(C.Tag.map_retry),
          ) {
            Text(stringResource(R.string.map_retry), color = colors.accent)
          }
        }
      }
    }
  }
}

/**
 * Card of the selected [game], laid out as in the mockup: seats, title, game master, game system,
 * distance, date, and a button opening the game's details.
 *
 * @param searchedCity the city the distance is measured from, from [MapUiState.searchedCity]; null
 *   when it is measured from the user.
 */
@Composable
private fun SelectedGameCard(
    game: MapGameUiModel,
    searchedCity: MapCoordinates?,
    onViewDetails: () -> Unit,
) {
  val colors = LocalAppUiColors.current
  MapPanel(modifier = Modifier.fillMaxWidth(), borderColor = colors.accent.copy(alpha = 0.4f)) {
    Column(modifier = Modifier.padding(16.dp)) {
      // Drag handle, as on a bottom sheet.
      Box(
          modifier =
              Modifier.align(Alignment.CenterHorizontally)
                  .size(width = 32.dp, height = 4.dp)
                  .background(colors.outline, RoundedCornerShape(4.dp))
      )
      Spacer(Modifier.height(12.dp))
      Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Text(
            stringResource(R.string.map_nearby_adventure),
            color = colors.accent,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            pluralStringResource(
                R.plurals.map_seats_open,
                count = game.availableSeats,
                game.availableSeats,
            ),
            color = colors.secondaryText,
            style = MaterialTheme.typography.labelSmall,
        )
      }
      // Serif title: intentional branding from the mockup.
      Text(
          text = game.title,
          color = colors.primaryText,
          style = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Serif),
          modifier = Modifier.testTag(C.Tag.map_selected_game_title),
      )
      GameInfo(Icons.Default.Person, stringResource(R.string.map_game_master, game.gameMasterName))
      Row(
          modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
      ) {
        Surface(shape = RoundedCornerShape(8.dp), color = colors.surface) {
          GameInfo(
              Icons.Default.Casino,
              game.gameSystem,
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
          )
        }
        game.distanceKm?.let { km ->
          // The ViewModel measures distances from the searched city when there is one.
          val distance =
              if (searchedCity == null) stringResource(R.string.map_distance_from_you, km)
              else
                  stringResource(
                      R.string.map_distance_from_place,
                      km,
                      searchedCity.label ?: stringResource(R.string.map_searched_city),
                  )
          GameInfo(
              Icons.Default.LocationOn,
              distance,
              modifier = Modifier.testTag(C.Tag.map_selected_game_distance),
          )
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
              Modifier.fillMaxWidth().padding(top = 12.dp).testTag(C.Tag.map_view_game_details),
          colors =
              ButtonDefaults.buttonColors(
                  containerColor = colors.accent,
                  contentColor = colors.accentContent,
              ),
          shape = RoundedCornerShape(12.dp),
      ) {
        Text(stringResource(R.string.map_view_game_details))
        Spacer(Modifier.width(8.dp))
        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
      }
    }
  }
}

/** One line of game information: a small accent icon followed by [text]. */
@Composable
private fun GameInfo(icon: ImageVector, text: String, modifier: Modifier = Modifier) {
  val colors = LocalAppUiColors.current
  Row(
      modifier = modifier,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(icon, contentDescription = null, tint = colors.accent, modifier = Modifier.size(16.dp))
    Text(text, color = colors.primaryText, style = MaterialTheme.typography.bodySmall)
  }
}

/** Formats a session date like "Saturday, Oct 10 · 7:00 PM". */
internal fun formatGameDate(dateTime: LocalDateTime, locale: Locale = Locale.getDefault()): String =
    dateTime.format(DateTimeFormatter.ofPattern("EEEE, MMM d · h:mm a", locale))
