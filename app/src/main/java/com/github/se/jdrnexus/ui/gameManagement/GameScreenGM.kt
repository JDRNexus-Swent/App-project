// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.ui.gameManagement

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.github.se.jdrnexus.model.filerepresentation.CharacterAction
import com.github.se.jdrnexus.model.filerepresentation.CharacterFile
import com.github.se.jdrnexus.model.filerepresentation.CharacterHealthPoints
import com.github.se.jdrnexus.model.filerepresentation.CharacterStat
import com.github.se.jdrnexus.resources.C
import com.github.se.jdrnexus.resources.ScreenBottomBar

/**
 * Dashboard sections the GM can hide, in the order they are listed when hidden.
 *
 * @property title Heading shown on the section card and its restore button.
 * @property key Suffix of the section's hide/restore test tags.
 */
private enum class GmSection(val title: String, val key: String) {
  COMBAT("Combat Live", "combat"),
  ACTIVITY("Activity Feed", "activity"),
  NPCS("NPCs", "npcs"),
  LORE("Campaign Lore", "lore"),
}

/**
 * Connects the GM view model to the stateless game-master session UI.
 *
 * @param viewModel Supplies session state and handles game actions.
 * @param onTabSelected Receives selections from the shared bottom navigation.
 * @param onDiceClick Opens the dice roller.
 * @param groupName Name shown for the active campaign.
 */
