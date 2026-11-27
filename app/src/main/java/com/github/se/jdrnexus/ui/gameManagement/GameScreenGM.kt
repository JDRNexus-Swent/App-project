// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
// Co-authored-by: Codex <codex@openai.com>
package com.github.se.jdrnexus.ui.gameManagement

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.se.jdrnexus.R
import com.github.se.jdrnexus.resources.AppTopBar
import com.github.se.jdrnexus.resources.C
import com.github.se.jdrnexus.resources.ScreenBottomBar

/**
 * Connects the GM view model to the stateless game-master session UI.
 *
 * @param viewModel Supplies session state and handles game actions.
 * @param onTabSelected Receives selections from the shared bottom navigation.
 * @param onDiceClick Opens the dice roller.
 * @param groupName Name shown for the active campaign.
 * @param onBack Leaves the screen; the top bar shows no back button when it is null.
 */
@Composable
fun GameScreenGM(
    viewModel: GameViewModelGM,
    onTabSelected: (String) -> Unit = {},
    onDiceClick: () -> Unit = {},
    groupName: String = "",
    onBack: (() -> Unit)? = null,
) {
  val uiState by viewModel.uiState.collectAsState()
  GameScreenGMContent(
      uiState = uiState,
      groupName = groupName,
      onAddEnemy = viewModel::addEnemy,
      onRemoveEnemy = viewModel::removeEnemy,
      onUpdateEnemyHp = viewModel::updateEnemyHP,
      onStartCombat = viewModel::startCombat,
      onNextRound = viewModel::nextRound,
      onEndCombat = viewModel::endCombat,
      onRetry = {
        viewModel.loadActivityFeed()
        viewModel.loadNPCs()
        viewModel.loadLoreDocuments()
      },
      onTabSelected = onTabSelected,
      onDiceClick = onDiceClick,
      onStartGame = viewModel::startGame,
      onBack = onBack,
  )
}

/**
 * Shows the Start game button, or a "Game in progress" label once the game has started.
 *
 * The button is disabled while the screen loads and while the start is being saved.
 */
@Composable
private fun GameStartControl(uiState: GameViewModelGMUiState, onStartGame: () -> Unit) {
  if (uiState.isGameStarted) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier =
            Modifier.testTag(C.Tag.game_gm_game_in_progress)
                .clip(CircleShape)
                .background(colors.surfaceContainerLowest)
                .padding(horizontal = 10.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Box(Modifier.size(6.dp).clip(CircleShape).background(colors.primary))
      Text(
          stringResource(R.string.game_gm_game_in_progress),
          color = colors.onSurface,
          style = MaterialTheme.typography.labelMedium,
      )
    }
  } else {
    Button(
        onClick = onStartGame,
        enabled = !uiState.isLoading && !uiState.isStartingGame,
        modifier = Modifier.fillMaxWidth().height(46.dp).testTag(C.Tag.game_gm_start_game),
        shape = RoundedCornerShape(10.dp),
    ) {
      if (uiState.isStartingGame) {
        CircularProgressIndicator(
            modifier = Modifier.size(18.dp).testTag(C.Tag.game_gm_start_game_progress),
            color = LocalContentColor.current,
            strokeWidth = 2.dp,
        )
      } else {
        Icon(Icons.Filled.Flag, contentDescription = null, modifier = Modifier.size(18.dp))
      }
      Spacer(Modifier.width(8.dp))
      Text(stringResource(R.string.game_gm_start_game))
    }
  }
}

/**
 * Renders the game-master session from supplied state and callbacks.
 *
 * @param uiState Current combat, activity, NPC, and lore state.
 * @param groupName Name shown for the active campaign.
 * @param onAddEnemy Adds a validated enemy to the encounter.
 * @param onRemoveEnemy Removes an enemy by ID.
 * @param onUpdateEnemyHp Changes an enemy's hit points.
 * @param onStartCombat Starts the encounter.
 * @param onNextRound Advances the encounter round.
 * @param onEndCombat Ends the encounter.
 * @param onRetry Requests the unavailable session data again.
 * @param onTabSelected Receives shared bottom-navigation selections.
 * @param onDiceClick Opens the dice roller.
 * @param onStartGame Starts the campaign's game session.
 * @param onBack Leaves the screen; the top bar shows no back button when it is null.
 * @param renderAddEnemyDialogAsWindow Whether the add-enemy dialog opens in its own window; tests
 *   pass `false` to render it inline.
 */
