// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.ui.jdrfile

import com.github.se.jdrnexus.codec.JDRFileCodecException
import com.github.se.jdrnexus.model.filerepresentation.CharacterAction
import com.github.se.jdrnexus.model.filerepresentation.CharacterFile
import com.github.se.jdrnexus.model.filerepresentation.CharacterHealthPoints
import com.github.se.jdrnexus.model.filerepresentation.CharacterStat
import com.github.se.jdrnexus.model.filerepresentation.LoreFile
import com.github.se.jdrnexus.model.filerepresentation.LoreSection
import com.github.se.jdrnexus.model.personalSpace.DocumentType
import com.github.se.jdrnexus.model.personalSpace.FileEdit
import com.github.se.jdrnexus.model.personalSpace.JDRFile
import com.github.se.jdrnexus.model.personalSpace.Placement
import com.github.se.jdrnexus.model.personalSpace.WorkspaceRepository
import com.github.se.jdrnexus.utils.MainDispatcherRule
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EditFileViewModelTest {
  private val characterJson =
      """{"version":1,"type":"character","character":{"name":"Jean-claude","stats":[{"statID":"strength","name":"Strength","value":16},{"statID":"dexterity","name":"Dexterity","value":12}],"actions":[{"actionID":"sword_attack","name":"Sword Attack","description":"Attack"},{"actionID":"heavy_strike","name":"Heavy Strike","description":"Strike"}],"hp":{"current":18,"max":24}}}"""
  private val loreJson =
      """{"version":1,"type":"lore","title":"The Kingdom","sections":[{"sectionID":"history","title":"History","content":"Founded long ago."},{"sectionID":"king","title":"The King","content":"Rules today."}]}"""

  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  @Test
  fun loadsCharacterUsingRepositoryFileTypeAndExposesItsModel() = runTest {
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
    runCurrent()

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
            CharacterHealthPoints(18, 24),
        ),
        viewModel.uiState.value.character,
    )
    assertNull(viewModel.uiState.value.lore)
    assertFalse(viewModel.uiState.value.isLoading)
    assertNull(viewModel.uiState.value.error)
  }

  @Test
  fun loadingAndSavingFlagsReflectPendingRepositoryOperations() = runTest {
    val file = JDRFile(id = "character", type = DocumentType.CHARACTER, content = characterJson)
    val loadGate = CompletableDeferred<Unit>()
    val loadRepository = FakeRepository(file, getFileGate = loadGate)
    val loadingViewModel = EditFileViewModel(loadRepository)

    loadingViewModel.loadFile(file.id)
    runCurrent()
    assertTrue(loadingViewModel.uiState.value.isLoading)

    loadGate.complete(Unit)
    runCurrent()
    assertFalse(loadingViewModel.uiState.value.isLoading)
    assertEquals(file, loadingViewModel.uiState.value.file)

    val saveGate = CompletableDeferred<Unit>()
    val saveRepository = FakeRepository(file, updateFileGate = saveGate)
    val savingViewModel = loadedViewModel(file, saveRepository)
    savingViewModel.updateCharacterName("Updated")

    savingViewModel.saveJDRfile()
    runCurrent()
    assertTrue(savingViewModel.uiState.value.isSaving)
    assertFalse(savingViewModel.uiState.value.isSaved)

    saveGate.complete(Unit)
    runCurrent()
    assertFalse(savingViewModel.uiState.value.isSaving)
    assertTrue(savingViewModel.uiState.value.isSaved)
  }

  @Test
  fun loadsLoreAndAcceptsEmptyCollections() = runTest {
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
        CharacterFile("", emptyList(), emptyList(), CharacterHealthPoints(0, 0)),
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
  fun loadErrorsAreExposedForMissingFilesRepositoryFailuresAndInvalidContent() = runTest {
    val missing = EditFileViewModel(FakeRepository())
    missing.loadFile("missing")
    runCurrent()
    assertEquals(EditFileViewModel.FILE_NOT_FOUND, missing.uiState.value.error)

    val repositoryFailure =
        EditFileViewModel(FakeRepository(getFailure = IllegalStateException("Offline")))
    repositoryFailure.loadFile("file")
    runCurrent()
    assertEquals("Offline", repositoryFailure.uiState.value.error)

    val mismatched =
        loadedViewModel(JDRFile(id = "mismatch", type = DocumentType.TEXT, content = characterJson))
    assertEquals(JDRFileCodecException.INVALID_FILE_MESSAGE, mismatched.uiState.value.error)

    val folder = loadedViewModel(JDRFile(id = "folder", type = DocumentType.FOLDER, content = ""))
    assertEquals(EditFileViewModel.FOLDER_ERROR, folder.uiState.value.error)

    val malformed = loadedViewModel(JDRFile(id = "broken", type = DocumentType.TEXT, content = "{"))
    assertTrue(malformed.uiState.value.error?.isNotBlank() == true)
    assertFalse(malformed.uiState.value.isLoading)
  }

  @Test
  fun characterFieldsStatsAndActionsAreEditedByStableIds() = runTest {
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
            CharacterHealthPoints(18, 24),
        ),
        character,
    )
  }

  @Test
  fun invalidCharacterEditsDoNotApplyAndExposeValidation() = runTest {
    val viewModel =
        loadedViewModel(
            JDRFile(id = "character", type = DocumentType.CHARACTER, content = characterJson)
        )
    val initial = viewModel.uiState.value.character

    viewModel.addCharacterStat("Luck", Double.NaN)
    assertEquals(initial, viewModel.uiState.value.character)
    assertEquals(
        EditFileViewModel.STAT_VALUE_ERROR,
        viewModel.uiState.value.fieldErrors.values.single(),
    )

    viewModel.addCharacterStat(" ", 1.0)
    assertEquals(
        EditFileViewModel.STAT_NAME_ERROR,
        viewModel.uiState.value.fieldErrors.values.single(),
    )
    viewModel.updateCharacterStatName("strength", "")
    assertEquals(
        EditFileViewModel.STAT_NAME_ERROR,
        viewModel.uiState.value.fieldErrors[EditFileField.CharacterStatName("strength")],
    )
    viewModel.updateCharacterStatValue("strength", Double.POSITIVE_INFINITY)
    assertEquals(
        EditFileViewModel.STAT_VALUE_ERROR,
        viewModel.uiState.value.fieldErrors[EditFileField.CharacterStatValue("strength")],
    )

    viewModel.updateCharacterStatValue("missing", 1.0)
    assertEquals(EditFileViewModel.STAT_NOT_FOUND_ERROR, viewModel.uiState.value.error)
    assertEquals(
        EditFileViewModel.STAT_VALUE_ERROR,
        viewModel.uiState.value.fieldErrors[EditFileField.CharacterStatValue("strength")],
    )

    viewModel.addCharacterAction(" ", "Description")
    assertEquals(
        EditFileViewModel.ACTION_NAME_ERROR,
        viewModel.uiState.value.fieldErrors.values.single(),
    )
    viewModel.updateCharacterActionName("sword_attack", "")
    assertEquals(
        EditFileViewModel.ACTION_NAME_ERROR,
        viewModel.uiState.value.fieldErrors.values.single(),
    )
    viewModel.removeCharacterAction("missing")
    assertEquals(EditFileViewModel.ACTION_NOT_FOUND_ERROR, viewModel.uiState.value.error)
  }

  @Test
  fun healthPointUpdatesValidateBoundsAndClampCurrentWhenMaximumDrops() = runTest {
    val viewModel =
        loadedViewModel(
            JDRFile(id = "character", type = DocumentType.CHARACTER, content = characterJson)
        )

    viewModel.updateCharacterCurrentHp(-1)
    assertEquals(
        EditFileViewModel.CURRENT_HP_ERROR,
        viewModel.uiState.value.fieldErrors[EditFileField.CharacterCurrentHp],
    )
    assertEquals(CharacterHealthPoints(18, 24), viewModel.uiState.value.character?.hp)

    viewModel.updateCharacterCurrentHp(25)
    assertEquals(
        EditFileViewModel.CURRENT_HP_EXCEEDS_MAX_ERROR,
        viewModel.uiState.value.fieldErrors[EditFileField.CharacterCurrentHp],
    )

    viewModel.updateCharacterCurrentHp(20)
    assertEquals(CharacterHealthPoints(20, 24), viewModel.uiState.value.character?.hp)
    viewModel.updateCharacterMaxHp(16)
    assertEquals(CharacterHealthPoints(16, 16), viewModel.uiState.value.character?.hp)

    viewModel.updateCharacterMaxHp(-1)
    assertEquals(
        EditFileViewModel.MAX_HP_ERROR,
        viewModel.uiState.value.fieldErrors[EditFileField.CharacterMaxHp],
    )
    assertEquals(CharacterHealthPoints(16, 16), viewModel.uiState.value.character?.hp)
  }

  @Test
  fun loadingInvalidHealthPointsExposesFieldErrorsAndPreventsSaving() = runTest {
    val invalidFile =
        JDRFile(
            id = "character",
            type = DocumentType.CHARACTER,
            content =
                """{"version":1,"type":"character","character":{"hp":{"current":12,"max":10}}}""",
        )
    val repository = FakeRepository(invalidFile)
    val viewModel = loadedViewModel(invalidFile, repository)

    assertEquals(
        EditFileViewModel.CURRENT_HP_EXCEEDS_MAX_ERROR,
        viewModel.uiState.value.fieldErrors[EditFileField.CharacterCurrentHp],
    )
    viewModel.saveJDRfile()
    assertTrue(repository.updatedFiles.isEmpty())
  }

  @Test
  fun loreTitleAndSectionsAreUpdatedIndividuallyBySectionId() = runTest {
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
  fun invalidLoreSectionTitlesAndUnknownIdsAreRejected() = runTest {
    val viewModel =
        loadedViewModel(JDRFile(id = "lore", type = DocumentType.TEXT, content = loreJson))

    viewModel.addLoreSection(" ", "Text")
    assertEquals(
        EditFileViewModel.SECTION_TITLE_ERROR,
        viewModel.uiState.value.fieldErrors.values.single(),
    )
    viewModel.updateLoreSectionTitle("missing", "Title")
    assertEquals(EditFileViewModel.SECTION_NOT_FOUND_ERROR, viewModel.uiState.value.error)
    viewModel.updateLoreSectionDescription("missing", "Description")
    assertEquals(EditFileViewModel.SECTION_NOT_FOUND_ERROR, viewModel.uiState.value.error)
    viewModel.removeLoreSection("missing")
    assertEquals(EditFileViewModel.SECTION_NOT_FOUND_ERROR, viewModel.uiState.value.error)
  }

  @Test
  fun savePersistsUpdatedJsonToSameFileAndPreservesRepositoryMetadata() = runTest {
    val file =
        JDRFile(
            id = "character",
            name = "Hero",
            ownerId = "owner",
            placements = mapOf("parent" to Placement(folderId = "parent")),
            type = DocumentType.CHARACTER,
            content = characterJson,
        )
    val repository = FakeRepository(file)
    val viewModel = EditFileViewModel(repository)
    viewModel.loadFile(file.id)
    runCurrent()
    viewModel.updateCharacterName("Saved Hero")
    viewModel.addCharacterStat("Willpower", 17.0)

    viewModel.saveJDRfile()
    runCurrent()

    val savedFile = repository.updatedFiles.single()
    assertEquals(file.id, savedFile.id)
    assertEquals(file.ownerId, savedFile.ownerId)
    assertEquals(file.placements, savedFile.placements)
    val savedContent = JSONObject(savedFile.content).getJSONObject("character")
    assertEquals("Saved Hero", savedContent.getString("name"))
    val savedStat = savedContent.getJSONArray("stats").getJSONObject(2)
    assertEquals("Willpower", savedStat.getString("name"))
    assertEquals(17, savedStat.getInt("value"))
    assertEquals(18, savedContent.getJSONObject("hp").getInt("current"))
    assertEquals(24, savedContent.getJSONObject("hp").getInt("max"))
    assertTrue(viewModel.uiState.value.isSaved)
    assertFalse(viewModel.uiState.value.isSaving)
    assertNull(viewModel.uiState.value.error)
  }

  @Test
  fun saveReportsValidationAndRepositoryErrors() = runTest {
    val invalidFile = JDRFile(id = "lore", type = DocumentType.TEXT, content = loreJson)
    val validationRepo = FakeRepository(invalidFile)
    val invalid = loadedViewModel(invalidFile, validationRepo)
    invalid.updateLoreSectionTitle("history", " ")
    invalid.saveJDRfile()
    runCurrent()
    assertEquals(
        EditFileViewModel.SECTION_TITLE_ERROR,
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
    runCurrent()
    assertEquals("Write denied", failing.uiState.value.error)
    assertFalse(failing.uiState.value.isSaved)
    assertFalse(failing.uiState.value.isSaving)
  }

  @Test
  fun cancelledSaveResetsSavingFlagWithoutReportingAnError() = runTest {
    val file = JDRFile(id = "character", type = DocumentType.CHARACTER, content = characterJson)
    val repository = FakeRepository(file, updateFailure = CancellationException("Save cancelled"))
    val viewModel = loadedViewModel(file, repository)

    viewModel.updateCharacterName("Updated")
    viewModel.saveJDRfile()
    runCurrent()

    assertFalse(viewModel.uiState.value.isSaving)
    assertFalse(viewModel.uiState.value.isSaved)
    assertNull(viewModel.uiState.value.error)
  }

  @Test
  fun emptyCharacterNameAndLoreTitleCanBeSaved() = runTest {
    val characterFile =
        JDRFile(id = "character", type = DocumentType.CHARACTER, content = characterJson)
    val characterRepository = FakeRepository(characterFile)
    val character = loadedViewModel(characterFile, characterRepository)
    character.updateCharacterName("")
    character.saveJDRfile()
    runCurrent()
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
    runCurrent()
    assertEquals(
        "",
        JSONObject(loreRepository.updatedFiles.single().content).getString("title"),
    )
    assertTrue(lore.uiState.value.fieldErrors.isEmpty())
  }

  @Test
  fun saveBeforeLoadingExposesAnError() = runTest {
    val viewModel = EditFileViewModel(FakeRepository())

    viewModel.saveJDRfile()

    assertEquals(EditFileViewModel.LOAD_BEFORE_SAVE_ERROR, viewModel.uiState.value.error)
  }

  @Test
  fun reloadingCancelsThePendingLoad() = runTest {
    val file = JDRFile(id = "character", type = DocumentType.CHARACTER, content = characterJson)
    val staleGate = CompletableDeferred<Unit>()
    val repository = FakeRepository(file, getFileGate = staleGate, gatedFileId = "missing")
    val viewModel = EditFileViewModel(repository)
    viewModel.loadFile("missing")
    runCurrent()

    viewModel.loadFile(file.id)
    runCurrent()
    staleGate.complete(Unit)
    runCurrent()

    assertEquals(listOf("missing", file.id), repository.requestedFileIds)
    assertEquals(file, viewModel.uiState.value.file)
    assertNull(viewModel.uiState.value.error)
  }

  @Test
  fun loadErrorWithoutMessageFallsBackToGenericText() = runTest {
    val loadFailureViewModel =
        EditFileViewModel(FakeRepository(getFailure = IllegalStateException()))

    loadFailureViewModel.loadFile("lore")
    runCurrent()

    assertEquals(EditFileViewModel.LOAD_ERROR, loadFailureViewModel.uiState.value.error)
  }

  @Test
  fun saveErrorWithoutMessageFallsBackToGenericText() = runTest {
    val loreFile = JDRFile(id = "lore", type = DocumentType.TEXT, content = loreJson)
    val saveFailureViewModel =
        loadedViewModel(loreFile, FakeRepository(loreFile, updateFailure = IllegalStateException()))
    saveFailureViewModel.updateLoreTitle("A New Realm")

    saveFailureViewModel.saveJDRfile()
    runCurrent()

    assertEquals(EditFileViewModel.SAVE_ERROR, saveFailureViewModel.uiState.value.error)
  }

  @Test
  fun loadingInvalidFieldsExposesEveryFieldError() = runTest {
    val file =
        JDRFile(
            id = "character",
            type = DocumentType.CHARACTER,
            content =
                """{"version":1,"type":"character","character":{"stats":[{"statID":"luck","name":" ","value":3}],"actions":[{"actionID":"dodge","name":"","description":"Evade"}],"hp":{"current":-2,"max":-1}}}""",
        )

    val viewModel = loadedViewModel(file)

    assertEquals(
        mapOf(
            EditFileField.CharacterCurrentHp to EditFileViewModel.CURRENT_HP_ERROR,
            EditFileField.CharacterMaxHp to EditFileViewModel.MAX_HP_ERROR,
            EditFileField.CharacterStatName("luck") to EditFileViewModel.STAT_NAME_ERROR,
            EditFileField.CharacterActionName("dodge") to EditFileViewModel.ACTION_NAME_ERROR,
        ),
        viewModel.uiState.value.fieldErrors,
    )
  }

  @Test
  fun fileThatFailedToLoadIsNeverSaved() = runTest {
    val files =
        listOf(
            JDRFile(id = "character", type = DocumentType.CHARACTER, content = loreJson),
            JDRFile(id = "lore", type = DocumentType.TEXT, content = characterJson),
            JDRFile(id = "folder", type = DocumentType.FOLDER, content = ""),
        )
    val repositories = files.map { FakeRepository(it) }
    val viewModels = files.zip(repositories).map { (file, repo) -> loadedViewModel(file, repo) }

    viewModels.forEach { it.saveJDRfile() }
    runCurrent()

    assertEquals(
        listOf(
            EditFileViewModel.CHARACTER_REQUIRED_ERROR,
            EditFileViewModel.LORE_REQUIRED_ERROR,
            EditFileViewModel.FOLDER_ERROR,
        ),
        viewModels.map { it.uiState.value.error },
    )
    assertEquals(emptyList<JDRFile>(), repositories.flatMap { it.updatedFiles })
  }

  @Test
  fun saveIgnoresSecondTapWhileSaving() = runTest {
    val file = JDRFile(id = "character", type = DocumentType.CHARACTER, content = characterJson)
    val saveGate = CompletableDeferred<Unit>()
    val repository = FakeRepository(file, updateFileGate = saveGate)
    val viewModel = loadedViewModel(file, repository)
    viewModel.updateCharacterName("Updated")

    viewModel.saveJDRfile()
    runCurrent()
    viewModel.saveJDRfile()
    runCurrent()
    assertEquals(1, repository.updatedFiles.size)
    assertTrue(viewModel.uiState.value.isSaving)
    assertFalse(viewModel.uiState.value.isSaved)
    assertNull(viewModel.uiState.value.error)

    saveGate.complete(Unit)
    runCurrent()

    assertEquals(1, repository.updatedFiles.size)
    assertTrue(viewModel.uiState.value.isSaved)
  }

  @Test
  fun editDuringSaveLeavesFileUnsaved() = runTest {
    val file = JDRFile(id = "character", type = DocumentType.CHARACTER, content = characterJson)
    val saveGate = CompletableDeferred<Unit>()
    val repository = FakeRepository(file, updateFileGate = saveGate)
    val viewModel = loadedViewModel(file, repository)
    viewModel.updateCharacterName("Saved Hero")

    viewModel.saveJDRfile()
    runCurrent()
    viewModel.updateCharacterName("Edited Hero")
    assertTrue(viewModel.uiState.value.isSaving)
    assertFalse(viewModel.uiState.value.isSaved)

    saveGate.complete(Unit)
    runCurrent()

    val savedName =
        JSONObject(repository.updatedFiles.single().content)
            .getJSONObject("character")
            .getString("name")
    assertEquals("Saved Hero", savedName)
    assertEquals("Edited Hero", viewModel.uiState.value.character?.name)
    assertFalse(viewModel.uiState.value.isSaved)
    assertFalse(viewModel.uiState.value.isSaving)
  }

  @Test
  fun editingTheWrongFileTypeReportsAnError() = runTest {
    val loreFile = JDRFile(id = "lore", type = DocumentType.TEXT, content = loreJson)
    val characterFile =
        JDRFile(id = "character", type = DocumentType.CHARACTER, content = characterJson)
    val characterEdits: List<(EditFileViewModel) -> Unit> =
        listOf(
            { it.updateCharacterCurrentHp(5) },
            { it.updateCharacterMaxHp(30) },
            { it.updateCharacterName("Hero") },
        )

    val loreViewModels = characterEdits.map { edit -> loadedViewModel(loreFile).also(edit) }
    val characterViewModel = loadedViewModel(characterFile).also { it.updateLoreTitle("Realm") }

    assertEquals(
        listOf(
            EditFileViewModel.CHARACTER_REQUIRED_ERROR,
            EditFileViewModel.CHARACTER_REQUIRED_ERROR,
            EditFileViewModel.CHARACTER_REQUIRED_ERROR,
        ),
        loreViewModels.map { it.uiState.value.error },
    )
    assertEquals(listOf(null, null, null), loreViewModels.map { it.uiState.value.character })
    assertEquals(EditFileViewModel.LORE_REQUIRED_ERROR, characterViewModel.uiState.value.error)
    assertNull(characterViewModel.uiState.value.lore)
  }

  @Test
  fun unknownCharacterIdsAreRejected() = runTest {
    val file = JDRFile(id = "character", type = DocumentType.CHARACTER, content = characterJson)
    val original = loadedViewModel(file).uiState.value.character
    val edits: List<(EditFileViewModel) -> Unit> =
        listOf(
            { it.updateCharacterStatName("missing", "Might") },
            { it.removeCharacterStat("missing") },
            { it.updateCharacterActionName("missing", "Slash") },
            { it.updateCharacterActionDescription("missing", "A precise cut") },
        )

    val viewModels = edits.map { edit -> loadedViewModel(file).also(edit) }

    assertEquals(
        listOf(
            EditFileViewModel.STAT_NOT_FOUND_ERROR,
            EditFileViewModel.STAT_NOT_FOUND_ERROR,
            EditFileViewModel.ACTION_NOT_FOUND_ERROR,
            EditFileViewModel.ACTION_NOT_FOUND_ERROR,
        ),
        viewModels.map { it.uiState.value.error },
    )
    assertEquals(List(4) { original }, viewModels.map { it.uiState.value.character })
  }

  private fun TestScope.loadedViewModel(
      file: JDRFile,
      repository: FakeRepository = FakeRepository(file),
  ): EditFileViewModel {
    val viewModel = EditFileViewModel(repository)
    viewModel.loadFile(file.id)
    runCurrent()
    return viewModel
  }

  private class FakeRepository(
      private val file: JDRFile? = null,
      private val getFailure: Exception? = null,
      private val updateFailure: Exception? = null,
      private val getFileGate: CompletableDeferred<Unit>? = null,
      private val updateFileGate: CompletableDeferred<Unit>? = null,
      /** When set, [getFileGate] only holds the load of this id. */
      private val gatedFileId: String? = null,
  ) : WorkspaceRepository {
    val requestedFileIds = mutableListOf<String>()
    val updatedFiles = mutableListOf<JDRFile>()

    override fun getNewUid(): String = "new-id"

    override suspend fun createFile(file: JDRFile): Result<Unit> = Result.success(Unit)

    override suspend fun updateFile(edit: FileEdit): Result<Unit> {
      val existing = file?.takeIf { it.id == edit.id } ?: JDRFile(id = edit.id)
      updatedFiles.add(existing.copy(name = edit.name, content = edit.content))
      updateFileGate?.await()
      return updateFailure?.let { Result.failure(it) } ?: Result.success(Unit)
    }

    override suspend fun deleteFile(fileId: String): Result<Unit> = Result.success(Unit)

    override suspend fun getFile(fileId: String): Result<JDRFile?> {
      requestedFileIds.add(fileId)
      if (gatedFileId == null || fileId == gatedFileId) getFileGate?.await()
      return getFailure?.let { Result.failure(it) }
          ?: Result.success(file?.takeIf { it.id == fileId })
    }

    override suspend fun removeItemFromGroup(
        itemId: String,
        groupId: String,
    ): Result<Unit> {
      TODO("Not yet implemented")
    }

    override fun getFileFlow(fileId: String): Flow<JDRFile?> = emptyFlow()

    override fun getPersonalFolderFiles(folderId: String, ownerId: String): Flow<List<JDRFile>> =
        emptyFlow()

    override fun getGroupFolderFiles(folderId: String, groupId: String): Flow<List<JDRFile>> =
        emptyFlow()

    override suspend fun addItemToGroup(
        itemId: String,
        groupId: String,
        folderId: String,
        isHidden: Boolean,
        includeContent: Boolean,
    ): Result<Unit> = error("Not used")

    override suspend fun setItemHidden(
        itemId: String,
        groupId: String,
        isHidden: Boolean,
    ): Result<Unit> = error("Not used")
  }
}
