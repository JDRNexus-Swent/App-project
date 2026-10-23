// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.github.se.jdrnexus.ui.gameManagement

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.github.se.jdrnexus.model.personalSpace.CampaignGroup
import com.github.se.jdrnexus.resources.JdrNexusLogo
import com.github.se.jdrnexus.ui.theme.SampleAppTheme
import com.github.se.jdrnexus.viewmodel.GameManagementUiState
import com.github.se.jdrnexus.viewmodel.GameManagementViewModel
import com.github.se.jdrnexus.viewmodel.GroupLoadState
import com.github.se.jdrnexus.viewmodel.GroupRole
import com.github.se.jdrnexus.viewmodel.UserGroup

private data class GameManagementColors(
    val background: Color,
    val card: Color,
    val raised: Color,
    val text: Color,
    val secondaryText: Color,
    val outline: Color,
    val accent: Color,
    val accentContent: Color,
)

private val lightGameManagementColors =
    GameManagementColors(
        background = Color(0xFFF5E9DE),
        card = Color(0xFFE4D5C8),
        raised = Color(0xFFF7EDE4),
        text = Color(0xFF33251D),
        secondaryText = Color(0xFF96745E),
        outline = Color(0xFFB9A08D),
        accent = Color(0xFF704222),
        accentContent = Color(0xFFFFFFFF),
    )

private val darkGameManagementColors =
    GameManagementColors(
        background = Color(0xFF17100D),
        card = Color(0xFF2C211B),
        raised = Color(0xFF1C140F),
        text = Color(0xFFE9DDD2),
        secondaryText = Color(0xFFA98E79),
        outline = Color(0xFF705E50),
        accent = Color(0xFFE2BC32),
        accentContent = Color(0xFF17100D),
    )

internal object GameManagementTestTags {
  const val CREATE_GROUP_BUTTON = "game_management_create_group"
  const val CREATE_GROUP_DIALOG = "game_management_create_group_dialog"
  const val GROUP_NAME_FIELD = "game_management_group_name"
  const val CREATE_GROUP_CONFIRM = "game_management_create_group_confirm"
}

/**
 * Displays the signed-in user's campaign groups and their available actions.
 *
 * Selecting "Enter workspace" invokes [onEnterWorkspace] with that group's ID so the destination
 * can load its shared files. [gameManagementViewModel] provides the loading and creation state.
 */
@Composable
fun GameManagementScreen(
    onEnterWorkspace: (String) -> Unit,
    onManageGroup: (String) -> Unit = {},
    onTabSelected: (String) -> Unit = {},
    gameManagementViewModel: GameManagementViewModel,
) {
  val uiState by gameManagementViewModel.uiState.collectAsState()
  LaunchedEffect(gameManagementViewModel) { gameManagementViewModel.loadGroups() }

  GameManagementScreenContent(
      uiState = uiState,
      onCreateGroupClick = gameManagementViewModel::openCreateDialog,
      onGroupNameChanged = gameManagementViewModel::onGroupNameChanged,
      onCreateGroup = { gameManagementViewModel.createGroup(uiState.groupName) },
      onDismissCreateDialog = gameManagementViewModel::closeCreateDialog,
      onRetryLoad = gameManagementViewModel::loadGroups,
      onDismissSuccess = gameManagementViewModel::dismissSuccessMessage,
      onEnterWorkspace = onEnterWorkspace,
      onManageGroup = onManageGroup,
      onInvite = gameManagementViewModel::requestInvitationPlaceholder,
      onTabSelected = onTabSelected,
  )
}

