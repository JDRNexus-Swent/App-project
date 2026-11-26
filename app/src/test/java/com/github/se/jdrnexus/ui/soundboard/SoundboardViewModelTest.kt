// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.github.se.jdrnexus.ui.soundboard

import com.github.se.jdrnexus.model.soundboard.Soundboard
import com.github.se.jdrnexus.ui.soundboard.SoundboardPlayer.Event
import com.github.se.jdrnexus.utils.BOARD_ID
import com.github.se.jdrnexus.utils.CROWD
import com.github.se.jdrnexus.utils.DUNGEON
import com.github.se.jdrnexus.utils.FIRE
import com.github.se.jdrnexus.utils.SWORD
import com.github.se.jdrnexus.utils.SoundboardViewModelTestFixture
import com.github.se.jdrnexus.utils.TAVERN
import com.github.se.jdrnexus.utils.THUNDER
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
  fun ambienceAdvancesInPlaylistOrderAndWraps() = runTest {
    load()
    val fire = playFire()
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
    vm.toggleSoundEffect(SWORD)
    runCurrent()
    val failed = player.latest(SWORD)

    failed.emit(Event.Failed)
    runCurrent()

    assertEquals(SoundboardPlaybackError.Playback(SWORD), state.error)
    assertEquals(emptySet<String>(), state.pendingEffectIds)
    assertEquals(1, failed.closeCount)

    vm.toggleSoundEffect(THUNDER)
    runCurrent()
    val thunder = player.latest(THUNDER)
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
    vm.toggleSoundEffect(THUNDER)
    runCurrent()
    val thunder = player.latest(THUNDER)

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
  fun volumesApplyImmediatelyToTheirOwnChannelAndStayLocal() = runTest {
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
  }

  @Test
  fun muteTogglesRestoreEachChannelsLastAudibleGain() = runTest {
    load()
    val fire = playFire()
    val sword = playEffect(SWORD)
    vm.setAmbienceVolume(0.5f)
    vm.setSoundEffectVolume(0.25f)

    vm.toggleAmbienceMute()
    vm.toggleSoundEffectMute()

    assertEquals(0f, fire.volume)
    assertEquals(0f, sword.volume)
    assertEquals(board.copy(ambienceVolume = 0f, effectsVolume = 0f), state.soundboard)

    vm.toggleAmbienceMute()
    vm.toggleSoundEffectMute()

    assertEquals(0.5f, fire.volume)
    assertEquals(0.25f, sword.volume)
    assertEquals(board.copy(ambienceVolume = 0.5f, effectsVolume = 0.25f), state.soundboard)
  }

  @Test
  fun invalidVolumeReportsTypedErrorWithoutChangingGain() = runTest {
    load()
    val fire = playFire()
    val sword = playEffect(SWORD)

    vm.setAmbienceVolume(Float.NaN)
    assertEquals(SoundboardVolumeError.AMBIENCE_OUT_OF_RANGE, state.volumeError)
    vm.setSoundEffectVolume(1.5f)

    assertEquals(SoundboardVolumeError.EFFECTS_OUT_OF_RANGE, state.volumeError)
    assertEquals(board, state.soundboard)
    assertEquals(0.7f, fire.volume)
    assertEquals(0.6f, sword.volume)
  }

  @Test
  fun invalidVolumeErrorClearsWhenItsOwnSliderIsCorrected() = runTest {
    load()

    vm.setSoundEffectVolume(1.5f)
    playFire()
    vm.setAmbienceVolume(0.4f)

    assertEquals(SoundboardVolumeError.EFFECTS_OUT_OF_RANGE, state.volumeError)

    vm.setSoundEffectVolume(0.5f)

    assertNull(state.volumeError)
  }

  @Test
  fun correctingVolumeKeepsAnExistingPlaybackError() = runTest {
    load()
    val sword = playEffect(SWORD)
    val playbackError = SoundboardPlaybackError.Playback(SWORD)
    sword.emit(Event.Failed)
    runCurrent()

    vm.setAmbienceVolume(1.5f)

    assertEquals(playbackError, state.error)
    assertEquals(SoundboardVolumeError.AMBIENCE_OUT_OF_RANGE, state.volumeError)

    vm.setAmbienceVolume(0.5f)

    assertEquals(playbackError, state.error)
    assertNull(state.volumeError)
  }

  @Test
  fun synchronousStartFailureReleasesEffectAndAllowsRetry() = runTest {
    load()
    player.failNextStart = true

    vm.toggleSoundEffect(SWORD)
    runCurrent()
    val failed = player.latest(SWORD)

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