@Composable
fun GameScreenGM(
    viewModel: GameViewModelGM,
    onTabSelected: (String) -> Unit = {},
    onDiceClick: () -> Unit = {},
    groupName: String = "",
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
      onSelectLore = viewModel::selectLoreDocument,
      onTabSelected = onTabSelected,
      onDiceClick = onDiceClick,
  )
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
 * @param onSelectLore Selects a lore document.
 * @param onTabSelected Receives shared bottom-navigation selections.
 * @param onDiceClick Opens the dice roller.
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
    onSelectLore: (String) -> Unit = {},
    onTabSelected: (String) -> Unit = {},
    onDiceClick: () -> Unit = {},
) {
  var showAddEnemyDialog by rememberSaveable { mutableStateOf(false) }
  var selectedNpcId by rememberSaveable { mutableStateOf<String?>(null) }
  // A set of enum values is Serializable, so the hidden sections survive rotation.
  var hidden by rememberSaveable { mutableStateOf(emptySet<GmSection>()) }
  var expandedLoreId by rememberSaveable { mutableStateOf<String?>(null) }
  val selectedNpc = uiState.npcs.firstOrNull { it.id == selectedNpcId }

  Scaffold(
      bottomBar = { ScreenBottomBar(selectedTab = "Campaigns", onTabSelected = onTabSelected) },
      contentColor = MaterialTheme.colorScheme.onSurface,
      floatingActionButton = {
        FloatingActionButton(
            onClick = onDiceClick,
            modifier = Modifier.testTag(C.Tag.game_gm_dice),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ) {
          Icon(imageVector = Icons.Default.Casino, contentDescription = "Roll dice")
        }
      },
  ) { padding ->
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      item {
        Column(modifier = Modifier.padding(horizontal = 2.dp)) {
          Text(
              "Campaign · Game Master",
              color = MaterialTheme.colorScheme.tertiary,
              style = MaterialTheme.typography.labelLarge,
          )
          Text(
              groupName,
              color = MaterialTheme.colorScheme.onSecondary,
              fontFamily = FontFamily.Serif,
              fontSize = 30.sp,
              lineHeight = 34.sp,
          )
        }
      }
      if (hidden.isNotEmpty()) {
        item { HiddenSections(GmSection.entries.filter { it in hidden }) { hidden -= it } }
      }
      if (GmSection.COMBAT !in hidden) {
        item {
          SectionCard(
              GmSection.COMBAT,
              onDismiss = { hidden += GmSection.COMBAT },
              titleTrailing = "Round ${uiState.currentRound}",
          ) {
            Text(
                if (uiState.isCombatActive) "ACTIVE" else "INACTIVE",
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
              if (!uiState.isCombatActive) {
                Button(
                    onClick = onStartCombat,
                    modifier = Modifier.testTag(C.Tag.game_gm_start_combat),
                ) {
                  Icon(Icons.Filled.PlayArrow, contentDescription = null)
                  Spacer(Modifier.width(4.dp))
                  Text("Start", color = MaterialTheme.colorScheme.background)
                }
              } else {
                CombatIconButton(Icons.Filled.SkipNext, C.Tag.game_gm_next_round, onNextRound)
                CombatIconButton(Icons.Filled.Stop, C.Tag.game_gm_end_combat, onEndCombat)
              }
              CombatIconButton(Icons.Filled.Add, C.Tag.game_gm_add_enemy) {
                showAddEnemyDialog = true
              }
            }
          }
          if (uiState.enemies.isEmpty()) {
            Text(
                "No enemies in this encounter.",
                modifier = Modifier.testTag(C.Tag.game_gm_empty_combat),
                color = MaterialTheme.colorScheme.tertiary,
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
              modifier = Modifier.fillMaxWidth().testTag(C.Tag.game_gm_loading),
              horizontalAlignment = Alignment.CenterHorizontally,
          ) {
            CircularProgressIndicator()
            Text("Loading campaign data...")
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
            TextButton(onClick = onRetry) { Text("Retry") }
          }
        }
      }
      if (GmSection.ACTIVITY !in hidden) {
        item {
          SectionCard(GmSection.ACTIVITY, onDismiss = { hidden += GmSection.ACTIVITY }) {
            if (uiState.activityFeed.isEmpty()) {
              Text("No recent player activity.", color = MaterialTheme.colorScheme.tertiary)
            }
            uiState.activityFeed.takeLast(8).reversed().forEach { activity ->
              Row(
                  modifier = Modifier.testTag(C.Tag.gameGmActivity(activity.id)),
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                Text(
                    "•",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "${activity.playerName} · ${activity.action}" +
                        activity.diceResult?.let { " ($it)" }.orEmpty(),
                    color = MaterialTheme.colorScheme.onSecondary,
                )
              }
            }
          }
        }
      }
      if (GmSection.NPCS !in hidden) {
        item {
          SectionCard(GmSection.NPCS, onDismiss = { hidden += GmSection.NPCS }) {
            if (uiState.npcs.isEmpty()) {
              Text(
                  "No NPCs available.",
                  modifier = Modifier.testTag(C.Tag.game_gm_empty_npcs),
                  color = MaterialTheme.colorScheme.tertiary,
              )
            } else {
              LazyRow(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(16.dp),
              ) {
                items(uiState.npcs) { npc -> NpcItem(npc) { selectedNpcId = npc.id } }
              }
            }
          }
        }
      }
      if (GmSection.LORE !in hidden) {
        item {
          SectionCard(GmSection.LORE, onDismiss = { hidden += GmSection.LORE }) {
            if (uiState.loreDocuments.isEmpty()) {
              Text(
                  "No lore documents available.",
                  modifier = Modifier.testTag(C.Tag.game_gm_empty_lore),
                  color = MaterialTheme.colorScheme.tertiary,
              )
            } else {
              Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                uiState.loreDocuments.forEach { document ->
                  LoreRow(
                      document = document,
                      expanded = expandedLoreId == document.id,
                      onExpand = {
                        expandedLoreId = if (expandedLoreId == document.id) null else document.id
                      },
                      onSelect = { onSelectLore(document.id) },
                  )
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

/** Displays restore actions for dashboard [sections] hidden by the user. */
@Composable
private fun HiddenSections(sections: List<GmSection>, onRestore: (GmSection) -> Unit) {
  SectionCard("Hidden Sections", modifier = Modifier.testTag(C.Tag.game_gm_hidden_sections)) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      sections.forEach { section ->
        OutlinedButton(
            onClick = { onRestore(section) },
            modifier = Modifier.testTag(C.Tag.gameGmRestore(section.key)),
            contentPadding = PaddingValues(horizontal = 12.dp),
        ) {
          Text("+ ${section.title}")
        }
      }
    }
  }
}

/** Displays the card of a hideable dashboard [section]; [onDismiss] hides it. */
@Composable
private fun SectionCard(
    section: GmSection,
    onDismiss: () -> Unit,
    titleTrailing: String? = null,
    content: @Composable () -> Unit,
) =
    SectionCard(
        section.title,
        onDismiss,
        titleTrailing,
        Modifier,
        C.Tag.gameGmHide(section.key),
        content,
    )

/**
 * Displays a titled card section with optional dismissal and trailing status text.
 *
 * @param title Section heading.
 * @param onDismiss Optional callback for the section's close action.
 * @param titleTrailing Optional status text shown beside the heading.
 * @param content Content rendered inside the card.
 */
@Composable
private fun SectionCard(
    title: String,
    onDismiss: (() -> Unit)? = null,
    titleTrailing: String? = null,
    modifier: Modifier = Modifier,
    dismissTestTag: String? = null,
    content: @Composable () -> Unit,
) {
  Card(
      modifier = modifier,
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary),
      shape = RoundedCornerShape(22.dp),
  ) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onSecondary,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.headlineSmall,
            fontFamily = FontFamily.Serif,
        )
        titleTrailing?.let {
          Box(Modifier.size(8.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
          Spacer(Modifier.width(6.dp))
          Text(it, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
        }
        onDismiss?.let {
          IconButton(
              onClick = it,
              modifier = dismissTestTag?.let(Modifier::testTag) ?: Modifier,
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

/** Displays one campaign [npc] as a portrait and name; [onClick] opens its information. */
@Composable
private fun NpcItem(npc: Npc, onClick: () -> Unit) {
  Column(
      modifier =
          Modifier.width(84.dp).testTag(C.Tag.gameGmNpc(npc.id)).clickable(onClick = onClick),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    Box(
        modifier =
            Modifier.size(48.dp)
                .clip(CircleShape)
                .border(1.dp, MaterialTheme.colorScheme.tertiary, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
      Icon(
          Icons.Filled.Person,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onSecondary,
      )
    }
    Text(
        text = npc.name,
        color = MaterialTheme.colorScheme.onSecondary,
        style = MaterialTheme.typography.bodySmall,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
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
      enemy.character.actions.firstOrNull { it.actionID.startsWith("condition_") }?.name ?: "Normal"
  val hpColor =
      when {
        hpRatio >= 0.65f -> MaterialTheme.colorScheme.onSecondary
        hpRatio >= 0.35f -> MaterialTheme.colorScheme.primary
        else -> Color.Red
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
            text = "$condition · AC $armorClass",
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
            text = "/ ${enemy.maxHp} HP",
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
              contentDescription = "Remove ${enemy.name}",
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
 * Displays a lore document summary with controls to expand it or select it.
 *
 * @param document Lore document being summarized.
 * @param expanded Whether the full section text is visible.
 * @param onExpand Toggles the document's expanded state.
 * @param onSelect Selects the document for editing or review.
 */
@Composable
private fun LoreRow(
    document: LoreDocument,
    expanded: Boolean,
    onExpand: () -> Unit,
    onSelect: () -> Unit,
) {
  Column(modifier = Modifier.testTag(C.Tag.gameGmLore(document.id))) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
            document.title,
            color = MaterialTheme.colorScheme.onSecondary,
            fontWeight = FontWeight.Bold,
        )
        Text(
            document.lore.sections.joinToString("\n") { "${it.title}: ${it.description}" },
            color = MaterialTheme.colorScheme.onSecondary,
            maxLines = if (expanded) Int.MAX_VALUE else 2,
            overflow = TextOverflow.Ellipsis,
        )
      }
      TextButton(
          onClick = onExpand,
          modifier = Modifier.testTag(C.Tag.gameGmLoreExpand(document.id)),
      ) {
        Text(if (expanded) "Collapse" else "Read", color = MaterialTheme.colorScheme.primary)
      }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(
          if (document.isShared) "Shared with players" else "Private",
          color = MaterialTheme.colorScheme.onSecondary,
      )
      Spacer(Modifier.width(8.dp))
      TextButton(
          onClick = onSelect,
          modifier = Modifier.testTag(C.Tag.gameGmLoreSelect(document.id)),
      ) {
        Icon(Icons.Filled.Share, contentDescription = null)
        Spacer(Modifier.width(4.dp))
        Text("Select", color = MaterialTheme.colorScheme.primary)
      }
    }
  }
}

/**
 * Collects enemy details and submits a valid [CombatNPC] to the caller.
 *
 * @param onDismiss Closes the dialog without adding an enemy.
 * @param onAdd Receives the constructed enemy after validation.
 */
@Composable
private fun AddEnemyDialog(onDismiss: () -> Unit, onAdd: (CombatNPC) -> Unit) {
  var name by rememberSaveable { mutableStateOf("") }
  var hp by rememberSaveable { mutableStateOf("10") }
  var maxHp by rememberSaveable { mutableStateOf("10") }
  var armorClass by rememberSaveable { mutableStateOf("10") }
  var condition by rememberSaveable { mutableStateOf("") }
  val isValid =
      isValidEnemyInput(name, hp.toIntOrNull(), maxHp.toIntOrNull(), armorClass.toIntOrNull())
  Dialog(onDismissRequest = onDismiss) {
    Card(
        modifier = Modifier.testTag(C.Tag.game_gm_add_enemy_dialog),
        shape = RoundedCornerShape(20.dp),
    ) {
      Column(
          modifier = Modifier.padding(20.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Text("Add enemy", style = MaterialTheme.typography.titleLarge)
        EnemyField("Name", name, C.Tag.game_gm_enemy_name) { name = it }
        EnemyField("HP", hp, C.Tag.game_gm_enemy_hp, numeric = true) { hp = it }
        EnemyField("Max HP", maxHp, C.Tag.game_gm_enemy_max_hp, numeric = true) { maxHp = it }
        EnemyField("AC", armorClass, C.Tag.game_gm_enemy_ac, numeric = true) { armorClass = it }
        EnemyField("Condition", condition, C.Tag.game_gm_enemy_condition) { condition = it }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
          TextButton(onClick = onDismiss) { Text("Cancel") }
          // isValid guarantees the three numbers parse, so toInt() cannot throw here.
          Button(
              onClick = {
                if (isValid)
                    onAdd(newEnemy(name, hp.toInt(), maxHp.toInt(), armorClass.toInt(), condition))
              },
              enabled = isValid,
          ) {
            Text("Add")
          }
        }
      }
    }
  }
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
    label: String,
    value: String,
    testTag: String,
    numeric: Boolean = false,
    onValueChange: (String) -> Unit,
) {
  OutlinedTextField(
      value = value,
      onValueChange = onValueChange,
      label = { Text(label) },
      singleLine = true,
      keyboardOptions =
          if (numeric) KeyboardOptions(keyboardType = KeyboardType.Number)
          else KeyboardOptions.Default,
      modifier = Modifier.fillMaxWidth().testTag(testTag),
  )
}
