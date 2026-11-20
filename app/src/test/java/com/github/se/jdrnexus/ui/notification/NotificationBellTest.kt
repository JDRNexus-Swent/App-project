// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.ui.notification

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onChild
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.se.jdrnexus.resources.C
import com.github.se.jdrnexus.ui.theme.SampleAppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationBellTest {
  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun hidesBadgeWhenNothingIsUnread() {
    setBell(unreadCount = 0)

    composeTestRule
        .onAllNodesWithTag(C.Tag.notification_unread_badge, useUnmergedTree = true)
        .assertCountEquals(0)
    composeTestRule
        .onNodeWithContentDescription(PLAIN_DESCRIPTION, useUnmergedTree = true)
        .assertIsDisplayed()
  }

  @Test
  fun showsUnreadCountOnBadge() {
    setBell(unreadCount = SMALL_UNREAD_COUNT)

    badgeText().assertTextEquals("3")
    composeTestRule
        .onNodeWithContentDescription(UNREAD_DESCRIPTION, useUnmergedTree = true)
        .assertIsDisplayed()
  }

  @Test
  fun writesCountInFullAtTheCap() {
    setBell(unreadCount = MAX_DISPLAYED_UNREAD_COUNT)

    badgeText().assertTextEquals("9")
  }

  @Test
  fun capsCountAboveTheCap() {
    setBell(unreadCount = MAX_DISPLAYED_UNREAD_COUNT + 1)

    badgeText().assertTextEquals("9+")
  }

  @Test
  fun clickInvokesCallback() {
    var clicks = 0
    setBell(unreadCount = 0, onClick = { clicks++ })

    composeTestRule.onNodeWithTag(C.Tag.notification_bell).performClick()

    assertEquals(1, clicks)
  }

  private fun setBell(unreadCount: Int, onClick: () -> Unit = {}) {
    composeTestRule.setContent {
      SampleAppTheme { NotificationBell(unreadCount = unreadCount, onClick = onClick) }
    }
  }

  /** The badge's text is a child node, so it is only reachable in the unmerged tree. */
  private fun badgeText() =
      composeTestRule
          .onNodeWithTag(C.Tag.notification_unread_badge, useUnmergedTree = true)
          .onChild()

  private companion object {
    /** An unread count below the cap, written in full. */
    const val SMALL_UNREAD_COUNT = 3
    const val PLAIN_DESCRIPTION = "Notifications"
    const val UNREAD_DESCRIPTION = "Notifications, unread notifications available"
  }
}
