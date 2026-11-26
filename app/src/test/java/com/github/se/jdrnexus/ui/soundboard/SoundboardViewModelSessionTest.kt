// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.github.se.jdrnexus.ui.soundboard

import com.github.se.jdrnexus.model.repository.AuthUser
import com.github.se.jdrnexus.model.soundboard.SoundboardError
import com.github.se.jdrnexus.model.soundboard.SoundboardException
import com.github.se.jdrnexus.ui.soundboard.SoundboardPlayer.Event
import com.github.se.jdrnexus.utils.BOARD_ID
import com.github.se.jdrnexus.utils.DUNGEON
import com.github.se.jdrnexus.utils.FIRE
import com.github.se.jdrnexus.utils.OTHER_USER_ID
import com.github.se.jdrnexus.utils.SWORD
import com.github.se.jdrnexus.utils.SoundboardViewModelTestFixture
import com.github.se.jdrnexus.utils.TAVERN
import com.github.se.jdrnexus.utils.THUNDER
import com.github.se.jdrnexus.utils.USER_ID
import com.github.se.jdrnexus.utils.effect
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
internal class SoundboardViewModelSessionTest : SoundboardViewModelTestFixture() {
  @Test
  fun loadShowsLoadingThenReadyForTheRequestedAccountAndBoard() = runTest {
    repository.board(BOARD_ID).value = board

    vm.loadSoundboard(BOARD_ID)
    assertEquals(SoundboardLoadState.Loading, state.loadState)
    assertNull(state.soundboard)
    runCurrent()

    assertEquals(listOf(USER_ID to BOARD_ID), repository.observations)
    assertEquals(SoundboardLoadState.Ready, state.loadState)
    assertEquals(board, state.soundboard)
    assertEquals(TAVERN, state.selectedPlaylistId)
  }

  @Test
  fun emptyBoardKeepsItsIdAndEmptyPlaylist() = runTest {
    val empty =
        board.copy(playlists = listOf(tavern.copy(tracks = emptyList())), effects = emptyList())

    load(empty)

    assertEquals(SoundboardLoadState.Empty, state.loadState)
    assertEquals(empty, state.soundboard)
    assertEquals(TAVERN, state.selectedPlaylistId)
  }

  @Test
  fun missingBoardCanRecoverWhenTheRepositoryEmitsIt() = runTest {
    vm.loadSoundboard(BOARD_ID)
    runCurrent()

    assertEquals(SoundboardLoadState.Failed(SoundboardError.SOUNDBOARD_NOT_FOUND), state.loadState)
    assertNull(state.soundboard)

    repository.board(BOARD_ID).value = board
    runCurrent()

    assertEquals(SoundboardLoadState.Ready, state.loadState)
    assertEquals(board, state.soundboard)
  }

  @Test
  fun observationFailureSurfacesTheRepositoryReason() = runTest {
    repository.observationFailure = SoundboardException(SoundboardError.NOT_SIGNED_IN)

    vm.loadSoundboard(BOARD_ID)
    runCurrent()

    assertEquals(SoundboardLoadState.Failed(SoundboardError.NOT_SIGNED_IN), state.loadState)
    assertNull(state.soundboard)
  }

  @Test
  fun blankUserDoesNotObserveAStoredBoard() = runTest {
    repository.board(BOARD_ID).value = board
    auth.authState.value = AuthUser(" ", null, null)

    vm.loadSoundboard(BOARD_ID)
    runCurrent()

    assertEquals(SoundboardLoadState.Failed(SoundboardError.NOT_SIGNED_IN), state.loadState)
    assertEquals(emptyList<Pair<String, String>>(), repository.observations)
  }

  @Test
  fun blankBoardIdDoesNotObserveAStoredBoard() = runTest {
    vm.loadSoundboard(" ")
    runCurrent()

    assertEquals(SoundboardLoadState.Failed(SoundboardError.SOUNDBOARD_NOT_FOUND), state.loadState)
    assertEquals(emptyList<Pair<String, String>>(), repository.observations)
  }

  @Test
  fun signingOutClearsTheBoardAndStopsActiveAndPendingSounds() = runTest {
    load()
    val fire = playFire()
    vm.toggleSoundEffect(SWORD)
    val sword = player.latest(SWORD)
    assertEquals(setOf(SWORD), state.pendingEffectIds)

    auth.authState.value = null
    runCurrent()
    sword.emit(Event.Started)
    repository.board(BOARD_ID).value = board.copy(name = "Old account update")
    runCurrent()

    assertEquals(
        SoundboardUiState(loadState = SoundboardLoadState.Failed(SoundboardError.NOT_SIGNED_IN)),
        state,
    )
    assertEquals(listOf(1, 1), listOf(fire, sword).map { it.closeCount })
    assertEquals(listOf(USER_ID to BOARD_ID), repository.observations)
  }

  @Test
  fun switchingAccountsObservesTheSameBoardIdForTheNewAccount() = runTest {
    load()
    val fire = playFire()
    vm.setAmbienceVolume(0.4f)
    vm.setSoundEffectVolume(0.3f)
    val otherBoard = board.copy(name = "Other account's board", playlists = listOf(dungeon))
    repository.board(BOARD_ID, OTHER_USER_ID).value = otherBoard

    auth.authState.value = AuthUser(OTHER_USER_ID, null, null)
    runCurrent()
    repository.board(BOARD_ID).value = board.copy(name = "Old account update")
    runCurrent()

    assertEquals(listOf(USER_ID to BOARD_ID, OTHER_USER_ID to BOARD_ID), repository.observations)
    assertEquals(otherBoard, state.soundboard)
    assertEquals(DUNGEON, state.selectedPlaylistId)
    assertNull(state.ambience)
    assertEquals(1, fire.closeCount)
  }

