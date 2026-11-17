// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.github.se.jdrnexus.model.notification

import java.util.Date

/** Events that can be presented to a user as an application notification. */
enum class NotificationType {
  /** An invitation to join a campaign group. */
  GROUP_INVITATION,
  /** A join, removal, or other membership change for a campaign. */
  MEMBERSHIP_CHANGE,
  /** A change to campaign content or campaign status. */
  CAMPAIGN_UPDATE,
}

/** A notification persisted for exactly one authenticated user. */
data class Notification(
    val id: String = "",
    val userId: String = "",
    val type: NotificationType = NotificationType.CAMPAIGN_UPDATE,
    val title: String = "",
    val message: String = "",
    val groupId: String? = null,
    val createdAt: Date? = null,
    val isRead: Boolean = false,
)
