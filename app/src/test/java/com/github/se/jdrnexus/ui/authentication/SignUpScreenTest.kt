package com.github.se.jdrnexus.ui.authentication

import android.app.PendingIntent
import android.content.Context
import android.os.CancellationSignal
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
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
@Config(qualifiers = "w360dp-h2400dp-xhdpi")
class SignUpScreenTest {
  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun signUpScreen_displaysAllRegistrationContent() {
    showSignUp()

    composeTestRule.onNodeWithText("JDRNexus").assertIsDisplayed()
    composeTestRule.onNodeWithText("Email address").assertIsDisplayed()
    composeTestRule.onNodeWithText("Password").assertIsDisplayed()
    composeTestRule.onNodeWithText("Register").assertIsDisplayed()
    composeTestRule
        .onAllNodesWithText("Already have an account?", substring = true)
        .assertCountEquals(1)
    composeTestRule.onNodeWithText("Log in").assertIsDisplayed()
    usernameInput().assertIsDisplayed()
    emailInput(C.Tag.sign_up_email).assertIsDisplayed()
    passwordInput(C.Tag.sign_up_password).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.sign_up_submit).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.sign_up_sign_in).assertIsDisplayed()
  }

  @Test
  fun signUpScreen_acceptsUsernameEmailAndMasksPasswordUntilVisibilityIsToggled() {
    showSignUp()
    val username = usernameInput()
    val email = emailInput(C.Tag.sign_up_email)
    val password = passwordInput(C.Tag.sign_up_password)
    username.performTextInput("Ranger")
    username.assertTextEquals("Ranger")
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
        .onNodeWithTag(C.Tag.sign_up_password_visibility)
        .assertHasClickAction()
        .performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNode(passwordMatcher).assertIsDisplayed()
    password.assertTextEquals("secret-pass")
    assertEquals(
        "secret-pass",
        password.fetchSemanticsNode().config[SemanticsProperties.InputText].text,
    )

    composeTestRule.onNodeWithTag(C.Tag.sign_up_password_visibility).performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNode(passwordMatcher).assertIsDisplayed()
    password.assertTextEquals("•••••••••••")
    assertEquals(
        "secret-pass",
        password.fetchSemanticsNode().config[SemanticsProperties.InputText].text,
    )
  }

  @Test
  fun signUpScreen_emptyUsernameShowsValidation() {
    val repository = FakeAuthRepository()
    showSignUp(repository)
    emailInput(C.Tag.sign_up_email).performTextInput("hero@example.com")
    passwordInput(C.Tag.sign_up_password).performTextInput("secret1")

    composeTestRule.onNodeWithTag(C.Tag.sign_up_submit).performClick()

    composeTestRule.onNodeWithText(AuthError.INVALID_USERNAME.message).assertIsDisplayed()
    assertEquals(0, repository.signUpCalls)
  }

  @Test
  fun signUpScreen_invalidEmailShowsValidation() {
    val repository = FakeAuthRepository()
    showSignUp(repository)
    usernameInput().performTextInput("Ranger")
    emailInput(C.Tag.sign_up_email).performTextInput("invalid")
    passwordInput(C.Tag.sign_up_password).performTextInput("secret1")

    composeTestRule.onNodeWithTag(C.Tag.sign_up_submit).performClick()

    composeTestRule.onNodeWithText(AuthError.INVALID_EMAIL.message).assertIsDisplayed()
    assertEquals(0, repository.signUpCalls)
  }

  @Test
  fun signUpScreen_shortPasswordShowsValidationAndEditingInputClearsError() {
    val repository = FakeAuthRepository()
    showSignUp(repository)
    usernameInput().performTextInput("Ranger")
    emailInput(C.Tag.sign_up_email).performTextInput("hero@example.com")
    passwordInput(C.Tag.sign_up_password).performTextInput("12345")

    composeTestRule.onNodeWithTag(C.Tag.sign_up_submit).performClick()

    composeTestRule.onNodeWithText("Password must be at least 6 characters.").assertIsDisplayed()
    assertEquals(0, repository.signUpCalls)

    passwordInput(C.Tag.sign_up_password).performTextInput("6")
    composeTestRule
        .onAllNodesWithText("Password must be at least 6 characters.")
        .assertCountEquals(0)
  }

  @Test
  fun signUpScreen_invalidSubmissionDisplaysValidationErrors() {
    val repository = FakeAuthRepository()
    showSignUp(repository)

    composeTestRule.onNodeWithTag(C.Tag.sign_up_submit).performClick()

    composeTestRule.onNodeWithText(AuthError.INVALID_EMAIL.message).assertIsDisplayed()
    assertEquals(0, repository.signUpCalls)
  }

  @Test
  fun signUpScreen_loadingDisablesSubmitAndReplacesButtonTextWithProgress() {
    val repository = FakeAuthRepository().apply { signUpResponse = CompletableDeferred() }
    showSignUp(repository)
    enterSignUpCredentials()

    composeTestRule.onNodeWithTag(C.Tag.sign_up_submit).performClick()
    composeTestRule.waitForIdle()

    assertEquals(1, repository.signUpCalls)
    composeTestRule.onAllNodesWithText("Register").assertCountEquals(0)
    composeTestRule.onNodeWithTag(C.Tag.sign_up_submit).assertIsDisplayed().assertIsNotEnabled()
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
  fun signUpScreen_registrationFailureDisplaysMessageAndRestoresRegisterButton() {
    val repository =
        FakeAuthRepository().apply {
          signUpResponse = CompletableDeferred(AuthResult.Failure(AuthError.EMAIL_ALREADY_IN_USE))
        }
    showSignUp(repository)
    enterSignUpCredentials()

    composeTestRule.onNodeWithTag(C.Tag.sign_up_submit).performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithText(AuthError.EMAIL_ALREADY_IN_USE.message).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.sign_up_submit).assertIsDisplayed().assertIsEnabled()
    composeTestRule.onNodeWithText("Register").assertIsDisplayed()
    assertEquals(1, repository.signUpCalls)
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
  fun signUpScreen_successfulRegistrationInvokesRegisterCallback() {
    val repository = FakeAuthRepository()
    var registered = false
    showSignUp(repository, onRegister = { registered = true })
    enterSignUpCredentials()

    composeTestRule.onNodeWithTag(C.Tag.sign_up_submit).performClick()
    composeTestRule.waitForIdle()

    assertEquals(1, repository.signUpCalls)
    assertEquals(true, registered)
  }

  @Test
  fun signUpScreen_logInInvokesSignInNavigationCallback() {
    var signInRequested = false
    showSignUp(onSignInScreen = { signInRequested = true })

    composeTestRule.onNodeWithTag(C.Tag.sign_up_sign_in).performClick()

    assertEquals(true, signInRequested)
  }

  private fun showSignUp(
      repository: FakeAuthRepository = FakeAuthRepository(),
      onRegister: () -> Unit = {},
      onSignInScreen: () -> Unit = {},
  ) {
    val viewModel = AuthViewModel(repository)
    composeTestRule.setContent {
      CompositionLocalProvider(LocalDensity provides Density(0.7f)) {
        SampleAppTheme {
          SignUpScreen(
              signUpViewModel = viewModel,
              onRegister = onRegister,
              onSignInScreen = onSignInScreen,
          )
        }
      }
    }
  }

  private fun emailInput(tag: String) = composeTestRule.onNodeWithTag(tag)

  private fun passwordInput(tag: String) = composeTestRule.onNodeWithTag(tag)

  private fun usernameInput() = composeTestRule.onNodeWithTag(C.Tag.sign_up_username)

  private fun enterSignUpCredentials() {
    usernameInput().performTextInput("Ranger")
    emailInput(C.Tag.sign_up_email).performTextInput("hero@example.com")
    passwordInput(C.Tag.sign_up_password).performTextInput("secret1")
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
}
