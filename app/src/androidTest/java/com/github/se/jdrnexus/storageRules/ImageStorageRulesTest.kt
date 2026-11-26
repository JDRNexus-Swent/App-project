// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.storageRules

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.se.jdrnexus.utils.StorageEmulatorTestUtils.MAX_IMAGE_BYTES
import com.github.se.jdrnexus.utils.StorageEmulatorTestUtils.awaitWithTimeout
import com.github.se.jdrnexus.utils.StorageEmulatorTestUtils.deleteBypassingRules
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
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Storage rules of `images/{ownerId}/{fileId}` on the emulators. Instrumented because Storage
 * cannot run under Robolectric (TESTING.md 4.2).
 */
@RunWith(AndroidJUnit4::class)
class ImageStorageRulesTest {

  private val run = UUID.randomUUID().toString()
  private val owner = "image-rules-owner"
  private val member = "image-rules-member"
  private val outsider = "image-rules-outsider"
  private val groupId = "$run-group"
  private val fileId = "$run-image"
  private val bytes = ByteArray(100) { it.toByte() }

  // Every picture stored by this test, deleted by its uploader in tearDown.
  private val uploaded = mutableListOf<StorageReference>()

  @Before
  fun setUp() = runBlocking {
    writeUserGroupIdsBypassingRules(member, groupId)
    writeUserGroupIdsBypassingRules(outsider, "$run-other-group")
  }

  // Removes what the test wrote. A failed picture delete (one the test already deleted) is ignored.
  @After
  fun tearDown() = runBlocking {
    uploaded.forEach { runCatching { it.delete().awaitWithTimeout() } }
    deleteBypassingRules("documents/$fileId")
    listOf(member, outsider).forEach { deleteBypassingRules("users/$it") }
  }

  private fun picture(storage: FirebaseStorage, id: String = fileId, ownerId: String = owner) =
      storage.reference.child("images/$ownerId/$id")

  private suspend fun StorageReference.upload(
      data: ByteArray = bytes,
      type: String = "image/jpeg",
  ) {
    putBytes(data, StorageMetadata.Builder().setContentType(type).build()).awaitWithTimeout()
    uploaded += this
  }

  private suspend fun StorageReference.read(): ByteArray =
      getBytes(MAX_IMAGE_BYTES + 1L).awaitWithTimeout()

  // The image file's record, as the Storage rules read it.
  private suspend fun writeRecord(
      ownerId: String = owner,
      sharedIn: List<String> = listOf(groupId),
      type: String = "IMAGE",
  ) =
      writeBypassingRules(
          "documents/$fileId",
          mapOf("ownerId" to ownerId, "sharedGroupsIds" to sharedIn, "type" to type),
      )

  private suspend fun assertDenied(case: String, request: suspend () -> Any?) =
      assertEquals(case, StorageException.ERROR_NOT_AUTHORIZED, storageErrorCode(request))

  @Test
  fun ownerUploadsAndReadsJpegPngAndWebpUpTo10MiB() = runBlocking {
    val storage = storageAs(owner)

    listOf("image/jpeg", "image/png", "image/webp").forEach { type ->
      val id = "$run-${type.substringAfter('/')}"
      picture(storage, id).upload(type = type)
      assertArrayEquals(type, bytes, picture(storage, id).read())
    }
    picture(storage).upload(ByteArray(MAX_IMAGE_BYTES))
    assertEquals(MAX_IMAGE_BYTES, picture(storage).read().size)
  }

  @Test
  fun uploadsBreakingTheRulesAreDenied() = runBlocking {
    val storage = storageAs(owner)
    picture(storage).upload()

    assertDenied("signed out") { picture(signedOutStorage(), "$run-1").upload() }
    assertDenied("another user's path") { picture(storageAs(member), "$run-2").upload() }
    assertDenied("GIF") { picture(storage, "$run-3").upload(type = "image/gif") }
    assertDenied("10 MiB + 1 byte") {
      picture(storage, "$run-4").upload(ByteArray(MAX_IMAGE_BYTES + 1))
    }
    assertDenied("overwrite") { picture(storage).upload(ByteArray(10)) }
    assertDenied("metadata update") {
      picture(storage)
          .updateMetadata(StorageMetadata.Builder().setContentType("image/png").build())
          .awaitWithTimeout()
    }
    assertArrayEquals(bytes, picture(storage).read())
  }

  @Test
  fun memberOfASharingGroupReadsThePicture() = runBlocking {
    picture(storageAs(owner)).upload()
    writeRecord()

    assertArrayEquals(bytes, picture(storageAs(member)).read())
  }

  @Test
  fun readersOutsideTheSharingGroupsAreDenied() = runBlocking {
    picture(storageAs(owner)).upload()
    assertDenied("no record") { picture(storageAs(member)).read() }

    writeRecord()
    assertDenied("signed out") { picture(signedOutStorage()).read() }
    assertDenied("member of another group") { picture(storageAs(outsider)).read() }
    assertDenied("user without a users document") { picture(storageAs("$run-newcomer")).read() }

    writeRecord(sharedIn = emptyList())
    assertDenied("record not shared") { picture(storageAs(member)).read() }

    writeRecord(type = "TEXT")
    assertDenied("record of another type") { picture(storageAs(member)).read() }

    // A record with the picture's id but another owner must not open the picture.
    writeRecord(ownerId = member)
    assertDenied("record of another owner") { picture(storageAs(member)).read() }
  }

  @Test
  fun onlyTheOwnerDeletes() = runBlocking {
    picture(storageAs(owner)).upload()
    writeRecord()

    assertDenied("member") { picture(storageAs(member)).delete().awaitWithTimeout() }
    picture(storageAs(owner)).delete().awaitWithTimeout()

    assertEquals(
        StorageException.ERROR_OBJECT_NOT_FOUND,
        storageErrorCode { picture(storageAs(owner)).read() },
    )
  }
}
