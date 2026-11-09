// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.github.se.jdrnexus

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.navOptions

/** Remembers a [NavigationHelper] bound to [navController]. */
@Composable
fun rememberNavigationHelper(navController: NavHostController): NavigationHelper =
    remember(navController) { NavigationHelper(navController) }

/**
 * Wraps a [NavHostController] and ignores navigation requests made while a transition is running,
 * which prevents duplicate or conflicting navigation from rapid taps. Assumes the app's root screen
 * is Home or SignIn. Holds no state.
 */
class NavigationHelper(private val navController: NavHostController) {

  /** Whether there is a previous entry to go back to. */
  val canGoBack: Boolean
    get() = navController.previousBackStackEntry != null

  /** Navigates to [route], or does nothing if a transition is in progress. */
  fun navigate(route: String, options: NavOptionsBuilder.() -> Unit = {}) {
    if (!canNavigate()) return
    navController.navigate(route, navOptions(options), null)
  }

  /**
   * Pops the back stack, or does nothing if a transition is in progress.
   *
   * Refuses an inclusive pop of Home or SignIn. Returns true if something was popped; false means
   * the request was ignored or there was nothing to pop, so use [canGoBack] to decide on fallbacks.
   */
  fun popBackStack(route: String? = null, inclusive: Boolean = false): Boolean {
    if (!canNavigate()) return false

    if (route == null) {
      if (!canGoBack) return false
      return navController.popBackStack()
    }

    if (inclusive && route in setOf(AllRoutes.Home.name, AllRoutes.SignIn.name)) return false
    return navController.popBackStack(route, inclusive)
  }

  private fun canNavigate() =
      navController.currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED
}
