package com.github.se.jdrnexus.model.personalSpace

data class JDRFile(
    val id: String = "",
    val name: String = "",
    val ownerId: String = "",

    // list of campaign which this file is shared
    val sharedGroupsIds: List<String> = emptyList(),
    // list of all the  folders that should reference this file
    val parentFolderIds: List<String> = emptyList(),
    val type: DocumentType = DocumentType.TEXT,
    val content: String = "", // will be empty for a folder
)

enum class DocumentType {
  FOLDER,
  TEXT,
  CHARACTER,
}
