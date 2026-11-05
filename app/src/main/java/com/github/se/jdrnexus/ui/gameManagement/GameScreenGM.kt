// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.github.se.jdrnexus.ui.gameManagement

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.github.se.jdrnexus.ui.theme.SampleAppTheme

object GameScreenGMTestTags {
  const val LOADING = "game_gm_loading"
  const val ERROR = "game_gm_error"
  const val EMPTY_COMBAT = "game_gm_empty_combat"
  const val ADD_ENEMY = "game_gm_add_enemy"
  const val START_COMBAT = "game_gm_start_combat"
  const val NEXT_ROUND = "game_gm_next_round"
  const val END_COMBAT = "game_gm_end_combat"
  const val DISMISS_ACTIVITY = "game_gm_dismiss_activity"
  const val ADD_ENEMY_DIALOG = "game_gm_add_enemy_dialog"
  const val ENEMY_NAME = "game_gm_enemy_name"

  fun enemy(id: String) = "game_gm_enemy_$id"

  fun enemyIncrease(id: String) = "game_gm_enemy_increase_$id"

  fun enemyDecrease(id: String) = "game_gm_enemy_decrease_$id"

  fun enemyRemove(id: String) = "game_gm_enemy_remove_$id"

  fun npc(id: String) = "game_gm_npc_$id"

  fun lore(id: String) = "game_gm_lore_$id"

  fun navigationTab(name: String) = "game_gm_navigation_$name"
}

@Composable
fun GameScreenGM(
    viewModel: GameViewModelGM,
    onTabSelected: (String) -> Unit = {},
    groupID: String = "",
) {
  val uiState by viewModel.uiState.collectAsState()
  GameScreenGMContent(
      uiState = uiState,
      onAddEnemy = viewModel::addEnemy,
      onRemoveEnemy = viewModel::removeEnemy,
      onUpdateEnemyHp = viewModel::updateEnemyHP,
      onStartCombat = viewModel::startCombat,
      onNextRound = viewModel::nextRound,
      onEndCombat = viewModel::endCombat,
      onSelectLore = viewModel::selectLoreDocument,
      onTabSelected = onTabSelected,
      groupID = groupID,
  )
}

