// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.ui.gameManagement

import android.content.ClipData
import android.content.Intent
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.github.se.jdrnexus.R
import com.github.se.jdrnexus.model.personalSpace.InvitationCodes
import com.github.se.jdrnexus.model.personalSpace.InvitationError
import com.github.se.jdrnexus.resources.C
import com.github.se.jdrnexus.viewmodel.InvitationState
import com.github.se.jdrnexus.viewmodel.JoinState
import kotlinx.coroutines.launch

/** The text shown to the user for each invitation failure. */
@StringRes
internal fun invitationErrorMessage(error: InvitationError): Int =
    when (error) {
      InvitationError.NOT_SIGNED_IN -> R.string.invitation_error_not_signed_in
      InvitationError.GROUP_NOT_FOUND -> R.string.invitation_error_group_not_found
      InvitationError.NOT_GAME_MASTER -> R.string.invitation_error_not_game_master
      InvitationError.INVALID_CODE -> R.string.invitation_error_invalid_code
      InvitationError.UNKNOWN_CODE -> R.string.invitation_error_unknown_code
      InvitationError.ALREADY_MEMBER -> R.string.invitation_error_already_member
      InvitationError.NETWORK -> R.string.invitation_error_network
    }

/**
 * The text shown in the join dialog for each failure. A NETWORK error there can come after the
 * membership was written (`GameManagementViewModel.joinGroup` reads the group back afterwards), so
 * its text asks the player to check their groups list before retrying.
 */
@StringRes
internal fun joinErrorMessage(error: InvitationError): Int =
    if (error == InvitationError.NETWORK) R.string.join_error_network
    else invitationErrorMessage(error)

/**
 * Uppercases [input] and keeps only characters a code can contain, up to the length of a generated
 * code. Characters like `0`, `O`, `1`, `I` or `L` are never in a code, so they are dropped as the
 * user types.
 */
internal fun cleanInvitationCodeInput(input: String): String =
    input.uppercase().filter { it in InvitationCodes.ALPHABET }.take(InvitationCodes.LENGTH)

/**
 * Shows a group's invitation code with copy, share and regenerate actions.
 *
 * Regenerating asks for confirmation inside the dialog first, because the old code stops working.
 * The dialog can always be closed, even while a code loads.
 *
 * @param groupName name of the group, shown in the subtitle.
 * @param code the group's code, or `null` while none is loaded.
 * @param state whether the code is loading or the last request failed.
 * @param renderAsWindow whether to show the dialog in its own window; tests render it inline.
 * @param onRetry called by "Try again" when there is no code and none is loading.
 * @param onRegenerate called once the GameMaster confirms regenerating the code.
 * @param onDismiss called when the dialog is closed.
 */
@Composable
internal fun InviteDialog(
    groupName: String,
    code: String?,
    state: InvitationState,
    renderAsWindow: Boolean,
    onRetry: () -> Unit,
    onRegenerate: () -> Unit,
    onDismiss: () -> Unit,
) {
  val colors = gameManagementColors()
  val isLoading = state == InvitationState.Loading
  val error = (state as? InvitationState.Error)?.error
  val canUseCode = code != null && !isLoading
  var confirmingRegenerate by rememberSaveable { mutableStateOf(false) }
  val clipboard = LocalClipboard.current
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val clipLabel = stringResource(R.string.invite_code_label)
  val shareText = stringResource(R.string.invite_share_text, code.orEmpty())

  GameManagementDialogFrame(
      title = stringResource(R.string.invite_dialog_title),
      tag = C.Tag.invite_dialog,
      closeTag = C.Tag.invite_close,
      renderAsWindow = renderAsWindow,
      onDismiss = onDismiss,
  ) {
    Text(
        stringResource(R.string.invite_dialog_subtitle, groupName),
        color = colors.secondaryText,
        fontSize = 13.sp,
    )
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(colors.raised)
                .border(1.dp, colors.outline, RoundedCornerShape(12.dp))
                .padding(vertical = 16.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Text(clipLabel, color = colors.secondaryText, fontSize = 11.sp, letterSpacing = 1.5.sp)
      when {
        code != null ->
            Text(
                code,
                color = colors.text,
                fontSize = 34.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 7.sp,
                modifier = Modifier.testTag(C.Tag.invite_code),
            )
        isLoading ->
            CircularProgressIndicator(
                color = colors.accent,
                strokeWidth = 2.dp,
                modifier = Modifier.size(34.dp).testTag(C.Tag.invite_loading),
            )
        else ->
            TextButton(
                onClick = onRetry,
                colors = ButtonDefaults.textButtonColors(contentColor = colors.accent),
                modifier = Modifier.testTag(C.Tag.invite_retry),
            ) {
              Text(stringResource(R.string.invite_retry))
            }
      }
    }
    if (error != null) {
      ErrorLine(stringResource(invitationErrorMessage(error)), C.Tag.invite_error)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      OutlinedButton(
          onClick = {
            scope.launch {
              clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(clipLabel, code)))
            }
          },
          enabled = canUseCode,
          shape = RoundedCornerShape(8.dp),
          border = BorderStroke(1.dp, colors.outline),
          colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.accent),
          modifier = Modifier.weight(1f).height(46.dp).testTag(C.Tag.invite_copy),
      ) {
        IconLabel(Icons.Filled.ContentCopy, stringResource(R.string.invite_copy))
      }
      Button(
          onClick = {
            val send =
                Intent(Intent.ACTION_SEND)
                    .setType("text/plain")
                    .putExtra(Intent.EXTRA_TEXT, shareText)
            context.startActivity(Intent.createChooser(send, null))
          },
          enabled = canUseCode,
          shape = RoundedCornerShape(8.dp),
          colors = accentButtonColors(colors),
          modifier = Modifier.weight(1f).height(46.dp).testTag(C.Tag.invite_share),
      ) {
        IconLabel(Icons.Filled.Share, stringResource(R.string.invite_share))
      }
    }
    if (confirmingRegenerate) {
      RegenerateConfirmation(
          onCancel = { confirmingRegenerate = false },
          onConfirm = {
            confirmingRegenerate = false
            onRegenerate()
          },
      )
    } else {
      TextButton(
          onClick = { confirmingRegenerate = true },
          enabled = canUseCode,
          colors = ButtonDefaults.textButtonColors(contentColor = colors.secondaryText),
          modifier = Modifier.align(Alignment.CenterHorizontally).testTag(C.Tag.invite_regenerate),
      ) {
        IconLabel(Icons.Filled.Refresh, stringResource(R.string.invite_regenerate))
      }
    }
  }
}

