package com.github.se.jdrnexus.model.repository

import com.google.firebase.Firebase
import com.google.firebase.auth.auth

object AuthRepositoryProvider {
  private val _repository: AuthRepository by lazy { AuthRepositoryFirebase(Firebase.auth) }
  var repository: AuthRepository = _repository
}
