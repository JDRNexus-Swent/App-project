package com.github.se.jdrnexus.endToEnd

import android.util.Log
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.se.jdrnexus.MainActivity
import com.github.se.jdrnexus.resources.C
import com.github.se.jdrnexus.ui.home.TestTags
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AuthFlowE2ETest {

  @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()

  companion object {
    @BeforeClass @JvmStatic fun configureFirebaseEmulators() = TestFirebaseEmulators.configure()
  }

  @After
  fun cleanUp() {
    runBlocking {
      val auth = FirebaseAuth.getInstance()
      val user = auth.currentUser ?: return@runBlocking
      try {
        withTimeout(5_000) { user.delete().await() }
      } catch (e: Exception) {
        Log.w("E2E", "Cleanup failed for ${user.email}", e)
      } finally {
        auth.signOut()
      }
    }
  }

  @Test
  fun user_can_sign_up_log_out_and_sign_in_again() {
    val suffix = System.currentTimeMillis()
    val username = "e2e_user_$suffix"
    val email = "e2e_$suffix@example.com"
    val password = "Passw0rd123"

    // 1. Sign Up
    waitForTag(C.Tag.sign_in_sign_up)
    composeRule.onNodeWithTag(C.Tag.sign_in_sign_up).performClick()
    waitForTag(C.Tag.sign_up_submit)

    composeRule.onNodeWithTag(C.Tag.sign_up_username).performTextInput(username)
    composeRule.onNodeWithTag(C.Tag.sign_up_email).performTextInput(email)
    composeRule.onNodeWithTag(C.Tag.sign_up_password).performTextInput(password)
    composeRule.onNodeWithTag(C.Tag.sign_up_submit).performClick()

    // 2. Go to home
    waitForTag(TestTags.TITLE)
    assertEquals(email, FirebaseAuth.getInstance().currentUser?.email)
    composeRule.onNodeWithTag(TestTags.TITLE).assertIsDisplayed()
    composeRule.onNodeWithTag(TestTags.PSPACEBUTTON).assertIsDisplayed()
    composeRule.onNodeWithTag(C.Tag.sign_up_submit).assertDoesNotExist()

    // 3. Log out
    composeRule.onNodeWithTag(TestTags.LOGOUT).performClick()
    waitForTag(C.Tag.sign_in_submit)
    composeRule.onNodeWithTag(TestTags.TITLE).assertDoesNotExist()

    // 4. Sign In with the new account
    composeRule.onNodeWithTag(C.Tag.sign_in_email).performTextReplacement(email)
    composeRule.onNodeWithTag(C.Tag.sign_in_password).performTextReplacement(password)
    composeRule.onNodeWithTag(C.Tag.sign_in_submit).performClick()

    waitForTag(TestTags.TITLE)
    assertEquals(email, FirebaseAuth.getInstance().currentUser?.email)
    composeRule.onNodeWithTag(TestTags.TITLE).assertIsDisplayed()
    composeRule.onNodeWithTag(C.Tag.sign_in_submit).assertDoesNotExist()
  }

  private fun waitForTag(tag: String, timeout: Long = 30_000) =
      composeRule.waitUntil(timeout) {
        composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
      }
}

internal object TestFirebaseEmulators {
  private var configured = false

  @Synchronized
  fun configure() {
    if (configured) return

    FirebaseAuth.getInstance().useEmulator("10.0.2.2", 9099)
    FirebaseFirestore.getInstance().useEmulator("10.0.2.2", 8080)
    configured = true
  }
}
