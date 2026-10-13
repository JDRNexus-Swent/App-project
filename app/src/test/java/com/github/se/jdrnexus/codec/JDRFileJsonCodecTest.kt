// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.github.se.jdrnexus.codec

import com.github.se.jdrnexus.model.filerepresentation.CharacterAction
import com.github.se.jdrnexus.model.filerepresentation.CharacterFile
import com.github.se.jdrnexus.model.filerepresentation.CharacterStat
import com.github.se.jdrnexus.model.filerepresentation.LoreFile
import com.github.se.jdrnexus.model.filerepresentation.LoreSection
import org.json.JSONException
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class JDRFileJsonCodecTest {
  private val characterJson =
      """{"version":1,"type":"character","character":{"name":"Jean-claude","stats":[{"statID":"strength","name":"Strength","value":16},{"statID":"dexterity","name":"Dexterity","value":12}],"actions":[{"actionID":"sword_attack","name":"Sword Attack","description":"Attack"}]}}"""
  private val loreJson =
      """{"version":1,"type":"lore","title":"The Kingdom","sections":[{"sectionID":"history","title":"History","content":"Founded long ago."},{"sectionID":"king","title":"The King","content":"Rules today."}]}"""

  @Test
  fun deserializesCharacterStatsWithIndependentIdsNamesAndValues() {
    val character = JDRFileJsonCodec.deserializeCharacter(characterJson)

    assertEquals("Jean-claude", character.name)
    assertEquals(
        listOf(
            CharacterStat("strength", "Strength", 16.0),
            CharacterStat("dexterity", "Dexterity", 12.0),
        ),
        character.stats,
    )
    assertEquals(
        listOf(CharacterAction("sword_attack", "Sword Attack", "Attack")),
        character.actions,
    )
  }

  @Test
  fun deserializesCharacterWithoutStatsOrActions() {
    assertEquals(
        CharacterFile("", emptyList(), emptyList()),
        JDRFileJsonCodec.deserializeCharacter("""{"type":"character"}"""),
    )
  }

  @Test
  fun deserializesLoreSectionsWithStableIdsAndSeparateTitles() {
    val lore = JDRFileJsonCodec.deserializeLore(loreJson)

    assertEquals("The Kingdom", lore.title)
    assertEquals(
        listOf(
            LoreSection("history", "History", "Founded long ago."),
            LoreSection("king", "The King", "Rules today."),
        ),
        lore.sections,
    )
  }

  @Test
  fun deserializesLoreWithoutSections() {
    assertEquals(LoreFile("", emptyList()), JDRFileJsonCodec.deserializeLore("""{"type":"lore"}"""))
  }

  @Test
  fun serializesCharacterFieldsWithExplicitIdentifiers() {
    val character = JDRFileJsonCodec.deserializeCharacter(characterJson)
    val root = JSONObject(JDRFileJsonCodec.serializeCharacter(character))
    val body = root.getJSONObject("character")
    val stat = body.getJSONArray("stats").getJSONObject(0)
    val action = body.getJSONArray("actions").getJSONObject(0)

    assertEquals(1, root.getInt("version"))
    assertEquals("character", root.getString("type"))
    assertEquals("Jean-claude", body.getString("name"))
    assertEquals("strength", stat.getString("statID"))
    assertEquals("Strength", stat.getString("name"))
    assertEquals(16, stat.getInt("value"))
    assertEquals("sword_attack", action.getString("actionID"))
    assertEquals("Sword Attack", action.getString("name"))
    assertEquals("Attack", action.getString("description"))
  }

  @Test
  fun serializesLoreFieldsWithExplicitSectionIds() {
    val lore = JDRFileJsonCodec.deserializeLore(loreJson)
    val root = JSONObject(JDRFileJsonCodec.serializeLore(lore))
    val sections = root.getJSONArray("sections")
    val first = sections.getJSONObject(0)
    val second = sections.getJSONObject(1)

    assertEquals(1, root.getInt("version"))
    assertEquals("lore", root.getString("type"))
    assertEquals("The Kingdom", root.getString("title"))
    assertEquals("history", first.getString("sectionID"))
    assertEquals("History", first.getString("title"))
    assertEquals("Founded long ago.", first.getString("content"))
    assertEquals("king", second.getString("sectionID"))
    assertEquals("The King", second.getString("title"))
    assertEquals("Rules today.", second.getString("content"))
  }

  @Test
  fun serializationRoundTripsCharacterAndLoreModels() {
    val character = JDRFileJsonCodec.deserializeCharacter(characterJson)
    assertEquals(
        character,
        JDRFileJsonCodec.deserializeCharacter(JDRFileJsonCodec.serializeCharacter(character)),
    )

    val lore = JDRFileJsonCodec.deserializeLore(loreJson)
    assertEquals(lore, JDRFileJsonCodec.deserializeLore(JDRFileJsonCodec.serializeLore(lore)))
  }

  @Test
  fun nullModelsSerializeAsMinimalTypedTemplates() {
    val character = JSONObject(JDRFileJsonCodec.serializeCharacter(null))
    val lore = JSONObject(JDRFileJsonCodec.serializeLore(null))

    assertEquals(setOf("version", "type"), character.keys().asSequence().toSet())
    assertEquals("character", character.getString("type"))
    assertEquals(setOf("version", "type"), lore.keys().asSequence().toSet())
    assertEquals("lore", lore.getString("type"))
  }

  @Test
  fun deserializationRejectsMismatchedTypesMalformedJsonAndNonNumericValues() {
    assertThrows(IllegalArgumentException::class.java) {
      JDRFileJsonCodec.deserializeCharacter("""{"type":"lore"}""")
    }
    assertThrows(IllegalArgumentException::class.java) {
      JDRFileJsonCodec.deserializeLore("""{"type":"character"}""")
    }
    assertThrows(JSONException::class.java) { JDRFileJsonCodec.deserializeCharacter("{") }
    assertThrows(JSONException::class.java) { JDRFileJsonCodec.deserializeLore("{}") }
    assertThrows(IllegalArgumentException::class.java) {
      JDRFileJsonCodec.deserializeCharacter(
          """{"type":"character","character":{"stats":[{"statID":"str","name":"Strength","value":"strong"}]}}"""
      )
    }
  }

  @Test
  fun deserializationRejectsUnsupportedVersions() {
    assertThrows(IllegalArgumentException::class.java) {
      JDRFileJsonCodec.deserializeCharacter("""{"version":2,"type":"character"}""")
    }
    assertThrows(IllegalArgumentException::class.java) {
      JDRFileJsonCodec.deserializeLore("""{"version":2,"type":"lore"}""")
    }
    assertThrows(IllegalArgumentException::class.java) {
      JDRFileJsonCodec.deserializeCharacter("""{"version":"1","type":"character"}""")
    }
  }

  @Test
  fun deserializationRejectsMissingBlankAndDuplicateIdentifiers() {
    val missingStatId =
        """{"type":"character","character":{"stats":[{"name":"Strength","value":16}]}}"""
    val blankActionId =
        """{"type":"character","character":{"actions":[{"actionID":" ","name":"Act","description":"Text"}]}}"""
    val duplicateStatIds =
        """{"type":"character","character":{"stats":[{"statID":"same","name":"One","value":1},{"statID":"same","name":"Two","value":2}]}}"""
    val missingSectionId = """{"type":"lore","sections":[{"title":"History","content":"Text"}]}"""
    val blankSectionId =
        """{"type":"lore","sections":[{"sectionID":"","title":"History","content":"Text"}]}"""

    assertThrows(JSONException::class.java) { JDRFileJsonCodec.deserializeCharacter(missingStatId) }
    assertThrows(IllegalArgumentException::class.java) {
      JDRFileJsonCodec.deserializeCharacter(blankActionId)
    }
    assertThrows(IllegalArgumentException::class.java) {
      JDRFileJsonCodec.deserializeCharacter(duplicateStatIds)
    }
    assertThrows(JSONException::class.java) { JDRFileJsonCodec.deserializeLore(missingSectionId) }
    assertThrows(IllegalArgumentException::class.java) {
      JDRFileJsonCodec.deserializeLore(blankSectionId)
    }
  }

  @Test
  fun deserializationRejectsPresentFieldsWithWrongContainerTypes() {
    assertThrows(JSONException::class.java) {
      JDRFileJsonCodec.deserializeCharacter("""{"type":"character","character":"broken"}""")
    }
    assertThrows(JSONException::class.java) {
      JDRFileJsonCodec.deserializeCharacter(
          """{"type":"character","character":{"stats":"broken","actions":[]}}"""
      )
    }
    assertThrows(JSONException::class.java) {
      JDRFileJsonCodec.deserializeCharacter(
          """{"type":"character","character":{"stats":[],"actions":{}}}"""
      )
    }
    assertThrows(JSONException::class.java) {
      JDRFileJsonCodec.deserializeLore("""{"type":"lore","sections":"broken"}""")
    }
  }

  @Test
  fun deserializationRejectsPresentNamesAndTitlesWithWrongTypes() {
    assertThrows(JSONException::class.java) {
      JDRFileJsonCodec.deserializeCharacter("""{"type":"character","character":{"name":7}}""")
    }
    assertThrows(JSONException::class.java) {
      JDRFileJsonCodec.deserializeLore("""{"type":"lore","title":{}}""")
    }
  }

  @Test
  fun deserializationRejectsWrongTypesInNestedStringFields() {
    val invalidCharacterJson =
        listOf(
            """{"type":7}""",
            """{"type":"character","character":{"stats":[{"statID":1,"name":"Power","value":1}]}}""",
            """{"type":"character","character":{"stats":[{"statID":"power","name":{},"value":1}]}}""",
            """{"type":"character","character":{"actions":[{"actionID":1,"name":"Act","description":"Text"}]}}""",
            """{"type":"character","character":{"actions":[{"actionID":"act","name":"Act","description":false}]}}""",
        )
    invalidCharacterJson.forEach { json ->
      assertThrows(JSONException::class.java) { JDRFileJsonCodec.deserializeCharacter(json) }
    }

    val invalidLoreJson =
        listOf(
            """{"type":false}""",
            """{"type":"lore","sections":[{"sectionID":1,"title":"History","content":"Text"}]}""",
            """{"type":"lore","sections":[{"sectionID":"history","title":[],"content":"Text"}]}""",
            """{"type":"lore","sections":[{"sectionID":"history","title":"History","content":{}}]}""",
        )
    invalidLoreJson.forEach { json ->
      assertThrows(JSONException::class.java) { JDRFileJsonCodec.deserializeLore(json) }
    }
  }

  @Test
  fun serializationRejectsInvalidIdentifiersAndStatValues() {
    assertThrows(IllegalArgumentException::class.java) {
      JDRFileJsonCodec.serializeCharacter(
          CharacterFile("", listOf(CharacterStat("", "Power", 1.0)), emptyList())
      )
    }
    assertThrows(IllegalArgumentException::class.java) {
      JDRFileJsonCodec.serializeCharacter(
          CharacterFile("", listOf(CharacterStat("luck", "Luck", Double.NaN)), emptyList())
      )
    }
    assertThrows(IllegalArgumentException::class.java) {
      JDRFileJsonCodec.serializeCharacter(
          CharacterFile(
              "",
              emptyList(),
              listOf(CharacterAction("", "Act", "Text")),
          )
      )
    }
    assertThrows(IllegalArgumentException::class.java) {
      JDRFileJsonCodec.serializeLore(
          LoreFile(
              "",
              listOf(
                  LoreSection("same", "One", "1"),
                  LoreSection("same", "Two", "2"),
              ),
          )
      )
    }
  }
}
