// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.ui.dice

import dev.romainguy.kotlin.math.Float3
import dev.romainguy.kotlin.math.Quaternion
import org.junit.Assert.assertEquals
import org.junit.Test

/** The hand-written vector and quaternion operations of DieMath.kt. */
class DieMathTest {

  @Test
  fun dotProductIsTheCosineOfTheAngleBetweenUnitVectors() {
    assertEquals(1f, dotProduct(X, X), VECTOR_EPSILON)
    assertEquals(0f, dotProduct(X, Y), VECTOR_EPSILON)
    assertEquals(-1f, dotProduct(X, -X), VECTOR_EPSILON)
  }

  @Test
  fun crossProductFollowsTheRightHandRule() {
    assertSameVector(Z, crossProduct(X, Y))
    // Swapping the operands flips the result: catches a cross product with its terms reversed.
    assertSameVector(-Z, crossProduct(Y, X))
  }

  @Test
  fun rotateTurnsAVectorAroundTheAxis() {
    // A quarter turn around z sends x to y.
    assertSameVector(Y, rotate(QUARTER_TURN_AROUND_Z, X))
  }

  @Test
  fun combineAppliesTheInnerRotationFirst() {
    // x, a quarter turn around z: y; then a quarter turn around x: z.
    val zThenX = combine(outer = QUARTER_TURN_AROUND_X, inner = QUARTER_TURN_AROUND_Z)
    assertSameVector(Z, rotate(zThenX, X))
    // In the other order, x stays x, then becomes y: catches outer and inner swapped.
    val xThenZ = combine(outer = QUARTER_TURN_AROUND_Z, inner = QUARTER_TURN_AROUND_X)
    assertSameVector(Y, rotate(xThenZ, X))
  }

  private companion object {
    val X = Float3(x = 1f)
    val Y = Float3(y = 1f)
    val Z = Float3(z = 1f)

    /** A right angle, in degrees. */
    const val QUARTER_TURN_DEGREES = 90f

    val QUARTER_TURN_AROUND_X: Quaternion = Quaternion.fromAxisAngle(X, QUARTER_TURN_DEGREES)
    val QUARTER_TURN_AROUND_Z: Quaternion = Quaternion.fromAxisAngle(Z, QUARTER_TURN_DEGREES)
  }
}
