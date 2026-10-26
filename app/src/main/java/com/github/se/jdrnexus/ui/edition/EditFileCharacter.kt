// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.github.se.jdrnexus.ui.edition

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import com.github.se.jdrnexus.model.filerepresentation.CharacterAction
import com.github.se.jdrnexus.model.filerepresentation.CharacterStat
import com.github.se.jdrnexus.ui.jdrfile.EditFileField
import com.github.se.jdrnexus.ui.jdrfile.EditFileViewModel

/** Semantic test tags used to locate elements in the character editor. */
object EditFileCharacterTestTags {

  const val STATS_LIST = "edit_character_stats"

  const val ACTIONS_LIST = "edit_character_actions"

  const val BACK = "edit_character_back"
  const val CURRENT_HP = "edit_character_current_hp"
  const val MAX_HP = "edit_character_max_hp"
  const val LIST = "edit_character_list"
}

/**
 * Displays an editor for a character file, including its name, stats, actions, and save state.
 *
 * @param viewModel supplies the character data and handles edits and saving.
 * @param onBack is called when the user requests to leave the editor.
 * @param onSaved is called when the view model reports a successful save.
 */
@Composable
fun EditFileCharacter(
    viewModel: EditFileViewModel,
    onBack: () -> Unit,
    onSaved: () -> Unit,
) {
  val state by viewModel.uiState.collectAsState()

  LaunchedEffect(state.isSaved) { if (state.isSaved) onSaved() }

  EditFileScaffold(
      isLoading = state.isLoading,
      loadingMessage = stringResource(R.string.edit_character_loading),
      hasContent = state.character != null,
      screenTitle = stringResource(R.string.edit_character_title),
      backTestTag = EditFileCharacterTestTags.BACK,
      error = state.error,
      emptyMessage = stringResource(R.string.edit_character_empty),
      onBack = onBack,
  ) {
    val character = state.character ?: return@EditFileScaffold
    var newStatName by remember { mutableStateOf("") }
    var newStatValue by remember { mutableStateOf("") }
    var newActionName by remember { mutableStateOf("") }
    var newActionDescription by remember { mutableStateOf("") }

    LazyColumn(
        modifier =
            Modifier.fillMaxSize()
                .imePadding()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .testTag(EditFileCharacterTestTags.LIST),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      item(key = "top-bar") {
        EditFileHeader(
            title = stringResource(R.string.edit_character_title),
            backTestTag = EditFileCharacterTestTags.BACK,
            onBack = onBack,
        )
      }
      item(key = "character-name") {
        OutlinedTextField(
            value = character.name,
            onValueChange = viewModel::updateCharacterName,
            modifier = Modifier.fillMaxWidth().testTag("edit_character_name"),
            label = { Text(stringResource(R.string.edit_character_name_label)) },
            singleLine = true,
        )
      }
      item(key = "character-hp") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
          NumberField(
              label = stringResource(R.string.edit_character_current_hp_label),
              value = character.hp.current.toString(),
              stateKey = "current-hp",
              error = state.fieldErrors[EditFileField.CharacterCurrentHp],
              modifier = Modifier.weight(1f).testTag(EditFileCharacterTestTags.CURRENT_HP),
              keyboardType = KeyboardType.Number,
              onValueChange = { value ->
                val parsed = value.toIntOrNull()
                if (parsed == null) {
                  return@NumberField
                }
                viewModel.updateCharacterCurrentHp(parsed)
              },
          )
          NumberField(
              label = stringResource(R.string.edit_character_max_hp_label),
              value = character.hp.max.toString(),
              stateKey = "max-hp",
              error = state.fieldErrors[EditFileField.CharacterMaxHp],
              modifier = Modifier.weight(1f).testTag(EditFileCharacterTestTags.MAX_HP),
              keyboardType = KeyboardType.Number,
              onValueChange = { value ->
                val parsed = value.toIntOrNull()
                if (parsed == null) {
                  return@NumberField
                }
                viewModel.updateCharacterMaxHp(parsed)
              },
          )
        }
      }
      item(key = "stats-heading") {
        EditFileSectionHeading(
            title = stringResource(R.string.edit_character_stats_title),
            count = character.stats.size,
            modifier = Modifier.testTag(EditFileCharacterTestTags.STATS_LIST),
            singularCountLabel = stringResource(R.string.edit_character_stat_singular),
            pluralCountLabel = stringResource(R.string.edit_character_stat_plural),
        )
      }
      items(character.stats, key = { "stat-${it.statID}" }) { stat ->
        StatRow(
            stat = stat,
            nameError = state.fieldErrors[EditFileField.CharacterStatName(stat.statID)],
            valueError = state.fieldErrors[EditFileField.CharacterStatValue(stat.statID)],
            onNameChange = { viewModel.updateCharacterStatName(stat.statID, it) },
            onValueChange = {
              viewModel.updateCharacterStatValue(
                  stat.statID,
                  it.toDoubleOrNull() ?: Double.NaN,
              )
            },
            onRemove = { viewModel.removeCharacterStat(stat.statID) },
        )
      }
      item(key = "add-stat") {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(6.dp),
              verticalAlignment = Alignment.CenterVertically,
          ) {
            OutlinedTextField(
                value = newStatName,
                onValueChange = { newStatName = it },
                modifier = Modifier.weight(1f).testTag("add_entry_name"),
                label = { Text(stringResource(R.string.edit_character_stat_name_label)) },
                singleLine = true,
            )
            OutlinedTextField(
                value = newStatValue,
                onValueChange = { newStatValue = it },
                modifier = Modifier.weight(0.6f).testTag("add_entry_value"),
                label = { Text(stringResource(R.string.edit_character_value_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
          }
          OutlinedButton(
              onClick = {
                val name = newStatName.trim()
                val value = newStatValue.toDoubleOrNull()
                if (name.isBlank() || value == null || !value.isFinite()) {
                  return@OutlinedButton
                }
                viewModel.addCharacterStat(name, value)
                newStatName = ""
                newStatValue = ""
              },
              modifier = Modifier.fillMaxWidth().testTag("confirm_add_stat"),
              shape = RoundedCornerShape(8.dp),
          ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Text(
                stringResource(R.string.edit_character_add_stat),
                modifier = Modifier.padding(start = 6.dp),
            )
          }
        }
      }
      item(key = "section-divider") { HorizontalDivider() }
      item(key = "actions-heading") {
        EditFileSectionHeading(
            title = stringResource(R.string.edit_character_actions_title),
            count = character.actions.size,
            modifier = Modifier.testTag(EditFileCharacterTestTags.ACTIONS_LIST),
            singularCountLabel = stringResource(R.string.edit_character_action_singular),
            pluralCountLabel = stringResource(R.string.edit_character_action_plural),
        )
      }
      items(character.actions, key = { "action-${it.actionID}" }) { action ->
        ActionRow(
            action = action,
            nameError = state.fieldErrors[EditFileField.CharacterActionName(action.actionID)],
            onNameChange = { viewModel.updateCharacterActionName(action.actionID, it) },
            onDescriptionChange = {
              viewModel.updateCharacterActionDescription(action.actionID, it)
            },
            onRemove = { viewModel.removeCharacterAction(action.actionID) },
        )
      }
      item(key = "add-action") {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
          OutlinedTextField(
              value = newActionName,
              onValueChange = { newActionName = it },
              modifier = Modifier.fillMaxWidth().testTag("add_action_name"),
              label = { Text(stringResource(R.string.edit_character_action_name_label)) },
              singleLine = true,
          )
          OutlinedTextField(
              value = newActionDescription,
              onValueChange = { newActionDescription = it },
              modifier = Modifier.fillMaxWidth().testTag("add_action_description"),
              label = { Text(stringResource(R.string.edit_character_description_label)) },
              singleLine = true,
          )
          OutlinedButton(
              onClick = {
                val name = newActionName.trim()
                if (name.isBlank()) {
                  return@OutlinedButton
                }
                viewModel.addCharacterAction(name, newActionDescription)
                newActionName = ""
                newActionDescription = ""
              },
              modifier = Modifier.fillMaxWidth().testTag("confirm_add_action"),
              shape = RoundedCornerShape(8.dp),
          ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Text(
                stringResource(R.string.edit_character_add_action),
                modifier = Modifier.padding(start = 6.dp),
            )
          }
        }
      }
      state.error?.let { error ->
        item(key = "error") { EditFileErrorMessage(error, "edit_character_error") }
      }
      item(key = "save") {
        EditFileSaveButton(
            isSaving = state.isSaving,
            isSaved = state.isSaved,
            testTag = "edit_character_save",
            onSave = viewModel::saveJDRfile,
        )
      }
    }
  }
}

