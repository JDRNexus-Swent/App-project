// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.model.personalSpace

import com.github.se.jdrnexus.utils.FirebaseEmulatorTestUtils
import com.github.se.jdrnexus.utils.FirebaseEmulatorTestUtils.pumpMainLooperUntil
import com.github.se.jdrnexus.utils.FirebaseEmulatorTestUtils.runAndPumpMainLooper
import com.github.se.jdrnexus.utils.waitFor
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLog

/**
 * Personal-space writes of [WorkspaceRepositoryFirestore] while the device is offline: they must
 * complete from the local cache, be visible at once through the repository's reads, and reach the
 * server on reconnection.
 *
 * Uses the Firestore emulator with the project security rules; start it with "firebase
 * emulators:exec --only auth,firestore". The server state is read bypassing the client
 * ([FirebaseEmulatorTestUtils.readBypassingRules]), so it never comes from the local cache.
 */
@RunWith(RobolectricTestRunner::class)
class WorkspaceRepositoryFirestoreOfflineTest {

  private lateinit var repository: WorkspaceRepository
  private lateinit var firestore: FirebaseFirestore

  @Before
  fun setUp() {
    firestore = FirebaseEmulatorTestUtils.signInAs(USER_ID)
    repository = WorkspaceRepositoryFirestore(firestore)
  }

  @After
  fun tearDown() {
    // tearDown re-enables the network first, so a test failing while offline leaves no trace.
    runBlocking { FirebaseEmulatorTestUtils.tearDown(firestore, "documents") }
  }

  private fun folder(id: String, parent: String = PERSONAL_ROOT_ID, name: String = "Folder") =
      JDRFile(
          id = id,
          name = name,
          ownerId = USER_ID,
          personalParentId = parent,
          type = DocumentType.FOLDER,
      )

  private fun text(id: String, parent: String = PERSONAL_ROOT_ID, content: String = "") =
      JDRFile(
          id = id,
          name = "Notes",
          ownerId = USER_ID,
          personalParentId = parent,
          content = content,
      )

  /** Stores [files] on the server, then reads each through the repository so it is cached. */
  private suspend fun storeAndCache(vararg files: JDRFile) {
    files.forEach {
      FirebaseEmulatorTestUtils.writeBypassingRules(firestore, "documents/${it.id}", it)
    }
    files.forEach { assertEquals(it, repository.getFile(it.id).getOrThrow()) }
  }

  /** What the server stores for [id], read bypassing the client and its cache. */
  private suspend fun onServer(id: String): JDRFile? =
      FirebaseEmulatorTestUtils.readBypassingRules(firestore, "documents/$id", JDRFile::class.java)

  /** Runs a repository call that must complete within [OFFLINE_TIMEOUT_MS]. */
  private suspend fun <T> promptly(operationName: String, block: suspend () -> T): T =
      runAndPumpMainLooper(operationName, OFFLINE_TIMEOUT_MS, block)

  private suspend fun personalFolder(folderId: String, condition: (List<JDRFile>) -> Boolean) =
      repository.getPersonalFolderFiles(folderId, USER_ID).waitFor(condition = condition)

  /**
   * The Firestore error code of [result]. Offline, a read of a document deleted on the device fails
   * with UNAVAILABLE ("client is offline"), like a document that was never cached: only the server
   * can say it is gone.
   */
  private fun errorCode(result: Result<JDRFile?>) =
      (result.exceptionOrNull() as? FirebaseFirestoreException)?.code

  private suspend fun reconnectAndSync() {
    firestore.enableNetwork().await()
    firestore.waitForPendingWrites().await()
  }

  @Test
  fun createFileCompletesOfflineAndReachesTheServerOnReconnection() = runBlocking {
    val created = folder(repository.getNewUid(), name = "Made offline")
    firestore.disableNetwork().await()

    val result = promptly("createFile") { repository.createFile(created) }

    assertEquals(Result.success(Unit), result)
    assertEquals(created, promptly("getFile") { repository.getFile(created.id).getOrThrow() })
    assertEquals(listOf(created), personalFolder(PERSONAL_ROOT_ID) { it.isNotEmpty() })
    assertNull(onServer(created.id))

    reconnectAndSync()

    assertEquals(created, onServer(created.id))
  }

