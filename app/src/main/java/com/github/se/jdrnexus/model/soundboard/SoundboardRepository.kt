// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.model.soundboard

import android.net.Uri
import kotlinx.coroutines.flow.Flow

/**
 * Stores the [Soundboard]s of each account, on this device only: the mp3 files are copied into the
 * app's private storage and nothing is uploaded. An account can have any number of soundboards, and
 * two accounts on the same phone never see each other's.
 *
 * The repository does not authenticate: the caller passes the signed-in user's id as `userId`.
 *
 * The edit screen works on a draft: [importAudio] copies each picked file at once, and
 * [saveSoundboard] stores the whole draft when the Game Master presses Save. Leaving without saving
 * calls [discardUnsavedImports].
 *
 * Failures are returned as a [Result.failure] holding a [SoundboardException], except
 * [kotlinx.coroutines.CancellationException], which is rethrown. The flows instead throw the
 * [SoundboardException]. A blank `userId` fails with [SoundboardError.NOT_SIGNED_IN].
 */
interface SoundboardRepository {

  /** Returns a new unique id for a [Soundboard] the Game Master is creating. */
  fun getNewSoundboardId(): String

  /**
   * Emits the user's saved soundboards in the order they were created, then again after every save
   * or deletion. Emits an empty list if the user has none.
   */
  fun observeSoundboards(userId: String): Flow<List<Soundboard>>

  /**
   * Emits the user's saved soundboard with id [soundboardId], then again after every save. Emits
   * null if the user has no soundboard with that id, for example after it is deleted.
   */
  fun observeSoundboard(userId: String, soundboardId: String): Flow<Soundboard?>

  /**
   * Copies the mp3 at [source] (a uri from the system file picker) into the app's private storage,
   * for the user. The copy is not part of a soundboard until a [saveSoundboard] uses it.
   *
   * @return The copy and a name for it, or [SoundboardError.UNSUPPORTED_FORMAT],
   *   [SoundboardError.FILE_NOT_FOUND], [SoundboardError.CORRUPTED_FILE],
   *   [SoundboardError.STORAGE_FULL] or [SoundboardError.UNKNOWN]. On failure, no copy is left
   *   behind.
   */
  suspend fun importAudio(userId: String, source: Uri): Result<ImportedAudio>

  /**
   * Saves [soundboard] for the user: creates it if its id is new, otherwise replaces the saved one
   * with that id. Then deletes the user's copies that none of their soundboards uses any more:
   * removed tracks and effects, and imports that were never saved. Names are stored without
   * surrounding spaces.
   *
   * @return [SoundboardError.INVALID_NAME] if the soundboard, a playlist, a track or an effect name
   *   is blank, [SoundboardError.INVALID_VOLUME] if a volume is outside [MIN_VOLUME]..[MAX_VOLUME],
   *   [SoundboardError.AUDIO_NOT_FOUND] if a track or effect uses an [AudioFile] that [importAudio]
   *   did not create for this user. The saved soundboards are then left unchanged.
   */
  suspend fun saveSoundboard(userId: String, soundboard: Soundboard): Result<Unit>

  /**
   * Deletes the user's soundboard with id [soundboardId], and the copies of its tracks and effects.
   *
   * @return [SoundboardError.SOUNDBOARD_NOT_FOUND] if the user has no soundboard with that id.
   */
  suspend fun deleteSoundboard(userId: String, soundboardId: String): Result<Unit>

  /**
   * Deletes the user's imported copies that none of their saved soundboards uses, when the Game
   * Master leaves the edit screen without saving.
   */
  suspend fun discardUnsavedImports(userId: String): Result<Unit>
}

/**
 * The result of [SoundboardRepository.importAudio].
 *
 * @property audio The copy of the picked file.
 * @property name The picked file's name without its extension, to prefill the track or effect name.
 */
data class ImportedAudio(val audio: AudioFile, val name: String)

/** Why a soundboard operation failed. Carried by [SoundboardException]. */
enum class SoundboardError {
  /** The user id is blank: nobody is signed in. */
  NOT_SIGNED_IN,

  /** The picked file is not an mp3. */
  UNSUPPORTED_FORMAT,

  /** The picked file says it is an mp3 but cannot be read as audio. */
  CORRUPTED_FILE,

  /** The picked file no longer exists or can no longer be read. */
  FILE_NOT_FOUND,

  /** The device does not have enough free space for the copy. */
  STORAGE_FULL,

  /** A soundboard, playlist, track or effect name is blank. */
  INVALID_NAME,

  /** The ambience or effects volume is outside [MIN_VOLUME]..[MAX_VOLUME]. */
  INVALID_VOLUME,

  /** A track or effect uses an audio file that the repository did not import for this user. */
  AUDIO_NOT_FOUND,

  /** The user has no soundboard with the given id. */
  SOUNDBOARD_NOT_FOUND,

  /** Any other failure, such as a database or file system error. */
  UNKNOWN,
}

/** Failure of a soundboard operation. Every failed [Result] of [SoundboardRepository] holds one. */
class SoundboardException(val error: SoundboardError, cause: Throwable? = null) :
    Exception(error.name, cause)
