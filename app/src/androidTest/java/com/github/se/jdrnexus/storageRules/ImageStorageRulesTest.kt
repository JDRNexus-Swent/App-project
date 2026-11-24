// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.storageRules

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.se.jdrnexus.model.personalSpace.DocumentType
import com.github.se.jdrnexus.model.personalSpace.JDRFile
import com.github.se.jdrnexus.model.personalSpace.Placement
import com.github.se.jdrnexus.utils.StorageEmulatorTestUtils.awaitWithTimeout
import com.github.se.jdrnexus.utils.StorageEmulatorTestUtils.signedOutStorage
import com.github.se.jdrnexus.utils.StorageEmulatorTestUtils.storageAs
import com.github.se.jdrnexus.utils.StorageEmulatorTestUtils.storageErrorCode
import com.github.se.jdrnexus.utils.StorageEmulatorTestUtils.writeBypassingRules
import com.github.se.jdrnexus.utils.StorageEmulatorTestUtils.writeUserGroupIdsBypassingRules
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.StorageMetadata
import com.google.firebase.storage.StorageReference
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Security rules of `images/{ownerId}/{fileId}` in Firebase Storage (the pictures of image files),
 * on the emulators started with the project rules ("firebase emulators:exec --only
 * auth,firestore,storage"). Only the owner uploads (JPEG, PNG or WebP up to 10 MiB, never over an
 * existing picture) and deletes. Members of a group the image file is shared in read it, which the
 * rules check in the Firestore documents `documents/{fileId}` and `users/{uid}`. Instrumented
 * because the Storage SDK cannot read the emulator's responses under Robolectric (TESTING.md,
 * section 4.2). Every id is new for each test and the emulators drop their data at the end of the
 * run, so no cleanup is needed.
 */
@RunWith(AndroidJUnit4::class)
class ImageStorageRulesTest {

  companion object {
    private const val OWNER_ID = "image-rules-owner"
    private const val MEMBER_ID = "image-rules-member"
    private const val OUTSIDER_ID = "image-rules-outsider"
    private const val MAX_BYTES = 10 * 1024 * 1024
    // The rules check the declared type and the size, not the content.
    private val SMALL_PICTURE = ByteArray(100) { it.toByte() }
  }

  private val run = UUID.randomUUID().toString()
  private val groupId = "$run-group"
  private val otherGroupId = "$run-other-group"
  // A user who never had a users document.
  private val newcomerId = "$run-newcomer"
  private val fileId = "$run-image"

  @Before
  fun setUp() = runBlocking {
    writeUserGroupIdsBypassingRules(MEMBER_ID, groupId)
    writeUserGroupIdsBypassingRules(OUTSIDER_ID, otherGroupId)
  }

  // ============ Helpers ============

  private fun metadata(contentType: String) =
      StorageMetadata.Builder().setContentType(contentType).build()

  private fun picture(storage: FirebaseStorage, id: String = fileId, ownerId: String = OWNER_ID) =
      storage.reference.child("images/$ownerId/$id")

  private suspend fun StorageReference.upload(
      bytes: ByteArray = SMALL_PICTURE,
      contentType: String = "image/jpeg",
  ) = putBytes(bytes, metadata(contentType)).awaitWithTimeout()

  private suspend fun StorageReference.bytes(): ByteArray =
      getBytes(MAX_BYTES.toLong()).awaitWithTimeout()

  // Uploads the owner's picture of the image file.
  private suspend fun uploadOwnersPicture() = picture(storageAs(OWNER_ID)).upload()

  // The Firestore document of the image file, shared in [groupId] unless [sharedIn] is empty.
  private suspend fun writeImageDocument(
      ownerId: String = OWNER_ID,
      sharedIn: String? = groupId,
      hidden: Boolean = false,
  ) {
    val file =
        JDRFile(
            id = fileId,
            name = "Dungeon map",
            ownerId = ownerId,
            type = DocumentType.IMAGE,
            content = "images/$ownerId/$fileId",
        )
    val shared = sharedIn?.let { file.withPlacement(it, Placement(it, hidden)) } ?: file
    writeBypassingRules("documents/$fileId", shared.toMap())
  }

  // Fails unless every request of [cases] is refused by the rules.
  private suspend fun assertAllDenied(vararg cases: Pair<String, suspend () -> Any?>) {
    cases.forEach { (case, request) ->
      assertEquals(case, StorageException.ERROR_NOT_AUTHORIZED, storageErrorCode(request))
    }
  }

  private suspend fun assertNotStored(id: String) =
      assertEquals(
          StorageException.ERROR_OBJECT_NOT_FOUND,
          storageErrorCode { picture(storageAs(OWNER_ID), id).bytes() },
      )

  // ============ create ============

