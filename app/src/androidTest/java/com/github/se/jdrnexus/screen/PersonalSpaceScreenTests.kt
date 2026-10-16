// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.screen

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composer
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.CompositionTracer
import androidx.compose.runtime.InternalComposeTracingApi
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.se.jdrnexus.FolderItem
import com.github.se.jdrnexus.PersonalSpaceScreen
import com.github.se.jdrnexus.model.personalSpace.DocumentType
import com.github.se.jdrnexus.model.personalSpace.JDRFile
import com.github.se.jdrnexus.model.personalSpace.WorkspaceRepository
import com.github.se.jdrnexus.model.repository.AuthRepository
import com.github.se.jdrnexus.model.repository.AuthResult
import com.github.se.jdrnexus.model.repository.AuthUser
import com.github.se.jdrnexus.viewmodel.PersonalSpaceViewModel
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(InternalComposeTracingApi::class)
@RunWith(AndroidJUnit4::class)
class PersonalSpaceScreenTests {
  @get:Rule val composeTestRule = createComposeRule()

  /**
   * The Compose compiler wraps every composable in `if (isTraceInProgress()) trace...` checks. This
   * no-op tracer answers alternately true and false, so both sides of those checks run during the
   * tests below; nothing is recorded.
   */
  private object AlternatingNoOpTracer : CompositionTracer {
    private var calls = 0

    override fun isTraceInProgress(): Boolean = calls++ % 2 == 0

    override fun traceEventStart(key: Int, dirty1: Int, dirty2: Int, info: String) {}

    override fun traceEventEnd() {}
  }

  private object DisabledTracer : CompositionTracer {
    override fun isTraceInProgress(): Boolean = false

    override fun traceEventStart(key: Int, dirty1: Int, dirty2: Int, info: String) {}

    override fun traceEventEnd() {}
  }

  @Before
  fun enableCompositionTracing() {
    Composer.setTracer(AlternatingNoOpTracer)
  }

  @After
  fun disableCompositionTracing() {
    Composer.setTracer(DisabledTracer)
  }

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
      PersonalSpaceViewModel(FakeWorkspaceRepository(flowOf(items)), FakeAuthRepository())

  private fun setScreen(
      viewModel: PersonalSpaceViewModel = viewModel(),
      onBackButton: () -> Unit = {},
      onDocumentClicked: (JDRFile) -> Unit = {},
      onCreateFile: (String) -> Unit = {},
  ) {
    composeTestRule.setContent {
      PersonalSpaceScreen(
          onBackButton = onBackButton,
          personalSpaceViewModel = viewModel,
          onDocumentClicked = onDocumentClicked,
          onCreateFile = onCreateFile,
      )
    }
  }

