// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.ui.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.se.jdrnexus.codec.JDRFileJsonCodec
import com.github.se.jdrnexus.model.filerepresentation.CharacterFile
import com.github.se.jdrnexus.model.personalSpace.CampaignGroup
import com.github.se.jdrnexus.model.personalSpace.DocumentType
import com.github.se.jdrnexus.model.personalSpace.GroupRepository
import com.github.se.jdrnexus.model.personalSpace.JDRFile
import com.github.se.jdrnexus.model.personalSpace.WorkspaceRepository
import com.github.se.jdrnexus.model.personalSpace.isContainer
import com.github.se.jdrnexus.model.repository.AuthRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Loads a group's session for a player and lets them edit and save their own character sheets.
 *
 * The session combines the signed-in user ([AuthRepository.authState]), the group
 * ([GroupRepository.getGroupFlow]) and every file placed in the group, walking the folder tree from
 * the group root ([WorkspaceRepository.getGroupFolderFiles]). Hidden files and the content of
 * hidden folders are skipped. The player's own character files are editable; the Game Master's
 * character and lore files are read-only.
 *
 * When a remote update arrives for a character with unsaved local edits, the local edits are kept
 * until [saveCharacter] succeeds; characters without unsaved edits always show the latest version.
 *
 * Firebase access remains in the repository implementations; this ViewModel depends only on
 * repository interfaces.
 */
