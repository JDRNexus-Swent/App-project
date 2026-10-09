// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.github.se.jdrnexus

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.se.jdrnexus.model.personalSpace.DocumentType
import com.github.se.jdrnexus.model.personalSpace.JDRFile
import com.github.se.jdrnexus.ui.theme.SampleAppTheme

internal val PERSONAL_SPACE_CONTAINER_COLOR =
    SemanticsPropertyKey<Color>("PersonalSpaceContainerColor")

private data class PersonalSpaceThemeColors(
    val backgroundColor: Color,
    val cardColor: Color,
    val iconBackground: Color,
    val primaryText: Color,
    val secondaryText: Color,
    val borderColor: Color,
    val accentColor: Color,
    val floatingActionContentColor: Color,
)

private val darkPersonalSpaceTheme =
    PersonalSpaceThemeColors(
        backgroundColor = Color(0xFF17100D),
        cardColor = Color(0xFF2C211B),
        iconBackground = Color(0xFF1C140F),
        primaryText = Color(0xFFE9DDD2),
        secondaryText = Color(0xFFA98E79),
        borderColor = Color(0xFF705E50),
        accentColor = Color(0xFFE2BC32),
        floatingActionContentColor = Color(0xFF17100D),
    )

private val lightPersonalSpaceTheme =
    PersonalSpaceThemeColors(
        backgroundColor = Color(0xFFF5E9DE),
        cardColor = Color(0xFFE4D5C8),
        iconBackground = Color(0xFFF7EDE4),
        primaryText = Color(0xFF33251D),
        secondaryText = Color(0xFF96745E),
        borderColor = Color(0xFFB9A08D),
        accentColor = Color(0xFF704222),
        floatingActionContentColor = Color.White,
    )

private val samplePersonalSpaceItems =
    listOf(
        JDRFile(
            id = "ashen-realms",
            name = "The Ashen Realms",
            type = DocumentType.FOLDER,
            content = "8 files · World lore & maps",
        ),
        JDRFile(
            id = "elara-moonwhisper",
            name = "Elara Moonwhisper",
            type = DocumentType.CHARACTER,
            content = "Level 7 · Half-elf ranger",
        ),
        JDRFile(
            id = "sunken-citadel",
            name = "The Sunken Citadel",
            type = DocumentType.TEXT,
            content = "Lore of a forgotten kingdom",
        ),
        JDRFile(
            id = "silver-covenant",
            name = "The Silver Covenant",
            type = DocumentType.FOLDER,
            content = "4 files · Allies & adversaries",
        ),
    )

private const val PERSONAL_SPACE_BACKGROUND_TAG = "personalSpaceBackground"

