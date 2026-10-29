// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.github.se.jdrnexus.ui.map

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.se.jdrnexus.ui.theme.SampleAppTheme
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MapScreenTest {
  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun citySearchAndFiltersAreForwardedToViewModel() {
    val mapVM = FakeMapVM()
    composeTestRule.setContent { SampleAppTheme { MapScreen(mapVM) } }

    composeTestRule.onNodeWithTag(MapScreenTestTags.CITY_SEARCH_FIELD).performTextInput("Lausanne")
    composeTestRule.onNodeWithTag(MapScreenTestTags.CITY_SEARCH_BUTTON).performClick()
    composeTestRule
        .onNodeWithTag(MapScreenTestTags.GAME_SYSTEM_FILTER)
        .performTextInput("Dungeons & Dragons")
    composeTestRule.onNodeWithTag(MapScreenTestTags.APPLY_FILTERS_BUTTON).performClick()

    assertEquals("Lausanne", mapVM.searchedCity)
    assertEquals("Dungeons & Dragons", mapVM.appliedFilters?.gameSystem)
  }

  @Test
  fun loadErrorIsDisplayedAndRetryReloadsGames() {
    val mapVM =
        FakeMapVM(
            MapUiState(
                loadState = MapLoadState.Error("Network unavailable"),
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
            date = LocalDate.of(2026, 11, 5),
            availableSeats = 2,
            distanceKm = 3.5,
        )
    val mapVM = FakeMapVM(MapUiState(games = listOf(game), selectedGame = game))
    composeTestRule.setContent { SampleAppTheme { MapScreen(mapVM) } }

    composeTestRule.onNodeWithTag(MapScreenTestTags.SELECTED_GAME).assertIsDisplayed()
    composeTestRule.onNodeWithText("The Sunken Crown").assertIsDisplayed()
    composeTestRule.onNodeWithText("Game Master: Alex").assertIsDisplayed()
    composeTestRule.onNodeWithText("2 seats available · 3.5 km away").assertIsDisplayed()
  }

  private class FakeMapVM(initialState: MapUiState = MapUiState()) : MapVM {
    private val state = MutableStateFlow(initialState)
    override val uiState: StateFlow<MapUiState> = state

    var loadRequests = 0
    var searchedCity: String? = null
    var appliedFilters: MapFilters? = null

    override fun loadNearbyGames() {
      loadRequests++
    }

    override fun searchCity(query: String) {
      searchedCity = query
    }

    override fun selectGame(gameId: String?) = Unit

    override fun updateUserLocation() = Unit

    override fun onLocationPermissionResult(granted: Boolean) = Unit

    override fun applyFilters(filters: MapFilters) {
      appliedFilters = filters
    }
  }
}
