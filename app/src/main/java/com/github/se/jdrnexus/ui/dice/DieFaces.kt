// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.ui.dice

import dev.romainguy.kotlin.math.Float3
import dev.romainguy.kotlin.math.Quaternion
import dev.romainguy.kotlin.math.normalize
import kotlin.math.abs

/**
 * The 3D dice, in the app's assets: one node per die, named after it ("d4" … "d20"), each centred
 * on the origin. "RPG Dice Set" by ghosted (sketchfab.com/dianaavlis2002), CC BY 4.0.
 */
internal const val DICE_MODEL_ASSET = "models/dice.glb"

/** Name of this die's node in [DICE_MODEL_ASSET], e.g. "d20". */
internal val DiceType.modelNodeName: String
  get() = name.lowercase()

/** Direction from the die towards the viewer: the camera sits on +z and looks at the die. */
internal val TOWARDS_VIEWER = Float3(0f, 0f, 1f)

/** Below this cosine, two directions count as exactly opposite. */
private const val OPPOSITE_COSINE = -0.9999f

/**
 * Outward normal, in the model's coordinates, of the face showing each value, value 1 first. The d4
 * is read at a corner instead (each corner shows the same number on its three faces), so its
 * vectors point from the centre to the corners.
 *
 * Measured from [DICE_MODEL_ASSET] by reading the number on each face of its texture. The tests
 * check the usual layout of real dice: opposite faces add up to the same total.
 */
private val FACE_DIRECTIONS: Map<DiceType, List<Float3>> =
    mapOf(
        DiceType.D4 to
            listOf(
                Float3(0.9310f, -0.3298f, 0.1562f),
                Float3(-0.6005f, -0.3420f, 0.7228f),
                Float3(-0.0051f, 1.0000f, 0.0078f),
                Float3(-0.3254f, -0.3281f, -0.8868f),
            ),
        DiceType.D6 to
            listOf(
                Float3(-0.9642f, 0.0000f, -0.2651f),
                Float3(0.0000f, 1.0000f, 0.0000f),
                Float3(0.2651f, 0.0000f, -0.9642f),
                Float3(-0.2651f, 0.0000f, 0.9642f),
                Float3(0.0000f, -1.0000f, 0.0000f),
                Float3(0.9642f, 0.0000f, 0.2651f),
            ),
        DiceType.D8 to
            listOf(
                Float3(-0.1961f, -0.2911f, -0.9364f),
                Float3(-0.6549f, -0.4249f, 0.6249f),
                Float3(0.0760f, -0.9951f, -0.0630f),
                Float3(-0.9274f, 0.2791f, -0.2491f),
                Float3(0.9274f, -0.2791f, 0.2491f),
                Float3(-0.0760f, 0.9951f, 0.0630f),
                Float3(0.6549f, 0.4249f, -0.6249f),
                Float3(0.1961f, 0.2911f, 0.9364f),
            ),
        DiceType.D10 to
            listOf(
                Float3(0.0040f, -0.9999f, -0.0100f),
                Float3(-0.1931f, 0.5732f, 0.7963f),
                Float3(0.7801f, 0.1030f, -0.6171f),
                Float3(-0.9553f, -0.0950f, -0.2801f),
                Float3(0.9553f, 0.0950f, 0.2801f),
                Float3(-0.7801f, -0.1030f, 0.6171f),
                Float3(0.1931f, -0.5732f, -0.7963f),
                Float3(-0.0040f, 0.9999f, 0.0100f),
                Float3(0.4760f, -0.5870f, 0.6549f),
                // The face printed "0".
                Float3(-0.4760f, 0.5870f, -0.6549f),
            ),
        DiceType.D12 to
            listOf(
                Float3(0.0060f, -0.9999f, 0.0100f),
                Float3(-0.6047f, -0.4578f, -0.6517f),
                Float3(-0.4390f, 0.4530f, 0.7760f),
                Float3(-0.8098f, -0.4479f, 0.3789f),
                Float3(0.8800f, -0.4400f, 0.1790f),
                Float3(0.1080f, -0.4370f, 0.8930f),
                Float3(-0.1080f, 0.4370f, -0.8930f),
                Float3(-0.8800f, 0.4400f, -0.1790f),
                Float3(0.8098f, 0.4479f, -0.3789f),
                Float3(0.4390f, -0.4530f, -0.7760f),
                Float3(0.6047f, 0.4578f, 0.6517f),
                Float3(-0.0060f, 0.9999f, -0.0100f),
            ),
        DiceType.D20 to
            listOf(
                Float3(0.2889f, -0.3299f, 0.8987f),
                Float3(0.4220f, 0.3310f, -0.8440f),
                Float3(-0.5998f, -0.7458f, 0.2899f),
                Float3(-0.0020f, 1.0000f, -0.0030f),
                Float3(0.9208f, 0.3359f, 0.1980f),
                Float3(-0.9421f, 0.3310f, 0.0540f),
                Float3(0.5519f, -0.7429f, 0.3789f),
                Float3(-0.5173f, -0.3372f, -0.7865f),
                Float3(-0.6342f, 0.3341f, 0.6972f),
                Float3(0.0530f, -0.7478f, -0.6618f),
                Float3(-0.0530f, 0.7478f, 0.6618f),
                Float3(0.6342f, -0.3341f, -0.6972f),
                Float3(0.5173f, 0.3372f, 0.7865f),
                Float3(-0.5519f, 0.7429f, -0.3789f),
                Float3(0.9421f, -0.3310f, -0.0540f),
                Float3(-0.9208f, -0.3359f, -0.1980f),
                Float3(0.0020f, -1.0000f, 0.0030f),
                Float3(0.5998f, 0.7458f, -0.2899f),
                Float3(-0.4220f, -0.3310f, 0.8440f),
                Float3(-0.2889f, 0.3299f, -0.8987f),
            ),
    )

/** Outward direction, in the model's coordinates, of the face (corner on a d4) showing [value]. */
internal fun faceDirection(diceType: DiceType, value: Int): Float3 =
    normalize(FACE_DIRECTIONS.getValue(diceType)[value - 1])

/** Rotation of a die resting with [value] turned towards the viewer. */
internal fun landingRotation(diceType: DiceType, value: Int): Quaternion =
    rotationBetween(faceDirection(diceType, value), TOWARDS_VIEWER)

/** Shortest rotation turning the unit direction [from] onto the unit direction [to]. */
internal fun rotationBetween(from: Float3, to: Float3): Quaternion {
  // dotProduct and crossProduct are ours: see DieMath.kt for why kotlin-math's are not imported.
  val cosine = dotProduct(from, to)
  if (cosine < OPPOSITE_COSINE) {
    // Half a turn, around any axis perpendicular to [from]: x, unless [from] lies along x.
    val helper = if (abs(from.x) < abs(OPPOSITE_COSINE)) Float3(x = 1f) else Float3(y = 1f)
    return Quaternion(normalize(crossProduct(from, helper)), 0f)
  }
  // A quaternion turns by twice its own angle: (cross, 1 + cos), once normalised, holds exactly
  // half the angle between the two directions, so it turns by the whole of it.
  return normalize(Quaternion(crossProduct(from, to), 1f + cosine))
}
