// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.github.se.jdrnexus.model.personalSpace

import android.os.Looper
import com.github.se.jdrnexus.utils.FirebaseEmulatorTestUtils
import com.github.se.jdrnexus.utils.FirebaseEmulatorTestUtils.DEFAULT_TIMEOUT_MS
import com.github.se.jdrnexus.utils.waitFor
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.EventListener
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.WriteBatch
import io.mockk.coVerify
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.spyk
import io.mockk.verify
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * Those test use firebase emulator WITH TEST SETTING, make sure to activate it with "firebase
 * emulators:exec --config firebase.test.json"
 */
@RunWith(RobolectricTestRunner::class)
class WorkspaceRepositoryFirestoreTest {

  private lateinit var repository: WorkspaceRepository
  private lateinit var firestore: FirebaseFirestore

  @Before
  fun setUp() {
    firestore = FirebaseEmulatorTestUtils.getFirestore()
    repository = WorkspaceRepositoryFirestore(firestore)
  }

  @After
  fun tearDown() {
    runBlocking { FirebaseEmulatorTestUtils.tearDown(firestore, "documents") }
  }

  private suspend fun persistFiles(vararg files: JDRFile) {
    files.forEach { file -> firestore.collection("documents").document(file.id).set(file).await() }
  }

  private suspend fun getPersistedFile(fileId: String): JDRFile? =
      firestore.collection("documents").document(fileId).get().await().toObject(JDRFile::class.java)

  private suspend fun removeItemFromGroupAndPumpMainLooper(
      itemId: String,
      groupId: String,
      parentFolderId: String = groupId,
      targetRepository: WorkspaceRepository = repository,
  ): Result<Unit> {
    val operation =
        CoroutineScope(Dispatchers.Default).async {
          targetRepository.removeItemFromGroup(itemId, groupId, parentFolderId)
        }
    val startTime = System.currentTimeMillis()
    try {
      FirebaseEmulatorTestUtils.pumpMainLooperUntil(
          timeoutMessage = { "removeItemFromGroup did not complete within $DEFAULT_TIMEOUT_MS ms" }
      ) {
        operation.isCompleted
      }
    } catch (e: AssertionError) {
      operation.cancel()
      throw e
    }
    shadowOf(Looper.getMainLooper()).idle()
    return operation.await()
  }

  /** Same idea as removeItemFromGroupAndPumpMainLooper, for any repository call. */
  private suspend fun <T> runAndPumpMainLooper(operationName: String, block: suspend () -> T): T {
    val operation = CoroutineScope(Dispatchers.Default).async { block() }
    try {
      FirebaseEmulatorTestUtils.pumpMainLooperUntil(
          timeoutMessage = { "$operationName did not complete within $DEFAULT_TIMEOUT_MS ms" }
      ) {
        operation.isCompleted
      }
    } catch (e: AssertionError) {
      operation.cancel()
      throw e
    }
    shadowOf(Looper.getMainLooper()).idle()
    return operation.await()
  }

  private fun groupRootOf(groupId: String) =
      JDRFile(id = groupId, sharedGroupsIds = listOf(groupId), type = DocumentType.GROUP_ROOT)

  /** A file placed in [folderId] inside [groupId] (the group id means the group root). */
  private fun inGroup(
      id: String,
      groupId: String,
      folderId: String,
      type: DocumentType = DocumentType.TEXT,
      isHidden: Boolean = false,
  ) =
      JDRFile(
          id = id,
          sharedGroupsIds = listOf(groupId),
          placements = mapOf(groupId to Placement(folderId, isHidden)),
          type = type,
      )

  private fun hiddenIn(file: JDRFile, groupId: String) =
      file.copy(
          placements =
              file.placements + (groupId to file.placements.getValue(groupId).copy(isHidden = true))
      )

  private fun assertFailureWith(result: Result<Unit>, type: Class<out Throwable>, message: String) {
    assertTrue(result.isFailure)
    assertEquals(type, result.exceptionOrNull()?.javaClass)
    assertEquals(message, result.exceptionOrNull()?.message)
  }

  @Test
  fun removeItemFromGroup_rejectsBlankItemId() = runBlocking {
    val result = repository.removeItemFromGroup("", "group-${UUID.randomUUID()}", "")

    assertTrue(result.isFailure)
    assertEquals(IllegalArgumentException::class.java, result.exceptionOrNull()?.javaClass)
    assertEquals("Item ID cannot be empty.", result.exceptionOrNull()?.message)
  }

  @Test
  fun removeItemFromGroup_rejectsBlankGroupId() = runBlocking {
    val result = repository.removeItemFromGroup("item-${UUID.randomUUID()}", " ", "")

    assertTrue(result.isFailure)
    assertEquals(IllegalArgumentException::class.java, result.exceptionOrNull()?.javaClass)
    assertEquals("Group ID cannot be empty.", result.exceptionOrNull()?.message)
  }

  @Test
  fun removeItemFromGroup_rejectsBlankParentFolderId() = runBlocking {
    val result =
        repository.removeItemFromGroup(
            "item-${UUID.randomUUID()}",
            "group-${UUID.randomUUID()}",
            " ",
        )

    assertTrue(result.isFailure)
    assertEquals(IllegalArgumentException::class.java, result.exceptionOrNull()?.javaClass)
    assertEquals("Parent folder ID cannot be empty.", result.exceptionOrNull()?.message)
  }

  @Test
  fun removeItemFromGroup_failsWhenItemDoesNotExist() = runBlocking {
    val result =
        removeItemFromGroupAndPumpMainLooper(
            itemId = "missing-${UUID.randomUUID()}",
            groupId = "group-${UUID.randomUUID()}",
        )

    assertTrue(result.isFailure)
    assertEquals(Exception::class.java, result.exceptionOrNull()?.javaClass)
    assertEquals("Document not found in Firestore", result.exceptionOrNull()?.message)
  }

  @Test
  fun removeItemFromGroup_failsWhenItemIsNotSharedWithGroup() = runBlocking {
    val groupId = "group-${UUID.randomUUID()}"
    val file = JDRFile(id = "file-${UUID.randomUUID()}", sharedGroupsIds = listOf("other-group"))
    persistFiles(file)

    val result = removeItemFromGroupAndPumpMainLooper(file.id, groupId)

    assertTrue(result.isFailure)
    assertEquals(IllegalStateException::class.java, result.exceptionOrNull()?.javaClass)
    assertEquals("This item does not belong to the group.", result.exceptionOrNull()?.message)
    assertEquals(file, getPersistedFile(file.id))
  }

  @Test
  fun removeItemFromGroup_failsWhenFetchingItemFails() = runBlocking {
    val itemId = "item-${UUID.randomUUID()}"
    val groupId = "group-${UUID.randomUUID()}"
    val firestoreMock = mockk<FirebaseFirestore>()
    val collectionMock = mockk<CollectionReference>()
    val documentMock = mockk<DocumentReference>()
    every { firestoreMock.collection("documents") } returns collectionMock
    every { collectionMock.document(itemId) } returns documentMock
    every { documentMock.get() } returns Tasks.forException(IllegalStateException("read failed"))

    val result =
        WorkspaceRepositoryFirestore(firestoreMock).removeItemFromGroup(itemId, groupId, groupId)

    assertTrue(result.isFailure)
    assertEquals(IllegalStateException::class.java, result.exceptionOrNull()?.javaClass)
    assertEquals("read failed", result.exceptionOrNull()?.message)
  }

