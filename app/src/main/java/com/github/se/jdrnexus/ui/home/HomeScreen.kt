// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.github.se.jdrnexus.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.se.jdrnexus.resources.AppTopBar
import com.github.se.jdrnexus.resources.MAX_LANDSCAPE_CONTENT_WIDTH
import com.github.se.jdrnexus.resources.ScreenBottomBar
import com.github.se.jdrnexus.resources.ScreenNavigationRail
import com.github.se.jdrnexus.resources.showComingSoon
import com.github.se.jdrnexus.resources.useLandscapeLayout
import com.github.se.jdrnexus.ui.authentication.AuthStatus
import com.github.se.jdrnexus.ui.authentication.AuthViewModel
import com.github.se.jdrnexus.ui.theme.LocalAppUiColors

object TestTags {
  const val TITLE = "home_title"
  const val PROFILE_PIC = "home_image_placeholder"
  const val QUICKTOOLS = "home_quicktools"
  const val PSPACEBUTTON = "home_to_pspace"
  const val DICEBUTTON = "home_to_dice"
  const val DICE_ICON = "home_dice_icon"
  const val GROUPMANAGEMENTBUTTON = "home_to_group_management"
  const val GROUPMANAGEMENT_ICON = "home_group_management_icon"
  const val PSPACE_ICON = "home_personal_space_icon"
  const val LOGOUT = "home_logout"
  val BUTTONS = listOf(DICEBUTTON, GROUPMANAGEMENTBUTTON)
}

/**
 * Signed-in home: the shared top bar (profile, title, log out), a placeholder for upcoming features
 * and the Quicktools shortcuts. Navigation uses the shared bottom bar, or the side rail on short,
 * wide windows such as a phone in landscape, where the Quicktools also move next to their label.
 */
@Composable
fun HomeScreen(
    viewModel: AuthViewModel = viewModel(factory = AuthViewModel.Factory),
    modifier: Modifier = Modifier,
    onLogout: () -> Unit = {},
    onPersonalSpaceClick: () -> Unit = {},
    onDiceClick: () -> Unit = {},
    onGroupManagementClick: () -> Unit = {},
) {

  val uiState by viewModel.uiState.collectAsState()
  var logoutRequested by remember { mutableStateOf(false) }
  val context = LocalContext.current
  val colors = LocalAppUiColors.current

  LaunchedEffect(uiState.status) {
    when (uiState.status) {
      AuthStatus.Idle -> {
        if (logoutRequested) {
          logoutRequested = false
          onLogout()
        }
      }
      is AuthStatus.Error -> logoutRequested = false
      else -> Unit
    }
  }

  val onTabSelected: (String) -> Unit = { tab ->
    when (tab) {
      HOME_TAB -> Unit
      "Campaigns" -> onGroupManagementClick()
      "Files" -> onPersonalSpaceClick()
      "Map" -> showComingSoon(context, "Map")
    }
  }
  val quickTools =
      listOf(
          QuickTool(Icons.Filled.Casino, TestTags.DICEBUTTON, TestTags.DICE_ICON, onDiceClick),
          QuickTool(
              Icons.Filled.Mic,
              TestTags.GROUPMANAGEMENTBUTTON,
              TestTags.GROUPMANAGEMENT_ICON,
              onGroupManagementClick,
          ),
          QuickTool(
              Icons.Filled.Edit,
              TestTags.PSPACEBUTTON,
              TestTags.PSPACE_ICON,
              onPersonalSpaceClick,
          ),
      )

  BoxWithConstraints(modifier = modifier.fillMaxSize().background(colors.background)) {
    val isLandscape = useLandscapeLayout(maxWidth, maxHeight)
    Row(modifier = Modifier.fillMaxSize()) {
      if (isLandscape) {
        ScreenNavigationRail(selectedTab = HOME_TAB, onTabSelected = onTabSelected)
      }
      Scaffold(
          modifier = Modifier.weight(1f).fillMaxHeight(),
          containerColor = colors.background,
          topBar = {
            AppTopBar(
                title = "JDRNexus",
                titleModifier = Modifier.testTag(TestTags.TITLE),
                leadingContent = {
                  Surface(
                      modifier = Modifier.size(40.dp).testTag(TestTags.PROFILE_PIC),
                      color = colors.raisedSurface,
                      shape = MaterialTheme.shapes.medium,
                  ) {
                    Box {}
                  }
                },
                trailingContent = {
                  TextButton(
                      onClick = {
                        logoutRequested = true
                        viewModel.signOut()
                      },
                      modifier = Modifier.testTag(TestTags.LOGOUT),
                  ) {
                    Text("Log out")
                  }
                },
            )
          },
          bottomBar = {
            if (!isLandscape) {
              ScreenBottomBar(selectedTab = HOME_TAB, onTabSelected = onTabSelected)
            }
          },
      ) { contentPadding ->
        if (isLandscape) {
          HomeLandscapeContent(contentPadding = contentPadding, quickTools = quickTools)
        } else {
          HomePortraitContent(contentPadding = contentPadding, quickTools = quickTools)
        }
      }
    }
  }
}

