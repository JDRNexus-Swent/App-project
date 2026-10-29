// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.ui.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.se.jdrnexus.codec.JDRFileJsonCodec
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
 * Loads a group's session for a player and lets them edit and save their own character sheet.
 *
 * The session combines the signed-in user ([AuthRepository.authState]), the group
 * ([GroupRepository.getGroupFlow]) and every file placed in the group, walking the folder tree from
 * the group root ([WorkspaceRepository.getGroupFolderFiles]). Hidden files and the content of
 * hidden folders are skipped. The first readable character file owned by the player is their
 * character; the Game Master's lore files are shown read-only.
 *
 * When a remote update arrives while the character has unsaved local edits, the local edits are
 * kept until [saveCharacter] succeeds; otherwise the character always shows the latest version.
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

  override fun updateCharacterHp(current: Int) {
    val state = _uiState.value
    val sheet = state.character ?: return reportError(CHARACTER_REQUIRED_ERROR)
    val hp = sheet.character.hp
    val hpError =
        when {
          current < 0 -> CURRENT_HP_ERROR
          current > hp.max -> CURRENT_HP_EXCEEDS_MAX_ERROR
          else -> null
        }
    if (hpError != null) return _uiState.update { it.copy(hpError = hpError) }

    _uiState.value =
        state.copy(
            character =
                sheet.copy(
                    character = sheet.character.copy(hp = hp.copy(current = current)),
                    hasUnsavedChanges = true,
                ),
            error = null,
            hpError = null,
        )
  }

  override fun saveCharacter() {
    val state = _uiState.value
    if (state.isSaving) return
    val selected = state.character ?: return reportError(CHARACTER_REQUIRED_ERROR)

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
              character =
                  current.character?.let {
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
      val localEdit = current.character?.takeIf { it.hasUnsavedChanges }
      val unreadable = mutableMapOf<String, String>()
      var character: SessionCharacter? = null
      val lores = mutableListOf<SessionLore>()
      for (file in files) {
        try {
          when {
            file.type == DocumentType.CHARACTER && file.ownerId == userId && character == null ->
                character =
                    localEdit?.takeIf { it.file.id == file.id }
                        ?: SessionCharacter(
                            file,
                            JDRFileJsonCodec.deserializeCharacter(file.content),
                        )
            file.type == DocumentType.TEXT && file.ownerId == group.gameMasterId ->
                lores += SessionLore(file, JDRFileJsonCodec.deserializeLore(file.content))
          }
        } catch (e: Exception) {
          unreadable[file.id] = e.message?.takeIf(String::isNotBlank) ?: UNREADABLE_FILE_ERROR
        }
      }

      current.copy(
          character = character,
          lores = lores,
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

  private fun reportError(message: String) {
    _uiState.update { it.copy(error = message) }
  }

  internal companion object {
    const val CHARACTER_REQUIRED_ERROR = "You have no character in this session."
    const val CURRENT_HP_ERROR = "Current HP must be a non-negative number."
    const val CURRENT_HP_EXCEEDS_MAX_ERROR = "Current HP cannot exceed maximum HP."
    const val GROUP_NOT_FOUND_ERROR = "This group no longer exists."
    const val LOAD_ERROR = "Unable to load this session."
    const val SAVE_ERROR = "Unable to save this character."
    const val SIGNED_OUT_ERROR = "You must be signed in to join a session."
    const val UNREADABLE_FILE_ERROR = "Unable to read this file."
  }
}
