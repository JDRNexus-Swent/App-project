// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.ui.authentication

import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.github.se.jdrnexus.R
import com.github.se.jdrnexus.model.repository.AuthError
import com.github.se.jdrnexus.model.repository.AuthRepository
import com.github.se.jdrnexus.model.repository.AuthRepositoryFirebase
import com.github.se.jdrnexus.model.repository.AuthResult
import com.github.se.jdrnexus.model.repository.AuthUser
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuthUiState(
    val email: String = "",
    val password: String = "",
    val username: String = "",
    val emailError: String? = null,
    val passwordError: String? = null,
    val usernameError: String? = null,
    val status: AuthStatus = AuthStatus.Idle,
)

sealed interface AuthStatus {
  data object Idle : AuthStatus

  data object Loading : AuthStatus

  data class Success(val user: AuthUser) : AuthStatus

  data class Error(val message: String) : AuthStatus
}

/** Whether a user is signed in, as reported by [AuthRepository.authState]. */
sealed interface SessionState {
  /** The repository has not reported the session yet. */
  data object Checking : SessionState

  data object SignedIn : SessionState

  data object SignedOut : SessionState
}

class AuthViewModel(private val repository: AuthRepository) : ViewModel() {

  private val _uiState = MutableStateFlow(AuthUiState())
  val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

  // The last session the repository reported; only used to spot a session that ends.
  private var currentSession: SessionState = SessionState.Checking

  private val _launchSession = MutableStateFlow<SessionState>(SessionState.Checking)

  /**
   * The session found when the app started. It leaves [SessionState.Checking] once and then never
   * changes, so the app can pick its first screen from it without reacting to later sign-ins.
   */
  val launchSession: StateFlow<SessionState> = _launchSession.asStateFlow()

  private val _sessionEnded = MutableStateFlow(false)

  /**
   * True from the moment a signed-in session ends (sign-out, expired token, deleted account) until
   * [onSessionEndHandled] is called. Kept as state, not as a transition to observe, so a screen
   * that starts collecting after the session ended still sees it.
   */
  val sessionEnded: StateFlow<Boolean> = _sessionEnded.asStateFlow()

  /** Marks the end of the session as handled, once the app has left the signed-in screens. */
  fun onSessionEndHandled() {
    _sessionEnded.value = false
  }

  init {
    viewModelScope.launch { observeSession() }
  }

  private suspend fun observeSession() {
    // A failing auth state is treated as signed out: the user can still sign in again.
    repository.authState
        .catch { failure ->
          if (failure is CancellationException) throw failure
          Log.e("AuthViewModel", "Auth state unavailable", failure)
          emit(null)
        }
        .collect { user ->
          val previous = currentSession
          val session = if (user == null) SessionState.SignedOut else SessionState.SignedIn
          currentSession = session
          if (_launchSession.value == SessionState.Checking) {
            _launchSession.value = session
            // Checked in the background: the user is not kept waiting, even offline.
            if (session == SessionState.SignedIn) viewModelScope.launch { refreshSession() }
          }
          val ended = previous == SessionState.SignedIn && session == SessionState.SignedOut
          if (session == SessionState.SignedIn) _sessionEnded.value = false
          if (ended) _sessionEnded.value = true
          // The session ended outside a request of this ViewModel (expired, deleted account):
          // forget the previous user's form. A running request handles its own state.
          if (ended && _uiState.value.status !is AuthStatus.Loading) {
            _uiState.value = AuthUiState()
          }
        }
  }

  private suspend fun refreshSession() {
    try {
      // An invalid session is signed out by the repository and reported through authState.
      repository.refreshSession()
    } catch (cancellation: CancellationException) {
      throw cancellation
    } catch (failure: Exception) {
      Log.e("AuthViewModel", "Session check failed", failure)
    }
  }

  fun onEmailChange(email: String) = updateInput { copy(email = email) }

  fun onPasswordChange(password: String) = updateInput { copy(password = password) }

  fun onUsernameChange(username: String) = updateInput { copy(username = username) }

