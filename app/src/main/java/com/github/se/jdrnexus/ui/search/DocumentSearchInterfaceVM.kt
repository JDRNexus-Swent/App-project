// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.github.se.jdrnexus.ui.search

import com.github.se.jdrnexus.model.personalSpace.JDRFile
import kotlinx.coroutines.flow.StateFlow

/** The workspace whose documents are searched. */
sealed interface DocumentSearchScope {
  /** Searches every document in the signed-in user's Personal Space. */
  data object Personal : DocumentSearchScope

  /** Searches documents visible to the signed-in user in [groupId]. */
  data class Group(val groupId: String) : DocumentSearchScope
}

/** A localized-search-screen state that the UI can translate into a user-facing message. */
enum class SearchError {
  NOT_SIGNED_IN,
  GROUP_ACCESS_DENIED,
  SEARCH_FAILED,
}

/** The current result phase for a document search. */
sealed interface DocumentSearchStatus {
  /** No non-blank query has been submitted. */
  data object Blank : DocumentSearchStatus

  /** A query is being evaluated. */
  data object Loading : DocumentSearchStatus

  /** The query completed and [DocumentSearchUiState.results] is non-empty. */
  data object Results : DocumentSearchStatus

  /** The query completed successfully without matching documents. */
  data object NoResults : DocumentSearchStatus

  /** The query could not be completed; the UI should map the error to a localized string. */
  data class Error(val reason: SearchError) : DocumentSearchStatus
}

/** Observable data needed to render document search results and progress. */
data class DocumentSearchResult(
    val file: JDRFile,
    /** Folder names from the workspace root to the folder containing [file]. */
    val folderPath: List<String>,
)

/** Observable data needed to render document search results and progress. */
data class DocumentSearchUiState(
    val query: String = "",
    val results: List<DocumentSearchResult> = emptyList(),
    val status: DocumentSearchStatus = DocumentSearchStatus.Blank,
)

/** UI-facing contract for searching documents in a personal or group workspace. */
interface DocumentSearchInterfaceVM {
  /** StateFlow containing the current query, matching documents, and search status. */
  val uiState: StateFlow<DocumentSearchUiState>

  /**
   * Replaces the current query and searches the configured workspace. Blank input clears results
   * and returns the screen to [DocumentSearchStatus.Blank].
   */
  fun onQueryChanged(query: String)

  /** Clears the query and any results, returning the screen to [DocumentSearchStatus.Blank]. */
  fun clearQuery()

  /** Repeats the current non-blank query, for example after an error is displayed. */
  fun retry()
}
