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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class GroupWorkspaceTab(
    val label: String,
    val icon: ImageVector,
) {
  HOME("Home", Icons.Default.Home),
  CAMPAIGNS("Campaigns", Icons.AutoMirrored.Filled.MenuBook),
  FILES("Files", Icons.Default.Folder),
  MAP("Map", Icons.Default.Map),
}

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
      GroupWorkspaceTab.entries.forEach { tab ->
        val selected = tab == selectedTab
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
              contentDescription = tab.label,
              tint = itemColor,
              modifier = Modifier.size(28.dp),
          )

          Spacer(modifier = Modifier.height(7.dp))

          Text(
              text = tab.label,
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