/** Tab of the shared navigation that Home highlights. */
private const val HOME_TAB = "Home"

/**
 * A Quicktools shortcut: its [icon], the tags tests find its button and icon by, and its action.
 */
private data class QuickTool(
    val icon: ImageVector,
    val buttonTag: String,
    val iconTag: String,
    val onClick: () -> Unit,
)

/** Placeholder above the Quicktools, then the Quicktools label and a row of square shortcuts. */
@Composable
private fun HomePortraitContent(contentPadding: PaddingValues, quickTools: List<QuickTool>) {
  Column(modifier = Modifier.fillMaxSize().padding(contentPadding).padding(16.dp)) {
    ComingSoonPlaceholder(modifier = Modifier.weight(1f).fillMaxWidth())
    QuicktoolsLabel()
    QuickToolsRow(
        quickTools = quickTools,
        modifier = Modifier.fillMaxWidth(2f / 3f).padding(top = 12.dp),
    )
  }
}

/** Share of the landscape content width taken by the Quicktools shortcuts. */
private const val LANDSCAPE_TOOLS_WIDTH_FRACTION = 1f / 3f

/**
 * Landscape content: the placeholder fills the height, and the Quicktools label and shortcuts share
 * one row at the bottom, so the shortcuts stay square without pushing the placeholder off screen.
 * Past [MAX_LANDSCAPE_CONTENT_WIDTH] (tablets) the content is centered instead of stretched.
 */
@Composable
private fun HomeLandscapeContent(contentPadding: PaddingValues, quickTools: List<QuickTool>) {
  Box(
      modifier = Modifier.fillMaxSize().padding(contentPadding),
      contentAlignment = Alignment.TopCenter,
  ) {
    BoxWithConstraints(
        modifier =
            Modifier.widthIn(max = MAX_LANDSCAPE_CONTENT_WIDTH)
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
      val toolsWidth = maxWidth * LANDSCAPE_TOOLS_WIDTH_FRACTION
      Column(modifier = Modifier.fillMaxSize()) {
        ComingSoonPlaceholder(modifier = Modifier.weight(1f).fillMaxWidth())
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
          QuicktoolsLabel()
          QuickToolsRow(quickTools = quickTools, modifier = Modifier.width(toolsWidth))
        }
      }
    }
  }
}

/** Placeholder until more Home screen features are available. */
@Composable
private fun ComingSoonPlaceholder(modifier: Modifier) {
  Box(modifier = modifier, contentAlignment = Alignment.Center) {
    Text(
        text = "More features coming soon",
        style = MaterialTheme.typography.bodyLarge,
        color = LocalAppUiColors.current.secondaryText,
    )
  }
}

/** Title of the Quicktools section. */
@Composable
private fun QuicktoolsLabel() {
  Text(
      text = "Quicktools",
      modifier = Modifier.testTag(TestTags.QUICKTOOLS),
      style = MaterialTheme.typography.headlineSmall,
      color = LocalAppUiColors.current.primaryText,
  )
}

/** The [quickTools] as square buttons sharing the width given by [modifier]. */
@Composable
private fun QuickToolsRow(quickTools: List<QuickTool>, modifier: Modifier) {
  Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
    quickTools.forEach { tool ->
      Button(
          onClick = tool.onClick,
          modifier = Modifier.weight(1f).aspectRatio(1f).testTag(tool.buttonTag),
          shape = RoundedCornerShape(8.dp),
          colors =
              ButtonDefaults.buttonColors(
                  containerColor = MaterialTheme.colorScheme.onPrimary,
                  contentColor = MaterialTheme.colorScheme.primary,
              ),
      ) {
        Icon(
            imageVector = tool.icon,
            contentDescription = null,
            modifier = Modifier.size(32.dp).testTag(tool.iconTag),
        )
      }
    }
  }
}
