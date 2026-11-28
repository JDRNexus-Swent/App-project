// Co-authored-by: Codex <codex@openai.com>
// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.github.se.jdrnexus.model.connectivity.ConnectivityRepository
import com.github.se.jdrnexus.model.connectivity.ConnectivityRepositoryProvider
import com.github.se.jdrnexus.model.gameManagement.GameSessionRepository
import com.github.se.jdrnexus.model.gameManagement.GameSessionRepositoryFirestore
import com.github.se.jdrnexus.model.notification.Notification
import com.github.se.jdrnexus.model.notification.NotificationRepositoryProvider
import com.github.se.jdrnexus.model.personalSpace.DocumentType
import com.github.se.jdrnexus.model.personalSpace.GroupRepository
import com.github.se.jdrnexus.model.personalSpace.GroupRepositoryFirestore
import com.github.se.jdrnexus.model.personalSpace.JDRFile
import com.github.se.jdrnexus.model.personalSpace.WorkspaceRepository
import com.github.se.jdrnexus.model.personalSpace.WorkspaceRepositoryProvider
import com.github.se.jdrnexus.model.repository.AuthRepository
import com.github.se.jdrnexus.model.repository.AuthRepositoryFirebase
import com.github.se.jdrnexus.model.repository.DiceRepositoryFirestore
import com.github.se.jdrnexus.model.soundboard.Media3SoundboardPlayer
import com.github.se.jdrnexus.model.soundboard.SoundboardRepositoryProvider
import com.github.se.jdrnexus.resources.C
import com.github.se.jdrnexus.resources.LocalIsOnline
import com.github.se.jdrnexus.resources.showComingSoon
import com.github.se.jdrnexus.ui.authentication.AuthViewModel
import com.github.se.jdrnexus.ui.authentication.SessionState
import com.github.se.jdrnexus.ui.authentication.SignInScreen
import com.github.se.jdrnexus.ui.authentication.SignUpScreen
import com.github.se.jdrnexus.ui.dice.DiceOfflineScreen
import com.github.se.jdrnexus.ui.edition.EditFileCharacter
import com.github.se.jdrnexus.ui.edition.EditFileLore
import com.github.se.jdrnexus.ui.game.GamePlayerViewModel
import com.github.se.jdrnexus.ui.game.GameScreenPlayer
import com.github.se.jdrnexus.ui.gameManagement.campaigns.GameManagementScreen
import com.github.se.jdrnexus.ui.gameManagement.gm.GameScreenGM
import com.github.se.jdrnexus.ui.gameManagement.gm.GameViewModelGMViewModel
import com.github.se.jdrnexus.ui.gameManagement.groups.GroupManagementScreen
import com.github.se.jdrnexus.ui.gameManagement.groups.GroupManagementViewModel
import com.github.se.jdrnexus.ui.groupWorkSpace.GroupWorkSpaceScreen
import com.github.se.jdrnexus.ui.groupWorkSpace.GroupWorkspaceViewModel
import com.github.se.jdrnexus.ui.home.HomeScreen
import com.github.se.jdrnexus.ui.jdrfile.EditFileViewModel
import com.github.se.jdrnexus.ui.map.MapScreen
import com.github.se.jdrnexus.ui.map.MapViewModel
import com.github.se.jdrnexus.ui.notification.NotificationDestination
import com.github.se.jdrnexus.ui.notification.NotificationScreen
import com.github.se.jdrnexus.ui.notification.destination
import com.github.se.jdrnexus.ui.profile.ProfileScreen
import com.github.se.jdrnexus.ui.search.DocumentSearchInterfaceVM
import com.github.se.jdrnexus.ui.search.DocumentSearchScope
import com.github.se.jdrnexus.ui.search.DocumentSearchScreen
import com.github.se.jdrnexus.ui.search.DocumentSearchViewModel
import com.github.se.jdrnexus.ui.soundboard.SoundboardScreen
import com.github.se.jdrnexus.ui.soundboard.SoundboardViewModel
import com.github.se.jdrnexus.ui.soundboard.SoundboardViewModelInterface
import com.github.se.jdrnexus.ui.theme.SampleAppTheme
import com.github.se.jdrnexus.viewmodel.GameManagementViewModel
import com.github.se.jdrnexus.viewmodel.GroupRole
import com.github.se.jdrnexus.viewmodel.NotificationViewModel
import com.github.se.jdrnexus.viewmodel.NotificationViewModelInterface
import com.github.se.jdrnexus.viewmodel.PersonalSpaceViewModel
import com.github.se.jdrnexus.viewmodel.ProfileViewModel
import com.github.se.jdrnexus.viewmodel.ProfileViewModelInterface
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      SampleAppTheme {
        Surface(
            modifier = Modifier.fillMaxSize().semantics { testTag = C.Tag.main_screen_container },
            color = MaterialTheme.colorScheme.background,
        ) {
          JDRNexusApp()
        }
      }
    }
  }
}

