// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.se.jdrnexus.ui.gameManagement

import org.junit.Assert.assertEquals
import org.junit.Test

/** Plain JUnit tests of [cleanInvitationCodeInput], the filter behind the join dialog's field. */
class CleanInvitationCodeInputTest {

  @Test
  fun lowercaseLettersAreUppercased() {
    assertEquals("ABC", cleanInvitationCodeInput("abc"))
  }

  @Test
  fun charactersNeverInACodeAreDropped() {
    assertEquals("AB2", cleanInvitationCodeInput("a0O1iL b-2.!"))
  }

  @Test
  fun atMostEightCharactersAreKept() {
    assertEquals("ABCDEFGH", cleanInvitationCodeInput("ABCDEFGHJK"))
  }

  @Test
  fun aValidCodeIsKeptAsTyped() {
    assertEquals("XYZ23456", cleanInvitationCodeInput("XYZ23456"))
  }
}
