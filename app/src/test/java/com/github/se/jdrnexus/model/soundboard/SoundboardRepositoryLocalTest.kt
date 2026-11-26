// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.model.soundboard

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.system.ErrnoException
import android.system.OsConstants
import android.webkit.MimeTypeMap
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowMediaMetadataRetriever
import org.robolectric.shadows.ShadowStatFs
import org.robolectric.shadows.util.DataSource

/** Covers [SoundboardRepositoryLocal] on an in-memory Room database, with a fake file picker. */
@RunWith(AndroidJUnit4::class)
class SoundboardRepositoryLocalTest {

  private companion object {
    const val USER = "gm-user"
    const val OTHER_USER = "other-user"
    const val BOARD_ID = "board-tides"

    /** Authority of the [FakeAudioProvider], standing in for the system file picker's. */
    const val AUTHORITY = "com.github.se.jdrnexus.test.audio"
    /** The type providers give mp3s, shared with mp1 and mp2: the whole MPEG audio family. */
    const val MPEG_AUDIO_TYPE = "audio/mpeg"
    const val MP3_EXTENSION = "mp3"
    const val MP2_EXTENSION = "mp2"
    const val AAC_TYPE = "audio/mp4"

    /** What [MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO] returns for a file with audio. */
    const val HAS_AUDIO = "yes"
    const val TRACK_DURATION_MS = 492_000L
    const val EFFECT_DURATION_MS = 3_000L

    /** Volumes of the test soundboard, different from the defaults so that saving them shows. */
    const val AMBIENCE_VOLUME = 0.7f
    const val EFFECTS_VOLUME = 0.4f
    const val CHANGED_VOLUME = 0.5f

    /** How far outside [MIN_VOLUME]..[MAX_VOLUME] the rejected volumes are. */
    const val OUT_OF_RANGE = 0.1f

    /** Free blocks of the shadowed disk: far more than any test file needs. */
    const val PLENTY_OF_BLOCKS = 1_000_000

    val MP3_BYTES = "the bytes of an mp3".toByteArray()
  }

  private lateinit var context: Context
  private lateinit var database: SoundboardDatabase
  private lateinit var audioDirectory: File
  private lateinit var picker: FakeAudioProvider
  private lateinit var repository: SoundboardRepositoryLocal

