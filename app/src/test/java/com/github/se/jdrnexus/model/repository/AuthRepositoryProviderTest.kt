// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.model.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.firebase.FirebaseApp
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AuthRepositoryProviderTest {

  @Test
  fun providerDefaultsToFirebaseAndCanBeReplaced() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    if (FirebaseApp.getApps(context).isEmpty()) FirebaseApp.initializeApp(context)
    val original = AuthRepositoryProvider.repository
    val fake = mockk<AuthRepository>()

    try {
      assertEquals(AuthRepositoryFirebase::class, original::class)
      AuthRepositoryProvider.repository = fake
      assertSame(fake, AuthRepositoryProvider.repository)
    } finally {
      AuthRepositoryProvider.repository = original
    }
  }
}
