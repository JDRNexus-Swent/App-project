// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.github.se.jdrnexus

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.se.jdrnexus.model.repository.AuthRepository
import com.github.se.jdrnexus.model.repository.AuthResult
import com.github.se.jdrnexus.model.repository.AuthUser
import com.github.se.jdrnexus.resources.C
import com.github.se.jdrnexus.ui.authentication.AuthViewModel
import com.github.se.jdrnexus.ui.home.TestTags
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityNavigationTest {
  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun startsAtSignInAndReturnsFromSignUp() {
    showApp()

    composeTestRule.onNodeWithTag(C.Tag.sign_in_sign_up).assertIsDisplayed().performClick()
    composeTestRule.onNodeWithTag(C.Tag.sign_up_sign_in).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.sign_up_sign_in).performClick()
    composeTestRule.onNodeWithTag(C.Tag.sign_in_sign_up).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.sign_up_sign_in).assertIsNotDisplayed()
  }

  @Test
  fun successfulSignInAndLogoutReturnToSignIn() {
    val repository = FakeAuthRepository()
    showApp(repository)

    composeTestRule.onNodeWithTag(C.Tag.sign_in_email).performTextInput("hero@example.com")
    composeTestRule.onNodeWithTag(C.Tag.sign_in_password).performTextInput("secret1")
    composeTestRule.onNodeWithTag(C.Tag.sign_in_submit).performClick()

    waitForTag(TestTags.TITLE)
    composeTestRule.onNodeWithTag(TestTags.TITLE).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.sign_in_sign_up).assertIsNotDisplayed()
    assert(repository.signInCalls == 1)

    composeTestRule.onNodeWithTag(TestTags.LOGOUT).performClick()
    waitForTag(C.Tag.sign_in_sign_up)
    composeTestRule.onNodeWithTag(C.Tag.sign_in_sign_up).assertIsDisplayed()
    composeTestRule.onNodeWithTag(TestTags.TITLE).assertIsNotDisplayed()
  }

  @Test
  fun successfulRegistrationNavigatesToHome() {
    val repository = FakeAuthRepository()
    showApp(repository)

    composeTestRule.onNodeWithTag(C.Tag.sign_in_sign_up).performClick()
    composeTestRule.onNodeWithTag(C.Tag.sign_up_username).performTextInput("Ranger")
    composeTestRule.onNodeWithTag(C.Tag.sign_up_email).performTextInput("hero@example.com")
    composeTestRule.onNodeWithTag(C.Tag.sign_up_password).performTextInput("secret1")
    composeTestRule.onNodeWithTag(C.Tag.sign_up_submit).performClick()

    waitForTag(TestTags.TITLE)
    composeTestRule.onNodeWithTag(TestTags.TITLE).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.sign_up_sign_in).assertIsNotDisplayed()
    assert(repository.signUpCalls == 1)
  }

  private fun showApp(repository: FakeAuthRepository = FakeAuthRepository()) {
    val viewModel = AuthViewModel(repository)
    composeTestRule.setContent { JDRNexusApp(authViewModel = viewModel) }
  }

  private fun waitForTag(tag: String) {
    composeTestRule.waitUntil(5_000) {
      composeTestRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    }
  }

  private class FakeAuthRepository : AuthRepository {
    override val authState: Flow<AuthUser?> = MutableStateFlow(null)
    var signInCalls = 0
    var signUpCalls = 0

    override suspend fun signUpWithEmail(
        email: String,
        password: String,
        username: String,
    ): AuthResult<AuthUser> {
      signUpCalls++
      return AuthResult.Success(TEST_USER)
    }

    override suspend fun signInWithEmail(email: String, password: String): AuthResult<AuthUser> {
      signInCalls++
      return AuthResult.Success(TEST_USER)
    }

    override suspend fun signInWithGoogle(idToken: String): AuthResult<AuthUser> =
        AuthResult.Success(TEST_USER)

    override suspend fun signOut(): AuthResult<Unit> = AuthResult.Success(Unit)

    private companion object {
      val TEST_USER = AuthUser(uid = "user-1", email = "hero@example.com", username = "Ranger")
    }
  }
}
