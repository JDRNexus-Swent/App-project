// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.github.se.jdrnexus.ui.home

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.se.jdrnexus.resources.MAX_LANDSCAPE_CONTENT_WIDTH
import com.github.se.jdrnexus.resources.useLandscapeLayout
import com.github.se.jdrnexus.ui.authentication.AuthStatus
import com.github.se.jdrnexus.ui.authentication.AuthViewModel

object TestTags {
  const val SCREEN = "home_screen"
  const val TITLE = "home_title"
  const val OBJECTSROW = "home_placeholder_objects"
  const val PROFILE_PIC = "home_image_placeholder"
  const val ADVENTURES = "home_local_adventures"
  const val QUICKTOOLS = "home_quicktools"
  const val PSPACEBUTTON = "home_to_pspace"
  const val DICEBUTTON = "home_to_dice"
  const val GROUPMANAGEMENTBUTTON = "home_to_group_management"
  const val LOGOUT = "home_logout"
  val BUTTONS = listOf(DICEBUTTON, GROUPMANAGEMENTBUTTON)
  val placeholderObjects = listOf("home_placeholder_1", "home_placeholder_2", "home_placeholder_3")
}

@Composable
fun HomeScreen(
    viewModel: AuthViewModel = viewModel(factory = AuthViewModel.Factory),
    modifier: Modifier = Modifier,
    onLogout: () -> Unit = {},
    onPersonalSpaceClick: () -> Unit = {},
    onDiceClick: () -> Unit = {},
    onGroupManagementClick: () -> Unit = {},
) {

  val uiState by viewModel.uiState.collectAsState()
  var logoutRequested by remember { mutableStateOf(false) }

  LaunchedEffect(uiState.status) {
    when (uiState.status) {
      AuthStatus.Idle -> {
        if (logoutRequested) {
          logoutRequested = false
          onLogout()
        }
      }
      is AuthStatus.Error -> logoutRequested = false
      else -> Unit
    }
  }

  val onLogoutClick = {
    logoutRequested = true
    viewModel.signOut()
  }
  val quickTools =
      listOf(
          QuickTool("Dice", TestTags.DICEBUTTON, Icons.Filled.Casino, onDiceClick),
          QuickTool(
              "Group Management",
              TestTags.GROUPMANAGEMENTBUTTON,
              Icons.Filled.Groups,
              onGroupManagementClick,
          ),
          QuickTool(
              "Personal Space",
              TestTags.PSPACEBUTTON,
              Icons.Filled.Person,
              onPersonalSpaceClick,
          ),
      )

  Surface(
      modifier = modifier.fillMaxSize().testTag(TestTags.SCREEN),
      color = MaterialTheme.colorScheme.background,
      contentColor = MaterialTheme.colorScheme.onBackground,
  ) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
      if (useLandscapeLayout(maxWidth, maxHeight)) {
        HomeLandscape(onLogoutClick = onLogoutClick, quickTools = quickTools)
      } else {
        HomePortrait(onLogoutClick = onLogoutClick, quickTools = quickTools)
      }
    }
  }
}

/**
 * A Quicktools shortcut: its [label], the [tag] tests find it by, the [icon] shown next to the
 * label in landscape, and what it opens.
 */
private data class QuickTool(
    val label: String,
    val tag: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
)

/**
 * Portrait home: header, title and adventures stacked, with the Quicktools as a row of square
 * buttons at the bottom.
 */
@Composable
private fun HomePortrait(onLogoutClick: () -> Unit, quickTools: List<QuickTool>) {
  Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
      ProfilePlaceholder()
      LogoutButton(onClick = onLogoutClick)
    }

    HomeTitle()

    AdventuresRow(modifier = Modifier.padding(vertical = 16.dp))

    SectionLabel(text = "Local Adventures", tag = TestTags.ADVENTURES)

    Spacer(modifier = Modifier.weight(1f))

    SectionLabel(text = "Quicktools", tag = TestTags.QUICKTOOLS)

    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      quickTools.forEach { tool ->
        QuickToolButton(tool = tool, modifier = Modifier.weight(1f).aspectRatio(1f))
      }
    }
  }
}

