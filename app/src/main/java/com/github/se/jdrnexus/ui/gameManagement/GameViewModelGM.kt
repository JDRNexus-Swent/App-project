// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.github.se.jdrnexus.ui.gameManagement

import kotlinx.coroutines.flow.StateFlow

/** Represents an enemy participating in the live combat encounter. */
data class Enemy(
    val id: String,
    val name: String,
    val hp: Int,
    val maxHp: Int,
    val initiative: Int = 0,
)

/** Represents a Non-Player Character (NPC) in the campaign. */
data class Npc(
    val id: String,
    val name: String,
    val portraitUrl: String?,
    val information: String,
)

/** Represents an activity entry in the session activity feed. */
data class ActivityItem(
    val id: String,
    val playerName: String,
    val action: String,
    val diceResult: String?,
    val timestamp: Long = System.currentTimeMillis(),
)

/** Represents a campaign lore document accessible to the GM and optionally shared with players. */
data class LoreDocument(
    val id: String,
    val title: String,
    val content: String,
    val isShared: Boolean = false,
)

/** UI state holding the live game master session data for GameScreenGM. */
data class GameViewModelGMUiState(
    val enemies: List<Enemy> = emptyList(),
    val isCombatActive: Boolean = false,
    val currentRound: Int = 1,
    val activityFeed: List<ActivityItem> = emptyList(),
    val npcs: List<Npc> = emptyList(),
    val loreDocuments: List<LoreDocument> = emptyList(),
    val selectedLoreDocument: LoreDocument? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
)

/**
 * UI-facing contract for managing the Game Master's session, including live combat, player
 * activity, NPCs, and campaign lore control.
 */
interface GameViewModelGM {

  /** StateFlow exposing the current UI state of the GM session. */
  val uiState: StateFlow<GameViewModelGMUiState>

  // ==================== 1. Live Combat Management ====================

  /**
   * Adds an enemy to the current combat encounter.
   *
   * @param enemy The enemy to add.
   */
  fun addEnemy(enemy: Enemy)

  /**
   * Removes an enemy from the current combat encounter by its ID.
   *
   * @param enemyId The unique identifier of the enemy to remove.
   */
  fun removeEnemy(enemyId: String)

  /**
   * Increases or decreases an enemy's HP by [change].
   *
   * @param enemyId The unique identifier of the enemy.
   * @param change The amount to change HP by (positive for healing/damage adjustment, negative for
   *   damage).
   */
  fun updateEnemyHP(enemyId: String, change: Int)

  /** Advances the combat encounter to the next round. */
  fun nextRound()

  /** Starts a new combat encounter. */
  fun startCombat()

  /** Ends the current combat encounter. */
  fun endCombat()

  // ==================== 2. Activity Feed ====================

  /** Retrieves recent player actions and session activities. */
  fun loadActivityFeed()

  /** Observes real-time player actions and updates the activity feed. */
  fun observeActivityFeed()

  // ==================== 3. NPC Management ====================

  /** Retrieves the campaign's NPCs. */
  fun loadNPCs()

  /**
   * Adds a new campaign NPC.
   *
   * @param npc The NPC to add.
   */
  fun addNPC(npc: Npc)

  /**
   * Modifies information for an existing NPC.
   *
   * @param npcId The unique identifier of the NPC to update.
   * @param updatedNPC The updated NPC data.
   */
  fun updateNPC(npcId: String, updatedNPC: Npc)

  /**
   * Removes an NPC from the campaign.
   *
   * @param npcId The unique identifier of the NPC to remove.
   */
  fun removeNPC(npcId: String)

  // ==================== 4. Campaign Lore ====================

  /** Retrieves campaign lore documents. */
  fun loadLoreDocuments()

  /**
   * Selects a lore document to be displayed in detail.
   *
   * @param documentId The unique identifier of the lore document to select.
   */
  fun selectLoreDocument(documentId: String)

  /**
   * Persists modifications to a lore document.
   *
   * @param document The lore document containing modifications.
   */
  fun saveLoreDocument(document: LoreDocument)

  /**
   * Controls whether a lore document is shared and accessible to players.
   *
   * @param documentId The unique identifier of the lore document.
   * @param isShared True if accessible to players, false if restricted to GM.
   */
  fun setLoreVisibility(documentId: String, isShared: Boolean)
}