  /** Forces the system theme seen by [content], whatever mode the device is in. */
  @Composable
  private fun WithNightMode(night: Boolean, content: @Composable () -> Unit) {
    val configuration =
        Configuration(LocalConfiguration.current).apply {
          uiMode =
              (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                  if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        }
    CompositionLocalProvider(LocalConfiguration provides configuration, content = content)
  }

  @Test
  fun addButtonOpensViewModelAddMenu() {
    val viewModel = viewModel()
    assertFalse(viewModel.uiState.value.isAddMenuExpanded)
    setScreen(viewModel)

    composeTestRule.onNodeWithContentDescription("Add item").assertHasClickAction().performClick()

    composeTestRule.onNodeWithText("New folder").assertIsDisplayed()
    composeTestRule.onNodeWithText("New file").assertIsDisplayed()
    assertTrue(viewModel.uiState.value.isAddMenuExpanded)
  }

  @Test
  fun newFileClosesMenuAndRequestsFileCreationInCurrentFolder() {
    val viewModel = viewModel()
    var createdInFolderId: String? = null
    setScreen(viewModel, onCreateFile = { createdInFolderId = it })

    composeTestRule.onNodeWithContentDescription("Add item").performClick()
    composeTestRule.onNodeWithText("New file").performClick()
    composeTestRule.waitForIdle()

    assertEquals("", createdInFolderId)
    assertFalse(viewModel.uiState.value.isAddMenuExpanded)
  }

  @Test
  fun newFolderDialogShowsValidationAndCreatesFolder() {
    val viewModel = viewModel()
    setScreen(viewModel)

    composeTestRule.onNodeWithContentDescription("Add item").performClick()
    composeTestRule.onNodeWithText("New folder").performClick()
    composeTestRule.waitForIdle()

    assertTrue(viewModel.uiState.value.isNewFolderDialogOpen)
    composeTestRule.onNodeWithText("Folder name").assertIsDisplayed()
    composeTestRule.onNodeWithText("Create").assertIsDisplayed()
    composeTestRule.onNodeWithText("Cancel").assertIsDisplayed()

    composeTestRule.onNodeWithText("Create").performClick()
    composeTestRule.waitForIdle()

    assertEquals("Folder name cannot be empty.", viewModel.uiState.value.newFolderError)
    composeTestRule.onNodeWithText("Folder name cannot be empty.").assertIsDisplayed()

    composeTestRule.onNode(hasSetTextAction()).performTextInput("New campaign")
    composeTestRule.waitForIdle()

    assertEquals("New campaign", viewModel.uiState.value.newFolderName)
    // The error Text is removed from the composition once cleared, so it no longer exists.
    composeTestRule.onNodeWithText("Folder name cannot be empty.").assertDoesNotExist()

    composeTestRule.onNodeWithText("Create").performClick()
    composeTestRule.waitForIdle()

    assertFalse(viewModel.uiState.value.isNewFolderDialogOpen)
    composeTestRule.onNodeWithText("Folder name").assertDoesNotExist()
  }

  @Test
  fun cancelingNewFolderDialogClosesAndClearsIt() {
    val viewModel = viewModel()
    setScreen(viewModel)

    composeTestRule.onNodeWithContentDescription("Add item").performClick()
    composeTestRule.onNodeWithText("New folder").performClick()

    composeTestRule.onNode(hasSetTextAction()).performTextInput("Draft")
    composeTestRule.waitForIdle()
    assertEquals("Draft", viewModel.uiState.value.newFolderName)

    composeTestRule.onNodeWithText("Cancel").performClick()
    composeTestRule.waitForIdle()

    assertFalse(viewModel.uiState.value.isNewFolderDialogOpen)
    assertEquals("", viewModel.uiState.value.newFolderName)
    // The dialog leaves the composition when closed, so its field no longer exists.
    composeTestRule.onNodeWithText("Folder name").assertDoesNotExist()
  }

  @Test
  fun backButtonInvokesCallbackAtRoot() {
    var backButtonClicked = false
    setScreen(onBackButton = { backButtonClicked = true })

    composeTestRule.onNodeWithContentDescription("Back").performClick()

    assertTrue(backButtonClicked)
  }

  @Test
  fun backButtonInsideFolderGoesUpInsteadOfLeaving() {
    val viewModel = viewModel()
    var backButtonClicked = false
    setScreen(viewModel, onBackButton = { backButtonClicked = true })

    composeTestRule.onNodeWithText("The Ashen Realms").performClick()
    composeTestRule.onNodeWithText("This folder is empty.").assertIsDisplayed()

    composeTestRule.onNodeWithContentDescription("Back").performClick()
    composeTestRule.waitForIdle()

    assertFalse(backButtonClicked)
    assertEquals("", viewModel.uiState.value.currentFolderId)
    composeTestRule.onNodeWithText("The Ashen Realms").assertIsDisplayed()
  }

  @Test
  fun viewModelFilesShowTheirVisibleLabelsAndIcons() {
    setScreen()

    composeTestRule.onNodeWithText("4 items").assertIsDisplayed()

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
  fun bothThemesShowTheFiles() {
    val viewModel = viewModel()
    var night by mutableStateOf(true)
    composeTestRule.setContent {
      WithNightMode(night) {
        PersonalSpaceScreen(
            onBackButton = {},
            personalSpaceViewModel = viewModel,
            onDocumentClicked = {},
            onCreateFile = {},
        )
      }
    }

    composeTestRule.onNodeWithText("The Ashen Realms").assertIsDisplayed()

    night = false
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithText("Personal Space").assertIsDisplayed()
    composeTestRule.onNodeWithText("The Ashen Realms").assertIsDisplayed()
  }

  @Test
  fun headerShowsDraftFolderNameWhenSet() {
    val viewModel = viewModel()
    viewModel.onNewFolderNameChanged("Campaign")
    setScreen(viewModel)

    composeTestRule.onAllNodesWithText("Campaign").assertCountEquals(2)
    composeTestRule.onNodeWithText("Personal Space").assertDoesNotExist()
  }

  @Test
  fun emptyPersonalSpaceShowsEmptyStateWithoutItems() {
    setScreen(viewModel(emptyList()))

    composeTestRule.onNodeWithText("Your personal space is empty.").assertIsDisplayed()
    composeTestRule.onNodeWithText("The Ashen Realms").assertIsNotDisplayed()
    composeTestRule.onNodeWithText("Elara Moonwhisper").assertIsNotDisplayed()
    composeTestRule.onNodeWithText("The Sunken Citadel").assertIsNotDisplayed()
    composeTestRule.onNodeWithText("The Silver Covenant").assertIsNotDisplayed()
  }

  @Test
  fun loadingStateShowsProgressIndicator() {
    val repository = FakeWorkspaceRepository(flow { awaitCancellation() })
    setScreen(PersonalSpaceViewModel(repository, FakeAuthRepository()))

    composeTestRule
        .onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate))
        .assertExists()
    composeTestRule.onNodeWithText("Your personal space is empty.").assertDoesNotExist()
  }

