// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.ui.home

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.se.jdrnexus.model.repository.AuthError
import com.github.se.jdrnexus.model.repository.AuthRepository
import com.github.se.jdrnexus.model.repository.AuthResult
import com.github.se.jdrnexus.model.repository.AuthUser
import com.github.se.jdrnexus.resources.C
import com.github.se.jdrnexus.ui.authentication.AuthStatus
import com.github.se.jdrnexus.ui.authentication.AuthViewModel
import com.github.se.jdrnexus.ui.theme.SampleAppTheme
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeScreenS1Test {
  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun rendersWithCustomTheme() {
    val background = Color(0xFF123456)
    composeTestRule.setContent {
      MaterialTheme(colorScheme = lightColorScheme(background = background)) {
        HomeScreen(createViewModel())
      }
    }

    composeTestRule.onNodeWithTag(TestTags.TITLE).assertIsDisplayed()
  }

  @Test
  fun displaysTitleProfileQuicktoolsAndSharedBottomBar() {
    composeTestRule.setContent { SampleAppTheme { HomeScreen(createViewModel()) } }

    composeTestRule.onNodeWithTag(TestTags.TITLE).assertIsDisplayed()
    composeTestRule.onNodeWithTag(TestTags.PROFILE_PIC).assertIsDisplayed()
    composeTestRule.onNodeWithTag(TestTags.QUICKTOOLS).assertIsDisplayed()
    composeTestRule.onNodeWithText("More features coming soon").assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(com.github.se.jdrnexus.resources.C.Tag.shared_bottom_bar)
        .assertIsDisplayed()
    composeTestRule.onNodeWithText("Local Adventures").assertIsNotDisplayed()
    composeTestRule.onNodeWithText("Adventure 1").assertIsNotDisplayed()
  }

  @Test
  fun quickToolButtonsAreSquareAndClickable() {
    var diceClicked = false
    var groupManagementClicked = false
    composeTestRule.setContent {
      SampleAppTheme {
        HomeScreen(
            viewModel = createViewModel(),
            onDiceClick = { diceClicked = true },
            onGroupManagementClick = { groupManagementClicked = true },
        )
      }
    }

    TestTags.BUTTONS.forEachIndexed { index, tag ->
      val button = composeTestRule.onNodeWithTag(tag)
      button.assertIsDisplayed()
      composeTestRule
          .onNodeWithTag(
              listOf(TestTags.DICE_ICON, TestTags.GROUPMANAGEMENT_ICON)[index],
              useUnmergedTree = true,
          )
          .assertIsDisplayed()
      composeTestRule.onNodeWithText("Dice").assertIsNotDisplayed()
      composeTestRule.onNodeWithText("Group Management").assertIsNotDisplayed()
      val bounds = button.fetchSemanticsNode().boundsInRoot
      Assert.assertEquals(bounds.width, bounds.height, 1f)
      button.performClick()
    }
    Assert.assertTrue(diceClicked)
    Assert.assertTrue(groupManagementClicked)
  }

  @Test
  fun logoutButtonInvokesCallbackOnSuccessfulSignOut() {
    val signOutResult = CompletableDeferred<AuthResult<Unit>>()
    val viewModel = AuthViewModel(FakeAuthRepository { signOutResult.await() })
    var loggedOut = false

    composeTestRule.setContent {
      SampleAppTheme { HomeScreen(viewModel, onLogout = { loggedOut = true }) }
    }
    composeTestRule.onNodeWithText("Log out").performClick()
    composeTestRule.waitForIdle()

    signOutResult.complete(AuthResult.Success(Unit))
    composeTestRule.waitUntil(5_000) { loggedOut }
    Assert.assertTrue(loggedOut)
  }

  @Test
  fun logoutCallbackDoesNotRunWhenSignOutFails() {
    val signOutResult = CompletableDeferred<AuthResult<Unit>>()
    val viewModel = AuthViewModel(FakeAuthRepository { signOutResult.await() })
    var loggedOut = false

    composeTestRule.setContent {
      SampleAppTheme { HomeScreen(viewModel, onLogout = { loggedOut = true }) }
    }
    composeTestRule.onNodeWithText("Log out").performClick()
    composeTestRule.waitForIdle()
    Assert.assertFalse(loggedOut)

    signOutResult.complete(AuthResult.Failure(AuthError.UNKNOWN))
    composeTestRule.waitUntil(5_000) { viewModel.uiState.value.status is AuthStatus.Error }
    Assert.assertFalse(loggedOut)
  }

  @Test
  fun personalSpaceButtonInvokesCallback() {
    var clicked = false
    composeTestRule.setContent {
      SampleAppTheme { HomeScreen(createViewModel(), onPersonalSpaceClick = { clicked = true }) }
    }

    composeTestRule.onNodeWithTag(TestTags.PSPACEBUTTON).performClick()
    composeTestRule.onNodeWithTag(TestTags.PSPACE_ICON, useUnmergedTree = true).assertIsDisplayed()
    composeTestRule.onNodeWithText("Personal Space").assertIsNotDisplayed()

    Assert.assertTrue(clicked)
  }

  @Test
  fun mapTabInvokesCallback() {
    var clicked = false
    composeTestRule.setContent {
      SampleAppTheme { HomeScreen(createViewModel(), onMapClick = { clicked = true }) }
    }

    composeTestRule.onNodeWithTag(C.Tag.bottomTab("map")).performClick()

    Assert.assertTrue(clicked)
  }

  private fun createViewModel() = AuthViewModel(FakeAuthRepository())

  private class FakeAuthRepository(
      private val signOutAction: suspend () -> AuthResult<Unit> = { AuthResult.Success(Unit) }
  ) : AuthRepository {
    override val authState: Flow<AuthUser?> = emptyFlow()

    override suspend fun signUpWithEmail(
        email: String,
        password: String,
        username: String,
    ): AuthResult<AuthUser> = AuthResult.Failure(AuthError.UNKNOWN)

    override suspend fun signInWithEmail(
        email: String,
        password: String,
    ): AuthResult<AuthUser> = AuthResult.Failure(AuthError.UNKNOWN)

    override suspend fun signInWithGoogle(idToken: String): AuthResult<AuthUser> =
        AuthResult.Failure(AuthError.UNKNOWN)

    override suspend fun signOut(): AuthResult<Unit> = signOutAction()
  }
}
