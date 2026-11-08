// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.model.repository

import com.github.se.jdrnexus.utils.FirebaseEmulatorTestUtils
import com.github.se.jdrnexus.utils.FirebaseEmulatorTestUtils.pumpMainLooperUntil
import com.github.se.jdrnexus.utils.waitFor
import com.google.firebase.auth.FirebaseAuth
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Runs [AuthRepositoryFirebase] against the Auth emulator, to check how real Firebase errors are
 * translated. Needs the emulator started with: firebase emulators:exec --config firebase.test.json
 */
@RunWith(RobolectricTestRunner::class)
class AuthRepositoryFirebaseEmulatorTest {

  private lateinit var firebaseAuth: FirebaseAuth
  private lateinit var repository: AuthRepositoryFirebase

  @Before
  fun setUp() {
    firebaseAuth = FirebaseEmulatorTestUtils.getAuth()
    repository = AuthRepositoryFirebase(firebaseAuth)
  }

  @After fun tearDown() = firebaseAuth.signOut()

  /**
   * Firebase Auth completes its tasks on the main looper, which the test itself runs on: the call
   * runs in the background while the looper is pumped, otherwise the test would wait forever.
   */
  private fun <T> call(operation: suspend () -> T): T = runBlocking {
    val result = async(Dispatchers.Default) { operation() }
    pumpMainLooperUntil { result.isCompleted }
    result.await()
  }

  private fun randomEmail() = "player-${UUID.randomUUID()}@example.com"

  private fun signUp(email: String): AuthUser {
    val user = call {
      repository.signUpWithEmail(email, "secret1", "Ranger")
    }
        .requireUser("email sign-up")
    assertEquals(email, user.email)
    assertEquals("Ranger", user.username)
    return user
  }

  private fun AuthResult<AuthUser>.requireUser(operation: String): AuthUser =
      when (this) {
        is AuthResult.Success -> value
        is AuthResult.Failure -> throw AssertionError("Expected $operation to succeed, got $error")
        is AuthResult.AccountCreatedNeedsRecovery -> throw AssertionError(message)
      }

  @Test
  fun authStateFollowsSignUpAndSignOut() {
    val email = randomEmail()

    val user = signUp(email)

    // waitFor pumps the main looper itself, so it is not wrapped in call.
    assertEquals(user, runBlocking { repository.authState.waitFor { it?.username == "Ranger" } })

    assertEquals(AuthResult.Success(Unit), call { repository.signOut() })
    assertEquals(null, runBlocking { repository.authState.waitFor { it == null } })
  }

  @Test
  fun signUpWithUsedEmailReturnsEmailAlreadyInUse() {
    val email = randomEmail()
    signUp(email)
    assertEquals(AuthResult.Success(Unit), call { repository.signOut() })

    assertEquals(
        AuthResult.Failure(AuthError.EMAIL_ALREADY_IN_USE),
        call { repository.signUpWithEmail(email, "secret1", "Ranger") },
    )
    assertEquals(null, firebaseAuth.currentUser)
  }

  @Test
  fun signUpWithMalformedEmailReturnsInvalidEmail() {
    assertEquals(
        AuthResult.Failure(AuthError.INVALID_EMAIL),
        call { repository.signUpWithEmail("not-an-email", "secret1", "Ranger") },
    )
    assertEquals(null, firebaseAuth.currentUser)
  }

  @Test
  fun signUpWithShortPasswordReturnsWeakPassword() {
    assertEquals(
        AuthResult.Failure(AuthError.WEAK_PASSWORD),
        call { repository.signUpWithEmail(randomEmail(), "123", "Ranger") },
    )
    assertEquals(null, firebaseAuth.currentUser)
  }

  @Test
  fun signInWithWrongPasswordReturnsWrongPassword() {
    val email = randomEmail()
    signUp(email)
    assertEquals(AuthResult.Success(Unit), call { repository.signOut() })

    assertEquals(
        AuthResult.Failure(AuthError.WRONG_PASSWORD),
        call { repository.signInWithEmail(email, "wrong-password") },
    )
    assertEquals(null, firebaseAuth.currentUser)
  }

  @Test
  fun usernameIsStoredOnTheAccount() {
    val email = randomEmail()
    val user = signUp(email)
    assertEquals(AuthResult.Success(Unit), call { repository.signOut() })

    // A fresh sign-in reads the profile from the server, not the local user object.
    assertEquals(
        AuthResult.Success(user),
        call { repository.signInWithEmail(email, "secret1") },
    )
  }

  @Test
  fun signInWithUnknownEmailReturnsUserNotFound() {
    assertEquals(
        AuthResult.Failure(AuthError.USER_NOT_FOUND),
        call { repository.signInWithEmail(randomEmail(), "secret1") },
    )
    assertEquals(null, firebaseAuth.currentUser)
  }

  @Test
  fun signInWithGoogleCreatesSession() {
    val email = randomEmail()
    // The Auth emulator accepts an unsigned JSON payload as Google id token.
    val idToken = """{"sub":"${UUID.randomUUID()}","email":"$email","email_verified":true}"""

    val user = call { repository.signInWithGoogle(idToken) }.requireUser("Google sign-in")

    assertEquals(email, user.email)
    assertEquals(email, firebaseAuth.currentUser?.email)
  }
}
