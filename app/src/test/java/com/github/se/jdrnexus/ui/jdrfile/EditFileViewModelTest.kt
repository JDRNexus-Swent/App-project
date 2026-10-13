// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.github.se.jdrnexus.ui.jdrfile

import android.os.Looper
import com.github.se.jdrnexus.model.filerepresentation.CharacterAction
import com.github.se.jdrnexus.model.filerepresentation.CharacterFile
import com.github.se.jdrnexus.model.filerepresentation.CharacterStat
import com.github.se.jdrnexus.model.filerepresentation.LoreFile
import com.github.se.jdrnexus.model.filerepresentation.LoreSection
import com.github.se.jdrnexus.model.personalSpace.DocumentType
import com.github.se.jdrnexus.model.personalSpace.JDRFile
import com.github.se.jdrnexus.model.personalSpace.WorkspaceRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class EditFileViewModelTest {
  private val characterJson =
      """{"version":1,"type":"character","character":{"name":"Jean-claude","stats":[{"statID":"strength","name":"Strength","value":16},{"statID":"dexterity","name":"Dexterity","value":12}],"actions":[{"actionID":"sword_attack","name":"Sword Attack","description":"Attack"},{"actionID":"heavy_strike","name":"Heavy Strike","description":"Strike"}]}}"""
  private val loreJson =
      """{"version":1,"type":"lore","title":"The Kingdom","sections":[{"sectionID":"history","title":"History","content":"Founded long ago."},{"sectionID":"king","title":"The King","content":"Rules today."}]}"""

  private fun idleMainLooper() {
    shadowOf(Looper.getMainLooper()).idle()
  }

  @Test
  fun loadsCharacterUsingRepositoryFileTypeAndExposesItsModel() {
    val file =
        JDRFile(
            id = "character-1",
            name = "Hero",
            type = DocumentType.CHARACTER,
            content = characterJson,
        )
    val repository = FakeRepository(file)
    val viewModel = EditFileViewModel(repository)

    viewModel.loadFile(file.id)
    idleMainLooper()

    assertEquals(listOf(file.id), repository.requestedFileIds)
    assertEquals(file, viewModel.uiState.value.file)
    assertEquals(
        CharacterFile(
            "Jean-claude",
            listOf(
                CharacterStat("strength", "Strength", 16.0),
                CharacterStat("dexterity", "Dexterity", 12.0),
            ),
            listOf(
                CharacterAction("sword_attack", "Sword Attack", "Attack"),
                CharacterAction("heavy_strike", "Heavy Strike", "Strike"),
            ),
        ),
        viewModel.uiState.value.character,
    )
    assertNull(viewModel.uiState.value.lore)
    assertFalse(viewModel.uiState.value.isLoading)
    assertNull(viewModel.uiState.value.error)
  }

  @Test
  fun loadingAndSavingFlagsReflectPendingRepositoryOperations() {
    val file = JDRFile(id = "character", type = DocumentType.CHARACTER, content = characterJson)
    val loadGate = CompletableDeferred<Unit>()
    val loadRepository = FakeRepository(file, getFileGate = loadGate)
    val loadingViewModel = EditFileViewModel(loadRepository)

    loadingViewModel.loadFile(file.id)
    idleMainLooper()
    assertTrue(loadingViewModel.uiState.value.isLoading)

    loadGate.complete(Unit)
    idleMainLooper()
    assertFalse(loadingViewModel.uiState.value.isLoading)
    assertEquals(file, loadingViewModel.uiState.value.file)

    val saveGate = CompletableDeferred<Unit>()
    val saveRepository = FakeRepository(file, updateFileGate = saveGate)
    val savingViewModel = loadedViewModel(file, saveRepository)
    savingViewModel.updateCharacterName("Updated")

    savingViewModel.saveJDRfile()
    idleMainLooper()
    assertTrue(savingViewModel.uiState.value.isSaving)
    assertFalse(savingViewModel.uiState.value.isSaved)

    saveGate.complete(Unit)
    idleMainLooper()
    assertFalse(savingViewModel.uiState.value.isSaving)
    assertTrue(savingViewModel.uiState.value.isSaved)
  }

  @Test
  fun loadsLoreAndAcceptsEmptyCollections() {
    val loreFile = JDRFile(id = "lore-1", type = DocumentType.TEXT, content = loreJson)
    val loreViewModel = loadedViewModel(loreFile)
    assertEquals(
        LoreFile(
            "The Kingdom",
            listOf(
                LoreSection("history", "History", "Founded long ago."),
                LoreSection("king", "The King", "Rules today."),
            ),
        ),
        loreViewModel.uiState.value.lore,
    )
    assertNull(loreViewModel.uiState.value.character)

    val emptyCharacter =
        loadedViewModel(
            JDRFile(
                id = "new-character",
                type = DocumentType.CHARACTER,
                content = """{"version":1,"type":"character"}""",
            )
        )
    assertEquals(
        CharacterFile("", emptyList(), emptyList()),
        emptyCharacter.uiState.value.character,
    )

    val emptyLore =
        loadedViewModel(
            JDRFile(
                id = "new-lore",
                type = DocumentType.TEXT,
                content = """{"version":1,"type":"lore"}""",
            )
        )
    assertEquals(LoreFile("", emptyList()), emptyLore.uiState.value.lore)
  }

  @Test
  fun loadErrorsAreExposedForMissingFilesRepositoryFailuresAndInvalidContent() {
    val missing = EditFileViewModel(FakeRepository())
    missing.loadFile("missing")
    idleMainLooper()
    assertEquals("File not found.", missing.uiState.value.error)

    val repositoryFailure =
        EditFileViewModel(FakeRepository(getFailure = IllegalStateException("Offline")))
    repositoryFailure.loadFile("file")
    idleMainLooper()
    assertEquals("Offline", repositoryFailure.uiState.value.error)

    val mismatched =
        loadedViewModel(JDRFile(id = "mismatch", type = DocumentType.TEXT, content = characterJson))
    assertEquals("The JSON type does not match this file.", mismatched.uiState.value.error)

    val folder = loadedViewModel(JDRFile(id = "folder", type = DocumentType.FOLDER, content = ""))
    assertEquals("Folders cannot be edited as files.", folder.uiState.value.error)

    val malformed = loadedViewModel(JDRFile(id = "broken", type = DocumentType.TEXT, content = "{"))
    assertTrue(malformed.uiState.value.error?.isNotBlank() == true)
    assertFalse(malformed.uiState.value.isLoading)
  }

  @Test
  fun characterFieldsStatsAndActionsAreEditedByStableIds() {
    val viewModel =
        loadedViewModel(
            JDRFile(id = "character", type = DocumentType.CHARACTER, content = characterJson)
        )

    viewModel.updateCharacterName("New Hero")
    viewModel.addCharacterStat("Willpower", 17.5)
    viewModel.updateCharacterStatName("strength", "Might")
    viewModel.updateCharacterStatValue("strength", 18.0)
    viewModel.removeCharacterStat("dexterity")
    viewModel.addCharacterAction("Healing", "Restore health")
    viewModel.updateCharacterActionName("sword_attack", "Slash")
    viewModel.updateCharacterActionDescription("sword_attack", "A precise cut")
    viewModel.removeCharacterAction("heavy_strike")

    val character = viewModel.uiState.value.character!!
    val addedStat = character.stats.single { it.name == "Willpower" }
    val addedAction = character.actions.single { it.name == "Healing" }
    assertTrue(addedStat.statID.isNotBlank())
    assertTrue(addedAction.actionID.isNotBlank())
    assertEquals(
        CharacterFile(
            "New Hero",
            listOf(
                CharacterStat("strength", "Might", 18.0),
                addedStat,
            ),
            listOf(
                CharacterAction("sword_attack", "Slash", "A precise cut"),
                addedAction,
            ),
        ),
        character,
    )
  }

  @Test
  fun invalidCharacterEditsDoNotApplyAndExposeValidation() {
    val viewModel =
        loadedViewModel(
            JDRFile(id = "character", type = DocumentType.CHARACTER, content = characterJson)
        )
    val initial = viewModel.uiState.value.character

    viewModel.addCharacterStat("Luck", Double.NaN)
    assertEquals(initial, viewModel.uiState.value.character)
    assertEquals(
        "Stat value must be a finite number.",
        viewModel.uiState.value.fieldErrors.values.single(),
    )

    viewModel.addCharacterStat(" ", 1.0)
    assertEquals(
        "Stat name cannot be empty.",
        viewModel.uiState.value.fieldErrors.values.single(),
    )
    viewModel.updateCharacterStatName("strength", "")
    assertEquals(
        "Stat name cannot be empty.",
        viewModel.uiState.value.fieldErrors[EditFileField.CharacterStatName("strength")],
    )
    viewModel.updateCharacterStatValue("strength", Double.POSITIVE_INFINITY)
    assertEquals(
        "Stat value must be a finite number.",
        viewModel.uiState.value.fieldErrors[EditFileField.CharacterStatValue("strength")],
    )

    viewModel.updateCharacterStatValue("missing", 1.0)
    assertEquals("Stat not found.", viewModel.uiState.value.error)
    assertEquals(
        "Stat value must be a finite number.",
        viewModel.uiState.value.fieldErrors[EditFileField.CharacterStatValue("strength")],
    )

    viewModel.addCharacterAction(" ", "Description")
    assertEquals(
        "Action name cannot be empty.",
        viewModel.uiState.value.fieldErrors.values.single(),
    )
    viewModel.updateCharacterActionName("sword_attack", "")
    assertEquals(
        "Action name cannot be empty.",
        viewModel.uiState.value.fieldErrors.values.single(),
    )
    viewModel.removeCharacterAction("missing")
    assertEquals("Action not found.", viewModel.uiState.value.error)
  }

  @Test
  fun loreTitleAndSectionsAreUpdatedIndividuallyBySectionId() {
    val viewModel =
        loadedViewModel(JDRFile(id = "lore", type = DocumentType.TEXT, content = loreJson))

    viewModel.updateLoreTitle("A New Realm")
    viewModel.addLoreSection("Geography", "Mountainous")
    viewModel.updateLoreSectionTitle("history", "Origins")
    viewModel.updateLoreSectionDescription("history", "A distant past.")
    viewModel.removeLoreSection("king")

    val addedSection = viewModel.uiState.value.lore!!.sections.single { it.title == "Geography" }
    assertTrue(addedSection.sectionID.isNotBlank())
    assertEquals(
        LoreFile(
            "A New Realm",
            listOf(
                LoreSection("history", "Origins", "A distant past."),
                addedSection,
            ),
        ),
        viewModel.uiState.value.lore,
    )
  }

  @Test
  fun invalidLoreSectionTitlesAndUnknownIdsAreRejected() {
    val viewModel =
        loadedViewModel(JDRFile(id = "lore", type = DocumentType.TEXT, content = loreJson))

    viewModel.addLoreSection(" ", "Text")
    assertEquals(
        "Section title cannot be empty.",
        viewModel.uiState.value.fieldErrors.values.single(),
    )
    viewModel.updateLoreSectionTitle("missing", "Title")
    assertEquals("Section not found.", viewModel.uiState.value.error)
    viewModel.updateLoreSectionDescription("missing", "Description")
    assertEquals("Section not found.", viewModel.uiState.value.error)
    viewModel.removeLoreSection("missing")
    assertEquals("Section not found.", viewModel.uiState.value.error)
  }

  @Test
  fun savePersistsUpdatedJsonToSameFileAndPreservesRepositoryMetadata() {
    val file =
        JDRFile(
            id = "character",
            name = "Hero",
            ownerId = "owner",
            parentFolderIds = listOf("parent"),
            type = DocumentType.CHARACTER,
            content = characterJson,
        )
    val repository = FakeRepository(file)
    val viewModel = EditFileViewModel(repository)
    viewModel.loadFile(file.id)
    idleMainLooper()
    viewModel.updateCharacterName("Saved Hero")
    viewModel.addCharacterStat("Willpower", 17.0)

    viewModel.saveJDRfile()
    idleMainLooper()

    val savedFile = repository.updatedFiles.single()
    assertEquals(file.id, savedFile.id)
    assertEquals(file.ownerId, savedFile.ownerId)
    assertEquals(file.parentFolderIds, savedFile.parentFolderIds)
    val savedContent = JSONObject(savedFile.content).getJSONObject("character")
    assertEquals("Saved Hero", savedContent.getString("name"))
    val savedStat = savedContent.getJSONArray("stats").getJSONObject(2)
    assertEquals("Willpower", savedStat.getString("name"))
    assertEquals(17, savedStat.getInt("value"))
    assertTrue(viewModel.uiState.value.isSaved)
    assertFalse(viewModel.uiState.value.isSaving)
    assertNull(viewModel.uiState.value.error)
  }

  @Test
  fun saveReportsValidationAndRepositoryErrors() {
    val invalidFile = JDRFile(id = "lore", type = DocumentType.TEXT, content = loreJson)
    val validationRepo = FakeRepository(invalidFile)
    val invalid = loadedViewModel(invalidFile, validationRepo)
    invalid.updateLoreSectionTitle("history", " ")
    invalid.saveJDRfile()
    idleMainLooper()
    assertEquals(
        "Section title cannot be empty.",
        invalid.uiState.value.fieldErrors[EditFileField.LoreSectionTitle("history")],
    )
    assertTrue(validationRepo.updatedFiles.isEmpty())

    val failingRepo =
        FakeRepository(
            invalidFile,
            updateFailure = IllegalStateException("Write denied"),
        )
    val failing = loadedViewModel(invalidFile, failingRepo)
    failing.updateLoreTitle("Valid title")
    failing.saveJDRfile()
    idleMainLooper()
    assertEquals("Write denied", failing.uiState.value.error)
    assertFalse(failing.uiState.value.isSaved)
    assertFalse(failing.uiState.value.isSaving)
  }

  @Test
  fun emptyCharacterNameAndLoreTitleCanBeSaved() {
    val characterFile =
        JDRFile(id = "character", type = DocumentType.CHARACTER, content = characterJson)
    val characterRepository = FakeRepository(characterFile)
    val character = loadedViewModel(characterFile, characterRepository)
    character.updateCharacterName("")
    character.saveJDRfile()
    idleMainLooper()
    assertEquals(
        "",
        JSONObject(characterRepository.updatedFiles.single().content)
            .getJSONObject("character")
            .getString("name"),
    )
    assertTrue(character.uiState.value.fieldErrors.isEmpty())

    val loreFile = JDRFile(id = "lore", type = DocumentType.TEXT, content = loreJson)
    val loreRepository = FakeRepository(loreFile)
    val lore = loadedViewModel(loreFile, loreRepository)
    lore.updateLoreTitle("")
    lore.saveJDRfile()
    idleMainLooper()
    assertEquals(
        "",
        JSONObject(loreRepository.updatedFiles.single().content).getString("title"),
    )
    assertTrue(lore.uiState.value.fieldErrors.isEmpty())
  }

  @Test
  fun saveBeforeLoadingExposesAnError() {
    val viewModel = EditFileViewModel(FakeRepository())

    viewModel.saveJDRfile()

    assertEquals("Load a file before saving.", viewModel.uiState.value.error)
  }

  private fun loadedViewModel(
      file: JDRFile,
      repository: FakeRepository = FakeRepository(file),
  ): EditFileViewModel {
    val viewModel = EditFileViewModel(repository)
    viewModel.loadFile(file.id)
    idleMainLooper()
    return viewModel
  }

  private class FakeRepository(
      private val file: JDRFile? = null,
      private val getFailure: Exception? = null,
      private val updateFailure: Exception? = null,
      private val getFileGate: CompletableDeferred<Unit>? = null,
      private val updateFileGate: CompletableDeferred<Unit>? = null,
  ) : WorkspaceRepository {
    val requestedFileIds = mutableListOf<String>()
    val updatedFiles = mutableListOf<JDRFile>()

    override fun getNewUid(): String = "new-id"

    override suspend fun createFile(file: JDRFile): Result<Unit> = Result.success(Unit)

    override suspend fun updateFile(file: JDRFile): Result<Unit> {
      updatedFiles.add(file)
      updateFileGate?.await()
      return updateFailure?.let { Result.failure(it) } ?: Result.success(Unit)
    }

    override suspend fun deleteFile(fileId: String): Result<Unit> = Result.success(Unit)

    override suspend fun getFile(fileId: String): Result<JDRFile?> {
      requestedFileIds.add(fileId)
      getFileGate?.await()
      return getFailure?.let { Result.failure(it) }
          ?: Result.success(file?.takeIf { it.id == fileId })
    }

    override fun getFileFlow(fileId: String): Flow<JDRFile?> = emptyFlow()

    override fun getPersonalRootFiles(ownerId: String): Flow<List<JDRFile>> = emptyFlow()

    override fun getGroupRootFiles(groupId: String): Flow<List<JDRFile>> = emptyFlow()

    override fun getDocumentsInFolder(folderId: String): Flow<List<JDRFile>> = emptyFlow()
  }
}
