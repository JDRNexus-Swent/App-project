// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.github.se.jdrnexus.model.notification

import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore

object NotificationRepositoryProvider {
  private val defaultRepository: NotificationRepository by lazy {
    NotificationRepositoryFirestore(Firebase.firestore)
  }

  var repository: NotificationRepository = defaultRepository
}
