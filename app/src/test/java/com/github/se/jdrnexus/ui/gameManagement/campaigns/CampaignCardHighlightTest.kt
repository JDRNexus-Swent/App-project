// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.ui.gameManagement

import org.junit.Assert.assertEquals
import org.junit.Test

class CampaignCardHighlightTest {
  @Test
  fun everyPartIsNormalWhenNothingIsHighlighted() {
    CampaignCardElement.entries.forEach { element ->
      assertEquals(CardHighlight.NORMAL, cardHighlight(element, emptySet()))
    }
    assertEquals(CardHighlight.NORMAL, cardHighlight(null, emptySet()))
  }

  @Test
  fun highlightedPartsAreFocused() {
    val highlighted = setOf(CampaignCardElement.PLAY, CampaignCardElement.ENTER_WORKSPACE)

    assertEquals(CardHighlight.FOCUSED, cardHighlight(CampaignCardElement.PLAY, highlighted))
    assertEquals(
        CardHighlight.FOCUSED,
        cardHighlight(CampaignCardElement.ENTER_WORKSPACE, highlighted),
    )
  }

  @Test
  fun otherPointablePartsAreDimmed() {
    val highlighted = setOf(CampaignCardElement.PLAY)

    assertEquals(CardHighlight.DIMMED, cardHighlight(CampaignCardElement.ROLE, highlighted))
    assertEquals(CardHighlight.DIMMED, cardHighlight(CampaignCardElement.INVITE, highlighted))
  }

  @Test
  fun partsTheHelpNeverPointsAtAreDimmedWhileSomethingIsHighlighted() {
    assertEquals(CardHighlight.DIMMED, cardHighlight(null, setOf(CampaignCardElement.ROLE)))
  }
}
