// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.github.se.jdrnexus.ui.jdrfile

import com.github.se.jdrnexus.model.filerepresentation.CharacterFile
import com.github.se.jdrnexus.model.filerepresentation.LoreFile
import com.github.se.jdrnexus.model.personalSpace.JDRFile
import kotlinx.coroutines.flow.StateFlow

/** Field identity for errors that belong beside an editable value in the screen. */
sealed interface EditFileField {
  data class CharacterStatName(val statID: String) : EditFileField

  data class CharacterStatValue(val statID: String) : EditFileField

  data object CharacterCurrentHp : EditFileField

  data object CharacterMaxHp : EditFileField

  data class CharacterActionName(val actionID: String) : EditFileField

  data class LoreSectionTitle(val sectionID: String) : EditFileField
}

/** Observable data needed to render and report progress or errors in the edit screen. */
data class EditFileUiState(
    val file: JDRFile? = null,
    val character: CharacterFile? = null,
    val lore: LoreFile? = null,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val error: String? = null,
    val fieldErrors: Map<EditFileField, String> = emptyMap(),
)

/** UI-facing contract for editing and saving a character or lore file. */
interface EditFileInterfaceVM {
  /** StateFlow containing the selected file, parsed editor model, and operation status. */
  val uiState: StateFlow<EditFileUiState>

  /** Loads [fileId] and selects the editor model from its repository document type. */
  fun loadFile(fileId: String)

  /** Replaces the character's display name. */
  fun updateCharacterName(name: String)

  /** Updates current HP without allowing negative values or values above max HP. */
  fun updateCharacterCurrentHp(current: Int)

  /** Updates max HP without allowing negative values, clamping current HP when necessary. */
  fun updateCharacterMaxHp(max: Int)

  /** Adds a named stat under a generated ID if [value] is finite. */
  fun addCharacterStat(name: String, value: Double)

  /** Updates the name of the stat identified by [statID]. */
  fun updateCharacterStatName(statID: String, name: String)

  /** Updates the finite numeric value of the stat identified by [statID]. */
  fun updateCharacterStatValue(statID: String, value: Double)

  /** Removes the stat identified by [statID]. */
  fun removeCharacterStat(statID: String)

  /** Adds a named action under a generated ID. */
  fun addCharacterAction(name: String, description: String)

  /** Updates the name for the action identified by [actionID]. */
  fun updateCharacterActionName(actionID: String, name: String)

  /** Updates the description for the action identified by [actionID]. */
  fun updateCharacterActionDescription(actionID: String, description: String)

  /** Removes the action identified by [actionID]. */
  fun removeCharacterAction(actionID: String)

  /** Replaces the current lore title. */
  fun updateLoreTitle(title: String)

  /** Adds a lore section with a generated stable ID. */
  fun addLoreSection(title: String, description: String)

  /** Updates the title of the lore section identified by [sectionID]. */
  fun updateLoreSectionTitle(sectionID: String, title: String)

  /** Updates the description of the lore section identified by [sectionID]. */
  fun updateLoreSectionDescription(sectionID: String, description: String)

  /** Removes the lore section identified by [sectionID]. */
  fun removeLoreSection(sectionID: String)

  /** Validates and persists the current editor content to the loaded repository file. */
  fun saveJDRfile()
}
