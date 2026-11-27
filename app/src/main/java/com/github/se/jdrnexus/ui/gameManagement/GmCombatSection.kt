// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
// Co-authored-by: Codex <codex@openai.com>
package com.github.se.jdrnexus.ui.gameManagement

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.github.se.jdrnexus.R
import com.github.se.jdrnexus.model.filerepresentation.CharacterAction
import com.github.se.jdrnexus.model.filerepresentation.CharacterFile
import com.github.se.jdrnexus.model.filerepresentation.CharacterHealthPoints
import com.github.se.jdrnexus.model.filerepresentation.CharacterStat
import com.github.se.jdrnexus.resources.C

@Composable
internal fun GmCombatSection(
    currentRound: Int,
    isCombatActive: Boolean,
    enemies: List<CombatNPC>,
    onDismiss: () -> Unit,
    onStartCombat: () -> Unit,
    onNextRound: () -> Unit,
    onEndCombat: () -> Unit,
    onOpenAddEnemy: () -> Unit,
    onUpdateEnemyHp: (String, Int) -> Unit,
    onRemoveEnemy: (String) -> Unit,
) {
  SectionCard(
      GmSection.COMBAT,
      onDismiss = onDismiss,
      titleTrailing = stringResource(R.string.game_gm_round, currentRound),
  ) {
    Text(
        stringResource(
            if (isCombatActive) R.string.game_gm_combat_active else R.string.game_gm_combat_inactive
        ),
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      if (!isCombatActive) {
        Button(
            onClick = onStartCombat,
            modifier = Modifier.testTag(C.Tag.game_gm_start_combat),
        ) {
          Icon(Icons.Filled.PlayArrow, contentDescription = null)
          Spacer(Modifier.width(4.dp))
          Text(
              stringResource(R.string.game_gm_start_combat),
              color = MaterialTheme.colorScheme.background,
          )
        }
      } else {
        CombatIconButton(Icons.Filled.SkipNext, C.Tag.game_gm_next_round, onNextRound)
        CombatIconButton(Icons.Filled.Stop, C.Tag.game_gm_end_combat, onEndCombat)
      }
      CombatIconButton(Icons.Filled.Add, C.Tag.game_gm_add_enemy, onOpenAddEnemy)
    }
    if (enemies.isEmpty()) {
      Text(
          stringResource(R.string.game_gm_empty_combat),
          modifier = Modifier.testTag(C.Tag.game_gm_empty_combat),
          color = MaterialTheme.colorScheme.tertiary,
      )
    } else {
      enemies.forEach { enemy ->
        EnemyRow(
            enemy = enemy,
            onIncrease = { onUpdateEnemyHp(enemy.id, 1) },
            onDecrease = { onUpdateEnemyHp(enemy.id, -1) },
            onRemove = { onRemoveEnemy(enemy.id) },
        )
      }
    }
  }
}

/** Round outlined encounter control showing only [icon]. */
@Composable
private fun CombatIconButton(icon: ImageVector, testTag: String, onClick: () -> Unit) {
  OutlinedButton(
      onClick = onClick,
      modifier = Modifier.testTag(testTag),
      shape = CircleShape,
      contentPadding = PaddingValues(horizontal = 14.dp),
  ) {
    Icon(icon, contentDescription = null)
    Spacer(Modifier.width(4.dp))
  }
}

/**
 * Displays one enemy's combat statistics and controls for changing or removing it.
 *
 * @param enemy Enemy whose current combat values are shown.
 * @param onIncrease Called when the enemy should gain one hit point.
 * @param onDecrease Called when the enemy should lose one hit point.
 * @param onRemove Called when the enemy is removed from the encounter.
 */
