// Co-authored-by: Codex <codex@openai.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.ui.gameManagement.campaigns

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.se.jdrnexus.R
import com.github.se.jdrnexus.resources.C
import com.github.se.jdrnexus.ui.theme.SampleAppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CampaignHelpPagesTest {
  @get:Rule val composeTestRule = createComposeRule()

  private val pages = campaignHelpPages()

  @Test
  fun pagesGoFromTheScreenToTheCardActions() {
    assertEquals(
        listOf(
            R.string.campaign_help_start_title,
            R.string.campaign_help_roles_title,
            R.string.campaign_help_workspace_title,
        ),
        pages.map { it.title },
    )
    assertEquals(
        listOf(
            R.string.campaign_help_start_body,
            R.string.campaign_help_roles_body,
            R.string.campaign_help_workspace_body,
        ),
        pages.map { it.body },
    )
  }

  @Test
  fun everyPageHasAnIllustrationAtAQuarterOfTheWindow() {
    pages.forEach { page ->
      assertNotNull(page.illustration)
      assertEquals(0.25f, page.illustrationMaxHeightFraction)
    }
  }

  @Test
  fun firstPageShowsTheRealCreateAndJoinButtons() {
    setIllustration(pageIndex = 0)

    // The screen tags these buttons where it calls them; the help's copies carry no tag.
    composeTestRule.onNodeWithText("Create a new group").assertIsDisplayed()
    composeTestRule.onNodeWithText("Join a group").assertIsDisplayed()
  }

  @Test
  fun rolesPageShowsTheGameMasterCardOfTheExampleGroup() {
    setIllustration(pageIndex = 1)

    composeTestRule.onNodeWithText("The Sunken Crown").assertIsDisplayed()
    composeTestRule.onNodeWithText("GameMaster").assertIsDisplayed()
    composeTestRule.onNodeWithText("Fantasy campaign · 3 members").assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.invite_button).assertIsDisplayed()
  }

  @Test
  fun workspacePageShowsTheCardActions() {
    setIllustration(pageIndex = 2)

    composeTestRule.onNodeWithTag(C.Tag.enter_workspace_button).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.play_button).assertIsDisplayed()
  }

  /**
   * Renders the illustration of page [pageIndex] on its own, without [HelpIllustration], so its
   * parts can be found by their text and tags.
   */
  private fun setIllustration(pageIndex: Int) {
    val illustration = requireNotNull(pages[pageIndex].illustration)
    composeTestRule.setContent { SampleAppTheme(darkTheme = false) { illustration() } }
  }
}