@Composable
internal fun GameScreenGMContent(
    uiState: GameViewModelGMUiState,
    groupName: String = "",
    onAddEnemy: (CombatNPC) -> Unit = {},
    onRemoveEnemy: (String) -> Unit = {},
    onUpdateEnemyHp: (String, Int) -> Unit = { _, _ -> },
    onStartCombat: () -> Unit = {},
    onNextRound: () -> Unit = {},
    onEndCombat: () -> Unit = {},
    onRetry: () -> Unit = {},
    onTabSelected: (String) -> Unit = {},
    onDiceClick: () -> Unit = {},
    onStartGame: () -> Unit = {},
    onBack: (() -> Unit)? = null,
    renderAddEnemyDialogAsWindow: Boolean = true,
) {
  var showAddEnemyDialog by rememberSaveable { mutableStateOf(false) }
  var selectedNpcId by rememberSaveable { mutableStateOf<String?>(null) }
  // A set of enum values is Serializable, so the hidden sections survive rotation.
  var hidden by rememberSaveable { mutableStateOf(emptySet<GmSection>()) }
  var expandedLoreId by rememberSaveable { mutableStateOf<String?>(null) }
  val selectedNpc = uiState.npcs.firstOrNull { it.id == selectedNpcId }

  Scaffold(
      topBar = { AppTopBar(title = stringResource(R.string.game_gm_header), onBack = onBack) },
      bottomBar = { ScreenBottomBar(selectedTab = "Campaigns", onTabSelected = onTabSelected) },
      contentColor = MaterialTheme.colorScheme.onSurface,
      floatingActionButton = {
        FloatingActionButton(
            onClick = onDiceClick,
            modifier = Modifier.testTag(C.Tag.game_gm_dice),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ) {
          Icon(
              imageVector = Icons.Default.Casino,
              contentDescription = stringResource(R.string.game_gm_dice_cd),
          )
        }
      },
  ) { padding ->
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding).testTag(C.Tag.game_gm_list),
        // 96dp = 24dp gap + 56dp FAB + 16dp FAB margin, so the last card can scroll above the FAB.
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      item {
        Column(
            modifier = Modifier.padding(horizontal = 2.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Text(
              groupName,
              color = MaterialTheme.colorScheme.onSecondary,
              fontFamily = FontFamily.Serif,
              fontSize = 30.sp,
              lineHeight = 34.sp,
          )
          GameStartControl(uiState = uiState, onStartGame = onStartGame)
        }
      }
      if (hidden.isNotEmpty()) {
        item { HiddenSections(GmSection.entries.filter { it in hidden }) { hidden -= it } }
      }
      if (GmSection.COMBAT !in hidden) {
        item {
          GmCombatSection(
              currentRound = uiState.currentRound,
              isCombatActive = uiState.isCombatActive,
              enemies = uiState.enemies,
              onDismiss = { hidden += GmSection.COMBAT },
              onStartCombat = onStartCombat,
              onNextRound = onNextRound,
              onEndCombat = onEndCombat,
              onOpenAddEnemy = { showAddEnemyDialog = true },
              onUpdateEnemyHp = onUpdateEnemyHp,
              onRemoveEnemy = onRemoveEnemy,
          )
        }
      }
      if (uiState.isLoading) {
        item {
          Column(
              modifier = Modifier.fillMaxWidth().testTag(C.Tag.game_gm_loading),
              horizontalAlignment = Alignment.CenterHorizontally,
          ) {
            CircularProgressIndicator()
            Text(stringResource(R.string.game_gm_loading))
          }
        }
      }
      if (uiState.error.isNotBlank()) {
        item {
          Column(
              modifier = Modifier.fillMaxWidth().testTag(C.Tag.game_gm_error),
              verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Text(uiState.error, color = MaterialTheme.colorScheme.error)
            TextButton(onClick = onRetry) { Text(stringResource(R.string.game_gm_retry)) }
          }
        }
      }
      if (GmSection.ACTIVITY !in hidden) {
        item {
          GmActivitySection(
              activityFeed = uiState.activityFeed,
              onDismiss = { hidden += GmSection.ACTIVITY },
          )
        }
      }
      if (GmSection.NPCS !in hidden) {
        item {
          GmNpcSection(
              npcs = uiState.npcs,
              onDismiss = { hidden += GmSection.NPCS },
              onSelectNpc = { selectedNpcId = it },
          )
        }
      }
      if (GmSection.LORE !in hidden) {
        item {
          GmLoreSection(
              loreDocuments = uiState.loreDocuments,
              expandedLoreId = expandedLoreId,
              onDismiss = { hidden += GmSection.LORE },
              onToggleLore = { id -> expandedLoreId = if (expandedLoreId == id) null else id },
          )
        }
      }
    }
  }

  if (showAddEnemyDialog) {
    AddEnemyDialog(
        onDismiss = { showAddEnemyDialog = false },
        onAdd = {
          onAddEnemy(it)
          showAddEnemyDialog = false
        },
        renderAsWindow = renderAddEnemyDialogAsWindow,
    )
  }
  selectedNpc?.let { npc -> GmNpcDialog(npc, onDismiss = { selectedNpcId = null }) }
}
