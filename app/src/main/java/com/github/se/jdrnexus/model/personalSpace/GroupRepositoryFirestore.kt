/** Co-authored-by: Claude Sonnet 5.5 */
package com.github.se.jdrnexus.model.personalSpace

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class GroupRepositoryFirestore(private val firestore: FirebaseFirestore) : GroupRepository {

  companion object {
    private const val GROUPS_COLLECTION_PATH = "campaign_groups"
    private const val USERS_COLLECTION_PATH = "users"
  }

  private val groupsCollection = firestore.collection(GROUPS_COLLECTION_PATH)

  // Created on first use, so code that never touches files does not need this collection.
  private val documentsCollection by lazy {
    firestore.collection(WorkspaceRepositoryFirestore.COLLECTION_PATH)
  }

  // Created on first use, like documentsCollection.
  private val usersCollection by lazy { firestore.collection(USERS_COLLECTION_PATH) }

  // ==========================================
  // GROUP MANAGEMENT
  // ==========================================

  override fun getNewGroupId(): String {
    return groupsCollection.document().id
  }

  override suspend fun createGroup(group: CampaignGroup): Result<Unit> = safeFirestoreRequest {
    // The group, its root folder and the GM's membership are written together: all or nothing.
    val batch = firestore.batch()
    batch.set(groupsCollection.document(group.id), group)
    batch.set(documentsCollection.document(group.id), rootFileOf(group))
    batch.set(
        usersCollection.document(group.gameMasterId),
        mapOf("groupIds" to com.google.firebase.firestore.FieldValue.arrayUnion(group.id)),
        com.google.firebase.firestore.SetOptions.merge(),
    )
    batch.commit().await()
  }

  override suspend fun updateGroup(group: CampaignGroup): Result<Unit> = safeFirestoreRequest {
    // The root folder shows the group's name, so both are updated in the same batch.
    val batch = firestore.batch()
    batch.update(groupsCollection.document(group.id), group.toMap())
    batch.update(documentsCollection.document(group.id), mapOf("name" to group.name))
    batch.commit().await()
  }

  override suspend fun deleteGroup(groupId: String): Result<Unit> = safeFirestoreRequest {
    groupsCollection.document(groupId).delete().await()
  }

  override suspend fun getGroup(groupId: String): Result<CampaignGroup> = safeFirestoreRequest {
    val documentSnapshot = groupsCollection.document(groupId).get().await()

    if (!documentSnapshot.exists()) {
      throw Exception("CampaignGroup not found in Firestore")
    }

    val group = documentSnapshot.toObject(CampaignGroup::class.java)
    group ?: throw Exception("Failed to parse CampaignGroup")
  }

  override suspend fun addUserToGroup(groupId: String, userId: String): Result<Unit> =
      safeFirestoreRequest {
        // The group and the user's own list of groups change together (see the security rules).
        val batch = firestore.batch()
        // Use FieldValue.arrayUnion to safely add an item to an array without duplicates
        batch.update(
            groupsCollection.document(groupId),
            "participantIds",
            com.google.firebase.firestore.FieldValue.arrayUnion(userId),
        )
        batch.set(
            usersCollection.document(userId),
            mapOf("groupIds" to com.google.firebase.firestore.FieldValue.arrayUnion(groupId)),
            com.google.firebase.firestore.SetOptions.merge(),
        )
        batch.commit().await()
      }

  override suspend fun removeUserFromGroup(groupId: String, userId: String): Result<Unit> =
      safeFirestoreRequest {
        // The group and the user's own list of groups change together (see the security rules).
        val batch = firestore.batch()
        // Use FieldValue.arrayRemove to safely remove an item from an array
        batch.update(
            groupsCollection.document(groupId),
            "participantIds",
            com.google.firebase.firestore.FieldValue.arrayRemove(userId),
        )
        batch.set(
            usersCollection.document(userId),
            mapOf("groupIds" to com.google.firebase.firestore.FieldValue.arrayRemove(groupId)),
            com.google.firebase.firestore.SetOptions.merge(),
        )
        batch.commit().await()
      }

  /** Create the hidden root folder of [group]: same id, same name, shared with the group itself. */
  private fun rootFileOf(group: CampaignGroup): JDRFile =
      JDRFile(
          id = group.id,
          name = group.name,
          ownerId = group.gameMasterId,
          sharedGroupsIds = listOf(group.id),
          type = DocumentType.GROUP_ROOT,
      )

  // ==========================================
  // REAL-TIME SYNCHRONIZATION (Flows) for GROUPS
  // ==========================================

  override fun getGroupFlow(groupId: String): Flow<CampaignGroup?> = callbackFlow {
    val listenerRegistration =
        groupsCollection.document(groupId).addSnapshotListener { snapshot, error ->
          if (error != null) {
            close(error)
            return@addSnapshotListener
          }
          if (snapshot != null) {
            trySend(snapshot.toObject(CampaignGroup::class.java))
          } else {
            trySend(null)
          }
        }
    awaitClose { listenerRegistration.remove() }
  }

  override fun getUserGroups(userId: String): Flow<List<CampaignGroup>> = callbackFlow {
    // Listen for changes on all campaign groups where this user is a participant.
    val listenerRegistration =
        groupsCollection.whereArrayContains("participantIds", userId).addSnapshotListener {
            snapshot,
            error ->
          if (error != null) {
            close(error)
            return@addSnapshotListener
          }

          if (snapshot != null) {
            val groups = mutableListOf<CampaignGroup>()

            for (document in snapshot.documents) {
              try {
                val group = document.toObject(CampaignGroup::class.java)
                if (group != null) {
                  groups.add(group)
                }
              } catch (e: Exception) {
                Log.e(
                    "GroupRepository",
                    "Error in the deserialization of campaign group doc ${document.id}",
                    e,
                )
              }
            }
            trySend(groups)
          }
        }

    awaitClose { listenerRegistration.remove() }
  }
}
