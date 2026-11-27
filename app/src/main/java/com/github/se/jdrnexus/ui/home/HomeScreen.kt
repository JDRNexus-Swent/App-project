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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.se.jdrnexus.R
import com.github.se.jdrnexus.resources.AppTopBar
import com.github.se.jdrnexus.resources.C
import com.github.se.jdrnexus.resources.ScreenBottomBar
import com.github.se.jdrnexus.ui.authentication.AuthStatus
import com.github.se.jdrnexus.ui.authentication.AuthViewModel
import com.github.se.jdrnexus.ui.notification.NotificationBell
import com.github.se.jdrnexus.ui.profile.ProfileButton

object TestTags {
  const val TITLE = "home_title"
  const val QUICKTOOLS = "home_quicktools"
  const val PSPACEBUTTON = "home_to_pspace"
  const val DICEBUTTON = "home_to_dice"
  const val DICE_ICON = "home_dice_icon"
  const val SOUNDBOARDBUTTON = "home_to_soundboard"
  const val SOUNDBOARD_ICON = "home_soundboard_icon"
  const val PSPACE_ICON = "home_personal_space_icon"
  const val LOGOUT = "home_logout"
  val BUTTONS = listOf(DICEBUTTON, SOUNDBOARDBUTTON)
}

@Composable
fun HomeScreen(
    viewModel: AuthViewModel = viewModel(factory = AuthViewModel.Factory),
    modifier: Modifier = Modifier,
    onLogout: () -> Unit = {},
    onPersonalSpaceClick: () -> Unit = {},
    onDiceClick: () -> Unit = {},
    onSoundboardClick: () -> Unit = {},
    onGroupManagementClick: () -> Unit = {},
    onHomeClick: () -> Unit = {},
    onMapClick: () -> Unit = {},
    unreadNotificationCount: Int = 0,
    onNotificationsClick: (() -> Unit)? = null,
    // Opens the profile screen; the top bar shows the profile button only when it is given.
    onProfileClick: (() -> Unit)? = null,
) {

  val uiState by viewModel.uiState.collectAsState()
  val logoutRequested = remember { mutableStateOf(false) }
  val canNavigate = { !logoutRequested.value && uiState.status !is AuthStatus.Loading }
  val context = LocalContext.current
  val colors = MaterialTheme.colorScheme

  // Keyed on the flag, not the status: a sign-out that ends at once never shows Loading.
  LaunchedEffect(uiState.isSignedOut) {
    if (uiState.isSignedOut) {
      logoutRequested.value = false
      viewModel.onSignedOutHandled()
      onLogout()
    }
  }
  LaunchedEffect(uiState.status) {
    if (uiState.status is AuthStatus.Error) logoutRequested.value = false
  }

  Scaffold(
      modifier = modifier.fillMaxSize(),
      containerColor = colors.background,
      topBar = {
        AppTopBar(
            title = "JDRNexus",
            titleModifier = Modifier.testTag(TestTags.TITLE),
            leadingContent = {
              if (onProfileClick != null) {
                ProfileButton(
                    onClick = { if (canNavigate()) onProfileClick() },
                    enabled = canNavigate(),
                )
              }
            },
            trailingContent = {
              // Shown only when the caller can open the notifications, so the bell is never dead.
              if (onNotificationsClick != null) {
                NotificationBell(
                    unreadCount = unreadNotificationCount,
                    onClick = { if (canNavigate()) onNotificationsClick() },
                    enabled = canNavigate(),
                )
              }
              TextButton(
                  onClick = {
                    if (!logoutRequested.value) {
                      logoutRequested.value = true
                      viewModel.requestSignOut()
                    }
                  },
                  enabled = !logoutRequested.value && uiState.status !is AuthStatus.Loading,
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
            enabled = canNavigate(),
            onTabSelected = { tab ->
              when (tab) {
                "Home" -> onHomeClick()
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
            color = colors.onSurfaceVariant,
        )
      }
      Text(
          text = "Quicktools",
          modifier = Modifier.testTag(TestTags.QUICKTOOLS),
          style = MaterialTheme.typography.headlineSmall,
          color = colors.onSurface,
      )
      Row(
          modifier = Modifier.fillMaxWidth(2f / 3f).padding(top = 12.dp),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Button(
            onClick = { if (canNavigate()) onDiceClick() },
            enabled = canNavigate(),
            modifier = Modifier.weight(1f).aspectRatio(1f).testTag(TestTags.DICEBUTTON),
            shape = RoundedCornerShape(8.dp),
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surface,
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
            onClick = { if (canNavigate()) onSoundboardClick() },
            enabled = canNavigate(),
            modifier = Modifier.weight(1f).aspectRatio(1f).testTag(TestTags.SOUNDBOARDBUTTON),
            shape = RoundedCornerShape(8.dp),
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary,
                ),
        ) {
          Icon(
              imageVector = Icons.Filled.Mic,
              contentDescription = null,
              modifier = Modifier.size(32.dp).testTag(TestTags.SOUNDBOARD_ICON),
          )
        }
        Button(
            onClick = { if (canNavigate()) onPersonalSpaceClick() },
            enabled = canNavigate(),
            modifier = Modifier.weight(1f).aspectRatio(1f).testTag(TestTags.PSPACEBUTTON),
            shape = RoundedCornerShape(8.dp),
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surface,
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

  if (uiState.isSignOutWarningShown) {
    UnsyncedChangesSignOutDialog(
        onCancel = {
          logoutRequested.value = false
          viewModel.dismissSignOutWarning()
        },
        onConfirm = {
          // Set again: the dialog lives in the ViewModel, so it survives a rotation that resets it.
          logoutRequested.value = true
          viewModel.confirmSignOut()
        },
    )
  }
}

/**
 * Warns that changes made offline have not reached the server yet and would be lost by signing out.
 * [onConfirm] signs out anyway; [onCancel], or dismissing the dialog, stays signed in.
 */
@Composable
internal fun UnsyncedChangesSignOutDialog(onCancel: () -> Unit, onConfirm: () -> Unit) {
  AlertDialog(
      onDismissRequest = onCancel,
      modifier = Modifier.testTag(C.Tag.sign_out_unsynced_dialog),
      title = { Text(stringResource(R.string.sign_out_unsynced_title)) },
      text = { Text(stringResource(R.string.sign_out_unsynced_message)) },
      confirmButton = {
        TextButton(
            onClick = onConfirm,
            modifier = Modifier.testTag(C.Tag.sign_out_unsynced_confirm),
            colors =
                ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
        ) {
          Text(stringResource(R.string.sign_out_unsynced_confirm))
        }
      },
      dismissButton = {
        TextButton(
            onClick = onCancel,
            modifier = Modifier.testTag(C.Tag.sign_out_unsynced_cancel),
        ) {
          Text(stringResource(R.string.sign_out_unsynced_cancel))
        }
      },
  )
}
