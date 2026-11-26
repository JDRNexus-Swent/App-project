// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.ui.profile

import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onChild
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.se.jdrnexus.resources.C
import com.github.se.jdrnexus.ui.theme.SampleAppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProfileButtonTest {
  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun describesItselfAndReportsTaps() {
    var clicks = 0
    composeTestRule.setContent { SampleAppTheme { ProfileButton(onClick = { clicks++ }) } }

    composeTestRule
        .onNodeWithTag(C.Tag.open_profile, useUnmergedTree = true)
        .onChild()
        .assertContentDescriptionEquals("Open profile")
    composeTestRule.onNodeWithTag(C.Tag.open_profile).assertIsEnabled().performClick()

    assertEquals(1, clicks)
  }

  @Test
  fun disabledButtonIgnoresTaps() {
    var clicks = 0
    composeTestRule.setContent {
      SampleAppTheme { ProfileButton(onClick = { clicks++ }, enabled = false) }
    }

    composeTestRule.onNodeWithTag(C.Tag.open_profile).assertIsNotEnabled().performClick()

    assertEquals(0, clicks)
  }
}