  @Test
  fun removeItemFromGroup_propagatesCoroutineCancellation() = runBlocking {
    val itemId = "item-${UUID.randomUUID()}"
    val firestoreMock = mockk<FirebaseFirestore>()
    val collectionMock = mockk<CollectionReference>()
    val documentMock = mockk<DocumentReference>()
    val pendingRead = TaskCompletionSource<DocumentSnapshot>()
    every { firestoreMock.collection("documents") } returns collectionMock
    every { collectionMock.document(itemId) } returns documentMock
    every { documentMock.get() } returns pendingRead.task
    val mockRepository = WorkspaceRepositoryFirestore(firestoreMock)

    val job = launch {
      val groupId = "group-${UUID.randomUUID()}"
      mockRepository.removeItemFromGroup(itemId, groupId, groupId)
    }
    yield()
    job.cancel()
    job.join()

    assertTrue(job.isCancelled)
  }

  @Test
  fun removeItemFromGroup_failsWhenFetchingDescendantsFails() = runBlocking {
    val groupId = "group-${UUID.randomUUID()}"
    val folder =
        JDRFile(
            id = "folder-${UUID.randomUUID()}",
            sharedGroupsIds = listOf(groupId),
            type = DocumentType.FOLDER,
        )
    persistFiles(folder)

    val failingRepository = spyk(WorkspaceRepositoryFirestore(firestore))
    every { failingRepository.getGroupFolderFiles(folder.id, groupId) } returns
        flow { throw IllegalStateException("descendant read failed") }

    val result =
        removeItemFromGroupAndPumpMainLooper(
            itemId = folder.id,
            groupId = groupId,
            targetRepository = failingRepository,
        )

    assertTrue(result.isFailure)
    assertEquals(IllegalStateException::class.java, result.exceptionOrNull()?.javaClass)
    assertEquals("descendant read failed", result.exceptionOrNull()?.message)
    assertEquals(folder, getPersistedFile(folder.id))
  }

  @Test
  fun removeItemFromGroup_returnsFailureWhenBatchCommitFails() = runBlocking {
    val itemId = "item-${UUID.randomUUID()}"
    val groupId = "group-${UUID.randomUUID()}"
    val file = JDRFile(id = itemId, sharedGroupsIds = listOf(groupId))
    val firestoreMock = mockk<FirebaseFirestore>()
    val collectionMock = mockk<CollectionReference>()
    val documentMock = mockk<DocumentReference>()
    val snapshotMock = mockk<DocumentSnapshot>()
    val batchMock = mockk<WriteBatch>()
    every { firestoreMock.collection("documents") } returns collectionMock
    every { collectionMock.document(itemId) } returns documentMock
    every { documentMock.get() } returns Tasks.forResult(snapshotMock)
    every { snapshotMock.exists() } returns true
    every { snapshotMock.toObject(JDRFile::class.java) } returns file
    every { firestoreMock.batch() } returns batchMock
    every { batchMock.update(any(), any()) } returns batchMock
    every { batchMock.commit() } returns
        Tasks.forException(IllegalStateException("batch commit failed"))

    val result =
        WorkspaceRepositoryFirestore(firestoreMock).removeItemFromGroup(itemId, groupId, groupId)

    assertTrue(result.isFailure)
    assertEquals(IllegalStateException::class.java, result.exceptionOrNull()?.javaClass)
    assertEquals("batch commit failed", result.exceptionOrNull()?.message)
    verify(exactly = 1) { batchMock.update(documentMock, any()) }
    verify(exactly = 1) { batchMock.commit() }
    confirmVerified(batchMock)
  }

  @Test
  fun removeItemFromGroup_removesSingleFileWithoutDeletingOrChangingOtherMemberships() =
      runBlocking {
        val groupId = "group-${UUID.randomUUID()}"
        val otherGroupId = "other-group-${UUID.randomUUID()}"
        val personalFolderId = "personal-folder-${UUID.randomUUID()}"
        val groupFolder =
            JDRFile(
                id = "group-folder-${UUID.randomUUID()}",
                sharedGroupsIds = listOf(groupId),
                type = DocumentType.FOLDER,
            )
        val personalFolder =
            JDRFile(
                id = personalFolderId,
                ownerId = "owner-${UUID.randomUUID()}",
                type = DocumentType.FOLDER,
            )
        val otherGroupFolder =
            JDRFile(
                id = "other-group-folder-${UUID.randomUUID()}",
                sharedGroupsIds = listOf(otherGroupId),
                type = DocumentType.FOLDER,
            )
        val file =
            JDRFile(
                id = "file-${UUID.randomUUID()}",
                sharedGroupsIds = listOf(groupId, otherGroupId),
                placements =
                    mapOf(
                        groupId to Placement(folderId = groupFolder.id),
                        otherGroupId to Placement(folderId = otherGroupFolder.id),
                    ),
                personalParentId = personalFolderId,
            )
        val child =
            JDRFile(
                id = "child-${UUID.randomUUID()}",
                sharedGroupsIds = listOf(groupId),
                placements = mapOf(groupId to Placement(folderId = file.id)),
            )
        persistFiles(groupFolder, personalFolder, otherGroupFolder, file, child)
        val repositorySpy = spyk(WorkspaceRepositoryFirestore(firestore))

        val result =
            removeItemFromGroupAndPumpMainLooper(
                file.id,
                groupId,
                parentFolderId = groupFolder.id,
                targetRepository = repositorySpy,
            )

        assertTrue(result.isSuccess)
        assertEquals(
            file.copy(
                sharedGroupsIds = listOf(otherGroupId),
                placements = mapOf(otherGroupId to Placement(folderId = otherGroupFolder.id)),
            ),
            getPersistedFile(file.id),
        )
        assertEquals(groupFolder, getPersistedFile(groupFolder.id))
        assertEquals(personalFolder, getPersistedFile(personalFolder.id))
        assertEquals(otherGroupFolder, getPersistedFile(otherGroupFolder.id))
        assertEquals(child, getPersistedFile(child.id))
        verify(exactly = 0) { repositorySpy.getGroupFolderFiles(file.id, groupId) }
        coVerify(exactly = 1) { repositorySpy.getFile(file.id) }
      }

  @Test
  fun removeItemFromGroup_removesFolderRecursivelyAndPreservesOtherGroupPlacements() = runBlocking {
    val groupId = "group-${UUID.randomUUID()}"
    val otherGroupId = "other-group-${UUID.randomUUID()}"
    val groupRoot =
        JDRFile(
            id = groupId,
            sharedGroupsIds = listOf(groupId),
            type = DocumentType.FOLDER,
        )
    val root =
        JDRFile(
            id = "root-${UUID.randomUUID()}",
            sharedGroupsIds = listOf(groupId),
            placements = mapOf(groupId to Placement(folderId = groupId)),
            type = DocumentType.FOLDER,
        )
    val otherGroupFolder =
        JDRFile(
            id = "other-group-folder-${UUID.randomUUID()}",
            sharedGroupsIds = listOf(otherGroupId),
            type = DocumentType.FOLDER,
        )
    val nestedFolder =
        JDRFile(
            id = "nested-${UUID.randomUUID()}",
            sharedGroupsIds = listOf(groupId, otherGroupId),
            placements =
                mapOf(
                    groupId to Placement(folderId = root.id),
                    otherGroupId to Placement(folderId = otherGroupFolder.id),
                ),
            type = DocumentType.FOLDER,
        )
    val nestedFile =
        JDRFile(
            id = "nested-file-${UUID.randomUUID()}",
            sharedGroupsIds = listOf(groupId, otherGroupId),
            placements =
                mapOf(
                    groupId to Placement(folderId = nestedFolder.id),
                    otherGroupId to Placement(folderId = otherGroupFolder.id),
                ),
        )
    val childNotSharedWithGroup =
        JDRFile(
            id = "not-shared-${UUID.randomUUID()}",
            sharedGroupsIds = listOf(otherGroupId),
            placements = mapOf(otherGroupId to Placement(folderId = root.id)),
        )
    val unrelatedFile = JDRFile(id = "unrelated-${UUID.randomUUID()}", name = "Untouched")
    persistFiles(
        groupRoot,
        root,
        otherGroupFolder,
        nestedFolder,
        nestedFile,
        childNotSharedWithGroup,
        unrelatedFile,
    )

    val result = removeItemFromGroupAndPumpMainLooper(root.id, groupId)

    assertTrue(result.isSuccess)
    assertEquals(groupRoot, getPersistedFile(groupRoot.id))
    assertEquals(
        root.copy(sharedGroupsIds = emptyList(), placements = emptyMap()),
        getPersistedFile(root.id),
    )
    assertEquals(
        nestedFolder.copy(
            sharedGroupsIds = listOf(otherGroupId),
            placements = mapOf(otherGroupId to Placement(folderId = otherGroupFolder.id)),
        ),
        getPersistedFile(nestedFolder.id),
    )
    assertEquals(
        nestedFile.copy(
            sharedGroupsIds = listOf(otherGroupId),
            placements = mapOf(otherGroupId to Placement(folderId = otherGroupFolder.id)),
        ),
        getPersistedFile(nestedFile.id),
    )
    assertEquals(otherGroupFolder, getPersistedFile(otherGroupFolder.id))
    assertEquals(childNotSharedWithGroup, getPersistedFile(childNotSharedWithGroup.id))
    assertEquals(unrelatedFile, getPersistedFile(unrelatedFile.id))
  }

