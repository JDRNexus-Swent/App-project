// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.github.se.jdrnexus.ui.soundboard

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.github.se.jdrnexus.model.soundboard.AudioFile
import com.github.se.jdrnexus.ui.soundboard.SoundboardPlayer.Event
import com.github.se.jdrnexus.ui.soundboard.SoundboardPlayer.Playback
import java.io.File

/** Plays each local MP3 on its own ExoPlayer so ambience and effects can overlap. */
class Media3SoundboardPlayer internal constructor(private val newPlayer: () -> Player) :
    SoundboardPlayer {

  constructor(context: Context) : this(playerFactory(context.applicationContext))

  override fun create(audio: AudioFile, onEvent: (Event) -> Unit): Playback {
    val item = MediaItem.fromUri(Uri.fromFile(File(audio.filePath)))
    val player = newPlayer()
    return try {
      Media3Playback(player, item, onEvent)
    } catch (setupFailure: RuntimeException) {
      try {
        player.release()
      } catch (releaseFailure: RuntimeException) {
        if (releaseFailure !== setupFailure) setupFailure.addSuppressed(releaseFailure)
      }
      throw setupFailure
    }
  }

  private class Media3Playback(
      private val player: Player,
      private val item: MediaItem,
      private val onEvent: (Event) -> Unit,
  ) : Playback, Player.Listener {
    private val handler = Handler(player.applicationLooper)
    private var startReported = false
    private var closed = false

    private val reportPosition = Runnable { reportProgress() }

    init {
      player.setMediaItem(item)
      player.addListener(this)
    }

    override fun play() {
      if (closed || player.playWhenReady) return
      player.play()
      if (!closed && player.playWhenReady && player.playbackState == Player.STATE_IDLE) {
        player.prepare()
      }
    }

    override fun pause(): Long {
      if (closed) return 0L
      startReported = false
      handler.removeCallbacks(reportPosition)
      val position = player.currentPosition
      player.pause()
      return position
    }

    override fun setVolume(volume: Float) {
      if (!closed) player.volume = volume
    }

    override fun onEvents(player: Player, events: Player.Events) {
      if (
          !events.containsAny(
              Player.EVENT_IS_PLAYING_CHANGED,
              Player.EVENT_PLAYBACK_STATE_CHANGED,
              Player.EVENT_PLAY_WHEN_READY_CHANGED,
          )
      ) {
        return
      }
      handler.removeCallbacks(reportPosition)
      if (closed || !player.playWhenReady) return
      if (player.playbackState == Player.STATE_ENDED) {
        finish(Event.Completed)
        return
      }
      if (!player.isPlaying) return

      if (!startReported) {
        startReported = true
        onEvent(Event.Started)
      }
      if (!closed && player.isPlaying) handler.post(reportPosition)
    }

    override fun onPlayerError(error: PlaybackException) {
      if (!closed) finish(Event.Failed)
    }

    private fun reportProgress() {
      if (closed || !player.isPlaying) return
      onEvent(Event.Position(player.currentPosition))
      if (!closed && player.isPlaying) handler.postDelayed(reportPosition, POSITION_INTERVAL_MS)
    }

    private fun finish(event: Event) {
      close()
      onEvent(event)
    }

    override fun close() {
      if (closed) return
      closed = true
      handler.removeCallbacks(reportPosition)
      try {
        player.release()
      } catch (failure: RuntimeException) {
        Log.w(TAG, "Unable to release soundboard playback", failure)
      }
    }
  }

  private companion object {
    const val POSITION_INTERVAL_MS = 250L
    const val TAG = "SoundboardPlayer"

    fun playerFactory(context: Context): () -> Player = { ExoPlayer.Builder(context).build() }
  }
}
