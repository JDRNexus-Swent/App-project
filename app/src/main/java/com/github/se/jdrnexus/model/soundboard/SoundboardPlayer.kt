// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.github.se.jdrnexus.model.soundboard

/** Opens independent playback handles for locally imported sounds. */
interface SoundboardPlayer {
  /**
   * Opens a handle without starting playback or delivering events. Methods and callbacks run on the
   * main thread. Each callback belongs to the handle created with it. A new handle accepts its
   * initial volume before [Playback.play] prepares the audio.
   */
  fun create(audio: AudioFile, onEvent: (Event) -> Unit): Playback

  /** Events for one handle. Report [Started] each time audio begins or resumes. */
  sealed interface Event {
    data object Started : Event

    data class Position(val positionMs: Long) : Event

    data object Completed : Event

    data object Failed : Event
  }

  /**
   * Controls one sound from preparation to release. The handle releases itself before delivering
   * [Event.Completed] or [Event.Failed]. Open a new handle to play that sound again.
   */
  interface Playback {
    /** Prepares and starts, or resumes from the paused position. Completion is asynchronous. */
    fun play()

    /**
     * Cancels a pending start or pauses active audio, returning its retained position. Suppresses
     * outstanding start, position, and completion callbacks from the paused play request.
     */
    fun pause(): Long

    /** Sets gain for pending or active playback, from 0f (silent) to 1f (full gain). */
    fun setVolume(volume: Float)

    /**
     * Stops and releases the sound, including a pending start. An explicit call suppresses
     * subsequent callbacks. Safe to call repeatedly without throwing.
     */
    fun close()
  }
}
