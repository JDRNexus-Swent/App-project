package com.github.se.jdrnexus.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme =
    darkColorScheme(
//        primary = Purple80, secondary = PurpleGrey80, tertiary = Pink80
        primary = Color(0xFFD4B95D),       // gold
        secondary = Color(0xFFB89B5A),
        tertiary = Color(0xFF8E8278),

        background = Color(0xFF1C1816),
        surface = Color(0xFF221A18),

        onPrimary = Color(0xFF2E2520),
        onSecondary = Color.White,
        onTertiary = Color.White,

        onBackground = Color(0xFF6F5138),
        onSurface = Color(0xFFF2EEE9)
    )

private val LightColorScheme =
    lightColorScheme(
//        primary = Purple40,
//        secondary = PurpleGrey40,
//        tertiary = Pink40,
        primary = Color(0xFF6F5138),       // brown
        secondary = Color(0xFFD4B95D),     // gold
        tertiary = Color(0xFF8E8278),

        background = Color(0xFFF2EEE9),
        surface = Color(0xFFFFFFFF),

        onPrimary = Color.White,
        onSecondary = Color(0xFF2E2520),
        onTertiary = Color.White,

        onBackground = Color(0xFFE7E0D8),
        onSurface = Color(0xFFD1C7BD)

        /* Other default colors to override
        background = Color(0xFFFFFBFE),
        surface = Color(0xFFFFFBFE),
        onPrimary = Color.White,
        onSecondary = Color.White,
        onTertiary = Color.White,
        onBackground = Color(0xFF1C1B1F),
        onSurface = Color(0xFF1C1B1F),
        */
    )

//@Composable
//fun SampleAppTheme(
//    darkTheme: Boolean = isSystemInDarkTheme(),
//    // Dynamic color is available on Android 12+
//    dynamicColor: Boolean = false,
//    content: @Composable () -> Unit,
//) {
//  val colorScheme =
//      when {
//        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
//          val context = LocalContext.current
//          if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
//        }
//        darkTheme -> DarkColorScheme
//        else -> LightColorScheme
//      }
//  val view = LocalView.current
//  if (!view.isInEditMode) {
//    SideEffect {
//      val window = (view.context as Activity).window
//      window.statusBarColor = colorScheme.primary.toArgb()
//      WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = darkTheme
//    }
//  }
//
//  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
//}
@Composable
fun SampleAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
