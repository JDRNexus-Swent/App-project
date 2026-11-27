// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.ui.soundboard

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.se.jdrnexus.R
import com.github.se.jdrnexus.model.soundboard.Soundboard
import com.github.se.jdrnexus.resources.AppTopBar
import com.github.se.jdrnexus.resources.C
import com.github.se.jdrnexus.resources.ScreenBottomBar

/** MIME type of the files the Game Master can import: the soundboard only plays mp3. */
private const val MP3_MIME_TYPE = "audio/mpeg"

// ============ Screen entry ============

/**
 * The soundboard editor: the soundboard's name, its ambience playlists and tracks, and its sound
 * effects, saved together with Save soundboard.
 *
 * The screen holds no state: everything it shows comes from
 * [SoundboardEditViewModelInterface.uiState], and every control calls the ViewModel. Names are
 * edited in place, one ViewModel call per keystroke. Errors are shown inline, next to the control
 * that caused them. The top bar's back and the system back both call
 * [SoundboardEditViewModelInterface.requestLeave], which asks before discarding unsaved changes.
 *
 * @param viewModel Edits the draft and exposes its state.
 * @param soundboardId The soundboard to edit, or null to create a new one.
 * @param onDone Leaves the editor, once the draft is saved or discarded.
 * @param onTabSelected Receives the shared bottom bar's tab selections.
 */
@Composable
fun SoundboardEditScreen(
    viewModel: SoundboardEditViewModelInterface,
    soundboardId: String?,
    onDone: () -> Unit,
    onTabSelected: (String) -> Unit,
) {
  val uiState by viewModel.uiState.collectAsState()
  LaunchedEffect(viewModel, soundboardId) { viewModel.loadSoundboard(soundboardId) }
  // Saved and discarded are final: the editor closes once, when the ViewModel reaches them.
  LaunchedEffect(uiState.status) {
    if (
        uiState.status == SoundboardEditStatus.SAVED ||
            uiState.status == SoundboardEditStatus.DISCARDED
    ) {
      onDone()
    }
  }
  BackHandler(onBack = viewModel::requestLeave)

  Scaffold(
      topBar = {
        AppTopBar(
            title = stringResource(R.string.soundboard_edit_title),
            titleContent = {
              SoundboardHeader(
                  label = stringResource(R.string.soundboard_edit_header_label),
                  title = stringResource(R.string.soundboard_edit_title),
              )
            },
            onBack = viewModel::requestLeave,
        )
      },
      bottomBar = { ScreenBottomBar(selectedTab = "Campaigns", onTabSelected = onTabSelected) },
      contentColor = MaterialTheme.colorScheme.onSurface,
  ) { padding ->
    val draft = uiState.draft
    // enableEdgeToEdge() keeps the window from resizing for the keyboard: imePadding lifts the
    // content above it, and consuming the bars' padding first keeps them from counting twice.
    val contentModifier =
        Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()
    when (val loadState = uiState.loadState) {
      SoundboardEditLoadState.Loading ->
          Box(contentModifier, contentAlignment = Alignment.Center) {
            CircularProgressIndicator(Modifier.testTag(C.Tag.soundboard_edit_loading))
          }
      is SoundboardEditLoadState.Failed ->
          Column(
              modifier = contentModifier.padding(24.dp),
              verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
              horizontalAlignment = Alignment.CenterHorizontally,
          ) {
            Text(
                stringResource(soundboardErrorText(loadState.reason)),
                modifier = Modifier.testTag(C.Tag.soundboard_edit_error),
                style = MaterialTheme.typography.bodyLarge,
            )
            Button(
                onClick = { viewModel.loadSoundboard(soundboardId) },
                modifier = Modifier.testTag(C.Tag.soundboard_edit_retry),
            ) {
              Text(stringResource(R.string.soundboard_retry))
            }
          }
      SoundboardEditLoadState.Ready ->
          if (draft != null) {
            SoundboardEditContent(draft, uiState, viewModel, contentModifier)
          }
    }
  }

  if (uiState.isDiscardDialogVisible) {
    DiscardDialog(onConfirm = viewModel::discard, onDismiss = viewModel::dismissDiscardDialog)
  }
}

// ============ Draft content ============

/**
 * The draft, top to bottom as in the mockup: the name field, the ambience card, the sound effects
 * card and Save soundboard.
 *
 * While the draft is being saved or discarded, nothing can be edited. While a file is imported, the
 * draft stays editable but no second import or save can start.
 */
