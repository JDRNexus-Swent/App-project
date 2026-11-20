// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.model.profile

import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore

/**
 * Provides the profile repository used by the app. [repository] is mutable for testing purposes.
 */
object ProfileRepositoryProvider {
  private val _repository: ProfileRepository by lazy {
    ProfileRepositoryFirestore(Firebase.firestore)
  }

  var repository: ProfileRepository = _repository
}
