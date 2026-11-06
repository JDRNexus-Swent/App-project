package com.github.se.jdrnexus.ui.groupWorkSpace

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.se.jdrnexus.R

enum class GroupWorkspaceTab(
    val icon: ImageVector,
) {
  HOME(Icons.Default.Home),
  CAMPAIGNS(Icons.AutoMirrored.Filled.MenuBook),
  FILES(Icons.Default.Folder),
  MAP(Icons.Default.Map),
}

/**
 * Displays bottom navigation for the group workspace.
 *
 * @param selectedTab Currently selected destination.
 * @param onTabSelected Called when a destination is selected.
 * @param modifier Modifier applied to the bottom bar.
 */
@Composable
fun GroupWorkspaceBottomBar(
    selectedTab: GroupWorkspaceTab,
    onTabSelected: (GroupWorkspaceTab) -> Unit,
    modifier: Modifier = Modifier,
) {
  val colors = MaterialTheme.colorScheme
  Column(
      modifier =
          modifier
              .fillMaxWidth()
              .background(colors.surface)
              .windowInsetsPadding(WindowInsets.navigationBars)
  ) {
    HorizontalDivider(
        thickness = 1.dp,
        color = colors.onSurface,
    )

    Row(
        modifier = Modifier.fillMaxWidth().height(82.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      listOf(GroupWorkspaceTab.CAMPAIGNS).forEach { tab ->
        val selected = tab == selectedTab
        val label =
            stringResource(
                when (tab) {
                  GroupWorkspaceTab.HOME -> R.string.workspace_tab_home
                  GroupWorkspaceTab.CAMPAIGNS -> R.string.workspace_tab_campaigns
                  GroupWorkspaceTab.FILES -> R.string.workspace_tab_files
                  GroupWorkspaceTab.MAP -> R.string.workspace_tab_map
                }
            )
        val itemColor =
            if (selected) {
              colors.primary
            } else {
              colors.tertiary
            }

        Column(
            modifier = Modifier.weight(1f).fillMaxHeight().clickable { onTabSelected(tab) },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top,
        ) {
          // The small line above the selected destination.
          Box(
              modifier =
                  Modifier.width(44.dp)
                      .height(3.dp)
                      .background(
                          color = if (selected) colors.primary else Color.Transparent,
                          shape =
                              RoundedCornerShape(
                                  bottomStart = 2.dp,
                                  bottomEnd = 2.dp,
                              ),
                      )
          )

          Spacer(modifier = Modifier.height(12.dp))

          Icon(
              imageVector = tab.icon,
              contentDescription = label,
              tint = itemColor,
              modifier = Modifier.size(28.dp),
          )

          Spacer(modifier = Modifier.height(7.dp))

          Text(
              text = label,
              color = itemColor,
              fontSize = 14.sp,
              fontWeight =
                  if (selected) {
                    FontWeight.SemiBold
                  } else {
                    FontWeight.Normal
                  },
          )
        }
      }
    }
  }
}
