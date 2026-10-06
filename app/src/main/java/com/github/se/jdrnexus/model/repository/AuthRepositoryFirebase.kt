// Co-authored-by: OpenAI Codex
package com.github.se.jdrnexus.model.repository

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class AuthRepositoryFirebase internal constructor(private val client: FirebaseAuthClient) :
    AuthRepository {

  constructor(
      firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance()
  ) : this(FirebaseAuthClientFirebase(firebaseAuth))

  override val authState: Flow<AuthUser?>
    get() = client.authState

  override suspend fun signUpWithEmail(
      email: String,
      password: String,
      username: String,
  ): AuthResult<AuthUser> {
    val cleanUsername = username.trim()
    if (cleanUsername.isEmpty()) return AuthResult.Failure(AuthError.INVALID_USERNAME)

    val createdUser =
        try {
          client.createEmailAccount(email, password)
        } catch (cancellation: CancellationException) {
          throw cancellation
        } catch (failure: Exception) {
          return AuthResult.Failure(authErrorFor(failure))
        }

    return try {
      AuthResult.Success(client.updateUsername(createdUser.uid, cleanUsername))
    } catch (cancellation: CancellationException) {
      val cleanup = withContext(NonCancellable) { cleanupCreatedAccount(createdUser.uid) }
      cleanup.firstFailure()?.let { cancellation.addSuppressed(AuthOperationException(it)) }
      throw cancellation
    } catch (usernameFailure: Exception) {
      val cleanup = cleanupCreatedAccount(createdUser.uid)
      if (cleanup.isSuccessful) {
        AuthResult.Failure(authErrorFor(usernameFailure))
      } else {
        AuthResult.AccountCreatedNeedsRecovery(
            usernameError = authErrorFor(usernameFailure),
            accountDeletionError = cleanup.accountDeletionError,
            signOutError = cleanup.signOutError,
        )
      }
    }
  }

  override suspend fun signInWithEmail(email: String, password: String): AuthResult<AuthUser> =
      resultOf {
        client.signInWithEmail(email, password)
      }

  override suspend fun signInWithGoogle(idToken: String): AuthResult<AuthUser> = resultOf {
    if (idToken.isBlank()) {
      throw AuthOperationException(AuthError.INVALID_GOOGLE_CREDENTIAL)
    }
    client.signInWithGoogle(idToken)
  }

  override suspend fun signOut(): AuthResult<Unit> = resultOf { client.signOut() }

  private suspend fun <T> resultOf(operation: suspend () -> T): AuthResult<T> =
      try {
        AuthResult.Success(operation())
      } catch (cancellation: CancellationException) {
        throw cancellation
      } catch (failure: AuthOperationException) {
        AuthResult.Failure(failure.error)
      } catch (failure: Exception) {
        AuthResult.Failure(authErrorFor(failure))
      }

  private suspend fun cleanupCreatedAccount(uid: String): CleanupOutcome {
    var deletionError: AuthError? = null
    var signOutError: AuthError? = null

    try {
      client.deleteAccount(uid)
    } catch (cancellation: CancellationException) {
      throw cancellation
    } catch (failure: Exception) {
      deletionError = authErrorFor(failure)
    }

    try {
      client.signOut()
    } catch (cancellation: CancellationException) {
      throw cancellation
    } catch (failure: Exception) {
      signOutError = authErrorFor(failure)
    }

    return CleanupOutcome(deletionError, signOutError)
  }

  private fun CleanupOutcome.firstFailure(): AuthError? = accountDeletionError ?: signOutError

  private fun authErrorFor(failure: Exception): AuthError =
      (failure as? AuthOperationException)?.error ?: AuthError.UNKNOWN
}

private data class CleanupOutcome(
    val accountDeletionError: AuthError?,
    val signOutError: AuthError?,
) {
  val isSuccessful: Boolean
    get() = accountDeletionError == null && signOutError == null
}

internal interface FirebaseAuthClient {
  val authState: Flow<AuthUser?>

  suspend fun createEmailAccount(email: String, password: String): AuthUser

  suspend fun updateUsername(uid: String, username: String): AuthUser

  suspend fun deleteAccount(uid: String)

  suspend fun signInWithEmail(email: String, password: String): AuthUser

  suspend fun signInWithGoogle(idToken: String): AuthUser

  fun signOut()
}

internal class AuthOperationException(val error: AuthError, cause: Throwable? = null) :
    Exception(error.message, cause)

