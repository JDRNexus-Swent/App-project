// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.github.se.jdrnexus.ui.groupWorkSpace

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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.se.jdrnexus.model.personalSpace.DocumentType
import com.github.se.jdrnexus.model.personalSpace.JDRFile
import com.github.se.jdrnexus.resources.JdrNexusLogo
import com.github.se.jdrnexus.resources.darkFieldColors
import com.github.se.jdrnexus.viewmodel.LoadState
import kotlin.collections.emptyList
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupWorkSpaceScreen(
    gwViewModel: GroupWorkspaceViewModel = viewModel(),
    groupId: String,
    groupName: String,
    onBack: () -> Unit = {},
    onItemClicked: (JDRFile, Boolean) -> Unit = { _, _ -> },
    personalItems: List<JDRFile> = emptyList(),
) {
  val uiState by gwViewModel.uiState.collectAsState()
  val scope = rememberCoroutineScope()

  var showCreateFolderDialog by remember { mutableStateOf(false) }
  var showAddFilesDialog by remember { mutableStateOf(false) }
  var itemToEdit by remember { mutableStateOf<JDRFile?>(null) }
  var itemToRemove by remember { mutableStateOf<JDRFile?>(null) }

  var folderName by remember { mutableStateOf("") }
  var editedName by remember { mutableStateOf("") }
  var editedContent by remember { mutableStateOf("") }
  var selectedTab by rememberSaveable { mutableStateOf(GroupWorkspaceTab.CAMPAIGNS) }

  val folderStack = remember { mutableStateListOf<String>() }

  // Close a dialog after a successful operation.
  // An error leaves the dialog open so the user can correct or retry.

  fun openFolder(folderId: String) {
    folderStack.add(uiState.currentFolderId)
    gwViewModel.loadWorkspace(folderId)
  }

  fun navigateBack() {
    if (folderStack.isNotEmpty()) {
      gwViewModel.loadWorkspace(folderStack.removeAt(folderStack.lastIndex))
    } else {
      onBack()
    }
  }

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
                canCreateFolder = uiState.canCreateFolder,
                onAddFolder = {
                  gwViewModel.clearError()
                  folderName = ""
                  showCreateFolderDialog = true
                },
                onAddFile = {
                  gwViewModel.clearError()
                  showAddFilesDialog = true
                },
            )
          }
        },
        bottomBar = {
          GroupWorkspaceBottomBar(
              selectedTab = selectedTab,
              onTabSelected = { selectedTab = it },
          )
        },
    ) { padding ->
      Column(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 14.dp)) {
        WorkspaceHeader(
            groupName =
                if (uiState.currentFolderId.isEmpty()) groupName else uiState.currentFolderName,
            itemCount = uiState.items.size,
            onBack = { navigateBack() },
            roleLabel =
                if (uiState.canCreateFolder) {
                  "GAME MASTER"
                } else {
                  "PLAYER"
                },
        )

        Spacer(Modifier.height(16.dp))

        WorkspaceContent(
            uiState = uiState,
            groupId = groupId,
            onItemClick = { file ->
              if (file.type == DocumentType.FOLDER) {
                openFolder(file.id)
              } else {
                onItemClicked(
                    file,
                    uiState.canEditItems || file.ownerId == uiState.currentUserId,
                )
              }
            },
            onEdit = {
              gwViewModel.clearError()
              itemToEdit = it
              editedName = it.name
              editedContent = it.content
            },
            onRemove = {
              gwViewModel.clearError()
              itemToRemove = it
            },
            canEditItems = uiState.canEditItems,
            currentUserId = uiState.currentUserId,
            viewModel = gwViewModel,
        )
      }
    }
  }

  // Create folder
  if (showCreateFolderDialog) {
    AlertDialog(
        modifier = Modifier.testTag("create_folder_dialog"),
        containerColor = color().surface,
        onDismissRequest = {
          if (!uiState.isOperationInProgress) {
            showCreateFolderDialog = false
          }
        },
        title = { Text("New folder", color = color().tertiary) },
        text = {
          OutlinedTextField(
              modifier = Modifier.testTag("create_folder_name_input"),
              value = folderName,
              onValueChange = { folderName = it },
              label = { Text("Folder name") },
              singleLine = true,
              enabled = !uiState.isOperationInProgress,
              colors =
                  darkFieldColors(
                      container = color().onBackground,
                      border = color().onSurface,
                      textPrimary = color().onSecondary,
                      primary = color().primary,
                  ),
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
              modifier = Modifier.testTag("create_folder_confirm"),
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
            Text("Create", color = color().primary)
          }
        },
        dismissButton = {
          TextButton(
              modifier = Modifier.testTag("create_folder_cancel"),
              enabled = !uiState.isOperationInProgress,
              onClick = {
                showCreateFolderDialog = false
                gwViewModel.clearError()
              },
          ) {
            Text("Cancel", color = color().primary)
          }
        },
    )
  }

  // Add existing personnel files
  if (showAddFilesDialog) {

    val availableFiles =
        availableFilesToAdd(
            personalItems = personalItems,
            groupId = groupId,
            ownerId = uiState.currentUserId,
        )

    AlertDialog(
        modifier = Modifier.testTag("add_files_dialog"),
        onDismissRequest = {
          if (!uiState.isOperationInProgress) {
            showAddFilesDialog = false
          }
        },
        title = { Text("Add files to workspace", color = color().tertiary) },
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
          }

          if (availableFiles.isEmpty()) {
            Text("No personal files available to add.")
          } else {
            LazyColumn(
                modifier =
                    Modifier.size(
                            width = 300.dp,
                            height = 280.dp,
                        )
                        .testTag("add_files_list")
            ) {
              items(availableFiles, key = { it.id }) { file ->
                Row(
                    modifier =
                        Modifier.fillMaxWidth()
                            .clickable(enabled = !uiState.isOperationInProgress) {
                              gwViewModel.clearError()

                              scope.launch {
                                val job = gwViewModel.addFile(file.id)
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
                      Icons.Default.Description,
                      contentDescription = null,
                  )
                  Text(file.name)
                }
              }
            }
          }
        },
        containerColor = color().surface,
        confirmButton = {
          TextButton(
              modifier = Modifier.testTag("add_files_done"),
              enabled = !uiState.isOperationInProgress,
              onClick = { showAddFilesDialog = false },
          ) {
            Text("Done", color = color().primary)
          }
        },
    )
  }

  // Edit file or folder
  itemToEdit?.let { file ->
    AlertDialog(
        modifier = Modifier.testTag("edit_item_dialog"),
        containerColor = color().surface,
        onDismissRequest = { if (!uiState.isOperationInProgress) itemToEdit = null },
        title = {
          Text(
              if (file.type == DocumentType.FOLDER) {
                "Edit folder"
              } else {
                "Edit file"
              },
              color = color().tertiary,
          )
        },
        text = {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                modifier = Modifier.testTag("edit_item_name_input"),
                value = editedName,
                onValueChange = { editedName = it },
                label = { Text("Name", color = color().primary) },
                singleLine = true,
                enabled = !uiState.isOperationInProgress,
                colors =
                    darkFieldColors(
                        container = color().onBackground,
                        border = color().onSurface,
                        textPrimary = color().onSecondary,
                        primary = color().primary,
                    ),
                isError = uiState.errorMessage.isNotBlank(),
                supportingText = {
                  if (uiState.errorMessage.isNotBlank()) {
                    Text(text = uiState.errorMessage)
                  }
                },
            )

            if (file.type != DocumentType.FOLDER) {
              OutlinedTextField(
                  modifier = Modifier.testTag("edit_item_content_input"),
                  value = editedContent,
                  onValueChange = { editedContent = it },
                  label = { Text("Content", color = color().primary) },
                  enabled = !uiState.isOperationInProgress,
                  colors =
                      darkFieldColors(
                          container = color().onBackground,
                          border = color().onSurface,
                          textPrimary = color().onSecondary,
                          primary = color().primary,
                      ),
              )
            }
          }
        },
        confirmButton = {
          TextButton(
              modifier = Modifier.testTag("edit_item_save"),
              enabled = !uiState.isOperationInProgress,
              onClick = {
                gwViewModel.clearError()

                scope.launch {
                  val job =
                      gwViewModel.editItem(
                          file.copy(
                              name = editedName.trim(),
                              content = editedContent,
                          )
                      )

                  job?.join()

                  if (gwViewModel.uiState.value.errorMessage.isEmpty()) {
                    itemToEdit = null
                  }
                }
              },
          ) {
            Text("Save", color = color().primary)
          }
        },
        dismissButton = {
          TextButton(
              modifier = Modifier.testTag("edit_item_cancel"),
              enabled = !uiState.isOperationInProgress,
              onClick = {
                itemToEdit = null
                gwViewModel.clearError()
              },
          ) {
            Text("Cancel", color = color().primary)
          }
        },
    )
  }

  // Remove item from this group's workspace
  itemToRemove?.let { file ->
    AlertDialog(
        modifier = Modifier.testTag("remove_item_dialog"),
        containerColor = color().surface,
        onDismissRequest = { if (!uiState.isOperationInProgress) itemToRemove = null },
        title = { Text("Remove from workspace?", color = color().tertiary) },
        text = {
          Text("${file.name} will be deleted from this group.", color = color().onSecondary)
        },
        confirmButton = {
          TextButton(
              modifier = Modifier.testTag("remove_item_confirm"),
              enabled = !uiState.isOperationInProgress,
              onClick = {
                gwViewModel.clearError()

                scope.launch {
                  val job = gwViewModel.removeItem(file)
                  job.join()

                  if (gwViewModel.uiState.value.errorMessage.isEmpty()) {
                    itemToRemove = null
                  }
                }
              },
          ) {
            Text("Remove", color = color().primary)
          }
        },
        dismissButton = {
          TextButton(
              modifier = Modifier.testTag("remove_item_cancel"),
              enabled = !uiState.isOperationInProgress,
              onClick = { itemToRemove = null },
          ) {
            Text("Cancel", color = color().primary)
          }
        },
    )
  }
}

