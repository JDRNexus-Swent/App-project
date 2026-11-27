package com.github.se.jdrnexus.ui.profile

// Co-authored-by: Codex <codex@openai.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.github.se.jdrnexus.R
import com.github.se.jdrnexus.model.profile.ProfileStats
import com.github.se.jdrnexus.resources.AppTopBar
import com.github.se.jdrnexus.resources.C
import com.github.se.jdrnexus.resources.ScreenBottomBar
import com.github.se.jdrnexus.ui.theme.LocalAppUiColors
import com.github.se.jdrnexus.viewmodel.ProfileUiState
import com.github.se.jdrnexus.viewmodel.ProfileViewModelInterface

/**
 * Displays the signed-in user's profile and forwards edits to the supplied ViewModel.
 *
 * System back cancels the edit form when no save is running. Leaving the screen (back button,
 * system back, or a bottom-bar tab) while [ProfileUiState.isSaving] is true first asks the user to
 * confirm, since leaving cancels the save or picture upload. The dialog closes by itself when the
 * save ends.
 */
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModelInterface,
    onBack: () -> Unit,
    onTabSelected: (String) -> Unit,
    selectedTab: String = "Home",
) {
  val state by viewModel.uiState.collectAsState()
  val pickPicture =
      rememberLauncherForActivityResult(PickVisualMedia()) { uri ->
        uri?.let(viewModel::updateProfilePicture)
      }
  LaunchedEffect(viewModel) { viewModel.loadProfile() }
  // The exit waiting for confirmation: BACK_EXIT or the name of a tab. Saveable so the dialog
  // survives a rotation.
  var pendingExit by rememberSaveable { mutableStateOf<String?>(null) }
  val leave = { exit: String -> if (exit == BACK_EXIT) onBack() else onTabSelected(exit) }
  val requestExit = { exit: String -> if (state.isSaving) pendingExit = exit else leave(exit) }
  LaunchedEffect(state.isSaving) { if (!state.isSaving) pendingExit = null }
  // A running save comes first: the editor's Cancel button is disabled meanwhile, so system back
  // asks to leave instead of cancelling the edit.
  BackHandler {
    if (state.isEditing && !state.isSaving) viewModel.cancelEditing() else requestExit(BACK_EXIT)
  }

  pendingExit?.let { exit ->
    LeaveWhileSavingDialog(
        onLeave = {
          pendingExit = null
          leave(exit)
        },
        onStay = { pendingExit = null },
    )
  }

  ProfileContent(
      state,
      onRetry = viewModel::loadProfile,
      onStartEditing = viewModel::startEditing,
      onUsernameChange = viewModel::onUsernameChange,
      onBioChange = viewModel::onBioChange,
      onCancel = viewModel::cancelEditing,
      onSave = viewModel::saveProfile,
      onBack = { requestExit(BACK_EXIT) },
      onTabSelected = requestExit,
      selectedTab = selectedTab,
  ) {
    pickPicture.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly))
  }
}

/** [ProfileScreen]'s pending exit for the back button, as opposed to a tab name. */
private const val BACK_EXIT = "back"

/** Asks whether to leave the profile while a save or picture upload is still running. */
@Composable
private fun LeaveWhileSavingDialog(onLeave: () -> Unit, onStay: () -> Unit) {
  AlertDialog(
      modifier = Modifier.testTag(C.Tag.profile_leave_dialog),
      onDismissRequest = onStay,
      title = { Text(stringResource(R.string.profile_leave_title)) },
      text = { Text(stringResource(R.string.profile_leave_message)) },
      confirmButton = {
        TextButton(onClick = onLeave, modifier = Modifier.testTag(C.Tag.profile_leave_confirm)) {
          Text(stringResource(R.string.profile_leave_confirm))
        }
      },
      dismissButton = {
        TextButton(onClick = onStay, modifier = Modifier.testTag(C.Tag.profile_leave_stay)) {
          Text(stringResource(R.string.profile_leave_stay))
        }
      },
  )
}

