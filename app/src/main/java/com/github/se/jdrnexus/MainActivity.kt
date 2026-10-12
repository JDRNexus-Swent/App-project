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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.github.se.jdrnexus.resources.C
import com.github.se.jdrnexus.ui.authentication.SignInScreen
import com.github.se.jdrnexus.ui.authentication.SignUpScreen
import com.github.se.jdrnexus.ui.home.HomeScreen
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
}

/**
 * Added parameters let navigation tests invoke the real route callbacks without starting
 * Firebase-backed authentication or home-screen behavior.
 */
@Composable
fun JDRNexusApp(
    signInContent: (@Composable (onSignUp: () -> Unit, onSignInSuccess: () -> Unit) -> Unit)? =
        null,
    signUpContent: (@Composable (onRegister: () -> Unit, onSignInScreen: () -> Unit) -> Unit)? =
        null,
    homeContent: (@Composable (onLogout: () -> Unit) -> Unit)? = null,
) {
  val context = LocalContext.current
  val credentialManager = remember(context) { CredentialManager.create(context) }
  val navController = rememberNavController()

  Scaffold { innerPadding ->
    NavHost(
        navController = navController,
        // Keep this stable instead of deriving it from the current destination.
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
        if (signInContent == null) {
          SignInScreen(
              credentialManager = credentialManager,
              onSignUp = onSignUp,
              onSignInSuccess = onSignInSuccess,
              forgotPassword = {},
          )
        } else {
          signInContent(onSignUp, onSignInSuccess)
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
        if (signUpContent == null) {
          SignUpScreen(onRegister = onRegister, onSignInScreen = onSignInScreen)
        } else {
          signUpContent(onRegister, onSignInScreen)
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
        if (homeContent == null) {
          HomeScreen(onLogout = onLogout)
        } else {
          homeContent(onLogout)
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
