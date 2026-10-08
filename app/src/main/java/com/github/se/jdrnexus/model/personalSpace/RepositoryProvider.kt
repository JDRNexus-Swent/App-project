package com.github.se.jdrnexus.model.personalSpace

import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore

/**
 * Provides a single instance of the repository in the app. `repository` is mutable for testing
 * purposes.
 */
object WorkspaceRepositoryProvider {
  private val _repository: WorkspaceRepository by lazy {
    WorkspaceRepositoryFirestore(Firebase.firestore)
  }

  var repository: WorkspaceRepository = _repository
}
