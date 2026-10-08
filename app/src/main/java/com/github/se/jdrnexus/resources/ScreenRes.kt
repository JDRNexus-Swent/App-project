package com.github.se.jdrnexus.resources

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * * Reusable JDRNexus logo used on the authentication screens. * The logo is drawn using Compose
 *   Canvas so that its colors can be * changed dynamically according to the current Material
 *   theme. * *
 *
 * @param modifier Controls the size and position of the logo. *
 * @param color Main color used to draw the logo. *
 * @param backGroundColor Color used for the inside of the logo circle.
 */
@Composable
fun JdrNexusLogo(
    modifier: Modifier = Modifier,
    color: Color,
    backGroundColor: Color,
) {
  Canvas(modifier = modifier) {
    val center = center

    val radius = size.minDimension * 0.35f
    //      fun s(value : Float): Float = value*unit
    //      val radius = s(35f)

    // Outer circle around the logo
    drawCircle(
        color = color.copy(alpha = 0.2f),
        radius = radius + (25f),
        center = center,
        style = Stroke(width = (3f)),
    )

    // Fill the inner circle with the screen background color.
    drawCircle(
        color = backGroundColor, // grey fill
        radius = radius,
        center = center,
    )

    // Inner circle outline.
    drawCircle(
        color = color.copy(alpha = 0.35f),
        radius = radius - (2f),
        center = center,
        style = Stroke(width = (3f)),
    )

    // Draws one diamond around the main logo circle.
    // This helper is reused for the four diamonds around the logo.
    fun drawDiamond(x: Float, y: Float, size: Float = 10f) {
      val path =
          Path().apply {
            moveTo(x, y - size)
            lineTo(x + size, y)
            lineTo(x, y + size)
            lineTo(x - size, y)
            close()
          }

      drawPath(
          path = path,
          color = color,
          style = Stroke(width = 3f),
      )
    }

    // Four decorative diamonds surrounding the logo.
    drawDiamond(center.x, center.y - radius - (40f))
    drawDiamond(center.x, center.y + radius + (40f))
    drawDiamond(center.x - radius - (40f), center.y)
    drawDiamond(center.x + radius + (40f), center.y)

    // D20 dice shape in the center of the logo.
    val d20Radius = (radius * 0.35f)

    val top = Offset(center.x, center.y - (radius * 0.37f))
    val left = Offset(center.x - d20Radius, center.y - (radius * 0.10f))
    val right = Offset(center.x + d20Radius, center.y - (radius * 0.10f))
    val bottom = Offset(center.x, center.y + (radius * 0.35f))

    // lines for the fox shape in the logo
    drawLine(color, top, left, 4f)
    drawLine(color, top, right, 4f)
    drawLine(color, left, bottom, 4f)
    drawLine(color, right, bottom, 4f)
    drawLine(color, left, right, 4f)

    drawLine(
        color,
        Offset(center.x - (radius * 0.25f), center.y - (radius * 0.37f)),
        Offset(center.x + (radius * 0.25f), center.y - (radius * 0.37f)),
        3f,
    )

    drawLine(
        color,
        Offset(center.x - radius * 0.25f, center.y - radius * 0.37f),
        Offset(center.x - radius * 0.50f, center.y - radius * 0.2f),
        3f,
    )
    drawLine(
        color,
        Offset(center.x + radius * 0.25f, center.y - radius * 0.37f),
        Offset(center.x + radius * 0.50f, center.y - radius * 0.2f),
        3f,
    )

    drawLine(
        color,
        center,
        bottom,
        3f,
    )

    drawLine(
        color,
        Offset(center.x - radius * 0.5f, center.y - radius * 0.2f),
        center,
        3f,
    )

    drawLine(
        color,
        Offset(center.x + radius * 0.5f, center.y - radius * 0.2f),
        center,
        3f,
    )
    drawLine(
        color,
        Offset(center.x + radius * 0.5f, center.y - radius * 0.75f),
        center,
        3f,
    )
    drawLine(
        color,
        Offset(center.x - radius * 0.5f, center.y - radius * 0.75f),
        center,
        3f,
    )
    drawLine(
        color,
        Offset(center.x - radius * 0.5f, center.y - radius * 0.75f),
        Offset(center.x - radius * 0.5f, center.y - radius * 0.2f),
        3f,
    )
    drawLine(
        color,
        Offset(center.x + radius * 0.5f, center.y - radius * 0.75f),
        Offset(center.x + radius * 0.5f, center.y - radius * 0.2f),
        3f,
    )
    drawLine(
        color,
        Offset(center.x + radius * 0.5f, center.y - radius * 0.2f),
        bottom,
        3f,
    )
    drawLine(
        color,
        Offset(center.x - radius * 0.5f, center.y - radius * 0.2f),
        bottom,
        3f,
    )

    // Book under the fox shape
    val bookY = center.y + radius * 0.5f

    val leftPage =
        Path().apply {
          moveTo(center.x - radius * 0.38f, bookY)
          quadraticTo(
              center.x - radius * 0.2f,
              bookY - radius * 0.10f,
              center.x,
              bookY + radius * 0.10f,
          )
          lineTo(center.x, bookY + radius * 0.35f)
        }

    val rightPage =
        Path().apply {
          moveTo(center.x + radius * 0.38f, bookY)
          quadraticTo(
              center.x + radius * 0.2f,
              bookY - radius * 0.10f,
              center.x,
              bookY + radius * 0.10f,
          )
          lineTo(center.x, bookY + radius * 0.35f)
        }

    // Draw both pages of the book.
    drawPath(
        leftPage,
        color,
        style = Stroke(width = 4f),
    )

    drawPath(
        rightPage,
        color,
        style = Stroke(width = 4f),
    )

    // Center line separating the two pages.
    drawLine(
        color,
        Offset(center.x, bookY + radius * 0.10f),
        Offset(center.x, bookY + radius * 0.35f),
        4f,
    )

    // Decorative lines representing text on the book pages.
    repeat(3) { i ->
      val y = bookY + radius * 0.10f + i * radius * 0.08f

      drawLine(
          color,
          Offset(center.x - radius * 0.35f, y),
          Offset(center.x - radius * 0.15f, y + radius * 0.04f),
          2f,
      )

      drawLine(
          color,
          Offset(center.x + radius * 0.35f, y),
          Offset(center.x + radius * 0.15f, y + radius * 0.04f),
          2f,
      )
    }
  }
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
