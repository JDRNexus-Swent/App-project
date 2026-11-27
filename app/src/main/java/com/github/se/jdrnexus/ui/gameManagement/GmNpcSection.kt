// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
// Co-authored-by: Codex <codex@openai.com>
package com.github.se.jdrnexus.ui.gameManagement

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.se.jdrnexus.R
import com.github.se.jdrnexus.resources.C

@Composable
internal fun GmNpcSection(
    npcs: List<Npc>,
    onDismiss: () -> Unit,
    onSelectNpc: (String) -> Unit,
) {
  SectionCard(GmSection.NPCS, onDismiss = onDismiss) {
    if (npcs.isEmpty()) {
      Text(
          stringResource(R.string.game_gm_empty_npcs),
          modifier = Modifier.testTag(C.Tag.game_gm_empty_npcs),
          color = MaterialTheme.colorScheme.tertiary,
      )
    } else {
      LazyRow(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        items(npcs) { npc -> NpcItem(npc) { onSelectNpc(npc.id) } }
      }
    }
  }
}

@Composable
internal fun GmNpcDialog(npc: Npc, onDismiss: () -> Unit) {
  AlertDialog(
      onDismissRequest = onDismiss,
      title = { Text(npc.name) },
      text = {
        Text(npc.information.ifBlank { stringResource(R.string.game_gm_npc_no_information) })
      },
      confirmButton = {
        TextButton(onClick = onDismiss) {
          Text(stringResource(R.string.game_gm_close))
        }
      },
  )
}

/** Displays one campaign [npc] as a portrait and name; [onClick] opens its information. */
@Composable
private fun NpcItem(npc: Npc, onClick: () -> Unit) {
  Column(
      modifier =
          Modifier.width(84.dp).testTag(C.Tag.gameGmNpc(npc.id)).clickable(onClick = onClick),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    Box(
        modifier =
            Modifier.size(48.dp)
                .clip(CircleShape)
                .border(1.dp, MaterialTheme.colorScheme.tertiary, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
      Icon(
          Icons.Filled.Person,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onSecondary,
      )
    }
    Text(
        text = npc.name,
        color = MaterialTheme.colorScheme.onSecondary,
        style = MaterialTheme.typography.bodySmall,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
  }
}
