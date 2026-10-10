package com.github.se.jdrnexus.resources

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.github.se.jdrnexus.R

/**
 * * Reusable JDRNexus logo used on the authentication screens. * The logo is drawn using Compose
 *   Canvas so that its colors can be * changed dynamically according to the current Material
 *   theme. * *
 *
 * @param modifier Controls the size and position of the logo.
 */
@Composable
fun JdrNexusLogo(
    modifier: Modifier = Modifier,
) {
  val isDarkTheme = MaterialTheme.colorScheme.background == Color(0xFF1C1816)

  val logo =
      if (isDarkTheme) {
        R.drawable.app_logo_dark
      } else {
        R.drawable.app_logo_light
      }

  Image(
      painter = painterResource(id = logo),
      contentDescription = "JDRNexus logo",
      modifier = modifier,
      contentScale = ContentScale.Fit,
  )
}

/**
 * Reusable decorative divider used on the Sign In and Sign Up screens.
 *
 * It consists of a horizontal line on each side of a small diamond. The color is provided by the
 * screen so the divider follows the current Material theme.
 *
 * @param color Color used for the lines and diamond.
 */
@Composable
fun DivideWithDiamonds(color: Color) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    // Left side of the divider.
    HorizontalDivider(
        modifier = Modifier.weight(1f),
        color = color.copy(alpha = .5f),
    )
    // Diamond displayed in the center of the divider.
    Canvas(modifier = Modifier.size(14.dp).padding(horizontal = 2.dp)) {
      val path =
          Path().apply {
            moveTo(size.width / 2, 0f)
            lineTo(size.width, size.height / 2)
            lineTo(size.width / 2, size.height)
            lineTo(0f, size.height / 2)
            close()
          }

      drawPath(
          path = path,
          color = color,
      )
    }
    // Right side of the divider.
    HorizontalDivider(
        modifier = Modifier.weight(1f),
        color = color.copy(alpha = .5f),
    )
  }
}

/**
 * Provides the custom colors used by the outlined text fields on the authentication screens.
 *
 * This keeps the styling of the Sign In and Sign Up input fields consistent and avoids duplicating
 * the same OutlinedTextFieldDefaults configuration in both screens.
 *
 * * @param container Background color of the text field.
 *
 * @param border Border color of the text field.
 * @param textPrimary Color of the entered text.
 * @param primary Color used for the cursor.
 */
@Composable
fun darkFieldColors(
    container: Color,
    border: Color,
    textPrimary: Color,
    primary: Color,
) =
    OutlinedTextFieldDefaults.colors(
        focusedContainerColor = container,
        unfocusedContainerColor = container,
        focusedBorderColor = border,
        unfocusedBorderColor = border,
        focusedTextColor = textPrimary,
        unfocusedTextColor = textPrimary,
        cursorColor = primary,
    )
