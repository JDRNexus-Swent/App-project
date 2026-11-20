// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.ui.groupWorkSpace

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.se.jdrnexus.R
import com.github.se.jdrnexus.model.personalSpace.DocumentType
import com.github.se.jdrnexus.model.personalSpace.JDRFile
import com.github.se.jdrnexus.resources.C
import com.github.se.jdrnexus.resources.JdrNexusLogo
import com.github.se.jdrnexus.resources.ScreenBottomBar
import com.github.se.jdrnexus.resources.darkFieldColors
import com.github.se.jdrnexus.viewmodel.LoadState
import kotlin.collections.emptyList
import kotlinx.coroutines.launch

/**
 * Displays a group's workspace and its files and folders.
 *
 * @param gwViewModel ViewModel that provides and updates the workspace state.
 * @param groupId Group identifier; must match the group ID used by the ViewModel.
 * @param groupName Name displayed for the group.
 * @param onBack Called when the user navigates back from the workspace root.
 * @param onTabSelected Called when a shared bottom-navigation tab is selected.
 * @param onItemClicked Called when the user opens an item; the Boolean indicates whether the user
 *   can edit the clicked item.
 * @param personalItems Personal files available to add to the group.
 * @param onCharacterCreated Called with the file ID of a character created in this workspace.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupWorkSpaceScreen(
    gwViewModel: GroupWorkspaceViewModel,
    groupId: String,
    groupName: String,
    onBack: () -> Unit = {},
    onTabSelected: (String) -> Unit = {},
    onItemClicked: ((JDRFile, Boolean) -> Unit)? = null,
    personalItems: List<JDRFile> = emptyList(),
    onSearchClicked: () -> Unit = {},
    onCharacterCreated: (String) -> Unit = {},
) {
  val uiState by gwViewModel.uiState.collectAsState()
  val scope = rememberCoroutineScope()
  val snackbarHostState = remember { SnackbarHostState() }

  var showCreateItemDialog by rememberSaveable { mutableStateOf(false) }
  var showCreateFolderDialog by rememberSaveable { mutableStateOf(false) }
  var showAddFilesDialog by rememberSaveable { mutableStateOf(false) }
  var itemToEditId by rememberSaveable { mutableStateOf<String?>(null) }
  var itemToRemoveId by rememberSaveable { mutableStateOf<String?>(null) }
  var itemToViewId by rememberSaveable { mutableStateOf<String?>(null) }

  var folderName by rememberSaveable { mutableStateOf("") }
  var editedName by rememberSaveable { mutableStateOf("") }
  val isDialogOpen =
      showCreateItemDialog ||
          showCreateFolderDialog ||
          showAddFilesDialog ||
          itemToEditId != null ||
          itemToRemoveId != null ||
          itemToViewId != null

  LaunchedEffect(uiState.errorMessage, isDialogOpen) {
    val message = uiState.errorMessage
    if (message.isNotBlank() && !isDialogOpen) {
      snackbarHostState.showSnackbar(message)
      if (gwViewModel.uiState.value.errorMessage == message) {
        gwViewModel.clearError()
      }
    }
  }

  // Close a dialog after a successful operation.
  // An error leaves the dialog open so the user can correct or retry.

  fun navigateBack() {
    if (!gwViewModel.navigateBack()) {
      onBack()
    }
  }

  BackHandler(enabled = uiState.folderHistory.isNotEmpty()) { navigateBack() }

  Box(Modifier.fillMaxSize().background(color().background)) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
      JdrNexusLogo(Modifier.size(320.dp))
    }

    Scaffold(
        containerColor = Color.Transparent,
        floatingActionButton = {
          if (uiState.loadState == LoadState.Success) {
            WorkspaceFab(
                onCreateItem = {
                  gwViewModel.clearError()
                  showCreateItemDialog = true
                },
                onAddFile = {
                  gwViewModel.clearError()
                  showAddFilesDialog = true
                },
            )
          }
        },
        bottomBar = {
          ScreenBottomBar(
              selectedTab = "Campaigns",
              onTabSelected = onTabSelected,
          )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
      Column(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 14.dp)) {
        WorkspaceHeader(
            groupName =
                if (uiState.currentFolderId.isEmpty()) groupName else uiState.currentFolderName,
            itemCount = uiState.items.size,
            onBack = { navigateBack() },
            onSearchClicked = onSearchClicked,
            roleLabel =
                if (uiState.loadState == LoadState.Success) {
                  if (uiState.isGameMaster) {
                    stringResource(R.string.workspace_role_game_master)
                  } else {
                    stringResource(R.string.workspace_role_player)
                  }
                } else {
                  null
                },
        )

        Spacer(Modifier.height(16.dp))

        WorkspaceContent(
            uiState = uiState,
            groupId = groupId,
            onItemClick = { file ->
              if (file.type == DocumentType.FOLDER) {
                gwViewModel.openFolder(file.id)
              } else {
                val canEdit = file.ownerId == uiState.currentUserId

                if (onItemClicked != null) {
                  onItemClicked(file, canEdit)
                } else {
                  itemToViewId = file.id
                }
              }
            },
            onEdit = {
              gwViewModel.clearError()
              itemToEditId = it.id
              editedName = it.name
            },
            onRemove = {
              gwViewModel.clearError()
              itemToRemoveId = it.id
            },
            currentUserId = uiState.currentUserId,
            viewModel = gwViewModel,
        )
      }
    }
  }

  // Choose the type of item to create
  if (showCreateItemDialog) {
    CreateItemDialog(
        isOperationInProgress = uiState.isOperationInProgress,
        onFolderSelected = {
          showCreateItemDialog = false
          folderName = ""
          showCreateFolderDialog = true
        },
        onCharacterSelected = {
          showCreateItemDialog = false
          gwViewModel.createCharacter(onCharacterCreated)
        },
        onDismiss = { showCreateItemDialog = false },
    )
  }

  // Create folder
  if (showCreateFolderDialog) {
    AlertDialog(
        modifier = Modifier.testTag(C.Tag.workspace_create_folder_dialog),
        containerColor = color().surface,
        onDismissRequest = {
          if (!uiState.isOperationInProgress) {
            showCreateFolderDialog = false
            gwViewModel.clearError()
          }
        },
        title = { Text(stringResource(R.string.workspace_new_folder), color = color().tertiary) },
        text = {
          OutlinedTextField(
              modifier = Modifier.testTag(C.Tag.workspace_create_folder_name_input),
              value = folderName,
              onValueChange = { folderName = it },
              label = { Text(stringResource(R.string.workspace_folder_name)) },
              singleLine = true,
              enabled = !uiState.isOperationInProgress,
              colors = workspaceFieldColors(),
              isError = uiState.errorMessage.isNotBlank(),
              supportingText = {
                if (uiState.errorMessage.isNotBlank()) {
                  Text(
                      text = uiState.errorMessage,
                  )
                }
              },
          )
        },
        confirmButton = {
          TextButton(
              modifier = Modifier.testTag(C.Tag.workspace_create_folder_confirm),
              enabled = !uiState.isOperationInProgress,
              onClick = {
                gwViewModel.clearError()

                scope.launch {
                  val job = gwViewModel.createFolder(folderName.trim())
                  job?.join()

                  if (gwViewModel.uiState.value.errorMessage.isEmpty()) {
                    showCreateFolderDialog = false
                    folderName = ""
                  }
                }
              },
          ) {
            OperationButtonContent(
                isInProgress = uiState.isOperationInProgress,
                progressTag = C.Tag.workspace_create_folder_progress,
                label = stringResource(R.string.workspace_create),
            )
          }
        },
        dismissButton = {
          TextButton(
              modifier = Modifier.testTag(C.Tag.workspace_create_folder_cancel),
              enabled = !uiState.isOperationInProgress,
              onClick = {
                showCreateFolderDialog = false
                gwViewModel.clearError()
              },
          ) {
            Text(stringResource(R.string.workspace_cancel), color = color().primary)
          }
        },
    )
  }

  // Add existing personnel files
  if (showAddFilesDialog) {

    val availableItems =
        availableItemsToShare(
            personalItems = personalItems,
            groupId = groupId,
            ownerId = uiState.currentUserId,
        )

    AlertDialog(
        modifier = Modifier.testTag(C.Tag.workspace_add_files_dialog),
        onDismissRequest = {
          if (!uiState.isOperationInProgress) {
            showAddFilesDialog = false
            gwViewModel.clearError()
          }
        },
        title = {
          Text(stringResource(R.string.workspace_share_item_title), color = color().tertiary)
        },
        text = {
          Column {
            // Display any error from the ViewModel.
            if (uiState.errorMessage.isNotBlank()) {
              Text(
                  text = uiState.errorMessage,
                  color = MaterialTheme.colorScheme.error,
                  modifier = Modifier.padding(bottom = 8.dp),
              )
            }

            if (availableItems.isEmpty()) {
              Text(stringResource(R.string.workspace_no_personal_items))
            } else {
              LazyColumn(
                  modifier =
                      Modifier.size(
                              width = 300.dp,
                              height = 280.dp,
                          )
                          .testTag(C.Tag.workspace_add_files_list)
              ) {
                items(availableItems, key = { it.id }) { item ->
                  Row(
                      modifier =
                          Modifier.fillMaxWidth()
                              .clickable(enabled = !uiState.isOperationInProgress) {
                                gwViewModel.clearError()

                                scope.launch {
                                  val job = gwViewModel.shareItem(item.id)
                                  job?.join()

                                  if (gwViewModel.uiState.value.errorMessage.isEmpty()) {
                                    showAddFilesDialog = false
                                  }
                                }
                              }
                              .padding(vertical = 12.dp),
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.spacedBy(12.dp),
                  ) {
                    Icon(
                        imageVector =
                            when (item.type) {
                              DocumentType.FOLDER -> Icons.Default.Folder
                              DocumentType.TEXT -> Icons.Default.Description
                              DocumentType.CHARACTER -> Icons.Default.People
                              DocumentType.GROUP_ROOT -> Icons.Default.Folder
                            },
                        contentDescription = null,
                        tint = color().primary,
                    )
                    Text(item.name)
                  }
                }
              }
            }
          }
        },
        containerColor = color().surface,
        confirmButton = {
          TextButton(
              modifier = Modifier.testTag(C.Tag.workspace_add_files_done),
              enabled = !uiState.isOperationInProgress,
              onClick = {
                showAddFilesDialog = false
                gwViewModel.clearError()
              },
          ) {
            Text(stringResource(R.string.workspace_done), color = color().primary)
          }
        },
    )
  }

  // Edit file or folder
  itemToEditId?.let { itemId ->
    uiState.items
        .firstOrNull { it.id == itemId }
        ?.let { file ->
          AlertDialog(
              modifier = Modifier.testTag(C.Tag.workspace_edit_item_dialog),
              containerColor = color().surface,
              onDismissRequest = {
                if (!uiState.isOperationInProgress) {
                  itemToEditId = null
                  gwViewModel.clearError()
                }
              },
              title = {
                Text(
                    if (file.type == DocumentType.FOLDER) {
                      stringResource(R.string.workspace_edit_folder)
                    } else {
                      stringResource(R.string.workspace_edit_file)
                    },
                    color = color().tertiary,
                )
              },
              text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                  OutlinedTextField(
                      modifier = Modifier.testTag(C.Tag.workspace_edit_item_name_input),
                      value = editedName,
                      onValueChange = { editedName = it },
                      label = {
                        Text(stringResource(R.string.workspace_name), color = color().primary)
                      },
                      singleLine = true,
                      enabled = !uiState.isOperationInProgress,
                      colors = workspaceFieldColors(),
                      isError = uiState.errorMessage.isNotBlank(),
                      supportingText = {
                        if (uiState.errorMessage.isNotBlank()) {
                          Text(text = uiState.errorMessage)
                        }
                      },
                  )
                }
              },
              confirmButton = {
                TextButton(
                    modifier = Modifier.testTag(C.Tag.workspace_edit_item_save),
                    enabled = !uiState.isOperationInProgress,
                    onClick = {
                      gwViewModel.clearError()

                      scope.launch {
                        val job =
                            gwViewModel.editItem(
                                file.copy(
                                    name = editedName.trim(),
                                )
                            )

                        job?.join()

                        if (gwViewModel.uiState.value.errorMessage.isEmpty()) {
                          itemToEditId = null
                        }
                      }
                    },
                ) {
                  OperationButtonContent(
                      isInProgress = uiState.isOperationInProgress,
                      progressTag = C.Tag.workspace_edit_item_progress,
                      label = stringResource(R.string.workspace_save),
                  )
                }
              },
              dismissButton = {
                TextButton(
                    modifier = Modifier.testTag(C.Tag.workspace_edit_item_cancel),
                    enabled = !uiState.isOperationInProgress,
                    onClick = {
                      itemToEditId = null
                      gwViewModel.clearError()
                    },
                ) {
                  Text(stringResource(R.string.workspace_cancel), color = color().primary)
                }
              },
          )
        }
  }

  // Remove item from this group's workspace
  itemToRemoveId?.let { itemId ->
    uiState.items
        .firstOrNull { it.id == itemId }
        ?.let { file ->
          AlertDialog(
              modifier = Modifier.testTag(C.Tag.workspace_remove_item_dialog),
              containerColor = color().surface,
              onDismissRequest = {
                if (!uiState.isOperationInProgress) {
                  itemToRemoveId = null
                  gwViewModel.clearError()
                }
              },
              title = {
                Text(stringResource(R.string.workspace_remove_title), color = color().tertiary)
              },
              text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                  if (uiState.errorMessage.isNotBlank()) {
                    Text(
                        text = uiState.errorMessage,
                        color = MaterialTheme.colorScheme.error,
                    )
                  }
                  Text(
                      stringResource(R.string.workspace_remove_message, file.name),
                      color = color().onSecondary,
                  )
                }
              },
              confirmButton = {
                TextButton(
                    modifier = Modifier.testTag(C.Tag.workspace_remove_item_confirm),
                    enabled = !uiState.isOperationInProgress,
                    onClick = {
                      gwViewModel.clearError()

                      scope.launch {
                        val job = gwViewModel.removeItem(file)
                        job.join()

                        if (gwViewModel.uiState.value.errorMessage.isEmpty()) {
                          itemToRemoveId = null
                        }
                      }
                    },
                ) {
                  OperationButtonContent(
                      isInProgress = uiState.isOperationInProgress,
                      progressTag = C.Tag.workspace_remove_item_progress,
                      label = stringResource(R.string.workspace_remove),
                  )
                }
              },
              dismissButton = {
                TextButton(
                    modifier = Modifier.testTag(C.Tag.workspace_remove_item_cancel),
                    enabled = !uiState.isOperationInProgress,
                    onClick = {
                      itemToRemoveId = null
                      gwViewModel.clearError()
                    },
                ) {
                  Text(stringResource(R.string.workspace_cancel), color = color().primary)
                }
              },
          )
        }
  }

  itemToViewId?.let { itemId ->
    uiState.items
        .firstOrNull { it.id == itemId }
        ?.let {
          AlertDialog(
              onDismissRequest = { itemToViewId = null },
              title = {
                Text(
                    stringResource(R.string.workspace_file_viewing_unavailable),
                    color = color().tertiary,
                )
              },
              text = {
                Text(
                    stringResource(R.string.workspace_file_viewing_unavailable_message),
                    color = color().onSecondary,
                )
              },
              confirmButton = {
                TextButton(onClick = { itemToViewId = null }) {
                  Text(stringResource(R.string.workspace_ok), color = color().primary)
                }
              },
          )
        }
  }
}

/**
 * Displays a file or folder in the workspace.
 *
 * @param file File or folder to display.
 * @param canEdit Whether the item can be edited.
 * @param canRemove Whether the item can be removed.
 * @param isHidden Whether the item is hidden from the group.
 * @param canChangeVisibility Whether the user can change the item's visibility.
 * @param onOpen Called when the item is opened.
 * @param onEdit Called when the item is edited.
 * @param onRemove Called when the item is removed.
 * @param onToggleVisibility Called when the item's visibility is toggled.
 */
