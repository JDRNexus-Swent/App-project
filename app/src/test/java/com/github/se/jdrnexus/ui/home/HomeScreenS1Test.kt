// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.github.se.jdrnexus.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.se.jdrnexus.model.repository.AuthError
import com.github.se.jdrnexus.model.repository.AuthRepository
import com.github.se.jdrnexus.model.repository.AuthResult
import com.github.se.jdrnexus.model.repository.AuthUser
import com.github.se.jdrnexus.resources.C
import com.github.se.jdrnexus.resources.MAX_LANDSCAPE_CONTENT_WIDTH
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
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
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
    composeTestRule.onNodeWithTag(C.Tag.shared_bottom_bar).assertIsDisplayed()
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

  private var screenSize by mutableStateOf(LANDSCAPE_SIZES.first())

  private fun node(tag: String) = composeTestRule.onNodeWithTag(tag, useUnmergedTree = true)

  private fun bounds(tag: String) = node(tag).getUnclippedBoundsInRoot()

  private fun setHomeScreen(
      onDiceClick: () -> Unit = {},
      onGroupManagementClick: () -> Unit = {},
      onPersonalSpaceClick: () -> Unit = {},
  ) {
    composeTestRule.setContent {
      SampleAppTheme {
        Box(Modifier.size(screenSize).testTag(SCREEN)) {
          HomeScreen(
              viewModel = createViewModel(),
              onDiceClick = onDiceClick,
              onGroupManagementClick = onGroupManagementClick,
              onPersonalSpaceClick = onPersonalSpaceClick,
          )
        }
      }
    }
  }

  /**
   * Landscape swaps the bottom bar for the side rail, and the Quicktools sit beside their label.
   */
  private fun assertLandscapeLayout(size: DpSize) {
    node(C.Tag.shared_navigation_rail).assertIsDisplayed()
    node(C.Tag.shared_bottom_bar).assertDoesNotExist()
    val label = bounds(TestTags.QUICKTOOLS)
    val dice = bounds(TestTags.DICEBUTTON)
    TOOL_BUTTONS.forEach { tag ->
      val tool = bounds(tag)
      Assert.assertTrue("$tag is not right of the label at $size", tool.left >= label.right)
      Assert.assertEquals(
          "$tag is not on the tools row at $size",
          dice.top.value,
          tool.top.value,
          1f,
      )
    }
  }

  @Test
  @Config(qualifiers = LARGE_WINDOW)
  fun landscapeShowsTheRailAndSquareQuicktoolsOnAThirdOfTheContent() {
    setHomeScreen()

    LANDSCAPE_SIZES.forEach { size ->
      screenSize = size
      composeTestRule.waitForIdle()
      assertLandscapeLayout(size)

      val screen = bounds(SCREEN)
      (ALL_ELEMENTS + C.Tag.shared_navigation_rail).forEach { tag ->
        val element = bounds(tag)
        Assert.assertTrue("$tag goes below the $size screen", element.bottom <= screen.bottom)
        Assert.assertTrue("$tag goes past the $size screen", element.right <= screen.right)
      }
      TOOL_BUTTONS.forEach { tag ->
        val tool = bounds(tag)
        Assert.assertEquals("$tag is not square at $size", tool.width.value, tool.height.value, 1f)
        Assert.assertTrue("$tag is only ${tool.width} wide at $size", tool.width >= 48.dp)
      }
      // The three squares span a third of the content right of the rail, inside its padding.
      val contentWidth = screen.right - bounds(C.Tag.shared_navigation_rail).right - 48.dp
      val toolsWidth = bounds(TestTags.PSPACEBUTTON).right - bounds(TestTags.DICEBUTTON).left
      Assert.assertEquals(
          "the Quicktools do not take a third of the content at $size",
          (contentWidth / 3).value,
          toolsWidth.value,
          1f,
      )
    }
  }

  @Test
  @Config(qualifiers = LARGE_WINDOW)
  fun landscapeRailHighlightsHomeAndOpensTheOtherDestinations() {
    var campaigns = 0
    var files = 0
    setHomeScreen(
        onGroupManagementClick = { campaigns++ },
        onPersonalSpaceClick = { files++ },
    )
    assertLandscapeLayout(screenSize)

    node(C.Tag.railTab("home")).assertIsSelected()
    node(C.Tag.railTab("home")).performClick()
    node(C.Tag.railTab("campaigns")).performClick()
    node(C.Tag.railTab("files")).performClick()

    Assert.assertEquals(1, campaigns)
    Assert.assertEquals(1, files)
  }

  @Test
  @Config(qualifiers = LARGE_WINDOW)
  fun landscapeQuicktoolsInvokeTheirCallbacks() {
    val clicked = mutableListOf<String>()
    setHomeScreen(
        onDiceClick = { clicked += "dice" },
        onGroupManagementClick = { clicked += "groups" },
        onPersonalSpaceClick = { clicked += "personal" },
    )
    assertLandscapeLayout(screenSize)

    TOOL_BUTTONS.forEach { node(it).performClick() }

    Assert.assertEquals(listOf("dice", "groups", "personal"), clicked)
  }

  @Test
  @Config(qualifiers = LARGE_WINDOW)
  fun splitScreenKeepsTheBottomBar() {
    screenSize = SPLIT_SCREEN_SIZE
    setHomeScreen()

    node(C.Tag.shared_bottom_bar).assertIsDisplayed()
    node(C.Tag.shared_navigation_rail).assertDoesNotExist()
  }

  /** On a tablet the content is centered at the capped width, so the Quicktools do not stretch. */
  @Test
  @Config(qualifiers = TABLET_WINDOW)
  fun tabletCentersTheLandscapeContent() {
    screenSize = TABLET_SIZE
    setHomeScreen()
    assertLandscapeLayout(TABLET_SIZE)

    val railRight = bounds(C.Tag.shared_navigation_rail).right
    val available = bounds(SCREEN).right - railRight
    val contentLeft = railRight + (available - MAX_LANDSCAPE_CONTENT_WIDTH) / 2
    val contentRight = contentLeft + MAX_LANDSCAPE_CONTENT_WIDTH
    Assert.assertTrue(
        "the content is not centered at the capped width",
        bounds(TestTags.QUICKTOOLS).left >= contentLeft,
    )
    Assert.assertTrue(
        "the Quicktools stretch past the capped content",
        bounds(TestTags.PSPACEBUTTON).right <= contentRight,
    )
  }

  private fun createViewModel() = AuthViewModel(FakeAuthRepository())

  private companion object {
    /** Tag of the box that sizes the screen in the landscape tests. */
    const val SCREEN = "home_test_screen"

    /** Window large enough to hold every forced phone size of these tests. */
    const val LARGE_WINDOW = "w900dp-h900dp-xhdpi"

    /**
     * A typical phone in landscape, a smaller one, and the smallest that gets the landscape layout.
     */
    val LANDSCAPE_SIZES =
        listOf(DpSize(844.dp, 390.dp), DpSize(720.dp, 360.dp), DpSize(640.dp, 360.dp))

    /** Half of a landscape phone in split screen: wider than tall, but too narrow for the rail. */
    val SPLIT_SCREEN_SIZE = DpSize(420.dp, 390.dp)

    /** A tablet in landscape, wider than [MAX_LANDSCAPE_CONTENT_WIDTH]. */
    val TABLET_SIZE = DpSize(1280.dp, 800.dp)

    /** Window large enough to hold [TABLET_SIZE]. */
    const val TABLET_WINDOW = "w1300dp-h900dp-xhdpi"

    val TOOL_BUTTONS =
        listOf(TestTags.DICEBUTTON, TestTags.GROUPMANAGEMENTBUTTON, TestTags.PSPACEBUTTON)

    val ALL_ELEMENTS =
        listOf(TestTags.PROFILE_PIC, TestTags.TITLE, TestTags.LOGOUT, TestTags.QUICKTOOLS) +
            TOOL_BUTTONS
  }

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
