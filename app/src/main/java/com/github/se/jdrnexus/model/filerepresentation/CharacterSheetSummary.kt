// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.model.filerepresentation

import com.github.se.jdrnexus.codec.JDRFileCodecException
import com.github.se.jdrnexus.codec.JDRFileJsonCodec

/** Name given to a character whose sheet has no name yet. */
const val DEFAULT_CHARACTER_NAME = "New character"

/**
 * What a workspace card can show about a character, read from its sheet (the `content` JSON of a
 * character file, the only source of truth for these values).
 *
 * @property name The trimmed sheet name, or null when it is blank or the sheet cannot be read.
 * @property hp The sheet's health points, or null when they are not set (no maximum HP).
 */
data class CharacterSheetSummary(
    val name: String?,
    val hp: CharacterHealthPoints?,
)

/**
 * Reads the summary of the character sheet stored in [content]. An unreadable sheet gives an empty
 * summary rather than an error, so a card never shows the raw JSON.
 *
 * HP are only reported when the maximum is above 0: the codec reads a sheet without HP as 0 / 0,
 * and a maximum of 0 is never a value a player chose, so showing it would be misleading.
 */
fun characterSheetSummary(content: String): CharacterSheetSummary {
  val character =
      try {
        JDRFileJsonCodec.deserializeCharacter(content)
      } catch (_: JDRFileCodecException) {
        return CharacterSheetSummary(name = null, hp = null)
      }
  return CharacterSheetSummary(
      name = character.name.trim().ifEmpty { null },
      hp = character.hp.takeIf { it.max > 0 },
  )
}

/**
 * Returns the character sheet [content] with its name replaced by [name], every other field kept.
 *
 * @throws JDRFileCodecException if [content] is not a readable character sheet.
 */
fun renameCharacterSheet(content: String, name: String): String =
    JDRFileJsonCodec.serializeCharacter(
        JDRFileJsonCodec.deserializeCharacter(content).copy(name = name)
    )