enum class AllRoutes {
  SignIn,
  SignUp,
  Home,
  PersoSpace,
  Search,
  Dice,
  GroupManagement,
  EditChar,
  EditLore,
  Profile,
  Map,
  Notifications,
  Soundboard,
}

object GroupWorkspaceRoute {
  const val route = "group_workspace/{groupId}"

  const val groupIdArgument = "groupId"

  fun createRoute(groupId: String): String {
    return "group_workspace/${Uri.encode(groupId)}"
  }
}

object GroupDocumentSearchRoute {
  const val route = "group_document_search/{groupId}"
  const val groupIdArgument = "groupId"

  fun createRoute(groupId: String): String = "group_document_search/${Uri.encode(groupId)}"
}

/** Route of the Game Master's game screen for one group. */
object GameMasterRoute {
  const val route = "game_master/{groupId}"
  const val groupIdArgument = "groupId"

  /** Builds the route opening the Game Master's game screen of [groupId]. */
  fun createRoute(groupId: String): String = "game_master/${Uri.encode(groupId)}"
}

/** Route of the player's game screen for one group. */
object GamePlayerRoute {
  const val route = "game_player/{groupId}"
  const val groupIdArgument = "groupId"

  /** Builds the route opening the player's game screen of [groupId]. */
  fun createRoute(groupId: String): String = "game_player/${Uri.encode(groupId)}"
}

/**
 * Route of the screen where a Game Master manages one group (members, removal, deletion). The list
 * of the user's groups (Campaigns) is [AllRoutes.GroupManagement].
 */
object ManageGroupRoute {
  const val route = "manage_group/{groupId}"
  const val groupIdArgument = "groupId"

  /** Builds the route opening the management screen of [groupId]. */
  fun createRoute(groupId: String): String = "manage_group/${Uri.encode(groupId)}"
}

/** The first screen for the session found at launch, or null while it is still being checked. */
internal fun launchRouteFor(session: SessionState): AllRoutes? =
    when (session) {
      SessionState.Checking -> null
      SessionState.SignedIn -> AllRoutes.Home
      SessionState.SignedOut -> AllRoutes.SignIn
    }

/**
 * Optional dependencies let navigation tests use production screens with deterministic fakes.
 *
 * Without a [startDestination], the app opens Home for a user still signed in from a previous
 * launch and SignIn otherwise, showing a loading indicator until the session is known.
 */
