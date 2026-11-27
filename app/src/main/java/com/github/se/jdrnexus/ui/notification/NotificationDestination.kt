// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.ui.notification

import com.github.se.jdrnexus.model.notification.Notification
import com.github.se.jdrnexus.model.notification.NotificationType

/** Screen of the app that a notification leads to when the user selects it. */
sealed interface NotificationDestination {
  /** The workspace of the campaign group the notification is about. */
  data class GroupWorkspace(val groupId: String) : NotificationDestination

  /**
   * The campaign list. Invitations lead here: the invited user is not a member of the group yet, so
   * its workspace is not accessible, but the campaign list is where they join groups.
   */
  data object Campaigns : NotificationDestination
}

/**
 * Where selecting this notification should take the user, or `null` when it has no related content
 * to open (a group update without a valid group).
 */
fun Notification.destination(): NotificationDestination? =
    when (type) {
      NotificationType.GROUP_INVITATION -> NotificationDestination.Campaigns
      NotificationType.MEMBERSHIP_CHANGE,
      NotificationType.CAMPAIGN_UPDATE ->
          groupId?.takeIf(String::isNotBlank)?.let(NotificationDestination::GroupWorkspace)
    }
