// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.github.se.jdrnexus.ui.jdrfile

import android.os.Looper
import com.github.se.jdrnexus.codec.JDRFileCodecException
import com.github.se.jdrnexus.model.filerepresentation.CharacterAction
import com.github.se.jdrnexus.model.filerepresentation.CharacterFile
import com.github.se.jdrnexus.model.filerepresentation.CharacterHealthPoints
import com.github.se.jdrnexus.model.filerepresentation.CharacterStat
import com.github.se.jdrnexus.model.filerepresentation.LoreFile
import com.github.se.jdrnexus.model.filerepresentation.LoreSection
import com.github.se.jdrnexus.model.personalSpace.DocumentType
import com.github.se.jdrnexus.model.personalSpace.JDRFile
import com.github.se.jdrnexus.model.personalSpace.Placement
import com.github.se.jdrnexus.model.personalSpace.WorkspaceRepository
import kotlinx.coroutines.CancellationException
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
      """{"version":1,"type":"character","character":{"name":"Jean-claude","stats":[{"statID":"strength","name":"Strength","value":16},{"statID":"dexterity","name":"Dexterity","value":12}],"actions":[{"actionID":"sword_attack","name":"Sword Attack","description":"Attack"},{"actionID":"heavy_strike","name":"Heavy Strike","description":"Strike"}],"hp":{"current":18,"max":24}}}"""
  private val loreJson =
      """{"version":1,"type":"lore","title":"The Kingdom","sections":[{"sectionID":"history","title":"History","content":"Founded long ago."},{"sectionID":"king","title":"The King","content":"Rules today."}]}"""

  /**
   * idleMainLooper() lets Robolectric process the queued main-thread work so the coroutine started
   * by the ViewModel can finish.
   */
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
            CharacterHealthPoints(18, 24),
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
  fun loadErrorsAreExposedForMissingFilesRepositoryFailuresAndInvalidContent() {
    val missing = EditFileViewModel(FakeRepository())
    missing.loadFile("missing")
    idleMainLooper()
    assertEquals(EditFileViewModel.FILE_NOT_FOUND, missing.uiState.value.error)

    val repositoryFailure =
        EditFileViewModel(FakeRepository(getFailure = IllegalStateException("Offline")))
    repositoryFailure.loadFile("file")
    idleMainLooper()
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
  fun characterFieldsStatsAndActionsAreEditedByStableIds() {
    val viewModel =
        loadedViewModel(
            JDRFile(id = "character", type = DocumentType.CHARACTER, content = characterJson)
        )

    viewModel.updateCharacterName("New Hero")
    viewModel.onAddStatNameChange("Willpower")
    viewModel.onAddStatValueChange("17.5")
    viewModel.addCharacterStat()
    viewModel.updateCharacterStatName("strength", "Might")
    viewModel.updateCharacterStatValue("strength", 18.0)
    viewModel.removeCharacterStat("dexterity")
    viewModel.onAddActionNameChange("Healing")
    viewModel.onAddActionDescriptionChange("Restore health")
    viewModel.addCharacterAction()
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
  fun invalidCharacterEditsDoNotApplyAndExposeValidation() {
    val viewModel =
        loadedViewModel(
            JDRFile(id = "character", type = DocumentType.CHARACTER, content = characterJson)
        )
    val initial = viewModel.uiState.value.character

    viewModel.onAddStatNameChange("Luck")
    viewModel.onAddStatValueChange("NaN")
    viewModel.addCharacterStat()
    assertEquals(initial, viewModel.uiState.value.character)
    assertEquals(
        EditFileViewModel.STAT_VALUE_ERROR,
        viewModel.uiState.value.fieldErrors[EditFileField.NewCharacterStatValue],
    )

    viewModel.onAddStatNameChange(" ")
    viewModel.onAddStatValueChange("1.0")
    viewModel.addCharacterStat()
    assertEquals(
        EditFileViewModel.STAT_NAME_ERROR,
        viewModel.uiState.value.fieldErrors[EditFileField.NewCharacterStatName],
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

    viewModel.onAddActionNameChange(" ")
    viewModel.onAddActionDescriptionChange("Description")
    viewModel.addCharacterAction()
    assertEquals(
        EditFileViewModel.ACTION_NAME_ERROR,
        viewModel.uiState.value.fieldErrors[EditFileField.NewCharacterActionName],
    )
    viewModel.updateCharacterActionName("sword_attack", "")
    assertEquals(
        EditFileViewModel.ACTION_NAME_ERROR,
        viewModel.uiState.value.fieldErrors[EditFileField.CharacterActionName("sword_attack")],
    )
    viewModel.removeCharacterAction("missing")
    assertEquals(EditFileViewModel.ACTION_NOT_FOUND_ERROR, viewModel.uiState.value.error)
  }

  @Test
  fun fixingOneFieldErrorKeepsOtherFieldErrorsUntilTheyAreFixed() {
    val viewModel =
        loadedViewModel(
            JDRFile(id = "character", type = DocumentType.CHARACTER, content = characterJson)
        )
    val nameField = EditFileField.CharacterStatName("strength")
    val valueField = EditFileField.CharacterStatValue("strength")

    viewModel.updateCharacterStatName("strength", "")
    viewModel.updateCharacterStatValue("strength", Double.NaN)

    assertEquals(
        mapOf(
            nameField to EditFileViewModel.STAT_NAME_ERROR,
            valueField to EditFileViewModel.STAT_VALUE_ERROR,
        ),
        viewModel.uiState.value.fieldErrors,
    )

    viewModel.updateCharacterStatName("strength", "Might")

    assertEquals(
        mapOf(valueField to EditFileViewModel.STAT_VALUE_ERROR),
        viewModel.uiState.value.fieldErrors,
    )

    viewModel.updateCharacterStatValue("strength", 17.0)

    assertTrue(viewModel.uiState.value.fieldErrors.isEmpty())
  }

  @Test
  fun newEntryDraftValidationTracksEachFieldAsItChanges() {
    val viewModel =
        loadedViewModel(
            JDRFile(id = "character", type = DocumentType.CHARACTER, content = characterJson)
        )

    viewModel.onAddStatNameChange("")
    viewModel.onAddStatValueChange("")

    assertEquals(
        mapOf(
            EditFileField.NewCharacterStatName to EditFileViewModel.STAT_NAME_ERROR,
            EditFileField.NewCharacterStatValue to EditFileViewModel.STAT_VALUE_ERROR,
        ),
        viewModel.uiState.value.fieldErrors,
    )

    viewModel.onAddStatNameChange("Stealth")

    assertEquals(
        mapOf(EditFileField.NewCharacterStatValue to EditFileViewModel.STAT_VALUE_ERROR),
        viewModel.uiState.value.fieldErrors,
    )

    viewModel.onAddStatValueChange("invalid")
    assertEquals(
        mapOf(EditFileField.NewCharacterStatValue to EditFileViewModel.STAT_VALUE_ERROR),
        viewModel.uiState.value.fieldErrors,
    )

    viewModel.onAddStatValueChange("2.5")
    assertTrue(viewModel.uiState.value.fieldErrors.isEmpty())

    viewModel.onAddActionNameChange("")
    assertEquals(
        mapOf(EditFileField.NewCharacterActionName to EditFileViewModel.ACTION_NAME_ERROR),
        viewModel.uiState.value.fieldErrors,
    )

    viewModel.onAddActionNameChange("Guard")
    assertTrue(viewModel.uiState.value.fieldErrors.isEmpty())
  }

  @Test
  fun healthPointUpdatesValidateBoundsAndClampCurrentWhenMaximumDrops() {
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
  fun loadingInvalidHealthPointsExposesFieldErrorsAndPreventsSaving() {
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
  fun saveAndReopenPreservesStatsActionsAndRepositoryMetadata() {
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
    idleMainLooper()
    viewModel.updateCharacterName("Saved Hero")
    viewModel.onAddStatNameChange("Willpower")
    viewModel.onAddStatValueChange("17.0")
    viewModel.addCharacterStat()
    viewModel.onAddActionNameChange("Healing")
    viewModel.onAddActionDescriptionChange("Restore health")
    viewModel.addCharacterAction()

    viewModel.saveJDRfile()
    idleMainLooper()

    val savedFile = repository.updatedFiles.single()
    assertEquals(file.id, savedFile.id)
    assertEquals(file.ownerId, savedFile.ownerId)
    assertEquals(file.placements, savedFile.placements)
    val savedContent = JSONObject(savedFile.content).getJSONObject("character")
    assertEquals("Saved Hero", savedContent.getString("name"))
    val savedStat = savedContent.getJSONArray("stats").getJSONObject(2)
    assertEquals("Willpower", savedStat.getString("name"))
    assertEquals(17, savedStat.getInt("value"))
    val savedAction =
        JSONObject(savedFile.content)
            .getJSONObject("character")
            .getJSONArray("actions")
            .getJSONObject(2)
    assertEquals("Healing", savedAction.getString("name"))
    assertEquals("Restore health", savedAction.getString("description"))
    assertEquals(18, savedContent.getJSONObject("hp").getInt("current"))
    assertEquals(24, savedContent.getJSONObject("hp").getInt("max"))
    assertTrue(viewModel.uiState.value.isSaved)
    assertFalse(viewModel.uiState.value.isSaving)
    assertNull(viewModel.uiState.value.error)

    val reopened = loadedViewModel(savedFile, FakeRepository(savedFile))
    assertEquals(
        listOf("Strength", "Dexterity", "Willpower"),
        reopened.uiState.value.character!!.stats.map { it.name },
    )
    assertEquals(
        listOf("Sword Attack", "Heavy Strike", "Healing"),
        reopened.uiState.value.character!!.actions.map { it.name },
    )
    assertEquals(
        listOf("Attack", "Strike", "Restore health"),
        reopened.uiState.value.character!!.actions.map { it.description },
    )
  }

  @Test
  fun invalidUnaddedDraftErrorsDoNotPreventSaving() {
    val file = JDRFile(id = "character", type = DocumentType.CHARACTER, content = characterJson)
    val repository = FakeRepository(file)
    val viewModel = loadedViewModel(file, repository)
    viewModel.addCharacterStat()
    viewModel.addCharacterAction()

    viewModel.saveJDRfile()
    idleMainLooper()

    assertEquals(1, repository.updatedFiles.size)
    assertTrue(viewModel.uiState.value.isSaved)
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
    idleMainLooper()
    assertEquals("Write denied", failing.uiState.value.error)
    assertFalse(failing.uiState.value.isSaved)
    assertFalse(failing.uiState.value.isSaving)
  }

  @Test
  fun cancelledSaveResetsSavingFlagWithoutReportingAnError() {
    val file = JDRFile(id = "character", type = DocumentType.CHARACTER, content = characterJson)
    val repository = FakeRepository(file, updateFailure = CancellationException("Save cancelled"))
    val viewModel = loadedViewModel(file, repository)

    viewModel.updateCharacterName("Updated")
    viewModel.saveJDRfile()
    idleMainLooper()

    assertFalse(viewModel.uiState.value.isSaving)
    assertFalse(viewModel.uiState.value.isSaved)
    assertNull(viewModel.uiState.value.error)
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

    assertEquals(EditFileViewModel.LOAD_BEFORE_SAVE_ERROR, viewModel.uiState.value.error)
  }

  @Test
  fun removingStatWithErrorsClearsErrorsAndAllowsSaving() {
    val file = JDRFile(id = "character", type = DocumentType.CHARACTER, content = characterJson)
    val repository = FakeRepository(file)
    val viewModel = loadedViewModel(file, repository)
    viewModel.updateCharacterStatName("dexterity", "")
    viewModel.updateCharacterStatValue("dexterity", Double.NaN)
    assertEquals(
        mapOf(
            EditFileField.CharacterStatName("dexterity") to EditFileViewModel.STAT_NAME_ERROR,
            EditFileField.CharacterStatValue("dexterity") to EditFileViewModel.STAT_VALUE_ERROR,
        ),
        viewModel.uiState.value.fieldErrors,
    )

    viewModel.removeCharacterStat("dexterity")

    assertEquals(emptyMap<EditFileField, String>(), viewModel.uiState.value.fieldErrors)
    viewModel.saveJDRfile()
    idleMainLooper()
    assertTrue(viewModel.uiState.value.isSaved)
    val reopened = loadedViewModel(repository.updatedFiles.single())
    assertEquals(
        listOf(CharacterStat("strength", "Strength", 16.0)),
        reopened.uiState.value.character?.stats,
    )
  }

  @Test
  fun removingActionWithErrorClearsErrorsAndAllowsSaving() {
    val file = JDRFile(id = "character", type = DocumentType.CHARACTER, content = characterJson)
    val repository = FakeRepository(file)
    val viewModel = loadedViewModel(file, repository)
    viewModel.updateCharacterActionName("sword_attack", "")
    assertEquals(
        mapOf(
            EditFileField.CharacterActionName("sword_attack") to EditFileViewModel.ACTION_NAME_ERROR
        ),
        viewModel.uiState.value.fieldErrors,
    )

    viewModel.removeCharacterAction("sword_attack")

    assertEquals(emptyMap<EditFileField, String>(), viewModel.uiState.value.fieldErrors)
    viewModel.saveJDRfile()
    idleMainLooper()
    assertTrue(viewModel.uiState.value.isSaved)
    val reopened = loadedViewModel(repository.updatedFiles.single())
    assertEquals(
        listOf(CharacterAction("heavy_strike", "Heavy Strike", "Strike")),
        reopened.uiState.value.character?.actions,
    )
  }

  @Test
  fun removingLoreSectionWithErrorClearsErrorsAndAllowsSaving() {
    val file = JDRFile(id = "lore", type = DocumentType.TEXT, content = loreJson)
    val repository = FakeRepository(file)
    val viewModel = loadedViewModel(file, repository)
    viewModel.updateLoreSectionTitle("history", "")
    assertEquals(
        mapOf(EditFileField.LoreSectionTitle("history") to EditFileViewModel.SECTION_TITLE_ERROR),
        viewModel.uiState.value.fieldErrors,
    )

    viewModel.removeLoreSection("history")

    assertEquals(emptyMap<EditFileField, String>(), viewModel.uiState.value.fieldErrors)
    viewModel.saveJDRfile()
    idleMainLooper()
    assertTrue(viewModel.uiState.value.isSaved)
    val reopened = loadedViewModel(repository.updatedFiles.single())
    assertEquals(
        listOf(LoreSection("king", "The King", "Rules today.")),
        reopened.uiState.value.lore?.sections,
    )
  }

  @Test
  fun invalidStatDraftRetainsInputsAndDoesNotAddStat() {
    val viewModel =
        loadedViewModel(
            JDRFile(id = "character", type = DocumentType.CHARACTER, content = characterJson)
        )
    val initial = viewModel.uiState.value.character

    for (value in listOf("", "invalid", "NaN", "Infinity")) {
      viewModel.onAddStatNameChange(" Luck ")
      viewModel.onAddStatValueChange(value)
      viewModel.addCharacterStat()

      val state = viewModel.uiState.value
      assertEquals(" Luck ", state.newStatName)
      assertEquals(value, state.newStatValue)
      assertEquals(initial, state.character)
      assertEquals(
          mapOf(EditFileField.NewCharacterStatValue to EditFileViewModel.STAT_VALUE_ERROR),
          state.fieldErrors,
      )
    }
  }

  @Test
  fun addingStatTrimsNameAndClearsOnlyStatDraftAndErrors() {
    val viewModel =
        loadedViewModel(
            JDRFile(id = "character", type = DocumentType.CHARACTER, content = characterJson)
        )
    viewModel.onAddStatNameChange("")
    viewModel.onAddStatValueChange("invalid")
    viewModel.onAddActionNameChange(" ")
    viewModel.onAddActionDescriptionChange("Keep this description")
    viewModel.onAddStatNameChange(" Luck ")
    viewModel.onAddStatValueChange("2.5")

    viewModel.addCharacterStat()

    val state = viewModel.uiState.value
    assertEquals(listOf("Strength", "Dexterity", "Luck"), state.character?.stats?.map { it.name })
    assertEquals(listOf(16.0, 12.0, 2.5), state.character?.stats?.map { it.value })
    assertEquals("", state.newStatName)
    assertEquals("", state.newStatValue)
    assertEquals(" ", state.newActionName)
    assertEquals("Keep this description", state.newActionDescription)
    assertEquals(
        mapOf(EditFileField.NewCharacterActionName to EditFileViewModel.ACTION_NAME_ERROR),
        state.fieldErrors,
    )
  }

  @Test
  fun invalidActionDraftRetainsInputsAndDoesNotAddAction() {
    val viewModel =
        loadedViewModel(
            JDRFile(id = "character", type = DocumentType.CHARACTER, content = characterJson)
        )
    val initial = viewModel.uiState.value.character
    viewModel.onAddActionNameChange(" ")
    viewModel.onAddActionDescriptionChange("Restore health")

    viewModel.addCharacterAction()

    val state = viewModel.uiState.value
    assertEquals(" ", state.newActionName)
    assertEquals("Restore health", state.newActionDescription)
    assertEquals(initial, state.character)
    assertEquals(
        mapOf(EditFileField.NewCharacterActionName to EditFileViewModel.ACTION_NAME_ERROR),
        state.fieldErrors,
    )
  }

  @Test
  fun addingActionTrimsNameAndClearsOnlyActionDraftAndErrors() {
    val viewModel =
        loadedViewModel(
            JDRFile(id = "character", type = DocumentType.CHARACTER, content = characterJson)
        )
    viewModel.onAddActionNameChange("")
    viewModel.onAddActionDescriptionChange(" Restore health ")
    viewModel.onAddStatNameChange("Luck")
    viewModel.onAddStatValueChange("invalid")
    viewModel.onAddActionNameChange(" Healing ")

    viewModel.addCharacterAction()

    val state = viewModel.uiState.value
    assertEquals(
        listOf("Sword Attack", "Heavy Strike", "Healing"),
        state.character?.actions?.map { it.name },
    )
    assertEquals(
        listOf("Attack", "Strike", " Restore health "),
        state.character?.actions?.map { it.description },
    )
    assertEquals("", state.newActionName)
    assertEquals("", state.newActionDescription)
    assertEquals("Luck", state.newStatName)
    assertEquals("invalid", state.newStatValue)
    assertEquals(
        mapOf(EditFileField.NewCharacterStatValue to EditFileViewModel.STAT_VALUE_ERROR),
        state.fieldErrors,
    )
  }

  @Test
  fun loadingAnotherFileClearsAllDraftInputsAndErrors() {
    val file = JDRFile(id = "character", type = DocumentType.CHARACTER, content = characterJson)
    val viewModel = loadedViewModel(file)
    viewModel.onAddStatNameChange("Luck")
    viewModel.onAddStatValueChange("invalid")
    viewModel.onAddActionNameChange(" ")
    viewModel.onAddActionDescriptionChange("Restore health")

    viewModel.loadFile("missing")
    idleMainLooper()

    val state = viewModel.uiState.value
    assertEquals("", state.newStatName)
    assertEquals("", state.newStatValue)
    assertEquals("", state.newActionName)
    assertEquals("", state.newActionDescription)
    assertEquals(emptyMap<EditFileField, String>(), state.fieldErrors)
    assertEquals(EditFileViewModel.FILE_NOT_FOUND, state.error)
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

    override suspend fun removeItemFromGroup(
        itemId: String,
        groupId: String,
        parentFolderId: String,
    ): Result<Unit> {
      TODO("Not yet implemented")
    }

    override fun getFileFlow(fileId: String): Flow<JDRFile?> = emptyFlow()

    override fun getPersonalRootFiles(ownerId: String): Flow<List<JDRFile>> = emptyFlow()

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
