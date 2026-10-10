package com.github.se.jdrnexus.ui.authentication

import android.app.PendingIntent
import android.content.Context
import android.os.CancellationSignal
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CreateCredentialRequest
import androidx.credentials.CreateCredentialResponse
import androidx.credentials.CredentialManager
import androidx.credentials.CredentialManagerCallback
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.PrepareGetCredentialResponse
import androidx.credentials.SignalCredentialStateRequest
import androidx.credentials.SignalCredentialStateResponse
import androidx.credentials.exceptions.ClearCredentialException
import androidx.credentials.exceptions.CreateCredentialException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.publickeycredential.SignalCredentialStateException
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.se.jdrnexus.model.repository.AuthError
import com.github.se.jdrnexus.model.repository.AuthRepository
import com.github.se.jdrnexus.model.repository.AuthResult
import com.github.se.jdrnexus.model.repository.AuthUser
import com.github.se.jdrnexus.resources.C
import com.github.se.jdrnexus.ui.theme.SampleAppTheme
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import java.util.concurrent.Executor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h800dp-xhdpi")
class SignInScreenTest {
  @get:Rule val composeTestRule = createComposeRule()

  /** Helper functions to execute the tests */
  private fun showSignIn(
      repository: FakeAuthRepository = FakeAuthRepository(),
      credentialManager: CredentialManager? = null,
      onSignInSuccess: () -> Unit = {},
      onSignUp: () -> Unit = {},
      forgotPassword: () -> Unit = {},
  ) {
    val viewModel = AuthViewModel(repository)
    composeTestRule.setContent {
      SampleAppTheme {
        if (credentialManager == null) {
          SignInScreen(
              signInViewModel = viewModel,
              onSignInSuccess = onSignInSuccess,
              onSignUp = onSignUp,
              forgotPassword = forgotPassword,
          )
        } else {
          SignInScreen(
              signInViewModel = viewModel,
              credentialManager = credentialManager,
              onSignInSuccess = onSignInSuccess,
              onSignUp = onSignUp,
              forgotPassword = forgotPassword,
          )
        }
      }
    }
  }

  private fun emailInput(tag: String) = composeTestRule.onNodeWithTag(tag)

  private fun passwordInput(tag: String) = composeTestRule.onNodeWithTag(tag)

  private fun enterSignInCredentials() {
    emailInput(C.Tag.sign_in_email).performTextInput("hero@example.com")
    passwordInput(C.Tag.sign_in_password).performTextInput("secret1")
  }

  private class FakeAuthRepository : AuthRepository {
    override val authState: Flow<AuthUser?> = MutableStateFlow(null)
    var signInCalls = 0
    var signUpCalls = 0
    var googleSignInCalls = 0
    var lastGoogleToken: String? = null
    var lastSignInArguments: Pair<String, String>? = null
    var lastSignUpArguments: Triple<String, String, String>? = null
    var signInResponse: CompletableDeferred<AuthResult<AuthUser>> =
        CompletableDeferred(AuthResult.Success(TEST_USER))
    var signUpResponse: CompletableDeferred<AuthResult<AuthUser>> =
        CompletableDeferred(AuthResult.Success(TEST_USER))

    override suspend fun signInWithEmail(
        email: String,
        password: String,
    ): AuthResult<AuthUser> {
      signInCalls++
      lastSignInArguments = email to password
      return signInResponse.await()
    }

    override suspend fun signUpWithEmail(
        email: String,
        password: String,
        username: String,
    ): AuthResult<AuthUser> {
      signUpCalls++
      lastSignUpArguments = Triple(username, email, password)
      return signUpResponse.await()
    }

    override suspend fun signInWithGoogle(idToken: String): AuthResult<AuthUser> {
      googleSignInCalls++
      lastGoogleToken = idToken
      return AuthResult.Success(TEST_USER)
    }

    override suspend fun signOut(): AuthResult<Unit> = AuthResult.Success(Unit)

