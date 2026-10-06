package com.github.se.jdrnexus.ui.authentication

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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.se.jdrnexus.resources.DarkFieldColors
import com.github.se.jdrnexus.resources.DivideWithDiamonds
import com.github.se.jdrnexus.resources.JdrNexusLogo
import com.github.se.jdrnexus.ui.theme.SampleAppTheme

@Composable
fun SignUpScreen(
    // later : signUpViewModel:SignUpViewModel = ViewModel(),
    onRegister: () -> Unit = {},
    onSignInScreen: () -> Unit = {},
    emailError: String? = null,
    passwordError: String? = null,
    usernameError: String? = null,
    isLoading: Boolean = false,
) {

  // Use the application's Material 3 theme colors.
  val colors = MaterialTheme.colorScheme

  // Local UI state for the form fields.
  var email by remember { mutableStateOf("") }
  var password by remember { mutableStateOf("") }
  var username by remember { mutableStateOf("") }

  // Keeps track of whether the password should be visible or masked.
  var passwordVisible by remember { mutableStateOf(false) }

  // later :   val uiState by signUpViewModel.uiState.collectAsState()
  // later :   LaunchedEffect(uiState.user) { uiState.user?.let { onRegister() } }

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
            backGroundColor = colors.background,
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
      Text(
          text = "Your next chapter awaits",
          fontSize = 13.sp,
          color = colors.tertiary,
      )
      Spacer(modifier = Modifier.height(8.dp))
      DivideWithDiamonds(colors.primary)
      Spacer(modifier = Modifier.height(8.dp))

      // Sign-up introduction.
      Text(
          text = "Welcome new adventurer.",
          fontSize = 22.sp,
          fontFamily = FontFamily.Serif,
          modifier = Modifier.fillMaxWidth(),
          color = colors.onSecondary,
      )
      Spacer(modifier = Modifier.height(5.dp))
      Text(
          text = "Sign up to start your adventure",
          color = colors.tertiary,
          fontSize = 12.sp,
          modifier = Modifier.fillMaxWidth(),
      )
      Spacer(Modifier.height(16.dp))
      // Username field.
      Text(
          text = "Username",
          fontWeight = FontWeight.Medium,
          modifier = Modifier.fillMaxWidth(),
          color = colors.onSecondary,
      )
      Spacer(Modifier.height(10.dp))
      OutlinedTextField(
          value = username, // later : uiState.username
          onValueChange = {
            username = it
          }, // later :  onValueChange = {signUpViewModel.setUsername(it)}
          shape = RoundedCornerShape(45.dp),
          placeholder = { Text(text = "Username", color = colors.tertiary) },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
          colors =
              DarkFieldColors(
                  container = colors.onBackground,
                  border = colors.onSurface,
                  textPrimary = colors.onSecondary,
                  primary = colors.primary,
              ),
          // later :
          //          isError = uiState.usernameError != null,
          //          supportingText = {
          //              uiState.usernameError?.let { error ->
          //                  Text(text = error)
          //              }
          //          },
          isError = usernameError != null,
          supportingText = { usernameError?.let { error -> Text(text = error) } },
      )
      Spacer(Modifier.height(20.dp))

      // Email field.
      Text(
          text = "Email address",
          fontWeight = FontWeight.Medium,
          modifier = Modifier.fillMaxWidth(),
          color = colors.onSecondary,
      )
      Spacer(Modifier.height(10.dp))

      OutlinedTextField(
          value = email, // later : value = uiState.email
          onValueChange = {
            email = it
          }, // later :  onValueChange = {signUpViewModel.setEmail(it) },
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
          modifier = Modifier.fillMaxWidth(),
          colors =
              DarkFieldColors(
                  container = colors.onBackground,
                  border = colors.onSurface,
                  textPrimary = colors.onSecondary,
                  primary = colors.primary,
              ),
          // later :
          //          isError = uiState.emailError != null,
          //          supportingText = {
          //              uiState.emailError?.let { error ->
          //                  Text(text = error)
          //              }
          //          },
          isError = emailError != null,
          supportingText = { emailError?.let { error -> Text(text = error) } },
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
          value = password, // later : value = uiState.password
          onValueChange = {
            password = it
          }, // later :     onValueChange = {signUpViewModel.setPassword(it) },
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
            IconButton(onClick = { passwordVisible = !passwordVisible }) {
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
          modifier = Modifier.fillMaxWidth(),
          colors =
              DarkFieldColors(
                  container = colors.onBackground,
                  border = colors.onSurface,
                  textPrimary = colors.onSecondary,
                  primary = colors.primary,
              ),
          // later :
          //          isError = uiState.passwordError != null,
          //          supportingText = {
          //              uiState.passwordError?.let { error ->
          //                  Text(text = error)
          //              }
          //          },
          isError = passwordError != null,
          supportingText = { passwordError?.let { error -> Text(text = error) } },
      )
      Spacer(modifier = Modifier.height(8.dp))
      DivideWithDiamonds(colors.primary)
      Spacer(Modifier.height(8.dp))

      // Register button.
      Button(
          onClick = onRegister, // later :  onClick = uiState.onRegister()
          enabled = !isLoading,
          modifier = Modifier.fillMaxWidth().height(54.dp),
          shape = RoundedCornerShape(18.dp),
          colors = ButtonDefaults.buttonColors(colors.primary, colors.onPrimary),
      ) {
        if (isLoading) { //  later : if (uiState.isLoading)
          CircularProgressIndicator(
              modifier = Modifier.size(24.dp),
              color = colors.onPrimary,
              strokeWidth = 2.dp,
          )
        } else {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Register",
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

      // Link to the Sign In screen if the user already have an account
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "Already have an account? ",
            color = colors.tertiary,
        )

        TextButton(
            onClick = onSignInScreen,
            contentPadding = PaddingValues(2.dp),
        ) {
          Text(
              "Log in",
              color = colors.primary,
              fontWeight = FontWeight.Bold,
          )
        }
      }
    }
  }
}

// Preview of the Sign-Up screen using the light theme and dark theme
@Preview(
    showBackground = true,
    name = "Light Mode",
)
@Composable
fun SignUpScreenLightPreview() {
  SampleAppTheme(darkTheme = false, dynamicColor = false) { SignUpScreen() }
}

@Preview(
    showBackground = true,
    name = "Dark Mode",
)
@Composable
fun SignUpScreenDarkPreview() {
  SampleAppTheme(darkTheme = true) { SignUpScreen() }
}
