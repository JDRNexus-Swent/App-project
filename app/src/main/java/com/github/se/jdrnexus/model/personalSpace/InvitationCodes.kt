// Co-authored-by: Claude Sonnet 5.5
package com.github.se.jdrnexus.model.personalSpace

import java.security.SecureRandom
import java.util.Random

/** Generation and validation of invitation codes. Pure logic: no Firestore here. */
object InvitationCodes {
  /** No `0/O`, `1/I`, nor `L` (looks like `1` in many fonts): 31 characters. */
  const val ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"

  /** Length of the generated codes. */
  const val LENGTH = 8

  private const val MIN_ACCEPTED_LENGTH = 6
  private const val MAX_ACCEPTED_LENGTH = 8

  /**
   * Generates a new random invitation code of [LENGTH] characters taken from [ALPHABET].
   *
   * @param random The source of randomness. Production uses a [SecureRandom]; tests pass a seeded
   *   [Random] to get a deterministic result.
   * @return The generated code.
   */
  fun generate(random: Random = SecureRandom()): String =
      String(CharArray(LENGTH) { ALPHABET[random.nextInt(ALPHABET.length)] })

  /**
   * Makes a code typed or pasted by a user comparable with a stored one: surrounding spaces are
   * removed and letters are uppercased.
   *
   * @param raw The code as entered by the user.
   * @return The normalized code. It is not checked: use [isValid] for that.
   */
  fun normalize(raw: String): String = raw.trim().uppercase()

  /**
   * Tells whether [code] can be an invitation code: 6 to 8 characters, all from [ALPHABET]. This is
   * checked before any network call, so a typo costs nothing.
   *
   * @param code The code, already normalized with [normalize].
   * @return `true` if the format is valid. It does not mean the invitation exists.
   */
  fun isValid(code: String): Boolean =
      code.length in MIN_ACCEPTED_LENGTH..MAX_ACCEPTED_LENGTH && code.all { it in ALPHABET }
}
