// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.viewmodel

import android.net.Uri
import com.github.se.jdrnexus.model.profile.ProfileRepository
import com.github.se.jdrnexus.model.profile.ProfileStats
import com.github.se.jdrnexus.model.profile.UserProfile
import com.github.se.jdrnexus.model.repository.AuthRepository
import com.github.se.jdrnexus.model.repository.AuthResult
import com.github.se.jdrnexus.model.repository.AuthUser
import com.github.se.jdrnexus.utils.MainDispatcherRule
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {
  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  // ============ Shared test data ============
  private companion object {
    const val USER_ID = "user-42"
    const val PICTURE_URL = "https://example.com/aria.png"
    val SAVED_PROFILE = UserProfile(USER_ID, "Aria", "Bard of the Sunken Crown", "")
    val STATS = ProfileStats(campaignsCreated = 1, groupsJoined = 3, filesCreated = 7)
  }

  // ============ Fakes ============

  /** Returns the configured results; a non-null gate holds the operation until completed. */
  private class FakeProfileRepository : ProfileRepository {
    var profileResult: Result<UserProfile> = Result.success(SAVED_PROFILE)
    var statsResult: Result<ProfileStats> = Result.success(STATS)
    var updateResult: Result<Unit> = Result.success(Unit)
    var uploadResult: Result<String> = Result.success(PICTURE_URL)
    var gate: CompletableDeferred<Unit>? = null

    val profileRequests = mutableListOf<String>()
    val updates = mutableListOf<Triple<String, String, String>>()
    val uploads = mutableListOf<Pair<String, Uri>>()

    override suspend fun getProfile(userId: String): Result<UserProfile> {
      profileRequests.add(userId)
      gate?.await()
      return profileResult
    }

    override suspend fun updateProfile(
        userId: String,
        username: String,
        bio: String,
    ): Result<Unit> {
      updates.add(Triple(userId, username, bio))
      gate?.await()
      return updateResult
    }

    override suspend fun uploadProfilePicture(userId: String, image: Uri): Result<String> {
      uploads.add(userId to image)
      gate?.await()
      return uploadResult
    }

    override suspend fun getProfileStats(userId: String): Result<ProfileStats> = statsResult
  }

  /** Signed in as the given user; tests switch user through [authState]. */
  private class FakeAuthRepository(user: AuthUser?) : AuthRepository {
    override val authState = MutableStateFlow(user)

    override suspend fun signUpWithEmail(email: String, password: String, username: String) =
        error("unused")

    override suspend fun signInWithEmail(email: String, password: String) = error("unused")

    override suspend fun signInWithGoogle(idToken: String) = error("unused")

    override suspend fun signOut(): AuthResult<Unit> = error("unused")
  }

  private val repository = FakeProfileRepository()

  private fun viewModel(
      user: AuthUser? = AuthUser(USER_ID, null, null),
      auth: FakeAuthRepository = FakeAuthRepository(user),
  ) = ProfileViewModel(repository, auth).apply { loadProfile() }

  // ============ Loading ============

  @Test
  fun loadsProfileAndStatsOfSignedInUser() = runTest {
    repository.gate = CompletableDeferred()
    val vm = viewModel()
    runCurrent()
    assertTrue(vm.uiState.value.isLoading)

    repository.gate!!.complete(Unit)
    runCurrent()

    assertEquals(listOf(USER_ID), repository.profileRequests)
    assertEquals(SAVED_PROFILE, vm.uiState.value.profile)
    assertEquals(STATS, vm.uiState.value.stats)
    assertFalse(vm.uiState.value.isLoading)
    assertNull(vm.uiState.value.errorMessage)
  }

  @Test
  fun exposesErrorWhenProfileLoadFails() = runTest {
    repository.profileResult = Result.failure(Exception("Network down"))

    val vm = viewModel()
    runCurrent()

    assertEquals("Network down", vm.uiState.value.errorMessage)
    assertFalse(vm.uiState.value.isLoading)
  }

  @Test
  fun exposesErrorWhenStatsLoadFails() = runTest {
    repository.statsResult = Result.failure(Exception("Permission denied"))
    repository.gate = CompletableDeferred()

    val vm = viewModel()
    runCurrent()
    assertTrue(vm.uiState.value.isLoading)

    repository.gate!!.complete(Unit)
    runCurrent()

    assertEquals("Permission denied", vm.uiState.value.errorMessage)
    assertFalse(vm.uiState.value.isLoading)
  }

  @Test
  fun loadIsSkippedWhenProfileAlreadyLoaded() = runTest {
    val vm = viewModel() // first load, completes on runCurrent()
    runCurrent()

    vm.loadProfile()
    runCurrent()

    assertEquals(listOf(USER_ID), repository.profileRequests)
    assertFalse(vm.uiState.value.isLoading)
    assertEquals(SAVED_PROFILE, vm.uiState.value.profile)
  }

  @Test
  fun loadIsNotRestartedWhileOneRuns() = runTest {
    repository.gate = CompletableDeferred()
    val vm = viewModel() // starts the first load, held at the gate
    runCurrent()
    assertTrue(vm.uiState.value.isLoading)
    assertEquals(listOf(USER_ID), repository.profileRequests)

    vm.loadProfile()
    runCurrent()
    repository.gate!!.complete(Unit)
    runCurrent()

    assertEquals(listOf(USER_ID), repository.profileRequests)
    assertEquals(SAVED_PROFILE, vm.uiState.value.profile)
    assertFalse(vm.uiState.value.isLoading)
  }

  @Test
  fun exposesErrorWithoutRepositoryCallWhenSignedOut() = runTest {
    val vm = viewModel(user = null)
    runCurrent()

    assertEquals("You must be signed in.", vm.uiState.value.errorMessage)
    assertEquals(emptyList<String>(), repository.profileRequests)
  }

  @Test
  fun reloadClearsPreviousError() = runTest {
    repository.profileResult = Result.failure(Exception("Network down"))
    val vm = viewModel()
    runCurrent()
    repository.profileResult = Result.success(SAVED_PROFILE)

    vm.loadProfile()
    runCurrent()

    assertNull(vm.uiState.value.errorMessage)
    assertEquals(SAVED_PROFILE, vm.uiState.value.profile)
  }

  // ============ Edit form ============

  @Test
  fun startEditingFillsDraftsWithSavedValues() = runTest {
    val vm = viewModel()
    runCurrent()

    vm.startEditing()

    val state = vm.uiState.value
    assertTrue(state.isEditing)
    assertEquals("Aria", state.usernameDraft)
    assertEquals("Bard of the Sunken Crown", state.bioDraft)
    assertFalse(state.canSave)
  }

  @Test
  fun startEditingIsIgnoredUntilProfileLoaded() = runTest {
    repository.profileResult = Result.failure(Exception("Network down"))
    val vm = viewModel()
    runCurrent()

    vm.startEditing()

    assertFalse(vm.uiState.value.isEditing)
    assertFalse(vm.uiState.value.canSave)
    assertEquals("Network down", vm.uiState.value.errorMessage)
  }

  @Test
  fun usernameShorterThanThreeTrimmedCharactersIsRejected() = runTest {
    val vm = viewModel()
    runCurrent()
    vm.startEditing()

    vm.onUsernameChange("  Al  ")

    assertEquals("The username needs at least 3 characters.", vm.uiState.value.usernameError)
    assertFalse(vm.uiState.value.canSave)
  }

  @Test
  fun usernameLongerThanThirtyCharactersIsRejected() = runTest {
    val vm = viewModel()
    runCurrent()
    vm.startEditing()

    vm.onUsernameChange("a".repeat(31))

    assertEquals("The username holds at most 30 characters.", vm.uiState.value.usernameError)
    assertFalse(vm.uiState.value.canSave)
  }

  @Test
  fun blankUsernameIsRejected() = runTest {
    val vm = viewModel()
    runCurrent()
    vm.startEditing()

    vm.onUsernameChange("   ")

    assertEquals("Enter a username.", vm.uiState.value.usernameError)
    assertFalse(vm.uiState.value.canSave)
  }

  @Test
  fun bioLongerThanThreeHundredCharactersIsRejected() = runTest {
    val vm = viewModel()
    runCurrent()
    vm.startEditing()

    vm.onBioChange("b".repeat(301))

    assertEquals("The bio holds at most 300 characters.", vm.uiState.value.bioError)
    assertFalse(vm.uiState.value.canSave)
  }

  @Test
  fun validChangedDraftCanBeSaved() = runTest {
    val vm = viewModel()
    runCurrent()
    vm.startEditing()

    vm.onUsernameChange("Aria the Bold")

    assertNull(vm.uiState.value.usernameError)
    assertTrue(vm.uiState.value.canSave)
  }

  @Test
  fun cancelEditingDropsDraftsAndKeepsSavedProfile() = runTest {
    val vm = viewModel()
    runCurrent()
    vm.startEditing()
    vm.onUsernameChange("Aria the Bold")

    vm.cancelEditing()

    val state = vm.uiState.value
    assertFalse(state.isEditing)
    assertEquals("", state.usernameDraft)
    assertEquals(SAVED_PROFILE, state.profile)
  }

  // ============ Saving ============

  @Test
  fun saveSendsTrimmedDraftsAndUpdatesProfileOnSuccess() = runTest {
    val vm = viewModel()
    runCurrent()
    vm.startEditing()
    vm.onUsernameChange("  Aria the Bold ")
    vm.onBioChange(" Retired bard  ")
    repository.gate = CompletableDeferred()

    vm.saveProfile()
    runCurrent()
    assertTrue(vm.uiState.value.isSaving)
    assertFalse(vm.uiState.value.canSave)

    repository.gate!!.complete(Unit)
    runCurrent()

    assertEquals(listOf(Triple(USER_ID, "Aria the Bold", "Retired bard")), repository.updates)
    val state = vm.uiState.value
    assertEquals(
        SAVED_PROFILE.copy(username = "Aria the Bold", bio = "Retired bard"),
        state.profile,
    )
    assertFalse(state.isEditing)
    assertFalse(state.isSaving)
  }

  @Test
  fun saveDoesNothingWhenDraftsAreUnchanged() = runTest {
    val vm = viewModel()
    runCurrent()
    vm.startEditing()

    vm.saveProfile()
    runCurrent()

    assertEquals(emptyList<Triple<String, String, String>>(), repository.updates)
  }

  @Test
  fun failedSaveKeepsFormOpenAndExposesError() = runTest {
    repository.updateResult = Result.failure(Exception("Permission denied"))
    val vm = viewModel()
    runCurrent()
    vm.startEditing()
    vm.onUsernameChange("Aria the Bold")

    vm.saveProfile()
    runCurrent()

    val state = vm.uiState.value
    assertEquals("Permission denied", state.errorMessage)
    assertTrue(state.isEditing)
    assertEquals("Aria the Bold", state.usernameDraft)
    assertEquals(SAVED_PROFILE, state.profile)
    assertTrue(state.canSave)
  }

  @Test
  fun retriedSaveClearsErrorAndSavesOnSuccess() = runTest {
    repository.updateResult = Result.failure(Exception("Permission denied"))
    val vm = viewModel()
    runCurrent()
    vm.startEditing()
    vm.onUsernameChange("Aria the Bold")
    vm.saveProfile()
    runCurrent()
    repository.updateResult = Result.success(Unit)
    repository.gate = CompletableDeferred()

    vm.saveProfile()
    assertNull(vm.uiState.value.errorMessage)
    assertTrue(vm.uiState.value.isSaving)

    repository.gate!!.complete(Unit)
    runCurrent()

    val attempt = Triple(USER_ID, "Aria the Bold", SAVED_PROFILE.bio)
    assertEquals(listOf(attempt, attempt), repository.updates)
    val state = vm.uiState.value
    assertEquals(SAVED_PROFILE.copy(username = "Aria the Bold"), state.profile)
    assertFalse(state.isEditing)
    assertFalse(state.isSaving)
    assertNull(state.errorMessage)
  }

  @Test
  fun saveIsRefusedWhenSignedInUserChanged() = runTest {
    val auth = FakeAuthRepository(AuthUser(USER_ID, null, null))
    val vm = viewModel(auth = auth)
    runCurrent()
    vm.startEditing()
    vm.onUsernameChange("Aria the Bold")
    auth.authState.value = AuthUser("other-user", null, null)

    vm.saveProfile()
    runCurrent()

    assertEquals(emptyList<Triple<String, String, String>>(), repository.updates)
    val state = vm.uiState.value
    assertEquals("The signed-in account changed. Reload the profile.", state.errorMessage)
    assertEquals(UserProfile(), state.profile)
    assertFalse(state.isEditing)
    assertFalse(state.isSaving)

    // Retry now loads the new account's profile instead of staying stuck on the old one.
    val otherProfile = UserProfile("other-user", "Brann", "", "")
    repository.profileResult = Result.success(otherProfile)
    vm.loadProfile()
    runCurrent()

    assertEquals(listOf(USER_ID, "other-user"), repository.profileRequests)
    assertEquals(otherProfile, vm.uiState.value.profile)
    assertNull(vm.uiState.value.errorMessage)
  }

  // ============ Profile picture ============

  @Test
  fun pictureUploadShowsNewPictureOnSuccess() = runTest {
    val vm = viewModel()
    runCurrent()
    val image = mockk<Uri>()
    repository.gate = CompletableDeferred()

    vm.updateProfilePicture(image)
    runCurrent()
    assertTrue(vm.uiState.value.isSaving)

    repository.gate!!.complete(Unit)
    runCurrent()

    assertEquals(listOf(USER_ID to image), repository.uploads)
    assertEquals(PICTURE_URL, vm.uiState.value.profile.pictureUrl)
    assertFalse(vm.uiState.value.isSaving)
  }

  @Test
  fun pictureUploadIsIgnoredWhileAnotherUploadRuns() = runTest {
    val vm = viewModel()
    runCurrent()
    val first = mockk<Uri>()
    val second = mockk<Uri>()
    repository.gate = CompletableDeferred()
    vm.updateProfilePicture(first)
    runCurrent()

    vm.updateProfilePicture(second)
    runCurrent()
    repository.gate!!.complete(Unit)
    runCurrent()

    assertEquals(listOf(USER_ID to first), repository.uploads)
    assertEquals(PICTURE_URL, vm.uiState.value.profile.pictureUrl)
    assertFalse(vm.uiState.value.isSaving)
  }

  @Test
  fun pictureUploadIsIgnoredUntilProfileLoaded() = runTest {
    repository.profileResult = Result.failure(Exception("Network down"))
    val vm = viewModel()
    runCurrent()

    vm.updateProfilePicture(mockk())
    runCurrent()

    assertEquals(emptyList<Pair<String, Uri>>(), repository.uploads)
    assertFalse(vm.uiState.value.isSaving)
    assertEquals("Network down", vm.uiState.value.errorMessage)
  }

  @Test
  fun pictureUploadIsRefusedWhenSignedInUserChanged() = runTest {
    val auth = FakeAuthRepository(AuthUser(USER_ID, null, null))
    val vm = viewModel(auth = auth)
    runCurrent()
    auth.authState.value = AuthUser("other-user", null, null)

    vm.updateProfilePicture(mockk())
    runCurrent()

    assertEquals(emptyList<Pair<String, Uri>>(), repository.uploads)
    assertEquals(
        "The signed-in account changed. Reload the profile.",
        vm.uiState.value.errorMessage,
    )
    assertEquals(UserProfile(), vm.uiState.value.profile)
    assertFalse(vm.uiState.value.isSaving)
  }

  @Test
  fun failedPictureUploadKeepsOldPictureAndExposesError() = runTest {
    val oldPicture = "https://example.com/old.png"
    repository.profileResult = Result.success(SAVED_PROFILE.copy(pictureUrl = oldPicture))
    repository.uploadResult = Result.failure(Exception("Upload failed"))
    val vm = viewModel()
    runCurrent()

    vm.updateProfilePicture(mockk())
    runCurrent()

    assertEquals("Upload failed", vm.uiState.value.errorMessage)
    assertEquals(oldPicture, vm.uiState.value.profile.pictureUrl)
    assertFalse(vm.uiState.value.isSaving)
  }
}
