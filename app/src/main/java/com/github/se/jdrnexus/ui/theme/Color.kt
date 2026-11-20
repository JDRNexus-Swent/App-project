// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Theme values Material's colour scheme has no role for. All other colours come from
 * `MaterialTheme.colorScheme` (see [SampleAppTheme]).
 *
 * @property isDarkTheme Whether [SampleAppTheme] uses the dark palette; picks the logo and map
 *   style.
 * @property dieText Colour of the result number drawn on the dice pictures. The pictures are
 *   near-black in both themes, so it is the same light gold in both.
 * @property bannerText Text colour drawn on the campaign banner.
 * @property bannerColors Gradient of the campaign banner on the game-management screen.
 */
data class AppUiColors(
    val isDarkTheme: Boolean,
    val dieText: Color,
    val bannerText: Color,
    val bannerColors: List<Color>,
) {
  companion object {
    val Light =
        AppUiColors(
            isDarkTheme = false,
            dieText = Color(0xFFD9B87A),
            bannerText = Color.White,
            bannerColors = listOf(Color(0xFF9D7354), Color(0xFF4E5D55), Color(0xFFC49B66)),
        )

    val Dark =
        AppUiColors(
            isDarkTheme = true,
            dieText = Color(0xFFD9B87A),
            bannerText = Color.White,
            bannerColors = listOf(Color(0xFF51402F), Color(0xFF271C17), Color(0xFF80632D)),
        )
  }
}

/** Provides the current [AppUiColors]; set by [SampleAppTheme]. */
val LocalAppUiColors = staticCompositionLocalOf { AppUiColors.Light }
