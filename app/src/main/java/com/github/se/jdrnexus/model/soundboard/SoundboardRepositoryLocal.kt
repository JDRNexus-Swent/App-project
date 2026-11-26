// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.model.soundboard

import android.content.ContentResolver
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.StatFs
import android.provider.OpenableColumns
import android.system.ErrnoException
import android.system.OsConstants
import android.webkit.MimeTypeMap
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Extension of the copies, and of the picked files when their provider gives no type. */
private const val MP3_EXTENSION = "mp3"

/**
 * Types that mean mp3. `audio/mpeg` is the standard one; some file managers send the non-standard
 * `audio/mp3`.
 */
private val MP3_MIME_TYPES = setOf("audio/mpeg", "audio/mp3")

/** The columns every document provider gives for a picked file: its name and its size. */
private val DESCRIPTION_COLUMNS = arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)

/** What [MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO] returns for a file that has audio. */
private const val HAS_AUDIO = "yes"

/**
 * [SoundboardRepository] backed by Room ([SoundboardDatabase]), with the mp3 copies in
 * [audioDirectory]. Use one instance for the whole app (see [SoundboardRepositoryProvider]), so
 * that [writeLock] covers every write.
 *
 * Each copy is named after the id of its [AudioFileEntity], in [audioDirectory]. A copy is deleted
 * before its row: if deleting the file fails, the row stays, and a later cleanup tries again.
 *
 * @param contentResolver Opens the uris the system file picker returns.
 * @param audioDirectory Folder of the copies, in the app's private storage.
 * @param ioDispatcher Runs the file copies and deletions.
 * @param newId Generates the ids of soundboards and copies.
 */