  @Test
  fun removeItemFromGroup_preservesPersonalSpaceDocumentsAndMetadata() = runBlocking {
    val groupId = "group-${UUID.randomUUID()}"
    val otherGroupId = "other-group-${UUID.randomUUID()}"
    val ownerId = "owner-${UUID.randomUUID()}"
    val groupRoot =
        JDRFile(
            id = groupId,
            sharedGroupsIds = listOf(groupId),
            type = DocumentType.FOLDER,
        )
    val root =
        JDRFile(
            id = "personal-root-${UUID.randomUUID()}",
            name = "Personal root",
            ownerId = ownerId,
            sharedGroupsIds = listOf(groupId),
            placements = mapOf(groupId to Placement(folderId = groupId)),
            type = DocumentType.FOLDER,
        )
    val nestedFolder =
        JDRFile(
            id = "personal-nested-${UUID.randomUUID()}",
            name = "Personal nested folder",
            ownerId = ownerId,
            sharedGroupsIds = listOf(groupId),
            placements = mapOf(groupId to Placement(folderId = root.id)),
            personalParentId = root.id,
            type = DocumentType.FOLDER,
        )
    val otherGroupFolder =
        JDRFile(
            id = "other-group-folder-${UUID.randomUUID()}",
            name = "Other group's folder",
            ownerId = ownerId,
            sharedGroupsIds = listOf(otherGroupId),
            type = DocumentType.FOLDER,
        )
    val personalFile =
        JDRFile(
            id = "personal-file-${UUID.randomUUID()}",
            name = "Personal notes",
            ownerId = ownerId,
            sharedGroupsIds = listOf(groupId, otherGroupId),
            placements =
                mapOf(
                    groupId to Placement(folderId = nestedFolder.id),
                    otherGroupId to Placement(folderId = otherGroupFolder.id),
                ),
            personalParentId = nestedFolder.id,
            type = DocumentType.TEXT,
            content = "Keep this content",
        )
    persistFiles(groupRoot, root, nestedFolder, otherGroupFolder, personalFile)

    val result = removeItemFromGroupAndPumpMainLooper(root.id, groupId)

    assertTrue(result.isSuccess)
    assertEquals(groupRoot, getPersistedFile(groupRoot.id))
    assertEquals(
        root.copy(sharedGroupsIds = emptyList(), placements = emptyMap()),
        getPersistedFile(root.id),
    )
    assertEquals(
        nestedFolder.copy(
            sharedGroupsIds = emptyList(),
            placements = emptyMap(),
        ),
        getPersistedFile(nestedFolder.id),
    )
    assertEquals(otherGroupFolder, getPersistedFile(otherGroupFolder.id))
    assertEquals(
        personalFile.copy(
            sharedGroupsIds = listOf(otherGroupId),
            placements = mapOf(otherGroupId to Placement(folderId = otherGroupFolder.id)),
        ),
        getPersistedFile(personalFile.id),
    )

    val personalRootItems =
        repository.getPersonalRootFiles(ownerId).waitFor { files -> files.any { it.id == root.id } }
    assertTrue(
        personalRootItems.any {
          it == root.copy(sharedGroupsIds = emptyList(), placements = emptyMap())
        }
    )
    assertTrue(
        repository
            .getPersonalFolderFiles(root.id, ownerId)
            .waitFor { files -> files.any { it.id == nestedFolder.id } }
            .any { it == nestedFolder.copy(sharedGroupsIds = emptyList(), placements = emptyMap()) }
    )
    assertTrue(
        repository
            .getPersonalFolderFiles(nestedFolder.id, ownerId)
            .waitFor { files -> files.any { it.id == personalFile.id } }
            .any {
              it ==
                  personalFile.copy(
                      sharedGroupsIds = listOf(otherGroupId),
                      placements = mapOf(otherGroupId to Placement(folderId = otherGroupFolder.id)),
                  )
            }
    )
    assertEquals(
        personalFile.copy(
            sharedGroupsIds = listOf(otherGroupId),
            placements = mapOf(otherGroupId to Placement(folderId = otherGroupFolder.id)),
        ),
        repository.getFile(personalFile.id).getOrThrow(),
    )
  }

  @Test
  fun removeItemFromGroup_detachesNestedFolderFromExclusiveParent() = runBlocking {
    val groupId = "group-${UUID.randomUUID()}"
    val parent =
        JDRFile(
            id = "parent-${UUID.randomUUID()}",
            sharedGroupsIds = listOf(groupId),
            type = DocumentType.FOLDER,
        )
    val nestedFolder =
        JDRFile(
            id = "nested-${UUID.randomUUID()}",
            sharedGroupsIds = listOf(groupId),
            placements = mapOf(groupId to Placement(folderId = parent.id)),
            type = DocumentType.FOLDER,
        )
    persistFiles(parent, nestedFolder)

    val result = removeItemFromGroupAndPumpMainLooper(nestedFolder.id, groupId, parent.id)

    assertTrue(result.isSuccess)
    assertEquals(
        nestedFolder.copy(sharedGroupsIds = emptyList(), placements = emptyMap()),
        getPersistedFile(nestedFolder.id),
    )
    assertEquals(parent, getPersistedFile(parent.id))
  }

  @Test
  fun removeItemFromGroup_removesSelectedParentReferenceWhenParentIsSharedWithAnotherGroup() =
      runBlocking {
        val groupId = "group-${UUID.randomUUID()}"
        val otherGroupId = "other-group-${UUID.randomUUID()}"
        val groupRoot =
            JDRFile(
                id = groupId,
                sharedGroupsIds = listOf(groupId),
                type = DocumentType.FOLDER,
            )
        val parent =
            JDRFile(
                id = "parent-${UUID.randomUUID()}",
                sharedGroupsIds = listOf(groupId, otherGroupId),
                type = DocumentType.FOLDER,
            )
        val nestedFile =
            JDRFile(
                id = "file-${UUID.randomUUID()}",
                sharedGroupsIds = listOf(groupId, otherGroupId),
                placements =
                    mapOf(
                        groupId to Placement(folderId = groupId),
                        otherGroupId to Placement(folderId = parent.id),
                    ),
            )
        persistFiles(groupRoot, parent, nestedFile)

        val result = removeItemFromGroupAndPumpMainLooper(nestedFile.id, otherGroupId, parent.id)

        assertTrue(result.isSuccess)
        assertEquals(
            nestedFile.copy(
                sharedGroupsIds = listOf(groupId),
                placements = mapOf(groupId to Placement(folderId = groupId)),
            ),
            getPersistedFile(nestedFile.id),
        )
        assertEquals(groupRoot, getPersistedFile(groupRoot.id))
        assertEquals(parent, getPersistedFile(parent.id))
      }