@Composable
fun WorkspaceCard(
    file: JDRFile,
    canEdit: Boolean,
    canRemove: Boolean,
    isHidden: Boolean,
    canChangeVisibility: Boolean,
    operationInProgress: Boolean,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
    onToggleVisibility: () -> Unit,
) {

  val isFolder = file.type == DocumentType.FOLDER

  Card(
      modifier =
          Modifier.fillMaxWidth().clickable(onClick = onOpen).testTag(C.Tag.workspaceItem(file.id)),
      shape = RoundedCornerShape(17.dp),
      border =
          BorderStroke(
              1.dp,
              color().outline.copy(alpha = 0.65f),
          ),
      colors = CardDefaults.cardColors(containerColor = color().surface),
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
          color = color().background,
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
              imageVector =
                  when (file.type) {
                    DocumentType.FOLDER -> Icons.Filled.Folder
                    DocumentType.TEXT -> Icons.Default.Description
                    else -> Icons.Default.People
                  },
              contentDescription = null,
              tint = color().primary,
              modifier = Modifier.size(30.dp),
          )
        }
      }

      Column(
          modifier = Modifier.weight(1f),
          verticalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        Text(
            text =
                if (isFolder) {
                  stringResource(R.string.workspace_folder_type)
                } else {
                  stringResource(file.type.labelResource)
                },
            style = MaterialTheme.typography.labelSmall,
            color = color().tertiary,
        )

        Text(
            text = file.name,
            style = MaterialTheme.typography.titleMedium,
            color = color().onSecondary,
        )

        // A character's content is its saved JSON: never show it.
        val description = if (file.type == DocumentType.CHARACTER) "" else file.content
        Text(
            text =
                description.replace("\n", " ").ifBlank {
                  if (isFolder) {
                    stringResource(R.string.workspace_open_folder_description)
                  } else {
                    stringResource(R.string.workspace_no_description)
                  }
                },
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            color = color().tertiary,
        )

        if (canEdit || canRemove) {
          Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (canEdit) {
              IconButton(
                  onClick = onEdit,
                  enabled = !operationInProgress,
                  modifier = Modifier.size(36.dp).testTag(C.Tag.workspaceEdit(file.id)),
              ) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription =
                        stringResource(R.string.workspace_edit_description, file.name),
                    tint = color().onSecondary,
                )
              }
            }

            if (canRemove) {
              IconButton(
                  onClick = onRemove,
                  enabled = !operationInProgress,
                  modifier = Modifier.size(36.dp).testTag(C.Tag.workspaceRemove(file.id)),
              ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription =
                        stringResource(R.string.workspace_remove_description, file.name),
                    tint = color().onSecondary,
                )
              }
            }
            if (canChangeVisibility) {
              IconButton(
                  onClick = onToggleVisibility,
                  enabled = !operationInProgress,
                  modifier = Modifier.size(36.dp).testTag(C.Tag.workspaceVisibility(file.id)),
              ) {
                Icon(
                    imageVector =
                        if (!isHidden) {
                          Icons.Default.Visibility
                        } else {
                          Icons.Default.VisibilityOff
                        },
                    contentDescription =
                        if (isHidden) {
                          stringResource(R.string.workspace_show_description, file.name)
                        } else {
                          stringResource(R.string.workspace_hide_description, file.name)
                        },
                    tint = color().onSecondary,
                )
              }
            }
          }
        }
      }

      if (isFolder) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = stringResource(R.string.workspace_open_description, file.name),
            tint = color().primary,
        )
      }
    }
  }
}