  /**
   * Removes the field errors and turns an [AuthStatus.Error] back into [AuthStatus.Idle], keeping
   * the typed email, password and username.
   *
   * [SignInScreen] and [SignUpScreen] share one instance of this ViewModel in `JDRNexusApp`, so
   * each of them calls this before switching to the other; otherwise an error from one screen would
   * show on the other. A running request ([AuthStatus.Loading]) or a finished one
   * ([AuthStatus.Success]) is left untouched.
   */
  fun clearErrors() {
    _uiState.update {
      it.copy(
          emailError = null,
          passwordError = null,
          usernameError = null,
          status = if (it.status is AuthStatus.Error) AuthStatus.Idle else it.status,
      )
    }
  }

  fun signUp() {
    if (isAuthenticationLocked()) return
    val form = _uiState.value
    validate(form, isSignUp = true)?.let {
      setStatus(AuthStatus.Error(it))
      return
    }

    authenticate {
      repository.signUpWithEmail(
          email = form.email.trim(),
          password = form.password,
          username = form.username.trim(),
      )
    }
  }

  fun signIn() {
    if (isAuthenticationLocked()) return
    val form = _uiState.value
    validate(form, isSignUp = false)?.let {
      setStatus(AuthStatus.Error(it))
      return
    }

    authenticate { repository.signInWithEmail(form.email.trim(), form.password) }
  }

  fun signOut() {
    if (_uiState.value.status is AuthStatus.Loading) return
    setStatus(AuthStatus.Loading)

    viewModelScope.launch {
      val status =
          try {
            when (val result = repository.signOut()) {
              is AuthResult.Success -> {
                // Nothing typed by the signed-out user stays in the shared sign-in form.
                _uiState.value = AuthUiState()
                AuthStatus.Idle
              }
              is AuthResult.Failure -> AuthStatus.Error(result.error.message)
              is AuthResult.AccountCreatedNeedsRecovery -> AuthStatus.Error(result.message)
            }
          } catch (cancellation: CancellationException) {
            setStatus(AuthStatus.Idle)
            throw cancellation
          } catch (_: Exception) {
            AuthStatus.Error(AuthError.UNKNOWN.message)
          }

      setStatus(status)
    }
  }

  fun googleSignIn(
      context: Context,
      credentialManager: CredentialManager,
  ) {
    if (isAuthenticationLocked()) return
    setStatus(AuthStatus.Loading)

    viewModelScope.launch {
      try {
        val googleOption =
            GetSignInWithGoogleOption.Builder(
                    serverClientId = context.getString(R.string.default_web_client_id)
                )
                .build()

        val request = GetCredentialRequest.Builder().addCredentialOption(googleOption).build()

        val credential =
            credentialManager
                .getCredential(
                    context = context,
                    request = request,
                )
                .credential

        if (
            credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
          val googleCredential = GoogleIdTokenCredential.createFrom(credential.data)
          if (googleCredential.idToken.isBlank()) {
            setStatus(AuthStatus.Error(AuthError.INVALID_GOOGLE_CREDENTIAL.message))
          } else {
            setStatus(authenticateRequest { repository.signInWithGoogle(googleCredential.idToken) })
          }
        } else {
          setStatus(AuthStatus.Error("Unsupported Google credential. Please try again."))
        }
      } catch (e: GetCredentialCancellationException) {
        // Closing the Google picker is not an authentication failure.
        setStatus(AuthStatus.Idle)
        Log.d("AuthViewModel", "Google sign-in cancelled", e)
      } catch (e: NoCredentialException) {
        setStatus(AuthStatus.Error("No available Google account was found."))
        Log.e("AuthViewModel", "No Google credential available", e)
      } catch (e: GetCredentialException) {
        setStatus(AuthStatus.Error("Unable to start Google sign-in. Please try again."))
        Log.e("AuthViewModel", "Credential Manager error", e)
      } catch (e: CancellationException) {
        setStatus(AuthStatus.Idle)
        throw e
      } catch (e: Exception) {
        setStatus(AuthStatus.Error("Google sign-in failed. Please try again."))
        Log.e("AuthViewModel", "Unexpected Google sign-in error", e)
      }
    }
  }

