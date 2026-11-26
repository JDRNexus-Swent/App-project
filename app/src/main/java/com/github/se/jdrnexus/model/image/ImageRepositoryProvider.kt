// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.model.image

import com.github.se.jdrnexus.model.personalSpace.WorkspaceRepositoryProvider
import com.google.firebase.Firebase
import com.google.firebase.app
import com.google.firebase.storage.storage

/**
 * Provides the image repository used by the app. It is built the first time this object is used,
 * with the app's Storage instance and the workspace repository of [WorkspaceRepositoryProvider].
 * [repository] is mutable for testing purposes.
 */
object ImageRepositoryProvider {
  var repository: ImageRepository =
      ImageRepositoryFirebase(
          Firebase.storage,
          Firebase.app.applicationContext.contentResolver,
          WorkspaceRepositoryProvider.repository,
      )
}
