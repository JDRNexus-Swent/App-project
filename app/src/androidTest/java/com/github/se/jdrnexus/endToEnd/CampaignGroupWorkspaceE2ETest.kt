// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.github.se.jdrnexus.endToEnd

import android.util.Log
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.se.jdrnexus.MainActivity
import com.github.se.jdrnexus.model.personalSpace.DocumentType
import com.github.se.jdrnexus.model.personalSpace.GroupRepositoryFirestore
import com.github.se.jdrnexus.model.personalSpace.JDRFile
import com.github.se.jdrnexus.model.personalSpace.WorkspaceRepositoryProvider
import com.github.se.jdrnexus.resources.C
import com.github.se.jdrnexus.ui.gameManagement.GameManagementTestTags
import com.github.se.jdrnexus.ui.home.TestTags
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.UUID
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CampaignGroupWorkspaceE2ETest {

  companion object {
    private const val PASSWORD = "Passw0rd123"
    private const val TIMEOUT_MS = 40_000L

    @BeforeClass @JvmStatic fun configureFirebaseEmulators() = TestFirebaseEmulators.configure()
  }

  @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()

  @Before
  fun setUp() {
    FirebaseAuth.getInstance().signOut()
  }

  @After
  fun cleanUp() {
    runBlocking {
      val auth = FirebaseAuth.getInstance()
      val user = auth.currentUser ?: return@runBlocking
      val firestore = FirebaseFirestore.getInstance()
      val cleanupFailures = mutableListOf<Throwable>()
      try {
        val groupIds =
            try {
              withTimeout(5_000) {
                firestore
                    .collection("campaign_groups")
                    .whereEqualTo("gameMasterId", user.uid)
                    .get()
                    .await()
                    .documents
                    .map { it.id }
              }
            } catch (cancellation: CancellationException) {
              throw cancellation
            } catch (failure: Exception) {
              cleanupFailures += failure
              Log.e("E2E", "Failed to find campaign groups for ${user.email}", failure)
              emptyList()
            }

        val groupRepository = GroupRepositoryFirestore(firestore)
        // Attempt every group deletion with its own timeout.
        groupIds.forEach { groupId ->
          try {
            withTimeout(5_000) { groupRepository.deleteGroup(groupId).getOrThrow() }
          } catch (cancellation: CancellationException) {
            throw cancellation
          } catch (failure: Exception) {
            cleanupFailures += failure
            Log.e("E2E", "Failed to delete campaign group $groupId", failure)
          }
        }

        // Clear user documents even when campaign-group cleanup had a failure.
        try {
          withTimeout(5_000) { clearUserDocuments(firestore, user.uid) }
        } catch (cancellation: CancellationException) {
          throw cancellation
        } catch (failure: Exception) {
          cleanupFailures += failure
          Log.e("E2E", "Failed to clear documents for ${user.email}", failure)
        }

        // Always attempt to delete the emulator account after data cleanup.
        try {
          withTimeout(5_000) { user.delete().await() }
        } catch (cancellation: CancellationException) {
          throw cancellation
        } catch (failure: Exception) {
          cleanupFailures += failure
          Log.e("E2E", "Failed to delete test account ${user.email}", failure)
        }
      } catch (cancellation: CancellationException) {
        throw cancellation
      } catch (failure: Exception) {
        cleanupFailures += failure
        Log.e("E2E", "Cleanup failed for ${user.email}", failure)
      } finally {
        auth.signOut()
      }
      if (cleanupFailures.isNotEmpty()) {
        throw AssertionError("E2E cleanup failed", cleanupFailures.first())
      }
    }
  }

  @Test
  fun userCanCreateCampaignGroupOpenWorkspaceAndAddFile() {
    val suffix = UUID.randomUUID().toString()
    val email = "campaign_group_e2e_$suffix@example.com"
    val username = "campaign_group_e2e_$suffix"
    val groupName = "E2E campaign $suffix"
    val fileName = "E2E notes $suffix"

    // Register a unique emulator account as setup for the required sign-in flow.
    signUpNewUser(username, email)
    waitForTag(TestTags.TITLE)
    composeRule.onNodeWithTag(TestTags.LOGOUT).performClick()
    waitForTag(C.Tag.sign_in_submit)

    // Verify that the user can sign in through the existing authentication UI.
    composeRule.onNodeWithTag(C.Tag.sign_in_email).performTextReplacement(email)
    composeRule.onNodeWithTag(C.Tag.sign_in_password).performTextReplacement(PASSWORD)
    composeRule
        .onNodeWithTag("sign_in_scroll_container")
        .performScrollToNode(hasTestTag(C.Tag.sign_in_submit))
    composeRule.onNodeWithTag(C.Tag.sign_in_submit).performClick()
    waitForTag(TestTags.TITLE)
    assertEquals(email, FirebaseAuth.getInstance().currentUser?.email)
    composeRule.onNodeWithTag(TestTags.TITLE).assertIsDisplayed()

    // Seed one isolated personal file for the workspace add-file UI to offer.
    val fileId = seedPersonalFile(fileName)

    // Verify that the user can create a campaign group from the application UI.
    composeRule.onNodeWithTag(TestTags.GROUPMANAGEMENTBUTTON).performClick()
    waitForTag(GameManagementTestTags.CREATE_GROUP_BUTTON)
    composeRule.onNodeWithTag(GameManagementTestTags.CREATE_GROUP_BUTTON).performClick()
    composeRule.onNodeWithTag(GameManagementTestTags.CREATE_GROUP_DIALOG).assertIsDisplayed()
    composeRule.onNodeWithTag(GameManagementTestTags.GROUP_NAME_FIELD).performTextInput(groupName)
    composeRule.onNodeWithTag(GameManagementTestTags.CREATE_GROUP_CONFIRM).performClick()

    waitForText(groupName)
    composeRule.onNodeWithText(groupName).assertIsDisplayed()
    val groupId = findCreatedGroupId(groupName)
    runBlocking {
      assertEquals(
          groupName,
          FirebaseFirestore.getInstance()
              .collection("campaign_groups")
              .document(groupId)
              .get()
              .await()
              .getString("name"),
      )
    }

    // Verify that the newly created campaign group opens its workspace.
    composeRule.onNodeWithTag(C.Tag.enter_workspace_button).performClick()
    waitForTag(C.Tag.workspace_actions_fab)
    composeRule.onNodeWithTag(C.Tag.workspace_actions_fab).assertIsDisplayed()

    // Verify that the user can add the personal file through the workspace UI.
    composeRule.onNodeWithTag(C.Tag.workspace_actions_fab).performClick()
    composeRule.onNodeWithTag(C.Tag.workspace_add_file).performClick()
    composeRule.onNodeWithTag(C.Tag.workspace_add_files_dialog).assertIsDisplayed()
    composeRule.onNodeWithText(fileName).assertIsDisplayed().performClick()

    waitForTag(C.Tag.workspaceItem(fileId))
    composeRule.onNodeWithTag(C.Tag.workspaceItem(fileId)).assertIsDisplayed()
    // Verify that the visible workspace file has the expected persisted Firestore metadata.
    runBlocking {
      val storedFile =
          FirebaseFirestore.getInstance().collection("documents").document(fileId).get().await()
      assertTrue(storedFile.exists())
      assertEquals(fileName, storedFile.getString("name"))
      assertEquals(
          requireNotNull(FirebaseAuth.getInstance().currentUser).uid,
          storedFile.getString("ownerId"),
      )
      assertEquals(listOf(groupId), storedFile.get("sharedGroupsIds"))
      val placement = storedFile.get("placements") as? Map<*, *>
      assertEquals(
          groupId,
          (placement?.get(groupId) as? Map<*, *>)?.get("folderId"),
      )
    }
  }

  private suspend fun clearUserDocuments(firestore: FirebaseFirestore, userId: String) {
    val snapshot = firestore.collection("documents").whereEqualTo("ownerId", userId).get().await()
    snapshot.documents.chunked(500).forEach { chunk ->
      val batch = firestore.batch()
      chunk.forEach { batch.delete(it.reference) }
      batch.commit().await()
    }
  }

  private fun signUpNewUser(username: String, email: String) {
    waitForTag(C.Tag.sign_in_sign_up)
    composeRule
        .onNodeWithTag("sign_in_scroll_container")
        .performScrollToNode(hasTestTag(C.Tag.sign_in_sign_up))
    composeRule.onNodeWithTag(C.Tag.sign_in_sign_up).performClick()
    waitForTag(C.Tag.sign_up_username)
    composeRule.onNodeWithTag(C.Tag.sign_up_username).performTextInput(username)
    composeRule.onNodeWithTag(C.Tag.sign_up_email).performTextInput(email)
    composeRule.onNodeWithTag(C.Tag.sign_up_password).performTextInput(PASSWORD)
    composeRule
        .onNodeWithTag("sign_up_scroll_container")
        .performScrollToNode(hasTestTag(C.Tag.sign_up_submit))
    composeRule.onNodeWithTag(C.Tag.sign_up_submit).performClick()
  }

  private fun seedPersonalFile(fileName: String): String {
    val userId = requireNotNull(FirebaseAuth.getInstance().currentUser).uid
    val fileId = WorkspaceRepositoryProvider.repository.getNewUid()
    runBlocking {
      WorkspaceRepositoryProvider.repository
          .createFile(
              JDRFile(
                  id = fileId,
                  name = fileName,
                  ownerId = userId,
                  type = DocumentType.TEXT,
                  content = "Created for campaign workspace E2E testing.",
              )
          )
          .getOrThrow()
    }
    return fileId
  }

  private fun findCreatedGroupId(groupName: String): String {
    return runBlocking {
      FirebaseFirestore.getInstance()
          .collection("campaign_groups")
          .whereEqualTo("name", groupName)
          .whereEqualTo("gameMasterId", requireNotNull(FirebaseAuth.getInstance().currentUser).uid)
          .get()
          .await()
          .documents
          .single()
          .id
    }
  }

  private fun waitForTag(tag: String, timeout: Long = TIMEOUT_MS) =
      composeRule.waitUntil(timeout) {
        composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
      }

  private fun waitForText(text: String, timeout: Long = TIMEOUT_MS) =
      composeRule.waitUntil(timeout) {
        composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
      }
}
