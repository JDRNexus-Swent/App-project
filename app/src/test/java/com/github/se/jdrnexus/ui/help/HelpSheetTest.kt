// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.ui.help

import androidx.activity.ComponentDialog
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.se.jdrnexus.R
import com.github.se.jdrnexus.resources.C
import com.github.se.jdrnexus.ui.theme.SampleAppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

@RunWith(AndroidJUnit4::class)
class HelpSheetTest {
  @get:Rule val composeTestRule = createComposeRule()

  private val pages =
      listOf(
          HelpPage(
              R.string.campaign_help_start_title,
              R.string.campaign_help_start_body,
          ),
          HelpPage(
              R.string.campaign_help_roles_title,
              R.string.campaign_help_roles_body,
          ),
          HelpPage(
              R.string.campaign_help_workspace_title,
              R.string.campaign_help_workspace_body,
          ),
      )

  @Test
  fun firstPageIsShownWithNext() {
    setPagerContent(pages)

    composeTestRule.onNodeWithTag(C.Tag.helpPage(0)).assertIsDisplayed()
    composeTestRule.onNodeWithText("Campaigns").assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.help_next).assertIsDisplayed()
    composeTestRule.onAllNodesWithTag(C.Tag.help_done).assertCountEquals(0)
    composeTestRule
        .onNodeWithTag(C.Tag.help_page_indicator)
        .assertContentDescriptionEquals("Page 1 of 3")
  }

  @Test
  fun nextMovesToTheFollowingPage() {
    setPagerContent(pages)

    composeTestRule.onNodeWithTag(C.Tag.help_next).performClick()

    composeTestRule.onNodeWithTag(C.Tag.helpPage(1)).assertIsDisplayed()
    composeTestRule.onNodeWithText("Roles and invitations").assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.helpPage(0)).assertIsNotDisplayed()
    composeTestRule
        .onNodeWithTag(C.Tag.help_page_indicator)
        .assertContentDescriptionEquals("Page 2 of 3")
  }

  @Test
  fun lastPageReplacesNextWithGotItWhichCallsOnDoneOnce() {
    var doneCalls = 0
    setPagerContent(pages, onDone = { doneCalls++ })

    composeTestRule.onNodeWithTag(C.Tag.help_next).performClick()
    composeTestRule.onNodeWithTag(C.Tag.help_next).performClick()

    composeTestRule.onNodeWithTag(C.Tag.helpPage(2)).assertIsDisplayed()
    composeTestRule.onAllNodesWithTag(C.Tag.help_next).assertCountEquals(0)
    composeTestRule.onNodeWithTag(C.Tag.help_done).assertIsDisplayed().performClick()
    assertEquals(1, doneCalls)
  }

  @Test
  fun singlePageShowsGotItAtOnce() {
    var doneCalls = 0
    setPagerContent(pages.take(1), onDone = { doneCalls++ })

    composeTestRule.onAllNodesWithTag(C.Tag.help_next).assertCountEquals(0)
    composeTestRule.onNodeWithTag(C.Tag.help_done).performClick()
    assertEquals(1, doneCalls)
  }

  @Test
  fun noPageStillOffersGotIt() {
    var doneCalls = 0
    setPagerContent(emptyList(), onDone = { doneCalls++ })

    composeTestRule.onNodeWithTag(C.Tag.help_done).performClick()
    assertEquals(1, doneCalls)
  }

  @Test
  fun sheetShowsItsPagesAndReportsGotIt() {
    var dismissals = 0
    composeTestRule.setContent {
      SampleAppTheme(darkTheme = false) {
        HelpSheet(pages = pages.take(1), onDismiss = { dismissals++ })
      }
    }

    composeTestRule.onNodeWithTag(C.Tag.help_sheet).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.help_done).performClick()
    composeTestRule.waitForIdle()

    assertEquals(1, dismissals)
  }

  @Test
  fun systemBackClosesTheSheet() {
    val dismissals = setClosableSheet()

    composeTestRule.waitForIdle()
    val dialog = ShadowDialog.getLatestDialog() as ComponentDialog
    composeTestRule.runOnUiThread { dialog.onBackPressedDispatcher.onBackPressed() }
    composeTestRule.waitForIdle()

    assertSheetClosed(dismissals)
  }

  @Test
  fun tapOutsideTheSheetClosesIt() {
    val dismissals = setClosableSheet()

    // The scrim covers the whole window; its top edge is above the sheet.
    composeTestRule.onNodeWithContentDescription("Close sheet").performTouchInput {
      click(Offset(centerX, 1f))
    }
    composeTestRule.waitForIdle()

    assertSheetClosed(dismissals)
  }

  @Test
  fun swipeDownClosesTheSheet() {
    val dismissals = setClosableSheet()

    composeTestRule.onNodeWithTag(C.Tag.help_sheet).performTouchInput { swipeDown() }
    composeTestRule.waitForIdle()

    assertSheetClosed(dismissals)
  }

  @Test
  fun quickSecondTapOnNextDuringTheSlideToTheLastPageDoesNotClose() {
    var doneCalls = 0
    setPagerContent(pages, onDone = { doneCalls++ })
    composeTestRule.onNodeWithTag(C.Tag.help_next).performClick()
    composeTestRule.onNodeWithTag(C.Tag.helpPage(1)).assertIsDisplayed()

    // Freezes time halfway through the slide to the last page, where the second tap lands.
    composeTestRule.mainClock.autoAdvance = false
    composeTestRule.onNodeWithTag(C.Tag.help_next).performClick()
    composeTestRule.mainClock.advanceTimeBy(SLIDE_HALFWAY_MILLIS)
    composeTestRule.onAllNodesWithTag(C.Tag.help_done).assertCountEquals(0)
    composeTestRule.onNodeWithTag(C.Tag.help_next).performClick()
    composeTestRule.mainClock.autoAdvance = true
    composeTestRule.waitForIdle()

    assertEquals(0, doneCalls)
    composeTestRule.onNodeWithTag(C.Tag.helpPage(2)).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.help_done).assertIsDisplayed()
  }

  @Test
  @Config(qualifiers = "land")
  fun longPageStaysReadableInLandscapeWithLargeFont() {
    val longPage =
        HelpPage(
            R.string.campaign_help_roles_title,
            R.string.campaign_help_roles_body,
            illustration = { Box(Modifier.fillMaxWidth().height(2000.dp)) },
        )
    composeTestRule.setContent {
      val density = LocalDensity.current
      CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
        SampleAppTheme(darkTheme = false) {
          HelpPagerContent(pages = listOf(longPage), onDone = {})
        }
      }
    }

    // The button and the dots stay on screen.
    composeTestRule.onNodeWithTag(C.Tag.help_done).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.help_page_indicator).assertIsDisplayed()

    // The page scrolls far enough for the end of its text to sit above the dots.
    composeTestRule.onNodeWithTag(C.Tag.helpPage(0)).performSemanticsAction(
        SemanticsActions.ScrollBy
    ) {
      it(0f, 100_000f)
    }
    val text =
        composeTestRule
            .onNodeWithText("to send to your players", substring = true)
            .getUnclippedBoundsInRoot()
    val dots = composeTestRule.onNodeWithTag(C.Tag.help_page_indicator).getUnclippedBoundsInRoot()
    assertTrue(text.bottom <= dots.top)
  }

  @Test
  fun pagerKeepsTheSameHeightOnPagesOfDifferentHeights() {
    val short = HelpPage(R.string.campaign_help_start_title, R.string.campaign_help_start_body)
    val tall =
        HelpPage(
            R.string.campaign_help_roles_title,
            R.string.campaign_help_roles_body,
            illustration = { Box(Modifier.fillMaxWidth().height(200.dp)) },
        )
    setPagerContent(listOf(short, tall))

    // The dots sit right under the pager, so they move if its height changes.
    val dotsTopOnShortPage = dotsTop()
    composeTestRule.onNodeWithTag(C.Tag.help_next).performClick()
    composeTestRule.onNodeWithTag(C.Tag.helpPage(1)).assertIsDisplayed()

    assertEquals(dotsTopOnShortPage, dotsTop())
  }

  @Test
  fun currentPageSurvivesRecreation() {
    val restorationTester = StateRestorationTester(composeTestRule)
    restorationTester.setContent {
      SampleAppTheme(darkTheme = false) { HelpPagerContent(pages = pages, onDone = {}) }
    }

    composeTestRule.onNodeWithTag(C.Tag.help_next).performClick()
    restorationTester.emulateSavedInstanceStateRestore()

    composeTestRule.onNodeWithTag(C.Tag.helpPage(1)).assertIsDisplayed()
  }

  /**
   * Shows a [HelpSheet] that its host stops composing once dismissed, as a screen does, and returns
   * the number of dismissals so far.
   */
  private fun setClosableSheet(): () -> Int {
    var dismissals = 0
    composeTestRule.setContent {
      SampleAppTheme(darkTheme = false) {
        var isOpen by remember { mutableStateOf(true) }
        if (isOpen) {
          HelpSheet(
              pages = pages,
              onDismiss = {
                dismissals++
                isOpen = false
              },
          )
        }
      }
    }
    composeTestRule.onNodeWithTag(C.Tag.help_sheet).assertIsDisplayed()
    return { dismissals }
  }

  private fun assertSheetClosed(dismissals: () -> Int) {
    composeTestRule.onAllNodesWithTag(C.Tag.help_sheet).assertCountEquals(0)
    assertEquals(1, dismissals())
  }

  private fun dotsTop() =
      composeTestRule.onNodeWithTag(C.Tag.help_page_indicator).getUnclippedBoundsInRoot().top

  private fun setPagerContent(pages: List<HelpPage>, onDone: () -> Unit = {}) {
    composeTestRule.setContent {
      SampleAppTheme(darkTheme = false) { HelpPagerContent(pages = pages, onDone = onDone) }
    }
  }

  private companion object {
    /** About half of the pager's default slide between two pages. */
    const val SLIDE_HALFWAY_MILLIS = 150L
  }
}
