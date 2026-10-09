// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.github.se.jdrnexus.screen

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.se.jdrnexus.PersonalSpaceScreen
import com.github.se.jdrnexus.model.personalSpace.DocumentType
import com.github.se.jdrnexus.model.personalSpace.JDRFile
import com.github.se.jdrnexus.model.personalSpace.WorkspaceRepository
import com.github.se.jdrnexus.model.repository.AuthRepository
import com.github.se.jdrnexus.model.repository.AuthResult
import com.github.se.jdrnexus.model.repository.AuthUser
import com.github.se.jdrnexus.viewmodel.PersonalSpaceViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PersonalSpaceScreenTests {
  @get:Rule val composeTestRule = createComposeRule()

  private val files =
      listOf(
          JDRFile(
              id = "ashen-realms",
              name = "The Ashen Realms",
              type = DocumentType.FOLDER,
              content = "8 files · World lore & maps",
          ),
          JDRFile(
              id = "elara-moonwhisper",
              name = "Elara Moonwhisper",
              type = DocumentType.CHARACTER,
              content = "Level 7 · Half-elf ranger",
          ),
          JDRFile(
              id = "sunken-citadel",
              name = "The Sunken Citadel",
              type = DocumentType.TEXT,
              content = "Lore of a forgotten kingdom",
          ),
          JDRFile(
              id = "silver-covenant",
              name = "The Silver Covenant",
              type = DocumentType.FOLDER,
              content = "4 files · Allies & adversaries",
          ),
      )

  private fun viewModel(items: List<JDRFile> = files) =
      PersonalSpaceViewModel(FakeWorkspaceRepository(items), FakeAuthRepository())

  @Test
  fun addButtonOpensViewModelAddMenu() {
    val viewModel = viewModel()
    composeTestRule.setContent {
      PersonalSpaceScreen(
          onBackButton = {},
          personalSpaceViewModel = viewModel,
          onDocumentClicked = {},
          onCreateFile = {},
      )
    }

    composeTestRule.onNodeWithContentDescription("Add item").assertHasClickAction().performClick()

    composeTestRule.onNodeWithText("New folder").assertIsDisplayed()
    assertTrue(viewModel.uiState.value.isAddMenuExpanded)
  }

  @Test
  fun backButtonInvokesCallbackAtRoot() {
    var backButtonClicked = false
    composeTestRule.setContent {
      PersonalSpaceScreen(
          onBackButton = { backButtonClicked = true },
          personalSpaceViewModel = viewModel(),
          onDocumentClicked = {},
          onCreateFile = {},
      )
    }

    composeTestRule.onNodeWithContentDescription("Back").performClick()

    assertTrue(backButtonClicked)
  }

  @Test
  fun viewModelFilesShowTheirVisibleLabelsAndIcons() {
    composeTestRule.setContent {
      PersonalSpaceScreen(
          onBackButton = {},
          personalSpaceViewModel = viewModel(),
          onDocumentClicked = {},
          onCreateFile = {},
      )
    }

    composeTestRule.onNodeWithText("The Ashen Realms").assertIsDisplayed()
    composeTestRule.onAllNodesWithText("FOLDER").assertCountEquals(2)
    composeTestRule
        .onAllNodesWithContentDescription("Folder", useUnmergedTree = true)
        .assertCountEquals(2)

    composeTestRule.onNodeWithText("Elara Moonwhisper").assertIsDisplayed()
    composeTestRule.onNodeWithText("CHARACTER SHEET").assertIsDisplayed()
    composeTestRule
        .onNodeWithContentDescription("Character", useUnmergedTree = true)
        .assertIsDisplayed()

    composeTestRule.onNodeWithText("The Sunken Citadel").assertIsDisplayed()
    composeTestRule.onNodeWithText("TEXT DOCUMENT").assertIsDisplayed()
    composeTestRule
        .onNodeWithContentDescription("Text document", useUnmergedTree = true)
        .assertIsDisplayed()
  }

  @Test
  fun emptyPersonalSpaceShowsEmptyStateWithoutItems() {
    composeTestRule.setContent {
      PersonalSpaceScreen(
          onBackButton = {},
          personalSpaceViewModel = viewModel(emptyList()),
          onDocumentClicked = {},
          onCreateFile = {},
      )
    }

    composeTestRule.onNodeWithText("Your personal space is empty.").assertIsDisplayed()
    composeTestRule.onNodeWithText("The Ashen Realms").assertIsNotDisplayed()
    composeTestRule.onNodeWithText("Elara Moonwhisper").assertIsNotDisplayed()
    composeTestRule.onNodeWithText("The Sunken Citadel").assertIsNotDisplayed()
    composeTestRule.onNodeWithText("The Silver Covenant").assertIsNotDisplayed()
  }

  @Test
  fun clickingFolderNavigatesThroughViewModel() {
    val viewModel = viewModel()
    composeTestRule.setContent {
      PersonalSpaceScreen(
          onBackButton = {},
          personalSpaceViewModel = viewModel,
          onDocumentClicked = {},
          onCreateFile = {},
      )
    }

    composeTestRule.onNodeWithText("The Ashen Realms").performClick()

    assertEquals("ashen-realms", viewModel.uiState.value.currentFolderId)
  }

  @Test
  fun clickingDocumentInvokesDocumentNavigationCallback() {
    var openedDocumentId: String? = null
    composeTestRule.setContent {
      PersonalSpaceScreen(
          onBackButton = {},
          personalSpaceViewModel = viewModel(),
          onDocumentClicked = { openedDocumentId = it },
          onCreateFile = {},
      )
    }

    composeTestRule.onNodeWithText("Elara Moonwhisper").performClick()

    assertEquals("elara-moonwhisper", openedDocumentId)
  }

  private class FakeWorkspaceRepository(items: List<JDRFile>) : WorkspaceRepository {
    private val rootFiles = MutableStateFlow(items)

    override fun getNewUid(): String = "new-folder-id"

    override suspend fun createFile(file: JDRFile): Result<Unit> = Result.success(Unit)

    override suspend fun updateFile(file: JDRFile): Result<Unit> = Result.success(Unit)

    override suspend fun deleteFile(fileId: String): Result<Unit> = Result.success(Unit)

    override suspend fun getFile(fileId: String): Result<JDRFile?> = Result.success(null)

    override fun getFileFlow(fileId: String): Flow<JDRFile?> = flowOf(null)

    override fun getPersonalRootFiles(ownerId: String): Flow<List<JDRFile>> = rootFiles

    override fun getGroupRootFiles(groupId: String): Flow<List<JDRFile>> = flowOf(emptyList())

    override fun getDocumentsInFolder(folderId: String): Flow<List<JDRFile>> = flowOf(emptyList())
  }

  private class FakeAuthRepository : AuthRepository {
    private val user = AuthUser("test-user", "player@example.com", "Player")
    override val authState: StateFlow<AuthUser?> = MutableStateFlow(user)

    override suspend fun signUpWithEmail(
        email: String,
        password: String,
        username: String,
    ): AuthResult<AuthUser> = AuthResult.Success(user)

    override suspend fun signInWithEmail(
        email: String,
        password: String,
    ): AuthResult<AuthUser> = AuthResult.Success(user)

    override suspend fun signInWithGoogle(idToken: String): AuthResult<AuthUser> =
        AuthResult.Success(user)

    override suspend fun signOut(): AuthResult<Unit> = AuthResult.Success(Unit)
  }
}
