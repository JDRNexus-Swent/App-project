// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.github.se.jdrnexus

import androidx.lifecycle.Lifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavOptions
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationHelperTest {
  @Test
  fun ignoresNavigationWhileCurrentEntryIsTransitioning() {
    var state = Lifecycle.State.RESUMED
    val fakeLifecycle = mockk<Lifecycle>()
    every { fakeLifecycle.currentState } answers { state }
    val entry = mockk<NavBackStackEntry>()
    every { entry.lifecycle } returns fakeLifecycle
    val navController = mockk<NavHostController>()
    every { navController.currentBackStackEntry } returns entry
    every {
      navController.navigate(
          any<String>(),
          any<NavOptions>(),
          isNull(),
      )
    } answers { state = Lifecycle.State.STARTED }
    val navigation = NavigationHelper(navController)

    navigation.navigate("campaigns")
    navigation.navigate("files")

    verify(exactly = 1) { navController.navigate("campaigns", any<NavOptions>(), isNull()) }
    verify(exactly = 0) { navController.navigate("files", any<NavOptions>(), isNull()) }
  }

  @Test
  fun popBackStackReturnsWhetherAnEntryWasPopped() {
    var state = Lifecycle.State.RESUMED
    val fakeLifecycle = mockk<Lifecycle>()
    every { fakeLifecycle.currentState } answers { state }
    val entry = mockk<NavBackStackEntry>()
    every { entry.lifecycle } returns fakeLifecycle
    val navController = mockk<NavHostController>()
    every { navController.currentBackStackEntry } returns entry
    every { navController.previousBackStackEntry } returns entry
    every { navController.popBackStack() } returns true
    val navigation = NavigationHelper(navController)

    assertTrue(navigation.popBackStack())

    state = Lifecycle.State.STARTED
    assertFalse(navigation.popBackStack())
  }
}
