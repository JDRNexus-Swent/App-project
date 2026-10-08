// Co-authored-by: AI Agent
package com.github.se.jdrnexus.model.personalSpace

import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.junit.Assert.assertNotEquals

/** Those test use firebase emulator, make sure to activate it with "firebase emulators:start" */
@RunWith(RobolectricTestRunner::class)
class WorkspaceRepositoryFirestoreTest {

  private lateinit var repository: WorkspaceRepository
  private lateinit var firestore: FirebaseFirestore

  @Before
  fun setUp() {
    if (FirebaseApp.getApps(ApplicationProvider.getApplicationContext()).isEmpty()) {
      FirebaseApp.initializeApp(ApplicationProvider.getApplicationContext())
    }
    firestore = FirebaseFirestore.getInstance()
    try {
      firestore.useEmulator("127.0.0.1", 8080)
    } catch (e: IllegalStateException) {
      // If Firestore is already configured for production, its host won't be local.
      // We explicitly throw an exception here to avoid wiping out the production database
      // if someone runs tests without properly configuring the emulator instance first.
      if (
          !firestore.firestoreSettings.host.contains("127.0.0.1") &&
              !firestore.firestoreSettings.host.contains("10.0.2.2")
      ) {
        throw IllegalStateException(
            "Firestore is not using the emulator! Aborting to prevent data loss.",
            e,
        )
      }
    }
    repository = WorkspaceRepositoryFirestore(firestore)
  }

  @After
  fun tearDown() {
    runBlocking {
      // Re-enable network in case it was disabled during tests
      try {
        firestore.enableNetwork().await()
      } catch (e: Exception) {
        // ignore
      }

      // Clean up the database for the next test to ensure a clean state
      try {
        val snapshot = firestore.collection("documents").get().await()
        for (doc in snapshot.documents) {
          doc.reference.delete().await()
        }
      } catch (e: Exception) {
        // ignore
      }
    }
  }

  /**
   * Helper function to collect a Flow in a background coroutine while actively pumping the
   * Robolectric MainLooper. This is required because Firebase SDK callbacks (including snapshot
   * listeners) dispatch to the Android Main thread, which gets blocked in typical runBlocking
   * tests.
   *
   * It uses thread-safe Atomic properties to guarantee memory visibility across Dispatchers.
   */
  private suspend fun <T> Flow<T>.waitFor(
      timeoutMs: Long = 5000,
      condition: (T) -> Boolean,
  ): T {
    // use of atomic value alows the real storage in memory (not in cache)
    val latestValue = AtomicReference<T?>(null)
    val hasValue = AtomicBoolean(false)
    val isReady = AtomicBoolean(false)
    val job =
        CoroutineScope(Dispatchers.Default).launch {
          this@waitFor.collect {
            latestValue.set(it)
            hasValue.set(true)
            if (condition(it)) {
              isReady.set(true)
            }
          }
        }

    val startTime = System.currentTimeMillis()
    while (System.currentTimeMillis() - startTime < timeoutMs) {
      // Pump Robolectric's main looper so Firebase callbacks can run!
      shadowOf(Looper.getMainLooper()).idle()
      if (isReady.get()) {
        job.cancel()
        @Suppress("UNCHECKED_CAST")
        return latestValue.get() as T
      }
      delay(50)
    }
    job.cancel()
    throw AssertionError(
        "Timeout waiting for condition. Last value emitted: ${latestValue.get()}, emitted at all: ${hasValue.get()}"
    )
  }
  /**
   * Tests the creation of a new uid and verifies that it not empty and unique
   */
  @Test
  fun testGetNewUid() {
    val uid1 = repository.getNewUid()
    val uid2 = repository.getNewUid()

    assertTrue("Generated UID should not be empty", uid1.isNotEmpty())
    assertNotEquals("Generated UIDs should be unique", uid1, uid2)
  }

