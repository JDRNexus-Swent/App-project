// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.ui.soundboard

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.DirectionsWalk
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Dangerous
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Hardware
import androidx.compose.material.icons.outlined.HistoryEdu
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.se.jdrnexus.R
import com.github.se.jdrnexus.model.soundboard.AmbiencePlaylist
import com.github.se.jdrnexus.model.soundboard.SoundEffect
import com.github.se.jdrnexus.model.soundboard.SoundEffectIcon
import com.github.se.jdrnexus.model.soundboard.Soundboard
import com.github.se.jdrnexus.model.soundboard.SoundboardError
import com.github.se.jdrnexus.resources.AppTopBar
import com.github.se.jdrnexus.resources.C
import com.github.se.jdrnexus.resources.ScreenBottomBar

/** Number of rows of the sound effects grid, as in the mockup. */
private const val EFFECT_ROWS = 3

/** Height of one sound effect button. */
private val EFFECT_TILE_HEIGHT = 56.dp

/** Width of one sound effect button, so a fourth column peeks out and invites a swipe. */
private val EFFECT_TILE_WIDTH = 104.dp

/** Space between sound effect buttons, in both directions. */
private val EFFECT_TILE_SPACING = 8.dp

// ============ Screen entry ============

/**
 * The Game Master's soundboard during a game session: the ambience playlists and the grid of sound
 * effects.
 *
 * The screen holds no state: everything it shows comes from [SoundboardViewModelInterface.uiState],
 * and every control calls the ViewModel.
 *
 * @param viewModel Plays the soundboard and exposes its state.
 * @param soundboardId The soundboard to play, or null for the user's first saved soundboard.
 * @param onBack Leaves the screen.
 * @param onEdit Opens the editor on the given soundboard, or on a new one when it is null.
 * @param onTabSelected Receives the shared bottom bar's tab selections.
 */
@Composable
fun SoundboardScreen(
    viewModel: SoundboardViewModelInterface,
    soundboardId: String?,
    onBack: () -> Unit,
    onEdit: (soundboardId: String?) -> Unit,
    onTabSelected: (String) -> Unit,
) {
  val uiState by viewModel.uiState.collectAsState()
  LaunchedEffect(viewModel, soundboardId) { viewModel.loadSoundboard(soundboardId) }

  Scaffold(
      topBar = {
        AppTopBar(
            title = stringResource(R.string.soundboard_title),
            titleContent = {
              SoundboardHeader(
                  label = stringResource(R.string.soundboard_header_label),
                  title = stringResource(R.string.soundboard_title),
              )
            },
            onBack = onBack,
        )
      },
      bottomBar = { ScreenBottomBar(selectedTab = "Campaigns", onTabSelected = onTabSelected) },
      contentColor = MaterialTheme.colorScheme.onSurface,
  ) { padding ->
    val board = uiState.soundboard
    val contentModifier = Modifier.fillMaxSize().padding(padding)
    when (val loadState = uiState.loadState) {
      SoundboardLoadState.Loading -> SoundboardLoading(contentModifier)
      is SoundboardLoadState.Failed ->
          SoundboardMessage(
              message = stringResource(soundboardErrorText(loadState.reason)),
              messageTag = C.Tag.soundboard_error,
              buttonText = stringResource(R.string.soundboard_retry),
              buttonTag = C.Tag.soundboard_retry,
              onClick = { viewModel.loadSoundboard(soundboardId) },
              modifier = contentModifier,
          )
      SoundboardLoadState.Empty ->
          SoundboardMessage(
              message = stringResource(R.string.soundboard_empty),
              messageTag = C.Tag.soundboard_empty,
              buttonText = stringResource(R.string.soundboard_edit),
              buttonTag = C.Tag.soundboard_edit,
              onClick = { onEdit(board?.id) },
              modifier = contentModifier,
          )
      SoundboardLoadState.Ready ->
          if (board != null) {
            SoundboardContent(
                board = board,
                uiState = uiState,
                viewModel = viewModel,
                onEdit = { onEdit(board.id) },
                modifier = contentModifier,
            )
          }
    }
  }
}