/** Renders the game-management screen from state and callbacks, without owning screen state. */
@Composable
internal fun GameManagementScreenContent(
    uiState: GameManagementUiState,
    onCreateGroupClick: () -> Unit,
    onGroupNameChanged: (String) -> Unit,
    onCreateGroup: () -> Unit,
    onDismissCreateDialog: () -> Unit,
    onRetryLoad: () -> Unit,
    onDismissSuccess: () -> Unit,
    onEnterWorkspace: (String) -> Unit,
    onManageGroup: (String) -> Unit,
    onInvite: (String) -> Unit,
    onTabSelected: (String) -> Unit,
    darkTheme: Boolean = isSystemInDarkTheme(),
    renderCreateDialogAsWindow: Boolean = true,
) {
  val colors = if (darkTheme) darkGameManagementColors else lightGameManagementColors

  Scaffold(
      containerColor = colors.background,
      bottomBar = {
        GameManagementBottomBar(
            colors = colors,
            onTabSelected = onTabSelected,
        )
      },
  ) { contentPadding ->
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(contentPadding),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      item { GameManagementHeader(colors) }
      item { GroupHeading(uiState = uiState, colors = colors) }
      item {
        Button(
            onClick = onCreateGroupClick,
            modifier =
                Modifier.fillMaxWidth()
                    .height(48.dp)
                    .testTag(GameManagementTestTags.CREATE_GROUP_BUTTON),
            shape = RoundedCornerShape(50),
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = colors.accent,
                    contentColor = colors.accentContent,
                ),
        ) {
          Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(8.dp))
          Text("Create a new group", fontWeight = FontWeight.Medium)
        }
      }
      if (uiState.successMessage.isNotEmpty()) {
        item {
          SuccessBanner(
              message = uiState.successMessage,
              colors = colors,
              onDismiss = onDismissSuccess,
          )
        }
      }
      when (val loadState = uiState.loadState) {
        GroupLoadState.Loading -> item { GroupLoadingCard(colors) }
        is GroupLoadState.Error ->
            item {
              GroupLoadError(
                  message = loadState.message,
                  colors = colors,
                  onRetry = onRetryLoad,
              )
            }
        GroupLoadState.Empty ->
            item {
              EmptyGroupsCard(
                  colors = colors,
                  onCreateGroup = onCreateGroupClick,
              )
            }
        GroupLoadState.Success ->
            if (uiState.groups.isEmpty()) {
              item {
                EmptyGroupsCard(
                    colors = colors,
                    onCreateGroup = onCreateGroupClick,
                )
              }
            } else {
              items(uiState.groups, key = { it.group.id }) { userGroup ->
                CampaignGroupCard(
                    group = userGroup.group,
                    isGameMaster = userGroup.role == GroupRole.GAME_MASTER,
                    colors = colors,
                    onEnterWorkspace = { onEnterWorkspace(userGroup.group.id) },
                    onManageGroup = { onManageGroup(userGroup.group.id) },
                    onInvite = { onInvite(userGroup.group.id) },
                )
              }
            }
      }
      item {
        Text(
            text =
                "Group creation starts a new party. Management and invitations belong only to that group's GameMaster.",
            color = colors.secondaryText,
            fontSize = 10.sp,
            lineHeight = 15.sp,
            modifier = Modifier.padding(top = 2.dp),
        )
      }
    }
  }

  if (uiState.isCreateDialogOpen) {
    CreateGroupDialog(
        uiState = uiState,
        colors = colors,
        renderAsWindow = renderCreateDialogAsWindow,
        onNameChanged = onGroupNameChanged,
        onCreate = onCreateGroup,
        onDismiss = onDismissCreateDialog,
    )
  }
}

/** Shows the profile, app identity, and notification affordance at the top of the screen. */
@Composable
private fun GameManagementHeader(colors: GameManagementColors) {
  Row(
      modifier = Modifier.fillMaxWidth().height(76.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Box(
        modifier =
            Modifier.size(38.dp)
                .clip(CircleShape)
                .background(colors.raised)
                .border(1.dp, colors.outline, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
      Icon(Icons.Filled.Person, contentDescription = "Profile", tint = colors.secondaryText)
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      JdrNexusLogo(Modifier.size(34.dp))
      Text(
          text = "JDRNexus",
          color = colors.text,
          fontFamily = FontFamily.Serif,
          fontSize = 20.sp,
          lineHeight = 22.sp,
      )
    }
    IconButton(onClick = {}, modifier = Modifier.size(38.dp)) {
      Icon(
          Icons.Filled.Notifications,
          contentDescription = "Notifications",
          tint = colors.accent,
      )
    }
  }
}

/** Displays the group section title and the loaded group count, when available. */
@Composable
private fun GroupHeading(uiState: GameManagementUiState, colors: GameManagementColors) {
  Column(modifier = Modifier.fillMaxWidth()) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(
          text = "Your groups",
          color = colors.text,
          fontFamily = FontFamily.Serif,
          fontSize = 27.sp,
          letterSpacing = 1.2.sp,
          modifier = Modifier.weight(1f),
      )
      Box(Modifier.width(26.dp).height(1.dp).background(colors.outline))
      Text("◆", color = colors.accent, fontSize = 8.sp, modifier = Modifier.padding(start = 8.dp))
    }
    val subtitle =
        if (uiState.loadState is GroupLoadState.Success) {
          val count = uiState.groups.size
          "${if (count == 1) "1 group" else "$count groups"} · Your next chapter, together."
        } else {
          "Your next chapter, together."
        }
    Text(
        text = subtitle,
        color = colors.secondaryText,
        fontSize = 12.sp,
        modifier = Modifier.padding(top = 4.dp),
    )
  }
}

