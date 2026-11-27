// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.ui.soundboard

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.se.jdrnexus.model.soundboard.AmbiencePlaylist
import com.github.se.jdrnexus.model.soundboard.AmbienceTrack
import com.github.se.jdrnexus.model.soundboard.AudioFile
import com.github.se.jdrnexus.model.soundboard.SoundEffect
import com.github.se.jdrnexus.model.soundboard.SoundEffectIcon
import com.github.se.jdrnexus.model.soundboard.Soundboard
import com.github.se.jdrnexus.model.soundboard.SoundboardError
import com.github.se.jdrnexus.resources.C
import com.github.se.jdrnexus.ui.theme.SampleAppTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Covers [SoundboardScreen] rendered from a [FakeSoundboardViewModel]. */
@RunWith(AndroidJUnit4::class)
// A tall window, so every section of the list is composed without scrolling.
@Config(qualifiers = "w411dp-h1600dp")
class SoundboardScreenTest {
  @get:Rule val composeTestRule = createComposeRule()

  private val byTheFire = AmbienceTrack("By the fire", AudioFile("fire", "/a/fire.mp3", 492_000))
  private val murmurs = AmbienceTrack("Tavern murmurs", AudioFile("murmurs", "/a/m.mp3", 400_000))
  private val tavern = AmbiencePlaylist("tavern", "Tavern", listOf(byTheFire, murmurs))
  private val dungeon = AmbiencePlaylist("dungeon", "Dungeon")
  private val battle = AmbienceTrack("Battle drums", AudioFile("drums", "/a/drums.mp3", 300_000))
  private val combat = AmbiencePlaylist("combat", "Combat", listOf(battle))

  /** One effect per icon, so every icon is drawn once. */
  private val effects =
      SoundEffectIcon.entries.map {
        SoundEffect(it.name.lowercase(), AudioFile("fx_${it.name}", "/a/${it.name}.mp3", 3_000), it)
      }
  private val swordId = "fx_SWORD"

  private val board =
      Soundboard(
          id = "board",
          name = "Tides",
          playlists = listOf(tavern, dungeon, combat),
          effects = effects,
          ambienceVolume = AMBIENCE_VOLUME,
          effectsVolume = EFFECTS_VOLUME,
      )

  private val ready =
      SoundboardUiState(
          soundboard = board,
          loadState = SoundboardLoadState.Ready,
          selectedPlaylistId = "tavern",
      )

  private val playing =
      ready.copy(
          ambience = AmbiencePlayback("tavern", "fire", 154_000, AmbiencePlaybackStatus.PLAYING)
      )

  private val paused =
      playing.copy(ambience = playing.ambience!!.copy(status = AmbiencePlaybackStatus.PAUSED))

  private val preparing =
      playing.copy(ambience = playing.ambience!!.copy(status = AmbiencePlaybackStatus.PREPARING))

  private companion object {
    // Each sample volume and the text the screen shows for it.
    const val AMBIENCE_VOLUME = 0.7f
    const val AMBIENCE_VOLUME_TEXT = "70 %"
    const val EFFECTS_VOLUME = 0.4f
    const val EFFECTS_VOLUME_TEXT = "40 %"
  }

  private var backs = 0
  private val edits = mutableListOf<String?>()
  private val tabs = mutableListOf<String>()

  // ============ Load states ============

  @Test
  fun loadsTheRequestedSoundboardOnce() {
    val vm = setContent(SoundboardUiState(loadState = SoundboardLoadState.Empty), "board")

    assertEquals(listOf("loadSoundboard(board)"), vm.calls)
  }

  @Test
  fun loadsTheFirstSoundboardWhenNoIdIsGiven() {
    val vm = setContent(SoundboardUiState(loadState = SoundboardLoadState.Empty), null)

    assertEquals(listOf("loadSoundboard(null)"), vm.calls)
  }

  @Test
  fun loadingShowsASpinner() {
    composeTestRule.mainClock.autoAdvance = false
    setContent(SoundboardUiState())
    composeTestRule.mainClock.advanceTimeBy(100)

    composeTestRule.onNodeWithTag(C.Tag.soundboard_loading).assertExists()
  }