    private companion object {
      val TEST_USER = AuthUser(uid = "user-1", email = "hero@example.com", username = "Ranger")
    }
  }

  private class FakeCredentialManager(private val response: GetCredentialResponse) :
      CredentialManager {
    var getCredentialCalls = 0

    override fun getCredentialAsync(
        context: Context,
        request: GetCredentialRequest,
        cancellationSignal: CancellationSignal?,
        executor: Executor,
        callback: CredentialManagerCallback<GetCredentialResponse, GetCredentialException>,
    ) {
      getCredentialCalls++
      executor.execute { callback.onResult(response) }
    }

    override fun getCredentialAsync(
        context: Context,
        pendingGetCredentialHandle: PrepareGetCredentialResponse.PendingGetCredentialHandle,
        cancellationSignal: CancellationSignal?,
        executor: Executor,
        callback: CredentialManagerCallback<GetCredentialResponse, GetCredentialException>,
    ) {
      throw UnsupportedOperationException("Not used in authentication screen tests")
    }

    override fun prepareGetCredentialAsync(
        request: GetCredentialRequest,
        cancellationSignal: CancellationSignal?,
        executor: Executor,
        callback: CredentialManagerCallback<PrepareGetCredentialResponse, GetCredentialException>,
    ) {
      throw UnsupportedOperationException("Not used in authentication screen tests")
    }

    override fun createCredentialAsync(
        context: Context,
        request: CreateCredentialRequest,
        cancellationSignal: CancellationSignal?,
        executor: Executor,
        callback: CredentialManagerCallback<CreateCredentialResponse, CreateCredentialException>,
    ) {
      throw UnsupportedOperationException("Not used in authentication screen tests")
    }

    override fun clearCredentialStateAsync(
        request: ClearCredentialStateRequest,
        cancellationSignal: CancellationSignal?,
        executor: Executor,
        callback: CredentialManagerCallback<Void?, ClearCredentialException>,
    ) {
      throw UnsupportedOperationException("Not used in authentication screen tests")
    }

    override fun signalCredentialStateAsync(
        request: SignalCredentialStateRequest,
        executor: Executor,
        callback:
            CredentialManagerCallback<
                SignalCredentialStateResponse,
                SignalCredentialStateException,
            >,
    ) {
      throw UnsupportedOperationException("Not used in authentication screen tests")
    }

    override fun createSettingsPendingIntent(): PendingIntent =
        throw UnsupportedOperationException("Not used in authentication screen tests")
  }

