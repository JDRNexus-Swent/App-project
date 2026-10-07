// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.github.se.jdrnexus.ui.authentification

import androidx.lifecycle.ViewModel
import com.github.se.jdrnexus.model.repository.AuthError
import com.github.se.jdrnexus.model.repository.AuthRepository
import com.github.se.jdrnexus.model.repository.AuthResult
import com.github.se.jdrnexus.model.repository.AuthUser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

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

  suspend fun signUp() {
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

  suspend fun signIn() {
    val form = _uiState.value
    validate(form, requireUsername = false)?.let {
      setStatus(AuthStatus.Error(it))
      return
    }

    authenticate { repository.signInWithEmail(form.email.trim(), form.password) }
  }

  suspend fun signInWithGoogle(token: String) {
    if (token.isBlank()) {
      setStatus(AuthStatus.Error(AuthError.INVALID_GOOGLE_CREDENTIAL.message))
      return
    }

    authenticate { repository.signInWithGoogle(token) }
  }

  private suspend fun authenticate(request: suspend () -> AuthResult<AuthUser>) {
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

  private companion object {
    const val MIN_PASSWORD_LENGTH = 6
    val EMAIL_PATTERN = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
  }
}
