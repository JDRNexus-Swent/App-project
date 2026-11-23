// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.ui.help

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HelpViewModelTest {
  private val viewModel = HelpViewModel()

  @Test
  fun helpIsHiddenAtFirst() {
    assertFalse(viewModel.uiState.value.showHelp)
  }

  @Test
  fun helpButtonShowsTheHelp() {
    viewModel.onHelpClicked()

    assertTrue(viewModel.uiState.value.showHelp)
  }

  @Test
  fun dismissingHidesTheHelp() {
    viewModel.onHelpClicked()
    viewModel.onHelpDismissed()

    assertFalse(viewModel.uiState.value.showHelp)
  }

  @Test
  fun helpButtonShowsTheHelpAgainAfterItWasDismissed() {
    viewModel.onHelpClicked()
    viewModel.onHelpDismissed()
    viewModel.onHelpClicked()

    assertTrue(viewModel.uiState.value.showHelp)
  }

  @Test
  fun dismissingTwiceKeepsTheHelpHidden() {
    viewModel.onHelpClicked()
    viewModel.onHelpDismissed()
    viewModel.onHelpDismissed()

    assertFalse(viewModel.uiState.value.showHelp)
  }
}
