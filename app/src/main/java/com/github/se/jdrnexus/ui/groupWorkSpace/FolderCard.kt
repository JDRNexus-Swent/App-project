// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.ui.groupWorkSpace

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.se.jdrnexus.R
import com.github.se.jdrnexus.model.personalSpace.JDRFile
import com.github.se.jdrnexus.resources.C

/**
 * The folder card shared by the Group Workspace and the Personal Space, so folders look and behave
 * the same in both. Each screen decides which actions the user may take: an action is shown only
 * when its callback is given, so an unavailable action is neither displayed nor reachable.
 *
 * @param folder The folder to show; its name is the title, its content (if any) the description.
 * @param onOpen Called when the card is clicked, to open the folder.
 * @param onEdit Renames the folder, or null when the user may not rename it.
 * @param onRemove Removes the folder, or null when the user may not remove it.
 * @param onToggleVisibility Hides or shows the folder, or null when the user may not change it.
 * @param isHidden Whether the folder is hidden, which picks the visibility icon.
 * @param actionsEnabled Whether the actions can be clicked; false while an operation runs.
 */
@Composable
fun FolderCard(
    folder: JDRFile,
    onOpen: () -> Unit,
    onEdit: (() -> Unit)? = null,
    onRemove: (() -> Unit)? = null,
    onToggleVisibility: (() -> Unit)? = null,
    isHidden: Boolean = false,
    actionsEnabled: Boolean = true,
) {
  WorkspaceItemCard(
      file = folder,
      icon = Icons.Filled.Folder,
      typeLabel = stringResource(R.string.workspace_folder_type),
      title = folder.name,
      description = folder.content,
      emptyDescription = stringResource(R.string.workspace_open_folder_description),
      showOpenArrow = true,
      onOpen = onOpen,
      onEdit = onEdit,
      onRemove = onRemove,
      onToggleVisibility = onToggleVisibility,
      isHidden = isHidden,
      actionsEnabled = actionsEnabled,
  )
}

/**
 * The layout of every Group Workspace card, folders ([FolderCard]) and files alike: an icon in a
 * lighter square, the type label, title and description, the allowed actions and, for a folder, the
 * open arrow. An action is shown only when its callback is not null.
 */
@Composable
internal fun WorkspaceItemCard(
    file: JDRFile,
    icon: ImageVector,
    typeLabel: String,
    title: String,
    description: String,
    emptyDescription: String,
    showOpenArrow: Boolean,
    onOpen: () -> Unit,
    onEdit: (() -> Unit)?,
    onRemove: (() -> Unit)?,
    onToggleVisibility: (() -> Unit)?,
    isHidden: Boolean,
    actionsEnabled: Boolean,
) {
  val colors = MaterialTheme.colorScheme
  Card(
      modifier =
          Modifier.fillMaxWidth().clickable(onClick = onOpen).testTag(C.Tag.workspaceItem(file.id)),
      shape = RoundedCornerShape(17.dp),
      border = BorderStroke(1.dp, colors.outline.copy(alpha = 0.65f)),
      colors = CardDefaults.cardColors(containerColor = colors.surface),
  ) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
      // Icon inside a lighter square
      Surface(
          modifier = Modifier.size(62.dp),
          shape = RoundedCornerShape(13.dp),
          color = colors.background,
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
              imageVector = icon,
              contentDescription = null,
              tint = colors.primary,
              modifier = Modifier.size(30.dp),
          )
        }
      }

      Column(
          modifier = Modifier.weight(1f),
          verticalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        Text(
            text = typeLabel,
            style = MaterialTheme.typography.labelSmall,
            color = colors.tertiary,
        )
        Text(
            text = title,
            modifier = Modifier.testTag(C.Tag.workspaceItemTitle(file.id)),
            style = MaterialTheme.typography.titleMedium,
            color = colors.onSecondary,
        )
        Text(
            text = description.replace("\n", " ").ifBlank { emptyDescription },
            modifier = Modifier.testTag(C.Tag.workspaceItemDescription(file.id)),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            color = colors.tertiary,
        )

        if (onEdit != null || onRemove != null || onToggleVisibility != null) {
          Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            onEdit?.let {
              CardAction(
                  icon = Icons.Default.Edit,
                  description = stringResource(R.string.workspace_edit_description, file.name),
                  tag = C.Tag.workspaceEdit(file.id),
                  enabled = actionsEnabled,
                  onClick = it,
              )
            }
            onRemove?.let {
              CardAction(
                  icon = Icons.Default.Delete,
                  description = stringResource(R.string.workspace_remove_description, file.name),
                  tag = C.Tag.workspaceRemove(file.id),
                  enabled = actionsEnabled,
                  onClick = it,
              )
            }
            onToggleVisibility?.let {
              CardAction(
                  icon = if (isHidden) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                  description =
                      if (isHidden) {
                        stringResource(R.string.workspace_show_description, file.name)
                      } else {
                        stringResource(R.string.workspace_hide_description, file.name)
                      },
                  tag = C.Tag.workspaceVisibility(file.id),
                  enabled = actionsEnabled,
                  onClick = it,
              )
            }
          }
        }
      }

      if (showOpenArrow) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = stringResource(R.string.workspace_open_description, file.name),
            tint = colors.primary,
        )
      }
    }
  }
}

/** One of the card's action buttons. */
@Composable
private fun CardAction(
    icon: ImageVector,
    description: String,
    tag: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
  IconButton(
      onClick = onClick,
      enabled = enabled,
      modifier = Modifier.size(36.dp).testTag(tag),
  ) {
    Icon(icon, contentDescription = description, tint = MaterialTheme.colorScheme.onSecondary)
  }
}
