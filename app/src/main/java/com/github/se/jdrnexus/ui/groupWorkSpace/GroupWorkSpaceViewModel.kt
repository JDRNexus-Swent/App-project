// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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
    val canEditItems: Boolean = false,
    val currentFolderName: String = "",
)

class GroupWorkspaceViewModel(
    val groupId: String,
    val role: GroupWorkspaceRole,
    val repository: WorkspaceRepository = WorkspaceRepositoryProvider.repository,
    val authRepository: AuthRepository = AuthRepositoryFirebase(),
) : ViewModel() {

  private val _uiState =
      MutableStateFlow(
          GroupWorkspaceUiState(
              canCreateFolder = role == GroupWorkspaceRole.GAME_MASTER,
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

  private suspend fun requireAccessibleFolder(folderId: String): JDRFile {
    val folder =
        repository.getFile(folderId).getOrThrow()
            ?: throw IllegalStateException("Folder not found.")

    check(folder.type == DocumentType.FOLDER) { "The requested item is not a folder." }

    check(groupId in folder.sharedGroupsIds) {
      "This folder does not belong to the current group's workspace."
    }

    return folder
  }

  private suspend fun requireItemInCurrentWorkspace(
      item: JDRFile,
      folderId: String,
  ) {
    requireBelongsToGroup(item)

    val effectiveFolderId = folderId.ifEmpty { groupId }
    if (folderId.isNotEmpty()) requireAccessibleFolder(folderId)
    val itemsInFolder = repository.getDocumentsInFolder(effectiveFolderId).first()

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
        val folderName =
            if (folderId.isEmpty()) {
              ""
            } else {
              requireAccessibleFolder(folderId).name
            }

        _uiState.update { it.copy(currentFolderName = folderName) }
        val effectiveFolderId = folderId.ifEmpty { groupId }
        val source = repository.getDocumentsInFolder(effectiveFolderId)

        val updates =
            if (folderId.isEmpty()) {
              source
            } else {
              // React to changes to the selected folder itself.
              val folderChanges = repository.getFileFlow(folderId).map { Unit }

              combine(source, folderChanges) { items, _ -> items }
            }

        updates.collect { items ->
          if (requestGeneration == fetchGeneration) {
            // Revalidate the selected folder when its contents or metadata change.
            if (folderId.isNotEmpty()) {
              requireAccessibleFolder(folderId)
            }

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
                    ),
                items = emptyList(),
                currentFolderName = "",
            )
          }
        }
      }
    }
  }

  /** Creates a folder in the currently displayed folder. Only the GameMaster may create folders. */
  fun createFolder(name: String): Job? {
    val folderName = name.trim()
    if (folderName.isEmpty()) {
      showError("Folder name cannot be empty.")
      return null
    }

    val parentId = _uiState.value.currentFolderId
    return viewModelScope.launch {
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
                parentFolderIds = listOf(parentId.ifEmpty { groupId }),
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
  fun addFile(fileId: String): Job? {
    if (fileId.isBlank()) {
      showError("File ID cannot be empty.")
      return null
    }

    val parentId = _uiState.value.currentFolderId

    return viewModelScope.launch {
      performOperation {
        val uid = requireUid()

        if (parentId.isNotEmpty()) {
          requireAccessibleFolder(parentId)
        }

        val file =
            repository.getFile(fileId).getOrThrow()
                ?: throw IllegalArgumentException("File not found.")

        check(file.type != DocumentType.FOLDER) { "A folder cannot be added as a file." }
        check(file.ownerId == uid) { "You can only add files that you own." }

        // Remove old placements in this group and stale folder references.
        // Keep references to folders belonging to other groups.

        val parentIdsToRemove = mutableSetOf<String>()

        for (folderId in file.parentFolderIds) {
          // Never remove the current destination folder here.
          if (folderId == parentId) continue
          if (folderId == groupId) {
            if (parentId.isNotEmpty()) parentIdsToRemove.add(folderId)
            continue
          }

          val parent = repository.getFile(folderId).getOrThrow()

          // Remove only a confirmed folder belonging to this group.
          // Preserve unknown references and references to other groups.
          if (
              parent != null &&
                  parent.type == DocumentType.FOLDER &&
                  groupId in parent.sharedGroupsIds &&
                  (parent.sharedGroupsIds - groupId).isEmpty()
          ) {
            parentIdsToRemove.add(folderId)
          }
        }

        val updatedParentIds =
            (file.parentFolderIds - parentIdsToRemove + listOf(parentId.ifEmpty { groupId }))
                .distinct()

        val updated =
            file.copy(
                sharedGroupsIds = (file.sharedGroupsIds + groupId).distinct(),
                parentFolderIds = updatedParentIds,
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
  fun removeItem(item: JDRFile): Job {
    val parentId = _uiState.value.currentFolderId

    return viewModelScope.launch {
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
                parentFolderId = parentId.takeIf { it.isNotEmpty() },
            )
            .getOrThrow()
        if (existing.type == DocumentType.FOLDER && _uiState.value.currentFolderId == existing.id) {
          loadWorkspace("")
        }
      }
    }
  }

  /** Edits an item's name/content while preserving its ownership and workspace references. */
  fun editItem(updatedItem: JDRFile): Job {
    val folderId = _uiState.value.currentFolderId
    return viewModelScope.launch {
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
