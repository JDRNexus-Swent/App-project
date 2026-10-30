// Co-authored-by: AI Agent
package com.github.se.jdrnexus.model.repository

import com.github.se.jdrnexus.ui.dice.DiceRollResult
import com.github.se.jdrnexus.ui.dice.DiceType
import com.github.se.jdrnexus.utils.FirebaseEmulatorTestUtils
import com.github.se.jdrnexus.utils.waitFor
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import io.mockk.every
import io.mockk.mockk
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DiceRepositoryFirestoreTest {

  private lateinit var repository: DiceRepositoryFirestore
  private lateinit var firestore: FirebaseFirestore
  private lateinit var authRepository: AuthRepository

  private val testUser =
      AuthUser(uid = "testUid", email = "test@example.com", username = "testUser")
  private lateinit var testGroupId: String

  @Before
  fun setUp() {
    testGroupId = UUID.randomUUID().toString()
    firestore = FirebaseEmulatorTestUtils.getFirestore()

    authRepository = mockk()
    every { authRepository.authState } returns flowOf(testUser)
    repository = DiceRepositoryFirestore(firestore, authRepository)
  }

  @After
  fun tearDown() {
    runBlocking {
      FirebaseEmulatorTestUtils.tearDown(firestore, "campaign_groups/$testGroupId/dice_rolls")
    }
  }

  @Test
  fun testSaveRollResultAndGetHistory() {
    runBlocking {
      val rollResult =
          DiceRollResult(diceType = DiceType.D20, rawValue = 15, modifier = 2, totalValue = 17)

      val result = repository.saveRollResult(testGroupId, rollResult)
      assertTrue("saveRollResult should succeed", result.isSuccess)

      // Retrieve history
      val historyResult = repository.getDiceRollsHistory(testGroupId)
      assertTrue("getDiceRollsHistory should succeed", historyResult.isSuccess)
      val historyList = historyResult.getOrThrow()
      assertEquals(1, historyList.size)
      val entity = historyList.first()

      assertEquals("testUid", entity.userId)
      assertEquals("testUser", entity.username)
      assertEquals("D20", entity.diceType)
      assertEquals(2, entity.modifier)
      assertEquals(17, entity.totalValue)
    }
  }

  @Test
  fun testSaveRollResultFailsIfUserNotAuthenticated() {
    runBlocking {
      every { authRepository.authState } returns flowOf(null) // Unauthenticated user

      val rollResult =
          DiceRollResult(diceType = DiceType.D6, rawValue = 3, modifier = 0, totalValue = 3)
      val result = repository.saveRollResult(testGroupId, rollResult)
      assertTrue("saveRollResult should fail when unauthenticated", result.isFailure)
      assertEquals("User is not authenticated", result.exceptionOrNull()?.message)
    }
  }

  @Test
  fun testGetDiceRollsHistory_emptyCollection() {
    runBlocking {
      val groupId = UUID.randomUUID().toString()
      val historyResult = repository.getDiceRollsHistory(groupId)
      assertTrue("getDiceRollsHistory should succeed on empty collection", historyResult.isSuccess)
      assertTrue("List should be empty", historyResult.getOrThrow().isEmpty())
    }
  }

  @Test
  fun testOfflineBehavior() {
    runBlocking {
      val groupId = UUID.randomUUID().toString()
      val rollResult =
          DiceRollResult(diceType = DiceType.D20, rawValue = 10, modifier = 1, totalValue = 11)

      firestore.enableNetwork().await()
      repository.saveRollResult(groupId, rollResult)

      // Wait until Firestore has persisted the document and the local cache is updated.
      FirebaseEmulatorTestUtils.pumpMainLooperUntil {
        firestore
            .collection("campaign_groups")
            .document(groupId)
            .collection("dice_rolls")
            .get(Source.CACHE)
            .await()
            .documents
            .isNotEmpty()
      }

      firestore.disableNetwork().await()

      val historyResult = repository.getDiceRollsHistory(groupId)
      assertTrue(
          "getDiceRollsHistory should succeed from cache while offline",
          historyResult.isSuccess,
      )
      val historyList = historyResult.getOrThrow()
      assertEquals(1, historyList.size)
      assertEquals("D20", historyList.first().diceType)

      firestore.enableNetwork().await()
    }
  }

  @Test
  fun testSaveRollResult_Exception() {
    runBlocking {
      val rollResult =
          DiceRollResult(diceType = DiceType.D6, rawValue = 3, modifier = 0, totalValue = 3)

      // Empty group ID causes Firestore to throw an exception when creating document path
      val result = repository.saveRollResult("", rollResult)
      assertTrue(
          "saveRollResult should fail and catch exception for invalid path",
          result.isFailure,
      )
    }
  }

  @Test
  fun testGetDiceRollsHistory_Exception() {
    runBlocking {
      // Empty group ID causes Firestore to throw an exception when creating document path
      val result = repository.getDiceRollsHistory("")
      assertTrue(
          "getDiceRollsHistory should fail and catch exception for invalid path",
          result.isFailure,
      )
    }
  }

  @Test
  fun testGetDiceRollsStream() {
    runBlocking {
      val rollResult1 =
          DiceRollResult(diceType = DiceType.D10, rawValue = 5, modifier = 1, totalValue = 6)
      repository.saveRollResult(testGroupId, rollResult1)

      // Initial flow state should have 1 item
      val initialList = repository.getDiceRollsStream(testGroupId).waitFor { it.isNotEmpty() }
      assertEquals(1, initialList.size)
      assertEquals("D10", initialList.first().diceType)

      val latestList = AtomicReference<List<DiceRollEntity>>(emptyList())
      val job =
          launch(Dispatchers.Default) {
            repository.getDiceRollsStream(testGroupId).collect { latestList.set(it) }
          }
      try {
        FirebaseEmulatorTestUtils.pumpMainLooperUntil { latestList.get().size == 1 }
        // add delay to be sure both roll are not register with the same timestamp
        delay(10)

        // Now save a second roll
        val rollResult2 =
            DiceRollResult(diceType = DiceType.D4, rawValue = 2, modifier = 0, totalValue = 2)
        repository.saveRollResult(testGroupId, rollResult2)

        FirebaseEmulatorTestUtils.pumpMainLooperUntil { latestList.get().size == 2 }

        val items = latestList.get()
        assertEquals(2, items.size)
        // Order is descending by timestamp, so rollResult2 should be first if it has a higher
        // timestamp.
        assertEquals("D4", items[0].diceType)
        assertEquals("D10", items[1].diceType)
      } finally {
        job.cancel()
      }
    }
  }
}
