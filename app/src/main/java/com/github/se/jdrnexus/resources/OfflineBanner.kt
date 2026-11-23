// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.resources

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.github.se.jdrnexus.R

/**
 * Whether the device is online, as seen by every screen. `JDRNexusApp` provides it once from the
 * connectivity repository; it is true when nothing provides it, so previews and screen tests show
 * no banner unless they ask for one.
 */
val LocalIsOnline = compositionLocalOf { true }

/**
 * "You're offline" strip, as wide as its container, shown under a screen's header while
 * [LocalIsOnline] is false, and nothing while online. [AppTopBar] shows it, so screens using it
 * need no code of their own.
 *
 * It only informs: many screens (the dice, the personal space) keep working offline, so it uses the
 * theme's neutral surface colours rather than its error colours.
 */
@Composable
fun OfflineBanner(modifier: Modifier = Modifier) {
  if (LocalIsOnline.current) return
  val colors = MaterialTheme.colorScheme
  Row(
      modifier =
          modifier
              .fillMaxWidth()
              .background(colors.surfaceVariant)
              .padding(horizontal = 16.dp, vertical = 6.dp)
              // Screen readers announce the banner as soon as it appears, without waiting for
              // the user to move onto it.
              .semantics { liveRegion = LiveRegionMode.Polite }
              .testTag(C.Tag.offline_banner),
      horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(
        Icons.Outlined.CloudOff,
        contentDescription = null,
        tint = colors.onSurfaceVariant,
        modifier = Modifier.size(18.dp),
    )
    Text(
        stringResource(R.string.offline_banner),
        color = colors.onSurfaceVariant,
        style = MaterialTheme.typography.labelLarge,
    )
  }
}
