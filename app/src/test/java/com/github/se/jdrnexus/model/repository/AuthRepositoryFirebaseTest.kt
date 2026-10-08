// Co-authored-by: OpenAI Codex
package com.github.se.jdrnexus.model.repository

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
  fun cancellationDuringUsernameUpdateCleansUpAndIsRethrown() = runBlocking {
    val client = FakeFirebaseAuthClient().apply { cancelUsernameUpdate = true }
    val repository = AuthRepositoryFirebase(client)
    var cancellation: CancellationException? = null

    try {
      withContext(NonCancellable) {
        repository.signUpWithEmail("player@example.com", "password123", "Player")
      }
    } catch (failure: CancellationException) {
      cancellation = failure
    }

    assertNotNull(cancellation)
    assertFalse(client.accountExists)
    assertEquals(1, client.deleteAccountCalls)
    assertEquals(1, client.signOutCalls)
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
  fun mapsCommonFirebaseErrors() {
    assertEquals(AuthError.INVALID_EMAIL, authErrorForFirebaseCode("ERROR_INVALID_EMAIL"))
    assertEquals(AuthError.WRONG_PASSWORD, authErrorForFirebaseCode("ERROR_WRONG_PASSWORD"))
    assertEquals(
        AuthError.EMAIL_ALREADY_IN_USE,
        authErrorForFirebaseCode("ERROR_EMAIL_ALREADY_IN_USE"),
    )
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
    val repository = AuthRepositoryFirebase(FakeFirebaseAuthClient())

    assertEquals(
        AuthResult.Failure(AuthError.INVALID_USERNAME),
        repository.signUpWithEmail("player@example.com", "password123", "  "),
    )
    assertEquals(
        AuthResult.Failure(AuthError.INVALID_GOOGLE_CREDENTIAL),
        repository.signInWithGoogle(" "),
    )
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
    var cancelUsernameUpdate = false
    var deleteAccountCalls = 0
    var signOutCalls = 0

    override suspend fun createEmailAccount(email: String, password: String): AuthUser {
      createFailure?.let { throw AuthOperationException(it) }
      createdPassword = password
      val createdUser = AuthUser("user-1", email, null)
      accountExists = true
      authState.value = createdUser
      return createdUser
    }

    override suspend fun updateUsername(uid: String, username: String): AuthUser {
      if (cancelUsernameUpdate) throw CancellationException("Username update cancelled")
      usernameFailure?.let { throw AuthOperationException(it) }
      return requireNotNull(authState.value).copy(username = username).also { authState.value = it }
    }

    override suspend fun deleteAccount(uid: String) {
      deleteAccountCalls++
      deleteFailure?.let { throw AuthOperationException(it) }
      accountExists = false
      authState.value = null
    }

    override suspend fun signInWithEmail(email: String, password: String): AuthUser {
      emailSignInFailure?.let { throw AuthOperationException(it) }
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
