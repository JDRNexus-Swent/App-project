// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.github.se.jdrnexus.ui.gameManagement

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.se.jdrnexus.model.personalSpace.CampaignGroup
import com.github.se.jdrnexus.model.personalSpace.GroupRepository
import com.github.se.jdrnexus.model.repository.AuthError
import com.github.se.jdrnexus.model.repository.AuthRepository
import com.github.se.jdrnexus.model.repository.AuthResult
import com.github.se.jdrnexus.model.repository.AuthUser
import com.github.se.jdrnexus.ui.theme.SampleAppTheme
import com.github.se.jdrnexus.viewmodel.GameManagementUiState
import com.github.se.jdrnexus.viewmodel.GameManagementViewModel
import com.github.se.jdrnexus.viewmodel.GroupActionState
import com.github.se.jdrnexus.viewmodel.GroupLoadState
import com.github.se.jdrnexus.viewmodel.GroupRole
import com.github.se.jdrnexus.viewmodel.UserGroup
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GameManagementScreenTest {
  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun gameMasterSeesManageAndDisabledInviteActions() {
    setContent(
        uiState =
            stateFor(
                CampaignGroup(
                    id = "gm-group",
                    name = "The Sunken Crown",
                    gameMasterId = "user-1",
                    participantIds = listOf("user-1", "player-1"),
                )
            )
    )

    composeTestRule.onNodeWithText("Manage group").assertIsDisplayed()
    composeTestRule.onNodeWithText("Invite · Coming soon").assertIsDisplayed().assertIsNotEnabled()
  }

  @Test
  fun gameMasterManageActionReceivesTheSelectedGroupId() {
    var managedGroupId = ""
    setContent(
        uiState =
            stateFor(
                CampaignGroup(
                    id = "managed-group",
                    name = "The Sunken Crown",
                    gameMasterId = "user-1",
                )
            ),
        onManageGroup = { managedGroupId = it },
    )

    composeTestRule.onNodeWithText("Manage group").performClick()

    assertEquals("managed-group", managedGroupId)
  }

  @Test
  fun playerOnlySeesWorkspaceAction() {
    setContent(
        uiState =
            stateFor(
                CampaignGroup(
                    id = "player-group",
                    name = "Clockwork Dominion",
                    gameMasterId = "another-user",
                    participantIds = listOf("user-1", "another-user"),
                )
            )
    )

    composeTestRule.onNodeWithText("Enter workspace").assertIsDisplayed().assertIsEnabled()
    composeTestRule.onAllNodesWithText("Manage group").assertCountEquals(0)
    composeTestRule.onAllNodesWithText("Invite · Coming soon").assertCountEquals(0)
  }

  @Test
  fun groupCardShowsItsCampaignDetailsAndMembershipCount() {
    setContent(
        uiState =
            stateFor(
                CampaignGroup(
                    id = "campaign-1",
                    name = "The Sunken Crown",
                    description = "Pathfinder 2e",
                    gameMasterId = "user-1",
                    participantIds = listOf("user-1", "player-1"),
                )
            )
    )

    composeTestRule.onNodeWithText("The Sunken Crown").assertIsDisplayed()
    composeTestRule.onNodeWithText("Pathfinder 2e · 2 members").assertIsDisplayed()
    composeTestRule.onNodeWithText("1 group · Your next chapter, together.").assertIsDisplayed()
  }

  @Test
  fun multipleGroupsShowPluralCountAndFallbackCampaignDetails() {
    setContent(
        uiState =
            stateFor(
                CampaignGroup(
                    id = "campaign-1",
                    name = "The Sunken Crown",
                    gameMasterId = "user-1",
                    participantIds = listOf("user-1"),
                ),
                CampaignGroup(
                    id = "campaign-2",
                    name = "Clockwork Dominion",
                    gameMasterId = "another-user",
                    participantIds = listOf("user-1", "another-user"),
                ),
            )
    )

    composeTestRule.onNodeWithText("2 groups · Your next chapter, together.").assertIsDisplayed()
    composeTestRule.onNodeWithText("Campaign system not set · 1 member").assertIsDisplayed()
    composeTestRule.onNode(hasScrollAction()).performScrollToNode(hasText("Player"))
    composeTestRule.onNodeWithText("Player").assertIsDisplayed()
  }

  @Test
  fun enterWorkspacePassesSelectedGroupIdToNavigation() {
    var navigatedTo = ""
    setContent(
        uiState =
            stateFor(
                CampaignGroup(
                    id = "specific-group",
                    name = "The Sunken Crown",
                    gameMasterId = "user-1",
                )
            ),
        onEnterWorkspace = { navigatedTo = it },
    )

    composeTestRule.onNodeWithText("Enter workspace").performClick()

    assertEquals("specific-group", navigatedTo)
  }

  @Test
  fun loadingStateShowsProgressInsteadOfEmptyState() {
    setContent(uiState = GameManagementUiState(loadState = GroupLoadState.Loading))

    composeTestRule.onNodeWithText("Loading your groups...").assertIsDisplayed()
    composeTestRule.onAllNodesWithText("Your party awaits").assertCountEquals(0)
  }

  @Test
  fun loadErrorShowsRepositoryMessageAndRetryInvokesCallback() {
    var retries = 0
    setContent(
        uiState = GameManagementUiState(loadState = GroupLoadState.Error("No connection")),
        onRetryLoad = { retries++ },
    )

    composeTestRule.onNodeWithText("Couldn't load groups").assertIsDisplayed()
    composeTestRule.onNodeWithText("No connection").assertIsDisplayed()
    composeTestRule.onNodeWithText("Retry loading").performClick()

    assertEquals(1, retries)
  }

  @Test
  fun emptyGroupsShowCreateFirstGroupCallToAction() {
    var createClicks = 0
    setContent(uiState = stateFor(), onCreateGroupClick = { createClicks++ })

    composeTestRule.onNodeWithText("Your party awaits").assertIsDisplayed()
    composeTestRule.onNodeWithText("Create your first group").performClick()

    assertEquals(1, createClicks)
  }

  @Test
  fun successWithNoGroupsShowsCreateFirstGroupCallToAction() {
    var createClicks = 0
    setContent(
        uiState = GameManagementUiState(loadState = GroupLoadState.Success),
        onCreateGroupClick = { createClicks++ },
    )

    composeTestRule.onNodeWithText("Your party awaits").assertIsDisplayed()
    composeTestRule.onNodeWithText("Create your first group").performClick()

    assertEquals(1, createClicks)
  }

  @Test
  fun headerCreateActionInvokesDialogCallback() {
    var createClicks = 0
    setContent(uiState = stateFor(), onCreateGroupClick = { createClicks++ })

    composeTestRule
        .onNodeWithTag(GameManagementTestTags.CREATE_GROUP_BUTTON)
        .assertIsDisplayed()
        .performClick()

    assertEquals(1, createClicks)
  }

  @Test
  fun createDialogPassesEditedNameAndSubmissionToCallbacks() {
    var changedName = ""
    var createClicks = 0
    setContent(
        uiState = stateFor().copy(isCreateDialogOpen = true, groupName = "New campaign"),
        onGroupNameChanged = { changedName = it },
        onCreateGroup = { createClicks++ },
    )

    composeTestRule.onNodeWithTag(GameManagementTestTags.CREATE_GROUP_DIALOG).assertIsDisplayed()
    val nameField = composeTestRule.onNodeWithTag(GameManagementTestTags.GROUP_NAME_FIELD)
    nameField.performTextClearance()
    nameField.performTextInput("New campaign II")
    composeTestRule.onNodeWithTag(GameManagementTestTags.CREATE_GROUP_CONFIRM).performClick()

    assertEquals("New campaign II", changedName)
    assertEquals(1, createClicks)
  }

  @Test
  fun emptyCreateGroupNameShowsValidationAndCanBeDismissed() {
    var dismissClicks = 0
    setContent(
        uiState = stateFor().copy(isCreateDialogOpen = true),
        onDismissCreateDialog = { dismissClicks++ },
    )

    composeTestRule.onNodeWithTag(GameManagementTestTags.CREATE_GROUP_CONFIRM).assertIsNotEnabled()
    val nameField = composeTestRule.onNodeWithTag(GameManagementTestTags.GROUP_NAME_FIELD)
    nameField.performTextInput("Campaign")
    nameField.performTextClearance()

    composeTestRule.onNodeWithText("A group name is required.").assertIsDisplayed()
    composeTestRule.onNodeWithTag(GameManagementTestTags.CREATE_GROUP_CONFIRM).assertIsNotEnabled()
    composeTestRule.onNodeWithText("Cancel").performClick()

    assertEquals(1, dismissClicks)
  }

  @Test
  fun createDialogShowsCreationFailureAndKeepsRetryAvailable() {
    var createClicks = 0
    setContent(
        uiState =
            stateFor()
                .copy(
                    isCreateDialogOpen = true,
                    groupName = "Keep this name",
                    createState = GroupActionState.Error("Offline"),
                ),
        onCreateGroup = { createClicks++ },
    )

    composeTestRule.onNodeWithText("Couldn't create your group.").assertIsDisplayed()
    composeTestRule.onNodeWithText("Retry creation").performClick()

    assertEquals(1, createClicks)
  }

  @Test
  fun createDialogDisablesSubmissionAndDismissalWhileCreating() {
    var createClicks = 0
    var dismissClicks = 0
    setContent(
        uiState =
            stateFor()
                .copy(
                    isCreateDialogOpen = true,
                    groupName = "In progress",
                    createState = GroupActionState.Loading,
                ),
        onCreateGroup = { createClicks++ },
        onDismissCreateDialog = { dismissClicks++ },
    )

    composeTestRule.onNodeWithTag(GameManagementTestTags.CREATE_GROUP_CONFIRM).assertIsNotEnabled()
    composeTestRule.onNodeWithText("Cancel").assertIsNotEnabled()
    composeTestRule.onNodeWithText("Creating your group. Please wait...").assertIsDisplayed()

    assertEquals(0, createClicks)
    assertEquals(0, dismissClicks)
  }

  @Test
  fun successBannerDismissActionInvokesCallback() {
    var dismissClicks = 0
    setContent(
        uiState =
            stateFor()
                .copy(
                    createState =
                        GroupActionState.Success(
                            groupId = "new-campaign",
                            message = "New campaign was created.",
                        )
                ),
        onDismissSuccess = { dismissClicks++ },
    )

    composeTestRule.onNodeWithText("New campaign was created.").assertIsDisplayed()
    composeTestRule.onNodeWithText("Dismiss").performClick()

    assertEquals(1, dismissClicks)
  }

  @Test
  fun bottomNavigationPassesSelectedTabToCallback() {
    var selectedTab = ""
    setContent(uiState = stateFor(), onTabSelected = { selectedTab = it })

    composeTestRule.onNodeWithText("Files").performClick()

    assertEquals("Files", selectedTab)
  }

  @Test
  fun darkThemeRendersScreenContent() {
    setContent(uiState = stateFor(), darkTheme = true)

    composeTestRule.onNodeWithText("Your groups").assertIsDisplayed()
    composeTestRule.onNodeWithText("Your party awaits").assertIsDisplayed()
  }

  @Test
  fun screenConnectsViewModelAndNavigationActions() {
    val group =
        CampaignGroup(
            id = "campaign-1",
            name = "The Sunken Crown",
            gameMasterId = "user-1",
            participantIds = listOf("user-1"),
        )
    val groupRepository = ScreenGroupRepository(listOf(group))
    val viewModel =
        GameManagementViewModel(
            groupRepository,
            ScreenAuthRepository(),
        )
    var enteredGroupId = ""
    var managedGroupId = ""
    var selectedTab = ""

    composeTestRule.setContent {
      SampleAppTheme(darkTheme = false) {
        GameManagementScreen(
            onEnterWorkspace = { enteredGroupId = it },
            onManageGroup = { managedGroupId = it },
            onTabSelected = { selectedTab = it },
            gameManagementViewModel = viewModel,
        )
      }
    }

    composeTestRule.waitUntil(5_000) { viewModel.uiState.value.loadState == GroupLoadState.Success }
    composeTestRule.onNodeWithText("The Sunken Crown").assertIsDisplayed()
    composeTestRule.onNodeWithText("Enter workspace").performClick()
    composeTestRule.onNodeWithText("Manage group").performClick()
    composeTestRule.onNodeWithText("Files").performClick()
    assertEquals("campaign-1", enteredGroupId)
    assertEquals("campaign-1", managedGroupId)
    assertEquals("Files", selectedTab)

    composeTestRule.mainClock.autoAdvance = false
    composeTestRule.onNodeWithTag(GameManagementTestTags.CREATE_GROUP_BUTTON).performClick()
    composeTestRule.mainClock.advanceTimeBy(100)
    composeTestRule.onNodeWithTag(GameManagementTestTags.CREATE_GROUP_DIALOG).assertIsDisplayed()
    val groupNameField = composeTestRule.onNodeWithTag(GameManagementTestTags.GROUP_NAME_FIELD)
    groupNameField.performTextInput("New campaign")
    composeTestRule.mainClock.advanceTimeBy(100)
    composeTestRule.onNodeWithTag(GameManagementTestTags.CREATE_GROUP_CONFIRM).performClick()
    composeTestRule.waitUntil(5_000) { viewModel.uiState.value.successMessage.isNotEmpty() }
    composeTestRule.mainClock.advanceTimeBy(100)
    composeTestRule.onNodeWithText("New campaign was created.").assertIsDisplayed()
    assertEquals("New campaign", groupRepository.createdGroups.single().name)
    composeTestRule.onNodeWithText("Dismiss").performClick()
    composeTestRule.mainClock.advanceTimeBy(100)
    composeTestRule.onAllNodesWithText("New campaign was created.").assertCountEquals(0)
    composeTestRule.mainClock.autoAdvance = true
  }

  private fun setContent(
      uiState: GameManagementUiState,
      onEnterWorkspace: (String) -> Unit = {},
      onManageGroup: (String) -> Unit = {},
      onCreateGroupClick: () -> Unit = {},
      onGroupNameChanged: (String) -> Unit = {},
      onCreateGroup: () -> Unit = {},
      onDismissCreateDialog: () -> Unit = {},
      onRetryLoad: () -> Unit = {},
      onDismissSuccess: () -> Unit = {},
      onTabSelected: (String) -> Unit = {},
      darkTheme: Boolean = false,
  ) {
    composeTestRule.setContent {
      SampleAppTheme(darkTheme = darkTheme) {
        val currentUiState = remember { mutableStateOf(uiState) }
        GameManagementScreenContent(
            uiState = currentUiState.value,
            onCreateGroupClick = onCreateGroupClick,
            onGroupNameChanged = { name ->
              currentUiState.value = currentUiState.value.copy(groupName = name)
              onGroupNameChanged(name)
            },
            onCreateGroup = onCreateGroup,
            onDismissCreateDialog = onDismissCreateDialog,
            onRetryLoad = onRetryLoad,
            onDismissSuccess = onDismissSuccess,
            onEnterWorkspace = onEnterWorkspace,
            onManageGroup = onManageGroup,
            onInvite = {},
            onTabSelected = onTabSelected,
            darkTheme = darkTheme,
        )
      }
    }
  }

  private fun stateFor(vararg groups: CampaignGroup) =
      GameManagementUiState(
          groups =
              groups.map { group ->
                UserGroup(
                    group = group,
                    role =
                        if (group.gameMasterId == "user-1") {
                          GroupRole.GAME_MASTER
                        } else {
                          GroupRole.PLAYER
                        },
                )
              },
          loadState = if (groups.isEmpty()) GroupLoadState.Empty else GroupLoadState.Success,
      )

  private class ScreenAuthRepository : AuthRepository {
    override val authState: Flow<AuthUser?> = flowOf(AuthUser("user-1", null, null))

    override suspend fun signUpWithEmail(
        email: String,
        password: String,
        username: String,
    ): AuthResult<AuthUser> = AuthResult.Failure(AuthError.UNKNOWN)

    override suspend fun signInWithEmail(email: String, password: String): AuthResult<AuthUser> =
        AuthResult.Failure(AuthError.UNKNOWN)

    override suspend fun signInWithGoogle(idToken: String): AuthResult<AuthUser> =
        AuthResult.Failure(AuthError.UNKNOWN)

    override suspend fun signOut(): AuthResult<Unit> = AuthResult.Success(Unit)
  }

  private class ScreenGroupRepository(private val groups: List<CampaignGroup>) :
      GroupRepository by unusedGroupRepository {
    val createdGroups = mutableListOf<CampaignGroup>()

    override fun getNewGroupId(): String = "new-campaign"

    override suspend fun createGroup(group: CampaignGroup): Result<Unit> {
      createdGroups += group
      return Result.success(Unit)
    }

    override fun getUserGroups(userId: String): Flow<List<CampaignGroup>> = flowOf(groups)
  }

  private companion object {
    val unusedGroupRepository =
        object : GroupRepository {
          override fun getNewGroupId(): String = error("Not used")

          override suspend fun createGroup(group: CampaignGroup): Result<Unit> = error("Not used")

          override suspend fun updateGroup(group: CampaignGroup): Result<Unit> = error("Not used")

          override suspend fun deleteGroup(groupId: String): Result<Unit> = error("Not used")

          override suspend fun getGroup(groupId: String): Result<CampaignGroup> = error("Not used")

          override suspend fun addUserToGroup(
              groupId: String,
              userId: String,
          ): Result<Unit> = error("Not used")

          override suspend fun removeUserFromGroup(
              groupId: String,
              userId: String,
          ): Result<Unit> = error("Not used")

          override fun getGroupFlow(groupId: String): Flow<CampaignGroup?> = error("Not used")

          override fun getUserGroups(userId: String): Flow<List<CampaignGroup>> = error("Not used")
        }
  }
}
