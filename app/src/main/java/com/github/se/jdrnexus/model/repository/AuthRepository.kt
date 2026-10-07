// Co-authored-by: OpenAI Codex
package com.github.se.jdrnexus.model.repository

import kotlinx.coroutines.flow.Flow

data class AuthUser(val uid: String, val email: String?, val username: String?)

enum class AuthError(val message: String) {
  INVALID_EMAIL("Enter a valid email address."),
  WRONG_PASSWORD("The password is incorrect."),
  INVALID_CREDENTIALS("The email or password is incorrect."),
  USER_NOT_FOUND("No account exists for this email address."),
  EMAIL_ALREADY_IN_USE("An account with this email address already exists."),
  ACCOUNT_EXISTS_WITH_DIFFERENT_PROVIDER(
      "An account already exists for this email using a different sign-in method."
  ),
  WEAK_PASSWORD("Choose a stronger password."),
  INVALID_USERNAME("Enter a username."),
  SESSION_NOT_FOUND("The signed-in account is no longer available."),
  INVALID_GOOGLE_CREDENTIAL("Google sign-in could not be verified. Try again."),
  NETWORK_ERROR("Couldn't connect. Check your internet connection and try again."),
  TOO_MANY_REQUESTS("Too many attempts. Wait a little and try again."),
  UNKNOWN("Something went wrong. Please try again."),
}

sealed interface AuthResult<out T> {
  data class Success<T>(val value: T) : AuthResult<T>

  data class Failure(val error: AuthError) : AuthResult<Nothing>

  data class AccountCreatedNeedsRecovery(
      val usernameError: AuthError,
      val accountDeletionError: AuthError?,
      val signOutError: AuthError?,
  ) : AuthResult<Nothing> {
    val message: String =
        "Your account was created, but setup and cleanup did not finish. Do not retry sign-up; " +
            "try signing in with this email or contact support."
  }
}

interface AuthRepository {
  val authState: Flow<AuthUser?>

  suspend fun signUpWithEmail(
      email: String,
      password: String,
      username: String,
  ): AuthResult<AuthUser>

  suspend fun signInWithEmail(email: String, password: String): AuthResult<AuthUser>

  suspend fun signInWithGoogle(idToken: String): AuthResult<AuthUser>

  suspend fun signOut(): AuthResult<Unit>
}
