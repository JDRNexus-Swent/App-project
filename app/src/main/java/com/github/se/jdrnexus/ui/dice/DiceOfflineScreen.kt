// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.ui.dice

// import androidx.compose.ui.tooling.preview.Preview
// import com.github.se.jdrnexus.ui.theme.SampleAppTheme
import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
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
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.se.jdrnexus.R
import com.github.se.jdrnexus.model.repository.AuthRepositoryFirebase
import com.github.se.jdrnexus.model.repository.DiceLogic
import com.github.se.jdrnexus.model.repository.DiceRepositoryFirestore
import com.github.se.jdrnexus.resources.C
import com.github.se.jdrnexus.resources.DivideWithDiamonds
import com.google.firebase.firestore.FirebaseFirestore
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.delay

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

/** Maps the roles used by this screen onto the app's [MaterialTheme] color scheme. */
@Composable
private fun diceThemeColors(): DiceThemeColors {
  val scheme = MaterialTheme.colorScheme
  return DiceThemeColors(
      backgroundColor = scheme.background,
      cardColor = scheme.surface,
      cardBorder = scheme.onBackground,
      panelColor = scheme.background,
      panelBorder = scheme.onBackground,
      primaryText = scheme.onSecondary,
      secondaryText = scheme.tertiary,
      accentColor = scheme.primary,
      dieText = scheme.secondary,
      selectedDiceBackground = scheme.primary.copy(alpha = 0.15f),
      rollGradient = listOf(scheme.primary, lerp(scheme.primary, scheme.secondary, 0.5f)),
      rollBorder = scheme.secondary,
      rollText = scheme.onPrimary,
      errorBackground = scheme.errorContainer,
      errorText = scheme.onErrorContainer,
  )
}

/** How long the die shows its rolling indicator before revealing the result. */
private const val ROLL_ANIMATION_MILLIS = 1000L

/**
 * Offline dice roller: lets the user pick a die, adjust the modifier and roll, without saving the
 * result anywhere. All the dice logic lives in [diceViewModel]; without a group id it keeps every
 * roll local and never touches the repository. This screen only renders its state and forwards user
 * intents.
 *
 * @param diceViewModel The ViewModel handling dice selection, modifiers and rolls.
 * @param onClose Called when the user closes the dice roller.
 */
@Composable
fun DiceOfflineScreen(
    diceViewModel: DiceViewModel =
        viewModel(
            factory =
                DiceViewModelFactory(
                    diceLogic = DiceLogic(),
                    diceRepository =
                        DiceRepositoryFirestore(
                            FirebaseFirestore.getInstance(),
                            AuthRepositoryFirebase(),
                        ),
                )
        ),
    onClose: () -> Unit = {},
) {
  val uiState by diceViewModel.uiState.collectAsState()
  DiceOfflineContent(uiState = uiState, diceViewModel = diceViewModel, onClose = onClose)
}

/**
 * Stateless body of [DiceOfflineScreen], split out so previews can render it with a fixed [uiState]
 * and no Firebase.
 */
