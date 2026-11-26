// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.model.connectivity

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ConnectivityRepositoryProviderTest {
  @Test
  fun buildsTheAndroidRepositoryFromAContext() {
    val context = ApplicationProvider.getApplicationContext<Context>()

    val repository = ConnectivityRepositoryProvider.repository(context)

    assertEquals(ConnectivityRepositoryAndroid::class, repository::class)
  }
}
