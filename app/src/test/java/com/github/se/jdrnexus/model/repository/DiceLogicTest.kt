// Co-authored-by: AI Agent
package com.github.se.jdrnexus.model.repository

import com.github.se.jdrnexus.ui.dice.DiceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiceLogicTest {

  private val diceLogic = DiceLogic()

  @Test
  fun `roll generates value within bounds and applies modifier`() {
    val diceType = DiceType.D20
    val modifier = 5

    // Since we use Random internally, we can run this multiple times
    // to ensure it stays within bounds.
    repeat(100) {
      val result = diceLogic.roll(diceType, modifier)

      assertEquals("Dice type should be D20", diceType, result.diceType)
      assertEquals("Modifier should be $modifier", modifier, result.modifier)

      assertTrue(
          "Raw value ${result.rawValue} should be >= 1",
          result.rawValue >= 1,
      )
      assertTrue(
          "Raw value ${result.rawValue} should be <= ${diceType.maxValue}",
          result.rawValue <= diceType.maxValue,
      )

      assertEquals(
          "Total value should be rawValue + modifier",
          result.rawValue + modifier,
          result.totalValue,
      )
    }
  }

  @Test
  fun `roll works correctly for all dice types`() {
    for (diceType in DiceType.entries) {
      val result = diceLogic.roll(diceType, 0)
      assertTrue(
          "Raw value ${result.rawValue} should be between 1 and ${diceType.maxValue}",
          result.rawValue in 1..diceType.maxValue,
      )
      assertEquals(
          "With modifier 0, totalValue should equal rawValue",
          result.rawValue,
          result.totalValue,
      )
    }
  }

  @Test
  fun `roll with negative modifier calculates total value correctly`() {
    val result = diceLogic.roll(DiceType.D10, -3)
    assertEquals(-3, result.modifier)
    assertEquals(result.rawValue - 3, result.totalValue)
  }
}
