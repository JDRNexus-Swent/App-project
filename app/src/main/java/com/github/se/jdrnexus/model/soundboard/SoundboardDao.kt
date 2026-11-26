// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.model.soundboard

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * The queries of [SoundboardDatabase]. Room generates the body of every abstract function from its
 * annotation. The open functions marked [Transaction] combine several of them, and Room runs each
 * one in a single transaction: if it throws, none of its changes are kept.
 */
@Dao
internal abstract class SoundboardDao {

  /** The user's soundboards with their content, in creation order, again after every change. */
  @Transaction
  @Query("SELECT * FROM soundboards WHERE userId = :userId ORDER BY soundboardKey")
  abstract fun observeSoundboards(userId: String): Flow<List<SoundboardWithContent>>

  /** The user's soundboard with [soundboardId] and its content, or null, after every change. */
  @Transaction
  @Query("SELECT * FROM soundboards WHERE userId = :userId AND id = :soundboardId")
  abstract fun observeSoundboard(userId: String, soundboardId: String): Flow<SoundboardWithContent?>

  @Insert abstract suspend fun insertAudioFile(audioFile: AudioFileEntity)

  /**
   * Saves [soundboard] for [userId], creating it or replacing its content, all at once.
   *
   * @throws SoundboardException with [SoundboardError.AUDIO_NOT_FOUND] if a track or effect uses an
   *   audio file the user did not import. Nothing is saved then.
   */
  @Transaction
  open suspend fun saveSoundboard(userId: String, soundboard: Soundboard) {
    val usedAudioIds =
        (soundboard.playlists.flatMap { it.tracks }.map { it.audio.id } +
                soundboard.effects.map { it.audio.id })
            .toSet()
    if (findAudioIds(userId, usedAudioIds.toList()).size != usedAudioIds.size) {
      throw SoundboardException(SoundboardError.AUDIO_NOT_FOUND)
    }

    val row =
        SoundboardEntity(
            userId = userId,
            id = soundboard.id,
            name = soundboard.name,
            ambienceVolume = soundboard.ambienceVolume,
            effectsVolume = soundboard.effectsVolume,
        )
    val existingKey = findSoundboardKey(userId, soundboard.id)
    val soundboardKey =
        if (existingKey == null) {
          insertSoundboard(row)
        } else {
          // Updating the row in place keeps its key, and so its place in the creation order.
          updateSoundboard(row.copy(soundboardKey = existingKey))
          deleteContent(existingKey)
          existingKey
        }

    soundboard.playlists.forEachIndexed { playlistPosition, playlist ->
      val playlistKey =
          insertPlaylist(
              PlaylistEntity(
                  soundboardKey = soundboardKey,
                  position = playlistPosition,
                  id = playlist.id,
                  name = playlist.name,
              )
          )
      insertTracks(
          playlist.tracks.mapIndexed { trackPosition, track ->
            TrackEntity(playlistKey, trackPosition, track.name, track.audio.id)
          }
      )
    }
    insertEffects(
        soundboard.effects.mapIndexed { position, effect ->
          EffectEntity(soundboardKey, position, effect.name, effect.icon, effect.audio.id)
        }
    )
  }

  /**
   * Deletes the user's soundboard with [soundboardId] and its content.
   *
   * @return The ids of the audio files its tracks and effects used, or null if the user has no
   *   soundboard with that id.
   */
  @Transaction
  open suspend fun deleteSoundboard(userId: String, soundboardId: String): Set<String>? {
    val soundboardKey = findSoundboardKey(userId, soundboardId) ?: return null
    val audioIds = audioIdsOf(soundboardKey).toSet()
    deleteContent(soundboardKey)
    deleteSoundboardRow(soundboardKey)
    return audioIds
  }

  /** Ids of the user's audio files that no track or effect uses. */
  @Query(
      """
      SELECT id FROM soundboard_audio_files
      WHERE userId = :userId
        AND id NOT IN (SELECT audioId FROM soundboard_tracks)
        AND id NOT IN (SELECT audioId FROM soundboard_effects)
      """
  )
  abstract suspend fun findUnusedAudioIds(userId: String): List<String>

  @Query("DELETE FROM soundboard_audio_files WHERE id = :audioId")
  abstract suspend fun deleteAudioFile(audioId: String)

  @Query("SELECT id FROM soundboard_audio_files WHERE userId = :userId AND id IN (:audioIds)")
  protected abstract suspend fun findAudioIds(userId: String, audioIds: List<String>): List<String>

  @Query("SELECT soundboardKey FROM soundboards WHERE userId = :userId AND id = :soundboardId")
  protected abstract suspend fun findSoundboardKey(userId: String, soundboardId: String): Long?

  @Query(
      """
      SELECT audioId FROM soundboard_effects WHERE soundboardKey = :soundboardKey
      UNION
      SELECT audioId FROM soundboard_tracks WHERE playlistKey IN
        (SELECT playlistKey FROM soundboard_playlists WHERE soundboardKey = :soundboardKey)
      """
  )
  protected abstract suspend fun audioIdsOf(soundboardKey: Long): List<String>

  /** Returns the key Room generated for the new row. */
  @Insert protected abstract suspend fun insertSoundboard(soundboard: SoundboardEntity): Long

  @Update protected abstract suspend fun updateSoundboard(soundboard: SoundboardEntity)

  /** Returns the key Room generated for the new row. */
  @Insert protected abstract suspend fun insertPlaylist(playlist: PlaylistEntity): Long

  @Insert protected abstract suspend fun insertTracks(tracks: List<TrackEntity>)

  @Insert protected abstract suspend fun insertEffects(effects: List<EffectEntity>)

  /** Deletes the playlists, tracks and effects of a soundboard, but not the soundboard itself. */
  @Transaction
  protected open suspend fun deleteContent(soundboardKey: Long) {
    deleteTracks(soundboardKey)
    deletePlaylists(soundboardKey)
    deleteEffects(soundboardKey)
  }

  @Query(
      """
      DELETE FROM soundboard_tracks WHERE playlistKey IN
        (SELECT playlistKey FROM soundboard_playlists WHERE soundboardKey = :soundboardKey)
      """
  )
  protected abstract suspend fun deleteTracks(soundboardKey: Long)

  @Query("DELETE FROM soundboard_playlists WHERE soundboardKey = :soundboardKey")
  protected abstract suspend fun deletePlaylists(soundboardKey: Long)

  @Query("DELETE FROM soundboard_effects WHERE soundboardKey = :soundboardKey")
  protected abstract suspend fun deleteEffects(soundboardKey: Long)

  @Query("DELETE FROM soundboards WHERE soundboardKey = :soundboardKey")
  protected abstract suspend fun deleteSoundboardRow(soundboardKey: Long)
}