@Composable
private fun EnemyRow(
    enemy: CombatNPC,
    onIncrease: () -> Unit,
    onDecrease: () -> Unit,
    onRemove: () -> Unit,
) {
  val hpRatio = (enemy.hp.toFloat() / enemy.maxHp.coerceAtLeast(1)).coerceIn(0f, 1f)
  val armorClass =
      enemy.character.stats.firstOrNull { it.statID == "armorClass" }?.value?.toInt() ?: 0
  val condition =
      enemy.character.actions.firstOrNull { it.actionID.startsWith("condition_") }?.name
          ?: stringResource(R.string.game_gm_condition_normal)
  val hpColor =
      when {
        hpRatio >= 0.65f -> MaterialTheme.colorScheme.onSecondary
        hpRatio >= 0.35f -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.error
      }

  Column(
      modifier = Modifier.fillMaxWidth().testTag(C.Tag.gameGmEnemy(enemy.id)),
      verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = enemy.name,
            color = MaterialTheme.colorScheme.onSecondary,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = stringResource(R.string.game_gm_condition_ac, condition, armorClass),
            color = MaterialTheme.colorScheme.tertiary,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
      }
      Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(5.dp),
      ) {
        HpControlButton("−", enemy.hp > 0, onDecrease, C.Tag.gameGmEnemyDecrease(enemy.id))
        Box(
            modifier =
                Modifier.width(42.dp)
                    .border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                    .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
          Text(
              text = enemy.hp.toString(),
              color = MaterialTheme.colorScheme.onSecondary,
              fontWeight = FontWeight.SemiBold,
          )
        }
        Text(
            text = stringResource(R.string.game_gm_max_hp, enemy.maxHp),
            color = MaterialTheme.colorScheme.tertiary,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
        )
        HpControlButton(
            "+",
            enemy.hp < enemy.maxHp,
            onIncrease,
            C.Tag.gameGmEnemyIncrease(enemy.id),
        )
        IconButton(
            onClick = onRemove,
            modifier = Modifier.testTag(C.Tag.gameGmEnemyRemove(enemy.id)),
        ) {
          Icon(
              imageVector = Icons.Filled.Delete,
              contentDescription = stringResource(R.string.game_gm_remove_enemy_cd, enemy.name),
              tint = MaterialTheme.colorScheme.error,
          )
        }
      }
    }
    LinearProgressIndicator(
        progress = { hpRatio },
        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)),
        color = hpColor,
        trackColor = MaterialTheme.colorScheme.background,
    )
  }
}

/**
 * Renders a compact enabled or disabled control for changing an enemy's hit points.
 *
 * @param symbol Displayed control symbol, such as "+" or "−".
 * @param enabled Whether the control accepts clicks.
 * @param onClick Called when the enabled control is clicked.
 * @param testTag Test tag of the control.
 */
@Composable
private fun HpControlButton(
    symbol: String,
    enabled: Boolean,
    onClick: () -> Unit,
    testTag: String,
) {
  Box(
      modifier =
          Modifier.testTag(testTag)
              .size(42.dp)
              .clip(RoundedCornerShape(8.dp))
              .clickable(enabled = enabled, onClick = onClick),
      contentAlignment = Alignment.Center,
  ) {
    Text(
        text = symbol,
        color =
            if (enabled) MaterialTheme.colorScheme.onSecondary
            else MaterialTheme.colorScheme.tertiary,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
    )
  }
}

/**
 * Collects enemy details and submits a valid [CombatNPC] to the caller.
 *
 * @param onDismiss Closes the dialog without adding an enemy.
 * @param onAdd Receives the constructed enemy after validation.
 * @param renderAsWindow Whether the content opens in a dialog window or renders inline.
 */
