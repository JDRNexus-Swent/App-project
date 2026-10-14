// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.ui.dice

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.se.jdrnexus.R
import com.github.se.jdrnexus.resources.C
import com.github.se.jdrnexus.resources.DivideWithDiamonds
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

private data class DiceThemeColors(
    val backgroundColor: Color,
    val cardColor: Color,
    val cardBorder: Color,
    val panelColor: Color,
    val panelBorder: Color,
    val primaryText: Color,
    val secondaryText: Color,
    val accentColor: Color,
    val dieText: Color,
    val selectedDiceBackground: Color,
    val rollGradient: List<Color>,
    val rollBorder: Color,
    val rollText: Color,
    val errorBackground: Color,
    val errorText: Color,
)

private val darkDiceTheme =
    DiceThemeColors(
        backgroundColor = Color(0xFF0F0B09),
        cardColor = Color(0xFF1E1714),
        cardBorder = Color(0xFF5A4630),
        panelColor = Color(0xFF241C18),
        panelBorder = Color(0xFF4A3B2E),
        primaryText = Color(0xFFF2EEE9),
        secondaryText = Color(0xFFA99A8A),
        accentColor = Color(0xFFD4B95D),
        dieText = Color(0xFFD4B95D),
        selectedDiceBackground = Color(0xFF3A2E22),
        rollGradient = listOf(Color(0xFFF0D77A), Color(0xFFC9A63E)),
        rollBorder = Color(0xFFF5E3A0),
        rollText = Color(0xFF2E2520),
        errorBackground = Color(0xFF4A1F1A),
        errorText = Color(0xFFF5C9C0),
    )

private val lightDiceTheme =
    DiceThemeColors(
        backgroundColor = Color(0xFFE9DFD6),
        cardColor = Color(0xFFF2E6DA),
        cardBorder = Color(0xFFDCCBBB),
        panelColor = Color(0xFFEFE2D6),
        panelBorder = Color(0xFFD6C4B3),
        primaryText = Color(0xFF2E2520),
        secondaryText = Color(0xFF8E7A68),
        accentColor = Color(0xFF6F5138),
        dieText = Color(0xFFD4B95D),
        selectedDiceBackground = Color(0xFFE2D0BE),
        rollGradient = listOf(Color(0xFF5A3E26), Color(0xFFA77A3A)),
        rollBorder = Color(0xFFD4B95D),
        rollText = Color(0xFFF7EDE4),
        errorBackground = Color(0xFFF6D9D3),
        errorText = Color(0xFF7A2318),
    )

/**
 * Offline dice roller: lets the user pick a die, adjust the modifier and roll, without saving the
 * result anywhere. All the dice logic lives in [diceViewModel]; this screen only renders [uiState]
 * and forwards user intents.
 *
 * @param uiState The current dice state exposed by the ViewModel.
 * @param diceViewModel The ViewModel handling dice selection, modifiers and rolls.
 * @param onClose Called when the user closes the dice roller.
 * @param darkTheme Whether to use the dark palette; follows the system setting by default.
 */
@Composable
fun DiceOfflineScreen(
    uiState: DiceUiState,
    diceViewModel: DiceInterfaceVM,
    onClose: () -> Unit = {},
    darkTheme: Boolean = isSystemInDarkTheme(),
) {
  val colors = if (darkTheme) darkDiceTheme else lightDiceTheme
  val controlsEnabled = !uiState.isRolling

  Box(
      modifier =
          Modifier.fillMaxSize()
              .background(colors.backgroundColor)
              .padding(horizontal = 16.dp, vertical = 24.dp)
              .testTag(C.Tag.dice_offline_screen),
  ) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(24.dp),
        color = colors.cardColor,
        border = BorderStroke(1.dp, colors.cardBorder),
    ) {
      Column(
          modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        DiceHeader(uiState = uiState, colors = colors, onClose = onClose)

        Spacer(Modifier.height(16.dp))
        DieDisplay(uiState = uiState, colors = colors)

        Spacer(Modifier.height(24.dp))
        ModifierControls(
            modifier = uiState.modifier,
            enabled = controlsEnabled,
            colors = colors,
            onChange = diceViewModel::increaseModifier,
        )

        Spacer(Modifier.height(20.dp))
        DiceSelector(
            selected = uiState.selectedDice,
            enabled = controlsEnabled,
            colors = colors,
            onSelect = diceViewModel::selectDice,
        )

        uiState.errorMessage?.let { message ->
          Spacer(Modifier.height(16.dp))
          ErrorBanner(message = message, colors = colors, onDismiss = diceViewModel::dismissError)
        }

        Spacer(Modifier.height(20.dp))
        RollButton(enabled = controlsEnabled, colors = colors, onClick = diceViewModel::rollDice)
      }
    }
  }
}

