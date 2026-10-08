package com.github.se.jdrnexus.ui.authentication

import android.annotation.SuppressLint
import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.github.se.jdrnexus.R
import com.github.se.jdrnexus.resources.C
import com.github.se.jdrnexus.resources.DivideWithDiamonds
import com.github.se.jdrnexus.resources.JdrNexusLogo
import com.github.se.jdrnexus.resources.darkFieldColors
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import kotlinx.coroutines.launch

@Composable
fun SignInScreen(
    signInViewModel: AuthViewModel,
    credentialManager: CredentialManager = CredentialManager.create(LocalContext.current),
    onSignInSuccess: () -> Unit = {},
    onSignUp: () -> Unit = {},
    forgotPassword: () -> Unit = {},
) {
  val uiState by signInViewModel.uiState.collectAsState()
  // Get the colors defined by the application's Material theme.
  val colors = MaterialTheme.colorScheme

  val email = uiState.email
  val password = uiState.password

  val isLoading = uiState.status is AuthStatus.Loading
  val errorMessage = (uiState.status as? AuthStatus.Error)?.message

  // Keeps track of whether the password should be visible or masked.
  var passwordVisible by remember { mutableStateOf(false) }
  val context = LocalContext.current

  val coroutineScope = rememberCoroutineScope()

  LaunchedEffect(uiState.status) {
    if (uiState.status is AuthStatus.Success) {
      onSignInSuccess()
    }
  }

  @SuppressLint("LocalContextGetResourceValueCall")
  fun signInWithGoogle() {
    coroutineScope.launch {
      try {
        val googleIdOption =
            GetGoogleIdOption.Builder()
                .setServerClientId(context.getString(R.string.default_web_client_id))
                .setFilterByAuthorizedAccounts(false)
                .setAutoSelectEnabled(false)
                .build()

        val request = GetCredentialRequest.Builder().addCredentialOption(googleIdOption).build()

        val result =
            credentialManager.getCredential(
                context = context,
                request = request,
            )

        val credential = result.credential

        if (
            credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
          try {
            val googleCredential = GoogleIdTokenCredential.createFrom(credential.data)

            val idToken = googleCredential.idToken

            signInViewModel.signInWithGoogle(idToken)
          } catch (e: GoogleIdTokenParsingException) {
            Log.e(
                "SignInScreen",
                "Invalid Google ID token",
                e,
            )
          }
        } else {
          Log.e(
              "SignInScreen",
              "Unexpected credential type: ${credential.type}",
          )
        }
      } catch (e: Exception) {
        Log.e(
            "SignInScreen",
            "Google Sign-In failed",
            e,
        )
      }
    }
  }


  Surface(
      modifier = Modifier.fillMaxSize(),
      color = colors.background,
  ) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {

      // Application logo.
      Box(
          modifier = Modifier.fillMaxWidth(),
          contentAlignment = Alignment.Center,
      ) {
        JdrNexusLogo(
            modifier = Modifier.size(140.dp),
            color = colors.primary,
            backGroundColor = colors.onBackground,
        )
      }
      // Application name and subtitle.
      Text(
          text = "JDRNexus",
          fontFamily = FontFamily.Serif,
          fontSize = 40.sp,
          textAlign = TextAlign.Center,
          color = colors.onSecondary,
      )
      Spacer(modifier = Modifier.height(8.dp))
      Text(
          text = "Your next chapter awaits",
          fontSize = 13.sp,
          color = colors.tertiary,
      )
      Spacer(modifier = Modifier.height(8.dp))
      DivideWithDiamonds(colors.primary)
      Spacer(modifier = Modifier.height(8.dp))

      // Sign-in introduction.
      Text(
          text = "Welcome back, adventurer.",
          fontSize = 22.sp,
          fontFamily = FontFamily.Serif,
          modifier = Modifier.fillMaxWidth(),
          color = colors.onSecondary,
      )
      Spacer(modifier = Modifier.height(5.dp))
      Text(
          text = "Sign in to continue your story.",
          color = colors.tertiary,
          fontSize = 13.sp,
          modifier = Modifier.fillMaxWidth(),
      )
      Spacer(modifier = Modifier.height(16.dp))

      // Email field.
      Text(
          text = "Email address",
          fontWeight = FontWeight.Medium,
          modifier = Modifier.fillMaxWidth(),
          color = colors.onSecondary,
      )
      Spacer(Modifier.height(10.dp))

      OutlinedTextField(
          value = email,
          onValueChange = { signInViewModel.onEmailChange(it) },
          shape = RoundedCornerShape(45.dp),
          leadingIcon = {
            Icon(
                imageVector = Icons.Outlined.Email,
                contentDescription = null,
                tint = colors.primary,
            )
          },
          placeholder = { Text(text = "You@example.com", color = colors.tertiary) },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag(C.Tag.sign_in_email),
          colors =
              darkFieldColors(
                  container = colors.onBackground,
                  border = colors.onSurface,
                  textPrimary = colors.onSecondary,
                  primary = colors.primary,
              ),
      )
      Spacer(modifier = Modifier.height(20.dp))

      // Password field.
      Text(
          text = "Password",
          fontWeight = FontWeight.Medium,
          modifier = Modifier.fillMaxWidth(),
          color = colors.onSecondary,
      )

      Spacer(modifier = Modifier.height(10.dp))

      OutlinedTextField(
          value = password,
          onValueChange = { signInViewModel.onPasswordChange(it) },
          shape = RoundedCornerShape(45.dp),
          leadingIcon = {
            Icon(
                imageVector = Icons.Outlined.Lock,
                contentDescription = null,
                tint = colors.primary,
            )
          },
          trailingIcon = {
            // Toggle between visible and masked password.
            IconButton(
                onClick = { passwordVisible = !passwordVisible },
                modifier = Modifier.testTag(C.Tag.sign_in_password_visibility),
            ) {
              Icon(
                  imageVector = Icons.Outlined.Visibility,
                  contentDescription = "Toggle password visibility",
                  tint = colors.primary,
              )
            }
          },
          visualTransformation =
              if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
          placeholder = { Text(text = "Enter your password", color = colors.tertiary) },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag(C.Tag.sign_in_password),
          colors =
              darkFieldColors(
                  container = colors.onBackground,
                  border = colors.onSurface,
                  textPrimary = colors.onSecondary,
                  primary = colors.primary,
              ),
          isError = errorMessage != null,
          supportingText = { errorMessage?.let { error -> Text(text = error) } },
      )

      // button in case of forgotten password
      TextButton(
          onClick = forgotPassword,
          modifier = Modifier.align(Alignment.End).testTag(C.Tag.sign_in_forgot_password),
      ) {
        Text(
            text = "Forgot password ?",
            color = colors.primary,
        )
      }
      Spacer(Modifier.height(4.dp))
      // Sign-in button to sign in to the app .
      Button(
          onClick = { signInViewModel.signIn() },
          enabled = !isLoading,
          modifier = Modifier.fillMaxWidth().height(54.dp).testTag(C.Tag.sign_in_submit),
          shape = RoundedCornerShape(18.dp),
          colors = ButtonDefaults.buttonColors(colors.primary, colors.onPrimary),
      ) {
        if (isLoading) {
          CircularProgressIndicator(
              Modifier.size(24.dp),
              color = colors.onPrimary,
              strokeWidth = 2.dp,
          )
        } else {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Sign In",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.width(12.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
            )
          }
        }
      }
      Spacer(Modifier.height(8.dp))

      Row(verticalAlignment = Alignment.CenterVertically) {
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = colors.tertiary.copy(alpha = .3f),
        )

        Text(
            text = " or continue with ",
            color = colors.tertiary,
        )

        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = colors.tertiary.copy(alpha = .3f),
        )
      }
      Spacer(Modifier.height(8.dp))

      //  Sign in with Google for users who already have an account.
      GoogleSignInButton(
          click = { signInWithGoogle() },
          border = colors.onSurface,
          logo = colors.primary,
          textColor = colors.onSecondary,
          container = colors.onBackground,
      )
      Spacer(modifier = Modifier.height(2.dp))

      // Link to the Sign-Up screen if the user doesn't have an account yet
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "New to the realm? ",
            color = colors.tertiary,
        )

        TextButton(
            onClick = onSignUp,
            modifier = Modifier.testTag(C.Tag.sign_in_sign_up),
            contentPadding = PaddingValues(2.dp),
        ) {
          Text(
              "Sign up",
              color = colors.primary,
              fontWeight = FontWeight.Bold,
          )
        }
      }
    }
  }
}

/**
 * Reusable Google sign-in button.
 *
 * The button receives its colors and click action from the SignInScreen so that the authentication
 * logic remains outside this UI component.
 *
 * @param click Action executed when the button is pressed.
 * @param border Border color of the button.
 * @param logo Color applied to the Google logo.
 * @param textColor Color of the button text.
 * @param container Background color of the button.
 */
@Composable
fun GoogleSignInButton(
    click: () -> Unit,
    border: Color,
    logo: Color,
    textColor: Color,
    container: Color,
) {
  Button(
      onClick = click,
      colors = ButtonDefaults.buttonColors(containerColor = container),
      shape = RoundedCornerShape(50),
      border = BorderStroke(1.dp, border),
      modifier = Modifier.padding(8.dp).height(48.dp).testTag(C.Tag.sign_in_google),
  ) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth(),
    ) {
      Image(
          painter = painterResource(id = R.drawable.google_logo),
          contentDescription = "Google Logo",
          modifier = Modifier.size(30.dp).padding(end = 8.dp),
          colorFilter = ColorFilter.tint(logo),
      )

      Text(
          text = "Sign in with Google",
          color = textColor,
          fontSize = 16.sp,
      )
    }
  }
}


