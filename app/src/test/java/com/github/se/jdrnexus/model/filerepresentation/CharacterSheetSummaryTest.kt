// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.model.filerepresentation

import com.github.se.jdrnexus.codec.JDRFileCodecException
import com.github.se.jdrnexus.codec.JDRFileJsonCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CharacterSheetSummaryTest {

  @Test
  fun summaryReadsTrimmedNameAndHpFromTheSheet() {
    val summary =
        characterSheetSummary(sheet(name = "  Aria  ", hp = """{"current":25,"max":40}"""))

    assertEquals(CharacterSheetSummary("Aria", CharacterHealthPoints(25, 40)), summary)
  }

  @Test
  fun summaryHasNoNameWhenTheSheetNameIsEmpty() {
    assertEquals(null, characterSheetSummary(sheet(name = "")).name)
  }

  @Test
  fun summaryHasNoNameWhenTheSheetNameIsWhitespaceOnly() {
    assertEquals(null, characterSheetSummary(sheet(name = "   ")).name)
  }

  @Test
  fun summaryHasNoHpWhenTheSheetHasNone() {
    assertEquals(CharacterSheetSummary("Aria", null), characterSheetSummary(sheet(name = "Aria")))
  }

  @Test
  fun summaryHasNoHpWhenMaxHpIsZero() {
    val summary = characterSheetSummary(sheet(name = "Aria", hp = """{"current":0,"max":0}"""))

    assertEquals(null, summary.hp)
  }

  @Test
  fun summaryKeepsZeroCurrentHpWhenMaxHpIsSet() {
    val summary = characterSheetSummary(sheet(name = "Aria", hp = """{"current":0,"max":40}"""))

    assertEquals(CharacterHealthPoints(0, 40), summary.hp)
  }

  @Test
  fun summaryOfANewCharacterTemplateIsEmpty() {
    assertEquals(
        CharacterSheetSummary(null, null),
        characterSheetSummary(JDRFileJsonCodec.serializeCharacter(null)),
    )
  }

  @Test
  fun summaryOfUnreadableContentIsEmpty() {
    assertEquals(CharacterSheetSummary(null, null), characterSheetSummary("not json"))
    assertEquals(CharacterSheetSummary(null, null), characterSheetSummary(""))
  }

  @Test
  fun renamingASheetChangesOnlyItsName() {
    val content =
        """{"version":1,"type":"character","character":{"name":"Aria","stats":[{"statID":"str","name":"Strength","value":16}],"actions":[{"actionID":"slash","name":"Slash","description":"Swing"}],"hp":{"current":25,"max":40}}}"""

    val renamed = JDRFileJsonCodec.deserializeCharacter(renameCharacterSheet(content, "Brann"))

    assertEquals(
        CharacterFile(
            "Brann",
            listOf(CharacterStat("str", "Strength", 16.0)),
            listOf(CharacterAction("slash", "Slash", "Swing")),
            CharacterHealthPoints(25, 40),
        ),
        renamed,
    )
  }

  @Test
  fun renamingTheNewCharacterTemplateGivesItAName() {
    val renamed = renameCharacterSheet(JDRFileJsonCodec.serializeCharacter(null), "Brann")

    assertEquals("Brann", JDRFileJsonCodec.deserializeCharacter(renamed).name)
  }

  @Test
  fun renamingAnUnreadableSheetThrows() {
    val error =
        assertThrows(JDRFileCodecException::class.java) {
          renameCharacterSheet("not json", "Brann")
        }

    assertEquals(JDRFileCodecException.INVALID_FILE_MESSAGE, error.message)
  }

  private fun sheet(name: String, hp: String? = null): String {
    val hpField = hp?.let { ""","hp":$it""" } ?: ""
    return """{"version":1,"type":"character","character":{"name":"$name"$hpField}}"""
  }
}
