// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.firestoreRules

import com.github.se.jdrnexus.utils.FirebaseEmulatorTestUtils
import com.github.se.jdrnexus.utils.FirebaseEmulatorTestUtils.assertPermissionDenied
import com.google.firebase.firestore.FirebaseFirestore
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Firestore rules for image files: `content` must be `images/<ownerId>/<docId>` and never changes.
 */
@RunWith(RobolectricTestRunner::class)
class ImageDocumentRulesTest {

  private val id = UUID.randomUUID().toString()
  private lateinit var owner: FirebaseFirestore
  private val image
    get() = owner.collection("documents").document(id)

  @Before
  fun setUp() {
    owner = FirebaseEmulatorTestUtils.signInAs(OWNER_ID)
  }

  @After fun tearDown() = runBlocking { FirebaseEmulatorTestUtils.tearDown(owner, "documents") }

  // A personal file at the root, as WorkspaceRepositoryFirestore.createFile writes it.
  private fun file(
      type: String = "IMAGE",
      content: String = "images/$OWNER_ID/$id",
      name: String = "Map",
  ) =
      mapOf(
          "id" to id,
          "name" to name,
          "ownerId" to OWNER_ID,
          "sharedGroupsIds" to emptyList<String>(),
          "placements" to emptyMap<String, Any>(),
          "personalParentId" to "",
          "type" to type,
          "content" to content,
      )

  private suspend fun stored() =
      FirebaseEmulatorTestUtils.readBypassingRules(owner, image.path, Map::class.java)

  @Test
  fun ownerCreatesImagePointingToItsOwnPicture() = runBlocking {
    image.set(file()).await()

    assertEquals(file(), stored())
  }

  @Test
  fun otherPathsAndUnknownTypesAreDenied() = runBlocking {
    assertPermissionDenied("another user's picture") {
      image.set(file(content = "images/other/$id")).await()
    }
    assertPermissionDenied("another file's picture") {
      image.set(file(content = "images/$OWNER_ID/other")).await()
    }
    assertPermissionDenied("VIDEO") { image.set(file(type = "VIDEO")).await() }

    assertEquals(null, stored())
  }

  @Test
  fun imageCanBeRenamedButItsPathNeverChanges() = runBlocking {
    FirebaseEmulatorTestUtils.writeBypassingRules(owner, image.path, file())

    assertPermissionDenied { image.update("content", "images/$OWNER_ID/other").await() }
    image.update("name", "Crypt").await()

    assertEquals(file(name = "Crypt"), stored())
  }

  private companion object {
    const val OWNER_ID = "image-owner"
  }
}
