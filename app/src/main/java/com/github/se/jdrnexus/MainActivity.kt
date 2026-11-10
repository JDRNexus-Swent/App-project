// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus

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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.credentials.CredentialManager
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.github.se.jdrnexus.model.personalSpace.DocumentType
import com.github.se.jdrnexus.model.personalSpace.GroupRepositoryFirestore
import com.github.se.jdrnexus.model.personalSpace.JDRFile
import com.github.se.jdrnexus.model.repository.AuthRepositoryFirebase
import com.github.se.jdrnexus.resources.C
import com.github.se.jdrnexus.resources.showComingSoon
import com.github.se.jdrnexus.ui.authentication.AuthViewModel
import com.github.se.jdrnexus.ui.authentication.SignInScreen
import com.github.se.jdrnexus.ui.authentication.SignUpScreen
import com.github.se.jdrnexus.ui.dice.DiceOfflineScreen
import com.github.se.jdrnexus.ui.edition.EditFileCharacter
import com.github.se.jdrnexus.ui.edition.EditFileLore
import com.github.se.jdrnexus.ui.gameManagement.GameManagementScreen
import com.github.se.jdrnexus.ui.home.HomeScreen
import com.github.se.jdrnexus.ui.jdrfile.EditFileViewModel
import com.github.se.jdrnexus.ui.map.MapScreen
import com.github.se.jdrnexus.ui.map.MapViewModel
import com.github.se.jdrnexus.ui.theme.SampleAppTheme
import com.github.se.jdrnexus.viewmodel.GameManagementViewModel
import com.github.se.jdrnexus.viewmodel.PersonalSpaceViewModel
import com.google.firebase.firestore.FirebaseFirestore

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
  Dice,
  GroupManagement,
  EditChar,
  EditLore,
  Map,
}

/**
 * Optional ViewModel injection lets navigation tests use the production screens with deterministic
 * fake repositories instead of Firebase.
 */
@Composable
fun JDRNexusApp(
    authViewModel: AuthViewModel? = null,
    gameManagementViewModel: GameManagementViewModel? = null,
    personalSpaceViewModel: PersonalSpaceViewModel? = null,
    editFileViewModel: EditFileViewModel? = null,
) {
  val activeAuthViewModel = authViewModel ?: viewModel(factory = AuthViewModel.Factory)
  val context = LocalContext.current
  val credentialManager = remember(context) { CredentialManager.create(context) }
  val navController = rememberNavController()
  val navigation = rememberNavigationHelper(navController)

  // Create an editFileViewModel to be able to retreive the file for the edition screens
  val activeEditFileViewModel = editFileViewModel ?: viewModel()

  Scaffold(
      containerColor = MaterialTheme.colorScheme.background,
      // Let each destination handle its own system-bar insets.
      contentWindowInsets = WindowInsets(0),
  ) { innerPadding ->
    NavHost(
        navController = navController,
        // Use instead of deriving it from the current destination.
        startDestination = AllRoutes.SignIn.name,
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
        val onDocumentClicked: (JDRFile) -> Unit = { document ->
          activeEditFileViewModel.loadFile(document.id)
          when (document.type) {
            DocumentType.CHARACTER -> navigation.navigate(AllRoutes.EditChar.name)
            DocumentType.TEXT -> navigation.navigate(AllRoutes.EditLore.name)
            DocumentType.FOLDER -> Unit
            DocumentType.GROUP_ROOT -> Unit
          }
        }

        PersonalSpaceScreen(
            onBackButton = { navigation.popBackStack() },
            personalSpaceViewModel = personalSpaceViewModel ?: viewModel(),
            onDocumentClicked = onDocumentClicked,
            onCreateFile = { fileId ->
              activeEditFileViewModel.loadFile(fileId)
              navigation.navigate(AllRoutes.EditChar.name)
            },
            onTabSelected = { tab ->
              when (tab) {
                "Home" -> navigation.popBackStack(AllRoutes.Home.name, false)
                "Campaigns" -> navigation.navigate(AllRoutes.GroupManagement.name)
                "Files" -> Unit
                "Map" -> navController.navigate(AllRoutes.Map.name)
              }
            },
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
                                groupRepository =
                                    GroupRepositoryFirestore(FirebaseFirestore.getInstance()),
                                authRepository = AuthRepositoryFirebase(),
                            )
                          }
                        }
                )
        GameManagementScreen(
            gameManagementViewModel = activeGameManagementViewModel,
            onEnterWorkspace = { showComingSoon(context, "Workspace") },
            onManageGroup = { showComingSoon(context, "Group management") },
            onTabSelected = { tab ->
              when (tab) {
                "Home" -> navigation.popBackStack(AllRoutes.Home.name, inclusive = false)
                "Campaigns" -> Unit
                "Files" -> navigation.navigate(AllRoutes.PersoSpace.name)
                "Map" -> navController.navigate(AllRoutes.Map.name)
              }
            },
        )
      }

      composable(route = AllRoutes.Map.name) {
        MapScreen(
            mapVM = viewModel<MapViewModel>(factory = MapViewModel.Factory),
            onTabSelected = { tab ->
              when (tab) {
                "Home" -> navController.popBackStack(AllRoutes.Home.name, false)
                "Campaigns" -> navController.navigate(AllRoutes.GroupManagement.name)
                "Files" -> navController.navigate(AllRoutes.PersoSpace.name)
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

@Preview(showBackground = true)
@Composable
fun AppPreview() {
  SampleAppTheme { JDRNexusApp() }
}
