package com.github.se.jdrnexus.ui.edition

// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import com.github.se.jdrnexus.model.filerepresentation.LoreSection
import com.github.se.jdrnexus.ui.jdrfile.EditFileField
import com.github.se.jdrnexus.ui.jdrfile.EditFileViewModel

/** Test tags exposed by the lore editor for UI tests. */
object EditFileLoreTestTags {
  const val LIST = "edit_lore_list"
  const val SECTIONS_LIST = "edit_lore_sections"
  const val BACK = "edit_lore_back"
}

/**
 * Displays an editor for a lore file, including its title, sections, validation messages, and save
 * state.
 *
 * @param viewModel supplies the lore data and handles edits and saving.
 * @param onBack is called when the user requests to leave the editor.
 * @param onSaved is called when the view model reports a successful save.
 */
@Composable
fun EditFileLore(
    viewModel: EditFileViewModel,
    onBack: () -> Unit,
    onSaved: () -> Unit,
) {
  val state by viewModel.uiState.collectAsState()

  LaunchedEffect(state.isSaved) { if (state.isSaved) onSaved() }

  EditFileScaffold(
      isLoading = state.isLoading,
      loadingMessage = "Loading lore...",
      hasContent = state.lore != null,
      screenTitle = "LORE NOTE",
      backTestTag = EditFileLoreTestTags.BACK,
      error = state.error,
      emptyMessage = "No lore is loaded.",
      onBack = onBack,
  ) {
    val lore = state.lore ?: return@EditFileScaffold
    var newSectionTitle by remember { mutableStateOf("") }
    var newSectionDescription by remember { mutableStateOf("") }

    LazyColumn(
        modifier =
            Modifier.fillMaxSize()
                .imePadding()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .testTag(EditFileLoreTestTags.LIST),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      item(key = "top-bar") {
        EditFileHeader(
            title = "LORE NOTE",
            backTestTag = EditFileLoreTestTags.BACK,
            onBack = onBack,
        )
      }
      item(key = "lore-title") {
        OutlinedTextField(
            value = lore.title,
            onValueChange = viewModel::updateLoreTitle,
            modifier = Modifier.fillMaxWidth().testTag("edit_lore_title"),
            label = { Text("Lore title") },
            singleLine = true,
        )
      }
      item(key = "sections-heading") {
        EditFileSectionHeading(
            title = "Sections",
            count = lore.sections.size,
            modifier = Modifier.fillMaxWidth().testTag(EditFileLoreTestTags.SECTIONS_LIST),
            singularCountLabel = "section",
        )
      }
      items(lore.sections, key = { "section-${it.sectionID}" }) { section ->
        LoreSectionCard(
            section = section,
            titleError = state.fieldErrors[EditFileField.LoreSectionTitle(section.sectionID)],
            onTitleChange = { viewModel.updateLoreSectionTitle(section.sectionID, it) },
            onDescriptionChange = { viewModel.updateLoreSectionDescription(section.sectionID, it) },
            onRemove = { viewModel.removeLoreSection(section.sectionID) },
        )
      }
      item(key = "add-section") {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
          OutlinedTextField(
              value = newSectionTitle,
              onValueChange = { newSectionTitle = it },
              modifier = Modifier.fillMaxWidth().testTag("add_section_title"),
              label = { Text("Section title") },
              singleLine = true,
          )
          OutlinedTextField(
              value = newSectionDescription,
              onValueChange = { newSectionDescription = it },
              modifier = Modifier.fillMaxWidth().testTag("add_section_description"),
              label = { Text("Content") },
              minLines = 2,
          )
          OutlinedButton(
              onClick = {
                viewModel.addLoreSection(newSectionTitle, newSectionDescription)
                if (newSectionTitle.isNotBlank()) {
                  newSectionTitle = ""
                  newSectionDescription = ""
                }
              },
              modifier = Modifier.fillMaxWidth().testTag("confirm_add_section"),
              shape = RoundedCornerShape(8.dp),
          ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Text("Add section", modifier = Modifier.padding(start = 6.dp))
          }
        }
      }
      item(key = "section-divider") { HorizontalDivider() }
      state.error?.let { error ->
        item(key = "error") { EditFileErrorMessage(error, "edit_lore_error") }
      }
      item(key = "save") {
        EditFileSaveButton(
            isSaving = state.isSaving,
            isSaved = state.isSaved,
            testTag = "edit_lore_save",
            onSave = viewModel::saveJDRfile,
        )
      }
    }
  }
}

