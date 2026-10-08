// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.github.se.jdrnexus

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import com.github.se.jdrnexus.model.personalSpace.DocumentType
import com.github.se.jdrnexus.model.personalSpace.JDRFile
import com.github.se.jdrnexus.ui.theme.SampleAppTheme
import androidx.compose.material.icons.filled.CatchingPokemon

const val PERSONAL_SPACE_ADD_BUTTON_TAG = "personalSpaceAddButton"
const val PERSONAL_SPACE_BACK_BUTTON_TAG = "personalSpaceBackButton"
const val PERSONAL_SPACE_ITEM_TAG_PREFIX = "personalSpaceItem"

private val personalSpaceItems =
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
            id = "session-pact",
            name = "Session 12 — The Pact",
            type = DocumentType.TEXT,
            content = "Notes from our last adventure",
        ),
        JDRFile(
            id = "silver-covenant",
            name = "The Silver Covenant",
            type = DocumentType.FOLDER,
            content = "4 files · Allies & adversaries",
        ),
    )

@Composable
fun PersonalSpaceScreen(
    onAddButton: () -> Unit,
    onBackButton: () -> Unit,
    onFolderClicked: () -> Unit,
    navigation: NavigationPlaceHolder? = null,
) {
  val darkTheme = isSystemInDarkTheme()
  val backgroundColor = if (darkTheme) Color(0xFF17100D) else Color(0xFFF5E9DE)
  val cardColor = if (darkTheme) Color(0xFF2C211B) else Color(0xFFE4D5C8)
  val iconBackground = if (darkTheme) Color(0xFF1C140F) else Color(0xFFF7EDE4)
  val primaryText = if (darkTheme) Color(0xFFE9DDD2) else Color(0xFF33251D)
  val secondaryText = if (darkTheme) Color(0xFFA98E79) else Color(0xFF96745E)
  val borderColor = if (darkTheme) Color(0xFF705E50) else Color(0xFFB9A08D)
  val accentColor = if (darkTheme) Color(0xFFE2BC32) else Color(0xFF704222)

  Scaffold(
      containerColor = backgroundColor,
      floatingActionButton = {
        FloatingActionButton(
            onClick = onAddButton,
            modifier = Modifier.testTag(PERSONAL_SPACE_ADD_BUTTON_TAG),
            shape = RoundedCornerShape(50),
            containerColor = accentColor,
            contentColor = if (darkTheme) Color(0xFF17100D) else Color.White,
        ) {
          SpaceIcon(kind = "plus", tint = if (darkTheme) Color(0xFF17100D) else Color.White)
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
              modifier =
                  Modifier.testTag(PERSONAL_SPACE_BACK_BUTTON_TAG)
                      .semantics { contentDescription = "Back" },
          ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = null,
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
          SpaceIcon(kind = "search", tint = accentColor)
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Medieval",
            color = primaryText,
            fontFamily = FontFamily.Serif,
            fontSize = 30.sp,
            letterSpacing = 1.5.sp,
            modifier = Modifier.testTag("personalSpaceTitle"),
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
      modifier =
          modifier
              .fillMaxWidth()
              .height(96.dp)
              .testTag("$PERSONAL_SPACE_ITEM_TAG_PREFIX${folder.id}"),
      shape = RoundedCornerShape(12.dp),
      colors = CardDefaults.cardColors(containerColor = cardColor),
      border = BorderStroke(1.dp, borderColor),
  ) {
    Row(
        modifier = Modifier.fillMaxSize().padding(horizontal = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Box(
          modifier = Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)).background(iconBackground),
          contentAlignment = Alignment.Center,
      ) {
        SpaceIcon(
            kind =
                when (folder.type) {
                  DocumentType.FOLDER -> "folder"
                  DocumentType.CHARACTER -> "person"
                  DocumentType.TEXT ->
                      if (folder.name.startsWith("Session")) "document" else "book"
                },
            tint = accentColor,
            size = 24.dp,
        )
      }


      Column(modifier = Modifier.weight(1f).padding(start = 14.dp, end = 10.dp)) {
        Text(
            text =
                when (folder.type) {
                  DocumentType.FOLDER -> "FOLDER"
                  DocumentType.CHARACTER -> "CHARACTER SHEET"
                  DocumentType.TEXT ->
                      if (folder.name.startsWith("Session")) "TEXT DOCUMENT" else "LORE NOTE"
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
        SpaceIcon(kind = "chevron", tint = secondaryText, size = 16.dp)
      }
    }
  }
}

@Composable
private fun SpaceIcon(kind: String, tint: Color, size: Dp = 24.dp) {
  Canvas(modifier = Modifier.size(size).testTag("personalSpaceIcon$kind")) {
    val scale = this.size.minDimension / 24f
    drawContext.transform.scale(scale, scale, pivot = androidx.compose.ui.geometry.Offset.Zero)
    val stroke = Stroke(width = 1.6f)
    when (kind) {
      "back" -> {
        drawLine(tint, androidx.compose.ui.geometry.Offset(19f, 12f), androidx.compose.ui.geometry.Offset(5f, 12f), 1.8f)
        drawLine(tint, androidx.compose.ui.geometry.Offset(5f, 12f), androidx.compose.ui.geometry.Offset(11f, 6f), 1.8f)
        drawLine(tint, androidx.compose.ui.geometry.Offset(5f, 12f), androidx.compose.ui.geometry.Offset(11f, 18f), 1.8f)
      }
      "search" -> {
        drawCircle(tint, radius = 7f, center = androidx.compose.ui.geometry.Offset(10f, 10f), style = stroke)
        drawLine(tint, androidx.compose.ui.geometry.Offset(15f, 15f), androidx.compose.ui.geometry.Offset(21f, 21f), 1.8f)
      }
      "plus" -> {
        drawLine(tint, androidx.compose.ui.geometry.Offset(12f, 4f), androidx.compose.ui.geometry.Offset(12f, 20f), 1.8f)
        drawLine(tint, androidx.compose.ui.geometry.Offset(4f, 12f), androidx.compose.ui.geometry.Offset(20f, 12f), 1.8f)
      }
      "chevron" -> {
        drawLine(tint, androidx.compose.ui.geometry.Offset(9f, 5f), androidx.compose.ui.geometry.Offset(15f, 12f), 1.8f)
        drawLine(tint, androidx.compose.ui.geometry.Offset(15f, 12f), androidx.compose.ui.geometry.Offset(9f, 19f), 1.8f)
      }
      "folder" -> {
        val path =
            Path().apply {
              moveTo(3f, 6f)
              lineTo(9f, 6f)
              lineTo(11f, 8f)
              lineTo(21f, 8f)
              lineTo(21f, 19f)
              lineTo(3f, 19f)
              close()
            }
        drawPath(path, tint, style = stroke)
      }
      "person" -> {
        drawCircle(tint, radius = 4f, center = androidx.compose.ui.geometry.Offset(12f, 8f), style = stroke)
        val path =
            Path().apply {
              moveTo(4f, 21f)
              quadraticTo(4f, 14f, 12f, 14f)
              quadraticTo(20f, 14f, 20f, 21f)
            }
        drawPath(path, tint, style = stroke)
      }
      "book" -> {
        val path =
            Path().apply {
              moveTo(12f, 6f)
              quadraticTo(8f, 3f, 3f, 5f)
              lineTo(3f, 19f)
              quadraticTo(8f, 17f, 12f, 20f)
              quadraticTo(16f, 17f, 21f, 19f)
              lineTo(21f, 5f)
              quadraticTo(16f, 3f, 12f, 6f)
              close()
              moveTo(12f, 6f)
              lineTo(12f, 20f)
            }
        drawPath(path, tint, style = stroke)
      }
      "document" -> {
        val path =
            Path().apply {
              moveTo(6f, 3f)
              lineTo(15f, 3f)
              lineTo(19f, 7f)
              lineTo(19f, 21f)
              lineTo(6f, 21f)
              close()
              moveTo(14f, 3f)
              lineTo(14f, 8f)
              lineTo(19f, 8f)
              moveTo(9f, 12f)
              lineTo(16f, 12f)
              moveTo(9f, 16f)
              lineTo(16f, 16f)
            }
        drawPath(path, tint, style = stroke)
      }
    }
  }
}

@Preview
@Composable
private fun PersonalSpaceScreenPreview() {
  SampleAppTheme(darkTheme = true, dynamicColor = false) {
    PersonalSpaceScreen(onAddButton = {}, onBackButton = {}, onFolderClicked = {})
  }
}