  @Test
  fun updateFileCompletesOfflineAndReachesTheServerOnReconnection() = runBlocking {
    val stored = text("file", content = "old")
    storeAndCache(stored)
    firestore.disableNetwork().await()

    val result = promptly("updateFile") { repository.updateFile(FileEdit("file", "Edited", "new")) }

    val edited = stored.copy(name = "Edited", content = "new")
    assertEquals(Result.success(Unit), result)
    assertEquals(edited, promptly("getFile") { repository.getFile("file").getOrThrow() })
    assertEquals(stored, onServer("file"))

    reconnectAndSync()

    assertEquals(edited, onServer("file"))
  }

  /** The Personal Space renames a folder by reading it, then saving it with its new name. */
  @Test
  fun renamingAFolderCompletesOfflineAndKeepsItsContent() = runBlocking {
    val stored = folder("folder", name = "Old name")
    val child = text("child", parent = "folder")
    storeAndCache(stored, child)
    firestore.disableNetwork().await()

    val result =
        promptly("rename") {
          val existing = repository.getFile("folder").getOrThrow()!!
          repository.updateFile(existing.copy(name = "New name").toEdit())
        }

    val renamed = stored.copy(name = "New name")
    assertEquals(Result.success(Unit), result)
    assertEquals(listOf(renamed), personalFolder(PERSONAL_ROOT_ID) { it == listOf(renamed) })
    assertEquals(listOf(child), personalFolder("folder") { it.isNotEmpty() })
    assertEquals(stored, onServer("folder"))

    reconnectAndSync()

    assertEquals(renamed, onServer("folder"))
    assertEquals(child, onServer("child"))
  }

  @Test
  fun deletingAFileCompletesOfflineAndReachesTheServerOnReconnection() = runBlocking {
    val stored = text("file")
    storeAndCache(stored)
    firestore.disableNetwork().await()

    val result = promptly("deleteFile") { repository.deleteFile("file") }

    assertEquals(Result.success(Unit), result)
    assertEquals(
        FirebaseFirestoreException.Code.UNAVAILABLE,
        promptly("getFile") { errorCode(repository.getFile("file")) },
    )
    assertEquals(stored, onServer("file"))

    reconnectAndSync()

    assertNull(onServer("file"))
  }

  @Test
  fun deletingAFolderOfflineDeletesItsCachedContentAndReachesTheServerOnReconnection() =
      runBlocking {
        val kept = text("kept")
        storeAndCache(
            folder("folder"),
            folder("sub", parent = "folder"),
            text("deep", parent = "sub"),
            kept,
        )
        firestore.disableNetwork().await()

        val result = promptly("deleteFile") { repository.deleteFile("folder") }

        assertEquals(Result.success(Unit), result)
        assertEquals(listOf(kept), personalFolder(PERSONAL_ROOT_ID) { it == listOf(kept) })
        listOf("folder", "sub", "deep").forEach {
          assertEquals(
              it,
              FirebaseFirestoreException.Code.UNAVAILABLE,
              promptly("getFile") { errorCode(repository.getFile(it)) },
          )
        }

        reconnectAndSync()

        listOf("folder", "sub", "deep").forEach { assertNull(it, onServer(it)) }
        assertEquals(kept, onServer("kept"))
      }

  @Test
  fun hasPendingWritesIsTrueAfterAnOfflineWriteAndFalseOnceSynced() = runBlocking {
    firestore.disableNetwork().await()
    promptly("createFile") { repository.createFile(text(repository.getNewUid())) }.getOrThrow()

    assertTrue(promptly("hasPendingWrites") { repository.hasPendingWrites() })

    reconnectAndSync()

    assertFalse(promptly("hasPendingWrites") { repository.hasPendingWrites() })
  }