  @Test
  fun updatingTheSameAccountsProfileKeepsPlaybackAndVolume() = runTest {
    load()
    val fire = playFire()
    vm.setAmbienceVolume(0.4f)

    auth.authState.value = AuthUser(USER_ID, null, "New display name")
    runCurrent()

    assertEquals(listOf(USER_ID to BOARD_ID), repository.observations)
    assertEquals(AmbiencePlayback(TAVERN, FIRE, 0, AmbiencePlaybackStatus.PLAYING), state.ambience)
    assertEquals(0, fire.closeCount)
    assertEquals(1, fire.playCount)
    assertEquals(0.4f, fire.volume)
    assertEquals(board.copy(ambienceVolume = 0.4f), state.soundboard)
  }

  @Test
  fun signingInAfterAnUnauthenticatedLoadOpensTheRequestedBoard() = runTest {
    auth.authState.value = null
    repository.board(BOARD_ID).value = board

    vm.loadSoundboard(BOARD_ID)
    runCurrent()

    assertEquals(SoundboardLoadState.Failed(SoundboardError.NOT_SIGNED_IN), state.loadState)

    auth.authState.value = AuthUser(USER_ID, null, null)
    runCurrent()

    assertEquals(listOf(USER_ID to BOARD_ID), repository.observations)
    assertEquals(SoundboardLoadState.Ready, state.loadState)
    assertEquals(board, state.soundboard)
  }

  @Test
  fun boardEditsCancelRemovedSoundsAndPreserveRetainedPlayback() = runTest {
    load()
    val fire = playFire()
    val thunder = playEffect(THUNDER)
    vm.toggleSoundEffect(SWORD)
    runCurrent()
    val sword = player.latest(SWORD)
    val edited = board.copy(playlists = listOf(dungeon), effects = listOf(effect(THUNDER)))

    repository.board(BOARD_ID).value = edited
    runCurrent()
    sword.emit(Event.Started)
    runCurrent()

    assertEquals(edited, state.soundboard)
    assertEquals(DUNGEON, state.selectedPlaylistId)
    assertNull(state.ambience)
    assertEquals(setOf(THUNDER), state.playingEffectIds)
    assertEquals(emptySet<String>(), state.pendingEffectIds)
    assertEquals(1, fire.closeCount)
    assertEquals(1, sword.closeCount)
    assertEquals(0, thunder.closeCount)
  }

  @Test
  fun deletingTheObservedBoardStopsPlaybackAndReportsItMissing() = runTest {
    load()
    val fire = playFire()

    repository.board(BOARD_ID).value = null
    runCurrent()

    assertEquals(SoundboardLoadState.Failed(SoundboardError.SOUNDBOARD_NOT_FOUND), state.loadState)
    assertNull(state.soundboard)
    assertNull(state.ambience)
    assertEquals(1, fire.closeCount)
  }

  @Test
  fun switchingBoardsStopsOldPlaybackAndObservesOnlyTheNewBoard() = runTest {
    load()
    val fire = playFire()
    val nextBoard = board.copy(id = "other", name = "Other Game", ambienceVolume = 0f)

    vm.setAmbienceLooping(false)
    vm.setAmbienceVolume(0.4f)
    vm.setSoundEffectVolume(0.3f)
    load(nextBoard)
    repository.board(BOARD_ID).value = board.copy(name = "Old update")
    runCurrent()

    assertEquals(nextBoard, state.soundboard)
    assertEquals(SoundboardLoadState.Ready, state.loadState)
    assertEquals(true, state.isAmbienceLooping)
    assertNull(state.ambience)
    assertEquals(1, fire.closeCount)

    val nextFire = playFire()
    vm.toggleAmbienceMute()

    assertEquals(1f, state.soundboard?.ambienceVolume)
    assertEquals(1f, nextFire.volume)
  }

  @Test
  fun clearingTheSessionViewModelReleasesPlayback() = runTest {
    load()
    val fire = playFire()
    val sword = playEffect(SWORD)

    store.clear()

    assertEquals(1, fire.closeCount)
    assertEquals(1, sword.closeCount)
  }

  @Test
  fun observedEditsPreserveSessionVolumesAndApplyNewSoundboardContent() = runTest {
    load()
    val fire = playFire()
    vm.setAmbienceVolume(0.4f)
    vm.setSoundEffectVolume(0.3f)
    val edited =
        board.copy(
            name = "Renamed board",
            effects = listOf(effect(THUNDER)),
            ambienceVolume = 0.9f,
            effectsVolume = 0.8f,
        )

    repository.board(BOARD_ID).value = edited
    runCurrent()
    val thunder = playEffect(THUNDER)

    assertEquals(edited.copy(ambienceVolume = 0.4f, effectsVolume = 0.3f), state.soundboard)
    assertEquals(0.4f, fire.volume)
    assertEquals(0.3f, thunder.volume)
  }

  @Test
  fun reloadingTheCurrentBoardKeepsTheSamePlayback() = runTest {
    load()
    val fire = playFire()

    vm.loadSoundboard(BOARD_ID)
    runCurrent()

    assertEquals(listOf(USER_ID to BOARD_ID), repository.observations)
    assertEquals(0, fire.closeCount)
    assertEquals(1, fire.playCount)
    assertEquals(AmbiencePlayback(TAVERN, FIRE, 0, AmbiencePlaybackStatus.PLAYING), state.ambience)
  }
}
