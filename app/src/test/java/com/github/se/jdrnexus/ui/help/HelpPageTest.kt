// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.ui.help

import com.github.se.jdrnexus.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HelpPageTest {
  @Test
  fun pageWithoutSettingsHasNoIllustrationAndTheDefaultFraction() {
    val page = page()

    assertNull(page.illustration)
    assertEquals(DEFAULT_HELP_ILLUSTRATION_HEIGHT_FRACTION, page.illustrationMaxHeightFraction)
  }

  @Test
  fun wholeWindowIsAllowed() {
    assertEquals(1f, page(fraction = 1f).illustrationMaxHeightFraction)
  }

  @Test(expected = IllegalArgumentException::class)
  fun zeroFractionIsRejected() {
    page(fraction = 0f)
  }

  @Test(expected = IllegalArgumentException::class)
  fun negativeFractionIsRejected() {
    page(fraction = -0.1f)
  }

  @Test(expected = IllegalArgumentException::class)
  fun fractionAboveTheWindowIsRejected() {
    page(fraction = 1.5f)
  }

  private fun page(fraction: Float = DEFAULT_HELP_ILLUSTRATION_HEIGHT_FRACTION) =
      HelpPage(
          R.string.campaign_help_start_title,
          R.string.campaign_help_start_body,
          illustrationMaxHeightFraction = fraction,
      )
}
