// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.model.gameManagement

import com.github.se.jdrnexus.model.filerepresentation.CharacterFile
import com.github.se.jdrnexus.model.filerepresentation.CharacterHealthPoints
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class GameSessionSnapshotTest {
  private val goblin =
      GameCombatNPC(
          id = "enemy-1",
          character =
              CharacterFile("Goblin", emptyList(), emptyList(), CharacterHealthPoints(6, 8)),
          initiative = 12,
      )
  private val guide = GameNpc(id = "npc-1", name = "Guide", portraitUrl = null, information = "")

  @Test
  fun invalidEnemiesAreRejected() {
    val invalidEnemies =
        listOf(
            goblin.copy(id = " "),
            goblin.copy(character = goblin.character.copy(name = " ")),
            goblin.copy(character = goblin.character.copy(hp = CharacterHealthPoints(0, 0))),
            goblin.copy(character = goblin.character.copy(hp = CharacterHealthPoints(-1, 8))),
        )

    val rejections = invalidEnemies.map { rejectionOf(GameSessionSnapshot(enemies = listOf(it))) }

    assertEquals(
        listOf(
            "Enemy ID and name are required.",
            "Enemy ID and name are required.",
            "Enemy HP must be between zero and max HP.",
            "Enemy HP must be between zero and max HP.",
        ),
        rejections,
    )
  }

  @Test
  fun invalidNpcsAreRejected() {
    val invalidNpcs = listOf(guide.copy(id = " "), guide.copy(name = " "))

    val rejections = invalidNpcs.map { rejectionOf(GameSessionSnapshot(npcs = listOf(it))) }

    assertEquals(
        listOf("NPC ID and name are required.", "NPC ID and name are required."),
        rejections,
    )
  }

  private fun rejectionOf(session: GameSessionSnapshot): String? =
      assertThrows(IllegalArgumentException::class.java) { session.validate() }.message
}
