/** Co-authored-by: Claude Sonnet 5.5 */
package com.github.se.jdrnexus.model.personalSpace

import android.util.Log
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Transaction
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class GroupRepositoryFirestore(private val firestore: FirebaseFirestore) : GroupRepository {

  companion object {
    private const val GROUPS_COLLECTION_PATH = "campaign_groups"
    private const val USERS_COLLECTION_PATH = "users"
    private const val INVITATIONS_COLLECTION_PATH = "invitations"
    private const val REDEMPTIONS_COLLECTION_PATH = "redemptions"
    private const val INVITE_CODE_FIELD = "inviteCode"
    private const val MAX_CODE_ATTEMPTS = 5
  }

  private val groupsCollection = firestore.collection(GROUPS_COLLECTION_PATH)

  // Created on first use, so code that never touches files does not need this collection.
  private val documentsCollection by lazy {
    firestore.collection(WorkspaceRepositoryFirestore.COLLECTION_PATH)
  }

  // Created on first use, like documentsCollection.
  private val usersCollection by lazy { firestore.collection(USERS_COLLECTION_PATH) }

  // Created on first use, like the other collections.
  private val invitationsCollection by lazy { firestore.collection(INVITATIONS_COLLECTION_PATH) }

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
    val group =
        groupsCollection.document(groupId).get().await().toObject(CampaignGroup::class.java)
            ?: return@safeFirestoreRequest
    val batch = firestore.batch()
    batch.delete(groupsCollection.document(groupId))
    batch.delete(documentsCollection.document(groupId)) // the group's root folder

    group.participantIds.forEach { userId ->
      batch.update(
          usersCollection.document(userId),
          "groupIds",
          com.google.firebase.firestore.FieldValue.arrayRemove(groupId),
      )
    }
    batch.commit().await()
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

  // ==========================================
  // GROUPS INVITATION
  // ==========================================
  override suspend fun createInvitation(groupId: String, gameMasterId: String): Result<String> =
      invitationRequest(onPermissionDenied = InvitationError.NOT_GAME_MASTER) {
        if (gameMasterId.isBlank()) throw InvitationException(InvitationError.NOT_SIGNED_IN)
        // document("") would throw an IllegalArgumentException, so a blank id is handled here.
        if (groupId.isBlank()) throw InvitationException(InvitationError.GROUP_NOT_FOUND)

        val groupRef = groupsCollection.document(groupId)

        firestore
            .runTransaction { transaction ->
              val group = transaction.get(groupRef)
              requireGameMaster(group, gameMasterId)

              // A code already exists: return it and write nothing
              val existing = group.getString(INVITE_CODE_FIELD)
              if (!existing.isNullOrBlank()) return@runTransaction existing

              val code = freeInvitationCode(transaction)
              transaction.set(invitationRef(code), invitationData(groupId, gameMasterId))
              transaction.update(groupRef, INVITE_CODE_FIELD, code)
              code
            }
            .await()
      }

  override suspend fun regenerateInvitation(
      groupId: String,
      gameMasterId: String,
  ): Result<String> =
      invitationRequest(onPermissionDenied = InvitationError.NOT_GAME_MASTER) {
        if (gameMasterId.isBlank()) throw InvitationException(InvitationError.NOT_SIGNED_IN)
        if (groupId.isBlank()) throw InvitationException(InvitationError.GROUP_NOT_FOUND)

        val groupRef = groupsCollection.document(groupId)
        val group = groupRef.get().await()
        requireGameMaster(group, gameMasterId)

        val newCode = freeInvitationCode() ?: throw InvitationException(InvitationError.NETWORK)
        val oldCode = group.getString(INVITE_CODE_FIELD)

        val batch = firestore.batch()
        if (!oldCode.isNullOrBlank()) batch.delete(invitationRef(oldCode))
        batch.set(invitationRef(newCode), invitationData(groupId, gameMasterId))
        batch.update(groupRef, INVITE_CODE_FIELD, newCode)
        batch.commit().await()
        newCode
      }

  override suspend fun joinGroupWithCode(code: String, userId: String): Result<CampaignGroup> =
      invitationRequest(onPermissionDenied = InvitationError.NETWORK) {
        if (userId.isBlank()) throw InvitationException(InvitationError.NOT_SIGNED_IN)

        val normalized = InvitationCodes.normalize(code)
        if (!InvitationCodes.isValid(normalized)) {
          throw InvitationException(InvitationError.INVALID_CODE)
        }

        val invitation = invitationRef(normalized).get().await()
        val groupId = invitation.getString("groupId")
        if (!invitation.exists() || groupId.isNullOrBlank()) {
          throw InvitationException(InvitationError.UNKNOWN_CODE)
        }

        val userDoc = usersCollection.document(userId).get().await()
        val memberOf = (userDoc.get("groupIds") as? List<*>) ?: emptyList<Any>()
        if (groupId in memberOf) throw InvitationException(InvitationError.ALREADY_MEMBER)

        val batch = firestore.batch()
        batch.set(
            invitationRef(normalized).collection(REDEMPTIONS_COLLECTION_PATH).document(userId),
            mapOf("groupId" to groupId),
        )
        batch.update(
            groupsCollection.document(groupId),
            "participantIds",
            FieldValue.arrayUnion(userId),
        )
        batch.set(
            usersCollection.document(userId),
            mapOf("groupIds" to FieldValue.arrayUnion(groupId)),
            SetOptions.merge(),
        )
        batch.commit().await()

        getGroup(groupId).getOrElse { throw InvitationException(InvitationError.NETWORK) }
      }

  private suspend fun <T> invitationRequest(
      onPermissionDenied: InvitationError,
      block: suspend () -> T,
  ): Result<T> =
      try {
        Result.success(block())
      } catch (e: CancellationException) {
        throw e // Cancelling a coroutine is not a failure of the operation.
      } catch (e: InvitationException) {
        Result.failure(e)
      } catch (e: FirebaseFirestoreException) {
        val error =
            when (e.code) {
              FirebaseFirestoreException.Code.PERMISSION_DENIED -> onPermissionDenied
              FirebaseFirestoreException.Code.NOT_FOUND -> InvitationError.GROUP_NOT_FOUND
              else -> InvitationError.NETWORK
            }
        Result.failure(InvitationException(error))
      } catch (e: Exception) {
        Result.failure(InvitationException(InvitationError.NETWORK))
      }

  private fun invitationRef(code: String) = invitationsCollection.document(code)

  /** The invitation document: `{groupId, createdBy}`, no empty or null field. */
  private fun invitationData(groupId: String, createdBy: String) =
      mapOf("groupId" to groupId, "createdBy" to createdBy)

  /** Fails with GROUP_NOT_FOUND or NOT_GAME_MASTER unless [group] exists and [userId] runs it. */
  private fun requireGameMaster(group: DocumentSnapshot, userId: String) {
    if (!group.exists()) throw InvitationException(InvitationError.GROUP_NOT_FOUND)
    if (group.getString("gameMasterId") != userId) {
      throw InvitationException(InvitationError.NOT_GAME_MASTER)
    }
  }

  /**
   * Returns a code that no invitation uses yet, or null after [MAX_CODE_ATTEMPTS] collisions A
   * collision matters because writing an existing code would redirect another group's invitation.
   */
  private suspend fun freeInvitationCode(): String? {
    repeat(MAX_CODE_ATTEMPTS) {
      val code = InvitationCodes.generate()
      if (!invitationRef(code).get().await().exists()) return code
    }
    return null
  }

  /**
   * Same idea as [freeInvitationCode], but reads through [transaction] so the read is tracked: if
   * another write takes the candidate code before the commit, Firestore replays the transaction.
   */
  private fun freeInvitationCode(transaction: Transaction): String {
    repeat(MAX_CODE_ATTEMPTS) {
      val code = InvitationCodes.generate()
      if (!transaction.get(invitationRef(code)).exists()) return code
    }
    throw InvitationException(InvitationError.NETWORK)
  }
}
