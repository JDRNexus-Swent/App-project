// Co-authored-by: Claude Sonnet 5.5
package com.github.se.jdrnexus.model.personalSpace

import java.util.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InvitationCodeTest {

  @Test
  fun generatedCodeHasTheExpectedLength() {
    val code = InvitationCodes.generate(Random(1))

    assertEquals(8, code.length)
  }

  @Test
  fun generatedCodesUseOnlyTheAlphabet() {
    val random = Random(42)

    val codes = List(500) { InvitationCodes.generate(random) }

    val forbidden = codes.filter { code -> code.any { it !in InvitationCodes.ALPHABET } }
    assertEquals(emptyList<String>(), forbidden)
  }

  @Test
  fun alphabetHasNoConfusableCharacter() {
    for (c in "0O1IL") {
      assertFalse("'$c' must not be in the alphabet", c in InvitationCodes.ALPHABET)
    }
  }

  @Test
  fun sameSeedGivesSameCode() {
    assertEquals(InvitationCodes.generate(Random(7)), InvitationCodes.generate(Random(7)))
  }

  @Test
  fun normalizeTrimsSpacesAndUppercases() {
    assertEquals("K7M2QXPA", InvitationCodes.normalize("  k7m2qxpa \n"))
  }

  @Test
  fun isValidAcceptsLengthsFromSixToEight() {
    assertTrue(InvitationCodes.isValid("K7M2QX"))
    assertTrue(InvitationCodes.isValid("K7M2QXP"))
    assertTrue(InvitationCodes.isValid("K7M2QXPA"))
  }

  @Test
  fun isValidRejectsWrongLengths() {
    assertFalse(InvitationCodes.isValid(""))
    assertFalse(InvitationCodes.isValid("K7M2Q"))
    assertFalse(InvitationCodes.isValid("K7M2QXPAB"))
  }

  @Test
  fun isValidRejectsForbiddenCharacters() {
    assertFalse("confusable O", InvitationCodes.isValid("K7M2QXPO"))
    assertFalse("confusable 0", InvitationCodes.isValid("K7M2QXP0"))
    assertFalse("lowercase (not normalized)", InvitationCodes.isValid("k7m2qxpa"))
    assertFalse("inner space", InvitationCodes.isValid("K7M2 XPA"))
  }
}
