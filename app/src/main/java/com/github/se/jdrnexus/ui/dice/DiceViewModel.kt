package com.github.se.jdrnexus.ui.dice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.github.se.jdrnexus.model.repository.DiceLogic
import com.github.se.jdrnexus.model.repository.DiceRepositoryFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Factory responsible for instantiating the DiceViewModel with its required dependencies. This
 * class tells the Android framework exactly how to construct the ViewModel.
 */
class DiceViewModelFactory(
    private val diceLogic: DiceLogic,
    private val diceRepository: DiceRepositoryFirestore?,
    private val groupId: String? = null,
) : ViewModelProvider.Factory {

  @Suppress("UNCHECKED_CAST")
  override fun <T : ViewModel> create(modelClass: Class<T>): T {
    // Verify that the requested ViewModel class matches our DiceViewModel
    if (modelClass.isAssignableFrom(DiceViewModel::class.java)) {
      // Construct and return the ViewModel with the provided parameters
      return DiceViewModel(diceLogic, diceRepository, groupId) as T
    }
    throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
  }
}

/**
 * ViewModel managing the state and logic of the Dice Screen.
 *
 * @property diceLogic The logic component responsible for math generation.
 * @property diceRepository The repository handling Cloud Firestore persistence.
 * @property groupId The ID of the current campaign group. If null, the app operates in
 *   offline/local mode and skips database synchronization.
 */
class DiceViewModel(
    private val diceLogic: DiceLogic,
    private val diceRepository: DiceRepositoryFirestore?,
    private val groupId: String? = null,
) : ViewModel(), DiceInterfaceVM {

  // Internal mutable state
  private val _uiState = MutableStateFlow(DiceUiState())
  // Public immutable state exposed to the UI
  val uiState: StateFlow<DiceUiState> = _uiState.asStateFlow()

  override fun selectDice(diceType: DiceType) {
    _uiState.update { currentState -> currentState.copy(selectedDice = diceType) }
  }

  override fun setModifier(modifier: Int) {
    val newModifier = modifier.coerceIn(-20, 20)
    _uiState.update { currentState -> currentState.copy(modifier = newModifier) }
  }

  override fun increaseModifier(modifier: Int) {
    _uiState.update { currentState ->
      // modifier should be between 20 and -20 (more is incoherent)
      val newModifier = (currentState.modifier + modifier).coerceIn(-20, 20)
      currentState.copy(modifier = newModifier)
    }
  }

  override fun rollDice() {
    val currentState = _uiState.value

    // Prevent starting a new roll if one is already in progress
    if (currentState.isRolling) return

    val repository = groupId?.let {
      checkNotNull(diceRepository) { "A dice repository is required for group rolls." }
    }
    val result = diceLogic.roll(currentState.selectedDice, currentState.modifier)

    _uiState.update {
      it.copy(
          isRolling = true,
          currentResult = result,
      )
    }

    if (groupId != null && repository != null) {
      // Launch a coroutine in the ViewModel's scope so it doesn't block the main thread
      viewModelScope.launch {
        val saveResult = repository.saveRollResult(groupId, result)

        // If saving fails (e.g., permission error or weird edge case), notify the UI
        if (saveResult.isFailure) {
          _uiState.update {
            it.copy(
                errorMessage =
                    "Failed to sync roll: ${saveResult.exceptionOrNull()?.localizedMessage}"
            )
          }
        }
      }
    }
  }

  override fun onRollAnimationFinished() {
    _uiState.update { it.copy(isRolling = false) }
  }

  override fun dismissError() {
    _uiState.update { currentState -> currentState.copy(errorMessage = null) }
  }
}