// ============ Loading, empty and failed states ============

/** Spinner shown while the soundboard loads. */
@Composable
private fun SoundboardLoading(modifier: Modifier) {
  Box(modifier, contentAlignment = Alignment.Center) {
    CircularProgressIndicator(Modifier.testTag(C.Tag.soundboard_loading))
  }
}

/**
 * A centred [message] with one button under it: Retry after a failed load, or Edit soundboard when
 * the soundboard has nothing to play.
 */
@Composable
private fun SoundboardMessage(
    message: String,
    messageTag: String,
    buttonText: String,
    buttonTag: String,
    onClick: () -> Unit,
    modifier: Modifier,
) {
  Column(
      modifier = modifier.padding(24.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
      horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Text(
        message,
        modifier = Modifier.testTag(messageTag),
        style = MaterialTheme.typography.bodyLarge,
    )
    Button(onClick = onClick, modifier = Modifier.testTag(buttonTag)) { Text(buttonText) }
  }
}

// ============ Ready content ============

/**
 * The loaded soundboard, top to bottom as in the mockup: ambience, sound effects and the Edit
 * soundboard button.
 */
@Composable
private fun SoundboardContent(
    board: Soundboard,
    uiState: SoundboardUiState,
    viewModel: SoundboardViewModelInterface,
    onEdit: () -> Unit,
    modifier: Modifier,
) {
  LazyColumn(
      modifier = modifier.testTag(C.Tag.soundboard_content),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    item { AmbienceSection(board = board, uiState = uiState, viewModel = viewModel) }
    item {
      EffectsSection(
          effects = board.effects,
          uiState = uiState,
          onToggle = viewModel::toggleSoundEffect,
      )
    }
    item {
      OutlinedButton(
          onClick = onEdit,
          modifier = Modifier.fillMaxWidth().testTag(C.Tag.soundboard_edit),
          shape = RoundedCornerShape(12.dp),
      ) {
        Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
        Text(stringResource(R.string.soundboard_edit), Modifier.padding(start = 8.dp))
      }
    }
  }
}

// ============ Ambience ============

/**
 * The ambience section: the playlist tabs and the play/pause button. With nothing playing,
 * play/pause starts the selected playlist from its first track; otherwise it pauses or resumes the
 * current track.
 */
@Composable
private fun AmbienceSection(
    board: Soundboard,
    uiState: SoundboardUiState,
    viewModel: SoundboardViewModelInterface,
) {
  val ambience = uiState.ambience
  val selectedPlaylist = board.playlists.firstOrNull { it.id == uiState.selectedPlaylistId }
  SoundboardSection(
      title = stringResource(R.string.soundboard_ambience),
      trailing = stringResource(R.string.soundboard_ambience_hint),
  ) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      items(board.playlists, key = { it.id }) { playlist ->
        PlaylistTab(
            playlist = playlist,
            isSelected = playlist.id == uiState.selectedPlaylistId,
            isPlaying = playlist.id == ambience?.playlistId,
            onClick = { viewModel.selectPlaylist(playlist.id) },
        )
      }
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Spacer(Modifier.weight(1f))
      PlayPauseButton(
          status = ambience?.status,
          canStart = selectedPlaylist?.tracks?.isNotEmpty() == true,
          onPlay = {
            // Resume the paused track, or start the selected playlist from its first track.
            if (ambience != null) viewModel.playAmbience(ambience.playlistId, ambience.audioId)
            else selectedPlaylist?.let { viewModel.playPlaylist(it.id) }
          },
          onPause = viewModel::pauseAmbience,
      )
    }
  }
}

/**
 * One playlist tab: outlined when [isSelected], with a check mark when it is [isPlaying]. Shared
 * with the soundboard editor, which never marks a tab as playing and disables the tabs while the
 * draft is being saved or discarded ([enabled]).
 */