@Composable
fun WorkspaceCard(
    file: JDRFile,
    canEdit: Boolean,
    canRemove: Boolean,
    isHidden: Boolean,
    canChangeVisibility: Boolean,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
    onToggleVisibility: () -> Unit,
) {

  val isFolder = file.type == DocumentType.FOLDER

  Card(
      modifier =
          Modifier.fillMaxWidth().clickable(onClick = onOpen).testTag("workspace_item_${file.id}"),
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
                  "FOLDER"
                } else {
                  file.type.name.replace("_", " ")
                },
            style = MaterialTheme.typography.labelSmall,
            color = color().tertiary,
        )

        Text(
            text = file.name,
            style = MaterialTheme.typography.titleMedium,
            color = color().onSecondary,
        )

        Text(
            text =
                file.content.replace("\n", " ").ifBlank {
                  if (isFolder) {
                    "Open folder to view its contents"
                  } else {
                    "No description available"
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
                  modifier = Modifier.size(36.dp).testTag("workspace_edit_${file.id}"),
              ) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = "Edit ${file.name}",
                    tint = color().onSecondary,
                )
              }
            }

            if (canRemove) {
              IconButton(
                  onClick = onRemove,
                  modifier = Modifier.size(36.dp).testTag("workspace_remove_${file.id}"),
              ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Remove ${file.name}",
                    tint = color().onSecondary,
                )
              }
            }
            if (canChangeVisibility) {
              IconButton(
                  onClick = onToggleVisibility,
                  modifier = Modifier.size(36.dp).testTag("workspace_visibility_${file.id}"),
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
                          "Show ${file.name}"
                        } else {
                          "Hide ${file.name}"
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
            contentDescription = "Open ${file.name}",
            tint = color().primary,
        )
      }
    }
  }
}

