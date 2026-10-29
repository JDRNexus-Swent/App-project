// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.ui.game

import com.github.se.jdrnexus.codec.JDRFileJsonCodec
import com.github.se.jdrnexus.model.filerepresentation.CharacterAction
import com.github.se.jdrnexus.model.filerepresentation.CharacterFile
import com.github.se.jdrnexus.model.filerepresentation.CharacterHealthPoints
import com.github.se.jdrnexus.model.filerepresentation.CharacterStat
import com.github.se.jdrnexus.model.filerepresentation.LoreFile
import com.github.se.jdrnexus.model.filerepresentation.LoreSection
import com.github.se.jdrnexus.model.personalSpace.CampaignGroup
import com.github.se.jdrnexus.model.personalSpace.DocumentType
import com.github.se.jdrnexus.model.personalSpace.GroupRepository
import com.github.se.jdrnexus.model.personalSpace.JDRFile
import com.github.se.jdrnexus.model.personalSpace.Placement
import com.github.se.jdrnexus.model.personalSpace.WorkspaceRepository
import com.github.se.jdrnexus.model.repository.AuthError
import com.github.se.jdrnexus.model.repository.AuthRepository
import com.github.se.jdrnexus.model.repository.AuthResult
import com.github.se.jdrnexus.model.repository.AuthUser
import com.github.se.jdrnexus.ui.authentication.MainDispatcherRule
import com.github.se.jdrnexus.ui.game.GamePlayerViewModel.Companion.CHARACTER_REQUIRED_ERROR
import com.github.se.jdrnexus.ui.game.GamePlayerViewModel.Companion.CURRENT_HP_ERROR
import com.github.se.jdrnexus.ui.game.GamePlayerViewModel.Companion.CURRENT_HP_EXCEEDS_MAX_ERROR
import com.github.se.jdrnexus.ui.game.GamePlayerViewModel.Companion.GROUP_NOT_FOUND_ERROR
import com.github.se.jdrnexus.ui.game.GamePlayerViewModel.Companion.SAVE_ERROR
import com.github.se.jdrnexus.ui.game.GamePlayerViewModel.Companion.SIGNED_OUT_ERROR
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// Robolectric provides the real org.json implementation used by JDRFileJsonCodec.
@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class GamePlayerViewModelTest {
  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  private val player = AuthUser("player", null, null)
  private val group = CampaignGroup(id = "group", gameMasterId = "gm")

  private val hero =
      CharacterFile(
          name = "Hero",
          stats = listOf(CharacterStat("strength", "Strength", 16.0)),
          actions = listOf(CharacterAction("slash", "Slash", "Hit")),
          hp = CharacterHealthPoints(current = 10, max = 20),
      )
  private val villain = hero.copy(name = "Villain")
  private val lore = LoreFile("World", listOf(LoreSection("history", "History", "Long ago.")))

  private fun characterFile(
      id: String,
      ownerId: String,
      character: CharacterFile = hero,
      folderId: String = group.id,
      isHidden: Boolean = false,
  ) =
      JDRFile(
          id = id,
          ownerId = ownerId,
          type = DocumentType.CHARACTER,
          content = JDRFileJsonCodec.serializeCharacter(character),
          sharedGroupsIds = listOf(group.id),
          placements = mapOf(group.id to Placement(folderId, isHidden)),
      )

  private fun loreFile(id: String, ownerId: String = group.gameMasterId) =
      JDRFile(
          id = id,
          ownerId = ownerId,
          type = DocumentType.TEXT,
          content = JDRFileJsonCodec.serializeLore(lore),
          sharedGroupsIds = listOf(group.id),
          placements = mapOf(group.id to Placement(group.id)),
      )

  private fun folder(id: String, isHidden: Boolean = false) =
      JDRFile(
          id = id,
          ownerId = group.gameMasterId,
          type = DocumentType.FOLDER,
          sharedGroupsIds = listOf(group.id),
          placements = mapOf(group.id to Placement(group.id, isHidden)),
      )

  private fun createViewModel(
      workspace: FakeWorkspaceRepository,
      groups: FakeGroupRepository = FakeGroupRepository(group),
      auth: FakeAuthRepository = FakeAuthRepository(player),
  ) = GamePlayerViewModel(workspace, groups, auth)

  private fun ownSheetSession(): Pair<GamePlayerViewModel, FakeWorkspaceRepository> {
    val workspace = FakeWorkspaceRepository(group.id to listOf(characterFile("own", player.uid)))
    return createViewModel(workspace) to workspace
  }

  @Test
  fun loadsOwnCharacterAndGameMasterLoreFromTheWholeGroupTree() = runTest {
    val workspace =
        FakeWorkspaceRepository(
            group.id to
                listOf(
                    characterFile("gm-sheet", group.gameMasterId, villain),
                    characterFile("other-player", "someone-else"),
                    characterFile("hidden", group.gameMasterId, isHidden = true),
                    loreFile("lore"),
                    loreFile("other-lore", ownerId = "someone-else"),
                    folder("nested"),
                    folder("secret", isHidden = true),
                ),
            "nested" to listOf(characterFile("own", player.uid, folderId = "nested")),
            "secret" to listOf(loreFile("secret-lore")),
        )
    val viewModel = createViewModel(workspace)

    viewModel.loadSession(group.id)
    assertTrue(viewModel.uiState.value.isLoading)
    advanceUntilIdle()

    val state = viewModel.uiState.value
    assertFalse(state.isLoading)
    assertNull(state.error)
    assertEquals(group.id, state.groupId)
    assertEquals("own", state.character!!.file.id)
    assertEquals(hero, state.character!!.character)
    assertFalse(state.character!!.hasUnsavedChanges)
    assertEquals(listOf("lore"), state.lores.map { it.file.id })
    assertEquals(lore, state.lores.single().lore)
    assertTrue(state.unreadableFiles.isEmpty())
  }

  @Test
  fun reportsUnreadableFilesWithoutFailingTheSession() = runTest {
    val broken = characterFile("broken", player.uid).copy(content = "not json")
    val viewModel =
        createViewModel(
            FakeWorkspaceRepository(group.id to listOf(broken, characterFile("own", player.uid)))
        )

    viewModel.loadSession(group.id)
    advanceUntilIdle()

    assertEquals("own", viewModel.uiState.value.character!!.file.id)
    assertEquals(setOf("broken"), viewModel.uiState.value.unreadableFiles.keys)
    assertNull(viewModel.uiState.value.error)
  }

  @Test
  fun reportsSignedOutMissingGroupAndRepositoryFailures() = runTest {
    val signedOut =
        createViewModel(FakeWorkspaceRepository(), auth = FakeAuthRepository(user = null))
    signedOut.loadSession(group.id)
    advanceUntilIdle()
    assertEquals(SIGNED_OUT_ERROR, signedOut.uiState.value.error)
    assertFalse(signedOut.uiState.value.isLoading)

    val missingGroup = createViewModel(FakeWorkspaceRepository(), FakeGroupRepository(null))
    missingGroup.loadSession(group.id)
    advanceUntilIdle()
    assertEquals(GROUP_NOT_FOUND_ERROR, missingGroup.uiState.value.error)

    val failing =
        createViewModel(FakeWorkspaceRepository(failure = IllegalStateException("Offline")))
    failing.loadSession(group.id)
    advanceUntilIdle()
    assertEquals("Offline", failing.uiState.value.error)
    assertFalse(failing.uiState.value.isLoading)
  }

  @Test
  fun editsTheCharacterLocallyAndMarksItUnsaved() = runTest {
    val (viewModel, _) = ownSheetSession()
    viewModel.loadSession(group.id)
    advanceUntilIdle()

    viewModel.updateCharacterHp(20)

    val selected = viewModel.uiState.value.character!!
    assertTrue(selected.hasUnsavedChanges)
    assertEquals(hero.copy(hp = CharacterHealthPoints(current = 20, max = 20)), selected.character)
    assertNull(viewModel.uiState.value.error)
  }

  @Test
  fun rejectsInvalidHpThroughHpErrorWithoutChangingTheCharacter() = runTest {
    val (viewModel, _) = ownSheetSession()
    viewModel.loadSession(group.id)
    advanceUntilIdle()

    viewModel.updateCharacterHp(-1)
    assertEquals(CURRENT_HP_ERROR, viewModel.uiState.value.hpError)
    viewModel.updateCharacterHp(21)
    assertEquals(CURRENT_HP_EXCEEDS_MAX_ERROR, viewModel.uiState.value.hpError)

    assertNull(viewModel.uiState.value.error)
    val selected = viewModel.uiState.value.character!!
    assertEquals(hero, selected.character)
    assertFalse(selected.hasUnsavedChanges)

    viewModel.updateCharacterHp(5)
    assertNull(viewModel.uiState.value.hpError)
    assertEquals(5, viewModel.uiState.value.character!!.character.hp.current)
  }

  @Test
  fun editsAndSaveRequireTheirOwnCharacter() = runTest {
    // Only someone else's sheet is in the group, so the player has no character.
    val workspace =
        FakeWorkspaceRepository(group.id to listOf(characterFile("gm-sheet", group.gameMasterId)))
    val viewModel = createViewModel(workspace)
    viewModel.loadSession(group.id)
    advanceUntilIdle()

    assertNull(viewModel.uiState.value.character)
    viewModel.updateCharacterHp(5)
    assertEquals(CHARACTER_REQUIRED_ERROR, viewModel.uiState.value.error)
    viewModel.saveCharacter()
    advanceUntilIdle()
    assertEquals(CHARACTER_REQUIRED_ERROR, viewModel.uiState.value.error)
    assertTrue(workspace.updatedFiles.isEmpty())

    viewModel.dismissError()
    assertNull(viewModel.uiState.value.error)
  }

  @Test
  fun savePersistsTheSelectedCharacterAndClearsUnsavedChanges() = runTest {
    val saveGate = CompletableDeferred<Unit>()
    val workspace =
        FakeWorkspaceRepository(
            group.id to listOf(characterFile("own", player.uid)),
            updateGate = saveGate,
        )
    val viewModel = createViewModel(workspace)
    viewModel.loadSession(group.id)
    advanceUntilIdle()
    viewModel.updateCharacterHp(3)

    viewModel.saveCharacter()
    advanceUntilIdle()
    assertTrue(viewModel.uiState.value.isSaving)
    viewModel.saveCharacter()

    saveGate.complete(Unit)
    advanceUntilIdle()

    val saved = workspace.updatedFiles.single()
    assertEquals("own", saved.id)
    assertEquals(
        hero.copy(hp = CharacterHealthPoints(3, 20)),
        JDRFileJsonCodec.deserializeCharacter(saved.content),
    )
    assertFalse(viewModel.uiState.value.isSaving)
    assertFalse(viewModel.uiState.value.character!!.hasUnsavedChanges)
    assertNull(viewModel.uiState.value.error)
  }

  @Test
  fun failedSaveReportsTheErrorAndKeepsLocalEdits() = runTest {
    val workspace =
        FakeWorkspaceRepository(
            group.id to listOf(characterFile("own", player.uid)),
            updateResult = Result.failure(IllegalStateException("")),
        )
    val viewModel = createViewModel(workspace)
    viewModel.loadSession(group.id)
    advanceUntilIdle()
    viewModel.updateCharacterHp(3)

    viewModel.saveCharacter()
    advanceUntilIdle()

    assertEquals(SAVE_ERROR, viewModel.uiState.value.error)
    assertFalse(viewModel.uiState.value.isSaving)
    val selected = viewModel.uiState.value.character!!
    assertTrue(selected.hasUnsavedChanges)
    assertEquals(3, selected.character.hp.current)
  }

  @Test
  fun remoteUpdatesReplaceASavedCharacterButKeepUnsavedEdits() = runTest {
    val (viewModel, workspace) = ownSheetSession()
    viewModel.loadSession(group.id)
    advanceUntilIdle()

    val remoteHero = hero.copy(name = "Changed elsewhere")
    workspace.folders.getValue(group.id).value =
        listOf(characterFile("own", player.uid, remoteHero))
    advanceUntilIdle()
    assertEquals(remoteHero, viewModel.uiState.value.character!!.character)

    viewModel.updateCharacterHp(1)
    workspace.folders.getValue(group.id).value = listOf(characterFile("own", player.uid, villain))
    advanceUntilIdle()

    val own = viewModel.uiState.value.character!!
    assertEquals(remoteHero.copy(hp = CharacterHealthPoints(1, 20)), own.character)
    assertTrue(own.hasUnsavedChanges)
  }

  @Test
  fun characterIsClearedWhenItsSheetDisappears() = runTest {
    val (viewModel, workspace) = ownSheetSession()
    viewModel.loadSession(group.id)
    advanceUntilIdle()

    workspace.folders.getValue(group.id).value = emptyList()
    advanceUntilIdle()

    assertNull(viewModel.uiState.value.character)
  }

  @Test
  fun loadingAnotherSessionReplacesThePreviousOne() = runTest {
    val other = CampaignGroup(id = "other", gameMasterId = "gm")
    val workspace =
        FakeWorkspaceRepository(
            group.id to listOf(characterFile("own", player.uid)),
            other.id to emptyList(),
        )
    val viewModel = createViewModel(workspace, FakeGroupRepository(group, other))
    viewModel.loadSession(group.id)
    advanceUntilIdle()

    viewModel.loadSession(other.id)
    advanceUntilIdle()
    assertEquals(other.id, viewModel.uiState.value.groupId)
    assertNull(viewModel.uiState.value.character)

    workspace.folders.getValue(group.id).value = listOf(characterFile("late", player.uid))
    advanceUntilIdle()
    assertNull(viewModel.uiState.value.character)
  }

  private class FakeWorkspaceRepository(
      vararg folderContents: Pair<String, List<JDRFile>>,
      private val failure: Exception? = null,
      private val updateResult: Result<Unit> = Result.success(Unit),
      private val updateGate: CompletableDeferred<Unit>? = null,
  ) : WorkspaceRepository by UnusedWorkspaceRepository {
    val folders = folderContents.associate { (id, files) -> id to MutableStateFlow(files) }
    val updatedFiles = mutableListOf<JDRFile>()

    override fun getGroupFolderFiles(folderId: String, groupId: String): Flow<List<JDRFile>> {
      failure?.let {
        return flow { throw it }
      }
      return folders[folderId] ?: MutableStateFlow(emptyList())
    }

    override suspend fun updateFile(file: JDRFile): Result<Unit> {
      updatedFiles += file
      updateGate?.await()
      return updateResult
    }
  }

  private class FakeGroupRepository(vararg groups: CampaignGroup?) :
      GroupRepository by UnusedGroupRepository {
    private val groupsById = groups.filterNotNull().associateBy { it.id }

    override fun getGroupFlow(groupId: String): Flow<CampaignGroup?> =
        MutableStateFlow(groupsById[groupId])
  }

  private class FakeAuthRepository(user: AuthUser?) : AuthRepository {
    override val authState: Flow<AuthUser?> = MutableStateFlow(user)

    override suspend fun signUpWithEmail(email: String, password: String, username: String) =
        AuthResult.Failure(AuthError.UNKNOWN)

    override suspend fun signInWithEmail(email: String, password: String) =
        AuthResult.Failure(AuthError.UNKNOWN)

    override suspend fun signInWithGoogle(idToken: String) = AuthResult.Failure(AuthError.UNKNOWN)

    override suspend fun signOut() = AuthResult.Success(Unit)
  }

  private companion object {
    val UnusedWorkspaceRepository =
        object : WorkspaceRepository {
          override fun getNewUid() = error("Not used")

          override suspend fun createFile(file: JDRFile) = error("Not used")

          override suspend fun updateFile(file: JDRFile) = error("Not used")

          override suspend fun deleteFile(fileId: String) = error("Not used")

          override suspend fun getFile(fileId: String) = error("Not used")

          override suspend fun removeItemFromGroup(
              itemId: String,
              groupId: String,
              parentFolderId: String,
          ) = error("Not used")

          override suspend fun addItemToGroup(
              itemId: String,
              groupId: String,
              folderId: String,
              isHidden: Boolean,
              includeContent: Boolean,
          ) = error("Not used")

          override suspend fun setItemHidden(itemId: String, groupId: String, isHidden: Boolean) =
              error("Not used")

          override fun getFileFlow(fileId: String) = error("Not used")

          override fun getPersonalRootFiles(ownerId: String) = error("Not used")

          override fun getPersonalFolderFiles(folderId: String, ownerId: String) = error("Not used")

          override fun getGroupFolderFiles(folderId: String, groupId: String) = error("Not used")
        }

    val UnusedGroupRepository =
        object : GroupRepository {
          override fun getNewGroupId() = error("Not used")

          override suspend fun createGroup(group: CampaignGroup) = error("Not used")

          override suspend fun updateGroup(group: CampaignGroup) = error("Not used")

          override suspend fun deleteGroup(groupId: String) = error("Not used")

          override suspend fun getGroup(groupId: String) = error("Not used")

          override suspend fun addUserToGroup(groupId: String, userId: String) = error("Not used")

          override suspend fun removeUserFromGroup(groupId: String, userId: String) =
              error("Not used")

          override fun getGroupFlow(groupId: String) = error("Not used")

          override fun getUserGroups(userId: String) = error("Not used")
        }
  }
}
