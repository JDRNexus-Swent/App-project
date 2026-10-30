// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.ui.dice

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatModifierTest {

  @Test
  fun formatModifierAddsExplicitSign() {
    assertEquals("+0", formatModifier(0))
    assertEquals("+4", formatModifier(4))
    assertEquals("-3", formatModifier(-3))
  }
}