  /**
   * Tests the creation of a new document and verifies that it can be retrieved successfully with
   * all its data intact.
   */
  @Test
  fun testCreateAndGetFile() {
    runBlocking {
      val fileId = UUID.randomUUID().toString()
      val file =
          JDRFile(
              id = fileId,
              name = "Test File",
              ownerId = "owner1",
              type = DocumentType.TEXT,
              content = "Hello World",
          )

      val createResult = repository.createFile(file)
      assertTrue("createFile should succeed", createResult.isSuccess)

      val getResult = repository.getFile(fileId)
      assertTrue("getFile should succeed", getResult.isSuccess)
      assertEquals("Fetched file should match created file", file, getResult.getOrNull())
    }
  }

  /**
   * Verifies that creating a file with invalid arguments (e.g. empty ID) is correctly caught and
   * returned as a failure, avoiding a hard crash.
   */
  @Test
  fun testCreateFile_Exception() {
    runBlocking {
      // Empty ID throws IllegalArgumentException in Firebase
      val invalidFile = JDRFile(id = "", name = "Invalid")
      val result = repository.createFile(invalidFile)
      assertTrue("createFile should fail with empty ID", result.isFailure)
    }
  }

  /** Tests updating an existing document and verifies the changes are persisted in the database. */
  @Test
  fun testUpdateFile() {
    runBlocking {
      val fileId = UUID.randomUUID().toString()
      val file = JDRFile(id = fileId, name = "Original", ownerId = "owner1")
      repository.createFile(file)

      val updatedFile = file.copy(name = "Updated", content = "New Content")
      val updateResult = repository.updateFile(updatedFile)
      assertTrue("updateFile should succeed", updateResult.isSuccess)

      val getResult = repository.getFile(fileId)
      assertEquals("File name should be updated", "Updated", getResult.getOrNull()?.name)
      assertEquals("File content should be updated", "New Content", getResult.getOrNull()?.content)
    }
  }

  /**
   * Verifies that updating a file that doesn't exist explicitly returns a failure rather than
   * upserting an accidental phantom file.
   */
  @Test
  fun testUpdateFile_NotFound() {
    runBlocking {
      val fileId = "non_existent_id_for_update"
      val file = JDRFile(id = fileId, name = "Ghost", ownerId = "owner1")

      val result = repository.updateFile(file)
      assertTrue("updateFile should fail when document does not exist", result.isFailure)
    }
  }

  /** Verifies that updating a file with an invalid ID returns a failure correctly. */
  @Test
  fun testUpdateFile_Exception() {
    runBlocking {
      val invalidFile = JDRFile(id = "", name = "Invalid")
      val result = repository.updateFile(invalidFile)
      assertTrue("updateFile should fail with empty ID", result.isFailure)
    }
  }

  /**
   * Ensures that attempting to fetch a non-existent document returns a successful Result with a
   * null value, rather than throwing an exception.
   */
  @Test
  fun testGetFile_NotFound() {
    runBlocking {
      val result = repository.getFile("non_existent_id")
      assertTrue("getFile should succeed even if not found", result.isSuccess)
      assertNull("File should be null when not found", result.getOrNull())
    }
  }

  /** Tests the deletion of a document and verifies it is no longer accessible via getFile. */
  @Test
  fun testDeleteFile() {
    runBlocking {
      val fileId = UUID.randomUUID().toString()
      val file = JDRFile(id = fileId, name = "To Delete")
      repository.createFile(file)

      val deleteResult = repository.deleteFile(fileId)
      assertTrue("deleteFile should succeed", deleteResult.isSuccess)

      val getResult = repository.getFile(fileId)
      assertTrue("getFile should succeed after delete", getResult.isSuccess)
      assertNull("File should be null after deletion", getResult.getOrNull())
    }
  }

  /** Verifies that attempting to delete a document with an invalid ID is caught properly. */
  @Test
  fun testDeleteFile_Exception() {
    runBlocking {
      val result = repository.deleteFile("")
      assertTrue("deleteFile should fail with empty ID", result.isFailure)
    }
  }

