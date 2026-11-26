// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.firestoreRules

import com.github.se.jdrnexus.model.personalSpace.DocumentType
import com.github.se.jdrnexus.model.personalSpace.FileEdit
import com.github.se.jdrnexus.model.personalSpace.JDRFile
import com.github.se.jdrnexus.model.personalSpace.Placement
import com.github.se.jdrnexus.utils.FirebaseEmulatorTestUtils
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Security rules of `documents/{docId}` (files, folders and group roots, see DATA_ARCHITECTURE.md
 * §1 to §8), on the Firestore emulator started with the project rules ("firebase emulators:exec
 * --only auth,firestore"). Each write is the one WorkspaceRepositoryFirestore sends: the whole
 * document on creation, name and content on edit, and targeted placement updates to share, unshare,
 * hide or reattach. The group root's rename and its deletion with the group are in
 * CampaignGroupsRulesTest, since they are batches on the group.
 */
@RunWith(RobolectricTestRunner::class)
class DocumentsRulesTest {

  companion object {
    private const val GM_ID = "gm"
    private const val PLAYER_ID = "player"
    private const val COMPANION_ID = "companion"
    // Signed in, member of no group.
    private const val OUTSIDER_ID = "outsider"
    private const val GROUP_NAME = "The Sunken Crown"
  }

  private val run = UUID.randomUUID().toString()
  // GM gm, participants gm, player and companion.
  private val groupId = "$run-group"
  // GM gm, who is its only participant.
  private val otherGroupId = "$run-other-group"

  // The player's documents. All sit at the root of the player's personal space.
  private val playerFile = "$run-player-file" // TEXT, in the group at its root
  private val playerSheet = "$run-player-sheet" // CHARACTER, in the group under gmFolder
  private val playerPrivate = "$run-player-private" // TEXT, in no group
  private val playerFolder = "$run-player-folder" // FOLDER, in no group
  // The GM's documents.
  private val gmFolder = "$run-gm-folder" // FOLDER, in the group at its root
  private val hiddenFolder = "$run-hidden-folder" // FOLDER, in the group at its root, hidden
  private val gmPrivate = "$run-gm-private" // TEXT, in no group
  private val gmBoth = "$run-gm-both" // TEXT, in the group under gmFolder, and in the other group
  // A document created by a test.
  private val newId = "$run-new"

  private val seededIds =
      listOf(
          groupId,
          otherGroupId,
          playerFile,
          playerSheet,
          playerPrivate,
          playerFolder,
          gmFolder,
          hiddenFolder,
          gmPrivate,
          gmBoth,
      )

  // Signed in as the GM; also used to seed and check data bypassing the rules.
  private lateinit var gm: FirebaseFirestore
  private lateinit var player: FirebaseFirestore
  private lateinit var companion: FirebaseFirestore
  private lateinit var outsider: FirebaseFirestore

  private fun FirebaseFirestore.doc(id: String) = collection("documents").document(id)

  private fun placement(folderId: String, isHidden: Boolean = false) =
      mapOf("folderId" to folderId, "isHidden" to isHidden)

  // A document as stored: sharedGroupsIds follows the keys of placements.
  private fun document(
      id: String,
      name: String = "Notes",
      ownerId: String = PLAYER_ID,
      type: String = "TEXT",
      personalParentId: String = "",
      placements: Map<String, Map<String, Any>> = emptyMap(),
      sharedGroupsIds: List<String> = placements.keys.toList(),
  ) =
      mapOf(
          "id" to id,
          "name" to name,
          "ownerId" to ownerId,
          "sharedGroupsIds" to sharedGroupsIds,
          "placements" to placements,
          "personalParentId" to personalParentId,
          "type" to type,
          "content" to "",
      )

  private suspend fun seed(id: String, data: Map<String, Any>) =
      FirebaseEmulatorTestUtils.writeBypassingRules(gm, "documents/$id", data)

  private suspend fun seedGroup(id: String, participantIds: List<String>) =
      FirebaseEmulatorTestUtils.writeGroupBypassingRules(
          gm,
          id,
          GM_ID,
          participantIds,
          name = GROUP_NAME,
          withRoot = true,
      )

  @Before
  fun setUp() {
    gm = FirebaseEmulatorTestUtils.signInAs(GM_ID)
    player = FirebaseEmulatorTestUtils.signInAs(PLAYER_ID)
    companion = FirebaseEmulatorTestUtils.signInAs(COMPANION_ID)
    outsider = FirebaseEmulatorTestUtils.signInAs(OUTSIDER_ID)
    runBlocking {
      seedGroup(groupId, listOf(GM_ID, PLAYER_ID, COMPANION_ID))
      seedGroup(otherGroupId, listOf(GM_ID))
      FirebaseEmulatorTestUtils.writeUserGroupIdsBypassingRules(gm, GM_ID, groupId, otherGroupId)
      FirebaseEmulatorTestUtils.writeUserGroupIdsBypassingRules(gm, PLAYER_ID, groupId)
      FirebaseEmulatorTestUtils.writeUserGroupIdsBypassingRules(gm, COMPANION_ID, groupId)
      seed(playerFile, document(playerFile, placements = mapOf(groupId to placement(groupId))))
      seed(
          playerSheet,
          document(
              playerSheet,
              type = "CHARACTER",
              placements = mapOf(groupId to placement(gmFolder)),
          ),
      )
      seed(playerPrivate, document(playerPrivate))
      seed(playerFolder, document(playerFolder, type = "FOLDER"))
      seed(
          gmFolder,
          document(
              gmFolder,
              ownerId = GM_ID,
              type = "FOLDER",
              placements = mapOf(groupId to placement(groupId)),
          ),
      )
      seed(
          hiddenFolder,
          document(
              hiddenFolder,
              ownerId = GM_ID,
              type = "FOLDER",
              placements = mapOf(groupId to placement(groupId, isHidden = true)),
          ),
      )
      seed(gmPrivate, document(gmPrivate, ownerId = GM_ID))
      seed(
          gmBoth,
          document(
              gmBoth,
              ownerId = GM_ID,
              placements =
                  mapOf(groupId to placement(gmFolder), otherGroupId to placement(otherGroupId)),
          ),
      )
    }
  }

  @After
  fun tearDown() = runBlocking {
    FirebaseEmulatorTestUtils.tearDown(gm, "documents", "campaign_groups", "users")
  }

  private suspend fun storedIds() =
      FirebaseEmulatorTestUtils.documentIdsBypassingRules(gm, "documents")

  // A field of the document (a dotted path), read bypassing the rules, so the check does not
  // depend on them; null if missing.
  private suspend fun stored(id: String, field: String): Any? =
      field.split('.').fold<String, Any?>(
          FirebaseEmulatorTestUtils.readBypassingRules(gm, "documents/$id", Map::class.java)
      ) { value, key ->
        (value as Map<*, *>?)?.get(key)
      }

  // ========================================== get

  @Test
  fun onlyTheOwnerAndTheGroupsMembersCanGetADocument() = runBlocking {
    assertEquals(playerPrivate, player.doc(playerPrivate).get().await().id)
    assertEquals("Notes", companion.doc(playerFile).get().await().getString("name"))
    // Hiding is not a security boundary (DATA_ARCHITECTURE.md §5): members can read hidden ones.
    assertEquals("FOLDER", companion.doc(hiddenFolder).get().await().getString("type"))

    FirebaseEmulatorTestUtils.assertPermissionDenied("a member, on a document in no group") {
      companion.doc(playerPrivate).get().await()
    }
    FirebaseEmulatorTestUtils.assertPermissionDenied("an outsider, on a group's document") {
      outsider.doc(playerFile).get().await()
    }
    val signedOut = FirebaseEmulatorTestUtils.signedOutFirestore()
    FirebaseEmulatorTestUtils.assertPermissionDenied("signed out") {
      signedOut.doc(playerPrivate).get().await()
    }
  }

  @Test
  fun aMissingDocumentIdIsNotFound() = runBlocking {
    assertFalse(outsider.doc("$run-missing").get().await().exists())
  }

  // ========================================== list

  @Test
  fun aUserCanListTheirPersonalRootAndTheirGroupRoot() = runBlocking {
    // WorkspaceRepositoryFirestore.getPersonalFolderFiles
    val personal =
        player
            .collection("documents")
            .whereEqualTo("ownerId", PLAYER_ID)
            .whereEqualTo("personalParentId", "")
            .get()
            .await()
    assertEquals(
        listOf(playerFile, playerFolder, playerPrivate, playerSheet),
        personal.documents.map { it.id },
    )

    // WorkspaceRepositoryFirestore.getGroupFolderFiles, at the group root
    val group =
        companion
            .collection("documents")
            .whereArrayContains("sharedGroupsIds", groupId)
            .whereEqualTo("placements.$groupId.folderId", groupId)
            .get()
            .await()
    assertEquals(listOf(gmFolder, hiddenFolder, playerFile), group.documents.map { it.id })
  }

  @Test
  fun queriesThatCouldReturnOthersDocumentsAreDenied() = runBlocking {
    FirebaseEmulatorTestUtils.assertPermissionDenied("no filter") {
      player.collection("documents").get().await()
    }
    FirebaseEmulatorTestUtils.assertPermissionDenied("another user's documents") {
      companion.collection("documents").whereEqualTo("ownerId", PLAYER_ID).get().await()
    }
    FirebaseEmulatorTestUtils.assertPermissionDenied("a group the user is not in") {
      outsider.collection("documents").whereArrayContains("sharedGroupsIds", groupId).get().await()
    }
  }

  // ========================================== create

  // WorkspaceRepositoryFirestore.createFile: the whole JDRFile, built with its group placement.
  @Test
  fun aUserCanCreateDocumentsInTheirSpaceAndInTheirGroup() = runBlocking {
    val created =
        listOf(
            JDRFile(id = "$newId-text", name = "Notes", ownerId = PLAYER_ID),
            JDRFile(
                id = "$newId-sheet",
                name = "Aria",
                ownerId = PLAYER_ID,
                personalParentId = playerFolder,
                type = DocumentType.CHARACTER,
            ),
            // Under a folder of another member: any visible folder of the group can receive it.
            JDRFile(
                    id = "$newId-folder",
                    name = "Maps",
                    ownerId = PLAYER_ID,
                    type = DocumentType.FOLDER,
                )
                .withPlacement(groupId, Placement(gmFolder, isHidden = false)),
            JDRFile(id = "$newId-secret", name = "Plot", ownerId = PLAYER_ID)
                .withPlacement(groupId, Placement(hiddenFolder, isHidden = true)),
        )

    created.forEach { player.doc(it.id).set(it).await() }

    assertEquals((seededIds + created.map { it.id }).sorted(), storedIds())
  }

  @Test
  fun invalidDocumentCreationsAreDenied() = runBlocking {
    listOf(
            "another owner" to document(newId, ownerId = COMPANION_ID),
            "an extra field" to document(newId) + ("tags" to listOf<String>()),
            "a missing field" to document(newId) - "content",
            "a name that is not a string" to document(newId) + ("name" to 5),
            "content that is not a string" to document(newId) + ("content" to 5),
            "an id that is not the document's" to document(newId) + ("id" to "$run-other"),
            "an unknown type" to document(newId, type = "IMAGE"),
            // A root comes only with its group (CampaignGroupsRulesTest).
            "a group root without its group" to
                document(newId, type = "GROUP_ROOT", sharedGroupsIds = listOf(newId)),
            "a personal folder of another user" to document(newId, personalParentId = gmFolder),
            "a personal parent that is a file" to document(newId, personalParentId = playerPrivate),
            "a folder as its own personal parent" to
                document(newId, type = "FOLDER", personalParentId = newId),
            "a group the user is not in" to
                document(newId, placements = mapOf(otherGroupId to placement(otherGroupId))),
            "two groups" to
                document(
                    newId,
                    placements =
                        mapOf(
                            groupId to placement(groupId),
                            otherGroupId to placement(otherGroupId),
                        ),
                ),
            "placements without sharedGroupsIds" to
                document(
                    newId,
                    placements = mapOf(groupId to placement(groupId)),
                    sharedGroupsIds = emptyList(),
                ),
            "a malformed placement" to
                document(newId, placements = mapOf(groupId to placement(groupId) + ("order" to 1))),
            // Hidden, so that only the folder's groups refuse it (a visible item would also fail
            // on the folder's missing placement).
            "a group folder that is not in the group" to
                document(newId, placements = mapOf(groupId to placement(playerFolder, true))),
            "a group parent that is a file" to
                document(newId, placements = mapOf(groupId to placement(playerFile))),
            "visible under a hidden folder" to
                document(newId, placements = mapOf(groupId to placement(hiddenFolder))),
            "a folder as its own group parent" to
                document(newId, type = "FOLDER", placements = mapOf(groupId to placement(newId))),
        )
        .forEach { (case, data) ->
          FirebaseEmulatorTestUtils.assertPermissionDenied(case) {
            player.doc(newId).set(data).await()
          }
        }
    val signedOut = FirebaseEmulatorTestUtils.signedOutFirestore()
    FirebaseEmulatorTestUtils.assertPermissionDenied("signed out") {
      signedOut.doc(newId).set(document(newId)).await()
    }

    assertEquals(seededIds.sorted(), storedIds())
  }

  // ========================================== edit

  // WorkspaceRepositoryFirestore.updateFile
  private suspend fun edit(client: FirebaseFirestore, id: String) =
      client.doc(id).update(FileEdit(id, "Renamed", "New content").toMap()).await()

  @Test
  fun theOwnerCanEditTheNameAndContent() = runBlocking {
    edit(player, playerFile)

    assertEquals("Renamed", stored(playerFile, "name"))
    assertEquals("New content", stored(playerFile, "content"))
  }

  @Test
  fun invalidEditsAreDenied() = runBlocking {
    listOf(
            Triple("the game master, not the owner", gm, playerFile) to
                FileEdit(playerFile, "Renamed", "New content").toMap(),
            Triple("changing the owner", player, playerFile) to mapOf("ownerId" to COMPANION_ID),
            Triple("moving it in the personal space", player, playerFile) to
                mapOf("personalParentId" to playerFolder),
            Triple("a name that is not a string", player, playerFile) to mapOf("name" to 5),
            Triple("content that is not a string", player, playerFile) to mapOf("content" to 5),
            Triple("the group root renamed alone", gm, groupId) to mapOf("name" to "Renamed"),
        )
        .forEach { (target, write) ->
          val (case, client, id) = target
          FirebaseEmulatorTestUtils.assertPermissionDenied(case) {
            client.doc(id).update(write).await()
          }
        }
    // The group's rename is valid, so only the root's own rule refuses.
    FirebaseEmulatorTestUtils.assertPermissionDenied("the group root renamed with its content") {
      val batch = gm.batch()
      batch.update(gm.collection("campaign_groups").document(groupId), "name", "Renamed")
      batch.update(gm.doc(groupId), mapOf("name" to "Renamed", "content" to "Secret"))
      batch.commit().await()
    }

    assertEquals("Notes", stored(playerFile, "name"))
    assertEquals("", stored(playerFile, "content"))
    assertEquals(PLAYER_ID, stored(playerFile, "ownerId"))
    assertEquals("", stored(playerFile, "personalParentId"))
    assertEquals(GROUP_NAME, stored(groupId, "name"))
  }

  @Test
  fun updatingAMissingDocumentGivesNotFound() = runBlocking {
    FirebaseEmulatorTestUtils.assertFirestoreError(FirebaseFirestoreException.Code.NOT_FOUND) {
      edit(player, "$run-missing")
    }

    assertEquals(seededIds.sorted(), storedIds())
  }

  // ========================================== share into a group

  // WorkspaceRepositoryFirestore.addItemToGroup (addPlacementUpdate).
  private fun shared(groupId: String, folderId: String, isHidden: Boolean = false) =
      mapOf(
          "placements.$groupId" to placement(folderId, isHidden),
          "sharedGroupsIds" to FieldValue.arrayUnion(groupId),
      )

  @Test
  fun theOwnerCanShareADocumentIntoTheirGroup() = runBlocking {
    player.doc(playerPrivate).update(shared(groupId, gmFolder)).await()

    assertEquals(listOf(groupId), stored(playerPrivate, "sharedGroupsIds"))
    assertEquals(placement(gmFolder), stored(playerPrivate, "placements.$groupId"))
  }

  @Test
  fun invalidSharesAreDenied() = runBlocking {
    listOf(
            Triple("not the owner", companion, playerPrivate) to shared(groupId, gmFolder),
            Triple("a group the user is not in", player, playerPrivate) to
                shared(otherGroupId, otherGroupId),
            Triple("with another field", player, playerPrivate) to
                shared(groupId, gmFolder) + ("name" to "Shared"),
            Triple("visible under a hidden folder", player, playerPrivate) to
                shared(groupId, hiddenFolder),
            // Hidden, so that only the folder's groups refuse it (a visible item would also fail
            // on the folder's missing placement).
            Triple("a folder that is not in the group", player, playerPrivate) to
                shared(groupId, playerFolder, isHidden = true),
            Triple("a folder under itself", player, playerFolder) to shared(groupId, playerFolder),
            Triple("a malformed placement", player, playerPrivate) to
                mapOf(
                    "placements.$groupId" to mapOf("folderId" to groupId, "isHidden" to "yes"),
                    "sharedGroupsIds" to FieldValue.arrayUnion(groupId),
                ),
            // The GM is a member of both groups and the parent is valid: only the check that a
            // single entry changes refuses.
            Triple("changing another group's entry too", gm, gmFolder) to
                shared(otherGroupId, otherGroupId) + ("placements.$groupId.isHidden" to true),
            // A root has no placements, so the keys check refuses it too.
            Triple("a group root", gm, groupId) to shared(otherGroupId, otherGroupId),
        )
        .forEach { (target, write) ->
          val (case, client, id) = target
          FirebaseEmulatorTestUtils.assertPermissionDenied(case) {
            client.doc(id).update(write).await()
          }
        }

    assertEquals(emptyList<String>(), stored(playerPrivate, "sharedGroupsIds"))
    assertEquals(emptyList<String>(), stored(playerFolder, "sharedGroupsIds"))
    assertEquals(placement(groupId), stored(gmFolder, "placements.$groupId"))
    assertEquals(listOf(groupId), stored(gmFolder, "sharedGroupsIds"))
    assertEquals(listOf(groupId), stored(groupId, "sharedGroupsIds"))
  }

  // ========================================== unshare from a group

  // WorkspaceRepositoryFirestore.removeItemFromGroup (removePlacementUpdate).
  private fun unshared(groupId: String) =
      mapOf(
          "placements.$groupId" to FieldValue.delete(),
          "sharedGroupsIds" to FieldValue.arrayRemove(groupId),
      )

  @Test
  fun theOwnerOrTheGameMasterCanUnshareADocument() = runBlocking {
    player.doc(playerFile).update(unshared(groupId)).await()
    gm.doc(playerSheet).update(unshared(groupId)).await()

    listOf(playerFile, playerSheet).forEach {
      assertEquals(emptyList<String>(), stored(it, "sharedGroupsIds"))
      assertEquals(emptyMap<String, Any>(), stored(it, "placements"))
    }
  }

  @Test
  fun invalidUnsharesAreDenied() = runBlocking {
    listOf(
            Triple("another member", companion, playerFile) to unshared(groupId),
            Triple("with another field", player, playerFile) to
                unshared(groupId) + ("name" to "Gone"),
            Triple("the group root", gm, groupId) to unshared(groupId),
        )
        .forEach { (target, write) ->
          val (case, client, id) = target
          FirebaseEmulatorTestUtils.assertPermissionDenied(case) {
            client.doc(id).update(write).await()
          }
        }

    assertEquals(listOf(groupId), stored(playerFile, "sharedGroupsIds"))
    assertEquals(listOf(groupId), stored(groupId, "sharedGroupsIds"))
  }

  // ========================================== change a placement entry

  // Accepted limit (DATA_ARCHITECTURE.md §7.5, exception 2): the rules cannot tell who changes an
  // entry, so any member may hide, reveal or reattach any document of the group. The cascades of
  // WorkspaceRepositoryFirestore rely on it to reattach other users' documents.
  @Test
  fun anyMemberCanChangeAPlacementEntryOfTheGroup() = runBlocking {
    // setItemHidden (hiddenUpdate), by the GM then by another member
    gm.doc(playerFile).update("placements.$groupId.isHidden", true).await()
    assertEquals(true, stored(playerFile, "placements.$groupId.isHidden"))
    companion.doc(playerFile).update("placements.$groupId.isHidden", false).await()
    assertEquals(false, stored(playerFile, "placements.$groupId.isHidden"))

    // A reattachment after a cascade (addPlacementUpdate on a document already in the group)
    companion.doc(playerSheet).update(shared(groupId, groupId)).await()
    assertEquals(placement(groupId), stored(playerSheet, "placements.$groupId"))
  }

  @Test
  fun invalidPlacementEntryChangesAreDenied() = runBlocking {
    listOf(
            Triple("an outsider", outsider, playerFile) to
                mapOf("placements.$groupId.isHidden" to true),
            Triple("with the content", player, playerFile) to
                mapOf("placements.$groupId.isHidden" to true, "content" to "Secret"),
            // The group would stay in sharedGroupsIds without a placement.
            Triple("removing the entry", companion, playerFile) to
                mapOf("placements.$groupId" to FieldValue.delete()),
            Triple("two entries at once", gm, gmBoth) to
                mapOf(
                    "placements.$groupId.isHidden" to true,
                    "placements.$otherGroupId.isHidden" to true,
                ),
        )
        .forEach { (target, write) ->
          val (case, client, id) = target
          FirebaseEmulatorTestUtils.assertPermissionDenied(case) {
            client.doc(id).update(write).await()
          }
        }

    assertEquals(placement(groupId), stored(playerFile, "placements.$groupId"))
    assertEquals(listOf(groupId), stored(playerFile, "sharedGroupsIds"))
    assertEquals("", stored(playerFile, "content"))
    assertEquals(
        mapOf(groupId to placement(gmFolder), otherGroupId to placement(otherGroupId)),
        stored(gmBoth, "placements"),
    )
  }

  // Known gap (DATA_ARCHITECTURE.md §12): an entry change does not check the entry's shape, so a
  // member can write one the app cannot read, hiding the document from everyone, its owner
  // included. If the rules come to check the entry, this test fails and must become an
  // assertPermissionDenied.
  @Test
  fun aMemberCanStillWriteAMalformedPlacementEntry() = runBlocking {
    companion.doc(playerFile).update("placements.$groupId.isHidden", "yes").await()

    assertEquals("yes", stored(playerFile, "placements.$groupId.isHidden"))
  }

  // ========================================== delete

  @Test
  fun onlyTheOwnerCanDeleteADocument() = runBlocking {
    FirebaseEmulatorTestUtils.assertPermissionDenied("the game master") {
      gm.doc(playerFile).delete().await()
    }
    FirebaseEmulatorTestUtils.assertPermissionDenied("another member") {
      companion.doc(playerFile).delete().await()
    }
    assertEquals(seededIds.sorted(), storedIds())

    player.doc(playerFile).delete().await()

    assertEquals((seededIds - playerFile).sorted(), storedIds())
  }

  // The root with its group is deleted in CampaignGroupsRulesTest.
  @Test
  fun theGroupRootCannotBeDeletedAlone() = runBlocking {
    FirebaseEmulatorTestUtils.assertPermissionDenied { gm.doc(groupId).delete().await() }

    assertEquals(seededIds.sorted(), storedIds())
  }
}
