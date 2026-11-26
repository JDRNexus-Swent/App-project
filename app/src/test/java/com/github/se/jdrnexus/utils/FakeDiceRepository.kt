// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.utils

import com.github.se.jdrnexus.model.repository.DiceRepository
import com.github.se.jdrnexus.model.repository.DiceRollEntity
import com.github.se.jdrnexus.ui.dice.DiceRollResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory [DiceRepository] for ViewModel and repository tests.
 *
 * The test sets what the repository answers ([saveResult], [rollsByGroup], [historyFailure]) and
 * reads what it was asked to save ([savedRolls]). A saved roll is only recorded: it is not added to
 * [rollsByGroup], because the stored entity depends on the signed-in user.
 */
class FakeDiceRepository : DiceRepository {

  /** Returned by every [saveRollResult] call. */
  var saveResult: Result<Unit> = Result.success(Unit)

  /** Every [saveRollResult] call, in order, as (group ID, roll). */
  val savedRolls = mutableListOf<Pair<String, DiceRollResult>>()

  /** Stored rolls per group ID, newest first; read by [getDiceRollsHistory] and the stream. */
  val rollsByGroup = MutableStateFlow<Map<String, List<DiceRollEntity>>>(emptyMap())

  /** When not null, [getDiceRollsHistory] fails with it. */
  var historyFailure: Throwable? = null

  override suspend fun saveRollResult(groupId: String, rollResult: DiceRollResult): Result<Unit> {
    savedRolls += groupId to rollResult
    return saveResult
  }

  override suspend fun getDiceRollsHistory(groupId: String): Result<List<DiceRollEntity>> =
      historyFailure?.let { Result.failure(it) }
          ?: Result.success(rollsByGroup.value[groupId].orEmpty())

  override fun getDiceRollsStream(groupId: String): Flow<List<DiceRollEntity>> = rollsByGroup.map {
    it[groupId].orEmpty()
  }
}