class SoundboardRepositoryLocal
internal constructor(
    private val dao: SoundboardDao,
    private val contentResolver: ContentResolver,
    private val audioDirectory: File,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) : SoundboardRepository {

  /**
   * Taken by every change to the database, so that a cleanup never deletes a copy while a save
   * starts using it, or while an import registers it.
   */
  private val writeLock = Mutex()

  override fun getNewSoundboardId(): String = newId()

  override fun observeSoundboards(userId: String): Flow<List<Soundboard>> =
      observe(userId) { dao.observeSoundboards(userId).map { rows -> rows.map { it.toModel() } } }

  override fun observeSoundboard(userId: String, soundboardId: String): Flow<Soundboard?> =
      observe(userId) { dao.observeSoundboard(userId, soundboardId).map { it?.toModel() } }

  override suspend fun importAudio(userId: String, source: Uri): Result<ImportedAudio> =
      request(userId) {
        withContext(ioDispatcher) {
          val picked = describe(source)
          if (!picked.looksLikeMp3()) throw SoundboardException(SoundboardError.UNSUPPORTED_FORMAT)
          audioDirectory.mkdirs()
          picked.sizeBytes?.let { checkFreeSpace(it) }

          val id = newId()
          val copy = copyOf(id)
          try {
            copy(source, copy)
            val durationMs = readDurationMs(copy)
            writeLock.withLock { dao.insertAudioFile(AudioFileEntity(id, userId, durationMs)) }
            ImportedAudio(AudioFile(id, copy.absolutePath, durationMs), picked.nameWithoutExtension)
          } catch (e: Throwable) {
            // Also on cancellation: an import that does not finish leaves nothing behind.
            copy.delete()
            throw e
          }
        }
      }

  override suspend fun saveSoundboard(userId: String, soundboard: Soundboard): Result<Unit> =
      request(userId) {
        val trimmed = soundboard.trimNames()
        if (!trimmed.hasAllNames()) throw SoundboardException(SoundboardError.INVALID_NAME)
        if (!trimmed.hasValidVolumes()) throw SoundboardException(SoundboardError.INVALID_VOLUME)
        writeLock.withLock {
          dao.saveSoundboard(userId, trimmed)
          deleteCopies(dao.findUnusedAudioIds(userId))
        }
      }

  override suspend fun deleteSoundboard(userId: String, soundboardId: String): Result<Unit> =
      request(userId) {
        writeLock.withLock {
          val usedAudioIds =
              dao.deleteSoundboard(userId, soundboardId)
                  ?: throw SoundboardException(SoundboardError.SOUNDBOARD_NOT_FOUND)
          // Only its own copies: the unsaved imports may belong to a draft still being edited.
          deleteCopies(dao.findUnusedAudioIds(userId).filter { it in usedAudioIds })
        }
      }

  override suspend fun discardUnsavedImports(userId: String): Result<Unit> =
      request(userId) { writeLock.withLock { deleteCopies(dao.findUnusedAudioIds(userId)) } }

  // ============ Import steps ============

  /** What the file picker's provider says about the picked file. */
  private data class PickedFile(
      val displayName: String,
      val sizeBytes: Long?,
      val mimeType: String?,
  ) {
    val nameWithoutExtension: String
      get() = displayName.substringBeforeLast('.')

    /**
     * Whether the provider's type or the name's extension says mp3. [readDurationMs] then checks
     * the content itself.
     */
    fun looksLikeMp3(): Boolean {
      val extensionType =
          MimeTypeMap.getSingleton()
              .getMimeTypeFromExtension(displayName.substringAfterLast('.', "").lowercase())
      return mimeType in MP3_MIME_TYPES || extensionType in MP3_MIME_TYPES
    }
  }

  /** Reads the name, size and type of [source] from its provider. */
  private fun describe(source: Uri): PickedFile {
    val (name, size) =
        try {
          contentResolver.query(source, DESCRIPTION_COLUMNS, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val nameColumn = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeColumn = cursor.getColumnIndex(OpenableColumns.SIZE)
            val name = if (nameColumn >= 0) cursor.getString(nameColumn) else null
            val size =
                if (sizeColumn >= 0 && !cursor.isNull(sizeColumn)) cursor.getLong(sizeColumn)
                else null
            name to size
          } ?: (null to null)
        } catch (e: SecurityException) {
          throw SoundboardException(SoundboardError.FILE_NOT_FOUND, e)
        }
    return PickedFile(
        displayName = name ?: source.lastPathSegment.orEmpty(),
        sizeBytes = size,
        mimeType = contentResolver.getType(source),
    )
  }

  /** Fails with [SoundboardError.STORAGE_FULL] if [sizeBytes] do not fit in [audioDirectory]. */
  private fun checkFreeSpace(sizeBytes: Long) {
    if (StatFs(audioDirectory.path).availableBytes < sizeBytes) {
      throw SoundboardException(SoundboardError.STORAGE_FULL)
    }
  }

  /** Copies the content of [source] into [destination]. */
  private fun copy(source: Uri, destination: File) {
    try {
      val input =
          contentResolver.openInputStream(source)
              ?: throw SoundboardException(SoundboardError.FILE_NOT_FOUND)
      input.use { from -> destination.outputStream().use { to -> from.copyTo(to) } }
    } catch (e: FileNotFoundException) {
      throw SoundboardException(SoundboardError.FILE_NOT_FOUND, e)
    } catch (e: SecurityException) {
      throw SoundboardException(SoundboardError.FILE_NOT_FOUND, e)
    } catch (e: IOException) {
      if (e.isStorageFull()) throw SoundboardException(SoundboardError.STORAGE_FULL, e)
      throw e
    }
  }

  /**
   * Reads the duration of the mp3 [copy] with the platform's media parser.
   *
   * @throws SoundboardException with [SoundboardError.UNSUPPORTED_FORMAT] if the content is audio
   *   but not mp3, or [SoundboardError.CORRUPTED_FILE] if it cannot be read as audio.
   */
  private fun readDurationMs(copy: File): Long {
    val retriever = MediaMetadataRetriever()
    try {
      retriever.setDataSource(copy.path)
      val hasAudio = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO)
      val durationMs =
          retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
      if (hasAudio != HAS_AUDIO || durationMs == null || durationMs <= 0) {
        throw SoundboardException(SoundboardError.CORRUPTED_FILE)
      }
      // Some devices give no type; the provider's type or the extension then has to do.
      val contentType = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
      if (contentType != null && contentType !in MP3_MIME_TYPES) {
        throw SoundboardException(SoundboardError.UNSUPPORTED_FORMAT)
      }
      return durationMs
    } catch (e: RuntimeException) {
      // setDataSource throws IllegalArgumentException or RuntimeException for unreadable content.
      throw SoundboardException(SoundboardError.CORRUPTED_FILE, e)
    } finally {
      retriever.release()
    }
  }

  // ============ Copies ============

  /** The file of the copy with [audioId]. */
  private fun copyOf(audioId: String) = File(audioDirectory, "$audioId.$MP3_EXTENSION")

  /** Deletes each copy, then its row. A copy that cannot be deleted keeps its row. */
  private suspend fun deleteCopies(audioIds: Collection<String>) {
    for (audioId in audioIds) {
      val copy = copyOf(audioId)
      val isDeleted = withContext(ioDispatcher) { !copy.exists() || copy.delete() }
      if (isDeleted) dao.deleteAudioFile(audioId)
    }
  }

  // ============ Model ============

  private fun SoundboardWithContent.toModel() =
      Soundboard(
          id = soundboard.id,
          name = soundboard.name,
          playlists =
              playlists
                  .sortedBy { it.playlist.position }
                  .map { (playlist, tracks) ->
                    AmbiencePlaylist(
                        id = playlist.id,
                        name = playlist.name,
                        tracks =
                            tracks
                                .sortedBy { it.track.position }
                                .map { AmbienceTrack(it.track.name, it.audio.toModel()) },
                    )
                  },
          effects =
              effects
                  .sortedBy { it.effect.position }
                  .map { SoundEffect(it.effect.name, it.audio.toModel(), it.effect.icon) },
          ambienceVolume = soundboard.ambienceVolume,
          effectsVolume = soundboard.effectsVolume,
      )

  private fun AudioFileEntity.toModel() = AudioFile(id, copyOf(id).absolutePath, durationMs)

  // ============ Errors ============

  /**
   * Runs [action] for [userId] and returns its outcome as a [Result], following the error rules of
   * [SoundboardRepository].
   */
  private suspend fun <T> request(userId: String, action: suspend () -> T): Result<T> =
      try {
        if (userId.isBlank()) throw SoundboardException(SoundboardError.NOT_SIGNED_IN)
        Result.success(action())
      } catch (e: CancellationException) {
        throw e
      } catch (e: SoundboardException) {
        Result.failure(e)
      } catch (e: Exception) {
        Result.failure(SoundboardException(SoundboardError.UNKNOWN, e))
      }

  /** The flow of [rows], whose failures become a [SoundboardException]. */
  private fun <T> observe(userId: String, rows: () -> Flow<T>): Flow<T> =
      if (userId.isBlank()) {
        flow { throw SoundboardException(SoundboardError.NOT_SIGNED_IN) }
      } else {
        rows().catch { e ->
          throw if (e is SoundboardException || e is CancellationException) e
          else SoundboardException(SoundboardError.UNKNOWN, e)
        }
      }
}

