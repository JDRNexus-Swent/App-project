// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.github.se.jdrnexus.ui.home

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

object TestTags {
  const val TITLE = "home_title"
  const val OBJECTSROW = "home_placeholder_objects"
  const val PROFILE_PIC = "home_image_placeholder"
  const val ADVENTURES = "home_local_adventures"
  const val QUICKTOOLS = "home_quicktools"
  val BUTTONS = listOf("home_quicktool_1", "home_quicktool_2", "home_quicktool_3")
  val placeholderObjects = listOf("home_placeholder_1", "home_placeholder_2", "home_placeholder_3")
}

@Composable
fun HomeScreen(modifier: Modifier = Modifier, onLogout: () -> Unit = {}) {
  Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Surface(
          modifier = Modifier.size(48.dp).testTag(TestTags.PROFILE_PIC),
          color = MaterialTheme.colorScheme.secondaryContainer,
          shape = MaterialTheme.shapes.medium,
      ) {
        Box {}
      }
      Button(onClick = onLogout) { Text("Log out") }
    }

    Text(
        text = "JDRNexus",
        modifier = Modifier.testTag(TestTags.TITLE),
        style = MaterialTheme.typography.headlineLarge,
    )

    Row(
        modifier =
            Modifier.testTag(TestTags.OBJECTSROW)
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 16.dp)
    ) {
      placeholderObjects.forEachIndexed { index, placeholder ->
        Surface(
            modifier = Modifier.padding(end = 12.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = MaterialTheme.shapes.medium,
        ) {
          Text(
              text = placeholder,
              modifier =
                  Modifier.testTag(TestTags.placeholderObjects[index])
                      .padding(horizontal = 24.dp, vertical = 32.dp),
              style = MaterialTheme.typography.titleMedium,
          )
        }
      }
    }

    Text(
        text = "Local Adventures",
        modifier = Modifier.testTag(TestTags.ADVENTURES),
        style = MaterialTheme.typography.headlineSmall,
    )

    Spacer(modifier = Modifier.weight(1f))

    Text(
        text = "Quicktools",
        modifier = Modifier.testTag(TestTags.QUICKTOOLS),
        style = MaterialTheme.typography.headlineSmall,
    )

    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      quicktools.forEachIndexed { index, quicktool ->
        Button(
            onClick = {},
            modifier = Modifier.weight(1f).aspectRatio(1f).testTag(TestTags.BUTTONS[index]),
        ) {
          Text(quicktool)
        }
      }
    }
  }
}

private val placeholderObjects = listOf("Adventure 1", "Adventure 2", "Adventure 3")
private val quicktools = listOf("Tool 1", "Tool 2", "Tool 3")