  /** Ids handed out by the repository's id generator: `id-1`, `id-2`… */
  private var lastId = 0

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    database = Room.inMemoryDatabaseBuilder(context, SoundboardDatabase::class.java).build()
    audioDirectory = File(context.filesDir, "soundboard_audio")
    ShadowStatFs.registerStats(audioDirectory, PLENTY_OF_BLOCKS, PLENTY_OF_BLOCKS, PLENTY_OF_BLOCKS)
    // As on a device: both extensions map to the same MPEG audio type.
    shadowOf(MimeTypeMap.getSingleton()).addExtensionMimeTypeMapping(MP3_EXTENSION, MPEG_AUDIO_TYPE)
    shadowOf(MimeTypeMap.getSingleton()).addExtensionMimeTypeMapping(MP2_EXTENSION, MPEG_AUDIO_TYPE)
    picker = Robolectric.setupContentProvider(FakeAudioProvider::class.java, AUTHORITY)
    repository =
        SoundboardRepositoryLocal(
            dao = database.soundboardDao(),
            contentResolver = context.contentResolver,
            audioDirectory = audioDirectory,
            newId = { "id-${++lastId}" },
        )
  }

  @After
  fun tearDown() {
    database.close()
  }

  // ============ Import ============

  @Test
  fun importCopiesTheMp3AndNamesItAfterTheFile() = runTest {
    val source = pick("Sword clash.mp3")
    readableAsMp3(EFFECT_DURATION_MS)

    val imported = repository.importAudio(USER, source).getOrThrow()

    val copy = File(audioDirectory, "id-1.mp3")
    assertEquals(
        ImportedAudio(AudioFile("id-1", copy.absolutePath, EFFECT_DURATION_MS), "Sword clash"),
        imported,
    )
    assertEquals(MP3_BYTES.toList(), copy.readBytes().toList())
  }

  @Test
  fun importAcceptsAnMp3WhoseProviderGivesNoType() = runTest {
    val source = pick("Tavern.mp3", mimeType = null)
    readableAsMp3(TRACK_DURATION_MS)

    val imported = repository.importAudio(USER, source).getOrThrow()

    assertEquals("Tavern", imported.name)
  }

  @Test
  fun importKeepsADotThatIsNotAnExtensionInTheName() = runTest {
    val source = pick("Dr. Dre - Still")
    readableAsMp3(TRACK_DURATION_MS)

    val imported = repository.importAudio(USER, source).getOrThrow()

    assertEquals("Dr. Dre - Still", imported.name)
  }

  @Test
  fun importRejectsAnMp2SentWithTheMpegAudioType() = runTest {
    // mp2 shares mp3's type, audio/mpeg: only its extension gives it away.
    val source = pick("Old broadcast.mp2", mimeType = MPEG_AUDIO_TYPE)

    val result = repository.importAudio(USER, source)

    assertEquals(SoundboardError.UNSUPPORTED_FORMAT, result.error())
    assertEquals(emptySet<String>(), copies())
  }

  @Test
  fun importRejectsAFileThatIsNotAnMp3() = runTest {
    val source = pick("Notes.txt", mimeType = "text/plain")

    val result = repository.importAudio(USER, source)

    assertEquals(SoundboardError.UNSUPPORTED_FORMAT, result.error())
    assertEquals(emptySet<String>(), copies())
  }

  @Test
  fun importRejectsAnotherAudioFormatNamedLikeAnMp3() = runTest {
    val source = pick("Tavern.mp3")
    readableAs(TRACK_DURATION_MS, AAC_TYPE)

    val result = repository.importAudio(USER, source)

    assertEquals(SoundboardError.UNSUPPORTED_FORMAT, result.error())
    assertEquals(emptySet<String>(), copies())
  }

  @Test
  fun importReportsAnMp3ThatCannotBeReadAsCorrupted() = runTest {
    val source = pick("Broken.mp3")
    ShadowMediaMetadataRetriever.addException(
        DataSource.toDataSource(nextCopy().path),
        IllegalArgumentException("not audio"),
    )

    val result = repository.importAudio(USER, source)

    assertEquals(SoundboardError.CORRUPTED_FILE, result.error())
    assertEquals(emptySet<String>(), copies())
  }

  @Test
  fun importReportsAnMp3WithoutAudioAsCorrupted() = runTest {
    val source = pick("Silent.mp3")
    // The parser opens it, but finds neither audio nor a duration.

    val result = repository.importAudio(USER, source)

    assertEquals(SoundboardError.CORRUPTED_FILE, result.error())
    assertEquals(emptySet<String>(), copies())
  }

  @Test
  fun importReportsAFileThatNoLongerExists() = runTest {
    val source = pick("Gone.mp3")
    picker.files.clear()

    val result = repository.importAudio(USER, source)

    assertEquals(SoundboardError.FILE_NOT_FOUND, result.error())
    assertEquals(emptySet<String>(), copies())
  }

  @Test
  fun importReportsAFullDiskBeforeCopying() = runTest {
    val source = pick("Tavern.mp3")
    ShadowStatFs.registerStats(audioDirectory, PLENTY_OF_BLOCKS, 0, 0)

    val result = repository.importAudio(USER, source)

    assertEquals(SoundboardError.STORAGE_FULL, result.error())
    assertEquals(emptySet<String>(), copies())
  }

  @Test
  fun aWriteFailingForLackOfSpaceIsStorageFull() {
    val full = IOException("write failed", ErrnoException("write", OsConstants.ENOSPC))
    val other = IOException("write failed", ErrnoException("write", OsConstants.EIO))

    assertEquals(listOf(true, false), listOf(full.isStorageFull(), other.isStorageFull()))
  }

  @Test
  fun anImportIsNotInASoundboardUntilSaved() = runTest {
    importAudio("Tavern.mp3")

    assertEquals(emptyList<Soundboard>(), repository.observeSoundboards(USER).first())
  }

  // ============ Save and observe ============

  @Test
  fun aSavedSoundboardIsEmittedWithItsContentInOrder() = runTest {
    val board = tidesBoard()

    repository.saveSoundboard(USER, board).getOrThrow()

    assertEquals(listOf(board), repository.observeSoundboards(USER).first())
    assertEquals(board, repository.observeSoundboard(USER, BOARD_ID).first())
  }

  @Test
  fun observeEmitsAgainAfterEverySave() = runTest {
    // Imported first: an import also changes the tables the flow watches.
    val board = tidesBoard()
    val emissions = Channel<List<Soundboard>>(Channel.UNLIMITED)
    val collector = launch { repository.observeSoundboards(USER).collect { emissions.send(it) } }
    assertEquals(emptyList<Soundboard>(), emissions.receive())

    repository.saveSoundboard(USER, board).getOrThrow()

    assertEquals(listOf(board), emissions.receive())
    collector.cancel()
  }

  @Test
  fun savingAnExistingSoundboardReplacesItAndKeepsItsPlace() = runTest {
    val first = tidesBoard()
    val second = Soundboard(id = "board-dungeon", name = "Dungeon")
    repository.saveSoundboard(USER, first).getOrThrow()
    repository.saveSoundboard(USER, second).getOrThrow()
    val renamed =
        first.copy(name = "Tides of war", effects = emptyList(), ambienceVolume = CHANGED_VOLUME)

    repository.saveSoundboard(USER, renamed).getOrThrow()

    assertEquals(listOf(renamed, second), repository.observeSoundboards(USER).first())
  }

  @Test
  fun eachUserOnlySeesTheirOwnSoundboards() = runTest {
    val mine = tidesBoard()
    val theirs = Soundboard(id = BOARD_ID, name = "Same id, other user")
    repository.saveSoundboard(USER, mine).getOrThrow()

    repository.saveSoundboard(OTHER_USER, theirs).getOrThrow()

    assertEquals(listOf(mine), repository.observeSoundboards(USER).first())
    assertEquals(listOf(theirs), repository.observeSoundboards(OTHER_USER).first())
  }

  @Test
  fun observeSoundboardEmitsNullForAnUnknownId() = runTest {
    assertNull(repository.observeSoundboard(USER, "unknown").first())
  }

  @Test
  fun namesAreSavedWithoutSurroundingSpaces() = runTest {
    val track = AmbienceTrack("  By the fire ", importAudio("Fire.mp3"))
    val effect = SoundEffect(" Sword ", importAudio("Sword.mp3"))
    val board =
        Soundboard(
            id = BOARD_ID,
            name = " Tides ",
            playlists = listOf(AmbiencePlaylist("tavern", " Tavern", listOf(track))),
            effects = listOf(effect),
        )

    repository.saveSoundboard(USER, board).getOrThrow()

    val saved = repository.observeSoundboard(USER, BOARD_ID).first()!!
    assertEquals(
        listOf("Tides", "Tavern", "By the fire", "Sword"),
        listOf(
            saved.name,
            saved.playlists[0].name,
            saved.playlists[0].tracks[0].name,
            saved.effects[0].name,
        ),
    )
  }

  @Test
  fun saveRejectsEveryBlankName() = runTest {
    val board = tidesBoard()
    val blankNames =
        listOf(
            board.copy(name = "  "),
            board.copy(playlists = board.playlists.map { it.copy(name = "") }),
            board.copy(
                playlists =
                    board.playlists.map { playlist ->
                      playlist.copy(tracks = playlist.tracks.map { it.copy(name = " ") })
                    }
            ),
            board.copy(effects = board.effects.map { it.copy(name = "") }),
        )

    val errors = blankNames.map { repository.saveSoundboard(USER, it).error() }

    assertEquals(List(blankNames.size) { SoundboardError.INVALID_NAME }, errors)
    assertEquals(emptyList<Soundboard>(), repository.observeSoundboards(USER).first())
  }

  @Test
  fun saveRejectsVolumesOutOfRange() = runTest {
    val board = Soundboard(id = BOARD_ID, name = "Tides")
    val badVolumes =
        listOf(
            board.copy(ambienceVolume = MAX_VOLUME + OUT_OF_RANGE),
            board.copy(effectsVolume = MIN_VOLUME - OUT_OF_RANGE),
            board.copy(ambienceVolume = Float.NaN),
        )

    val errors = badVolumes.map { repository.saveSoundboard(USER, it).error() }

    assertEquals(List(badVolumes.size) { SoundboardError.INVALID_VOLUME }, errors)
    assertEquals(emptyList<Soundboard>(), repository.observeSoundboards(USER).first())
  }

  @Test
  fun saveAcceptsTheVolumeBounds() = runTest {
    // MAX_VOLUME is also DEFAULT_VOLUME: a volume the repository forgot to store reads back as the
    // default, so checking it at MAX_VOLUME alone would still pass. Each volume is therefore set
    // to MIN_VOLUME on one of the two soundboards, where a forgotten volume shows.
    val bounds =
        listOf(
            Soundboard(
                id = "board-quiet-ambience",
                name = "Quiet ambience",
                ambienceVolume = MIN_VOLUME,
                effectsVolume = MAX_VOLUME,
            ),
            Soundboard(
                id = "board-quiet-effects",
                name = "Quiet effects",
                ambienceVolume = MAX_VOLUME,
                effectsVolume = MIN_VOLUME,
            ),
        )

    bounds.forEach { repository.saveSoundboard(USER, it).getOrThrow() }

    // Comparing the whole soundboards checks both volumes of each.
    assertEquals(bounds, repository.observeSoundboards(USER).first())
  }

  @Test
  fun saveRejectsAudioTheUserDidNotImportAndKeepsTheSavedSoundboard() = runTest {
    val saved = tidesBoard()
    repository.saveSoundboard(USER, saved).getOrThrow()
    val othersAudio = importAudio("Theirs.mp3", userId = OTHER_USER)
    val invented = AudioFile("invented", "/nowhere.mp3", EFFECT_DURATION_MS)

    val foreignAudio = listOf(othersAudio, invented)

    val errors = foreignAudio.map { audio ->
      repository
          .saveSoundboard(
              USER,
              saved.copy(name = "Changed", effects = listOf(SoundEffect("Fx", audio))),
          )
          .error()
    }

    assertEquals(List(foreignAudio.size) { SoundboardError.AUDIO_NOT_FOUND }, errors)
    assertEquals(listOf(saved), repository.observeSoundboards(USER).first())
  }

  @Test
  fun saveDeletesTheCopiesNoSoundboardUsesAnyMore() = runTest {
    val board = tidesBoard()
    repository.saveSoundboard(USER, board).getOrThrow()
    val otherBoardAudio = importAudio("Drip.mp3")
    repository
        .saveSoundboard(
            USER,
            Soundboard(
                id = "board-dungeon",
                name = "Dungeon",
                effects = listOf(SoundEffect("Drip", otherBoardAudio)),
            ),
        )
        .getOrThrow()
    importAudio("Unsaved.mp3")
    val removedTrack = board.playlists[0].tracks[1].audio

    repository
        .saveSoundboard(
            USER,
            board.copy(
                playlists =
                    listOf(board.playlists[0].copy(tracks = board.playlists[0].tracks.take(1)))
            ),
        )
        .getOrThrow()

    // Neither the removed track nor the unsaved import is left.
    assertEquals(
        (board.allAudio() - removedTrack + otherBoardAudio).map { File(it.filePath).name }.toSet(),
        copies(),
    )
  }

  @Test
  fun aSavedCopyCanBeSavedAgainAfterItsSoundIsMoved() = runTest {
    val board = tidesBoard()
    repository.saveSoundboard(USER, board).getOrThrow()
    val track = board.playlists[0].tracks[0]
    val moved =
        board.copy(
            effects = board.effects + SoundEffect("Fire", track.audio),
            playlists = emptyList(),
        )

    repository.saveSoundboard(USER, moved).getOrThrow()

    assertTrue(File(track.audio.filePath).exists())
    assertEquals(moved, repository.observeSoundboard(USER, BOARD_ID).first())
  }

  // ============ Delete ============

  @Test
  fun deleteRemovesTheSoundboardAndOnlyItsCopies() = runTest {
    val board = tidesBoard()
    repository.saveSoundboard(USER, board).getOrThrow()
    val kept = Soundboard(id = "board-dungeon", name = "Dungeon")
    repository.saveSoundboard(USER, kept).getOrThrow()
    val unsaved = importAudio("Draft.mp3")

    repository.deleteSoundboard(USER, BOARD_ID).getOrThrow()

    assertEquals(listOf(kept), repository.observeSoundboards(USER).first())
    assertNull(repository.observeSoundboard(USER, BOARD_ID).first())
    assertEquals(setOf(File(unsaved.filePath).name), copies())
  }

  @Test
  fun deleteFailsForASoundboardTheUserDoesNotHave() = runTest {
    val theirs = Soundboard(id = BOARD_ID, name = "Theirs")
    repository.saveSoundboard(OTHER_USER, theirs).getOrThrow()

    val result = repository.deleteSoundboard(USER, BOARD_ID)

    assertEquals(SoundboardError.SOUNDBOARD_NOT_FOUND, result.error())
    assertEquals(listOf(theirs), repository.observeSoundboards(OTHER_USER).first())
  }

  // ============ Discard ============

  @Test
  fun discardDeletesOnlyTheUsersUnsavedImports() = runTest {
    val board = tidesBoard()
    repository.saveSoundboard(USER, board).getOrThrow()
    importAudio("Draft.mp3")
    val othersImport = importAudio("Theirs.mp3", userId = OTHER_USER)

    repository.discardUnsavedImports(USER).getOrThrow()

    assertEquals((board.allAudio() + othersImport).map { File(it.filePath).name }.toSet(), copies())
  }

  @Test
  fun aDiscardedImportCannotBeSaved() = runTest {
    val draft = importAudio("Draft.mp3")
    repository.discardUnsavedImports(USER).getOrThrow()

    val result =
        repository.saveSoundboard(
            USER,
            Soundboard(id = BOARD_ID, name = "Tides", effects = listOf(SoundEffect("Fx", draft))),
        )

    assertEquals(SoundboardError.AUDIO_NOT_FOUND, result.error())
  }

  // ============ Signed out and ids ============

  @Test
  fun everyOperationFailsWhenNobodyIsSignedIn() = runTest {
    val board = Soundboard(id = BOARD_ID, name = "Tides")

    val errors =
        listOf(
                repository.importAudio(" ", pick("Tavern.mp3")),
                repository.saveSoundboard("", board),
                repository.deleteSoundboard("", BOARD_ID),
                repository.discardUnsavedImports(""),
            )
            .map { it.error() }

    assertEquals(List(errors.size) { SoundboardError.NOT_SIGNED_IN }, errors)
  }

  @Test
  fun bothFlowsFailWhenNobodyIsSignedIn() = runTest {
    val errors =
        listOf(
                runCatching { repository.observeSoundboards("").first() },
                runCatching { repository.observeSoundboard("", BOARD_ID).first() },
            )
            .map { it.error() }

    assertEquals(List(errors.size) { SoundboardError.NOT_SIGNED_IN }, errors)
  }

  @Test
  fun newSoundboardIdsAreUnique() {
    val withRandomIds =
        SoundboardRepositoryLocal(database.soundboardDao(), context.contentResolver, audioDirectory)

    assertNotEquals(withRandomIds.getNewSoundboardId(), withRandomIds.getNewSoundboardId())
  }

  @Test
  fun theProviderAlwaysGivesTheSameRepository() {
    assertSame(
        SoundboardRepositoryProvider.repository(context),
        SoundboardRepositoryProvider.repository(context),
    )
  }

  // ============ Helpers ============

  /** A soundboard with two playlists (one with two tracks) and two effects, all imported. */
  private suspend fun tidesBoard(): Soundboard {
    val fire = AmbienceTrack("By the fire", importAudio("Fire.mp3", TRACK_DURATION_MS))
    val murmurs = AmbienceTrack("Murmurs", importAudio("Murmurs.mp3", TRACK_DURATION_MS))
    return Soundboard(
        id = BOARD_ID,
        name = "Tides",
        playlists =
            listOf(
                AmbiencePlaylist("tavern", "Tavern", listOf(fire, murmurs)),
                AmbiencePlaylist("dungeon", "Dungeon"),
            ),
        effects =
            listOf(
                SoundEffect("Sword", importAudio("Sword.mp3"), SoundEffectIcon.SWORD),
                SoundEffect("Bell", importAudio("Bell.mp3")),
            ),
        ambienceVolume = AMBIENCE_VOLUME,
        effectsVolume = EFFECTS_VOLUME,
    )
  }

  /** Imports [name] as an mp3 of [durationMs] for [userId] and returns its copy. */
  private suspend fun importAudio(
      name: String,
      durationMs: Long = EFFECT_DURATION_MS,
      userId: String = USER,
  ): AudioFile {
    val source = pick(name)
    readableAsMp3(durationMs)
    return repository.importAudio(userId, source).getOrThrow().audio
  }

  /** Offers [name] in the fake file picker and returns its uri. */
  private fun pick(name: String, mimeType: String? = MPEG_AUDIO_TYPE): Uri {
    picker.files[name] = FakeAudioProvider.Served(name, mimeType, MP3_BYTES)
    return Uri.Builder().scheme("content").authority(AUTHORITY).appendPath(name).build()
  }

  /** The file the next import copies into. */
  private fun nextCopy() = File(audioDirectory, "id-${lastId + 1}.mp3")

  /** Makes the platform's media parser read the next copy as an mp3 of [durationMs]. */
  private fun readableAsMp3(durationMs: Long) = readableAs(durationMs, MPEG_AUDIO_TYPE)

  /** Makes the platform's media parser read the next copy as audio of [type]. */
  private fun readableAs(durationMs: Long, type: String) {
    val copy = DataSource.toDataSource(nextCopy().path)
    ShadowMediaMetadataRetriever.addMetadata(
        copy,
        MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO,
        HAS_AUDIO,
    )
    ShadowMediaMetadataRetriever.addMetadata(
        copy,
        MediaMetadataRetriever.METADATA_KEY_DURATION,
        durationMs.toString(),
    )
    ShadowMediaMetadataRetriever.addMetadata(
        copy,
        MediaMetadataRetriever.METADATA_KEY_MIMETYPE,
        type,
    )
  }

  /** Names of the files in the copies folder. */
  private fun copies(): Set<String> = audioDirectory.list().orEmpty().toSet()

  private fun Soundboard.allAudio(): Set<AudioFile> =
      (playlists.flatMap { it.tracks }.map { it.audio } + effects.map { it.audio }).toSet()

  private fun Result<*>.error(): SoundboardError? =
      (exceptionOrNull() as? SoundboardException)?.error
}

