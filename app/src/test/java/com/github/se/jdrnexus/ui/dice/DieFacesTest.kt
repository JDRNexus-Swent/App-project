// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.ui.dice

import dev.romainguy.kotlin.math.Float3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The table of which face shows which value, and the rotation that turns a value to the viewer. */
class DieFacesTest {

  /** Every die except the d4, which is read at its corners. */
  private val diceWithFaces = DiceType.entries - DiceType.D4

  private fun values(diceType: DiceType) = 1..diceType.maxValue

  @Test
  fun everyValueHasItsOwnDirection() {
    DiceType.entries.forEach { type ->
      val directions = values(type).map { faceDirection(type, it) }
      directions.forEachIndexed { i, a ->
        directions.drop(i + 1).forEach { b ->
          assertTrue("$type: two values share a face", dotProduct(a, b) < SAME_DIRECTION_COSINE)
        }
      }
    }
  }

  @Test
  fun oppositeFacesAddUpToTheSameTotalAsOnRealDice() {
    // Catches a misread number: a single wrong value breaks the sum with its opposite face.
    diceWithFaces.forEach { type ->
      values(type).forEach { value ->
        val opposite =
            values(type).single {
              dotProduct(faceDirection(type, it), faceDirection(type, value)) < OPPOSITE_COSINE
            }
        assertEquals(
            "$type: $value is opposite $opposite",
            oppositeTotal(type),
            printed(type, value) + printed(type, opposite),
        )
      }
    }
  }

  @Test
  fun d4CornersAreTheCornersOfARegularTetrahedron() {
    // Catches d4 entries taken from faces instead of corners: the angle between two corners of a
    // regular tetrahedron, seen from its centre, has a cosine of -1/3.
    val corners = values(DiceType.D4).map { faceDirection(DiceType.D4, it) }
    corners.forEachIndexed { i, a ->
      corners.drop(i + 1).forEach { b ->
        assertEquals(TETRAHEDRON_CORNER_COSINE, dotProduct(a, b), TABLE_EPSILON)
      }
    }
  }

  @Test
  fun landingRotationTurnsTheRolledFaceTowardsTheViewer() {
    DiceType.entries.forEach { type ->
      values(type).forEach { value ->
        val rotation = landingRotation(type, value)
        assertSameVector(
            TOWARDS_VIEWER,
            rotate(rotation, faceDirection(type, value)),
            "$type $value:",
        )
      }
    }
  }

  @Test
  fun rotationBetweenOppositeDirectionsIsAHalfTurn() {
    // The general formula divides by zero here: catches a missing special case, which would give
    // a die with no rotation at all (NaN) for a face pointing straight away from the viewer.
    val away = -TOWARDS_VIEWER
    assertSameVector(TOWARDS_VIEWER, rotate(rotationBetween(away, TOWARDS_VIEWER), away))
    val alongX = Float3(x = 1f)
    assertSameVector(-alongX, rotate(rotationBetween(alongX, -alongX), alongX))
  }

  @Test
  fun modelNodeNamesMatchTheNodesInTheModel() {
    assertEquals(
        listOf("d4", "d6", "d8", "d10", "d12", "d20"),
        DiceType.entries.map { it.modelNodeName },
    )
  }

  /** The number printed on the face: the d10's 10 is printed "0". */
  private fun printed(diceType: DiceType, value: Int) =
      if (diceType == DiceType.D10) value % D10_PRINTED_MODULO else value

  /** What two opposite faces add up to: 7 on a d6, 21 on a d20, 9 on a d10 printed 0 to 9. */
  private fun oppositeTotal(diceType: DiceType) =
      if (diceType == DiceType.D10) D10_PRINTED_MODULO - 1 else diceType.maxValue + 1

  private companion object {
    /** Above this cosine, two directions are too close to be two faces of the same die. */
    const val SAME_DIRECTION_COSINE = 0.99f

    /** Below this cosine, two directions are opposite, give or take the table's rounding. */
    const val OPPOSITE_COSINE = -0.99f

    /** The table is rounded to 4 decimals. */
    const val TABLE_EPSILON = 1e-2f

    /** Cosine of the angle between two corners of a regular tetrahedron, seen from its centre. */
    const val TETRAHEDRON_CORNER_COSINE = -1f / 3f

    /** The d10 is printed 0 to 9, its 10 being the 0. */
    const val D10_PRINTED_MODULO = 10
  }
}
