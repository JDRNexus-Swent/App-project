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
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
          CircularProgressIndicator()
          Text(loadingMessage, modifier = Modifier.padding(top = 12.dp))
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

@Composable
internal fun EditFileSectionHeading(
    title: String,
    count: Int,
    modifier: Modifier = Modifier,
    singularCountLabel: String = title.lowercase().dropLast(1),
) {
  Row(
      modifier = modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(title, style = MaterialTheme.typography.titleMedium)
    Text(
        text = "${count} ${if (count == 1) singularCountLabel else title.lowercase()}",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

@Composable
internal fun EditFileErrorMessage(error: String, testTag: String) {
  Text(
      text = error,
      color = MaterialTheme.colorScheme.error,
      style = MaterialTheme.typography.bodySmall,
      modifier = Modifier.testTag(testTag),
  )
}

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
