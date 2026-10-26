// Co-authored-by: Claude Sonnet 5.5
package com.github.se.jdrnexus.model.personalSpace

/**
 * Keeps the files that [userId] may see in the group [groupId].
 *
 * A file hidden in that group is kept only for the Game Master and for the file's owner. A file
 * hidden in another group is not affected. "Hidden" is a display preference, not a security
 * boundary: hidden files are still downloaded by every member, so never rely on it for secrets.
 */
fun List<JDRFile>.visibleInGroup(
    groupId: String,
    userId: String,
    isGameMaster: Boolean,
): List<JDRFile> = filter {
  isGameMaster || it.ownerId == userId || it.placements[groupId]?.isHidden != true
}