/** Presents a successful group-creation message with a dismiss action. */
@Composable
private fun SuccessBanner(
    message: String,
    colors: GameManagementColors,
    onDismiss: () -> Unit,
) {
  Row(
      modifier =
          Modifier.fillMaxWidth()
              .clip(RoundedCornerShape(14.dp))
              .background(colors.card)
              .border(1.dp, colors.outline, RoundedCornerShape(14.dp))
              .padding(horizontal = 12.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = colors.accent)
    Text(
        message,
        color = colors.text,
        fontSize = 12.sp,
        modifier = Modifier.weight(1f).padding(start = 8.dp),
    )
    TextButton(onClick = onDismiss, contentPadding = PaddingValues(horizontal = 6.dp)) {
      Text("Dismiss", color = colors.secondaryText, fontSize = 10.sp)
    }
  }
}

/** Shows progress while the user's groups are being loaded. */
@Composable
private fun GroupLoadingCard(colors: GameManagementColors) {
  Column(
      modifier =
          Modifier.fillMaxWidth()
              .clip(RoundedCornerShape(18.dp))
              .background(colors.card)
              .border(1.dp, colors.outline, RoundedCornerShape(18.dp))
              .padding(24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Text(
        "Your groups",
        color = colors.text,
        fontFamily = FontFamily.Serif,
        fontSize = 21.sp,
        modifier = Modifier.align(Alignment.Start),
    )
    CircularProgressIndicator(
        color = colors.accent,
        modifier = Modifier.size(30.dp),
        strokeWidth = 2.dp,
    )
    Text("Loading your groups...", color = colors.text, fontSize = 12.sp)
    Text("Gathering your parties and memberships.", color = colors.secondaryText, fontSize = 11.sp)
  }
}

/** Displays a group-loading error and lets the user retry. */
@Composable
private fun GroupLoadError(
    message: String,
    colors: GameManagementColors,
    onRetry: () -> Unit,
) {
  Column(
      modifier =
          Modifier.fillMaxWidth()
              .clip(RoundedCornerShape(18.dp))
              .background(colors.card)
              .border(1.dp, colors.outline, RoundedCornerShape(18.dp))
              .padding(20.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Text(
        "Couldn't load groups",
        color = colors.text,
        fontFamily = FontFamily.Serif,
        fontSize = 21.sp,
    )
    Text(message, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
    Text(
        "Check your connection, then try again. Your memberships haven't changed.",
        color = colors.secondaryText,
        fontSize = 12.sp,
    )
    OutlinedActionButton(
        label = "Retry loading",
        colors = colors,
        onClick = onRetry,
    )
  }
}

/** Prompts users without groups to create their first campaign group. */
@Composable
private fun EmptyGroupsCard(
    colors: GameManagementColors,
    onCreateGroup: () -> Unit,
) {
  Column(
      modifier =
          Modifier.fillMaxWidth()
              .clip(RoundedCornerShape(18.dp))
              .background(colors.card)
              .border(1.dp, colors.outline, RoundedCornerShape(18.dp))
              .padding(18.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    Icon(Icons.Filled.Person, contentDescription = null, tint = colors.accent)
    Text(
        "Your party awaits",
        color = colors.text,
        fontFamily = FontFamily.Serif,
        fontSize = 23.sp,
        letterSpacing = 1.sp,
    )
    Text(
        "You don't belong to any groups yet. Create one to begin your shared story.",
        color = colors.secondaryText,
        fontSize = 12.sp,
    )
    PrimaryActionButton(
        label = "Create your first group",
        colors = colors,
        onClick = onCreateGroup,
    )
  }
}

/** Shows a campaign's details and actions appropriate to the user's role. */
@Composable
private fun CampaignGroupCard(
    group: CampaignGroup,
    isGameMaster: Boolean,
    colors: GameManagementColors,
    onEnterWorkspace: () -> Unit,
    onManageGroup: () -> Unit,
    onInvite: () -> Unit,
) {
  Column(
      modifier =
          Modifier.fillMaxWidth()
              .clip(RoundedCornerShape(18.dp))
              .background(colors.card)
              .border(1.dp, colors.outline, RoundedCornerShape(18.dp)),
  ) {
    CampaignBanner(groupName = group.name, colors = colors)
    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Row(
            modifier =
                Modifier.clip(RoundedCornerShape(5.dp))
                    .background(colors.raised)
                    .padding(horizontal = 7.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
          Icon(
              if (isGameMaster) Icons.Filled.Settings else Icons.Filled.Person,
              contentDescription = null,
              modifier = Modifier.size(11.dp),
              tint = colors.accent,
          )
          Text(
              text = if (isGameMaster) "GameMaster" else "Player",
              color = colors.accent,
              fontSize = 9.sp,
              modifier = Modifier.padding(start = 4.dp),
          )
        }
        Spacer(Modifier.weight(1f))
        Text("Your group", color = colors.secondaryText, fontSize = 10.sp)
      }
      Text(
          text = group.name,
          color = colors.text,
          fontFamily = FontFamily.Serif,
          fontSize = 21.sp,
          letterSpacing = 0.8.sp,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.padding(top = 7.dp),
      )
      val details = group.description.takeIf(String::isNotBlank) ?: "Campaign system not set"
      val memberCount = group.participantIds.size
      val members = if (memberCount == 1) "1 member" else "$memberCount members"
      Text(
          text = "$details · $members",
          color = colors.secondaryText,
          fontSize = 10.sp,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.padding(top = 4.dp, bottom = 10.dp),
      )
      OutlinedActionButton(
          label = "Enter workspace",
          colors = colors,
          icon = { Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(16.dp)) },
          onClick = onEnterWorkspace,
      )
      if (isGameMaster) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
          TextButton(
              onClick = onManageGroup,
              contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp),
          ) {
            Icon(Icons.Filled.Settings, null, tint = colors.accent, modifier = Modifier.size(13.dp))
            Text(
                "Manage group",
                color = colors.text,
                fontSize = 10.sp,
                modifier = Modifier.padding(start = 4.dp),
            )
          }
          Text("·", color = colors.outline)
          TextButton(
              onClick = onInvite,
              enabled = false,
              contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp),
          ) {
            Icon(
                Icons.Filled.Lock,
                null,
                tint = colors.secondaryText,
                modifier = Modifier.size(12.dp),
            )
            Text(
                "Invite · Coming soon",
                color = colors.secondaryText,
                fontSize = 10.sp,
                modifier = Modifier.padding(start = 4.dp),
            )
          }
        }
      }
    }
  }
}

