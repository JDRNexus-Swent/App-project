// Co-authored-by: Claude Sonnet 5.5
package com.github.se.jdrnexus.model.personalSpace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupVisibilityTest {

  /** A file placed in each given group; the map value tells whether it is hidden there. */
  private fun file(
      id: String,
      ownerId: String = "owner",
      hiddenIn: Map<String, Boolean> = emptyMap(),
  ) =
      JDRFile(
          id = id,
          ownerId = ownerId,
          sharedGroupsIds = hiddenIn.keys.toList(),
          placements = hiddenIn.mapValues { (_, hidden) -> Placement("folder", hidden) },
      )

  private val visible = file("visible", hiddenIn = mapOf("group" to false))
  private val hidden = file("hidden", hiddenIn = mapOf("group" to true))
  private val hiddenByPlayer =
      file("hidden-by-player", ownerId = "player", hiddenIn = mapOf("group" to true))
  private val hiddenElsewhere =
      file("hidden-elsewhere", hiddenIn = mapOf("group" to false, "other-group" to true))
  private val all = listOf(visible, hidden, hiddenByPlayer, hiddenElsewhere)

  @Test
  fun player_seesVisibleFilesAndTheirOwnHiddenFiles() {
    val result = all.visibleInGroup("group", userId = "player", isGameMaster = false)

    assertEquals(listOf(visible, hiddenByPlayer, hiddenElsewhere), result)
  }

  @Test
  fun player_doesNotSeeFilesHiddenByOthers() {
    val result = all.visibleInGroup("group", userId = "player", isGameMaster = false)

    assertTrue(hidden !in result)
  }
    @Test
    fun player_doesNotSeeFilesNotSharedWithGroup() {
        val result = listOf(visible).visibleInGroup("other-group", userId = "player", isGameMaster = false)

        assertTrue("File not shared with the group should not be visible", result.isEmpty())
    }

    @Test
    fun player_doesNotSeeFilesHiddenInAnotherGroup() {
        val result = listOf(hiddenElsewhere).visibleInGroup("other-group", userId = "player", isGameMaster = false)

        assertTrue("File explicitly hidden in the requested group should not be visible", result.isEmpty())
    }

  @Test
  fun gameMaster_seesEverything() {
    val result = all.visibleInGroup("group", userId = "gm", isGameMaster = true)

    assertEquals(all, result)
  }

  @Test
  fun hiddenInAnotherGroup_doesNotHideTheFileHere() {
    val result = listOf(hiddenElsewhere).visibleInGroup("group", "player", isGameMaster = false)

    assertEquals(listOf(hiddenElsewhere), result)
  }

  @Test
  fun emptyList_staysEmpty() {
    assertTrue(emptyList<JDRFile>().visibleInGroup("group", "player", false).isEmpty())
  }

}