/**
 * Displays the workspace breadcrumb header and back navigation.
 *
 * @param groupName Current group or folder name.
 * @param itemCount Number of items in the current folder.
 * @param onBack Called when the back button is pressed.
 * @param roleLabel Role label associated with the current user.
 * @param onSearchClicked Called when the search action is pressed.
 */
@Composable
fun WorkspaceHeader(
    groupName: String,
    itemCount: Int,
    onBack: () -> Unit,
    roleLabel: String?,
    onSearchClicked: () -> Unit = {},
) {

  Column(modifier = Modifier.fillMaxWidth()) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
      IconButton(onClick = onBack) {
        Icon(
            Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = stringResource(R.string.workspace_back_description),
            tint = color().primary,
        )
      }

      Text(
          stringResource(R.string.workspace_groups),
          color = color().tertiary,
          style = MaterialTheme.typography.bodyMedium,
      )

      Text(
          stringResource(
              R.string.workspace_breadcrumb,
              stringResource(R.string.workspace_breadcrumb_separator),
              groupName,
          ),
          color = color().onSecondary,
          style = MaterialTheme.typography.bodyMedium,
          maxLines = 1,
      )

      Spacer(Modifier.weight(1f))

      IconButton(
          onClick = onSearchClicked,
          modifier = Modifier.testTag(C.Tag.search_open),
      ) {
        Icon(
            imageVector = Icons.Filled.Search,
            contentDescription = stringResource(R.string.search_open),
            tint = color().primary,
        )
      }
    }

    Text(
        text = groupName,
        style = MaterialTheme.typography.headlineMedium,
        color = color().onSecondary,
        fontFamily = FontFamily.Serif,
        fontSize = 30.sp,
    )

    Spacer(Modifier.height(8.dp))

    Text(
        text = stringResource(R.string.workspace_item_count, itemCount),
        style = MaterialTheme.typography.bodyMedium,
        color = color().tertiary,
    )

    Spacer(Modifier.height(10.dp))

    roleLabel?.let { role ->
      Surface(
          shape = RoundedCornerShape(8.dp),
          color = color().surfaceContainerHighest,
      ) {
        Text(
            text = role,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelMedium,
            color = color().primary,
        )
      }
    }

    Spacer(Modifier.height(16.dp))
  }
}

