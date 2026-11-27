// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
// Co-authored-by: Codex <codex@openai.com>
package com.github.se.jdrnexus.ui.gameManagement

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.se.jdrnexus.R
import com.github.se.jdrnexus.resources.C

@Composable
internal fun GmLoreSection(
    loreDocuments: List<LoreDocument>,
    expandedLoreId: String?,
    onDismiss: () -> Unit,
    onToggleLore: (String) -> Unit,
) {
  SectionCard(GmSection.LORE, onDismiss = onDismiss) {
    if (loreDocuments.isEmpty()) {
      Text(
          stringResource(R.string.game_gm_empty_lore),
          modifier = Modifier.testTag(C.Tag.game_gm_empty_lore),
          color = MaterialTheme.colorScheme.tertiary,
      )
    } else {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        loreDocuments.forEach { document ->
          LoreRow(
              document = document,
              expanded = expandedLoreId == document.id,
              onExpand = { onToggleLore(document.id) },
          )
        }
      }
    }
  }
}

/**
 * Displays a lore document summary with controls to expand it or select it.
 *
 * @param document Lore document being summarized.
 * @param expanded Whether the full section text is visible.
 * @param onExpand Toggles the document's expanded state.
 */
@Composable
private fun LoreRow(document: LoreDocument, expanded: Boolean, onExpand: () -> Unit) {
  val sectionFormat = stringResource(R.string.game_gm_lore_section)
  Column(modifier = Modifier.testTag(C.Tag.gameGmLore(document.id))) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
            document.title,
            color = MaterialTheme.colorScheme.onSecondary,
            fontWeight = FontWeight.Bold,
        )
        Text(
            document.lore.sections.joinToString("\n") {
              sectionFormat.format(it.title, it.description)
            },
            color = MaterialTheme.colorScheme.onSecondary,
            maxLines = if (expanded) Int.MAX_VALUE else 2,
            overflow = TextOverflow.Ellipsis,
        )
      }
      TextButton(
          onClick = onExpand,
          modifier = Modifier.testTag(C.Tag.gameGmLoreExpand(document.id)),
      ) {
        Text(
            stringResource(
                if (expanded) R.string.game_gm_lore_collapse else R.string.game_gm_lore_read
            ),
            color = MaterialTheme.colorScheme.primary,
        )
      }
    }
    Text(
        stringResource(
            if (document.isShared) R.string.game_gm_lore_shared else R.string.game_gm_lore_private
        ),
        color = MaterialTheme.colorScheme.onSecondary,
    )
  }
}
