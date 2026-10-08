package com.github.se.jdrnexus.model.personalSpace

data class JDRFile(
    val id: String = "",
    val name: String = "",
    val ownerId: String = "",

    // list of campaign which this file is shared
    val sharedGroupsIds: List<String> = emptyList(),
    // list of all the group folders that should reference this file
    val parentFolderIds: List<String> = emptyList(),
    val personalParentId: String = "",
    val type: DocumentType = DocumentType.TEXT,
    val content: String = "", // will be empty for a folder
) {
  fun toMap(): Map<String, Any?> {

    return mapOf(
        "name" to name,
        "ownerId" to ownerId,
        "sharedGroupsIds" to sharedGroupsIds,
        "parentFolderIds" to parentFolderIds,
        "personalParentId" to personalParentId,
        "type" to type,
        "content" to content,
    )
  }
}

enum class DocumentType {
  FOLDER,
  TEXT,
  CHARACTER,
}
