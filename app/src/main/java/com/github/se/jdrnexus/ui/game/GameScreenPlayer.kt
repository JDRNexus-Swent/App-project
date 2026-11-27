// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
// Co-authored-by: Codex <noreply@openai.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.ui.game

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.github.se.jdrnexus.R
import com.github.se.jdrnexus.model.filerepresentation.CharacterAction
import com.github.se.jdrnexus.model.filerepresentation.CharacterFile
import com.github.se.jdrnexus.model.filerepresentation.CharacterHealthPoints
import com.github.se.jdrnexus.model.filerepresentation.CharacterStat
import com.github.se.jdrnexus.model.personalSpace.JDRFile
import com.github.se.jdrnexus.resources.AppTopBar
import com.github.se.jdrnexus.resources.C
import com.github.se.jdrnexus.resources.ScreenBottomBar
import com.github.se.jdrnexus.ui.theme.SampleAppTheme

/** Displays the player's character sheet and saves pending edits before navigating away. */
@Composable
fun GameScreenPlayer(
    groupId: String,
    viewModel: GamePlayerInterfaceVM,
    onBack: () -> Unit,
    onOpenDice: (Int) -> Unit,
    onOpenGroupWorkspace: (String) -> Unit,
    onTabSelected: (String) -> Unit,
) {
  val uiState by viewModel.uiState.collectAsState()
  var selectedTab by rememberSaveable { mutableStateOf("Campaigns") }
  var wasSaving by remember { mutableStateOf(false) }
  var saveSucceeded by remember { mutableStateOf(false) }
  val hasUnsavedChanges = uiState.character?.hasUnsavedChanges == true

  LaunchedEffect(groupId) { viewModel.loadSession(groupId) }
  LaunchedEffect(uiState.isSaving, uiState.error, hasUnsavedChanges) {
    if (uiState.isSaving) {
      wasSaving = true
      saveSucceeded = false
    } else if (wasSaving) {
      saveSucceeded = uiState.error == null && !hasUnsavedChanges
      wasSaving = false
    } else if (hasUnsavedChanges) {
      saveSucceeded = false
    }
  }

  val character = uiState.character
  val saveCharacter = {
    if (character?.hasUnsavedChanges == true) {
      saveSucceeded = false
      viewModel.saveCharacter()
    }
  }
  fun navigate(destination: GamePlayerDestination) {
    when (destination) {
      GamePlayerDestination.Back -> onBack()
      is GamePlayerDestination.Workspace -> onOpenGroupWorkspace(destination.groupId)
      is GamePlayerDestination.Dice -> onOpenDice(destination.modifier)
      is GamePlayerDestination.Tab -> {
        onTabSelected(destination.name)
        selectedTab = destination.name
      }
    }
  }

  val ready = uiState.navigation as? GamePlayerNavigation.Ready
  LaunchedEffect(ready) { if (ready != null) viewModel.consumeNavigation()?.let(::navigate) }

  val leaveScreen = { viewModel.requestNavigation(GamePlayerDestination.Back) }
  BackHandler(onBack = leaveScreen)

  val failed = uiState.navigation as? GamePlayerNavigation.SaveFailed
  if (failed != null) {
    AlertDialog(
        onDismissRequest = viewModel::stayOnScreen,
        title = { Text(stringResource(R.string.game_player_leave_title)) },
        text = {
          Text(failed.saveBeforeLeavingError, Modifier.testTag(C.Tag.game_player_leave_error))
        },
        confirmButton = {
          TextButton(
              onClick = viewModel::leaveAnyway,
              modifier = Modifier.testTag(C.Tag.game_player_leave_anyway),
          ) {
            Text(stringResource(R.string.game_player_leave_anyway))
          }
        },
        dismissButton = {
          TextButton(
              onClick = viewModel::stayOnScreen,
              modifier = Modifier.testTag(C.Tag.game_player_stay),
          ) {
            Text(stringResource(R.string.game_player_stay))
          }
        },
    )
  }

  GameScreenPlayerContent(
      uiState = uiState,
      selectedTab = selectedTab,
      saveSucceeded = saveSucceeded,
      onBack = leaveScreen,
      onHpChanged = viewModel::updateCharacterHp,
      onSave = saveCharacter,
      onOpenDice = { viewModel.requestNavigation(GamePlayerDestination.Dice(it)) },
      onOpenGroupWorkspace = { viewModel.requestNavigation(GamePlayerDestination.Workspace(it)) },
      onTabSelected = { viewModel.requestNavigation(GamePlayerDestination.Tab(it)) },
  )
}

@Composable
private fun GameScreenPlayerContent(
    uiState: GamePlayerUiState,
    selectedTab: String,
    saveSucceeded: Boolean,
    onBack: () -> Unit,
    onHpChanged: (Int) -> Unit,
    onSave: () -> Unit,
    onOpenDice: (Int) -> Unit,
    onOpenGroupWorkspace: (String) -> Unit,
    onTabSelected: (String) -> Unit,
) {
  val character = uiState.character

  Scaffold(
      contentColor = MaterialTheme.colorScheme.onSurface,
      topBar = {
        AppTopBar(
            title = stringResource(R.string.game_player_title),
            onBack = onBack,
            backTestTag = C.Tag.game_player_back,
            trailingContent = {
              OutlinedButton(
                  onClick = { uiState.groupId?.let(onOpenGroupWorkspace) },
                  enabled = uiState.groupId != null,
                  modifier = Modifier.testTag(C.Tag.game_player_workspace),
              ) {
                Text(stringResource(R.string.game_player_workspace))
              }
            },
        )
      },
      bottomBar = { ScreenBottomBar(selectedTab, onTabSelected) },
  ) { contentPadding ->
    LazyColumn(
        modifier = Modifier.fillMaxSize().imePadding().padding(contentPadding),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      when {
        uiState.isLoading -> {
          item {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              CircularProgressIndicator(modifier = Modifier.testTag(C.Tag.game_player_loading))
              Text(stringResource(R.string.game_player_loading))
            }
          }
        }
        character == null -> {
          val error = uiState.error
          item {
            Text(
                text = error ?: stringResource(R.string.game_player_empty),
                modifier =
                    Modifier.fillMaxWidth()
                        .padding(vertical = 32.dp)
                        .testTag(
                            if (error == null) C.Tag.game_player_empty else C.Tag.game_player_error
                        ),
                color =
                    if (error == null) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.error,
            )
          }
        }
        else -> {
          playerCharacterSection(
              sheet = character.character,
              uiState = uiState,
              saveSucceeded = saveSucceeded,
              onHpChanged = onHpChanged,
              onSave = onSave,
              onOpenDice = onOpenDice,
          )
        }
      }
    }
  }
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
        selectedTab = "Campaigns",
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