/**
 * Displays an editable stat row with name and value fields and a removal button.
 *
 * @param stat stat represented by this row.
 * @param nameError validation message for the stat name, if any.
 * @param valueError validation message for the stat value, if any.
 * @param onNameChange called with an updated stat name.
 * @param onValueChange called with an updated stat value string.
 * @param onRemove called when the stat is requested to be removed.
 */
@Composable
private fun StatRow(
    stat: CharacterStat,
    nameError: String?,
    valueError: String?,
    onNameChange: (String) -> Unit,
    onValueChange: (String) -> Unit,
    onRemove: () -> Unit,
) {
  Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(6.dp),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    EditableTextField(
        value = stat.name,
        onValueChange = onNameChange,
        modifier = Modifier.weight(1f).testTag("stat_name_${stat.statID}"),
        label = { Text(stringResource(R.string.edit_character_name_label)) },
        singleLine = true,
        isError = nameError != null,
        supportingText = nameError?.let { message -> ({ Text(message) }) },
    )
    NumberField(
        label = stringResource(R.string.edit_character_value_label),
        value = stat.value.toString(),
        stateKey = stat.statID,
        error = valueError,
        modifier = Modifier.weight(0.55f).testTag("stat_value_${stat.statID}"),
        onValueChange = onValueChange,
    )
    IconButton(
        onClick = onRemove,
        modifier = Modifier.size(40.dp).testTag("remove_stat_${stat.statID}"),
    ) {
      Icon(Icons.Default.Close, contentDescription = "Remove ${stat.name}")
    }
  }
}

