// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.github.se.jdrnexus.ui.soundboard

import com.github.se.jdrnexus.model.soundboard.Soundboard
import com.github.se.jdrnexus.model.soundboard.SoundboardError
import com.github.se.jdrnexus.model.soundboard.SoundboardException
import com.github.se.jdrnexus.model.soundboard.SoundboardPlayer.Event
import com.github.se.jdrnexus.utils.BOARD_ID
import com.github.se.jdrnexus.utils.CROWD
import com.github.se.jdrnexus.utils.DUNGEON
import com.github.se.jdrnexus.utils.FIRE
import com.github.se.jdrnexus.utils.SWORD
import com.github.se.jdrnexus.utils.SoundboardViewModelTestFixture
import com.github.se.jdrnexus.utils.TAVERN
import com.github.se.jdrnexus.utils.THUNDER
import com.github.se.jdrnexus.utils.USER_ID
import com.github.se.jdrnexus.utils.WIND
import com.github.se.jdrnexus.utils.audio
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
internal class SoundboardViewModelTest : SoundboardViewModelTestFixture() {
  @Test
  fun browsingAnotherPlaylistKeepsTheCurrentAmbience() = runTest {
    load()
    val playback = playFire()

    vm.selectPlaylist(DUNGEON)
    vm.selectPlaylist("unknown")
    runCurrent()

    assertEquals(DUNGEON, state.selectedPlaylistId)
    assertEquals(AmbiencePlayback(TAVERN, FIRE, 0, AmbiencePlaybackStatus.PLAYING), state.ambience)
    assertEquals(0, playback.closeCount)
  }

  @Test
  fun ambienceReportsPreparationAndProgressAndResumesWithoutRestarting() = runTest {
    load()
    vm.playAmbience(TAVERN, FIRE)
    runCurrent()
    val playback = player.latest(FIRE)
    assertEquals(
        AmbiencePlayback(TAVERN, FIRE, 0, AmbiencePlaybackStatus.PREPARING),
        state.ambience,
    )
    assertEquals(audio(FIRE), playback.audio)
    assertEquals(0.7f, playback.volume)

    playback.emit(Event.Started)
    playback.emit(Event.Position(3_000))
    runCurrent()
    assertEquals(
        AmbiencePlayback(TAVERN, FIRE, 3_000, AmbiencePlaybackStatus.PLAYING),
        state.ambience,
    )

    playback.pausePosition = 3_100
    vm.pauseAmbience()
    runCurrent()
    assertEquals(
        AmbiencePlayback(TAVERN, FIRE, 3_100, AmbiencePlaybackStatus.PAUSED),
        state.ambience,
    )

    vm.playAmbience(TAVERN, FIRE)
    runCurrent()
    playback.emit(Event.Started)
    runCurrent()
    vm.playAmbience(TAVERN, FIRE)
    runCurrent()

    assertEquals(
        AmbiencePlayback(TAVERN, FIRE, 3_100, AmbiencePlaybackStatus.PLAYING),
        state.ambience,
    )
    assertEquals(listOf(playback), player.created)
    assertEquals(2, playback.playCount)
    assertEquals(1, playback.pauseCount)
  }

  @Test
  fun playingAPlaylistStartsAtTheFirstTrackAndWraps() = runTest {
    load()

    vm.playPlaylist(TAVERN)
    val fire = player.latest(FIRE)
    fire.emit(Event.Started)
    vm.selectPlaylist(DUNGEON)

    fire.emit(Event.Completed)
    runCurrent()
    val crowd = player.latest(CROWD)
    assertEquals(
        AmbiencePlayback(TAVERN, CROWD, 0, AmbiencePlaybackStatus.PREPARING),
        state.ambience,
    )

    crowd.emit(Event.Started)
    crowd.emit(Event.Completed)
    runCurrent()

    assertEquals(listOf(FIRE, CROWD, FIRE), player.created.map { it.audio.id })
    assertEquals(1, fire.closeCount)
    assertEquals(1, crowd.closeCount)
  }

  @Test
  fun disablingLoopStopsAmbienceAfterTheLastTrack() = runTest {
    load()
    vm.playAmbience(TAVERN, CROWD)
    runCurrent()
    val crowd = player.latest(CROWD)
    crowd.emit(Event.Started)
    vm.setAmbienceLooping(false)

    crowd.emit(Event.Completed)
    runCurrent()

    assertEquals(false, state.isAmbienceLooping)
    assertNull(state.ambience)
    assertEquals(listOf(crowd), player.created)
    assertEquals(1, crowd.closeCount)
  }

