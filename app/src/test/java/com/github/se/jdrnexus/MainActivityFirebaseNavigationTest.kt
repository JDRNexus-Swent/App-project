// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.github.se.jdrnexus

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.se.jdrnexus.model.personalSpace.GroupRepositoryFirestore
import com.github.se.jdrnexus.model.repository.AuthRepositoryFirebase
import com.github.se.jdrnexus.resources.C
import com.github.se.jdrnexus.ui.authentication.AuthViewModel
import com.github.se.jdrnexus.ui.gameManagement.GameManagementTestTags
import com.github.se.jdrnexus.ui.home.TestTags
import com.github.se.jdrnexus.utils.FirebaseEmulatorTestUtils
import com.github.se.jdrnexus.viewmodel.GameManagementViewModel
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityFirebaseNavigationTest {
  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun returningFromSignUpToSignInKeepsSignInAvailable() {
    val firebaseAuth = FirebaseEmulatorTestUtils.getAuth()

    composeTestRule.setContent { JDRNexusApp() }

    scrollToTag("sign_in_scroll_container", C.Tag.sign_in_sign_up)
    composeTestRule.onNodeWithTag(C.Tag.sign_in_sign_up).assertIsDisplayed().performClick()
    scrollToTag("sign_up_scroll_container", C.Tag.sign_up_sign_in)
    composeTestRule.onNodeWithTag(C.Tag.sign_up_sign_in).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.sign_up_sign_in).performClick()
    scrollToTag("sign_in_scroll_container", C.Tag.sign_in_sign_up)
    composeTestRule.onNodeWithTag(C.Tag.sign_in_sign_up).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.sign_up_sign_in).assertIsNotDisplayed()
    assertNull(firebaseAuth.currentUser)
  }

  @Test
  fun emulatorRegistrationLogoutAndSignInNavigateBetweenScreens() {
    val firebaseAuth = FirebaseEmulatorTestUtils.getAuth()

    val email = "player-${UUID.randomUUID()}@example.com"
    composeTestRule.setContent { JDRNexusApp() }

    scrollToTag("sign_in_scroll_container", C.Tag.sign_in_sign_up)
    composeTestRule.onNodeWithTag(C.Tag.sign_in_sign_up).performClick()
    composeTestRule.onNodeWithTag(C.Tag.sign_up_username).performTextInput("Ranger")
    composeTestRule.onNodeWithTag(C.Tag.sign_up_email).performTextInput(email)
    composeTestRule.onNodeWithTag(C.Tag.sign_up_password).performTextInput("secret1")
    scrollToTag("sign_up_scroll_container", C.Tag.sign_up_submit)
    composeTestRule.onNodeWithTag(C.Tag.sign_up_submit).performClick()

    waitForTag(TestTags.TITLE)
    composeTestRule.onNodeWithTag(TestTags.TITLE).assertIsDisplayed()
    assertEquals(email, firebaseAuth.currentUser?.email)
    assertEquals("Ranger", firebaseAuth.currentUser?.displayName)

    composeTestRule.onNodeWithTag(TestTags.LOGOUT).performClick()
    waitForTag(C.Tag.sign_in_sign_up)
    scrollToTag("sign_in_scroll_container", C.Tag.sign_in_sign_up)
    composeTestRule.onNodeWithTag(C.Tag.sign_in_sign_up).assertIsDisplayed()
    assertNull(firebaseAuth.currentUser)

    composeTestRule.onNodeWithTag(C.Tag.sign_in_email).performTextClearance()
    composeTestRule.onNodeWithTag(C.Tag.sign_in_email).performTextInput(email)
    composeTestRule.onNodeWithTag(C.Tag.sign_in_password).performTextClearance()
    composeTestRule.onNodeWithTag(C.Tag.sign_in_password).performTextInput("secret1")
    composeTestRule.onNodeWithTag(C.Tag.sign_in_submit).performClick()

    waitForTag(TestTags.TITLE)
    composeTestRule.onNodeWithTag(TestTags.TITLE).assertIsDisplayed()
    assertEquals(email, firebaseAuth.currentUser?.email)
  }

  @Test
  fun emulatorRegistrationNavigatesToGameManagementAndLogsOut() {
    val firebaseAuth = FirebaseEmulatorTestUtils.getAuth()

    val authRepository = AuthRepositoryFirebase(firebaseAuth)
    val authViewModel = AuthViewModel(authRepository)
    val gameManagementViewModel =
        GameManagementViewModel(
            GroupRepositoryFirestore(FirebaseEmulatorTestUtils.getFirestore()),
            authRepository,
        )
    val email = "campaign-${UUID.randomUUID()}@example.com"
    composeTestRule.setContent {
      JDRNexusApp(
          authViewModel = authViewModel,
          gameManagementViewModel = gameManagementViewModel,
      )
    }

    scrollToTag("sign_in_scroll_container", C.Tag.sign_in_sign_up)
    composeTestRule.onNodeWithTag(C.Tag.sign_in_sign_up).performClick()
    composeTestRule.onNodeWithTag(C.Tag.sign_up_username).performTextInput("Ranger")
    composeTestRule.onNodeWithTag(C.Tag.sign_up_email).performTextInput(email)
    composeTestRule.onNodeWithTag(C.Tag.sign_up_password).performTextInput("secret1")
    scrollToTag("sign_up_scroll_container", C.Tag.sign_up_submit)
    composeTestRule.onNodeWithTag(C.Tag.sign_up_submit).performClick()
    waitForTag(TestTags.TITLE)
    composeTestRule.onNodeWithTag(TestTags.GROUPMANAGEMENTBUTTON).performClick()
    waitForTag(GameManagementTestTags.CREATE_GROUP_BUTTON)
    composeTestRule.onNodeWithTag(GameManagementTestTags.CREATE_GROUP_BUTTON).assertIsDisplayed()

    composeTestRule.onNodeWithTag(C.Tag.bottomTab("home")).performClick()
    waitForTag(TestTags.LOGOUT)
    composeTestRule.onNodeWithTag(TestTags.LOGOUT).performClick()
    waitForTag(C.Tag.sign_in_sign_up)
    assertNull(firebaseAuth.currentUser)
  }

  private fun waitForTag(tag: String) {
    composeTestRule.waitUntil(5_000) {
      composeTestRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    }
  }

  private fun scrollToTag(containerTag: String, targetTag: String) {
    composeTestRule.onNodeWithTag(containerTag).performScrollToNode(hasTestTag(targetTag))
  }
}
