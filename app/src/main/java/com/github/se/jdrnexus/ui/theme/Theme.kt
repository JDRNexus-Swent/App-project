// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

/**
 * The app's dark palette. Every Material colour role except `scrim` and `surfaceTint` is set, so
 * built-in components (dialogs, text fields, menus) never fall back to Material's default purple:
 * `surfaceTint` defaults to this palette's `primary`, and `scrim`, the layer that dims the screen
 * behind a dialog, keeps Material's black. The "fixed" roles keep the same value in both themes, as
 * Material defines them. Each `on…` colour reaches 4.5:1 contrast on its matching background, and
 * `outline`, the text-field border, reaches 3:1 on the field fill, `background` and `surface` (both
 * checked by `ThemeTest`).
 */
internal val DarkColorScheme =
    darkColorScheme(
        primary = Color(0xFFE2BC32), // gold
        onPrimary = Color(0xFF17100D),
        primaryContainer = Color(0xFF5A4A14),
        onPrimaryContainer = Color(0xFFF6E3A1),
        inversePrimary = Color(0xFF704222),
        secondary = Color(0xFF735927),
        onSecondary = Color(0xFFE9DDD2),
        secondaryContainer = Color(0xFF4D3B18),
        onSecondaryContainer = Color(0xFFF3E2BC),
        tertiary = Color(0xFFA98E79),
        onTertiary = Color(0xFF17100D),
        tertiaryContainer = Color(0xFF4A3C33),
        onTertiaryContainer = Color(0xFFEADBCE),
        background = Color(0xFF17100D),
        onBackground = Color(0xFFE9DDD2),
        surface = Color(0xFF2C211B),
        onSurface = Color(0xFFE9DDD2),
        surfaceVariant = Color(0xFF2C211B),
        onSurfaceVariant = Color(0xFFA98E79),
        inverseSurface = Color(0xFFE9DDD2),
        inverseOnSurface = Color(0xFF2C211B),
        error = Color(0xFFFFB4A9),
        onError = Color(0xFF5F1410),
        errorContainer = Color(0xFF7A2318),
        onErrorContainer = Color(0xFFFFDAD4),
        outline = Color(0xFF897362),
        outlineVariant = Color(0xFF4A3C33),
        surfaceBright = Color(0xFF3A2E27),
        surfaceDim = Color(0xFF17100D),
        surfaceContainerLowest = Color(0xFF1C140F),
        surfaceContainerLow = Color(0xFF211813),
        surfaceContainer = Color(0xFF261C17),
        surfaceContainerHigh = Color(0xFF2C211B),
        surfaceContainerHighest = Color(0xFF33271F),
        primaryFixed = Color(0xFFEBD3BD),
        primaryFixedDim = Color(0xFFD8B99E),
        onPrimaryFixed = Color(0xFF2B1607),
        onPrimaryFixedVariant = Color(0xFF5A3519),
        secondaryFixed = Color(0xFFF0E0BF),
        secondaryFixedDim = Color(0xFFD9B87A),
        onSecondaryFixed = Color(0xFF2A1F08),
        onSecondaryFixedVariant = Color(0xFF5A4519),
        tertiaryFixed = Color(0xFFE8D9CF),
        tertiaryFixedDim = Color(0xFFCDB8A9),
        onTertiaryFixed = Color(0xFF241812),
        onTertiaryFixedVariant = Color(0xFF54412F),
    )

/**
 * The app's light palette. Every Material colour role except `scrim` and `surfaceTint` is set, so
 * built-in components (dialogs, text fields, menus) never fall back to Material's default purple:
 * `surfaceTint` defaults to this palette's `primary`, and `scrim`, the layer that dims the screen
 * behind a dialog, keeps Material's black. The "fixed" roles keep the same value in both themes, as
 * Material defines them. Each `on…` colour reaches 4.5:1 contrast on its matching background, and
 * `outline`, the text-field border, reaches 3:1 on the field fill, `background` and `surface` (both
 * checked by `ThemeTest`).
 */
internal val LightColorScheme =
    lightColorScheme(
        primary = Color(0xFF704222), // brown
        onPrimary = Color.White,
        primaryContainer = Color(0xFFEBD3BD),
        onPrimaryContainer = Color(0xFF3E2210),
        inversePrimary = Color(0xFFE2BC32),
        secondary = Color(0xFFD9B87A), // gold
        onSecondary = Color(0xFF33251D),
        secondaryContainer = Color(0xFFF0E0BF),
        onSecondaryContainer = Color(0xFF3D2E10),
        tertiary = Color(0xFF735948),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFE8D9CF),
        onTertiaryContainer = Color(0xFF2E2018),
        background = Color(0xFFF5E9DE),
        onBackground = Color(0xFF33251D),
        surface = Color(0xFFE4D5C8),
        onSurface = Color(0xFF33251D),
        surfaceVariant = Color(0xFFE4D5C8),
        onSurfaceVariant = Color(0xFF735948),
        inverseSurface = Color(0xFF33251D),
        inverseOnSurface = Color(0xFFF5E9DE),
        error = Color(0xFF9C2F23),
        onError = Color.White,
        errorContainer = Color(0xFFF9DAD4),
        onErrorContainer = Color(0xFF4A140D),
        outline = Color(0xFF8E6F57),
        outlineVariant = Color(0xFFD8C6B6),
        surfaceBright = Color(0xFFF7EDE4),
        surfaceDim = Color(0xFFDCCBBE),
        surfaceContainerLowest = Color(0xFFF7EDE4),
        surfaceContainerLow = Color(0xFFF2E4D8),
        surfaceContainer = Color(0xFFEDDFD2),
        surfaceContainerHigh = Color(0xFFE9DACD),
        surfaceContainerHighest = Color(0xFFE4D5C8),
        primaryFixed = Color(0xFFEBD3BD),
        primaryFixedDim = Color(0xFFD8B99E),
        onPrimaryFixed = Color(0xFF2B1607),
        onPrimaryFixedVariant = Color(0xFF5A3519),
        secondaryFixed = Color(0xFFF0E0BF),
        secondaryFixedDim = Color(0xFFD9B87A),
        onSecondaryFixed = Color(0xFF2A1F08),
        onSecondaryFixedVariant = Color(0xFF5A4519),
        tertiaryFixed = Color(0xFFE8D9CF),
        tertiaryFixedDim = Color(0xFFCDB8A9),
        onTertiaryFixed = Color(0xFF241812),
        onTertiaryFixedVariant = Color(0xFF54412F),
    )

/**
 * Wraps every screen of the app. Screens read their colours from [MaterialTheme.colorScheme]
 * ([LightColorScheme] or [DarkColorScheme]); [LocalAppUiColors] only adds what Material has no role
 * for (the dark-theme flag, the dice number colour and the campaign banner colours).
 *
 * @param darkTheme Whether to use the dark palette; follows the system setting by default.
 * @param content The screen content drawn with this theme.
 */
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