@Composable
internal fun GameScreenGMContent(
    uiState: GameViewModelGMUiState,
    onAddEnemy: (Enemy) -> Unit = {},
    onRemoveEnemy: (String) -> Unit = {},
    onUpdateEnemyHp: (String, Int) -> Unit = { _, _ -> },
    onStartCombat: () -> Unit = {},
    onNextRound: () -> Unit = {},
    onEndCombat: () -> Unit = {},
    onSelectLore: (String) -> Unit = {},
    onTabSelected: (String) -> Unit = {},
    groupID: String = "",
) {
  var showAddEnemyDialog by rememberSaveable { mutableStateOf(false) }
  var selectedNpcId by rememberSaveable { mutableStateOf<String?>(null) }
  var activityVisible by rememberSaveable { mutableStateOf(true) }
  var expandedLoreId by rememberSaveable { mutableStateOf<String?>(null) }
  val selectedNpc = uiState.npcs.firstOrNull { it.id == selectedNpcId }
  val color = MaterialTheme.colorScheme

  Scaffold(
      bottomBar = { GameGMNavigationBar(onTabSelected) },
      floatingActionButton = {
        FloatingActionButton(onClick = { showAddEnemyDialog = true }) {
          Icon(Icons.Filled.Add, contentDescription = "Add enemy")
        }
      },
      contentColor = color.background,
  ) { padding ->
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      item {
        Column {
          Text(
              "Campaign Game Master",
              style = MaterialTheme.typography.headlineSmall,
              color = color.tertiary,
              fontSize = 13.sp,
          )
          Text(groupID, color = color.onSecondary)
        }
      }
      item {
        SectionCard("Combat Live") {
          Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
          ) {
            Column { Text("Round ${uiState.currentRound}") }
            if (uiState.isCombatActive) {
              Text("LIVE", color = color.primary, fontWeight = FontWeight.Bold)
            }
          }
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!uiState.isCombatActive) {
              Button(
                  onClick = onStartCombat,
                  modifier = Modifier.testTag(GameScreenGMTestTags.START_COMBAT),
              ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Start")
              }
            } else {
              OutlinedButton(
                  onClick = onNextRound,
                  modifier = Modifier.testTag(GameScreenGMTestTags.NEXT_ROUND),
                  shape = RoundedCornerShape(50.dp),
              ) {
                Icon(Icons.Filled.SkipNext, contentDescription = null)
                Spacer(Modifier.width(4.dp))
              }
              OutlinedButton(
                  onClick = onEndCombat,
                  modifier = Modifier.testTag(GameScreenGMTestTags.END_COMBAT),
              ) {
                Icon(Icons.Filled.Stop, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("End")
              }
            }
            OutlinedButton(
                onClick = { showAddEnemyDialog = true },
                modifier = Modifier.testTag(GameScreenGMTestTags.ADD_ENEMY),
            ) {
              Icon(Icons.Filled.Add, contentDescription = null)
              Spacer(Modifier.width(4.dp))
              Text("Enemy")
            }
          }
          if (uiState.enemies.isEmpty()) {
            Text(
                "No enemies in this encounter.",
                modifier = Modifier.testTag(GameScreenGMTestTags.EMPTY_COMBAT),
            )
          } else {
            uiState.enemies.forEach { enemy ->
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
      if (uiState.isLoading) {
        item {
          Column(
              modifier = Modifier.fillMaxWidth().testTag(GameScreenGMTestTags.LOADING),
              horizontalAlignment = Alignment.CenterHorizontally,
          ) {
            CircularProgressIndicator()
            Text("Loading campaign data...")
          }
        }
      }
      uiState.error?.let { message ->
        item {
          Text(
              message,
              color = MaterialTheme.colorScheme.error,
              modifier = Modifier.testTag(GameScreenGMTestTags.ERROR),
          )
        }
      }
      if (activityVisible) {
        item {
          SectionCard("Activity Feed", onDismiss = { activityVisible = false }) {
            if (uiState.activityFeed.isEmpty()) {
              Text("No recent player activity.")
            } else {
              uiState.activityFeed.takeLast(8).reversed().forEach { activity ->
                Column {
                  Text(
                      "${activity.playerName}: ${activity.action} (${activity.diceResult})",
                      fontWeight = FontWeight.Bold,
                      color = color.onSecondary,
                  )
                  activity.diceResult?.let { Text("Dice result: $it") }
                }
              }
            }
          }
        }
      }
      item {
        SectionCard("NPCs") {
          if (uiState.npcs.isEmpty()) {
            Text("No NPCs available.")
          } else {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
              uiState.npcs.forEach { npc ->
                Column(
                    modifier = Modifier.width(92.dp).testTag(GameScreenGMTestTags.npc(npc.id)),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                  Box(
                      modifier = Modifier.size(56.dp).clip(CircleShape),
                      contentAlignment = Alignment.Center,
                  ) {
                    Icon(
                        Icons.Filled.Person,
                        contentDescription =
                            if (npc.portraitUrl.isNullOrBlank()) "No portrait"
                            else "Portrait of ${npc.name}",
                        modifier = Modifier.size(36.dp),
                    )
                  }
                  Text(npc.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                  TextButton(onClick = { selectedNpcId = npc.id }) { Text("Details") }
                }
              }
            }
          }
        }
      }
      item {
        SectionCard("Campaign Lore") {
          if (uiState.loreDocuments.isEmpty()) {
            Text("No lore documents available.")
          } else {
            uiState.loreDocuments.forEach { document ->
              Column(modifier = Modifier.testTag(GameScreenGMTestTags.lore(document.id))) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Column(modifier = Modifier.weight(1f)) {
                    Text(document.title, fontWeight = FontWeight.Bold)
                    Text(
                        document.content,
                        maxLines = if (expandedLoreId == document.id) Int.MAX_VALUE else 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                  }
                  TextButton(
                      onClick = {
                        expandedLoreId = if (expandedLoreId == document.id) null else document.id
                      }
                  ) {
                    Text(if (expandedLoreId == document.id) "Collapse" else "Read")
                  }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Text(if (document.isShared) "Shared with players" else "Private")
                  Spacer(Modifier.width(8.dp))
                  TextButton(onClick = { onSelectLore(document.id) }) {
                    Icon(Icons.Filled.Share, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Select")
                  }
                }
              }
            }
          }
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
    )
  }
  selectedNpc?.let { npc ->
    AlertDialog(
        onDismissRequest = { selectedNpcId = null },
        title = { Text(npc.name) },
        text = { Text(npc.information.ifBlank { "No information available." }) },
        confirmButton = { TextButton(onClick = { selectedNpcId = null }) { Text("Close") } },
    )
  }
}

@Composable
private fun SectionCard(
    title: String,
    onDismiss: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
  Card(
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.onBackground),
      border =
          BorderStroke(
              1.dp,
              MaterialTheme.colorScheme.onSurface,
          ),
      shape = RoundedCornerShape(24.dp),
  ) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSecondary,
        )
        onDismiss?.let {
          OutlinedButton(
              onClick = it,
              modifier = Modifier.testTag(GameScreenGMTestTags.DISMISS_ACTIVITY),
              shape = CircleShape,
          ) {
            Icon(
                Icons.Filled.Close,
                contentDescription = "Dismiss",
                tint = MaterialTheme.colorScheme.tertiary,
            )
          }
        }
      }
      content()
    }
  }
}

