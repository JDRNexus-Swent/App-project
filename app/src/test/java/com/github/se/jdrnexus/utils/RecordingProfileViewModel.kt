// Co-authored-by: Codex <codex@openai.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.utils

import android.net.Uri
import com.github.se.jdrnexus.viewmodel.ProfileUiState
import com.github.se.jdrnexus.viewmodel.ProfileViewModelInterface
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Fake [ProfileViewModelInterface] for screen and navigation tests. It counts the calls made by
 * `ProfileScreen`, records saved drafts and picked images, and updates [uiState] only for the edit
 * form (start, drafts, cancel), so each test sets the rest of the state itself.
 */
internal class RecordingProfileViewModel(initialState: ProfileUiState = ProfileUiState()) :
    ProfileViewModelInterface {
  override val uiState = MutableStateFlow(initialState)
  var loadCalls = 0
    private set

  var startEditingCalls = 0
    private set

  var cancelEditingCalls = 0
    private set

  val savedDrafts = mutableListOf<Pair<String, String>>()
  val pickedImages = mutableListOf<Uri>()

  override fun loadProfile() {
    loadCalls++
  }

  override fun startEditing() {
    startEditingCalls++
    val state = uiState.value
    uiState.value =
        state.copy(
            isEditing = true,
            usernameDraft = state.profile.username,
            bioDraft = state.profile.bio,
        )
  }

  override fun onUsernameChange(username: String) {
    uiState.value = uiState.value.copy(usernameDraft = username, canSave = true)
  }

  override fun onBioChange(bio: String) {
    uiState.value = uiState.value.copy(bioDraft = bio, canSave = true)
  }

  override fun cancelEditing() {
    cancelEditingCalls++
    uiState.value = uiState.value.copy(isEditing = false)
  }

  override fun saveProfile() {
    val state = uiState.value
    savedDrafts.add(state.usernameDraft to state.bioDraft)
  }

  override fun updateProfilePicture(image: Uri) {
    pickedImages.add(image)
  }
}