  @Test
  fun failedLoadShowsItsReasonAndRetryReloads() {
    val vm =
        setContent(
            SoundboardUiState(
                loadState = SoundboardLoadState.Failed(SoundboardError.SOUNDBOARD_NOT_FOUND)
            ),
            "board",
        )

    composeTestRule
        .onNodeWithTag(C.Tag.soundboard_error)
        .assertTextContains("This soundboard no longer exists.")
    composeTestRule.onNodeWithTag(C.Tag.soundboard_retry).performClick()

    assertEquals(listOf("loadSoundboard(board)", "loadSoundboard(board)"), vm.calls)
  }

  @Test
  fun everyFailureReasonHasItsOwnMessage() {
    val vm = setContent(SoundboardUiState())
    val messages =
        SoundboardError.entries.map { reason ->
          vm.state.value = SoundboardUiState(loadState = SoundboardLoadState.Failed(reason))
          composeTestRule.waitForIdle()
          composeTestRule.onNodeWithTag(C.Tag.soundboard_error).fetchText()
        }

    assertEquals(SoundboardError.entries.size, messages.toSet().size)
  }

  @Test
  fun emptySoundboardOffersTheEditorOnANewSoundboard() {
    setContent(SoundboardUiState(loadState = SoundboardLoadState.Empty))

    composeTestRule.onNodeWithTag(C.Tag.soundboard_empty).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.soundboard_edit).performClick()

