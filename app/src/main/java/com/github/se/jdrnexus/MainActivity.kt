// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.github.se.jdrnexus.model.connectivity.ConnectivityRepository
import com.github.se.jdrnexus.model.connectivity.ConnectivityRepositoryProvider
import com.github.se.jdrnexus.model.gameManagement.GameSessionRepository
import com.github.se.jdrnexus.model.gameManagement.GameSessionRepositoryFirestore
import com.github.se.jdrnexus.model.personalSpace.DocumentType
import com.github.se.jdrnexus.model.personalSpace.GroupRepository
import com.github.se.jdrnexus.model.personalSpace.GroupRepositoryFirestore
import com.github.se.jdrnexus.model.personalSpace.JDRFile
import com.github.se.jdrnexus.model.personalSpace.WorkspaceRepository
import com.github.se.jdrnexus.model.personalSpace.WorkspaceRepositoryProvider
import com.github.se.jdrnexus.model.repository.AuthRepository
import com.github.se.jdrnexus.model.repository.AuthRepositoryFirebase
import com.github.se.jdrnexus.model.repository.DiceRepositoryFirestore
import com.github.se.jdrnexus.resources.C
import com.github.se.jdrnexus.resources.LocalIsOnline
import com.github.se.jdrnexus.resources.showComingSoon
import com.github.se.jdrnexus.ui.authentication.AuthViewModel
import com.github.se.jdrnexus.ui.authentication.SignInScreen
import com.github.se.jdrnexus.ui.authentication.SignUpScreen
import com.github.se.jdrnexus.ui.dice.DiceOfflineScreen
import com.github.se.jdrnexus.ui.edition.EditFileCharacter
import com.github.se.jdrnexus.ui.edition.EditFileLore
import com.github.se.jdrnexus.ui.game.GamePlayerViewModel
import com.github.se.jdrnexus.ui.game.GameScreenPlayer
import com.github.se.jdrnexus.ui.gameManagement.GameManagementScreen
import com.github.se.jdrnexus.ui.gameManagement.GameScreenGM
import com.github.se.jdrnexus.ui.gameManagement.GameViewModelGMViewModel
import com.github.se.jdrnexus.ui.groupWorkSpace.GroupWorkSpaceScreen
import com.github.se.jdrnexus.ui.groupWorkSpace.GroupWorkspaceViewModel
import com.github.se.jdrnexus.ui.home.HomeScreen
import com.github.se.jdrnexus.ui.jdrfile.EditFileViewModel
import com.github.se.jdrnexus.ui.map.MapScreen
import com.github.se.jdrnexus.ui.map.MapViewModel
import com.github.se.jdrnexus.ui.search.DocumentSearchInterfaceVM
import com.github.se.jdrnexus.ui.search.DocumentSearchScope
import com.github.se.jdrnexus.ui.search.DocumentSearchScreen
import com.github.se.jdrnexus.ui.search.DocumentSearchViewModel
import com.github.se.jdrnexus.ui.theme.SampleAppTheme
import com.github.se.jdrnexus.viewmodel.GameManagementViewModel
import com.github.se.jdrnexus.viewmodel.GroupRole
import com.github.se.jdrnexus.viewmodel.PersonalSpaceViewModel
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
  Map,
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

/** Optional dependencies let navigation tests use production screens with deterministic fakes. */
@Composable
fun JDRNexusApp(
    authViewModel: AuthViewModel? = null,
    startDestination: AllRoutes = AllRoutes.SignIn,
    gameManagementViewModel: GameManagementViewModel? = null,
    personalSpaceViewModel: PersonalSpaceViewModel? = null,
    editFileViewModel: EditFileViewModel? = null,
    workspaceRepository: WorkspaceRepository? = null,
    authRepository: AuthRepository? = null,
    groupRepository: GroupRepository? = null,
    personalSearchViewModel: DocumentSearchInterfaceVM? = null,
    groupSearchViewModel: DocumentSearchInterfaceVM? = null,
    gameSessionRepository: GameSessionRepository? = null,
    connectivityRepository: ConnectivityRepository? = null,
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
  val openDocument: (JDRFile, String?) -> Unit = { document, groupId ->
    activeEditFileViewModel.loadFile(document.id, groupId)
    when (document.type) {
      DocumentType.CHARACTER -> navigation.navigate(AllRoutes.EditChar.name)
      DocumentType.TEXT -> navigation.navigate(AllRoutes.EditLore.name)
      DocumentType.FOLDER,
      DocumentType.GROUP_ROOT -> Unit
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

  CompositionLocalProvider(LocalIsOnline provides isOnline) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        // Let each destination handle its own system-bar insets.
        contentWindowInsets = WindowInsets(0),
    ) { innerPadding ->
      NavHost(
          navController = navController,
          // Use instead of deriving it from the current destination.
          startDestination = startDestination.name,
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
          val onLogout = {
            navigation.navigate(AllRoutes.SignIn.name) {
              // Sign-in may no longer be on the stack after authentication succeeds.
              popUpTo(AllRoutes.Home.name) { inclusive = true }
              launchSingleTop = true
            }
          }
          val onPersonalSpaceClicked = { navigation.navigate(AllRoutes.PersoSpace.name) }
          val onDiceClicked = { navigation.navigate(AllRoutes.Dice.name) }
          val onGroupManagementClicked = { navigation.navigate(AllRoutes.GroupManagement.name) }
          val onMapClicked = { navigation.navigate(AllRoutes.Map.name) }
          HomeScreen(
              viewModel = activeAuthViewModel,
              onLogout = onLogout,
              onPersonalSpaceClick = onPersonalSpaceClicked,
              onDiceClick = onDiceClicked,
              onGroupManagementClick = onGroupManagementClicked,
              onMapClick = onMapClicked,
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

        composable(route = AllRoutes.Search.name) {
          DocumentSearchScreen(
              onBack = { navigation.popBackStack() },
              onDocumentClicked = { file -> openDocument(file, null) },
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
              onManageGroup = { showComingSoon(context, "Group management") },
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
              viewModel(
                  factory =
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
              )
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
          DocumentSearchScreen(
              onBack = { navigation.popBackStack() },
              onDocumentClicked = { file -> openDocument(file, groupId) },
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
      }
    }
  }
}

@Preview(showBackground = true)
@Composable
fun AppPreview() {
  SampleAppTheme { JDRNexusApp() }
}
