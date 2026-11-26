// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.github.se.jdrnexus.ui.soundboard

import com.github.se.jdrnexus.model.soundboard.MAX_VOLUME
import com.github.se.jdrnexus.model.soundboard.MIN_VOLUME
import com.github.se.jdrnexus.model.soundboard.Soundboard
import com.github.se.jdrnexus.model.soundboard.SoundboardError
import kotlinx.coroutines.flow.StateFlow

/** Load state of the selected local soundboard. */
sealed interface SoundboardLoadState {
  data object Loading : SoundboardLoadState

  /** The board has no playable tracks or effects. It may still contain empty playlists. */
  data object Empty : SoundboardLoadState

  data object Ready : SoundboardLoadState

  data class Failed(val reason: SoundboardError) : SoundboardLoadState
}

/** Whether the current ambience track is being prepared, playing, or paused. */
enum class AmbiencePlaybackStatus {
  PREPARING,
  PLAYING,
  PAUSED,
}

/** The current ambience track and its progress. The track's duration is in the soundboard. */
data class AmbiencePlayback(
    val playlistId: String,
    val audioId: String,
    val positionMs: Long,
    val status: AmbiencePlaybackStatus,
)

/** An action failure that can be displayed while the soundboard stays usable. */
sealed interface SoundboardPlaybackError {
  /** The player failed to start or continue the identified track or effect. */
  data class Playback(val audioId: String) : SoundboardPlaybackError

  /** The effective volume settings could not be saved. */
  data class SaveVolume(val reason: SoundboardError) : SoundboardPlaybackError
}

/**
 * State rendered by the soundboard during a game session. Sound IDs are the model's audio file IDs.
 *
 * @property soundboard The loaded board, including its current volumes, or null while loading or
 *   after a load failure. An empty saved board still has an ID and uses
 *   [SoundboardLoadState.Empty].
 * @property loadState Whether the board is loading, empty, ready, or failed to load.
 * @property selectedPlaylistId The playlist being browsed, which can differ from the playing one.
 * @property ambience The current track, or null when ambience is stopped.
 * @property isAmbienceLooping Whether playback returns to the start after the last playlist track.
 * @property playingEffectIds Effects currently playing, independently of ambience and each other.
 * @property pendingEffectIds Effects with a playback action in progress.
 * @property error The latest playback or volume failure, or null when there is none.
 */
data class SoundboardUiState(
    val soundboard: Soundboard? = null,
    val loadState: SoundboardLoadState = SoundboardLoadState.Loading,
    val selectedPlaylistId: String? = null,
    val ambience: AmbiencePlayback? = null,
    val isAmbienceLooping: Boolean = true,
    val playingEffectIds: Set<String> = emptySet(),
    val pendingEffectIds: Set<String> = emptySet(),
    val error: SoundboardPlaybackError? = null,
)

/**
 * Playback controls for the Game Master's local soundboard. Scope this ViewModel to the game
 * session so rotation and visiting the editor preserve playback.
 *
 * When the saved board changes, stop playback and cancel pending starts for any removed sounds.
 */
interface SoundboardViewModelInterface {
  /** State for the screen to render. The ViewModel sets and clears errors. */
  val uiState: StateFlow<SoundboardUiState>

  /** Selects a playlist for browsing its tracks. */
  fun selectPlaylist(playlistId: String)

  /** Starts the specified track or resumes it if paused. A track already playing continues. */
  fun playAmbience(playlistId: String, audioId: String)

  /** Pauses ambience and retains the position for resuming. */
  fun pauseAmbience()

  /** Stops ambience and clears its playback position. */
  fun stopAmbience()

  /** Updates whether ambience wraps after playing through the current playlist in order. */
  fun setAmbienceLooping(isLooping: Boolean)

  /** Starts the one-shot effect or stops it if already playing. Other sounds can continue. */
  fun toggleSoundEffect(audioId: String)

  /**
   * Updates and persists [Soundboard.ambienceVolume]. Accepts values in [MIN_VOLUME]..[MAX_VOLUME].
   */
  fun setAmbienceVolume(volume: Float)

  /**
   * Updates and persists [Soundboard.effectsVolume]. Accepts values in [MIN_VOLUME]..[MAX_VOLUME].
   */
  fun setSoundEffectVolume(volume: Float)

  /** Mutes ambience or restores its last audible gain. */
  fun toggleAmbienceMute()

  /** Mutes effects or restores their last audible gain. */
  fun toggleSoundEffectMute()

  /** Stops all sounds and cancels pending starts on session exit. Safe to call repeatedly. */
  fun stopAll()

  /**
   * Loads and observes the saved board with [soundboardId]. Switching boards stops playback from
   * the previous board. If the requested board is missing or deleted, stop its playback and report
   * [SoundboardError.SOUNDBOARD_NOT_FOUND].
   */
  fun loadSoundboard(soundboardId: String)
}
