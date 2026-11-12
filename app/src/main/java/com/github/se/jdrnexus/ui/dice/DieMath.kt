// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.ui.dice

import dev.romainguy.kotlin.math.Float3
import dev.romainguy.kotlin.math.Quaternion
import dev.romainguy.kotlin.math.conjugate

/*
 * Vector and quaternion operations for the 3D die, written here instead of imported.
 *
 * kotlin-math, which comes with SceneView, has them (`dot`, `cross`, `Quaternion.times`), but as
 * `inline` functions: their compiled body is copied into our code instead of being called. That
 * body is compiled for Java 21, and the Kotlin compiler refuses to copy Java 21 bytecode into this
 * Java 17 project ("Cannot inline bytecode built with JVM target 21"). Its non-inline functions
 * (`normalize`, `slerp`, `conjugate`, `Quaternion.fromAxisAngle`) are only called, so they import
 * fine. These are the same formulas as in kotlin-math.
 */

/** Dot product of [a] and [b]: the cosine of their angle when both have length 1. */
internal fun dotProduct(a: Float3, b: Float3): Float = a.x * b.x + a.y * b.y + a.z * b.z

/** Cross product of [a] and [b]: perpendicular to both, following the right-hand rule. */
internal fun crossProduct(a: Float3, b: Float3): Float3 =
    Float3(a.y * b.z - a.z * b.y, a.z * b.x - a.x * b.z, a.x * b.y - a.y * b.x)

/** The rotation [inner] followed by the rotation [outer] (the quaternion product outer × inner). */
internal fun combine(outer: Quaternion, inner: Quaternion): Quaternion =
    Quaternion(
        outer.w * inner.x + outer.x * inner.w + outer.y * inner.z - outer.z * inner.y,
        outer.w * inner.y - outer.x * inner.z + outer.y * inner.w + outer.z * inner.x,
        outer.w * inner.z + outer.x * inner.y - outer.y * inner.x + outer.z * inner.w,
        outer.w * inner.w - outer.x * inner.x - outer.y * inner.y - outer.z * inner.z,
    )

/** [vector] turned by the unit quaternion [rotation]: rotation × vector × conjugate(rotation). */
internal fun rotate(rotation: Quaternion, vector: Float3): Float3 {
  val turned = combine(combine(rotation, Quaternion(vector, 0f)), conjugate(rotation))
  return Float3(turned.x, turned.y, turned.z)
}
