// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.ui.map

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.se.jdrnexus.ui.theme.SampleAppTheme
import java.time.LocalDateTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MapScreenTest {
  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun mapStyleMatchesLightAndDarkThemes() {
    val lightStyle = mapStyleJson(isDarkTheme = false)
    val darkStyle = mapStyleJson(isDarkTheme = true)

    assertNotEquals(lightStyle, darkStyle)
    assertTrue(lightStyle.contains("#efe6da"))
    assertTrue(darkStyle.contains("#211d19"))
    assertEquals(25f, mapMarkerHue(isDarkTheme = false))
    assertEquals(45f, mapMarkerHue(isDarkTheme = true))
  }

  @Test
  fun citySearchAndFiltersAreForwardedToViewModel() {
    val mapVM = FakeMapVM()
    composeTestRule.setContent { SampleAppTheme { MapScreen(mapVM) } }

    composeTestRule.onNodeWithTag(MapScreenTestTags.CITY_SEARCH_FIELD).performTextInput("Lausanne")
    composeTestRule.onNodeWithTag(MapScreenTestTags.CITY_SEARCH_BUTTON).performClick()
    composeTestRule.onNodeWithTag(MapScreenTestTags.FILTERS_TOGGLE).performClick()
    composeTestRule
        .onNodeWithTag(MapScreenTestTags.GAME_SYSTEM_FILTER)
        .performTextInput("Dungeons & Dragons")
    composeTestRule.onNodeWithTag(MapScreenTestTags.AVAILABLE_SEATS_FILTER).performClick()
    composeTestRule.onNodeWithTag(MapScreenTestTags.APPLY_FILTERS_BUTTON).performClick()

    assertEquals("Lausanne", mapVM.searchedCity)
    assertEquals(1, mapVM.applyRequests)
    assertEquals("Dungeons & Dragons", mapVM.uiState.value.filters.gameSystem)
    assertEquals(true, mapVM.uiState.value.filters.onlyGamesWithAvailableSeats)
  }

  @Test
  fun loadErrorIsDisplayedAndRetryReloadsGames() {
    val mapVM =
        FakeMapVM(
            MapUiState(
                status = MapStatus("Network unavailable", canRetry = true),
            )
        )
    composeTestRule.setContent { SampleAppTheme { MapScreen(mapVM) } }

    composeTestRule.onNodeWithText("Network unavailable").assertIsDisplayed()
    composeTestRule.onNodeWithTag(MapScreenTestTags.RETRY_BUTTON).performClick()

    assertEquals(2, mapVM.loadRequests)
  }

  @Test
  fun selectedGameDetailsUseViewModelData() {
    val game =
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
    val mapVM =
        FakeMapVM(
            MapUiState(
                games = listOf(game),
                selectedGame = game,
            )
        )
    var viewedGameId: String? = null
    composeTestRule.setContent {
      SampleAppTheme { MapScreen(mapVM, onViewGameDetails = { viewedGameId = it }) }
    }

    composeTestRule.onNodeWithTag(MapScreenTestTags.SELECTED_GAME).assertIsDisplayed()
    composeTestRule.onNodeWithText("The Sunken Crown").assertIsDisplayed()
    composeTestRule.onNodeWithText("GM Alex").assertIsDisplayed()
    composeTestRule.onNodeWithText("2 seats open").assertIsDisplayed()
    composeTestRule.onNodeWithTag(MapScreenTestTags.VIEW_GAME_DETAILS_BUTTON).performClick()
    assertEquals("session-1", viewedGameId)
  }

  @Test
  fun bottomNavigationForwardsSelectedTab() {
    val mapVM = FakeMapVM()
    var selectedTab: String? = null
    composeTestRule.setContent {
      SampleAppTheme { MapScreen(mapVM, onTabSelected = { selectedTab = it }) }
    }

    composeTestRule.onNodeWithTag("map_tab_Files").performClick()

    assertEquals("Files", selectedTab)
  }

  private class FakeMapVM(initialState: MapUiState = MapUiState()) : MapVM {
    private val state = MutableStateFlow(initialState)
    override val uiState: StateFlow<MapUiState> = state

    var loadRequests = 0
    var searchedCity: String? = null
    var applyRequests = 0

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

    override fun onLocationPermissionResult(granted: Boolean) = Unit

    override fun onFiltersChange(filters: MapFilters) {
      state.value = state.value.copy(filters = filters)
    }

    override fun applyFilters() {
      applyRequests++
    }
  }
}
