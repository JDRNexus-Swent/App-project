package com.github.se.jdrnexus.ui.groupWorkSpace

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.se.jdrnexus.model.personalSpace.DocumentType
import com.github.se.jdrnexus.model.personalSpace.JDRFile
import com.github.se.jdrnexus.model.personalSpace.WorkspaceRepository
import com.github.se.jdrnexus.model.personalSpace.WorkspaceRepositoryProvider
import com.github.se.jdrnexus.model.repository.AuthRepository
import com.github.se.jdrnexus.model.repository.AuthRepositoryFirebase
import com.github.se.jdrnexus.viewmodel.LoadState
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class GroupWorkspaceRole {
  GAME_MASTER,
  PLAYER,
}

data class GroupWorkspaceUiState(
    val currentUserId: String = "",
    val items: List<JDRFile> = emptyList(),
    val currentFolderId: String = "",
    val loadState: LoadState = LoadState.Loading,
    val isOperationInProgress: Boolean = false,
    val errorMessage: String? = "",
    val canCreateFolder: Boolean = false,
    val canAddFile: Boolean = false,
    val canEditItems: Boolean = false,
)

class GroupWorkspaceViewModel(
    private val groupId: String,
    private val role: GroupWorkspaceRole,
    private val repository: WorkspaceRepository = WorkspaceRepositoryProvider.repository,
    private val authRepository: AuthRepository = AuthRepositoryFirebase(),
) : ViewModel() {

  private val _uiState =
      MutableStateFlow(
          GroupWorkspaceUiState(
              canCreateFolder = role == GroupWorkspaceRole.GAME_MASTER,
              canAddFile = true,
              canEditItems = role == GroupWorkspaceRole.GAME_MASTER,
          )
      )

  val uiState: StateFlow<GroupWorkspaceUiState> = _uiState.asStateFlow()

  // Keeps track of the coroutine currently loading the workspace.
  private var fetchJob: Job? = null
  private var fetchGeneration = 0L

  // Prevents two workspace modifications from running their critical sections simultaneously within
  // this ViewModel.
  private val operationMutex = Mutex()

  // Counts how many workspace operations have started but have not yet finished.
  private val pendingOperations = AtomicInteger(0)

  private suspend fun requireUid(): String =
      authRepository.authState.first()?.uid ?: throw IllegalStateException("You must be signed in.")

  /** make sure that only the game master can perform an operation add/edit/delete a file */
  private fun requireGameMaster() {
    check(role == GroupWorkspaceRole.GAME_MASTER) {
      "Only the GameMaster can perform this operation."
    }
  }

  fun canEditItem(item: JDRFile, uid: String): Boolean {
    return role == GroupWorkspaceRole.GAME_MASTER || item.ownerId == uid
  }

  /** make sure the user can edit the file */
  private fun requireCanEdit(item: JDRFile, uid: String) {
    check(role == GroupWorkspaceRole.GAME_MASTER || item.ownerId == uid) {
      "You cannot edit an item that you do not own."
    }
  }

  /** make sure the item belong to the group */
  private fun requireBelongsToGroup(item: JDRFile) {
    check(groupId in item.sharedGroupsIds) { "This item does not belong to the current group." }
  }

  private suspend fun requireAccessibleFolder(folderId: String) {
    check(repository.isAccessibleFolder(folderId, groupId)) {
      "This folder is not part of the current group's workspace."
    }
  }

  private suspend fun requireItemInCurrentWorkspace(
      item: JDRFile,
      folderId: String,
  ) {
    requireBelongsToGroup(item)

    val itemsInFolder =
        if (folderId.isEmpty()) {
          repository.getGroupRootFiles(groupId).first()
        } else {
          requireAccessibleFolder(folderId)
          repository.getDocumentsInFolder(folderId).first()
        }

    check(itemsInFolder.any { it.id == item.id && groupId in it.sharedGroupsIds }) {
      "This item is not in the current workspace folder."
    }
  }

  init {
    require(groupId.isNotBlank()) { "Group ID cannot be empty." }
    loadWorkspace()
  }

  /** Observes the current folder, or the group root when folderId is empty. */
  fun loadWorkspace(folderId: String = _uiState.value.currentFolderId) {
    val requestGeneration = ++fetchGeneration
    fetchJob?.cancel()

    _uiState.update {
      it.copy(
          currentFolderId = folderId,
          items = emptyList(),
          loadState = LoadState.Loading,
          errorMessage = "",
      )
    }

    fetchJob = viewModelScope.launch {
      try {
        val uid =
            authRepository.authState.first()?.uid
                ?: throw IllegalStateException("You must be signed in.")

        _uiState.update { it.copy(currentUserId = uid) }
        val source =
            if (folderId.isEmpty()) {
              repository.getGroupRootFiles(groupId)
            } else {
              requireAccessibleFolder(folderId)
              repository.getDocumentsInFolder(folderId)
            }

        source.collect { items ->
          if (requestGeneration == fetchGeneration) {
            val groupItems = items.filter { item -> groupId in item.sharedGroupsIds }
            _uiState.update {
              it.copy(
                  items = groupItems,
                  loadState = LoadState.Success,
                  errorMessage = "",
              )
            }
          }
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        if (requestGeneration == fetchGeneration) {
          _uiState.update {
            it.copy(
                loadState =
                    LoadState.Error(
                        e.message?.takeIf(String::isNotBlank)
                            ?: "Unable to load the group workspace."
                    )
            )
          }
        }
      }
    }
  }

  /** Creates a folder in the currently displayed folder. Only the GameMaster may create folders. */
  fun createFolder(name: String) {
    val folderName = name.trim()

    if (folderName.isEmpty()) {
      showError("Folder name cannot be empty.")
      return
    }

    val parentId = _uiState.value.currentFolderId
    viewModelScope.launch {
      performOperation {
        requireGameMaster()
        if (parentId.isNotEmpty()) requireAccessibleFolder(parentId)
        val uid = requireUid()

        val folder =
            JDRFile(
                id = repository.getNewUid(),
                name = folderName,
                ownerId = uid,
                sharedGroupsIds = listOf(groupId),
                parentFolderIds = if (parentId.isEmpty()) emptyList() else listOf(parentId),
                personalParentId = "",
                type = DocumentType.FOLDER,
                content = "",
            )

        repository.createFile(folder).getOrThrow()
      }
    }
  }

  /**
   * Adds an existing document to the group workspace.
   *
   * The document itself is not duplicated. Its group-sharing and folder references are updated
   * instead.
   */
  fun addFile(fileId: String) {
    if (fileId.isBlank()) {
      showError("File ID cannot be empty.")
      return
    }

    val parentId = _uiState.value.currentFolderId
    viewModelScope.launch {
      performOperation {
        val uid = requireUid()
        if (parentId.isNotEmpty()) requireAccessibleFolder(parentId)

        val file =
            repository.getFile(fileId).getOrThrow()
                ?: throw IllegalArgumentException("File not found.")

        check(file.type != DocumentType.FOLDER) { "A folder cannot be added as a file." }

        check(file.ownerId == uid) { "You can only add files that you own." }

        val updated =
            file.copy(
                sharedGroupsIds = (file.sharedGroupsIds + groupId).distinct(),
                parentFolderIds =
                    if (parentId.isEmpty()) {
                      file.parentFolderIds
                    } else {
                      (file.parentFolderIds + parentId).distinct()
                    },
            )

        repository.updateFile(updated).getOrThrow()
      }
    }
  }

  /**
   * Removes an item from this workspace.
   *
   * Removing a reference does not permanently delete the underlying document, which may also be
   * used elsewhere.
   */
  fun removeItem(item: JDRFile) {
    val parentId = _uiState.value.currentFolderId

    viewModelScope.launch {
      performOperation {
        val uid = requireUid()

        val existing =
            repository.getFile(item.id).getOrThrow()
                ?: throw IllegalArgumentException("Item no longer exists.")

        requireItemInCurrentWorkspace(existing, parentId)
        requireCanEdit(existing, uid)

        check(role == GroupWorkspaceRole.GAME_MASTER || existing.type != DocumentType.FOLDER) {
          "Players cannot edit folders."
        }

        repository
            .removeItemFromGroup(
                itemId = existing.id,
                groupId = groupId,
            )
            .getOrThrow()
      }
    }
  }

  /** Edits an item's name/content while preserving its ownership and workspace references. */
  fun editItem(updatedItem: JDRFile) {
    val folderId = _uiState.value.currentFolderId
    viewModelScope.launch {
      performOperation {
        val uid = requireUid()
        val existing =
            repository.getFile(updatedItem.id).getOrThrow()
                ?: throw IllegalArgumentException("Item no longer exists.")

        requireItemInCurrentWorkspace(existing, folderId)
        requireCanEdit(existing, uid)
        check(role == GroupWorkspaceRole.GAME_MASTER || existing.type != DocumentType.FOLDER) {
          "Players cannot edit folders."
        }

        require(updatedItem.name.isNotBlank()) { "The item name cannot be blank." }
        repository
            .updateFile(
                existing.copy(
                    name = updatedItem.name.trim(),
                    content = updatedItem.content,
                )
            )
            .getOrThrow()
      }
    }
  }

  private suspend fun performOperation(
      operation: suspend () -> Unit,
  ) {
    pendingOperations.incrementAndGet()
    _uiState.update { it.copy(isOperationInProgress = true, errorMessage = "") }

    try {
      operationMutex.withLock { operation() }
      // The repository's Flow is responsible for refreshing the UI.
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      showError(e.message?.takeIf(String::isNotBlank) ?: "The workspace operation failed.")
    } finally {
      val remaining = pendingOperations.decrementAndGet()
      _uiState.update { it.copy(isOperationInProgress = remaining > 0) }
    }
  }

  private fun showError(message: String) {
    _uiState.update { it.copy(errorMessage = message) }
  }

  /** clears the current operation error */
  fun clearError() {
    _uiState.update { it.copy(errorMessage = "") }
  }
}
