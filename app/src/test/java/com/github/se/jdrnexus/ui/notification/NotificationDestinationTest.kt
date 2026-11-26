// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.ui.notification

import com.github.se.jdrnexus.model.notification.NotificationType
import com.github.se.jdrnexus.utils.testNotification
import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationDestinationTest {

  @Test
  fun invitationLeadsToCampaignsEvenWithAGroup() {
    val invitation =
        testNotification(ID, type = NotificationType.GROUP_INVITATION, groupId = GROUP_ID)

    assertEquals(NotificationDestination.Campaigns, invitation.destination())
  }

  @Test
  fun membershipChangeLeadsToItsGroupWorkspace() {
    val change = testNotification(ID, type = NotificationType.MEMBERSHIP_CHANGE, groupId = GROUP_ID)

    assertEquals(NotificationDestination.GroupWorkspace(GROUP_ID), change.destination())
  }

  @Test
  fun campaignUpdateLeadsToItsGroupWorkspace() {
    val update = testNotification(ID, type = NotificationType.CAMPAIGN_UPDATE, groupId = GROUP_ID)

    assertEquals(NotificationDestination.GroupWorkspace(GROUP_ID), update.destination())
  }

  @Test
  fun groupNotificationWithoutGroupLeadsNowhere() {
    val update = testNotification(ID, type = NotificationType.CAMPAIGN_UPDATE, groupId = null)

    assertEquals(null, update.destination())
  }

  @Test
  fun groupNotificationWithBlankGroupLeadsNowhere() {
    val change =
        testNotification(ID, type = NotificationType.MEMBERSHIP_CHANGE, groupId = BLANK_GROUP_ID)

    assertEquals(null, change.destination())
  }

  private companion object {
    const val ID = "notification-1"
    const val GROUP_ID = "group-1"

    /** A corrupted group id: present but made of spaces only. */
    const val BLANK_GROUP_ID = "   "
  }
}