@Composable
private fun ProfileContent(
    state: ProfileUiState,
    onRetry: () -> Unit,
    onStartEditing: () -> Unit,
    onUsernameChange: (String) -> Unit,
    onBioChange: (String) -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
    onTabSelected: (String) -> Unit,
    selectedTab: String,
    onPickPicture: () -> Unit,
) {
  val colors = MaterialTheme.colorScheme
  Scaffold(
      containerColor = colors.background,
      contentColor = colors.onBackground,
      topBar = { AppTopBar(title = "Profile", onBack = onBack) },
      bottomBar = { ScreenBottomBar(selectedTab, onTabSelected) },
  ) { padding ->
    Column(
        modifier =
            Modifier.fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      val profile = state.profile
      val profileLoaded = profile.userId.isNotBlank()
      if (state.isLoading) {
        CircularProgressIndicator(Modifier.testTag(C.Tag.profile_loading))
      } else {
        state.errorMessage?.let { error ->
          Text(
              error,
              Modifier.testTag(C.Tag.profile_error),
              color = colors.error,
          )
          if (!profileLoaded && !state.isEditing) {
            TextButton(
                onClick = onRetry,
                modifier = Modifier.testTag(C.Tag.profile_retry),
            ) {
              Text("Retry")
            }
          }
        }
        if (state.isSaving) {
          LinearProgressIndicator(Modifier.fillMaxWidth().testTag(C.Tag.profile_saving))
        }
        ProfilePicture(
            pictureUrl = profile.pictureUrl,
            isEditable = state.isEditing,
            onPickPicture = onPickPicture,
            enabled = !state.isSaving,
        )
        if (state.isEditing) {
          ProfileEditor(state, onUsernameChange, onBioChange, onCancel, onSave)
        } else {
          Text(
              profile.username.ifBlank { NO_USERNAME },
              Modifier.testTag(C.Tag.profile_username),
              style = MaterialTheme.typography.headlineMedium,
              textAlign = TextAlign.Center,
          )
          Text(
              profile.bio.ifBlank { NO_DESCRIPTION },
              Modifier.testTag(C.Tag.profile_bio),
              color = colors.onSurfaceVariant,
              textAlign = TextAlign.Center,
          )
          ProfileStatistics(state.stats)
          OutlinedButton(
              onClick = onStartEditing,
              modifier = Modifier.fillMaxWidth().testTag(C.Tag.profile_edit),
              enabled = !state.isSaving,
              border = BorderStroke(1.dp, colors.primary),
              colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.primary),
          ) {
            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text("Edit profile")
          }
        }
      }
    }
  }
}

@Composable
private fun ProfileEditor(
    state: ProfileUiState,
    onUsernameChange: (String) -> Unit,
    onBioChange: (String) -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
) {
  ProfileTextField(
      label = "Username",
      hint = "Choose a username",
      tag = C.Tag.profile_username,
      value = state.usernameDraft,
      onValueChange = onUsernameChange,
      enabled = !state.isSaving,
      error = state.usernameError,
  )
  ProfileTextField(
      label = "Description",
      hint = "Tell other players about yourself",
      tag = C.Tag.profile_bio,
      value = state.bioDraft,
      onValueChange = onBioChange,
      enabled = !state.isSaving,
      error = state.bioError,
      minLines = 3,
  )
  Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
    TextButton(
        onClick = onCancel,
        modifier = Modifier.testTag(C.Tag.profile_cancel),
        enabled = !state.isSaving,
    ) {
      Text("Cancel")
    }
    Button(
        onClick = onSave,
        modifier = Modifier.testTag(C.Tag.profile_save),
        enabled = state.canSave && !state.isSaving,
    ) {
      Text("Save")
    }
  }
}

@Composable
private fun ProfileTextField(
    label: String,
    hint: String,
    tag: String,
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    error: String?,
    minLines: Int = 1,
) {
  OutlinedTextField(
      value = value,
      onValueChange = onValueChange,
      modifier = Modifier.fillMaxWidth().testTag(tag),
      enabled = enabled,
      label = { Text(label) },
      placeholder = { Text(hint) },
      isError = error != null,
      supportingText = error?.let { message -> { Text(message) } },
      singleLine = minLines == 1,
      minLines = minLines,
  )
}