@Composable
fun WorkspaceHeader(
    groupName: String,
    itemCount: Int,
    onBack: () -> Unit,
    roleLabel: String,
) {

  Column(modifier = Modifier.fillMaxWidth()) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
      IconButton(onClick = onBack) {
        Icon(
            Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Go back",
            tint = color().primary,
        )
      }

      Text(
          "Groups",
          color = color().tertiary,
          style = MaterialTheme.typography.bodyMedium,
      )

      Text(
          "  ›  $groupName",
          color = color().onSecondary,
          style = MaterialTheme.typography.bodyMedium,
          maxLines = 1,
      )

      Spacer(Modifier.weight(1f))

      IconButton(onClick = { /* Add search behavior */ }) {
        Icon(
            Icons.Default.Search,
            contentDescription = "Search",
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
        text = "$itemCount items · Shared group workspace",
        style = MaterialTheme.typography.bodyMedium,
        color = color().tertiary,
    )

    Spacer(Modifier.height(10.dp))

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = color().onBackground,
    ) {
      Text(
          text = roleLabel,
          modifier =
              Modifier.padding(
                  horizontal = 10.dp,
                  vertical = 5.dp,
              ),
          style = MaterialTheme.typography.labelMedium,
          color = color().primary,
      )
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
    canEditItems: Boolean,
    currentUserId: String,
    viewModel: GroupWorkspaceViewModel,
) {

  when (uiState.loadState) {
    LoadState.Loading -> {

      Box(
          Modifier.fillMaxSize(),
          contentAlignment = Alignment.Center,
      ) {
        CircularProgressIndicator()
      }
    }

    is LoadState.Error -> {

      Box(
          Modifier.fillMaxSize(),
          contentAlignment = Alignment.Center,
      ) {
        val message = uiState.errorMessage.ifBlank { (uiState.loadState).message }
        Text(message.ifBlank { "Unable to load workspace" })
      }
    }

    LoadState.Success -> {

      if (uiState.items.isEmpty()) {
        EmptyWorkspace()
      } else {

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(bottom = 100.dp),
        ) {
          items(
              uiState.items,
              key = { it.id },
          ) { file ->
            val isHidden = file.placements[groupId]?.isHidden ?: false
            val canChangeVisibility = uiState.canCreateFolder || currentUserId == file.ownerId
            val itemCanEdit = canEditItems || file.ownerId == currentUserId
            WorkspaceCard(
                file = file,
                canEdit = itemCanEdit,
                canRemove = itemCanEdit,
                isHidden = isHidden,
                canChangeVisibility = canChangeVisibility,
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
          "No files yet",
          style = MaterialTheme.typography.titleLarge,
          color = color().tertiary,
      )

      Text(
          "Add files to share them with the group.",
          color = color().tertiary,
      )
    }
  }
}

@Composable
fun WorkspaceFab(
    canCreateFolder: Boolean,
    onAddFolder: () -> Unit,
    onAddFile: () -> Unit,
) {

  var expanded by remember { mutableStateOf(false) }

  Column(horizontalAlignment = Alignment.End) {
    AnimatedVisibility(expanded) {
      Column {
        if (canCreateFolder) {

          ExtendedFloatingActionButton(
              containerColor = color().surface,
              text = { Text("Folder", color = color().primary) },
              icon = {
                Icon(
                    Icons.Default.CreateNewFolder,
                    null,
                    tint = color().primary,
                )
              },
              onClick = onAddFolder,
              modifier = Modifier.width(120.dp).testTag("workspace_add_folder"),
          )

          Spacer(Modifier.height(8.dp))
        }

        ExtendedFloatingActionButton(
            containerColor = color().surface,
            text = { Text("File", color = color().primary) },
            icon = {
              Icon(
                  Icons.Default.Description,
                  null,
                  tint = color().primary,
              )
            },
            onClick = onAddFile,
            modifier = Modifier.width(120.dp).testTag("workspace_add_file"),
        )

        Spacer(Modifier.height(8.dp))
      }
    }

    FloatingActionButton(
        containerColor = color().primary,
        onClick = { expanded = !expanded },
        shape = RoundedCornerShape(50),
        modifier = Modifier.testTag("workspace_actions_fab"),
    ) {
      Icon(
          Icons.Default.Add,
          null,
      )
    }
  }
}

@Composable
private fun color(): ColorScheme {
  return MaterialTheme.colorScheme
}

fun availableFilesToAdd(
    personalItems: List<JDRFile>,
    groupId: String,
    ownerId: String,
): List<JDRFile> {
  return personalItems.filter { file ->
    file.ownerId == ownerId && file.type != DocumentType.FOLDER && groupId !in file.sharedGroupsIds
  }
}
