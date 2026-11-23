// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.viewmodel

import android.net.Uri
import com.github.se.jdrnexus.model.profile.ProfileStats
import com.github.se.jdrnexus.model.profile.UserProfile
import kotlinx.coroutines.flow.StateFlow

/** Immutable state rendered by the profile screen. */
data class ProfileUiState(
    /** Saved profile, shown outside edit mode. Empty fields until it is loaded. */
    val profile: UserProfile = UserProfile(),
    /** Activity counters of the signed-in user. */
    val stats: ProfileStats = ProfileStats(),
    /** True while the edit form is shown. */
    val isEditing: Boolean = false,
    /** Username typed in the edit form. */
    val usernameDraft: String = "",
    /** Bio typed in the edit form. */
    val bioDraft: String = "",
    /**
     * Error to show under the username field, or null. The username is required and holds 3 to 30
     * characters once trimmed. Stays null until the field is edited.
     */
    val usernameError: String? = null,
    /** Error to show under the bio field, or null. The bio holds at most 300 characters. */
    val bioError: String? = null,
    /** True when the drafts are valid, differ from the saved profile and no save is running. */
    val canSave: Boolean = false,
    /** True while the profile and stats are being loaded. */
    val isLoading: Boolean = true,
    /** True while a save or a picture upload is running. */
    val isSaving: Boolean = false,
    /** Error from the latest repository operation, or null. Cleared when a new one starts. */
    val errorMessage: String? = null,
)

/**
 * Public contract of the profile screen's ViewModel, so the screen can be tested with a fake.
 *
 * Implementations act on the signed-in user only and go through a `ProfileRepository`. They never
 * access Firebase directly. The edit form's drafts and validation live here, so the screen only
 * displays the state and forwards user events.
 */
interface ProfileViewModelInterface {
  /** Current profile, stats, edit form and operation status. */
  val uiState: StateFlow<ProfileUiState>

  /**
   * Loads the profile and stats of the signed-in user. Ignored while a load runs or once the
   * profile is loaded.
   */
  fun loadProfile()

  /** Opens the edit form, with the drafts set to the saved values. Ignored until it is loaded. */
  fun startEditing()

  /** Updates the username draft and its validation error. */
  fun onUsernameChange(username: String)

  /** Updates the bio draft and its validation error. */
  fun onBioChange(bio: String)

  /** Closes the edit form and drops the drafts. */
  fun cancelEditing()

  /** Validates and saves the drafts, then closes the edit form once the save succeeds. */
  fun saveProfile()

  /**
   * Uploads the picked [image] as the new profile picture and shows it once the upload succeeds.
   * Ignored until the profile is loaded or while another save or upload runs.
   */
  fun updateProfilePicture(image: Uri)
}
