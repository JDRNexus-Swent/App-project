// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.ui.dice

import dev.romainguy.kotlin.math.Float3
import dev.romainguy.kotlin.math.Quaternion
import org.junit.Assert.assertEquals

/**
 * Tolerance on each component of a unit vector computed in floats: about 0.06° between two
 * directions.
 */
internal const val VECTOR_EPSILON = 1e-3f

/**
 * Above this cosine, two unit directions count as the same: within about 2.6°. A cosine near 1
 * moves with the square of the angle, so this is far looser than [VECTOR_EPSILON] per component.
 */
private const val SAME_DIRECTION_COSINE = 0.999f

/** The three axes: a rotation is fully known once we know where it sends them. */
private val AXES = listOf(Float3(x = 1f), Float3(y = 1f), Float3(z = 1f))

/** Asserts that [actual] is the same direction as [expected], component by component. */
internal fun assertSameVector(expected: Float3, actual: Float3, message: String = "") {
  assertEquals("$message x of $actual", expected.x, actual.x, VECTOR_EPSILON)
  assertEquals("$message y of $actual", expected.y, actual.y, VECTOR_EPSILON)
  assertEquals("$message z of $actual", expected.z, actual.z, VECTOR_EPSILON)
}

/**
 * Asserts that [expected] and [actual] turn the die the same way. Compares where they send the
 * three axes rather than their four numbers: q and -q are the same rotation.
 */
internal fun assertSameRotation(expected: Quaternion, actual: Quaternion, message: String = "") {
  AXES.forEach { axis -> assertSameVector(rotate(expected, axis), rotate(actual, axis), message) }
}

/** Whether [a] and [b] turn the die the same way, within [SAME_DIRECTION_COSINE]. */
internal fun sameRotation(a: Quaternion, b: Quaternion): Boolean = AXES.all { axis ->
  dotProduct(rotate(a, axis), rotate(b, axis)) > SAME_DIRECTION_COSINE
}
