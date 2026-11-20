// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.se.jdrnexus.model.profile.ProfileRepository
import com.github.se.jdrnexus.model.profile.ProfileRepositoryProvider
import com.github.se.jdrnexus.model.repository.AuthRepository
import com.github.se.jdrnexus.model.repository.AuthRepositoryProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ============ Validation rules ============

private const val USERNAME_MIN_LENGTH = 3
private const val USERNAME_MAX_LENGTH = 30
private const val BIO_MAX_LENGTH = 300

private fun usernameError(username: String): String? =
    when {
      username.isBlank() -> "Enter a username."
      username.trim().length < USERNAME_MIN_LENGTH ->
          "The username needs at least $USERNAME_MIN_LENGTH characters."
      username.trim().length > USERNAME_MAX_LENGTH ->
          "The username holds at most $USERNAME_MAX_LENGTH characters."
      else -> null
    }

private fun bioError(bio: String): String? =
    if (bio.trim().length > BIO_MAX_LENGTH) "The bio holds at most $BIO_MAX_LENGTH characters."
    else null

// ============ Definition of the ViewModel ============

/**
 * Loads and edits the profile of the signed-in user through [profileRepository]. The signed-in user
 * is read from [authRepository] at the start of every operation.
 */
class ProfileViewModel(
    private val profileRepository: ProfileRepository = ProfileRepositoryProvider.repository,
    private val authRepository: AuthRepository = AuthRepositoryProvider.repository,
) : ViewModel(), ProfileViewModelInterface {

  private val _uiState = MutableStateFlow(ProfileUiState())
  override val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

  /** The running load, cancelled by a reload so an old response never overwrites a newer one. */
  private var loadJob: Job? = null

  init {
    loadProfile()
  }

  // ============ Internal helpers ============

  /** UID of the signed-in user, read from the auth repository. */
  private suspend fun requireUid(): String =
      authRepository.authState.first()?.uid ?: throw IllegalStateException("You must be signed in.")

  /**
   * Clears [ProfileUiState.errorMessage]. Called when a new repository operation starts, so the
   * error shown is always the one of the latest operation, and the same error raised twice is
   * emitted again (null in between).
   */
  private fun clearError() {
    _uiState.update { it.copy(errorMessage = null) }
  }

  /** Recomputes [ProfileUiState.canSave] from the drafts, the saved profile and the save status. */
  private fun ProfileUiState.withCanSave(): ProfileUiState {
    val username = usernameDraft.trim()
    val changed = username != profile.username || bioDraft.trim() != profile.bio
    val valid = usernameError(usernameDraft) == null && bioError(bioDraft) == null
    return copy(canSave = isEditing && changed && valid && !isSaving)
  }

  /** Message of [e], or [fallback] when it has none. */
  private fun messageOf(e: Throwable, fallback: String): String =
      e.message?.takeIf(String::isNotBlank) ?: fallback

  // ============ Loading ============

  override fun loadProfile() {
    loadJob?.cancel()
    clearError()
    _uiState.update { it.copy(isLoading = true) }
    loadJob = viewModelScope.launch {
      try {
        val uid = requireUid()
        val profile = profileRepository.getProfile(uid).getOrThrow()
        val stats = profileRepository.getProfileStats(uid).getOrThrow()
        _uiState.update { it.copy(profile = profile, stats = stats, isLoading = false) }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _uiState.update {
          it.copy(isLoading = false, errorMessage = messageOf(e, "Unable to load the profile."))
        }
      }
    }
  }

  // ============ Edit form ============

  override fun startEditing() {
    _uiState.update {
      it.copy(
              isEditing = true,
              usernameDraft = it.profile.username,
              bioDraft = it.profile.bio,
              usernameError = null,
              bioError = null,
          )
          .withCanSave()
    }
  }

  override fun onUsernameChange(username: String) {
    _uiState.update {
      it.copy(usernameDraft = username, usernameError = usernameError(username)).withCanSave()
    }
  }

  override fun onBioChange(bio: String) {
    _uiState.update { it.copy(bioDraft = bio, bioError = bioError(bio)).withCanSave() }
  }

  override fun cancelEditing() {
    _uiState.update {
      it.copy(
          isEditing = false,
          usernameDraft = "",
          bioDraft = "",
          usernameError = null,
          bioError = null,
          canSave = false,
      )
    }
  }

  // ============ Saving ============

  override fun saveProfile() {
    val state = _uiState.value
    if (!state.canSave) return
    val username = state.usernameDraft.trim()
    val bio = state.bioDraft.trim()
    clearError()
    _uiState.update { it.copy(isSaving = true).withCanSave() }

    viewModelScope.launch {
      try {
        profileRepository.updateProfile(requireUid(), username, bio).getOrThrow()
        _uiState.update {
          it.copy(
              profile = it.profile.copy(username = username, bio = bio),
              isEditing = false,
              usernameDraft = "",
              bioDraft = "",
              isSaving = false,
              canSave = false,
          )
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _uiState.update {
          it.copy(isSaving = false, errorMessage = messageOf(e, "Unable to save the profile."))
              .withCanSave()
        }
      }
    }
  }

  override fun updateProfilePicture(image: Uri) {
    if (_uiState.value.isSaving) return
    clearError()
    _uiState.update { it.copy(isSaving = true).withCanSave() }

    viewModelScope.launch {
      try {
        val url = profileRepository.uploadProfilePicture(requireUid(), image).getOrThrow()
        _uiState.update {
          it.copy(profile = it.profile.copy(pictureUrl = url), isSaving = false).withCanSave()
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _uiState.update {
          it.copy(
                  isSaving = false,
                  errorMessage = messageOf(e, "Unable to update the profile picture."),
              )
              .withCanSave()
        }
      }
    }
  }
}