/** Renders the decorative campaign banner using the selected theme colors. */
@Composable
private fun CampaignBanner(groupName: String, colors: GameManagementColors) {
  val colorsForBanner =
      if (colors == darkGameManagementColors) {
        listOf(Color(0xFF51402F), Color(0xFF271C17), Color(0xFF80632D))
      } else {
        listOf(Color(0xFF9D7354), Color(0xFF4E5D55), Color(0xFFC49B66))
      }
  Box(
      modifier =
          Modifier.fillMaxWidth()
              .height(82.dp)
              .background(Brush.horizontalGradient(colorsForBanner)),
      contentAlignment = Alignment.Center,
  ) {
    Text(
        text = "✦     ${groupName.take(1).uppercase()}     ✦",
        color = Color.White.copy(alpha = 0.75f),
        fontFamily = FontFamily.Serif,
        fontSize = 24.sp,
        letterSpacing = 8.sp,
    )
  }
}

/** Renders a filled action button used for primary calls to action. */
@Composable
private fun PrimaryActionButton(
    label: String,
    colors: GameManagementColors,
    onClick: () -> Unit,
) {
  Button(
      onClick = onClick,
      modifier = Modifier.fillMaxWidth().height(44.dp),
      shape = RoundedCornerShape(12.dp),
      colors =
          ButtonDefaults.buttonColors(
              containerColor = colors.accent,
              contentColor = colors.accentContent,
          ),
  ) {
    Icon(Icons.Filled.Add, null, modifier = Modifier.size(16.dp))
    Spacer(Modifier.width(6.dp))
    Text(label, fontSize = 12.sp)
  }
}

