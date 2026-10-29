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
 * It is called for every container of the result.
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

/** Where an item sits in a group, with its visibility. Used both for items being added and
 * for items reattached after their folder left.
 * */
internal data class GroupPlacement(val id: String, val folderId: String, val isHidden: Boolean)

/** What to write when a folder leaves a group: who leaves, and who stays under a new folder. */
internal data class GroupRemoval(
    val leaving: List<JDRFile>,
    val reattached: List<GroupPlacement>,
)

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
): List<GroupPlacement> {
  val root = subtree.first()
  val rootHidden = hideNew || destinationHidden
  val additions = mutableListOf(GroupPlacement(root.id, destinationFolderId, rootHidden))
  // Whether each item is hidden in the group once the addition is done, by item id.
  val hiddenInGroup = mutableMapOf(root.id to rootHidden)

  subtree.drop(1).forEach { file ->
    val placement = file.placements[groupId]
    if (placement != null) {
      hiddenInGroup[file.id] = placement.isHidden
    } else {
      val hidden = hideNew || hiddenInGroup[file.personalParentId] == true
      hiddenInGroup[file.id] = hidden
      additions += GroupPlacement(file.id, file.personalParentId, hidden)
    }
  }
  return additions
}

/**
 * Decides what to write when [root] leaves the group [groupId].
 *
 * [root] and the items below it that have the same owner, linked to it by an unbroken chain, leave
 * the group.
 * The walk stops at the first item of another owner: it stays in the group, with
 * everything under it, and is reattached to the folder that held [root]. It becomes hidden when
 * [parentHidden] is set, but is never revealed.
 *
 * [childrenOf] tells what a folder contains in the group.
 */
internal suspend fun planRemoval(
    root: JDRFile,
    groupId: String,
    parentHidden: Boolean,
    childrenOf: suspend (JDRFile) -> List<JDRFile>,
): GroupRemoval {
    val parentId = checkNotNull(root.placements[groupId]) { "The item is not in the group." }.folderId

    // What each folder of the walk contains, kept so the reattachments can be worked out afterwards.
    val content = mutableMapOf<String, List<JDRFile>>()

    val leaving =
        subtreeOf(root) { folder ->
            val children = childrenOf(folder)
            content[folder.id] = children
            children.filter { it.ownerId == root.ownerId }
        }

    val reattached =
        leaving
            .filter { it.isContainer() }
            .flatMap { folder -> content.getValue(folder.id) }
            .filter { it.ownerId != root.ownerId }
            .map {
                GroupPlacement(
                    it.id,
                    parentId,
                    parentHidden || it.placements[groupId]?.isHidden == true,
                )
            }
    return GroupRemoval(leaving, reattached)
}

/**
 * Decides what to write in each group when the files of [deleted] are deleted.
 *
 * [deleted] is the deleted folder and everything below it in its owner's personal space. A deleted
 * file that is in a group leaves it. Its place in the group is handled like an unshare
 * ([planRemoval]), so items of other owners are reattached, never deleted.
 *
 * This differs from [planRemoval], which follows the owner: here only what is deleted matters.
 *
 * The result has one entry per group where something leaves. A file of [deleted] appears in
 * `leaving`: the caller does not need to update it, it is deleted anyway.
 */
internal suspend fun planDeletion(
    deleted: List<JDRFile>,
    isParentHidden: suspend (folderId: String, groupId: String) -> Boolean,
    childrenOf: suspend (folder: JDRFile, groupId: String) -> List<JDRFile>,
): Map<String, GroupRemoval> {
  val deletedById = deleted.associateBy { it.id }
  val groupIds = deleted.flatMap { it.placements.keys }.distinct()

  return groupIds.associateWith { groupId ->
    val leaving = deleted.filter { groupId in it.placements }
    val reattached = mutableListOf<GroupPlacement>()

    leaving
        .filter { it.isContainer() }
        .forEach { folder ->
          val parentId = nearestSurvivor(folder, groupId, deletedById)
          val parentHidden = isParentHidden(parentId, groupId)
          childrenOf(folder, groupId)
              .filter { it.id !in deletedById }
              .mapTo(reattached) {
                GroupPlacement(
                    it.id,
                    parentId,
                    parentHidden || it.placements[groupId]?.isHidden == true,
                )
              }
        }
    GroupRemoval(leaving, reattached)
  }
}

/**
 * The folder that holds [file] in the group [groupId], or the nearest ancestor above it that is not
 * in [deletedById]. Falls back to the group root if the data is broken or loops.
 */
private fun nearestSurvivor(
    file: JDRFile,
    groupId: String,
    deletedById: Map<String, JDRFile>,
): String {
  val seen = mutableSetOf(file.id)
  var parentId = file.placements[groupId]?.folderId ?: return groupId
  while (true) {
    val parent = deletedById[parentId] ?: return parentId
    if (!seen.add(parent.id)) return groupId
    parentId = parent.placements[groupId]?.folderId ?: return groupId
  }
}
