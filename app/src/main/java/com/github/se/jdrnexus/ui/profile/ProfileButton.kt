// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.se.jdrnexus.R
import com.github.se.jdrnexus.resources.C

/** Diameter of the round profile button. */
private val PROFILE_BUTTON_SIZE = 38.dp

/**
 * Round avatar button opening the profile screen, meant for a top bar's leading slot. Home and
 * Campaigns both show it.
 *
 * @param onClick Called when the user opens the profile screen.
 * @param enabled Whether the button reacts to taps; Home disables it while the user signs out.
 */
@Composable
fun ProfileButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
  val colors = MaterialTheme.colorScheme
  IconButton(
      onClick = onClick,
      enabled = enabled,
      modifier =
          modifier
              .size(PROFILE_BUTTON_SIZE)
              .clip(CircleShape)
              .background(colors.surfaceContainerLowest)
              .border(1.dp, colors.outline, CircleShape)
              .testTag(C.Tag.open_profile),
  ) {
    Icon(
        Icons.Filled.Person,
        contentDescription = stringResource(R.string.profile_open),
        tint = colors.onSurfaceVariant,
    )
  }
}
