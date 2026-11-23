// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.ui.help

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * State of a screen's help.
 *
 * @property showHelp whether the screen shows its [HelpSheet].
 */
data class HelpUiState(val showHelp: Boolean = false)

/**
 * Decides when a screen shows its help. The screen draws a [HelpSheet] while [HelpUiState.showHelp]
 * is true and reports the user's actions through [onHelpClicked] and [onHelpDismissed]. It holds no
 * screen-specific content, so any screen with a help can use it.
 */
class HelpViewModel : ViewModel() {
  private val _uiState = MutableStateFlow(HelpUiState())

  /** The help's current state. */
  val uiState: StateFlow<HelpUiState> = _uiState.asStateFlow()

  /** Opens the help; called by the screen's help button. */
  fun onHelpClicked() {
    _uiState.update { it.copy(showHelp = true) }
  }

  /** Closes the help; called once the [HelpSheet] has been dismissed, whatever the way. */
  fun onHelpDismissed() {
    _uiState.update { it.copy(showHelp = false) }
  }
}
