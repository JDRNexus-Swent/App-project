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

  /** [random] is injectable so tests are deterministic; production uses a [SecureRandom]. */
  fun generate(random: Random = SecureRandom()): String =
      String(CharArray(LENGTH) { ALPHABET[random.nextInt(ALPHABET.length)] })

  fun normalize(raw: String): String = raw.trim().uppercase()

  fun isValid(code: String): Boolean =
      code.length in MIN_ACCEPTED_LENGTH..MAX_ACCEPTED_LENGTH && code.all { it in ALPHABET }
}
