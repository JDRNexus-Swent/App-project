// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
// Co-authored-by: Codex <codex@openai.com>
package com.github.se.jdrnexus.ui.gameManagement.gm.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.github.se.jdrnexus.R
import com.github.se.jdrnexus.resources.C
import com.github.se.jdrnexus.ui.gameManagement.gm.ActivityItem

/**
 * Shows up to eight recent campaign actions, including the player name and any recorded dice roll.
 * Displays an empty message when the feed has no entries.
 *
 * @param activityFeed Campaign activity ordered from newest to oldest.
 * @param onDismiss Called when the GM hides the activity section.
 */
@Composable
internal fun GmActivitySection(activityFeed: List<ActivityItem>, onDismiss: () -> Unit) {
  SectionCard(GmSection.ACTIVITY, onDismiss = onDismiss) {
    if (activityFeed.isEmpty()) {
      Text(
          stringResource(R.string.game_gm_empty_activity),
          color = MaterialTheme.colorScheme.tertiary,
      )
    }
    // The repository already orders the feed newest first.
    activityFeed.take(8).forEach { activity ->
      Row(
          modifier = Modifier.testTag(C.Tag.gameGmActivity(activity.id)),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Text(
            "•",
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
        )
        Text(
            stringResource(
                R.string.game_gm_activity,
                activity.playerName,
                activity.action,
            ) +
                activity.diceResult
                    ?.let { stringResource(R.string.game_gm_dice_result, it) }
                    .orEmpty(),
            color = MaterialTheme.colorScheme.onSecondary,
        )
      }
    }
  }
}
