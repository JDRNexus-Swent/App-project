package com.github.se.jdrnexus.ui.dice

/**
 * The ViewModel contract defining the interactions the UI can perform. The implementation will
 * handle whether the roll is saved online (if a groupId exists) or kept locally (offline mode).
 */
interface DiceInterfaceVM {

  /**
   * Updates the currently selected dice type.
   *
   * @param diceType The new dice type selected by the user.
   */
  fun selectDice(diceType: DiceType)

  /**
   * Updates the modifier to be applied to future rolls between 20 and -20.
   *
   * @param modifier The new modifier value (can be positive, negative, or zero).
   */
  fun setModifier(modifier: Int)

  /**
   * Updates the current modifier to be applied to future rolls.
   *
   * @param modifier The increase (can be positive or negative).
   */
  fun increaseModifier(modifier: Int)

  /** Initiates a dice roll. */
  fun rollDice()

  /**
   * Notifies the ViewModel that the UI has finished displaying the roll animation. This will reset
   * the isRolling state to false, allowing new rolls.
   */
  fun onRollAnimationFinished()

  /**
   * Clears the current error message. The UI must call this function after displaying the error to
   * the user (e.g., after dismissing a Snackbar/Toast) to prevent the error from showing again on
   * recomposition.
   */
  fun dismissError()
}
