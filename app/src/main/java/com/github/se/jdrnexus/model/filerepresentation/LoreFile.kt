// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.github.se.jdrnexus.model.filerepresentation

/** Lore data, with sections in display order. */
data class LoreFile(val title: String, val sections: List<LoreSection>)

/** Lore section with a stable ID, title, and description. */
data class LoreSection(val sectionID: String, val title: String, val description: String)