  @Test
  fun replacingAmbienceIgnoresLateEventsFromTheOldTrack() = runTest {
    load()
    val fire = playFire()

    vm.playAmbience(DUNGEON, WIND)
    runCurrent()
    fire.emit(Event.Completed)
    fire.emit(Event.Failed)
    runCurrent()

    assertEquals(
        AmbiencePlayback(DUNGEON, WIND, 0, AmbiencePlaybackStatus.PREPARING),
        state.ambience,
    )
    assertEquals(listOf(FIRE, WIND), player.created.map { it.audio.id })
    assertEquals(1, fire.closeCount)
    assertNull(state.error)
  }

  @Test
  fun stoppingAmbienceCancelsPendingStart() = runTest {
    load()
    vm.playAmbience(TAVERN, FIRE)
    runCurrent()
    val playback = player.latest(FIRE)

    vm.stopAmbience()
    playback.emit(Event.Started)
    runCurrent()

    assertNull(state.ambience)
    assertEquals(1, playback.closeCount)
  }

  @Test
  fun effectsCanPlayTogetherAndStoppingOneLeavesOtherSoundsRunning() = runTest {
    load()
    val fire = playFire()
    vm.toggleSoundEffect(SWORD)
    vm.toggleSoundEffect(THUNDER)
    runCurrent()
    val sword = player.latest(SWORD)
    val thunder = player.latest(THUNDER)
    assertEquals(setOf(SWORD, THUNDER), state.pendingEffectIds)
    assertEquals(emptySet<String>(), state.playingEffectIds)
    assertEquals(0.6f, sword.volume)

    sword.emit(Event.Started)
    thunder.emit(Event.Started)
    runCurrent()
    assertEquals(setOf(SWORD, THUNDER), state.playingEffectIds)
    assertEquals(emptySet<String>(), state.pendingEffectIds)

    vm.toggleSoundEffect(SWORD)
    runCurrent()

    assertEquals(setOf(THUNDER), state.playingEffectIds)
    assertEquals(1, sword.closeCount)
    assertEquals(0, thunder.closeCount)
    assertEquals(0, fire.closeCount)
  }

  @Test
  fun oneShotCompletionRemovesOnlyTheFinishedEffect() = runTest {
    load()
    val sword = playEffect(SWORD)
    val thunder = playEffect(THUNDER)

    sword.emit(Event.Completed)
    runCurrent()

    assertEquals(setOf(THUNDER), state.playingEffectIds)
    assertEquals(1, sword.closeCount)
    assertEquals(0, thunder.closeCount)
  }

  @Test
  fun effectFailureStaysVisibleUntilAnotherSoundStarts() = runTest {
    load()
    val failed = prepareEffect(SWORD)

    failed.emit(Event.Failed)
    runCurrent()

    assertEquals(SoundboardPlaybackError.Playback(SWORD), state.error)
    assertEquals(emptySet<String>(), state.pendingEffectIds)
    assertEquals(1, failed.closeCount)

    val thunder = prepareEffect(THUNDER)
    assertEquals(SoundboardPlaybackError.Playback(SWORD), state.error)
    thunder.emit(Event.Started)
    runCurrent()

    assertEquals(setOf(THUNDER), state.playingEffectIds)
    assertNull(state.error)
  }

  @Test
  fun ambienceFailureClearsTheTrackAndKeepsTheBoardAvailable() = runTest {
    load()
    val fire = playFire()

    fire.emit(Event.Failed)
    runCurrent()

    assertNull(state.ambience)
    assertEquals(1, fire.closeCount)
    assertEquals(SoundboardPlaybackError.Playback(FIRE), state.error)
    assertEquals(SoundboardLoadState.Ready, state.loadState)
  }

  @Test
  fun repeatedStopAllClearsPlayingAndPendingSoundsOnce() = runTest {
    load()
    val fire = playFire()
    val sword = playEffect(SWORD)
    val thunder = prepareEffect(THUNDER)

    vm.stopAll()
    vm.stopAll()
    thunder.emit(Event.Started)
    runCurrent()

    assertNull(state.ambience)
    assertEquals(emptySet<String>(), state.playingEffectIds)
    assertEquals(emptySet<String>(), state.pendingEffectIds)
    assertEquals(listOf(1, 1, 1), listOf(fire, sword, thunder).map { it.closeCount })
  }

  @Test
  fun slidersApplyLiveAndSaveOnlyOnRelease() = runTest {
    load()
    val fire = playFire()
    val sword = playEffect(SWORD)

    vm.setAmbienceVolume(0.4f)

    assertEquals(0.4f, fire.volume)
    assertEquals(0.6f, sword.volume)

    vm.setSoundEffectVolume(0.3f)

    assertEquals(0.4f, fire.volume)
    assertEquals(0.3f, sword.volume)
    assertEquals(board.copy(ambienceVolume = 0.4f, effectsVolume = 0.3f), state.soundboard)
    runCurrent()
    assertEquals(emptyList<Pair<String, Soundboard>>(), repository.saves)
    assertEquals(board, repository.board(BOARD_ID).value)

    val edited = board.copy(name = "Edited board")
    publishBoard(edited)

    vm.saveVolumes()
    runCurrent()

    val saved = edited.copy(ambienceVolume = 0.4f, effectsVolume = 0.3f)
    assertEquals(listOf(USER_ID to BOARD_ID), repository.observations)
    assertEquals(listOf(USER_ID to saved), repository.saves)
    assertEquals(saved, repository.board(BOARD_ID).value)
  }

