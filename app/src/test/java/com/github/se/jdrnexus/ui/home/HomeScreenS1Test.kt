// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.github.se.jdrnexus.ui.home

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.se.jdrnexus.model.repository.AuthError
import com.github.se.jdrnexus.model.repository.AuthRepository
import com.github.se.jdrnexus.model.repository.AuthResult
import com.github.se.jdrnexus.model.repository.AuthUser
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
  fun usesThemeBackgroundColor() {
    val background = Color(0xFF123456)
    composeTestRule.setContent {
      MaterialTheme(colorScheme = lightColorScheme(background = background)) {
        HomeScreen(createViewModel())
      }
    }

    Assert.assertEquals(
        background,
        composeTestRule.onRoot().captureToImage().toPixelMap()[0, 0],
    )
  }

  @Test
  fun displaysTitleProfilePlaceholderSectionsAndAdventures() {
    composeTestRule.setContent { SampleAppTheme { HomeScreen(createViewModel()) } }

    composeTestRule.onNodeWithTag(TestTags.TITLE).assertIsDisplayed()
    composeTestRule.onNodeWithTag(TestTags.PROFILE_PIC).assertIsDisplayed()
    composeTestRule.onNodeWithTag(TestTags.OBJECTSROW).assertExists()
    composeTestRule.onNodeWithTag(TestTags.ADVENTURES).assertIsDisplayed()
    composeTestRule.onNodeWithTag(TestTags.QUICKTOOLS).assertIsDisplayed()

    TestTags.placeholderObjects.forEachIndexed { index, ttag ->
      Assert.assertTrue(composeTestRule.onAllNodesWithTag(ttag).fetchSemanticsNodes().isNotEmpty())
      Assert.assertTrue(
          composeTestRule
              .onAllNodesWithText("Adventure ${index + 1}")
              .fetchSemanticsNodes()
              .isNotEmpty()
      )
    }
  }

  @Test
  fun quickToolButtonsAreSquareAndClickable() {
    composeTestRule.setContent { SampleAppTheme { HomeScreen(createViewModel()) } }

    TestTags.BUTTONS.forEachIndexed { index, tag ->
      val button = composeTestRule.onNodeWithTag(tag)
      button.assertIsDisplayed()
      composeTestRule.onNodeWithText("Tool ${index + 1}").assertIsDisplayed()
      val bounds = button.fetchSemanticsNode().boundsInRoot
      Assert.assertEquals(bounds.width, bounds.height, 1f)
      button.performClick()
    }
  }

  @Test
  fun logoutButtonInvokesCallback() {
    var loggedOut = false
    composeTestRule.setContent {
      SampleAppTheme { HomeScreen(createViewModel(), onLogout = { loggedOut = true }) }
    }

    composeTestRule.onNodeWithText("Log out").performClick()

    Assert.assertTrue(loggedOut)
  }

  @Test
  fun logoutCallbackWaitsForSignOutToComplete() {
    val signOutResult = CompletableDeferred<AuthResult<Unit>>()
    val viewModel = AuthViewModel(FakeAuthRepository { signOutResult.await() })
    var loggedOut = false

    composeTestRule.setContent {
      SampleAppTheme { HomeScreen(viewModel, onLogout = { loggedOut = true }) }
    }
    composeTestRule.onNodeWithText("Log out").performClick()
    composeTestRule.waitForIdle()
    Assert.assertFalse(loggedOut)

    signOutResult.complete(AuthResult.Success(Unit))
    composeTestRule.waitUntil(5_000) { loggedOut }
    Assert.assertTrue(loggedOut)
  }

  @Test
  fun personalSpaceButtonInvokesCallback() {
    var clicked = false
    composeTestRule.setContent {
      SampleAppTheme { HomeScreen(createViewModel(), onPersonalSpaceClick = { clicked = true }) }
    }

    composeTestRule.onNodeWithTag(TestTags.PSPACEBUTTON).performClick()

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
