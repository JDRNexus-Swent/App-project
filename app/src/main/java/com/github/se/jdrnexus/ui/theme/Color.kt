// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.github.se.jdrnexus.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

data class AppUiColors(
    val isDarkTheme: Boolean,
    val background: Color,
    val surface: Color,
    val raisedSurface: Color,
    val iconBackground: Color,
    val primaryText: Color,
    val secondaryText: Color,
    val outline: Color,
    val accent: Color,
    val accentContent: Color,
    val bannerText: Color,
    val bannerColors: List<Color>,
) {
  companion object {
    val Light =
        AppUiColors(
            isDarkTheme = false,
            background = Color(0xFFF5E9DE),
            surface = Color(0xFFE4D5C8),
            raisedSurface = Color(0xFFF7EDE4),
            iconBackground = Color(0xFFF7EDE4),
            primaryText = Color(0xFF33251D),
            secondaryText = Color(0xFF96745E),
            outline = Color(0xFFB9A08D),
            accent = Color(0xFF704222),
            accentContent = Color.White,
            bannerText = Color.White,
            bannerColors = listOf(Color(0xFF9D7354), Color(0xFF4E5D55), Color(0xFFC49B66)),
        )

    val Dark =
        AppUiColors(
            isDarkTheme = true,
            background = Color(0xFF17100D),
            surface = Color(0xFF2C211B),
            raisedSurface = Color(0xFF1C140F),
            iconBackground = Color(0xFF1C140F),
            primaryText = Color(0xFFE9DDD2),
            secondaryText = Color(0xFFA98E79),
            outline = Color(0xFF705E50),
            accent = Color(0xFFE2BC32),
            accentContent = Color(0xFF17100D),
            bannerText = Color.White,
            bannerColors = listOf(Color(0xFF51402F), Color(0xFF271C17), Color(0xFF80632D)),
        )
  }
}

val LocalAppUiColors = staticCompositionLocalOf { AppUiColors.Light }
