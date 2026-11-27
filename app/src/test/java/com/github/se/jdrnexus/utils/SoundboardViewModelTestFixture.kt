// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.github.se.jdrnexus.utils

import android.net.Uri
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.github.se.jdrnexus.model.repository.AuthRepository
import com.github.se.jdrnexus.model.repository.AuthResult
import com.github.se.jdrnexus.model.repository.AuthUser
import com.github.se.jdrnexus.model.soundboard.AmbiencePlaylist
import com.github.se.jdrnexus.model.soundboard.AmbienceTrack
import com.github.se.jdrnexus.model.soundboard.AudioFile
import com.github.se.jdrnexus.model.soundboard.ImportedAudio
import com.github.se.jdrnexus.model.soundboard.SoundEffect
import com.github.se.jdrnexus.model.soundboard.Soundboard
import com.github.se.jdrnexus.model.soundboard.SoundboardException
import com.github.se.jdrnexus.model.soundboard.SoundboardPlayer
import com.github.se.jdrnexus.model.soundboard.SoundboardPlayer.Event
import com.github.se.jdrnexus.model.soundboard.SoundboardRepository
import com.github.se.jdrnexus.ui.soundboard.SoundboardViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import org.junit.After
import org.junit.Rule

@OptIn(ExperimentalCoroutinesApi::class)
internal abstract class SoundboardViewModelTestFixture {
  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  protected val tavern = AmbiencePlaylist(TAVERN, "Tavern", listOf(track(FIRE), track(CROWD)))
  protected val dungeon = AmbiencePlaylist(DUNGEON, "Dungeon", listOf(track(WIND)))
  protected val board =
      Soundboard(
          id = BOARD_ID,
          name = "The Sunken Crown",
          playlists = listOf(tavern, dungeon),
          effects = listOf(effect(SWORD), effect(THUNDER)),
          ambienceVolume = 0.7f,
          effectsVolume = 0.6f,
      )
  protected val repository = FakeRepository()
  protected val player = FakePlayer()
  protected val store = ViewModelStore()
  protected val auth = FakeAuthRepository(AuthUser(USER_ID, null, null))
  protected val vm by lazy {
    ViewModelProvider.create(
            store,
            viewModelFactory { initializer { SoundboardViewModel(repository, player, auth) } },
        )[SoundboardViewModel::class]
  }
  protected val state
    get() = vm.uiState.value

  @After fun clearViewModel() = store.clear()

  protected fun TestScope.load(soundboard: Soundboard = board) {
    repository.board(soundboard.id).value = soundboard
    vm.loadSoundboard(soundboard.id)
    runCurrent()
  }

  protected fun TestScope.publishBoard(snapshot: Soundboard?) {
    repository.board(BOARD_ID).value = snapshot
    runCurrent()
  }

  protected fun TestScope.prepareEffect(audioId: String): FakePlayback {
    vm.toggleSoundEffect(audioId)
    runCurrent()
    return player.latest(audioId)
  }

  protected fun TestScope.playFire(): FakePlayback {
    vm.playAmbience(TAVERN, FIRE)
    return startLatest(FIRE)
  }

  protected fun TestScope.playEffect(audioId: String): FakePlayback {
    vm.toggleSoundEffect(audioId)
    return startLatest(audioId)
  }

  private fun TestScope.startLatest(audioId: String): FakePlayback {
    runCurrent()
    return player.latest(audioId).also {
      it.emit(Event.Started)
      runCurrent()
    }
  }

  protected class FakePlayer : SoundboardPlayer {
    val created = mutableListOf<FakePlayback>()
    var failNextCreate = false
    var failNextStart = false

    override fun create(audio: AudioFile, onEvent: (Event) -> Unit): SoundboardPlayer.Playback {
      if (failNextCreate) {
        failNextCreate = false
        error("Unable to open player")
      }
      val playback = FakePlayback(audio, onEvent, failNextStart)
      failNextStart = false
      created.add(playback)
      return playback
    }

    fun latest(audioId: String): FakePlayback = created.last { it.audio.id == audioId }
  }

  protected class FakePlayback(
      val audio: AudioFile,
      private val onEvent: (Event) -> Unit,
      private val failStart: Boolean,
  ) : SoundboardPlayer.Playback {
    var playCount = 0
    var closeCount = 0
    var pauseCount = 0
    var pausePosition = 0L
    var volume: Float? = null
      private set

    override fun play() {
      playCount++
      if (failStart) error("Unable to start playback")
    }

    override fun pause(): Long {
      pauseCount++
      return pausePosition
    }

    override fun setVolume(volume: Float) {
      this.volume = volume
    }

    override fun close() {
      closeCount++
    }

    fun emit(event: Event) = onEvent(event)
  }

  protected class FakeAuthRepository(user: AuthUser?) : AuthRepository {
    override val authState = MutableStateFlow(user)

    override suspend fun signUpWithEmail(
        email: String,
        password: String,
        username: String,
    ): AuthResult<AuthUser> = error("Playback cannot sign up")

    override suspend fun signInWithEmail(email: String, password: String): AuthResult<AuthUser> =
        error("Playback cannot sign in")

    override suspend fun signInWithGoogle(idToken: String): AuthResult<AuthUser> =
        error("Playback cannot sign in")

    override suspend fun signOut(): AuthResult<Unit> = error("Playback cannot sign out")
  }

  protected class FakeRepository : SoundboardRepository {
    private val boards = mutableMapOf<Pair<String, String>, MutableStateFlow<Soundboard?>>()
    val savedBoards = MutableStateFlow<List<Soundboard>>(emptyList())
    val observations = mutableListOf<Pair<String, String>>()
    val saves = mutableListOf<Pair<String, Soundboard>>()
    var observationFailure: Exception? = null
    var saveFailure: SoundboardException? = null

    fun board(id: String, userId: String = USER_ID): MutableStateFlow<Soundboard?> =
        boards.getOrPut(userId to id) { MutableStateFlow(null) }

    override fun observeSoundboard(userId: String, soundboardId: String): Flow<Soundboard?> {
      observations.add(userId to soundboardId)
      observationFailure?.let {
        return flow { throw it }
      }
      return board(soundboardId, userId)
    }

    override suspend fun saveSoundboard(userId: String, soundboard: Soundboard): Result<Unit> {
      saves.add(userId to soundboard)
      saveFailure?.let {
        return Result.failure(it)
      }
      board(soundboard.id, userId).value = soundboard
      return Result.success(Unit)
    }

    override fun getNewSoundboardId(): String = error("Playback cannot create boards")

    override fun observeSoundboards(userId: String): Flow<List<Soundboard>> = savedBoards

    override suspend fun importAudio(userId: String, source: Uri): Result<ImportedAudio> =
        error("Playback cannot import sounds")

    override suspend fun deleteSoundboard(userId: String, soundboardId: String): Result<Unit> =
        error("Playback cannot delete boards")

    override suspend fun discardUnsavedImports(userId: String): Result<Unit> =
        error("Playback cannot discard imports")
  }
}

internal const val BOARD_ID = "campaign-board"
internal const val USER_ID = "game-master"
internal const val OTHER_USER_ID = "other-game-master"
internal const val TAVERN = "tavern"
internal const val DUNGEON = "dungeon"
internal const val FIRE = "fire"
internal const val CROWD = "crowd"
internal const val WIND = "wind"
internal const val SWORD = "sword"
internal const val THUNDER = "thunder"

internal fun audio(id: String) = AudioFile(id, "/private/$id.mp3", 8_000)

private fun track(id: String) = AmbienceTrack(id, audio(id))

internal fun effect(id: String) = SoundEffect(id, audio(id))
