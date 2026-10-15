// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.github.se.jdrnexus

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.github.se.jdrnexus.model.personalSpace.DocumentType
import com.github.se.jdrnexus.model.personalSpace.JDRFile
import com.github.se.jdrnexus.resources.C
import com.github.se.jdrnexus.ui.authentication.AuthViewModel
import com.github.se.jdrnexus.ui.authentication.SignInScreen
import com.github.se.jdrnexus.ui.authentication.SignUpScreen
import com.github.se.jdrnexus.ui.edition.EditFileCharacter
import com.github.se.jdrnexus.ui.home.HomeScreen
import com.github.se.jdrnexus.ui.jdrfile.EditFileViewModel
import com.github.se.jdrnexus.ui.theme.SampleAppTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
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
  EditChar,
  EditLore,
}

/**
 * The optional ViewModel injection lets navigation tests use the production screens with a
 * deterministic fake auth repository instead of Firebase.
 */
@Composable
fun JDRNexusApp(authViewModel: AuthViewModel? = null) {
  val altAuthViewModel: AuthViewModel = viewModel(factory = AuthViewModel.Factory)
  val context = LocalContext.current
  val credentialManager = remember(context) { CredentialManager.create(context) }
  val navController = rememberNavController()

  // Create an editFileViewModel to be able to retreive the file for the edition screens
  val editFileViewModel: EditFileViewModel = viewModel()

  Scaffold { innerPadding ->
    NavHost(
        navController = navController,
        // Use instead of deriving it from the current destination.
        startDestination = AllRoutes.SignIn.name,
        modifier = Modifier.padding(innerPadding),
    ) {
      composable(route = AllRoutes.SignIn.name) {
        val onSignUp = { navController.navigate(AllRoutes.SignUp.name) }
        val onSignInSuccess = {
          navController.navigate(AllRoutes.Home.name) {
            // Authenticated users should not return to either auth screen with Back.
            popUpTo(AllRoutes.SignIn.name) { inclusive = true }
            launchSingleTop = true
          }
        }
        if (authViewModel == null) {
          SignInScreen(
              signInViewModel = altAuthViewModel,
              credentialManager = credentialManager,
              onSignUp = onSignUp,
              onSignInSuccess = onSignInSuccess,
              forgotPassword = {},
          )
        } else {
          SignInScreen(
              signInViewModel = authViewModel,
              credentialManager = credentialManager,
              onSignUp = onSignUp,
              onSignInSuccess = onSignInSuccess,
              forgotPassword = {},
          )
        }
      }

      composable(route = AllRoutes.SignUp.name) {
        val onRegister = {
          navController.navigate(AllRoutes.Home.name) {
            // The signed-in back stack starts at Home, not the registration flow.
            popUpTo(AllRoutes.SignIn.name) { inclusive = true }
            launchSingleTop = true
          }
        }
        // Return to the existing SignIn entry rather than pushing a duplicate.
        val onSignInScreen: () -> Unit = { navController.popBackStack() }
        if (authViewModel == null) {
          SignUpScreen(
              signUpViewModel = altAuthViewModel,
              onRegister = onRegister,
              onSignInScreen = onSignInScreen,
          )
        } else {
          SignUpScreen(
              signUpViewModel = authViewModel,
              onRegister = onRegister,
              onSignInScreen = onSignInScreen,
          )
        }
      }

      composable(route = AllRoutes.Home.name) {
        val onLogout = {
          navController.navigate(AllRoutes.SignIn.name) {
            // Sign-in may no longer be on the stack after authentication succeeds.
            popUpTo(AllRoutes.Home.name) { inclusive = true }
            launchSingleTop = true
          }
        }
        if (authViewModel == null) {
          HomeScreen(viewModel = altAuthViewModel, onLogout = onLogout)
        } else {
          HomeScreen(viewModel = authViewModel, onLogout = onLogout)
        }
      }

      composable(route = AllRoutes.PersoSpace.name) {
        val onDocumentClicked: (JDRFile) -> Unit = { document ->
          editFileViewModel.loadFile(document.id)
          when (document.type) {
            DocumentType.CHARACTER -> navController.navigate(AllRoutes.EditChar.name)
            DocumentType.TEXT -> navController.navigate(AllRoutes.EditLore.name)
            DocumentType.FOLDER -> Unit
          }
        }

        PersonalSpaceScreen(
            onBackButton = { navController.popBackStack() },
            onDocumentClicked = onDocumentClicked,
            onCreateFile = {},
        )
      }

      composable(route = AllRoutes.EditChar.name) {
        EditFileCharacter(
            viewModel = editFileViewModel,
            onBack = { navController.popBackStack() },
            onSaved = { navController.popBackStack() },
        )
      }

      composable(route = AllRoutes.EditLore.name) {}
    }
  }
}

@Preview(showBackground = true)
@Composable
fun AppPreview() {
  SampleAppTheme { JDRNexusApp() }
}
