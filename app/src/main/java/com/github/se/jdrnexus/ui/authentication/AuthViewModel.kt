// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuthUiState(
    val email: String = "",
    val password: String = "",
    val username: String = "",
    val status: AuthStatus = AuthStatus.Idle,
)

sealed interface AuthStatus {
  data object Idle : AuthStatus

  data object Loading : AuthStatus

  data class Success(val user: AuthUser) : AuthStatus

  data class Error(val message: String) : AuthStatus
}

class AuthViewModel(private val repository: AuthRepository) : ViewModel() {

  private val _uiState = MutableStateFlow(AuthUiState())
  val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

  fun onEmailChange(email: String) = updateInput { copy(email = email) }

  fun onPasswordChange(password: String) = updateInput { copy(password = password) }

  fun onUsernameChange(username: String) = updateInput { copy(username = username) }

  fun signUp() {
    val form = _uiState.value
    validate(form, requireUsername = true)?.let {
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
    val form = _uiState.value
    validate(form, requireUsername = false)?.let {
      setStatus(AuthStatus.Error(it))
      return
    }

    authenticate { repository.signInWithEmail(form.email.trim(), form.password) }
  }

  fun signOut() {
    viewModelScope.launch {
      setStatus(AuthStatus.Loading)

      val status =
          try {
            when (val result = repository.signOut()) {
              is AuthResult.Success -> AuthStatus.Idle
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
  fun googleSignIn(
      context: Context,
      credentialManager: CredentialManager,
  ) {
    if (_uiState.value.status is AuthStatus.Loading) return

    viewModelScope.launch {
      setStatus(AuthStatus.Loading)

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

          // Reuse the existing method that authenticates through the repository.
          signInWithGoogle(googleCredential.idToken)
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
    if (token.isBlank()) {
      setStatus(AuthStatus.Error(AuthError.INVALID_GOOGLE_CREDENTIAL.message))
      return
    }

    authenticate { repository.signInWithGoogle(token) }
  }

  private fun authenticate(request: suspend () -> AuthResult<AuthUser>) {
    viewModelScope.launch {
      setStatus(AuthStatus.Loading)

      val status =
          try {
            when (val result = request()) {
              is AuthResult.Success -> AuthStatus.Success(result.value)
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

  private fun validate(form: AuthUiState, requireUsername: Boolean): String? {
    if (!EMAIL_PATTERN.matches(form.email.trim())) return AuthError.INVALID_EMAIL.message
    if (form.password.length < MIN_PASSWORD_LENGTH) {
      return "Password must be at least $MIN_PASSWORD_LENGTH characters."
    }
    if (requireUsername && form.username.isBlank()) return AuthError.INVALID_USERNAME.message
    return null
  }

  private fun updateInput(update: AuthUiState.() -> AuthUiState) {
    _uiState.update { state ->
      state.update().let { updated ->
        if (updated.status is AuthStatus.Loading) updated
        else updated.copy(status = AuthStatus.Idle)
      }
    }
  }

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
