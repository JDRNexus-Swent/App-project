// Co-authored-by: Claude Sonnet 5.5
package com.github.se.jdrnexus.model.personalSpace

import kotlin.to
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class CascadeRulesTest {

  private fun folder(id: String) = JDRFile(id = id, type = DocumentType.FOLDER)

  private fun file(id: String) = JDRFile(id = id)

  /** A fake tree: what each folder contains, and which folders were read. */
  private class FakeTree(private val content: Map<String, List<JDRFile>>) {
    val reads = mutableListOf<String>()

    suspend fun childrenOf(folder: JDRFile): List<JDRFile> {
      reads += folder.id
      return content[folder.id].orEmpty()
    }
  }

  /** An item placed in [folderId] inside the group "group". */
  private fun inGroup(id: String, owner: String, folderId: String, isFolder: Boolean = false) =
      JDRFile(
          id = id,
          ownerId = owner,
          type = if (isFolder) DocumentType.FOLDER else DocumentType.TEXT,
          sharedGroupsIds = listOf("group"),
          placements = mapOf("group" to Placement(folderId)),
      )

  @Test
  fun subtreeOf_aFileIsAloneAndIsNotExpanded() = runBlocking {
    val tree = FakeTree(emptyMap())
    val root = file("file")

    val result = subtreeOf(root) { tree.childrenOf(it) }

    assertEquals(listOf(root), result)
    assertEquals(emptyList<String>(), tree.reads)
  }

  @Test
  fun subtreeOf_anEmptyFolderIsAlone() = runBlocking {
    val root = folder("empty")

    val result = subtreeOf(root) { FakeTree(emptyMap()).childrenOf(it) }

    assertEquals(listOf(root), result)
  }

  @Test
  fun subtreeOf_listsEveryDescendantOnceAndAFolderBeforeItsContent() = runBlocking {
    val dungeon = folder("dungeon")
    val room = folder("room")
    val goblin = file("goblin")
    val map = file("map")
    val tree =
        FakeTree(
            mapOf(
                "dungeon" to listOf(room, map),
                "room" to listOf(goblin),
            )
        )

    val result = subtreeOf(dungeon) { tree.childrenOf(it) }

    assertEquals(listOf("dungeon", "room", "goblin", "map"), result.map { it.id })
  }

  @Test
  fun subtreeOf_readsOnlyFolders() = runBlocking {
    val dungeon = folder("dungeon")
    val room = folder("room")
    val tree =
        FakeTree(
            mapOf(
                "dungeon" to listOf(room, file("map")),
                "room" to listOf(file("goblin")),
            )
        )

    subtreeOf(dungeon) { tree.childrenOf(it) }

    assertEquals(listOf("dungeon", "room"), tree.reads)
  }

  @Test
  fun subtreeOf_stopsWhenTheDataLoops() = runBlocking {
    val a = folder("a")
    val b = folder("b")
    val tree = FakeTree(mapOf("a" to listOf(b), "b" to listOf(a)))

    val result = subtreeOf(a) { tree.childrenOf(it) }

    assertEquals(listOf("a", "b"), result.map { it.id })
  }

  // ---- planAddition

  /** An item of the owner's personal space, optionally already placed in "group". */
  private fun item(
      id: String,
      parent: String = "",
      type: DocumentType = DocumentType.TEXT,
      placed: Placement? = null,
  ) =
      JDRFile(
          id = id,
          ownerId = "owner",
          personalParentId = parent,
          type = type,
          sharedGroupsIds = if (placed != null) listOf("group") else emptyList(),
          placements = if (placed != null) mapOf("group" to placed) else emptyMap(),
      )

  // dungeon
  // ├── map
  // └── room
  //     └── goblin
  private val dungeon = item("dungeon", type = DocumentType.FOLDER)
  private val map = item("map", parent = "dungeon")
  private val room = item("room", parent = "dungeon", type = DocumentType.FOLDER)
  private val goblin = item("goblin", parent = "room")

  private fun plan(
      subtree: List<JDRFile>,
      hideNew: Boolean = false,
      destinationHidden: Boolean = false,
  ) = planAddition(subtree, "group", "destination", hideNew, destinationHidden)

  @Test
  fun planAddition_aSingleItemGoesInTheDestination() {
    val result = plan(listOf(map))

    assertEquals(listOf(GroupPlacement("map", "destination", false)), result)
  }

  @Test
  fun planAddition_keepsTheStructureOfThePersonalSpace() {
    val result = plan(listOf(dungeon, map, room, goblin))

    assertEquals(
        listOf(
            GroupPlacement("dungeon", "destination", false),
            GroupPlacement("map", "dungeon", false),
            GroupPlacement("room", "dungeon", false),
            GroupPlacement("goblin", "room", false),
        ),
        result,
    )
  }

  @Test
  fun planAddition_hideNewHidesEverythingThatIsAdded() {
    val result = plan(listOf(dungeon, map, room, goblin), hideNew = true)

    assertEquals(listOf(true, true, true, true), result.map { it.isHidden })
  }

  @Test
  fun planAddition_aHiddenDestinationHidesEverythingThatIsAdded() {
    val result = plan(listOf(dungeon, map, room, goblin), destinationHidden = true)

    assertEquals(listOf(true, true, true, true), result.map { it.isHidden })
  }

  @Test
  fun planAddition_anItemAlreadyInTheGroupIsNotAddedAgain() {
    val placedRoom = item("room", "dungeon", DocumentType.FOLDER, Placement("elsewhere"))

    val result = plan(listOf(dungeon, map, placedRoom, goblin))

    assertEquals(listOf("dungeon", "map", "goblin"), result.map { it.id })
  }

  @Test
  fun planAddition_theContentOfAnItemAlreadyInTheGroupGoesUnderIt() {
    val placedRoom = item("room", "dungeon", DocumentType.FOLDER, Placement("elsewhere"))

    val result = plan(listOf(dungeon, placedRoom, goblin))

    assertEquals(GroupPlacement("goblin", "room", false), result.last())
  }

  @Test
  fun planAddition_hideNewAlsoHidesNewItemsUnderAVisibleItemAlreadyInTheGroup() {
    val placedRoom = item("room", "dungeon", DocumentType.FOLDER, Placement("elsewhere", false))

    val result = plan(listOf(dungeon, placedRoom, goblin), hideNew = true)

    assertEquals(GroupPlacement("goblin", "room", true), result.last())
  }

  @Test
  fun planAddition_aNewItemUnderAHiddenItemAlreadyInTheGroupIsHidden() {
    val hiddenRoom = item("room", "dungeon", DocumentType.FOLDER, Placement("elsewhere", true))

    val result = plan(listOf(dungeon, hiddenRoom, goblin))

    assertEquals(GroupPlacement("goblin", "room", true), result.last())
  }

  @Test
  fun planAddition_aNewItemUnderAVisibleItemAlreadyInTheGroupStaysVisible() {
    val visibleRoom = item("room", "dungeon", DocumentType.FOLDER, Placement("elsewhere", false))

    val result = plan(listOf(dungeon, visibleRoom, goblin))

    assertEquals(GroupPlacement("goblin", "room", false), result.last())
  }

  // ---- planRemoval

  /** An item of [owner] placed in [folder] inside "group". */
  private fun placed(
      id: String,
      folder: String = "group",
      owner: String = "A",
      type: DocumentType = DocumentType.TEXT,
      hidden: Boolean = false,
  ) =
      JDRFile(
          id = id,
          ownerId = owner,
          type = type,
          sharedGroupsIds = listOf("group"),
          placements = mapOf("group" to Placement(folder, hidden)),
      )

  private suspend fun removal(
      root: JDRFile,
      tree: FakeTree,
      parentHidden: Boolean = false,
  ) = planRemoval(root, "group", parentHidden) { tree.childrenOf(it) }

  @Test
  fun planRemoval_aFileLeavesAlone() = runBlocking {
    val file = placed("file")

    val result = removal(file, FakeTree(emptyMap()))

    assertEquals(listOf("file"), result.leaving.map { it.id })
    assertEquals(emptyList<GroupPlacement>(), result.reattached)
  }

  @Test
  fun planRemoval_aFolderLeavesWithAllContentOfTheSameOwner() = runBlocking {
    val folder = placed("folder", type = DocumentType.FOLDER)
    val sub = placed("sub", "folder", type = DocumentType.FOLDER)
    val tree =
        FakeTree(
            mapOf(
                "folder" to listOf(sub, placed("a", "folder")),
                "sub" to listOf(placed("b", "sub")),
            )
        )

    val result = removal(folder, tree)

    assertEquals(listOf("folder", "sub", "b", "a"), result.leaving.map { it.id })
    assertEquals(emptyList<GroupPlacement>(), result.reattached)
  }

  @Test
  fun planRemoval_anItemOfAnotherOwnerGoesToTheParentOfTheFolder() = runBlocking {
    val folder = placed("folder", "parent", type = DocumentType.FOLDER)
    val tree = FakeTree(mapOf("folder" to listOf(placed("theirs", "folder", owner = "B"))))

    val result = removal(folder, tree)

    assertEquals(listOf("folder"), result.leaving.map { it.id })
    assertEquals(listOf(GroupPlacement("theirs", "parent", false)), result.reattached)
  }

  @Test
  fun planRemoval_aFolderAtTheGroupRootReattachesToTheGroupRoot() = runBlocking {
    val folder = placed("folder", "group", type = DocumentType.FOLDER)
    val tree = FakeTree(mapOf("folder" to listOf(placed("theirs", "folder", owner = "B"))))

    val result = removal(folder, tree)

    assertEquals("group", result.reattached.single().folderId)
  }

  @Test
  fun planRemoval_stopsAtAFolderOfAnotherOwnerAndKeepsItsContent() = runBlocking {
    val folder = placed("folder", type = DocumentType.FOLDER)
    val theirs = placed("theirs", "folder", owner = "B", type = DocumentType.FOLDER)
    val tree =
        FakeTree(
            mapOf(
                "folder" to listOf(theirs),
                "theirs" to listOf(placed("mine", "theirs", owner = "A")),
            )
        )

    val result = removal(folder, tree)

    assertEquals(listOf("folder"), result.leaving.map { it.id })
    assertEquals(listOf("theirs"), result.reattached.map { it.id })
    assertEquals(listOf("folder"), tree.reads)
  }

  @Test
  fun planRemoval_aHiddenParentHidesTheReattachedItems() = runBlocking {
    val folder = placed("folder", "parent", type = DocumentType.FOLDER)
    val tree = FakeTree(mapOf("folder" to listOf(placed("theirs", "folder", owner = "B"))))

    val result = removal(folder, tree, parentHidden = true)

    assertEquals(true, result.reattached.single().isHidden)
  }

  @Test
  fun planRemoval_aVisibleParentNeverRevealsAnItem() = runBlocking {
    val folder = placed("folder", "parent", type = DocumentType.FOLDER)
    val tree =
        FakeTree(
            mapOf(
                "folder" to
                    listOf(
                        placed("hidden", "folder", owner = "B", hidden = true),
                        placed("visible", "folder", owner = "B"),
                    )
            )
        )

    val result = removal(folder, tree, parentHidden = false)

    assertEquals(listOf(true, false), result.reattached.map { it.isHidden })
  }

  @Test
  fun planRemoval_refusesAnItemThatIsNotInTheGroup() = runBlocking {
    val outside = JDRFile(id = "file")

    val error = runCatching { removal(outside, FakeTree(emptyMap())) }.exceptionOrNull()

    assertEquals(IllegalStateException::class.java, error?.javaClass)
  }

  @Test
  fun planRemoval_stopsAtAnotherOwnersFolderAndDoesNotReadIt() = runBlocking {
    val reads = mutableListOf<String>()
    val tree =
        mapOf(
            "dungeon" to
                listOf(inGroup("room", "A", "dungeon"), inGroup("pauls", "B", "dungeon", true)),
            "pauls" to listOf(inGroup("marie-file", "A", "pauls")),
        )
    val dungeon = inGroup("dungeon", "A", "group", true)

    val result =
        planRemoval(dungeon, "group", false) {
          reads += it.id
          tree[it.id].orEmpty()
        }

    assertEquals(listOf("dungeon", "room"), result.leaving.map { it.id })
    assertEquals(listOf(GroupPlacement("pauls", "group", false)), result.reattached)
    assertEquals(listOf("dungeon"), reads)
  }

  // ---- planDeletion

  private suspend fun deletion(
      deleted: List<JDRFile>,
      tree: FakeTree,
      hiddenParents: Set<String> = emptySet(),
  ) =
      planDeletion(
          deleted,
          isParentHidden = { folderId, _ -> folderId in hiddenParents },
          childrenOf = { folder, _ -> tree.childrenOf(folder) },
      )

  @Test
  fun planDeletion_aFileInNoGroupChangesNothing() = runBlocking {
    val result = deletion(listOf(file("file")), FakeTree(emptyMap()))

    assertEquals(emptyMap<String, GroupRemoval>(), result)
  }

  @Test
  fun planDeletion_aFolderLeavesTheGroupWithItsContent() = runBlocking {
    val folder = placed("folder", type = DocumentType.FOLDER)
    val child = placed("child", "folder")
    val tree = FakeTree(mapOf("folder" to listOf(child)))

    val result = deletion(listOf(folder, child), tree).getValue("group")

    assertEquals(listOf("folder", "child"), result.leaving.map { it.id })
    assertEquals(emptyList<GroupPlacement>(), result.reattached)
  }

  @Test
  fun planDeletion_anItemOfAnotherOwnerIsReattachedNotDeleted() = runBlocking {
    val folder = placed("folder", "parent", type = DocumentType.FOLDER)
    val tree = FakeTree(mapOf("folder" to listOf(placed("theirs", "folder", owner = "B"))))

    val result = deletion(listOf(folder), tree).getValue("group")

    assertEquals(listOf(GroupPlacement("theirs", "parent", false)), result.reattached)
  }

  @Test
  fun planDeletion_anItemOfTheOwnerThatIsNotDeletedIsReattachedToo() = runBlocking {
    val folder = placed("folder", type = DocumentType.FOLDER)
    val notDeleted = placed("notDeleted", "folder")
    val tree = FakeTree(mapOf("folder" to listOf(notDeleted)))

    val result = deletion(listOf(folder), tree).getValue("group")

    assertEquals(listOf("folder"), result.leaving.map { it.id })
    assertEquals(listOf(GroupPlacement("notDeleted", "group", false)), result.reattached)
  }

  @Test
  fun planDeletion_anItemBetweenDeletedFoldersStaysAndKeepsItsContent() = runBlocking {
    val d1 = placed("d1", type = DocumentType.FOLDER)
    val x = placed("x", "d1", type = DocumentType.FOLDER)
    val d2 = placed("d2", "x", type = DocumentType.FOLDER)
    val tree =
        FakeTree(
            mapOf(
                "d1" to listOf(x),
                "d2" to listOf(placed("theirs", "d2", owner = "B")),
            )
        )

    val result = deletion(listOf(d1, d2), tree).getValue("group")

    assertEquals(listOf("d1", "d2"), result.leaving.map { it.id })
    assertEquals(
        listOf(GroupPlacement("x", "group", false), GroupPlacement("theirs", "x", false)),
        result.reattached,
    )
  }

  @Test
  fun planDeletion_aFolderOfAnotherOwnerBetweenDeletedFoldersStaysAndAdoptsTheItemsBelow() =
      runBlocking {
        val folder = placed("folder", type = DocumentType.FOLDER)
        val theirs = placed("theirs", "folder", owner = "B", type = DocumentType.FOLDER)
        val inner = placed("inner", "theirs", type = DocumentType.FOLDER)
        val tree =
            FakeTree(
                mapOf(
                    "folder" to listOf(theirs),
                    "theirs" to listOf(inner),
                    "inner" to listOf(placed("c", "inner", owner = "B")),
                )
            )

        val result = deletion(listOf(folder, inner), tree).getValue("group")

        assertEquals(listOf("folder", "inner"), result.leaving.map { it.id })
        assertEquals(
            listOf(
                GroupPlacement("theirs", "group", false),
                GroupPlacement("c", "theirs", false),
            ),
            result.reattached,
        )
      }

  @Test
  fun planDeletion_decidesEachGroupOnItsOwn() = runBlocking {
    val file =
        JDRFile(
            id = "file",
            ownerId = "A",
            sharedGroupsIds = listOf("group", "other"),
            placements = mapOf("group" to Placement("group"), "other" to Placement("other")),
        )

    val result = deletion(listOf(file), FakeTree(emptyMap()))

    assertEquals(setOf("group", "other"), result.keys)
  }

  @Test
  fun planDeletion_aHiddenParentHidesTheReattachedItems() = runBlocking {
    val folder = placed("folder", "parent", type = DocumentType.FOLDER)
    val tree = FakeTree(mapOf("folder" to listOf(placed("theirs", "folder", owner = "B"))))

    val result = deletion(listOf(folder), tree, hiddenParents = setOf("parent")).getValue("group")

    assertEquals(true, result.reattached.single().isHidden)
  }

  @Test
  fun planDeletion_reattachesToTheNearestAncestorThatIsNotDeleted() = runBlocking {
    val folder = placed("folder", "parent", type = DocumentType.FOLDER)
    val sub = placed("sub", "folder", type = DocumentType.FOLDER)
    val tree =
        FakeTree(
            mapOf(
                "folder" to listOf(sub),
                "sub" to listOf(placed("theirs", "sub", owner = "B")),
            )
        )

    val result = deletion(listOf(folder, sub), tree).getValue("group")

    assertEquals(listOf(GroupPlacement("theirs", "parent", false)), result.reattached)
  }

  @Test
  fun planDeletion_aReattachedItemIsNeverRevealed() = runBlocking {
    val folder = placed("folder", "parent", type = DocumentType.FOLDER)
    val tree =
        FakeTree(mapOf("folder" to listOf(placed("theirs", "folder", owner = "B", hidden = true))))

    val result = deletion(listOf(folder), tree).getValue("group")

    assertEquals(true, result.reattached.single().isHidden)
  }
}
