// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.ui.help

import com.github.se.jdrnexus.model.help.HelpSeenRepository
import com.github.se.jdrnexus.utils.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HelpViewModelTest {
  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  private val repository = FakeHelpSeenRepository()

  @Test
  fun helpOpensByItselfWhenItHasNotBeenSeen() = runTest {
    val viewModel = createViewModel()

    assertTrue(viewModel.uiState.value.showHelp)
  }

  @Test
  fun helpStaysClosedWhenItHasBeenSeen() = runTest {
    repository.markSeen(TOPIC)

    val viewModel = createViewModel()

    assertFalse(viewModel.uiState.value.showHelp)
  }

  @Test
  fun helpOfAnotherTopicDoesNotCount() = runTest {
    repository.markSeen("other")

    val viewModel = createViewModel()

    assertTrue(viewModel.uiState.value.showHelp)
  }

  @Test
  fun dismissingHidesTheHelpAndRecordsItAsSeen() = runTest {
    val viewModel = createViewModel()

    viewModel.onHelpDismissed()
    advanceUntilIdle()

    assertFalse(viewModel.uiState.value.showHelp)
    assertTrue(repository.hasSeen(TOPIC))
  }

  @Test
  fun helpIsClosedBeforeTheRecordIsRead() = runTest {
    val viewModel = HelpViewModel(TOPIC, repository)

    assertFalse(viewModel.uiState.value.showHelp)
  }

  @Test
  fun helpButtonShowsTheHelpEvenWhenItHasBeenSeen() = runTest {
    repository.markSeen(TOPIC)
    val viewModel = createViewModel()

    viewModel.onHelpClicked()

    assertTrue(viewModel.uiState.value.showHelp)
  }

  @Test
  fun helpButtonShowsTheHelpAgainAfterItWasDismissed() = runTest {
    val viewModel = createViewModel()
    viewModel.onHelpDismissed()
    advanceUntilIdle()

    viewModel.onHelpClicked()

    assertTrue(viewModel.uiState.value.showHelp)
  }

  @Test
  fun dismissingTwiceKeepsTheHelpHiddenAndSeen() = runTest {
    val viewModel = createViewModel()

    viewModel.onHelpDismissed()
    viewModel.onHelpDismissed()
    advanceUntilIdle()

    assertFalse(viewModel.uiState.value.showHelp)
    assertTrue(repository.hasSeen(TOPIC))
    assertEquals(setOf(TOPIC), repository.seenTopics)
  }

  /** Creates the ViewModel and lets it read whether its help has been seen. */
  private fun TestScope.createViewModel(): HelpViewModel {
    val viewModel = HelpViewModel(TOPIC, repository)
    advanceUntilIdle()
    return viewModel
  }

  /** Keeps the seen topics in memory, as the preferences file keeps them on the device. */
  private class FakeHelpSeenRepository : HelpSeenRepository {
    val seenTopics = mutableSetOf<String>()

    override suspend fun hasSeen(topic: String): Boolean = topic in seenTopics

    override suspend fun markSeen(topic: String) {
      seenTopics += topic
    }
  }

  private companion object {
    const val TOPIC = "campaigns"
  }
}
