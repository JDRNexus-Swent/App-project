// Co-authored-by: OpenAI Codex <noreply@openai.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.ui.soundboard

import com.github.se.jdrnexus.model.soundboard.MAX_VOLUME
import com.github.se.jdrnexus.model.soundboard.MIN_VOLUME
import com.github.se.jdrnexus.model.soundboard.Soundboard
import com.github.se.jdrnexus.model.soundboard.SoundboardError
import kotlinx.coroutines.flow.StateFlow

/** Load state of the selected local soundboard. */
sealed interface SoundboardLoadState {
  data object Loading : SoundboardLoadState

  /**
   * The user has no saved board yet, or the board has no playable tracks or effects. A board may
   * still contain empty playlists.
   */
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

/**
 * The current ambience track and its progress. The track's duration is in the soundboard.
 *
 * @property positionMs Position in the track, refreshed at least once per second while playing so
 *   the screen's elapsed time keeps moving.
 */
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

  /** The current slider values could not be saved. */
  data class SaveVolume(val reason: SoundboardError) : SoundboardPlaybackError
}

/**
 * State rendered by the soundboard during a game session. Sound IDs are the model's audio file IDs.
 *
 * @property soundboard The loaded board, including its current volumes, or null while loading,
 *   after a load failure, or when the user has no saved board yet. An empty saved board still has
 *   an ID and uses [SoundboardLoadState.Empty].
 * @property loadState Whether the board is loading, empty, ready, or failed to load.
 * @property selectedPlaylistId The playlist being browsed, which can differ from the playing one.
 * @property ambience The current track, or null when ambience is stopped.
 * @property isAmbienceLooping Whether playback returns to the start after the last playlist track.
 * @property playingEffectIds Effects currently playing, independently of ambience and each other.
 * @property pendingEffectIds Effects with a playback action in progress.
 * @property error The latest playback or volume-save failure.
 * @property isAmbienceMuted Whether ambience plays at zero gain while its slider value is retained.
 * @property isSoundEffectsMuted Whether effects play at zero gain while their slider value is
 *   retained.
 * @property isTrackMenuExpanded Whether the menu listing the selected playlist's tracks is open.
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
    val isAmbienceMuted: Boolean = false,
    val isSoundEffectsMuted: Boolean = false,
    val isTrackMenuExpanded: Boolean = false,
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

  /**
   * Starts the playlist from its first track, then plays its tracks in order;
   * [SoundboardUiState.isAmbienceLooping] decides whether it starts again after the last one. Does
   * nothing for a playlist without tracks.
   */
  fun playPlaylist(playlistId: String)

  /** Pauses ambience and retains the position for resuming. */
  fun pauseAmbience()

  /** Stops ambience and clears its playback position. */
  fun stopAmbience()

  /** Updates whether ambience wraps after playing through the current playlist in order. */
  fun setAmbienceLooping(isLooping: Boolean)

  /** Starts the one-shot effect or stops it if already playing. Other sounds can continue. */
  fun toggleSoundEffect(audioId: String)

  /**
   * Applies [Soundboard.ambienceVolume] to the players and the state at once, without saving it.
   * Accepts values in [MIN_VOLUME]..[MAX_VOLUME]. The screen calls it on every slider move, then
   * [saveVolumes] when the Game Master lifts their finger.
   */
  fun setAmbienceVolume(volume: Float)

  /**
   * Applies [Soundboard.effectsVolume] to the players and the state at once, without saving it.
   * Accepts values in [MIN_VOLUME]..[MAX_VOLUME]. The screen calls it on every slider move, then
   * [saveVolumes] when the Game Master lifts their finger.
   */
  fun setSoundEffectVolume(volume: Float)

  /**
   * Saves the current ambience and effects volumes. A failure is reported as
   * [SoundboardPlaybackError.SaveVolume] and leaves the applied volumes as they are.
   */
  fun saveVolumes()

  /** Mutes ambience or restores its current slider gain. */
  fun toggleAmbienceMute()

  /** Mutes effects or restores their current slider gain. */
  fun toggleSoundEffectMute()

  /** Stops all sounds and cancels pending starts on session exit. Safe to call repeatedly. */
  fun stopAll()

  /** Opens or closes the menu listing the selected playlist's tracks. */
  fun setTrackMenuExpanded(isExpanded: Boolean)

  /**
   * Loads and observes the saved board with [soundboardId]. Switching boards stops playback from
   * the previous board. If the requested board is missing or deleted, stop its playback and report
   * [SoundboardError.SOUNDBOARD_NOT_FOUND].
   *
   * The navigation route from the game screen passes null, because it has no soundboard id. When
   * [soundboardId] is null, observes the user's first saved board instead. If the user has none,
   * [SoundboardUiState.soundboard] stays null and the state is [SoundboardLoadState.Empty].
   *
   * Loading the board that is already loaded keeps its state and playback, because the screen calls
   * this again after a rotation or when the Game Master comes back from the editor. After a failed
   * load, the same call loads again, so the screen's Retry works.
   */
  fun loadSoundboard(soundboardId: String?)
}
