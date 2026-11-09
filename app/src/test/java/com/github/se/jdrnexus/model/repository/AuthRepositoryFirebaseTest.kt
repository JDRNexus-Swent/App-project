// Co-authored-by: OpenAI Codex
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.model.repository

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthRepositoryFirebaseTest {

  @Test
  fun signUpCreatesAccountStoresUsernameAndPublishesSession() = runBlocking {
    val client = FakeFirebaseAuthClient()
    val repository = AuthRepositoryFirebase(client)

    val result = repository.signUpWithEmail("player@example.com", "password123", "  Player  ")

    val expectedUser = AuthUser("user-1", "player@example.com", "Player")
    assertEquals(AuthResult.Success(expectedUser), result)
    assertEquals(expectedUser, repository.authState.first())
    assertTrue(client.accountExists)
    assertEquals("password123", client.createdPassword)
  }

  @Test
  fun emailSignUpFailureBeforeAccountCreationReturnsFailure() = runBlocking {
    val client = FakeFirebaseAuthClient().apply { createFailure = AuthError.INVALID_EMAIL }
    val repository = AuthRepositoryFirebase(client)

    assertEquals(
        AuthResult.Failure(AuthError.INVALID_EMAIL),
        repository.signUpWithEmail("invalid", "password123", "Player"),
    )
    assertFalse(client.accountExists)
    assertEquals(0, client.deleteAccountCalls)
  }

  @Test
  fun cancellationDuringAccountCreationIsRethrownWithoutCleanup() = runBlocking {
    val client = FakeFirebaseAuthClient().apply { cancelAccountCreation = true }
    val repository = AuthRepositoryFirebase(client)

    val cancellation = expectCancellation {
      repository.signUpWithEmail("player@example.com", "password123", "Player")
    }

    assertEquals("Account creation cancelled", cancellation.message)
    assertFalse(client.accountExists)
    assertEquals(0, client.deleteAccountCalls)
    assertEquals(0, client.signOutCalls)
  }

  @Test
  fun usernameUpdateFailureCleansUpAndReturnsOriginalError() = runBlocking {
    val client = FakeFirebaseAuthClient().apply { usernameFailure = AuthError.NETWORK_ERROR }
    val repository = AuthRepositoryFirebase(client)

    assertEquals(
        AuthResult.Failure(AuthError.NETWORK_ERROR),
        repository.signUpWithEmail("player@example.com", "password123", "Player"),
    )
    assertFalse(client.accountExists)
    assertEquals(null, client.authState.value)
    assertEquals(1, client.deleteAccountCalls)
    assertEquals(1, client.signOutCalls)
  }

  @Test
  fun cleanupFailureReturnsDistinctRecoveryResultAndSignsOut() = runBlocking {
    val client =
        FakeFirebaseAuthClient().apply {
          usernameFailure = AuthError.NETWORK_ERROR
          deleteFailure = AuthError.NETWORK_ERROR
        }
    val repository = AuthRepositoryFirebase(client)

    val result = repository.signUpWithEmail("player@example.com", "password123", "Player")

    assertEquals(
        AuthResult.AccountCreatedNeedsRecovery(
            usernameError = AuthError.NETWORK_ERROR,
            accountDeletionError = AuthError.NETWORK_ERROR,
            signOutError = null,
        ),
        result,
    )
    assertTrue(client.accountExists)
    assertEquals(null, client.authState.value)
    assertEquals(1, client.deleteAccountCalls)
    assertEquals(1, client.signOutCalls)
    assertTrue((result as AuthResult.AccountCreatedNeedsRecovery).message.contains("Do not retry"))
  }

  @Test
  fun cancellingSignUpDuringUsernameUpdateStillCleansUp() = runBlocking {
    val client = FakeFirebaseAuthClient().apply { suspendUsernameUpdate = true }
    val repository = AuthRepositoryFirebase(client)
    var result: AuthResult<AuthUser>? = null

    val signUp = launch {
      result = repository.signUpWithEmail("player@example.com", "password123", "Player")
    }
    client.usernameUpdateStarted.await()
    signUp.cancelAndJoin()

    // No result: the cancellation is propagated, but cleanup ran even though the job was cancelled.
    assertTrue(signUp.isCancelled)
    assertEquals(null, result)
    assertFalse(client.accountExists)
    assertEquals(null, client.authState.value)
    assertEquals(1, client.deleteAccountCalls)
    assertEquals(1, client.signOutCalls)
  }

  @Test
  fun signOutFailureDuringCleanupIsReportedInRecoveryResult() = runBlocking {
    val client =
        FakeFirebaseAuthClient().apply {
          usernameFailure = AuthError.NETWORK_ERROR
          signOutFailure = AuthError.UNKNOWN
        }
    val repository = AuthRepositoryFirebase(client)

    assertEquals(
        AuthResult.AccountCreatedNeedsRecovery(
            usernameError = AuthError.NETWORK_ERROR,
            accountDeletionError = null,
            signOutError = AuthError.UNKNOWN,
        ),
        repository.signUpWithEmail("player@example.com", "password123", "Player"),
    )
    assertFalse(client.accountExists)
    assertEquals(1, client.deleteAccountCalls)
    assertEquals(1, client.signOutCalls)
  }

  @Test
  fun cancellationDuringSignInIsRethrown() = runBlocking {
    val client =
        FakeFirebaseAuthClient().apply {
          unexpectedSignInFailure = CancellationException("Sign-in cancelled")
        }
    val repository = AuthRepositoryFirebase(client)

    val cancellation = expectCancellation {
      repository.signInWithEmail("player@example.com", "secret")
    }

    assertEquals("Sign-in cancelled", cancellation.message)
  }

  @Test
  fun cancellationWithFailedCleanupKeepsCleanupErrorAsSuppressed() = runBlocking {
    val client =
        FakeFirebaseAuthClient().apply {
          suspendUsernameUpdate = true
          deleteFailure = AuthError.NETWORK_ERROR
        }
    val repository = AuthRepositoryFirebase(client)
    var cancellation: CancellationException? = null

    val signUp = launch {
      try {
        repository.signUpWithEmail("player@example.com", "password123", "Player")
      } catch (failure: CancellationException) {
        cancellation = failure
        throw failure
      }
    }
    client.usernameUpdateStarted.await()
    signUp.cancelAndJoin()

    // Coroutines debug mode may rethrow a copy of the exception, with the original as its cause.
    val cleanupErrors =
        generateSequence<Throwable>(requireNotNull(cancellation)) { it.cause }
            .flatMap { it.suppressed.asSequence() }
            .filterIsInstance<AuthOperationException>()
            .map { it.error }
            .toList()
    assertEquals(listOf(AuthError.NETWORK_ERROR), cleanupErrors)
    assertTrue(client.accountExists)
    assertEquals(1, client.signOutCalls)
  }

  @Test
  fun unexpectedExceptionIsReportedAsUnknownError() = runBlocking {
    val client =
        FakeFirebaseAuthClient().apply { unexpectedSignInFailure = IllegalStateException("boom") }
    val repository = AuthRepositoryFirebase(client)

    assertEquals(
        AuthResult.Failure(AuthError.UNKNOWN),
        repository.signInWithEmail("player@example.com", "secret"),
    )
  }

  @Test
  fun signInWithEmailReturnsCurrentAccount() = runBlocking {
    val client = FakeFirebaseAuthClient()
    val repository = AuthRepositoryFirebase(client)
    val user = AuthUser("user-2", "player@example.com", "Player")
    client.emailSignInResult = user

    assertEquals(
        AuthResult.Success(user),
        repository.signInWithEmail("player@example.com", "secret"),
    )
    assertEquals(user, repository.authState.first())
  }

  @Test
  fun signInWithEmailFailureReturnsTypedError() = runBlocking {
    val client = FakeFirebaseAuthClient().apply { emailSignInFailure = AuthError.WRONG_PASSWORD }
    val repository = AuthRepositoryFirebase(client)

    assertEquals(
        AuthResult.Failure(AuthError.WRONG_PASSWORD),
        repository.signInWithEmail("player@example.com", "wrong"),
    )
  }

  @Test
  fun googleSignInPassesIdTokenToFirebaseClient() = runBlocking {
    val client = FakeFirebaseAuthClient()
    val repository = AuthRepositoryFirebase(client)
    val user = AuthUser("google-user", "player@example.com", "Player")
    client.googleSignInResult = user

    assertEquals(AuthResult.Success(user), repository.signInWithGoogle("google-id-token"))
    assertEquals("google-id-token", client.googleIdToken)
  }

  @Test
  fun googleSignInFailureReturnsTypedError() = runBlocking {
    val client =
        FakeFirebaseAuthClient().apply { googleSignInFailure = AuthError.INVALID_CREDENTIALS }
    val repository = AuthRepositoryFirebase(client)

    assertEquals(
        AuthResult.Failure(AuthError.INVALID_CREDENTIALS),
        repository.signInWithGoogle("google-id-token"),
    )
  }

  @Test
  fun mapsEveryFirebaseErrorCode() {
    val expected =
        mapOf(
            "ERROR_INVALID_EMAIL" to AuthError.INVALID_EMAIL,
            "ERROR_MISSING_EMAIL" to AuthError.INVALID_EMAIL,
            "ERROR_WRONG_PASSWORD" to AuthError.WRONG_PASSWORD,
            "ERROR_INVALID_LOGIN_CREDENTIALS" to AuthError.INVALID_CREDENTIALS,
            "ERROR_INVALID_CREDENTIAL" to AuthError.INVALID_CREDENTIALS,
            "ERROR_USER_NOT_FOUND" to AuthError.USER_NOT_FOUND,
            "ERROR_EMAIL_ALREADY_IN_USE" to AuthError.EMAIL_ALREADY_IN_USE,
            "ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL" to
                AuthError.ACCOUNT_EXISTS_WITH_DIFFERENT_PROVIDER,
            "ERROR_WEAK_PASSWORD" to AuthError.WEAK_PASSWORD,
            "ERROR_MISSING_PASSWORD" to AuthError.WEAK_PASSWORD,
            "ERROR_NETWORK_REQUEST_FAILED" to AuthError.NETWORK_ERROR,
            "ERROR_TOO_MANY_REQUESTS" to AuthError.TOO_MANY_REQUESTS,
            "ERROR_SOMETHING_NEW" to AuthError.UNKNOWN,
        )

    expected.forEach { (code, error) -> assertEquals(code, error, authErrorForFirebaseCode(code)) }
  }

  @Test
  fun authStateRestoresExistingFirebaseSession() = runBlocking {
    val existingUser = AuthUser("existing-user", "player@example.com", "Player")
    val repository = AuthRepositoryFirebase(FakeFirebaseAuthClient(existingUser))

    assertEquals(existingUser, repository.authState.first())
  }

  @Test
  fun signOutClearsSession() = runBlocking {
    val client = FakeFirebaseAuthClient(AuthUser("user-1", "player@example.com", "Player"))
    val repository = AuthRepositoryFirebase(client)

    assertEquals(AuthResult.Success(Unit), repository.signOut())
    assertEquals(null, repository.authState.first())
  }

  @Test
  fun signOutFailureReturnsTypedError() = runBlocking {
    val client = FakeFirebaseAuthClient().apply { signOutFailure = AuthError.UNKNOWN }
    val repository = AuthRepositoryFirebase(client)

    assertEquals(AuthResult.Failure(AuthError.UNKNOWN), repository.signOut())
  }

  @Test
  fun blankUsernameAndGoogleTokenReturnReadableFailures() = runBlocking {
    val client = FakeFirebaseAuthClient()
    val repository = AuthRepositoryFirebase(client)

    assertEquals(
        AuthResult.Failure(AuthError.INVALID_USERNAME),
        repository.signUpWithEmail("player@example.com", "password123", "  "),
    )
    assertEquals(
        AuthResult.Failure(AuthError.INVALID_GOOGLE_CREDENTIAL),
        repository.signInWithGoogle(" "),
    )
    assertEquals(null, client.createdPassword)
    assertEquals(null, client.googleIdToken)
  }

  private suspend fun expectCancellation(operation: suspend () -> Unit): CancellationException =
      try {
        operation()
        throw AssertionError("Expected cancellation")
      } catch (failure: CancellationException) {
        failure
      }

  private class FakeFirebaseAuthClient(initialUser: AuthUser? = null) : FirebaseAuthClient {
    override val authState = MutableStateFlow(initialUser)
    var accountExists = initialUser != null
    var createdPassword: String? = null
    var googleIdToken: String? = null
    var emailSignInResult = AuthUser("user-2", "player@example.com", "Player")
    var googleSignInResult = AuthUser("google-user", "player@example.com", "Player")
    var createFailure: AuthError? = null
    var usernameFailure: AuthError? = null
    var emailSignInFailure: AuthError? = null
    var googleSignInFailure: AuthError? = null
    var deleteFailure: AuthError? = null
    var signOutFailure: AuthError? = null
    var unexpectedSignInFailure: Exception? = null
    var suspendUsernameUpdate = false
    val usernameUpdateStarted = CompletableDeferred<Unit>()
    var cancelAccountCreation = false
    var deleteAccountCalls = 0
    var signOutCalls = 0

    override suspend fun createEmailAccount(email: String, password: String): AuthUser {
      if (cancelAccountCreation) throw CancellationException("Account creation cancelled")
      createFailure?.let { throw AuthOperationException(it) }
      createdPassword = password
      val createdUser = AuthUser("user-1", email, null)
      accountExists = true
      authState.value = createdUser
      return createdUser
    }

    override suspend fun updateUsername(uid: String, username: String): AuthUser {
      if (suspendUsernameUpdate) {
        usernameUpdateStarted.complete(Unit)
        awaitCancellation()
      }
      usernameFailure?.let { throw AuthOperationException(it) }
      return requireNotNull(authState.value).copy(username = username).also { authState.value = it }
    }

    override suspend fun deleteAccount(uid: String) {
      deleteAccountCalls++
      // Like Task.await(), fails if the caller is cancelled: cleanup must run as NonCancellable.
      currentCoroutineContext().ensureActive()
      deleteFailure?.let { throw AuthOperationException(it) }
      accountExists = false
      authState.value = null
    }

    override suspend fun signInWithEmail(email: String, password: String): AuthUser {
      emailSignInFailure?.let { throw AuthOperationException(it) }
      unexpectedSignInFailure?.let { throw it }
      return emailSignInResult.also { authState.value = it }
    }

    override suspend fun signInWithGoogle(idToken: String): AuthUser {
      googleSignInFailure?.let { throw AuthOperationException(it) }
      googleIdToken = idToken
      return googleSignInResult.also { authState.value = it }
    }

    override fun signOut() {
      signOutCalls++
      signOutFailure?.let { throw AuthOperationException(it) }
      authState.value = null
    }
  }
}
