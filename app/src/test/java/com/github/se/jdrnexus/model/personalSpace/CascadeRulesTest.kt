// Co-authored-by: Claude Sonnet 5.5
package com.github.se.jdrnexus.model.personalSpace

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

    assertEquals(listOf(GroupAddition("map", "destination", false)), result)
  }

  @Test
  fun planAddition_keepsTheStructureOfThePersonalSpace() {
    val result = plan(listOf(dungeon, map, room, goblin))

    assertEquals(
        listOf(
            GroupAddition("dungeon", "destination", false),
            GroupAddition("map", "dungeon", false),
            GroupAddition("room", "dungeon", false),
            GroupAddition("goblin", "room", false),
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

    assertEquals(GroupAddition("goblin", "room", false), result.last())
  }

  @Test
  fun planAddition_hideNewAlsoHidesNewItemsUnderAVisibleItemAlreadyInTheGroup() {
    val placedRoom = item("room", "dungeon", DocumentType.FOLDER, Placement("elsewhere", false))

    val result = plan(listOf(dungeon, placedRoom, goblin), hideNew = true)

    assertEquals(GroupAddition("goblin", "room", true), result.last())
  }

  @Test
  fun planAddition_aNewItemUnderAHiddenItemAlreadyInTheGroupIsHidden() {
    val hiddenRoom = item("room", "dungeon", DocumentType.FOLDER, Placement("elsewhere", true))

    val result = plan(listOf(dungeon, hiddenRoom, goblin))

    assertEquals(GroupAddition("goblin", "room", true), result.last())
  }

  @Test
  fun planAddition_aNewItemUnderAVisibleItemAlreadyInTheGroupStaysVisible() {
    val visibleRoom = item("room", "dungeon", DocumentType.FOLDER, Placement("elsewhere", false))

    val result = plan(listOf(dungeon, visibleRoom, goblin))

    assertEquals(GroupAddition("goblin", "room", false), result.last())
  }
}