class GamePlayerViewModel(
    private val workspaceRepository: WorkspaceRepository,
    private val groupRepository: GroupRepository,
    private val authRepository: AuthRepository,
) : ViewModel(), GamePlayerInterfaceVM {
  private val _uiState = MutableStateFlow(GamePlayerUiState())
  override val uiState: StateFlow<GamePlayerUiState> = _uiState.asStateFlow()

  /** The one active session subscription; replaced by each [loadSession]. */
  private var sessionJob: Job? = null

  override fun loadSession(groupId: String) {
    sessionJob?.cancel()
    _uiState.value = GamePlayerUiState(groupId = groupId, isLoading = true)
    sessionJob = viewModelScope.launch {
      try {
        combine(
                authRepository.authState.map { it?.uid }.distinctUntilChanged(),
                groupRepository.getGroupFlow(groupId),
                observeGroupFiles(folderId = groupId, groupId = groupId),
            ) { userId, group, files ->
              Snapshot(userId, group, files)
            }
            .collect { applySnapshot(it) }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _uiState.update {
          it.copy(isLoading = false, error = e.message?.takeIf(String::isNotBlank) ?: LOAD_ERROR)
        }
      }
    }
  }

  override fun selectCharacter(fileId: String) {
    if (_uiState.value.characters.none { it.file.id == fileId }) {
      return reportError(CHARACTER_NOT_FOUND_ERROR)
    }
    _uiState.update { it.copy(selectedCharacterId = fileId, error = null) }
  }

  override fun updateCharacterStatValue(statID: String, value: Double) {
    mutateCharacter { character ->
      when {
        !value.isFinite() -> reject(STAT_VALUE_ERROR)
        character.stats.none { it.statID == statID } -> reject(STAT_NOT_FOUND_ERROR)
        else ->
            character.copy(
                stats =
                    character.stats.map { if (it.statID == statID) it.copy(value = value) else it }
            )
      }
    }
  }

  override fun updateCharacterActionName(actionID: String, name: String) {
    mutateCharacter { character ->
      when {
        character.actions.none { it.actionID == actionID } -> reject(ACTION_NOT_FOUND_ERROR)
        name.isBlank() -> reject(ACTION_NAME_ERROR)
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

  override fun updateCharacterCurrentHp(current: Int) {
    mutateCharacter { character ->
      when {
        current < 0 -> reject(CURRENT_HP_ERROR)
        current > character.hp.max -> reject(CURRENT_HP_EXCEEDS_MAX_ERROR)
        else -> character.copy(hp = character.hp.copy(current = current))
      }
    }
  }

  override fun saveCharacter() {
    val state = _uiState.value
    if (state.isSaving) return
    val selected = state.selectedCharacter ?: return reportError(CHARACTER_REQUIRED_ERROR)
    if (!selected.isEditable) return reportError(READ_ONLY_ERROR)

    val content =
        try {
          JDRFileJsonCodec.serializeCharacter(selected.character)
        } catch (e: Exception) {
          return reportError(e.message?.takeIf(String::isNotBlank) ?: SAVE_ERROR)
        }
    val updatedFile = selected.file.copy(content = content)

    _uiState.update { it.copy(isSaving = true, error = null) }
    viewModelScope.launch {
      try {
        workspaceRepository.updateFile(updatedFile).getOrThrow()
        _uiState.update { current ->
          current.copy(
              isSaving = false,
              characters =
                  current.characters.map {
                    // Edits made while saving stay unsaved.
                    if (it.file.id == updatedFile.id && it.character == selected.character) {
                      it.copy(file = updatedFile, hasUnsavedChanges = false)
                    } else it
                  },
          )
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _uiState.update {
          it.copy(isSaving = false, error = e.message?.takeIf(String::isNotBlank) ?: SAVE_ERROR)
        }
      } finally {
        _uiState.update { current ->
          if (current.isSaving) current.copy(isSaving = false) else current
        }
      }
    }
  }

  override fun dismissError() {
    _uiState.update { it.copy(error = null) }
  }

  /** Emits every visible, non-folder file below [folderId], following nested group folders. */
  @OptIn(ExperimentalCoroutinesApi::class)
  private fun observeGroupFiles(folderId: String, groupId: String): Flow<List<JDRFile>> =
      workspaceRepository.getGroupFolderFiles(folderId, groupId).flatMapLatest { children ->
        val visible = children.filterNot { it.placements[groupId]?.isHidden == true }
        val files = visible.filterNot { it.isContainer() }
        val folders = visible.filter { it.isContainer() && it.id != folderId }
        if (folders.isEmpty()) flowOf(files)
        else
            combine(folders.map { observeGroupFiles(it.id, groupId) }) { nested ->
              files + nested.flatMap { it }
            }
      }

  private data class Snapshot(
      val userId: String?,
      val group: CampaignGroup?,
      val files: List<JDRFile>,
  )

  /** Maps one combined repository emission into screen state, keeping unsaved local edits. */
  private fun applySnapshot(snapshot: Snapshot) {
    val (userId, group, files) = snapshot
    if (userId == null) return failSession(SIGNED_OUT_ERROR)
    if (group == null) return failSession(GROUP_NOT_FOUND_ERROR)

    _uiState.update { current ->
      val localEdits = current.characters.filter { it.hasUnsavedChanges }.associateBy { it.file.id }
      val unreadable = mutableMapOf<String, String>()
      val ownCharacters = mutableListOf<SessionCharacter>()
      val sharedCharacters = mutableListOf<SessionCharacter>()
      val lores = mutableListOf<SessionLore>()
      files.forEach { file ->
        val isOwn = file.ownerId == userId
        val isGameMaster = file.ownerId == group.gameMasterId
        try {
          when {
            file.type == DocumentType.CHARACTER && (isOwn || isGameMaster) -> {
              val character =
                  localEdits[file.id]
                      ?: SessionCharacter(
                          file = file,
                          character = JDRFileJsonCodec.deserializeCharacter(file.content),
                          isEditable = isOwn,
                      )
              if (isOwn) ownCharacters += character else sharedCharacters += character
            }
            file.type == DocumentType.TEXT && isGameMaster ->
                lores += SessionLore(file, JDRFileJsonCodec.deserializeLore(file.content))
          }
        } catch (e: Exception) {
          unreadable[file.id] = e.message?.takeIf(String::isNotBlank) ?: UNREADABLE_FILE_ERROR
        }
      }

      val characters = ownCharacters + sharedCharacters
      val selectedId =
          current.selectedCharacterId?.takeIf { id -> characters.any { it.file.id == id } }
              ?: characters.firstOrNull()?.file?.id
      current.copy(
          characters = characters,
          lores = lores,
          selectedCharacterId = selectedId,
          unreadableFiles = unreadable,
          isLoading = false,
          error =
              current.error?.takeUnless { it == SIGNED_OUT_ERROR || it == GROUP_NOT_FOUND_ERROR },
      )
    }
  }

  private fun failSession(message: String) {
    _uiState.update {
      GamePlayerUiState(groupId = it.groupId, isSaving = it.isSaving, error = message)
    }
  }

  private fun mutateCharacter(transform: (CharacterFile) -> CharacterFile?) {
    val state = _uiState.value
    val selected = state.selectedCharacter ?: return reportError(CHARACTER_REQUIRED_ERROR)
    if (!selected.isEditable) return reportError(READ_ONLY_ERROR)
    val updated = transform(selected.character) ?: return
    _uiState.value =
        state.copy(
            characters =
                state.characters.map {
                  if (it.file.id == selected.file.id) {
                    it.copy(character = updated, hasUnsavedChanges = true)
                  } else it
                },
            error = null,
        )
  }

  private fun reject(message: String): Nothing? {
    reportError(message)
    return null
  }

  private fun reportError(message: String) {
    _uiState.update { it.copy(error = message) }
  }

  internal companion object {
    const val ACTION_NAME_ERROR = "Action name cannot be empty."
    const val ACTION_NOT_FOUND_ERROR = "Action not found."
    const val CHARACTER_NOT_FOUND_ERROR = "Character not found."
    const val CHARACTER_REQUIRED_ERROR = "Select a character before editing."
    const val CURRENT_HP_ERROR = "Current HP must be a non-negative number."
    const val CURRENT_HP_EXCEEDS_MAX_ERROR = "Current HP cannot exceed maximum HP."
    const val GROUP_NOT_FOUND_ERROR = "This group no longer exists."
    const val LOAD_ERROR = "Unable to load this session."
    const val READ_ONLY_ERROR = "This character sheet is read-only."
    const val SAVE_ERROR = "Unable to save this character."
    const val SIGNED_OUT_ERROR = "You must be signed in to join a session."
    const val STAT_NOT_FOUND_ERROR = "Stat not found."
    const val STAT_VALUE_ERROR = "Stat value must be a finite number."
    const val UNREADABLE_FILE_ERROR = "Unable to read this file."
  }
}
