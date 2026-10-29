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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.transition.Visibility
import com.github.se.jdrnexus.model.personalSpace.DocumentType
import com.github.se.jdrnexus.model.personalSpace.JDRFile
import com.github.se.jdrnexus.model.personalSpace.WorkspaceRepository
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
    gwViewModel: GroupWorkspaceViewModel,
    onBack: () -> Unit = {},
    onItemClicked: (JDRFile, Boolean) -> Unit,
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
          WorkspaceFab(
              canCreateFolder = uiState.canCreateFolder,
              onAddFolder = {
                folderName = ""
                showCreateFolderDialog = true
              },
              onAddFile = { showAddFilesDialog = true },
          )
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
                if (uiState.currentFolderId.isEmpty()) gwViewModel.groupId
                else uiState.currentFolderName,
            itemCount = uiState.items.size,
            folderStack = folderStack,
            onBack = { navigateBack() },
            roleLabel = gwViewModel.role.name,
        )

        Spacer(Modifier.height(16.dp))

        WorkspaceContent(
            uiState = uiState,
            onItemClick = { file ->
              if (file.type == DocumentType.FOLDER) {
                openFolder(file.id)
              } else {
                onItemClicked(
                    file,
                    uiState.canEditItems,
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
        containerColor = color().surface,
        onDismissRequest = {
          if (!uiState.isOperationInProgress) {
            showCreateFolderDialog = false
          }
        },
        title = { Text("New folder", color = color().tertiary) },
        text = {
          OutlinedTextField(
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
              isError = uiState.errorMessage != "",
              supportingText = { uiState.errorMessage?.let { error -> Text(text = error) } },
          )
        },
        confirmButton = {
          TextButton(
              enabled = !uiState.isOperationInProgress,
              onClick = {
                gwViewModel.clearError()

                scope.launch {
                  val job = gwViewModel.createFolder(folderName.trim())
                  job?.join()

                  if (gwViewModel.uiState.value.errorMessage.isNullOrEmpty()) {
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

  // Add existing personal files
  if (showAddFilesDialog) {

    val availableFiles =
        availableFilesToAdd(
            personalItems = personalItems,
            groupId = gwViewModel.groupId,
            ownerId = uiState.currentUserId,
        )

    AlertDialog(
        onDismissRequest = {
          if (!uiState.isOperationInProgress) {
            showAddFilesDialog = false
          }
        },
        title = { Text("Add files to workspace", color = color().tertiary) },
        text = {
          Column {
            // Display any error from the ViewModel.
            uiState.errorMessage
                ?.takeIf { it.isNotBlank() }
                ?.let { message ->
                  Text(
                      text = message,
                      color = MaterialTheme.colorScheme.error,
                      modifier = Modifier.padding(bottom = 8.dp),
                  )
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

                                  if (gwViewModel.uiState.value.errorMessage.isNullOrEmpty()) {
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
          }
        },
        containerColor = color().surface,
        confirmButton = {
          TextButton(
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
                isError = uiState.errorMessage != "",
                supportingText = { uiState.errorMessage?.let { error -> Text(text = error) } },
            )

            if (file.type != DocumentType.FOLDER) {
              OutlinedTextField(
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

                  job.join()

                  if (gwViewModel.uiState.value.errorMessage.isNullOrEmpty()) {
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
        containerColor = color().surface,
        onDismissRequest = { if (!uiState.isOperationInProgress) itemToRemove = null },
        title = { Text("Remove from workspace?", color = color().tertiary) },
        text = {
          Text("${file.name} will be deleted from this group.", color = color().onSecondary)
        },
        confirmButton = {
          TextButton(
              enabled = !uiState.isOperationInProgress,
              onClick = {
                gwViewModel.clearError()

                scope.launch {
                  val job = gwViewModel.removeItem(file)
                  job.join()

                  if (gwViewModel.uiState.value.errorMessage.isNullOrEmpty()) {
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
    isHidden : Boolean,
    canChangeVisibility: Boolean,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
    onToggleVisibility: () -> Unit
) {

  val isFolder = file.type == DocumentType.FOLDER

  Card(
      modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
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
                  modifier = Modifier.size(36.dp),
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
                  modifier = Modifier.size(36.dp),
              ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Remove ${file.name}",
                    tint = color().onSecondary,
                )
              }
            }
              if (canChangeVisibility){
                  IconButton(
                      onClick = onToggleVisibility,
                      modifier = Modifier.size(36.dp)
                  ) {
                      Icon(
                          imageVector =
                              if (!isHidden){
                                  Icons.Default.Visibility}
                              else {
                                  Icons.Default.VisibilityOff},
                          contentDescription = "Hide ${file.name}",
                          tint = color().onSecondary
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
    folderStack: List<String>,
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
        Text("Unable to load workspace")
      }
    }

    LoadState.Success -> {

      if (uiState.items.isEmpty()) {
        print("jdfvoj")

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
            WorkspaceCard(
                file = file,
                canEdit =
                    viewModel.canEditItem(
                        file,
                        currentUserId,
                    ),
                canRemove = canEditItems,
                onOpen = { onItemClick(file) },
                onEdit = { onEdit(file) },
                onRemove = { onRemove(file) },
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

internal fun availableFilesToAdd(
    personalItems: List<JDRFile>,
    groupId: String,
    ownerId: String,
): List<JDRFile> = personalItems.filter { file ->
  file.type != DocumentType.FOLDER && groupId !in file.sharedGroupsIds && file.ownerId == ownerId
}

@Preview
@Composable
private fun GroupWorkSpaceScreenPreview2() {
  val repository = remember { FakeWorkspaceRepository() }
  val authRepository = remember { FakeAuthRepository() }

  val viewModel = remember {
    GroupWorkspaceViewModel(
        groupId = "preview-group",
        role = GroupWorkspaceRole.GAME_MASTER,
        repository = repository,
        authRepository = authRepository,
    )
  }

  SampleAppTheme(darkTheme = false) {
    GroupWorkSpaceScreen(
        gwViewModel = viewModel,
        onBack = {},
        onItemClicked = { _, _ -> },
        personalItems = emptyList(),
    )
  }
}

@Preview
@Composable
private fun GroupWorkSpaceScreenPreview() {
  val repository = remember { FakeWorkspaceRepository() }
  val authRepository = remember { FakeAuthRepository() }

  val viewModel = remember {
    GroupWorkspaceViewModel(
        groupId = "preview-group",
        role = GroupWorkspaceRole.PLAYER,
        repository = repository,
        authRepository = authRepository,
    )
  }

  SampleAppTheme(darkTheme = true) {
    GroupWorkSpaceScreen(
        gwViewModel = viewModel,
        onBack = {},
        onItemClicked = { _, _ -> },
        personalItems = emptyList(),
    )
  }
}

private class FakeWorkspaceRepository : WorkspaceRepository {

  private val groupId = "preview-group"

  private val files =
      MutableStateFlow(
          listOf(
              JDRFile(
                  id = "ashen-realms",
                  name = "The Ashen Realms",
                  type = DocumentType.FOLDER,
                  content = "8 files · World lore & maps",
                  sharedGroupsIds = listOf(groupId),
              ),
              JDRFile(
                  id = "elara-moonwhisper",
                  name = "Elara Moonwhisper",
                  ownerId = "preview-user",
                  type = DocumentType.CHARACTER,
                  content = "Level 7 · Half-elf ranger",
                  sharedGroupsIds = listOf(groupId),
              ),
              JDRFile(
                  id = "sunken-citadel",
                  name = "The Sunken Citadel",
                  type = DocumentType.TEXT,
                  content = "Lore of a forgotten kingdom",
                  sharedGroupsIds = listOf(groupId),
              ),
              JDRFile(
                  id = "session-pact",
                  name = "Session 12 — The Pact",
                  type = DocumentType.TEXT,
                  content = "Notes from our last adventure",
                  sharedGroupsIds = listOf(groupId),
              ),
              JDRFile(
                  id = "silver-covenant",
                  name = "The Silver Covenant",
                  type = DocumentType.FOLDER,
                  content = "4 files · Allies & adversaries",
                  sharedGroupsIds = listOf(groupId),
              ),
          )
      )

  override fun getNewUid(): String = UUID.randomUUID().toString()

  override suspend fun createFile(file: JDRFile): Result<Unit> = runCatching { files.value += file }

  override suspend fun updateFile(file: JDRFile): Result<Unit> = runCatching {
    val index = files.value.indexOfFirst { it.id == file.id }
    require(index >= 0) { "File not found: ${file.id}" }

    files.value = files.value.toMutableList().apply { set(index, file) }
  }

  override suspend fun deleteFile(fileId: String): Result<Unit> = runCatching {
    require(files.value.any { it.id == fileId }) { "File not found: $fileId" }

    files.value = files.value.filterNot { it.id == fileId }
  }

  override suspend fun getFile(fileId: String): Result<JDRFile?> =
      Result.success(files.value.find { it.id == fileId })

  override fun getFileFlow(fileId: String): Flow<JDRFile?> = files.map { list ->
    list.find { it.id == fileId }
  }

  override fun getPersonalRootFiles(ownerId: String): Flow<List<JDRFile>> = files.map { list ->
    list.filter { it.ownerId == ownerId && it.personalParentId.isEmpty() }
  }

  override fun getGroupRootFiles(groupId: String): Flow<List<JDRFile>> = files.map { list ->
    list.filter { groupId in it.sharedGroupsIds && it.parentFolderIds.isEmpty() }
  }

  override fun getDocumentsInFolder(folderId: String): Flow<List<JDRFile>> = files.map { list ->
    list.filter { folderId in it.parentFolderIds }
  }

  override suspend fun removeItemFromGroup(
      itemId: String,
      groupId: String,
      parentFolderId: String?,
  ): Result<Unit> = runCatching {
    val item =
        files.value.find { it.id == itemId }
            ?: throw IllegalArgumentException("File not found: $itemId")

    files.value =
        files.value.map {
          if (it.id == itemId) {
            it.copy(
                sharedGroupsIds = it.sharedGroupsIds - groupId,
                parentFolderIds = emptyList(),
            )
          } else {
            it
          }
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
