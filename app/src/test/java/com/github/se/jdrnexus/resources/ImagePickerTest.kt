// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.resources

import android.content.ContentResolver
import android.database.MatrixCursor
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.se.jdrnexus.utils.FakeImagePicker
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Tests of [rememberImagePicker] and [pickedImageName]. */
@RunWith(AndroidJUnit4::class)
class ImagePickerTest {
  @get:Rule val composeTestRule = createComposeRule()

  private val picture = Uri.parse("content://media/picker/0/42")

  @Test
  fun pickerAsksForImagesOnlyAndPassesThePickedPicture() {
    val picker = FakeImagePicker(picked = picture)
    val picked = showPicker(picker)

    composeTestRule.onNodeWithTag(PICK).performClick()
    composeTestRule.waitForIdle()

    assertEquals(PickVisualMedia.ImageOnly, picker.requests.single().mediaType)
    assertEquals(listOf(picture), picked)
  }

  @Test
  fun closingThePickerWithoutAPictureCallsNothing() {
    val picker = FakeImagePicker(picked = null)
    val picked = showPicker(picker)

    composeTestRule.onNodeWithTag(PICK).performClick()
    composeTestRule.waitForIdle()

    assertEquals(1, picker.requests.size)
    assertEquals(emptyList<Uri>(), picked)
  }

  @Test
  fun nameDropsTheLastExtension() {
    assertEquals("map", pickedImageName(resolverNaming("map.png"), picture, FALLBACK))
    assertEquals("old.map", pickedImageName(resolverNaming("old.map.webp"), picture, FALLBACK))
    assertEquals("Harbour", pickedImageName(resolverNaming(" Harbour .jpg"), picture, FALLBACK))
  }

  @Test
  fun nameWithoutExtensionIsKept() {
    assertEquals("map", pickedImageName(resolverNaming("map"), picture, FALLBACK))
  }

  @Test
  fun blankOrMissingNameGivesTheFallback() {
    assertEquals(FALLBACK, pickedImageName(resolverNaming(".png"), picture, FALLBACK))
    assertEquals(FALLBACK, pickedImageName(resolverNaming(null), picture, FALLBACK))
    assertEquals(FALLBACK, pickedImageName(resolverWithRows(), picture, FALLBACK))
    assertEquals(FALLBACK, pickedImageName(resolverReturning(null), picture, FALLBACK))
  }

  @Test
  fun refusedQueryGivesTheFallback() {
    val resolver = mockk<ContentResolver>()
    every { resolver.query(picture, any(), null, null, null) } throws SecurityException("denied")

    assertEquals(FALLBACK, pickedImageName(resolver, picture, FALLBACK))
  }

  private fun showPicker(picker: FakeImagePicker): List<Uri> {
    val picked = mutableListOf<Uri>()
    composeTestRule.setContent {
      picker.Provide {
        val pick = rememberImagePicker { picked += it }
        Button(onClick = pick, modifier = Modifier.testTag(PICK)) { Text("Pick") }
      }
    }
    return picked
  }

  private fun resolverNaming(name: String?) = resolverWithRows(arrayOf(name))

  private fun resolverWithRows(vararg rows: Array<Any?>) =
      resolverReturning(
          MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME)).apply { rows.forEach(::addRow) }
      )

  private fun resolverReturning(cursor: MatrixCursor?): ContentResolver {
    val resolver = mockk<ContentResolver>()
    every { resolver.query(picture, any(), null, null, null) } returns cursor
    return resolver
  }

  private companion object {
    const val PICK = "pick"
    const val FALLBACK = "Image"
  }
}
