// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.ui.notification

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.se.jdrnexus.model.notification.Notification
import com.github.se.jdrnexus.resources.C
import com.github.se.jdrnexus.ui.theme.SampleAppTheme
import com.github.se.jdrnexus.utils.FakeNotificationViewModel
import com.github.se.jdrnexus.utils.loadedNotifications
import com.github.se.jdrnexus.utils.testNotification
import com.github.se.jdrnexus.viewmodel.NotificationLoadState
import com.github.se.jdrnexus.viewmodel.NotificationUiState
import kotlinx.coroutines.flow.update
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationScreenTest {
  @get:Rule val composeTestRule = createComposeRule()

  private val opened = mutableListOf<Notification>()
  private val selectedTabs = mutableListOf<String>()
  private var backClicks = 0

  // ---------- Load states ----------

  @Test
  fun loadingStateShowsSpinnerAndNoList() {
    setScreen(FakeNotificationViewModel(NotificationUiState()))

    composeTestRule.onNodeWithTag(C.Tag.notification_loading).assertIsDisplayed()
    composeTestRule.onNodeWithText("Loading notifications…").assertIsDisplayed()
    composeTestRule.onAllNodesWithTag(C.Tag.notification_list).assertCountEquals(0)
  }

  @Test
  fun signedOutStateAsksToSignIn() {
    setScreen(
        FakeNotificationViewModel(NotificationUiState(loadState = NotificationLoadState.SignedOut))
    )

    composeTestRule.onNodeWithTag(C.Tag.notification_signed_out).assertIsDisplayed()
    composeTestRule.onNodeWithText("Sign in to see your notifications.").assertIsDisplayed()
  }

  @Test
  fun emptyStateExplainsWhatWillShowUp() {
    setScreen(
        FakeNotificationViewModel(NotificationUiState(loadState = NotificationLoadState.Empty))
    )

    composeTestRule.onNodeWithTag(C.Tag.notification_empty).assertIsDisplayed()
    composeTestRule.onNodeWithText("No notifications yet").assertIsDisplayed()
    composeTestRule.onAllNodesWithTag(C.Tag.notification_list).assertCountEquals(0)
  }

  @Test
  fun errorStateRetryCallsViewModel() {
    val viewModel =
        FakeNotificationViewModel(NotificationUiState(loadState = NotificationLoadState.Error(RAW)))
    setScreen(viewModel)

    composeTestRule.onNodeWithTag(C.Tag.notification_error).assertIsDisplayed()
    composeTestRule.onNodeWithText("Couldn't load your notifications.").assertIsDisplayed()
    composeTestRule.onAllNodesWithText(RAW).assertCountEquals(0)
    composeTestRule.onNodeWithTag(C.Tag.notification_retry).performClick()

    assertEquals(1, viewModel.retryCalls)
  }

  // ---------- List ----------

  @Test
  fun unreadAndReadNotificationsAreMarkedDifferently() {
    setScreen(FakeNotificationViewModel(loadedNotifications(unread, read)))

    composeTestRule.onNodeWithTag(C.Tag.notificationItem(UNREAD_ID)).assert(readState("Unread"))
    composeTestRule.onNodeWithTag(C.Tag.notificationItem(READ_ID)).assert(readState("Read"))
    composeTestRule
        .onNodeWithTag(C.Tag.notificationUnreadDot(UNREAD_ID), useUnmergedTree = true)
        .assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(C.Tag.notificationMarkAsRead(UNREAD_ID), useUnmergedTree = true)
        .assertIsDisplayed()
    composeTestRule
        .onAllNodesWithTag(C.Tag.notificationUnreadDot(READ_ID), useUnmergedTree = true)
        .assertCountEquals(0)
    composeTestRule
        .onAllNodesWithTag(C.Tag.notificationMarkAsRead(READ_ID), useUnmergedTree = true)
        .assertCountEquals(0)
  }

  @Test
  fun notificationWithoutConfirmedDateShowsJustNow() {
    setScreen(
        FakeNotificationViewModel(
            loadedNotifications(testNotification(UNREAD_ID, createdAt = null))
        )
    )

    composeTestRule.onNodeWithText("Just now", useUnmergedTree = true).assertIsDisplayed()
  }

  @Test
  fun selectingUnreadNotificationMarksItReadAndOpensIt() {
    val viewModel = FakeNotificationViewModel(loadedNotifications(unread, read))
    setScreen(viewModel)

    composeTestRule.onNodeWithTag(C.Tag.notificationItem(UNREAD_ID)).performClick()

    assertEquals(listOf(UNREAD_ID), viewModel.markedAsRead)
    assertEquals(listOf(unread), opened)
  }

  @Test
  fun selectingReadNotificationOpensItWithoutMarkingIt() {
    val viewModel = FakeNotificationViewModel(loadedNotifications(unread, read))
    setScreen(viewModel)

    composeTestRule.onNodeWithTag(C.Tag.notificationItem(READ_ID)).performClick()

    assertEquals(emptyList<String>(), viewModel.markedAsRead)
    assertEquals(listOf(read), opened)
  }

  @Test
  fun markAsReadLinkMarksWithoutOpening() {
    val viewModel = FakeNotificationViewModel(loadedNotifications(unread, read))
    setScreen(viewModel)

    composeTestRule
        .onNodeWithTag(C.Tag.notificationMarkAsRead(UNREAD_ID), useUnmergedTree = true)
        .performClick()

    assertEquals(listOf(UNREAD_ID), viewModel.markedAsRead)
    assertEquals(emptyList<Notification>(), opened)
  }

  @Test
  fun markAsReadLinkHasAFullTouchTarget() {
    setScreen(FakeNotificationViewModel(loadedNotifications(unread)))

    composeTestRule
        .onNodeWithTag(C.Tag.notificationMarkAsRead(UNREAD_ID), useUnmergedTree = true)
        .assertHeightIsAtLeast(MIN_TOUCH_TARGET)
  }

  // ---------- Mark all as read ----------

  @Test
  fun markAllAsksConfirmationBeforeMarking() {
    val viewModel = FakeNotificationViewModel(loadedNotifications(unread, read))
    setScreen(viewModel)

    composeTestRule.onNodeWithTag(C.Tag.notification_mark_all).performClick()
    waitForSnackbar()

    composeTestRule.onNodeWithText("Mark all notifications as read?").assertIsDisplayed()
    assertEquals(0, viewModel.markAllCalls)

    composeTestRule.onNodeWithTag(C.Tag.notification_snackbar_action).performClick()
    composeTestRule.waitUntil(TIMEOUT_MS) { viewModel.markAllCalls == 1 }
  }

  @Test
  fun dismissingConfirmationDoesNotMarkAll() {
    val viewModel = FakeNotificationViewModel(loadedNotifications(unread, read))
    setScreen(viewModel)

    composeTestRule.onNodeWithTag(C.Tag.notification_mark_all).performClick()
    waitForSnackbar()
    composeTestRule.onNodeWithTag(C.Tag.notification_snackbar_dismiss).performClick()
    composeTestRule.waitUntil(TIMEOUT_MS) {
      composeTestRule.onAllNodesWithTag(C.Tag.notification_snackbar).fetchSemanticsNodes().isEmpty()
    }

    assertEquals(0, viewModel.markAllCalls)
    composeTestRule.onNodeWithTag(C.Tag.notification_mark_all).assertIsEnabled()
  }

  @Test
  fun markAllIsDisabledWhileConfirmationIsShown() {
    setScreen(FakeNotificationViewModel(loadedNotifications(unread, read)))

    composeTestRule.onNodeWithTag(C.Tag.notification_mark_all).performClick()
    waitForSnackbar()

    composeTestRule.onNodeWithTag(C.Tag.notification_mark_all).assertIsNotEnabled()
  }

  @Test
  fun markAllIsDisabledWhenEverythingIsRead() {
    setScreen(FakeNotificationViewModel(loadedNotifications(read)))

    composeTestRule.onNodeWithTag(C.Tag.notification_mark_all).assertIsNotEnabled()
  }

  // ---------- Action errors ----------

  @Test
  fun actionErrorShowsReadableMessageThenClearsIt() {
    val viewModel = FakeNotificationViewModel(loadedNotifications(unread))
    setScreen(viewModel)

    viewModel.notificationUiState.update { it.copy(actionError = RAW) }
    waitForSnackbar()

    composeTestRule
        .onNodeWithText("Couldn't update your notifications. Try again.")
        .assertIsDisplayed()
    composeTestRule.onAllNodesWithText(RAW).assertCountEquals(0)
    composeTestRule.onNodeWithTag(C.Tag.notification_snackbar_dismiss).performClick()
    composeTestRule.waitUntil(TIMEOUT_MS) { viewModel.clearActionErrorCalls == 1 }
  }

  // ---------- Bars ----------

  @Test
  fun backActionInvokesCallback() {
    setScreen(FakeNotificationViewModel(loadedNotifications(unread)))

    composeTestRule.onNodeWithTag(C.Tag.shared_back_button).performClick()

    assertEquals(1, backClicks)
  }

  @Test
  fun bottomBarReportsSelectedTab() {
    setScreen(FakeNotificationViewModel(loadedNotifications(unread)))

    composeTestRule.onNodeWithTag(C.Tag.bottomTab("map")).performClick()

    assertEquals(listOf("Map"), selectedTabs)
  }

  private fun setScreen(viewModel: FakeNotificationViewModel) {
    composeTestRule.setContent {
      SampleAppTheme {
        NotificationScreen(
            viewModel = viewModel,
            onBack = { backClicks++ },
            onOpenNotification = { opened += it },
            onTabSelected = { selectedTabs += it },
        )
      }
    }
  }

  private fun waitForSnackbar() {
    composeTestRule.waitUntil(TIMEOUT_MS) {
      composeTestRule
          .onAllNodesWithTag(C.Tag.notification_snackbar)
          .fetchSemanticsNodes()
          .isNotEmpty()
    }
  }

  private fun readState(state: String) =
      SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, state)

  private companion object {
    const val UNREAD_ID = "unread"
    const val READ_ID = "read"

    /** Raw error message from the ViewModel, which the screen must never show to the user. */
    const val RAW = "PERMISSION_DENIED: raw failure"

    /** Longest wait for the snackbar, which appears and resumes asynchronously. */
    const val TIMEOUT_MS = 5_000L

    /** Smallest touch target Android recommends for anything tappable. */
    val MIN_TOUCH_TARGET = 48.dp

    val unread = testNotification(UNREAD_ID)
    val read = testNotification(READ_ID, isRead = true)
  }
}