/**
 * Lets a user type an invitation code and join its group.
 *
 * The field only accepts characters a code can contain. The field and the Join button are disabled
 * while a join runs, so a code can't be sent twice. The dialog can always be closed: the join runs
 * in the ViewModel, so closing doesn't cancel it. The typed code stays after an error, and editing
 * it clears the error.
 *
 * @param state the current join request: idle, running, failed or done.
 * @param renderAsWindow whether to show the dialog in its own window; tests render it inline.
 * @param onClearError called when the user edits the code while an error is shown.
 * @param onJoin called with the typed code when the user submits it.
 * @param onDismiss called when the dialog is closed.
 */
@Composable
internal fun JoinGroupDialog(
    state: JoinState,
    renderAsWindow: Boolean,
    onClearError: () -> Unit,
    onJoin: (String) -> Unit,
    onDismiss: () -> Unit,
) {
  val colors = gameManagementColors()
  var code by rememberSaveable { mutableStateOf("") }
  val isJoining = state == JoinState.Joining
  val error = (state as? JoinState.Error)?.error

  GameManagementDialogFrame(
      title = stringResource(R.string.join_dialog_title),
      tag = C.Tag.join_dialog,
      closeTag = C.Tag.join_close,
      renderAsWindow = renderAsWindow,
      onDismiss = onDismiss,
  ) {
    Text(
        stringResource(R.string.join_dialog_subtitle),
        color = colors.secondaryText,
        fontSize = 13.sp,
    )
    OutlinedTextField(
        value = code,
        onValueChange = { input ->
          val cleaned = cleanInvitationCodeInput(input)
          if (cleaned != code) {
            code = cleaned
            if (error != null) onClearError()
          }
        },
        enabled = !isJoining,
        label = { Text(stringResource(R.string.join_code_label)) },
        singleLine = true,
        isError = error != null,
        shape = RoundedCornerShape(10.dp),
        textStyle =
            TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 5.sp),
        keyboardOptions =
            KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters,
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Ascii,
            ),
        colors = gameManagementFieldColors(colors),
        modifier = Modifier.fillMaxWidth().testTag(C.Tag.join_code_field),
    )
    if (error != null) {
      ErrorLine(stringResource(joinErrorMessage(error)), C.Tag.join_error)
    }
    Button(
        onClick = { onJoin(code) },
        enabled = code.isNotEmpty() && !isJoining,
        shape = RoundedCornerShape(10.dp),
        colors = accentButtonColors(colors),
        modifier = Modifier.fillMaxWidth().height(46.dp).testTag(C.Tag.join_submit),
    ) {
      if (isJoining) {
        CircularProgressIndicator(
            color = colors.accentContent,
            strokeWidth = 2.dp,
            modifier = Modifier.size(16.dp).testTag(C.Tag.join_loading),
        )
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.join_in_progress))
      } else {
        IconLabel(Icons.AutoMirrored.Filled.Login, stringResource(R.string.join_submit))
      }
    }
    TextButton(
        onClick = onDismiss,
        colors = ButtonDefaults.textButtonColors(contentColor = colors.secondaryText),
        modifier = Modifier.align(Alignment.CenterHorizontally).testTag(C.Tag.join_cancel),
    ) {
      Text(stringResource(R.string.invitation_cancel))
    }
  }
}

