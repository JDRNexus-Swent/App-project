// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.se.jdrnexus.resources.AppTopBar
import com.github.se.jdrnexus.resources.ScreenBottomBar
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

@Composable
fun HomeScreen(
    viewModel: AuthViewModel = viewModel(factory = AuthViewModel.Factory),
    modifier: Modifier = Modifier,
    onLogout: () -> Unit = {},
    onPersonalSpaceClick: () -> Unit = {},
    onDiceClick: () -> Unit = {},
    onGroupManagementClick: () -> Unit = {},
    onMapClick: () -> Unit = {},
) {

  val uiState by viewModel.uiState.collectAsState()
  var logoutRequested by remember { mutableStateOf(false) }
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

  Scaffold(
      modifier = modifier.fillMaxSize(),
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
        ScreenBottomBar(
            selectedTab = "Home",
            onTabSelected = { tab ->
              when (tab) {
                "Home" -> Unit
                "Campaigns" -> onGroupManagementClick()
                "Files" -> onPersonalSpaceClick()
                "Map" -> onMapClick()
              }
            },
        )
      },
  ) { contentPadding ->
    Column(modifier = Modifier.fillMaxSize().padding(contentPadding).padding(16.dp)) {
      Box(
          modifier = Modifier.weight(1f).fillMaxWidth(),
          contentAlignment = Alignment.Center,
      ) {
        // Placeholder until more Home screen features are available.
        Text(
            text = "More features coming soon",
            style = MaterialTheme.typography.bodyLarge,
            color = colors.secondaryText,
        )
      }
      Text(
          text = "Quicktools",
          modifier = Modifier.testTag(TestTags.QUICKTOOLS),
          style = MaterialTheme.typography.headlineSmall,
          color = colors.primaryText,
      )
      Row(
          modifier = Modifier.fillMaxWidth(2f / 3f).padding(top = 12.dp),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Button(
            onClick = onDiceClick,
            modifier = Modifier.weight(1f).aspectRatio(1f).testTag(TestTags.DICEBUTTON),
            shape = RoundedCornerShape(8.dp),
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.onPrimary,
                    contentColor = MaterialTheme.colorScheme.primary,
                ),
        ) {
          Icon(
              imageVector = Icons.Filled.Casino,
              contentDescription = null,
              modifier = Modifier.size(32.dp).testTag(TestTags.DICE_ICON),
          )
        }
        Button(
            onClick = onGroupManagementClick,
            modifier = Modifier.weight(1f).aspectRatio(1f).testTag(TestTags.GROUPMANAGEMENTBUTTON),
            shape = RoundedCornerShape(8.dp),
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.onPrimary,
                    contentColor = MaterialTheme.colorScheme.primary,
                ),
        ) {
          Icon(
              imageVector = Icons.Filled.Mic,
              contentDescription = null,
              modifier = Modifier.size(32.dp).testTag(TestTags.GROUPMANAGEMENT_ICON),
          )
        }
        Button(
            onClick = onPersonalSpaceClick,
            modifier = Modifier.weight(1f).aspectRatio(1f).testTag(TestTags.PSPACEBUTTON),
            shape = RoundedCornerShape(8.dp),
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.onPrimary,
                    contentColor = MaterialTheme.colorScheme.primary,
                ),
        ) {
          Icon(
              imageVector = Icons.Filled.Edit,
              contentDescription = null,
              modifier = Modifier.size(32.dp).testTag(TestTags.PSPACE_ICON),
          )
        }
      }
    }
  }
}