/** Whether this failure, or one of its causes, is the system saying the disk is full. */
internal fun IOException.isStorageFull(): Boolean =
    generateSequence<Throwable>(this) { it.cause }
        .any { (it as? ErrnoException)?.errno == OsConstants.ENOSPC }

/** The soundboard with its names, and those of its playlists, tracks and effects, trimmed. */
private fun Soundboard.trimNames() =
    copy(
        name = name.trim(),
        playlists =
            playlists.map { playlist ->
              playlist.copy(
                  name = playlist.name.trim(),
                  tracks = playlist.tracks.map { it.copy(name = it.name.trim()) },
              )
            },
        effects = effects.map { it.copy(name = it.name.trim()) },
    )

/** Whether none of the names is empty. Call it on a [trimNames] soundboard. */
private fun Soundboard.hasAllNames() =
    name.isNotEmpty() &&
        playlists.all { playlist ->
          playlist.name.isNotEmpty() && playlist.tracks.all { it.name.isNotEmpty() }
        } &&
        effects.all { it.name.isNotEmpty() }

/** Whether both volumes are in [MIN_VOLUME]..[MAX_VOLUME]. NaN is not. */
private fun Soundboard.hasValidVolumes() =
    ambienceVolume in MIN_VOLUME..MAX_VOLUME && effectsVolume in MIN_VOLUME..MAX_VOLUME
