// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.se.jdrnexus.model.personalSpace.DocumentType
import com.github.se.jdrnexus.model.personalSpace.GroupRepository
import com.github.se.jdrnexus.model.personalSpace.JDRFile
import com.github.se.jdrnexus.model.personalSpace.PERSONAL_ROOT_ID
import com.github.se.jdrnexus.model.personalSpace.WorkspaceRepository
import com.github.se.jdrnexus.model.personalSpace.WorkspaceRepositoryProvider
import com.github.se.jdrnexus.model.personalSpace.isContainer
import com.github.se.jdrnexus.model.repository.AuthRepository
import com.github.se.jdrnexus.model.repository.AuthRepositoryProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Searches the names of every file type (folders, lore and characters) across the configured
 * workspace. Personal search recursively reads the signed-in user's folder tree; group search also
 * applies the workspace's membership and visibility rules before exposing results.
 *
 * A [groupRepository] is required when [scope] is [DocumentSearchScope.Group]. It is optional for
 * Personal Space search.
 */
class DocumentSearchViewModel(
    private val scope: DocumentSearchScope = DocumentSearchScope.Personal,
    private val repository: WorkspaceRepository = WorkspaceRepositoryProvider.repository,
    private val authRepository: AuthRepository = AuthRepositoryProvider.repository,
    private val groupRepository: GroupRepository? = null,
) : ViewModel(), DocumentSearchInterfaceVM {
  private val _uiState = MutableStateFlow(DocumentSearchUiState())
  override val uiState: StateFlow<DocumentSearchUiState> = _uiState.asStateFlow()

  private var searchJob: Job? = null
  private var requestId = 0L

  init {
    require((scope !is DocumentSearchScope.Group) || !scope.groupId.isBlank()) {
      "Group ID cannot be empty."
    }
  }

  override fun onQueryChanged(query: String) {
    searchJob?.cancel()
    val currentRequest = ++requestId
    val debounceMillis = 250L
    if (query.isBlank()) {
      _uiState.value = DocumentSearchUiState(query = query)
      return
    }

    _uiState.value = DocumentSearchUiState(query = query, status = DocumentSearchStatus.Loading)
    searchJob = viewModelScope.launch {
      try {
        delay(debounceMillis)
        val results = search(query.trim())
        if (currentRequest == requestId) {
          _uiState.update {
            it.copy(
                results = results,
                status =
                    if (results.isEmpty()) {
                      DocumentSearchStatus.NoResults
                    } else {
                      DocumentSearchStatus.Results
                    },
            )
          }
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: SearchException) {
        if (currentRequest == requestId) {
          _uiState.update {
            it.copy(results = emptyList(), status = DocumentSearchStatus.Error(e.reason))
          }
        }
      } catch (e: Exception) {
        if (currentRequest == requestId) {
          _uiState.update {
            it.copy(
                results = emptyList(),
                status = DocumentSearchStatus.Error(SearchError.SEARCH_FAILED),
            )
          }
        }
      }
    }
  }

  override fun clearQuery() = onQueryChanged("")

  override fun retry() {
    val query = _uiState.value.query
    if (query.isNotBlank()) onQueryChanged(query)
  }

  private suspend fun search(query: String): List<DocumentSearchResult> {
    val uid =
        authRepository.authState.first()?.uid ?: throw SearchException(SearchError.NOT_SIGNED_IN)

    val documents =
        when (val searchScope = scope) {
          DocumentSearchScope.Personal -> collectPersonalDocuments(uid)
          is DocumentSearchScope.Group -> collectGroupDocuments(searchScope.groupId, uid)
        }

    return documents
        .asSequence()
        // Folders, lore and characters are all searchable; a group root is the workspace itself.
        .filter { it.file.type != DocumentType.GROUP_ROOT }
        .filter { it.file.name.contains(query, ignoreCase = true) }
        .sortedWith(
            compareBy<DocumentSearchResult> { matchRank(it.file.name, query) }
                .thenBy { it.file.name.lowercase() }
                .thenBy { it.file.id }
        )
        .toList()
  }

  private fun matchRank(name: String, query: String): Int =
      when {
        name.equals(query, ignoreCase = true) -> 0
        name.startsWith(query, ignoreCase = true) -> 1
        name.split(Regex("\\s+")).any { it.startsWith(query, ignoreCase = true) } -> 2
        else -> 3
      }

  private suspend fun collectPersonalDocuments(ownerId: String): List<DocumentSearchResult> {
    val documents = mutableListOf<DocumentSearchResult>()
    // Each pending folder carries the location of its children: the names and ids down to it.
    val pendingFolders = ArrayDeque<Triple<String, List<String>, List<String>>>()
    val visitedFolderIds = mutableSetOf<String>()
    pendingFolders.addLast(Triple(PERSONAL_ROOT_ID, emptyList(), emptyList()))

    // One-shot reads: the first value of a listener can come from a partial local cache.
    while (pendingFolders.isNotEmpty()) {
      val (folderId, folderPath, folderIds) = pendingFolders.removeFirst()
      repository.fetchPersonalFolderFiles(folderId, ownerId).forEach { item ->
        documents.add(DocumentSearchResult(item, folderPath, folderIds))
        if (item.isContainer() && visitedFolderIds.add(item.id)) {
          pendingFolders.addLast(Triple(item.id, folderPath + item.name, folderIds + item.id))
        }
      }
    }
    return documents
  }

  private suspend fun collectGroupDocuments(
      groupId: String,
      userId: String,
  ): List<DocumentSearchResult> {
    val groups = groupRepository ?: throw SearchException(SearchError.SEARCH_FAILED)
    val group = groups.getGroupFlow(groupId).first()
    val isGameMaster = group?.gameMasterId == userId
    val isPlayer = group?.participantIds?.contains(userId) == true
    if (group == null || (!isGameMaster && !isPlayer)) {
      throw SearchException(SearchError.GROUP_ACCESS_DENIED)
    }

    val documentsById = linkedMapOf<String, JDRFile>()
    val pendingFolderIds = ArrayDeque<String>()
    val visitedFolderIds = mutableSetOf(groupId)
    pendingFolderIds.addLast(groupId)

    while (pendingFolderIds.isNotEmpty()) {
      val folderId = pendingFolderIds.removeFirst()
      repository.fetchGroupFolderFiles(folderId, groupId).forEach { item ->
        if (groupId !in item.sharedGroupsIds || item.placements[groupId]?.folderId != folderId) {
          return@forEach
        }
        documentsById[item.id] = item
        if (item.isContainer() && visitedFolderIds.add(item.id)) {
          pendingFolderIds.addLast(item.id)
        }
      }
    }

    val files = documentsById.values.toList()
    val childrenByParent = files.groupBy { it.placements[groupId]?.folderId }
    val ownedDescendantMemo = mutableMapOf<String, Boolean>()

    fun hasOwnedDescendant(
        folderId: String,
        visiting: MutableSet<String> = mutableSetOf(),
    ): Boolean {
      ownedDescendantMemo[folderId]?.let {
        return it
      }
      if (!visiting.add(folderId)) return false

      val found =
          childrenByParent[folderId].orEmpty().any { child ->
            child.ownerId == userId ||
                (child.isContainer() && hasOwnedDescendant(child.id, visiting))
          }
      visiting.remove(folderId)
      ownedDescendantMemo[folderId] = found
      return found
    }

    val visibleFiles =
        if (isGameMaster) files
        else
            files.filter { item ->
              canPlayerSee(
                  item = item,
                  userId = userId,
                  groupId = groupId,
                  documentsById = documentsById,
                  hasOwnedDescendant = ::hasOwnedDescendant,
              )
            }

    return visibleFiles.map { item ->
      val parents = mutableListOf<JDRFile>()
      var parentId = item.placements[groupId]?.folderId
      val visited = mutableSetOf<String>()
      while (parentId != null && parentId != groupId && visited.add(parentId)) {
        val parent = documentsById[parentId] ?: break
        parents.add(parent)
        parentId = parent.placements[groupId]?.folderId
      }
      parents.reverse()
      DocumentSearchResult(item, parents.map { it.name }, parents.map { it.id })
    }
  }

  private fun canPlayerSee(
      item: JDRFile,
      userId: String,
      groupId: String,
      documentsById: Map<String, JDRFile>,
      hasOwnedDescendant: (String) -> Boolean,
  ): Boolean {
    if (groupId !in item.sharedGroupsIds) return false
    val placement = item.placements[groupId] ?: return false

    if (item.ownerId == userId) return true
    if (item.isContainer() && hasOwnedDescendant(item.id)) return true
    if (placement.isHidden) return false

    var parentId = placement.folderId
    val visited = mutableSetOf<String>()
    while (parentId != groupId) {
      if (parentId.isBlank() || !visited.add(parentId)) return false
      val parent = documentsById[parentId] ?: return false
      if (groupId !in parent.sharedGroupsIds) return false
      val parentPlacement = parent.placements[groupId] ?: return false
      if (!parent.isContainer() || parentPlacement.isHidden) return false
      parentId = parentPlacement.folderId
    }
    return true
  }

  private class SearchException(val reason: SearchError) : Exception()
}
