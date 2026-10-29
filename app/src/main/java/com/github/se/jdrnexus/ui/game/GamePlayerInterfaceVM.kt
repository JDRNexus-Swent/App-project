// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.ui.game

import com.github.se.jdrnexus.model.filerepresentation.CharacterFile
import com.github.se.jdrnexus.model.filerepresentation.LoreFile
import com.github.se.jdrnexus.model.personalSpace.JDRFile
import kotlinx.coroutines.flow.StateFlow

/**
 * A character sheet visible during the session.
 *
 * The player's own sheets are editable; the Game Master's shared sheets are read-only.
 * [hasUnsavedChanges] is true while local edits have not been saved with
 * [GamePlayerInterfaceVM.saveCharacter].
 */
data class SessionCharacter(
    val file: JDRFile,
    val character: CharacterFile,
    val isEditable: Boolean,
    val hasUnsavedChanges: Boolean = false,
)

/** A lore document made available by the Game Master. Always read-only. */
data class SessionLore(val file: JDRFile, val lore: LoreFile)

/** Observable data needed to render the player's game screen. */
data class GamePlayerUiState(
    /** The group whose session is loaded, or null before [GamePlayerInterfaceVM.loadSession]. */
    val groupId: String? = null,
    /** Visible character sheets: the player's own, then the Game Master's shared ones. */
    val characters: List<SessionCharacter> = emptyList(),
    /** Lore documents the Game Master made available to the player. */
    val lores: List<SessionLore> = emptyList(),
    /** File ID of the character shown and edited, or null when there is no character. */
    val selectedCharacterId: String? = null,
    /** Files that could not be read, keyed by file ID, with a displayable reason. */
    val unreadableFiles: Map<String, String> = emptyMap(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    /**
     * Displayable repository, validation, or permission error, cleared by the next valid action.
     */
    val error: String? = null,
) {
  val selectedCharacter: SessionCharacter?
    get() = characters.firstOrNull { it.file.id == selectedCharacterId }
}

/**
 * UI-facing contract for the player's game screen (GameScreenPlayer).
 *
 * Changes saved by other participants are reflected in [uiState] while the session is loaded. Edit
 * functions modify the selected character locally and report invalid input or read-only sheets
 * through [GamePlayerUiState.error] instead of throwing.
 */
interface GamePlayerInterfaceVM {
  /** StateFlow containing the visible characters, lore, selection, and operation status. */
  val uiState: StateFlow<GamePlayerUiState>

  /** Starts observing the files of [groupId], replacing any previous session. Also retries. */
  fun loadSession(groupId: String)

  /** Selects the character identified by [fileId] for display and editing. */
  fun selectCharacter(fileId: String)

  /** Updates the finite numeric value of the stat identified by [statID]. */
  fun updateCharacterStatValue(statID: String, value: Double)

  /** Updates the name for the action identified by [actionID]. */
  fun updateCharacterActionName(actionID: String, name: String)

  /** Updates the description for the action identified by [actionID]. */
  fun updateCharacterActionDescription(actionID: String, description: String)

  /** Updates current HP without allowing negative values or values above max HP. */
  fun updateCharacterCurrentHp(current: Int)

  /** Persists the selected character so that the other participants receive the change. */
  fun saveCharacter()

  /** Clears [GamePlayerUiState.error] after the screen has displayed it. */
  fun dismissError()
}
