// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.model.profile

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
class ProfileRepositoryProviderTest {

  @Test
  fun providerDefaultsToFirestoreAndCanBeReplaced() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    if (FirebaseApp.getApps(context).isEmpty()) FirebaseApp.initializeApp(context)
    val original = ProfileRepositoryProvider.repository
    val fake = mockk<ProfileRepository>()

    try {
      assertEquals(ProfileRepositoryFirestore::class, original::class)
      ProfileRepositoryProvider.repository = fake
      assertSame(fake, ProfileRepositoryProvider.repository)
    } finally {
      ProfileRepositoryProvider.repository = original
    }
  }
}
