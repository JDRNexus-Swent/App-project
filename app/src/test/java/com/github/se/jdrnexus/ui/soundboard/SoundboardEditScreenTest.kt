// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.ui.soundboard

import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
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

/** Covers [SoundboardEditScreen] rendered from a [FakeSoundboardEditViewModel]. */
@RunWith(AndroidJUnit4::class)
// A tall window, so every card of the list is composed without scrolling.
@Config(qualifiers = "w411dp-h1600dp")
class SoundboardEditScreenTest {
  @get:Rule val composeTestRule = createComposeRule()

  private val byTheFire = AmbienceTrack("By the Fire", AudioFile("fire", "/a/fire.mp3", 492_000))
  private val tavern = AmbiencePlaylist("tavern", "Tavern", listOf(byTheFire))
  private val dungeon = AmbiencePlaylist("dungeon", "Dungeon")
  private val sword =
      SoundEffect("Sword Clash", AudioFile("sword", "/a/s.mp3", 3_000), SoundEffectIcon.SWORD)

  private val draft =
      Soundboard(
          id = "board",
          name = "Tides",
          playlists = listOf(tavern, dungeon),
          effects = listOf(sword),
      )

  private val ready =
      SoundboardEditUiState(
          draft = draft,
          loadState = SoundboardEditLoadState.Ready,
          selectedPlaylistId = "tavern",
          canSave = true,
      )

  private var dones = 0
  private val tabs = mutableListOf<String>()

  // ============ Load states ============

  @Test
  fun loadsTheRequestedSoundboard() {
    val vm = setContent(ready, "board")

    assertEquals(listOf("loadSoundboard(board)"), vm.calls)
  }

  @Test
  fun loadsANewDraftWhenNoIdIsGiven() {
    val vm = setContent(ready, null)

    assertEquals(listOf("loadSoundboard(null)"), vm.calls)
  }

  @Test
  fun loadingShowsASpinner() {
    composeTestRule.mainClock.autoAdvance = false
    setContent(SoundboardEditUiState())
    composeTestRule.mainClock.advanceTimeBy(100)

    composeTestRule.onNodeWithTag(C.Tag.soundboard_edit_loading).assertExists()
  }

  @Test
  fun failedLoadShowsItsReasonAndRetryReloads() {
    val vm =
        setContent(
            SoundboardEditUiState(
                loadState = SoundboardEditLoadState.Failed(SoundboardError.SOUNDBOARD_NOT_FOUND)
            )
        )

    composeTestRule
        .onNodeWithTag(C.Tag.soundboard_edit_error)
        .assertTextContains("This soundboard no longer exists.")
    composeTestRule.onNodeWithTag(C.Tag.soundboard_edit_retry).performClick()

    assertEquals(listOf("loadSoundboard(board)"), vm.callsAfterLoad())
  }

  // ============ Leaving ============

  @Test
  fun topBarBackAsksTheViewModelToLeave() {
    val vm = setContent(ready)

    composeTestRule.onNodeWithTag(C.Tag.shared_back_button).performClick()

    assertEquals(listOf("requestLeave"), vm.callsAfterLoad())
    assertEquals(0, dones)
  }

  @Test
  fun systemBackAsksTheViewModelToLeave() {
    val vm = setContent(ready)

    Espresso.pressBack()
    composeTestRule.waitForIdle()

    assertEquals(listOf("requestLeave"), vm.callsAfterLoad())
  }

  @Test
  fun discardDialogIsHiddenUntilTheViewModelShowsIt() {
    setContent(ready)

    composeTestRule.onNodeWithTag(C.Tag.soundboard_edit_discard_dialog).assertDoesNotExist()
  }

  @Test
  fun confirmingTheDialogDiscardsTheDraft() {
    val vm = setContent(ready.copy(hasUnsavedChanges = true, isDiscardDialogVisible = true))

    composeTestRule.onNodeWithText("Discard changes?").assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.soundboard_edit_discard_confirm).performClick()

