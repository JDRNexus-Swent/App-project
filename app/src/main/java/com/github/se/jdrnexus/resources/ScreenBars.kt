// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.github.se.jdrnexus.resources

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import com.github.se.jdrnexus.ui.theme.LocalAppUiColors

private data class ScreenTab(
    val label: String,
    val icon: ImageVector,
)

private val screenTabs =
    listOf(
        ScreenTab("Home", Icons.Filled.Home),
        ScreenTab("Campaigns", Icons.AutoMirrored.Outlined.MenuBook),
        ScreenTab("Files", Icons.Outlined.Folder),
        ScreenTab("Map", Icons.Outlined.Map),
    )

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun AppTopBar(
    title: String,
    modifier: Modifier = Modifier,
    titleModifier: Modifier = Modifier,
    titleContent: (@Composable () -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    backTestTag: String = C.Tag.shared_back_button,
    leadingContent: @Composable () -> Unit = {},
    trailingContent: @Composable () -> Unit = {},
) {
  val colors = LocalAppUiColors.current
  CenterAlignedTopAppBar(
      modifier = modifier.testTag(C.Tag.shared_top_bar),
      title = {
        if (titleContent == null) {
          Text(title, modifier = titleModifier)
        } else {
          titleContent()
        }
      },
      navigationIcon = {
        if (onBack != null) {
          IconButton(
              onClick = onBack,
              modifier = Modifier.testTag(backTestTag),
          ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        } else {
          leadingContent()
        }
      },
      actions = { trailingContent() },
      colors =
          TopAppBarDefaults.topAppBarColors(
              containerColor = colors.surface,
              titleContentColor = colors.primaryText,
              navigationIconContentColor = colors.accent,
              actionIconContentColor = colors.accent,
          ),
  )
}

@Composable
fun ScreenBottomBar(
    selectedTab: String,
    onTabSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
  val colors = LocalAppUiColors.current
  NavigationBar(
      modifier = modifier.testTag(C.Tag.shared_bottom_bar),
      containerColor = colors.surface,
  ) {
    screenTabs.forEach { tab ->
      NavigationBarItem(
          selected = selectedTab == tab.label,
          onClick = { onTabSelected(tab.label) },
          icon = { Icon(tab.icon, contentDescription = null) },
          label = { Text(tab.label) },
          modifier =
              Modifier.testTag(C.Tag.bottomTab(tab.label.lowercase())).semantics {
                selected = selectedTab == tab.label
              },
          colors =
              NavigationBarItemDefaults.colors(
                  selectedIconColor = colors.accent,
                  selectedTextColor = colors.accent,
                  indicatorColor = colors.raisedSurface,
                  unselectedIconColor = colors.secondaryText,
                  unselectedTextColor = colors.secondaryText,
              ),
      )
    }
  }
}

/**
 * Side rail with the same destinations as [ScreenBottomBar], for short, wide windows such as a
 * phone in landscape, where a bottom bar would take too much of the height. Material's adaptive
 * navigation makes the same swap.
 */
@Composable
fun ScreenNavigationRail(
    selectedTab: String,
    onTabSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
  val colors = LocalAppUiColors.current
  NavigationRail(
      modifier = modifier.testTag(C.Tag.shared_navigation_rail),
      containerColor = colors.surface,
  ) {
    // Centered vertically, so the tabs stay within thumb reach on a phone held sideways.
    Spacer(Modifier.weight(1f))
    screenTabs.forEach { tab ->
      NavigationRailItem(
          selected = selectedTab == tab.label,
          onClick = { onTabSelected(tab.label) },
          icon = { Icon(tab.icon, contentDescription = null) },
          label = { Text(tab.label) },
          modifier =
              Modifier.testTag(C.Tag.railTab(tab.label.lowercase())).semantics {
                selected = selectedTab == tab.label
              },
          colors =
              NavigationRailItemDefaults.colors(
                  selectedIconColor = colors.accent,
                  selectedTextColor = colors.accent,
                  indicatorColor = colors.raisedSurface,
                  unselectedIconColor = colors.secondaryText,
                  unselectedTextColor = colors.secondaryText,
              ),
      )
    }
    Spacer(Modifier.weight(1f))
  }
}

fun showComingSoon(context: Context, feature: String) {
  Toast.makeText(context, "$feature is coming soon.", Toast.LENGTH_SHORT).show()
}
