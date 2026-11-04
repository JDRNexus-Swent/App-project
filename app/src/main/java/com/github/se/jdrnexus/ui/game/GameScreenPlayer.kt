// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.github.se.jdrnexus.ui.game

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.github.se.jdrnexus.model.filerepresentation.CharacterAction
import com.github.se.jdrnexus.model.filerepresentation.CharacterFile
import com.github.se.jdrnexus.model.filerepresentation.CharacterHealthPoints
import com.github.se.jdrnexus.model.filerepresentation.CharacterStat
import com.github.se.jdrnexus.model.personalSpace.JDRFile
import com.github.se.jdrnexus.ui.theme.SampleAppTheme

object GameScreenPlayerTestTags {
  const val BACK = "game_player_back"
  const val LOADING = "game_player_loading"
  const val EMPTY = "game_player_empty"
  const val ERROR = "game_player_error"
  const val CHARACTER_NAME = "game_player_character_name"
  const val CURRENT_HP = "game_player_current_hp"
  const val HP_DECREASE = "game_player_hp_decrease"
  const val HP_INCREASE = "game_player_hp_increase"
  const val SAVE = "game_player_save"
  const val SAVING = "game_player_saving"
  const val SAVE_SUCCESS = "game_player_save_success"
  const val WORKSPACE = "game_player_workspace"

  fun statRoll(statId: String) = "game_player_stat_roll_$statId"

  fun navigationTab(name: String) = "game_player_navigation_$name"
}

@Composable
fun GameScreenPlayer(
    groupId: String,
    viewModel: GamePlayerInterfaceVM,
    onBack: () -> Unit,
    onOpenDice: (Int) -> Unit,
    onOpenGroupWorkspace: (String) -> Unit,
    onTabSelected: (String) -> Unit = {},
) {
  val uiState by viewModel.uiState.collectAsState()
  var wasSaving by remember { mutableStateOf(false) }
  var saveSucceeded by remember { mutableStateOf(false) }

  LaunchedEffect(groupId) { viewModel.loadSession(groupId) }
  LaunchedEffect(uiState.isSaving, uiState.error) {
    if (uiState.isSaving) {
      wasSaving = true
      saveSucceeded = false
    } else if (wasSaving) {
      saveSucceeded = uiState.error == null
      wasSaving = false
    }
  }

  val character = uiState.character
  val saveCharacter = {
    if (character?.hasUnsavedChanges == true) {
      saveSucceeded = false
      viewModel.saveCharacter()
    }
  }
  val leaveScreen = {
    saveCharacter()
    onBack()
  }
  BackHandler(onBack = leaveScreen)

  GameScreenPlayerContent(
      uiState = uiState,
      saveSucceeded = saveSucceeded,
      onBack = leaveScreen,
      onHpChanged = viewModel::updateCharacterHp,
      onSave = saveCharacter,
      onOpenDice = onOpenDice,
      onOpenGroupWorkspace = { id ->
        saveCharacter()
        onOpenGroupWorkspace(id)
      },
      onTabSelected = { tab ->
        saveCharacter()
        onTabSelected(tab)
      },
  )
}

