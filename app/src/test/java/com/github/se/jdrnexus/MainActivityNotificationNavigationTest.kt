// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus

import android.content.Context
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onChild
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.se.jdrnexus.model.notification.NotificationType
import com.github.se.jdrnexus.model.personalSpace.CampaignGroup
import com.github.se.jdrnexus.model.repository.AuthError
import com.github.se.jdrnexus.model.repository.AuthRepository
import com.github.se.jdrnexus.model.repository.AuthResult
import com.github.se.jdrnexus.model.repository.AuthUser
import com.github.se.jdrnexus.resources.C
import com.github.se.jdrnexus.ui.authentication.AuthViewModel
import com.github.se.jdrnexus.ui.gameManagement.GameManagementTestTags
import com.github.se.jdrnexus.ui.home.TestTags
import com.github.se.jdrnexus.utils.FakeNotificationViewModel
import com.github.se.jdrnexus.utils.NavigationGroupRepository
import com.github.se.jdrnexus.utils.NavigationWorkspaceRepository
import com.github.se.jdrnexus.utils.loadedNotifications
import com.github.se.jdrnexus.utils.testNotification
import com.google.firebase.FirebaseApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Navigation to and from the notification screen, with the production screens and fakes. */
@RunWith(AndroidJUnit4::class)
class MainActivityNotificationNavigationTest {
  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun homeBellCountsOnlyUnreadNotificationsAndOpensThem() {
    showApp(
        FakeNotificationViewModel(
            loadedNotifications(
                testNotification("unread-1"),
                testNotification("unread-2"),
                testNotification("read", isRead = true),
            )
        )
    )

    assertBadge("2")
    composeTestRule.onNodeWithTag(C.Tag.notification_bell).performClick()

    waitForTag(C.Tag.notification_list)
  }

  @Test
  fun homeBadgeFollowsNotificationsArrivingWhileHomeIsShown() {
    val viewModel = FakeNotificationViewModel(loadedNotifications(testNotification(ID)))
    showApp(viewModel)
    assertBadge("1")

    // The repository pushes a new unread notification while the user stays on Home.
    viewModel.notificationUiState.update { state ->
      state.copy(notifications = state.notifications + testNotification(OTHER_ID))
    }

    composeTestRule.waitForIdle()
    assertBadge("2")
  }

  @Test
  fun homeBadgeDropsAfterMarkingANotificationRead() {
    val viewModel =
        FakeNotificationViewModel(
            loadedNotifications(testNotification(ID), testNotification(OTHER_ID))
        )
    showApp(viewModel)
    assertBadge("2")
    composeTestRule.onNodeWithTag(C.Tag.notification_bell).performClick()
    waitForTag(C.Tag.notification_list)

    composeTestRule
        .onNodeWithTag(C.Tag.notificationMarkAsRead(ID), useUnmergedTree = true)
        .performClick()
    // The fake only records the call: apply the update the repository would push back.
    viewModel.notificationUiState.update { state ->
      state.copy(
          notifications =
              state.notifications.map { if (it.id == ID) it.copy(isRead = true) else it }
      )
    }
    composeTestRule.onNodeWithTag(C.Tag.shared_back_button).performClick()

    waitForTag(TestTags.TITLE)
    assertBadge("1")
  }

  @Test
  fun backFromNotificationsReturnsHome() {
    openNotifications(FakeNotificationViewModel(loadedNotifications(testNotification(ID))))

    composeTestRule.onNodeWithTag(C.Tag.shared_back_button).performClick()

    waitForTag(TestTags.TITLE)
    composeTestRule.onAllNodesWithTag(C.Tag.notification_list).assertCountEquals(0)
  }

  @Test
  fun homeTabFromNotificationsReturnsHome() {
    openNotifications(FakeNotificationViewModel(loadedNotifications(testNotification(ID))))

    composeTestRule.onNodeWithTag(C.Tag.bottomTab("home")).performClick()

    waitForTag(TestTags.TITLE)
    composeTestRule.onAllNodesWithTag(C.Tag.notification_list).assertCountEquals(0)
  }

  @Test
  fun groupNotificationOpensItsWorkspaceAndBackReturnsToNotifications() {
    val viewModel =
        FakeNotificationViewModel(
            loadedNotifications(
                testNotification(ID, type = NotificationType.CAMPAIGN_UPDATE, groupId = GROUP_ID)
            )
        )
    openNotifications(viewModel)

    composeTestRule.onNodeWithTag(C.Tag.notificationItem(ID)).performClick()

    waitForText(GROUP_NAME)
    assertEquals(listOf(ID), viewModel.markedAsRead)
    // The workspace's back button has no test tag: it is found by its description.
    val context = ApplicationProvider.getApplicationContext<Context>()
    composeTestRule
        .onNodeWithContentDescription(context.getString(R.string.workspace_back_description))
        .performClick()
    waitForTag(C.Tag.notification_list)
  }

