// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.github.se.jdrnexus.ui.edition

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/**
 * Provides the shared loading, empty/error, and content states for an edit screen.
 *
 * Loading takes precedence over the other states. When content is unavailable, the scaffold shows
 * the header and either [error] or [emptyMessage]; otherwise it invokes [content].
 *
 * @param isLoading whether the file is currently loading.
 * @param loadingMessage text shown while loading.
 * @param hasContent whether the editor has content to display.
 * @param screenTitle title shown in the header when content is unavailable.
 * @param backTestTag test tag applied to the header's back button.
 * @param error optional error shown instead of [emptyMessage].
 * @param emptyMessage message shown when there is no content and no error.
 * @param onBack called when the back button is pressed.
 * @param content editor content shown when loading is complete and content is available.
 */
@Composable
internal fun EditFileScaffold(
    isLoading: Boolean,
    loadingMessage: String,
    hasContent: Boolean,
    screenTitle: String,
    backTestTag: String,
    error: String?,
    emptyMessage: String,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
  Surface(
      modifier = Modifier.fillMaxSize(),
      color = MaterialTheme.colorScheme.background,
      contentColor = MaterialTheme.colorScheme.onBackground,
  ) {
    when {
      isLoading -> {
        Column(modifier = Modifier.fillMaxSize()) {
          EditFileHeader(
              title = screenTitle,
              backTestTag = backTestTag,
              onBack = onBack,
              modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
          )
          Column(
              modifier = Modifier.fillMaxSize(),
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.Center,
          ) {
            CircularProgressIndicator()
            Text(loadingMessage, modifier = Modifier.padding(top = 12.dp))
          }
        }
      }
      !hasContent -> {
        Column(modifier = Modifier.fillMaxSize()) {
          EditFileHeader(
              title = screenTitle,
              backTestTag = backTestTag,
              onBack = onBack,
              modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
          )
          Text(
              text = error ?: emptyMessage,
              modifier = Modifier.padding(24.dp),
              color = MaterialTheme.colorScheme.error,
          )
        }
      }
      else -> content()
    }
  }
}

/**
 * Displays a screen title beside a back button.
 *
 * @param title text displayed as the header title.
 * @param backTestTag test tag applied to the back button.
 * @param onBack called when the back button is pressed.
 * @param modifier modifier applied to the header row.
 */
@Composable
internal fun EditFileHeader(
    title: String,
    backTestTag: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
  Row(
      modifier = modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    IconButton(onClick = onBack, modifier = Modifier.testTag(backTestTag)) {
      Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
    }
    Text(
        text = title,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
    )
  }
}

/**
 * Displays a section title and its item count.
 *
 * A singular label is used when [count] is one; otherwise the lowercased [title] is used.
 *
 * @param title section heading and plural count label.
 * @param count number of items in the section.
 * @param modifier modifier applied to the heading row.
 * @param singularCountLabel label used when [count] is one.
 */
@Composable
internal fun EditFileSectionHeading(
    title: String,
    count: Int,
    modifier: Modifier = Modifier,
    singularCountLabel: String = "item",
    pluralCountLabel: String = "items",
) {
  Row(
      modifier = modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(title, style = MaterialTheme.typography.titleMedium)
    Text(
        text = "${count} ${if (count == 1) singularCountLabel else pluralCountLabel}",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

/**
 * Displays an error message using the theme's error color.
 *
 * @param error text to display.
 * @param testTag test tag applied to the message.
 */
@Composable
internal fun EditFileErrorMessage(error: String, testTag: String) {
  Text(
      text = error,
      color = MaterialTheme.colorScheme.error,
      style = MaterialTheme.typography.bodySmall,
      modifier = Modifier.testTag(testTag),
  )
}

/**
 * Displays a save progress indicator or an enabled save button.
 *
 * The button label is "Saved" when [isSaved] is true and "Save" otherwise. While [isSaving] is
 * true, only the progress indicator is displayed.
 *
 * @param isSaving whether a save operation is in progress.
 * @param isSaved whether the current content has been saved.
 * @param testTag test tag applied to the save button when it is displayed.
 * @param onSave called when the save button is pressed.
 */
@Composable
internal fun EditFileSaveButton(
    isSaving: Boolean,
    isSaved: Boolean,
    testTag: String,
    onSave: () -> Unit,
) {
  if (isSaving) {
    CircularProgressIndicator(modifier = Modifier.fillMaxWidth())
  } else {
    Button(
        onClick = onSave,
        modifier = Modifier.fillMaxWidth().testTag(testTag),
    ) {
      Text(if (isSaved) "Saved" else "Save")
    }
  }
}