/**
 * Renders an outlined action button with optional leading icon content.
 *
 * @param icon Optional composable content placed before the button label.
 */
@Composable
private fun OutlinedActionButton(
    label: String,
    colors: GameManagementColors,
    onClick: () -> Unit,
    icon: (@Composable () -> Unit)? = null,
) {
  Button(
      onClick = onClick,
      modifier = Modifier.fillMaxWidth().height(42.dp),
      shape = RoundedCornerShape(10.dp),
      border = androidx.compose.foundation.BorderStroke(1.dp, colors.outline),
      colors =
          ButtonDefaults.buttonColors(
              containerColor = colors.raised,
              contentColor = colors.accent,
          ),
      contentPadding = PaddingValues(horizontal = 12.dp),
  ) {
    icon?.invoke()
    if (icon != null) Spacer(Modifier.width(8.dp))
    Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium)
  }
}

/** Collects the required group name and reports validation, progress, and creation errors. */
@Composable
private fun CreateGroupDialog(
    uiState: GameManagementUiState,
    colors: GameManagementColors,
    renderAsWindow: Boolean,
    onNameChanged: (String) -> Unit,
    onCreate: () -> Unit,
    onDismiss: () -> Unit,
) {
  var nameTouched by rememberSaveable { mutableStateOf(false) }
  val nameIsEmpty = uiState.groupName.isBlank()
  val showRequiredError = nameTouched && nameIsEmpty
  val isFailure = uiState.createError.isNotEmpty() && !nameIsEmpty
  val dialogContent: @Composable () -> Unit = {
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(colors.card)
                .border(1.dp, colors.outline, RoundedCornerShape(18.dp))
                .padding(18.dp)
                .testTag(GameManagementTestTags.CREATE_GROUP_DIALOG),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "Create a group",
            color = colors.text,
            fontFamily = FontFamily.Serif,
            fontSize = 23.sp,
            letterSpacing = 1.sp,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onDismiss, enabled = !uiState.isCreatingGroup) {
          Icon(Icons.Filled.Close, contentDescription = "Close", tint = colors.secondaryText)
        }
      }
      Text(
          "Gather your party. You'll be the GameMaster of this new group.",
          color = colors.secondaryText,
          fontSize = 12.sp,
      )
      Text("Group name · Required", color = colors.text, fontSize = 11.sp)
      if (showRequiredError) {
        Text(
            "A group name is required.",
            color = MaterialTheme.colorScheme.error,
            fontSize = 11.sp,
        )
      }
      OutlinedTextField(
          value = uiState.groupName,
          onValueChange = {
            nameTouched = true
            onNameChanged(it)
          },
          enabled = !uiState.isCreatingGroup,
          placeholder = { Text("Name your adventuring party") },
          singleLine = true,
          shape = RoundedCornerShape(10.dp),
          keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
          isError = showRequiredError || isFailure,
          supportingText = {
            if (!showRequiredError) {
              Text("Choose a name your party will recognize.", color = colors.secondaryText)
            }
          },
          colors =
              androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                  focusedContainerColor = colors.raised,
                  unfocusedContainerColor = colors.raised,
                  disabledContainerColor = colors.raised,
                  focusedBorderColor = colors.accent,
                  unfocusedBorderColor = colors.outline,
                  focusedTextColor = colors.text,
                  unfocusedTextColor = colors.text,
                  cursorColor = colors.accent,
              ),
          modifier = Modifier.fillMaxWidth().testTag(GameManagementTestTags.GROUP_NAME_FIELD),
      )
      if (uiState.isCreatingGroup) {
        Text("Creating your group. Please wait...", color = colors.secondaryText, fontSize = 11.sp)
      }
      if (isFailure) {
        Text(
            "Couldn't create your group.",
            color = MaterialTheme.colorScheme.error,
            fontSize = 12.sp,
        )
        Text(
            "Check your connection and try again. Your group name is still here.",
            color = colors.secondaryText,
            fontSize = 11.sp,
        )
      }
      Button(
          onClick = onCreate,
          enabled = !nameIsEmpty && !uiState.isCreatingGroup,
          modifier =
              Modifier.fillMaxWidth()
                  .height(44.dp)
                  .testTag(GameManagementTestTags.CREATE_GROUP_CONFIRM),
          shape = RoundedCornerShape(10.dp),
          colors =
              ButtonDefaults.buttonColors(
                  containerColor = colors.accent,
                  contentColor = colors.accentContent,
              ),
      ) {
        if (uiState.isCreatingGroup) {
          CircularProgressIndicator(
              modifier = Modifier.size(16.dp),
              color = colors.accentContent,
              strokeWidth = 2.dp,
          )
          Spacer(Modifier.width(8.dp))
          Text("Creating...")
        } else {
          if (isFailure) {
            Icon(Icons.Filled.Settings, null, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(6.dp))
            Text("Retry creation")
          } else {
            Icon(Icons.Filled.Add, null, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(6.dp))
            Text("Create group")
          }
        }
      }
      TextButton(
          onClick = onDismiss,
          enabled = !uiState.isCreatingGroup,
          modifier = Modifier.align(Alignment.CenterHorizontally),
      ) {
        Text("Cancel", color = colors.secondaryText, fontSize = 11.sp)
      }
    }
  }
  if (renderAsWindow) {
    Dialog(onDismissRequest = { if (!uiState.isCreatingGroup) onDismiss() }) { dialogContent() }
  } else {
    dialogContent()
  }
}