    assertEquals(listOf("discard"), vm.callsAfterLoad())
  }

  @Test
  fun cancellingTheDialogKeepsEditing() {
    val vm = setContent(ready.copy(hasUnsavedChanges = true, isDiscardDialogVisible = true))

    composeTestRule.onNodeWithTag(C.Tag.soundboard_edit_discard_cancel).performClick()

    assertEquals(listOf("dismissDiscardDialog"), vm.callsAfterLoad())
  }

  @Test
  fun editorClosesOnceWhenSaved() {
    val vm = setContent(ready)

    vm.state.value = ready.copy(status = SoundboardEditStatus.SAVED)
    composeTestRule.waitForIdle()
    // A later state that is still SAVED must not close the editor a second time.
    vm.state.value = vm.state.value.copy(canSave = false)
    composeTestRule.waitForIdle()

    assertEquals(1, dones)
  }

  @Test
  fun editorClosesOnceWhenDiscarded() {
    setContent(ready.copy(status = SoundboardEditStatus.DISCARDED))

    assertEquals(1, dones)
  }

  @Test
  fun bottomBarSendsTheSelectedTab() {
    setContent(ready)

    composeTestRule.onNodeWithTag(C.Tag.bottomTab("home")).performClick()

    assertEquals(listOf("Home"), tabs)
  }

  // ============ Name ============

  @Test
  fun typingTheNameRenamesTheSoundboard() {
    val vm = setContent(ready)

    composeTestRule.onNodeWithTag(C.Tag.soundboard_edit_name).performTextReplacement("Depths")

    assertEquals(listOf("renameSoundboard(Depths)"), vm.callsAfterLoad())
  }

  @Test
  fun emptyNameErrorIsShownUnderTheField() {
    setContent(ready.copy(nameError = SoundboardNameError.EMPTY))

    composeTestRule.onNodeWithText("Give the soundboard a name.").assertIsDisplayed()
  }

  @Test
  fun tooLongNameErrorNamesTheLimit() {
    setContent(ready.copy(nameError = SoundboardNameError.TOO_LONG))

    composeTestRule.onNodeWithText("At most 80 characters.").assertIsDisplayed()
  }

  // ============ Ambience ============

  @Test
  fun playlistTabsSelectAndAddPlaylists() {
    val vm = setContent(ready)

    composeTestRule.onNodeWithText("1 track").assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.soundboardPlaylist("dungeon")).performClick()
    composeTestRule.onNodeWithTag(C.Tag.soundboard_edit_add_playlist).performClick()

    assertEquals(
        listOf("selectPlaylist(dungeon)", "addPlaylist(New playlist)"),
        vm.callsAfterLoad(),
    )
  }

  @Test
  fun addPlaylistSitsUnderTheTabs() {
    setContent(ready)

    val tab =
        composeTestRule.onNodeWithTag(C.Tag.soundboardPlaylist("tavern")).getUnclippedBoundsInRoot()
    val add =
        composeTestRule.onNodeWithTag(C.Tag.soundboard_edit_add_playlist).getUnclippedBoundsInRoot()
    assertTrue(add.top >= tab.bottom)
  }

  @Test
  fun tracksAreRenamedInPlaceAndRemoved() {
    val vm = setContent(ready)

    composeTestRule.onNodeWithText("08:12 · Ambience track").assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(C.Tag.soundboardEditTrackName("fire"))
        .performTextReplacement("Hearth")
    composeTestRule.onNodeWithTag(C.Tag.soundboardEditTrackRemove("fire")).performClick()

    assertEquals(
        listOf("renameTrack(tavern, fire, Hearth)", "removeTrack(tavern, fire)"),
        vm.callsAfterLoad(),
    )
  }

  @Test
  fun emptyPlaylistSaysItHasNoTracks() {
    setContent(
        ready.copy(draft = draft.copy(playlists = listOf(tavern.copy(tracks = emptyList()))))
    )

    composeTestRule.onNodeWithText("No tracks in this playlist").assertIsDisplayed()
  }

  // ============ Sound effects ============

  @Test
  fun effectsAreRenamedInPlaceAndRemoved() {
    val vm = setContent(ready)

    composeTestRule.onNodeWithText("1 sound").assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(C.Tag.soundboardEditEffectName("sword"))
        .performTextReplacement("Clash")
    composeTestRule.onNodeWithTag(C.Tag.soundboardEditEffectRemove("sword")).performClick()

    assertEquals(
        listOf("renameSoundEffect(sword, Clash)", "removeSoundEffect(sword)"),
        vm.callsAfterLoad(),
    )
  }

  @Test
  fun noEffectsSaysThereAreNoSounds() {
    setContent(ready.copy(draft = draft.copy(effects = emptyList())))

    composeTestRule.onNodeWithText("No sound effects yet").assertIsDisplayed()
  }

  @Test
  fun removingAFocusedRowLeavesTheNextRowUnfocused() {
    val sea = AmbienceTrack("Sea", AudioFile("sea", "/a/sea.mp3", 1_000))
    val skull = SoundEffect("Skull", AudioFile("skull", "/a/k.mp3", 1_000), SoundEffectIcon.SKULL)
    val vm =
        setContent(
            ready.copy(
                draft =
                    draft.copy(
                        playlists = listOf(tavern.copy(tracks = listOf(byTheFire, sea))),
                        effects = listOf(sword, skull),
                    )
            )
        )

    // The remove button doesn't take the focus, so the removed row's field still has it.
    composeTestRule
        .onNodeWithTag(C.Tag.soundboardEditTrackName("fire"))
        .performClick()
        .assertIsFocused()
    vm.state.value =
        vm.state.value.copy(
            draft =
                vm.state.value.draft!!.copy(playlists = listOf(tavern.copy(tracks = listOf(sea))))
        )
    composeTestRule.onNodeWithTag(C.Tag.soundboardEditTrackName("sea")).assertIsNotFocused()

    composeTestRule
        .onNodeWithTag(C.Tag.soundboardEditEffectName("sword"))
        .performClick()
        .assertIsFocused()
    vm.state.value =
        vm.state.value.copy(draft = vm.state.value.draft!!.copy(effects = listOf(skull)))
    composeTestRule.onNodeWithTag(C.Tag.soundboardEditEffectName("skull")).assertIsNotFocused()
  }

  // ============ Save ============

  @Test
  fun saveIsDisabledUntilTheDraftCanBeSaved() {
    setContent(ready.copy(canSave = false))

    composeTestRule.onNodeWithTag(C.Tag.soundboard_edit_save).assertIsNotEnabled()
  }

  @Test
  fun saveSavesTheDraft() {
    val vm = setContent(ready)

    composeTestRule.onNodeWithTag(C.Tag.soundboard_edit_save).performClick()

    assertEquals(listOf("save"), vm.callsAfterLoad())
  }

  @Test
  fun savingShowsASpinnerAndLocksTheDraft() {
    composeTestRule.mainClock.autoAdvance = false
    setContent(ready.copy(status = SoundboardEditStatus.SAVING))
    composeTestRule.mainClock.advanceTimeBy(100)

    composeTestRule.onNodeWithTag(C.Tag.soundboard_edit_save_progress).assertExists()
    composeTestRule.onNodeWithTag(C.Tag.soundboard_edit_save).assertIsNotEnabled()
    assertDraftControls(enabled = false)
  }

  @Test
  fun discardingLocksTheDraft() {
    setContent(ready.copy(status = SoundboardEditStatus.DISCARDING))

    composeTestRule.onNodeWithTag(C.Tag.soundboard_edit_save).assertIsNotEnabled()
    assertDraftControls(enabled = false)
  }

  @Test
  fun saveErrorIsShownAboveSave() {
    setContent(ready.copy(error = SoundboardEditError.Save(SoundboardError.INVALID_NAME)))

    composeTestRule
        .onNodeWithTag(C.Tag.soundboard_edit_save_error)
        .assertTextContains("Every name must be filled in.")
  }

  // ============ Helpers ============

  private fun setContent(
      uiState: SoundboardEditUiState,
      soundboardId: String? = "board",
  ): FakeSoundboardEditViewModel {
    val vm = FakeSoundboardEditViewModel(uiState)
    composeTestRule.setContent {
      SampleAppTheme(darkTheme = false) {
        SoundboardEditScreen(
            viewModel = vm,
            soundboardId = soundboardId,
            onDone = { dones++ },
            onTabSelected = { tabs += it },
        )
      }
    }
    composeTestRule.waitForIdle()
    return vm
  }

  /**
   * Checks that every control editing the draft (the soundboard, track and effect names, tabs, Add
   * playlist, remove buttons) is [enabled], or that all of them are disabled.
   */
  private fun assertDraftControls(enabled: Boolean) {
    listOf(
            C.Tag.soundboard_edit_name,
            C.Tag.soundboard_edit_add_playlist,
            C.Tag.soundboardPlaylist("tavern"),
            C.Tag.soundboardPlaylist("dungeon"),
            C.Tag.soundboardEditTrackName("fire"),
            C.Tag.soundboardEditTrackRemove("fire"),
            C.Tag.soundboardEditEffectName("sword"),
            C.Tag.soundboardEditEffectRemove("sword"),
        )
        .forEach { tag ->
          val node = composeTestRule.onNodeWithTag(tag)
          if (enabled) node.assertIsEnabled() else node.assertIsNotEnabled()
        }
  }
}