/** Title, decorative divider, close button and the selected die with its valid range. */
@Composable
private fun DiceHeader(uiState: DiceUiState, colors: DiceThemeColors, onClose: () -> Unit) {
  Box(modifier = Modifier.fillMaxWidth()) {
    Box(modifier = Modifier.width(120.dp).align(Alignment.TopCenter).padding(top = 12.dp)) {
      DivideWithDiamonds(color = colors.accentColor)
    }
    IconButton(
        onClick = onClose,
        modifier = Modifier.align(Alignment.TopEnd).testTag(C.Tag.dice_offline_close),
    ) {
      Icon(Icons.Filled.Close, contentDescription = "Close", tint = colors.secondaryText)
    }
  }

  Text(
      text = "Dice Roller",
      color = colors.primaryText,
      fontFamily = FontFamily.Serif,
      fontWeight = FontWeight.SemiBold,
      fontSize = 30.sp,
  )

  Spacer(Modifier.height(4.dp))
  Text(
      text = uiState.selectedDice.name,
      color = colors.accentColor,
      fontFamily = FontFamily.Serif,
      fontSize = 18.sp,
      modifier = Modifier.testTag(C.Tag.dice_offline_selected_type),
  )

  Spacer(Modifier.height(8.dp))
  val min = 1 + uiState.modifier
  val max = uiState.selectedDice.maxValue + uiState.modifier
  Surface(
      shape = RoundedCornerShape(6.dp),
      color = colors.panelColor,
      border = BorderStroke(1.dp, colors.panelBorder),
  ) {
    Text(
        text = "Range: $min – $max",
        color = colors.primaryText,
        fontSize = 12.sp,
        modifier =
            Modifier.padding(horizontal = 10.dp, vertical = 4.dp).testTag(C.Tag.dice_offline_range),
    )
  }
}

private val DIE_SIZE = 270.dp

/**
 * Where the blank front face sits in a die image, as fractions of the image size, and how tall the
 * number engraved on it should be.
 */
private data class DieFace(
    @DrawableRes val image: Int,
    val centerX: Float,
    val centerY: Float,
    val textHeight: Float,
)

private fun dieFace(diceType: DiceType): DieFace =
    when (diceType) {
      DiceType.D4 -> DieFace(R.drawable.dice_d4, 0.50f, 0.63f, 0.22f)
      DiceType.D6 -> DieFace(R.drawable.dice_d6, 0.45f, 0.55f, 0.30f)
      DiceType.D8 -> DieFace(R.drawable.dice_d8, 0.50f, 0.43f, 0.20f)
      DiceType.D10 -> DieFace(R.drawable.dice_d10, 0.50f, 0.55f, 0.20f)
      DiceType.D12 -> DieFace(R.drawable.dice_d12, 0.48f, 0.48f, 0.22f)
      DiceType.D20 -> DieFace(R.drawable.dice_d20, 0.50f, 0.50f, 0.18f)
    }

/**
 * Big die image with the raw roll engraved on its front face ("?" before the first roll, or a
 * loading indicator while rolling), followed by the computation including the modifier.
 */