@Composable
fun PersonalSpaceScreen(
    onAddButton: () -> Unit,
    onBackButton: () -> Unit,
    onFolderClicked: () -> Unit,
    personalSpaceItems: List<JDRFile> = samplePersonalSpaceItems,
) {
  val darkTheme = isSystemInDarkTheme()
  val themeColors = if (darkTheme) darkPersonalSpaceTheme else lightPersonalSpaceTheme
  val backgroundColor = themeColors.backgroundColor
  val cardColor = themeColors.cardColor
  val iconBackground = themeColors.iconBackground
  val primaryText = themeColors.primaryText
  val secondaryText = themeColors.secondaryText
  val borderColor = themeColors.borderColor
  val accentColor = themeColors.accentColor

  Scaffold(
      modifier =
          Modifier.testTag(PERSONAL_SPACE_BACKGROUND_TAG).semantics {
            this[PERSONAL_SPACE_CONTAINER_COLOR] = backgroundColor
          },
      containerColor = backgroundColor,
      floatingActionButton = {
        FloatingActionButton(
            onClick = onAddButton,
            shape = RoundedCornerShape(50),
            containerColor = accentColor,
            contentColor = themeColors.floatingActionContentColor,
        ) {
          Icon(
              imageVector = Icons.Filled.Add,
              contentDescription = "Add item",
              tint = themeColors.floatingActionContentColor,
          )
        }
      },
      bottomBar = {
        Surface(
            modifier = Modifier.fillMaxWidth().height(80.dp),
            color = cardColor,
            border = BorderStroke(0.5.dp, borderColor),
        ) {}
      },
  ) { contentPadding ->
    Column(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
      Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().height(52.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
          IconButton(
              onClick = onBackButton,
          ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = accentColor,
            )
          }
          Spacer(modifier = Modifier.width(16.dp))
          Text(
              text = "My Space",
              color = secondaryText,
              fontSize = 12.sp,
          )
          Spacer(modifier = Modifier.width(10.dp))
          Text(text = "›", color = secondaryText, fontSize = 19.sp)
          Spacer(modifier = Modifier.width(10.dp))
          Text(text = "Medieval", color = primaryText, fontSize = 12.sp)
          Spacer(modifier = Modifier.weight(1f))
          Icon(
              imageVector = Icons.Filled.Search,
              contentDescription = "Search",
              tint = accentColor,
          )
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Medieval",
            color = primaryText,
            fontFamily = FontFamily.Serif,
            fontSize = 30.sp,
            letterSpacing = 1.5.sp,
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
              text = "${personalSpaceItems.size} items · Your personal notes, kept together.",
              color = secondaryText,
              fontSize = 12.sp,
              modifier = Modifier.weight(1f),
          )
          Box(
              modifier = Modifier.width(20.dp).height(1.dp).background(borderColor),
          )
          Text(
              text = "◆",
              color = accentColor,
              fontSize = 8.sp,
              modifier = Modifier.padding(start = 8.dp),
          )
        }
      }

      if (personalSpaceItems.isEmpty()) {
        Text(
            text = "Your personal space is empty.",
            color = secondaryText,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
        )
      } else {
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          items(personalSpaceItems, key = { it.id }) { item ->
            FolderItem(
                folder = item,
                onFolderClicked = onFolderClicked,
                cardColor = cardColor,
                iconBackground = iconBackground,
                primaryText = primaryText,
                secondaryText = secondaryText,
                borderColor = borderColor,
                accentColor = accentColor,
            )
          }
        }
      }
    }
  }
}

@Composable
fun FolderItem(
    folder: JDRFile,
    onFolderClicked: () -> Unit,
    modifier: Modifier = Modifier,
    cardColor: Color = MaterialTheme.colorScheme.surface,
    iconBackground: Color = MaterialTheme.colorScheme.surfaceVariant,
    primaryText: Color = MaterialTheme.colorScheme.onSurface,
    secondaryText: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    borderColor: Color = MaterialTheme.colorScheme.outline,
    accentColor: Color = MaterialTheme.colorScheme.primary,
) {
  Card(
      onClick = onFolderClicked,
      modifier = modifier.fillMaxWidth().height(96.dp),
      shape = RoundedCornerShape(12.dp),
      colors = CardDefaults.cardColors(containerColor = cardColor),
      border = BorderStroke(1.dp, borderColor),
  ) {
    Row(
        modifier = Modifier.fillMaxSize().padding(horizontal = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Box(
          modifier =
              Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)).background(iconBackground),
          contentAlignment = Alignment.Center,
      ) {
        Icon(
            imageVector =
                when (folder.type) {
                  DocumentType.FOLDER -> Icons.Filled.Folder
                  DocumentType.CHARACTER -> Icons.Filled.Person
                  DocumentType.TEXT -> Icons.AutoMirrored.Filled.MenuBook
                },
            contentDescription =
                when (folder.type) {
                  DocumentType.FOLDER -> "Folder"
                  DocumentType.CHARACTER -> "Character"
                  DocumentType.TEXT -> "Text document"
                },
            tint = accentColor,
            modifier = Modifier.size(24.dp),
        )
      }
      Column(modifier = Modifier.weight(1f).padding(start = 14.dp, end = 10.dp)) {
        Text(
            text =
                when (folder.type) {
                  DocumentType.FOLDER -> "FOLDER"
                  DocumentType.CHARACTER -> "CHARACTER SHEET"
                  DocumentType.TEXT -> "TEXT DOCUMENT"
                },
            color = secondaryText,
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.2.sp,
        )
        Text(
            text = folder.name,
            color = primaryText,
            fontSize = 15.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
        Text(
            text = folder.content,
            color = secondaryText,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 3.dp),
        )
      }

      if (folder.type == DocumentType.FOLDER) {
        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = secondaryText,
            modifier = Modifier.size(16.dp),
        )
      }
    }
  }
}

@Preview
@Composable
private fun PersonalSpaceScreenPreview() {
  SampleAppTheme(darkTheme = true) {
    PersonalSpaceScreen(onAddButton = {}, onBackButton = {}, onFolderClicked = {})
  }
}
