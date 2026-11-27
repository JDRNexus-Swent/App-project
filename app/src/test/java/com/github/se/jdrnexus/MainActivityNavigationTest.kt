// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.se.jdrnexus.codec.JDRFileJsonCodec
import com.github.se.jdrnexus.model.filerepresentation.CharacterFile
import com.github.se.jdrnexus.model.filerepresentation.CharacterHealthPoints
import com.github.se.jdrnexus.model.filerepresentation.CharacterStat
import com.github.se.jdrnexus.model.map.MapGame
import com.github.se.jdrnexus.model.map.MapLocation
import com.github.se.jdrnexus.model.map.MapRepository
import com.github.se.jdrnexus.model.map.MapRepositoryProvider
import com.github.se.jdrnexus.model.personalSpace.CampaignGroup
import com.github.se.jdrnexus.model.personalSpace.DocumentType
import com.github.se.jdrnexus.model.personalSpace.FileEdit
import com.github.se.jdrnexus.model.personalSpace.GroupRepository
import com.github.se.jdrnexus.model.personalSpace.JDRFile
import com.github.se.jdrnexus.model.personalSpace.Placement
import com.github.se.jdrnexus.model.personalSpace.WorkspaceRepository
import com.github.se.jdrnexus.model.profile.ProfileRepositoryProvider
import com.github.se.jdrnexus.model.repository.AuthError
import com.github.se.jdrnexus.model.repository.AuthRepository
import com.github.se.jdrnexus.model.repository.AuthResult
import com.github.se.jdrnexus.model.repository.AuthUser
import com.github.se.jdrnexus.resources.C
import com.github.se.jdrnexus.ui.authentication.AuthStatus
import com.github.se.jdrnexus.ui.authentication.AuthViewModel
import com.github.se.jdrnexus.ui.dice.FlatDieView
import com.github.se.jdrnexus.ui.dice.LocalDieView
import com.github.se.jdrnexus.ui.edition.EditFileCharacterTestTags
import com.github.se.jdrnexus.ui.edition.EditFileLoreTestTags
import com.github.se.jdrnexus.ui.gameManagement.GameManagementTestTags
import com.github.se.jdrnexus.ui.home.TestTags
import com.github.se.jdrnexus.ui.jdrfile.EditFileViewModel
import com.github.se.jdrnexus.ui.search.DocumentSearchInterfaceVM
import com.github.se.jdrnexus.ui.search.DocumentSearchResult
import com.github.se.jdrnexus.ui.search.DocumentSearchStatus
import com.github.se.jdrnexus.ui.search.DocumentSearchUiState
import com.github.se.jdrnexus.ui.search.DocumentSearchViewModel
import com.github.se.jdrnexus.ui.soundboard.SoundboardUiState
import com.github.se.jdrnexus.ui.soundboard.SoundboardViewModelInterface
import com.github.se.jdrnexus.utils.FakeConnectivityRepository
import com.github.se.jdrnexus.utils.FakeNotificationViewModel
import com.github.se.jdrnexus.utils.FakeProfileRepository
import com.github.se.jdrnexus.utils.LOADED_PROFILE_STATE
import com.github.se.jdrnexus.utils.NavigationGameSessionRepository
import com.github.se.jdrnexus.utils.NavigationGroupRepository
import com.github.se.jdrnexus.utils.NavigationWorkspaceRepository
import com.github.se.jdrnexus.utils.RecordingProfileViewModel
import com.github.se.jdrnexus.utils.tapInOneFrame
import com.github.se.jdrnexus.viewmodel.GameManagementViewModel
import com.github.se.jdrnexus.viewmodel.PersonalSpaceViewModel
import com.google.firebase.FirebaseApp
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityNavigationTest {
  @get:Rule val composeTestRule = createComposeRule()

  // Every app built by showApp gets this factory; only the soundboard tests open that route.
  private val soundboardViewModels = mutableListOf<LifetimeSoundboardViewModel>()
  private val soundboardFactory = viewModelFactory {
    initializer<ViewModel> { LifetimeSoundboardViewModel().also { soundboardViewModels += it } }
  }

  @Test
  fun startsAtSignInAndReturnsFromSignUp() {
    showApp()

    scrollToTag(SIGN_IN_SCROLL_CONTAINER, C.Tag.sign_in_sign_up)
    composeTestRule.onNodeWithTag(C.Tag.sign_in_sign_up).assertIsDisplayed().performClick()
    scrollToTag(SIGN_UP_SCROLL_CONTAINER, C.Tag.sign_up_sign_in)
    composeTestRule.onNodeWithTag(C.Tag.sign_up_sign_in).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.sign_up_sign_in).performClick()
    scrollToTag(SIGN_IN_SCROLL_CONTAINER, C.Tag.sign_in_sign_up)
    composeTestRule.onNodeWithTag(C.Tag.sign_in_sign_up).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.sign_up_sign_in).assertIsNotDisplayed()
  }

  @Test
  fun signedOutLaunchOpensSignIn() {
    showApp(FakeAuthRepository(initialUser = null), groupRepository = groupRepository())

    composeTestRule.onNodeWithTag(C.Tag.sign_in_submit).assertIsDisplayed()
    composeTestRule.onAllNodesWithTag(TestTags.TITLE).assertCountEquals(0)
    composeTestRule.onAllNodesWithTag(C.Tag.session_loading).assertCountEquals(0)
  }

  @Test
  fun signedInLaunchOpensHomeWithoutSignIn() {
    showApp(
        FakeAuthRepository(initialUser = FakeAuthRepository.TEST_USER),
        groupRepository = groupRepository(),
    )

    composeTestRule.onNodeWithTag(TestTags.TITLE).assertIsDisplayed()
    composeTestRule.onAllNodesWithTag(C.Tag.sign_in_submit).assertCountEquals(0)
  }

  @Test
  fun launchShowsLoadingUntilTheSessionIsKnownAndNeverShowsSignIn() {
    val authState = MutableSharedFlow<AuthUser?>(replay = 1)
    showApp(FakeAuthRepository(authState), groupRepository = groupRepository())

    composeTestRule.onNodeWithTag(C.Tag.session_loading).assertIsDisplayed()
    composeTestRule.onAllNodesWithTag(C.Tag.sign_in_submit).assertCountEquals(0)

    composeTestRule.runOnIdle { authState.tryEmit(FakeAuthRepository.TEST_USER) }
    waitForTag(TestTags.TITLE)

    composeTestRule.onAllNodesWithTag(C.Tag.session_loading).assertCountEquals(0)
    composeTestRule.onAllNodesWithTag(C.Tag.sign_in_submit).assertCountEquals(0)
  }

  @Test
  fun sessionEndingOnAnotherScreenReturnsToSignIn() {
    val repository = FakeAuthRepository(initialUser = FakeAuthRepository.TEST_USER)
    showApp(repository, groupRepository = groupRepository())
    composeTestRule.onNodeWithTag(TestTags.DICEBUTTON).performClick()
    waitForTag(C.Tag.dice_offline_screen)

    composeTestRule.runOnIdle { repository.endSession() }
    waitForTag(C.Tag.sign_in_submit)
    // SignIn must be alone on the back stack: Back must not lead to Dice or Home.
    Espresso.pressBackUnconditionally()
    composeTestRule.waitForIdle()

    composeTestRule.onAllNodesWithTag(C.Tag.dice_offline_screen).assertCountEquals(0)
    composeTestRule.onAllNodesWithTag(TestTags.TITLE).assertCountEquals(0)
  }

  @Test
  fun sessionEndedBeforeTheAppIsShownStillLeadsToSignIn() {
    // A session restored at launch and ended before the app composes, as when a test signs out in
    // @Before after the activity started: the app must still leave Home.
    val repository = FakeAuthRepository(initialUser = FakeAuthRepository.TEST_USER)
    val viewModel = AuthViewModel(repository)
    composeTestRule.runOnIdle { repository.endSession() }

    showApp(repository, groupRepository = groupRepository(), authViewModel = viewModel)
    waitForTag(C.Tag.sign_in_submit)

    composeTestRule.onAllNodesWithTag(TestTags.TITLE).assertCountEquals(0)
    assertEquals(false, viewModel.sessionEnded.value)
  }

  @Test
  fun logoutLeavesNoCredentialsInTheSignInForm() {
    showApp(groupRepository = groupRepository())
    composeTestRule.onNodeWithTag(C.Tag.sign_in_email).performTextInput("hero@example.com")
    composeTestRule.onNodeWithTag(C.Tag.sign_in_password).performTextInput("secret1")
    composeTestRule.onNodeWithTag(C.Tag.sign_in_submit).performClick()
    waitForTag(TestTags.LOGOUT)

    composeTestRule.onNodeWithTag(TestTags.LOGOUT).performClick()
    waitForTag(C.Tag.sign_in_email)

    composeTestRule.onNodeWithTag(C.Tag.sign_in_email).assert(hasEditableText(""))
    composeTestRule.onNodeWithTag(C.Tag.sign_in_password).assert(hasEditableText(""))
  }

  @Test
  fun offlineBannerFollowsTheConnectionOnEveryScreenWithATopBar() {
    val connectivity = FakeConnectivityRepository(isOnline = false)
    showApp(groupRepository = groupRepository(), connectivityRepository = connectivity)
    composeTestRule.onNodeWithTag(C.Tag.sign_in_email).performTextInput("hero@example.com")
    composeTestRule.onNodeWithTag(C.Tag.sign_in_password).performTextInput("secret1")
    composeTestRule.onNodeWithTag(C.Tag.sign_in_submit).performClick()
    waitForTag(TestTags.TITLE)

    composeTestRule.onNodeWithTag(C.Tag.offline_banner, useUnmergedTree = true).assertIsDisplayed()

    composeTestRule.runOnIdle { connectivity.online.value = true }
    composeTestRule.onNodeWithTag(C.Tag.offline_banner, useUnmergedTree = true).assertDoesNotExist()

    composeTestRule.onNodeWithTag(C.Tag.bottomTab("campaigns")).performClick()
    waitForTag(C.Tag.enter_workspace_button)
    composeTestRule.runOnIdle { connectivity.online.value = false }
    composeTestRule.onNodeWithTag(C.Tag.offline_banner, useUnmergedTree = true).assertIsDisplayed()
  }

  @Test
  fun rapidSignUpTapsDoNotStackScreensOrLeaveNavigationBlank() {
    showApp()
    scrollToTag(SIGN_IN_SCROLL_CONTAINER, C.Tag.sign_in_sign_up)

    composeTestRule.onNodeWithTag(C.Tag.sign_in_sign_up).performTouchInput {
      repeat(10) { click() }
    }
    waitForTag(C.Tag.sign_up_sign_in)
    scrollToTag(SIGN_UP_SCROLL_CONTAINER, C.Tag.sign_up_sign_in)

    composeTestRule.onNodeWithTag(C.Tag.sign_up_sign_in).assertIsDisplayed().performClick()
    composeTestRule.onNodeWithTag(C.Tag.sign_in_sign_up).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.sign_up_sign_in).assertIsNotDisplayed()

    composeTestRule.onNodeWithTag(C.Tag.sign_in_sign_up).performTouchInput {
      repeat(10) { click() }
    }
    waitForTag(C.Tag.sign_up_sign_in)
    scrollToTag(SIGN_UP_SCROLL_CONTAINER, C.Tag.sign_up_sign_in)
    composeTestRule.onNodeWithTag(C.Tag.sign_up_sign_in).performTouchInput {
      repeat(10) { click() }
    }
    waitForTag(C.Tag.sign_in_sign_up)
    composeTestRule.onNodeWithTag(C.Tag.sign_in_sign_up).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.sign_up_sign_in).assertIsNotDisplayed()
  }

  @Test
  fun successfulSignInAndLogoutReturnToSignIn() {
    val repository = FakeAuthRepository()
    showApp(repository)

    assertEquals(null, repository.currentUser)
    composeTestRule.onNodeWithTag(C.Tag.sign_in_email).performTextInput("hero@example.com")
    composeTestRule.onNodeWithTag(C.Tag.sign_in_password).performTextInput("secret1")
    composeTestRule.onNodeWithTag(C.Tag.sign_in_submit).performClick()

    waitForTag(TestTags.TITLE)
    composeTestRule.onNodeWithTag(TestTags.TITLE).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.sign_in_sign_up).assertIsNotDisplayed()
    assertEquals(1, repository.signInCalls)
    assertEquals(FakeAuthRepository.TEST_USER, repository.currentUser)

    composeTestRule.onNodeWithTag(TestTags.LOGOUT).performClick()
    waitForTag(C.Tag.sign_in_sign_up)
    scrollToTag(SIGN_IN_SCROLL_CONTAINER, C.Tag.sign_in_sign_up)
    composeTestRule.onNodeWithTag(C.Tag.sign_in_sign_up).assertIsDisplayed()
    composeTestRule.onNodeWithTag(TestTags.TITLE).assertIsNotDisplayed()
    assertEquals(null, repository.currentUser)
  }

  @Test
  fun successfulRegistrationNavigatesToHome() {
    val repository = FakeAuthRepository()
    showApp(repository)

    assertEquals(null, repository.currentUser)
    scrollToTag(SIGN_IN_SCROLL_CONTAINER, C.Tag.sign_in_sign_up)
    composeTestRule.onNodeWithTag(C.Tag.sign_in_sign_up).performClick()
    composeTestRule.onNodeWithTag(C.Tag.sign_up_username).performTextInput("Ranger")
    composeTestRule.onNodeWithTag(C.Tag.sign_up_email).performTextInput("hero@example.com")
    composeTestRule.onNodeWithTag(C.Tag.sign_up_password).performTextInput("secret1")
    composeTestRule.onNodeWithTag(C.Tag.sign_up_submit).performClick()

    waitForTag(TestTags.TITLE)
    composeTestRule.onNodeWithTag(TestTags.TITLE).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.sign_up_sign_in).assertIsNotDisplayed()
    assertEquals(1, repository.signUpCalls)
    assertEquals(FakeAuthRepository.TEST_USER, repository.currentUser)
  }

  @Test
  fun signUpLinkAndSignInTappedInOneFrameOpenSignUpWithoutSigningIn() {
    val repository = FakeAuthRepository()
    showApp(repository)
    composeTestRule.onNodeWithTag(C.Tag.sign_in_email).performTextInput("hero@example.com")
    composeTestRule.onNodeWithTag(C.Tag.sign_in_password).performTextInput("secret1")

    composeTestRule.tapInOneFrame(C.Tag.sign_in_sign_up, C.Tag.sign_in_submit)
    waitForTag(C.Tag.sign_up_sign_in)

    assertEquals(0, repository.signInCalls)
    assertEquals(null, repository.currentUser)
    composeTestRule.onAllNodesWithTag(TestTags.TITLE).assertCountEquals(0)
  }

  @Test
  fun systemBackFromSignUpNeverShowsSignUpErrorsOnSignIn() {
    val repository = FakeAuthRepository().apply { signUpResult = CompletableDeferred() }
    showApp(repository)
    scrollToTag(SIGN_IN_SCROLL_CONTAINER, C.Tag.sign_in_sign_up)
    composeTestRule.onNodeWithTag(C.Tag.sign_in_sign_up).performClick()
    composeTestRule.onNodeWithTag(C.Tag.sign_up_username).performTextInput("Ranger")
    composeTestRule.onNodeWithTag(C.Tag.sign_up_email).performTextInput("hero@example.com")
    composeTestRule.onNodeWithTag(C.Tag.sign_up_password).performTextInput("secret1")
    composeTestRule.onNodeWithTag(C.Tag.sign_up_submit).performClick()

    Espresso.pressBack()
    composeTestRule.onNodeWithTag(C.Tag.sign_up_submit).assertExists()
    repository.signUpResult?.complete(AuthResult.Failure(AuthError.EMAIL_ALREADY_IN_USE))
    waitForText(AuthError.EMAIL_ALREADY_IN_USE.message)
    Espresso.pressBack()
    waitForTag(C.Tag.sign_in_submit)

    composeTestRule.onAllNodesWithTag(C.Tag.sign_up_submit).assertCountEquals(0)
    composeTestRule.onAllNodesWithText(AuthError.EMAIL_ALREADY_IN_USE.message).assertCountEquals(0)
  }

  @Test
  fun homeNavigationOpensDiceAndReturnsHome() {
    val repository = FakeAuthRepository()
    showApp(repository)

    composeTestRule.onNodeWithTag(C.Tag.sign_in_email).performTextInput("hero@example.com")
    composeTestRule.onNodeWithTag(C.Tag.sign_in_password).performTextInput("secret1")
    composeTestRule.onNodeWithTag(C.Tag.sign_in_submit).performClick()
    waitForTag(TestTags.TITLE)

    composeTestRule.onNodeWithTag(TestTags.DICEBUTTON).performClick()
    waitForTag(C.Tag.dice_offline_screen)
    composeTestRule.onNodeWithTag(C.Tag.dice_offline_screen).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.dice_offline_close).performClick()

    composeTestRule.onNodeWithTag(TestTags.TITLE).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.dice_offline_screen).assertIsNotDisplayed()
  }

  @Test
  fun homeProfileButtonOpensProfileAndBackButtonReturnsHome() {
    val profileViewModel = RecordingProfileViewModel(LOADED_PROFILE_STATE)
    openProfileFromHome(profileViewModel)

    composeTestRule.onNodeWithTag(C.Tag.profile_username).assertTextContains("Rowan")
    assertEquals(1, profileViewModel.loadCalls)

    composeTestRule.onNodeWithTag(C.Tag.shared_back_button).performClick()

    waitForTag(TestTags.TITLE)
    composeTestRule.onAllNodesWithTag(C.Tag.profile_username).assertCountEquals(0)
  }

  @Test
  fun systemBackFromProfileReturnsHome() {
    openProfileFromHome(RecordingProfileViewModel(LOADED_PROFILE_STATE))

    Espresso.pressBack()

    waitForTag(TestTags.TITLE)
    composeTestRule.onAllNodesWithTag(C.Tag.profile_username).assertCountEquals(0)
  }

  @Test
  fun homeTabFromProfileReturnsHome() {
    openProfileFromHome(RecordingProfileViewModel(LOADED_PROFILE_STATE))

    composeTestRule.onNodeWithTag(C.Tag.bottomTab("home")).performClick()

    waitForTag(TestTags.TITLE)
    composeTestRule.onAllNodesWithTag(C.Tag.profile_username).assertCountEquals(0)
  }

  @Test
  fun campaignsTabFromProfileOpensCampaigns() {
    showApp(
        groupRepository = groupRepository(),
        profileViewModel = RecordingProfileViewModel(LOADED_PROFILE_STATE),
    )
    signIn()
    composeTestRule.onNodeWithTag(C.Tag.open_profile).performClick()
    waitForTag(C.Tag.profile_username)

    composeTestRule.onNodeWithTag(C.Tag.bottomTab("campaigns")).performClick()

    waitForTag(C.Tag.play_button)
    composeTestRule.onAllNodesWithTag(C.Tag.profile_username).assertCountEquals(0)
  }

  @Test
  fun filesTabFromProfileOpensPersonalSpace() {
    openProfileFromHome(RecordingProfileViewModel(LOADED_PROFILE_STATE))

    composeTestRule.onNodeWithTag(C.Tag.bottomTab("files")).performClick()

    waitForText(PERSONAL_SPACE_TITLE)
    composeTestRule.onAllNodesWithTag(C.Tag.profile_username).assertCountEquals(0)
  }

  @Test
  fun mapTabFromProfileOpensTheMap() {
    val defaultMapRepository = MapRepositoryProvider.mapRepository
    MapRepositoryProvider.mapRepository = NoGamesMapRepository()
    try {
      openProfileFromHome(RecordingProfileViewModel(LOADED_PROFILE_STATE))

      composeTestRule.onNodeWithTag(C.Tag.bottomTab("map")).performClick()

      waitForTag(C.Tag.map_screen)
      composeTestRule.onAllNodesWithTag(C.Tag.profile_username).assertCountEquals(0)
    } finally {
      MapRepositoryProvider.mapRepository = defaultMapRepository
    }
  }

  @Test
  fun eachProfileVisitLoadsTheProfileWithAFreshViewModel() {
    // No injected ViewModel: JDRNexusApp creates the real one for the Profile entry.
    showApp(profileViewModel = null)
    signIn()
    // Read only after showApp: the provider's default repository needs the FirebaseApp it starts.
    val defaultProfileRepository = ProfileRepositoryProvider.repository
    val profileRepository = FakeProfileRepository()
    ProfileRepositoryProvider.repository = profileRepository
    try {
      repeat(2) {
        composeTestRule.onNodeWithTag(C.Tag.open_profile).performClick()
        waitForTag(C.Tag.profile_username)
        composeTestRule.onNodeWithTag(C.Tag.shared_back_button).performClick()
        waitForTag(TestTags.TITLE)
        composeTestRule.waitForIdle()
      }

      // ProfileViewModel.loadProfile keeps an already loaded profile of the same user, so only a
      // new ViewModel on the second visit fetches it again.
      assertEquals(2, profileRepository.profileRequests.size)
    } finally {
      ProfileRepositoryProvider.repository = defaultProfileRepository
    }
  }

  @Test
  fun doubleTapOnProfileButtonOpensASingleProfile() {
    showApp(profileViewModel = RecordingProfileViewModel(LOADED_PROFILE_STATE))
    signIn()

    composeTestRule.onNodeWithTag(C.Tag.open_profile).performTouchInput {
      click()
      click()
    }
    waitForTag(C.Tag.profile_username)
    composeTestRule.onNodeWithTag(C.Tag.shared_back_button).performClick()

    // One back leaves the profile: a second profile entry would still be on screen.
    waitForTag(TestTags.TITLE)
    composeTestRule.onAllNodesWithTag(C.Tag.profile_username).assertCountEquals(0)
  }

  @Test
  fun campaignsProfileButtonOpensProfileAndBackReturnsToCampaigns() {
    showApp(
        groupRepository = groupRepository(),
        profileViewModel = RecordingProfileViewModel(LOADED_PROFILE_STATE),
    )
    signInAndOpenCampaigns()
    waitForTag(C.Tag.play_button)

    composeTestRule.onNodeWithTag(C.Tag.open_profile).performClick()
    waitForTag(C.Tag.profile_username)
    composeTestRule.onNodeWithTag(C.Tag.shared_back_button).performClick()

    waitForTag(C.Tag.play_button)
    composeTestRule.onAllNodesWithTag(C.Tag.profile_username).assertCountEquals(0)
  }

  @Test
  fun rapidCloseClicksDoNotRemoveHomeFromTheBackStack() {
    val repository = FakeAuthRepository()
    showApp(repository)

    composeTestRule.onNodeWithTag(C.Tag.sign_in_email).performTextInput("hero@example.com")
    composeTestRule.onNodeWithTag(C.Tag.sign_in_password).performTextInput("secret1")
    composeTestRule.onNodeWithTag(C.Tag.sign_in_submit).performClick()
    waitForTag(TestTags.TITLE)
    composeTestRule.onNodeWithTag(TestTags.DICEBUTTON).performClick()
    waitForTag(C.Tag.dice_offline_close)

    composeTestRule.onNodeWithTag(C.Tag.dice_offline_close).performTouchInput {
      click()
      click()
    }

    composeTestRule.onNodeWithTag(TestTags.TITLE).assertIsDisplayed()
  }

  @Test
  fun logoutDisablesNavigationUntilSignedOut() {
    val repository = FakeAuthRepository()
    repository.signOutResult = CompletableDeferred()
    showApp(repository)

    composeTestRule.onNodeWithTag(C.Tag.sign_in_email).performTextInput("hero@example.com")
    composeTestRule.onNodeWithTag(C.Tag.sign_in_password).performTextInput("secret1")
    composeTestRule.onNodeWithTag(C.Tag.sign_in_submit).performClick()
    waitForTag(TestTags.TITLE)

    composeTestRule.onNodeWithTag(TestTags.LOGOUT).performClick()
    composeTestRule.onNodeWithTag(TestTags.DICEBUTTON).assertIsNotEnabled()
    composeTestRule.onNodeWithTag(TestTags.SOUNDBOARDBUTTON).assertIsNotEnabled()
    composeTestRule.onNodeWithTag(TestTags.PSPACEBUTTON).assertIsNotEnabled()
    composeTestRule.onNodeWithTag(TestTags.DICEBUTTON).performClick()
    composeTestRule.onAllNodesWithTag(C.Tag.dice_offline_screen).assertCountEquals(0)
    repository.signOutResult?.complete(AuthResult.Success(Unit))

    waitForTag(C.Tag.sign_in_sign_up)
    scrollToTag(SIGN_IN_SCROLL_CONTAINER, C.Tag.sign_in_sign_up)
    composeTestRule.onNodeWithTag(C.Tag.sign_in_sign_up).assertIsDisplayed()
    composeTestRule.onNodeWithTag(TestTags.TITLE).assertIsNotDisplayed()
    assertEquals(null, repository.currentUser)
  }

  @Test
  fun signOutExceptionKeepsAppAliveAndHomeUsable() {
    val repository = FakeAuthRepository()
    repository.signOutFailure = IllegalStateException("Sign-out failed")
    val viewModel = AuthViewModel(repository)
    composeTestRule.setContent {
      // This test opens the dice screen, and Robolectric cannot render the 3D die: draw the flat
      // one instead.
      CompositionLocalProvider(LocalDieView provides FlatDieView) {
        JDRNexusApp(
            authViewModel = viewModel,
            authRepository = repository,
            groupRepository = FakeGroupRepository(),
            workspaceRepository = NavigationWorkspaceRepository(),
            notificationViewModel = FakeNotificationViewModel(),
            connectivityRepository = FakeConnectivityRepository(),
        )
      }
    }

    composeTestRule.onNodeWithTag(C.Tag.sign_in_email).performTextInput("hero@example.com")
    composeTestRule.onNodeWithTag(C.Tag.sign_in_password).performTextInput("secret1")
    composeTestRule.onNodeWithTag(C.Tag.sign_in_submit).performClick()
    waitForTag(TestTags.TITLE)

    composeTestRule.onNodeWithTag(TestTags.LOGOUT).performClick()
    composeTestRule.waitUntil(5_000) {
      viewModel.uiState.value.status is AuthStatus.Error &&
          composeTestRule.onAllNodesWithTag(TestTags.LOGOUT).fetchSemanticsNodes().isNotEmpty()
    }

    composeTestRule.onNodeWithTag(TestTags.TITLE).assertIsDisplayed()
    composeTestRule.onNodeWithTag(TestTags.DICEBUTTON).assertIsDisplayed().performClick()
    composeTestRule.onNodeWithTag(C.Tag.dice_offline_screen).assertIsDisplayed()
  }

  @Test
  fun homeTabFromCampaignsOpenedViaFilesReturnsHome() {
    showAuthenticatedApp()

    composeTestRule.onNodeWithTag(C.Tag.bottomTab("files")).performClick()
    waitForText(PERSONAL_SPACE_TITLE)
    composeTestRule.onNodeWithTag(C.Tag.bottomTab("campaigns")).performClick()
    waitForTag(GameManagementTestTags.CREATE_GROUP_BUTTON)
    composeTestRule.onNodeWithTag(C.Tag.bottomTab("home")).performClick()

    composeTestRule.onNodeWithTag(TestTags.TITLE).assertIsDisplayed()
    composeTestRule
        .onAllNodesWithTag(GameManagementTestTags.CREATE_GROUP_BUTTON)
        .assertCountEquals(0)
  }

  @Test
  fun groupWorkspaceSearchOpensEditorAndReturnsToWorkspace() {
    val loreFile = loreFile()
    groupWorkspaceSearchStart(loreFile)

    composeTestRule.onNodeWithTag(C.Tag.searchResult(loreFile.id)).performClick()

    // The selected file must open in the existing lore editor.
    waitForTag(EditFileLoreTestTags.LIST)
    composeTestRule.onNodeWithTag(EditFileLoreTestTags.LIST).assertIsDisplayed()

    // Returning from the editor should restore Group Search.
    composeTestRule.onNodeWithTag(EditFileLoreTestTags.BACK).performClick()
    composeTestRule.onNodeWithTag(C.Tag.search_query).assertIsDisplayed()

    // Returning from Group Search should restore Group Workspace.
    composeTestRule.onNodeWithTag(C.Tag.shared_back_button).performClick()
    waitForText("The Sunken Crown")
    composeTestRule.onNodeWithTag(C.Tag.search_open).assertIsDisplayed()
  }

  @Test
  fun groupSearchOpensAnotherMembersFileReadOnly() {
    val loreFile = loreFile().copy(ownerId = "another-player")
    groupWorkspaceSearchStart(loreFile)

    composeTestRule.onNodeWithTag(C.Tag.searchResult(loreFile.id)).performClick()

    waitForTag(EditFileLoreTestTags.LIST)
    composeTestRule.onNodeWithTag("edit_lore_save").assertIsNotEnabled()
    composeTestRule.onNodeWithTag("edit_lore_title").assertTextContains("Dragon Lore")
  }

  @Test
  fun groupSearchOpensOwnFileWithEditingEnabled() {
    // The signed-in user-1 owns this file and is a participant of group-1.
    val loreFile = loreFile().copy(ownerId = "user-1")
    groupWorkspaceSearchStart(loreFile)

    composeTestRule.onNodeWithTag(C.Tag.searchResult(loreFile.id)).performClick()

    waitForTag(EditFileLoreTestTags.LIST)
    composeTestRule.onNodeWithTag("edit_lore_save").assertIsEnabled()
    composeTestRule.onNodeWithTag("edit_lore_title").assertTextContains("Dragon Lore")
  }

  @Test
  fun playerCanNavigateToHomeFromGroupWorkspace() {
    openGroupWorkspaceAsPlayer()

    composeTestRule.onNodeWithTag(C.Tag.bottomTab("home")).performClick()

    composeTestRule.onNodeWithTag(TestTags.TITLE).assertIsDisplayed()
  }

  @Test
  fun playerCanNavigateToGroupManagementFromGroupWorkspace() {
    openGroupWorkspaceAsPlayer()

    composeTestRule.onNodeWithTag(C.Tag.bottomTab("campaigns")).performClick()

    // The group management screen should display the available workspace.
    composeTestRule.onNodeWithTag(C.Tag.enter_workspace_button).assertIsDisplayed()
  }

  @Test
  fun playerCanNavigateToPersonalSpaceFromGroupWorkspace() {
    openGroupWorkspaceAsPlayer()

    composeTestRule.onNodeWithTag(C.Tag.bottomTab("files")).performClick()

    waitForText("My Space")

    composeTestRule.onNodeWithText("My Space").assertIsDisplayed()
  }

  @Test
  fun playerCanNavigateToMapFromGroupWorkspace() {
    val previousRepository = MapRepositoryProvider.mapRepository
    MapRepositoryProvider.mapRepository = NoGamesMapRepository()

    try {
      openGroupWorkspaceAsPlayer()

      composeTestRule.onNodeWithTag(C.Tag.bottomTab("map")).performClick()

      composeTestRule.onNodeWithTag(C.Tag.map_screen).assertIsDisplayed()
    } finally {
      MapRepositoryProvider.mapRepository = previousRepository
    }
  }

  @Test
  fun groupWorkspaceFileClickOpensLoreEditor() {
    val loreFile = loreFile()

    showApp(
        groupRepository = groupRepository("user-1"),
        workspaceRepository = NavigationWorkspaceRepository(listOf(loreFile)),
    )
    signInAndOpenCampaigns()

    // Open Group Workspace.
    waitForTag(C.Tag.enter_workspace_button)
    composeTestRule.onNodeWithTag(C.Tag.enter_workspace_button).performClick()

    waitForText("Dragon Lore")

    // Click the file in the workspace.
    composeTestRule.onNodeWithTag(C.Tag.workspaceItem(loreFile.id)).performClick()

    // Verify that the lore editor opens.
    waitForTag(EditFileLoreTestTags.LIST)
    composeTestRule.onNodeWithTag(EditFileLoreTestTags.LIST).assertIsDisplayed()
  }

  // Creating a character opens its editor, and back returns to the workspace.
  @Test
  fun groupWorkspaceCreateCharacterOpensEditorAndBackReturnsToWorkspace() {
    val workspaceRepository = NavigationWorkspaceRepository()
    showApp(groupRepository = groupRepository(), workspaceRepository = workspaceRepository)
    signInAndOpenCampaigns()
    waitForTag(C.Tag.enter_workspace_button)
    composeTestRule.onNodeWithTag(C.Tag.enter_workspace_button).performClick()
    waitForTag(C.Tag.workspace_actions_fab)

    composeTestRule.onNodeWithTag(C.Tag.workspace_actions_fab).performClick()
    composeTestRule.onNodeWithTag(C.Tag.workspace_create_item).performClick()
    composeTestRule.onNodeWithTag(C.Tag.workspace_create_item_character).performClick()

    waitForTag(EditFileCharacterTestTags.LIST)
    composeTestRule.onNodeWithTag(EditFileCharacterTestTags.LIST).assertIsDisplayed()
    val created = workspaceRepository.createdFiles.single()
    assertEquals(DocumentType.CHARACTER, created.type)
    assertEquals(Placement("group-1", false), created.placements["group-1"])

    composeTestRule.onNodeWithTag(EditFileCharacterTestTags.BACK).performClick()
    waitForTag(C.Tag.workspace_actions_fab)
    composeTestRule.onNodeWithTag(C.Tag.workspace_actions_fab).assertIsDisplayed()
  }

  @Test
  fun backFromLoreReturnsToPersonalSpace() {
    val loreFile = showAuthenticatedApp()

    composeTestRule.onNodeWithTag(C.Tag.bottomTab("files")).performClick()
    waitForText(loreFile.name)
    composeTestRule.onNodeWithText(loreFile.name).performClick()
    waitForTag(EditFileLoreTestTags.LIST)
    composeTestRule.onNodeWithTag(EditFileLoreTestTags.BACK).performClick()

    composeTestRule.onAllNodesWithText(PERSONAL_SPACE_TITLE).assertCountEquals(1)
  }

  @Test
  fun tappingAnImageInPersonalSpaceOpensNoEditor() {
    val image =
        JDRFile(
            id = "image-1",
            name = "Kingdom Map",
            ownerId = "user-1",
            type = DocumentType.IMAGE,
            content = "images/user-1/image-1",
        )
    showAuthenticatedApp(extraFiles = listOf(image))

    composeTestRule.onNodeWithTag(C.Tag.bottomTab("files")).performClick()
    waitForText(image.name)
    composeTestRule.onNodeWithText(image.name).performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onAllNodesWithText(PERSONAL_SPACE_TITLE).assertCountEquals(1)
    composeTestRule.onNodeWithText(image.name).assertIsDisplayed()
  }

  @Test
  fun mapTabOpensMapScreen() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    if (FirebaseApp.getApps(context).isEmpty()) checkNotNull(FirebaseApp.initializeApp(context))
    // No real Firestore: the Map screen loads its games from a fake repository.
    val defaultMapRepository = MapRepositoryProvider.mapRepository
    MapRepositoryProvider.mapRepository = NoGamesMapRepository()
    try {
      showApp()
      composeTestRule.onNodeWithTag(C.Tag.sign_in_email).performTextInput("hero@example.com")
      composeTestRule.onNodeWithTag(C.Tag.sign_in_password).performTextInput("secret1")
      composeTestRule.onNodeWithTag(C.Tag.sign_in_submit).performClick()
      waitForTag(TestTags.TITLE)

      composeTestRule.onNodeWithTag(C.Tag.bottomTab("map")).performClick()

      waitForTag(C.Tag.map_screen)
      composeTestRule.onNodeWithTag(C.Tag.map_screen).assertIsDisplayed()

      composeTestRule.onNodeWithTag(C.Tag.bottomTab("home")).performClick()
      waitForTag(TestTags.TITLE)
      composeTestRule.onNodeWithTag(TestTags.TITLE).assertIsDisplayed()
    } finally {
      MapRepositoryProvider.mapRepository = defaultMapRepository
    }
  }

  @Test
  fun campaignsNavigateToWorkspaceAndBack() {
    val groupRepository = groupRepository("user-1")
    showApp(groupRepository = groupRepository)
    signInAndOpenCampaigns()

    waitForTag(C.Tag.enter_workspace_button)
    composeTestRule.onNodeWithTag(C.Tag.enter_workspace_button).performClick()
    waitForText("The Sunken Crown")

    composeTestRule.onNodeWithContentDescription("Go back").performClick()
    waitForTag(C.Tag.enter_workspace_button)
  }

  @Test
  fun groupWorkspaceSearchShowsGroupFilesAndBackReturnsToWorkspace() {
    groupWorkspaceSearchStart(loreFile())
    composeTestRule.onNodeWithTag(C.Tag.shared_back_button).performClick()
    waitForText("The Sunken Crown")
  }

  @Test
  fun campaignsHelpButtonOpensTheHelpWhichCanBeClosedAndReopened() {
    showApp(groupRepository = groupRepository("user-1"))
    signInAndOpenCampaigns()

    // The help opens by itself on the first visit; close it so that "?" is tapped with it closed.
    waitForTag(C.Tag.help_sheet)
    closeHelpWithGotIt()

    composeTestRule.onNodeWithTag(C.Tag.help_button).performClick()
    waitForTag(C.Tag.help_sheet)
  }

  @Test
  fun campaignsHelpOpensByItselfOnTheFirstVisitOnly() {
    showApp(groupRepository = groupRepository("user-1"))
    signInAndOpenCampaigns()

    waitForTag(C.Tag.help_sheet)
    closeHelpWithGotIt()

    // Home removes Campaigns from the back stack, so coming back creates a new help ViewModel,
    // which reads the record again.
    composeTestRule.onNodeWithTag(C.Tag.bottomTab("home")).performClick()
    waitForTag(TestTags.TITLE)
    composeTestRule.onNodeWithTag(C.Tag.bottomTab("campaigns")).performClick()
    waitForTag(C.Tag.help_button)
    composeTestRule.waitForIdle()

    composeTestRule.onAllNodesWithTag(C.Tag.help_sheet).assertCountEquals(0)
  }

  @Test
  fun systemBackFromWorkspaceReturnsToCampaigns() {
    val groupRepository = groupRepository("user-1")
    showApp(groupRepository = groupRepository)
    signInAndOpenCampaigns()

    waitForTag(C.Tag.enter_workspace_button)
    composeTestRule.onNodeWithTag(C.Tag.enter_workspace_button).performClick()
    waitForText("The Sunken Crown")

    Espresso.pressBack()

    waitForTag(C.Tag.enter_workspace_button)
  }

  @Test
  fun rapidWorkspaceTapsCreateOneDestinationAndOneViewModel() {
    val groupRepository = groupRepository("user-1")
    showApp(groupRepository = groupRepository)
    signInAndOpenCampaigns()

    waitForTag(C.Tag.enter_workspace_button)
    composeTestRule.onNodeWithTag(C.Tag.enter_workspace_button).performTouchInput {
      repeat(10) { click() }
    }

    waitForText("The Sunken Crown")
    assertEquals(1, groupRepository.groupFlowLoads)
    assertEquals("group-1", groupRepository.lastGroupFlowId)
  }

  @Test
  fun workspaceGroupIdSurvivesSavedStateRestoration() {
    val groupRepository = groupRepository("user-1")
    val authRepository = FakeAuthRepository()
    val restorationTester = StateRestorationTester(composeTestRule)
    val authViewModel = AuthViewModel(authRepository)
    val workspaceRepository = NavigationWorkspaceRepository()

    restorationTester.setContent {
      JDRNexusApp(
          authViewModel = authViewModel,
          authRepository = authRepository,
          groupRepository = groupRepository,
          workspaceRepository = workspaceRepository,
          notificationViewModel = FakeNotificationViewModel(),
          connectivityRepository = FakeConnectivityRepository(),
      )
    }
    signInAndOpenCampaigns()
    waitForTag(C.Tag.enter_workspace_button)
    composeTestRule.onNodeWithTag(C.Tag.enter_workspace_button).performClick()
    waitForText("The Sunken Crown")

    restorationTester.emulateSavedInstanceStateRestore()

    waitForText("The Sunken Crown")
    assertEquals("group-1", groupRepository.lastGroupFlowId)
  }

  @Test
  fun personalSpaceSearchOpensEditorAndReturnsToPersonalSpace() {
    val searchViewModel =
        FakeSearchViewModel(
            JDRFile(
                id = "lore-1",
                name = "The Kingdom",
                ownerId = "user-1",
                type = DocumentType.TEXT,
            )
        )
    val loreFile = showAuthenticatedApp(searchViewModel)

    composeTestRule.onNodeWithTag(C.Tag.bottomTab("files")).performClick()
    waitForText(loreFile.name)
    composeTestRule.onNodeWithTag(C.Tag.search_open).performClick()
    composeTestRule.onNodeWithTag(C.Tag.search_query).performTextInput("Kingdom")
    composeTestRule.onNodeWithTag(C.Tag.searchResult(loreFile.id)).performClick()

    waitForTag(EditFileLoreTestTags.LIST)
    composeTestRule.onNodeWithTag(EditFileLoreTestTags.BACK).performClick()
    composeTestRule.onNodeWithTag(C.Tag.search_query).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.shared_back_button).performClick()
    waitForText(loreFile.name)
  }

  @Test
  fun searchBackRestoresThePersonalSpaceFolder() {
    // The folder is the root entry; its lore document appears only inside that folder.
    val folder =
        JDRFile(
            id = "campaign",
            name = "Campaign",
            ownerId = "user-1",
            type = DocumentType.FOLDER,
        )
    val loreFile =
        JDRFile(
            id = "nested-lore",
            name = "The Kingdom",
            ownerId = "user-1",
            personalParentId = folder.id,
            type = DocumentType.TEXT,
        )
    val searchViewModel = FakeSearchViewModel(loreFile)
    showAuthenticatedApp(searchViewModel, personalFolder = folder)

    composeTestRule.onNodeWithTag(C.Tag.bottomTab("files")).performClick()
    waitForText(folder.name)
    composeTestRule.onNodeWithText(folder.name).performClick()
    waitForText(loreFile.name)
    composeTestRule.onNodeWithTag(C.Tag.search_open).performClick()
    composeTestRule.onNodeWithTag(C.Tag.search_query).performTextInput("Kingdom")
    waitForTag(C.Tag.searchResult(loreFile.id))

    composeTestRule.onNodeWithTag(C.Tag.shared_back_button).performClick()

    // The lore document only shows inside the folder, and the top bar title and the heading both
    // show the folder name. At the root, the folder name would appear once, as a row.
    composeTestRule.onNodeWithText(loreFile.name).assertIsDisplayed()
    composeTestRule.onAllNodesWithText(folder.name).assertCountEquals(2)
  }

  @Test
  fun searchQuerySurvivesActivityRecreation() {
    val restorationTester = StateRestorationTester(composeTestRule)
    val authRepository = FakeAuthRepository()
    val workspaceRepository = FakeWorkspaceRepository(emptyList())
    val groupRepository = FakeGroupRepository()
    val authViewModel = AuthViewModel(authRepository)
    val personalSpaceViewModel = PersonalSpaceViewModel(workspaceRepository, authRepository)
    val editFileViewModel = EditFileViewModel(workspaceRepository)
    val searchViewModel =
        DocumentSearchViewModel(
            repository = workspaceRepository,
            authRepository = authRepository,
        )

    // Rotation recreates the UI but retains its ViewModels; this emulates that save/restore cycle.
    restorationTester.setContent {
      JDRNexusApp(
          authViewModel = authViewModel,
          personalSpaceViewModel = personalSpaceViewModel,
          editFileViewModel = editFileViewModel,
          authRepository = authRepository,
          groupRepository = groupRepository,
          workspaceRepository = workspaceRepository,
          personalSearchViewModel = searchViewModel,
          groupSearchViewModel = searchViewModel,
          notificationViewModel = FakeNotificationViewModel(),
          connectivityRepository = FakeConnectivityRepository(),
      )
    }

    composeTestRule.onNodeWithTag(C.Tag.sign_in_email).performTextInput("hero@example.com")
    composeTestRule.onNodeWithTag(C.Tag.sign_in_password).performTextInput("secret1")
    composeTestRule.onNodeWithTag(C.Tag.sign_in_submit).performClick()
    waitForTag(TestTags.TITLE)
    composeTestRule.onNodeWithTag(C.Tag.bottomTab("files")).performClick()
    composeTestRule.onNodeWithTag(C.Tag.search_open).performClick()
    composeTestRule.onNodeWithTag(C.Tag.search_query).performTextInput("Dragon")

    restorationTester.emulateSavedInstanceStateRestore()

    composeTestRule.onNodeWithTag(C.Tag.search_query).assertTextContains("Dragon")
  }

  @Test
  fun systemBackFromSearchRestoresThePersonalSpaceFolder() {
    // The folder is the root entry; its lore document appears only inside that folder.
    val folder =
        JDRFile(
            id = "system-back-campaign",
            name = "System Back Campaign",
            ownerId = "user-1",
            type = DocumentType.FOLDER,
        )
    val searchResult =
        JDRFile(
            id = "system-back-lore",
            name = "System Back Lore",
            ownerId = "user-1",
            personalParentId = folder.id,
            type = DocumentType.TEXT,
        )
    val searchViewModel = FakeSearchViewModel(searchResult)
    val personalLore = showAuthenticatedApp(searchViewModel, personalFolder = folder)

    composeTestRule.onNodeWithTag(C.Tag.bottomTab("files")).performClick()
    waitForText(folder.name)
    composeTestRule.onNodeWithText(folder.name).performClick()
    waitForText(personalLore.name)
    composeTestRule.onNodeWithTag(C.Tag.search_open).performClick()
    composeTestRule.onNodeWithTag(C.Tag.search_query).performTextInput("System Back")
    waitForTag(C.Tag.searchResult(searchResult.id))

    Espresso.pressBack()

    // The nested file proves the folder was restored, and its name is now in the title and heading.
    composeTestRule.onNodeWithText(personalLore.name).assertIsDisplayed()
    composeTestRule.onAllNodesWithText(folder.name).assertCountEquals(2)
  }

  /** A soundboard VM that does nothing and records when its owner clears it. */
  private class LifetimeSoundboardViewModel : ViewModel(), SoundboardViewModelInterface {
    var cleared = false
    override val uiState = MutableStateFlow(SoundboardUiState())

    override fun onCleared() {
      cleared = true
    }

    override fun selectPlaylist(playlistId: String) = Unit

    override fun playAmbience(playlistId: String, audioId: String) = Unit

    override fun playPlaylist(playlistId: String) = Unit

    override fun pauseAmbience() = Unit

    override fun stopAmbience() = Unit

    override fun setAmbienceLooping(isLooping: Boolean) = Unit

    override fun toggleSoundEffect(audioId: String) = Unit

    override fun setAmbienceVolume(volume: Float) = Unit

    override fun setSoundEffectVolume(volume: Float) = Unit

    override fun saveVolumes() = Unit

    override fun toggleAmbienceMute() = Unit

    override fun toggleSoundEffectMute() = Unit

    override fun stopAll() = Unit

    override fun setTrackMenuExpanded(isExpanded: Boolean) = Unit

    override fun loadSoundboard(soundboardId: String?) = Unit
  }

  private class NoGamesMapRepository : MapRepository {
    override suspend fun loadGamesNear(location: MapLocation?): List<MapGame> = emptyList()
  }

  private fun loreFile(): JDRFile =
      JDRFile(
          id = "shared-lore",
          name = "Dragon Lore",
          ownerId = "user-1",
          type = DocumentType.TEXT,
          content =
              """{"version":1,"type":"lore","title":"Dragon Lore","sections":[{"sectionID":"history","title":"History","content":"A dragon."}]}""",
          sharedGroupsIds = listOf("group-1"),
          placements =
              mapOf(
                  "group-1" to
                      Placement(
                          folderId = "group-1",
                          isHidden = false,
                      )
              ),
      )

  private fun groupWorkspaceSearchStart(loreFile: JDRFile) {
    val groupRepository = groupRepository()

    showApp(
        groupRepository = groupRepository,
        workspaceRepository = NavigationWorkspaceRepository(listOf(loreFile)),
    )
    signInAndOpenCampaigns()

    waitForTag(C.Tag.enter_workspace_button)
    composeTestRule.onNodeWithTag(C.Tag.enter_workspace_button).performClick()
    waitForText("The Sunken Crown")

    composeTestRule.onNodeWithTag(C.Tag.search_open).performClick()
    composeTestRule.onNodeWithTag(C.Tag.search_query).performTextInput("Dragon")
    waitForTag(C.Tag.searchResult(loreFile.id))
  }

  @Test
  fun gameMasterPlayOpensTheirGameScreenAndStartsTheGame() {
    val gameRepository = NavigationGameSessionRepository()
    openGameFromPlay(gameMasterId = "user-1", gameRepository = gameRepository)

    waitForTag(C.Tag.game_gm_start_game)
    waitForText("The Sunken Crown")
    composeTestRule.onAllNodesWithTag(C.Tag.game_player_back).assertCountEquals(0)

    composeTestRule.onNodeWithTag(C.Tag.game_gm_start_game).performClick()
    waitForTag(C.Tag.game_gm_game_in_progress)

    assertEquals("group-1", gameRepository.lastSavedGroupId)
    assertTrue(gameRepository.session.value.isGameStarted)
  }

  @Test
  fun backFromGameMasterScreenReturnsToCampaigns() {
    openGameFromPlay(gameMasterId = "user-1")
    waitForTag(C.Tag.shared_back_button)

    composeTestRule.onNodeWithTag(C.Tag.shared_back_button).performClick()

    assertBackOnCampaigns(C.Tag.game_gm_start_game)
  }

  @Test
  fun gameMasterDiceOpensAndReturnsToTheGameScreen() {
    openGameFromPlay(gameMasterId = "user-1")
    waitForTag(C.Tag.game_gm_dice)

    composeTestRule.onNodeWithTag(C.Tag.game_gm_dice).performClick()
    waitForTag(C.Tag.dice_offline_screen)
    Espresso.pressBack()

    waitForTag(C.Tag.game_gm_start_game)
  }

  @Test
  fun gameMasterCampaignsTabReturnsToCampaigns() {
    openGameFromPlay(gameMasterId = "user-1")
    waitForTag(C.Tag.game_gm_start_game)

    composeTestRule.onNodeWithTag(C.Tag.bottomTab("campaigns")).performClick()

    assertBackOnCampaigns(C.Tag.game_gm_start_game)
  }

  @Test
  fun homeMicOpensTheSoundboardAndBackReturnsHome() {
    showApp()
    signIn()

    composeTestRule.onNodeWithTag(TestTags.SOUNDBOARDBUTTON).performClick()
    waitForTag(C.Tag.soundboard_loading)
    Espresso.pressBack()

    waitForTag(TestTags.TITLE)
    composeTestRule.onAllNodesWithTag(C.Tag.soundboard_loading).assertCountEquals(0)
  }

  @Test
  fun soundboardOpenedFromHomeLivesOnHome() {
    showApp()
    signIn()

    repeat(2) {
      composeTestRule.onNodeWithTag(TestTags.SOUNDBOARDBUTTON).performClick()
      waitForTag(C.Tag.soundboard_loading)
      Espresso.pressBack()
      waitForTag(TestTags.TITLE)
    }

    composeTestRule.runOnIdle {
      assertEquals(1, soundboardViewModels.size)
      assertFalse(soundboardViewModels.single().cleared)
    }
  }

  @Test
  fun soundboardStopsAtLogout() {
    showApp()
    signIn()
    composeTestRule.onNodeWithTag(TestTags.SOUNDBOARDBUTTON).performClick()
    waitForTag(C.Tag.soundboard_loading)
    Espresso.pressBack()
    waitForTag(TestTags.LOGOUT)

    composeTestRule.onNodeWithTag(TestTags.LOGOUT).performClick()

    waitForTag(C.Tag.sign_in_email)
    composeTestRule.runOnIdle { assertTrue(soundboardViewModels.single().cleared) }
  }

  @Test
  fun campaignsTabFromHomeSoundboardOpensCampaigns() {
    showApp(groupRepository = groupRepository())
    signIn()

    composeTestRule.onNodeWithTag(TestTags.SOUNDBOARDBUTTON).performClick()
    waitForTag(C.Tag.soundboard_loading)
    composeTestRule.onNodeWithTag(C.Tag.bottomTab("campaigns")).performClick()

    waitForTag(C.Tag.play_button)
    composeTestRule.onAllNodesWithTag(C.Tag.soundboard_loading).assertCountEquals(0)
  }

  @Test
  fun rapidPlayTapsOpenOneGameScreen() {
    showGameApp(gameMasterId = "user-1")
    signInAndOpenCampaigns()
    waitForTag(C.Tag.play_button)

    composeTestRule.onNodeWithTag(C.Tag.play_button).performTouchInput { repeat(10) { click() } }
    waitForTag(C.Tag.game_gm_start_game)
    Espresso.pressBack()

    assertBackOnCampaigns(C.Tag.game_gm_start_game)
  }

  @Test
  fun playerPlayOpensTheirGameScreenAndBackReturnsToCampaigns() {
    openGameFromPlay(gameMasterId = "gm-1")

    waitForTag(C.Tag.game_player_back)
    composeTestRule.onAllNodesWithTag(C.Tag.game_gm_start_game).assertCountEquals(0)

    composeTestRule.onNodeWithTag(C.Tag.game_player_back).performClick()

    assertBackOnCampaigns(C.Tag.game_player_back)
  }

  @Test
  fun playerWorkspaceButtonOpensTheGroupWorkspace() {
    openGameFromPlay(gameMasterId = "gm-1")
    waitForTag(C.Tag.game_player_workspace)

    composeTestRule.onNodeWithTag(C.Tag.game_player_workspace).performClick()

    waitForTagToDisappear(C.Tag.game_player_workspace)
    waitForText("The Sunken Crown")
  }

  @Test
  fun playerDiceOpensAndReturnsToTheGameScreen() {
    val strengthRoll = C.Tag.gamePlayerStatRoll("strength")
    openGameFromPlay(
        gameMasterId = "gm-1",
        workspaceRepository = NavigationWorkspaceRepository(listOf(playerCharacterFile())),
    )
    waitForTag(C.Tag.game_player_character_name)

    composeTestRule.onNode(hasScrollAction()).performScrollToNode(hasTestTag(strengthRoll))
    composeTestRule.onNodeWithTag(strengthRoll).performClick()
    waitForTag(C.Tag.dice_offline_screen)
    Espresso.pressBack()

    waitForTag(C.Tag.game_player_back)
  }

  @Test
  fun playerCampaignsTabReturnsToCampaigns() {
    openGameFromPlay(gameMasterId = "gm-1")
    waitForTag(C.Tag.game_player_back)

    composeTestRule.onNodeWithTag(C.Tag.bottomTab("campaigns")).performClick()

    assertBackOnCampaigns(C.Tag.game_player_back)
  }

  @Test
  fun manageGroupOpensTheGroupManagementScreenAndBackReturnsToCampaigns() {
    openGroupManagement(groupRepository("user-1"))

    composeTestRule.onNodeWithTag(C.Tag.shared_back_button).performClick()

    assertBackOnCampaigns(C.Tag.group_management_delete)
  }

  @Test
  fun deletingTheGroupReturnsToCampaigns() {
    val groupRepository = groupRepository("user-1")
    openGroupManagement(groupRepository)

    composeTestRule.onNodeWithTag(C.Tag.group_management_delete).performClick()
    composeTestRule.onNodeWithTag(C.Tag.group_management_confirm).performClick()

    assertBackOnCampaigns(C.Tag.group_management_delete)
    assertEquals("group-1", groupRepository.deletedGroupId)
  }

  private fun openGroupManagement(groupRepository: NavigationGroupRepository) {
    showApp(groupRepository = groupRepository)
    signInAndOpenCampaigns()
    waitForText("Manage group")
    composeTestRule.onNodeWithText("Manage group").performClick()
    waitForTag(C.Tag.group_management_delete)
  }

  @Test
  fun campaignsTabInAWorkspaceOpenedFromThePlayerScreenReturnsToCampaigns() {
    openGameFromPlay(gameMasterId = "gm-1")
    waitForTag(C.Tag.game_player_workspace)
    composeTestRule.onNodeWithTag(C.Tag.game_player_workspace).performClick()
    waitForTagToDisappear(C.Tag.game_player_workspace)
    waitForText("The Sunken Crown")

    composeTestRule.onNodeWithTag(C.Tag.bottomTab("campaigns")).performClick()

    assertBackOnCampaigns(C.Tag.game_player_back)
  }

  @Test
  fun gameMasterHomeTabReturnsHome() =
      assertGameTabOpens("user-1", C.Tag.game_gm_start_combat, C.Tag.bottomTab("home")) {
        waitForTag(TestTags.TITLE)
      }

  @Test
  fun gameMasterFilesTabOpensPersonalSpace() =
      assertGameTabOpens("user-1", C.Tag.game_gm_start_combat, C.Tag.bottomTab("files")) {
        waitForText(PERSONAL_SPACE_TITLE)
      }

  @Test
  fun gameMasterMapTabOpensTheMap() =
      assertGameTabOpens("user-1", C.Tag.game_gm_start_combat, C.Tag.bottomTab("map")) {
        waitForTag(C.Tag.map_screen)
      }

  @Test
  fun playerHomeTabReturnsHome() =
      assertGameTabOpens(
          "gm-1",
          C.Tag.game_player_back,
          C.Tag.bottomTab("home"),
      ) {
        waitForTag(TestTags.TITLE)
      }

  @Test
  fun playerFilesTabOpensPersonalSpace() =
      assertGameTabOpens(
          "gm-1",
          C.Tag.game_player_back,
          C.Tag.bottomTab("files"),
      ) {
        waitForText(PERSONAL_SPACE_TITLE)
      }

  @Test
  fun playerMapTabOpensTheMap() =
      assertGameTabOpens(
          "gm-1",
          C.Tag.game_player_back,
          C.Tag.bottomTab("map"),
      ) {
        waitForTag(C.Tag.map_screen)
      }

  /**
   * Opens the game screen of the group led by [gameMasterId] as `user-1`, taps [tabTag], runs
   * [waitForDestination] and checks the game screen (found by [gameScreenTag]) is no longer shown.
   * The Map screen reads its games from [NoGamesMapRepository] instead of Firestore.
   */
  private fun assertGameTabOpens(
      gameMasterId: String,
      gameScreenTag: String,
      tabTag: String,
      waitForDestination: () -> Unit,
  ) {
    val defaultMapRepository = MapRepositoryProvider.mapRepository
    MapRepositoryProvider.mapRepository = NoGamesMapRepository()
    try {
      openGameFromPlay(gameMasterId)
      waitForTag(gameScreenTag)

      composeTestRule.onNodeWithTag(tabTag).performClick()

      waitForDestination()
      composeTestRule.onAllNodesWithTag(gameScreenTag).assertCountEquals(0)
    } finally {
      MapRepositoryProvider.mapRepository = defaultMapRepository
    }
  }

  private fun showGameApp(
      gameMasterId: String,
      gameRepository: NavigationGameSessionRepository = NavigationGameSessionRepository(),
      workspaceRepository: NavigationWorkspaceRepository = NavigationWorkspaceRepository(),
  ) {
    showApp(
        groupRepository = groupRepository(gameMasterId),
        workspaceRepository = workspaceRepository,
        gameSessionRepository = gameRepository,
    )
  }

  /** Signs in as `user-1` and taps Play on the only group, led by [gameMasterId]. */
  private fun openGameFromPlay(
      gameMasterId: String,
      gameRepository: NavigationGameSessionRepository = NavigationGameSessionRepository(),
      workspaceRepository: NavigationWorkspaceRepository = NavigationWorkspaceRepository(),
  ) {
    showGameApp(gameMasterId, gameRepository, workspaceRepository)
    signInAndOpenCampaigns()
    waitForTag(C.Tag.play_button)
    composeTestRule.onNodeWithTag(C.Tag.play_button).performClick()
  }

  private fun assertBackOnCampaigns(gameScreenTag: String) {
    waitForTag(C.Tag.play_button)
    composeTestRule.onAllNodesWithTag(gameScreenTag).assertCountEquals(0)
  }

  /** Closes the open Campaigns help through its pages and "Got it", then waits for it to go. */
  private fun closeHelpWithGotIt() {
    composeTestRule.onNodeWithTag(C.Tag.help_next).performClick()
    composeTestRule.onNodeWithTag(C.Tag.help_next).performClick()
    composeTestRule.onNodeWithTag(C.Tag.help_done).performClick()
    waitForTagToDisappear(C.Tag.help_sheet)
  }

  private fun waitForTagToDisappear(tag: String) {
    composeTestRule.waitUntil(5_000) {
      composeTestRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty()
    }
  }

  private fun playerCharacterFile() =
      JDRFile(
          id = "hero-sheet",
          ownerId = "user-1",
          type = DocumentType.CHARACTER,
          content =
              JDRFileJsonCodec.serializeCharacter(
                  CharacterFile(
                      name = "Hero",
                      stats = listOf(CharacterStat("strength", "Strength", 16.0)),
                      actions = emptyList(),
                      hp = CharacterHealthPoints(current = 10, max = 20),
                  )
              ),
          sharedGroupsIds = listOf("group-1"),
          placements = mapOf("group-1" to Placement("group-1", false)),
      )

  private fun showApp(
      repository: AuthRepository = FakeAuthRepository(),
      groupRepository: NavigationGroupRepository? = null,
      workspaceRepository: NavigationWorkspaceRepository = NavigationWorkspaceRepository(),
      gameSessionRepository: NavigationGameSessionRepository? = null,
      connectivityRepository: FakeConnectivityRepository = FakeConnectivityRepository(),
      profileViewModel: RecordingProfileViewModel? = RecordingProfileViewModel(),
      authViewModel: AuthViewModel = AuthViewModel(repository),
  ) {
    val context = ApplicationProvider.getApplicationContext<Context>()
    if (FirebaseApp.getApps(context).isEmpty()) {
      checkNotNull(FirebaseApp.initializeApp(context))
    }
    val editFileViewModel =
        EditFileViewModel(
            workspaceRepository = workspaceRepository,
            groupRepository = groupRepository,
            authRepository = repository,
        )
    composeTestRule.setContent {
      // The 3D die cannot render under Robolectric: the dice screen draws the flat one instead.
      CompositionLocalProvider(LocalDieView provides FlatDieView) {
        JDRNexusApp(
            authViewModel = authViewModel,
            authRepository = repository,
            groupRepository = groupRepository,
            workspaceRepository = workspaceRepository,
            editFileViewModel = editFileViewModel,
            gameSessionRepository = gameSessionRepository,
            notificationViewModel = FakeNotificationViewModel(),
            connectivityRepository = connectivityRepository,
            profileViewModel = profileViewModel,
            soundboardViewModelFactory = soundboardFactory,
        )
      }
    }
  }

  /** Signs in with the fake account and waits for Home. */
  private fun signIn() {
    composeTestRule.onNodeWithTag(C.Tag.sign_in_email).performTextInput("hero@example.com")
    composeTestRule.onNodeWithTag(C.Tag.sign_in_password).performTextInput("secret1")
    composeTestRule.onNodeWithTag(C.Tag.sign_in_submit).performClick()
    waitForTag(TestTags.TITLE)
  }

  private fun signInAndOpenCampaigns() {
    signIn()
    composeTestRule.onNodeWithTag(C.Tag.bottomTab("campaigns")).performClick()
  }

  /** Shows the app with [profileViewModel], signs in and opens the profile from Home. */
  private fun openProfileFromHome(profileViewModel: RecordingProfileViewModel) {
    showApp(profileViewModel = profileViewModel)
    signIn()
    composeTestRule.onNodeWithTag(C.Tag.open_profile).performClick()
    waitForTag(C.Tag.profile_username)
  }

  private fun waitForTag(tag: String) {
    composeTestRule.waitUntil(5_000) {
      composeTestRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    }
  }

  private fun waitForText(text: String) {
    composeTestRule.waitUntil(5_000) {
      composeTestRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }
  }

  private fun showAuthenticatedApp(
      documentSearchViewModel: DocumentSearchInterfaceVM? = null,
      personalFolder: JDRFile? = null,
      extraFiles: List<JDRFile> = emptyList(),
  ): JDRFile {
    val repository = FakeAuthRepository()
    val loreFile =
        JDRFile(
            id = "lore-1",
            name = "The Kingdom",
            ownerId = "user-1",
            personalParentId = personalFolder?.id.orEmpty(),
            type = DocumentType.TEXT,
            content =
                """{"version":1,"type":"lore","title":"The Kingdom","sections":[{"sectionID":"history","title":"History","content":"Founded long ago."}]}""",
        )
    val workspaceRepository =
        FakeWorkspaceRepository(listOfNotNull(personalFolder, loreFile) + extraFiles)
    val groupRepository = FakeGroupRepository()
    val authViewModel = AuthViewModel(repository)
    val personalSpaceViewModel = PersonalSpaceViewModel(workspaceRepository, repository)
    val gameManagementViewModel = GameManagementViewModel(groupRepository, repository)
    val editFileViewModel = EditFileViewModel(workspaceRepository)
    composeTestRule.setContent {
      JDRNexusApp(
          authViewModel = authViewModel,
          gameManagementViewModel = gameManagementViewModel,
          personalSpaceViewModel = personalSpaceViewModel,
          editFileViewModel = editFileViewModel,
          authRepository = repository,
          groupRepository = groupRepository,
          workspaceRepository = workspaceRepository,
          personalSearchViewModel = documentSearchViewModel,
          groupSearchViewModel = documentSearchViewModel,
          notificationViewModel = FakeNotificationViewModel(),
          connectivityRepository = FakeConnectivityRepository(),
      )
    }
    composeTestRule.onNodeWithTag(C.Tag.sign_in_email).performTextInput("hero@example.com")
    composeTestRule.onNodeWithTag(C.Tag.sign_in_password).performTextInput("secret1")
    composeTestRule.onNodeWithTag(C.Tag.sign_in_submit).performClick()
    waitForTag(TestTags.TITLE)
    personalSpaceViewModel.fetchItems("")
    return loreFile
  }

  private fun scrollToTag(containerTag: String, targetTag: String) {
    composeTestRule.onNodeWithTag(containerTag).performScrollToNode(hasTestTag(targetTag))
  }

  private fun openGroupWorkspaceAsPlayer() {
    showApp(groupRepository = groupRepository())
    signInAndOpenCampaigns()

    waitForTag(C.Tag.enter_workspace_button)
    composeTestRule.onNodeWithTag(C.Tag.enter_workspace_button).performClick()

    waitForText("The Sunken Crown")
  }

  private fun groupRepository(gameMasterId: String = "gm-1") =
      NavigationGroupRepository(
          CampaignGroup(
              id = "group-1",
              name = "The Sunken Crown",
              gameMasterId = gameMasterId,
              participantIds =
                  if (gameMasterId == "user-1") {
                    listOf("user-1")
                  } else {
                    listOf(gameMasterId, "user-1")
                  },
          )
      )

  private fun hasEditableText(text: String) =
      SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(text))

  /**
   * Reports the session through [mutableAuthState]: a StateFlow by default, or a
   * `MutableSharedFlow(replay = 1)` that reports nothing until the test emits, like a slow start-up
   * check.
   */
  private class FakeAuthRepository(
      private val mutableAuthState: MutableSharedFlow<AuthUser?> = MutableStateFlow(null)
  ) : AuthRepository {
    constructor(initialUser: AuthUser?) : this(MutableStateFlow(initialUser))

    override val authState: Flow<AuthUser?> = mutableAuthState

    /** The last user reported by [authState], or null if none was reported. */
    val currentUser: AuthUser?
      get() = mutableAuthState.replayCache.lastOrNull()

    var signInCalls = 0
    var signUpCalls = 0
    var signOutResult: CompletableDeferred<AuthResult<Unit>>? = null
    var signOutFailure: Exception? = null
    var signUpResult: CompletableDeferred<AuthResult<AuthUser>>? = null

    override suspend fun signUpWithEmail(
        email: String,
        password: String,
        username: String,
    ): AuthResult<AuthUser> {
      signUpCalls++
      signUpResult?.let {
        val result = it.await()
        if (result is AuthResult.Success) mutableAuthState.tryEmit(result.value)
        return result
      }
      mutableAuthState.tryEmit(TEST_USER)
      return AuthResult.Success(TEST_USER)
    }

    override suspend fun signInWithEmail(email: String, password: String): AuthResult<AuthUser> {
      signInCalls++
      mutableAuthState.tryEmit(TEST_USER)
      return AuthResult.Success(TEST_USER)
    }

    override suspend fun signInWithGoogle(idToken: String): AuthResult<AuthUser> {
      mutableAuthState.tryEmit(TEST_USER)
      return AuthResult.Success(TEST_USER)
    }

    override suspend fun signOut(): AuthResult<Unit> {
      signOutFailure?.let { throw it }
      signOutResult?.let {
        val result = it.await()
        if (result is AuthResult.Success) mutableAuthState.tryEmit(null)
        return result
      }
      mutableAuthState.tryEmit(null)
      return AuthResult.Success(Unit)
    }

    /** Ends the session without a sign-out request, as an expired or revoked session does. */
    fun endSession() {
      mutableAuthState.tryEmit(null)
    }

    companion object {
      val TEST_USER = AuthUser(uid = "user-1", email = "hero@example.com", username = "Ranger")
    }
  }

  private class FakeSearchViewModel(private val file: JDRFile) : DocumentSearchInterfaceVM {
    private val mutableUiState = MutableStateFlow(DocumentSearchUiState())
    override val uiState: StateFlow<DocumentSearchUiState> = mutableUiState

    override fun onQueryChanged(query: String) {
      mutableUiState.value =
          DocumentSearchUiState(
              query = query,
              results = listOf(DocumentSearchResult(file, emptyList())),
              status = DocumentSearchStatus.Results,
          )
    }

    override fun clearQuery() {
      mutableUiState.value = DocumentSearchUiState()
    }

    override fun retry() = Unit
  }

  private class FakeGroupRepository : GroupRepository by UnusedGroupRepository {
    override fun getUserGroups(userId: String) = flowOf(emptyList<CampaignGroup>())
  }

  private class FakeWorkspaceRepository(private val files: List<JDRFile>) :
      WorkspaceRepository by UnusedWorkspaceRepository {
    override suspend fun getFile(fileId: String): Result<JDRFile?> =
        Result.success(files.firstOrNull { it.id == fileId })

    override fun getPersonalFolderFiles(folderId: String, ownerId: String) =
        flowOf(files.filter { it.ownerId == ownerId && it.personalParentId == folderId })
  }

  private companion object {
    const val SIGN_IN_SCROLL_CONTAINER = "sign_in_scroll_container"
    const val SIGN_UP_SCROLL_CONTAINER = "sign_up_scroll_container"
    const val PERSONAL_SPACE_TITLE = "My Space"

    val UnusedGroupRepository =
        object : GroupRepository {
          override fun getNewGroupId() = error("Not used")

          override suspend fun createGroup(group: CampaignGroup) = error("Not used")

          override suspend fun updateGroup(group: CampaignGroup) = error("Not used")

          override suspend fun deleteGroup(groupId: String) = error("Not used")

          override suspend fun getGroup(groupId: String) = error("Not used")

          override suspend fun addUserToGroup(groupId: String, userId: String) = error("Not used")

          override suspend fun removeUserFromGroup(groupId: String, userId: String) =
              error("Not used")

          override fun getGroupFlow(groupId: String) = error("Not used")

          override fun getUserGroups(userId: String) = error("Not used")

          override suspend fun createInvitation(groupId: String, gameMasterId: String) =
              error("Not used")

          override suspend fun regenerateInvitation(groupId: String, gameMasterId: String) =
              error("Not used")

          override suspend fun joinGroupWithCode(code: String, userId: String) = error("Not used")
        }

    val UnusedWorkspaceRepository =
        object : WorkspaceRepository {
          override fun getNewUid() = error("Not used")

          override suspend fun createFile(file: JDRFile, waitForServer: Boolean) = error("Not used")

          override suspend fun updateFile(edit: FileEdit, waitForServer: Boolean) =
              error("Not used")

          override suspend fun deleteFile(fileId: String) = error("Not used")

          override suspend fun getFile(fileId: String) = error("Not used")

          override suspend fun removeItemFromGroup(itemId: String, groupId: String) =
              error("Not used")

          override suspend fun addItemToGroup(
              itemId: String,
              groupId: String,
              folderId: String,
              isHidden: Boolean,
              includeContent: Boolean,
          ) = error("Not used")

          override suspend fun hasPendingWrites(): Boolean = false

          override suspend fun setItemHidden(itemId: String, groupId: String, isHidden: Boolean) =
              error("Not used")

          override fun getFileFlow(fileId: String) = error("Not used")

          override fun getPersonalFolderFiles(folderId: String, ownerId: String) = error("Not used")

          override fun getGroupFolderFiles(folderId: String, groupId: String) = error("Not used")
        }
  }
}
