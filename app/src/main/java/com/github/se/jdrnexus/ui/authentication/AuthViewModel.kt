// Co-authored-by: OpenAI Codex <noreply@openai.com>
// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.github.se.jdrnexus.ui.authentication

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.github.se.jdrnexus.model.repository.AuthError
import com.github.se.jdrnexus.model.repository.AuthRepository
import com.github.se.jdrnexus.model.repository.AuthRepositoryFirebase
import com.github.se.jdrnexus.model.repository.AuthResult
import com.github.se.jdrnexus.model.repository.AuthUser
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

class AuthViewModel(private val repository: AuthRepository) : ViewModel() {

  private val _uiState = MutableStateFlow(AuthUiState())
  val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

  fun onEmailChange(email: String) = updateInput { copy(email = email) }

  fun onPasswordChange(password: String) = updateInput { copy(password = password) }

  fun onUsernameChange(username: String) = updateInput { copy(username = username) }

  fun signUp() {
    if (isAuthenticationLocked()) return
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
    if (isAuthenticationLocked()) return
    val form = _uiState.value
    validate(form, requireUsername = false)?.let {
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

  private fun isAuthenticationLocked(): Boolean =
      _uiState.value.status.let { it is AuthStatus.Loading || it is AuthStatus.Success }

  private fun validate(form: AuthUiState, requireUsername: Boolean): String? {
    val emailError = if (!isValidEmail(form.email)) AuthError.INVALID_EMAIL.message else null
    val passwordError =
        if (form.password.length < MIN_PASSWORD_LENGTH) {
          "Password must be at least $MIN_PASSWORD_LENGTH characters."
        } else {
          null
        }
    val usernameError =
        if (requireUsername && form.username.isBlank()) AuthError.INVALID_USERNAME.message else null

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
            passwordError =
                updated.passwordError?.takeUnless {
                  updated.password.length >= MIN_PASSWORD_LENGTH
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
