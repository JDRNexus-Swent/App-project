// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.github.se.jdrnexus.ui.jdrfile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.se.jdrnexus.codec.JDRFileJsonCodec
import com.github.se.jdrnexus.model.filerepresentation.CharacterAction
import com.github.se.jdrnexus.model.filerepresentation.CharacterFile
import com.github.se.jdrnexus.model.filerepresentation.CharacterStat
import com.github.se.jdrnexus.model.filerepresentation.LoreFile
import com.github.se.jdrnexus.model.filerepresentation.LoreSection
import com.github.se.jdrnexus.model.personalSpace.DocumentType
import com.github.se.jdrnexus.model.personalSpace.WorkspaceRepository
import com.github.se.jdrnexus.model.personalSpace.WorkspaceRepositoryProvider
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class EditFileViewModel(
    private val repository: WorkspaceRepository = WorkspaceRepositoryProvider.repository
) : ViewModel(), EditFileInterfaceVM {
  private val _uiState = MutableStateFlow(EditFileUiState())
  override val uiState: StateFlow<EditFileUiState> = _uiState.asStateFlow()

  private var loadJob: Job? = null

  override fun loadFile(fileId: String) {
    loadJob?.cancel()
    _uiState.value = EditFileUiState(isLoading = true)
    loadJob = viewModelScope.launch {
      val file =
          try {
            repository.getFile(fileId).getOrThrow()
          } catch (e: CancellationException) {
            throw e
          } catch (e: Exception) {
            _uiState.value =
                EditFileUiState(error = e.message?.takeIf(String::isNotBlank) ?: LOAD_ERROR)
            return@launch
          }

      if (file == null) {
        _uiState.value = EditFileUiState(error = FILE_NOT_FOUND)
        return@launch
      }

      try {
        val (character, lore) =
            when (file.type) {
              DocumentType.CHARACTER -> JDRFileJsonCodec.deserializeCharacter(file.content) to null
              DocumentType.TEXT -> null to JDRFileJsonCodec.deserializeLore(file.content)
              DocumentType.FOLDER -> throw IllegalArgumentException(FOLDER_ERROR)
            }
        _uiState.value = EditFileUiState(file = file, character = character, lore = lore)
      } catch (e: CancellationException) {
        throw e
      } catch (e: IllegalArgumentException) {
        _uiState.value =
            EditFileUiState(
                file = file,
                error = e.message?.takeIf(String::isNotBlank) ?: INVALID_JSON_ERROR,
            )
      } catch (_: Exception) {
        _uiState.value = EditFileUiState(file = file, error = INVALID_JSON_ERROR)
      }
    }
  }

  override fun updateCharacterName(name: String) {
    mutateCharacter { it.copy(name = name) }
  }

  override fun addCharacterStat(name: String, value: Double) {
    val statID = UUID.randomUUID().toString()
    mutateCharacter { character ->
      when {
        !value.isFinite() -> rejectField(EditFileField.CharacterStatValue(statID), STAT_VALUE_ERROR)
        name.isBlank() -> rejectField(EditFileField.CharacterStatName(statID), STAT_NAME_ERROR)
        else ->
            character.copy(
                stats = character.stats + CharacterStat(statID = statID, name = name, value = value)
            )
      }
    }
  }

  override fun updateCharacterStatName(statID: String, name: String) {
    mutateCharacter { character ->
      when {
        character.stats.none { it.statID == statID } -> reject(STAT_NOT_FOUND_ERROR)
        name.isBlank() -> rejectField(EditFileField.CharacterStatName(statID), STAT_NAME_ERROR)
        else ->
            character.copy(
                stats =
                    character.stats.map { if (it.statID == statID) it.copy(name = name) else it }
            )
      }
    }
  }

  override fun updateCharacterStatValue(statID: String, value: Double) {
    mutateCharacter { character ->
      when {
        !value.isFinite() -> rejectField(EditFileField.CharacterStatValue(statID), STAT_VALUE_ERROR)
        character.stats.none { it.statID == statID } -> reject(STAT_NOT_FOUND_ERROR)
        else ->
            character.copy(
                stats =
                    character.stats.map { if (it.statID == statID) it.copy(value = value) else it }
            )
      }
    }
  }

  override fun removeCharacterStat(statID: String) {
    mutateCharacter { character ->
      if (character.stats.none { it.statID == statID }) reject(STAT_NOT_FOUND_ERROR)
      else character.copy(stats = character.stats.filterNot { it.statID == statID })
    }
  }

  override fun addCharacterAction(name: String, description: String) {
    val actionID = UUID.randomUUID().toString()
    mutateCharacter { character ->
      when {
        name.isBlank() ->
            rejectField(EditFileField.CharacterActionName(actionID), ACTION_NAME_ERROR)
        else ->
            character.copy(
                actions = character.actions + CharacterAction(actionID, name, description)
            )
      }
    }
  }

  override fun updateCharacterActionName(actionID: String, name: String) {
    mutateCharacter { character ->
      when {
        character.actions.none { it.actionID == actionID } -> reject(ACTION_NOT_FOUND_ERROR)
        name.isBlank() ->
            rejectField(EditFileField.CharacterActionName(actionID), ACTION_NAME_ERROR)
        else ->
            character.copy(
                actions =
                    character.actions.map {
                      if (it.actionID == actionID) it.copy(name = name) else it
                    }
            )
      }
    }
  }

  override fun updateCharacterActionDescription(actionID: String, description: String) {
    mutateCharacter { character ->
      if (character.actions.none { it.actionID == actionID }) reject(ACTION_NOT_FOUND_ERROR)
      else {
        character.copy(
            actions =
                character.actions.map {
                  if (it.actionID == actionID) it.copy(description = description) else it
                }
        )
      }
    }
  }

  override fun removeCharacterAction(actionID: String) {
    mutateCharacter { character ->
      if (character.actions.none { it.actionID == actionID }) reject(ACTION_NOT_FOUND_ERROR)
      else character.copy(actions = character.actions.filterNot { it.actionID == actionID })
    }
  }

  override fun updateLoreTitle(title: String) {
    mutateLore { it.copy(title = title) }
  }

  override fun addLoreSection(title: String, description: String) {
    val sectionID = UUID.randomUUID().toString()
    mutateLore { lore ->
      when {
        title.isBlank() ->
            rejectField(EditFileField.LoreSectionTitle(sectionID), SECTION_TITLE_ERROR)
        else -> lore.copy(sections = lore.sections + LoreSection(sectionID, title, description))
      }
    }
  }

  override fun updateLoreSectionTitle(sectionID: String, title: String) {
    updateLoreSection(sectionID) { it.copy(title = title) }
  }

  override fun updateLoreSectionDescription(sectionID: String, description: String) {
    updateLoreSection(sectionID) { it.copy(description = description) }
  }

  override fun removeLoreSection(sectionID: String) {
    mutateLore { lore ->
      if (lore.sections.none { it.sectionID == sectionID }) reject(SECTION_NOT_FOUND_ERROR)
      else lore.copy(sections = lore.sections.filterNot { it.sectionID == sectionID })
    }
  }

  override fun saveJDRfile() {
    val state = _uiState.value
    if (state.isSaving) return

    val generalValidationError = validateForSave(state)
    if (generalValidationError != null) {
      reportError(generalValidationError)
      return
    }
    val fieldErrors = collectFieldErrors(state)
    if (fieldErrors.isNotEmpty()) {
      _uiState.update { it.copy(error = null, isSaved = false, fieldErrors = fieldErrors) }
      return
    }

    val file = state.file ?: return reportError(LOAD_BEFORE_SAVE_ERROR)
    val content =
        try {
          when (file.type) {
            DocumentType.CHARACTER -> JDRFileJsonCodec.serializeCharacter(state.character)
            DocumentType.TEXT -> JDRFileJsonCodec.serializeLore(state.lore)
            DocumentType.FOLDER -> return reportError(FOLDER_ERROR)
          }
        } catch (e: Exception) {
          return reportError(e.message?.takeIf(String::isNotBlank) ?: SAVE_ERROR)
        }

    val updatedFile = file.copy(content = content)
    _uiState.update { it.copy(isSaving = true, isSaved = false, error = null) }
    viewModelScope.launch {
      try {
        repository.updateFile(updatedFile).getOrThrow()
        _uiState.update { current ->
          current.copy(
              file = updatedFile,
              isSaving = false,
              isSaved = current.character == state.character && current.lore == state.lore,
              error = null,
          )
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _uiState.update {
          it.copy(
              isSaving = false,
              isSaved = false,
              error = e.message?.takeIf(String::isNotBlank) ?: SAVE_ERROR,
          )
        }
      }
    }
  }

  private fun updateLoreSection(sectionID: String, transform: (LoreSection) -> LoreSection) {
    mutateLore { lore ->
      val sectionIndex = lore.sections.indexOfFirst { it.sectionID == sectionID }
      if (sectionIndex < 0) reject(SECTION_NOT_FOUND_ERROR)
      else {
        lore.copy(
            sections =
                lore.sections.mapIndexed { index, section ->
                  if (index == sectionIndex) transform(section) else section
                }
        )
      }
    }
  }

  private fun mutateCharacter(transform: (CharacterFile) -> CharacterFile?) {
    val state = _uiState.value
    val character = state.character ?: return reportError(CHARACTER_REQUIRED_ERROR)
    val updated = transform(character) ?: return
    val newState = state.copy(character = updated, isSaved = false, error = null)
    _uiState.value = newState.copy(fieldErrors = collectFieldErrors(newState))
  }

  private fun mutateLore(transform: (LoreFile) -> LoreFile?) {
    val state = _uiState.value
    val lore = state.lore ?: return reportError(LORE_REQUIRED_ERROR)
    val updated = transform(lore) ?: return
    val newState = state.copy(lore = updated, isSaved = false, error = null)
    _uiState.value = newState.copy(fieldErrors = collectFieldErrors(newState))
  }

  private fun reject(message: String): Nothing? {
    reportError(message)
    return null
  }

  private fun rejectField(field: EditFileField, message: String): Nothing? {
    _uiState.update { current ->
      current.copy(
          error = null,
          isSaved = false,
          fieldErrors = mapOf(field to message),
      )
    }
    return null
  }

  private fun validateForSave(state: EditFileUiState): String? {
    val file = state.file ?: return LOAD_BEFORE_SAVE_ERROR
    return when (file.type) {
      DocumentType.CHARACTER -> if (state.character == null) CHARACTER_REQUIRED_ERROR else null
      DocumentType.TEXT -> if (state.lore == null) LORE_REQUIRED_ERROR else null
      DocumentType.FOLDER -> FOLDER_ERROR
    }
  }

  private fun collectFieldErrors(state: EditFileUiState): Map<EditFileField, String> = buildMap {
    state.character?.let { character ->
      character.stats.forEach { stat ->
        if (stat.name.isBlank()) {
          put(EditFileField.CharacterStatName(stat.statID), STAT_NAME_ERROR)
        }
        if (!stat.value.isFinite()) {
          put(EditFileField.CharacterStatValue(stat.statID), STAT_VALUE_ERROR)
        }
      }
      character.actions.forEach { action ->
        if (action.name.isBlank()) {
          put(EditFileField.CharacterActionName(action.actionID), ACTION_NAME_ERROR)
        }
      }
    }
    state.lore?.let { lore ->
      lore.sections.forEach { section ->
        if (section.title.isBlank()) {
          put(EditFileField.LoreSectionTitle(section.sectionID), SECTION_TITLE_ERROR)
        }
      }
    }
  }

  private fun reportError(message: String) {
    _uiState.update { it.copy(error = message, isSaved = false) }
  }

  private companion object {
    const val ACTION_NOT_FOUND_ERROR = "Action not found."
    const val ACTION_NAME_ERROR = "Action name cannot be empty."
    const val CHARACTER_REQUIRED_ERROR = "Load a character file before editing."
    const val FILE_NOT_FOUND = "File not found."
    const val FOLDER_ERROR = "Folders cannot be edited as files."
    const val INVALID_JSON_ERROR = "Unable to read this file. Check its JSON format."
    const val LOAD_BEFORE_SAVE_ERROR = "Load a file before saving."
    const val LOAD_ERROR = "Unable to load this file."
    const val LORE_REQUIRED_ERROR = "Load a lore file before editing."
    const val SAVE_ERROR = "Unable to save this file."
    const val SECTION_NOT_FOUND_ERROR = "Section not found."
    const val SECTION_TITLE_ERROR = "Section title cannot be empty."
    const val STAT_NOT_FOUND_ERROR = "Stat not found."
    const val STAT_NAME_ERROR = "Stat name cannot be empty."
    const val STAT_VALUE_ERROR = "Stat value must be a finite number."
  }
}
