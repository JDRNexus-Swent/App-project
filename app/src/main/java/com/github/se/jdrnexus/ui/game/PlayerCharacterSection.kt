// Co-authored-by: Codex <noreply@openai.com>
package com.github.se.jdrnexus.ui.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.github.se.jdrnexus.R
import com.github.se.jdrnexus.model.filerepresentation.CharacterFile
import com.github.se.jdrnexus.resources.C

// Placeholder until roll modifiers can be derived from the character's stats.
private const val PLACEHOLDER_DICE_MODIFIER = 0

/**
 * Adds the player's character sheet to this lazy list, including HP controls, stat rolls, action
 * descriptions, and save feedback.
 *
 * @param sheet Character data to display.
 * @param uiState Session state providing HP validation, save progress, and error messages.
 * @param saveSucceeded Whether to show the save confirmation.
 * @param onHpChanged Called with the proposed current HP after a whole number is entered or a
 *   damage or heal button is pressed.
 * @param onSave Called when the player presses the save button.
 * @param onOpenDice Called with a placeholder modifier of zero when the player rolls a stat.
 */
internal fun LazyListScope.playerCharacterSection(
    sheet: CharacterFile,
    uiState: GamePlayerUiState,
    saveSucceeded: Boolean,
    onHpChanged: (Int) -> Unit,
    onSave: () -> Unit,
    onOpenDice: (Int) -> Unit,
) {
  item {
    Text(
        sheet.name,
        style = MaterialTheme.typography.headlineMedium,
        modifier = Modifier.testTag(C.Tag.game_player_character_name),
    )
  }
  item {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      NumericField(
          value = sheet.hp.current.toString(),
          error = uiState.hpError,
          modifier = Modifier.fillMaxWidth().testTag(C.Tag.game_player_current_hp),
          onValidValue = onHpChanged,
      )
      Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        OutlinedButton(
            onClick = { onHpChanged(sheet.hp.current - 1) },
            enabled = sheet.hp.current > 0,
            modifier = Modifier.weight(1f).testTag(C.Tag.game_player_hp_decrease),
        ) {
          Text(stringResource(R.string.game_player_take_damage))
        }
        OutlinedButton(
            onClick = { onHpChanged(sheet.hp.current + 1) },
            enabled = sheet.hp.current < sheet.hp.max,
            modifier = Modifier.weight(1f).testTag(C.Tag.game_player_hp_increase),
        ) {
          Text(stringResource(R.string.game_player_heal))
        }
      }
      Text(stringResource(R.string.game_player_max_hp, sheet.hp.max.toString()))
    }
  }
  item {
    Text(
        stringResource(R.string.game_player_stats),
        style = MaterialTheme.typography.titleMedium,
    )
  }
  items(sheet.stats, key = { it.statID }) { stat ->
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Column {
        Text(stat.name, style = MaterialTheme.typography.bodyLarge)
        Text(stringResource(R.string.game_player_stat_value, stat.value.toString()))
      }
      OutlinedButton(
          onClick = { onOpenDice(PLACEHOLDER_DICE_MODIFIER) },
          modifier = Modifier.testTag(C.Tag.gamePlayerStatRoll(stat.statID)),
      ) {
        Text(stringResource(R.string.game_player_roll))
      }
    }
  }
  item {
    HorizontalDivider()
    Text(
        stringResource(R.string.game_player_actions),
        style = MaterialTheme.typography.titleMedium,
    )
  }
  items(sheet.actions, key = { it.actionID }) { action ->
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Text(action.name, style = MaterialTheme.typography.bodyLarge)
      Text(action.description)
    }
  }
  uiState.error?.let { message ->
    item {
      Text(
          message,
          color = MaterialTheme.colorScheme.error,
          modifier = Modifier.testTag(C.Tag.game_player_error),
      )
    }
  }
  if (uiState.isSaving) {
    item {
      Row(
          modifier = Modifier.fillMaxWidth().testTag(C.Tag.game_player_saving),
          horizontalArrangement = Arrangement.Center,
          verticalAlignment = Alignment.CenterVertically,
      ) {
        CircularProgressIndicator()
        Text(
            stringResource(R.string.game_player_saving),
            modifier = Modifier.padding(start = 12.dp),
        )
      }
    }
  } else {
    item {
      Button(
          onClick = onSave,
          modifier = Modifier.fillMaxWidth().testTag(C.Tag.game_player_save),
      ) {
        Text(stringResource(R.string.game_player_save))
      }
    }
  }
  if (saveSucceeded) {
    item {
      Text(
          stringResource(R.string.game_player_saved),
          color = MaterialTheme.colorScheme.primary,
          modifier = Modifier.testTag(C.Tag.game_player_save_success),
      )
    }
  }
}

@Composable
private fun NumericField(
    value: String,
    error: String?,
    modifier: Modifier,
    onValidValue: (Int) -> Unit,
) {
  var draft by remember { mutableStateOf(value) }
  var isFocused by remember { mutableStateOf(false) }
  var hasInvalidInput by remember { mutableStateOf(false) }
  val displayedError =
      if (hasInvalidInput) stringResource(R.string.game_player_invalid_number) else error
  LaunchedEffect(value, isFocused) {
    if (!isFocused || draft.toIntOrNull() != value.toIntOrNull()) {
      draft = value
      hasInvalidInput = false
    }
  }

  OutlinedTextField(
      value = draft,
      onValueChange = { input ->
        draft = input
        val parsed = input.toIntOrNull()
        hasInvalidInput = parsed == null
        parsed?.let(onValidValue)
      },
      label = { Text(stringResource(R.string.game_player_current_hp)) },
      singleLine = true,
      isError = displayedError != null,
      supportingText = displayedError?.let { message -> ({ Text(message) }) },
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
      modifier = modifier.onFocusChanged { isFocused = it.isFocused },
  )
}
