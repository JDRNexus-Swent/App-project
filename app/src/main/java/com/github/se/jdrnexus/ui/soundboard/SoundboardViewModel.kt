// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.github.se.jdrnexus.ui.soundboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.se.jdrnexus.model.repository.AuthRepository
import com.github.se.jdrnexus.model.soundboard.AudioFile
import com.github.se.jdrnexus.model.soundboard.MAX_VOLUME
import com.github.se.jdrnexus.model.soundboard.MIN_VOLUME
import com.github.se.jdrnexus.model.soundboard.Soundboard
import com.github.se.jdrnexus.model.soundboard.SoundboardError
import com.github.se.jdrnexus.model.soundboard.SoundboardException
import com.github.se.jdrnexus.model.soundboard.SoundboardPlayer
import com.github.se.jdrnexus.model.soundboard.SoundboardRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Playback state for one signed-in user's game session. Retain this ViewModel across configuration
 * changes and while the soundboard editor is open. Sign-out and account switches stop playback and
 * clear the previous account's board. Screen actions run on the main thread. Volume changes apply
 * immediately, and only [saveVolumes] persists them.
 */
class SoundboardViewModel(
    private val repository: SoundboardRepository,
    private val player: SoundboardPlayer,
    private val authRepository: AuthRepository,
) : ViewModel(), SoundboardViewModelInterface {
  private val state = MutableStateFlow(SoundboardUiState())
  override val uiState = state.asStateFlow()

  private var requestedBoardId: String? = null
  private var observation: Job? = null
  private var ambienceHandle: ActiveSound? = null
  private val effects = mutableMapOf<String, ActiveSound>()
  private var boardSession: BoardSession? = null
  private val volumeSaveLock = Mutex()

  override fun loadSoundboard(soundboardId: String?) {
    if (
        requestedBoardId == soundboardId &&
            observation?.isActive == true &&
            state.value.loadState !is SoundboardLoadState.Failed
    ) {
      return
    }
    observation?.cancel()
    requestedBoardId = soundboardId
    resetBoard()
    observation = viewModelScope.launch {
      try {
        authRepository.authState
            .map { it?.uid.orEmpty() }
            .distinctUntilChanged()
            .collectLatest { userId -> observeBoard(userId, soundboardId) }
      } catch (failure: Exception) {
        currentCoroutineContext().ensureActive()
        failLoad(failure.reason())
      }
    }
  }

  private suspend fun observeBoard(userId: String, boardId: String?) {
    resetBoard()
    when {
      userId.isBlank() -> failLoad(SoundboardError.NOT_SIGNED_IN)
      boardId?.isBlank() == true -> failLoad(SoundboardError.SOUNDBOARD_NOT_FOUND)
      else ->
          try {
            val boards =
                if (boardId == null) repository.observeSoundboards(userId).map { it.firstOrNull() }
                else repository.observeSoundboard(userId, boardId)
            boards.collect { saved ->
              currentCoroutineContext().ensureActive()
              when {
                saved != null -> {
                  if (saved.id != state.value.soundboard?.id) {
                    resetBoard()
                    boardSession = BoardSession(userId)
                  }
                  showBoard(saved)
                }
                boardId == null -> resetBoard(SoundboardLoadState.Empty)
                else -> failLoad(SoundboardError.SOUNDBOARD_NOT_FOUND)
              }
            }
          } catch (failure: Exception) {
            currentCoroutineContext().ensureActive()
            failLoad(failure.reason())
          }
    }
  }

  private fun resetBoard(loadState: SoundboardLoadState = SoundboardLoadState.Loading) {
    stopAll()
    boardSession = null
    state.value = SoundboardUiState(loadState = loadState)
  }

  private fun failLoad(reason: SoundboardError) = resetBoard(SoundboardLoadState.Failed(reason))

  /**
   * Applies saved edits while keeping session volumes and playback for sounds still on the board.
   */
  private fun showBoard(saved: Soundboard) {
    val previous = state.value
    val board =
        previous.soundboard?.let {
          saved.copy(ambienceVolume = it.ambienceVolume, effectsVolume = it.effectsVolume)
        } ?: saved
    val playlist =
        board.playlists.firstOrNull { it.id == previous.selectedPlaylistId }
            ?: board.playlists.firstOrNull()
    val empty = board.effects.isEmpty() && board.playlists.all { it.tracks.isEmpty() }
    val effectIds = board.effects.mapTo(mutableSetOf()) { it.audio.id }
    previous.ambience?.let { playing ->
      if (
          board.playlists.none {
            it.id == playing.playlistId &&
                it.tracks.any { track -> track.audio.id == playing.audioId }
          }
      ) {
        stopAmbience()
      }
    }
    effects.values.filter { it.audioId !in effectIds }.forEach(::close)
    state.update {
      it.copy(
          soundboard = board,
          loadState = if (empty) SoundboardLoadState.Empty else SoundboardLoadState.Ready,
          selectedPlaylistId = playlist?.id,
      )
    }
  }

  override fun selectPlaylist(playlistId: String) {
    if (state.value.soundboard?.playlists?.any { it.id == playlistId } == true) {
      state.update { it.copy(selectedPlaylistId = playlistId) }
    }
  }

  override fun setTrackMenuExpanded(isExpanded: Boolean) {
    state.update { it.copy(isTrackMenuExpanded = isExpanded) }
  }

  override fun playPlaylist(playlistId: String) {
    val firstTrack =
        state.value.soundboard
            ?.playlists
            ?.firstOrNull { it.id == playlistId }
            ?.tracks
            ?.firstOrNull() ?: return
    playAmbience(playlistId, firstTrack.audio.id)
  }

  override fun playAmbience(playlistId: String, audioId: String) {
    val board = state.value.soundboard ?: return
    val audio =
        board.playlists
            .firstOrNull { it.id == playlistId }
            ?.tracks
            ?.firstOrNull { it.audio.id == audioId }
            ?.audio ?: return
    val current = ambienceHandle
    if (current?.playlistId == playlistId && current.audioId == audioId) {
      val playback = state.value.ambience ?: return
      if (playback.status == AmbiencePlaybackStatus.PAUSED) {
        state.update {
          it.copy(ambience = playback.copy(status = AmbiencePlaybackStatus.PREPARING))
        }
        control(current) { play() }
      }
      return
    }
    stopAmbience()
    start(audio, playlistId, VolumeChannel.AMBIENCE.gain(state.value, board))
  }

  override fun pauseAmbience() {
    val active = ambienceHandle ?: return
    val playback = state.value.ambience ?: return
    if (playback.status == AmbiencePlaybackStatus.PAUSED) return
    state.update { it.copy(ambience = playback.copy(status = AmbiencePlaybackStatus.PAUSED)) }
    control(active) {
      val position = pause()
      state.update { it.copy(ambience = it.ambience?.copy(positionMs = position)) }
    }
  }

  override fun stopAmbience() {
    ambienceHandle?.let(::close)
  }

  override fun setAmbienceLooping(isLooping: Boolean) {
    state.update { it.copy(isAmbienceLooping = isLooping) }
  }

  override fun toggleSoundEffect(audioId: String) {
    val current = effects[audioId]
    if (current != null) {
      close(current)
      return
    }
    val board = state.value.soundboard ?: return
    val audio = board.effects.firstOrNull { it.audio.id == audioId }?.audio ?: return
    start(audio, null, VolumeChannel.EFFECTS.gain(state.value, board))
  }

  /** Registers the new handle before invoking player controls that can deliver callbacks. */
  private fun start(audio: AudioFile, playlistId: String?, volume: Float) {
    val sound = ActiveSound(audio.id, playlistId)
    val playback =
        try {
          player.create(audio) { event -> handleEvent(sound, event) }
        } catch (_: Exception) {
          reportPlaybackFailure(sound.audioId)
          return
        }
    sound.playback = playback
    if (playlistId == null) {
      effects[sound.audioId] = sound
      state.update { it.copy(pendingEffectIds = it.pendingEffectIds + sound.audioId) }
    } else {
      ambienceHandle = sound
      state.update {
        it.copy(
            ambience = AmbiencePlayback(playlistId, audio.id, 0, AmbiencePlaybackStatus.PREPARING),
        )
      }
    }
    control(sound) { setVolume(volume) }
    control(sound) { play() }
  }

  private fun handleEvent(sound: ActiveSound, event: SoundboardPlayer.Event) {
    if (!sound.isActive()) return
    val playingAmbience =
        state.value.ambience?.takeIf {
          sound.playlistId != null && it.status != AmbiencePlaybackStatus.PAUSED
        }
    when (event) {
      SoundboardPlayer.Event.Started -> {
        state.update {
          val error = it.error.takeUnless { error -> error is SoundboardPlaybackError.Playback }
          when {
            sound.playlistId == null ->
                it.copy(
                    pendingEffectIds = it.pendingEffectIds - sound.audioId,
                    playingEffectIds = it.playingEffectIds + sound.audioId,
                    error = error,
                )
            playingAmbience != null ->
                it.copy(
                    ambience = playingAmbience.copy(status = AmbiencePlaybackStatus.PLAYING),
                    error = error,
                )
            else -> it
          }
        }
      }
      is SoundboardPlayer.Event.Position -> {
        if (playingAmbience != null) {
          state.update { it.copy(ambience = playingAmbience.copy(positionMs = event.positionMs)) }
        }
      }
      SoundboardPlayer.Event.Completed -> {
        if (sound.playlistId == null) close(sound)
        else if (playingAmbience != null) advanceAmbience(playingAmbience)
      }
      SoundboardPlayer.Event.Failed -> {
        close(sound)
        reportPlaybackFailure(sound.audioId)
      }
    }
  }

  /** Advances the playlist that owns the playing track, even while another playlist is selected. */
  private fun advanceAmbience(playback: AmbiencePlayback) {
    val current = state.value
    val tracks = current.soundboard?.playlists?.firstOrNull { it.id == playback.playlistId }?.tracks
    stopAmbience()
    val index = tracks?.indexOfFirst { it.audio.id == playback.audioId } ?: return
    if (index < 0) return
    val next =
        tracks.getOrNull(index + 1) ?: tracks.firstOrNull().takeIf { current.isAmbienceLooping }
    next?.let { playAmbience(playback.playlistId, it.audio.id) }
  }

  /** Turns synchronous player failures into UI errors and releases the failed handle. */
  private inline fun control(sound: ActiveSound, action: SoundboardPlayer.Playback.() -> Unit) {
    if (!sound.isActive()) return
    try {
      sound.playback.action()
    } catch (_: Exception) {
      if (sound.isActive()) {
        close(sound)
        reportPlaybackFailure(sound.audioId)
      }
    }
  }

  private fun reportPlaybackFailure(audioId: String) {
    state.update { it.copy(error = SoundboardPlaybackError.Playback(audioId)) }
  }

  /** Checks handle identity so callbacks from a replaced handle cannot affect its replacement. */
  private fun ActiveSound.isActive(): Boolean =
      if (playlistId == null) effects[audioId] === this else ambienceHandle === this

  /** Removes the handle from active state before releasing it so late callbacks are ignored. */
  private fun close(sound: ActiveSound) {
    if (sound.playlistId == null) {
      effects.remove(sound.audioId)
      state.update {
        it.copy(
            playingEffectIds = it.playingEffectIds - sound.audioId,
            pendingEffectIds = it.pendingEffectIds - sound.audioId,
        )
      }
    } else {
      ambienceHandle = null
      state.update { it.copy(ambience = null) }
    }
    sound.playback.close()
  }

  override fun stopAll() {
    stopAmbience()
    effects.values.toList().forEach(::close)
  }

  override fun onCleared() {
    stopAll()
  }

  override fun setAmbienceVolume(volume: Float) = setVolume(VolumeChannel.AMBIENCE, volume)

  override fun setSoundEffectVolume(volume: Float) = setVolume(VolumeChannel.EFFECTS, volume)

  override fun toggleAmbienceMute() = toggleMute(VolumeChannel.AMBIENCE)

  override fun toggleSoundEffectMute() = toggleMute(VolumeChannel.EFFECTS)

  private fun toggleMute(channel: VolumeChannel) {
    val board = state.value.soundboard ?: return
    state.update { channel.setMuted(it, !channel.isMuted(it)) }
    applyVolume(channel, channel.gain(state.value, board))
  }

  private fun setVolume(channel: VolumeChannel, volume: Float) {
    val board = state.value.soundboard ?: return
    if (volume.isNaN()) return
    val adjusted = channel.write(board, volume.coerceIn(MIN_VOLUME, MAX_VOLUME))
    state.update { it.copy(soundboard = adjusted) }
    applyVolume(channel, channel.gain(state.value, adjusted))
  }

  private fun applyVolume(channel: VolumeChannel, volume: Float) {
    val sounds =
        when (channel) {
          VolumeChannel.AMBIENCE -> listOfNotNull(ambienceHandle)
          VolumeChannel.EFFECTS -> effects.values.toList()
        }
    sounds.forEach { sound -> control(sound) { setVolume(volume) } }
  }

  override fun saveVolumes() {
    val session = boardSession ?: return
    val released = state.value.soundboard ?: return
    viewModelScope.launch {
      volumeSaveLock.withLock {
        if (boardSession !== session) return@withLock
        val board = state.value.soundboard ?: return@withLock
        try {
          // stale race with editing, but editing is not here yet
          // and repo should implement save Volume in the meantime, avoiding it
          repository
              .saveSoundboard(
                  session.userId,
                  board.copy(
                      ambienceVolume = released.ambienceVolume,
                      effectsVolume = released.effectsVolume,
                  ),
              )
              .getOrThrow()
          if (boardSession === session) {
            state.update {
              it.copy(
                  error =
                      it.error.takeUnless { error -> error is SoundboardPlaybackError.SaveVolume }
              )
            }
          }
        } catch (failure: Exception) {
          val reason = failure.reason()
          if (boardSession === session) {
            state.update { it.copy(error = SoundboardPlaybackError.SaveVolume(reason)) }
          }
        }
      }
    }
  }

  private fun Exception.reason(): SoundboardError =
      when (this) {
        is CancellationException -> throw this
        is SoundboardException -> error
        else -> SoundboardError.UNKNOWN
      }

  /** A playback handle whose [playlistId] is null for effects and set for ambience tracks. */
  private class ActiveSound(val audioId: String, val playlistId: String?) {
    lateinit var playback: SoundboardPlayer.Playback
  }

  private class BoardSession(val userId: String)

  private enum class VolumeChannel {
    AMBIENCE,
    EFFECTS;

    fun isMuted(state: SoundboardUiState): Boolean =
        when (this) {
          AMBIENCE -> state.isAmbienceMuted
          EFFECTS -> state.isSoundEffectsMuted
        }

    fun setMuted(state: SoundboardUiState, muted: Boolean): SoundboardUiState =
        when (this) {
          AMBIENCE -> state.copy(isAmbienceMuted = muted)
          EFFECTS -> state.copy(isSoundEffectsMuted = muted)
        }

    fun gain(state: SoundboardUiState, board: Soundboard): Float =
        when {
          isMuted(state) -> MIN_VOLUME
          this == AMBIENCE -> board.ambienceVolume
          else -> board.effectsVolume
        }

    fun write(board: Soundboard, gain: Float): Soundboard =
        when (this) {
          AMBIENCE -> board.copy(ambienceVolume = gain)
          EFFECTS -> board.copy(effectsVolume = gain)
        }
  }
}
