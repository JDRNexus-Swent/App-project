// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.github.se.jdrnexus.ui.groupWorkSpace

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.se.jdrnexus.model.personalSpace.DocumentType
import com.github.se.jdrnexus.model.personalSpace.GroupRepository
import com.github.se.jdrnexus.model.personalSpace.JDRFile
import com.github.se.jdrnexus.model.personalSpace.Placement
import com.github.se.jdrnexus.model.personalSpace.WorkspaceRepository
import com.github.se.jdrnexus.model.personalSpace.WorkspaceRepositoryProvider
import com.github.se.jdrnexus.model.personalSpace.isContainer
import com.github.se.jdrnexus.model.repository.AuthRepository
import com.github.se.jdrnexus.model.repository.AuthRepositoryProvider
import com.github.se.jdrnexus.viewmodel.LoadState
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
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
    val folderHistory: List<String> = emptyList(),
    val groupId: String = "",
    val groupName: String = "",
    val personalFiles: List<JDRFile> = emptyList(),
    val personalFilesError: String = "",
)

class GroupWorkspaceViewModel(
    private val groupId: String,
    private val groupRepository: GroupRepository,
    private val repository: WorkspaceRepository = WorkspaceRepositoryProvider.repository,
    private val authRepository: AuthRepository = AuthRepositoryProvider.repository,
) : ViewModel() {

  private var role: GroupWorkspaceRole? = null
  private val _uiState = MutableStateFlow(GroupWorkspaceUiState())

  val uiState: StateFlow<GroupWorkspaceUiState> = _uiState.asStateFlow()

  // Keeps track of the coroutine currently loading the workspace.
  private var fetchJob: Job? = null
  private var personalFilesJob: Job? = null
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
    check(role != null) { "You are not a member of this group." }
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

      if (child.isContainer() && hasOwnedDescendant(child.id, uid, visited)) {
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
    when (role) {
      GroupWorkspaceRole.GAME_MASTER -> return true
      GroupWorkspaceRole.PLAYER -> Unit
      null -> return false
    }

    // A player can only see items that have a valid placement in this group.
    if (groupId !in item.sharedGroupsIds) return false

    val placement = item.placements[groupId] ?: return false

    // Players retain access to their own items, even when hidden.
    if (item.ownerId == uid) return true

    // A player can see a folder if it contains one of their own items,
    // even when the folder or one of its ancestors is hidden.
    if (item.isContainer() && hasOwnedDescendant(item.id, uid)) {
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

      if (!parent.isContainer()) return false
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
    check(folder.isContainer()) { "The requested item is not a folder." }

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

    viewModelScope.launch {
      authRepository.authState
          .map { it?.uid }
          .distinctUntilChanged()
          .collectLatest { uid ->
            fetchJob?.cancel()
            fetchJob = null
            fetchGeneration++
            role = null

            if (uid == null) {
              _uiState.value = GroupWorkspaceUiState(loadState = LoadState.SignedOut)
              return@collectLatest
            }

            _uiState.value = GroupWorkspaceUiState(loadState = LoadState.Loading)
            observeMembership(uid)
          }
    }
  }

  private suspend fun observeMembership(uid: String) {
    try {
      groupRepository.getGroupFlow(groupId).collect { group ->
        val newRole =
            when {
              group?.gameMasterId == uid -> GroupWorkspaceRole.GAME_MASTER

              group?.participantIds?.contains(uid) == true -> GroupWorkspaceRole.PLAYER

              else -> null
            }

        if (newRole == null) {
          role = null
          fetchJob?.cancel()
          fetchGeneration++

            _uiState.update {
              it.copy(
                  items = emptyList(),
                  currentFolderId = "",
                  currentFolderName = "",
                  folderHistory = emptyList(),
                  canCreateFolder = false,
                  canEditItems = false,
                  loadState = LoadState.Error("You are no longer a member of this group."),
                  groupId = "",
                  groupName = "",
              )
            }
          personalFilesJob?.cancel()
            personalFilesJob = null
          return@collect
        }

        val roleChanged = role != newRole
        role = newRole

          _uiState.update {
            it.copy(
                canCreateFolder = newRole == GroupWorkspaceRole.GAME_MASTER,
                canEditItems = newRole == GroupWorkspaceRole.GAME_MASTER,
                groupId = groupId,
                groupName = group?.name.orEmpty(),
            )
          }

          if (personalFilesJob == null) {
            personalFilesJob = loadPersonalFiles(uid)
        }

        if (roleChanged || fetchJob == null) {
          loadWorkspace()
        }
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      role = null
      fetchJob?.cancel()
      fetchGeneration++
      _uiState.update {
        it.copy(
            currentUserId = "",
            items = emptyList(),
            currentFolderId = "",
            currentFolderName = "",
            folderHistory = emptyList(),
            canCreateFolder = false,
            canEditItems = false,
            loadState = LoadState.Error(e.message ?: "Unable to verify group membership."),
        )
      }
    }
  }

  private fun loadPersonalFiles(uid: String): Job = viewModelScope.launch {
    try {
      personalFilesFlow(uid).collect { files ->
        _uiState.update {
          it.copy(
              personalFiles = files,
              personalFilesError = "",
              errorMessage = if (it.personalFilesError.isNotBlank()) "" else it.errorMessage,
          )
        }
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      val message = e.message?.takeIf(String::isNotBlank) ?: "Unable to load personal files."
      _uiState.update {
        it.copy(
            personalFiles = emptyList(),
            personalFilesError = message,
            errorMessage = message,
        )
      }
    }
  }

  /**
   * Keeps the traversal behind one repository-facing helper so it can be replaced by the shared
   * Personal Space traversal when that API is available.
   */
  private fun personalFilesFlow(uid: String) =
      repository.getPersonalRootFiles(uid).map { roots ->
        val files = mutableListOf<JDRFile>()
        val visited = mutableSetOf<String>()

        suspend fun collectFolder(folder: JDRFile) {
          if (!visited.add(folder.id)) return
          repository.getPersonalFolderFiles(folder.id, uid).first().forEach { child ->
            if (child.type == DocumentType.FOLDER) {
              collectFolder(child)
            } else if (child.type != DocumentType.GROUP_ROOT) {
              files += child
            }
          }
        }

        roots.forEach { root ->
          if (root.type == DocumentType.FOLDER) {
            collectFolder(root)
          } else if (root.type != DocumentType.GROUP_ROOT) {
            files += root
          }
        }
        files
      }

  /** Opens a child folder and records the current folder for back navigation. */
  fun openFolder(folderId: String) {
    val currentFolderId = _uiState.value.currentFolderId
    if (folderId == currentFolderId) return

    _uiState.update { state -> state.copy(folderHistory = state.folderHistory + currentFolderId) }
    loadWorkspace(folderId, preserveFolderHistory = true)
  }

  /** Navigates to the previous folder, returning whether navigation occurred. */
  fun navigateBack(): Boolean {
    val history = _uiState.value.folderHistory
    if (history.isEmpty()) return false

    _uiState.update { it.copy(folderHistory = history.dropLast(1)) }
    loadWorkspace(history.last(), preserveFolderHistory = true)
    return true
  }

  /** Observes the current folder, or the group root when folderId is empty. */
  fun loadWorkspace(
      folderId: String = _uiState.value.currentFolderId,
      preserveFolderHistory: Boolean = false,
  ) {
    if (role == null) return
    val requestGeneration = ++fetchGeneration
    fetchJob?.cancel()

    _uiState.update {
      it.copy(
          currentFolderId = folderId,
          folderHistory = if (preserveFolderHistory) it.folderHistory else emptyList(),
          items = emptyList(),
          loadState = LoadState.Loading,
          errorMessage = it.personalFilesError.ifBlank { "" },
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
              source.map { items -> items to true }
            } else {
              // Observe both folder contents and changes to the selected folder.
              combine(source, repository.getFileFlow(folderId)) { items, folder ->
                val folderValid =
                    folder != null &&
                        folder.type == DocumentType.FOLDER &&
                        groupId in folder.sharedGroupsIds

                items to folderValid
              }
            }

        updates.collect { (items, folderValid) ->
          if (requestGeneration != fetchGeneration) return@collect

          // If the selected folder is no longer available, return to the group root.
          if (!folderValid) {
            loadWorkspace("")
            showError("This folder is no longer available.")
            return@collect
          }

          try {
            // Revalidate the selected folder when its contents or metadata change.
            if (folderId.isNotEmpty()) {
              requireAccessibleFolder(folderId, uid)
            }
            // Ignore this result if a newer request has started.
            if (requestGeneration != fetchGeneration) return@collect

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
              )
            }
          } catch (e: CancellationException) {
            throw e
          } catch (e: Exception) {
            if (requestGeneration == fetchGeneration) {
              val message = e.message ?: "Unable to load workspace."

              _uiState.update {
                it.copy(
                    items = emptyList(),
                    loadState = LoadState.Error(message),
                    errorMessage =
                        it.personalFilesError.ifBlank { message },
                )
              }
            }
          }
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        if (requestGeneration == fetchGeneration) {
          if (folderId.isNotEmpty() && repository.getFile(folderId).getOrNull() == null) {
            loadWorkspace("")
            showError("This folder is no longer available.")
            return@launch
          }
          _uiState.update {
            it.copy(
                loadState = LoadState.Error(e.message ?: "Unable to load the group workspace."),
                items = emptyList(),
                currentFolderName = "",
                errorMessage =
                    it.personalFilesError.ifBlank {
                        e.message ?: "Unable to load the group workspace."
                    },
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
                    personalParentId = "",
                    type = DocumentType.FOLDER,
                    content = "",
                )
                .withPlacement(groupId, Placement(parentId.ifEmpty { groupId }, isHidden))

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
  fun editItem(updatedItem: JDRFile): Job? {
    if (updatedItem.name.isBlank()) {
      showError("The item name cannot be blank.")
      return null
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
    _uiState.update { it.copy(isOperationInProgress = true) }

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
