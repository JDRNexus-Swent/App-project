// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.resources

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.se.jdrnexus.R

/**
 * "Needs a connection" line, shown next to actions that are disabled because the device is offline,
 * so the user knows why they cannot use them. Nothing is shown while [isOnline] is true.
 *
 * @param tag test tag of the line, so a screen showing several can tell them apart.
 */
@Composable
fun NeedsConnectionHint(
    isOnline: Boolean,
    modifier: Modifier = Modifier,
    tag: String = C.Tag.needs_connection,
) {
  if (isOnline) return
  val colors = MaterialTheme.colorScheme
  Row(
      modifier = modifier.testTag(tag),
      horizontalArrangement = Arrangement.spacedBy(6.dp),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(
        Icons.Outlined.CloudOff,
        contentDescription = null,
        tint = colors.onSurfaceVariant,
        modifier = Modifier.size(16.dp),
    )
    Text(
        stringResource(R.string.needs_connection),
        color = colors.onSurfaceVariant,
        style = MaterialTheme.typography.labelMedium,
    )
  }
}