@Composable
internal fun PlaylistTab(
    playlist: AmbiencePlaylist,
    isSelected: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
  val colors = MaterialTheme.colorScheme
  Surface(
      onClick = onClick,
      enabled = enabled,
      modifier =
          Modifier.testTag(C.Tag.soundboardPlaylist(playlist.id)).semantics {
            selected = isSelected
          },
      shape = RoundedCornerShape(10.dp),
      color = colors.surfaceContainerLow,
      border =
          BorderStroke(
              if (isSelected) 2.dp else 1.dp,
              if (isSelected) colors.primary else colors.outlineVariant,
          ),
  ) {
    Row(
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      if (isPlaying) {
        Icon(
            Icons.Filled.Check,
            contentDescription = stringResource(R.string.soundboard_playing_cd),
            modifier = Modifier.size(16.dp),
            tint = colors.primary,
        )
      }
      Text(playlist.name, style = MaterialTheme.typography.labelLarge)
    }
  }
}

/**
 * Plays or pauses the ambience. With nothing playing ([status] is null), it starts the selected
 * playlist, so it is enabled only when that playlist has tracks ([canStart]). Disabled while a
 * track is being prepared, when it shows a spinner instead of its icon.
 */
@Composable
private fun PlayPauseButton(
    status: AmbiencePlaybackStatus?,
    canStart: Boolean,
    onPlay: () -> Unit,
    onPause: () -> Unit,
) {
  val isPlaying = status == AmbiencePlaybackStatus.PLAYING
  FilledIconButton(
      onClick = if (isPlaying) onPause else onPlay,
      enabled =
          when (status) {
            null -> canStart
            AmbiencePlaybackStatus.PREPARING -> false
            else -> true
          },
      modifier = Modifier.size(48.dp).testTag(C.Tag.soundboard_play_pause),
      shape = RoundedCornerShape(12.dp),
  ) {
    if (status == AmbiencePlaybackStatus.PREPARING) {
      CircularProgressIndicator(
          modifier = Modifier.size(20.dp).testTag(C.Tag.soundboard_preparing),
          color = LocalContentColor.current,
          strokeWidth = 2.dp,
      )
    } else {
      Icon(
          if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
          contentDescription =
              stringResource(
                  if (isPlaying) R.string.soundboard_pause_cd else R.string.soundboard_play_cd
              ),
      )
    }
  }
}

// ============ Sound effects ============

/**
 * The sound effects section: a grid of three rows that scrolls sideways, one button per effect.
 * Several effects can play at once.
 */
@Composable
private fun EffectsSection(
    effects: List<SoundEffect>,
    uiState: SoundboardUiState,
    onToggle: (String) -> Unit,
) {
  // The effects read row by row, as the Game Master ordered them: column c holds the effects
  // c, c + columns and c + 2 × columns.
  val columns = (effects.size + EFFECT_ROWS - 1) / EFFECT_ROWS
  SoundboardSection(
      title = stringResource(R.string.soundboard_effects),
      trailing =
          pluralStringResource(R.plurals.soundboard_effects_count, effects.size, effects.size),
  ) {
    LazyRow(
        modifier = Modifier.fillMaxWidth().testTag(C.Tag.soundboard_effects_grid),
        horizontalArrangement = Arrangement.spacedBy(EFFECT_TILE_SPACING),
    ) {
      items(columns) { column ->
        Column(verticalArrangement = Arrangement.spacedBy(EFFECT_TILE_SPACING)) {
          for (row in 0 until EFFECT_ROWS) {
            effects.getOrNull(column + row * columns)?.let { effect ->
              EffectTile(
                  effect = effect,
                  isPlaying = effect.audio.id in uiState.playingEffectIds,
                  isPending = effect.audio.id in uiState.pendingEffectIds,
                  onClick = { onToggle(effect.audio.id) },
              )
            }
          }
        }
      }
    }
  }
}

/**
 * One sound effect button: its icon and name. Outlined in the primary colour while [isPlaying], and
 * disabled while [isPending], so a second tap cannot start the same action twice.
 */
