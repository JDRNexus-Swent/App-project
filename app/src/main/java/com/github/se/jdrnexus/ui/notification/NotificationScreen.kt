// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.ui.notification

import android.text.format.DateUtils
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.github.se.jdrnexus.R
import com.github.se.jdrnexus.model.notification.Notification
import com.github.se.jdrnexus.model.notification.NotificationType
import com.github.se.jdrnexus.resources.AppTopBar
import com.github.se.jdrnexus.resources.C
import com.github.se.jdrnexus.resources.ScreenBottomBar
import com.github.se.jdrnexus.ui.theme.LocalAppUiColors
import com.github.se.jdrnexus.ui.theme.SampleAppTheme
import com.github.se.jdrnexus.viewmodel.NotificationLoadState
import com.github.se.jdrnexus.viewmodel.NotificationUiState
import com.github.se.jdrnexus.viewmodel.NotificationViewModelInterface
import java.util.Date
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Bottom-bar tab highlighted on this screen: notifications are about campaigns. */
private const val SELECTED_TAB = "Campaigns"

/** Horizontal inset of a notification row's content. */
private val ROW_HORIZONTAL_PADDING = 24.dp

/** Vertical inset of a notification row's content. */
private val ROW_VERTICAL_PADDING = 16.dp

/** Space between the type icon and the text column of a row. */
private val ROW_CONTENT_SPACING = 16.dp

/** Space between the title, the message and the "Mark as read" action of a row. */
private val ROW_TEXT_SPACING = 4.dp

/** Space between a row's title, its time and its unread dot. */
private val ROW_HEADER_SPACING = 8.dp

/** Diameter of the circle behind a notification's type icon. */
private val TYPE_ICON_CONTAINER_SIZE = 40.dp

/** Size of a notification's type icon inside its circle. */
private val TYPE_ICON_SIZE = 20.dp

/** Diameter of the dot marking an unread notification. */
private val UNREAD_DOT_SIZE = 6.dp

/** Lines of the message shown before it is cut with an ellipsis. */
private const val MESSAGE_MAX_LINES = 3

/** Thickness of the line between two notification rows. */
private val DIVIDER_THICKNESS = 1.dp

/**
 * Opacity of the line between rows. The full outline color is too strong for a separator that
 * repeats on every row.
 */
private const val DIVIDER_ALPHA = 0.6f

/** Space between the top bar and the first row, showing the background as in the design. */
private val LIST_TOP_PADDING = 8.dp

/** Inset around the centered full-screen states (loading, empty, error, signed out). */
private val STATE_PADDING = 32.dp

/** Space between the icon, title, body and action of a full-screen state. */
private val STATE_SPACING = 8.dp

/** Extra space between a full-screen state's text and its action button. */
private val STATE_ACTION_TOP_PADDING = 8.dp

/** Size of the icon above a full-screen state's message. */
private val STATE_ICON_SIZE = 48.dp

/** Minimum width of the error state's retry button, so it reads as a button and not a link. */
private val RETRY_BUTTON_MIN_WIDTH = 160.dp

/** Size of the icon inside the retry button. */
private val RETRY_ICON_SIZE = 18.dp

/** Space between the retry icon and its label. */
private val RETRY_ICON_SPACING = 8.dp

/** Border width of the retry button. */
private val RETRY_BORDER_WIDTH = 1.dp

/** Margin around the snackbar. */
private val SNACKBAR_PADDING = 12.dp

/**
 * Lists the signed-in user's notifications, following the "Draft – Notifications" designs.
 *
 * Unread notifications are drawn on a raised background with a dark title, a dot and a "Mark as
 * read" action; read ones blend into the background with muted text. Selecting a notification marks
 * it as read and calls [onOpenNotification]. The top bar's "mark all as read" action asks for
 * confirmation in a snackbar before calling the ViewModel.
 *
 * @param viewModel Source of the notifications and target of the user's actions.
 * @param onBack Called when the user leaves the screen with the top bar's back action.
 * @param onOpenNotification Called with the notification the user selected, so the caller can
 *   navigate to its related content.
 * @param onTabSelected Called with the label of the bottom-bar tab the user pressed.
 */
