// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.github.se.jdrnexus

import androidx.compose.ui.test.assertCountEquals
import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.se.jdrnexus.model.personalSpace.GroupRepository
import com.github.se.jdrnexus.model.personalSpace.WorkspaceRepository
import com.github.se.jdrnexus.model.repository.AuthRepository
import com.github.se.jdrnexus.model.repository.AuthResult
import com.github.se.jdrnexus.model.repository.AuthUser
import com.github.se.jdrnexus.resources.C
import com.github.se.jdrnexus.ui.authentication.AuthStatus
import com.github.se.jdrnexus.ui.authentication.AuthViewModel
import com.github.se.jdrnexus.ui.gameManagement.GameManagementTestTags
import com.github.se.jdrnexus.ui.home.TestTags
import com.google.firebase.FirebaseApp
import com.github.se.jdrnexus.viewmodel.GameManagementViewModel
import com.github.se.jdrnexus.viewmodel.PersonalSpaceViewModel
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityNavigationTest {
  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun startsAtSignInAndReturnsFromSignUp() {
    showApp()

    scrollToTag("sign_in_scroll_container", C.Tag.sign_in_sign_up)
    composeTestRule.onNodeWithTag(C.Tag.sign_in_sign_up).assertIsDisplayed().performClick()
    scrollToTag("sign_up_scroll_container", C.Tag.sign_up_sign_in)
    composeTestRule.onNodeWithTag(C.Tag.sign_up_sign_in).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.sign_up_sign_in).performClick()
    scrollToTag("sign_in_scroll_container", C.Tag.sign_in_sign_up)
    composeTestRule.onNodeWithTag(C.Tag.sign_in_sign_up).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.sign_up_sign_in).assertIsNotDisplayed()
  }

  @Test
  fun successfulSignInAndLogoutReturnToSignIn() {
    val repository = FakeAuthRepository()
    showApp(repository)

    assert(repository.authState.value == null)
    composeTestRule.onNodeWithTag(C.Tag.sign_in_email).performTextInput("hero@example.com")
    composeTestRule.onNodeWithTag(C.Tag.sign_in_password).performTextInput("secret1")
    composeTestRule.onNodeWithTag(C.Tag.sign_in_submit).performClick()

    waitForTag(TestTags.TITLE)
    composeTestRule.onNodeWithTag(TestTags.TITLE).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.sign_in_sign_up).assertIsNotDisplayed()
    assert(repository.signInCalls == 1)
    assert(repository.authState.value == FakeAuthRepository.TEST_USER)

    composeTestRule.onNodeWithTag(TestTags.LOGOUT).performClick()
    waitForTag(C.Tag.sign_in_sign_up)
    scrollToTag("sign_in_scroll_container", C.Tag.sign_in_sign_up)
    composeTestRule.onNodeWithTag(C.Tag.sign_in_sign_up).assertIsDisplayed()
    composeTestRule.onNodeWithTag(TestTags.TITLE).assertIsNotDisplayed()
    assert(repository.authState.value == null)
  }

  @Test
  fun successfulRegistrationNavigatesToHome() {
    val repository = FakeAuthRepository()
    showApp(repository)

    assert(repository.authState.value == null)
    scrollToTag("sign_in_scroll_container", C.Tag.sign_in_sign_up)
    composeTestRule.onNodeWithTag(C.Tag.sign_in_sign_up).performClick()
    composeTestRule.onNodeWithTag(C.Tag.sign_up_username).performTextInput("Ranger")
    composeTestRule.onNodeWithTag(C.Tag.sign_up_email).performTextInput("hero@example.com")
    composeTestRule.onNodeWithTag(C.Tag.sign_up_password).performTextInput("secret1")
    composeTestRule.onNodeWithTag(C.Tag.sign_up_submit).performClick()

    waitForTag(TestTags.TITLE)
    composeTestRule.onNodeWithTag(TestTags.TITLE).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.sign_up_sign_in).assertIsNotDisplayed()
    assert(repository.signUpCalls == 1)
    assert(repository.authState.value == FakeAuthRepository.TEST_USER)
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
    repository.signOutResult?.complete(AuthResult.Success(Unit))

    waitForTag(C.Tag.sign_in_sign_up)
    scrollToTag("sign_in_scroll_container", C.Tag.sign_in_sign_up)
    composeTestRule.onNodeWithTag(C.Tag.sign_in_sign_up).assertIsDisplayed()
    composeTestRule.onNodeWithTag(TestTags.TITLE).assertIsNotDisplayed()
    assert(repository.authState.value == null)
  }

  @Test
  fun signOutExceptionKeepsAppAliveAndHomeUsable() {
    val repository = FakeAuthRepository()
    repository.signOutFailure = IllegalStateException("Sign-out failed")
    val viewModel = AuthViewModel(repository)
    composeTestRule.setContent { JDRNexusApp(authViewModel = viewModel) }

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
  fun gameManagementTabsReturnHomeAndOpenPersonalSpace() {
    val repository = FakeAuthRepository()
    val groupRepository =
        mockk<GroupRepository> { every { getUserGroups(any()) } returns flowOf(emptyList()) }
    val workspaceRepository =
        mockk<WorkspaceRepository> {
          every { getPersonalRootFiles(any()) } returns flowOf(emptyList())
        }
    val authViewModel = AuthViewModel(repository)
    val gameManagementViewModel = GameManagementViewModel(groupRepository, repository)
    val personalSpaceViewModel = PersonalSpaceViewModel(workspaceRepository, repository)
    composeTestRule.setContent {
      JDRNexusApp(
          authViewModel = authViewModel,
          gameManagementViewModel = gameManagementViewModel,
          personalSpaceViewModel = personalSpaceViewModel,
      )
    }
    composeTestRule.onNodeWithTag(C.Tag.sign_in_email).performTextInput("hero@example.com")
    composeTestRule.onNodeWithTag(C.Tag.sign_in_password).performTextInput("secret1")
    composeTestRule.onNodeWithTag(C.Tag.sign_in_submit).performClick()
    waitForTag(TestTags.TITLE)

    composeTestRule.onNodeWithTag(TestTags.GROUPMANAGEMENTBUTTON).performClick()
    waitForTag(GameManagementTestTags.CREATE_GROUP_BUTTON)
    composeTestRule.onNodeWithText("Home").performClick()
    composeTestRule.onNodeWithTag(TestTags.TITLE).assertIsDisplayed()

    composeTestRule.onNodeWithTag(TestTags.GROUPMANAGEMENTBUTTON).performClick()
    waitForTag(GameManagementTestTags.CREATE_GROUP_BUTTON)
    composeTestRule.onNodeWithText("Files").performClick()
    composeTestRule.onAllNodesWithText("My Space").assertCountEquals(1)
  }

  private fun showApp(repository: FakeAuthRepository = FakeAuthRepository()) {
    val context = ApplicationProvider.getApplicationContext<Context>()
    if (FirebaseApp.getApps(context).isEmpty()) {
      checkNotNull(FirebaseApp.initializeApp(context))
    }
    val viewModel = AuthViewModel(repository)
    composeTestRule.setContent { JDRNexusApp(authViewModel = viewModel) }
  }

  private fun waitForTag(tag: String) {
    composeTestRule.waitUntil(5_000) {
      composeTestRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    }
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
}