@Composable
private fun DieDisplay(uiState: DiceUiState, colors: DiceThemeColors) {
  val result = uiState.currentResult?.takeIf { it.diceType == uiState.selectedDice }
  val face = dieFace(uiState.selectedDice)

  Box(
      modifier =
          Modifier.size(DIE_SIZE).testTag(C.Tag.dice_offline_die).semantics {
            contentDescription = "${uiState.selectedDice.name} die"
          },
  ) {
    Image(
        painter = painterResource(face.image),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = Modifier.fillMaxSize(),
    )
    if (uiState.isRolling) {
      CircularProgressIndicator(
          color = colors.dieText,
          modifier =
              Modifier.centeredAt(face.centerX, face.centerY)
                  .size(40.dp)
                  .testTag(C.Tag.dice_offline_loading),
      )
    } else {
      // Sized in dp so the number always fits its face, whatever the user's font scale.

      // textheight is an empirical value found by the agent to fit the number in the die and
      // make it look nice
      val fontSize = with(LocalDensity.current) { (DIE_SIZE * face.textHeight).toSp() }
      Text(
          text = result?.rawValue?.toString() ?: "?",
          style =
              TextStyle(
                  color = colors.dieText,
                  fontFamily = FontFamily.Serif,
                  fontWeight = FontWeight.Bold,
                  fontSize = fontSize,
                  shadow = Shadow(Color.Black.copy(alpha = 0.7f), Offset(0f, 3f), 4f),
              ),
          modifier =
              Modifier.centeredAt(face.centerX, face.centerY).testTag(C.Tag.dice_offline_result),
      )
    }
  }

  Spacer(Modifier.height(8.dp))
  Text(
      text =
          if (result == null) {
            AnnotatedString("Tap Roll to throw your ${uiState.selectedDice.name}")
          } else {
            buildAnnotatedString {
              append("Total: ")
              if (result.modifier != 0) {
                val sign = if (result.modifier > 0) "+" else "−"
                append("${result.rawValue} $sign ${abs(result.modifier)} = ")
              }
              withStyle(SpanStyle(color = colors.accentColor, fontWeight = FontWeight.Bold)) {
                append(result.totalValue.toString())
              }
            }
          },
      color = colors.secondaryText,
      fontSize = 15.sp,
      modifier = Modifier.testTag(C.Tag.dice_offline_result_detail),
  )
}

/**
 * Places the element so that its center lands at ([fractionX], [fractionY]) of the parent's
 * available space.
 */
private fun Modifier.centeredAt(fractionX: Float, fractionY: Float): Modifier =
    layout { measurable, constraints ->
      val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
      layout(constraints.maxWidth, constraints.maxHeight) {
        placeable.place(
            (constraints.maxWidth * fractionX - placeable.width / 2f).roundToInt(),
            (constraints.maxHeight * fractionY - placeable.height / 2f).roundToInt(),
        )
      }
    }

/** Minus/plus buttons around the current modifier (a bonus added to every roll). */
@Composable
private fun ModifierControls(
    modifier: Int,
    enabled: Boolean,
    colors: DiceThemeColors,
    onChange: (Int) -> Unit,
) {
  Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(40.dp),
  ) {
    SquareIconButton(
        onClick = { onChange(-1) },
        enabled = enabled,
        colors = colors,
        tag = C.Tag.dice_offline_modifier_decrease,
    ) {
      Icon(Icons.Filled.Remove, contentDescription = "Decrease modifier", tint = colors.primaryText)
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      Text(
          text = formatModifier(modifier),
          color = colors.primaryText,
          fontWeight = FontWeight.Bold,
          fontSize = 22.sp,
          modifier = Modifier.testTag(C.Tag.dice_offline_modifier_value),
      )
      Text(text = "Modifier", color = colors.secondaryText, fontSize = 12.sp)
    }
    SquareIconButton(
        onClick = { onChange(1) },
        enabled = enabled,
        colors = colors,
        tag = C.Tag.dice_offline_modifier_increase,
    ) {
      Icon(Icons.Filled.Add, contentDescription = "Increase modifier", tint = colors.primaryText)
    }
  }
}

@Composable
private fun SquareIconButton(
    onClick: () -> Unit,
    enabled: Boolean,
    colors: DiceThemeColors,
    tag: String,
    content: @Composable () -> Unit,
) {
  Surface(
      onClick = onClick,
      enabled = enabled,
      shape = RoundedCornerShape(10.dp),
      color = colors.panelColor,
      border = BorderStroke(1.dp, colors.panelBorder),
      modifier = Modifier.size(44.dp).alpha(if (enabled) 1f else 0.5f).testTag(tag),
  ) {
    Box(contentAlignment = Alignment.Center) { content() }
  }
}

/** One selection button per [DiceType]; the selected one is highlighted. */
@Composable
private fun DiceSelector(
    selected: DiceType,
    enabled: Boolean,
    colors: DiceThemeColors,
    onSelect: (DiceType) -> Unit,
) {
  Row(
      modifier =
          Modifier.fillMaxWidth()
              .clip(RoundedCornerShape(12.dp))
              .background(colors.panelColor)
              .border(1.dp, colors.panelBorder, RoundedCornerShape(12.dp))
              .padding(6.dp),
  ) {
    DiceType.entries.forEach { type ->
      val isSelected = type == selected
      Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          modifier =
              Modifier.weight(1f)
                  .clip(RoundedCornerShape(8.dp))
                  .then(
                      if (isSelected)
                          Modifier.background(colors.selectedDiceBackground)
                              .border(1.dp, colors.accentColor, RoundedCornerShape(8.dp))
                      else Modifier
                  )
                  .clickable(enabled = enabled, role = Role.Tab) { onSelect(type) }
                  .semantics { this.selected = isSelected }
                  .padding(vertical = 8.dp)
                  .testTag(C.Tag.diceOfflineSelect(type.name)),
      ) {
        DiceShape(
            diceType = type,
            fill = Color.Transparent,
            outline = if (isSelected) colors.accentColor else colors.secondaryText,
            strokeWidth = 2f,
            modifier = Modifier.size(26.dp),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = type.name,
            color = if (isSelected) colors.accentColor else colors.primaryText,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            fontSize = 12.sp,
        )
      }
    }
  }
}