private class FirebaseAuthClientFirebase(private val firebaseAuth: FirebaseAuth) :
    FirebaseAuthClient {

  private val profileUpdates = MutableSharedFlow<AuthUser?>(extraBufferCapacity = 1)

  @OptIn(ExperimentalCoroutinesApi::class)
  override val authState: Flow<AuthUser?> =
      merge(
              callbackFlow {
                val listener = FirebaseAuth.AuthStateListener { auth ->
                  trySend(auth.currentUser?.toAuthUser())
                }
                firebaseAuth.addAuthStateListener(listener)
                awaitClose { firebaseAuth.removeAuthStateListener(listener) }
              },
              profileUpdates,
          )
          .distinctUntilChanged()

  override suspend fun createEmailAccount(email: String, password: String): AuthUser =
      withFirebaseErrors {
        firebaseAuth.createUserWithEmailAndPassword(email, password).await().user?.toAuthUser()
            ?: throw AuthOperationException(AuthError.UNKNOWN)
      }

  override suspend fun updateUsername(uid: String, username: String): AuthUser =
      withFirebaseErrors {
        val user =
            firebaseAuth.currentUser?.takeIf { it.uid == uid }
                ?: throw AuthOperationException(AuthError.SESSION_NOT_FOUND)
        val profile = UserProfileChangeRequest.Builder().setDisplayName(username).build()
        user.updateProfile(profile).await()
        user.toAuthUser().also { profileUpdates.tryEmit(it) }
      }

  override suspend fun deleteAccount(uid: String) {
    withFirebaseErrors {
      val user =
          firebaseAuth.currentUser?.takeIf { it.uid == uid }
              ?: throw AuthOperationException(AuthError.SESSION_NOT_FOUND)
      user.delete().await()
    }
  }

  override suspend fun signInWithEmail(email: String, password: String): AuthUser =
      withFirebaseErrors {
        firebaseAuth.signInWithEmailAndPassword(email, password).await().user?.toAuthUser()
            ?: throw AuthOperationException(AuthError.UNKNOWN)
      }

  override suspend fun signInWithGoogle(idToken: String): AuthUser = withFirebaseErrors {
    val credential = GoogleAuthProvider.getCredential(idToken, null)
    firebaseAuth.signInWithCredential(credential).await().user?.toAuthUser()
        ?: throw AuthOperationException(AuthError.UNKNOWN)
  }

  override fun signOut() {
    firebaseAuth.signOut()
  }

  private suspend fun <T> withFirebaseErrors(operation: suspend () -> T): T =
      try {
        operation()
      } catch (cancellation: CancellationException) {
        throw cancellation
      } catch (failure: AuthOperationException) {
        throw failure
      } catch (failure: FirebaseAuthException) {
        throw AuthOperationException(authErrorForFirebaseCode(failure.errorCode), failure)
      } catch (failure: FirebaseNetworkException) {
        throw AuthOperationException(AuthError.NETWORK_ERROR, failure)
      }
}

internal fun authErrorForFirebaseCode(code: String): AuthError =
    when (code) {
      "ERROR_INVALID_EMAIL",
      "ERROR_MISSING_EMAIL" -> AuthError.INVALID_EMAIL
      "ERROR_WRONG_PASSWORD" -> AuthError.WRONG_PASSWORD
      "ERROR_INVALID_LOGIN_CREDENTIALS",
      "ERROR_INVALID_CREDENTIAL" -> AuthError.INVALID_CREDENTIALS
      "ERROR_USER_NOT_FOUND" -> AuthError.USER_NOT_FOUND
      "ERROR_EMAIL_ALREADY_IN_USE" -> AuthError.EMAIL_ALREADY_IN_USE
      "ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL" ->
          AuthError.ACCOUNT_EXISTS_WITH_DIFFERENT_PROVIDER
      "ERROR_WEAK_PASSWORD",
      "ERROR_MISSING_PASSWORD" -> AuthError.WEAK_PASSWORD
      "ERROR_NETWORK_REQUEST_FAILED" -> AuthError.NETWORK_ERROR
      "ERROR_TOO_MANY_REQUESTS" -> AuthError.TOO_MANY_REQUESTS
      else -> AuthError.UNKNOWN
    }

private fun com.google.firebase.auth.FirebaseUser.toAuthUser(): AuthUser =
    AuthUser(uid = uid, email = email, username = displayName)