  /** Verifies that attempting to fetch a document with an invalid ID is caught properly. */
  @Test
  fun testGetFile_Exception() {
    runBlocking {
      val result = repository.getFile("")
      assertTrue("getFile should fail with empty ID", result.isFailure)
    }
  }

  /**
   * Tests real-time updates for a single file. Verifies the Flow initially emits the current state
   * of the document.
   */
  @Test
  fun testGetFileFlow() {
    runBlocking {
      val fileId = UUID.randomUUID().toString()
      val file = JDRFile(id = fileId, name = "Flow Test")
      repository.createFile(file)

      val emittedFile = repository.getFileFlow(fileId).waitFor { it != null }
      assertEquals("Flow should emit the created file", file, emittedFile)
    }
  }

  /** Ensures that subscribing to a non-existent document emits a null value. */
  @Test
  fun testGetFileFlow_NotFound() {
    runBlocking {
      val fileId = "non_existent_flow_id"
      // Flow should emit null for a non-existent file
      val emittedFile = repository.getFileFlow(fileId).waitFor(timeoutMs = 10000) { it == null }
      assertNull("Flow should emit null for non-existent file", emittedFile)
    }
  }

  /**
   * Tests real-time synchronization of a document. Ensures that changes made in the database are
   * immediately emitted to subscribers without requiring a manual refresh.
   */
  @Test
  fun testGetFileFlow_Updates() {
    runBlocking {
      val fileId = UUID.randomUUID().toString()
      val file = JDRFile(id = fileId, name = "Initial")
      repository.createFile(file)

      val latestName = AtomicReference("")
      val job =
          launch(Dispatchers.Default) {
            repository.getFileFlow(fileId).collect { if (it != null) latestName.set(it.name) }
          }

      // Wait for initial value
      var startTime = System.currentTimeMillis()
      while (latestName.get() != "Initial" && System.currentTimeMillis() - startTime < 5000) {
        shadowOf(Looper.getMainLooper()).idle()
        delay(50)
      }
      assertEquals("Initial name should be collected", "Initial", latestName.get())

      // Update the file
      repository.updateFile(file.copy(name = "Updated"))

      // Wait for the updated value
      startTime = System.currentTimeMillis()
      while (latestName.get() != "Updated" && System.currentTimeMillis() - startTime < 5000) {
        shadowOf(Looper.getMainLooper()).idle()
        delay(50)
      }
      assertEquals("Updated name should be collected", "Updated", latestName.get())

      job.cancel()
    }
  }

  /** Tests retrieving all documents at the root of a user's personal space (no parent folders). */
  @Test
  fun testGetPersonalRootFiles() {
    runBlocking {
      val ownerId = UUID.randomUUID().toString()
      val file1 =
          JDRFile(
              id = UUID.randomUUID().toString(),
              ownerId = ownerId,
              name = "File 1",
              parentFolderIds = emptyList(),
          )
      val file2 =
          JDRFile(
              id = UUID.randomUUID().toString(),
              ownerId = ownerId,
              name = "File 2",
              parentFolderIds = emptyList(),
          )
      val fileOtherOwner =
          JDRFile(
              id = UUID.randomUUID().toString(),
              ownerId = "other",
              name = "Other File",
              parentFolderIds = emptyList(),
          )
      val fileWithParent =
          JDRFile(
              id = UUID.randomUUID().toString(),
              ownerId = ownerId,
              name = "Child File",
              parentFolderIds = listOf("parent1"),
          )

      repository.createFile(file1)
      repository.createFile(file2)
      repository.createFile(fileOtherOwner)
      repository.createFile(fileWithParent)

      val files = repository.getPersonalRootFiles(ownerId).waitFor { it.size == 2 }

      assertEquals("Should retrieve exactly 2 files for the owner at root", 2, files.size)
      assertTrue(files.contains(file1))
      assertTrue(files.contains(file2))
    }
  }

