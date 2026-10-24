// Co-authored-by: Claude Sonnet 5.5
package com.github.se.jdrnexus.model.personalSpace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JDRFilePlacementTest {

  @Test
  fun isContainer_isTrueForFolderAndGroupRoot() {
    assertTrue(JDRFile(type = DocumentType.FOLDER).isContainer())
    assertTrue(JDRFile(type = DocumentType.GROUP_ROOT).isContainer())
  }

  @Test
  fun isContainer_isFalseForTextAndCharacter() {
    assertFalse(JDRFile(type = DocumentType.TEXT).isContainer())
    assertFalse(JDRFile(type = DocumentType.CHARACTER).isContainer())
  }

  @Test
  fun placement_isVisibleByDefault() {
    assertFalse(Placement(folderId = "folder").isHidden)
  }

  @Test
  fun file_hasNoPlacementByDefault() {
    assertTrue(JDRFile().placements.isEmpty())
  }

  @Test
  fun toMap_includesPlacements() {
    val file = JDRFile(placements = mapOf("group1" to Placement("folder1", isHidden = true)))

    assertEquals(mapOf("group1" to Placement("folder1", true)), file.toMap()["placements"])
  }
}