@Composable
private fun ProfileStatistics(stats: ProfileStats) {
  val colors = MaterialTheme.colorScheme
  val counters =
      listOf(
          "Campaigns created" to stats.campaignsCreated,
          "Groups joined" to stats.groupsJoined,
          "Files created" to stats.filesCreated,
      )
  Row(
      Modifier.fillMaxWidth().height(IntrinsicSize.Min).testTag(C.Tag.profile_stats),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    counters.forEach { (label, value) ->
      Surface(
          modifier = Modifier.weight(1f).fillMaxHeight(),
          shape = MaterialTheme.shapes.medium,
          color = colors.surface,
          contentColor = colors.onSurface,
          border = BorderStroke(1.dp, colors.outline),
      ) {
        Column(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          Text(value.toString(), style = MaterialTheme.typography.headlineSmall)
          Text(
              label,
              color = colors.onSurfaceVariant,
              style = MaterialTheme.typography.labelSmall,
              textAlign = TextAlign.Center,
          )
        }
      }
    }
  }
}

@Composable
private fun ProfilePicture(
    pictureUrl: String,
    isEditable: Boolean,
    onPickPicture: () -> Unit,
    enabled: Boolean,
) {
  val colors = MaterialTheme.colorScheme
  val pickerModifier =
      if (!isEditable) {
        Modifier
      } else {
        Modifier.testTag(C.Tag.profile_pick_picture)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClickLabel = "Change profile picture",
                onClick = onPickPicture,
            )
      }
  Box(
      Modifier.size(100.dp)
          .clip(CircleShape)
          .background(colors.surface)
          .border(1.dp, colors.outline, CircleShape)
          .then(pickerModifier),
  ) {
    val imageModifier = Modifier.fillMaxSize().testTag(C.Tag.profile_picture)
    if (pictureUrl.isBlank()) {
      val logo =
          if (LocalAppUiColors.current.isDarkTheme) R.drawable.app_logo_dark
          else R.drawable.app_logo_light
      Image(
          painterResource(logo),
          "Default profile picture",
          imageModifier,
          contentScale = ContentScale.Crop,
      )
    } else {
      AsyncImage(
          model = pictureUrl,
          contentDescription = "Profile picture",
          modifier = imageModifier,
          placeholder = painterResource(R.drawable.profile_picture_loading),
          error = painterResource(R.drawable.profile_picture_error),
          contentScale = ContentScale.Crop,
      )
    }
    if (isEditable) {
      Icon(
          Icons.Default.Edit,
          contentDescription = null,
          modifier =
              Modifier.align(Alignment.BottomEnd)
                  .padding(10.dp)
                  .background(colors.surface, CircleShape)
                  .padding(5.dp)
                  .size(16.dp),
          tint = colors.primary,
      )
    }
  }
}

private const val NO_USERNAME = "No username"
private const val NO_DESCRIPTION = "No description"

// @Preview(name = "Light", showBackground = true)
// @Preview(
//    name = "Dark",
//    showBackground = true,
//    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
// )
// @Composable
// private fun ProfileScreenPreview() {
//  ProfilePreview(PreviewProfileViewModel.previewState)
// }
//
// @Preview(name = "Editing", showBackground = true)
// @Preview(
//    name = "Dark Editing",
//    showBackground = true,
//    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
// )
// @Composable
// private fun EditingProfileScreenPreview() {
//  ProfilePreview(
//      PreviewProfileViewModel.previewState.copy(
//          isEditing = true,
//          usernameDraft = "Mira Chen",
//      )
//  )
// }
//
// @Composable
// private fun ProfilePreview(state: ProfileUiState) {
//  SampleAppTheme {
//    ProfileContent(
//        state = state,
//        viewModel = PreviewProfileViewModel,
//        onBack = {},
//        onTabSelected = {},
//        selectedTab = "Home",
//        onPickPicture = {},
//    )
//  }
// }
//
// private object PreviewProfileViewModel : ProfileViewModelInterface {
//  val previewState =
//      ProfileUiState(
//          profile = UserProfile(username = "Mira Chen", bio = "Game master of the Lantern
// Below."),
//          stats = ProfileStats(campaignsCreated = 4, groupsJoined = 6, filesCreated = 23),
//          isLoading = false,
//      )
//  override val uiState = MutableStateFlow(previewState)
//
//  override fun loadProfile() = Unit
//
//  override fun startEditing() = Unit
//
//  override fun onUsernameChange(username: String) = Unit
//
//  override fun onBioChange(bio: String) = Unit
//
//  override fun cancelEditing() = Unit
//
//  override fun saveProfile() = Unit
//
//  override fun updateProfilePicture(image: Uri) = Unit
// }
