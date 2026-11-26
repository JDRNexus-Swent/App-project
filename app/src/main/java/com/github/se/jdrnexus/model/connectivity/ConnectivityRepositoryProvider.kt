// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.model.connectivity

import android.content.Context
import android.net.ConnectivityManager

/** Provides the connectivity repository used by the app. */
object ConnectivityRepositoryProvider {
  /**
   * Connectivity of the device behind [context]. The application context is used so that the
   * repository never holds an Activity.
   */
  fun repository(context: Context): ConnectivityRepository =
      ConnectivityRepositoryAndroid(
          checkNotNull(context.applicationContext.getSystemService(ConnectivityManager::class.java))
      )
}
