package com.github.se.jdrnexus.model.repository

import com.github.se.jdrnexus.ui.dice.*
import kotlin.random.Random

/** Handles the mathematical generation of dice rolls. */
class DiceLogic {

  /** Rolls the specified dice and applies the modifier. */
  fun roll(diceType: DiceType, modifier: Int): DiceRollResult {
    // Generate a random integer between 1 and the dice's maximum value (inclusive)
    // nextInt(from, until) is inclusive for 'from' and exclusive for 'until'
    val rawValue = Random.nextInt(1, diceType.maxValue + 1)

    // Calculate the final result by adding the modifier
    val totalValue = rawValue + modifier

    return DiceRollResult(
        diceType = diceType,
        rawValue = rawValue,
        modifier = modifier,
        totalValue = totalValue,
    )
  }
}
