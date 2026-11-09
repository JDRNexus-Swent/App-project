// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.github.se.jdrnexus

import androidx.lifecycle.Lifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavOptions
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
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
  fun navigateForwardsNavOptions() {
    val fakeLifecycle = mockk<Lifecycle>()
    every { fakeLifecycle.currentState } returns Lifecycle.State.RESUMED
    val entry = mockk<NavBackStackEntry>()
    every { entry.lifecycle } returns fakeLifecycle
    val navController = mockk<NavHostController>()
    every { navController.currentBackStackEntry } returns entry
    every { navController.navigate(any<String>(), any<NavOptions>(), isNull()) } returns Unit
    val navigation = NavigationHelper(navController)
    val options = slot<NavOptions>()

    navigation.navigate("campaigns") { launchSingleTop = true }

    verify { navController.navigate("campaigns", capture(options), isNull()) }
    assertTrue(options.captured.shouldLaunchSingleTop())
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

  @Test
  fun popBackStackWithoutPreviousEntryDoesNotPop() {
    val fakeLifecycle = mockk<Lifecycle>()
    every { fakeLifecycle.currentState } returns Lifecycle.State.RESUMED
    val entry = mockk<NavBackStackEntry>()
    every { entry.lifecycle } returns fakeLifecycle
    val navController = mockk<NavHostController>()
    every { navController.currentBackStackEntry } returns entry
    every { navController.previousBackStackEntry } returns null
    val navigation = NavigationHelper(navController)

    assertFalse(navigation.popBackStack())

    verify(exactly = 0) { navController.popBackStack() }
  }

  @Test
  fun popBackStackToRouteUsesRequestedDestination() {
    val fakeLifecycle = mockk<Lifecycle>()
    every { fakeLifecycle.currentState } returns Lifecycle.State.RESUMED
    val entry = mockk<NavBackStackEntry>()
    every { entry.lifecycle } returns fakeLifecycle
    val navController = mockk<NavHostController>()
    every { navController.currentBackStackEntry } returns entry
    every { navController.popBackStack("campaigns", false) } returns true
    val navigation = NavigationHelper(navController)

    assertTrue(navigation.popBackStack("campaigns"))

    verify(exactly = 1) { navController.popBackStack("campaigns", false) }
  }

  @Test
  fun inclusivePopOfRootDestinationIsRefused() {
    val fakeLifecycle = mockk<Lifecycle>()
    every { fakeLifecycle.currentState } returns Lifecycle.State.RESUMED
    val rootDestination = mockk<NavDestination>()
    every { rootDestination.route } returns "Home"
    val rootEntry = mockk<NavBackStackEntry>()
    every { rootEntry.destination } returns rootDestination
    every { rootEntry.lifecycle } returns fakeLifecycle
    val navController = mockk<NavHostController>()
    every { navController.currentBackStackEntry } returns rootEntry
    every { navController.currentBackStack } returns MutableStateFlow(listOf(rootEntry))
    val navigation = NavigationHelper(navController)

    assertFalse(navigation.popBackStack("Home", inclusive = true))

    verify(exactly = 0) { navController.popBackStack("Home", true) }
  }
}