/**
 * The card, serif title and close button shared by the invite and join dialogs. The close button
 * and, in a window, back or a tap outside always close the dialog.
 *
 * @param tag test tag of the card.
 * @param closeTag test tag of the close button.
 * @param renderAsWindow whether to show the dialog in its own window; tests render it inline.
 * @param content the dialog's body, placed under the title.
 */
@Composable
private fun GameManagementDialogFrame(
    title: String,
    tag: String,
    closeTag: String,
    renderAsWindow: Boolean,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
  val colors = gameManagementColors()
  val card: @Composable () -> Unit = {
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(colors.card)
                .border(1.dp, colors.outline, RoundedCornerShape(18.dp))
                .padding(18.dp)
                .testTag(tag),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = title,
            color = colors.text,
            fontFamily = FontFamily.Serif,
            fontSize = 23.sp,
            letterSpacing = 1.sp,
            modifier = Modifier.weight(1f),
        )
        IconButton(
            onClick = onDismiss,
            colors = IconButtonDefaults.iconButtonColors(contentColor = colors.secondaryText),
            modifier = Modifier.testTag(closeTag),
        ) {
          Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.dialog_close))
        }
      }
      content()
    }
  }
  if (renderAsWindow) {
    Dialog(onDismissRequest = onDismiss) { card() }
  } else {
    card()
  }
}

/**
 * Text field colours of the join dialog's code field, so typed text stays readable in both themes.
 */
@Composable
private fun gameManagementFieldColors(colors: GameManagementColors): TextFieldColors =
    OutlinedTextFieldDefaults.colors(
        focusedContainerColor = colors.raised,
        unfocusedContainerColor = colors.raised,
        disabledContainerColor = colors.raised,
        focusedBorderColor = colors.accent,
        unfocusedBorderColor = colors.outline,
        focusedTextColor = colors.text,
        unfocusedTextColor = colors.text,
        cursorColor = colors.accent,
    )

/** Colours of the invitation dialogs' filled accent buttons. */
@Composable
private fun accentButtonColors(colors: GameManagementColors): ButtonColors =
    ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.accentContent)

/** The inline box that asks the GameMaster to confirm replacing the current code. */
@Composable
private fun RegenerateConfirmation(onCancel: () -> Unit, onConfirm: () -> Unit) {
  val colors = gameManagementColors()
  Column(
      modifier =
          Modifier.fillMaxWidth()
              .clip(RoundedCornerShape(12.dp))
              .border(1.dp, colors.accent, RoundedCornerShape(12.dp))
              .padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      Icon(Icons.Filled.Warning, null, tint = colors.accent, modifier = Modifier.size(20.dp))
      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            stringResource(R.string.invite_regenerate_title),
            color = colors.text,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            stringResource(R.string.invite_regenerate_message),
            color = colors.secondaryText,
            fontSize = 12.sp,
        )
      }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      OutlinedButton(
          onClick = onCancel,
          shape = RoundedCornerShape(8.dp),
          border = BorderStroke(1.dp, colors.outline),
          colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.text),
          modifier = Modifier.weight(1f).height(44.dp).testTag(C.Tag.invite_regenerate_cancel),
      ) {
        Text(stringResource(R.string.invitation_cancel))
      }
      Button(
          onClick = onConfirm,
          shape = RoundedCornerShape(8.dp),
          colors = accentButtonColors(colors),
          modifier = Modifier.weight(1f).height(44.dp).testTag(C.Tag.invite_regenerate_confirm),
      ) {
        Text(stringResource(R.string.invite_regenerate_confirm))
      }
    }
  }
}

/** An error icon and message, in the theme's error colour. */
@Composable
private fun ErrorLine(message: String, tag: String) {
  Row(
      horizontalArrangement = Arrangement.spacedBy(6.dp),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(
        Icons.Filled.ErrorOutline,
        null,
        tint = MaterialTheme.colorScheme.error,
        modifier = Modifier.size(16.dp),
    )
    Text(
        message,
        color = MaterialTheme.colorScheme.error,
        fontSize = 12.sp,
        modifier = Modifier.testTag(tag),
    )
  }
}

/** An icon followed by a label, inside a button. */
@Composable
private fun IconLabel(icon: ImageVector, label: String) {
  Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
  Spacer(Modifier.width(8.dp))
  Text(label)
}
