// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.github.se.jdrnexus.ui.search

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.se.jdrnexus.FolderItem
import com.github.se.jdrnexus.R
import com.github.se.jdrnexus.model.personalSpace.JDRFile
import com.github.se.jdrnexus.resources.AppTopBar
import com.github.se.jdrnexus.resources.C
import com.github.se.jdrnexus.ui.theme.LocalAppUiColors

@Composable
fun DocumentSearchScreen(
    onBack: () -> Unit,
    onDocumentClicked: (JDRFile) -> Unit,
    searchViewModel: DocumentSearchInterfaceVM = viewModel<DocumentSearchViewModel>(),
) {
  val uiState by searchViewModel.uiState.collectAsState()
  val colors = LocalAppUiColors.current
  val focusRequester = remember { FocusRequester() }
  val keyboardController = LocalSoftwareKeyboardController.current

  LaunchedEffect(Unit) {
    withFrameNanos {}
    focusRequester.requestFocus()
    keyboardController?.show()
  }
  BackHandler(onBack = onBack)

  Scaffold(
      containerColor = colors.background,
      topBar = {
        AppTopBar(
            title = stringResource(R.string.search_title),
            onBack = onBack,
            titleContent = {
              OutlinedTextField(
                  value = uiState.query,
                  onValueChange = searchViewModel::onQueryChanged,
                  modifier =
                      Modifier.fillMaxWidth()
                          .focusRequester(focusRequester)
                          .testTag(C.Tag.search_query),
                  placeholder = { Text(stringResource(R.string.search_hint)) },
                  leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = null,
                    )
                  },
                  trailingIcon = {
                    if (uiState.query.isNotEmpty()) {
                      IconButton(
                          onClick = searchViewModel::clearQuery,
                          modifier = Modifier.testTag(C.Tag.search_clear),
                      ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.search_clear),
                        )
                      }
                    }
                  },
                  singleLine = true,
                  keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                  keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide() }),
              )
            },
        )
      },
  ) { contentPadding ->
    when (val status = uiState.status) {
      DocumentSearchStatus.Blank ->
          SearchMessage(
              message = stringResource(R.string.search_blank_hint),
              modifier = Modifier.fillMaxSize().padding(contentPadding),
          )
      DocumentSearchStatus.Loading ->
          Box(
              modifier = Modifier.fillMaxSize().padding(contentPadding),
              contentAlignment = Alignment.Center,
          ) {
            CircularProgressIndicator(
                modifier = Modifier.testTag(C.Tag.search_loading),
                color = colors.accent,
            )
          }
      DocumentSearchStatus.NoResults ->
          SearchMessage(
              message = stringResource(R.string.search_no_results),
              modifier = Modifier.fillMaxSize().padding(contentPadding),
          )
      is DocumentSearchStatus.Error ->
          SearchMessage(
              message = stringResource(status.reason.toStringResource()),
              modifier = Modifier.fillMaxSize().padding(contentPadding),
              action = {
                TextButton(onClick = searchViewModel::retry) {
                  Text(stringResource(R.string.search_retry))
                }
              },
          )
      DocumentSearchStatus.Results ->
          LazyColumn(
              modifier = Modifier.fillMaxSize().padding(contentPadding),
              contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
              verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            items(uiState.results, key = { it.file.id }) { result ->
              FolderItem(
                  folder =
                      result.file.copy(
                          content =
                              result.folderPath.joinToString(" / ").ifEmpty {
                                stringResource(R.string.search_personal_space_path)
                              }
                      ),
                  onFolderClicked = { onDocumentClicked(result.file) },
                  modifier = Modifier.testTag(C.Tag.searchResult(result.file.id)),
                  cardColor = colors.surface,
                  iconBackground = colors.iconBackground,
                  primaryText = colors.primaryText,
                  secondaryText = colors.secondaryText,
                  borderColor = colors.outline,
                  accentColor = colors.accent,
              )
            }
          }
    }
  }
}

@Composable
private fun SearchMessage(
    message: String,
    modifier: Modifier = Modifier,
    action: @Composable (() -> Unit)? = null,
) {
  val colors = LocalAppUiColors.current
  Box(modifier = modifier.padding(24.dp), contentAlignment = Alignment.Center) {
    androidx.compose.foundation.layout.Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Text(
          text = message,
          color = colors.secondaryText,
          style = MaterialTheme.typography.bodyLarge,
      )
      action?.invoke()
    }
  }
}

private fun SearchError.toStringResource(): Int =
    when (this) {
      SearchError.NOT_SIGNED_IN -> R.string.search_error_not_signed_in
      SearchError.GROUP_ACCESS_DENIED -> R.string.search_error_group_access_denied
      SearchError.SEARCH_FAILED -> R.string.search_error_failed
    }
