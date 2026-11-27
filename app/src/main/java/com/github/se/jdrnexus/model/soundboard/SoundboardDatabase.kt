// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.model.soundboard

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * The SQLite database of the soundboards, on this device only. Room generates its implementation.
 *
 * The schema is not exported: there is a single version so far. Exporting it becomes useful with
 * the first migration, to test the migration against the previous schema.
 */
@Database(
    entities =
        [
            SoundboardEntity::class,
            PlaylistEntity::class,
            TrackEntity::class,
            EffectEntity::class,
            AudioFileEntity::class,
        ],
    version = 1,
    exportSchema = false,
)
internal abstract class SoundboardDatabase : RoomDatabase() {
  abstract fun soundboardDao(): SoundboardDao
}