@Composable
fun JDRNexusApp(
    authViewModel: AuthViewModel? = null,
    startDestination: AllRoutes? = null,
    gameManagementViewModel: GameManagementViewModel? = null,
    personalSpaceViewModel: PersonalSpaceViewModel? = null,
    editFileViewModel: EditFileViewModel? = null,
    workspaceRepository: WorkspaceRepository? = null,
    authRepository: AuthRepository? = null,
    groupRepository: GroupRepository? = null,
    personalSearchViewModel: DocumentSearchInterfaceVM? = null,
    groupSearchViewModel: DocumentSearchInterfaceVM? = null,
    notificationViewModel: NotificationViewModelInterface? = null,
    gameSessionRepository: GameSessionRepository? = null,
    connectivityRepository: ConnectivityRepository? = null,
    profileViewModel: ProfileViewModelInterface? = null,
    soundboardViewModelFactory: ViewModelProvider.Factory? = null,
) {
  val activeAuthViewModel = authViewModel ?: viewModel(factory = AuthViewModel.Factory)
  val activeAuthRepository = authRepository ?: AuthRepositoryFirebase()
  val activeGroupRepository =
      groupRepository ?: GroupRepositoryFirestore(FirebaseFirestore.getInstance())
  val activeWorkspaceRepository = workspaceRepository ?: WorkspaceRepositoryProvider.repository
  val context = LocalContext.current
  val credentialManager = remember(context) { CredentialManager.create(context) }
  val navController = rememberNavController()
  val navigation = rememberNavigationHelper(navController)
  // Every screen reads the connection through LocalIsOnline to show the offline banner, and the
  // ViewModels of the screens with server-only actions read it to disable them.
  val activeConnectivityRepository =
      connectivityRepository
          ?: remember(context) { ConnectivityRepositoryProvider.repository(context) }

  // Create an editFileViewModel to be able to retrieve the file for the edition screens
  val activeEditFileViewModel =
      editFileViewModel
          ?: viewModel(
              factory =
                  viewModelFactory {
                    initializer {
                      EditFileViewModel(
                          workspaceRepository = activeWorkspaceRepository,
                          groupRepository = activeGroupRepository,
                          authRepository = activeAuthRepository,
                      )
                    }
                  }
          )
  val openProfile = { navigation.navigate(AllRoutes.Profile.name) { launchSingleTop = true } }
  val activeNotificationViewModel =
      notificationViewModel
          ?: viewModel<NotificationViewModel>(
              factory =
                  viewModelFactory {
                    initializer {
                      NotificationViewModel(
                          repository = NotificationRepositoryProvider.repository,
                          authRepository = activeAuthRepository,
                      )
                    }
                  }
          )
  val openDocument: (JDRFile, String?) -> Unit = { document, groupId ->
    activeEditFileViewModel.loadFile(document.id, groupId)
    when (document.type) {
      DocumentType.CHARACTER -> navigation.navigate(AllRoutes.EditChar.name)
      DocumentType.TEXT -> navigation.navigate(AllRoutes.EditLore.name)
      DocumentType.FOLDER,
      DocumentType.GROUP_ROOT,
      DocumentType.IMAGE -> Unit
    }
  }
  val openNotification: (Notification) -> Unit = { notification ->
    when (val destination = notification.destination()) {
      is NotificationDestination.GroupWorkspace ->
          navigation.navigate(GroupWorkspaceRoute.createRoute(destination.groupId)) {
            launchSingleTop = true
          }
      NotificationDestination.Campaigns ->
          navigation.navigate(AllRoutes.GroupManagement.name) { launchSingleTop = true }
      null -> Unit
    }
  }
  // Shared by the group workspace and its search, which reuses the workspace's ViewModel.
  val groupWorkspaceViewModelFactory: (String) -> ViewModelProvider.Factory = { groupId ->
    viewModelFactory {
      initializer {
        GroupWorkspaceViewModel(
            groupId = groupId,
            groupRepository = activeGroupRepository,
            repository = activeWorkspaceRepository,
            authRepository = activeAuthRepository,
            connectivityRepository = activeConnectivityRepository,
        )
      }
    }
  }
  val onGameTabSelected: (String) -> Unit = { tab ->
    when (tab) {
      "Home" -> navigation.popBackStack(AllRoutes.Home.name, inclusive = false)
      "Campaigns" -> navigation.popBackStack(AllRoutes.GroupManagement.name, inclusive = false)
      "Files" -> navigation.navigate(AllRoutes.PersoSpace.name)
      "Map" -> navigation.navigate(AllRoutes.Map.name)
    }
  }

  // Collected only while the app is started, so the network callback is released in the
  // background; starts as online, so no banner flashes before the first value.
  val isOnline by
      activeConnectivityRepository.isOnline.collectAsStateWithLifecycle(initialValue = true)

  val launchSession by activeAuthViewModel.launchSession.collectAsStateWithLifecycle()
  val launchRoute = startDestination ?: launchRouteFor(launchSession)

  // A session that ends while the user is in the app (sign-out, expired token, deleted account)
  // leads back to SignIn, with nothing left to return to. This is the only path back to SignIn,
  // the Log out button included. Keyed on the route too, so the end is
  // handled once the NavHost has a destination, however late this effect starts.
  val sessionEnded by activeAuthViewModel.sessionEnded.collectAsStateWithLifecycle()
  val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
  LaunchedEffect(sessionEnded, currentRoute) {
    if (sessionEnded && currentRoute != null) {
      navController.showSignInAfterSessionEnded(currentRoute)
      activeAuthViewModel.onSessionEndHandled()
    }
  }

  CompositionLocalProvider(LocalIsOnline provides isOnline) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        // Let each destination handle its own system-bar insets.
        contentWindowInsets = WindowInsets(0),
    ) { innerPadding ->
      if (launchRoute == null) {
        // Shown instead of SignIn so a signed-in user never sees it flash before Home.
        Box(
            modifier = Modifier.fillMaxSize().padding(innerPadding).testTag(C.Tag.session_loading),
            contentAlignment = Alignment.Center,
        ) {
          CircularProgressIndicator()
        }
        return@Scaffold
      }
      NavHost(
          navController = navController,
          // Fixed for the app's lifetime: later sign-ins and sign-outs navigate instead.
          startDestination = launchRoute.name,
          modifier = Modifier.fillMaxSize().padding(innerPadding),
      ) {
        composable(route = AllRoutes.SignIn.name) {
          val onSignUp = {
            navigation.navigate(AllRoutes.SignUp.name) {
              popUpTo(AllRoutes.SignIn.name)
              launchSingleTop = true
            }
          }
          val onSignInSuccess = {
            navigation.navigate(AllRoutes.Home.name) {
              // Authenticated users should not return to either auth screen with Back.
              popUpTo(AllRoutes.SignIn.name) { inclusive = true }
              launchSingleTop = true
            }
          }
          SignInScreen(
              signInViewModel = activeAuthViewModel,
              credentialManager = credentialManager,
              onSignUp = onSignUp,
              onSignInSuccess = onSignInSuccess,
          )
        }

        composable(route = AllRoutes.Home.name) {
          // No onLogout: a successful sign-out ends the session, and the session-end effect above
          // is the one place that leads back to SignIn.
          val onPersonalSpaceClicked = { navigation.navigate(AllRoutes.PersoSpace.name) }
          val onDiceClicked = { navigation.navigate(AllRoutes.Dice.name) }
          val onGroupManagementClicked = { navigation.navigate(AllRoutes.GroupManagement.name) }
          val onMapClicked = { navigation.navigate(AllRoutes.Map.name) }
          val notificationState by activeNotificationViewModel.notificationUiState.collectAsState()
          HomeScreen(
              viewModel = activeAuthViewModel,
              onPersonalSpaceClick = onPersonalSpaceClicked,
              onDiceClick = onDiceClicked,
              onSoundboardClick = { navigation.navigate(AllRoutes.Soundboard.name) },
              onGroupManagementClick = onGroupManagementClicked,
              onMapClick = onMapClicked,
              onProfileClick = openProfile,
              unreadNotificationCount = notificationState.unreadCount,
              onNotificationsClick = {
                navigation.navigate(AllRoutes.Notifications.name) { launchSingleTop = true }
              },
          )
        }

        composable(route = AllRoutes.Notifications.name) {
          NotificationScreen(
              viewModel = activeNotificationViewModel,
              onBack = { navigation.popBackStack() },
              onOpenNotification = openNotification,
              onTabSelected = { tab ->
                when (tab) {
                  "Home" -> navigation.popBackStack(AllRoutes.Home.name, false)
                  "Campaigns" -> navigation.navigate(AllRoutes.GroupManagement.name)
                  "Files" -> navigation.navigate(AllRoutes.PersoSpace.name)
                  "Map" -> navigation.navigate(AllRoutes.Map.name)
                }
              },
          )
        }

        composable(route = AllRoutes.SignUp.name) {
          val onRegister = {
            navigation.navigate(AllRoutes.Home.name) {
              // The signed-in back stack starts at Home, not the registration flow.
              popUpTo(AllRoutes.SignIn.name) { inclusive = true }
              launchSingleTop = true
            }
          }
          // Return to the existing SignIn entry rather than pushing a duplicate.
          val onSignInScreen: () -> Unit = {
            navigation.popBackStack(AllRoutes.SignIn.name, inclusive = false)
          }
          SignUpScreen(
              signUpViewModel = activeAuthViewModel,
              onRegister = onRegister,
              onSignInScreen = onSignInScreen,
          )
        }

        composable(route = AllRoutes.PersoSpace.name) {
          PersonalSpaceScreen(
              onBackButton = { navigation.popBackStack() },
              personalSpaceViewModel = personalSpaceViewModel ?: viewModel(),
              onDocumentClicked = { document -> openDocument(document, null) },
              onCreateFile = { fileId ->
                activeEditFileViewModel.loadFile(fileId)
                navigation.navigate(AllRoutes.EditChar.name)
              },
              onTabSelected = { tab ->
                when (tab) {
                  "Home" -> navigation.popBackStack(AllRoutes.Home.name, false)
                  "Campaigns" -> navigation.navigate(AllRoutes.GroupManagement.name)
                  "Files" -> Unit
                  "Map" -> navigation.navigate(AllRoutes.Map.name)
                }
              },
              onSearchClicked = { navigation.navigate(AllRoutes.Search.name) },
          )
        }

        composable(route = AllRoutes.Search.name) { backStackEntry ->
          // The search is opened from the Personal Space, whose ViewModel opens folder results.
          val personalSpaceEntry =
              remember(backStackEntry) {
                navController.backStackEntryOrNull(AllRoutes.PersoSpace.name)
              }
          // Null when the search was not opened from the Personal Space.
          val workspaceViewModel = personalSpaceEntry?.let {
            personalSpaceViewModel ?: viewModel<PersonalSpaceViewModel>(it)
          }
          DocumentSearchScreen(
              onBack = { navigation.popBackStack() },
              onDocumentClicked = { file -> openDocument(file, null) },
              onFolderClicked = { result ->
                if (workspaceViewModel != null) {
                  workspaceViewModel.openFolderAt(result.file, result.folderIds, result.folderPath)
                  navigation.popBackStack()
                } else {
                  navController.replaceSearchWith(AllRoutes.PersoSpace.name, backStackEntry)
                }
              },
              rootPathLabel = stringResource(R.string.search_personal_space_path),
              searchViewModel = personalSearchViewModel ?: viewModel<DocumentSearchViewModel>(),
          )
        }

        composable(route = AllRoutes.Dice.name) {
          DiceOfflineScreen(onClose = { navigation.popBackStack() })
        }

        composable(route = AllRoutes.GroupManagement.name) {
          val activeGameManagementViewModel =
              gameManagementViewModel
                  ?: viewModel(
                      factory =
                          viewModelFactory {
                            initializer {
                              GameManagementViewModel(
                                  groupRepository = activeGroupRepository,
                                  authRepository = activeAuthRepository,
                                  connectivityRepository = activeConnectivityRepository,
                              )
                            }
                          }
                  )
          GameManagementScreen(
              gameManagementViewModel = activeGameManagementViewModel,
              onEnterWorkspace = { groupId ->
                navigation.navigate(GroupWorkspaceRoute.createRoute(groupId)) {
                  launchSingleTop = true
                }
              },
              onPlay = { groupId, role ->
                val route =
                    when (role) {
                      GroupRole.GAME_MASTER -> GameMasterRoute.createRoute(groupId)
                      GroupRole.PLAYER -> GamePlayerRoute.createRoute(groupId)
                    }
                navigation.navigate(route) { launchSingleTop = true }
              },
              onManageGroup = { groupId ->
                navigation.navigate(ManageGroupRoute.createRoute(groupId)) {
                  launchSingleTop = true
                }
              },
              onProfileClick = openProfile,
              onTabSelected = { tab ->
                when (tab) {
                  "Home" -> navigation.popBackStack(AllRoutes.Home.name, inclusive = false)
                  "Campaigns" -> Unit
                  "Files" -> navigation.navigate(AllRoutes.PersoSpace.name)
                  "Map" -> navigation.navigate(AllRoutes.Map.name)
                }
              },
          )
        }

        composable(route = ManageGroupRoute.route) { backStackEntry ->
          val groupId =
              requireNotNull(backStackEntry.arguments?.getString(ManageGroupRoute.groupIdArgument))
          val groupManagementViewModel: GroupManagementViewModel =
              viewModel(
                  factory =
                      viewModelFactory {
                        initializer {
                          GroupManagementViewModel(
                              groupId,
                              activeGroupRepository,
                              activeAuthRepository,
                          )
                        }
                      }
              )
          GroupManagementScreen(
              viewModel = groupManagementViewModel,
              onBack = { navigation.popBackStack() },
              onGroupDeleted = {
                navigation.popBackStack(AllRoutes.GroupManagement.name, inclusive = false)
              },
              onTabSelected = onGameTabSelected,
          )
        }

        composable(route = GameMasterRoute.route) { backStackEntry ->
          val groupId =
              requireNotNull(backStackEntry.arguments?.getString(GameMasterRoute.groupIdArgument))
          val gameViewModel: GameViewModelGMViewModel =
              viewModel(
                  factory =
                      viewModelFactory {
                        initializer {
                          val firestore = FirebaseFirestore.getInstance()
                          GameViewModelGMViewModel(
                              sessionRepository =
                                  gameSessionRepository
                                      ?: GameSessionRepositoryFirestore(
                                          firestore,
                                          DiceRepositoryFirestore(firestore, activeAuthRepository),
                                      ),
                              groupId = groupId,
                          )
                        }
                      }
              )
          // The title stays blank if the group cannot be read; the session stays usable.
          val groupName by
              remember(groupId) {
                    activeGroupRepository
                        .getGroupFlow(groupId)
                        .map { group -> group?.name.orEmpty() }
                        .catch { emit("") }
                  }
                  .collectAsState(initial = "")
          GameScreenGM(
              viewModel = gameViewModel,
              groupName = groupName,
              onBack = { navigation.popBackStack() },
              onTabSelected = onGameTabSelected,
              onDiceClick = { navigation.navigate(AllRoutes.Dice.name) { launchSingleTop = true } },
          )
        }

        composable(route = GamePlayerRoute.route) { backStackEntry ->
          val groupId =
              requireNotNull(backStackEntry.arguments?.getString(GamePlayerRoute.groupIdArgument))
          val gameViewModel: GamePlayerViewModel =
              viewModel(
                  factory =
                      viewModelFactory {
                        initializer {
                          GamePlayerViewModel(
                              workspaceRepository = activeWorkspaceRepository,
                              groupRepository = activeGroupRepository,
                              authRepository = activeAuthRepository,
                          )
                        }
                      }
              )
          GameScreenPlayer(
              groupId = groupId,
              viewModel = gameViewModel,
              onBack = { navigation.popBackStack() },
              onOpenDice = { navigation.navigate(AllRoutes.Dice.name) { launchSingleTop = true } },
              onOpenGroupWorkspace = { id ->
                navigation.navigate(GroupWorkspaceRoute.createRoute(id)) { launchSingleTop = true }
              },
              onTabSelected = onGameTabSelected,
          )
        }

        composable(route = GroupWorkspaceRoute.route) { backStackEntry ->
          val groupId =
              requireNotNull(
                  backStackEntry.arguments?.getString(GroupWorkspaceRoute.groupIdArgument)
              )
          val groupWorkspaceViewModel: GroupWorkspaceViewModel =
              viewModel(factory = groupWorkspaceViewModelFactory(groupId))
          val uiState by groupWorkspaceViewModel.uiState.collectAsState()
          GroupWorkSpaceScreen(
              gwViewModel = groupWorkspaceViewModel,
              groupId = groupId,
              groupName = uiState.groupName,
              personalItems = uiState.personalFiles,
              onBack = { navController.popBackStack() },
              onItemClicked = { file, _ -> openDocument(file, groupId) },
              onCharacterCreated = { fileId ->
                activeEditFileViewModel.loadFile(fileId, groupId)
                navigation.navigate(AllRoutes.EditChar.name)
              },
              onTabSelected = { tab ->
                when (tab) {
                  "Home" -> navigation.popBackStack(AllRoutes.Home.name, false)
                  "Campaigns" -> navigation.popBackStack(AllRoutes.GroupManagement.name, false)
                  "Files" -> navigation.navigate(AllRoutes.PersoSpace.name)
                  "Map" -> navigation.navigate(AllRoutes.Map.name)
                }
              },
              onSearchClicked = {
                navigation.navigate(GroupDocumentSearchRoute.createRoute(groupId))
              },
          )
        }

        composable(
            route = GroupDocumentSearchRoute.route,
            arguments =
                listOf(
                    navArgument(GroupDocumentSearchRoute.groupIdArgument) {
                      type = NavType.StringType
                    }
                ),
        ) { backStackEntry ->
          val groupId =
              requireNotNull(
                  backStackEntry.arguments?.getString(GroupDocumentSearchRoute.groupIdArgument)
              )
          val activeGroupSearchViewModel =
              groupSearchViewModel
                  ?: viewModel<DocumentSearchViewModel>(
                      factory =
                          viewModelFactory {
                            initializer {
                              DocumentSearchViewModel(
                                  scope = DocumentSearchScope.Group(groupId),
                                  repository = activeWorkspaceRepository,
                                  authRepository = activeAuthRepository,
                                  groupRepository = activeGroupRepository,
                              )
                            }
                          }
                  )
          // The search is opened from the group workspace, whose ViewModel opens folder results.
          val workspaceEntry =
              remember(backStackEntry) {
                navController.backStackEntryOrNull(GroupWorkspaceRoute.route)?.takeIf {
                  it.arguments?.getString(GroupWorkspaceRoute.groupIdArgument) == groupId
                }
              }
          // Without the workspace under the search, its own entry holds the ViewModel, which then
          // only names the group root.
          val groupWorkspaceViewModel: GroupWorkspaceViewModel =
              viewModel(
                  workspaceEntry ?: backStackEntry,
                  factory = groupWorkspaceViewModelFactory(groupId),
              )
          val workspaceState by groupWorkspaceViewModel.uiState.collectAsState()
          // A result at the group root is labelled with the group, never with the Personal Space.
          val groupRootLabel =
              workspaceState.groupName.ifBlank {
                stringResource(R.string.search_group_workspace_path)
              }
          DocumentSearchScreen(
              onBack = { navigation.popBackStack() },
              onDocumentClicked = { file -> openDocument(file, groupId) },
              onFolderClicked = { result ->
                if (workspaceEntry != null) {
                  groupWorkspaceViewModel.openFolderAt(result.file.id, result.folderIds)
                  navigation.popBackStack()
                } else {
                  navController.replaceSearchWith(
                      GroupWorkspaceRoute.createRoute(groupId),
                      backStackEntry,
                  )
                }
              },
              rootPathLabel = groupRootLabel,
              searchViewModel = activeGroupSearchViewModel,
          )
        }

        composable(route = AllRoutes.Map.name) {
          MapScreen(
              mapVM = viewModel<MapViewModel>(factory = MapViewModel.Factory),
              onTabSelected = { tab ->
                when (tab) {
                  "Home" -> navigation.popBackStack(AllRoutes.Home.name, false)
                  "Campaigns" -> navigation.navigate(AllRoutes.GroupManagement.name)
                  "Files" -> navigation.navigate(AllRoutes.PersoSpace.name)
                  "Map" -> Unit
                }
              },
              // No game details screen yet.
              onViewGameDetails = { showComingSoon(context, "Game details") },
          )
        }

        composable(route = AllRoutes.Soundboard.name) { backStackEntry ->
          // Home owns the soundboard, so there is one for the whole session: reopening it finds
          // the music still playing, and it stops at logout. The soundboard's own entry is the
          // owner only when Home is not on the stack (tests with another start destination).
          val owner: ViewModelStoreOwner =
              remember(backStackEntry) {
                runCatching { navController.getBackStackEntry(AllRoutes.Home.name) }.getOrNull()
                    ?: backStackEntry
              }
          // Requested as ViewModel so tests can pass a fake through soundboardViewModelFactory;
          // both factories build a SoundboardViewModelInterface.
          val soundboardViewModel =
              viewModel<ViewModel>(
                  viewModelStoreOwner = owner,
                  key = AllRoutes.Soundboard.name,
                  factory =
                      soundboardViewModelFactory
                          ?: viewModelFactory {
                            initializer<ViewModel> {
                              SoundboardViewModel(
                                  SoundboardRepositoryProvider.repository(context),
                                  Media3SoundboardPlayer(context),
                                  activeAuthRepository,
                              )
                            }
                          },
              )
                  as SoundboardViewModelInterface
          SoundboardScreen(
              viewModel = soundboardViewModel,
              soundboardId = null,
              onBack = { navigation.popBackStack() },
              // TODO(#301): open the editor route once a SoundboardEditViewModelInterface
              //  implementation exists.
              onEdit = {},
              onTabSelected = { tab ->
                when (tab) {
                  "Home" -> navigation.popBackStack(AllRoutes.Home.name, inclusive = false)
                  // Opened from Home, Campaigns is not on the stack, so popping would do nothing.
                  "Campaigns" -> navigation.navigate(AllRoutes.GroupManagement.name)
                  else -> onGameTabSelected(tab)
                }
              },
          )
        }

        composable(route = AllRoutes.EditChar.name) {
          EditFileCharacter(
              viewModel = activeEditFileViewModel,
              onBack = { navigation.popBackStack() },
              onSaved = { navigation.popBackStack() },
          )
        }

        composable(route = AllRoutes.EditLore.name) {
          EditFileLore(
              viewModel = activeEditFileViewModel,
              onBack = { navigation.popBackStack() },
              onSaved = { navigation.popBackStack() },
          )
        }

        composable(route = AllRoutes.Profile.name) {
          // Scoped to this entry: each visit reloads the profile and drops unsaved drafts.
          val activeProfileViewModel =
              profileViewModel
                  ?: viewModel<ProfileViewModel>(
                      factory =
                          viewModelFactory {
                            initializer { ProfileViewModel(authRepository = activeAuthRepository) }
                          }
                  )
          ProfileScreen(
              viewModel = activeProfileViewModel,
              onBack = { navigation.popBackStack() },
              onTabSelected = { tab ->
                when (tab) {
                  "Home" -> navigation.popBackStack(AllRoutes.Home.name, inclusive = false)
                  "Campaigns" -> navigation.navigate(AllRoutes.GroupManagement.name)
                  "Files" -> navigation.navigate(AllRoutes.PersoSpace.name)
                  "Map" -> navigation.navigate(AllRoutes.Map.name)
                }
              },
          )
        }
      }
    }
  }
}