  @Test
  fun removeItemFromGroup_removesParentReferenceWhenParentIsExclusiveToGroup() = runBlocking {
    val groupId = "group-${UUID.randomUUID()}"
    val parent =
        JDRFile(
            id = "parent-${UUID.randomUUID()}",
            sharedGroupsIds = listOf(groupId),
            type = DocumentType.FOLDER,
        )
    val nestedFile =
        JDRFile(
            id = "file-${UUID.randomUUID()}",
            sharedGroupsIds = listOf(groupId),
            placements = mapOf(groupId to Placement(folderId = parent.id)),
        )
    persistFiles(parent, nestedFile)

    val result = removeItemFromGroupAndPumpMainLooper(nestedFile.id, groupId, parent.id)

    assertTrue(result.isSuccess)
    assertEquals(
        nestedFile.copy(sharedGroupsIds = emptyList(), placements = emptyMap()),
        getPersistedFile(nestedFile.id),
    )
  }

  @Test
  fun removeItemFromGroup_handlesEmptyFolder() = runBlocking {
    val groupId = "group-${UUID.randomUUID()}"
    val groupRoot =
        JDRFile(
            id = groupId,
            sharedGroupsIds = listOf(groupId),
            type = DocumentType.FOLDER,
        )
    val folder =
        JDRFile(
            id = "empty-folder-${UUID.randomUUID()}",
            sharedGroupsIds = listOf(groupId),
            placements = mapOf(groupId to Placement(folderId = groupId)),
            type = DocumentType.FOLDER,
        )
    persistFiles(groupRoot, folder)

    val result = removeItemFromGroupAndPumpMainLooper(folder.id, groupId)

    assertTrue(result.isSuccess)
    assertEquals(
        folder.copy(sharedGroupsIds = emptyList(), placements = emptyMap()),
        getPersistedFile(folder.id),
    )
    assertEquals(groupRoot, getPersistedFile(groupRoot.id))
  }

  @Test
  fun removeItemFromGroup_doesNotProcessRepeatedDocumentsMoreThanOnce() = runBlocking {
    val groupId = "group-${UUID.randomUUID()}"
    val root =
        JDRFile(
            id = "root-${UUID.randomUUID()}",
            sharedGroupsIds = listOf(groupId),
            placements = mapOf(groupId to Placement(folderId = groupId)),
            type = DocumentType.FOLDER,
        )
    val child =
        JDRFile(
            id = "child-${UUID.randomUUID()}",
            sharedGroupsIds = listOf(groupId),
            placements = mapOf(groupId to Placement(folderId = root.id)),
            type = DocumentType.FOLDER,
        )
    val rootUpdate = slot<Map<String, Any>>()
    val childUpdate = slot<Map<String, Any>>()
    val repeatedRoot = root.copy(placements = mapOf(groupId to Placement(folderId = child.id)))
    persistFiles(repeatedRoot, child)

    val collection = firestore.collection("documents")
    val firestoreMock = mockk<FirebaseFirestore>()
    val batchMock = mockk<WriteBatch>()
    every { firestoreMock.collection("documents") } returns collection
    every { firestoreMock.batch() } returns batchMock
    every { batchMock.update(any(), any()) } returns batchMock
    every { batchMock.commit() } returns Tasks.forResult(null)

    val repositoryMock = WorkspaceRepositoryFirestore(firestoreMock)
    val result =
        removeItemFromGroupAndPumpMainLooper(
            root.id,
            groupId,
            child.id,
            targetRepository = repositoryMock,
        )

    assertTrue(result.isSuccess)
    verify(exactly = 1) { batchMock.update(collection.document(root.id), capture(rootUpdate)) }
    verify(exactly = 1) { batchMock.update(collection.document(child.id), capture(childUpdate)) }
    verify(exactly = 1) { batchMock.commit() }
    confirmVerified(batchMock)

    listOf(rootUpdate.captured, childUpdate.captured).forEach { update ->
      assertEquals(setOf("placements.$groupId", "sharedGroupsIds"), update.keys)
      assertEquals(FieldValue.delete(), update["placements.$groupId"])
      assertTrue(update["sharedGroupsIds"] is FieldValue)
    }
  }

  @Test
  fun removeItemFromGroup_refusesToRemoveGroupRoot() = runBlocking {
    val groupId = "group-${UUID.randomUUID()}"
    val groupRoot =
        JDRFile(
            id = groupId,
            sharedGroupsIds = listOf(groupId),
            type = DocumentType.GROUP_ROOT,
        )
    persistFiles(groupRoot)

    val result = removeItemFromGroupAndPumpMainLooper(groupRoot.id, groupId)

    assertTrue(result.isFailure)
    assertEquals(IllegalStateException::class.java, result.exceptionOrNull()?.javaClass)
    assertEquals(
        "A group root cannot be removed from its group.",
        result.exceptionOrNull()?.message,
    )
    assertEquals(groupRoot, getPersistedFile(groupRoot.id))
  }

  @Test
  fun addItemToGroup_addsTheItemAtTheGroupRoot() = runBlocking {
    val file = JDRFile(id = "file", name = "Goblin", ownerId = "owner", content = "hp: 7")
    persistFiles(groupRootOf("group"), file)

    val result =
        runAndPumpMainLooper("addItemToGroup") {
          repository.addItemToGroup("file", "group", "group")
        }

    assertTrue(result.isSuccess)
    assertEquals(
        file.copy(
            sharedGroupsIds = listOf("group"),
            placements = mapOf("group" to Placement("group")),
        ),
        getPersistedFile("file"),
    )
  }

  @Test
  fun addItemToGroup_addsInASubfolderAndKeepsTheItemsOtherGroups() = runBlocking {
    val folder = inGroup("folder", "group", "group", DocumentType.FOLDER)
    val file = inGroup("file", "other-group", "other-group")
    persistFiles(groupRootOf("group"), folder, file)

    val result =
        runAndPumpMainLooper("addItemToGroup") {
          repository.addItemToGroup("file", "group", "folder")
        }

    assertTrue(result.isSuccess)
    assertEquals(
        file.copy(
            sharedGroupsIds = listOf("other-group", "group"),
            placements = file.placements + ("group" to Placement("folder")),
        ),
        getPersistedFile("file"),
    )
  }

  @Test
  fun addItemToGroup_canAddTheItemHidden() = runBlocking {
    val file = JDRFile(id = "file")
    persistFiles(groupRootOf("group"), file)

    val result =
        runAndPumpMainLooper("addItemToGroup") {
          repository.addItemToGroup("file", "group", "group", isHidden = true)
        }

    assertTrue(result.isSuccess)
    assertEquals(Placement("group", true), getPersistedFile("file")?.placements?.get("group"))
  }

