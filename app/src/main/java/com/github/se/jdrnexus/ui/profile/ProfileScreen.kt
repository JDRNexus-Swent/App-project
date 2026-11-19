// Co-authored-by: Codex <codex@openai.com>
package com.github.se.jdrnexus.ui.profile

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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

/** Displays the signed-in user's profile and forwards edits to the supplied ViewModel. */
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
  BackHandler(onBack = onBack)

  ProfileContent(state, viewModel, onBack, onTabSelected, selectedTab) {
    pickPicture.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly))
  }
}

@Composable
private fun ProfileContent(
    state: ProfileUiState,
    viewModel: ProfileViewModelInterface,
    onBack: () -> Unit,
    onTabSelected: (String) -> Unit,
    selectedTab: String,
    onPickPicture: () -> Unit,
) {
  val colors = LocalAppUiColors.current
  Scaffold(
      containerColor = colors.background,
      contentColor = colors.primaryText,
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
      // isEmpty might be redundant depending on the VM implementation. Subject to changes
      val isEmpty =
          profile.username.isBlank() && profile.bio.isBlank() && profile.pictureUrl.isBlank()
      if (state.isLoading) {
        CircularProgressIndicator(Modifier.testTag(C.Tag.profile_loading))
      } else {
        state.errorMessage?.let { error ->
          Text(
              error,
              Modifier.testTag(C.Tag.profile_error),
              color = MaterialTheme.colorScheme.error,
          )
          if (isEmpty && !state.isEditing) {
            TextButton(
                onClick = viewModel::loadProfile,
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
          ProfileEditor(state, viewModel)
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
              color = colors.secondaryText,
              textAlign = TextAlign.Center,
          )
          ProfileStatistics(state.stats)
          OutlinedButton(
              onClick = viewModel::startEditing,
              modifier = Modifier.fillMaxWidth().testTag(C.Tag.profile_edit),
              enabled = !state.isSaving,
              border = BorderStroke(1.dp, colors.accent),
              colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.accent),
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
private fun ProfileEditor(state: ProfileUiState, viewModel: ProfileViewModelInterface) {
  ProfileTextField(
      label = "Username",
      hint = NO_USERNAME,
      tag = C.Tag.profile_username,
      value = state.usernameDraft,
      onValueChange = viewModel::onUsernameChange,
      enabled = !state.isSaving,
      error = state.usernameError,
  )
  ProfileTextField(
      label = "Description",
      hint = NO_DESCRIPTION,
      tag = C.Tag.profile_bio,
      value = state.bioDraft,
      onValueChange = viewModel::onBioChange,
      enabled = !state.isSaving,
      error = state.bioError,
      minLines = 3,
  )
  Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
    TextButton(
        onClick = viewModel::cancelEditing,
        modifier = Modifier.testTag(C.Tag.profile_cancel),
        enabled = !state.isSaving,
    ) {
      Text("Cancel")
    }
    Button(
        onClick = viewModel::saveProfile,
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
  Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Text(label, Modifier.clearAndSetSemantics {}, style = MaterialTheme.typography.labelLarge)
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth().testTag(tag).semantics { contentDescription = label },
        enabled = enabled,
        placeholder = { Text(hint) },
        isError = error != null,
        supportingText = error?.let { message -> { Text(message) } },
        singleLine = minLines == 1,
        minLines = minLines,
    )
  }
}

@Composable
private fun ProfileStatistics(stats: ProfileStats) {
  val colors = LocalAppUiColors.current
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
          contentColor = colors.primaryText,
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
              color = colors.secondaryText,
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
  val colors = LocalAppUiColors.current
  val logo = painterResource(R.drawable.app_logo_dark)
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
      Image(logo, "Default profile picture", imageModifier, contentScale = ContentScale.Crop)
    } else {
      AsyncImage(
          model = pictureUrl,
          contentDescription = "Profile picture",
          modifier = imageModifier,
          placeholder = painterResource(R.drawable.loading),
          error = painterResource(R.drawable.error),
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
          tint = colors.accent,
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
//
//  override fun clearError() = Unit
// }
