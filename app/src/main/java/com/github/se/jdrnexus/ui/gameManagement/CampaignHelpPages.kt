// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.ui.gameManagement

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.se.jdrnexus.R
import com.github.se.jdrnexus.model.personalSpace.CampaignGroup
import com.github.se.jdrnexus.ui.help.HelpPage

/**
 * Share of the window height the Campaigns help pictures take: a quarter, large enough to see which
 * parts of the example card are outlined. The same for every page, so the pictures keep one scale
 * while the user swipes.
 */
internal const val CAMPAIGN_HELP_ILLUSTRATION_HEIGHT_FRACTION = 0.25f

/**
 * Pages of the Campaigns screen's help, in the order [GameManagementScreen] shows them. Every user
 * sees every page, whatever their role in their groups.
 *
 * Each illustration is drawn with the screen's own composables, so the help shows exactly what the
 * user sees: the "Create a new group" and "Join a group" buttons, then a GameMaster's card of an
 * example group with the parts each page explains outlined.
 */
fun campaignHelpPages(): List<HelpPage> =
    listOf(
        HelpPage(
            title = R.string.campaign_help_start_title,
            body = R.string.campaign_help_start_body,
            illustration = { CreateAndJoinButtons() },
            illustrationMaxHeightFraction = CAMPAIGN_HELP_ILLUSTRATION_HEIGHT_FRACTION,
        ),
        HelpPage(
            title = R.string.campaign_help_roles_title,
            body = R.string.campaign_help_roles_body,
            illustration = {
              ExampleCampaignCard(setOf(CampaignCardElement.ROLE, CampaignCardElement.INVITE))
            },
            illustrationMaxHeightFraction = CAMPAIGN_HELP_ILLUSTRATION_HEIGHT_FRACTION,
        ),
        HelpPage(
            title = R.string.campaign_help_workspace_title,
            body = R.string.campaign_help_workspace_body,
            illustration = {
              ExampleCampaignCard(
                  setOf(CampaignCardElement.ENTER_WORKSPACE, CampaignCardElement.PLAY)
              )
            },
            illustrationMaxHeightFraction = CAMPAIGN_HELP_ILLUSTRATION_HEIGHT_FRACTION,
        ),
    )

/** The two buttons at the top of the Campaigns screen, one under the other as on the screen. */
@Composable
private fun CreateAndJoinButtons() {
  val colors = gameManagementColors()
  Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
    CreateGroupButton(colors = colors, onClick = {})
    JoinGroupButton(colors = colors, onClick = {})
  }
}

/** Number of members of the example group, so its card reads "3 members". */
private const val EXAMPLE_MEMBER_COUNT = 3

/**
 * A GameMaster's card for an example group, with [highlighted] outlined. The callbacks do nothing:
 * the help draws the card as an inert picture.
 */
@Composable
private fun ExampleCampaignCard(highlighted: Set<CampaignCardElement>) {
  val group =
      CampaignGroup(
          name = stringResource(R.string.campaign_help_example_name),
          description = stringResource(R.string.campaign_help_example_system),
          participantIds = List(EXAMPLE_MEMBER_COUNT) { index -> "example-member-$index" },
      )
  CampaignGroupCard(
      group = group,
      isGameMaster = true,
      colors = gameManagementColors(),
      onEnterWorkspace = {},
      onPlay = {},
      onManageGroup = {},
      onInvite = {},
      inviteEnabled = true,
      highlighted = highlighted,
  )
}
