// Co-authored-by: Claude Sonnet 5.5
package com.github.se.jdrnexus.model.personalSpace

/**
 * Represents a shared campaign group in JDRNexus.
 *
 * This class acts as the central hub for a campaign, providing a dedicated shared workspace for all
 * participants. It manages the metadata of the group, tracks the Game Master (admin), and holds the
 * list of players to enable real-time synchronization across their devices.
 */
data class CampaignGroup(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val gameMasterId: String = "",
    val participantIds: List<String> = emptyList(),

    // might be interesting to add longitude latitude here when we will do the map
) {
  /**
   * Converts the [CampaignGroup] object into a Map for safe Firestore updates. The [id] is
   * deliberately excluded to prevent accidental overwrites of the document's reference key.
   *
   * @return A map containing the updatable fields of the campaign group.
   */
  fun toMap(): Map<String, Any?> {
    return mapOf(
        "name" to name,
        "description" to description,
        "gameMasterId" to gameMasterId,
        "participantIds" to participantIds,
    )
  }
}
