// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.ui.help

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.github.se.jdrnexus.R
import com.github.se.jdrnexus.resources.C
import kotlinx.coroutines.launch

/**
 * Shows [pages] in a modal bottom sheet over the current screen. The screen stays visible behind
 * the sheet's scrim and receives no taps while the sheet is open.
 *
 * [onDismiss] is called once the sheet is closed, whether by "Got it" on the last page, a swipe
 * down, a tap outside the sheet or system back. The caller then stops composing [HelpSheet].
 *
 * @param pages the pages to show, in order.
 * @param onDismiss called when the sheet has been closed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpSheet(pages: List<HelpPage>, onDismiss: () -> Unit) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val scope = rememberCoroutineScope()
  ModalBottomSheet(
      onDismissRequest = onDismiss,
      sheetState = sheetState,
      modifier = Modifier.testTag(C.Tag.help_sheet),
  ) {
    HelpPagerContent(
        pages = pages,
        onDone = {
          // Slides the sheet down before removing it, as a swipe would, instead of cutting it off.
          scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
        },
    )
  }
}

/**
 * Content of [HelpSheet]: one page at a time, a dot per page, and a button that moves to the next
 * page or, on the last page, calls [onDone]. The user can also swipe between pages.
 *
 * @param pages the pages to show, in order. With no page, only the "Got it" button is shown.
 * @param onDone called by "Got it" on the last page.
 */
@Composable
internal fun HelpPagerContent(
    pages: List<HelpPage>,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
  val pagerState = rememberPagerState(pageCount = { pages.size })
  val scope = rememberCoroutineScope()
  // settledPage only changes once the pager has stopped: while it slides to the last page, "Next"
  // stays in place, so a second quick tap cannot land on "Got it" before that page is shown.
  val isLastPage = pagerState.settledPage >= pages.lastIndex

  Column(
      modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 16.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    // weight(fill = false) lets the page shrink and scroll when the sheet is short (landscape,
    // large fonts), while the dots and the button stay visible below it.
    HorizontalPager(
        state = pagerState,
        modifier = Modifier.weight(1f, fill = false),
        // The pager is as tall as the tallest page it composes. Composing every page keeps that
        // height, and so the sheet's, the same on every page instead of changing during a swipe.
        beyondViewportPageCount = pages.size,
        // A shorter page starts at the top, where the other pages start, instead of being centred.
        verticalAlignment = Alignment.Top,
    ) { index ->
      HelpPageContent(page = pages[index], modifier = Modifier.testTag(C.Tag.helpPage(index)))
    }
    Spacer(Modifier.height(16.dp))
    PageIndicator(currentPage = pagerState.currentPage, pageCount = pages.size)
    Spacer(Modifier.height(16.dp))
    if (isLastPage) {
      Button(onClick = onDone, modifier = Modifier.fillMaxWidth().testTag(C.Tag.help_done)) {
        Text(stringResource(R.string.help_done))
      }
    } else {
      Button(
          onClick = {
            // Starts from the page the pager is heading to, so a second tap during the animation
            // moves one page further instead of repeating the same move, and never past the last
            // page.
            val next = (pagerState.targetPage + 1).coerceAtMost(pages.lastIndex)
            scope.launch { pagerState.animateScrollToPage(next) }
          },
          modifier = Modifier.fillMaxWidth().testTag(C.Tag.help_next),
      ) {
        Text(stringResource(R.string.help_next))
      }
    }
  }
}

/**
 * Shows one help page: its illustration if it has one, its title and its explanation, scrolling if
 * they don't fit.
 */
@Composable
private fun HelpPageContent(page: HelpPage, modifier: Modifier = Modifier) {
  Column(
      modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
      horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    page.illustration?.let { illustration ->
      HelpIllustration(
          content = illustration,
          maxHeightFraction = page.illustrationMaxHeightFraction,
      )
      Spacer(Modifier.height(16.dp))
    }
    Text(
        text = stringResource(page.title),
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(8.dp))
    Text(
        text = stringResource(page.body),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
  }
}

/**
 * Shows one dot per page, the current one highlighted. Screen readers hear "Page x of y" instead of
 * the dots.
 */
@Composable
private fun PageIndicator(currentPage: Int, pageCount: Int) {
  val description =
      stringResource(R.string.help_page_indicator_description, currentPage + 1, pageCount)
  Row(
      modifier =
          Modifier.testTag(C.Tag.help_page_indicator).clearAndSetSemantics {
            contentDescription = description
          },
      horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    repeat(pageCount) { index ->
      val color =
          if (index == currentPage) MaterialTheme.colorScheme.primary
          else MaterialTheme.colorScheme.outlineVariant
      Box(Modifier.size(8.dp).clip(CircleShape).background(color))
    }
  }
}
