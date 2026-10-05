package com.github.se.jdrnexus.ui.authentication


//import androidx.credentials.CredentialManager
import android.icu.number.Scale
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas

import androidx.compose.foundation.Image
import androidx.compose.foundation.R
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fitInside
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsEndWidth
import androidx.compose.foundation.magnifier
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme

import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.draw.paint
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendModeColorFilter
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.RectRulers
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.se.jdrnexus.ui.theme.SampleAppTheme

@Composable
fun SignInScreen (
//    signInViewModel: ViewModel = viewModel(), // need to change to signinviewmodel
//    credentialManager: CredentialManager = CredentialManager
    onSignIn: ()-> Unit = {},
    email: String = "",
    password : String = "",
    signInWithGoogle: () -> Unit = {},
    onSignUp: ()-> Unit = {},
    forgotPassword: ()->Unit = {}

){


    val colors = MaterialTheme.colorScheme
    val gold = Color(0xFFD4B95D)
    val cardColor = Color(0xFF221A18)
    val context = LocalContext.current
    val background = Color(0xFFF2EEE9) // page background
    val primaryBrown = Color(0xFF6F5138) // logo, button, icons
    val textPrimary = Color(0xFF2E2520) // headings
    val textSecondary = Color(0xFF8E8278) // subtitle text
    val fieldColor = Color(0xFFE7E0D8) // textfield background
    val borderColor = Color(0xFFD1C7BD)

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
//    val uiState by signInViewModel.uiState.colectasState()



    Surface(
        modifier = Modifier.fillMaxSize(),
        color = colors.background)
    {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,

        ) {
            Spacer(modifier = Modifier.height(20.dp))
//            Image(
//                painter = painterResource(com.github.se.jdrnexus.R.drawable.app_logo),
//                contentDescription = "Logo",
//                modifier = Modifier.size(100.dp),
//                colorFilter = ColorFilter.tint(primaryBrown)
//
//
//            )
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                JdrNexusLogo(
                    modifier = Modifier.size(140.dp),
                    color = colors.primary,
                    backGroundColor = colors.onBackground
                )
            }
            Text(text = "JDRNexus",
                fontFamily = FontFamily.Serif,
                fontSize = 40.sp,
                textAlign = TextAlign.Center,
                color = colors.onSecondary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Your next chapter awaits",
                fontSize = 13.sp,
                color = colors.tertiary

            )
            Spacer(modifier = Modifier.height(8.dp))
            DevideWithDiamonds(colors.primary)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Welcome back, adventurer.",
                fontSize = 22.sp,
                fontFamily = FontFamily.Serif,
                modifier = Modifier.fillMaxWidth(),
                color = colors.onSecondary
            )
            Spacer(modifier = Modifier.height(5.dp))
            Text(
                text = "Sign in to continue your story.",
                color = colors.tertiary,
                fontSize = 13.sp,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Email address",
                fontWeight = FontWeight.Medium,
                modifier = Modifier.fillMaxWidth(),
                color = colors.onSecondary
            )
            Spacer(Modifier.height(10.dp))

            OutlinedTextField(
                value = email,
//                onValueChange = {signInViewModel.onChangeEmail(it) },
                onValueChange = {email = it},
                shape = RoundedCornerShape(45),
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Email,
                        null,
                        tint = colors.primary
                    )
                },
                placeholder = {
                    Text(text = "You@example.com", color = colors.tertiary)
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = darkFieldColors(colors.onBackground, colors.onSurface, colors.onSecondary,colors.primary)

            )
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "Password",
                fontWeight = FontWeight.Medium,
                modifier = Modifier.fillMaxWidth(),
                color = colors.onSecondary
            )

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                shape = RoundedCornerShape(45),
//                onValueChange = {signInViewModel.onChangePassword(it) },
                leadingIcon = {
                    Icon(
                        Icons.Outlined.Lock,
                        null,
                        tint = colors.primary
                    )
                },
                trailingIcon = {
                    IconButton(
                        onClick = {
                            passwordVisible = !passwordVisible
                        }
                    ) {
                        Icon(
                            Icons.Outlined.Visibility,
                            null,
                            tint = colors.primary
                        )
                    }
                },
                visualTransformation =
                    if (passwordVisible)
                        VisualTransformation.None
                    else
                        PasswordVisualTransformation(),
                placeholder = {
                    Text(text ="Enter your password", color = colors.tertiary)
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = darkFieldColors(colors.onBackground, colors.onSurface, colors.onSecondary,colors.primary)
            )
            Spacer(modifier = Modifier.height(4.dp))
            TextButton(
                onClick = forgotPassword,
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(
                    text = "Forgot password ?",
                    color = colors.primary
                )
            }
            Spacer(Modifier.height(4.dp))
            Button(onClick = onSignIn,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(18),
                colors = ButtonDefaults.buttonColors(colors.primary, colors.onPrimary))
            {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "Sign In",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(12.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null
                    )
                }

            }
            Spacer(Modifier.height(20.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {

                HorizontalDivider(
                    modifier = Modifier.weight(1f),
                    color = colors.tertiary.copy(alpha = .3f)
                )

                Text(
                    text = " or continue with ",
                    color = colors.tertiary
                )

                HorizontalDivider(
                    modifier = Modifier.weight(1f),
                    color = colors.tertiary.copy(alpha = .3f)
                )
            }
            Spacer(Modifier.height(16.dp))
            GoogleSignInButton(click = signInWithGoogle,colors.onSurface,colors.primary,colors.onSecondary, colors.onBackground)
            Spacer(modifier = Modifier.height(2.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "New to the realm? ",
                    color = colors.tertiary,

                )

                TextButton(
                    onClick = onSignUp,
                    contentPadding = PaddingValues(2.dp)
                ) {
                    Text(
                        "Sign up",
                        color = colors.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

        }


    }
}






@Composable
fun GoogleSignInButton(click: () -> Unit, border: Color,logo: Color, textColor: Color, container: Color) {
    Button(
        onClick = click,
        colors = ButtonDefaults.buttonColors(containerColor = container),
        shape = RoundedCornerShape(50),
        border = BorderStroke(1.dp, border),
        modifier = Modifier.padding(8.dp).height(48.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Image(
                painter = painterResource(id = com.github.se.jdrnexus.R.drawable.google_logo),
                contentDescription = "Google Logo",
                modifier = Modifier.size(30.dp).padding(end = 8.dp),
                colorFilter = ColorFilter.tint(logo)
            )

            Text(
                text = "Sign in with Google",
                color = textColor,
                fontSize = 16.sp,
            )

        }
    }
}

@Composable
private fun DevideWithDiamonds(color : Color){
    Row(verticalAlignment = Alignment.CenterVertically) {
        HorizontalDivider(modifier = Modifier.weight(1f),
            color = color.copy(alpha = .5f))
//        Text(text = "♦",
//            color = Color.Black,
//            modifier = Modifier.padding(12.dp))
        Canvas(
            modifier = Modifier
                .size(14.dp)
                .padding(horizontal = 2.dp)
        ) {
            val path = Path().apply {
                moveTo(size.width / 2, 0f)
                lineTo(size.width, size.height / 2)
                lineTo(size.width / 2, size.height)
                lineTo(0f, size.height / 2)
                close()
            }

            drawPath(
                path = path,
                color = color
            )
        }
        HorizontalDivider(modifier = Modifier.weight(1f),
            color = color.copy(alpha = .5f))
    }

}
@Composable
private fun darkFieldColors(
    container: Color,
    border: Color,
    textPrimary: Color,
    primary: Color

) = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = container,
    unfocusedContainerColor = container,
    focusedBorderColor = border,
    unfocusedBorderColor = border,
    focusedTextColor = textPrimary,
    unfocusedTextColor = textPrimary,
    cursorColor = primary
)
@Composable
fun JdrNexusLogo(
    modifier: Modifier = Modifier.size(260.dp),
    color: Color,
    backGroundColor: Color
) {
    Canvas(modifier = modifier) {

        val center = center
        val radius = size.minDimension * 0.35f

        // Outer circles
        drawCircle(
            color = color.copy(alpha = 0.2f),
            radius = radius + 25f,
            center = center,
            style = Stroke(width = 3f)
        )

        drawCircle(
            color = backGroundColor, // grey fill
            radius = radius,
            center = center
        )
        drawCircle(
            color = color.copy(alpha = 0.35f),
            radius = radius-2f,
            center = center,
            style = Stroke(width = 3f)
        )

        // Diamonds around circle
        fun drawDiamond(x: Float, y: Float, size: Float = 10f) {
            val path = Path().apply {
                moveTo(x, y - size)
                lineTo(x + size, y)
                lineTo(x, y + size)
                lineTo(x - size, y)
                close()
            }

            drawPath(
                path = path,
                color = color,
                style = Stroke(width = 3f)
            )
        }

        drawDiamond(center.x, center.y - radius - 40f)
        drawDiamond(center.x, center.y + radius + 40f)
        drawDiamond(center.x - radius - 40f, center.y)
        drawDiamond(center.x + radius + 40f, center.y)

        // D20 shape
        val d20Radius = 45f

        val top = Offset(center.x, center.y - 50f)
        val left = Offset(center.x - d20Radius, center.y - 10f)
        val right = Offset(center.x + d20Radius, center.y - 10f)
        val bottom = Offset(center.x, center.y + 45f)

        drawLine(color, top, left, 4f)
        drawLine(color, top, right, 4f)
        drawLine(color, left, bottom, 4f)
        drawLine(color, right, bottom, 4f)
        drawLine(color, left, right, 4f)

        // Internal lines
        drawLine(
            color,
            Offset(center.x - 35f, center.y - 50f),
            Offset(center.x + 35f, center.y - 50f),
            3f
        )

        drawLine(
            color,
            Offset(center.x - 35f, center.y - 50f),
            Offset(center.x - 70f, center.y - 25f),
            3f
        )
        drawLine(
            color,
            Offset(center.x + 35f, center.y - 50f),
            Offset(center.x + 70f, center.y - 25f),
            3f
        )

        drawLine(
            color,
            center,
            bottom,
            3f
        )

        drawLine(
            color,
            Offset(center.x - 70f, center.y - 25f),
            center,
            3f
        )

        drawLine(
            color,
            Offset(center.x + 70f, center.y - 25f),
            center,
            3f
        )
        drawLine(
            color,
            Offset(center.x + 70f,center.y -100f ),
            center,
            3f
        )
        drawLine(
            color,
            Offset(center.x - 70f,center.y -100f ),
            center,
            3f
        )
        drawLine(
            color,
            Offset(center.x - 70f,center.y -100f ),
            Offset(center.x - 70f, center.y - 25f),
            3f
        )
        drawLine(
            color,
            Offset(center.x + 70f,center.y -100f ),
            Offset(center.x + 70f, center.y - 25f),
            3f
        )
        drawLine(
            color,
            Offset(center.x + 70f, center.y - 25f),
            bottom,
            3f
        )
        drawLine(
            color,
            Offset(center.x - 70f, center.y - 25f),
            bottom,
            3f
        )


        // Book
        val bookY = center.y + 70f

        val leftPage = Path().apply {
            moveTo(center.x - 55f, bookY)
            quadraticTo(
                center.x - 25f,
                bookY - 10f,
                center.x,
                bookY + 10f
            )
            lineTo(center.x, bookY + 40f)
        }

        val rightPage = Path().apply {
            moveTo(center.x + 55f, bookY)
            quadraticTo(
                center.x + 25f,
                bookY - 10f,
                center.x,
                bookY + 10f
            )
            lineTo(center.x, bookY + 40f)
        }

        drawPath(
            leftPage,
            color,
            style = Stroke(width = 4f)
        )

        drawPath(
            rightPage,
            color,
            style = Stroke(width = 4f)
        )

        drawLine(
            color,
            Offset(center.x, bookY + 10f),
            Offset(center.x, bookY + 40f),
            4f
        )

        // Page lines
        repeat(3) { i ->
            val y = bookY + 10f + i * 8f

            drawLine(
                color,
                Offset(center.x - 45f, y),
                Offset(center.x - 15f, y + 4f),
                2f
            )

            drawLine(
                color,
                Offset(center.x + 45f, y),
                Offset(center.x + 15f, y + 4f),
                2f
            )
        }
    }
}

//@Preview(showBackground = true)
//@Composable
//fun SignInScreenPreview() {
//    SignInScreen()
//}
@Preview(
    showBackground = true,
    backgroundColor = 0xFFF2EEE9
)
@Composable
fun SignInScreenLightPreview() {
    SampleAppTheme(darkTheme = false, dynamicColor = false) {
        SignInScreen()
    }
}

@Preview(
    showBackground = true,
    name = "Dark Mode"
)
@Composable
fun SignInScreenDarkPreview() {
    SampleAppTheme(darkTheme = true) {
        SignInScreen()
    }
}