  @Test
  fun addItemToGroup_hidesTheItemWhenTheFolderIsHidden() = runBlocking {
    val hiddenFolder = inGroup("folder", "group", "group", DocumentType.FOLDER, isHidden = true)
    val file = JDRFile(id = "file")
    persistFiles(groupRootOf("group"), hiddenFolder, file)

    val result =
        runAndPumpMainLooper("addItemToGroup") {
          repository.addItemToGroup("file", "group", "folder", isHidden = false)
        }

    assertTrue(result.isSuccess)
    assertEquals(Placement("folder", true), getPersistedFile("file")?.placements?.get("group"))
  }

  @Test
  fun addItemToGroup_addsAFolderWithoutItsContent() = runBlocking {
    val folder = JDRFile(id = "folder", type = DocumentType.FOLDER)
    val child = JDRFile(id = "child", personalParentId = "folder")
    persistFiles(groupRootOf("group"), folder, child)

    val result =
        runAndPumpMainLooper("addItemToGroup") {
          repository.addItemToGroup("folder", "group", "group")
        }

    assertTrue(result.isSuccess)
    assertEquals(listOf("group"), getPersistedFile("folder")?.sharedGroupsIds)
    assertEquals(child, getPersistedFile("child"))
  }

  @Test
  fun addItemToGroup_refusesAnItemAlreadyInTheGroup() = runBlocking {
    val file = inGroup("file", "group", "group")
    persistFiles(groupRootOf("group"), file)

    val result =
        runAndPumpMainLooper("addItemToGroup") {
          repository.addItemToGroup("file", "group", "group")
        }

    assertFailureWith(
        result,
        IllegalStateException::class.java,
        "This item is already in the group.",
    )
    assertEquals(file, getPersistedFile("file"))
  }

  @Test
  fun addItemToGroup_refusesADestinationThatIsNotAFolder() = runBlocking {
    val notAFolder = inGroup("text", "group", "group")
    val file = JDRFile(id = "file")
    persistFiles(groupRootOf("group"), notAFolder, file)

    val result =
        runAndPumpMainLooper("addItemToGroup") {
          repository.addItemToGroup("file", "group", "text")
        }

    assertFailureWith(result, IllegalStateException::class.java, "The destination is not a folder.")
    assertEquals(file, getPersistedFile("file"))
  }

  @Test
  fun addItemToGroup_refusesADestinationOutsideTheGroup() = runBlocking {
    val foreignFolder = inGroup("folder", "other-group", "other-group", DocumentType.FOLDER)
    val file = JDRFile(id = "file")
    persistFiles(groupRootOf("group"), foreignFolder, file)

    val result =
        runAndPumpMainLooper("addItemToGroup") {
          repository.addItemToGroup("file", "group", "folder")
        }

    assertFailureWith(
        result,
        IllegalStateException::class.java,
        "The destination folder does not belong to the group.",
    )
    assertEquals(file, getPersistedFile("file"))
  }

  @Test
  fun addItemToGroup_refusesAGroupRoot() = runBlocking {
    persistFiles(groupRootOf("group"), groupRootOf("other-group"))

    val result =
        runAndPumpMainLooper("addItemToGroup") {
          repository.addItemToGroup("group", "other-group", "other-group")
        }

    assertFailureWith(
        result,
        IllegalStateException::class.java,
        "A group root cannot be added to a group.",
    )
  }

  @Test
  fun addItemToGroup_rejectsBlankIds() = runBlocking {
    assertFailureWith(
        repository.addItemToGroup(" ", "group", "group"),
        IllegalArgumentException::class.java,
        "Item ID cannot be empty.",
    )
    assertFailureWith(
        repository.addItemToGroup("file", " ", "group"),
        IllegalArgumentException::class.java,
        "Group ID cannot be empty.",
    )
    assertFailureWith(
        repository.addItemToGroup("file", "group", " "),
        IllegalArgumentException::class.java,
        "Folder ID cannot be empty.",
    )
  }

  @Test
  fun addItemToGroup_withContent_addsTheWholePersonalTreeKeepingItsStructure() = runBlocking {
    val folder = JDRFile(id = "folder", ownerId = "owner", type = DocumentType.FOLDER)
    val file =
        JDRFile(
            id = "file",
            ownerId = "owner",
            personalParentId = "folder",
            sharedGroupsIds = listOf("other-group"),
            placements = mapOf("other-group" to Placement("other-group")),
        )
    val subfolder =
        JDRFile(
            id = "subfolder",
            ownerId = "owner",
            personalParentId = "folder",
            type = DocumentType.FOLDER,
        )
    val deepFile = JDRFile(id = "deep", ownerId = "owner", personalParentId = "subfolder")
    val unrelated = JDRFile(id = "unrelated", ownerId = "owner")
    persistFiles(groupRootOf("group"), folder, file, subfolder, deepFile, unrelated)

    val result =
        runAndPumpMainLooper("addItemToGroup") {
          repository.addItemToGroup("folder", "group", "group", includeContent = true)
        }

    assertTrue(result.isSuccess)
    assertEquals(folder.withPlacement("group", Placement("group")), getPersistedFile("folder"))
    assertEquals(file.withPlacement("group", Placement("folder")), getPersistedFile("file"))
    assertEquals(
        subfolder.withPlacement("group", Placement("folder")),
        getPersistedFile("subfolder"),
    )
    assertEquals(deepFile.withPlacement("group", Placement("subfolder")), getPersistedFile("deep"))
    assertEquals(unrelated, getPersistedFile("unrelated"))
  }

  @Test
  fun addItemToGroup_withContent_keepsItemsAlreadyInTheGroupAndPlacesTheirContentUnderThem() =
      runBlocking {
        val folder = JDRFile(id = "folder", ownerId = "owner", type = DocumentType.FOLDER)
        val placed =
            JDRFile(
                    id = "placed",
                    ownerId = "owner",
                    personalParentId = "folder",
                    type = DocumentType.FOLDER,
                )
                .withPlacement("group", Placement("group"))
        val inside = JDRFile(id = "inside", ownerId = "owner", personalParentId = "placed")
        persistFiles(groupRootOf("group"), folder, placed, inside)

        val result =
            runAndPumpMainLooper("addItemToGroup") {
              repository.addItemToGroup("folder", "group", "group", includeContent = true)
            }

        assertTrue(result.isSuccess)
        assertEquals(placed, getPersistedFile("placed"))
        assertEquals(inside.withPlacement("group", Placement("placed")), getPersistedFile("inside"))
      }

  @Test
  fun addItemToGroup_withContent_hidesEverythingWhenAskedTo() = runBlocking {
    val folder = JDRFile(id = "folder", ownerId = "owner", type = DocumentType.FOLDER)
    val file = JDRFile(id = "file", ownerId = "owner", personalParentId = "folder")
    persistFiles(groupRootOf("group"), folder, file)

    val result =
        runAndPumpMainLooper("addItemToGroup") {
          repository.addItemToGroup(
              "folder",
              "group",
              "group",
              isHidden = true,
              includeContent = true,
          )
        }

    assertTrue(result.isSuccess)
    assertEquals(Placement("group", true), getPersistedFile("folder")?.placements?.get("group"))
    assertEquals(Placement("folder", true), getPersistedFile("file")?.placements?.get("group"))
  }

  @Test
  fun addItemToGroup_withContent_hidesEverythingWhenTheDestinationIsHidden() = runBlocking {
    val destination = inGroup("destination", "group", "group", DocumentType.FOLDER, isHidden = true)
    val folder = JDRFile(id = "folder", ownerId = "owner", type = DocumentType.FOLDER)
    val file = JDRFile(id = "file", ownerId = "owner", personalParentId = "folder")
    persistFiles(groupRootOf("group"), destination, folder, file)

    val result =
        runAndPumpMainLooper("addItemToGroup") {
          repository.addItemToGroup("folder", "group", "destination", includeContent = true)
        }

    assertTrue(result.isSuccess)
    assertEquals(
        Placement("destination", true),
        getPersistedFile("folder")?.placements?.get("group"),
    )
    assertEquals(Placement("folder", true), getPersistedFile("file")?.placements?.get("group"))
  }

