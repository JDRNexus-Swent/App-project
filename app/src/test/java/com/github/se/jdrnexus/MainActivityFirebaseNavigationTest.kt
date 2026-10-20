// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.github.se.jdrnexus

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.se.jdrnexus.resources.C
import com.github.se.jdrnexus.ui.home.TestTags
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
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
    val context = ApplicationProvider.getApplicationContext<Context>()
    val firebaseApp =
        FirebaseApp.getApps(context).firstOrNull()
            ?: FirebaseApp.initializeApp(context)
            ?: error("FirebaseApp could not be initialized for the Auth emulator test.")
    val firebaseAuth = FirebaseAuth.getInstance(firebaseApp)
    firebaseAuth.useEmulator("127.0.0.1", 9099)
    firebaseAuth.signOut()

    composeTestRule.setContent { JDRNexusApp() }

    composeTestRule.onNodeWithTag(C.Tag.sign_in_sign_up).performClick()
    composeTestRule.onNodeWithTag(C.Tag.sign_up_sign_in).performClick()
    composeTestRule.onNodeWithTag(C.Tag.sign_in_sign_up).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.sign_up_sign_in).assertIsNotDisplayed()
    assertNull(firebaseAuth.currentUser)
  }

  @Test
  fun emulatorRegistrationLogoutAndSignInNavigateBetweenScreens() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val firebaseApp =
        FirebaseApp.getApps(context).firstOrNull()
            ?: FirebaseApp.initializeApp(context)
            ?: error("FirebaseApp could not be initialized for the Auth emulator test.")
    val firebaseAuth = FirebaseAuth.getInstance(firebaseApp)
    firebaseAuth.useEmulator("127.0.0.1", 9099)
    firebaseAuth.signOut()

    val email = "player-${UUID.randomUUID()}@example.com"
    composeTestRule.setContent { JDRNexusApp() }

    composeTestRule.onNodeWithTag(C.Tag.sign_in_sign_up).performClick()
    composeTestRule.onNodeWithTag(C.Tag.sign_up_username).performTextInput("Ranger")
    composeTestRule.onNodeWithTag(C.Tag.sign_up_email).performTextInput(email)
    composeTestRule.onNodeWithTag(C.Tag.sign_up_password).performTextInput("secret1")
    composeTestRule.onNodeWithTag(C.Tag.sign_up_submit).performClick()

    waitForTag(TestTags.TITLE)
    composeTestRule.onNodeWithTag(TestTags.TITLE).assertIsDisplayed()
    assertEquals(email, firebaseAuth.currentUser?.email)
    assertEquals("Ranger", firebaseAuth.currentUser?.displayName)

    composeTestRule.onNodeWithTag(TestTags.LOGOUT).performClick()
    waitForTag(C.Tag.sign_in_sign_up)
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

  private fun waitForTag(tag: String) {
    composeTestRule.waitUntil(5_000) {
      composeTestRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    }
  }
}