  @Test
  fun errorStateShowsMessageAndRetryFetchesAgain() {
    val repository = FakeWorkspaceRepository(flow { throw IllegalStateException("Network down") })
    setScreen(PersonalSpaceViewModel(repository, FakeAuthRepository()))

    composeTestRule.onNodeWithText("Network down").assertIsDisplayed()
    assertEquals(1, repository.rootFetchCount)

    composeTestRule.onNodeWithText("Retry").performClick()
    composeTestRule.waitForIdle()

    assertEquals(2, repository.rootFetchCount)
    composeTestRule.onNodeWithText("Network down").assertIsDisplayed()
  }

  @Test
  fun screenKeepsContentWhenParentRecomposes() {
    val viewModel = viewModel()
    var parentTick by mutableIntStateOf(0)
    var onBack by mutableStateOf({})
    composeTestRule.setContent {
      parentTick.let {
        PersonalSpaceScreen(
            onBackButton = onBack,
            personalSpaceViewModel = viewModel,
            onDocumentClicked = {},
            onCreateFile = {},
        )
      }
    }

    // Recompose the parent with unchanged arguments, then with a changed one.
    parentTick++
    composeTestRule.waitForIdle()
    var backButtonClicked = false
    onBack = { backButtonClicked = true }
    composeTestRule.waitForIdle()
    // Toggle the screen's own state so its children recompose too.
    composeTestRule.runOnIdle { viewModel.onAddClicked() }
    composeTestRule.runOnIdle { viewModel.onAddMenuDismissed() }
    composeTestRule.runOnIdle { viewModel.onNewFolderNameChanged("Campaign") }

    composeTestRule.onAllNodesWithText("Campaign").assertCountEquals(2)
    composeTestRule.onNodeWithContentDescription("Back").performClick()
    assertTrue(backButtonClicked)
  }

  @Test
  fun clickingFolderNavigatesThroughViewModel() {
    val viewModel = viewModel()
    setScreen(viewModel)

    composeTestRule.onNodeWithText("The Ashen Realms").performClick()

    assertEquals("ashen-realms", viewModel.uiState.value.currentFolderId)
  }

  @Test
  fun clickingDocumentInvokesDocumentNavigationCallback() {
    var openedDocument: JDRFile? = null
    setScreen(onDocumentClicked = { openedDocument = it })

    composeTestRule.onNodeWithText("Elara Moonwhisper").performClick()

    assertEquals("elara-moonwhisper", openedDocument?.id)
    assertEquals(DocumentType.CHARACTER, openedDocument?.type)
  }

  @Test
  fun folderItemWithDefaultColorsShowsFileAndHandlesClick() {
    var clicked = false
    composeTestRule.setContent {
      FolderItem(folder = files[2], onFolderClicked = { clicked = true })
    }

    composeTestRule.onNodeWithText("TEXT DOCUMENT").assertIsDisplayed()
    composeTestRule.onNodeWithText("Lore of a forgotten kingdom").assertIsDisplayed()
    composeTestRule.onNodeWithText("The Sunken Citadel").performClick()

    assertTrue(clicked)
  }

  @Test
  fun folderItemUpdatesWhenItsArgumentsChange() {
    var folder by mutableStateOf(files[0])
    var accentColor by mutableStateOf(Color.Red)
    var parentTick by mutableIntStateOf(0)
    composeTestRule.setContent {
      parentTick.let {
        Column {
          FolderItem(folder = folder, onFolderClicked = {}, accentColor = accentColor)
          FolderItem(
              folder = folder,
              onFolderClicked = {},
              modifier = Modifier,
              cardColor = Color.Black,
              iconBackground = Color.Gray,
              primaryText = Color.White,
              secondaryText = Color.LightGray,
              borderColor = Color.DarkGray,
              accentColor = accentColor,
          )
        }
      }
    }

    // Unchanged arguments: both items may skip recomposition.
    parentTick++
    composeTestRule.waitForIdle()
    // Changed arguments: both items must update.
    accentColor = Color.Blue
    folder = files[1]
    composeTestRule.waitForIdle()

    composeTestRule.onAllNodesWithText("Elara Moonwhisper").assertCountEquals(2)
    composeTestRule.onAllNodesWithText("CHARACTER SHEET").assertCountEquals(2)
  }

  private class FakeWorkspaceRepository(private val rootFiles: Flow<List<JDRFile>>) :
      WorkspaceRepository {
    var rootFetchCount = 0

    override fun getNewUid(): String = "new-folder-id"

    override suspend fun createFile(file: JDRFile): Result<Unit> = Result.success(Unit)

    override suspend fun updateFile(file: JDRFile): Result<Unit> = Result.success(Unit)

    override suspend fun deleteFile(fileId: String): Result<Unit> = Result.success(Unit)

    override suspend fun getFile(fileId: String): Result<JDRFile?> = Result.success(null)

    override suspend fun removeItemFromGroup(
        itemId: String,
        groupId: String,
        parentFolderId: String?,
    ): Result<Unit> {
      TODO("Not yet implemented")
    }

    override fun getFileFlow(fileId: String): Flow<JDRFile?> = flowOf(null)

    override fun getPersonalRootFiles(ownerId: String): Flow<List<JDRFile>> {
      rootFetchCount++
      return rootFiles
    }

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
