// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.github.se.jdrnexus.model.gameManagement

import com.github.se.jdrnexus.model.filerepresentation.CharacterFile
import com.github.se.jdrnexus.model.filerepresentation.LoreFile
import kotlinx.coroutines.flow.Flow

/** An enemy whose combat state is synchronized with the campaign. */
data class GameCombatNPC(
    val id: String,
    val character: CharacterFile,
    val initiative: Int,
)

/** A campaign NPC whose information is synchronized with the campaign. */
data class GameNpc(
    val id: String,
    val name: String,
    val portraitUrl: String?,
    val information: String,
)

/** Persisted combat and NPC state for one campaign. */
data class GameSessionSnapshot(
    val enemies: List<GameCombatNPC> = emptyList(),
    val isCombatActive: Boolean = false,
    val currentRound: Int = 1,
    val npcs: List<GameNpc> = emptyList(),
) {
  /** Checks the invariants required before this session can be persisted or used by the UI. */
  fun validate() {
    require(currentRound >= 1) { "Combat round must be at least one." }
    require(enemies.map { it.id }.toSet().size == enemies.size) { "Enemy IDs must be unique." }
    enemies.forEach { enemy ->
      require(enemy.id.isNotBlank() && enemy.character.name.isNotBlank()) {
        "Enemy ID and name are required."
      }
      require(
          enemy.character.hp.max > 0 && enemy.character.hp.current in 0..enemy.character.hp.max
      ) {
        "Enemy HP must be between zero and max HP."
      }
      require(
          enemy.character.stats
              .filter { it.statID == "armorClass" }
              .all { it.value.isFinite() && it.value >= 0.0 }
      ) {
        "Enemy AC must be finite and non-negative."
      }
    }
    require(npcs.map { it.id }.toSet().size == npcs.size) { "NPC IDs must be unique." }
    npcs.forEach { npc ->
      require(npc.id.isNotBlank() && npc.name.isNotBlank()) { "NPC ID and name are required." }
    }
  }
}

/** A lore document with visibility scoped to its campaign. */
data class GameLoreEntry(
    val id: String,
    val lore: LoreFile,
    val isShared: Boolean,
)

/** A player action or dice roll shown in the campaign's shared activity feed. */
data class GameActivityEntry(
    val id: String,
    val userId: String,
    val playerName: String,
    val action: String,
    val diceResult: String?,
    val timestamp: Long,
)

/**
 * Loads and persists the GM-managed part of a campaign.
 *
 * Implementations must expose participant-readable live data and enforce that only the campaign's
 * Game Master can perform writes. Write methods return failures rather than silently accepting
 * invalid or unauthorized updates.
 */
interface GameSessionRepository {
  /** Observes the campaign's current combat and NPC state until collection is cancelled. */
  fun observeSession(groupId: String): Flow<GameSessionSnapshot>

  /**
   * Saves the complete combat and NPC snapshot.
   *
   * @return success after the write completes, or failure if validation, permissions, or storage
   *   rejects it.
   */
  suspend fun saveSession(groupId: String, session: GameSessionSnapshot): Result<Unit>

  /** Observes lore documents available in a campaign. */
  fun observeLore(groupId: String): Flow<List<GameLoreEntry>>

  /** Observes only lore documents shared with participants. */
  fun observeSharedLore(groupId: String): Flow<List<GameLoreEntry>>

  /** Saves a lore document; an existing document with the same ID is replaced. */
  suspend fun saveLore(groupId: String, lore: GameLoreEntry): Result<Unit>

  /** Changes whether a lore document is available to campaign participants. */
  suspend fun setLoreVisibility(
      groupId: String,
      documentId: String,
      isShared: Boolean,
  ): Result<Unit>

  /** Retrieves recent player actions and dice rolls for the activity feed. */
  suspend fun loadActivities(groupId: String): Result<List<GameActivityEntry>>

  /** Observes player actions and dice rolls as they are written. */
  fun observeActivities(groupId: String): Flow<List<GameActivityEntry>>

  /**
   * Records an action performed by a campaign participant.
   *
   * Implementations must verify that [userId] belongs to [groupId] and reject writes on behalf of
   * another user.
   */
  suspend fun recordActivity(
      groupId: String,
      userId: String,
      playerName: String,
      action: String,
      diceResult: String? = null,
  ): Result<Unit>
}
