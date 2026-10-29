// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.github.se.jdrnexus.model.gameManagement

import com.github.se.jdrnexus.codec.JDRFileJsonCodec
import com.github.se.jdrnexus.model.repository.DiceRepositoryFirestore
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

/** Firestore-backed session store; security rules authorize campaign reads and GM-only writes. */
class GameSessionRepositoryFirestore(
    private val firestore: FirebaseFirestore,
    private val diceRepository: DiceRepositoryFirestore,
) : GameSessionRepository {

  override suspend fun loadActivities(groupId: String): Result<List<GameActivityEntry>> =
      try {
        require(groupId.isNotBlank()) { "Group ID cannot be empty." }
        val diceRolls =
            diceRepository.getDiceRollsHistory(groupId).getOrThrow().map { roll ->
              GameActivityEntry(
                  id = roll.documentId,
                  userId = roll.userId,
                  playerName = roll.username,
                  action = "Rolled ${roll.diceType} with modifier ${roll.modifier}",
                  diceResult = roll.totalValue.toString(),
                  timestamp = roll.timestamp?.time ?: System.currentTimeMillis(),
              )
            }
        val actions =
            firestore
                .collection(GROUPS_COLLECTION)
                .document(groupId)
                .collection(ACTIVITIES_COLLECTION)
                .get()
                .await()
                .documents
                .map { document ->
                  val data = document.data ?: error("Invalid activity data.")
                  GameActivityEntry(
                      id = document.id,
                      userId = data.string("userId"),
                      playerName = data.string("playerName"),
                      action = data.string("action"),
                      diceResult = data["diceResult"] as? String,
                      timestamp = document.getDate("timestamp")?.time ?: System.currentTimeMillis(),
                  )
                }
        Result.success((diceRolls + actions).sortedByDescending(GameActivityEntry::timestamp))
      } catch (cancellation: CancellationException) {
        throw cancellation
      } catch (failure: Exception) {
        Result.failure(failure)
      }

  override fun observeActivities(groupId: String): Flow<List<GameActivityEntry>> =
      combine(
          diceRepository.getDiceRollsStream(groupId).map { rolls ->
            rolls.map { roll ->
              GameActivityEntry(
                  id = roll.documentId,
                  userId = roll.userId,
                  playerName = roll.username,
                  action = "Rolled ${roll.diceType} with modifier ${roll.modifier}",
                  diceResult = roll.totalValue.toString(),
                  timestamp = roll.timestamp?.time ?: System.currentTimeMillis(),
              )
            }
          },
          observePlayerActions(groupId),
      ) { diceRolls, actions ->
        (diceRolls + actions).sortedByDescending(GameActivityEntry::timestamp)
      }

  override suspend fun recordActivity(
      groupId: String,
      userId: String,
      playerName: String,
      action: String,
      diceResult: String?,
  ): Result<Unit> = writeResult {
    require(groupId.isNotBlank()) { "Group ID cannot be empty." }
    require(userId.isNotBlank()) { "User ID cannot be empty." }
    require(playerName.isNotBlank()) { "Player name cannot be empty." }
    require(action.isNotBlank()) { "Activity action cannot be empty." }
    firestore
        .collection(GROUPS_COLLECTION)
        .document(groupId)
        .collection(ACTIVITIES_COLLECTION)
        .add(
            mapOf(
                "userId" to userId,
                "playerName" to playerName,
                "action" to action,
                "diceResult" to diceResult,
                "timestamp" to FieldValue.serverTimestamp(),
            )
        )
        .await()
  }

  override fun observeSession(groupId: String): Flow<GameSessionSnapshot> = callbackFlow {
    val registration =
        firestore
            .collection(GROUPS_COLLECTION)
            .document(groupId)
            .collection(GM_DATA_COLLECTION)
            .document(SESSION_DOCUMENT)
            .addSnapshotListener { snapshot, error ->
              when {
                error != null -> close(error)
                snapshot != null -> {
                  try {
                    trySend(decodeSession(snapshot.data))
                  } catch (failure: Exception) {
                    close(failure)
                  }
                }
              }
            }
    awaitClose { registration.remove() }
  }

  private fun observePlayerActions(groupId: String): Flow<List<GameActivityEntry>> = callbackFlow {
    val registration =
        firestore
            .collection(GROUPS_COLLECTION)
            .document(groupId)
            .collection(ACTIVITIES_COLLECTION)
            .addSnapshotListener { snapshot, error ->
              when {
                error != null -> close(error)
                snapshot != null -> {
                  try {
                    trySend(
                        snapshot.documents.map { document ->
                          val data = document.data ?: error("Invalid activity data.")
                          GameActivityEntry(
                              id = document.id,
                              userId = data.string("userId"),
                              playerName = data.string("playerName"),
                              action = data.string("action"),
                              diceResult = data["diceResult"] as? String,
                              timestamp =
                                  document.getDate("timestamp")?.time ?: System.currentTimeMillis(),
                          )
                        }
                    )
                  } catch (failure: Exception) {
                    close(failure)
                  }
                }
              }
            }
    awaitClose { registration.remove() }
  }

  override suspend fun saveSession(
      groupId: String,
      session: GameSessionSnapshot,
  ): Result<Unit> = writeResult {
    require(groupId.isNotBlank()) { "Group ID cannot be empty." }
    validateSession(session)
    firestore
        .collection(GROUPS_COLLECTION)
        .document(groupId)
        .collection(GM_DATA_COLLECTION)
        .document(SESSION_DOCUMENT)
        .set(encodeSession(session))
        .await()
  }

  override fun observeLore(groupId: String): Flow<List<GameLoreEntry>> =
      observeLoreQuery(groupId, sharedOnly = false)

  override fun observeSharedLore(groupId: String): Flow<List<GameLoreEntry>> =
      observeLoreQuery(groupId, sharedOnly = true)

  private fun observeLoreQuery(
      groupId: String,
      sharedOnly: Boolean,
  ): Flow<List<GameLoreEntry>> = callbackFlow {
    val collection =
        firestore.collection(GROUPS_COLLECTION).document(groupId).collection(LORE_COLLECTION)
    val query: Query = if (sharedOnly) collection.whereEqualTo("isShared", true) else collection
    val registration = query.addSnapshotListener { snapshot, error ->
      when {
        error != null -> close(error)
        snapshot != null -> {
          try {
            trySend(
                snapshot.documents.mapNotNull { document ->
                  document.data?.let { decodeLore(document.id, it) }
                }
            )
          } catch (failure: Exception) {
            close(failure)
          }
        }
      }
    }
    awaitClose { registration.remove() }
  }

  override suspend fun saveLore(groupId: String, lore: GameLoreEntry): Result<Unit> = writeResult {
    require(groupId.isNotBlank()) { "Group ID cannot be empty." }
    require(lore.id.isNotBlank()) { "Lore document ID cannot be empty." }
    require(lore.lore.title.isNotBlank()) { "Lore document title cannot be empty." }
    firestore
        .collection(GROUPS_COLLECTION)
        .document(groupId)
        .collection(LORE_COLLECTION)
        .document(lore.id)
        .set(encodeLore(lore))
        .await()
  }

  override suspend fun setLoreVisibility(
      groupId: String,
      documentId: String,
      isShared: Boolean,
  ): Result<Unit> = writeResult {
    require(groupId.isNotBlank()) { "Group ID cannot be empty." }
    require(documentId.isNotBlank()) { "Lore document ID cannot be empty." }
    firestore
        .collection(GROUPS_COLLECTION)
        .document(groupId)
        .collection(LORE_COLLECTION)
        .document(documentId)
        .update("isShared", isShared)
        .await()
  }

  private fun encodeSession(session: GameSessionSnapshot): Map<String, Any> =
      mapOf(
          "enemies" to
              session.enemies.map { enemy ->
                mapOf(
                    "id" to enemy.id,
                    "character" to JDRFileJsonCodec.serializeCharacter(enemy.character),
                    "initiative" to enemy.initiative,
                )
              },
          "isCombatActive" to session.isCombatActive,
          "currentRound" to session.currentRound,
          "npcs" to
              session.npcs.map { npc ->
                mapOf(
                    "id" to npc.id,
                    "name" to npc.name,
                    "portraitUrl" to npc.portraitUrl,
                    "information" to npc.information,
                )
              },
      )

  private fun decodeSession(data: Map<String, Any>?): GameSessionSnapshot {
    if (data == null) return GameSessionSnapshot()
    val enemies =
        (data["enemies"] as? List<*>)
            ?.map { item ->
              val enemy = item as? Map<*, *> ?: error("Invalid enemy data.")
              GameEnemy(
                  id = enemy.string("id"),
                  character =
                      JDRFileJsonCodec.deserializeCharacter(
                          enemy["character"] as? String ?: error("Invalid enemy character.")
                      ),
                  initiative = enemy.int("initiative"),
              )
            }
            .orEmpty()
    val npcs =
        (data["npcs"] as? List<*>)
            ?.map { item ->
              val npc = item as? Map<*, *> ?: error("Invalid NPC data.")
              GameNpc(
                  id = npc.string("id"),
                  name = npc.string("name"),
                  portraitUrl = npc["portraitUrl"] as? String,
                  information = npc.string("information"),
              )
            }
            .orEmpty()
    val round = (data["currentRound"] as? Number)?.toInt() ?: 1
    require(round >= 1) { "Combat round must be at least one." }
    val session =
        GameSessionSnapshot(
            enemies = enemies,
            isCombatActive = data["isCombatActive"] as? Boolean ?: false,
            currentRound = round,
            npcs = npcs,
        )
    validateSession(session)
    return session
  }

  private fun validateSession(session: GameSessionSnapshot) {
    require(session.currentRound >= 1) { "Combat round must be at least one." }
    require(session.enemies.map { it.id }.toSet().size == session.enemies.size) {
      "Enemy IDs must be unique."
    }
    session.enemies.forEach { enemy ->
      require(enemy.id.isNotBlank() && enemy.character.name.isNotBlank()) {
        "Enemy ID and name are required."
      }
      require(
          enemy.character.hp.max > 0 && enemy.character.hp.current in 0..enemy.character.hp.max
      ) {
        "Enemy HP must be between zero and max HP."
      }
      require(enemy.character.stats.filter { it.statID == "armorClass" }.all { it.value >= 0.0 }) {
        "Enemy AC cannot be negative."
      }
    }
    require(session.npcs.map { it.id }.toSet().size == session.npcs.size) {
      "NPC IDs must be unique."
    }
    session.npcs.forEach { npc ->
      require(npc.id.isNotBlank() && npc.name.isNotBlank()) { "NPC ID and name are required." }
    }
  }

  private fun encodeLore(lore: GameLoreEntry): Map<String, Any?> =
      mapOf(
          "content" to JDRFileJsonCodec.serializeLore(lore.lore),
          "isShared" to lore.isShared,
      )

  private fun decodeLore(id: String, data: Map<String, Any>): GameLoreEntry {
    val file =
        JDRFileJsonCodec.deserializeLore(
            data["content"] as? String ?: error("Invalid lore content.")
        )
    return GameLoreEntry(
        id = id,
        lore = file,
        isShared = data["isShared"] as? Boolean ?: false,
    )
  }

  private fun Map<*, *>.string(key: String): String =
      this[key] as? String ?: error("Invalid $key value.")

  private fun Map<*, *>.int(key: String): Int =
      (this[key] as? Number)?.toInt() ?: error("Invalid $key value.")

  private fun Map<*, *>.stringList(key: String): List<String> =
      ((this[key] as? List<*>) ?: emptyList<Any>()).map {
        it as? String ?: error("Invalid $key value.")
      }

  private suspend fun writeResult(block: suspend () -> Unit): Result<Unit> =
      try {
        block()
        Result.success(Unit)
      } catch (cancellation: CancellationException) {
        throw cancellation
      } catch (failure: Exception) {
        Result.failure(failure)
      }

  private companion object {
    const val GROUPS_COLLECTION = "campaign_groups"
    const val GM_DATA_COLLECTION = "gm_data"
    const val LORE_COLLECTION = "gm_lore"
    const val ACTIVITIES_COLLECTION = "activities"
    const val SESSION_DOCUMENT = "session"
  }
}
