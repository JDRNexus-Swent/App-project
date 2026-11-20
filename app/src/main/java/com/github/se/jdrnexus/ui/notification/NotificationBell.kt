// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.ui.notification

import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.se.jdrnexus.R
import com.github.se.jdrnexus.resources.C

/** Largest unread count written in full on the badge; anything above shows as "9+". */
internal const val MAX_DISPLAYED_UNREAD_COUNT = 9

/**
 * Width of the ring drawn around the badge in the top bar's color, so the badge stays visible on
 * top of the bell, which uses the same accent color.
 */
private val BADGE_RING_WIDTH = 1.5.dp

/**
 * Entry point to the notification screen, meant for a top bar's trailing actions.
 *
 * Shows a badge with the number of unread notifications when there are any.
 *
 * @param unreadCount Number of unread notifications of the signed-in user.
 * @param onClick Called when the user opens the notification screen.
 */
@Composable
fun NotificationBell(
    unreadCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
  val colors = MaterialTheme.colorScheme
  val hasUnread = unreadCount > 0
  val description =
      stringResource(
          if (hasUnread) R.string.notifications_open_unread else R.string.notifications_open
      )
  IconButton(onClick = onClick, modifier = modifier.testTag(C.Tag.notification_bell)) {
    BadgedBox(
        badge = {
          if (hasUnread) {
            Badge(
                modifier =
                    Modifier.border(BADGE_RING_WIDTH, colors.surface, CircleShape)
                        .testTag(C.Tag.notification_unread_badge),
                containerColor = colors.primary,
                contentColor = colors.onPrimary,
            ) {
              Text(
                  if (unreadCount > MAX_DISPLAYED_UNREAD_COUNT) {
                    stringResource(
                        R.string.notifications_badge_overflow,
                        MAX_DISPLAYED_UNREAD_COUNT,
                    )
                  } else {
                    unreadCount.toString()
                  }
              )
            }
          }
        }
    ) {
      Icon(
          imageVector = if (hasUnread) Icons.Filled.Notifications else Icons.Outlined.Notifications,
          contentDescription = description,
      )
    }
  }
}
