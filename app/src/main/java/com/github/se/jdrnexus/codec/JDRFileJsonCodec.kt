// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.github.se.jdrnexus.codec

import com.github.se.jdrnexus.model.filerepresentation.CharacterAction
import com.github.se.jdrnexus.model.filerepresentation.CharacterFile
import com.github.se.jdrnexus.model.filerepresentation.CharacterStat
import com.github.se.jdrnexus.model.filerepresentation.LoreFile
import com.github.se.jdrnexus.model.filerepresentation.LoreSection
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** Internal JSON mapping for the character and lore file conventions. */
internal object JDRFileJsonCodec {
  fun deserializeCharacter(json: String): CharacterFile {
    val root = JSONObject(json)
    validateVersion(root)
    require(root.getStrictString("type") == "character") {
      "The JSON type does not match this file."
    }
    val character = if (root.has("character")) root.getJSONObject("character") else JSONObject()
    val statsArray = if (character.has("stats")) character.getJSONArray("stats") else JSONArray()
    val stats = buildList {
      val ids = mutableSetOf<String>()
      for (index in 0 until statsArray.length()) {
        val stat = statsArray.getJSONObject(index)
        val statID = stat.getStrictString("statID")
        require(statID.isNotBlank() && ids.add(statID)) {
          "Character stat IDs must be non-empty and unique."
        }
        val rawValue = stat.get("value")
        require(rawValue is Number && rawValue.toDouble().isFinite()) {
          "Character stat values must be numeric."
        }
        add(
            CharacterStat(
                statID = statID,
                name = stat.getStrictString("name"),
                value = rawValue.toDouble(),
            )
        )
      }
    }
    val actionsArray =
        if (character.has("actions")) character.getJSONArray("actions") else JSONArray()
    val actions = buildList {
      val ids = mutableSetOf<String>()
      for (index in 0 until actionsArray.length()) {
        val action = actionsArray.getJSONObject(index)
        val actionID = action.getStrictString("actionID")
        require(actionID.isNotBlank() && ids.add(actionID)) {
          "Character action IDs must be non-empty and unique."
        }
        add(
            CharacterAction(
                actionID = actionID,
                name = action.getStrictString("name"),
                description = action.getStrictString("description"),
            )
        )
      }
    }
    val name = if (character.has("name")) character.getStrictString("name") else ""
    return CharacterFile(name, stats, actions)
  }

  fun deserializeLore(json: String): LoreFile {
    val root = JSONObject(json)
    validateVersion(root)
    require(root.getStrictString("type") == "lore") { "The JSON type does not match this file." }
    val sectionsArray = if (root.has("sections")) root.getJSONArray("sections") else JSONArray()
    val sections = buildList {
      for (index in 0 until sectionsArray.length()) {
        val section = sectionsArray.getJSONObject(index)
        val sectionID = section.getStrictString("sectionID")
        require(sectionID.isNotBlank()) { "Lore section IDs cannot be empty." }
        add(
            LoreSection(
                sectionID = sectionID,
                title = section.getStrictString("title"),
                description = section.getStrictString("content"),
            )
        )
      }
    }
    require(sections.map(LoreSection::sectionID).toSet().size == sections.size) {
      "Lore section IDs must be unique."
    }
    val title = if (root.has("title")) root.getStrictString("title") else ""
    return LoreFile(title, sections)
  }

  fun serializeCharacter(character: CharacterFile?): String =
      character?.let(::characterToJson) ?: template("character")

  fun serializeLore(lore: LoreFile?): String = lore?.let(::loreToJson) ?: template("lore")

  /*
  make sure the JSON use the current version
   */
  private fun validateVersion(root: JSONObject) {
    if (root.has("version")) {
      val version = root.get("version")
      require(version is Number && version.toDouble() == SUPPORTED_VERSION.toDouble()) {
        UNSUPPORTED_VERSION_ERROR
      }
    }
  }

  /*
  This is to make sure this is a string (for instance a int will not be auto cast)
   */
  private fun JSONObject.getStrictString(key: String): String {
    val value = get(key)
    if (value !is String) throw JSONException("$key must be a string.")
    return value
  }

  private fun template(type: String): String =
      JSONObject().put("version", SUPPORTED_VERSION).put("type", type).toString()

  private fun characterToJson(character: CharacterFile): String {
    val stats = JSONArray()
    val statIDs = mutableSetOf<String>()
    character.stats.forEach { stat ->
      require(stat.statID.isNotBlank() && statIDs.add(stat.statID) && stat.value.isFinite()) {
        "Character stats must have unique IDs and finite numeric values."
      }
      stats.put(
          JSONObject().put("statID", stat.statID).put("name", stat.name).put("value", stat.value)
      )
    }
    val actions = JSONArray()
    val actionIDs = mutableSetOf<String>()
    character.actions.forEach { action ->
      require(action.actionID.isNotBlank() && actionIDs.add(action.actionID)) {
        "Character action IDs must be non-empty and unique."
      }
      actions.put(
          JSONObject()
              .put("actionID", action.actionID)
              .put("name", action.name)
              .put("description", action.description)
      )
    }
    return JSONObject()
        .put("version", SUPPORTED_VERSION)
        .put("type", "character")
        .put(
            "character",
            JSONObject().put("name", character.name).put("stats", stats).put("actions", actions),
        )
        .toString()
  }

  private fun loreToJson(lore: LoreFile): String {
    val sections = JSONArray()
    val sectionIDs = mutableSetOf<String>()
    lore.sections.forEach { section ->
      require(section.sectionID.isNotBlank() && sectionIDs.add(section.sectionID)) {
        "Lore section IDs must be non-empty and unique."
      }
      sections.put(
          JSONObject()
              .put("sectionID", section.sectionID)
              .put("title", section.title)
              .put("content", section.description)
      )
    }
    return JSONObject()
        .put("version", SUPPORTED_VERSION)
        .put("type", "lore")
        .put("title", lore.title)
        .put("sections", sections)
        .toString()
  }

  private const val SUPPORTED_VERSION = 1
  private const val UNSUPPORTED_VERSION_ERROR = "Unsupported JSON version."
}