@Composable
private fun DiceOfflineContent(
    uiState: DiceUiState,
    diceViewModel: DiceInterfaceVM,
    onClose: () -> Unit,
) {
  val colors = diceThemeColors()
  val controlsEnabled = !uiState.isRolling

  BoxWithConstraints(
      modifier =
          Modifier.fillMaxSize()
              .background(colors.backgroundColor)
              .testTag(C.Tag.dice_offline_screen),
  ) {
    val dimens = DiceDimens(screenHeight = maxHeight)
    Surface(
        modifier =
            Modifier.fillMaxSize()
                .padding(horizontal = dimens.scaled(16.dp), vertical = dimens.scaled(24.dp)),
        shape = RoundedCornerShape(24.dp),
        color = colors.cardColor,
        border = BorderStroke(1.dp, colors.cardBorder),
    ) {
      BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val contentPadding = dimens.scaled(16.dp)
        DieFillingColumn(
            viewportHeight = maxHeight - contentPadding * 2,
            modifier =
                Modifier.fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(contentPadding),
            top = {
              DiceHeader(uiState = uiState, colors = colors, onClose = onClose)
              Spacer(Modifier.height(dimens.scaled(16.dp)))
            },
            die = {
              DieImage(
                  uiState = uiState,
                  colors = colors,
                  onRollAnimationFinished = diceViewModel::onRollAnimationFinished,
              )
            },
        ) {
          DieResultText(uiState = uiState, colors = colors)

          Spacer(Modifier.height(dimens.scaled(24.dp)))
          ModifierControls(
              modifier = uiState.modifier,
              enabled = controlsEnabled,
              colors = colors,
              onChange = diceViewModel::increaseModifier,
          )

          Spacer(Modifier.height(dimens.scaled(20.dp)))
          DiceSelector(
              selected = uiState.selectedDice,
              enabled = controlsEnabled,
              colors = colors,
              dimens = dimens,
              onSelect = diceViewModel::selectDice,
          )

          uiState.errorMessage?.let { message ->
            Spacer(Modifier.height(dimens.scaled(16.dp)))
            ErrorBanner(message = message, colors = colors, onDismiss = diceViewModel::dismissError)
          }

          Spacer(Modifier.height(dimens.scaled(20.dp)))
          RollButton(
              enabled = controlsEnabled,
              colors = colors,
              dimens = dimens,
              onClick = diceViewModel::rollDice,
          )
        }
      }
    }
  }
}

/** Height of the phone the screen was designed on; its spacing is defined for this height. */
private val REFERENCE_SCREEN_HEIGHT = 917.dp

/** Smallest fraction of the designed spacing kept on short screens. */
private const val MIN_SCALE = 0.5f

/** Material's minimum size for anything the user taps. */
private val MIN_TOUCH_TARGET = 48.dp

/**
 * Spacing of the dice screen, scaled from the reference design to the [screenHeight] so short
 * phones keep every control on screen. Text stays in sp so it follows the user's font size, and
 * touch targets never shrink below [MIN_TOUCH_TARGET].
 */
private class DiceDimens(screenHeight: Dp) {
  private val scale = (screenHeight / REFERENCE_SCREEN_HEIGHT).coerceIn(MIN_SCALE, 1f)

  /** [designed] spacing as it should be on this screen. */
  fun scaled(designed: Dp): Dp = designed * scale

  /** [designed] size of a tappable element, never below [MIN_TOUCH_TARGET]. */
  fun touchTarget(designed: Dp): Dp = scaled(designed).coerceAtLeast(MIN_TOUCH_TARGET)
}

/**
 * Stacks [top], the die and [bottom] vertically, all centered horizontally. The die gets whatever
 * height the [viewportHeight] has left once [top] and [bottom] are laid out, so the controls stay
 * on screen on short phones. It never gets narrower than [MIN_DIE_HEIGHT_FRACTION] of the viewport
 * nor wider than [MAX_DIE_WIDTH_FRACTION] of the screen; if the controls still do not fit, the
 * caller's scroll takes over.
 */
