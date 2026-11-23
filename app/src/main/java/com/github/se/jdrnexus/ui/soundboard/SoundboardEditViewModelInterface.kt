// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.github.se.jdrnexus.ui.soundboard

import android.net.Uri
import com.github.se.jdrnexus.model.soundboard.SoundEffectIcon
import com.github.se.jdrnexus.model.soundboard.Soundboard
import com.github.se.jdrnexus.model.soundboard.SoundboardError
import kotlinx.coroutines.flow.StateFlow

/** Whether the editor is opening a draft, ready to edit it, or unable to open it. */
sealed interface SoundboardEditLoadState {
  data object Loading : SoundboardEditLoadState

  data object Ready : SoundboardEditLoadState

  data class Failed(val reason: SoundboardError) : SoundboardEditLoadState
}

/** Progress of editor operations. Saved and Discarded let the screen close after success. */
enum class SoundboardEditStatus {
  IDLE,
  IMPORTING,
  SAVING,
  DISCARDING,
  SAVED,
  DISCARDED,
}

/** The operation and typed repository reason for an editor failure. */
sealed interface SoundboardEditError {
  data class Import(val reason: SoundboardError) : SoundboardEditError

  data class Save(val reason: SoundboardError) : SoundboardEditError

  data class Discard(val reason: SoundboardError) : SoundboardEditError
}

/** Validation errors displayed below the soundboard title field. */
enum class SoundboardTitleError {
  EMPTY,
  TOO_LONG,
}

/**
 * State rendered by the soundboard editor.
 *
 * @property draft Editable snapshot, or null until a draft is available.
 * @property loadState State of opening the draft. An empty board is ready to edit.
 * @property selectedPlaylistId The playlist being edited, or null if there are no playlists.
 * @property selectedTrackId The highlighted track in the selected playlist, if any.
 * @property hasUnsavedChanges Whether leaving would discard draft changes or imported copies.
 * @property titleError The validation error for the title, or null when none is displayed.
 * @property canSave Whether Save is enabled. Requires an editable, idle draft with changes and a
 *   valid title and nonblank playlist, track, and effect names.
 * @property status The operation in progress or the successful completion of save or discard.
 * @property error The last failed operation. On failure, status returns to idle and the draft
 *   remains available to correct or retry.
 */
data class SoundboardEditUiState(
    val draft: Soundboard? = null,
    val loadState: SoundboardEditLoadState = SoundboardEditLoadState.Loading,
    val selectedPlaylistId: String? = null,
    val selectedTrackId: String? = null,
    val hasUnsavedChanges: Boolean = false,
    val titleError: SoundboardTitleError? = null,
    val canSave: Boolean = false,
    val status: SoundboardEditStatus = SoundboardEditStatus.IDLE,
    val error: SoundboardEditError? = null,
)

/**
 * Edits the signed-in user's local soundboard as a draft. Track and effect IDs are the IDs of their
 * imported audio. A successful import copies the audio immediately. Draft changes are applied
 * together by [save], which also removes private copies no longer used by any of the user's boards.
 */
interface SoundboardEditViewModelInterface {
  companion object {
    const val MAX_TITLE_LENGTH = 80
  }

  val uiState: StateFlow<SoundboardEditUiState>

  /**
   * Keeps the entered title in the draft and updates its inline error. Checks that the trimmed
   * title is nonempty and at most [MAX_TITLE_LENGTH] characters long. An invalid title disables
   * Save.
   */
  fun renameSoundboard(name: String)

  /** Creates an empty playlist with a new stable ID and selects it. */
  fun addPlaylist(name: String)

  /** Selects the playlist tab whose tracks the editor displays. */
  fun selectPlaylist(playlistId: String)

  /** Renames the playlist in the draft. */
  fun renamePlaylist(playlistId: String, name: String)

  /** Removes the playlist and its tracks from the draft. */
  fun removePlaylist(playlistId: String)

  /** Moves the playlist to its final zero-based position in the draft. */
  fun movePlaylist(playlistId: String, toIndex: Int)

  /** Highlights a track in the selected playlist. */
  fun selectTrack(audioId: String)

  /** Imports a picked MP3 into the specified playlist using the suggested file name. */
  fun importTrack(playlistId: String, source: Uri)

  /** Renames the track in the draft. */
  fun renameTrack(playlistId: String, audioId: String, name: String)

  /** Removes the track from the draft. */
  fun removeTrack(playlistId: String, audioId: String)

  /** Moves the track to its final zero-based position within its playlist. */
  fun moveTrack(playlistId: String, audioId: String, toIndex: Int)

  /** Imports a picked MP3 as an effect with its suggested name and the default icon. */
  fun importSoundEffect(source: Uri)

  /** Renames the effect in the draft. */
  fun renameSoundEffect(audioId: String, name: String)

  /** Changes the icon displayed on the effect button. */
  fun setSoundEffectIcon(audioId: String, icon: SoundEffectIcon)

  /** Removes the effect from the draft. */
  fun removeSoundEffect(audioId: String)

  /** Moves the effect to its final zero-based position in the draft. */
  fun moveSoundEffect(audioId: String, toIndex: Int)

  /** Saves the draft by ID, preserving the latest persisted volumes for an existing board. */
  fun save()

  /** Abandons the draft and removes unsaved imports when leaving the editor. */
  fun discard()

  /**
   * Opens the saved board with [soundboardId], or creates an unsaved draft with an ID from
   * [SoundboardRepository.getNewSoundboardId] when the ID is null. A missing saved board is
   * reported as [SoundboardError.SOUNDBOARD_NOT_FOUND].
   */
  fun loadSoundboard(soundboardId: String?)
}