@Composable
private fun SoundboardEditContent(
    draft: Soundboard,
    uiState: SoundboardEditUiState,
    viewModel: SoundboardEditViewModelInterface,
    modifier: Modifier,
) {
  val status = uiState.status
  val isIdle = status == SoundboardEditStatus.IDLE
  val canEdit =
      isIdle ||
          status == SoundboardEditStatus.IMPORTING_TRACK ||
          status == SoundboardEditStatus.IMPORTING_EFFECT

  LazyColumn(
      modifier = modifier.testTag(C.Tag.soundboard_edit_content),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    item {
      OutlinedTextField(
          value = draft.name,
          onValueChange = viewModel::renameSoundboard,
          enabled = canEdit,
          label = { Text(stringResource(R.string.soundboard_edit_name_label)) },
          isError = uiState.nameError != null,
          supportingText = uiState.nameError?.let { { Text(nameErrorText(it)) } },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag(C.Tag.soundboard_edit_name),
      )
    }
    item { AmbienceEditor(draft, uiState, viewModel, canEdit, isIdle) }
    item { EffectsEditor(draft, uiState, viewModel, canEdit, isIdle) }
    item {
      if (uiState.error is SoundboardEditError.Save) {
        InlineError(
            soundboardErrorText((uiState.error as SoundboardEditError.Save).reason),
            C.Tag.soundboard_edit_save_error,
        )
      }
      Button(
          onClick = viewModel::save,
          enabled = uiState.canSave && isIdle,
          modifier = Modifier.fillMaxWidth().testTag(C.Tag.soundboard_edit_save),
          shape = RoundedCornerShape(12.dp),
      ) {
        if (status == SoundboardEditStatus.SAVING) {
          CircularProgressIndicator(
              modifier = Modifier.size(18.dp).testTag(C.Tag.soundboard_edit_save_progress),
              color = LocalContentColor.current,
              strokeWidth = 2.dp,
          )
        } else {
          Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
        }
        Text(stringResource(R.string.soundboard_edit_save), Modifier.padding(start = 8.dp))
      }
    }
  }
}

/** The message shown under the name field for [error]. */
@Composable
private fun nameErrorText(error: SoundboardNameError): String =
    when (error) {
      SoundboardNameError.EMPTY -> stringResource(R.string.soundboard_edit_name_empty)
      SoundboardNameError.TOO_LONG ->
          pluralStringResource(
              R.plurals.soundboard_edit_name_too_long,
              SoundboardEditViewModelInterface.MAX_NAME_LENGTH,
              SoundboardEditViewModelInterface.MAX_NAME_LENGTH,
          )
    }

// ============ Ambience ============

/**
 * The ambience card: the playlist tabs, Add playlist, the selected playlist's name, its tracks (or
 * a hint when it has none) and Add track. Add track opens the system file picker for an mp3 and
 * imports it into the selected playlist; it is disabled until a playlist exists.
 */
