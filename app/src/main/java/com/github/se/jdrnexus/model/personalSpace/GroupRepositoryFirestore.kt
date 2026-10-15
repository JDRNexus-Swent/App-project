/** Co-authored-by: AI Agent */
package com.github.se.jdrnexus.model.personalSpace

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class GroupRepositoryFirestore(private val firestore: FirebaseFirestore) : GroupRepository {

  companion object {
    private const val GROUPS_COLLECTION_PATH = "campaign_groups"
  }

  private val groupsCollection = firestore.collection(GROUPS_COLLECTION_PATH)

  // ==========================================
  // GROUP MANAGEMENT
  // ==========================================

  override fun getNewGroupId(): String {
    return groupsCollection.document().id
  }

  override suspend fun createGroup(group: CampaignGroup): Result<Unit> {
    return try {
      groupsCollection.document(group.id).set(group).await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  override suspend fun updateGroup(group: CampaignGroup): Result<Unit> {
    return try {
      groupsCollection.document(group.id).update(group.toMap()).await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  override suspend fun deleteGroup(groupId: String): Result<Unit> {
    return try {
      groupsCollection.document(groupId).delete().await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  override suspend fun getGroup(groupId: String): Result<CampaignGroup> {
    return try {
      val documentSnapshot = groupsCollection.document(groupId).get().await()

      if (!documentSnapshot.exists()) {
        return Result.failure(Exception("CampaignGroup not found in Firestore"))
      }

      val group = documentSnapshot.toObject(CampaignGroup::class.java)
      if (group != null) {
        Result.success(group)
      } else {
        Result.failure(Exception("Failed to parse CampaignGroup"))
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  override suspend fun addUserToGroup(groupId: String, userId: String): Result<Unit> {
    return try {
      // Use FieldValue.arrayUnion to safely add an item to an array without duplicates
      groupsCollection
          .document(groupId)
          .update("participantIds", com.google.firebase.firestore.FieldValue.arrayUnion(userId))
          .await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  override suspend fun removeUserFromGroup(groupId: String, userId: String): Result<Unit> {
    return try {
      // Use FieldValue.arrayRemove to safely remove an item from an array
      groupsCollection
          .document(groupId)
          .update("participantIds", com.google.firebase.firestore.FieldValue.arrayRemove(userId))
          .await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

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
