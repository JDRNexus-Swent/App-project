// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.ui.help

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.github.se.jdrnexus.model.help.HelpSeenRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
 *
 * When created, it opens the help by itself if [repository] has no record of [topic] being seen;
 * closing the help records it, so it opens by itself only until the user first closes it.
 *
 * @param topic identifies the screen's help in [repository].
 * @param repository remembers which helps have been seen.
 */
class HelpViewModel(
    private val topic: String,
    private val repository: HelpSeenRepository,
) : ViewModel() {
  private val _uiState = MutableStateFlow(HelpUiState())

  /** The help's current state. */
  val uiState: StateFlow<HelpUiState> = _uiState.asStateFlow()

  init {
    viewModelScope.launch {
      if (!repository.hasSeen(topic)) {
        _uiState.update { it.copy(showHelp = true) }
      }
    }
  }

  /** Opens the help; called by the screen's help button. */
  fun onHelpClicked() {
    _uiState.update { it.copy(showHelp = true) }
  }

  /**
   * Closes the help and records it as seen; called once the [HelpSheet] has been dismissed,
   * whatever the way.
   */
  fun onHelpDismissed() {
    _uiState.update { it.copy(showHelp = false) }
    viewModelScope.launch { repository.markSeen(topic) }
  }

  companion object {
    /** Creates a [HelpViewModel] for the help of [topic], remembered in [repository]. */
    fun factory(topic: String, repository: HelpSeenRepository): ViewModelProvider.Factory =
        viewModelFactory {
          initializer { HelpViewModel(topic, repository) }
        }
  }
}
