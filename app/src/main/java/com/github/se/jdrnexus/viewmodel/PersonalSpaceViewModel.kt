// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.github.se.jdrnexus.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.se.jdrnexus.model.personalSpace.DocumentType
import com.github.se.jdrnexus.model.personalSpace.JDRFile
import com.github.se.jdrnexus.model.personalSpace.WorkspaceRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ============ Some useful data classes ============

/** Backend state of the current fetch. Exactly one of these at a time. */
sealed interface LoadState {
  data object Loading : LoadState

  data object Success : LoadState

  data class Error(val message: String) : LoadState
}

/**
 * UI state of the Personal Space screen.
 * - currentFolderId == "" means the user is at the Root.
 * - canNavigateUp is true when there is a previous folder to go back to (Back goes up a folder);
 *   false means Back should leave the screen.
 */
data class PersonalSpaceUiState(
    val items: List<JDRFile> = emptyList(),
    val currentFolderId: String = "",
    val loadState: LoadState = LoadState.Loading,
    val canNavigateUp: Boolean = false,
    val isAddMenuExpanded: Boolean = false,
    val isNewFolderDialogOpen: Boolean = false,
    val newFolderName: String = "",
    val newFolderError: String = "",
)

// ============ Definition of the ViewModel ============
class PersonalSpaceViewModel(
    private val repository: WorkspaceRepository,
    private val ownerId: String, // TODO: In my opinion this could be done in the implementation of the
    //repo but would need a discussion first !!!
) : ViewModel() {

    // ============ Internal variables ============
  private val _uiState = MutableStateFlow(PersonalSpaceUiState())
  val uiState: StateFlow<PersonalSpaceUiState> = _uiState.asStateFlow()

  /** Folders visited before the current one, used to go back up. "" = Root. */
  private val navigationPath = ArrayDeque<String>()

  /**
  * The fetch currently running. It is cancelled when a new fetch starts, so a slow response for a
  * previous folder can never overwrite the contents of the folder the user is now in.
  */
  private var fetchJob: Job? = null

    // ============ Fetching functions ============


  init {
    fetchItems(parentFolderId = "")
  }

  /**
   * Loads the items whose parent is [parentFolderId]. Can also be used as a "retry".
   *
   * canNavigateUp is recomputed here from the back stack. Every change to [navigationPath] is
   * followed by a call to this function, so the flag is always in sync with the stack.
   */
  fun fetchItems(parentFolderId: String) {
    fetchJob?.cancel()

    _uiState.value =
        _uiState.value.copy(
            items = emptyList(),
            currentFolderId = parentFolderId,
            loadState = LoadState.Loading,
            canNavigateUp = navigationPath.isNotEmpty(),
        )

    fetchJob = viewModelScope.launch {
      try {
        val files =
            if (parentFolderId.isEmpty()) {
              repository.getPersonalRootFiles(ownerId)
            } else {
              repository.getDocumentsInFolder(parentFolderId)
            }
        files.collect { items ->
          _uiState.update { it.copy(items = items, loadState = LoadState.Success) }
        }
      } catch (e: CancellationException) {
        throw e // never swallow cancellation
      } catch (e: Exception) {
        val message = e.message?.takeIf(String::isNotBlank) ?: "Unable to load items."
        _uiState.update { it.copy(loadState = LoadState.Error(message)) }
      }
    }
  }

    // ============ Seelction/Navigation functions ============

  /** Folders open in this screen; every other JDR file is opened as a document. */
  fun onItemClicked(item: JDRFile, navigateToDocument: (documentId: String) -> Unit) {
    if (item.type == DocumentType.FOLDER) {
      navigationPath.addLast(_uiState.value.currentFolderId)
      fetchItems(parentFolderId = item.id)
    } else {
      navigateToDocument(item.id)
    }
  }

  /**
   * Goes back to the parent folder. Does nothing when [PersonalSpaceUiState.canNavigateUp] is
   * false.
   */
  fun onNavigateUp() {
    if (navigationPath.isEmpty()) return

    fetchItems(parentFolderId = navigationPath.removeLast())
  }


    // ============ Addition handling functions (when "+" is clicked) ============

  /**
   * Handles the screen's "+" action. Call this from its click handler, then show the add menu when
   * [PersonalSpaceUiState.isAddMenuExpanded] becomes true.
   */
  fun onAddClicked() {
    _uiState.update { it.copy(isAddMenuExpanded = true) }
  }

  /** Call when the add menu is dismissed without choosing an action. */
  fun onAddMenuDismissed() {
    _uiState.update { it.copy(isAddMenuExpanded = false) }
  }

  /**
   * Call when the user chooses "New folder" from the add menu. Closes the menu and opens a fresh
   * folder-name dialog, clearing any previous name or error.
   */
  fun onNewFolderSelected() {
    _uiState.update {
      it.copy(
          isAddMenuExpanded = false,
          isNewFolderDialogOpen = true,
          newFolderName = "",
          newFolderError = "",
      )
    }
  }

  /**
   * Call from the folder-name field's text-change callback. The latest name is exposed in
   * [PersonalSpaceUiState.newFolderName]; editing also clears the previous validation or creation
   * error.
   */
  fun onNewFolderNameChanged(name: String) {
    _uiState.update { it.copy(newFolderName = name, newFolderError = "") }
  }

  /**
   * Call when the user dismisses or cancels the new-folder dialog. Closes it and clears the draft
   * name and error so the next time it opens it starts clean.
   */
  fun onNewFolderDialogDismissed() {
    _uiState.update {
      it.copy(isNewFolderDialogOpen = false, newFolderName = "", newFolderError = "")
    }
  }

    // ============ Creation functions ============

  /**
   * Call when the user confirms the new-folder dialog. The ViewModel creates the folder under the
   * currently viewed directory (an empty parent list at the root). A blank name sets
   * [PersonalSpaceUiState.newFolderError] without making a repository call.
   *
   * Creation runs asynchronously: on success the dialog closes and the item-list flow loaded by
   * [fetchItems] supplies the updated contents; on failure the dialog stays open and the error is
   * exposed in [PersonalSpaceUiState.newFolderError].
   */
  fun createFolder() {
    val state = _uiState.value
    val name = state.newFolderName.trim()
    if (name.isEmpty()) {
      _uiState.update { it.copy(newFolderError = "Folder name cannot be empty.") }
      return
    }

    val folder =
        JDRFile(
            name = name,
            ownerId = ownerId,
            parentFolderIds =
                if (state.currentFolderId.isEmpty()) emptyList() else listOf(state.currentFolderId),
            type = DocumentType.FOLDER,
        )

    viewModelScope.launch {
      try {
        repository.createFile(folder).getOrThrow()
        _uiState.update {
          it.copy(isNewFolderDialogOpen = false, newFolderName = "", newFolderError = "")
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        val message = e.message?.takeIf(String::isNotBlank) ?: "Unable to create the folder."
        _uiState.update { it.copy(newFolderError = message) }
      }
    }
  }

    /**
     * Call when the user chooses "New file" from the add menu. Closes the menu and invokes the UI's
     * navigation callback with the current folder ID; it is empty when the user is at the root.
     * The UI can pass this ID to the create-file screen.
     */
    fun onNewFileSelected(navigateToCreateFile: (parentFolderId: String) -> Unit) {
        _uiState.update { it.copy(isAddMenuExpanded = false) }
        navigateToCreateFile(_uiState.value.currentFolderId)
    }

    /* TODO: reflect on how the file will be saved back to the repo and displayed
    When we navigate to the next screen, is it the one to save to the repo ? And then when comeback fetch again from root
    Or make the file passback the parentFolder to call the ViewModel with a particular UI state set ?
    Fetch items will need to be recalled or items need to be updated... To be discussed
    */
}
