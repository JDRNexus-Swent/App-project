// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.github.se.jdrnexus.model.soundboard

import android.content.Context
import android.net.Uri
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.test.utils.FakePlayer
import androidx.test.core.app.ApplicationProvider
import com.github.se.jdrnexus.model.soundboard.SoundboardPlayer.Event
import java.io.File
import java.time.Duration
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@UnstableApi
@RunWith(RobolectricTestRunner::class)
class Media3SoundboardPlayerTest {
  private val player = FakePlayer(playbackState = Player.STATE_IDLE, bufferingDelayMs = 1000L)
  private val events = mutableListOf<Event>()
  private val audio = AudioFile("fire", "/private/by the fire.mp3", 5000)
  private val playback = Media3SoundboardPlayer {
    player
  }
      .create(audio) { event ->
        if (event == Event.Completed || event == Event.Failed) {
          assertEquals("Player must be released before $event", true, player.released)
        }
        events.add(event)
      }
  private val mainLooper = shadowOf(Looper.getMainLooper())

  @After fun closePlayback() = playback.close()

  @Test
  fun contextConstructorOpensIdlePlayback() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val adapter = Media3SoundboardPlayer(context)

    val handle = adapter.create(audio, events::add)

    try {
      assertEquals(0L, handle.pause())
      assertEquals(emptyList<Event>(), events)
    } finally {
      handle.close()
    }

    mainLooper.idle()
    assertEquals(emptyList<Event>(), events)
  }

  @Test
  fun closeDoesNotRetryAFailedRelease() {
    var releaseAttempts = 0
    val failingPlayer =
        object : Player by FakePlayer(playbackState = Player.STATE_IDLE) {
          override fun release() {
            releaseAttempts++
            throw IllegalStateException("Unable to release player")
          }
        }
    val handle = Media3SoundboardPlayer { failingPlayer }.create(audio) {}

    handle.close()
    handle.close()

    assertEquals(1, releaseAttempts)
  }

  @Test
  fun playbackControlsAreIgnoredAfterClose() {
    val calls = mutableListOf<String>()
    val recordingPlayer =
        object : Player by FakePlayer(playbackState = Player.STATE_IDLE) {
          override fun play() {
            calls.add("play")
          }

          override fun pause() {
            calls.add("pause")
          }

          override fun setVolume(volume: Float) {
            calls.add("setVolume")
          }
        }
    val handle = Media3SoundboardPlayer { recordingPlayer }.create(audio, events::add)
    handle.close()

    handle.play()
    handle.setVolume(0.2f)
    val position = handle.pause()
    mainLooper.idle()

    assertEquals(0L, position)
    assertEquals(emptyList<String>(), calls)
    assertEquals(emptyList<Event>(), events)
  }

  @Test
  fun setupFailureReleasesAllocatedPlayerAndPropagatesOriginalException() {
    val allocated = FakePlayer(playbackState = Player.STATE_IDLE)
    val failure = IllegalStateException("Unable to set media item")
    val failingPlayer =
        object : Player by allocated {
          override fun setMediaItem(mediaItem: MediaItem) {
            throw failure
          }
        }

    val thrown =
        assertThrows(IllegalStateException::class.java) {
          Media3SoundboardPlayer { failingPlayer }.create(audio) {}
        }

    assertSame(failure, thrown)
    assertEquals(true, allocated.released)
  }

  @Test
  fun closingOneHandleLeavesTheOtherPlaying() {
    val firstPlayer = FakePlayer(playbackState = Player.STATE_IDLE)
    val secondPlayer = FakePlayer(playbackState = Player.STATE_IDLE)
    val players = listOf(firstPlayer, secondPlayer).iterator()
    val adapter = Media3SoundboardPlayer { players.next() }
    val first = adapter.create(audio) {}
    val second = adapter.create(audio) {}

    try {
      first.play()
      second.play()
      firstPlayer.setPlaybackState(Player.STATE_READY)
      secondPlayer.setPlaybackState(Player.STATE_READY)
      first.close()

      assertEquals(true, firstPlayer.released)
      assertEquals(false, secondPlayer.released)
      assertEquals(true, secondPlayer.isPlaying)
    } finally {
      first.close()
      second.close()
    }
  }

  @Test
  fun openingAHandleAllowsGainBeforePreparingTheLocalFile() {
    playback.setVolume(0.4f)

    assertEquals(0.4f, player.volume)
    assertEquals(
        Uri.fromFile(File(audio.filePath)),
        player.currentMediaItem?.localConfiguration?.uri,
    )
    assertEquals(Player.STATE_IDLE, player.playbackState)
    assertEquals(emptyList<Event>(), events)

    playback.play()

    assertEquals(true, player.playWhenReady)
  }

  @Test
  fun progressSamplesTheCurrentPositionWhilePlaying() {
    playback.play()
    player.setPlaybackState(Player.STATE_READY)
    mainLooper.idle()

    player.setPosition(750L)
    mainLooper.idleFor(Duration.ofMillis(250))

    assertEquals(listOf(Event.Started, Event.Position(0), Event.Position(1000)), events)
  }

  @Test
  fun pausingDuringPreparationSuppressesStartUntilResume() {
    playback.play()
    playback.pause()

    mainLooper.idleFor(Duration.ofSeconds(1))

    assertEquals(emptyList<Event>(), events)

    playback.play()
    mainLooper.idle()

    assertEquals(listOf(Event.Started, Event.Position(0)), events)
  }

  @Test
  fun pauseCancelsProgressAndResumeRetainsPosition() {
    playback.play()
    player.setPosition(1250L)
    player.setPlaybackState(Player.STATE_READY)
    mainLooper.idle()

    assertEquals(1250L, playback.pause())
    mainLooper.idleFor(Duration.ofMillis(500))
    assertEquals(listOf(Event.Started, Event.Position(1250)), events)

    playback.play()
    mainLooper.idle()

    assertEquals(
        listOf(Event.Started, Event.Position(1250), Event.Started, Event.Position(1250)),
        events,
    )
  }

  @Test
  fun resumingAfterTheTrackEndedWhilePausedReportsCompletion() {
    playback.play()
    player.setPlaybackState(Player.STATE_READY)
    mainLooper.idle()
    playback.pause()

    player.setPlaybackState(Player.STATE_ENDED)
    mainLooper.idle()

    assertEquals(listOf(Event.Started, Event.Position(0)), events)

    playback.play()
    mainLooper.idle()

    assertEquals(listOf(Event.Started, Event.Position(0), Event.Completed), events)
  }

  @Test
  fun completionStopsPositionUpdatesAndReleasesPlayer() {
    playback.play()
    player.setPlaybackState(Player.STATE_READY)
    mainLooper.idle()

    player.setPlaybackState(Player.STATE_ENDED)
    mainLooper.idleFor(Duration.ofMillis(500))
    assertEquals(listOf(Event.Started, Event.Position(0), Event.Completed), events)
    assertEquals(true, player.released)
  }

  @Test
  fun playbackErrorStopsPositionUpdatesAndReleasesPlayer() {
    playback.play()
    player.setPlaybackState(Player.STATE_READY)
    mainLooper.idle()

    player.setPlayerError(
        PlaybackException("Unable to read MP3", null, PlaybackException.ERROR_CODE_IO_UNSPECIFIED)
    )
    mainLooper.idleFor(Duration.ofMillis(500))

    assertEquals(listOf(Event.Started, Event.Position(0), Event.Failed), events)
    assertEquals(true, player.released)
  }
}