@Composable
fun NotificationScreen(
    viewModel: NotificationViewModelInterface,
    onBack: () -> Unit,
    onOpenNotification: (Notification) -> Unit,
    onTabSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
  val uiState by viewModel.notificationUiState.collectAsState()
  val colors = LocalAppUiColors.current
  val snackbarHostState = remember { SnackbarHostState() }
  val scope = rememberCoroutineScope()
  var confirmingMarkAll by remember { mutableStateOf(false) }
  val hasUnread = uiState.notifications.any { !it.isRead }

  val confirmMessage = stringResource(R.string.notifications_mark_all_confirm)
  val confirmAction = stringResource(R.string.notifications_mark_all_confirm_action)
  val actionErrorMessage = stringResource(R.string.notifications_action_error)

  // The ViewModel's error is a raw exception message: show a readable one instead.
  LaunchedEffect(uiState.actionError) {
    if (uiState.actionError != null) {
      snackbarHostState.showSnackbar(actionErrorMessage, withDismissAction = true)
      viewModel.clearActionError()
    }
  }

  val requestMarkAll: () -> Unit = {
    confirmingMarkAll = true
    scope.launch {
      try {
        val result =
            snackbarHostState.showSnackbar(
                message = confirmMessage,
                actionLabel = confirmAction,
                withDismissAction = true,
                duration = SnackbarDuration.Long,
            )
        if (result == SnackbarResult.ActionPerformed) viewModel.markAllAsRead()
      } finally {
        confirmingMarkAll = false
      }
    }
  }

  Scaffold(
      modifier = modifier.fillMaxSize(),
      containerColor = colors.background,
      topBar = {
        AppTopBar(
            title = stringResource(R.string.notifications_title),
            onBack = onBack,
            trailingContent = {
              IconButton(
                  onClick = requestMarkAll,
                  enabled = hasUnread && !confirmingMarkAll,
                  modifier = Modifier.testTag(C.Tag.notification_mark_all),
              ) {
                Icon(
                    imageVector = Icons.Filled.DoneAll,
                    contentDescription = stringResource(R.string.notifications_mark_all),
                )
              }
            },
        )
      },
      bottomBar = { ScreenBottomBar(selectedTab = SELECTED_TAB, onTabSelected = onTabSelected) },
      snackbarHost = { SnackbarHost(snackbarHostState) { data -> NotificationSnackbar(data) } },
  ) { contentPadding ->
    val contentModifier = Modifier.fillMaxSize().padding(contentPadding)
    when (uiState.loadState) {
      NotificationLoadState.Loading ->
          NotificationState(
              title = null,
              body = stringResource(R.string.notifications_loading),
              modifier = contentModifier,
              header = {
                CircularProgressIndicator(
                    modifier = Modifier.testTag(C.Tag.notification_loading),
                    color = colors.accent,
                )
              },
          )
      NotificationLoadState.SignedOut ->
          NotificationState(
              title = null,
              body = stringResource(R.string.notifications_signed_out),
              modifier = contentModifier.testTag(C.Tag.notification_signed_out),
          )
      NotificationLoadState.Empty ->
          NotificationState(
              title = stringResource(R.string.notifications_empty_title),
              body = stringResource(R.string.notifications_empty_body),
              modifier = contentModifier.testTag(C.Tag.notification_empty),
              header = { StateIcon(Icons.Outlined.Notifications) },
              // The design uses the serif face for this title only, as a friendly accent.
              titleFontFamily = FontFamily.Serif,
          )
      is NotificationLoadState.Error ->
          NotificationState(
              title = stringResource(R.string.notifications_load_error),
              body = stringResource(R.string.notifications_load_error_body),
              modifier = contentModifier.testTag(C.Tag.notification_error),
              header = { StateIcon(Icons.Outlined.ErrorOutline) },
              action = { RetryButton(onClick = viewModel::retry) },
          )
      NotificationLoadState.Success ->
          LazyColumn(
              modifier = contentModifier.testTag(C.Tag.notification_list),
              contentPadding = PaddingValues(top = LIST_TOP_PADDING),
          ) {
            items(uiState.notifications, key = { it.id }) { notification ->
              NotificationRow(
                  notification = notification,
                  onClick = {
                    if (!notification.isRead) viewModel.markAsRead(notification.id)
                    onOpenNotification(notification)
                  },
                  onMarkAsRead = { viewModel.markAsRead(notification.id) },
              )
              HorizontalDivider(
                  thickness = DIVIDER_THICKNESS,
                  color = colors.outline.copy(alpha = DIVIDER_ALPHA),
              )
            }
          }
    }
  }
}