/** Displays the screen's bottom navigation tabs. */
@Composable
private fun GameManagementBottomBar(
    colors: GameManagementColors,
    onTabSelected: (String) -> Unit,
) {
  Surface(
      modifier = Modifier.fillMaxWidth().height(70.dp),
      color = colors.card,
      border = androidx.compose.foundation.BorderStroke(0.5.dp, colors.outline),
  ) {
    Row(
        modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically,
    ) {
      BottomTab("Home", Icons.Filled.Home, selected = false, colors, onTabSelected)
      BottomTab(
          "Campaigns",
          Icons.AutoMirrored.Outlined.MenuBook,
          selected = true,
          colors,
          onTabSelected,
      )
      BottomTab("Files", Icons.Outlined.Folder, selected = false, colors, onTabSelected)
      BottomTab("Map", Icons.Outlined.Map, selected = false, colors, onTabSelected)
    }
  }
}

/** Renders one bottom-navigation item and reports its label when selected. */
@Composable
private fun BottomTab(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    colors: GameManagementColors,
    onTabSelected: (String) -> Unit,
) {
  val tint = if (selected) colors.accent else colors.secondaryText
  TextButton(
      onClick = { onTabSelected(label) },
      contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
  ) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(19.dp))
      Text(
          label,
          color = tint,
          fontSize = 10.sp,
          fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
      )
    }
  }
}

/** Previews the screen with sample GM and player groups in dark theme. */
@Preview(showBackground = true, name = "Game management · dark")
@Composable
private fun GameManagementScreenPreview() {
  SampleAppTheme(darkTheme = true) {
    GameManagementScreenContent(
        uiState =
            GameManagementUiState(
                groups =
                    listOf(
                        UserGroup(
                            group =
                                CampaignGroup(
                                    id = "tides",
                                    name = "Tides of the Black Depths",
                                    description = "Pathfinder 2e",
                                    gameMasterId = "current-user",
                                    participantIds =
                                        listOf(
                                            "current-user",
                                            "player-1",
                                            "player-2",
                                            "player-3",
                                            "player-4",
                                            "player-5",
                                        ),
                                ),
                            role = GroupRole.GAME_MASTER,
                        ),
                        UserGroup(
                            group =
                                CampaignGroup(
                                    id = "clockwork",
                                    name = "Clockwork Dominion",
                                    description = "D&D 5e · GM Aria",
                                    gameMasterId = "another-user",
                                    participantIds =
                                        listOf(
                                            "current-user",
                                            "player-2",
                                            "player-3",
                                            "player-4",
                                            "player-5",
                                        ),
                                ),
                            role = GroupRole.PLAYER,
                        ),
                    ),
                loadState = GroupLoadState.Success,
            ),
        onCreateGroupClick = {},
        onGroupNameChanged = {},
        onCreateGroup = {},
        onDismissCreateDialog = {},
        onRetryLoad = {},
        onDismissSuccess = {},
        onEnterWorkspace = {},
        onManageGroup = {},
        onInvite = {},
        onTabSelected = {},
        darkTheme = true,
    )
  }
}