  @Test
  fun hasPendingWritesIsFalseWhenNothingWasWritten() = runBlocking {
    assertFalse(promptly("hasPendingWrites") { repository.hasPendingWrites() })
  }

  @Test
  fun getFileOfAnUncachedDocumentFailsPromptlyOffline() = runBlocking {
    FirebaseEmulatorTestUtils.writeBypassingRules(firestore, "documents/uncached", text("uncached"))
    firestore.disableNetwork().await()

    val result = promptly("getFile") { repository.getFile("uncached") }

    // The cache cannot tell whether it exists: the same error as before the cache fallback.
    assertEquals(
        FirebaseFirestoreException.Code.UNAVAILABLE,
        (result.exceptionOrNull() as? FirebaseFirestoreException)?.code,
    )
  }

  @Test
  fun createFileStillReturnsTheRulesDenialOnline() = runBlocking {
    val forbidden = text(repository.getNewUid()).copy(ownerId = OTHER_USER_ID)

    val result = promptly("createFile") { repository.createFile(forbidden) }

    assertEquals(
        FirebaseFirestoreException.Code.PERMISSION_DENIED,
        (result.exceptionOrNull() as? FirebaseFirestoreException)?.code,
    )
    assertNull(onServer(forbidden.id))
  }

  /** A write the server rejects after the call returned offline is logged, not swallowed. */
  @Test
  fun anOfflineWriteRejectedOnSyncIsLogged() = runBlocking {
    val forbidden = text(repository.getNewUid()).copy(ownerId = OTHER_USER_ID)
    firestore.disableNetwork().await()
    val result = promptly("createFile") { repository.createFile(forbidden) }
    assertEquals(Result.success(Unit), result)

    firestore.enableNetwork().await()

    val expected = "createFile(${forbidden.id}) was rejected when syncing"
    pumpMainLooperUntil(timeoutMessage = { "No log for the rejected write" }) {
      ShadowLog.getLogsForTag(LOG_TAG).any { it.msg == expected }
    }
    assertNull(onServer(forbidden.id))
  }

  /**
   * A slow server, which the emulator cannot simulate: the write of [id] is only answered, with a
   * refusal, when the returned function is called.
   */
  private fun slowServerRefusing(id: String): Pair<WorkspaceRepository, () -> Unit> {
    val answer = TaskCompletionSource<Void>()
    val firestoreMock = mockk<FirebaseFirestore>()
    val collectionMock = mockk<CollectionReference>()
    val documentMock = mockk<DocumentReference>()
    every { firestoreMock.collection("documents") } returns collectionMock
    every { collectionMock.document(id) } returns documentMock
    every { documentMock.set(any()) } returns answer.task
    every { documentMock.update(any<Map<String, Any>>()) } returns answer.task
    val refuse = {
      answer.setException(
          FirebaseFirestoreException("refused", FirebaseFirestoreException.Code.PERMISSION_DENIED)
      )
    }
    return WorkspaceRepositoryFirestore(firestoreMock) to refuse
  }

  /**
   * Runs [write], checks that it is still waiting well past the personal-space time limit, then
   * refuses it: [write] must return that refusal.
   */
  private suspend fun assertLateRefusalIsReturned(
      refuse: () -> Unit,
      write: suspend () -> Result<Unit>,
  ) {
    val operation = CoroutineScope(Dispatchers.Default).async { write() }

    val early =
        withTimeoutOrNull(WorkspaceRepositoryFirestore.SERVER_ACK_GRACE_MS * 2) {
          operation.await()
        }
    refuse()

    assertNull(early)
    assertEquals(
        FirebaseFirestoreException.Code.PERMISSION_DENIED,
        (operation.await().exceptionOrNull() as? FirebaseFirestoreException)?.code,
    )
  }

  @Test
  fun createFileWaitingForTheServerReturnsARefusalThatComesLate() = runBlocking {
    val (slowRepository, refuse) = slowServerRefusing("file")

    assertLateRefusalIsReturned(refuse) {
      slowRepository.createFile(text("file"), waitForServer = true)
    }
  }

