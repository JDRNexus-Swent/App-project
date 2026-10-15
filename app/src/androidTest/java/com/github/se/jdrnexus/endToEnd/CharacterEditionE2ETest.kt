package com.github.se.jdrnexus.endToEnd

import android.util.Log
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.se.jdrnexus.MainActivity
import com.github.se.jdrnexus.model.personalSpace.DocumentType
import com.github.se.jdrnexus.model.personalSpace.JDRFile
import com.github.se.jdrnexus.model.personalSpace.WorkspaceRepositoryProvider
import com.github.se.jdrnexus.resources.C
import com.github.se.jdrnexus.ui.edition.EditFileCharacterTestTags
import com.github.se.jdrnexus.ui.home.TestTags
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CharacterEditionE2ETest {

  @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()

  @After
  fun cleanUp() {
    runBlocking {
      val auth = FirebaseAuth.getInstance()
      val user = auth.currentUser ?: return@runBlocking
      try {
        withTimeout(15_000) {
          val repo = WorkspaceRepositoryProvider.repository

          val rootFiles = repo.getPersonalRootFiles(user.uid).first()
          rootFiles.forEach { file ->
            repo.deleteFile(file.id).onFailure {
              Log.w("E2E", "Could not delete file ${file.id}", it)
            }
          }

          user.delete().await()
        }
      } catch (e: Exception) {
        Log.w("E2E", "Cleanup failed for ${user.email}", e)
      } finally {
        auth.signOut()
      }
    }
  }

  companion object {
    private const val SEEDED_NAME = "E2E seeded hero"
    private const val TIMEOUT_MS = 40_000L
  }

  @Test
  fun new_user_can_sign_up_and_open_personal_space_add_menu() {
    signUpNewUser()
    openPersonalSpace()

    waitForText("Your personal space is empty.")
    composeRule.onNodeWithText("Your personal space is empty.").assertIsDisplayed()

    composeRule.onNodeWithContentDescription("Add item").performClick()
    composeRule.onNodeWithText("New folder").assertIsDisplayed()
    composeRule.onNodeWithText("New file").assertIsDisplayed()
  }

  @Test
  fun new_user_can_edit_a_prefilled_character_from_personal_space() {
    signUpNewUser()
    waitForTag(TestTags.TITLE)

    seedCharacter()

    composeRule.onNodeWithTag(TestTags.PSPACEBUTTON).performClick()
    waitForText("CHARACTER SHEET") // collect file from firestore
    composeRule.onNodeWithText("1 items").assertIsDisplayed()

    composeRule.onNodeWithText(SEEDED_NAME).performClick()
    waitForTag(EditFileCharacterTestTags.LIST)

    // name modification
    composeRule.onNodeWithTag("edit_character_name").performTextReplacement("E2E edited")

    composeRule
        .onNodeWithTag(EditFileCharacterTestTags.LIST)
        .performScrollToNode(hasTestTag("edit_character_save"))
    composeRule.onNodeWithTag("edit_character_save").performClick()

    waitForContentDescription("Add item")
    composeRule.onNodeWithTag(EditFileCharacterTestTags.LIST).assertDoesNotExist()
    composeRule.onNodeWithText("CHARACTER SHEET").assertIsDisplayed()
    composeRule.onNodeWithText("1 items").assertIsDisplayed()
  }

  private fun seedCharacter() {
    runBlocking {
      val user = FirebaseAuth.getInstance().currentUser!!
      val repo = WorkspaceRepositoryProvider.repository
      val file =
          JDRFile(
              id = repo.getNewUid(),
              name = SEEDED_NAME,
              ownerId = user.uid,
              personalParentId = "",
              type = DocumentType.CHARACTER,
              content =
                  """{"version":1,"type":"character","character":{"name":"$SEEDED_NAME","stats":[],"actions":[],"hp":{"current":10,"max":10}}}""",
          )
      repo.createFile(file).getOrThrow()
    }
  }

  private fun signUpNewUser() {
    val suffix = System.currentTimeMillis()
    waitForTag(C.Tag.sign_in_sign_up)
    composeRule.onNodeWithTag(C.Tag.sign_in_sign_up).performClick()

    waitForTag(C.Tag.sign_up_username)
    composeRule.onNodeWithTag(C.Tag.sign_up_username).performTextInput("e2e_user_$suffix")
    composeRule.onNodeWithTag(C.Tag.sign_up_email).performTextInput("e2e_$suffix@example.com")
    composeRule.onNodeWithTag(C.Tag.sign_up_password).performTextInput("Passw0rd123")
    composeRule.onNodeWithTag(C.Tag.sign_up_submit).performClick()
  }

  private fun openPersonalSpace() {
    waitForTag(TestTags.TITLE)
    composeRule.onNodeWithTag(TestTags.PSPACEBUTTON).performClick()
    waitForContentDescription("Add item")
  }

  private fun waitForTag(tag: String, timeout: Long = TIMEOUT_MS) =
    composeRule.waitUntil(timeout) {
      composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    }

  private fun waitForContentDescription(desc: String, timeout: Long = TIMEOUT_MS) =
    composeRule.waitUntil(timeout) {
      composeRule.onAllNodesWithContentDescription(desc).fetchSemanticsNodes().isNotEmpty()
    }

  private fun waitForText(text: String, timeout: Long = TIMEOUT_MS) =
    composeRule.waitUntil(timeout) {
      composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }
}
