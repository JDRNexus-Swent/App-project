// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.github.se.jdrnexus.codec

/** Safe, consistent error for invalid JDR JSON or data that cannot be encoded. */
class JDRFileCodecException(message: String, cause: Throwable) : Exception(message, cause) {
  companion object {
    const val INVALID_FILE_MESSAGE = "Unable to read this file. Check its JSON format."
    const val SERIALIZATION_MESSAGE = "Unable to serialize this file."
  }
}
