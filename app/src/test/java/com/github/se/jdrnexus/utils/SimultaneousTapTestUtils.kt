// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.utils

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry

/**
 * Runs the click action of each node tagged with [tags], in order, inside one UI-thread block.
 *
 * No frame can run between the clicks, so every button still has the `enabled` value of the last
 * composition, as when a user taps several buttons in the same frame. The actions are fetched
 * before the block because semantics queries cannot run on the UI thread.
 */
fun ComposeContentTestRule.tapInOneFrame(vararg tags: String) {
  val clicks = tags.map { tag ->
    checkNotNull(onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsActions.OnClick].action)
  }
  runOnUiThread { clicks.forEach { click -> click() } }
  waitForIdle()
}

/**
 * A [LifecycleOwner] whose state a test sets directly, starting at RESUMED. Provided as
 * `LocalLifecycleOwner` around a screen, it stands in for the navigation entry that the
 * NavController moves to CREATED when the screen is left.
 */
class TestScreenLifecycleOwner : LifecycleOwner {
  private val registry =
      LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.RESUMED }

  override val lifecycle: Lifecycle
    get() = registry

  /** Moves this owner to [state], as the NavController does when its screen is left. */
  fun moveTo(state: Lifecycle.State) {
    registry.currentState = state
  }
}