/**
 * Widths of the adventures and Quicktools columns in landscape: two thirds and one third of the
 * body, so the split looks the same on every phone.
 */
private const val LANDSCAPE_ADVENTURES_WEIGHT = 2f
private const val LANDSCAPE_TOOLS_WEIGHT = 1f

/** Space above and below the landscape content. */
private val LANDSCAPE_VERTICAL_PADDING = 16.dp

/** Space between the landscape header and body. */
private val LANDSCAPE_SECTION_GAP = 16.dp

/** Height of the landscape header row: the profile picture's size. */
private val LANDSCAPE_HEADER_HEIGHT = 48.dp

/** Everything above and below the landscape body at the normal font size. */
private val LANDSCAPE_CHROME_HEIGHT =
    LANDSCAPE_VERTICAL_PADDING * 2 + LANDSCAPE_HEADER_HEIGHT + LANDSCAPE_SECTION_GAP

/**
 * Smallest landscape body at the normal font size: the Quicktools label, three 48dp buttons and the
 * gaps between them. Scaled with the user's font size.
 */
private val LANDSCAPE_MIN_BODY_HEIGHT = 32.dp + 12.dp + 48.dp * 3 + 12.dp * 2

/**
 * Landscape home: the title moves into the header row to save height, and the body splits into the
 * adventures on the left and a Quicktools column on the right whose buttons share its height, so a
 * short, wide screen shows everything without overlapping or scrolling.
 */
@Composable
private fun HomeLandscape(onLogoutClick: () -> Unit, quickTools: List<QuickTool>) {
  // Past MAX_LANDSCAPE_CONTENT_WIDTH (tablets) the page stops growing and is centered, so the
  // tool buttons are not stretched and the columns keep their phone proportions.
  BoxWithConstraints(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    // The body takes the height the header leaves, so nothing scrolls at the normal font size.
    // With a large font it never gets shorter than the tool buttons need to show their labels:
    // the page then scrolls instead of cutting them off.
    val minBodyHeight = LANDSCAPE_MIN_BODY_HEIGHT * LocalDensity.current.fontScale
    val bodyHeight = maxOf(maxHeight - LANDSCAPE_CHROME_HEIGHT, minBodyHeight)
    Column(
        modifier =
            Modifier.widthIn(max = MAX_LANDSCAPE_CONTENT_WIDTH)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = LANDSCAPE_VERTICAL_PADDING),
        verticalArrangement = Arrangement.spacedBy(LANDSCAPE_SECTION_GAP),
    ) {
      Row(
          modifier = Modifier.fillMaxWidth().heightIn(min = LANDSCAPE_HEADER_HEIGHT),
          verticalAlignment = Alignment.CenterVertically,
      ) {
        ProfilePlaceholder()
        Spacer(Modifier.width(16.dp))
        HomeTitle()
        Spacer(Modifier.weight(1f))
        LogoutButton(onClick = onLogoutClick)
      }

      Row(
          modifier = Modifier.fillMaxWidth().height(bodyHeight),
          horizontalArrangement = Arrangement.spacedBy(24.dp),
      ) {
        Column(modifier = Modifier.weight(LANDSCAPE_ADVENTURES_WEIGHT).fillMaxHeight()) {
          SectionLabel(text = "Local Adventures", tag = TestTags.ADVENTURES)
          AdventuresRow(modifier = Modifier.padding(top = 12.dp).fillMaxHeight(), tallCards = true)
        }

        Column(
            modifier = Modifier.weight(LANDSCAPE_TOOLS_WEIGHT).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          SectionLabel(text = "Quicktools", tag = TestTags.QUICKTOOLS)
          quickTools.forEach { tool ->
            QuickToolButton(
                tool = tool,
                modifier = Modifier.fillMaxWidth().weight(1f),
                withIcon = true,
            )
          }
        }
      }
    }
  }
}