/** One notification row, styled according to whether it has been read. */
@Composable
private fun NotificationRow(
    notification: Notification,
    onClick: () -> Unit,
    onMarkAsRead: () -> Unit,
) {
  val colors = LocalAppUiColors.current
  val unread = !notification.isRead
  val readState =
      stringResource(
          if (unread) R.string.notifications_state_unread else R.string.notifications_state_read
      )
  Row(
      modifier =
          Modifier.fillMaxWidth()
              // Unread rows are raised on `surface`; read rows blend into the background.
              .background(if (unread) colors.surface else colors.background)
              .clickable(onClick = onClick)
              .semantics { stateDescription = readState }
              .testTag(C.Tag.notificationItem(notification.id))
              .padding(horizontal = ROW_HORIZONTAL_PADDING, vertical = ROW_VERTICAL_PADDING),
      horizontalArrangement = Arrangement.spacedBy(ROW_CONTENT_SPACING),
  ) {
    Box(
        modifier =
            Modifier.size(TYPE_ICON_CONTAINER_SIZE)
                .clip(CircleShape)
                .background(if (unread) colors.iconBackground else colors.surface),
        contentAlignment = Alignment.Center,
    ) {
      Icon(
          imageVector = notification.type.icon(),
          contentDescription = null,
          modifier = Modifier.size(TYPE_ICON_SIZE),
          tint = if (unread) colors.accent else colors.secondaryText,
      )
    }
    Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(ROW_TEXT_SPACING),
    ) {
      Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(ROW_HEADER_SPACING),
      ) {
        Text(
            text = notification.title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = if (unread) FontWeight.Bold else FontWeight.Normal,
            color = if (unread) colors.primaryText else colors.secondaryText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = relativeTime(notification.createdAt),
            style = MaterialTheme.typography.labelSmall,
            color = colors.secondaryText,
        )
        if (unread) {
          Box(
              modifier =
                  Modifier.size(UNREAD_DOT_SIZE)
                      .clip(CircleShape)
                      .background(colors.accent)
                      .testTag(C.Tag.notificationUnreadDot(notification.id))
          )
        }
      }
      Text(
          text = notification.message,
          style = MaterialTheme.typography.bodyMedium,
          color = if (unread) colors.primaryText else colors.secondaryText,
          maxLines = MESSAGE_MAX_LINES,
          overflow = TextOverflow.Ellipsis,
      )
      if (unread) {
        Text(
            text = stringResource(R.string.notifications_mark_as_read),
            modifier =
                Modifier.clickable(onClick = onMarkAsRead)
                    .testTag(C.Tag.notificationMarkAsRead(notification.id))
                    .padding(top = ROW_TEXT_SPACING),
            style = MaterialTheme.typography.labelMedium,
            color = colors.accent,
        )
      }
    }
  }
}

/**
 * Centered full-screen state: an optional [header] (icon or spinner), an optional [title], a [body]
 * and an optional [action].
 */
@Composable
private fun NotificationState(
    title: String?,
    body: String,
    modifier: Modifier = Modifier,
    header: @Composable (() -> Unit)? = null,
    action: @Composable (() -> Unit)? = null,
    titleFontFamily: FontFamily? = null,
) {
  val colors = LocalAppUiColors.current
  Box(modifier = modifier.padding(STATE_PADDING), contentAlignment = Alignment.Center) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(STATE_SPACING),
    ) {
      header?.invoke()
      if (title != null) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontFamily = titleFontFamily,
            color = colors.primaryText,
            textAlign = TextAlign.Center,
        )
      }
      Text(
          text = body,
          style = MaterialTheme.typography.bodyMedium,
          color = colors.secondaryText,
          textAlign = TextAlign.Center,
      )
      if (action != null) {
        Spacer(Modifier.height(STATE_ACTION_TOP_PADDING))
        action()
      }
    }
  }
}