/**
 * Displays one lore section and forwards its edits and removal request.
 *
 * @param section the lore section shown by this card.
 * @param titleError validation message for the section title, if any.
 * @param onTitleChange called when the title is edited.
 * @param onDescriptionChange called when the description is edited.
 * @param onRemove called when the user removes the section.
 */
@Composable
private fun LoreSectionCard(
    section: LoreSection,
    titleError: String?,
    onTitleChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onRemove: () -> Unit,
) {
  Surface(
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(10.dp),
      color = MaterialTheme.colorScheme.surfaceVariant,
      tonalElevation = 1.dp,
  ) {
    Column(modifier = Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
      ) {
        OutlinedTextField(
            value = section.title,
            onValueChange = onTitleChange,
            modifier = Modifier.weight(1f).testTag("section_title_${section.sectionID}"),
            label = { Text("Section title") },
            singleLine = true,
            isError = titleError != null,
            supportingText = titleError?.let { message -> ({ Text(message) }) },
        )
        IconButton(onClick = onRemove, modifier = Modifier.size(40.dp)) {
          Icon(Icons.Default.Close, contentDescription = "Remove ${section.title}")
        }
      }
      OutlinedTextField(
          value = section.description,
          onValueChange = onDescriptionChange,
          modifier = Modifier.fillMaxWidth().testTag("section_description_${section.sectionID}"),
          label = { Text("Content") },
          minLines = 3,
      )
    }
  }
}

/* REMOVE THE COMMENTS TO PREVIEW. THESE ARE HERE TO KEEP COVERAGE UP

/** Previews the lore editor with sample sections. */
@Preview(showBackground = true, heightDp = 820)
@Composable
private fun EditFileLorePreview() {
  val file = remember {
    JDRFile(
        id = "preview-lore",
        name = "The Sunken Citadel",
        type = DocumentType.TEXT,
        content =
            """{"version":1,"type":"lore","title":"The Sunken Citadel","sections":[{"sectionID":"kingdom","title":"A kingdom beneath the tide","content":"Once the heart of the kingdom of Aurel, the citadel now sleeps beneath the western sea."},{"sectionID":"oath","title":"The drowned oath","content":"The last queen bound her court to the sea to keep an ancient evil sealed."}]}""",
    )
  }
  val viewModel = remember { EditFileViewModel(PreviewWorkspaceRepo(file)) }
  LaunchedEffect(viewModel) { viewModel.loadFile(file.id) }

  SampleAppTheme { EditFileLore(viewModel = viewModel, onBack = {}, onSaved = {}) }
}

/** Supplies the sample file to the editor preview without accessing persistent storage. */
private class PreviewWorkspaceRepo(private val previewFile: JDRFile) : WorkspaceRepository {
  override fun getNewUid(): String = "preview-id"

  override suspend fun createFile(file: JDRFile): Result<Unit> = Result.success(Unit)

  override suspend fun updateFile(file: JDRFile): Result<Unit> = Result.success(Unit)

  override suspend fun deleteFile(fileId: String): Result<Unit> = Result.success(Unit)

  override suspend fun getFile(fileId: String): Result<JDRFile?> =
      Result.success(previewFile.takeIf { it.id == fileId })

  override fun getFileFlow(fileId: String): Flow<JDRFile?> = emptyFlow()

  override fun getPersonalRootFiles(ownerId: String): Flow<List<JDRFile>> = emptyFlow()

  override fun getGroupRootFiles(groupId: String): Flow<List<JDRFile>> = emptyFlow()

  override fun getDocumentsInFolder(folderId: String): Flow<List<JDRFile>> = emptyFlow()
}

 */
