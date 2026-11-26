// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.model.help

import android.content.Context
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Name of the preferences file where [HelpSeenRepositorySharedPreferences] keeps its records. */
private const val HELP_SEEN_PREFERENCES_NAME = "help_seen"

/**
 * [HelpSeenRepository] kept in the app's preferences on this device: one boolean per topic, shared
 * by every account signed in on the device. The file is read and written on [ioDispatcher], off the
 * main thread.
 */
class HelpSeenRepositorySharedPreferences(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : HelpSeenRepository {
  private val preferences =
      context.applicationContext.getSharedPreferences(
          HELP_SEEN_PREFERENCES_NAME,
          Context.MODE_PRIVATE,
      )

  override suspend fun hasSeen(topic: String): Boolean =
      withContext(ioDispatcher) { preferences.getBoolean(topic, false) }

  override suspend fun markSeen(topic: String) {
    withContext(ioDispatcher) { preferences.edit().putBoolean(topic, true).apply() }
  }
}
