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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.se.jdrnexus.model.personalSpace.CampaignGroup
import com.github.se.jdrnexus.model.personalSpace.DocumentType
import com.github.se.jdrnexus.model.personalSpace.GroupRepository
import com.github.se.jdrnexus.model.personalSpace.JDRFile
import com.github.se.jdrnexus.model.personalSpace.Placement
import com.github.se.jdrnexus.model.personalSpace.WorkspaceRepository
import com.github.se.jdrnexus.model.personalSpace.isContainer
import com.github.se.jdrnexus.model.repository.AuthRepository
import com.github.se.jdrnexus.model.repository.AuthResult
import com.github.se.jdrnexus.model.repository.AuthUser
import com.github.se.jdrnexus.resources.JdrNexusLogo
import com.github.se.jdrnexus.resources.darkFieldColors
import com.github.se.jdrnexus.ui.theme.SampleAppTheme
import com.github.se.jdrnexus.viewmodel.LoadState
import java.util.UUID
import kotlin.collections.emptyList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
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

private const val previewGroupId = "preview-group"
private const val previewGroupName = "The Ashen Realms"

@Preview(showBackground = true)
@Composable
private fun GroupWorkspaceGameMasterPreview() {
  val repository = remember { FakeWorkspaceRepository(/* your preview files */ ) }
  val authRepository = remember { FakeAuthRepository() }

  val group = remember {
    CampaignGroup(
        id = previewGroupId,
        name = previewGroupName,
        gameMasterId = "preview-user",
        participantIds = listOf("preview-user"),
    )
  }

  val groupRepository = remember { FakeGroupRepository(group) }

  val viewModel = remember {
    GroupWorkspaceViewModel(
        groupId = group.id,
        groupRepository = groupRepository,
        repository = repository,
        authRepository = authRepository,
    )
  }

  SampleAppTheme(darkTheme = false) {
    GroupWorkSpaceScreen(
        gwViewModel = viewModel,
        groupId = previewGroupId,
        groupName = previewGroupName,
        onItemClicked = { _, _ -> },
    )
  }
}

@Preview(showBackground = true)
@Composable
private fun GroupWorkspaceGameMasterPreview2() {
  val repository = remember { FakeWorkspaceRepository(/* your preview files */ ) }
  val authRepository = remember { FakeAuthRepository() }

  val group = remember {
    CampaignGroup(
        id = previewGroupId,
        name = previewGroupName,
        gameMasterId = "preview-user",
        participantIds = listOf("player-2"),
    )
  }

  val groupRepository = remember { FakeGroupRepository(group) }

  val viewModel = remember {
    GroupWorkspaceViewModel(
        groupId = group.id,
        groupRepository = groupRepository,
        repository = repository,
        authRepository = authRepository,
    )
  }

  SampleAppTheme(darkTheme = true) {
    GroupWorkSpaceScreen(
        gwViewModel = viewModel,
        groupId = previewGroupId,
        groupName = previewGroupName,
        onItemClicked = { _, _ -> },
    )
  }
}