/**
 * A [SoundboardEditViewModelInterface] whose state the test sets through [state], and which records
 * every call in [calls] as `name(arguments)`.
 */
private class FakeSoundboardEditViewModel(initial: SoundboardEditUiState) :
    SoundboardEditViewModelInterface {
  val state = MutableStateFlow(initial)
  override val uiState = state
  val calls = mutableListOf<String>()

  /** The calls after the screen's initial [loadSoundboard]. */
  fun callsAfterLoad() = calls.drop(1)

  override fun renameSoundboard(name: String) {
    calls += "renameSoundboard($name)"
  }

  override fun addPlaylist(name: String) {
    calls += "addPlaylist($name)"
  }

  override fun selectPlaylist(playlistId: String) {
    calls += "selectPlaylist($playlistId)"
  }

  override fun renamePlaylist(playlistId: String, name: String) {
    calls += "renamePlaylist($playlistId, $name)"
  }

  override fun removePlaylist(playlistId: String) {
    calls += "removePlaylist($playlistId)"
  }

  override fun movePlaylist(playlistId: String, toIndex: Int) {
    calls += "movePlaylist($playlistId, $toIndex)"
  }

  override fun selectTrack(audioId: String) {
    calls += "selectTrack($audioId)"
  }

  override fun importTrack(playlistId: String, source: Uri) {
    calls += "importTrack($playlistId, $source)"
  }

  override fun renameTrack(playlistId: String, audioId: String, name: String) {
    calls += "renameTrack($playlistId, $audioId, $name)"
  }

  override fun removeTrack(playlistId: String, audioId: String) {
    calls += "removeTrack($playlistId, $audioId)"
  }

  override fun moveTrack(playlistId: String, audioId: String, toIndex: Int) {
    calls += "moveTrack($playlistId, $audioId, $toIndex)"
  }

  override fun importSoundEffect(source: Uri) {
    calls += "importSoundEffect($source)"
  }

  override fun renameSoundEffect(audioId: String, name: String) {
    calls += "renameSoundEffect($audioId, $name)"
  }

  override fun setSoundEffectIcon(audioId: String, icon: SoundEffectIcon) {
    calls += "setSoundEffectIcon($audioId, $icon)"
  }

  override fun removeSoundEffect(audioId: String) {
    calls += "removeSoundEffect($audioId)"
  }

  override fun moveSoundEffect(audioId: String, toIndex: Int) {
    calls += "moveSoundEffect($audioId, $toIndex)"
  }

  override fun save() {
    calls += "save"
  }

  override fun requestLeave() {
    calls += "requestLeave"
  }

  override fun dismissDiscardDialog() {
    calls += "dismissDiscardDialog"
  }

  override fun discard() {
    calls += "discard"
  }

  override fun loadSoundboard(soundboardId: String?) {
    calls += "loadSoundboard($soundboardId)"
  }
}
