// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.model.help

/**
 * Remembers which screens' help the user has already seen, so a screen opens its help by itself
 * only on the first visit. Each help is identified by a topic, such as "campaigns".
 */
interface HelpSeenRepository {
  /** Whether the help of [topic] has been seen and closed at least once. */
  suspend fun hasSeen(topic: String): Boolean

  /** Records that the help of [topic] has been seen; recording it again changes nothing. */
  suspend fun markSeen(topic: String)
}
