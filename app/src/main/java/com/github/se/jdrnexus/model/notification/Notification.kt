// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.github.se.jdrnexus.model.notification

import java.util.Date

enum class NotificationType {
  GROUP_INVITATION,
  MEMBERSHIP_CHANGE,
  CAMPAIGN_UPDATE,
}

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
