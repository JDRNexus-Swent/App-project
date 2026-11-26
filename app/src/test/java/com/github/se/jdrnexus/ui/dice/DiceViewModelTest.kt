// Co-authored-by: Gemini
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.ui.dice

import androidx.lifecycle.ViewModel
import com.github.se.jdrnexus.model.connectivity.NEEDS_CONNECTION
import com.github.se.jdrnexus.model.repository.DiceLogic
import com.github.se.jdrnexus.utils.FakeConnectivityRepository
import com.github.se.jdrnexus.utils.FakeDiceRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DiceViewModelTest {

  private lateinit var diceLogic: DiceLogic
  private lateinit var diceRepository: FakeDiceRepository
  private val testDispatcher = StandardTestDispatcher()

  @Before
  fun setup() {
    Dispatchers.setMain(testDispatcher)
    diceLogic = mockk()
    diceRepository = FakeDiceRepository()
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun `initial state is correct`() {
    val viewModel = DiceViewModel(diceLogic, diceRepository)
    val state = viewModel.uiState.value

    assertEquals(DiceType.D20, state.selectedDice)
    assertEquals(0, state.modifier)
    assertNull(state.currentResult)
    assertFalse(state.isRolling)
    assertNull(state.errorMessage)
  }

  @Test
  fun `factory creates DiceViewModel successfully`() {
    val factory = DiceViewModelFactory(diceLogic, diceRepository, "group_1")
    val viewModel = factory.create(DiceViewModel::class.java)
    assertNotNull(viewModel)
  }

  @Test
  fun `factory throws exception for unknown ViewModel class`() {
    val factory = DiceViewModelFactory(diceLogic, diceRepository, null)

    class DummyViewModel : ViewModel()

    try {
      factory.create(DummyViewModel::class.java)
      fail("An IllegalArgumentException should have been thrown")
    } catch (e: IllegalArgumentException) {
      // Check the fail message is the good one
      assertTrue(e.message?.contains("Unknown ViewModel class") == true)
    }
  }

  @Test
  fun `selectDice updates selected dice type`() {
    val viewModel = DiceViewModel(diceLogic, diceRepository)
    viewModel.selectDice(DiceType.D10)
    assertEquals(DiceType.D10, viewModel.uiState.value.selectedDice)
  }

  @Test
  fun `setModifier applies bounds between -20 and 20`() {
    val viewModel = DiceViewModel(diceLogic, diceRepository)

    // Normal assignment
    viewModel.setModifier(15)
    assertEquals(15, viewModel.uiState.value.modifier)

    // Exceed upper bound
    viewModel.setModifier(25)
    assertEquals(20, viewModel.uiState.value.modifier)

    // Drop below lower bound
    viewModel.setModifier(-25)
    assertEquals(-20, viewModel.uiState.value.modifier)
  }

  @Test
  fun `increaseModifier applies bounds between -20 and 20`() {
    val viewModel = DiceViewModel(diceLogic, diceRepository)

    // Normal addition
    viewModel.increaseModifier(5)
    assertEquals(5, viewModel.uiState.value.modifier)

    // Exceed upper bound
    viewModel.increaseModifier(20)
    assertEquals(20, viewModel.uiState.value.modifier)

    // Drop below lower bound
    viewModel.increaseModifier(-50)
    assertEquals(-20, viewModel.uiState.value.modifier)
  }

  @Test
  fun `rollDice in offline mode generates result without saving`() = runTest {
    val viewModel = DiceViewModel(diceLogic, diceRepository = null) // No groupId
    val result = DiceRollResult(DiceType.D20, 15, 0, 15)
    every { diceLogic.roll(any(), any()) } returns result

    viewModel.rollDice()

    val state = viewModel.uiState.value
    assertTrue(state.isRolling)
    assertEquals(result, state.currentResult)
  }

  @Test
  fun `rollDice in online mode saves result successfully`() = runTest {
    val viewModel = DiceViewModel(diceLogic, diceRepository, groupId = "group_1")
    val result = DiceRollResult(DiceType.D20, 15, 0, 15)
    every { diceLogic.roll(any(), any()) } returns result
    diceRepository.saveResult = Result.success(Unit)

    viewModel.rollDice()
    advanceUntilIdle() // Process coroutines

    assertEquals(listOf("group_1" to result), diceRepository.savedRolls)
    assertNull(viewModel.uiState.value.errorMessage)
  }

  @Test
  fun `rollDice handles repository save failure`() = runTest {
    val viewModel = DiceViewModel(diceLogic, diceRepository, groupId = "group_1")
    val result = DiceRollResult(DiceType.D20, 10, 0, 10)
    val errorMsg = "Permission error"

    every { diceLogic.roll(any(), any()) } returns result
    diceRepository.saveResult = Result.failure(Exception(errorMsg))

    viewModel.rollDice()
    advanceUntilIdle()

    assertTrue(viewModel.uiState.value.errorMessage?.contains(errorMsg) == true)
  }

  @Test
  fun groupRollSyncFailsWithNeedsConnectionWithoutSavingWhileOffline() = runTest {
    val connectivity = FakeConnectivityRepository(isOnline = false)
    val viewModel = DiceViewModel(diceLogic, diceRepository, "group_1", connectivity)
    every { diceLogic.roll(any(), any()) } returns DiceRollResult(DiceType.D20, 12, 0, 12)

    viewModel.rollDice()
    runCurrent()

    assertEquals("Failed to sync roll: $NEEDS_CONNECTION", viewModel.uiState.value.errorMessage)
    assertEquals(emptyList<Pair<String, DiceRollResult>>(), diceRepository.savedRolls)
  }

  @Test
  fun groupRollSyncStopsWithNeedsConnectionWhenTheConnectionDrops() = runTest {
    val connectivity = FakeConnectivityRepository(isOnline = true)
    val viewModel = DiceViewModel(diceLogic, diceRepository, "group_1", connectivity)
    every { diceLogic.roll(any(), any()) } returns DiceRollResult(DiceType.D20, 12, 0, 12)
    diceRepository.saveGate = CompletableDeferred()
    viewModel.rollDice()
    runCurrent()
    assertNull(viewModel.uiState.value.errorMessage)

    connectivity.online.value = false
    runCurrent()

    assertEquals("Failed to sync roll: $NEEDS_CONNECTION", viewModel.uiState.value.errorMessage)
  }

  @Test
  fun groupRollIsSavedWhileOnline() = runTest {
    val connectivity = FakeConnectivityRepository(isOnline = true)
    val viewModel = DiceViewModel(diceLogic, diceRepository, "group_1", connectivity)
    val result = DiceRollResult(DiceType.D20, 12, 0, 12)
    every { diceLogic.roll(any(), any()) } returns result

    viewModel.rollDice()
    runCurrent()

    assertEquals(listOf("group_1" to result), diceRepository.savedRolls)
    assertNull(viewModel.uiState.value.errorMessage)
  }

  @Test
  fun `rollDice ignores request if already rolling`() {
    val viewModel = DiceViewModel(diceLogic, diceRepository)
    val result = DiceRollResult(DiceType.D20, 10, 0, 10)
    every { diceLogic.roll(any(), any()) } returns result

    viewModel.rollDice() // First call starts rolling
    viewModel.rollDice() // Second call should be ignored

    // Logic roll is only called once
    verify(exactly = 1) { diceLogic.roll(any(), any()) }
  }

  @Test
  fun `onRollAnimationFinished resets isRolling state`() {
    val viewModel = DiceViewModel(diceLogic, diceRepository)
    every { diceLogic.roll(any(), any()) } returns DiceRollResult(DiceType.D20, 10, 0, 10)

    viewModel.rollDice()
    assertTrue(viewModel.uiState.value.isRolling)

    viewModel.onRollAnimationFinished()
    assertFalse(viewModel.uiState.value.isRolling)
  }

  @Test
  fun `dismissError clears errorMessage`() = runTest {
    val viewModel = DiceViewModel(diceLogic, diceRepository, groupId = "group_1")
    every { diceLogic.roll(any(), any()) } returns DiceRollResult(DiceType.D20, 10, 0, 10)
    diceRepository.saveResult = Result.failure(Exception("Error"))

    viewModel.rollDice()
    advanceUntilIdle() // Ensure error state is reached

    viewModel.dismissError()
    assertNull(viewModel.uiState.value.errorMessage)
  }

  @Test
  fun `rollDice formats error message correctly on repository failure`() = runTest {
    val viewModel = DiceViewModel(diceLogic, diceRepository, groupId = "group_1")
    val result = DiceRollResult(DiceType.D20, 10, 0, 10)
    val simulatedError = "Permission denied"

    every { diceLogic.roll(any(), any()) } returns result
    // Simulate an error
    diceRepository.saveResult = Result.failure(Exception(simulatedError))

    viewModel.rollDice()
    advanceUntilIdle()

    assertEquals(
        "Failed to sync roll: $simulatedError",
        viewModel.uiState.value.errorMessage,
    )
  }
}