@Composable
private fun ErrorBanner(message: String, colors: DiceThemeColors, onDismiss: () -> Unit) {
  Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier =
          Modifier.fillMaxWidth()
              .clip(RoundedCornerShape(10.dp))
              .background(colors.errorBackground)
              .padding(start = 12.dp)
              .testTag(C.Tag.dice_offline_error),
  ) {
    Text(text = message, color = colors.errorText, fontSize = 13.sp, modifier = Modifier.weight(1f))
    TextButton(onClick = onDismiss, modifier = Modifier.testTag(C.Tag.dice_offline_error_dismiss)) {
      Text("Dismiss", color = colors.errorText, fontWeight = FontWeight.Bold)
    }
  }
}

@Composable
private fun RollButton(enabled: Boolean, colors: DiceThemeColors, onClick: () -> Unit) {
  Surface(
      onClick = onClick,
      enabled = enabled,
      shape = RoundedCornerShape(10.dp),
      color = Color.Transparent,
      border = BorderStroke(1.dp, colors.rollBorder),
      modifier =
          Modifier.fillMaxWidth()
              .height(58.dp)
              .testTag(C.Tag.dice_offline_roll)
              .alpha(if (enabled) 1f else 0.6f),
  ) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(colors.rollGradient)),
    ) {
      Text(
          text = if (enabled) "ROLL" else "ROLLING…",
          color = colors.rollText,
          fontFamily = FontFamily.Serif,
          fontWeight = FontWeight.SemiBold,
          fontSize = 20.sp,
          letterSpacing = 1.sp,
      )
    }
  }
}

/** Draws a flat silhouette of [diceType], used both for the big die and the selection icons. */
@Composable
private fun DiceShape(
    diceType: DiceType,
    fill: Color,
    outline: Color,
    strokeWidth: Float,
    modifier: Modifier = Modifier,
) {
  Canvas(modifier = modifier) {
    val path = dicePath(diceType, size)
    drawPath(path = path, color = fill)
    drawPath(path = path, color = outline, style = Stroke(width = strokeWidth * density))
  }
}

private fun dicePath(diceType: DiceType, size: Size): Path {
  val center = Offset(size.width / 2, size.height / 2)
  val radius = min(size.width, size.height) / 2 * 0.95f
  return when (diceType) {
    DiceType.D4 -> regularPolygon(center, radius, sides = 3, startDegrees = -90.0)
    DiceType.D6 -> regularPolygon(center, radius, sides = 4, startDegrees = -45.0)
    DiceType.D8 -> regularPolygon(center, radius, sides = 4, startDegrees = -90.0)
    DiceType.D10 ->
        Path().apply {
          moveTo(center.x, center.y - radius)
          lineTo(center.x + radius * 0.85f, center.y + radius * 0.1f)
          lineTo(center.x, center.y + radius)
          lineTo(center.x - radius * 0.85f, center.y + radius * 0.1f)
          close()
        }
    DiceType.D12 -> regularPolygon(center, radius, sides = 5, startDegrees = -90.0)
    DiceType.D20 -> regularPolygon(center, radius, sides = 6, startDegrees = -90.0)
  }
}

private fun regularPolygon(center: Offset, radius: Float, sides: Int, startDegrees: Double): Path =
    Path().apply {
      for (i in 0 until sides) {
        val angle = (startDegrees + 360.0 * i / sides) * PI / 180.0
        val x = center.x + radius * cos(angle).toFloat()
        val y = center.y + radius * sin(angle).toFloat()
        if (i == 0) moveTo(x, y) else lineTo(x, y)
      }
      close()
    }

/** Formats a modifier with an explicit sign, e.g. "+4", "-2" or "+0". */
internal fun formatModifier(modifier: Int): String =
    if (modifier >= 0) "+$modifier" else modifier.toString()