@Composable
private fun AmbienceEditor(
    draft: Soundboard,
    uiState: SoundboardEditUiState,
    viewModel: SoundboardEditViewModelInterface,
    canEdit: Boolean,
    isIdle: Boolean,
) {
  val selected = draft.playlists.firstOrNull { it.id == uiState.selectedPlaylistId }
  val newPlaylistName = stringResource(R.string.soundboard_edit_new_playlist)
  val picker =
      rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val playlistId = uiState.selectedPlaylistId
        if (uri != null && playlistId != null) viewModel.importTrack(playlistId, uri)
      }
  val trackCount = selected?.tracks?.size ?: 0

  SoundboardSection(
      title = stringResource(R.string.soundboard_ambience),
      trailing =
          pluralStringResource(R.plurals.soundboard_edit_tracks_count, trackCount, trackCount),
  ) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      items(draft.playlists, key = { it.id }) { playlist ->
        PlaylistTab(
            playlist = playlist,
            isSelected = playlist.id == uiState.selectedPlaylistId,
            isPlaying = false,
            onClick = { viewModel.selectPlaylist(playlist.id) },
            enabled = canEdit,
        )
      }
    }
    // Under the tabs rather than after them, so it stays visible however many playlists exist.
    TextButton(
        onClick = { viewModel.addPlaylist(newPlaylistName) },
        enabled = canEdit,
        modifier = Modifier.testTag(C.Tag.soundboard_edit_add_playlist),
    ) {
      Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
      Text(stringResource(R.string.soundboard_edit_add_playlist))
    }
    if (draft.playlists.isEmpty()) EmptyHint(R.string.soundboard_edit_no_playlist)
    if (selected != null) {
      OutlinedTextField(
          value = selected.name,
          onValueChange = { viewModel.renamePlaylist(selected.id, it) },
          enabled = canEdit,
          label = { Text(stringResource(R.string.soundboard_edit_playlist_name)) },
          isError = selected.name.isBlank(),
          supportingText =
              if (selected.name.isBlank()) {
                { Text(stringResource(R.string.soundboard_edit_name_required)) }
              } else null,
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag(C.Tag.soundboard_edit_playlist_name),
      )
      if (selected.tracks.isEmpty()) EmptyHint(R.string.soundboard_no_tracks)
      selected.tracks.forEach { track ->
        // Keyed by sound, so a field keeps its focus on its own sound when a row above is removed.
        key(track.audio.id) {
          SoundRow(
              icon = Icons.Outlined.MusicNote,
              name = track.name,
              subtitle =
                  stringResource(
                      R.string.soundboard_edit_track_subtitle,
                      formatDuration(track.audio.durationMs),
                  ),
              enabled = canEdit,
              onRename = { viewModel.renameTrack(selected.id, track.audio.id, it) },
              onRemove = { viewModel.removeTrack(selected.id, track.audio.id) },
              nameTag = C.Tag.soundboardEditTrackName(track.audio.id),
              removeTag = C.Tag.soundboardEditTrackRemove(track.audio.id),
          )
        }
      }
    }
    AddButton(
        text = stringResource(R.string.soundboard_edit_add_track),
        enabled = isIdle && selected != null,
        isImporting = uiState.status == SoundboardEditStatus.IMPORTING_TRACK,
        onClick = { picker.launch(MP3_MIME_TYPE) },
        tag = C.Tag.soundboard_edit_add_track,
        progressTag = C.Tag.soundboard_edit_add_track_progress,
    )
    (uiState.error as? SoundboardEditError.ImportTrack)?.let {
      InlineError(soundboardErrorText(it.reason), C.Tag.soundboard_edit_track_error)
    }
  }
}

// ============ Sound effects ============

/**
 * The sound effects card: one row per effect (or a hint when there are none) and Add sound, which
 * opens the system file picker for an mp3 and imports it as an effect with the default icon.
 */
@Composable
private fun EffectsEditor(
    draft: Soundboard,
    uiState: SoundboardEditUiState,
    viewModel: SoundboardEditViewModelInterface,
    canEdit: Boolean,
    isIdle: Boolean,
) {
  val picker =
      rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let(viewModel::importSoundEffect)
      }

  SoundboardSection(
      title = stringResource(R.string.soundboard_effects),
      trailing =
          pluralStringResource(
              R.plurals.soundboard_edit_sounds_count,
              draft.effects.size,
              draft.effects.size,
          ),
  ) {
    if (draft.effects.isEmpty()) EmptyHint(R.string.soundboard_edit_no_effects)
    draft.effects.forEach { effect ->
      key(effect.audio.id) {
        SoundRow(
            icon = effect.icon.imageVector(),
            name = effect.name,
            subtitle =
                stringResource(
                    R.string.soundboard_edit_effect_subtitle,
                    formatDuration(effect.audio.durationMs),
                ),
            enabled = canEdit,
            onRename = { viewModel.renameSoundEffect(effect.audio.id, it) },
            onRemove = { viewModel.removeSoundEffect(effect.audio.id) },
            nameTag = C.Tag.soundboardEditEffectName(effect.audio.id),
            removeTag = C.Tag.soundboardEditEffectRemove(effect.audio.id),
        )
      }
    }
    AddButton(
        text = stringResource(R.string.soundboard_edit_add_sound),
        enabled = isIdle,
        isImporting = uiState.status == SoundboardEditStatus.IMPORTING_EFFECT,
        onClick = { picker.launch(MP3_MIME_TYPE) },
        tag = C.Tag.soundboard_edit_add_sound,
        progressTag = C.Tag.soundboard_edit_add_sound_progress,
    )
    (uiState.error as? SoundboardEditError.ImportEffect)?.let {
      InlineError(soundboardErrorText(it.reason), C.Tag.soundboard_edit_effect_error)
    }
  }
}

