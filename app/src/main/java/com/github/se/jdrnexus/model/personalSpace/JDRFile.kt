package com.github.se.jdrnexus.model.personalSpace

data class JdrFile(
    val id: String = "",
    val name: String = "",
    val ownerId: String = "",
    // list of campaign which this file is shared
    val sharedWithGroups: List<String> = emptyList(),
    val fileType: DocumentType = DocumentType.TEXT,
    val content: String = "",
)

data class JdrFolder(
    val id: String = "",
    val name: String = "",
    val ownerId: String = "",
    // empty if it's in personal space, else, the id of the group
    val groupId: String = "",
    val parentFolderId: String? = null, // to create folder into folder
    val fileIds: List<String> =
        emptyList(), // all id of file (and not folder id) inside this folder
)

enum class DocumentType {
  TEXT,
  CHARACTER,
}
