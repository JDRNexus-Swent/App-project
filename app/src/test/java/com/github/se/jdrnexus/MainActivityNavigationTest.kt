// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.github.se.jdrnexus

import android.content.Context
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.se.jdrnexus.model.map.MapGame
import com.github.se.jdrnexus.model.map.MapLocation
import com.github.se.jdrnexus.model.map.MapRepository
import com.github.se.jdrnexus.model.map.MapRepositoryProvider
import com.github.se.jdrnexus.model.personalSpace.CampaignGroup
import com.github.se.jdrnexus.model.personalSpace.DocumentType
import com.github.se.jdrnexus.model.personalSpace.GroupRepository
import com.github.se.jdrnexus.model.personalSpace.JDRFile
import com.github.se.jdrnexus.model.personalSpace.WorkspaceRepository
import com.github.se.jdrnexus.model.repository.AuthRepository
import com.github.se.jdrnexus.model.repository.AuthResult
import com.github.se.jdrnexus.model.repository.AuthUser
import com.github.se.jdrnexus.resources.C
import com.github.se.jdrnexus.ui.authentication.AuthStatus
import com.github.se.jdrnexus.ui.authentication.AuthViewModel
import com.github.se.jdrnexus.ui.edition.EditFileLoreTestTags
import com.github.se.jdrnexus.ui.gameManagement.GameManagementTestTags
import com.github.se.jdrnexus.ui.home.TestTags
import com.github.se.jdrnexus.ui.jdrfile.EditFileViewModel
import com.github.se.jdrnexus.utils.NavigationGroupRepository
import com.github.se.jdrnexus.utils.NavigationWorkspaceRepository
import com.github.se.jdrnexus.viewmodel.GameManagementViewModel
import com.github.se.jdrnexus.viewmodel.PersonalSpaceViewModel
import com.google.firebase.FirebaseApp
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityNavigationTest {
  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun startsAtSignInAndReturnsFromSignUp() {
    showApp()

    scrollToTag(SIGN_IN_SCROLL_CONTAINER, C.Tag.sign_in_sign_up)
    composeTestRule.onNodeWithTag(C.Tag.sign_in_sign_up).assertIsDisplayed().performClick()
    scrollToTag(SIGN_UP_SCROLL_CONTAINER, C.Tag.sign_up_sign_in)
    composeTestRule.onNodeWithTag(C.Tag.sign_up_sign_in).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.sign_up_sign_in).performClick()
    scrollToTag(SIGN_IN_SCROLL_CONTAINER, C.Tag.sign_in_sign_up)
    composeTestRule.onNodeWithTag(C.Tag.sign_in_sign_up).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.sign_up_sign_in).assertIsNotDisplayed()
  }

  @Test
  fun rapidSignUpTapsDoNotStackScreensOrLeaveNavigationBlank() {
    showApp()
    scrollToTag(SIGN_IN_SCROLL_CONTAINER, C.Tag.sign_in_sign_up)

    composeTestRule.onNodeWithTag(C.Tag.sign_in_sign_up).performTouchInput {
      repeat(10) { click() }
    }
    waitForTag(C.Tag.sign_up_sign_in)
    scrollToTag(SIGN_UP_SCROLL_CONTAINER, C.Tag.sign_up_sign_in)

    composeTestRule.onNodeWithTag(C.Tag.sign_up_sign_in).assertIsDisplayed().performClick()
    composeTestRule.onNodeWithTag(C.Tag.sign_in_sign_up).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.sign_up_sign_in).assertIsNotDisplayed()

    composeTestRule.onNodeWithTag(C.Tag.sign_in_sign_up).performTouchInput {
      repeat(10) { click() }
    }
    waitForTag(C.Tag.sign_up_sign_in)
    scrollToTag(SIGN_UP_SCROLL_CONTAINER, C.Tag.sign_up_sign_in)
    composeTestRule.onNodeWithTag(C.Tag.sign_up_sign_in).performTouchInput {
      repeat(10) { click() }
    }
    waitForTag(C.Tag.sign_in_sign_up)
    composeTestRule.onNodeWithTag(C.Tag.sign_in_sign_up).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.sign_up_sign_in).assertIsNotDisplayed()
  }

  @Test
  fun successfulSignInAndLogoutReturnToSignIn() {
    val repository = FakeAuthRepository()
    showApp(repository)

    assertEquals(null, repository.authState.value)
    composeTestRule.onNodeWithTag(C.Tag.sign_in_email).performTextInput("hero@example.com")
    composeTestRule.onNodeWithTag(C.Tag.sign_in_password).performTextInput("secret1")
    composeTestRule.onNodeWithTag(C.Tag.sign_in_submit).performClick()

    waitForTag(TestTags.TITLE)
    composeTestRule.onNodeWithTag(TestTags.TITLE).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.sign_in_sign_up).assertIsNotDisplayed()
    assertEquals(1, repository.signInCalls)
    assertEquals(FakeAuthRepository.TEST_USER, repository.authState.value)

    composeTestRule.onNodeWithTag(TestTags.LOGOUT).performClick()
    waitForTag(C.Tag.sign_in_sign_up)
    scrollToTag(SIGN_IN_SCROLL_CONTAINER, C.Tag.sign_in_sign_up)
    composeTestRule.onNodeWithTag(C.Tag.sign_in_sign_up).assertIsDisplayed()
    composeTestRule.onNodeWithTag(TestTags.TITLE).assertIsNotDisplayed()
    assertEquals(null, repository.authState.value)
  }

  @Test
  fun successfulRegistrationNavigatesToHome() {
    val repository = FakeAuthRepository()
    showApp(repository)

    assertEquals(null, repository.authState.value)
    scrollToTag(SIGN_IN_SCROLL_CONTAINER, C.Tag.sign_in_sign_up)
    composeTestRule.onNodeWithTag(C.Tag.sign_in_sign_up).performClick()
    composeTestRule.onNodeWithTag(C.Tag.sign_up_username).performTextInput("Ranger")
    composeTestRule.onNodeWithTag(C.Tag.sign_up_email).performTextInput("hero@example.com")
    composeTestRule.onNodeWithTag(C.Tag.sign_up_password).performTextInput("secret1")
    composeTestRule.onNodeWithTag(C.Tag.sign_up_submit).performClick()

    waitForTag(TestTags.TITLE)
    composeTestRule.onNodeWithTag(TestTags.TITLE).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.sign_up_sign_in).assertIsNotDisplayed()
    assertEquals(1, repository.signUpCalls)
    assertEquals(FakeAuthRepository.TEST_USER, repository.authState.value)
  }

  @Test
  fun homeNavigationOpensDiceAndReturnsHome() {
    val repository = FakeAuthRepository()
    showApp(repository)

    composeTestRule.onNodeWithTag(C.Tag.sign_in_email).performTextInput("hero@example.com")
    composeTestRule.onNodeWithTag(C.Tag.sign_in_password).performTextInput("secret1")
    composeTestRule.onNodeWithTag(C.Tag.sign_in_submit).performClick()
    waitForTag(TestTags.TITLE)

    composeTestRule.onNodeWithTag(TestTags.DICEBUTTON).performClick()
    waitForTag(C.Tag.dice_offline_screen)
    composeTestRule.onNodeWithTag(C.Tag.dice_offline_screen).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.dice_offline_close).performClick()

    composeTestRule.onNodeWithTag(TestTags.TITLE).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.dice_offline_screen).assertIsNotDisplayed()
  }

  @Test
  fun rapidCloseClicksDoNotRemoveHomeFromTheBackStack() {
    val repository = FakeAuthRepository()
    showApp(repository)

    composeTestRule.onNodeWithTag(C.Tag.sign_in_email).performTextInput("hero@example.com")
    composeTestRule.onNodeWithTag(C.Tag.sign_in_password).performTextInput("secret1")
    composeTestRule.onNodeWithTag(C.Tag.sign_in_submit).performClick()
    waitForTag(TestTags.TITLE)
    composeTestRule.onNodeWithTag(TestTags.DICEBUTTON).performClick()
    waitForTag(C.Tag.dice_offline_close)

    composeTestRule.onNodeWithTag(C.Tag.dice_offline_close).performTouchInput {
      click()
      click()
    }

    composeTestRule.onNodeWithTag(TestTags.TITLE).assertIsDisplayed()
  }

  @Test
  fun logoutDisablesNavigationUntilSignedOut() {
    val repository = FakeAuthRepository()
    repository.signOutResult = CompletableDeferred()
    showApp(repository)

    composeTestRule.onNodeWithTag(C.Tag.sign_in_email).performTextInput("hero@example.com")
    composeTestRule.onNodeWithTag(C.Tag.sign_in_password).performTextInput("secret1")
    composeTestRule.onNodeWithTag(C.Tag.sign_in_submit).performClick()
    waitForTag(TestTags.TITLE)

    composeTestRule.onNodeWithTag(TestTags.LOGOUT).performClick()
    composeTestRule.onNodeWithTag(TestTags.DICEBUTTON).assertIsNotEnabled()
    composeTestRule.onNodeWithTag(TestTags.GROUPMANAGEMENTBUTTON).assertIsNotEnabled()
    composeTestRule.onNodeWithTag(TestTags.PSPACEBUTTON).assertIsNotEnabled()
    composeTestRule.onNodeWithTag(TestTags.DICEBUTTON).performClick()
    composeTestRule.onAllNodesWithTag(C.Tag.dice_offline_screen).assertCountEquals(0)
    repository.signOutResult?.complete(AuthResult.Success(Unit))

    waitForTag(C.Tag.sign_in_sign_up)
    scrollToTag(SIGN_IN_SCROLL_CONTAINER, C.Tag.sign_in_sign_up)
    composeTestRule.onNodeWithTag(C.Tag.sign_in_sign_up).assertIsDisplayed()
    composeTestRule.onNodeWithTag(TestTags.TITLE).assertIsNotDisplayed()
    assertEquals(null, repository.authState.value)
  }

  @Test
  fun signOutExceptionKeepsAppAliveAndHomeUsable() {
    val repository = FakeAuthRepository()
    repository.signOutFailure = IllegalStateException("Sign-out failed")
    val viewModel = AuthViewModel(repository)
    composeTestRule.setContent {
      JDRNexusApp(
          authViewModel = viewModel,
          authRepository = repository,
          groupRepository = FakeGroupRepository(),
          workspaceRepository = NavigationWorkspaceRepository(),
      )
    }

    composeTestRule.onNodeWithTag(C.Tag.sign_in_email).performTextInput("hero@example.com")
    composeTestRule.onNodeWithTag(C.Tag.sign_in_password).performTextInput("secret1")
    composeTestRule.onNodeWithTag(C.Tag.sign_in_submit).performClick()
    waitForTag(TestTags.TITLE)

    composeTestRule.onNodeWithTag(TestTags.LOGOUT).performClick()
    composeTestRule.waitUntil(5_000) {
      viewModel.uiState.value.status is AuthStatus.Error &&
          composeTestRule.onAllNodesWithTag(TestTags.LOGOUT).fetchSemanticsNodes().isNotEmpty()
    }

    composeTestRule.onNodeWithTag(TestTags.TITLE).assertIsDisplayed()
    composeTestRule.onNodeWithTag(TestTags.DICEBUTTON).assertIsDisplayed().performClick()
    composeTestRule.onNodeWithTag(C.Tag.dice_offline_screen).assertIsDisplayed()
  }

  @Test
  fun homeTabFromCampaignsOpenedViaFilesReturnsHome() {
    showAuthenticatedApp()

    composeTestRule.onNodeWithTag(C.Tag.bottomTab("files")).performClick()
    waitForText("My Space")
    composeTestRule.onNodeWithTag(C.Tag.bottomTab("campaigns")).performClick()
    waitForTag(GameManagementTestTags.CREATE_GROUP_BUTTON)
    composeTestRule.onNodeWithTag(C.Tag.bottomTab("home")).performClick()

    composeTestRule.onNodeWithTag(TestTags.TITLE).assertIsDisplayed()
    composeTestRule
        .onAllNodesWithTag(GameManagementTestTags.CREATE_GROUP_BUTTON)
        .assertCountEquals(0)
  }

  @Test
  fun backFromLoreReturnsToPersonalSpace() {
    val loreFile = showAuthenticatedApp()

    composeTestRule.onNodeWithTag(C.Tag.bottomTab("files")).performClick()
    waitForText(loreFile.name)
    composeTestRule.onNodeWithText(loreFile.name).performClick()
    waitForTag(EditFileLoreTestTags.LIST)
    composeTestRule.onNodeWithTag(EditFileLoreTestTags.BACK).performClick()

    composeTestRule.onAllNodesWithText("My Space").assertCountEquals(1)
  }

  @Test
  fun mapTabOpensMapScreen() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    if (FirebaseApp.getApps(context).isEmpty()) checkNotNull(FirebaseApp.initializeApp(context))
    // No real Firestore: the Map screen loads its games from a fake repository.
    val defaultMapRepository = MapRepositoryProvider.mapRepository
    MapRepositoryProvider.mapRepository = NoGamesMapRepository()
    try {
      showApp()
      composeTestRule.onNodeWithTag(C.Tag.sign_in_email).performTextInput("hero@example.com")
      composeTestRule.onNodeWithTag(C.Tag.sign_in_password).performTextInput("secret1")
      composeTestRule.onNodeWithTag(C.Tag.sign_in_submit).performClick()
      waitForTag(TestTags.TITLE)

      composeTestRule.onNodeWithTag(C.Tag.bottomTab("map")).performClick()

      waitForTag(C.Tag.map_screen)
      composeTestRule.onNodeWithTag(C.Tag.map_screen).assertIsDisplayed()

      composeTestRule.onNodeWithTag(C.Tag.bottomTab("home")).performClick()
      waitForTag(TestTags.TITLE)
      composeTestRule.onNodeWithTag(TestTags.TITLE).assertIsDisplayed()
    } finally {
      MapRepositoryProvider.mapRepository = defaultMapRepository
    }
  }

  @Test
  fun campaignsNavigateToWorkspaceAndBack() {
    val groupRepository = navigationGroupRepository()
    showApp(groupRepository = groupRepository)
    signInAndOpenCampaigns()

    waitForTag(C.Tag.enter_workspace_button)
    composeTestRule.onNodeWithTag(C.Tag.enter_workspace_button).performClick()
    waitForText("The Sunken Crown")

    composeTestRule.onNodeWithContentDescription("Go back").performClick()
    waitForTag(C.Tag.enter_workspace_button)
  }

  @Test
  fun systemBackFromWorkspaceReturnsToCampaigns() {
    val groupRepository = navigationGroupRepository()
    showApp(groupRepository = groupRepository)
    signInAndOpenCampaigns()

    waitForTag(C.Tag.enter_workspace_button)
    composeTestRule.onNodeWithTag(C.Tag.enter_workspace_button).performClick()
    waitForText("The Sunken Crown")

    Espresso.pressBack()

    waitForTag(C.Tag.enter_workspace_button)
  }

  @Test
  fun rapidWorkspaceTapsCreateOneDestinationAndOneViewModel() {
    val groupRepository = navigationGroupRepository()
    showApp(groupRepository = groupRepository)
    signInAndOpenCampaigns()

    waitForTag(C.Tag.enter_workspace_button)
    composeTestRule.onNodeWithTag(C.Tag.enter_workspace_button).performTouchInput {
      repeat(10) { click() }
    }

    waitForText("The Sunken Crown")
    assertEquals(1, groupRepository.groupFlowLoads)
    assertEquals("group-1", groupRepository.lastGroupFlowId)
  }

  @Test
  fun workspaceGroupIdSurvivesSavedStateRestoration() {
    val groupRepository = navigationGroupRepository()
    val authRepository = FakeAuthRepository()
    val restorationTester = StateRestorationTester(composeTestRule)
    val authViewModel = AuthViewModel(authRepository)
    val workspaceRepository = NavigationWorkspaceRepository()

    restorationTester.setContent {
      JDRNexusApp(
          authViewModel = authViewModel,
          authRepository = authRepository,
          groupRepository = groupRepository,
          workspaceRepository = workspaceRepository,
      )
    }
    signInAndOpenCampaigns()
    waitForTag(C.Tag.enter_workspace_button)
    composeTestRule.onNodeWithTag(C.Tag.enter_workspace_button).performClick()
    waitForText("The Sunken Crown")

    restorationTester.emulateSavedInstanceStateRestore()

    waitForText("The Sunken Crown")
    assertEquals("group-1", groupRepository.lastGroupFlowId)
  }

  private class NoGamesMapRepository : MapRepository {
    override suspend fun loadGamesNear(location: MapLocation?): List<MapGame> = emptyList()
  }

  private fun showApp(
      repository: FakeAuthRepository = FakeAuthRepository(),
      groupRepository: NavigationGroupRepository? = null,
      workspaceRepository: NavigationWorkspaceRepository = NavigationWorkspaceRepository(),
  ) {
    val context = ApplicationProvider.getApplicationContext<Context>()
    if (FirebaseApp.getApps(context).isEmpty()) {
      checkNotNull(FirebaseApp.initializeApp(context))
    }
    val viewModel = AuthViewModel(repository)
    composeTestRule.setContent {
      JDRNexusApp(
          authViewModel = viewModel,
          authRepository = repository,
          groupRepository = groupRepository,
          workspaceRepository = workspaceRepository,
      )
    }
  }

  private fun signInAndOpenCampaigns() {
    composeTestRule.onNodeWithTag(C.Tag.sign_in_email).performTextInput("hero@example.com")
    composeTestRule.onNodeWithTag(C.Tag.sign_in_password).performTextInput("secret1")
    composeTestRule.onNodeWithTag(C.Tag.sign_in_submit).performClick()
    waitForTag(TestTags.GROUPMANAGEMENTBUTTON)
    composeTestRule.onNodeWithTag(TestTags.GROUPMANAGEMENTBUTTON).performClick()
  }

  private fun navigationGroupRepository() =
      NavigationGroupRepository(
          CampaignGroup(
              id = "group-1",
              name = "The Sunken Crown",
              gameMasterId = "user-1",
              participantIds = listOf("user-1"),
          )
      )

  private fun waitForTag(tag: String) {
    composeTestRule.waitUntil(5_000) {
      composeTestRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    }
  }

  private fun waitForText(text: String) {
    composeTestRule.waitUntil(5_000) {
      composeTestRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }
  }

  private fun showAuthenticatedApp(): JDRFile {
    val repository = FakeAuthRepository()
    val loreFile =
        JDRFile(
            id = "lore-1",
            name = "The Kingdom",
            type = DocumentType.TEXT,
            content =
                """{"version":1,"type":"lore","title":"The Kingdom","sections":[{"sectionID":"history","title":"History","content":"Founded long ago."}]}""",
        )
    val workspaceRepository = FakeWorkspaceRepository(loreFile)
    val groupRepository = FakeGroupRepository()
    val authViewModel = AuthViewModel(repository)
    val personalSpaceViewModel = PersonalSpaceViewModel(workspaceRepository, repository)
    val gameManagementViewModel = GameManagementViewModel(groupRepository, repository)
    val editFileViewModel = EditFileViewModel(workspaceRepository)
    composeTestRule.setContent {
      JDRNexusApp(
          authViewModel = authViewModel,
          gameManagementViewModel = gameManagementViewModel,
          personalSpaceViewModel = personalSpaceViewModel,
          editFileViewModel = editFileViewModel,
          authRepository = repository,
          groupRepository = groupRepository,
          workspaceRepository = workspaceRepository,
      )
    }
    composeTestRule.onNodeWithTag(C.Tag.sign_in_email).performTextInput("hero@example.com")
    composeTestRule.onNodeWithTag(C.Tag.sign_in_password).performTextInput("secret1")
    composeTestRule.onNodeWithTag(C.Tag.sign_in_submit).performClick()
    waitForTag(TestTags.TITLE)
    personalSpaceViewModel.fetchItems("")
    return loreFile
  }

  private fun scrollToTag(containerTag: String, targetTag: String) {
    composeTestRule.onNodeWithTag(containerTag).performScrollToNode(hasTestTag(targetTag))
  }

  private class FakeAuthRepository : AuthRepository {
    private val mutableAuthState = MutableStateFlow<AuthUser?>(null)
    override val authState: StateFlow<AuthUser?> = mutableAuthState
    var signInCalls = 0
    var signUpCalls = 0
    var signOutResult: CompletableDeferred<AuthResult<Unit>>? = null
    var signOutFailure: Exception? = null

    override suspend fun signUpWithEmail(
        email: String,
        password: String,
        username: String,
    ): AuthResult<AuthUser> {
      signUpCalls++
      mutableAuthState.value = TEST_USER
      return AuthResult.Success(TEST_USER)
    }

    override suspend fun signInWithEmail(email: String, password: String): AuthResult<AuthUser> {
      signInCalls++
      mutableAuthState.value = TEST_USER
      return AuthResult.Success(TEST_USER)
    }

    override suspend fun signInWithGoogle(idToken: String): AuthResult<AuthUser> {
      mutableAuthState.value = TEST_USER
      return AuthResult.Success(TEST_USER)
    }

    override suspend fun signOut(): AuthResult<Unit> {
      signOutFailure?.let { throw it }
      signOutResult?.let {
        val result = it.await()
        if (result is AuthResult.Success) mutableAuthState.value = null
        return result
      }
      mutableAuthState.value = null
      return AuthResult.Success(Unit)
    }

    companion object {
      val TEST_USER = AuthUser(uid = "user-1", email = "hero@example.com", username = "Ranger")
    }
  }

  private class FakeGroupRepository : GroupRepository by UnusedGroupRepository {
    override fun getUserGroups(userId: String) = flowOf(emptyList<CampaignGroup>())
  }

  private class FakeWorkspaceRepository(private val file: JDRFile) :
      WorkspaceRepository by UnusedWorkspaceRepository {
    override suspend fun getFile(fileId: String): Result<JDRFile?> =
        Result.success(file.takeIf { it.id == fileId })

    override fun getPersonalRootFiles(ownerId: String) = flowOf(listOf(file))
  }

  private companion object {
    const val SIGN_IN_SCROLL_CONTAINER = "sign_in_scroll_container"
    const val SIGN_UP_SCROLL_CONTAINER = "sign_up_scroll_container"

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
  }
}