  /** the tests */
  @Test
  fun signInScreen_displaysAllAuthenticationContent() {
    showSignIn()

    composeTestRule.onNodeWithText("JDRNexus").assertIsDisplayed()
    composeTestRule.onNodeWithText("Email address").assertIsDisplayed()
    composeTestRule.onNodeWithText("Password").assertIsDisplayed()
    composeTestRule.onNodeWithText("Sign In").assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.sign_in_google).assertIsDisplayed().assertHasClickAction()
    composeTestRule.onNodeWithText("Forgot password ?").assertIsDisplayed()
    composeTestRule.onNodeWithText("Sign up").assertIsDisplayed()
    composeTestRule.onNodeWithContentDescription("Google Logo").assertIsDisplayed()
    emailInput(C.Tag.sign_in_email).assertIsDisplayed()
    passwordInput(C.Tag.sign_in_password).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.sign_in_submit).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.sign_in_forgot_password).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.sign_in_sign_up).assertIsDisplayed()
  }

  @Test
  fun signInScreen_acceptsEmailAndMasksPasswordUntilVisibilityIsToggled() {
    showSignIn()
    val email = emailInput(C.Tag.sign_in_email)
    val password = passwordInput(C.Tag.sign_in_password)
    email.performTextInput("hero@example.com")
    email.assertTextEquals("hero@example.com")
    password.performTextInput("secret-pass")
    password.assertTextEquals("•••••••••••")

    val passwordMatcher = SemanticsMatcher.expectValue(SemanticsProperties.Password, Unit)
    composeTestRule.onNode(passwordMatcher).assertIsDisplayed()
    assertEquals(
        "secret-pass",
        password.fetchSemanticsNode().config[SemanticsProperties.InputText].text,
    )
    composeTestRule
        .onNodeWithTag(C.Tag.sign_in_password_visibility)
        .assertHasClickAction()
        .performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNode(passwordMatcher).assertIsDisplayed()
    password.assertTextEquals("secret-pass")
    assertEquals(
        "secret-pass",
        password.fetchSemanticsNode().config[SemanticsProperties.InputText].text,
    )

    composeTestRule.onNodeWithTag(C.Tag.sign_in_password_visibility).performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNode(passwordMatcher).assertIsDisplayed()
    password.assertTextEquals("•••••••••••")
    assertEquals(
        "secret-pass",
        password.fetchSemanticsNode().config[SemanticsProperties.InputText].text,
    )
  }

  @Test
  fun signInScreen_invalidEmailShowsValidationAndCorrectingItClearsError() {
    val repository = FakeAuthRepository()
    showSignIn(repository)
    emailInput(C.Tag.sign_in_email).performTextInput("not-an-email")
    passwordInput(C.Tag.sign_in_password).performTextInput("secret1")

    composeTestRule.onNodeWithTag(C.Tag.sign_in_submit).performClick()
    composeTestRule.onNodeWithText(AuthError.INVALID_EMAIL.message).assertIsDisplayed()
    assertEquals(0, repository.signInCalls)

    emailInput(C.Tag.sign_in_email).performTextInput("@example.com")
    composeTestRule.onAllNodesWithText(AuthError.INVALID_EMAIL.message).assertCountEquals(0)
  }

  @Test
  fun signInScreen_shortPasswordShowsValidationAndValidPasswordSubmits() {
    val repository = FakeAuthRepository()
    showSignIn(repository)
    emailInput(C.Tag.sign_in_email).performTextInput("hero@example.com")
    passwordInput(C.Tag.sign_in_password).performTextInput("12345")

    composeTestRule.onNodeWithTag(C.Tag.sign_in_submit).performClick()
    composeTestRule.onNodeWithText("Password must be at least 6 characters.").assertIsDisplayed()
    assertEquals(0, repository.signInCalls)

    passwordInput(C.Tag.sign_in_password).performTextInput("6")
    composeTestRule
        .onAllNodesWithText("Password must be at least 6 characters.")
        .assertCountEquals(0)
    composeTestRule.onNodeWithTag(C.Tag.sign_in_submit).performClick()
    composeTestRule.waitForIdle()
    assertEquals(1, repository.signInCalls)
    assertEquals("hero@example.com" to "123456", repository.lastSignInArguments)
  }

  @Test
  fun signInScreen_invalidSubmissionShowsErrorWithoutCallingRepository() {
    val repository = FakeAuthRepository()
    showSignIn(repository)

    composeTestRule.onNodeWithTag(C.Tag.sign_in_submit).performClick()

    composeTestRule.onNodeWithText(AuthError.INVALID_EMAIL.message).assertIsDisplayed()
    assertEquals(0, repository.signInCalls)
  }

  @Test
  fun signInScreen_loadingDisablesSubmitAndReplacesButtonTextWithProgress() {
    val repository = FakeAuthRepository().apply { signInResponse = CompletableDeferred() }
    showSignIn(repository)
    enterSignInCredentials()

    composeTestRule.onNodeWithTag(C.Tag.sign_in_submit).performClick()
    composeTestRule.waitForIdle()

    assertEquals(1, repository.signInCalls)
    composeTestRule.onAllNodesWithText("Sign In").assertCountEquals(0)
    composeTestRule.onNodeWithTag(C.Tag.sign_in_submit).assertIsDisplayed().assertIsNotEnabled()
    composeTestRule
        .onAllNodes(
            SemanticsMatcher.expectValue(
                SemanticsProperties.ProgressBarRangeInfo,
                ProgressBarRangeInfo.Indeterminate,
            )
        )
        .assertCountEquals(1)
  }

  @Test
  fun signInScreen_authenticationFailureShowsMessageAndRestoresSubmitButton() {
    val repository =
        FakeAuthRepository().apply {
          signInResponse = CompletableDeferred(AuthResult.Failure(AuthError.WRONG_PASSWORD))
        }
    showSignIn(repository)
    enterSignInCredentials()

    composeTestRule.onNodeWithTag(C.Tag.sign_in_submit).performClick()
    composeTestRule.waitForIdle()

    assertEquals(1, repository.signInCalls)
    composeTestRule.onNodeWithText(AuthError.WRONG_PASSWORD.message).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.sign_in_submit).assertIsDisplayed().assertIsEnabled()
    composeTestRule.onNodeWithText("Sign In").assertIsDisplayed()
    composeTestRule
        .onAllNodes(
            SemanticsMatcher.expectValue(
                SemanticsProperties.ProgressBarRangeInfo,
                ProgressBarRangeInfo.Indeterminate,
            )
        )
        .assertCountEquals(0)
  }

  @Test
  fun signInScreen_successfulAuthenticationInvokesSuccessCallback() {
    val repository = FakeAuthRepository()
    var succeeded = false
    showSignIn(repository, onSignInSuccess = { succeeded = true })
    enterSignInCredentials()

    composeTestRule.onNodeWithTag(C.Tag.sign_in_submit).performClick()
    composeTestRule.waitForIdle()

    assertEquals(1, repository.signInCalls)
    assertEquals(true, succeeded)
  }

  @Test
  fun signInScreen_googleButtonAuthenticatesUsingInjectedCredentialManagerAndFakeToken() {
    val repository = FakeAuthRepository()
    val fakeIdToken = "eyJhbGciOiJub25lIn0.eyJzdWIiOiJ0ZXN0In0.c2lnbmF0dXJl"
    val credentialManager =
        FakeCredentialManager(
            GetCredentialResponse(
                GoogleIdTokenCredential.Builder().setId("test-user").setIdToken(fakeIdToken).build()
            )
        )
    var succeeded = false
    showSignIn(repository, credentialManager, onSignInSuccess = { succeeded = true })

    composeTestRule.onNodeWithTag(C.Tag.sign_in_google).assertIsDisplayed().assertHasClickAction()
    composeTestRule.onNodeWithTag(C.Tag.sign_in_google).performClick()
    composeTestRule.waitForIdle()

    assertEquals(1, credentialManager.getCredentialCalls)
    assertEquals(1, repository.googleSignInCalls)
    assertEquals(fakeIdToken, repository.lastGoogleToken)
    assertEquals(true, succeeded)
  }

  @Test
  fun signInScreen_forgotPasswordAndSignUpInvokeTheirCallbacks() {
    var forgotPasswordRequested = false
    var signUpRequested = false
    showSignIn(
        forgotPassword = { forgotPasswordRequested = true },
        onSignUp = { signUpRequested = true },
    )

    composeTestRule.onNodeWithTag(C.Tag.sign_in_forgot_password).performClick()
    composeTestRule.onNodeWithTag(C.Tag.sign_in_sign_up).performClick()

    assertEquals(true, forgotPasswordRequested)
    assertEquals(true, signUpRequested)
  }

  @Test
  fun signInScreen_canScrollToBottomControls() {
    showSignIn()

    composeTestRule
        .onNodeWithTag("sign_in_scroll_container")
        .performScrollToNode(hasTestTag(C.Tag.sign_in_sign_up))

    composeTestRule.onNodeWithTag(C.Tag.sign_in_sign_up).assertIsDisplayed()
  }
}
