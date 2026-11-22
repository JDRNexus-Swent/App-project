// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.model.soundboard

/** Volume of the ambience or the effects when none is set: full volume. */
const val DEFAULT_VOLUME = 1f

/** Lowest valid volume: silent. */
const val MIN_VOLUME = 0f

/** Highest valid volume: the files' own loudness. */
const val MAX_VOLUME = 1f

/**
 * One soundboard of an account on this device, as the Game Master edits and plays it. An account
 * can have any number of soundboards, such as one per campaign or per setting. The order of
 * [playlists], of their tracks and of [effects] is the order of the buttons.
 *
 * Only one ambience track plays at a time, while any number of effects can play over it. That rule
 * belongs to playback, not to this model.
 *
 * @property id Unique id of the soundboard, from [SoundboardRepository.getNewSoundboardId].
 * @property name Name shown in the list of soundboards and at the top of the edit screen. Empty
 *   until the Game Master names it; a soundboard cannot be saved with a blank name.
 * @property playlists The ambience playlists, such as "Tavern" or "Dungeon".
 * @property effects The sound effects, each played on its own.
 * @property ambienceVolume Volume of every ambience track, from [MIN_VOLUME] to [MAX_VOLUME].
 * @property effectsVolume Volume of every sound effect, from [MIN_VOLUME] to [MAX_VOLUME].
 */
data class Soundboard(
    val id: String,
    val name: String = "",
    val playlists: List<AmbiencePlaylist> = emptyList(),
    val effects: List<SoundEffect> = emptyList(),
    val ambienceVolume: Float = DEFAULT_VOLUME,
    val effectsVolume: Float = DEFAULT_VOLUME,
)

/**
 * A playlist of ambience tracks that loop in the background.
 *
 * @property id Unique id of the playlist in its soundboard, so the screens can select it.
 * @property name Name shown on the playlist's tab, such as "Tavern".
 * @property tracks The tracks of the playlist, in play order.
 */
data class AmbiencePlaylist(
    val id: String,
    val name: String,
    val tracks: List<AmbienceTrack> = emptyList(),
)

/**
 * One track of an [AmbiencePlaylist].
 *
 * @property name Name shown in the playlist, such as "By the fire".
 * @property audio The mp3 the track plays.
 */
data class AmbienceTrack(val name: String, val audio: AudioFile)

/**
 * A short sound played on its own, such as "Sword Clash".
 *
 * @property name Name shown on the effect's button.
 * @property icon Icon shown on the effect's button.
 * @property audio The mp3 the effect plays.
 */
data class SoundEffect(
    val name: String,
    val audio: AudioFile,
    val icon: SoundEffectIcon = SoundEffectIcon.SPEAKER,
)

/** The icons a Game Master can choose for a [SoundEffect]. */
enum class SoundEffectIcon {
  /** The default icon, for any sound. */
  SPEAKER,
  SWORD,
  SHIELD,
  ARROW,
  LIGHTNING,
  FIRE,
  SKULL,
  FOOTSTEPS,
  SCROLL,
  BELL,
}

/**
 * An mp3 imported from the device and copied into the app's private storage. Each import makes its
 * own copy, so one [AudioFile] is used by at most one track or effect.
 *
 * @property id Unique id of the copy.
 * @property filePath Absolute path of the app's own copy, which the player opens. The copy does not
 *   depend on the original file, so moving or deleting the original does not affect it.
 * @property durationMs Length of the audio in milliseconds, shown next to the track or effect.
 */
data class AudioFile(val id: String, val filePath: String, val durationMs: Long)