  @Test
  fun muteTogglesSilencePlaybackWithoutMovingTheSliders() = runTest {
    load()
    val fire = playFire()
    val sword = playEffect(SWORD)

    vm.toggleAmbienceMute()
    vm.toggleSoundEffectMute()

    assertEquals(0f, fire.volume)
    assertEquals(0f, sword.volume)
    assertEquals(true, state.isAmbienceMuted)
    assertEquals(true, state.isSoundEffectsMuted)
    assertEquals(board, state.soundboard)

    val thunder = playEffect(THUNDER)
    assertEquals(0f, thunder.volume)

    vm.toggleAmbienceMute()
    vm.toggleSoundEffectMute()

    assertEquals(false, state.isAmbienceMuted)
    assertEquals(false, state.isSoundEffectsMuted)
    assertEquals(0.7f, fire.volume)
    assertEquals(0.6f, sword.volume)
    assertEquals(0.6f, thunder.volume)
    runCurrent()
    assertEquals(emptyList<Pair<String, Soundboard>>(), repository.saves)
  }

  @Test
  fun sliderClampsOutOfRangeValuesAndIgnoresNaN() = runTest {
    load()
    val fire = playFire()
    val sword = playEffect(SWORD)

    vm.setAmbienceVolume(Float.NaN)
    vm.setSoundEffectVolume(1.5f)

    assertEquals(board.copy(effectsVolume = 1f), state.soundboard)
    assertEquals(0.7f, fire.volume)
    assertEquals(1f, sword.volume)
  }

  @Test
  fun failedVolumeSaveKeepsLiveGainUntilAReleaseSucceeds() = runTest {
    load()
    val fire = playFire()
    vm.setAmbienceVolume(0.4f)
    repository.saveFailure = SoundboardException(SoundboardError.STORAGE_FULL)

    vm.saveVolumes()
    runCurrent()
    playEffect(SWORD)

    assertEquals(SoundboardPlaybackError.SaveVolume(SoundboardError.STORAGE_FULL), state.error)
    assertEquals(board, repository.board(BOARD_ID).value)
    assertEquals(0.4f, fire.volume)
    assertEquals(0.4f, state.soundboard?.ambienceVolume)

    repository.saveFailure = null
    vm.saveVolumes()
    runCurrent()

    assertNull(state.error)
    assertEquals(board.copy(ambienceVolume = 0.4f), repository.board(BOARD_ID).value)
  }

  @Test
  fun playerCreationFailureReportsTheSoundWithoutTrackingIt() = runTest {
    load()
    player.failNextCreate = true

    vm.toggleSoundEffect(SWORD)

    assertEquals(SoundboardPlaybackError.Playback(SWORD), state.error)
    assertEquals(emptySet<String>(), state.pendingEffectIds)
    assertEquals(emptySet<String>(), state.playingEffectIds)
  }

  @Test
  fun trackMenuReflectsWhetherItIsExpanded() = runTest {
    vm.setTrackMenuExpanded(true)
    assertEquals(true, state.isTrackMenuExpanded)

    vm.setTrackMenuExpanded(false)
    assertEquals(false, state.isTrackMenuExpanded)
  }

  @Test
  fun synchronousStartFailureReleasesEffectAndAllowsRetry() = runTest {
    load()
    player.failNextStart = true

    val failed = prepareEffect(SWORD)

    assertEquals(1, failed.closeCount)
    assertEquals(emptySet<String>(), state.pendingEffectIds)
    assertEquals(SoundboardPlaybackError.Playback(SWORD), state.error)

    val retry = playEffect(SWORD)

    assertEquals(listOf(failed, retry), player.created)
    assertEquals(setOf(SWORD), state.playingEffectIds)
    assertNull(state.error)
  }

  @Test
  fun invalidPlaybackIdsLeaveCurrentPlaybackAlone() = runTest {
    load()
    val fire = playFire()

    vm.playAmbience(DUNGEON, FIRE)
    vm.toggleSoundEffect(FIRE)
    runCurrent()

    assertEquals(listOf(fire), player.created)
    assertEquals(0, fire.closeCount)
    assertEquals(AmbiencePlayback(TAVERN, FIRE, 0, AmbiencePlaybackStatus.PLAYING), state.ambience)
  }
}
