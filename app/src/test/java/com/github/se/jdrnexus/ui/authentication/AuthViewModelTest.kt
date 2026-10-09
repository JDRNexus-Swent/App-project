// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.github.se.jdrnexus.ui.authentication

import com.github.se.jdrnexus.model.repository.AuthError
import com.github.se.jdrnexus.model.repository.AuthRepository
import com.github.se.jdrnexus.model.repository.AuthResult
import com.github.se.jdrnexus.model.repository.AuthUser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  @Test
  fun inputChangesAreExposedThroughState() {
    val viewModel = AuthViewModel(FakeAuthRepository())

    viewModel.onEmailChange("player@example.com")
    viewModel.onPasswordChange("password")
    viewModel.onUsernameChange("Player")

    assertEquals(
        AuthUiState(
            email = "player@example.com",
            password = "password",
            username = "Player",
        ),
        viewModel.uiState.value,
    )
  }

  @Test
  fun signUpRejectsInvalidEmailWithoutCallingRepository() = runTest {
    val repository = FakeAuthRepository()
    val viewModel = AuthViewModel(repository)
    fillForm(viewModel, email = "invalid")

    viewModel.signUp()

    assertEquals(AuthStatus.Error(AuthError.INVALID_EMAIL.message), viewModel.uiState.value.status)
    assertEquals(0, repository.signUpCalls)
  }

  @Test
  fun signUpRejectsShortPasswordWithoutCallingRepository() = runTest {
    val repository = FakeAuthRepository()
    val viewModel = AuthViewModel(repository)
    fillForm(viewModel, password = "12345")

    viewModel.signUp()

    assertEquals(
        AuthStatus.Error("Password must be at least 6 characters."),
        viewModel.uiState.value.status,
    )
    assertEquals(0, repository.signUpCalls)
  }

  @Test
  fun signUpRejectsBlankUsernameWithoutCallingRepository() = runTest {
    val repository = FakeAuthRepository()
    val viewModel = AuthViewModel(repository)
    fillForm(viewModel, username = "  ")

    viewModel.signUp()

    assertEquals(
        AuthStatus.Error(AuthError.INVALID_USERNAME.message),
        viewModel.uiState.value.status,
    )
    assertEquals(0, repository.signUpCalls)
  }

  @Test
  fun signUpSetsLoadingAndSuccessAndPassesTrimmedFormValues() = runTest {
    val repository = FakeAuthRepository()
    val response = CompletableDeferred<AuthResult<AuthUser>>()
    repository.signUpResponse = response
    val viewModel = AuthViewModel(repository)
    fillForm(viewModel, email = " player@example.com ", username = " Player ")

    viewModel.signUp()
    runCurrent()

    assertEquals(AuthStatus.Loading, viewModel.uiState.value.status)
    assertEquals(
        Triple("Player", "player@example.com", "password"),
        repository.lastSignUpArguments,
    )

    response.complete(AuthResult.Success(USER))
    runCurrent()

    assertEquals(AuthStatus.Success(USER), viewModel.uiState.value.status)
  }

  @Test
  fun signUpExposesRepositoryErrorMessage() = runTest {
    val repository =
        FakeAuthRepository().apply {
          signUpResponse = CompletableDeferred(AuthResult.Failure(AuthError.EMAIL_ALREADY_IN_USE))
        }
    val viewModel = AuthViewModel(repository)
    fillForm(viewModel)

    viewModel.signUp()
    runCurrent()

    assertEquals(
        AuthStatus.Error(AuthError.EMAIL_ALREADY_IN_USE.message),
        viewModel.uiState.value.status,
    )
  }

  @Test
  fun signUpRecoveryFailureUsesItsUserFriendlyMessage() = runTest {
    val repository =
        FakeAuthRepository().apply {
          signUpResponse =
              CompletableDeferred(
                  AuthResult.AccountCreatedNeedsRecovery(
                      usernameError = AuthError.UNKNOWN,
                      accountDeletionError = AuthError.NETWORK_ERROR,
                      signOutError = null,
                  )
              )
        }
    val viewModel = AuthViewModel(repository)
    fillForm(viewModel)

    viewModel.signUp()
    runCurrent()

    assertEquals(
        AuthStatus.Error(
            "Your account was created, but setup and cleanup did not finish. Do not retry sign-up; " +
                "try signing in with this email or contact support."
        ),
        viewModel.uiState.value.status,
    )
  }

  @Test
  fun signInRejectsEmptyEmailWithoutCallingRepository() = runTest {
    val repository = FakeAuthRepository()
    val viewModel = AuthViewModel(repository)
    fillForm(viewModel, email = "")

    viewModel.signIn()

    assertEquals(AuthStatus.Error(AuthError.INVALID_EMAIL.message), viewModel.uiState.value.status)
    assertEquals(0, repository.signInCalls)
  }

  @Test
  fun signInRejectsShortPasswordWithoutCallingRepository() = runTest {
    val repository = FakeAuthRepository()
    val viewModel = AuthViewModel(repository)
    fillForm(viewModel, password = "12345")

    viewModel.signIn()

    assertEquals(
        AuthStatus.Error("Password must be at least 6 characters."),
        viewModel.uiState.value.status,
    )
    assertEquals(0, repository.signInCalls)
  }

  @Test
  fun signInExposesSuccessfulUser() = runTest {
    val repository = FakeAuthRepository()
    val viewModel = AuthViewModel(repository)
    fillForm(viewModel)

    viewModel.signIn()
    runCurrent()

    assertEquals(AuthStatus.Success(USER), viewModel.uiState.value.status)
    assertEquals("player@example.com" to "password", repository.lastSignInArguments)
  }

  @Test
  fun googleSignInPassesTokenAndExposesSuccessfulUser() = runTest {
    val repository = FakeAuthRepository()
    val viewModel = AuthViewModel(repository)

    viewModel.signInWithGoogle("id-token")
    runCurrent()

    assertEquals(1, repository.googleSignInCalls)
    assertEquals("id-token", repository.lastGoogleToken)
    assertEquals(AuthStatus.Success(USER), viewModel.uiState.value.status)
  }

  @Test
  fun googleSignInRejectsBlankTokenWithoutCallingRepository() = runTest {
    val repository = FakeAuthRepository()
    val viewModel = AuthViewModel(repository)

    viewModel.signInWithGoogle("")

    assertEquals(
        AuthStatus.Error(AuthError.INVALID_GOOGLE_CREDENTIAL.message),
        viewModel.uiState.value.status,
    )
    assertEquals(0, repository.googleSignInCalls)
  }

  @Test
  fun signOutCallsRepositoryAndReturnsToIdle() = runTest {
    val repository = FakeAuthRepository()
    val viewModel = AuthViewModel(repository)

    viewModel.signOut()
    runCurrent()

    assertEquals(1, repository.signOutCalls)
    assertEquals(AuthStatus.Idle, viewModel.uiState.value.status)
  }

  @Test
  fun signOutExposesRepositoryFailure() = runTest {
    val repository =
        FakeAuthRepository().apply { signOutResponse = AuthResult.Failure(AuthError.UNKNOWN) }
    val viewModel = AuthViewModel(repository)

    viewModel.signOut()
    runCurrent()

    assertEquals(1, repository.signOutCalls)
    assertEquals(AuthStatus.Error(AuthError.UNKNOWN.message), viewModel.uiState.value.status)
  }

  @Test
  fun repositoryExceptionBecomesGenericFriendlyError() = runTest {
    val repository =
        FakeAuthRepository().apply {
          signInFailure = IllegalStateException("internal implementation detail")
        }
    val viewModel = AuthViewModel(repository)
    fillForm(viewModel)

    viewModel.signIn()
    runCurrent()

    assertEquals(AuthStatus.Error(AuthError.UNKNOWN.message), viewModel.uiState.value.status)
  }

  @Test
  fun cancellationCancelsRequestAndResetsStatus() = runTest {
    val repository =
        FakeAuthRepository().apply { signInFailure = CancellationException("cancelled") }
    val viewModel = AuthViewModel(repository)
    fillForm(viewModel)
    viewModel.signIn()
    runCurrent()

    assertTrue(repository.signInJob?.isCancelled == true)
    assertEquals(AuthStatus.Idle, viewModel.uiState.value.status)
  }

  private fun fillForm(
      viewModel: AuthViewModel,
      email: String = "player@example.com",
      password: String = "password",
      username: String = "Player",
  ) {
    viewModel.onEmailChange(email)
    viewModel.onPasswordChange(password)
    viewModel.onUsernameChange(username)
  }

  private class FakeAuthRepository : AuthRepository {
    override val authState: Flow<AuthUser?> = MutableStateFlow(null)
    var signUpCalls = 0
    var signInCalls = 0
    var googleSignInCalls = 0
    var signOutCalls = 0
    var signInJob: Job? = null
    var lastSignUpArguments: Triple<String, String, String>? = null
    var lastSignInArguments: Pair<String, String>? = null
    var lastGoogleToken: String? = null
    var signUpResponse: CompletableDeferred<AuthResult<AuthUser>> =
        CompletableDeferred(AuthResult.Success(USER))
    var signInResponse: AuthResult<AuthUser> = AuthResult.Success(USER)
    var googleResponse: AuthResult<AuthUser> = AuthResult.Success(USER)
    var signOutResponse: AuthResult<Unit> = AuthResult.Success(Unit)
    var signInFailure: Exception? = null

    override suspend fun signUpWithEmail(
        email: String,
        password: String,
        username: String,
    ): AuthResult<AuthUser> {
      signUpCalls++
      lastSignUpArguments = Triple(username, email, password)
      return signUpResponse.await()
    }

    override suspend fun signInWithEmail(email: String, password: String): AuthResult<AuthUser> {
      signInCalls++
      lastSignInArguments = email to password
      signInJob = currentCoroutineContext()[Job]
      signInFailure?.let { throw it }
      return signInResponse
    }

    override suspend fun signInWithGoogle(idToken: String): AuthResult<AuthUser> {
      googleSignInCalls++
      lastGoogleToken = idToken
      return googleResponse
    }

    override suspend fun signOut(): AuthResult<Unit> {
      signOutCalls++
      return signOutResponse
    }
  }

  private companion object {
    val USER = AuthUser(uid = "user-1", email = "player@example.com", username = "Player")
  }
}

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule : TestWatcher() {
  private val dispatcher = StandardTestDispatcher()

  override fun starting(description: Description) {
    Dispatchers.setMain(dispatcher)
  }

  override fun finished(description: Description) {
    Dispatchers.resetMain()
  }
}