@Composable
private fun GameScreenPlayerContent(
    uiState: GamePlayerUiState,
    saveSucceeded: Boolean,
    onBack: () -> Unit,
    onHpChanged: (Int) -> Unit,
    onSave: () -> Unit,
    onOpenDice: (Int) -> Unit,
    onOpenGroupWorkspace: (String) -> Unit,
    onTabSelected: (String) -> Unit,
) {
  val character = uiState.character
  var selectedTab by rememberSaveable { mutableStateOf("Campaigns") }

  Scaffold(
      bottomBar = {
        PlayerBottomBar(
            selectedTab,
            { tab ->
              selectedTab = tab
              onTabSelected(tab)
            },
        )
      }
  ) { contentPadding ->
    LazyColumn(
        modifier = Modifier.fillMaxSize().imePadding().padding(contentPadding),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      item {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          OutlinedButton(
              onClick = onBack,
              modifier = Modifier.testTag(GameScreenPlayerTestTags.BACK),
          ) {
            Text("Back")
          }
          Text("Game session", style = MaterialTheme.typography.titleLarge)
          OutlinedButton(
              onClick = { uiState.groupId?.let(onOpenGroupWorkspace) },
              enabled = uiState.groupId != null,
              modifier = Modifier.testTag(GameScreenPlayerTestTags.WORKSPACE),
          ) {
            Text("Group Workspace")
          }
        }
      }
      when {
        uiState.isLoading -> {
          item {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              CircularProgressIndicator(
                  modifier = Modifier.testTag(GameScreenPlayerTestTags.LOADING)
              )
              Text("Loading game session...")
            }
          }
        }
        character == null -> {
          val error = uiState.error
          item {
            Text(
                text = error ?: "No character is available.",
                modifier =
                    Modifier.fillMaxWidth()
                        .padding(vertical = 32.dp)
                        .testTag(
                            if (error == null) GameScreenPlayerTestTags.EMPTY
                            else GameScreenPlayerTestTags.ERROR
                        ),
                color =
                    if (error == null) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.error,
            )
          }
        }
        else -> {
          val sheet = character.character
          item {
            Text(
                sheet.name,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.testTag(GameScreenPlayerTestTags.CHARACTER_NAME),
            )
          }
          item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
              NumericField(
                  value = sheet.hp.current.toString(),
                  error = uiState.hpError,
                  modifier = Modifier.fillMaxWidth().testTag(GameScreenPlayerTestTags.CURRENT_HP),
                  onValidValue = onHpChanged,
              )
              Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                OutlinedButton(
                    onClick = { onHpChanged(sheet.hp.current - 1) },
                    enabled = sheet.hp.current > 0,
                    modifier = Modifier.weight(1f).testTag(GameScreenPlayerTestTags.HP_DECREASE),
                ) {
                  Text("Take damage (−)")
                }
                OutlinedButton(
                    onClick = { onHpChanged(sheet.hp.current + 1) },
                    enabled = sheet.hp.current < Int.MAX_VALUE,
                    modifier = Modifier.weight(1f).testTag(GameScreenPlayerTestTags.HP_INCREASE),
                ) {
                  Text("Heal (+)")
                }
              }
              Text("Max HP: ${sheet.hp.max}")
            }
          }
          item { Text("Stats", style = MaterialTheme.typography.titleMedium) }
          items(sheet.stats, key = { it.statID }) { stat ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Column {
                Text(stat.name, style = MaterialTheme.typography.bodyLarge)
                Text("Value: ${stat.value}")
              }
              OutlinedButton(
                  onClick = { onOpenDice(0) },
                  modifier = Modifier.testTag(GameScreenPlayerTestTags.statRoll(stat.statID)),
              ) {
                Text("Modifier +0 · Roll")
              }
            }
          }
          item {
            HorizontalDivider()
            Text("Actions", style = MaterialTheme.typography.titleMedium)
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
                  modifier = Modifier.testTag(GameScreenPlayerTestTags.ERROR),
              )
            }
          }
          if (uiState.isSaving) {
            item {
              Row(
                  modifier = Modifier.fillMaxWidth().testTag(GameScreenPlayerTestTags.SAVING),
                  horizontalArrangement = Arrangement.Center,
                  verticalAlignment = Alignment.CenterVertically,
              ) {
                CircularProgressIndicator()
                Text("Saving changes...", modifier = Modifier.padding(start = 12.dp))
              }
            }
          } else {
            item {
              Button(
                  onClick = onSave,
                  modifier = Modifier.fillMaxWidth().testTag(GameScreenPlayerTestTags.SAVE),
              ) {
                Text("Save Changes")
              }
            }
          }
          if (saveSucceeded) {
            item {
              Text(
                  "Changes saved.",
                  color = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.testTag(GameScreenPlayerTestTags.SAVE_SUCCESS),
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun PlayerBottomBar(selectedTab: String, onTabSelected: (String) -> Unit) {
  val tabs =
      listOf(
          "Home" to Icons.Filled.Home,
          "Campaigns" to Icons.AutoMirrored.Outlined.MenuBook,
          "Files" to Icons.Outlined.Folder,
          "Map" to Icons.Outlined.Map,
      )
  NavigationBar {
    tabs.forEach { (label, icon) ->
      NavigationBarItem(
          selected = selectedTab == label,
          onClick = { onTabSelected(label) },
          icon = { Icon(icon, contentDescription = null) },
          label = { Text(label) },
          modifier = Modifier.testTag(GameScreenPlayerTestTags.navigationTab(label)),
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
  var draft by remember(value) { mutableStateOf(value) }
  var isFocused by remember { mutableStateOf(false) }
  var hasInvalidInput by remember { mutableStateOf(false) }
  val displayedError = if (hasInvalidInput) "Enter a whole number." else error
  LaunchedEffect(value, isFocused) {
    if (!isFocused) {
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
      label = { Text("Current HP") },
      singleLine = true,
      isError = displayedError != null,
      supportingText = displayedError?.let { message -> ({ Text(message) }) },
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
      modifier = modifier.onFocusChanged { isFocused = it.isFocused },
  )
}

@Preview(showBackground = true)
@Composable
private fun GameScreenPlayerPreview() {
  val character =
      SessionCharacter(
          file = JDRFile(id = "preview-character"),
          character =
              CharacterFile(
                  name = "Elara Moonwhisper",
                  stats =
                      listOf(
                          CharacterStat("dex", "Dexterity", 14.0),
                          CharacterStat("str", "Strenght", 16.0),
                      ),
                  actions = listOf(CharacterAction("aim", "Aim", "Take a careful shot.")),
                  hp = CharacterHealthPoints(current = 18, max = 24),
              ),
      )
  SampleAppTheme {
    GameScreenPlayerContent(
        uiState =
            GamePlayerUiState(
                groupId = "preview-group",
                character = character,
            ),
        saveSucceeded = false,
        onBack = {},
        onHpChanged = {},
        onSave = {},
        onOpenDice = {},
        onOpenGroupWorkspace = {},
        onTabSelected = {},
    )
  }
}