// ============ Rows and buttons shared by both cards ============

/** A one-line hint, in the muted text colour, that stands in for an empty list in a card. */
@Composable
private fun EmptyHint(@StringRes message: Int) {
  Text(
      stringResource(message),
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      style = MaterialTheme.typography.bodySmall,
  )
}

/**
 * One track or effect: its [icon], its [name] edited in place, its [subtitle] and a remove button.
 * Tapping the name edits it; each keystroke calls [onRename]. A blank name is shown as "Name
 * required" in the error colour, since the draft cannot be saved with it.
 */
@Composable
private fun SoundRow(
    icon: ImageVector,
    name: String,
    subtitle: String,
    enabled: Boolean,
    onRename: (String) -> Unit,
    onRemove: () -> Unit,
    nameTag: String,
    removeTag: String,
) {
  val colors = MaterialTheme.colorScheme
  Row(
      horizontalArrangement = Arrangement.spacedBy(10.dp),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = colors.surfaceContainerLow,
        border = BorderStroke(1.dp, colors.outlineVariant),
    ) {
      Icon(
          icon,
          contentDescription = null,
          tint = colors.primary,
          modifier = Modifier.padding(8.dp),
      )
    }
    Column(Modifier.weight(1f)) {
      BasicTextField(
          value = name,
          onValueChange = onRename,
          enabled = enabled,
          singleLine = true,
          textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
          cursorBrush = SolidColor(colors.primary),
          modifier = Modifier.fillMaxWidth().testTag(nameTag),
          decorationBox = { field ->
            Box {
              if (name.isBlank()) {
                Text(
                    stringResource(R.string.soundboard_edit_name_required),
                    color = colors.error,
                    style = MaterialTheme.typography.bodyLarge,
                )
              }
              field()
            }
          },
      )
      Text(subtitle, color = colors.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
    }
    OutlinedIconButton(
        onClick = onRemove,
        enabled = enabled,
        modifier = Modifier.size(32.dp).testTag(removeTag),
        border = BorderStroke(1.dp, colors.outlineVariant),
    ) {
      Icon(
          Icons.Filled.Remove,
          contentDescription =
              if (name.isBlank()) stringResource(R.string.soundboard_edit_remove_unnamed_cd)
              else stringResource(R.string.soundboard_edit_remove_cd, name),
          tint = colors.error,
          modifier = Modifier.size(16.dp),
      )
    }
  }
}

/**
 * Add track or Add sound: opens the file picker. Shows a spinner while its own import runs
 * ([isImporting]), and is disabled while any import or save runs.
 */
@Composable
private fun AddButton(
    text: String,
    enabled: Boolean,
    isImporting: Boolean,
    onClick: () -> Unit,
    tag: String,
    progressTag: String,
) {
  OutlinedButton(
      onClick = onClick,
      enabled = enabled,
      modifier = Modifier.fillMaxWidth().testTag(tag),
      shape = RoundedCornerShape(10.dp),
  ) {
    if (isImporting) {
      CircularProgressIndicator(
          modifier = Modifier.size(16.dp).testTag(progressTag),
          color = LocalContentColor.current,
          strokeWidth = 2.dp,
      )
    } else {
      Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
    }
    Text(text, Modifier.padding(start = 8.dp))
  }
}

// ============ Discard dialog ============

/**
 * Asks the Game Master to confirm leaving without saving. [onConfirm] discards the draft;
 * [onDismiss] keeps editing.
 */
@Composable
private fun DiscardDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
  AlertDialog(
      onDismissRequest = onDismiss,
      modifier = Modifier.testTag(C.Tag.soundboard_edit_discard_dialog),
      title = { Text(stringResource(R.string.soundboard_edit_discard_title)) },
      text = { Text(stringResource(R.string.soundboard_edit_discard_body)) },
      confirmButton = {
        TextButton(
            onClick = onConfirm,
            modifier = Modifier.testTag(C.Tag.soundboard_edit_discard_confirm),
        ) {
          Text(stringResource(R.string.soundboard_edit_discard_confirm))
        }
      },
      dismissButton = {
        TextButton(
            onClick = onDismiss,
            modifier = Modifier.testTag(C.Tag.soundboard_edit_discard_cancel),
        ) {
          Text(stringResource(R.string.soundboard_edit_discard_cancel))
        }
      },
  )
}
