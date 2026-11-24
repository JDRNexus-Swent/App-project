// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.ui.help

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable

/**
 * One page of a screen's help, shown by [HelpSheet].
 *
 * @property title string resource of the page's title.
 * @property body string resource of the page's explanation, one to three short sentences.
 * @property illustration optional picture drawn above the title, usually the screen's own
 *   composables filled with example data. [HelpIllustration] shrinks it and ignores taps on it, so
 *   it only shows what the text explains.
 * @property illustrationMaxHeightFraction largest share of the window height [illustration] may
 *   take, in (0, 1]: small for one component, larger for a whole screen.
 * @throws IllegalArgumentException if [illustrationMaxHeightFraction] is outside (0, 1].
 */
data class HelpPage(
    @StringRes val title: Int,
    @StringRes val body: Int,
    val illustration: (@Composable () -> Unit)? = null,
    val illustrationMaxHeightFraction: Float = DEFAULT_HELP_ILLUSTRATION_HEIGHT_FRACTION,
) {
  init {
    require(illustrationMaxHeightFraction > 0f && illustrationMaxHeightFraction <= 1f) {
      "illustrationMaxHeightFraction must be in (0, 1], was $illustrationMaxHeightFraction"
    }
  }
}
