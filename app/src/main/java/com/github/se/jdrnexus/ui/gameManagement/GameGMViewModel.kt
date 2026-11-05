// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.github.se.jdrnexus.ui.gameManagement

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.github.se.jdrnexus.model.filerepresentation.CharacterHealthPoints
import com.github.se.jdrnexus.model.filerepresentation.LoreSection
import com.github.se.jdrnexus.model.gameManagement.GameActivityEntry
import com.github.se.jdrnexus.model.gameManagement.GameCombatNPC
import com.github.se.jdrnexus.model.gameManagement.GameLoreEntry
import com.github.se.jdrnexus.model.gameManagement.GameNpc
import com.github.se.jdrnexus.model.gameManagement.GameSessionRepository
import com.github.se.jdrnexus.model.gameManagement.GameSessionSnapshot
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Factory for creating a GM session ViewModel with repository interfaces. */
class GameViewModelGMViewModelFactory(
    private val sessionRepository: GameSessionRepository,
    private val groupId: String,
) : ViewModelProvider.Factory {

  @Suppress("UNCHECKED_CAST")
  override fun <T : ViewModel> create(modelClass: Class<T>): T {
    if (modelClass.isAssignableFrom(GameViewModelGMViewModel::class.java)) {
      return GameViewModelGMViewModel(sessionRepository, groupId) as T
    }
    throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
  }
}

/**
 * Coordinates the GM screen with repository-backed campaign state.
 *
 * Writes are serialized and only reflected in the UI after the repository accepts them. Firestore
 * rules remain the authority for GM-only writes; failed permission checks are exposed as UI errors.
 */
