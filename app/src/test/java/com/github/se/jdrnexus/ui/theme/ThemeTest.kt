// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.math.max
import kotlin.math.min
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ThemeTest {
  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun lightThemeTextIsReadableOnEveryBackground() {
    assertEquals(emptyList<String>(), unreadablePairs(LightColorScheme))
  }

  @Test
  fun darkThemeTextIsReadableOnEveryBackground() {
    assertEquals(emptyList<String>(), unreadablePairs(DarkColorScheme))
  }

  @Test
  fun lightThemeFieldBordersStandOutFromWhatTheyTouch() {
    assertEquals(emptyList<String>(), faintFieldBorders(LightColorScheme))
  }

  @Test
  fun darkThemeFieldBordersStandOutFromWhatTheyTouch() {
    assertEquals(emptyList<String>(), faintFieldBorders(DarkColorScheme))
  }

  @Test
  fun lightThemeSetsEveryColourRoleItself() {
    assertEquals(
        emptyList<String>(),
        rolesLeftAtMaterialDefault(LightColorScheme, lightColorScheme()),
    )
  }

  @Test
  fun darkThemeSetsEveryColourRoleItself() {
    assertEquals(
        emptyList<String>(),
        rolesLeftAtMaterialDefault(DarkColorScheme, darkColorScheme()),
    )
  }

  @Test
  fun sampleAppThemeProvidesTheSchemeAndDarkFlagMatchingDarkTheme() {
    val seen = mutableMapOf<Boolean, Pair<Color, Boolean>>()

    composeTestRule.setContent {
      for (dark in listOf(false, true)) {
        SampleAppTheme(darkTheme = dark) {
          seen[dark] = MaterialTheme.colorScheme.background to LocalAppUiColors.current.isDarkTheme
        }
      }
    }
    composeTestRule.waitForIdle()

    assertEquals(LightColorScheme.background to false, seen[false])
    assertEquals(DarkColorScheme.background to true, seen[true])
  }

  /** Text/background pairs a screen draws, each needing the WCAG 4.5:1 contrast for body text. */
  private fun unreadablePairs(s: ColorScheme): List<String> {
    val pairs =
        listOf(
            Triple("onPrimary on primary", s.onPrimary, s.primary),
            Triple("onSecondary on secondary", s.onSecondary, s.secondary),
            Triple("onTertiary on tertiary", s.onTertiary, s.tertiary),
            Triple("onBackground on background", s.onBackground, s.background),
            Triple("onSurface on surface", s.onSurface, s.surface),
            Triple("onSurfaceVariant on surfaceVariant", s.onSurfaceVariant, s.surfaceVariant),
            Triple("onSurfaceVariant on background", s.onSurfaceVariant, s.background),
            Triple(
                "onSurfaceVariant on surfaceContainerHighest",
                s.onSurfaceVariant,
                s.surfaceContainerHighest,
            ),
            Triple("onSurface on surfaceContainerLowest", s.onSurface, s.surfaceContainerLowest),
            Triple("onSurface on surfaceContainerLow", s.onSurface, s.surfaceContainerLow),
            Triple("onSurface on surfaceContainer", s.onSurface, s.surfaceContainer),
            Triple("onSurface on surfaceContainerHigh", s.onSurface, s.surfaceContainerHigh),
            Triple("onSurface on surfaceContainerHighest", s.onSurface, s.surfaceContainerHighest),
            Triple(
                "onPrimaryContainer on primaryContainer",
                s.onPrimaryContainer,
                s.primaryContainer,
            ),
            Triple(
                "onSecondaryContainer on secondaryContainer",
                s.onSecondaryContainer,
                s.secondaryContainer,
            ),
            Triple(
                "onTertiaryContainer on tertiaryContainer",
                s.onTertiaryContainer,
                s.tertiaryContainer,
            ),
            Triple("onError on error", s.onError, s.error),
            Triple("onErrorContainer on errorContainer", s.onErrorContainer, s.errorContainer),
            Triple("error on background", s.error, s.background),
            Triple("error on surfaceContainerHigh", s.error, s.surfaceContainerHigh),
            Triple("inverseOnSurface on inverseSurface", s.inverseOnSurface, s.inverseSurface),
            Triple("onPrimaryFixed on primaryFixed", s.onPrimaryFixed, s.primaryFixed),
            Triple(
                "onPrimaryFixedVariant on primaryFixedDim",
                s.onPrimaryFixedVariant,
                s.primaryFixedDim,
            ),
            Triple("onSecondaryFixed on secondaryFixed", s.onSecondaryFixed, s.secondaryFixed),
            Triple(
                "onSecondaryFixedVariant on secondaryFixedDim",
                s.onSecondaryFixedVariant,
                s.secondaryFixedDim,
            ),
            Triple("onTertiaryFixed on tertiaryFixed", s.onTertiaryFixed, s.tertiaryFixed),
            Triple(
                "onTertiaryFixedVariant on tertiaryFixedDim",
                s.onTertiaryFixedVariant,
                s.tertiaryFixedDim,
            ),
        )
    return pairsBelow(4.5, pairs)
  }

  /**
   * Text-field border on each colour it touches: the field's own fill, the sign-in and sign-up page
   * (`background`) and the group workspace dialogs (`surface`). Each needs the WCAG 3:1 contrast
   * for the boundary of a control.
   */
  private fun faintFieldBorders(s: ColorScheme): List<String> =
      pairsBelow(
          3.0,
          listOf(
              Triple("outline on surfaceContainerHighest", s.outline, s.surfaceContainerHighest),
              Triple("outline on background", s.outline, s.background),
              Triple("outline on surface", s.outline, s.surface),
          ),
      )

  /** Names and contrasts of the [pairs] whose two colours differ by less than [minimum]:1. */
  private fun pairsBelow(minimum: Double, pairs: List<Triple<String, Color, Color>>): List<String> =
      pairs.mapNotNull { (name, a, b) ->
        val ratio = contrast(a, b)
        if (ratio < minimum) "$name: %.2f".format(ratio) else null
      }

  private fun contrast(a: Color, b: Color): Double {
    val la = a.luminance().toDouble()
    val lb = b.luminance().toDouble()
    return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
  }

  /**
   * Roles still equal to Material's default scheme, which is purple. White and black are excluded
   * because a role can legitimately be pure white or black in our palette too.
   */
  private fun rolesLeftAtMaterialDefault(ours: ColorScheme, default: ColorScheme): List<String> {
    val roles: List<Pair<String, (ColorScheme) -> Color>> =
        listOf(
            "primary" to { it.primary },
            "onPrimary" to { it.onPrimary },
            "primaryContainer" to { it.primaryContainer },
            "onPrimaryContainer" to { it.onPrimaryContainer },
            "inversePrimary" to { it.inversePrimary },
            "secondary" to { it.secondary },
            "onSecondary" to { it.onSecondary },
            "secondaryContainer" to { it.secondaryContainer },
            "onSecondaryContainer" to { it.onSecondaryContainer },
            "tertiary" to { it.tertiary },
            "onTertiary" to { it.onTertiary },
            "tertiaryContainer" to { it.tertiaryContainer },
            "onTertiaryContainer" to { it.onTertiaryContainer },
            "background" to { it.background },
            "onBackground" to { it.onBackground },
            "surface" to { it.surface },
            "onSurface" to { it.onSurface },
            "surfaceVariant" to { it.surfaceVariant },
            "onSurfaceVariant" to { it.onSurfaceVariant },
            "inverseSurface" to { it.inverseSurface },
            "inverseOnSurface" to { it.inverseOnSurface },
            "error" to { it.error },
            "onError" to { it.onError },
            "errorContainer" to { it.errorContainer },
            "onErrorContainer" to { it.onErrorContainer },
            "outline" to { it.outline },
            "outlineVariant" to { it.outlineVariant },
            "surfaceBright" to { it.surfaceBright },
            "surfaceDim" to { it.surfaceDim },
            "surfaceContainerLowest" to { it.surfaceContainerLowest },
            "surfaceContainerLow" to { it.surfaceContainerLow },
            "surfaceContainer" to { it.surfaceContainer },
            "surfaceContainerHigh" to { it.surfaceContainerHigh },
            "surfaceContainerHighest" to { it.surfaceContainerHighest },
            "primaryFixed" to { it.primaryFixed },
            "primaryFixedDim" to { it.primaryFixedDim },
            "onPrimaryFixed" to { it.onPrimaryFixed },
            "onPrimaryFixedVariant" to { it.onPrimaryFixedVariant },
            "secondaryFixed" to { it.secondaryFixed },
            "secondaryFixedDim" to { it.secondaryFixedDim },
            "onSecondaryFixed" to { it.onSecondaryFixed },
            "onSecondaryFixedVariant" to { it.onSecondaryFixedVariant },
            "tertiaryFixed" to { it.tertiaryFixed },
            "tertiaryFixedDim" to { it.tertiaryFixedDim },
            "onTertiaryFixed" to { it.onTertiaryFixed },
            "onTertiaryFixedVariant" to { it.onTertiaryFixedVariant },
        )
    return roles
        .filter { (_, role) ->
          role(ours) == role(default) && role(ours) != Color.White && role(ours) != Color.Black
        }
        .map { it.first }
  }
}
