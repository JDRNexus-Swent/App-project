// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.ui.game

import com.github.se.jdrnexus.model.filerepresentation.CharacterFile
import com.github.se.jdrnexus.model.filerepresentation.LoreFile
import com.github.se.jdrnexus.model.personalSpace.JDRFile
import kotlinx.coroutines.flow.StateFlow

/**
 * The player's character sheet in the session.
 *
 * [hasUnsavedChanges] is true while local edits have not been saved with
 * [GamePlayerInterfaceVM.saveCharacter].
 */
data class SessionCharacter(
    val file: JDRFile,
    val character: CharacterFile,
    val hasUnsavedChanges: Boolean = false,
)

/** A lore document made available by the Game Master. Always read-only. */
data class SessionLore(val file: JDRFile, val lore: LoreFile)

/** Observable data needed to render the player's game screen. */
data class GamePlayerUiState(
    /** The group whose session is loaded, or null before [GamePlayerInterfaceVM.loadSession]. */
    val groupId: String? = null,
    /** The player's own character sheet in the group, or null when they have none. */
    val character: SessionCharacter? = null,
    /** Lore documents the Game Master made available to the player. */
    val lores: List<SessionLore> = emptyList(),
    /** Files that could not be read, keyed by file ID, with a displayable reason. */
    val unreadableFiles: Map<String, String> = emptyMap(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    /** Displayable repository or validation error, cleared by the next valid action. */
    val error: String? = null,
    /** Displayable reason why the last HP update was rejected, cleared by the next valid one. */
    val hpError: String? = null,
)

/**
 * UI-facing contract for the player's game screen (GameScreenPlayer).
 *
 * Changes saved by other participants are reflected in [uiState] while the session is loaded. Edit
 * functions modify the player's character locally and report invalid input through
 * [GamePlayerUiState.error] instead of throwing.
 */
interface GamePlayerInterfaceVM {
  /** StateFlow containing the player's character, lore, and operation status. */
  val uiState: StateFlow<GamePlayerUiState>

  /** Starts observing the files of [groupId], replacing any previous session. Also retries. */
  fun loadSession(groupId: String)

  /**
   * Updates the player's character's [current] HP. Rejects a negative value or one above max HP by
   * setting [GamePlayerUiState.hpError] instead of throwing.
   */
  fun updateCharacterHp(current: Int)

  /** Persists the player's character so that the other participants receive the change. */
  fun saveCharacter()

  /** Clears [GamePlayerUiState.error] after the screen has displayed it. */
  fun dismissError()
}