  @Test
  fun ownerUploadsJpegPngAndWebpUpTo10MiB() = runBlocking {
    val owner = storageAs(OWNER_ID)
    val maxSize = ByteArray(MAX_BYTES)

    picture(owner, "$run-jpeg").upload(contentType = "image/jpeg")
    picture(owner, "$run-png").upload(contentType = "image/png")
    picture(owner, "$run-webp").upload(contentType = "image/webp")
    picture(owner, "$run-max").upload(bytes = maxSize)

    assertArrayEquals(SMALL_PICTURE, picture(owner, "$run-jpeg").bytes())
    assertArrayEquals(SMALL_PICTURE, picture(owner, "$run-png").bytes())
    assertArrayEquals(SMALL_PICTURE, picture(owner, "$run-webp").bytes())
    assertEquals(MAX_BYTES, picture(owner, "$run-max").bytes().size)
  }

  @Test
  fun uploadsBreakingTheRulesAreDenied() = runBlocking {
    val owner = storageAs(OWNER_ID)

    assertAllDenied(
        "signed out" to suspend { picture(signedOutStorage(), "$run-signed-out").upload() },
        "another user's path" to
            suspend { picture(storageAs(MEMBER_ID), "$run-other-path").upload() },
        "GIF" to suspend { picture(owner, "$run-gif").upload(contentType = "image/gif") },
        "10 MiB + 1 byte" to
            suspend { picture(owner, "$run-too-large").upload(bytes = ByteArray(MAX_BYTES + 1)) },
    )

    listOf("$run-signed-out", "$run-other-path", "$run-gif", "$run-too-large").forEach {
      assertNotStored(it)
    }
  }

  @Test
  fun storedPictureCannotBeOverwrittenOrHaveItsMetadataChanged() = runBlocking {
    uploadOwnersPicture()
    val owner = storageAs(OWNER_ID)

    assertAllDenied(
        "overwrite" to suspend { picture(owner).upload(bytes = ByteArray(10)) },
        "metadata update" to
            suspend { picture(owner).updateMetadata(metadata("image/png")).awaitWithTimeout() },
    )

    assertArrayEquals(SMALL_PICTURE, picture(owner).bytes())
    assertEquals("image/jpeg", picture(owner).metadata.awaitWithTimeout().contentType)
  }

  // ============ read ============

  @Test
  fun ownerReadsOwnPictureWithoutAnyFirestoreDocument() = runBlocking {
    uploadOwnersPicture()

    assertArrayEquals(SMALL_PICTURE, picture(storageAs(OWNER_ID)).bytes())
  }

  @Test
  fun memberOfASharingGroupReadsPicture() = runBlocking {
    uploadOwnersPicture()
    writeImageDocument()

    assertArrayEquals(SMALL_PICTURE, picture(storageAs(MEMBER_ID)).bytes())
  }

  @Test
  fun memberReadsPictureHiddenInTheGroup() = runBlocking {
    uploadOwnersPicture()
    writeImageDocument(hidden = true)

    assertArrayEquals(SMALL_PICTURE, picture(storageAs(MEMBER_ID)).bytes())
  }

  @Test
  fun readersOutsideTheSharingGroupsAreDenied() = runBlocking {
    uploadOwnersPicture()
    writeImageDocument()

    assertAllDenied(
        "signed out" to suspend { picture(signedOutStorage()).bytes() },
        "member of another group" to suspend { picture(storageAs(OUTSIDER_ID)).bytes() },
        "user without a users document" to suspend { picture(storageAs(newcomerId)).bytes() },
    )
  }

  @Test
  fun memberIsDeniedWhenTheImageIsNotShared() = runBlocking {
    uploadOwnersPicture()
    val member = storageAs(MEMBER_ID)

    val withoutDocument = storageErrorCode { picture(member).bytes() }
    writeImageDocument(sharedIn = null)
    val unshared = storageErrorCode { picture(member).bytes() }

    assertEquals(StorageException.ERROR_NOT_AUTHORIZED, withoutDocument)
    assertEquals(StorageException.ERROR_NOT_AUTHORIZED, unshared)
  }

  @Test
  fun documentOwnedBySomeoneElseDoesNotOpenThePicture() = runBlocking {
    uploadOwnersPicture()
    // A document with the picture's id, shared in the member's group, but owned by the member.
    writeImageDocument(ownerId = MEMBER_ID)

    val error = storageErrorCode { picture(storageAs(MEMBER_ID)).bytes() }

    assertEquals(StorageException.ERROR_NOT_AUTHORIZED, error)
  }

  // ============ delete ============

  @Test
  fun ownerDeletesOwnPicture() = runBlocking {
    uploadOwnersPicture()

    picture(storageAs(OWNER_ID)).delete().awaitWithTimeout()

    assertNotStored(fileId)
  }

  @Test
  fun memberCannotDeleteSharedPicture() = runBlocking {
    uploadOwnersPicture()
    writeImageDocument()

    val error = storageErrorCode { picture(storageAs(MEMBER_ID)).delete().awaitWithTimeout() }

    assertEquals(StorageException.ERROR_NOT_AUTHORIZED, error)
    assertArrayEquals(SMALL_PICTURE, picture(storageAs(OWNER_ID)).bytes())
  }
}