/** Large muted icon at the top of a full-screen state. */
@Composable
private fun StateIcon(icon: ImageVector) {
  Icon(
      imageVector = icon,
      contentDescription = null,
      modifier = Modifier.size(STATE_ICON_SIZE),
      tint = LocalAppUiColors.current.secondaryText,
  )
}

/** Outlined "Retry" button of the error state. */
@Composable
private fun RetryButton(onClick: () -> Unit) {
  val colors = LocalAppUiColors.current
  OutlinedButton(
      onClick = onClick,
      modifier = Modifier.widthIn(min = RETRY_BUTTON_MIN_WIDTH).testTag(C.Tag.notification_retry),
      border = BorderStroke(RETRY_BORDER_WIDTH, colors.accent),
  ) {
    Icon(
        imageVector = Icons.Filled.Refresh,
        contentDescription = null,
        modifier = Modifier.size(RETRY_ICON_SIZE),
        tint = colors.accent,
    )
    Spacer(Modifier.size(RETRY_ICON_SPACING))
    Text(stringResource(R.string.notifications_retry), color = colors.accent)
  }
}

/** Snackbar in the app's accent colors, with tagged buttons so tests can confirm or dismiss it. */
@Composable
private fun NotificationSnackbar(data: SnackbarData) {
  val colors = LocalAppUiColors.current
  Snackbar(
      modifier = Modifier.padding(SNACKBAR_PADDING).testTag(C.Tag.notification_snackbar),
      action =
          data.visuals.actionLabel?.let { label ->
            {
              TextButton(
                  onClick = data::performAction,
                  modifier = Modifier.testTag(C.Tag.notification_snackbar_action),
              ) {
                Text(label, color = colors.accentContent, fontWeight = FontWeight.Bold)
              }
            }
          },
      dismissAction =
          if (data.visuals.withDismissAction) {
            {
              IconButton(
                  onClick = data::dismiss,
                  modifier = Modifier.testTag(C.Tag.notification_snackbar_dismiss),
              ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.notifications_dismiss),
                    tint = colors.accentContent,
                )
              }
            }
          } else {
            null
          },
      containerColor = colors.accent,
      contentColor = colors.accentContent,
  ) {
    Text(data.visuals.message)
  }
}

/** Icon representing each kind of notification, as in the design. */
private fun NotificationType.icon(): ImageVector =
    when (this) {
      NotificationType.GROUP_INVITATION -> Icons.Filled.GroupAdd
      NotificationType.MEMBERSHIP_CHANGE -> Icons.Filled.PersonAdd
      NotificationType.CAMPAIGN_UPDATE -> Icons.AutoMirrored.Filled.MenuBook
    }

/**
 * Short relative time of a notification ("2 min. ago", "1 hr. ago", "Yesterday"). A missing date
 * means the server has not confirmed the creation time yet, so the notification was just created.
 */
@Composable
private fun relativeTime(createdAt: Date?): String =
    createdAt?.let {
      DateUtils.getRelativeTimeSpanString(
              it.time,
              System.currentTimeMillis(),
              DateUtils.MINUTE_IN_MILLIS,
              DateUtils.FORMAT_ABBREV_RELATIVE,
          )
          .toString()
    } ?: stringResource(R.string.notifications_just_now)

// ============ Previews ============

/**
 * In-memory ViewModel for the previews: marking as read updates the list, so they are interactive.
 */