  @Test
  fun updateFileWaitingForTheServerReturnsARefusalThatComesLate() = runBlocking {
    val (slowRepository, refuse) = slowServerRefusing("file")

    assertLateRefusalIsReturned(refuse) {
      slowRepository.updateFile(FileEdit("file", "Edited", ""), waitForServer = true)
    }
  }

  /**
   * A server that does not answer the read of [id] until [SlowRead.server] is completed, and a
   * local cache that answers with [cacheAnswer]. The emulator always answers in time, so this path
   * needs mocks.
   */
  private class SlowRead(id: String, cacheAnswer: Task<DocumentSnapshot>) {
    val server = TaskCompletionSource<DocumentSnapshot>()
    val repository: WorkspaceRepository

    init {
      val firestoreMock = mockk<FirebaseFirestore>()
      val collectionMock = mockk<CollectionReference>()
      val documentMock = mockk<DocumentReference>()
      every { firestoreMock.collection("documents") } returns collectionMock
      every { collectionMock.document(id) } returns documentMock
      every { documentMock.get() } returns server.task
      every { documentMock.get(Source.CACHE) } returns cacheAnswer
      repository = WorkspaceRepositoryFirestore(firestoreMock)
    }
  }

  /** A snapshot of an existing document holding [file]. */
  private fun snapshotOf(file: JDRFile): DocumentSnapshot {
    val snapshot = mockk<DocumentSnapshot>()
    every { snapshot.exists() } returns true
    every { snapshot.toObject(JDRFile::class.java) } returns file
    return snapshot
  }

  /** How long to wait to be sure the read has gone past its time limit. */
  private val pastTheReadLimitMs = WorkspaceRepositoryFirestore.SERVER_READ_TIMEOUT_MS + 1_000

  @Test
  fun getFileAnswersFromTheCacheWhenTheServerIsSlow() = runBlocking {
    val cachedFile = text("file", content = "cached")
    val read = SlowRead("file", Tasks.forResult(snapshotOf(cachedFile)))

    val result = withTimeoutOrNull(pastTheReadLimitMs) { read.repository.getFile("file") }

    assertEquals(Result.success(cachedFile), result)
    assertFalse(read.server.task.isComplete)
  }

  @Test
  fun getFileKeepsWaitingForTheServerWhenTheCacheDoesNotHaveTheDocument() = runBlocking {
    val cacheMiss =
        FirebaseFirestoreException(
            "Failed to get document from cache.",
            FirebaseFirestoreException.Code.UNAVAILABLE,
        )
    val read = SlowRead("file", Tasks.forException(cacheMiss))
    val operation = CoroutineScope(Dispatchers.Default).async { read.repository.getFile("file") }

    val beforeTheServerAnswers = withTimeoutOrNull(pastTheReadLimitMs) { operation.await() }
    val serverFile = text("file", content = "from the server")
    read.server.setResult(snapshotOf(serverFile))

    assertNull(beforeTheServerAnswers)
    assertEquals(Result.success(serverFile), operation.await())
  }

  @Test
  fun getFileReturnsACacheErrorOtherThanAMiss() = runBlocking {
    val cacheError =
        FirebaseFirestoreException("Cache corrupted.", FirebaseFirestoreException.Code.INTERNAL)
    val read = SlowRead("file", Tasks.forException(cacheError))

    val result = withTimeoutOrNull(pastTheReadLimitMs) { read.repository.getFile("file") }

    assertEquals(Result.failure<JDRFile?>(cacheError), result)
    assertFalse(read.server.task.isComplete)
  }

  private companion object {
    const val USER_ID = "A"
    const val OTHER_USER_ID = "B"
    const val LOG_TAG = "WorkspaceRepository"

    /** Offline, every call must return within this time instead of waiting for the server. */
    const val OFFLINE_TIMEOUT_MS = 2_000L
  }
}