    assertEquals(listOf<String?>(null), edits)
  }

  @Test
  fun emptySavedSoundboardOpensTheEditorOnIt() {
    setContent(
        SoundboardUiState(soundboard = Soundboard("saved"), loadState = SoundboardLoadState.Empty)
    )

    composeTestRule.onNodeWithTag(C.Tag.soundboard_edit).performClick()

    assertEquals(listOf<String?>("saved"), edits)
  }

  // ============ Bars and editor ============

  @Test
  fun backTabsAndEditUseTheirCallbacks() {
    setContent(ready)

    composeTestRule.onNodeWithTag(C.Tag.shared_back_button).performClick()
    composeTestRule.onNodeWithTag(C.Tag.bottomTab("files")).performClick()
    composeTestRule.onNodeWithTag(C.Tag.soundboard_edit).performClick()

    assertEquals(1, backs)
    assertEquals(listOf("Files"), tabs)
    assertEquals(listOf<String?>("board"), edits)
  }

  // ============ Volume ============

  @Test
  fun volumesShowTheirPercentage() {
    setContent(ready)

    composeTestRule.onNodeWithText(AMBIENCE_VOLUME_TEXT).assertIsDisplayed()
    composeTestRule.onNodeWithText(EFFECTS_VOLUME_TEXT).assertIsDisplayed()
  }

  @Test
  fun slidersApplyTheNewVolumeThenSaveIt() {
    val vm = setContent(ready)

    composeTestRule.onNodeWithTag(C.Tag.soundboard_ambience_volume).setProgress(0.5f)
    composeTestRule.onNodeWithTag(C.Tag.soundboard_effects_volume).setProgress(0.25f)

    assertEquals(
        listOf(
            "setAmbienceVolume(0.5)",
            "saveVolumes",
            "setSoundEffectVolume(0.25)",
            "saveVolumes",
        ),
        vm.callsAfterLoad(),
    )
  }

  @Test
  fun muteButtonsToggleMuteAndShowIt() {
    val vm = setContent(ready.copy(isAmbienceMuted = true))

    composeTestRule.onNodeWithTag(C.Tag.soundboard_ambience_mute).performClick()
    composeTestRule.onNodeWithTag(C.Tag.soundboard_effects_mute).performClick()

    assertEquals(listOf("toggleAmbienceMute", "toggleSoundEffectMute"), vm.callsAfterLoad())
    composeTestRule.onNodeWithContentDescription("Unmute").assertIsDisplayed()
    composeTestRule.onNodeWithContentDescription("Mute").assertIsDisplayed()
  }

  @Test
  fun volumeSaveErrorIsShownUnderTheVolume() {
    setContent(ready.copy(error = SoundboardPlaybackError.SaveVolume(SoundboardError.UNKNOWN)))

    composeTestRule
        .onNodeWithTag(C.Tag.soundboard_volume_error)
        .assertTextContains("The volume could not be saved.")
  }

  // ============ Ambience ============

  @Test
  fun tappingAPlaylistTabSelectsIt() {
    val vm = setContent(ready)

    composeTestRule.onNodeWithTag(C.Tag.soundboardPlaylist("dungeon")).performClick()

    assertEquals(listOf("selectPlaylist(dungeon)"), vm.callsAfterLoad())
  }

  @Test
  fun trackFieldOpensTheMenuThroughTheViewModel() {
    val vm = setContent(ready)

    composeTestRule.onNodeWithText("Choose a track").assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.soundboard_track_menu).performClick()

    assertEquals(listOf("setTrackMenuExpanded(true)"), vm.callsAfterLoad())
  }

  @Test
  fun trackFieldDescribesTheSelectedPlaylist() {
    val drips = AmbienceTrack("Drips", AudioFile("drips", "/a/drips.mp3", 60_000))
    val crypt = AmbiencePlaylist("crypt", "Crypt", listOf(drips))
    setContent(
        playing.copy(
            soundboard = board.copy(playlists = listOf(tavern, crypt)),
            selectedPlaylistId = "crypt",
        )
    )

    composeTestRule.onNodeWithText("Choose a track").assertIsDisplayed()
    composeTestRule.onNodeWithText("Playing · Tavern").assertDoesNotExist()
    composeTestRule.onNodeWithContentDescription("Playing").assertIsDisplayed()
  }

  @Test
  fun pickingATrackPlaysItAndClosesTheMenu() {
    val vm = setContent(ready.copy(isTrackMenuExpanded = true))

    composeTestRule.onNodeWithTag(C.Tag.soundboardTrack("murmurs")).performClick()

    assertEquals(
        listOf("playAmbience(tavern, murmurs)", "setTrackMenuExpanded(false)"),
        vm.callsAfterLoad(),
    )
  }

  @Test
  fun trackMenuStaysClosedUntilTheViewModelOpensIt() {
    setContent(ready)

    composeTestRule.onNodeWithTag(C.Tag.soundboardTrack("murmurs")).assertDoesNotExist()
  }

  @Test
  fun dismissingTheMenuClosesItThroughTheViewModel() {
    val vm = setContent(ready.copy(isTrackMenuExpanded = true))

    Espresso.pressBack()
    composeTestRule.waitForIdle()

    assertEquals(listOf("setTrackMenuExpanded(false)"), vm.callsAfterLoad())
  }

  @Test
  fun selectedPlaylistTabIsMarkedSelected() {
    setContent(ready)

    composeTestRule.onNodeWithTag(C.Tag.soundboardPlaylist("tavern")).assertIsSelected()
    composeTestRule.onNodeWithTag(C.Tag.soundboardPlaylist("dungeon")).assertIsNotSelected()
  }

  @Test
  fun playlistWithoutTracksCannotBePlayed() {
    setContent(ready.copy(selectedPlaylistId = "dungeon"))

    composeTestRule.onNodeWithTag(C.Tag.soundboard_play_pause).assertIsNotEnabled()
    composeTestRule.onNodeWithTag(C.Tag.soundboard_track_menu).assertIsNotEnabled()
    composeTestRule.onNodeWithText("No tracks in this playlist").assertIsDisplayed()
  }

  @Test
  fun playStartsTheSelectedPlaylistWhenNothingPlays() {
    val vm = setContent(ready)

    composeTestRule.onNodeWithTag(C.Tag.soundboard_play_pause).assertIsEnabled().performClick()

    assertEquals(listOf("playPlaylist(tavern)"), vm.callsAfterLoad())
  }

  @Test
  fun timeIsHiddenUntilATrackPlays() {
    setContent(ready)

    composeTestRule.onNodeWithTag(C.Tag.soundboard_elapsed).assertDoesNotExist()
    composeTestRule.onNodeWithTag(C.Tag.soundboard_looping).assertIsDisplayed()
  }

  @Test
  fun playingAmbienceMarksItsPlaylistAndPausesOnTap() {
    val vm = setContent(playing)

    composeTestRule.onNodeWithContentDescription("Playing").assertIsDisplayed()
    composeTestRule.onNodeWithText("Playing · Tavern").assertIsDisplayed()
    composeTestRule.onNodeWithText("By the fire").assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.soundboard_elapsed).assertTextContains("02:34 / 08:12")
    composeTestRule.onNodeWithTag(C.Tag.soundboard_play_pause).performClick()

    assertEquals(listOf("pauseAmbience"), vm.callsAfterLoad())
  }

  @Test
  fun durationsPastAnHourShowTheHours() {
    assertEquals("1:15:00", formatDuration(4_500_000))
  }

  @Test
  fun pausedTrackResumesOnTap() {
    val vm = setContent(paused)

    composeTestRule.onNodeWithText("Paused · Tavern").assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.soundboard_play_pause).performClick()

    assertEquals(listOf("playAmbience(tavern, fire)"), vm.callsAfterLoad())
  }

  @Test
  fun pausedTrackOfAnotherPlaylistStartsTheSelectedOne() {
    val vm = setContent(paused.copy(selectedPlaylistId = "combat"))

    composeTestRule.onNodeWithTag(C.Tag.soundboard_play_pause).performClick()

    assertEquals(listOf("playPlaylist(combat)"), vm.callsAfterLoad())
  }

  @Test
  fun pausedTrackCannotStartAnEmptyPlaylist() {
    setContent(paused.copy(selectedPlaylistId = "dungeon"))

    composeTestRule.onNodeWithTag(C.Tag.soundboard_play_pause).assertIsNotEnabled()
  }

  @Test
  fun preparingTrackShowsASpinnerAndDisablesPlayPause() {
    composeTestRule.mainClock.autoAdvance = false
    setContent(preparing)
    composeTestRule.mainClock.advanceTimeBy(100)

    composeTestRule.onNodeWithTag(C.Tag.soundboard_preparing).assertExists()
    composeTestRule.onNodeWithTag(C.Tag.soundboard_play_pause).assertIsNotEnabled()
    composeTestRule.onNodeWithText("Starting · Tavern").assertExists()
  }

  @Test
  fun loopingToggleShowsAndChangesLooping() {
    val vm = setContent(ready)

    composeTestRule.onNodeWithTag(C.Tag.soundboard_looping).assertIsOn().performClick()
    vm.state.value = ready.copy(isAmbienceLooping = false)
    composeTestRule.onNodeWithTag(C.Tag.soundboard_looping).assertIsOff()

    assertEquals(listOf("setAmbienceLooping(false)"), vm.callsAfterLoad())
  }

  @Test
  fun trackPlaybackErrorIsShownUnderTheAmbience() {
    setContent(playing.copy(error = SoundboardPlaybackError.Playback("fire")))

    composeTestRule
        .onNodeWithTag(C.Tag.soundboard_ambience_error)
        .assertTextContains("This sound could not be played.")
    composeTestRule.onNodeWithTag(C.Tag.soundboard_effects_error).assertDoesNotExist()
  }

  @Test
  fun playingTrackPausesFromAnotherPlaylistTab() {
    val vm = setContent(playing.copy(selectedPlaylistId = "combat"))

    composeTestRule.onNodeWithTag(C.Tag.soundboard_play_pause).performClick()

    assertEquals(listOf("pauseAmbience"), vm.callsAfterLoad())
  }

  @Test
  fun preparingTrackShowsASpinnerFromAnotherPlaylistTab() {
    composeTestRule.mainClock.autoAdvance = false
    setContent(preparing.copy(selectedPlaylistId = "combat"))
    composeTestRule.mainClock.advanceTimeBy(100)

    composeTestRule.onNodeWithTag(C.Tag.soundboard_preparing).assertExists()
    composeTestRule.onNodeWithTag(C.Tag.soundboard_play_pause).assertIsNotEnabled()
  }

  // ============ Sound effects ============

  @Test
  fun effectsToggleOnTap() {
    val vm = setContent(ready)

    composeTestRule.onNodeWithTag(C.Tag.soundboardEffect(swordId)).performClick()

    assertEquals(listOf("toggleSoundEffect($swordId)"), vm.callsAfterLoad())
  }

  @Test
  fun effectsShowTheirCount() {
    setContent(ready.copy(soundboard = board.copy(effects = effects.take(9))))

    composeTestRule.onNodeWithText("9 sounds").assertIsDisplayed()
  }

  @Test
  fun manyEffectsAskToSwipe() {
    setContent(ready)

    composeTestRule.onNodeWithText("10 sounds · Swipe →").assertIsDisplayed()
  }

  @Test
  // As wide as a phone in landscape, so the four columns of effects fit without scrolling.
  @Config(qualifiers = "w891dp-h1600dp")
  fun effectsThatFitInAWideWindowDoNotAskToSwipe() {
    setContent(ready)

    composeTestRule.onNodeWithText("10 sounds").assertIsDisplayed()
  }

  @Test
  fun effectsReadRowByRowInTheirOrder() {
    setContent(ready)
    val first = effectBounds(effects[0])
    val second = effectBounds(effects[1])

    assertEquals(first.top, second.top)
    assertTrue(second.left > first.left)
  }

  @Test
  fun playingEffectIsMarkedSelected() {
    setContent(ready.copy(playingEffectIds = setOf(swordId)))

    composeTestRule.onNodeWithTag(C.Tag.soundboardEffect(swordId)).assertIsSelected()
    composeTestRule.onNodeWithTag(C.Tag.soundboardEffect("fx_FIRE")).assertIsNotSelected()
  }

  @Test
  fun pendingEffectIsDisabled() {
    setContent(ready.copy(pendingEffectIds = setOf(swordId)))

    composeTestRule.onNodeWithTag(C.Tag.soundboardEffect(swordId)).assertIsNotEnabled()
    composeTestRule.onNodeWithTag(C.Tag.soundboardEffect("fx_FIRE")).assertIsEnabled()
  }

  @Test
  fun effectPlaybackErrorIsShownUnderTheEffects() {
    setContent(ready.copy(error = SoundboardPlaybackError.Playback(swordId)))

    composeTestRule
        .onNodeWithTag(C.Tag.soundboard_effects_error)
        .assertTextContains("This sound could not be played.")
    composeTestRule.onNodeWithTag(C.Tag.soundboard_ambience_error).assertDoesNotExist()
  }

  // ============ Helpers ============

  private fun setContent(
      uiState: SoundboardUiState,
      soundboardId: String? = "board",
  ): FakeSoundboardViewModel {
    val vm = FakeSoundboardViewModel(uiState)
    composeTestRule.setContent {
      SampleAppTheme(darkTheme = false) {
        SoundboardScreen(
            viewModel = vm,
            soundboardId = soundboardId,
            onBack = { backs++ },
            onEdit = { edits += it },
            onTabSelected = { tabs += it },
        )
      }
    }
    composeTestRule.waitForIdle()
    return vm
  }

  private fun effectBounds(effect: SoundEffect) =
      composeTestRule
          .onNodeWithTag(C.Tag.soundboardEffect(effect.audio.id))
          .getUnclippedBoundsInRoot()

  private fun androidx.compose.ui.test.SemanticsNodeInteraction.setProgress(value: Float) =
      performSemanticsAction(SemanticsActions.SetProgress) { it(value) }

  private fun androidx.compose.ui.test.SemanticsNodeInteraction.fetchText(): String =
      fetchSemanticsNode()
          .config[androidx.compose.ui.semantics.SemanticsProperties.Text]
          .joinToString { it.text }
}