  @Test
  fun invitationOpensCampaignsAndBackReturnsToNotifications() {
    val viewModel =
        FakeNotificationViewModel(
            loadedNotifications(testNotification(ID, type = NotificationType.GROUP_INVITATION))
        )
    openNotifications(viewModel)

    composeTestRule.onNodeWithTag(C.Tag.notificationItem(ID)).performClick()

    waitForTag(GameManagementTestTags.CREATE_GROUP_BUTTON)
    assertEquals(listOf(ID), viewModel.markedAsRead)
    Espresso.pressBack()
    waitForTag(C.Tag.notification_list)
  }

  @Test
  fun notificationWithoutGroupStaysOnNotifications() {
    val viewModel =
        FakeNotificationViewModel(
            loadedNotifications(
                testNotification(ID, type = NotificationType.CAMPAIGN_UPDATE, groupId = null)
            )
        )
    openNotifications(viewModel)

    composeTestRule.onNodeWithTag(C.Tag.notificationItem(ID)).performClick()
    composeTestRule.waitForIdle()

    assertEquals(listOf(ID), viewModel.markedAsRead)
    composeTestRule.onNodeWithTag(C.Tag.notification_list).assertIsDisplayed()
  }

  /** Starts the app on Home, signed in as [USER], a member of the group "The Sunken Crown". */
  private fun showApp(notificationViewModel: FakeNotificationViewModel) {
    val context = ApplicationProvider.getApplicationContext<Context>()
    if (FirebaseApp.getApps(context).isEmpty()) {
      checkNotNull(FirebaseApp.initializeApp(context))
    }
    val authRepository = SignedInAuthRepository()
    val authViewModel = AuthViewModel(authRepository)
    val group =
        CampaignGroup(
            id = GROUP_ID,
            name = GROUP_NAME,
            gameMasterId = GAME_MASTER_ID,
            participantIds = listOf(GAME_MASTER_ID, USER.uid),
        )
    composeTestRule.setContent {
      JDRNexusApp(
          authViewModel = authViewModel,
          startDestination = AllRoutes.Home,
          authRepository = authRepository,
          groupRepository = NavigationGroupRepository(group),
          workspaceRepository = NavigationWorkspaceRepository(),
          notificationViewModel = notificationViewModel,
      )
    }
    waitForTag(TestTags.TITLE)
  }

  private fun openNotifications(viewModel: FakeNotificationViewModel) {
    showApp(viewModel)
    composeTestRule.onNodeWithTag(C.Tag.notification_bell).performClick()
    waitForTag(C.Tag.notification_list)
  }

  private fun assertBadge(text: String) {
    composeTestRule
        .onNodeWithTag(C.Tag.notification_unread_badge, useUnmergedTree = true)
        .onChild()
        .assertTextEquals(text)
  }

  private fun waitForTag(tag: String) {
    composeTestRule.waitUntil(TIMEOUT_MS) {
      composeTestRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    }
  }

  private fun waitForText(text: String) {
    composeTestRule.waitUntil(TIMEOUT_MS) {
      composeTestRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }
  }

  /** Auth repository whose user is signed in from the start; no test signs in or out. */
  private class SignedInAuthRepository : AuthRepository {
    override val authState: StateFlow<AuthUser?> = MutableStateFlow(USER)

    override suspend fun signUpWithEmail(
        email: String,
        password: String,
        username: String,
    ): AuthResult<AuthUser> = AuthResult.Failure(AuthError.UNKNOWN)

    override suspend fun signInWithEmail(email: String, password: String): AuthResult<AuthUser> =
        AuthResult.Failure(AuthError.UNKNOWN)

    override suspend fun signInWithGoogle(idToken: String): AuthResult<AuthUser> =
        AuthResult.Failure(AuthError.UNKNOWN)

    override suspend fun signOut(): AuthResult<Unit> = AuthResult.Failure(AuthError.UNKNOWN)
  }

  private companion object {
    const val ID = "notification-1"
    /** A second unread notification, for tests where the unread count changes. */
    const val OTHER_ID = "notification-2"
    const val GROUP_ID = "group-1"
    const val GROUP_NAME = "The Sunken Crown"
    const val GAME_MASTER_ID = "gm-1"
    val USER = AuthUser(uid = "user-1", email = "hero@example.com", username = "Ranger")

    /** Longest wait for a navigation to show its destination. */
    const val TIMEOUT_MS = 5_000L
  }
}