/**
 * Displays an editable action card with name and description fields and a removal button.
 *
 * @param action action represented by this card.
 * @param nameError validation message for the action name, if any.
 * @param onNameChange called with an updated action name.
 * @param onDescriptionChange called with an updated action description.
 * @param onRemove called when the action is requested to be removed.
 */
@Composable
private fun ActionRow(
    action: CharacterAction,
    nameError: String?,
    onNameChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onRemove: () -> Unit,
) {
  Surface(
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(10.dp),
      color = MaterialTheme.colorScheme.surfaceVariant,
      tonalElevation = 1.dp,
  ) {
    Column(modifier = Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
      ) {
        EditableTextField(
            value = action.name,
            onValueChange = onNameChange,
            modifier = Modifier.weight(1f).testTag("action_name_${action.actionID}"),
            label = { Text(stringResource(R.string.edit_character_action_label)) },
            singleLine = true,
            isError = nameError != null,
            supportingText = nameError?.let { message -> ({ Text(message) }) },
        )
        IconButton(
            onClick = onRemove,
            modifier = Modifier.size(40.dp).testTag("remove_action_${action.actionID}"),
        ) {
          Icon(Icons.Default.Close, contentDescription = "Remove ${action.name}")
        }
      }
      OutlinedTextField(
          value = action.description,
          onValueChange = onDescriptionChange,
          modifier = Modifier.fillMaxWidth().testTag("action_description_${action.actionID}"),
          label = { Text(stringResource(R.string.edit_character_description_label)) },
          minLines = 2,
      )
    }
  }
}

/**
 * Displays a decimal input that keeps its draft while focused and shows an optional validation
 * message.
 *
 * @param label text shown as the field label.
 * @param value current committed value displayed when the field is not being edited.
 * @param stateKey stable key used to retain input state for this field.
 * @param error validation message to display, if any.
 * @param modifier modifier applied to the input field.
 * @param onValueChange called whenever the user changes the input text.
 */
@Composable
private fun NumberField(
    label: String,
    value: String,
    stateKey: String,
    error: String?,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Decimal,
    onValueChange: (String) -> Unit,
) {
  var draft by remember(stateKey) { mutableStateOf(value) }
  var isFocused by remember(stateKey) { mutableStateOf(false) }
  LaunchedEffect(value, isFocused) { if (!isFocused) draft = value }

  OutlinedTextField(
      value = draft,
      onValueChange = {
        draft = it
        onValueChange(it)
      },
      modifier = modifier.onFocusChanged { isFocused = it.isFocused },
      label = { Text(label) },
      singleLine = true,
      isError = error != null,
      keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
      supportingText = error?.let { message -> ({ Text(message) }) },
  )
}

/**
 * Displays a text input that keeps a local draft and reports edits to its owner.
 *
 * @param value current text value.
 * @param onValueChange called whenever the user changes the text.
 * @param modifier modifier applied to the input field.
 * @param label composable field label.
 * @param singleLine whether the input is restricted to one line.
 * @param minLines minimum number of visible text lines.
 * @param isError whether the field should use its error presentation.
 * @param supportingText optional composable supporting text, commonly a validation message.
 */
@Composable
private fun EditableTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: @Composable () -> Unit,
    singleLine: Boolean = false,
    minLines: Int = 1,
    isError: Boolean = false,
    supportingText: (@Composable () -> Unit)? = null,
) {
  var draft by remember(value) { mutableStateOf(value) }
  OutlinedTextField(
      value = draft,
      onValueChange = {
        draft = it
        onValueChange(it)
      },
      modifier = modifier,
      label = label,
      singleLine = singleLine,
      minLines = minLines,
      isError = isError,
      supportingText = supportingText,
  )
}
