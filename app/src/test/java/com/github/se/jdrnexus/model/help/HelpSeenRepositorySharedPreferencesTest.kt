// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.model.help

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

// Robolectric clears the shared preferences before each test: every test starts from an empty
// help_seen file, so no test depends on another.
@RunWith(AndroidJUnit4::class)
class HelpSeenRepositorySharedPreferencesTest {
  private val context = ApplicationProvider.getApplicationContext<Context>()

  @Test fun helpIsNotSeenAtFirst() = runTest { assertFalse(repository().hasSeen(TOPIC)) }

  @Test
  fun markSeenRecordsTheTopic() = runTest {
    val repository = repository()

    repository.markSeen(TOPIC)

    assertTrue(repository.hasSeen(TOPIC))
  }

  @Test
  fun topicsAreRecordedSeparately() = runTest {
    val repository = repository()

    repository.markSeen("other")

    assertFalse(repository.hasSeen(TOPIC))
  }

  @Test
  fun recordIsKeptForANewInstance() = runTest {
    repository().markSeen(TOPIC)

    assertTrue(repository().hasSeen(TOPIC))
  }

  @Test
  fun markingTwiceKeepsTheTopicSeen() = runTest {
    val repository = repository()

    repository.markSeen(TOPIC)
    repository.markSeen(TOPIC)

    assertTrue(repository.hasSeen(TOPIC))
  }

  private fun TestScope.repository() =
      HelpSeenRepositorySharedPreferences(context, StandardTestDispatcher(testScheduler))

  private companion object {
    const val TOPIC = "campaigns"
  }
}