  @Test
  fun addItemToGroup_withContent_ignoresTheOptionForAFileThatIsNotAFolder() = runBlocking {
    val file = JDRFile(id = "file", ownerId = "owner")
    persistFiles(groupRootOf("group"), file)

    val result =
        runAndPumpMainLooper("addItemToGroup") {
          repository.addItemToGroup("file", "group", "group", includeContent = true)
        }

    assertTrue(result.isSuccess)
    assertEquals(file.withPlacement("group", Placement("group")), getPersistedFile("file"))
  }

  @Test
  fun addItemToGroup_withContent_refusesToAddMoreItemsThanOneBatchCanWrite() = runBlocking {
    val folder = JDRFile(id = "folder", ownerId = "owner", type = DocumentType.FOLDER)
    persistFiles(groupRootOf("group"), folder)
    val repositorySpy = spyk(WorkspaceRepositoryFirestore(firestore))
    val children =
        (1..500).map { JDRFile(id = "child-$it", ownerId = "owner", personalParentId = "folder") }
    every { repositorySpy.getPersonalFolderFiles("folder", "owner") } returns flowOf(children)

    val result =
        runAndPumpMainLooper("addItemToGroup") {
          repositorySpy.addItemToGroup("folder", "group", "group", includeContent = true)
        }

    assertFailureWith(
        result,
        IllegalStateException::class.java,
        "Too many items to add at once (maximum 500).",
    )
    assertEquals(folder, getPersistedFile("folder"))
  }

  @Test
  fun setItemHidden_hidesAFile() = runBlocking {
    val file = inGroup("file", "group", "group")
    persistFiles(groupRootOf("group"), file)

    val result =
        runAndPumpMainLooper("setItemHidden") { repository.setItemHidden("file", "group", true) }

    assertTrue(result.isSuccess)
    assertEquals(hiddenIn(file, "group"), getPersistedFile("file"))
  }

  @Test
  fun setItemHidden_hidesEverythingInsideAFolder() = runBlocking {
    val folder = inGroup("folder", "group", "group", DocumentType.FOLDER)
    val nested = inGroup("nested", "group", "folder", DocumentType.FOLDER)
    val deep = inGroup("deep", "group", "nested")
    val child = inGroup("child", "group", "folder")
    val sibling = inGroup("sibling", "group", "group")
    persistFiles(groupRootOf("group"), folder, nested, deep, child, sibling)

    val result =
        runAndPumpMainLooper("setItemHidden") { repository.setItemHidden("folder", "group", true) }

    assertTrue(result.isSuccess)
    listOf(folder, nested, deep, child).forEach {
      assertEquals(hiddenIn(it, "group"), getPersistedFile(it.id))
    }
    assertEquals(sibling, getPersistedFile("sibling"))
  }

  @Test
  fun setItemHidden_doesNotChangeTheItemsOtherGroups() = runBlocking {
    val file =
        inGroup("file", "group", "group")
            .copy(
                sharedGroupsIds = listOf("group", "other-group"),
                placements =
                    mapOf(
                        "group" to Placement("group"),
                        "other-group" to Placement("other-group"),
                    ),
            )
    persistFiles(groupRootOf("group"), file)

    val result =
        runAndPumpMainLooper("setItemHidden") { repository.setItemHidden("file", "group", true) }

    assertTrue(result.isSuccess)
    assertEquals(hiddenIn(file, "group"), getPersistedFile("file"))
  }

  @Test
  fun setItemHidden_revealsOnlyTheItemAndNotItsContent() = runBlocking {
    val folder = inGroup("folder", "group", "group", DocumentType.FOLDER, isHidden = true)
    val child = inGroup("child", "group", "folder", isHidden = true)
    persistFiles(groupRootOf("group"), folder, child)

    val result =
        runAndPumpMainLooper("setItemHidden") { repository.setItemHidden("folder", "group", false) }

    assertTrue(result.isSuccess)
    assertEquals(Placement("group", false), getPersistedFile("folder")?.placements?.get("group"))
    assertEquals(child, getPersistedFile("child"))
  }

  @Test
  fun setItemHidden_refusesToRevealAnItemInAHiddenFolder() = runBlocking {
    val folder = inGroup("folder", "group", "group", DocumentType.FOLDER, isHidden = true)
    val child = inGroup("child", "group", "folder", isHidden = true)
    persistFiles(groupRootOf("group"), folder, child)

    val result =
        runAndPumpMainLooper("setItemHidden") { repository.setItemHidden("child", "group", false) }

    assertFailureWith(result, IllegalStateException::class.java, "Reveal the parent folder first.")
    assertEquals(child, getPersistedFile("child"))
  }

  @Test
  fun setItemHidden_doesNothingWhenAlreadyInTheRequestedState() = runBlocking {
    val file = inGroup("file", "group", "group")
    persistFiles(groupRootOf("group"), file)

    val result =
        runAndPumpMainLooper("setItemHidden") { repository.setItemHidden("file", "group", false) }

    assertTrue(result.isSuccess)
    assertEquals(file, getPersistedFile("file"))
  }

  @Test
  fun setItemHidden_refusesAnItemThatIsNotInTheGroup() = runBlocking {
    val file = JDRFile(id = "file")
    persistFiles(groupRootOf("group"), file)

    val result =
        runAndPumpMainLooper("setItemHidden") { repository.setItemHidden("file", "group", true) }

    assertFailureWith(
        result,
        IllegalStateException::class.java,
        "This item does not belong to the group.",
    )
  }

  @Test
  fun setItemHidden_refusesAGroupRoot() = runBlocking {
    persistFiles(groupRootOf("group"))

    val result =
        runAndPumpMainLooper("setItemHidden") { repository.setItemHidden("group", "group", true) }

    assertFailureWith(result, IllegalStateException::class.java, "A group root cannot be hidden.")
  }

  @Test
  fun setItemHidden_refusesToHideMoreItemsThanOneBatchCanWrite() = runBlocking {
    val folder = inGroup("folder", "group", "group", DocumentType.FOLDER)
    persistFiles(groupRootOf("group"), folder)
    val repositorySpy = spyk(WorkspaceRepositoryFirestore(firestore))
    val children = (1..500).map { inGroup("child-$it", "group", "folder") }
    every { repositorySpy.getGroupFolderFiles("folder", "group") } returns flowOf(children)

    val result =
        runAndPumpMainLooper("setItemHidden") {
          repositorySpy.setItemHidden("folder", "group", true)
        }

    assertFailureWith(
        result,
        IllegalStateException::class.java,
        "Too many items to update at once (maximum 500).",
    )
    assertEquals(folder, getPersistedFile("folder"))
  }

  @Test
  fun setItemHidden_rejectsBlankIds() = runBlocking {
    assertFailureWith(
        repository.setItemHidden(" ", "group", true),
        IllegalArgumentException::class.java,
        "Item ID cannot be empty.",
    )
    assertFailureWith(
        repository.setItemHidden("file", " ", true),
        IllegalArgumentException::class.java,
        "Group ID cannot be empty.",
    )
  }

  /** Tests the creation of a new uid and verifies that it not empty and unique */
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

