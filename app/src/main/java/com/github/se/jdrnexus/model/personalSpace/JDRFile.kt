// Co-authored-by: Claude Sonnet 5.5
package com.github.se.jdrnexus.model.personalSpace

import com.google.firebase.firestore.PropertyName

/** Where a file sits inside one group: the container holding it, and whether it is hidden. */
data class Placement(
    val folderId: String = "",
    // Without the annotation, Firestore would store this Kotlin property as "hidden".
    @get:PropertyName("isHidden") val isHidden: Boolean = false,
)

/**
 * The fields of a file that [WorkspaceRepository.updateFile] can change. Group sharing is not part
 * of it on purpose: it only changes through [WorkspaceRepository.addItemToGroup],
 * [WorkspaceRepository.removeItemFromGroup] and [WorkspaceRepository.setItemHidden]. Moving a file
 * in the personal space is not editable here either, it will need its own operation. [id] only
 * tells which document to update: [toMap] never writes it.
 */
data class FileEdit(
    val id: String,
    val name: String,
    val content: String,
) {
  fun toMap(): Map<String, Any> =
      mapOf(
          "name" to name,
          "content" to content,
      )
}

const val PERSONAL_ROOT_ID = ""

data class JDRFile(
    val id: String = "",
    val name: String = "",
    val ownerId: String = "",

    // list of campaign which this file is shared
    val sharedGroupsIds: List<String> = emptyList(),
    // groupId -> placement of this file in that group (at most one per group)
    val placements: Map<String, Placement> = emptyMap(),
    val personalParentId: String = PERSONAL_ROOT_ID,
    val type: DocumentType = DocumentType.TEXT,
    val content: String = "", // will be empty for a folder
) {
  fun toMap(): Map<String, Any?> {
    return mapOf(
        "name" to name,
        "ownerId" to ownerId,
        "sharedGroupsIds" to sharedGroupsIds,
        "placements" to placements,
        "personalParentId" to personalParentId,
        "type" to type,
        "content" to content,
    )
  }

  fun withPlacement(groupId: String, placement: Placement): JDRFile {
    require(type != DocumentType.GROUP_ROOT)
    val newPlacements = placements + (groupId to placement)
    val newSharedGroupsIds =
        if (groupId !in sharedGroupsIds) sharedGroupsIds + groupId else sharedGroupsIds
    return copy(
        placements = newPlacements,
        sharedGroupsIds = newSharedGroupsIds,
    )
  }

  fun withoutPlacement(groupId: String): JDRFile {
    require(type != DocumentType.GROUP_ROOT)
    val newPlacements = placements - groupId
    val newSharedGroupsIds = sharedGroupsIds - groupId
    return copy(
        placements = newPlacements,
        sharedGroupsIds = newSharedGroupsIds,
    )
  }
}

enum class DocumentType {
  FOLDER,
  TEXT,
  CHARACTER,
  GROUP_ROOT;

  /** Whether files of this type can hold other files. */
  fun isContainer(): Boolean = this == FOLDER || this == GROUP_ROOT
}

/**
 * Whether this file can hold other files. Kept outside the class so Firestore's POJO mapper does
 * not mistake it for a stored property.
 */
fun JDRFile.isContainer(): Boolean = type.isContainer()

/** The editable part of this file, for [WorkspaceRepository.updateFile]. */
fun JDRFile.toEdit() = FileEdit(id, name, content)
