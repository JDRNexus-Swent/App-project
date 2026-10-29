// Co-authored-by: Claude Sonnet 5.5
package com.github.se.jdrnexus.model.personalSpace

/**
 * Rules shared by the operations that move a whole folder in or out of a group.
 *
 * Nothing here reads or writes the database: repositories read the files, ask these functions what
 * to do, then write the result. This keeps the rules testable without Firestore.
 */

/**
 * [root] followed by all its descendants, each listed once.
 *
 * [childrenOf] tells what a folder contains, so the caller decides which tree is walked: the
 * personal space of an owner, or the folders of one group. It is only called for containers.
 *
 * A folder always comes before its content in the result
 */
internal suspend fun subtreeOf(
    root: JDRFile,
    childrenOf: suspend (JDRFile) -> List<JDRFile>,
): List<JDRFile> {
  val collected = linkedMapOf<String, JDRFile>()

  suspend fun visit(file: JDRFile) {
    if (collected.putIfAbsent(file.id, file) != null) return
    if (!file.isContainer()) return
    childrenOf(file).forEach { visit(it) }
  }

  visit(root)
  return collected.values.toList()
}

/** One item to add to a group: the folder that will hold it there, and whether it is hidden. */
internal data class GroupAddition(val id: String, val folderId: String, val isHidden: Boolean)

/**
 * Decides what to write when a folder is added to the group [groupId] together with its content.
 *
 * [subtree] is the folder followed by its content in its owner's personal space.
 *
 * - An item already in the group is not added again: its own content is placed under it.
 * - A new item is hidden when [hideNew] is set, or when the folder holding it is hidden, so a
 *   hidden folder never holds a visible item.
 *
 * The result starts with the folder itself.
 */
internal fun planAddition(
    subtree: List<JDRFile>,
    groupId: String,
    destinationFolderId: String,
    hideNew: Boolean,
    destinationHidden: Boolean,
): List<GroupAddition> {
  val root = subtree.first()
  val rootHidden = hideNew || destinationHidden
  val additions = mutableListOf(GroupAddition(root.id, destinationFolderId, rootHidden))
  // Whether each item is hidden in the group once the addition is done, by item id.
  val hiddenInGroup = mutableMapOf(root.id to rootHidden)

  subtree.drop(1).forEach { file ->
    val placement = file.placements[groupId]
    if (placement != null) {
      hiddenInGroup[file.id] = placement.isHidden
    } else {
      val hidden = hideNew || hiddenInGroup[file.personalParentId] == true
      hiddenInGroup[file.id] = hidden
      additions += GroupAddition(file.id, file.personalParentId, hidden)
    }
  }
  return additions
}