@Composable
private fun EnemyRow(
    enemy: Enemy,
    onIncrease: () -> Unit,
    onDecrease: () -> Unit,
    onRemove: () -> Unit,
) {
  val progress = (enemy.hp.toFloat() / enemy.maxHp.coerceAtLeast(1)).coerceIn(0f, 1f)
  Column(modifier = Modifier.fillMaxWidth().testTag(GameScreenGMTestTags.enemy(enemy.id))) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
            enemy.name,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSecondary,
        )
        Text(
            "AC 10 · Normal",
            color = MaterialTheme.colorScheme.tertiary,
        )
      }

      IconButton(
          onClick = onDecrease,
          enabled = enemy.hp > 0,
          modifier = Modifier.testTag(GameScreenGMTestTags.enemyDecrease(enemy.id)),
      ) {
        Icon(Icons.Filled.Remove, contentDescription = "Decrease HP")
      }

      Text(
          "${enemy.hp} / ${enemy.maxHp}HP",
          fontWeight = FontWeight.Bold,
      )

      IconButton(
          onClick = onIncrease,
          enabled = enemy.hp < enemy.maxHp,
          modifier = Modifier.testTag(GameScreenGMTestTags.enemyIncrease(enemy.id)),
      ) {
        Icon(Icons.Filled.Add, contentDescription = "Increase HP")
      }

      IconButton(
          onClick = onRemove,
          modifier = Modifier.testTag(GameScreenGMTestTags.enemyRemove(enemy.id)),
      ) {
        Icon(Icons.Filled.Delete, contentDescription = "Remove enemy")
      }
    }
    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
    Divider()
  }
}

