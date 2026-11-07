// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.se.jdrnexus.model.map.MapGame
import com.github.se.jdrnexus.model.map.MapLocation
import com.github.se.jdrnexus.model.map.MapRepository
import com.github.se.jdrnexus.model.map.MapRepositoryProvider
import com.github.se.jdrnexus.model.repository.AuthRepository
import com.github.se.jdrnexus.model.repository.AuthResult
import com.github.se.jdrnexus.model.repository.AuthUser
import com.github.se.jdrnexus.resources.C
import com.github.se.jdrnexus.ui.authentication.AuthViewModel
import com.github.se.jdrnexus.ui.home.TestTags
import com.google.firebase.FirebaseApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityNavigationTest {
  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun startsAtSignInAndReturnsFromSignUp() {
    showApp()

    scrollToTag("sign_in_scroll_container", C.Tag.sign_in_sign_up)
    composeTestRule.onNodeWithTag(C.Tag.sign_in_sign_up).assertIsDisplayed().performClick()
    scrollToTag("sign_up_scroll_container", C.Tag.sign_up_sign_in)
    composeTestRule.onNodeWithTag(C.Tag.sign_up_sign_in).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.sign_up_sign_in).performClick()
    scrollToTag("sign_in_scroll_container", C.Tag.sign_in_sign_up)
    composeTestRule.onNodeWithTag(C.Tag.sign_in_sign_up).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.sign_up_sign_in).assertIsNotDisplayed()
  }

  @Test
  fun rapidSignUpTapsDoNotStackScreensOrLeaveNavigationBlank() {
    showApp()
    scrollToTag("sign_in_scroll_container", C.Tag.sign_in_sign_up)

    composeTestRule.onNodeWithTag(C.Tag.sign_in_sign_up).performTouchInput {
      repeat(10) { click() }
    }
    waitForTag(C.Tag.sign_up_sign_in)
    scrollToTag("sign_up_scroll_container", C.Tag.sign_up_sign_in)

    composeTestRule.onNodeWithTag(C.Tag.sign_up_sign_in).assertIsDisplayed().performClick()
    composeTestRule.onNodeWithTag(C.Tag.sign_in_sign_up).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.sign_up_sign_in).assertIsNotDisplayed()

    composeTestRule.onNodeWithTag(C.Tag.sign_in_sign_up).performTouchInput {
      repeat(10) { click() }
    }
    waitForTag(C.Tag.sign_up_sign_in)
    scrollToTag("sign_up_scroll_container", C.Tag.sign_up_sign_in)
    composeTestRule.onNodeWithTag(C.Tag.sign_up_sign_in).performTouchInput {
      repeat(10) { click() }
    }
    waitForTag(C.Tag.sign_in_sign_up)
    composeTestRule.onNodeWithTag(C.Tag.sign_in_sign_up).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.sign_up_sign_in).assertIsNotDisplayed()
  }

  @Test
  fun successfulSignInAndLogoutReturnToSignIn() {
    val repository = FakeAuthRepository()
    showApp(repository)

    assert(repository.authState.value == null)
    composeTestRule.onNodeWithTag(C.Tag.sign_in_email).performTextInput("hero@example.com")
    composeTestRule.onNodeWithTag(C.Tag.sign_in_password).performTextInput("secret1")
    composeTestRule.onNodeWithTag(C.Tag.sign_in_submit).performClick()

    waitForTag(TestTags.TITLE)
    composeTestRule.onNodeWithTag(TestTags.TITLE).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.sign_in_sign_up).assertIsNotDisplayed()
    assert(repository.signInCalls == 1)
    assert(repository.authState.value == FakeAuthRepository.TEST_USER)

    composeTestRule.onNodeWithTag(TestTags.LOGOUT).performClick()
    waitForTag(C.Tag.sign_in_sign_up)
    scrollToTag("sign_in_scroll_container", C.Tag.sign_in_sign_up)
    composeTestRule.onNodeWithTag(C.Tag.sign_in_sign_up).assertIsDisplayed()
    composeTestRule.onNodeWithTag(TestTags.TITLE).assertIsNotDisplayed()
    assert(repository.authState.value == null)
  }

  @Test
  fun successfulRegistrationNavigatesToHome() {
    val repository = FakeAuthRepository()
    showApp(repository)

    assert(repository.authState.value == null)
    scrollToTag("sign_in_scroll_container", C.Tag.sign_in_sign_up)
    composeTestRule.onNodeWithTag(C.Tag.sign_in_sign_up).performClick()
    composeTestRule.onNodeWithTag(C.Tag.sign_up_username).performTextInput("Ranger")
    composeTestRule.onNodeWithTag(C.Tag.sign_up_email).performTextInput("hero@example.com")
    composeTestRule.onNodeWithTag(C.Tag.sign_up_password).performTextInput("secret1")
    composeTestRule.onNodeWithTag(C.Tag.sign_up_submit).performClick()

    waitForTag(TestTags.TITLE)
    composeTestRule.onNodeWithTag(TestTags.TITLE).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.sign_up_sign_in).assertIsNotDisplayed()
    assert(repository.signUpCalls == 1)
    assert(repository.authState.value == FakeAuthRepository.TEST_USER)
  }

  @Test
  fun homeNavigationOpensDiceAndReturnsHome() {
    val repository = FakeAuthRepository()
    showApp(repository)

    composeTestRule.onNodeWithTag(C.Tag.sign_in_email).performTextInput("hero@example.com")
    composeTestRule.onNodeWithTag(C.Tag.sign_in_password).performTextInput("secret1")
    composeTestRule.onNodeWithTag(C.Tag.sign_in_submit).performClick()
    waitForTag(TestTags.TITLE)

    composeTestRule.onNodeWithTag(TestTags.DICEBUTTON).performClick()
    waitForTag(C.Tag.dice_offline_screen)
    composeTestRule.onNodeWithTag(C.Tag.dice_offline_screen).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.dice_offline_close).performClick()

    composeTestRule.onNodeWithTag(TestTags.TITLE).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.dice_offline_screen).assertIsNotDisplayed()
  }

  @Test
  fun mapTabOpensMapScreen() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    if (FirebaseApp.getApps(context).isEmpty()) checkNotNull(FirebaseApp.initializeApp(context))
    // No real Firestore: the Map screen loads its games from a fake repository.
    val defaultMapRepository = MapRepositoryProvider.mapRepository
    MapRepositoryProvider.mapRepository = NoGamesMapRepository()
    try {
      showApp()
      composeTestRule.onNodeWithTag(C.Tag.sign_in_email).performTextInput("hero@example.com")
      composeTestRule.onNodeWithTag(C.Tag.sign_in_password).performTextInput("secret1")
      composeTestRule.onNodeWithTag(C.Tag.sign_in_submit).performClick()
      waitForTag(TestTags.TITLE)

      composeTestRule.onNodeWithTag(C.Tag.bottomTab("map")).performClick()

      waitForTag(C.Tag.map_screen)
      composeTestRule.onNodeWithTag(C.Tag.map_screen).assertIsDisplayed()
    } finally {
      MapRepositoryProvider.mapRepository = defaultMapRepository
    }
  }

  private class NoGamesMapRepository : MapRepository {
    override suspend fun loadGamesNear(location: MapLocation?): List<MapGame> = emptyList()
  }

  private fun showApp(repository: FakeAuthRepository = FakeAuthRepository()) {
    val context = ApplicationProvider.getApplicationContext<Context>()
    if (FirebaseApp.getApps(context).isEmpty()) {
      checkNotNull(FirebaseApp.initializeApp(context))
    }
    val viewModel = AuthViewModel(repository)
    composeTestRule.setContent { JDRNexusApp(authViewModel = viewModel) }
  }

  private fun waitForTag(tag: String) {
    composeTestRule.waitUntil(5_000) {
      composeTestRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    }
  }

  private fun scrollToTag(containerTag: String, targetTag: String) {
    composeTestRule.onNodeWithTag(containerTag).performScrollToNode(hasTestTag(targetTag))
  }

  private class FakeAuthRepository : AuthRepository {
    private val mutableAuthState = MutableStateFlow<AuthUser?>(null)
    override val authState: StateFlow<AuthUser?> = mutableAuthState
    var signInCalls = 0
    var signUpCalls = 0

    override suspend fun signUpWithEmail(
        email: String,
        password: String,
        username: String,
    ): AuthResult<AuthUser> {
      signUpCalls++
      mutableAuthState.value = TEST_USER
      return AuthResult.Success(TEST_USER)
    }

    override suspend fun signInWithEmail(email: String, password: String): AuthResult<AuthUser> {
      signInCalls++
      mutableAuthState.value = TEST_USER
      return AuthResult.Success(TEST_USER)
    }

    override suspend fun signInWithGoogle(idToken: String): AuthResult<AuthUser> {
      mutableAuthState.value = TEST_USER
      return AuthResult.Success(TEST_USER)
    }

    override suspend fun signOut(): AuthResult<Unit> {
      mutableAuthState.value = null
      return AuthResult.Success(Unit)
    }

    companion object {
      val TEST_USER = AuthUser(uid = "user-1", email = "hero@example.com", username = "Ranger")
    }
  }
}