class GameViewModelGMViewModel(
    private val sessionRepository: GameSessionRepository,
    private val groupId: String,
) : ViewModel(), GameViewModelGM {

  private val _uiState = MutableStateFlow(GameViewModelGMUiState(isLoading = true))
  override val uiState: StateFlow<GameViewModelGMUiState> = _uiState.asStateFlow()

  private val writeMutex = Mutex()
  private var sessionJob: Job? = null
  private var loreJob: Job? = null
  private var activityJob: Job? = null
  private var sessionLoaded = false
  private var sessionLoadCompleted = false
  private var loreLoaded = false
  private var activityLoaded = false

  init {
    observeSession()
    observeLoreDocuments()
    observeActivityFeed()
  }

  override fun addEnemy(enemy: CombatNPC) {
    mutateSession { session ->
      require(enemy.id.isNotBlank()) { "Enemy ID cannot be empty." }
      require(enemy.name.isNotBlank()) { "Enemy name cannot be empty." }
      require(session.enemies.none { it.id == enemy.id }) { "Enemy already exists." }
      session.copy(enemies = session.enemies + enemy.toRepositoryEnemy())
    }
  }

  override fun removeEnemy(enemyId: String) {
    mutateSession { session ->
      session.copy(enemies = session.enemies.filterNot { it.id == enemyId })
    }
  }

  override fun updateEnemyHP(enemyId: String, change: Int) {
    mutateSession { session ->
      val enemy = session.enemies.find { it.id == enemyId } ?: return@mutateSession session
      val hp =
          (enemy.character.hp.current.toLong() + change)
              .coerceIn(0L, enemy.character.hp.max.toLong())
              .toInt()
      val updatedEnemy =
          enemy.copy(
              character =
                  enemy.character.copy(
                      hp = CharacterHealthPoints(hp, enemy.character.hp.max),
                  )
          )
      session.copy(enemies = session.enemies.map { if (it.id == enemyId) updatedEnemy else it })
    }
  }

  override fun nextRound() {
    mutateSession { session ->
      if (!session.isCombatActive) session
      else {
        check(session.currentRound < Int.MAX_VALUE) { "Combat round cannot be advanced further." }
        session.copy(currentRound = session.currentRound + 1)
      }
    }
  }

  override fun startCombat() {
    mutateSession { session ->
      require(!session.isCombatActive) { "Combat is already active." }
      session.copy(isCombatActive = true, currentRound = 1)
    }
  }

  override fun endCombat() {
    mutateSession { session ->
      if (!session.isCombatActive) session else session.copy(isCombatActive = false)
    }
  }

  override fun loadActivityFeed() {
    activityLoaded = false
    refreshLoading()
    viewModelScope.launch {
      _uiState.update { it.copy(error = "") }
      try {
        sessionRepository
            .loadActivities(groupId)
            .fold(
                onSuccess = { rolls -> setActivityFeed(rolls) },
                onFailure = { failure -> reportError(failure, ACTIVITY_LOAD_ERROR) },
            )
      } catch (cancellation: CancellationException) {
        throw cancellation
      } catch (failure: Exception) {
        reportError(failure, ACTIVITY_LOAD_ERROR)
      } finally {
        markActivityLoaded()
      }
    }
  }

  override fun observeActivityFeed() {
    if (activityJob?.isActive == true) return
    activityLoaded = false
    refreshLoading()
    activityJob = viewModelScope.launch {
      sessionRepository
          .observeActivities(groupId)
          .catch { failure ->
            reportError(failure, ACTIVITY_SYNC_ERROR)
            markActivityLoaded()
          }
          .collect { rolls ->
            setActivityFeed(rolls)
            markActivityLoaded()
          }
    }
  }

  override fun loadNPCs() {
    observeSession()
  }

  override fun addNPC(npc: Npc) {
    mutateSession { session ->
      require(npc.id.isNotBlank()) { "NPC ID cannot be empty." }
      require(npc.name.isNotBlank()) { "NPC name cannot be empty." }
      require(session.npcs.none { it.id == npc.id }) { "NPC already exists." }
      session.copy(npcs = session.npcs + npc.toRepositoryNpc())
    }
  }

  override fun updateNPC(npcId: String, updatedNPC: Npc) {
    mutateSession { session ->
      require(updatedNPC.id == npcId) { "NPC ID cannot be changed." }
      require(updatedNPC.name.isNotBlank()) { "NPC name cannot be empty." }
      require(session.npcs.any { it.id == npcId }) { "NPC was not found." }
      session.copy(
          npcs = session.npcs.map { if (it.id == npcId) updatedNPC.toRepositoryNpc() else it }
      )
    }
  }

  override fun removeNPC(npcId: String) {
    mutateSession { session -> session.copy(npcs = session.npcs.filterNot { it.id == npcId }) }
  }

  override fun loadLoreDocuments() {
    observeLoreDocuments()
  }

  override fun selectLoreDocument(documentId: String) {
    _uiState.update { state ->
      state.copy(
          selectedLoreDocument = state.loreDocuments.find { it.id == documentId },
          hasUnsavedLoreChanges = false,
      )
    }
  }

  override fun updateLoreTitle(title: String) {
    updateSelectedLoreDocument { document ->
      document.copy(lore = document.lore.copy(title = title))
    }
  }

  override fun addLoreSection(title: String, description: String) {
    if (title.isBlank()) {
      reportError(IllegalArgumentException(LORE_SECTION_TITLE_ERROR), LORE_EDIT_ERROR)
      return
    }
    val section = LoreSection(UUID.randomUUID().toString(), title, description)
    updateSelectedLoreDocument { document ->
      document.copy(lore = document.lore.copy(sections = document.lore.sections + section))
    }
  }

  override fun updateLoreSectionTitle(sectionID: String, title: String) {
    updateLoreSection(sectionID) { it.copy(title = title) }
  }

  override fun updateLoreSectionDescription(sectionID: String, description: String) {
    updateLoreSection(sectionID) { it.copy(description = description) }
  }

  override fun removeLoreSection(sectionID: String) {
    updateSelectedLoreDocument { document ->
      if (document.lore.sections.none { it.sectionID == sectionID }) {
        reportError(IllegalArgumentException(LORE_SECTION_NOT_FOUND_ERROR), LORE_EDIT_ERROR)
        null
      } else {
        document.copy(
            lore =
                document.lore.copy(
                    sections = document.lore.sections.filterNot { it.sectionID == sectionID }
                )
        )
      }
    }
  }

  override fun saveLoreDocument(document: LoreDocument) {
    viewModelScope.launch {
      if (document.id.isBlank() || document.lore.title.isBlank()) {
        reportError(IllegalArgumentException("Lore ID and title are required."), LORE_SAVE_ERROR)
        return@launch
      }
      try {
        sessionRepository
            .saveLore(groupId, document.toRepositoryLore())
            .fold(
                onSuccess = {
                  _uiState.update { state ->
                    val documents =
                        state.loreDocuments.filterNot { it.id == document.id } + document
                    val selected =
                        if (state.selectedLoreDocument?.id == document.id) document
                        else state.selectedLoreDocument
                    state.copy(
                        loreDocuments = documents,
                        selectedLoreDocument = selected,
                        hasUnsavedLoreChanges =
                            if (state.selectedLoreDocument?.id == document.id) false
                            else state.hasUnsavedLoreChanges,
                        error = "",
                    )
                  }
                },
                onFailure = { failure -> reportError(failure, LORE_SAVE_ERROR) },
            )
      } catch (cancellation: CancellationException) {
        throw cancellation
      } catch (failure: Exception) {
        reportError(failure, LORE_SAVE_ERROR)
      }
    }
  }

  override fun setLoreVisibility(documentId: String, isShared: Boolean) {
    viewModelScope.launch {
      if (_uiState.value.loreDocuments.none { it.id == documentId }) {
        reportError(IllegalArgumentException("Lore document was not found."), LORE_VISIBILITY_ERROR)
        return@launch
      }
      try {
        sessionRepository
            .setLoreVisibility(groupId, documentId, isShared)
            .fold(
                onSuccess = {
                  _uiState.update { state ->
                    val documents =
                        state.loreDocuments.map {
                          if (it.id == documentId) it.copy(isShared = isShared) else it
                        }
                    val selected =
                        state.selectedLoreDocument?.let {
                          if (it.id == documentId) it.copy(isShared = isShared) else it
                        }
                    state.copy(
                        loreDocuments = documents,
                        selectedLoreDocument = selected,
                        error = "",
                    )
                  }
                },
                onFailure = { failure -> reportError(failure, LORE_VISIBILITY_ERROR) },
            )
      } catch (cancellation: CancellationException) {
        throw cancellation
      } catch (failure: Exception) {
        reportError(failure, LORE_VISIBILITY_ERROR)
      }
    }
  }

  /** Observes the campaign session and updates the UI state as snapshots arrive. */
  private fun observeSession() {
    if (sessionJob?.isActive == true) return
    sessionLoaded = false
    sessionLoadCompleted = false
    refreshLoading()
    sessionJob = viewModelScope.launch {
      sessionRepository
          .observeSession(groupId)
          .catch { failure ->
            reportError(failure, SESSION_LOAD_ERROR)
            markSessionLoadCompleted()
          }
          .collect { session ->
            _uiState.update { it.withSession(session) }
            markSessionLoaded()
          }
    }
  }

  /** Observes campaign lore and updates the document list while preserving the selection. */
  private fun observeLoreDocuments() {
    if (loreJob?.isActive == true) return
    loreLoaded = false
    refreshLoading()
    loreJob = viewModelScope.launch {
      sessionRepository
          .observeLore(groupId)
          .catch { failure ->
            reportError(failure, LORE_LOAD_ERROR)
            markLoreLoaded()
          }
          .collect { entries ->
            _uiState.update { state ->
              val documents = entries.map { it.toUiLore() }
              state.copy(
                  loreDocuments = documents,
                  selectedLoreDocument =
                      state.selectedLoreDocument?.let { selected ->
                        if (state.hasUnsavedLoreChanges) selected
                        else documents.find { it.id == selected.id }
                      },
              )
            }
            markLoreLoaded()
          }
    }
  }

  /**
   * Serializes a session change, validates the resulting snapshot, and saves it to the repository.
   *
   * Changes are rejected until the session is loaded; failures are reported in the UI state.
   */
  private fun mutateSession(change: (GameSessionSnapshot) -> GameSessionSnapshot) {
    viewModelScope.launch {
      // ensures thread safety
      writeMutex.withLock {
        if (!sessionLoaded) {
          reportError(IllegalStateException("Campaign session is not loaded."), SESSION_SAVE_ERROR)
          return@withLock
        }
        val current = _uiState.value.toSession()
        try {
          val updated = change(current)
          updated.validate()
          if (updated == current) return@withLock
          sessionRepository
              .saveSession(groupId, updated)
              .fold(
                  onSuccess = { _uiState.update { it.withSession(updated).copy(error = "") } },
                  onFailure = { failure -> reportError(failure, SESSION_SAVE_ERROR) },
              )
        } catch (cancellation: CancellationException) {
          throw cancellation
        } catch (failure: Exception) {
          reportError(failure, SESSION_SAVE_ERROR)
        }
      }
    }
  }

  /** Maps repository activity entries to UI items and updates the activity feed. */
  private fun setActivityFeed(entries: List<GameActivityEntry>) {
    val activities = entries.map { entry ->
      ActivityItem(
          id = entry.id,
          playerName = entry.playerName,
          action = entry.action,
          diceResult = entry.diceResult,
          timestamp = entry.timestamp,
      )
    }
    _uiState.update { it.copy(activityFeed = activities) }
  }

  /** Marks a session snapshot as available for mutations and finishes initial loading. */
  private fun markSessionLoaded() {
    sessionLoaded = true
    markSessionLoadCompleted()
  }

  /** Finishes initial loading without implying that a session snapshot was received. */
  private fun markSessionLoadCompleted() {
    sessionLoadCompleted = true
    refreshLoading()
  }

  /** Marks campaign lore as loaded and refreshes the screen loading state. */
  private fun markLoreLoaded() {
    loreLoaded = true
    refreshLoading()
  }

  /** Marks the activity feed as loaded and refreshes the screen loading state. */
  private fun markActivityLoaded() {
    activityLoaded = true
    refreshLoading()
  }

  /** Keeps the screen loading until the session, lore, and activity feed have all loaded. */
  private fun refreshLoading() {
    _uiState.update { it.copy(isLoading = !(sessionLoadCompleted && loreLoaded && activityLoaded)) }
  }

  /** Exposes a failure message in the UI state, using [fallback] when none is available. */
  private fun reportError(failure: Throwable, fallback: String) {
    val message = failure.message?.takeIf(String::isNotBlank) ?: fallback
    _uiState.update { it.copy(error = message) }
  }

  private fun updateLoreSection(sectionID: String, transform: (LoreSection) -> LoreSection) {
    updateSelectedLoreDocument { document ->
      if (document.lore.sections.none { it.sectionID == sectionID }) {
        reportError(IllegalArgumentException(LORE_SECTION_NOT_FOUND_ERROR), LORE_EDIT_ERROR)
        null
      } else {
        document.copy(
            lore =
                document.lore.copy(
                    sections =
                        document.lore.sections.map { section ->
                          if (section.sectionID == sectionID) transform(section) else section
                        }
                )
        )
      }
    }
  }

  private fun updateSelectedLoreDocument(transform: (LoreDocument) -> LoreDocument?) {
    val state = _uiState.value
    val document =
        state.selectedLoreDocument
            ?: return reportError(
                IllegalStateException(LORE_DOCUMENT_REQUIRED_ERROR),
                LORE_EDIT_ERROR,
            )
    val updated = transform(document) ?: return
    _uiState.value =
        state.copy(
            selectedLoreDocument = updated,
            hasUnsavedLoreChanges = true,
            error = "",
        )
  }

  /** Converts the current screen state into a repository session snapshot. */
  private fun GameViewModelGMUiState.toSession() =
      GameSessionSnapshot(
          enemies = enemies.map { it.toRepositoryEnemy() },
          isCombatActive = isCombatActive,
          currentRound = currentRound,
          npcs = npcs.map { it.toRepositoryNpc() },
      )

  /** Copies session data into this UI state while preserving unrelated screen state. */
  private fun GameViewModelGMUiState.withSession(session: GameSessionSnapshot) =
      copy(
          enemies = session.enemies.map { it.toUiEnemy() },
          isCombatActive = session.isCombatActive,
          currentRound = session.currentRound,
          npcs = session.npcs.map { it.toUiNpc() },
      )

  /** Converts this UI enemy to its repository model. */
  private fun CombatNPC.toRepositoryEnemy() = GameCombatNPC(id, character, initiative)

  /** Converts this repository enemy to its UI model. */
  private fun GameCombatNPC.toUiEnemy() = CombatNPC(id, character, initiative)

  /** Converts this UI NPC to its repository model. */
  private fun Npc.toRepositoryNpc() = GameNpc(id, name, portraitUrl, information)

  /** Converts this repository NPC to its UI model. */
  private fun GameNpc.toUiNpc() = Npc(id, name, portraitUrl, information)

  /** Converts this UI lore document to its repository model. */
  private fun LoreDocument.toRepositoryLore() = GameLoreEntry(id, lore, isShared)

  /** Converts this repository lore entry to its UI model. */
  private fun GameLoreEntry.toUiLore() = LoreDocument(id, lore, isShared)

  private companion object {
    const val SESSION_LOAD_ERROR = "Failed to load the campaign session."
    const val SESSION_SAVE_ERROR = "Failed to save the campaign session."
    const val LORE_LOAD_ERROR = "Failed to load campaign lore."
    const val LORE_EDIT_ERROR = "Failed to edit campaign lore."
    const val LORE_SAVE_ERROR = "Failed to save campaign lore."
    const val LORE_DOCUMENT_REQUIRED_ERROR = "Select a lore document before editing."
    const val LORE_SECTION_TITLE_ERROR = "Lore section title cannot be empty."
    const val LORE_SECTION_NOT_FOUND_ERROR = "Lore section was not found."
    const val LORE_VISIBILITY_ERROR = "Failed to update lore visibility."
    const val ACTIVITY_LOAD_ERROR = "Failed to load the activity feed."
    const val ACTIVITY_SYNC_ERROR = "Failed to synchronize the activity feed."
  }
}
