// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.model.soundboard

import android.content.Context
import androidx.room.Room
import java.io.File

/** Name of the database file, in the app's private storage. */
private const val DATABASE_NAME = "soundboard.db"

/** Name of the folder of the mp3 copies, in the app's private files. */
private const val AUDIO_DIRECTORY_NAME = "soundboard_audio"

/** Provides the soundboard repository used by the app. */
object SoundboardRepositoryProvider {
  @Volatile private var instance: SoundboardRepository? = null

  /**
   * The repository of the device behind [context], created on the first call. Every call returns
   * the same instance: Room wants one database object per file, and the repository's write lock
   * only works if every write goes through it. The application context is used so that the
   * repository never holds an Activity.
   */
  fun repository(context: Context): SoundboardRepository =
      instance
          ?: synchronized(this) {
            instance ?: create(context.applicationContext).also { instance = it }
          }

  private fun create(context: Context): SoundboardRepository {
    val database =
        Room.databaseBuilder(context, SoundboardDatabase::class.java, DATABASE_NAME).build()
    return SoundboardRepositoryLocal(
        dao = database.soundboardDao(),
        contentResolver = context.contentResolver,
        audioDirectory = File(context.filesDir, AUDIO_DIRECTORY_NAME),
    )
  }
}