@Composable
private fun AddEnemyDialog(onDismiss: () -> Unit, onAdd: (Enemy) -> Unit) {
  var name by rememberSaveable { mutableStateOf("") }
  var hp by rememberSaveable { mutableStateOf("10") }
  var maxHp by rememberSaveable { mutableStateOf("10") }
  var initiative by rememberSaveable { mutableStateOf("0") }
  Dialog(onDismissRequest = onDismiss) {
    Card(modifier = Modifier.testTag(GameScreenGMTestTags.ADD_ENEMY_DIALOG)) {
      Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Add enemy", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag(GameScreenGMTestTags.ENEMY_NAME),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          NumberField("HP", hp, { hp = it }, Modifier.weight(1f))
          NumberField("Max HP", maxHp, { maxHp = it }, Modifier.weight(1f))
          NumberField("Initiative", initiative, { initiative = it }, Modifier.weight(1f))
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
          TextButton(onClick = onDismiss) { Text("Cancel") }
          Button(
              onClick = {
                val maximum = maxHp.toIntOrNull()?.coerceAtLeast(1) ?: 10
                onAdd(
                    Enemy(
                        id = "enemy-${System.currentTimeMillis()}",
                        name = name.trim(),
                        hp = hp.toIntOrNull()?.coerceIn(0, maximum) ?: maximum,
                        maxHp = maximum,
                        initiative = initiative.toIntOrNull() ?: 0,
                    )
                )
              },
              enabled = name.isNotBlank(),
          ) {
            Text("Add")
          }
        }
      }
    }
  }
}

@Composable
private fun NumberField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier,
) {
  OutlinedTextField(
      value = value,
      onValueChange = onValueChange,
      label = { Text(label) },
      singleLine = true,
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
      modifier = modifier,
  )
}

@Composable
private fun GameGMNavigationBar(onTabSelected: (String) -> Unit) {
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
          selected = label == "Campaigns",
          onClick = { onTabSelected(label) },
          icon = { Icon(icon, contentDescription = label) },
          label = { Text(label) },
          modifier = Modifier.testTag(GameScreenGMTestTags.navigationTab(label)),
      )
    }
  }
}

private val previewEnemies =
    listOf(
        Enemy("ogre", "Ashen Ogre", 34, 59, 11),
        Enemy("cultist", "Cultist", 7, 18, 13),
        Enemy("wraith", "Wraith", 42, 42, 15),
    )

private val previewNpcs =
    listOf(
        Npc(
            "captain",
            "Captain Mira",
            "https://example.com/mira.png",
            "A stern captain who knows the old sea routes.",
        ),
        Npc("keeper", "The Archivist", null, "Keeper of the sealed campaign records."),
    )

private val previewActivities =
    listOf(
        ActivityItem("a1", "Elara", "Rolled a Dexterity check", "d20 = 18"),
        ActivityItem("a2", "Borin", "Attacked the Ashen Ogre", "d20 = 12"),
        ActivityItem("a3", "Mira", "Moved to the western gate", null),
    )

private val previewLore =
    listOf(
        LoreDocument(
            "l1",
            "The Sunken Crown",
            "The crown lies beneath the black water, guarded by a forgotten oath.",
            true,
        ),
        LoreDocument(
            "l2",
            "Old Harbor",
            "A short history of the harbor and its vanished founders.",
            false,
        ),
    )

@Preview(showBackground = true, widthDp = 420, heightDp = 900)
@Composable
private fun GameScreenGMPreview() {
  SampleAppTheme {
    GameScreenGMContent(
        uiState =
            GameViewModelGMUiState(
                enemies = previewEnemies,
                isCombatActive = true,
                currentRound = 3,
                activityFeed = previewActivities,
                npcs = previewNpcs,
                loreDocuments = previewLore,
            )
    )
  }
}

@Preview(showBackground = true, widthDp = 420, heightDp = 900, backgroundColor = 0xFF1C1816)
@Composable
private fun GameScreenGMDarkPreview() {
  SampleAppTheme(darkTheme = true) {
    GameScreenGMContent(
        uiState =
            GameViewModelGMUiState(
                enemies = previewEnemies,
                isCombatActive = true,
                currentRound = 3,
                activityFeed = previewActivities,
                npcs = previewNpcs,
                loreDocuments = previewLore,
            )
    )
  }
}

@Preview(showBackground = true, widthDp = 420, heightDp = 700)
@Composable
private fun GameScreenGMEmptyPreview() {
  SampleAppTheme { GameScreenGMContent(uiState = GameViewModelGMUiState()) }
}