/**
 * Stands in for the provider behind the system file picker: serves [files] by name, with the name,
 * size and type a real provider reports.
 */
class FakeAudioProvider : ContentProvider() {

  /** A file the picker offers. */
  data class Served(val displayName: String, val mimeType: String?, val bytes: ByteArray)

  /** The files offered, by the last segment of their uri. */
  val files = mutableMapOf<String, Served>()

  override fun onCreate() = true

  override fun query(
      uri: Uri,
      projection: Array<out String>?,
      selection: String?,
      selectionArgs: Array<out String>?,
      sortOrder: String?,
  ): Cursor? {
    val file = files[uri.lastPathSegment] ?: return null
    return MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)).apply {
      addRow(arrayOf<Any>(file.displayName, file.bytes.size.toLong()))
    }
  }

  override fun getType(uri: Uri): String? = files[uri.lastPathSegment]?.mimeType

  override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
    val file = files[uri.lastPathSegment] ?: throw FileNotFoundException(uri.toString())
    val served = File.createTempFile("picked", null, context!!.cacheDir)
    served.writeBytes(file.bytes)
    return ParcelFileDescriptor.open(served, ParcelFileDescriptor.MODE_READ_ONLY)
  }

  override fun insert(uri: Uri, values: ContentValues?): Uri? =
      throw UnsupportedOperationException()

  override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
      throw UnsupportedOperationException()

  override fun update(
      uri: Uri,
      values: ContentValues?,
      selection: String?,
      selectionArgs: Array<out String>?,
  ): Int = throw UnsupportedOperationException()
}