  /** Ensures that an empty list is emitted if a user has no documents in their personal root. */
  @Test
  fun testGetPersonalRootFiles_Empty() {
    runBlocking {
      val emptyOwnerId = "empty_owner_id"
      val files = repository.getPersonalRootFiles(emptyOwnerId).waitFor { it.isEmpty() }
      assertTrue("Should retrieve empty list when no root files exist", files.isEmpty())
    }
  }

  /** Tests retrieving all documents at the root of a shared campaign group. */
  @Test
  fun testGetGroupRootFiles() {
    runBlocking {
      val groupId = UUID.randomUUID().toString()
      val file1 =
          JDRFile(
              id = UUID.randomUUID().toString(),
              sharedGroupsIds = listOf(groupId),
              name = "Group File 1",
              parentFolderIds = emptyList(),
          )
      val file2 =
          JDRFile(
              id = UUID.randomUUID().toString(),
              sharedGroupsIds = listOf(groupId),
              name = "Group File 2",
              parentFolderIds = emptyList(),
          )
      val fileOtherGroup =
          JDRFile(
              id = UUID.randomUUID().toString(),
              sharedGroupsIds = listOf("otherGroup"),
              name = "Other Group File",
              parentFolderIds = emptyList(),
          )
      val fileWithParent =
          JDRFile(
              id = UUID.randomUUID().toString(),
              sharedGroupsIds = listOf(groupId),
              name = "Group Child File",
              parentFolderIds = listOf("parent1"),
          )

      repository.createFile(file1)
      repository.createFile(file2)
      repository.createFile(fileOtherGroup)
      repository.createFile(fileWithParent)

      val files = repository.getGroupRootFiles(groupId).waitFor { it.size == 2 }

      assertEquals("Should retrieve exactly 2 files for the group at root", 2, files.size)
      assertTrue(files.contains(file1))
      assertTrue(files.contains(file2))
    }
  }

  /** Ensures that an empty list is emitted if a campaign group has no root documents. */
  @Test
  fun testGetGroupRootFiles_Empty() {
    runBlocking {
      val emptyGroupId = "empty_group_id"
      val files = repository.getGroupRootFiles(emptyGroupId).waitFor { it.isEmpty() }
      assertTrue("Should retrieve empty list when no group root files exist", files.isEmpty())
    }
  }

  /** Tests retrieving all documents located within a specific folder. */
  @Test
  fun testGetDocumentsInFolder() {
    runBlocking {
      val folderId = UUID.randomUUID().toString()
      val file1 =
          JDRFile(
              id = UUID.randomUUID().toString(),
              parentFolderIds = listOf(folderId),
              name = "Child File 1",
          )
      val file2 =
          JDRFile(
              id = UUID.randomUUID().toString(),
              parentFolderIds = listOf(folderId, "otherFolder"),
              name = "Child File 2",
          )
      val fileOtherFolder =
          JDRFile(
              id = UUID.randomUUID().toString(),
              parentFolderIds = listOf("otherFolder"),
              name = "Other Folder File",
          )
      val fileRoot =
          JDRFile(
              id = UUID.randomUUID().toString(),
              parentFolderIds = emptyList(),
              name = "Root File",
          )

      repository.createFile(file1)
      repository.createFile(file2)
      repository.createFile(fileOtherFolder)
      repository.createFile(fileRoot)

      val files = repository.getDocumentsInFolder(folderId).waitFor { it.size == 2 }

      assertEquals("Should retrieve exactly 2 files for the folder", 2, files.size)
      assertTrue(files.contains(file1))
      assertTrue(files.contains(file2))
    }
  }

  /** Ensures that an empty list is emitted if a folder contains no documents. */
  @Test
  fun testGetDocumentsInFolder_Empty() {
    runBlocking {
      val emptyFolderId = "empty_folder_id"
      val files = repository.getDocumentsInFolder(emptyFolderId).waitFor { it.isEmpty() }
      assertTrue("Should retrieve empty list when no documents in folder", files.isEmpty())
    }
  }