@Composable
internal fun AddEnemyDialog(
    onDismiss: () -> Unit,
    onAdd: (CombatNPC) -> Unit,
    renderAsWindow: Boolean,
) {
  var name by rememberSaveable { mutableStateOf("") }
  var hp by rememberSaveable { mutableStateOf("10") }
  var maxHp by rememberSaveable { mutableStateOf("10") }
  var armorClass by rememberSaveable { mutableStateOf("10") }
  var condition by rememberSaveable { mutableStateOf("") }
  val isValid =
      isValidEnemyInput(name, hp.toIntOrNull(), maxHp.toIntOrNull(), armorClass.toIntOrNull())
  val content: @Composable () -> Unit = {
    Card(
        modifier = Modifier.testTag(C.Tag.game_gm_add_enemy_dialog),
        shape = RoundedCornerShape(20.dp),
    ) {
      Column(
          modifier = Modifier.padding(20.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Text(
            stringResource(R.string.game_gm_add_enemy_title),
            style = MaterialTheme.typography.titleLarge,
        )
        EnemyField(R.string.game_gm_enemy_name, name, C.Tag.game_gm_enemy_name) { name = it }
        EnemyField(R.string.game_gm_enemy_hp, hp, C.Tag.game_gm_enemy_hp, numeric = true) {
          hp = it
        }
        EnemyField(
            R.string.game_gm_enemy_max_hp,
            maxHp,
            C.Tag.game_gm_enemy_max_hp,
            numeric = true,
        ) {
          maxHp = it
        }
        EnemyField(R.string.game_gm_enemy_ac, armorClass, C.Tag.game_gm_enemy_ac, numeric = true) {
          armorClass = it
        }
        EnemyField(R.string.game_gm_enemy_condition, condition, C.Tag.game_gm_enemy_condition) {
          condition = it
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
          TextButton(onClick = onDismiss) { Text(stringResource(R.string.game_gm_cancel)) }
          // isValid guarantees the three numbers parse, so toInt() cannot throw here.
          Button(
              onClick = {
                if (isValid)
                    onAdd(newEnemy(name, hp.toInt(), maxHp.toInt(), armorClass.toInt(), condition))
              },
              enabled = isValid,
          ) {
            Text(stringResource(R.string.game_gm_add))
          }
        }
      }
    }
  }
  // Text fields inside a Dialog window opened after the first frame never let Robolectric go idle.
  if (renderAsWindow) Dialog(onDismissRequest = onDismiss) { content() } else content()
}

/** Builds the enemy for validated dialog input; a blank [condition] adds no condition action. */
private fun newEnemy(
    name: String,
    hp: Int,
    maxHp: Int,
    armorClass: Int,
    condition: String,
): CombatNPC {
  val trimmed = condition.trim()
  return CombatNPC(
      id = "enemy-${System.currentTimeMillis()}",
      character =
          CharacterFile(
              name = name.trim(),
              stats = listOf(CharacterStat("armorClass", "Armor Class", armorClass.toDouble())),
              actions =
                  if (trimmed.isEmpty()) emptyList()
                  else listOf(CharacterAction("condition_${trimmed.lowercase()}", trimmed, "")),
              hp = CharacterHealthPoints(hp, maxHp),
          ),
  )
}

/**
 * Checks that an enemy name and combat numbers satisfy the encounter constraints.
 *
 * @return `true` when the values can safely create a [CombatNPC].
 */
internal fun isValidEnemyInput(
    name: String,
    currentHp: Int?,
    maximumHp: Int?,
    armorClass: Int?,
): Boolean =
    name.isNotBlank() &&
        currentHp != null &&
        currentHp >= 0 &&
        maximumHp != null &&
        maximumHp > 0 &&
        currentHp <= maximumHp &&
        armorClass != null &&
        armorClass >= 0

/** Single-line field of the enemy dialog; [numeric] fields open the number keyboard. */
@Composable
private fun EnemyField(
    @StringRes label: Int,
    value: String,
    testTag: String,
    numeric: Boolean = false,
    onValueChange: (String) -> Unit,
) {
  OutlinedTextField(
      value = value,
      onValueChange = onValueChange,
      label = { Text(stringResource(label)) },
      singleLine = true,
      keyboardOptions =
          if (numeric) KeyboardOptions(keyboardType = KeyboardType.Number)
          else KeyboardOptions.Default,
      modifier = Modifier.fillMaxWidth().testTag(testTag),
  )
}