  /** An edit made from an older snapshot must not undo a share made in the meantime. */
  @Test
  fun updateFile_doesNotOverwriteGroupPlacements() {
    runBlocking {
      val groupId = "group-${UUID.randomUUID()}"
      val file = JDRFile(id = UUID.randomUUID().toString(), name = "Original", ownerId = "owner1")
      persistFiles(file)
      // The file is shared after the editor loaded its snapshot (which has no placement).
      val shared = file.withPlacement(groupId, Placement(folderId = groupId))
      persistFiles(shared)

      val result = repository.updateFile(file.copy(name = "Edited", content = "New content"))

      assertTrue(result.isSuccess)
      assertEquals(
          shared.copy(name = "Edited", content = "New content"),
          getPersistedFile(file.id),
      )
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

  /** Ensures that attempting to fetch a non-existent document returns a failure. */
  @Test
  fun testGetFile_NotFound() {
    runBlocking {
      val result = repository.getFile("non_existent_id")
      assertTrue("getFile should fail when document is not found", result.isFailure)
      assertEquals("Document not found in Firestore", result.exceptionOrNull()?.message)
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
      assertTrue(
          "getFile should fail after delete since the document no longer exists",
          getResult.isFailure,
      )
      assertEquals("Document not found in Firestore", getResult.exceptionOrNull()?.message)
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
      val emittedFile =
          repository.getFileFlow(fileId).waitFor(timeoutMs = DEFAULT_TIMEOUT_MS) { it == null }
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

      FirebaseEmulatorTestUtils.pumpMainLooperUntil { latestName.get() == "Initial" }
      assertEquals("Initial name should be collected", "Initial", latestName.get())

      // Update the file
      repository.updateFile(file.copy(name = "Updated"))

      FirebaseEmulatorTestUtils.pumpMainLooperUntil { latestName.get() == "Updated" }
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
          )
      val file2 =
          JDRFile(
              id = UUID.randomUUID().toString(),
              ownerId = ownerId,
              name = "File 2",
          )
      val fileOtherOwner =
          JDRFile(
              id = UUID.randomUUID().toString(),
              ownerId = "other",
              name = "Other File",
          )
      val fileWithParent =
          JDRFile(
              id = UUID.randomUUID().toString(),
              ownerId = ownerId,
              name = "Child File",
              personalParentId = "parent1",
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
              placements = mapOf(groupId to Placement(folderId = groupId)),
          )
      val file2 =
          JDRFile(
              id = UUID.randomUUID().toString(),
              sharedGroupsIds = listOf(groupId),
              name = "Group File 2",
              placements = mapOf(groupId to Placement(folderId = groupId)),
          )
      val fileOtherGroup =
          JDRFile(
              id = UUID.randomUUID().toString(),
              sharedGroupsIds = listOf("otherGroup"),
              name = "Other Group File",
              placements = mapOf("otherGroup" to Placement(folderId = "otherGroup")),
          )
      val fileWithParent =
          JDRFile(
              id = UUID.randomUUID().toString(),
              sharedGroupsIds = listOf(groupId),
              name = "Group Child File",
              placements = mapOf(groupId to Placement(folderId = "parent1")),
          )
      val parentFolder =
          JDRFile(
              id = "parent1",
              sharedGroupsIds = listOf(groupId),
              type = DocumentType.FOLDER,
              placements = mapOf(groupId to Placement(folderId = groupId)),
          )

      repository.createFile(file1)
      repository.createFile(file2)
      repository.createFile(fileOtherGroup)
      repository.createFile(fileWithParent)
      repository.createFile(parentFolder)

      val files = repository.getGroupFolderFiles(groupId, groupId).waitFor { it.size == 3 }

      assertEquals("Should retrieve exactly 3 items for the group at root", 3, files.size)
      assertTrue(files.contains(file1))
      assertTrue(files.contains(file2))
      assertTrue(files.contains(parentFolder))
      assertTrue(files.none { it.id == fileWithParent.id })
    }
  }

  /** Ensures that an empty list is emitted if a campaign group has no root documents. */
  @Test
  fun testGetGroupRootFiles_Empty() {
    runBlocking {
      val emptyGroupId = "empty_group_id"
      val files =
          repository.getGroupFolderFiles(emptyGroupId, emptyGroupId).waitFor { it.isEmpty() }
      assertTrue("Should retrieve empty list when no group root files exist", files.isEmpty())
    }
  }

  /** Tests retrieving all documents located within a specific folder. */
  @Test
  fun testGetGroupFolderFiles() {
    runBlocking {
      val folderId = UUID.randomUUID().toString()
      val groupId = UUID.randomUUID().toString()
      val file1 =
          JDRFile(
              id = UUID.randomUUID().toString(),
              sharedGroupsIds = listOf(groupId),
              placements = mapOf(groupId to Placement(folderId = folderId)),
              name = "Child File 1",
          )
      val file2 =
          JDRFile(
              id = UUID.randomUUID().toString(),
              sharedGroupsIds = listOf(groupId),
              placements =
                  mapOf(
                      groupId to Placement(folderId = folderId),
                      "otherGroup" to Placement(folderId = "otherFolder"),
                  ),
              name = "Child File 2",
          )
      val fileOtherFolder =
          JDRFile(
              id = UUID.randomUUID().toString(),
              sharedGroupsIds = listOf(groupId),
              placements = mapOf(groupId to Placement(folderId = "otherFolder")),
              name = "Other Folder File",
          )
      val fileRoot =
          JDRFile(
              id = UUID.randomUUID().toString(),
              sharedGroupsIds = listOf(groupId),
              placements = mapOf(groupId to Placement(folderId = groupId)),
              name = "Root File",
          )

      repository.createFile(file1)
      repository.createFile(file2)
      repository.createFile(fileOtherFolder)
      repository.createFile(fileRoot)

      val files = repository.getGroupFolderFiles(folderId, groupId).waitFor { it.size == 2 }

      assertEquals("Should retrieve exactly 2 files for the folder", 2, files.size)
      assertTrue(files.contains(file1))
      assertTrue(files.contains(file2))
    }
  }

  /** Ensures that an empty list is emitted if a folder contains no documents. */
  @Test
  fun testGetGroupFolderFiles_Empty() {
    runBlocking {
      val emptyFolderId = "empty_folder_id"
      val emptyGroupId = "empty_group_id"
      val files =
          repository.getGroupFolderFiles(emptyFolderId, emptyGroupId).waitFor { it.isEmpty() }
      assertTrue("Should retrieve empty list when no documents in folder", files.isEmpty())
    }
  }

  /**
   * Tests the reactivity of dynamic lists (shared workspace folders). If a Game Master adds or
   * removes a document from a folder, the interface should instantly update for all players.
   */
  @Test
  fun testGetGroupFolderFiles_Updates() {
    runBlocking {
      val folderId = UUID.randomUUID().toString()
      val groupId = UUID.randomUUID().toString()
      val file1 =
          JDRFile(
              id = UUID.randomUUID().toString(),
              sharedGroupsIds = listOf(groupId),
              placements = mapOf(groupId to Placement(folderId = folderId)),
              name = "File 1",
          )
      repository.createFile(file1)

      val latestList = AtomicReference(emptyList<JDRFile>())
      val hasEmitted = AtomicBoolean(false)
      val job =
          launch(Dispatchers.Default) {
            repository.getGroupFolderFiles(folderId, groupId).collect {
              latestList.set(it)
              hasEmitted.set(true)
            }
          }
      FirebaseEmulatorTestUtils.pumpMainLooperUntil {
        hasEmitted.get() && latestList.get().size == 1
      }
      assertEquals("List should have 1 item initially", 1, latestList.get().size)

      // Add another file dynamically
      val file2 =
          JDRFile(
              id = UUID.randomUUID().toString(),
              sharedGroupsIds = listOf(groupId),
              placements = mapOf(groupId to Placement(folderId = folderId)),
              name = "File 2",
          )
      repository.createFile(file2)

      FirebaseEmulatorTestUtils.pumpMainLooperUntil { latestList.get().size == 2 }
      assertEquals("List should be updated to 2 items", 2, latestList.get().size)

      // Remove the first file from the folder (written directly: updateFile no longer touches
      // placements)
      persistFiles(file1.copy(sharedGroupsIds = emptyList(), placements = emptyMap()))

      FirebaseEmulatorTestUtils.pumpMainLooperUntil {
        latestList.get().size == 1 && latestList.get().first().id == file2.id
      }
      assertEquals("List should be updated to 1 item after removal", 1, latestList.get().size)
      assertEquals("Remaining file should be File 2", file2.id, latestList.get().first().id)

      job.cancel()
    }
  }

  /**
   * Simulates offline behavior using Cloud Firestore's native cache. Ensures the app can read data
   * gracefully without a network connection.
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

  /**
   * Tests that if a document is corrupted in Firestore (e.g., incorrect types causing a
   * RuntimeException during deserialization), the application does not crash. Instead, the corrupt
   * document is simply ignored in the emitted list.
   */
  @Test
  fun testDeserializationError_IsCaught() {
    runBlocking {
      val corruptedData =
          hashMapOf(
              "ownerId" to "corrupted_owner",
              "personalParentId" to "",
              "placements" to 12345,
          )
      firestore.collection("documents").document("corrupted_doc").set(corruptedData).await()

      // Listen to the corrupted folder. The catch block will log the error and ignore the file
      // instead of crashing the entire Flow.
      val files = repository.getPersonalRootFiles("corrupted_owner").waitFor { true }

      // Assert the list is empty because the only document was corrupted and thus ignored.
      assertTrue("List should be empty due to parsing failure", files.isEmpty())
    }
  }

  /**
   * Verifies that Kotlin Coroutines' CancellationException is properly rethrown when an active
   * coroutine is canceled. If it were incorrectly swallowed by a generic catch(e: Exception),
   * coroutines would continue executing in the background, causing memory leaks.
   */
  @Test
  fun testCoroutinesCancellation_IsPropagated() {
    runBlocking {
      val file = JDRFile(id = "cancel_test")

      // Launch the creation in a separate Job so we can cancel it mid-flight
      val job = launch {
        try {
          repository.createFile(file)
        } catch (e: CancellationException) {
          // This confirms our 'catch (e: CancellationException) { throw e }' works perfectly
          throw e
        }
      }
      // yield() suspends this main coroutine and forces the dispatcher
      // to execute the 'job' launched just above.
      // The job will call Firebase and suspend on the await().
      // Since we are in Robolectric and not actively pumping the MainLooper,
      // the job will remain frozen there. Control is immediately returned to us.
      // This makes the cancellation 100% deterministic without relying on arbitrary delays.
      yield()

      // Cancel the job immediately before Firestore finishes the network call
      job.cancel()
      job.join()

      assertTrue("Job should be successfully cancelled", job.isCancelled)
    }
  }

  /**
   * Tests that when a user lacks the necessary permissions to read a Firestore document or
   * collection, the Flow correctly closes and surfaces a Permission Denied error to the collector.
   *
   * We use the custom 'waitFor' method to pump Robolectric's main looper and avoid deadlocks. The
   * condition '{ false }' ensures the Flow runs until the permission error is forcefully thrown.
   */
  @Test
  fun testSnapshotListeners_PermissionDenied() = runBlocking {
    val firestoreMock = mockk<FirebaseFirestore>()
    val collectionMock = mockk<CollectionReference>()
    val documentMock = mockk<DocumentReference>()
    val registrationMock = mockk<ListenerRegistration>(relaxed = true)

    every { firestoreMock.collection("documents") } returns collectionMock
    every { collectionMock.document("access_denied_test") } returns documentMock

    every { documentMock.addSnapshotListener(any<EventListener<DocumentSnapshot>>()) } answers
        {
          val listener = firstArg<EventListener<DocumentSnapshot>>()
          listener.onEvent(
              null,
              FirebaseFirestoreException(
                  "Simulated Permission Denied",
                  FirebaseFirestoreException.Code.PERMISSION_DENIED,
              ),
          )
          registrationMock
        }

    val mockRepository = WorkspaceRepositoryFirestore(firestoreMock)

    try {
      mockRepository.getFileFlow("access_denied_test").collect {}
      fail("getFileFlow should have thrown a permission-denied exception")
    } catch (e: FirebaseFirestoreException) {
      assertEquals(
          FirebaseFirestoreException.Code.PERMISSION_DENIED,
          e.code,
      )
    }
  }

  /**
   * Tests that list-based snapshot listeners correctly close the Flow and propagate Permission
   * Denied errors. We use MockK here because Firestore security rules cannot securely target
   * specific list queries without breaking normal behavior.
   */
  @Test
  fun testListListeners_PermissionDenied_WithMockK() = runBlocking {
    // 1. Create the mocks for Firestore components
    val firestoreMock = mockk<FirebaseFirestore>()
    val collectionMock = mockk<CollectionReference>()
    val queryMock = mockk<Query>()
    val registrationMock = mockk<ListenerRegistration>(relaxed = true)

    // 2. Set up the default routing for the documents collection
    every { firestoreMock.collection("documents") } returns collectionMock

    // Mocking the 'getPersonalRootFiles' query chain
    every { collectionMock.whereEqualTo("ownerId", "error_owner_test") } returns queryMock
    every { queryMock.whereEqualTo("personalParentId", "") } returns queryMock

    // Mocking the 'getGroupFolderFiles' query chain
    every { collectionMock.whereArrayContains("sharedGroupsIds", "error_group_test") } returns
        queryMock
    every {
      queryMock.whereEqualTo("placements.error_group_test.folderId", "error_group_test")
    } returns queryMock

    // Mocking the 'getPersonalFolderFiles' query chain
    every { queryMock.whereEqualTo("personalParentId", "error_folder_test") } returns queryMock

    // 3. Intercept 'addSnapshotListener' and instantly trigger a PERMISSION_DENIED error
    every { queryMock.addSnapshotListener(any()) } answers
        {
          val listener = firstArg<EventListener<QuerySnapshot>>()
          listener.onEvent(
              null, // No snapshot
              FirebaseFirestoreException(
                  "Simulated Permission Denied",
                  FirebaseFirestoreException.Code.PERMISSION_DENIED,
              ),
          )
          registrationMock
        }

    // Initialize a separate repository instance injected with our mock
    val mockRepository = WorkspaceRepositoryFirestore(firestoreMock)

    // 4. Assert that getPersonalRootFiles correctly catches and propagates the error
    try {
      mockRepository.getPersonalRootFiles("error_owner_test").collect {}
      fail("getPersonalRootFiles should have thrown an exception")
    } catch (e: Exception) {
      assertTrue(e.message?.contains("Simulated Permission Denied") == true)
    }

    // 5. Assert that getGroupFolderFiles correctly catches and propagates the error
    try {
      mockRepository.getGroupFolderFiles("error_group_test", "error_group_test").collect {}
      fail("getGroupFolderFiles should have thrown an exception")
    } catch (e: Exception) {
      assertTrue(e.message?.contains("Simulated Permission Denied") == true)
    }

    // 6. Assert that getPersonalFolderFiles correctly catches and propagates the error
    try {
      mockRepository.getPersonalFolderFiles("error_folder_test", "error_owner_test").collect {}
      fail("getPersonalFolderFiles should have thrown an exception")
    } catch (e: Exception) {
      assertTrue(e.message?.contains("Simulated Permission Denied") == true)
    }
  }
}