/** Square placeholder for the user's profile picture. */
@Composable
private fun ProfilePlaceholder() {
  Surface(
      modifier = Modifier.size(48.dp).testTag(TestTags.PROFILE_PIC),
      color = MaterialTheme.colorScheme.secondaryContainer,
      shape = MaterialTheme.shapes.medium,
  ) {
    // This box will contain the profile picture (and maybe more)
    Box {}
  }
}

/** Signs the user out. */
@Composable
private fun LogoutButton(onClick: () -> Unit) {
  Button(onClick = onClick, modifier = Modifier.testTag(TestTags.LOGOUT)) { Text("Log out") }
}

/** The app name shown at the top of the home screen. */
@Composable
private fun HomeTitle() {
  Text(
      text = "JDRNexus",
      modifier = Modifier.testTag(TestTags.TITLE),
      style = MaterialTheme.typography.headlineLarge,
  )
}

/** Title of a home section, found by tests through [tag]. */
@Composable
private fun SectionLabel(text: String, tag: String) {
  Text(
      text = text,
      modifier = Modifier.testTag(tag),
      style = MaterialTheme.typography.headlineSmall,
  )
}

/**
 * Width-to-height ratio of an adventure card in landscape, where the cards fill the height: the
 * card keeps the same shape on every phone.
 */
private const val TALL_ADVENTURE_CARD_ASPECT_RATIO = 2f / 3f

/**
 * Horizontally scrollable row of the user's adventures (placeholders for now). With [tallCards]
 * (landscape), each card fills the row's height at a fixed shape, with its name at the bottom.
 */
@Composable
private fun AdventuresRow(modifier: Modifier = Modifier, tallCards: Boolean = false) {
  Row(
      modifier =
          Modifier.testTag(TestTags.OBJECTSROW)
              .horizontalScroll(rememberScrollState())
              .then(modifier)
  ) {
    placeholderObjects.forEachIndexed { index, placeholder ->
      val name =
          @Composable { textModifier: Modifier ->
            Text(
                text = placeholder,
                modifier = Modifier.testTag(TestTags.placeholderObjects[index]).then(textModifier),
                style = MaterialTheme.typography.titleMedium,
            )
          }
      if (tallCards) {
        Surface(
            modifier =
                Modifier.padding(end = 12.dp)
                    .fillMaxHeight()
                    .aspectRatio(
                        TALL_ADVENTURE_CARD_ASPECT_RATIO,
                        matchHeightConstraintsFirst = true,
                    ),
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = MaterialTheme.shapes.large,
        ) {
          Box(contentAlignment = Alignment.BottomStart) { name(Modifier.padding(16.dp)) }
        }
      } else {
        Surface(
            modifier = Modifier.padding(end = 12.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = MaterialTheme.shapes.medium,
        ) {
          name(Modifier.padding(horizontal = 24.dp, vertical = 32.dp))
        }
      }
    }
  }
}

/**
 * Button opening one of the [QuickTool]s, sized by the caller's [modifier]. [withIcon] (landscape)
 * shows the tool's icon before a left-aligned label.
 */
@Composable
private fun QuickToolButton(tool: QuickTool, modifier: Modifier, withIcon: Boolean = false) {
  if (withIcon) {
    Button(
        onClick = tool.onClick,
        modifier = modifier.testTag(tool.tag),
        shape = MaterialTheme.shapes.large,
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) {
      Icon(tool.icon, contentDescription = null, modifier = Modifier.size(24.dp))
      Spacer(Modifier.width(14.dp))
      Text(tool.label, modifier = Modifier.weight(1f))
    }
  } else {
    Button(onClick = tool.onClick, modifier = modifier.testTag(tool.tag)) { Text(tool.label) }
  }
}

private val placeholderObjects = listOf("Adventure 1", "Adventure 2", "Adventure 3")