@Composable
private fun DieFillingColumn(
    viewportHeight: Dp,
    modifier: Modifier = Modifier,
    top: @Composable ColumnScope.() -> Unit,
    die: @Composable () -> Unit,
    bottom: @Composable ColumnScope.() -> Unit,
) {
  Layout(
      content = {
        Column(horizontalAlignment = Alignment.CenterHorizontally, content = top)
        Box { die() }
        Column(horizontalAlignment = Alignment.CenterHorizontally, content = bottom)
      },
      modifier = modifier,
  ) { measurables, constraints ->
    val width = constraints.maxWidth
    val loose = Constraints(maxWidth = width)
    val topPlaceable = measurables[0].measure(loose)
    val bottomPlaceable = measurables[2].measure(loose)
    val viewport = viewportHeight.roundToPx()
    val maxDieSize = (width * MAX_DIE_WIDTH_FRACTION).roundToInt()
    val minDieSize = min((viewport * MIN_DIE_HEIGHT_FRACTION).roundToInt(), maxDieSize)
    val dieSize =
        (viewport - topPlaceable.height - bottomPlaceable.height).coerceIn(minDieSize, maxDieSize)
    val diePlaceable = measurables[1].measure(Constraints.fixed(dieSize, dieSize))

    val height = topPlaceable.height + dieSize + bottomPlaceable.height
    layout(width, height) {
      var y = 0
      listOf(topPlaceable, diePlaceable, bottomPlaceable).forEach {
        it.place((width - it.width) / 2, y)
        y += it.height
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

/** The die never takes more than this fraction of the content width. */
private const val MAX_DIE_WIDTH_FRACTION = 0.78f

/** The die never shrinks below this fraction of the viewport height. */
private const val MIN_DIE_HEIGHT_FRACTION = 0.15f

/**
 * Where the blank front face sits in a die image, as fractions of the image size, and how tall the
 * number engraved on it should be.
 */
private data class DieFace(
    @param:DrawableRes val image: Int,
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

/** Result of the current roll if it was made with the selected die, null otherwise. */
private fun DiceUiState.displayedResult(): DiceRollResult? = currentResult?.takeIf {
  it.diceType == selectedDice
}

/**
 * Big die image, filling the size it is given, with the raw roll engraved on its front face ("?"
 * before the first roll, or a loading indicator while rolling). [onRollAnimationFinished] is called
 * once the loading indicator has been shown for [ROLL_ANIMATION_MILLIS].
 */
@Composable
private fun DieImage(
    uiState: DiceUiState,
    colors: DiceThemeColors,
    onRollAnimationFinished: () -> Unit,
) {
  val result = uiState.displayedResult()
  val face = dieFace(uiState.selectedDice)

  BoxWithConstraints(
      modifier =
          Modifier.fillMaxSize().testTag(C.Tag.dice_offline_die).semantics {
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
      // A roll stays "rolling" until the UI says its animation is over.
      LaunchedEffect(key1 = uiState.isRolling) {
        delay(ROLL_ANIMATION_MILLIS)
        onRollAnimationFinished()
      }
    } else {
      // Sized in dp so the number always fits its face, whatever the user's font scale.

      // textheight is an empirical value found by the agent to fit the number in the die and
      // make it look nice
      val fontSize = with(LocalDensity.current) { (maxWidth * face.textHeight).toSp() }
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
}

/** The computation of the current roll including the modifier, or a hint before the first roll. */
@Composable
private fun DieResultText(uiState: DiceUiState, colors: DiceThemeColors) {
  val result = uiState.displayedResult()

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

/**
 * Below this width, the six dice buttons no longer fit on one row and wrap into two rows of three.
 */
private val MIN_ONE_ROW_TILE_WIDTH = 64.dp

/** Size of a die outline relative to its button width. */
private const val DICE_ICON_WIDTH_FRACTION = 0.4f

/** Largest die outline, so the buttons do not grow huge on tablets. */
private val MAX_DICE_ICON_SIZE = 32.dp

/**
 * One selection button per [DiceType]; the selected one is highlighted. The buttons share the
 * available width evenly, on one row when it is wide enough and on two rows of three otherwise.
 */
@Composable
private fun DiceSelector(
    selected: DiceType,
    enabled: Boolean,
    colors: DiceThemeColors,
    dimens: DiceDimens,
    onSelect: (DiceType) -> Unit,
) {
  val padding = dimens.scaled(8.dp)
  val gap = dimens.scaled(8.dp)
  BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
    val rowWidth = maxWidth - padding * 2
    val diceCount = DiceType.entries.size
    val oneRowTileWidth = (rowWidth - gap * (diceCount - 1)) / diceCount
    val perRow = if (oneRowTileWidth >= MIN_ONE_ROW_TILE_WIDTH) diceCount else diceCount / 2
    val tileWidth = (rowWidth - gap * (perRow - 1)) / perRow
    val iconSize = (tileWidth * DICE_ICON_WIDTH_FRACTION).coerceAtMost(MAX_DICE_ICON_SIZE)

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(gap, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(gap),
        maxItemsInEachRow = perRow,
        modifier =
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(colors.panelColor)
                .border(1.dp, colors.panelBorder, RoundedCornerShape(12.dp))
                .padding(padding),
    ) {
      DiceType.entries.forEach { type ->
        val isSelected = type == selected
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            // Weight rather than a computed width: the row splits its exact pixel width, so
            // rounding can never push the last die onto a new row.
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
                    .padding(vertical = dimens.scaled(10.dp))
                    .testTag(C.Tag.diceOfflineSelect(type.name)),
        ) {
          DiceShape(
              diceType = type,
              fill = Color.Transparent,
              outline = if (isSelected) colors.accentColor else colors.secondaryText,
              strokeWidth = 2f,
              modifier = Modifier.size(iconSize),
          )
          Spacer(Modifier.height(dimens.scaled(4.dp)))
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
private fun RollButton(
    enabled: Boolean,
    colors: DiceThemeColors,
    dimens: DiceDimens,
    onClick: () -> Unit,
) {
  Surface(
      onClick = onClick,
      enabled = enabled,
      shape = RoundedCornerShape(10.dp),
      color = Color.Transparent,
      border = BorderStroke(1.dp, colors.rollBorder),
      modifier =
          Modifier.fillMaxWidth()
              .height(dimens.touchTarget(58.dp))
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

// /** No-op ViewModel so the previews can render without real dice logic. */
// private object PreviewDiceViewModel : DiceInterfaceVM {
//   override fun selectDice(diceType: DiceType) = Unit
//
//   override fun setModifier(modifier: Int) = Unit
//
//   override fun increaseModifier(modifier: Int) = Unit
//
//   override fun rollDice() = Unit
//
//   override fun onRollAnimationFinished() = Unit
//
//   override fun dismissError() = Unit
// }
//
// /** Preview state for [diceType] showing a roll of [rawValue] with a +4 modifier. */
// private fun previewState(diceType: DiceType, rawValue: Int) =
//     DiceUiState(
//         selectedDice = diceType,
//         modifier = 4,
//         currentResult =
//             DiceRollResult(diceType, rawValue = rawValue, modifier = 4, totalValue = rawValue +
// 4),
//     )
//
// @Preview(name = "Dice offline · Dark · D4", widthDp = 412, heightDp = 917)
// @Composable
// private fun DiceOfflineScreenDarkPreview() {
//   SampleAppTheme(darkTheme = true) {
//     DiceOfflineContent(
//         uiState = previewState(DiceType.D4, rawValue = 3),
//         diceViewModel = PreviewDiceViewModel,
//         onClose = {},
//     )
//   }
// }
//
// @Preview(name = "Dice offline · Light · D6", widthDp = 412, heightDp = 917)
// @Composable
// private fun DiceOfflineScreenLightPreview() {
//   SampleAppTheme(darkTheme = false) {
//     DiceOfflineContent(
//         uiState = previewState(DiceType.D6, rawValue = 5),
//         diceViewModel = PreviewDiceViewModel,
//         onClose = {},
//     )
//   }
// }
//
// @Preview(name = "Dice offline · Small phone · D10", widthDp = 320, heightDp = 640)
// @Composable
// private fun DiceOfflineScreenSmallPhonePreview() {
//   SampleAppTheme(darkTheme = true) {
//     DiceOfflineContent(
//         uiState = DiceUiState(selectedDice = DiceType.D10),
//         diceViewModel = PreviewDiceViewModel,
//         onClose = {},
//     )
//   }
// }
//
// @Preview(name = "Dice offline · Rolling with error · D12", widthDp = 412, heightDp = 917)
// @Composable
// private fun DiceOfflineScreenRollingPreview() {
//   SampleAppTheme(darkTheme = false) {
//     DiceOfflineContent(
//         uiState =
//             previewState(DiceType.D12, rawValue = 9)
//                 .copy(isRolling = true, errorMessage = "Couldn't roll, please retry."),
//         diceViewModel = PreviewDiceViewModel,
//         onClose = {},
//     )
//   }
// }
