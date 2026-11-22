// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.ui.help

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.Constraints
import com.github.se.jdrnexus.resources.C
import kotlin.math.roundToInt

/** Share of the window height a [HelpPage.illustration] may take when its page sets none. */
const val DEFAULT_HELP_ILLUSTRATION_HEIGHT_FRACTION = 0.2f

/**
 * Draws [content] as a picture: scaled down so it is at most [maxHeightFraction] of the window
 * height, centred, and inert.
 *
 * [content] is first measured at the full width of the help page, with as much height as it needs,
 * so it lays itself out as on a screen of that width; it is then drawn smaller as a whole, and
 * never enlarged. Taps on it reach nothing, while horizontal swipes still move the pager, and
 * screen readers skip it: the page's text describes it.
 *
 * @param content the composables to show, usually a screen's own components with example data.
 * @param maxHeightFraction largest share of the window height the picture may take, in (0, 1].
 */
@Composable
internal fun HelpIllustration(
    content: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    maxHeightFraction: Float = DEFAULT_HELP_ILLUSTRATION_HEIGHT_FRACTION,
) {
  val maxHeight = LocalWindowInfo.current.containerDpSize.height * maxHeightFraction
  Box(
      modifier = modifier.fillMaxWidth().clearAndSetSemantics { testTag = C.Tag.help_illustration },
  ) {
    Layout(content = { Box { content() } }) { measurables, constraints ->
      // Natural size: the full available width, and as much height as the content wants.
      val placeable =
          measurables
              .single()
              .measure(
                  constraints.copy(minWidth = 0, minHeight = 0, maxHeight = Constraints.Infinity)
              )
      val maxHeightPx = maxHeight.roundToPx()
      // Never enlarge, only shrink; an empty content keeps scale 1 instead of dividing by zero.
      val scale =
          if (placeable.height == 0) 1f else minOf(1f, maxHeightPx / placeable.height.toFloat())
      val scaledWidth = (placeable.width * scale).roundToInt()
      val scaledHeight = (placeable.height * scale).roundToInt()
      // Reports the scaled size, so the page reserves only the space the picture really takes.
      layout(constraints.maxWidth, scaledHeight) {
        placeable.placeWithLayer(x = (constraints.maxWidth - scaledWidth) / 2, y = 0) {
          scaleX = scale
          scaleY = scale
          transformOrigin = TransformOrigin(0f, 0f)
        }
      }
    }
    // Drawn over the picture: as the top-most element it receives the touches, so the buttons
    // underneath never do. It consumes nothing, so the pager still sees the drags.
    Box(
        Modifier.matchParentSize().pointerInput(Unit) {
          awaitPointerEventScope {
            while (true) {
              awaitPointerEvent()
            }
          }
        }
    )
  }
}