  /**
   * Tests the reactivity of dynamic lists (shared workspace folders). If a Game Master adds or
   * removes a document from a folder, the interface should instantly update for all players.
   */
  @Test
  fun testGetDocumentsInFolder_Updates() {
    runBlocking {
      val folderId = UUID.randomUUID().toString()
      val file1 =
          JDRFile(
              id = UUID.randomUUID().toString(),
              parentFolderIds = listOf(folderId),
              name = "File 1",
          )
      repository.createFile(file1)

      val latestList = AtomicReference(emptyList<JDRFile>())
      val hasEmitted = AtomicBoolean(false)
      val job =
          launch(Dispatchers.Default) {
            repository.getDocumentsInFolder(folderId).collect {
              latestList.set(it)
              hasEmitted.set(true)
            }
          }

      // Wait for initial emission (size 1)
      var startTime = System.currentTimeMillis()
      while (
          (!hasEmitted.get() || latestList.get().size != 1) &&
              System.currentTimeMillis() - startTime < 5000
      ) {
        shadowOf(Looper.getMainLooper()).idle()
        delay(50)
      }
      assertEquals("List should have 1 item initially", 1, latestList.get().size)

      // Add another file dynamically
      val file2 =
          JDRFile(
              id = UUID.randomUUID().toString(),
              parentFolderIds = listOf(folderId),
              name = "File 2",
          )
      repository.createFile(file2)

      // Wait for update (size 2)
      startTime = System.currentTimeMillis()
      while (latestList.get().size != 2 && System.currentTimeMillis() - startTime < 5000) {
        shadowOf(Looper.getMainLooper()).idle()
        delay(50)
      }
      assertEquals("List should be updated to 2 items", 2, latestList.get().size)

      // Remove the first file from the folder (simulate moving it to root)
      repository.updateFile(file1.copy(parentFolderIds = emptyList()))

      // Wait for update (size 1, only file2 remains)
      startTime = System.currentTimeMillis()
      while (
          (latestList.get().size != 1 || latestList.get().first().id != file2.id) &&
              System.currentTimeMillis() - startTime < 5000
      ) {
        shadowOf(Looper.getMainLooper()).idle()
        delay(50)
      }
      assertEquals("List should be updated to 1 item after removal", 1, latestList.get().size)
      assertEquals("Remaining file should be File 2", file2.id, latestList.get().first().id)

      job.cancel()
    }
  }

  /**
   * Simulates offline behavior using Cloud Firestore's native cache. Ensures the app can read data
   * gracefully without a network connection. JDRNexus relies on this so gaming sessions can
   * continue in poorly covered areas.
   */
  @Test
  fun testOfflineBehavior() {
    runBlocking {
      val fileId = UUID.randomUUID().toString()
      val file = JDRFile(id = fileId, name = "Offline Test", ownerId = "owner1")

      // Ensure network is enabled and file is created online
      firestore.enableNetwork().await()
      repository.createFile(file)

      // Force Robolectric to process any pending cache-write callbacks
      shadowOf(Looper.getMainLooper()).idle()

      // Simulate losing connection
      firestore.disableNetwork().await()

      // Reading while offline should hit the local Firestore cache!
      val getResult = repository.getFile(fileId)
      assertTrue("getFile should succeed from cache while offline", getResult.isSuccess)
      assertEquals("File name should match", "Offline Test", getResult.getOrNull()?.name)

      // When offline, querying a document that was never cached throws an exception
      // (Firebase cannot confirm its non-existence without reaching the server).
      val getNotFoundResult = repository.getFile("non_existent_offline")
      assertTrue("getFile should fail offline for uncached documents", getNotFoundResult.isFailure)

      // Restore network to leave the instance clean
      firestore.enableNetwork().await()
    }
  }
}