@Composable
private fun EffectTile(
    effect: SoundEffect,
    isPlaying: Boolean,
    isPending: Boolean,
    onClick: () -> Unit,
) {
  val colors = MaterialTheme.colorScheme
  Surface(
      onClick = onClick,
      enabled = !isPending,
      modifier =
          Modifier.width(EFFECT_TILE_WIDTH)
              .height(EFFECT_TILE_HEIGHT)
              .testTag(C.Tag.soundboardEffect(effect.audio.id))
              // Screen readers announce a playing effect as selected, as the border shows it.
              .semantics { selected = isPlaying },
      shape = RoundedCornerShape(10.dp),
      color = colors.surfaceContainerLow,
      border =
          BorderStroke(
              if (isPlaying) 2.dp else 1.dp,
              if (isPlaying) colors.primary else colors.outlineVariant,
          ),
  ) {
    Column(
        modifier = Modifier.padding(4.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Icon(
          effect.icon.imageVector(),
          contentDescription = null,
          modifier = Modifier.size(18.dp),
          tint = colors.primary,
      )
      Text(
          effect.name,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          style = MaterialTheme.typography.labelMedium,
      )
    }
  }
}

// ============ Shared with the soundboard editor ============

/**
 * The title block of the soundboard screens' top bar: a small context [label] over a serif [title].
 */
@Composable
internal fun SoundboardHeader(label: String, title: String) {
  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Text(
        label,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.labelSmall,
    )
    Text(title, fontFamily = FontFamily.Serif, style = MaterialTheme.typography.titleLarge)
  }
}

/**
 * A titled card of the soundboard screens, such as "Ambience": a serif [title], a small [trailing]
 * hint on the right, and the [content] below.
 */
@Composable
internal fun SoundboardSection(
    title: String,
    trailing: String,
    content: @Composable () -> Unit,
) {
  Card(
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary),
      shape = RoundedCornerShape(20.dp),
  ) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            title,
            modifier = Modifier.weight(1f),
            fontFamily = FontFamily.Serif,
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            trailing,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall,
        )
      }
      content()
    }
  }
}

/** The message shown for each repository failure [reason]. */
@StringRes
internal fun soundboardErrorText(reason: SoundboardError): Int =
    when (reason) {
      SoundboardError.NOT_SIGNED_IN -> R.string.soundboard_error_not_signed_in
      SoundboardError.UNSUPPORTED_FORMAT -> R.string.soundboard_error_unsupported_format
      SoundboardError.CORRUPTED_FILE -> R.string.soundboard_error_corrupted_file
      SoundboardError.FILE_NOT_FOUND -> R.string.soundboard_error_file_not_found
      SoundboardError.STORAGE_FULL -> R.string.soundboard_error_storage_full
      SoundboardError.INVALID_NAME -> R.string.soundboard_error_invalid_name
      SoundboardError.INVALID_VOLUME -> R.string.soundboard_error_invalid_volume
      SoundboardError.AUDIO_NOT_FOUND -> R.string.soundboard_error_audio_not_found
      SoundboardError.SOUNDBOARD_NOT_FOUND -> R.string.soundboard_error_soundboard_not_found
      SoundboardError.UNKNOWN -> R.string.soundboard_error_unknown
    }

/** The Material icon drawn for this effect icon. */
internal fun SoundEffectIcon.imageVector(): ImageVector =
    when (this) {
      SoundEffectIcon.SPEAKER -> Icons.AutoMirrored.Outlined.VolumeUp
      SoundEffectIcon.SWORD -> Icons.Outlined.Hardware
      SoundEffectIcon.SHIELD -> Icons.Outlined.Shield
      SoundEffectIcon.ARROW -> Icons.AutoMirrored.Outlined.ArrowForward
      SoundEffectIcon.LIGHTNING -> Icons.Outlined.Bolt
      SoundEffectIcon.FIRE -> Icons.Outlined.LocalFireDepartment
      SoundEffectIcon.SKULL -> Icons.Outlined.Dangerous
      SoundEffectIcon.FOOTSTEPS -> Icons.AutoMirrored.Outlined.DirectionsWalk
      SoundEffectIcon.SCROLL -> Icons.Outlined.HistoryEdu
      SoundEffectIcon.BELL -> Icons.Outlined.Notifications
    }
