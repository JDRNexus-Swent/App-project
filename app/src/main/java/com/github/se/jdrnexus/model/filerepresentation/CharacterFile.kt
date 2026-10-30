// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.github.se.jdrnexus.model.filerepresentation

/** Character data with independently identified stats and actions. */
data class CharacterFile(
    val name: String,
    val stats: List<CharacterStat>,
    val actions: List<CharacterAction>,
    val hp: CharacterHealthPoints,
)

/** Current and maximum health points. */
data class CharacterHealthPoints(val current: Int, val max: Int)

/** A named, numeric stat stored under its stable identifier. */
data class CharacterStat(val statID: String, val name: String, val value: Double)

/** A named action stored under its stable identifier. */
data class CharacterAction(val actionID: String, val name: String, val description: String)
