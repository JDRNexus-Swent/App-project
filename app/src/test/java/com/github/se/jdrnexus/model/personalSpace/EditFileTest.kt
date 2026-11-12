package com.github.se.jdrnexus.model.personalSpace

import org.junit.Assert.assertEquals
import org.junit.Test

class FileEditTest {

  @Test
  fun toMap_containsOnlyNameAndContent() {
    val edit = FileEdit(id = "file", name = "Goblin", content = "hp: 7")

    assertEquals(mapOf("name" to "Goblin", "content" to "hp: 7"), edit.toMap())
  }

  @Test
  fun toEdit_keepsOnlyTheEditableFields() {
    val file =
        JDRFile(
            id = "file",
            name = "Goblin",
            ownerId = "owner",
            sharedGroupsIds = listOf("group"),
            placements = mapOf("group" to Placement("group")),
            personalParentId = "folder",
            content = "hp: 7",
        )

    assertEquals(FileEdit(id = "file", name = "Goblin", content = "hp: 7"), file.toEdit())
  }
}
