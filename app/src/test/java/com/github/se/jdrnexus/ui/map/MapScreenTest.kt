// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.ui.map

import android.Manifest
import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.se.jdrnexus.R
import com.github.se.jdrnexus.resources.C
import com.github.se.jdrnexus.ui.theme.SampleAppTheme
import java.time.LocalDateTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class MapScreenTest {
  @get:Rule val composeTestRule = createComposeRule()

  private val context = ApplicationProvider.getApplicationContext<Application>()

  // ============ Search and filters ============

  @Test
  fun citySearchAndFiltersAreForwardedToViewModel() {
    val mapVM = FakeMapVM()
    composeTestRule.setContent { SampleAppTheme { MapScreen(mapVM) } }

    composeTestRule.onNodeWithTag(C.Tag.map_city_search_field).performTextInput("Lausanne")
    composeTestRule.onNodeWithTag(C.Tag.map_city_search_button).performClick()
    composeTestRule.onNodeWithTag(C.Tag.map_filters_toggle).performClick()
    composeTestRule
        .onNodeWithTag(C.Tag.map_game_system_filter)
        .performTextInput("Dungeons & Dragons")
    composeTestRule.onNodeWithTag(C.Tag.map_available_seats_filter).performClick()
    composeTestRule.onNodeWithTag(C.Tag.map_apply_filters).performClick()

    assertEquals("Lausanne", mapVM.searchedCity)
    assertEquals(1, mapVM.applyRequests)
    assertEquals("Dungeons & Dragons", mapVM.uiState.value.filters.gameSystem)
    assertEquals(true, mapVM.uiState.value.filters.onlyGamesWithAvailableSeats)
  }

  @Test
  fun filterPanelFollowsViewModelState() {
    val mapVM = FakeMapVM(MapUiState(filtersExpanded = true))
    composeTestRule.setContent { SampleAppTheme { MapScreen(mapVM) } }
    composeTestRule.onNodeWithTag(C.Tag.map_game_system_filter).assertIsDisplayed()

    composeTestRule.onNodeWithTag(C.Tag.map_filters_toggle).performClick()

    assertEquals(false, mapVM.uiState.value.filtersExpanded)
    composeTestRule.onAllNodesWithTag(C.Tag.map_game_system_filter).assertCountEquals(0)
  }

  @Test
  fun keyboardSearchKeySearchesTheTypedCity() {
    val mapVM = FakeMapVM()
    composeTestRule.setContent { SampleAppTheme { MapScreen(mapVM) } }
    val cityField = composeTestRule.onNodeWithTag(C.Tag.map_city_search_field)

    cityField.performTextInput("Lausanne")
    cityField.performImeAction()

    assertEquals("Lausanne", mapVM.searchedCity)
  }

  @Test
  fun blankSearchIsForwardedSoViewModelCanReportIt() {
    val mapVM = FakeMapVM()
    composeTestRule.setContent { SampleAppTheme { MapScreen(mapVM) } }

    composeTestRule.onNodeWithTag(C.Tag.map_city_search_button).performClick()
    assertEquals("", mapVM.searchedCity)

    mapVM.searchedCity = null
    composeTestRule.onNodeWithTag(C.Tag.map_city_search_field).performImeAction()
    assertEquals("", mapVM.searchedCity)
  }

  @Test
  fun clearingGameSystemFilterMatchesAnySystem() {
    val mapVM =
        FakeMapVM(
            MapUiState(filtersExpanded = true, filters = MapFilters(gameSystem = "Pathfinder"))
        )
    composeTestRule.setContent { SampleAppTheme { MapScreen(mapVM) } }

    composeTestRule.onNodeWithTag(C.Tag.map_game_system_filter).performTextClearance()

    assertEquals(null, mapVM.uiState.value.filters.gameSystem)
  }

  // ============ Status and retry ============

  @Test
  fun loadingShowsProgressIndicator() {
    val mapVM = FakeMapVM(MapUiState(loadingOperations = setOf(MapOperation.GAMES)))
    composeTestRule.setContent { SampleAppTheme { MapScreen(mapVM) } }

    composeTestRule
        .onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate))
        .assertIsDisplayed()
  }

  @Test
  fun loadErrorIsDisplayedAndRetryReloadsGames() {
    val mapVM =
        FakeMapVM(
            MapUiState(
                status =
                    MapStatus(
                        MapOperation.GAMES,
                        MapStatusType.ERROR,
                        "Network unavailable",
                        canRetry = true,
                    ),
            )
        )
    composeTestRule.setContent { SampleAppTheme { MapScreen(mapVM) } }

    composeTestRule.onNodeWithText("Network unavailable").assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.map_retry).performClick()

    assertEquals(2, mapVM.loadRequests)
  }

  @Test
  fun retryAfterCitySearchErrorSearchesCityAgain() {
    val mapVM =
        FakeMapVM(
            MapUiState(cityQuery = "Geneva", status = retryableError(MapOperation.CITY_SEARCH))
        )
    composeTestRule.setContent { SampleAppTheme { MapScreen(mapVM) } }

    composeTestRule.onNodeWithTag(C.Tag.map_retry).performClick()

    assertEquals("Geneva", mapVM.searchedCity)
    assertEquals(1, mapVM.loadRequests)
  }

  @Test
  fun retryAfterLocationErrorRequestsLocationAgain() {
    // A granted permission makes the request return at once instead of showing a dialog.
    shadowOf(ApplicationProvider.getApplicationContext<Application>())
        .grantPermissions(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )
    val mapVM = FakeMapVM(MapUiState(status = retryableError(MapOperation.DEVICE_LOCATION)))
    composeTestRule.setContent { SampleAppTheme { MapScreen(mapVM) } }

    composeTestRule.onNodeWithTag(C.Tag.map_retry).performClick()
    // The granted result is posted to the main thread rather than delivered during the click.
    composeTestRule.waitForIdle()

    assertEquals(listOf(true), mapVM.permissionResults)
    assertEquals(1, mapVM.loadRequests)
  }

  // ============ Selected game ============

  @Test
  fun selectedGameDetailsUseViewModelData() {
    val mapVM = FakeMapVM(MapUiState(games = listOf(SUNKEN_CROWN), selectedGame = SUNKEN_CROWN))
    var viewedGameId: String? = null
    composeTestRule.setContent {
      SampleAppTheme { MapScreen(mapVM, onViewGameDetails = { viewedGameId = it }) }
    }

    composeTestRule.onNodeWithTag(C.Tag.map_selected_game_title).assertIsDisplayed()
    composeTestRule.onNodeWithText("The Sunken Crown").assertIsDisplayed()
    composeTestRule
        .onNodeWithText(context.getString(R.string.map_game_master, "Alex"))
        .assertIsDisplayed()
    composeTestRule
        .onNodeWithText(context.resources.getQuantityString(R.plurals.map_seats_open, 2, 2))
        .assertIsDisplayed()
    composeTestRule
        .onNodeWithText(context.getString(R.string.map_distance_from_you, 3.5))
        .assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.map_view_game_details).performClick()
    assertEquals("session-1", viewedGameId)
  }

  @Test
  fun distanceIsMeasuredFromSearchedCity() {
    val mapVM =
        FakeMapVM(
            MapUiState(
                games = listOf(SUNKEN_CROWN),
                selectedGame = SUNKEN_CROWN,
                searchedCity = MapCoordinates(46.2044, 6.1432, label = "Geneva"),
            )
        )
    composeTestRule.setContent { SampleAppTheme { MapScreen(mapVM) } }

    composeTestRule
        .onNodeWithText(context.getString(R.string.map_distance_from_place, 3.5, "Geneva"))
        .assertIsDisplayed()
    composeTestRule
        .onAllNodesWithText(context.getString(R.string.map_distance_from_you, 3.5))
        .assertCountEquals(0)
  }

  @Test
  fun distanceFromUnnamedSearchedCityNamesItGenerically() {
    val mapVM =
        FakeMapVM(
            MapUiState(
                games = listOf(SUNKEN_CROWN),
                selectedGame = SUNKEN_CROWN,
                searchedCity = MapCoordinates(46.2044, 6.1432),
            )
        )
    composeTestRule.setContent { SampleAppTheme { MapScreen(mapVM) } }

    val searchedCity = context.getString(R.string.map_searched_city)
    composeTestRule
        .onNodeWithText(context.getString(R.string.map_distance_from_place, 3.5, searchedCity))
        .assertIsDisplayed()
  }

  @Test
  fun singleOpenSeatIsWrittenInSingular() {
    val game = SUNKEN_CROWN.copy(availableSeats = 1)
    val mapVM = FakeMapVM(MapUiState(games = listOf(game), selectedGame = game))
    composeTestRule.setContent { SampleAppTheme { MapScreen(mapVM) } }

    composeTestRule.onNodeWithText("1 seat open").assertIsDisplayed()
  }

  // ============ Pins ============
  // Robolectric never draws inside the Google map, so the pins are rendered on their own.

  @Test
  fun gamePinGrowsWhenSelected() {
    var selected by mutableStateOf(false)
    composeTestRule.setContent { SampleAppTheme { GamePin(selected = selected) } }
    composeTestRule.onNodeWithTag(C.Tag.map_game_pin).assertWidthIsEqualTo(36.dp)

    selected = true

    composeTestRule.onNodeWithTag(C.Tag.map_game_pin).assertWidthIsEqualTo(56.dp)
  }

  @Test
  fun userLocationPinIsLabelledYou() {
    composeTestRule.setContent { SampleAppTheme { UserLocationPin() } }

    composeTestRule.onNodeWithText(context.getString(R.string.map_you)).assertIsDisplayed()
  }

  // ============ Navigation ============

  @Test
  fun bottomNavigationForwardsSelectedTab() {
    val mapVM = FakeMapVM()
    var selectedTab: String? = null
    composeTestRule.setContent {
      SampleAppTheme { MapScreen(mapVM, onTabSelected = { selectedTab = it }) }
    }

    composeTestRule.onNodeWithTag(C.Tag.bottomTab("files")).performClick()

    assertEquals("Files", selectedTab)
  }

  // ============ Test data and fakes ============

  private companion object {
    val SUNKEN_CROWN =
        MapGameUiModel(
            id = "session-1",
            coordinates = MapCoordinates(46.5197, 6.6323),
            title = "The Sunken Crown",
            gameMasterName = "Alex",
            gameSystem = "Pathfinder",
            dateTime = LocalDateTime.of(2026, 11, 5, 19, 0),
            availableSeats = 2,
            distanceKm = 3.5,
        )
  }

  private fun retryableError(operation: MapOperation) =
      MapStatus(operation, MapStatusType.ERROR, "Something went wrong.", canRetry = true)

  private class FakeMapVM(initialState: MapUiState = MapUiState()) : MapVM {
    private val state = MutableStateFlow(initialState)
    override val uiState: StateFlow<MapUiState> = state

    var loadRequests = 0
    var searchedCity: String? = null
    var applyRequests = 0
    val permissionResults = mutableListOf<Boolean>()

    override fun loadNearbyGames() {
      loadRequests++
    }

    override fun onCityQueryChange(query: String) {
      state.value = state.value.copy(cityQuery = query)
    }

    override fun searchCity() {
      searchedCity = state.value.cityQuery
    }

    override fun selectGame(gameId: String?) = Unit

    override fun onLocationPermissionResult(granted: Boolean) {
      permissionResults += granted
    }

    override fun onFiltersExpandedChange(expanded: Boolean) {
      state.value = state.value.copy(filtersExpanded = expanded)
    }

    override fun onFiltersChange(filters: MapFilters) {
      state.value = state.value.copy(filters = filters)
    }

    override fun applyFilters() {
      applyRequests++
    }
  }
}