  fun signInWithGoogle(token: String) {
    if (isAuthenticationLocked()) return
    if (token.isBlank()) {
      setStatus(AuthStatus.Error(AuthError.INVALID_GOOGLE_CREDENTIAL.message))
      return
    }

    authenticate { repository.signInWithGoogle(token) }
  }

  private fun authenticate(request: suspend () -> AuthResult<AuthUser>) {
    if (isAuthenticationLocked()) return
    setStatus(AuthStatus.Loading)

    viewModelScope.launch {
      try {
        setStatus(authenticateRequest(request))
      } catch (cancellation: CancellationException) {
        setStatus(AuthStatus.Idle)
        throw cancellation
      }
    }
  }

  private suspend fun authenticateRequest(request: suspend () -> AuthResult<AuthUser>): AuthStatus =
      try {
        when (val result = request()) {
          is AuthResult.Success -> AuthStatus.Success(result.value)
          is AuthResult.Failure -> AuthStatus.Error(result.error.message)
          is AuthResult.AccountCreatedNeedsRecovery -> AuthStatus.Error(result.message)
        }
      } catch (cancellation: CancellationException) {
        throw cancellation
      } catch (_: Exception) {
        AuthStatus.Error(AuthError.UNKNOWN.message)
      }

  /**
   * Whether a request is running ([AuthStatus.Loading]) or has already signed the user in
   * ([AuthStatus.Success]). Reads the current state, not the last value a screen composed.
   */
  fun isAuthenticationLocked(): Boolean =
      _uiState.value.status.let { it is AuthStatus.Loading || it is AuthStatus.Success }

  private fun validate(form: AuthUiState, isSignUp: Boolean): String? {
    val emailError = if (!isValidEmail(form.email)) AuthError.INVALID_EMAIL.message else null
    // The length rule is a sign-up policy. On sign-in, any non-empty password goes to the
    // repository, which reports a wrong one as AuthError.INVALID_CREDENTIALS.
    val passwordError =
        when {
          isSignUp && form.password.length < MIN_PASSWORD_LENGTH ->
              "Password must be at least $MIN_PASSWORD_LENGTH characters."
          !isSignUp && form.password.isEmpty() -> AuthError.EMPTY_PASSWORD.message
          else -> null
        }
    val usernameError =
        if (isSignUp && form.username.isBlank()) AuthError.INVALID_USERNAME.message else null

    _uiState.update {
      it.copy(
          emailError = emailError,
          passwordError = passwordError,
          usernameError = usernameError,
      )
    }
    return emailError ?: passwordError ?: usernameError
  }

  private fun updateInput(update: AuthUiState.() -> AuthUiState) {
    _uiState.update { state ->
      state.update().let { updated ->
        updated.copy(
            emailError = updated.emailError?.takeUnless { isValidEmail(updated.email) },
            // Each password error disappears once the rule that produced it is met: any
            // character for the sign-in "empty" error, the full length for the sign-up one.
            passwordError =
                updated.passwordError?.takeUnless { error ->
                  if (error == AuthError.EMPTY_PASSWORD.message) {
                    updated.password.isNotEmpty()
                  } else {
                    updated.password.length >= MIN_PASSWORD_LENGTH
                  }
                },
            usernameError = updated.usernameError?.takeUnless { updated.username.isNotBlank() },
            status =
                if (updated.status is AuthStatus.Loading || updated.status is AuthStatus.Success) {
                  updated.status
                } else {
                  AuthStatus.Idle
                },
        )
      }
    }
  }

  private fun isValidEmail(email: String): Boolean = EMAIL_PATTERN.matches(email.trim())

  private fun setStatus(status: AuthStatus) {
    _uiState.update { it.copy(status = status) }
  }

  companion object {
    val Factory: ViewModelProvider.Factory = viewModelFactory {
      initializer { AuthViewModel(AuthRepositoryFirebase()) }
    }

    const val MIN_PASSWORD_LENGTH = 6
    val EMAIL_PATTERN = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
  }
}
