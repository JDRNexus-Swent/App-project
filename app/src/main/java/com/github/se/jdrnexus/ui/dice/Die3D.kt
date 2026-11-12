// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.ui.dice

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import dev.romainguy.kotlin.math.Quaternion
import io.github.sceneview.SceneView
import io.github.sceneview.SurfaceType
import io.github.sceneview.math.Position
import io.github.sceneview.node.ModelNode as DiceModelNode
import io.github.sceneview.rememberCameraNode
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberModelInstance
import io.github.sceneview.rememberModelLoader

/**
 * Where the camera starts, on the +z axis looking at the die. Only its direction matters: the scene
 * then moves it closer or further so the die fills the view, whatever the model's size.
 */
private const val CAMERA_START_DISTANCE = 1f

/** The point the camera looks at: the die's centre. */
private val DIE_CENTRE = Position(0f, 0f, 0f)

/**
 * Extra distance the camera keeps beyond a tight framing of the resting die, as a fraction of that
 * distance (SceneView's default is 0.15). A tumbling die is wider than a resting one (a turned cube
 * is up to 1.7× wider), and it also hops and bounces, so it needs this margin to never touch the
 * edges of its view, which would cut it off.
 */
private const val DIE_FRAMING_PADDING = 0.8f

/**
 * The 3D [diceType] from [DICE_MODEL_ASSET], on a transparent background so the screen shows
 * through around it. [rotation] and [lift] (a height, as a fraction of the die's size) are read on
 * every frame, without recomposing, so a roll can move the die smoothly.
 *
 * All dice live in one model, sharing their textures; only [diceType] is shown. [onDieSelected] is
 * called once the die's node is found in the model and made the only visible one, e.g. for tests to
 * wait for it; the die may not be drawn yet.
 */
@Composable
internal fun Die3D(
    diceType: DiceType,
    rotation: () -> Quaternion,
    lift: () -> Float,
    modifier: Modifier = Modifier,
    onDieSelected: () -> Unit = {},
) {
  val engine = rememberEngine()
  val modelLoader = rememberModelLoader(engine)
  // Null while the model loads, which takes a few frames.
  val model = rememberModelInstance(modelLoader, DICE_MODEL_ASSET)
  val cameraNode =
      rememberCameraNode(engine) {
        position = Position(0f, 0f, CAMERA_START_DISTANCE)
        lookAt(DIE_CENTRE)
      }
  var dice by remember { mutableStateOf<DiceModelNode?>(null) }
  // The loop below starts once; these keep it reading the latest functions it was given.
  val currentRotation by rememberUpdatedState(rotation)
  val currentLift by rememberUpdatedState(lift)
  val currentOnDieSelected by rememberUpdatedState(onDieSelected)

  LaunchedEffect(dice, diceType) {
    val node = dice ?: return@LaunchedEffect
    node.renderableNodes.forEach { it.isVisible = it.name == diceType.modelNodeName }
    // Only once the die really is in the model: a node renamed in dice.glb would show nothing.
    if (node.renderableNodes.any { it.name == diceType.modelNodeName }) currentOnDieSelected()
    // Follows the roll frame by frame, without recomposing the scene.
    snapshotFlow { currentRotation() to currentLift() }
        .collect { (newRotation, newLift) ->
          node.quaternion = newRotation
          node.position = Position(0f, newLift * node.size.y, 0f)
        }
  }

  SceneView(
      modifier = modifier,
      // Drawn inline with the other composables, so its transparent background blends in.
      surfaceType = SurfaceType.TextureSurface,
      engine = engine,
      modelLoader = modelLoader,
      isOpaque = false,
      autoFitContent = true,
      framingPadding = DIE_FRAMING_PADDING,
      cameraNode = cameraNode,
      // The die only moves when rolled: no orbiting or zooming with the fingers.
      cameraManipulator = null,
      onGestureListener = null,
  ) {
    model?.let { instance ->
      ModelNode(modelInstance = instance, autoAnimate = false, apply = { dice = this })
    }
  }
}