@Composable
private fun WorkspaceContent(
    uiState: GroupWorkspaceUiState,
    groupId: String,
    onItemClick: (JDRFile) -> Unit,
    onEdit: (JDRFile) -> Unit,
    onRemove: (JDRFile) -> Unit,
    currentUserId: String,
    viewModel: GroupWorkspaceViewModel,
) {

  when (uiState.loadState) {
    LoadState.Loading -> {

      Box(
          Modifier.fillMaxSize().testTag(C.Tag.workspace_loading),
          contentAlignment = Alignment.Center,
      ) {
        CircularProgressIndicator()
      }
    }

    LoadState.SignedOut -> {
      Box(
          Modifier.fillMaxSize(),
          contentAlignment = Alignment.Center,
      ) {
        Text("Sign in to view this workspace.")
      }
    }

    is LoadState.Error -> {

      Box(
          Modifier.fillMaxSize(),
          contentAlignment = Alignment.Center,
      ) {
        val message = uiState.errorMessage.ifBlank { (uiState.loadState).message }
        Text(message.ifBlank { stringResource(R.string.workspace_load_error) })
      }
    }

    LoadState.Success -> {
      Column(modifier = Modifier.fillMaxSize()) {
        if (uiState.isOperationInProgress) {
          Row(
              modifier =
                  Modifier.fillMaxWidth()
                      .padding(bottom = 10.dp)
                      .testTag(C.Tag.workspace_operation_progress),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
            )
            Text(stringResource(R.string.workspace_operation_in_progress))
          }
        }

        if (uiState.items.isEmpty()) {
          EmptyWorkspace()
        } else {
          LazyColumn(
              modifier = Modifier.fillMaxSize(),
              verticalArrangement = Arrangement.spacedBy(14.dp),
              contentPadding = PaddingValues(bottom = 100.dp),
          ) {
            items(
                uiState.items,
                key = { it.id },
            ) { file ->
              val isHidden = file.placements[groupId]?.isHidden ?: false
              val canChangeVisibility = uiState.isGameMaster || currentUserId == file.ownerId
              val canEdit = file.ownerId == currentUserId
              val canRemove = canEdit || uiState.isGameMaster

              WorkspaceCard(
                  file = file,
                  canEdit = canEdit,
                  canRemove = canRemove,
                  isHidden = isHidden,
                  canChangeVisibility = canChangeVisibility,
                  operationInProgress = uiState.isOperationInProgress,
                  onOpen = { onItemClick(file) },
                  onEdit = { onEdit(file) },
                  onRemove = { onRemove(file) },
                  onToggleVisibility = {
                    viewModel.setItemHidden(
                        item = file,
                        isHidden = !isHidden,
                    )
                  },
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun OperationButtonContent(
    isInProgress: Boolean,
    progressTag: String,
    label: String,
) {
  Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    if (isInProgress) {
      CircularProgressIndicator(
          modifier = Modifier.size(16.dp).testTag(progressTag),
          strokeWidth = 2.dp,
      )
    }
    Text(label, color = color().primary)
  }
}

/** Displays the empty state for a workspace with no items. */
@Composable
fun EmptyWorkspace() {

  Box(
      modifier = Modifier.fillMaxSize(),
      contentAlignment = Alignment.Center,
  ) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      Icon(
          Icons.Default.FolderOpen,
          contentDescription = null,
          modifier = Modifier.size(72.dp),
          tint = color().tertiary,
      )

      Spacer(Modifier.height(12.dp))

      Text(
          stringResource(R.string.workspace_empty_title),
          style = MaterialTheme.typography.titleLarge,
          color = color().tertiary,
      )

      Text(
          stringResource(R.string.workspace_empty_message),
          color = color().tertiary,
      )
    }
  }
}

/**
 * Lets the user choose the type of item to create in the current workspace.
 *
 * @param isOperationInProgress Whether a workspace operation is running, disabling the options.
 * @param onFolderSelected Called when the folder option is selected.
 * @param onCharacterSelected Called when the character option is selected.
 * @param onDismiss Called when the dialog is closed without a selection.
 */
@Composable
private fun CreateItemDialog(
    isOperationInProgress: Boolean,
    onFolderSelected: () -> Unit,
    onCharacterSelected: () -> Unit,
    onDismiss: () -> Unit,
) {
  AlertDialog(
      modifier = Modifier.testTag(C.Tag.workspace_create_item_dialog),
      containerColor = color().surface,
      onDismissRequest = onDismiss,
      title = {
        Text(stringResource(R.string.workspace_create_item_title), color = color().tertiary)
      },
      text = {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Text(
              stringResource(R.string.workspace_create_item_description),
              color = color().onSecondary,
          )
          CreateItemOption(
              label = stringResource(R.string.workspace_folder_action),
              icon = Icons.Filled.Folder,
              enabled = !isOperationInProgress,
              onClick = onFolderSelected,
              modifier = Modifier.testTag(C.Tag.workspace_create_item_folder),
          )
          CreateItemOption(
              label = stringResource(R.string.workspace_character_action),
              icon = Icons.Default.People,
              enabled = !isOperationInProgress,
              onClick = onCharacterSelected,
              modifier = Modifier.testTag(C.Tag.workspace_create_item_character),
          )
          CreateItemOption(
              label = stringResource(R.string.workspace_text_action),
              icon = Icons.Default.Description,
              enabled = false,
              onClick = {},
              modifier = Modifier.testTag(C.Tag.workspace_create_item_text),
              supportingText = stringResource(R.string.workspace_coming_soon),
          )
        }
      },
      confirmButton = {
        TextButton(
            modifier = Modifier.testTag(C.Tag.workspace_create_item_cancel),
            onClick = onDismiss,
        ) {
          Text(stringResource(R.string.workspace_cancel), color = color().primary)
        }
      },
  )
}

@Composable
private fun CreateItemOption(
    label: String,
    icon: ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
) {
  val contentAlpha = if (enabled) 1f else DISABLED_CONTENT_ALPHA

  Card(
      onClick = onClick,
      enabled = enabled,
      modifier = modifier.fillMaxWidth(),
      shape = RoundedCornerShape(12.dp),
      border = BorderStroke(1.dp, color().outline.copy(alpha = 0.65f)),
      colors =
          CardDefaults.cardColors(
              containerColor = color().background,
              disabledContainerColor = color().background,
          ),
  ) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Icon(
          imageVector = icon,
          contentDescription = null,
          tint = color().primary.copy(alpha = contentAlpha),
      )
      Text(
          text = label,
          modifier = Modifier.weight(1f),
          style = MaterialTheme.typography.titleMedium,
          color = color().onSecondary.copy(alpha = contentAlpha),
      )
      supportingText?.let {
        Text(
            text = it,
            style = MaterialTheme.typography.labelSmall,
            color = color().tertiary,
        )
      }
    }
  }
}

/**
 * Displays actions for creating an item in the workspace or adding a personal file to it.
 *
 * @param onCreateItem Called when the user wants to create a new item.
 * @param onAddFile Called when a file should be added.
 */
@Composable
fun WorkspaceFab(
    onCreateItem: () -> Unit,
    onAddFile: () -> Unit,
) {

  var expanded by remember { mutableStateOf(false) }

  Column(horizontalAlignment = Alignment.End) {
    AnimatedVisibility(expanded) {
      Column {
        ExtendedFloatingActionButton(
            containerColor = color().surface,
            text = { Text(stringResource(R.string.workspace_create), color = color().primary) },
            icon = {
              Icon(
                  Icons.Default.CreateNewFolder,
                  null,
                  tint = color().primary,
              )
            },
            onClick = {
              expanded = false
              onCreateItem()
            },
            modifier = Modifier.width(120.dp).testTag(C.Tag.workspace_create_item),
        )

        Spacer(Modifier.height(8.dp))

        ExtendedFloatingActionButton(
            containerColor = color().surface,
            text = {
              Text(stringResource(R.string.workspace_share_item_action), color = color().primary)
            },
            icon = {
              Icon(
                  Icons.Default.Share,
                  null,
                  tint = color().primary,
              )
            },
            onClick = {
              expanded = false
              onAddFile()
            },
            modifier = Modifier.width(120.dp).testTag(C.Tag.workspace_add_file),
        )

        Spacer(Modifier.height(8.dp))
      }
    }

    FloatingActionButton(
        containerColor = color().primary,
        onClick = { expanded = !expanded },
        shape = RoundedCornerShape(50),
        modifier = Modifier.testTag(C.Tag.workspace_actions_fab),
    ) {
      Icon(
          Icons.Default.Add,
          null,
      )
    }
  }
}

private const val DISABLED_CONTENT_ALPHA = 0.38f

@Composable
private fun color(): ColorScheme {
  return MaterialTheme.colorScheme
}

@Composable
private fun workspaceFieldColors(): TextFieldColors =
    darkFieldColors(
        container = color().surfaceContainerHighest,
        border = color().outline,
        textPrimary = color().onSecondary,
        primary = color().primary,
    )

/**
 * Returns personal items that can be shared with the specified group.
 *
 * @param personalItems Personal items to filter.
 * @param groupId Group identifier whose existing shares should be excluded.
 * @param ownerId Required owner identifier.
 */
fun availableItemsToShare(
    personalItems: List<JDRFile>,
    groupId: String,
    ownerId: String,
): List<JDRFile> {
  return personalItems.filter { item ->
    item.ownerId == ownerId &&
        item.type != DocumentType.GROUP_ROOT &&
        groupId !in item.sharedGroupsIds
  }
}

private val DocumentType.labelResource: Int
  get() =
      when (this) {
        DocumentType.FOLDER -> R.string.workspace_folder_type
        DocumentType.TEXT -> R.string.workspace_text_type
        DocumentType.CHARACTER -> R.string.workspace_character_type
        DocumentType.GROUP_ROOT -> R.string.workspace_group_root_type
      }
