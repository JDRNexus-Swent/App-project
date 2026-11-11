// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.github.se.jdrnexus.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

private val DarkColorScheme =
    darkColorScheme(
        primary = Color(0xFFD4B95D), // gold
        secondary = Color(0xFFB89B5A),
        tertiary = Color(0xFF8E8278),
        background = Color(0xFF1C1816),
        surface = Color(0xFF221A18),
        onPrimary = Color(0xFF2E2520),
        onSecondary = Color.White,
        onTertiary = Color.White,
        onBackground = Color(0xFF6F5138),
        onSurface = Color(0xFFF2EEE9),
    )

private val LightColorScheme =
    lightColorScheme(
        primary = Color(0xFF6F5138), // brown
        secondary = Color(0xFFD4B95D), // gold
        tertiary = Color(0xFF8E8278),
        background = Color(0xFFF2EEE9),
        surface = Color(0xFFFFFFFF),
        onPrimary = Color.White,
        onSecondary = Color(0xFF2E2520),
        onTertiary = Color.White,
        onBackground = Color(0xFFE7E0D8),
        onSurface = Color(0xFF33251D),
    )

@Composable
fun SampleAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
  val colorScheme =
      when {
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
      }

  CompositionLocalProvider(
      LocalAppUiColors provides if (darkTheme) AppUiColors.Dark else AppUiColors.Light
  ) {
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
  }
}
