// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.ui.gameManagement

import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp

/** Parts of a campaign card that the Campaigns help can point at. */
internal enum class CampaignCardElement {
  /** The "GameMaster" / "Player" badge. */
  ROLE,
  /** The "Play" button. */
  PLAY,
  /** The "Enter workspace" button. */
  ENTER_WORKSPACE,
  /** The GameMaster's "Invite" button. */
  INVITE,
}

/** How one part of a campaign card is drawn. */
internal enum class CardHighlight {
  /** No part is pointed at: drawn as on the Campaigns screen. */
  NORMAL,
  /** The help points at this part: outlined. */
  FOCUSED,
  /** The help points at other parts: faded. */
  DIMMED,
}

/**
 * Decides how a part of a campaign card is drawn.
 *
 * @param element the part, or `null` for a part the help never points at (banner, name, details,
 *   "Manage group"), which is faded whenever the help points at something.
 * @param highlighted the parts the help points at; empty on the Campaigns screen itself.
 */
internal fun cardHighlight(
    element: CampaignCardElement?,
    highlighted: Set<CampaignCardElement>,
): CardHighlight =
    when {
      highlighted.isEmpty() -> CardHighlight.NORMAL
      element != null && element in highlighted -> CardHighlight.FOCUSED
      else -> CardHighlight.DIMMED
    }

/** Opacity of the parts the help does not point at. */
private const val DIMMED_ALPHA = 0.3f

/**
 * Outlines or fades this part of a campaign card according to [cardHighlight]; leaves it unchanged
 * when [highlighted] is empty.
 *
 * The outline is thick because the help may draw the card several times smaller.
 */
internal fun Modifier.campaignCardHighlight(
    element: CampaignCardElement?,
    highlighted: Set<CampaignCardElement>,
    colors: GameManagementColors,
): Modifier =
    when (cardHighlight(element, highlighted)) {
      CardHighlight.NORMAL -> this
      CardHighlight.FOCUSED -> this.border(3.dp, colors.accent, RoundedCornerShape(8.dp))
      CardHighlight.DIMMED -> this.alpha(DIMMED_ALPHA)
    }
