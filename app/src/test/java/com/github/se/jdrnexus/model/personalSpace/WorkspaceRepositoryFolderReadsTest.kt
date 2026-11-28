// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.model.personalSpace

import com.github.se.jdrnexus.utils.FirebaseEmulatorTestUtils
import com.github.se.jdrnexus.utils.FirebaseEmulatorTestUtils.runAndPumpMainLooper
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * One-shot folder reads of [WorkspaceRepositoryFirestore]
 * ([WorkspaceRepository.fetchPersonalFolderFiles] and [WorkspaceRepository.fetchGroupFolderFiles]),
 * used by the document search. A listener's first value can come from the local cache alone; these
 * reads must return every document of the folder, even when only some of them are cached.
 *
 * Uses the Firestore emulator with the project security rules; start it with "firebase
 * emulators:exec --only auth,firestore".
 */
@RunWith(RobolectricTestRunner::class)
class WorkspaceRepositoryFolderReadsTest {

  private lateinit var repository: WorkspaceRepository
  private lateinit var firestore: FirebaseFirestore

  @Before
  fun setUp() {
    firestore = FirebaseEmulatorTestUtils.signInAs(USER_ID)
    repository = WorkspaceRepositoryFirestore(firestore)
  }

  @After
  fun tearDown() {
    runBlocking { FirebaseEmulatorTestUtils.tearDown(firestore, "documents") }
  }

  private fun personal(id: String, name: String, parent: String, type: DocumentType) =
      JDRFile(id = id, name = name, ownerId = USER_ID, personalParentId = parent, type = type)

  private fun inGroup(id: String, name: String, folderId: String, type: DocumentType) =
      JDRFile(
          id = id,
          name = name,
          ownerId = USER_ID,
          type = type,
          sharedGroupsIds = listOf(GROUP_ID),
          placements = mapOf(GROUP_ID to Placement(folderId = folderId)),
      )

  /** Stores [files] on the server only, bypassing the client and its cache. */
  private suspend fun storeOnServer(vararg files: JDRFile) = files.forEach {
    FirebaseEmulatorTestUtils.writeBypassingRules(firestore, "documents/${it.id}", it)
  }

  /** Reads [file] through the repository, so this device caches it, as opening it would. */
  private suspend fun cache(file: JDRFile) =
      assertEquals(
          file,
          runAndPumpMainLooper("getFile(${file.id})", READ_TIMEOUT_MS) {
            repository.getFile(file.id).getOrThrow()
          },
      )

  @Test
  fun fetchPersonalFolderFilesReturnsEveryChildOfAPartlyCachedFolder() = runBlocking {
    val folder = personal("quests", "Quests", PERSONAL_ROOT_ID, DocumentType.FOLDER)
    val cachedFile = personal("a-cached", "Dragon lair", folder.id, DocumentType.TEXT)
    val serverOnlyFile = personal("b-server", "Dragon hunt", folder.id, DocumentType.CHARACTER)
    storeOnServer(folder, cachedFile, serverOnlyFile)
    cache(cachedFile)

    val children =
        runAndPumpMainLooper("fetchPersonalFolderFiles", READ_TIMEOUT_MS) {
          repository.fetchPersonalFolderFiles(folder.id, USER_ID)
        }

    assertEquals(listOf(cachedFile, serverOnlyFile), children)
  }

  @Test
  fun fetchPersonalFolderFilesSkipsGroupRootsAndOtherOwnersFiles() = runBlocking {
    val file = personal("file", "Notes", PERSONAL_ROOT_ID, DocumentType.TEXT)
    val groupRoot = personal("root", "Campaign", PERSONAL_ROOT_ID, DocumentType.GROUP_ROOT)
    val otherOwnersFile =
        personal("other", "Their notes", PERSONAL_ROOT_ID, DocumentType.TEXT)
            .copy(ownerId = OTHER_USER_ID)
    storeOnServer(file, groupRoot, otherOwnersFile)

    val children =
        runAndPumpMainLooper("fetchPersonalFolderFiles", READ_TIMEOUT_MS) {
          repository.fetchPersonalFolderFiles(PERSONAL_ROOT_ID, USER_ID)
        }

    assertEquals(listOf(file), children)
  }

  @Test
  fun fetchPersonalFolderFilesServesTheCachedChildrenOffline() = runBlocking {
    val folder = personal("quests", "Quests", PERSONAL_ROOT_ID, DocumentType.FOLDER)
    val first = personal("a-first", "Dragon lair", folder.id, DocumentType.TEXT)
    val second = personal("b-second", "Dragon hunt", folder.id, DocumentType.TEXT)
    storeOnServer(folder, first, second)
    runAndPumpMainLooper("fetch online", READ_TIMEOUT_MS) {
      repository.fetchPersonalFolderFiles(folder.id, USER_ID)
    }
    runAndPumpMainLooper("disableNetwork", READ_TIMEOUT_MS) { firestore.disableNetwork().await() }

    val children =
        runAndPumpMainLooper("fetch offline", READ_TIMEOUT_MS) {
          repository.fetchPersonalFolderFiles(folder.id, USER_ID)
        }

    assertEquals(listOf(first, second), children)
  }

  @Test
  fun fetchGroupFolderFilesReturnsEveryChildOfAPartlyCachedFolderSortedByName() = runBlocking {
    FirebaseEmulatorTestUtils.writeUserGroupIdsBypassingRules(firestore, USER_ID, GROUP_ID)
    val folder = inGroup("maps", "Maps", GROUP_ID, DocumentType.FOLDER)
    val cachedFile = inGroup("a-cached", "Zebra pass", folder.id, DocumentType.TEXT)
    val serverOnlyFile = inGroup("b-server", "Dragon hunt", folder.id, DocumentType.TEXT)
    storeOnServer(folder, cachedFile, serverOnlyFile)
    cache(cachedFile)

    val children =
        runAndPumpMainLooper("fetchGroupFolderFiles", READ_TIMEOUT_MS) {
          repository.fetchGroupFolderFiles(folder.id, GROUP_ID)
        }

    assertEquals(listOf(serverOnlyFile, cachedFile), children)
  }

  private companion object {
    const val USER_ID = "A"
    const val OTHER_USER_ID = "B"
    const val GROUP_ID = "group"

    // The first query of a run can take a few seconds while the emulator connection warms up.
    const val READ_TIMEOUT_MS = 30_000L
  }
}
