// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.github.se.jdrnexus.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.se.jdrnexus.model.repository.AuthError
import com.github.se.jdrnexus.model.repository.AuthRepository
import com.github.se.jdrnexus.model.repository.AuthResult
import com.github.se.jdrnexus.model.repository.AuthUser
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
      composeTestRule.onNodeWithText(listOf("Dice", "Group Management")[index]).assertIsDisplayed()
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

    Assert.assertTrue(clicked)
  }

  private var screenSize by mutableStateOf(LANDSCAPE_SIZES.first())

  private fun bounds(tag: String) =
      composeTestRule.onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot()

  /** Whether some part of the screen can scroll vertically, i.e. its content does not fit. */
  private fun anythingScrollsVertically(): Boolean =
      composeTestRule
          .onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
          .fetchSemanticsNodes()
          .any { it.config[SemanticsProperties.VerticalScrollAxisRange].maxValue() > 0f }

  private fun assertToolsBesideAdventures(size: DpSize) {
    val adventures = bounds(TestTags.OBJECTSROW)
    TOOL_BUTTONS.forEach { tag ->
      Assert.assertTrue(
          "$tag is not right of the adventures at $size",
          bounds(tag).left >= adventures.right,
      )
    }
  }

  private fun setHomeScreen(
      fontScale: Float? = null,
      onDiceClick: () -> Unit = {},
      onGroupManagementClick: () -> Unit = {},
      onPersonalSpaceClick: () -> Unit = {},
  ) {
    composeTestRule.setContent {
      val density = LocalDensity.current
      CompositionLocalProvider(
          LocalDensity provides Density(density.density, fontScale ?: density.fontScale)
      ) {
        SampleAppTheme {
          Box(Modifier.size(screenSize)) {
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
  }

  @Test
  @Config(qualifiers = LARGE_WINDOW)
  fun landscapeFitsEveryElementWithToolsInTheRightThird() {
    setHomeScreen()
    val toolShares = mutableListOf<Float>()

    LANDSCAPE_SIZES.forEach { size ->
      screenSize = size
      composeTestRule.waitForIdle()

      Assert.assertFalse("the home screen scrolls at $size", anythingScrollsVertically())
      val screen = bounds(TestTags.SCREEN)
      ALL_ELEMENTS.forEach { tag ->
        val element = bounds(tag)
        Assert.assertTrue("$tag goes below the $size screen", element.bottom <= screen.bottom)
        Assert.assertTrue("$tag goes past the $size screen", element.right <= screen.right)
      }
      Assert.assertTrue(
          "the title runs into the logout button at $size",
          bounds(TestTags.TITLE).right <= bounds(TestTags.LOGOUT).left,
      )
      assertToolsBesideAdventures(size)

      val toolShare = bounds(TestTags.DICEBUTTON).width / size.width
      Assert.assertTrue(
          "the tools take $toolShare of the $size screen, not about a third",
          toolShare in MIN_TOOLS_SHARE..MAX_TOOLS_SHARE,
      )
      toolShares += toolShare
    }
    Assert.assertTrue(
        "the tools' share of the screen changes between phones: $toolShares",
        toolShares.max() - toolShares.min() <= MAX_TOOLS_SHARE_SPREAD,
    )
  }

  @Test
  @Config(qualifiers = LARGE_WINDOW)
  fun landscapeToolButtonsInvokeTheirCallbacks() {
    val clicked = mutableListOf<String>()
    setHomeScreen(
        onDiceClick = { clicked += "dice" },
        onGroupManagementClick = { clicked += "groups" },
        onPersonalSpaceClick = { clicked += "personal" },
    )
    assertToolsBesideAdventures(screenSize)

    TOOL_BUTTONS.forEach { composeTestRule.onNodeWithTag(it).performClick() }

    Assert.assertEquals(listOf("dice", "groups", "personal"), clicked)
  }

  /** On a tablet the page stops growing and is centered, so the tool buttons are not stretched. */
  @Test
  @Config(qualifiers = TABLET_WINDOW)
  fun tabletCentersTheLandscapeLayoutAtThePhoneProportions() {
    screenSize = TABLET_SIZE
    setHomeScreen()
    assertToolsBesideAdventures(TABLET_SIZE)

    val screen = bounds(TestTags.SCREEN)
    val contentLeft = screen.left + (TABLET_SIZE.width - MAX_LANDSCAPE_CONTENT_WIDTH) / 2
    val contentRight = contentLeft + MAX_LANDSCAPE_CONTENT_WIDTH
    Assert.assertTrue(
        "the page is not centered at the capped width",
        bounds(TestTags.PROFILE_PIC).left >= contentLeft - 1.dp,
    )
    ALL_ELEMENTS.forEach { tag ->
      Assert.assertTrue(
          "$tag stretches past the capped page",
          bounds(tag).right <= contentRight + 1.dp,
      )
    }
    TOOL_BUTTONS.forEach { tag ->
      Assert.assertTrue(
          "$tag is ${bounds(tag).width} wide, more than a third of the capped page",
          bounds(tag).width <= MAX_LANDSCAPE_CONTENT_WIDTH / 3,
      )
    }
  }

  @Test
  @Config(qualifiers = LARGE_WINDOW)
  fun splitScreenKeepsThePortraitLayout() {
    screenSize = SPLIT_SCREEN_SIZE
    setHomeScreen()

    val tops = TOOL_BUTTONS.map { bounds(it).top }
    Assert.assertTrue(
        "a narrow window keeps the tools side by side: $tops",
        tops.distinct().size == 1,
    )
  }

  /** With a large system font the page scrolls rather than cutting off the tool labels. */
  @Test
  @Config(qualifiers = LARGE_WINDOW)
  fun landscapeWithALargeFontScrollsInsteadOfCuttingOffToolLabels() {
    screenSize = LANDSCAPE_SIZES[1]
    setHomeScreen(fontScale = LARGE_FONT_SCALE)

    Assert.assertTrue("the page should scroll with a large font", anythingScrollsVertically())
    TOOL_BUTTONS.zip(TOOL_LABELS).forEach { (tag, label) ->
      val button = bounds(tag)
      val text =
          composeTestRule.onNodeWithText(label, useUnmergedTree = true).getUnclippedBoundsInRoot()
      Assert.assertTrue(
          "$label is cut off: $text in $button",
          text.top >= button.top && text.bottom <= button.bottom,
      )
    }
  }

  private fun createViewModel() = AuthViewModel(FakeAuthRepository())

  private companion object {
    /** Window large enough to hold every forced screen size of these tests. */
    const val LARGE_WINDOW = "w900dp-h900dp-xhdpi"

    /** A typical phone in landscape, a smaller one, and the smallest that gets two columns. */
    val LANDSCAPE_SIZES =
        listOf(DpSize(844.dp, 390.dp), DpSize(720.dp, 360.dp), DpSize(640.dp, 360.dp))

    /** Half of a landscape phone in split screen: wider than tall, but too narrow for two panes. */
    val SPLIT_SCREEN_SIZE = DpSize(420.dp, 390.dp)

    /** A tablet in landscape, wider than [MAX_LANDSCAPE_CONTENT_WIDTH]. */
    val TABLET_SIZE = DpSize(1280.dp, 800.dp)

    /** Window large enough to hold [TABLET_SIZE]. */
    const val TABLET_WINDOW = "w1300dp-h900dp-xhdpi"

    /** Largest font size Android offers. */
    const val LARGE_FONT_SCALE = 2f

    /**
     * The tools column is a third of the body, which is narrower than the screen because of the
     * page padding and the gap beside the adventures.
     */
    const val MIN_TOOLS_SHARE = 0.25f
    const val MAX_TOOLS_SHARE = 1f / 3f

    /** How much the tools' share of the screen width may differ between phone sizes. */
    const val MAX_TOOLS_SHARE_SPREAD = 0.03f

    val TOOL_BUTTONS =
        listOf(TestTags.DICEBUTTON, TestTags.GROUPMANAGEMENTBUTTON, TestTags.PSPACEBUTTON)

    val TOOL_LABELS = listOf("Dice", "Group Management", "Personal Space")

    val ALL_ELEMENTS =
        listOf(
            TestTags.PROFILE_PIC,
            TestTags.TITLE,
            TestTags.LOGOUT,
            TestTags.ADVENTURES,
            TestTags.OBJECTSROW,
            TestTags.QUICKTOOLS,
        ) + TOOL_BUTTONS
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
