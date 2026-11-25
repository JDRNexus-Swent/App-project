// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.ui.gameManagement

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.se.jdrnexus.R
import com.github.se.jdrnexus.resources.AppTopBar
import com.github.se.jdrnexus.resources.C
import com.github.se.jdrnexus.resources.ScreenBottomBar

/**
 * Shows the members of a campaign group and, for its Game Master, the buttons that remove a player
 * or delete the group, each behind a confirmation dialog.
 *
 * @param viewModel Provides the members and runs the removal and the deletion.
 * @param onBack Called by the top bar's back button.
 * @param onGroupDeleted Called once the group no longer exists, to leave the screen.
 * @param onTabSelected Called with the label of the bottom bar tab the user selects.
 */
@Composable
fun GroupManagementScreen(
    viewModel: GroupManagementInterfaceVM,
    onBack: () -> Unit,
    onGroupDeleted: () -> Unit,
    onTabSelected: (String) -> Unit,
) {
  val state by viewModel.uiState.collectAsState()
  // Saveable, so an open dialog survives a rotation.
  var playerToRemove by rememberSaveable { mutableStateOf<String?>(null) }
  var confirmDelete by rememberSaveable { mutableStateOf(false) }
  LaunchedEffect(state.isGroupDeleted) { if (state.isGroupDeleted) onGroupDeleted() }

  Scaffold(
      topBar = { AppTopBar(stringResource(R.string.group_management_title), onBack = onBack) },
      bottomBar = { ScreenBottomBar(selectedTab = "Campaigns", onTabSelected = onTabSelected) },
  ) { padding ->
    // The header stays in place; only the member list scrolls.
    Column(
        modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
          Text(state.groupName, style = MaterialTheme.typography.titleLarge)
          if (!state.isLoading) {
            val count = state.members.size
            Text(pluralStringResource(R.plurals.group_management_member_count, count, count))
          }
        }
        if (state.canManage) {
          OutlinedButton(
              onClick = { confirmDelete = true },
              enabled = !state.isWorking,
              colors =
                  ButtonDefaults.outlinedButtonColors(
                      contentColor = MaterialTheme.colorScheme.error
                  ),
              modifier = Modifier.testTag(C.Tag.group_management_delete),
          ) {
            Text(stringResource(R.string.group_management_delete))
          }
        }
      }
      state.error?.let { error ->
        Text(
            stringResource(error.messageId()),
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.testTag(C.Tag.group_management_error),
        )
      }
      if (state.isLoading) {
        CircularProgressIndicator(Modifier.testTag(C.Tag.group_management_loading))
      }
      LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(state.members, key = { it.uid }) { member ->
          MemberCard(
              member = member,
              canRemove = state.canManage && !member.isGameMaster,
              enabled = !state.isWorking,
              onRemove = { playerToRemove = member.uid },
          )
        }
      }
    }
  }

  playerToRemove?.let { uid ->
    val label = state.members.firstOrNull { it.uid == uid }?.label ?: uid
    ConfirmDialog(
        title = stringResource(R.string.group_management_remove_title),
        message = stringResource(R.string.group_management_remove_message, label),
        confirmLabel = stringResource(R.string.group_management_remove),
        onConfirm = { viewModel.removeMember(uid) },
        onDismiss = { playerToRemove = null },
    )
  }
  if (confirmDelete) {
    ConfirmDialog(
        title = stringResource(R.string.group_management_delete_title, state.groupName),
        message = stringResource(R.string.group_management_delete_message),
        confirmLabel = stringResource(R.string.group_management_delete),
        onConfirm = viewModel::deleteGroup,
        onDismiss = { confirmDelete = false },
    )
  }
}

/**
 * One member of the group, drawn like the group cards of the Campaigns screen: rounded card with an
 * outline, and a role badge. [onRemove] is offered only when [canRemove].
 */
@Composable
private fun MemberCard(
    member: GroupMember,
    canRemove: Boolean,
    enabled: Boolean,
    onRemove: () -> Unit,
) {
  val colors = gameManagementColors()
  val shape = RoundedCornerShape(18.dp)
  Row(
      modifier =
          Modifier.fillMaxWidth()
              .clip(shape)
              .background(colors.card)
              .border(1.dp, colors.outline, shape)
              .padding(horizontal = 12.dp, vertical = 10.dp)
              .testTag(C.Tag.groupManagementMember(member.uid)),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(Modifier.weight(1f)) {
      Text(member.label, color = colors.text, style = MaterialTheme.typography.titleMedium)
      RoleBadge(member.isGameMaster, colors, Modifier.padding(top = 4.dp))
    }
    if (canRemove) {
      TextButton(
          onClick = onRemove,
          enabled = enabled,
          modifier = Modifier.testTag(C.Tag.groupManagementRemove(member.uid)),
      ) {
        Text(stringResource(R.string.group_management_remove))
      }
    }
  }
}

/** A dialog asking to confirm a destructive action; both buttons close it. */
@Composable
private fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
  AlertDialog(
      onDismissRequest = onDismiss,
      title = { Text(title) },
      text = { Text(message) },
      confirmButton = {
        TextButton(
            onClick = {
              onDismiss()
              onConfirm()
            },
            modifier = Modifier.testTag(C.Tag.group_management_confirm),
        ) {
          Text(confirmLabel, color = MaterialTheme.colorScheme.error)
        }
      },
      dismissButton = {
        TextButton(
            onClick = onDismiss,
            modifier = Modifier.testTag(C.Tag.group_management_cancel),
        ) {
          Text(stringResource(R.string.group_management_cancel))
        }
      },
  )
}

private fun GroupManagementError.messageId(): Int =
    when (this) {
      GroupManagementError.LOAD_FAILED -> R.string.group_management_load_failed
      GroupManagementError.REMOVE_FAILED -> R.string.group_management_remove_failed
      GroupManagementError.DELETE_FAILED -> R.string.group_management_delete_failed
    }