/**
 * Opens SignIn and clears the back stack, unless [route], the current one, is an auth screen.
 *
 * Uses the NavController directly: NavigationHelper may ignore navigation during a transition, and
 * this redirect must never be dropped, since the session end is marked as handled right after it.
 */
private fun NavHostController.showSignInAfterSessionEnded(route: String) {
  if (route == AllRoutes.SignIn.name || route == AllRoutes.SignUp.name) return
  navigate(AllRoutes.SignIn.name) {
    popUpTo(graph.id) { inclusive = true }
    launchSingleTop = true
  }
}

@Preview(showBackground = true)
@Composable
fun AppPreview() {
  SampleAppTheme { JDRNexusApp() }
}

/** The topmost back stack entry of [route], or null when [route] is not on the back stack. */
private fun NavHostController.backStackEntryOrNull(route: String): NavBackStackEntry? =
    runCatching {
      getBackStackEntry(route)
    }
    .getOrNull()

/**
 * Opens the workspace at [workspaceRoute] in place of the search at [searchEntry], at its root.
 * Used when a folder result is tapped in a search that was not opened from its workspace, so there
 * is no workspace to open the folder in; the search is always opened from it in the app.
 */
private fun NavHostController.replaceSearchWith(
    workspaceRoute: String,
    searchEntry: NavBackStackEntry,
) {
  navigate(workspaceRoute) {
    popUpTo(searchEntry.destination.id) { inclusive = true }
    launchSingleTop = true
  }
}