private class FakeWorkspaceRepository(
    initialFiles: List<JDRFile> =
        listOf(
            JDRFile(
                id = "ashen-realms",
                name = "The Ashen Realmss",
                ownerId = "preview-user",
                type = DocumentType.FOLDER,
                content = "8 files · World lore & maps",
                personalParentId = "",
                sharedGroupsIds = listOf(previewGroupId),
                placements =
                    mapOf(
                        previewGroupId to
                            Placement(
                                folderId = previewGroupId,
                                isHidden = false,
                            )
                    ),
            ),
            JDRFile(
                id = "elara-moonwhisper",
                name = "Elara Moonwhisper",
                ownerId = "preview-user",
                type = DocumentType.CHARACTER,
                content = "Level 7 · Half-elf ranger",
                sharedGroupsIds = listOf(previewGroupId),
                placements =
                    mapOf(
                        previewGroupId to
                            Placement(
                                folderId = previewGroupId,
                                isHidden = false,
                            )
                    ),
            ),
            JDRFile(
                id = "sunken-citadel",
                name = "The Sunken Citadel",
                type = DocumentType.TEXT,
                content = "Lore of a forgotten kingdom",
                sharedGroupsIds = listOf(previewGroupId),
                placements =
                    mapOf(
                        previewGroupId to
                            Placement(
                                folderId = previewGroupId,
                                isHidden = false,
                            )
                    ),
            ),
            JDRFile(
                id = "session-pact",
                name = "Session 12 — The Pact",
                type = DocumentType.TEXT,
                content = "Notes from our last adventure",
                sharedGroupsIds = listOf(previewGroupId),
                placements =
                    mapOf(
                        previewGroupId to
                            Placement(
                                folderId = previewGroupId,
                                isHidden = false,
                            )
                    ),
            ),
            JDRFile(
                id = "silver-covenant",
                name = "The Silver Covenant",
                type = DocumentType.FOLDER,
                content = "4 files · Allies & adversaries",
                ownerId = "preview-user",
                sharedGroupsIds = listOf(previewGroupId),
                placements =
                    mapOf(
                        previewGroupId to
                            Placement(
                                folderId = previewGroupId,
                                isHidden = false,
                            )
                    ),
            ),
        ),
) : WorkspaceRepository {

  private val files = MutableStateFlow(initialFiles.associateBy { it.id })

  override fun getNewUid(): String = UUID.randomUUID().toString()

  override suspend fun createFile(file: JDRFile): Result<Unit> {
    files.value += (file.id to file)
    return Result.success(Unit)
  }

  override suspend fun updateFile(file: JDRFile): Result<Unit> {
    if (file.id !in files.value) {
      return Result.failure(IllegalArgumentException("Document not found"))
    }

    // Match the Firestore implementation: ordinary edits must not
    // overwrite group placements or group-sharing information.
    val current = files.value.getValue(file.id)
    files.value +=
        (file.id to
            file.copy(
                placements = current.placements,
                sharedGroupsIds = current.sharedGroupsIds,
            ))

    return Result.success(Unit)
  }

  override suspend fun deleteFile(fileId: String): Result<Unit> {
    files.value -= fileId
    return Result.success(Unit)
  }

  override suspend fun getFile(fileId: String): Result<JDRFile?> {
    val file =
        files.value[fileId] ?: return Result.failure(IllegalArgumentException("Document not found"))

    return Result.success(file)
  }

  override fun getFileFlow(fileId: String): Flow<JDRFile?> = files.map { it[fileId] }

  override fun getPersonalRootFiles(ownerId: String): Flow<List<JDRFile>> = files.map { allFiles ->
    allFiles.values.filter { file ->
      file.ownerId == ownerId && file.sharedGroupsIds.isEmpty() && file.personalParentId.isEmpty()
    }
  }

  override fun getPersonalFolderFiles(
      folderId: String,
      ownerId: String,
  ): Flow<List<JDRFile>> = files.map { allFiles ->
    allFiles.values.filter {
      it.ownerId == ownerId && it.personalParentId == folderId && it.type != DocumentType.GROUP_ROOT
    }
  }

  override fun getGroupFolderFiles(
      folderId: String,
      groupId: String,
  ): Flow<List<JDRFile>> = files.map { allFiles ->
    allFiles.values
        .filter { file ->
          groupId in file.sharedGroupsIds && file.placements[groupId]?.folderId == folderId
        }
        .sortedBy { it.name.lowercase() }
  }

  override suspend fun addItemToGroup(
      itemId: String,
      groupId: String,
      folderId: String,
      isHidden: Boolean,
      includeContent: Boolean,
  ): Result<Unit> {
    return try {
      require(!includeContent) { "includeContent is not supported yet." }

      val item = files.value[itemId] ?: throw IllegalArgumentException("Item not found")

      val destination =
          files.value[folderId] ?: throw IllegalArgumentException("Destination folder not found")

      require(item.type != DocumentType.GROUP_ROOT) { "A group root cannot be added to a group." }
      require(groupId !in item.sharedGroupsIds) { "This item is already in the group." }
      require(destination.isContainer()) { "The destination is not a folder." }
      require(groupId in destination.sharedGroupsIds) {
        "The destination folder does not belong to the group."
      }

      val hiddenByFolder = destination.placements[groupId]?.isHidden == true

      files.value +=
          (itemId to
              item.copy(
                  sharedGroupsIds = item.sharedGroupsIds + groupId,
                  placements =
                      item.placements +
                          (groupId to
                              Placement(
                                  folderId = folderId,
                                  isHidden = isHidden || hiddenByFolder,
                              )),
              ))

      Result.success(Unit)
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  override suspend fun removeItemFromGroup(
      itemId: String,
      groupId: String,
      parentFolderId: String,
  ): Result<Unit> {
    return try {
      val root = files.value[itemId] ?: throw IllegalArgumentException("Item not found")

      require(groupId in root.sharedGroupsIds) { "This item does not belong to the group." }
      require(root.type != DocumentType.GROUP_ROOT) {
        "A group root cannot be removed from its group."
      }

      val visited = mutableSetOf<String>()

      fun collectSubtree(file: JDRFile) {
        if (!visited.add(file.id)) return

        if (file.isContainer()) {
          files.value.values
              .filter {
                groupId in it.sharedGroupsIds && it.placements[groupId]?.folderId == file.id
              }
              .forEach(::collectSubtree)
        }
      }

      collectSubtree(root)

      visited.forEach { id ->
        val file = files.value[id] ?: return@forEach

        files.value +=
            (id to
                file.copy(
                    sharedGroupsIds = file.sharedGroupsIds - groupId,
                    placements = file.placements - groupId,
                ))
      }

      Result.success(Unit)
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  override suspend fun setItemHidden(
      itemId: String,
      groupId: String,
      isHidden: Boolean,
  ): Result<Unit> {
    return try {
      val item = files.value[itemId] ?: throw IllegalArgumentException("Item not found")

      val placement =
          item.placements[groupId]
              ?: throw IllegalArgumentException("This item does not belong to the group.")

      if (placement.isHidden == isHidden) {
        return Result.success(Unit)
      }

      if (isHidden && item.isContainer()) {
        val visited = mutableSetOf<String>()

        fun collectSubtree(file: JDRFile) {
          if (!visited.add(file.id)) return

          if (file.isContainer()) {
            files.value.values
                .filter {
                  groupId in it.sharedGroupsIds && it.placements[groupId]?.folderId == file.id
                }
                .forEach(::collectSubtree)
          }
        }

        collectSubtree(item)

        visited.forEach { id ->
          val file = files.value[id] ?: return@forEach
          val currentPlacement = file.placements[groupId] ?: return@forEach

          files.value +=
              (id to
                  file.copy(
                      placements =
                          file.placements + (groupId to currentPlacement.copy(isHidden = true))
                  ))
        }
      } else {
        files.value +=
            (itemId to
                item.copy(
                    placements = item.placements + (groupId to placement.copy(isHidden = isHidden))
                ))
      }

      Result.success(Unit)
    } catch (e: Exception) {
      Result.failure(e)
    }
  }
}

private class FakeAuthRepository : AuthRepository {

  private val fakeUser =
      AuthUser(
          uid = "preview-user",
          email = "player@example.com",
          username = "PreviewPlayer",
      )

  private val currentUser = MutableStateFlow<AuthUser?>(fakeUser)

  override val authState: Flow<AuthUser?> = currentUser

  override suspend fun signUpWithEmail(
      email: String,
      password: String,
      username: String,
  ): AuthResult<AuthUser> {
    val user =
        AuthUser(
            uid = "preview-user",
            email = email,
            username = username,
        )
    currentUser.value = user
    return AuthResult.Success(user)
  }

  override suspend fun signInWithEmail(
      email: String,
      password: String,
  ): AuthResult<AuthUser> {
    currentUser.value = fakeUser
    return AuthResult.Success(fakeUser)
  }

  override suspend fun signInWithGoogle(idToken: String): AuthResult<AuthUser> {
    currentUser.value = fakeUser
    return AuthResult.Success(fakeUser)
  }

  override suspend fun signOut(): AuthResult<Unit> {
    currentUser.value = null
    return AuthResult.Success(Unit)
  }
}

private class FakeGroupRepository(
    initialGroup: CampaignGroup,
) : GroupRepository {

  private val groups = MutableStateFlow(mapOf(initialGroup.id to initialGroup))

  override fun getNewGroupId(): String = UUID.randomUUID().toString()

  override suspend fun createGroup(group: CampaignGroup): Result<Unit> {
    groups.value += (group.id to group)
    return Result.success(Unit)
  }

  override suspend fun updateGroup(group: CampaignGroup): Result<Unit> {
    if (group.id !in groups.value) {
      return Result.failure(IllegalArgumentException("CampaignGroup not found"))
    }

    groups.value += (group.id to group)
    return Result.success(Unit)
  }

  override suspend fun deleteGroup(groupId: String): Result<Unit> {
    groups.value -= groupId
    return Result.success(Unit)
  }

  override suspend fun getGroup(groupId: String): Result<CampaignGroup> {
    val group =
        groups.value[groupId]
            ?: return Result.failure(IllegalArgumentException("CampaignGroup not found"))

    return Result.success(group)
  }

  override suspend fun addUserToGroup(
      groupId: String,
      userId: String,
  ): Result<Unit> {
    val group =
        groups.value[groupId]
            ?: return Result.failure(IllegalArgumentException("CampaignGroup not found"))

    groups.value +=
        (groupId to group.copy(participantIds = (group.participantIds + userId).distinct()))

    return Result.success(Unit)
  }

  override suspend fun removeUserFromGroup(
      groupId: String,
      userId: String,
  ): Result<Unit> {
    val group =
        groups.value[groupId]
            ?: return Result.failure(IllegalArgumentException("CampaignGroup not found"))

    groups.value += (groupId to group.copy(participantIds = group.participantIds - userId))

    return Result.success(Unit)
  }

  override fun getGroupFlow(groupId: String): Flow<CampaignGroup?> = groups.map { it[groupId] }

  override fun getUserGroups(userId: String): Flow<List<CampaignGroup>> = groups.map { groupMap ->
    groupMap.values.filter { group ->
      userId == group.gameMasterId || userId in group.participantIds
    }
  }
}
