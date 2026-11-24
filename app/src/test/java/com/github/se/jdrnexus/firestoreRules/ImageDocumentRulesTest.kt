// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.firestoreRules

import com.github.se.jdrnexus.utils.FirebaseEmulatorTestUtils
import com.google.firebase.firestore.DocumentReference
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
 * Security rules of `documents/{docId}` for image files (type `IMAGE`), on the Firestore emulator
 * started with the project rules ("firebase emulators:exec --only auth,firestore"). An image
 * document keeps the path of its picture in Firebase Storage in `content`, always
 * `images/<ownerId>/<docId>`: the rules accept no other path and never let it change, so the
 * Storage rules and the app can trust it.
 */
@RunWith(RobolectricTestRunner::class)
class ImageDocumentRulesTest {

  companion object {
    private const val OWNER_ID = "image-owner"
    private const val OTHER_USER_ID = "image-other"
    private const val NAME = "Dungeon map"
  }

  private val imageId = UUID.randomUUID().toString()
  private val picturePath = "images/$OWNER_ID/$imageId"

  // Signed in as the owner; also used to seed and check data bypassing the rules.
  private lateinit var owner: FirebaseFirestore

  @Before
  fun setUp() {
    owner = FirebaseEmulatorTestUtils.signInAs(OWNER_ID)
  }

  @After fun tearDown() = runBlocking { FirebaseEmulatorTestUtils.tearDown(owner, "documents") }

  // ============ Helpers ============

  private fun image(): DocumentReference = owner.collection("documents").document(imageId)

  // A personal file at the root, written the way WorkspaceRepositoryFirestore.createFile does.
  private fun file(type: String = "IMAGE", content: String = picturePath, name: String = NAME) =
      mapOf(
          "id" to imageId,
          "name" to name,
          "ownerId" to OWNER_ID,
          "sharedGroupsIds" to emptyList<String>(),
          "placements" to emptyMap<String, Any>(),
          "personalParentId" to "",
          "type" to type,
          "content" to content,
      )

  // Read bypassing the rules, so the check does not depend on them; null if missing.
  private suspend fun stored(): Map<*, *>? =
      FirebaseEmulatorTestUtils.readBypassingRules(owner, image().path, Map::class.java)

  private suspend fun seedImage() =
      FirebaseEmulatorTestUtils.writeBypassingRules(owner, image().path, file())

  // ============ create ============

  @Test
  fun ownerCreatesImageDocumentPointingToItsOwnPicture() = runBlocking {
    image().set(file()).await()

    assertEquals(file(), stored())
  }

  @Test
  fun imageDocumentPointingAnywhereElseIsDenied() = runBlocking {
    val otherPaths =
        mapOf(
            "another user's picture" to "images/$OTHER_USER_ID/$imageId",
            "another file's picture" to "images/$OWNER_ID/other-image",
            "no picture" to "",
        )

    otherPaths.forEach { (case, path) ->
      FirebaseEmulatorTestUtils.assertPermissionDenied(case) {
        image().set(file(content = path)).await()
      }
    }

    assertEquals(null, stored())
  }

  @Test
  fun unknownFileTypeIsDenied() = runBlocking {
    FirebaseEmulatorTestUtils.assertPermissionDenied { image().set(file(type = "VIDEO")).await() }

    assertEquals(null, stored())
  }

  // ============ update ============

  @Test
  fun ownerRenamesImage() = runBlocking {
    seedImage()

    image().update("name", "Crypt map").await()

    assertEquals(file(name = "Crypt map"), stored())
  }

  @Test
  fun picturePathOfImageCannotChange() = runBlocking {
    seedImage()

    FirebaseEmulatorTestUtils.assertPermissionDenied {
      image().update("content", "images/$OWNER_ID/other-image").await()
    }

    assertEquals(file(), stored())
  }
}