private class PreviewNotificationViewModel(state: NotificationUiState) :
    NotificationViewModelInterface {
  override val notificationUiState = MutableStateFlow(state)

  override fun markAsRead(notificationId: String) = update { notification ->
    if (notification.id == notificationId) notification.copy(isRead = true) else notification
  }

  override fun markAllAsRead() = update { it.copy(isRead = true) }

  override fun deleteNotification(notificationId: String) {
    notificationUiState.update { state ->
      state.copy(notifications = state.notifications.filterNot { it.id == notificationId })
    }
  }

  override fun clearActionError() {
    notificationUiState.update { it.copy(actionError = null) }
  }

  override fun retry() {
    notificationUiState.update { it.copy(loadState = NotificationLoadState.Success) }
  }

  private fun update(transform: (Notification) -> Notification) {
    notificationUiState.update { state ->
      state.copy(notifications = state.notifications.map(transform))
    }
  }
}

/** The notifications of the design: three unread and two read, covering every type. */
private fun previewNotifications(): List<Notification> {
  val now = System.currentTimeMillis()
  fun minutesAgo(minutes: Long) = Date(now - TimeUnit.MINUTES.toMillis(minutes))
  return listOf(
      Notification(
          id = "invitation",
          type = NotificationType.GROUP_INVITATION,
          title = "Group invitation",
          message = "Kael invited you to join Tides of the Black Depths.",
          createdAt = minutesAgo(minutes = 2),
      ),
      Notification(
          id = "member",
          type = NotificationType.MEMBERSHIP_CHANGE,
          title = "New member",
          message = "Arin joined your group Clockwork Dominion.",
          groupId = "clockwork",
          createdAt = minutesAgo(minutes = TimeUnit.HOURS.toMinutes(1)),
      ),
      Notification(
          id = "update",
          type = NotificationType.CAMPAIGN_UPDATE,
          title = "Campaign update",
          message = "Chapter IV of Tides of the Black Depths has new lore.",
          groupId = "tides",
          createdAt = minutesAgo(minutes = TimeUnit.HOURS.toMinutes(3)),
      ),
      Notification(
          id = "old-member",
          type = NotificationType.MEMBERSHIP_CHANGE,
          title = "New member",
          message = "Lio joined your group Tides of the Black Depths.",
          groupId = "tides",
          createdAt = minutesAgo(minutes = TimeUnit.DAYS.toMinutes(1)),
          isRead = true,
      ),
      Notification(
          id = "old-update",
          type = NotificationType.CAMPAIGN_UPDATE,
          title = "Campaign update",
          message = "Session notes for Clockwork Dominion were shared.",
          groupId = "clockwork",
          createdAt = minutesAgo(minutes = TimeUnit.DAYS.toMinutes(3)),
          isRead = true,
      ),
  )
}

@Composable
private fun NotificationScreenPreview(state: NotificationUiState, darkTheme: Boolean) {
  SampleAppTheme(darkTheme = darkTheme) {
    NotificationScreen(
        viewModel = remember { PreviewNotificationViewModel(state) },
        onBack = {},
        onOpenNotification = {},
        onTabSelected = {},
    )
  }
}

private val successState
  get() =
      NotificationUiState(
          notifications = previewNotifications(),
          loadState = NotificationLoadState.Success,
      )

@Preview(name = "Notifications · Light", widthDp = 412, heightDp = 917)
@Composable
private fun NotificationScreenLightPreview() =
    NotificationScreenPreview(successState, darkTheme = false)

@Preview(name = "Notifications · Dark", widthDp = 412, heightDp = 917)
@Composable
private fun NotificationScreenDarkPreview() =
    NotificationScreenPreview(successState, darkTheme = true)

@Preview(name = "Notifications · Loading", widthDp = 412, heightDp = 917)
@Composable
private fun NotificationScreenLoadingPreview() =
    NotificationScreenPreview(NotificationUiState(), darkTheme = false)

@Preview(name = "Notifications · Empty", widthDp = 412, heightDp = 917)
@Composable
private fun NotificationScreenEmptyPreview() =
    NotificationScreenPreview(
        NotificationUiState(loadState = NotificationLoadState.Empty),
        darkTheme = false,
    )

@Preview(name = "Notifications · Error", widthDp = 412, heightDp = 917)
@Composable
private fun NotificationScreenErrorPreview() =
    NotificationScreenPreview(
        NotificationUiState(loadState = NotificationLoadState.Error("offline")),
        darkTheme = false,
    )
