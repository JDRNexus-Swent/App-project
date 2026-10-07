// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.github.se.jdrnexus.ui.authentification

import com.github.se.jdrnexus.model.repository.AuthError
import com.github.se.jdrnexus.model.repository.AuthRepository
import com.github.se.jdrnexus.model.repository.AuthResult
import com.github.se.jdrnexus.model.repository.AuthUser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthViewModelTest {

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
  fun signUpRejectsInvalidEmailWithoutCallingRepository() = runBlocking {
    val repository = FakeAuthRepository()
    val viewModel = AuthViewModel(repository)
    fillForm(viewModel, email = "invalid")

    viewModel.signUp()

    assertEquals(AuthStatus.Error(AuthError.INVALID_EMAIL.message), viewModel.uiState.value.status)
    assertEquals(0, repository.signUpCalls)
  }

  @Test
  fun signUpRejectsShortPasswordWithoutCallingRepository() = runBlocking {
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
  fun signUpRejectsBlankUsernameWithoutCallingRepository() = runBlocking {
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
  fun signUpSetsLoadingAndSuccessAndPassesTrimmedFormValues() = runBlocking {
    val repository = FakeAuthRepository()
    val response = CompletableDeferred<AuthResult<AuthUser>>()
    repository.signUpResponse = response
    val viewModel = AuthViewModel(repository)
    fillForm(viewModel, email = " player@example.com ", username = " Player ")

    val operation = async(start = CoroutineStart.UNDISPATCHED) { viewModel.signUp() }

    assertEquals(AuthStatus.Loading, viewModel.uiState.value.status)
    assertEquals(
        Triple("Player", "player@example.com", "password"),
        repository.lastSignUpArguments,
    )

    response.complete(AuthResult.Success(USER))
    operation.await()

    assertEquals(AuthStatus.Success(USER), viewModel.uiState.value.status)
  }

  @Test
  fun signUpExposesRepositoryErrorMessage() = runBlocking {
    val repository =
        FakeAuthRepository().apply {
          signUpResponse = CompletableDeferred(AuthResult.Failure(AuthError.EMAIL_ALREADY_IN_USE))
        }
    val viewModel = AuthViewModel(repository)
    fillForm(viewModel)

    viewModel.signUp()

    assertEquals(
        AuthStatus.Error(AuthError.EMAIL_ALREADY_IN_USE.message),
        viewModel.uiState.value.status,
    )
  }

  @Test
  fun signUpRecoveryFailureUsesItsUserFriendlyMessage() = runBlocking {
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

    assertEquals(
        AuthStatus.Error(
            "Your account was created, but setup and cleanup did not finish. Do not retry sign-up; " +
                "try signing in with this email or contact support."
        ),
        viewModel.uiState.value.status,
    )
  }

  @Test
  fun signInRejectsEmptyEmailWithoutCallingRepository() = runBlocking {
    val repository = FakeAuthRepository()
    val viewModel = AuthViewModel(repository)
    fillForm(viewModel, email = "")

    viewModel.signIn()

    assertEquals(AuthStatus.Error(AuthError.INVALID_EMAIL.message), viewModel.uiState.value.status)
    assertEquals(0, repository.signInCalls)
  }

  @Test
  fun signInRejectsShortPasswordWithoutCallingRepository() = runBlocking {
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
  fun signInExposesSuccessfulUser() = runBlocking {
    val repository = FakeAuthRepository()
    val viewModel = AuthViewModel(repository)
    fillForm(viewModel)

    viewModel.signIn()

    assertEquals(AuthStatus.Success(USER), viewModel.uiState.value.status)
    assertEquals("player@example.com" to "password", repository.lastSignInArguments)
  }

  @Test
  fun googleSignInPassesTokenAndExposesSuccessfulUser() = runBlocking {
    val repository = FakeAuthRepository()
    val viewModel = AuthViewModel(repository)

    viewModel.signInWithGoogle("id-token")

    assertEquals(1, repository.googleSignInCalls)
    assertEquals("id-token", repository.lastGoogleToken)
    assertEquals(AuthStatus.Success(USER), viewModel.uiState.value.status)
  }

  @Test
  fun googleSignInRejectsBlankTokenWithoutCallingRepository() = runBlocking {
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
  fun repositoryExceptionBecomesGenericFriendlyError() = runBlocking {
    val repository =
        FakeAuthRepository().apply {
          signInFailure = IllegalStateException("internal implementation detail")
        }
    val viewModel = AuthViewModel(repository)
    fillForm(viewModel)

    viewModel.signIn()

    assertEquals(AuthStatus.Error(AuthError.UNKNOWN.message), viewModel.uiState.value.status)
  }

  @Test
  fun cancellationIsRethrown() = runBlocking {
    val repository =
        FakeAuthRepository().apply { signInFailure = CancellationException("cancelled") }
    val viewModel = AuthViewModel(repository)
    fillForm(viewModel)
    var wasCancelled = false

    try {
      viewModel.signIn()
    } catch (_: CancellationException) {
      wasCancelled = true
    }

    assertTrue(wasCancelled)
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
    var lastSignUpArguments: Triple<String, String, String>? = null
    var lastSignInArguments: Pair<String, String>? = null
    var lastGoogleToken: String? = null
    var signUpResponse: CompletableDeferred<AuthResult<AuthUser>> =
        CompletableDeferred(AuthResult.Success(USER))
    var signInResponse: AuthResult<AuthUser> = AuthResult.Success(USER)
    var googleResponse: AuthResult<AuthUser> = AuthResult.Success(USER)
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
      signInFailure?.let { throw it }
      return signInResponse
    }

    override suspend fun signInWithGoogle(idToken: String): AuthResult<AuthUser> {
      googleSignInCalls++
      lastGoogleToken = idToken
      return googleResponse
    }

    override suspend fun signOut(): AuthResult<Unit> = AuthResult.Success(Unit)
  }

  private companion object {
    val USER = AuthUser(uid = "user-1", email = "player@example.com", username = "Player")
  }
}