/**
 * A [SoundboardViewModelInterface] whose state the test sets through [state], and which records
 * every call in [calls] as `name(arguments)`.
 */
private class FakeSoundboardViewModel(initial: SoundboardUiState) : SoundboardViewModelInterface {
  val state = MutableStateFlow(initial)
  override val uiState = state
  val calls = mutableListOf<String>()

  /** The calls after the screen's initial [loadSoundboard]. */
  fun callsAfterLoad() = calls.drop(1)

  override fun selectPlaylist(playlistId: String) {
    calls += "selectPlaylist($playlistId)"
  }

  override fun playAmbience(playlistId: String, audioId: String) {
    calls += "playAmbience($playlistId, $audioId)"
  }

  override fun pauseAmbience() {
    calls += "pauseAmbience"
  }

  override fun playPlaylist(playlistId: String) {
    calls += "playPlaylist($playlistId)"
  }

  override fun stopAmbience() {
    calls += "stopAmbience"
  }

  override fun setAmbienceLooping(isLooping: Boolean) {
    calls += "setAmbienceLooping($isLooping)"
  }

  override fun toggleSoundEffect(audioId: String) {
    calls += "toggleSoundEffect($audioId)"
  }

  override fun setAmbienceVolume(volume: Float) {
    calls += "setAmbienceVolume($volume)"
  }

  override fun setSoundEffectVolume(volume: Float) {
    calls += "setSoundEffectVolume($volume)"
  }

  override fun saveVolumes() {
    calls += "saveVolumes"
  }

  override fun toggleAmbienceMute() {
    calls += "toggleAmbienceMute"
  }

  override fun toggleSoundEffectMute() {
    calls += "toggleSoundEffectMute"
  }

  override fun stopAll() {
    calls += "stopAll"
  }

  override fun setTrackMenuExpanded(isExpanded: Boolean) {
    calls += "setTrackMenuExpanded($isExpanded)"
  }

  override fun loadSoundboard(soundboardId: String?) {
    calls += "loadSoundboard($soundboardId)"
  }
}
