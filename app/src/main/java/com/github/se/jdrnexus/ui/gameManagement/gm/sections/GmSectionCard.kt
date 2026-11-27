// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
// Co-authored-by: Codex <codex@openai.com>
package com.github.se.jdrnexus.ui.gameManagement

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.github.se.jdrnexus.R
import com.github.se.jdrnexus.resources.C

/**
 * Dashboard sections the GM can hide, in the order they are listed when hidden.
 *
 * @property title Resource of the heading shown on the section card and its restore button.
 * @property key Suffix of the section's hide/restore test tags.
 */
internal enum class GmSection(@StringRes val title: Int, val key: String) {
  COMBAT(R.string.game_gm_section_combat, "combat"),
  ACTIVITY(R.string.game_gm_section_activity, "activity"),
  NPCS(R.string.game_gm_section_npcs, "npcs"),
  LORE(R.string.game_gm_section_lore, "lore"),
}

/** Displays restore actions for dashboard [sections] hidden by the user. */
@Composable
internal fun HiddenSections(sections: List<GmSection>, onRestore: (GmSection) -> Unit) {
  SectionCard(
      stringResource(R.string.game_gm_hidden_sections),
      modifier = Modifier.testTag(C.Tag.game_gm_hidden_sections),
  ) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      sections.forEach { section ->
        OutlinedButton(
            onClick = { onRestore(section) },
            modifier = Modifier.testTag(C.Tag.gameGmRestore(section.key)),
            contentPadding = PaddingValues(horizontal = 12.dp),
        ) {
          Text(stringResource(R.string.game_gm_restore_section, stringResource(section.title)))
        }
      }
    }
  }
}

/** Displays the card of a hideable dashboard [section]; [onDismiss] hides it. */
@Composable
internal fun SectionCard(
    section: GmSection,
    onDismiss: () -> Unit,
    titleTrailing: String? = null,
    content: @Composable () -> Unit,
) =
    SectionCard(
        stringResource(section.title),
        Modifier,
        onDismiss,
        titleTrailing,
        C.Tag.gameGmHide(section.key),
        content,
    )

/**
 * Displays a titled card section with optional dismissal and trailing status text.
 *
 * @param title Section heading.
 * @param modifier Modifier applied to the card.
 * @param onDismiss Optional callback for the section's close action.
 * @param titleTrailing Optional status text shown beside the heading.
 * @param dismissTestTag Test tag of the close action.
 * @param content Content rendered inside the card.
 */
@Composable
private fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    onDismiss: (() -> Unit)? = null,
    titleTrailing: String? = null,
    dismissTestTag: String? = null,
    content: @Composable () -> Unit,
) {
  Card(
      modifier = modifier,
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary),
      shape = RoundedCornerShape(22.dp),
  ) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onSecondary,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.headlineSmall,
            fontFamily = FontFamily.Serif,
        )
        titleTrailing?.let {
          Box(Modifier.size(8.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
          Spacer(Modifier.width(6.dp))
          Text(it, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
        }
        onDismiss?.let {
          IconButton(
              onClick = it,
              modifier = dismissTestTag?.let(Modifier::testTag) ?: Modifier,
          ) {
            Icon(
                Icons.Filled.Close,
                contentDescription = stringResource(R.string.game_gm_dismiss_cd),
                tint = MaterialTheme.colorScheme.tertiary,
            )
          }
        }
      }
      content()
    }
  }
}
