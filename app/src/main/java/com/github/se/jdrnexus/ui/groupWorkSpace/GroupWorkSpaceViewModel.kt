// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.github.se.jdrnexus.ui.groupWorkSpace

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.se.jdrnexus.model.personalSpace.DocumentType
import com.github.se.jdrnexus.model.personalSpace.JDRFile
import com.github.se.jdrnexus.model.personalSpace.Placement
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
    val errorMessage: String = "",
    val canCreateFolder: Boolean = false,
    val canEditItems: Boolean = false,
    val currentFolderName: String = "",
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

  /**
   * Retrieves the ID of the currently authenticated user.
   *
   * @throws IllegalStateException if no user is signed in.
   */
  private suspend fun requireUid(): String =
      authRepository.authState.first()?.uid ?: throw IllegalStateException("You must be signed in.")

  /**
   * Ensures that the current user has the Game Master role.
   *
   * Used to restrict privileged workspace operations to the Game Master.
   */
  private fun requireGameMaster() {
    check(role == GroupWorkspaceRole.GAME_MASTER) {
      "Only the GameMaster can perform this operation."
    }
  }

  /**
   * Retrieves an item by its ID.
   *
   * @throws IllegalStateException if the item does not exist.
   */
  private suspend fun getItem(folderId: String, error: String): JDRFile {
    return repository.getFile(folderId).getOrThrow() ?: throw IllegalStateException(error)
  }

  /**
   * Ensures that the user is allowed to edit the specified item.
   *
   * The Game Master can edit any item, while players can edit only their own items.
   */
  private fun requireCanEdit(item: JDRFile, uid: String) {
    check(role == GroupWorkspaceRole.GAME_MASTER || item.ownerId == uid) {
      "You cannot edit an item that you do not own."
    }
  }

  /**
   * Ensures that the item is shared with the current group and has a placement in that group's
   * workspace.
   */
  private fun requireBelongsToGroup(item: JDRFile) {
    check(groupId in item.sharedGroupsIds && item.placements.containsKey(groupId)) {
      "This item does not belong to the current group."
    }
  }

  private suspend fun hasOwnedDescendant(
      folderId: String,
      uid: String,
      visited: MutableSet<String> = mutableSetOf(),
  ): Boolean {
    if (!visited.add(folderId)) return false

    val children = repository.getGroupFolderFiles(folderId, groupId).first()

    for (child in children) {
      if (groupId !in child.sharedGroupsIds) continue
      if (child.placements[groupId] == null) continue

      if (child.ownerId == uid) return true

      if (child.type == DocumentType.FOLDER && hasOwnedDescendant(child.id, uid, visited)) {
        return true
      }
    }

    return false
  }

  private suspend fun canSeeItem(
      item: JDRFile,
      uid: String,
  ): Boolean {
    // The GM can see all items in the group workspace.
    if (role == GroupWorkspaceRole.GAME_MASTER) return true

    // A player can only see items that have a valid placement in this group.
    if (groupId !in item.sharedGroupsIds) return false

    val placement = item.placements[groupId] ?: return false

    // Players retain access to their own items, even when hidden.
    if (item.ownerId == uid) return true

    // A player can see a folder if it contains one of their own items,
    // even when the folder or one of its ancestors is hidden.
    if (item.type == DocumentType.FOLDER && hasOwnedDescendant(item.id, uid)) {
      return true
    }

    // Other items are invisible if they or any ancestor folder is hidden.
    if (placement.isHidden) return false

    var parentId = placement.folderId
    val visited = mutableSetOf<String>()

    while (parentId != groupId) {
      // Malformed or cyclic folder paths must not grant access.
      if (parentId.isBlank() || !visited.add(parentId)) return false

      val parent = repository.getFile(parentId).getOrThrow() ?: return false

      if (groupId !in parent.sharedGroupsIds) return false

      val parentPlacement = parent.placements[groupId] ?: return false

      if (parent.type != DocumentType.FOLDER) return false
      if (parentPlacement.isHidden) return false

      parentId = parentPlacement.folderId
    }

    return true
  }

  /**
   * Retrieves and validates a folder before allowing the user to access it.
   *
   * The folder must exist, be a regular folder, belong to the current group, and be visible to the
   * user.
   */
  private suspend fun requireAccessibleFolder(folderId: String, uid: String): JDRFile {
    val folder = getItem(folderId, "Folder not found")
    check(folder.type == DocumentType.FOLDER) { "The requested item is not a folder." }

    requireBelongsToGroup(folder)
    check(canSeeItem(folder, uid)) { "You cannot access this folder." }
    return folder
  }

  /**
   * Ensures that an item belongs to the current group and is located directly inside the folder
   * currently being displayed.
   *
   * Also validates access to the current folder and visibility of the item.
   */
  private suspend fun requireItemInCurrentWorkspace(
      item: JDRFile,
      folderId: String,
  ) {
    requireBelongsToGroup(item)
    val uid = requireUid()
    val effectiveFolderId = folderId.ifEmpty { groupId }
    if (folderId.isNotEmpty()) requireAccessibleFolder(folderId, uid)

    val placement =
        item.placements[groupId] ?: error("This item does not belong to the current group.")

    check(placement.folderId == effectiveFolderId) {
      "This item is not in the current workspace folder."
    }
    check(canSeeItem(item, uid)) { "You cannot access this item." }
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
        val uid = requireUid()

        _uiState.update { it.copy(currentUserId = uid) }
        val folderName =
            if (folderId.isEmpty()) {
              ""
            } else {
              requireAccessibleFolder(folderId, uid).name
            }

        _uiState.update { it.copy(currentFolderName = folderName) }
        val effectiveFolderId = folderId.ifEmpty { groupId }
        val source = repository.getGroupFolderFiles(effectiveFolderId, groupId)

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
              requireAccessibleFolder(folderId, uid)
            }

            val visibleItems = buildList {
              for (item in items) {
                if (canSeeItem(item, uid)) {
                  add(item)
                }
              }
            }

            _uiState.update {
              it.copy(
                  items = visibleItems,
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
                loadState = LoadState.Error(e.message ?: "Unable to load the group workspace."),
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
        val uid = requireUid()
        val parent =
            if (parentId.isNotEmpty()) {
              requireAccessibleFolder(parentId, uid)
            } else {
              null
            }

        val folderId = repository.getNewUid()

        val isHidden = parent?.placements?.get(groupId)?.isHidden == true
        val folder =
            JDRFile(
                id = folderId,
                name = folderName,
                ownerId = uid,
                sharedGroupsIds = listOf(groupId),
                placements =
                    mapOf(groupId to Placement(folderId = parentId.ifEmpty { groupId }, isHidden)),
                personalParentId = "",
                type = DocumentType.FOLDER,
                content = "",
            )

        repository.createFile(folder).getOrThrow()
      }
    }
  }

  /** Shares an existing, user-owned Personal Space file into the selected group folder. */
  fun addFile(fileId: String): Job? {
    if (fileId.isBlank()) {
      showError("File ID cannot be empty.")
      return null
    }

    val parentId = _uiState.value.currentFolderId
    val existingParent = parentId.ifEmpty { groupId }

    return viewModelScope.launch {
      performOperation {
        val uid = requireUid()

        if (parentId.isNotEmpty()) {
          requireAccessibleFolder(parentId, uid)
        }

        val file = getItem(fileId, "File not found")

        check(file.type != DocumentType.GROUP_ROOT) { "Only files can be added here." }
        // Todo: remove the check below when the add of folder is implemented
        check(file.type != DocumentType.FOLDER) { "Adding folders is not supported yet." }
        check(file.ownerId == uid) { "You can only add files that you own." }
        check(groupId !in file.sharedGroupsIds) { "This file is already in the group." }
        repository
            .addItemToGroup(
                itemId = file.id,
                groupId = groupId,
                folderId = existingParent,
                isHidden = false,
                includeContent = file.type == DocumentType.FOLDER,
            )
            .getOrThrow()
      }
    }
  }

  /** Removes this group's placement without deleting the underlying document. */
  fun removeItem(item: JDRFile): Job {
    val parentId = _uiState.value.currentFolderId

    return viewModelScope.launch {
      performOperation {
        val uid = requireUid()

        val existing = getItem(item.id, "Item no longer exists.")

        requireItemInCurrentWorkspace(existing, parentId)
        requireCanEdit(existing, uid)

        repository
            .removeItemFromGroup(
                itemId = existing.id,
                groupId = groupId,
                parentFolderId = parentId.ifEmpty { groupId },
            )
            .getOrThrow()
      }
    }
  }

  /** Edits an item's name/content while preserving its ownership and workspace references. */
  fun editItem(updatedItem: JDRFile): Job {
    if (updatedItem.name.isBlank()) {
      showError("The item name cannot be blank.")
      return Job().also { it.complete() }
    }

    val folderId = _uiState.value.currentFolderId
    return viewModelScope.launch {
      performOperation {
        val uid = requireUid()
        val existing = getItem(updatedItem.id, "Item no longer exists.")

        requireItemInCurrentWorkspace(existing, folderId)
        requireCanEdit(existing, uid)

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

  /** Changes whether an item is hidden in this group. */
  fun setItemHidden(item: JDRFile, isHidden: Boolean): Job {
    val folderId = _uiState.value.currentFolderId
    return viewModelScope.launch {
      performOperation {
        val uid = requireUid()
        val existing = getItem(item.id, "Item no longer exists.")
        requireItemInCurrentWorkspace(existing, folderId)
        requireCanEdit(existing, uid)
        repository.setItemHidden(existing.id, groupId, isHidden).getOrThrow()
      }
    }
  }

  /**
   * Executes a workspace operation while managing loading and error states. Serializes operations
   * using a Mutex, propagates coroutine cancellation, displays failures, and resets the loading
   * state when all pending operations finish. Workspace updates are observed through the
   * repository's Flow.
   */
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
      showError(e.message ?: "The workspace operation failed")
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
