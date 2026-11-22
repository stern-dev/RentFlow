package com.example.data.repository

import android.content.Context
import android.net.Uri
import com.example.R
import com.example.data.firebase.OperationType
import com.example.data.firebase.guardFirestoreReads
import com.example.data.firebase.handleFirestoreError
import com.example.domain.model.AppNotification
import com.example.domain.model.AuditLog
import com.example.domain.model.InspectionRoom
import com.example.domain.model.Lease
import com.example.domain.model.MoveInChecklistStatus
import com.example.domain.model.MoveInConditionChecklist
import com.example.domain.model.NotificationType
import com.example.domain.model.RoomInspectionItem
import com.example.ui.media.MaintenancePhotoProcessor
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

/**
 * Dedicated Firestore repository for structured room-by-room Move-In Condition Checklists.
 * Persists grouped rooms, per-item inspection statuses, up to 2 compressed inline photos per item,
 * and dual landlord/tenant remarks under `/move_in_condition_checklists/{checklistId}`.
 */
class MoveInChecklistRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in before accessing move-in condition checklists.")

    fun observeChecklistsForLandlord(userId: String): Flow<List<MoveInConditionChecklist>> {
        val cleanUid = userId.trim()
        if (cleanUid.isBlank()) return flowOf(emptyList())
        return db.collection(COLLECTION_NAME)
            .whereArrayContains("authorizedUids", cleanUid)
            .snapshots()
            .map { snap ->
                snap.toObjects(
                    MoveInConditionChecklist::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedByDescending { it.updatedAt?.seconds ?: (it.inspectionDateEpochMs / 1000L) }
            }
            .guardFirestoreReads(OperationType.LIST, COLLECTION_NAME)
    }

    fun observeChecklistsForTenant(userId: String): Flow<List<MoveInConditionChecklist>> {
        val cleanUid = userId.trim()
        if (cleanUid.isBlank()) return flowOf(emptyList())
        return db.collection(COLLECTION_NAME)
            .whereEqualTo("tenantUid", cleanUid)
            .snapshots()
            .map { snap ->
                snap.toObjects(
                    MoveInConditionChecklist::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedByDescending { it.updatedAt?.seconds ?: (it.inspectionDateEpochMs / 1000L) }
            }
            .guardFirestoreReads(OperationType.LIST, COLLECTION_NAME)
    }

    fun observeChecklistForLease(leaseId: String): Flow<MoveInConditionChecklist?> {
        val cleanLeaseId = leaseId.trim()
        if (cleanLeaseId.isBlank()) return flowOf(null)
        val docId = checklistDocIdForLease(cleanLeaseId)
        val path = "$COLLECTION_NAME/$docId"
        return db.collection(COLLECTION_NAME)
            .document(docId)
            .snapshots()
            .map { snap ->
                if (!snap.exists()) {
                    null
                } else {
                    snap.toObject(
                        MoveInConditionChecklist::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
            }
            .guardFirestoreReads(OperationType.GET, path)
    }

    suspend fun getChecklistById(checklistId: String): Result<MoveInConditionChecklist?> {
        val cleanId = checklistId.trim()
        if (cleanId.isBlank()) return Result.success(null)
        val path = "$COLLECTION_NAME/$cleanId"
        return try {
            requireUserId()
            val snap = db.collection(COLLECTION_NAME).document(cleanId).get().await()
            val checklist = if (snap.exists()) {
                snap.toObject(
                    MoveInConditionChecklist::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            } else {
                null
            }
            Result.success(checklist)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, path)
            Result.failure(e)
        }
    }

    suspend fun getChecklistByLeaseId(leaseId: String): Result<MoveInConditionChecklist?> {
        val cleanLeaseId = leaseId.trim()
        if (cleanLeaseId.isBlank()) return Result.success(null)
        return getChecklistById(checklistDocIdForLease(cleanLeaseId))
    }

    suspend fun compressItemPhotos(context: Context, uris: List<Uri>): List<String> =
        MaintenancePhotoProcessor.compressUrisToDataUris(
            context = context,
            uris = uris,
            maxCount = RoomInspectionItem.MAX_PHOTOS_PER_ITEM
        )

    suspend fun saveChecklist(
        lease: Lease,
        existingChecklist: MoveInConditionChecklist?,
        inspectionDateEpochMs: Long,
        waterMeterReading: String?,
        electricityMeterReading: String?,
        keysHandedOverCount: Long,
        rooms: List<InspectionRoom>,
        landlordSummary: String?,
        submitForTenantSignOff: Boolean,
        actorName: String
    ): Result<String> {
        val docId = existingChecklist?.id?.takeIf { it.isNotBlank() }
            ?: checklistDocIdForLease(lease.id)
        val path = "$COLLECTION_NAME/$docId"
        return try {
            val uid = requireUserId()
            if (existingChecklist?.statusEnum() == MoveInChecklistStatus.ACKNOWLEDGED) {
                return Result.failure(
                    IllegalStateException("Signed-off move-in checklists are permanently locked and cannot be edited.")
                )
            }
            require(lease.id.isNotBlank() && lease.orgId.isNotBlank() && lease.tenantUid.isNotBlank()) {
                "Lease metadata is incomplete for move-in condition checklist creation."
            }

            val cleanRooms = sanitizeAndValidateRooms(
                rooms = rooms,
                preserveTenantRemarksFrom = existingChecklist?.rooms
            ).getOrThrow()

            val nextStatus = if (submitForTenantSignOff) {
                MoveInChecklistStatus.PENDING_TENANT_REVIEW
            } else {
                MoveInChecklistStatus.DRAFT
            }
            val cleanWater = waterMeterReading?.trim()?.take(40)?.takeIf { it.isNotBlank() }
            val cleanPower = electricityMeterReading?.trim()?.take(40)?.takeIf { it.isNotBlank() }
            val cleanSummary = landlordSummary?.trim()?.take(800)?.takeIf { it.isNotBlank() }
            val cleanDateMs = inspectionDateEpochMs.takeIf { it > 0L } ?: System.currentTimeMillis()
            val cleanKeys = keysHandedOverCount.coerceIn(0L, 50L)

            if (existingChecklist == null) {
                val checklist = MoveInConditionChecklist(
                    id = docId,
                    leaseId = lease.id,
                    orgId = lease.orgId,
                    propertyId = lease.propertyId,
                    propertyName = lease.propertyName,
                    unitId = lease.unitId,
                    unitNumber = lease.unitNumber,
                    ownerUid = lease.ownerUid,
                    managerUids = lease.managerUids,
                    authorizedUids = lease.authorizedUids,
                    tenantUid = lease.tenantUid,
                    tenantName = lease.tenantName,
                    status = nextStatus.name,
                    inspectionDateEpochMs = cleanDateMs,
                    waterMeterReading = cleanWater,
                    electricityMeterReading = cleanPower,
                    keysHandedOverCount = cleanKeys,
                    rooms = cleanRooms,
                    landlordSummary = cleanSummary,
                    createdByUid = uid
                )
                db.collection(COLLECTION_NAME).document(docId).set(checklist.toCreateMap()).await()
            } else {
                val updates = hashMapOf<String, Any>(
                    "inspectionDateEpochMs" to cleanDateMs,
                    "waterMeterReading" to (cleanWater ?: FieldValue.delete()),
                    "electricityMeterReading" to (cleanPower ?: FieldValue.delete()),
                    "keysHandedOverCount" to cleanKeys,
                    "rooms" to cleanRooms.map { it.toMap() },
                    "landlordSummary" to (cleanSummary ?: FieldValue.delete()),
                    "status" to nextStatus.name,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                db.collection(COLLECTION_NAME).document(docId).update(updates).await()
            }

            if (nextStatus == MoveInChecklistStatus.PENDING_TENANT_REVIEW && lease.tenantUid.isNotBlank()) {
                val itemCount = cleanRooms.sumOf { it.items.size }
                val notif = AppNotification(
                    recipientUid = lease.tenantUid,
                    senderUid = uid,
                    orgId = lease.orgId,
                    type = NotificationType.LEASE.name,
                    title = "Move-In Condition Checklist Ready: Unit ${lease.unitNumber}",
                    body = "${lease.propertyName} • Unit ${lease.unitNumber}: Inspect ${cleanRooms.size} rooms ($itemCount items) and add remarks or sign off.",
                    relatedEntityId = docId,
                    read = false
                )
                db.collection("notifications").document().set(notif.toCreateMap()).await()
            }

            db.collection("audit_logs").document().set(
                AuditLog(
                    orgId = lease.orgId,
                    actorUid = uid,
                    actorName = actorName,
                    action = if (submitForTenantSignOff) {
                        "SUBMIT_MOVE_IN_CONDITION_CHECKLIST"
                    } else {
                        "SAVE_MOVE_IN_CONDITION_CHECKLIST_DRAFT"
                    },
                    entityType = "MoveInConditionChecklist",
                    entityId = docId,
                    details = "${nextStatus.label} for ${lease.propertyName} Unit ${lease.unitNumber} (${cleanRooms.size} rooms, ${cleanRooms.sumOf { it.items.size }} items)"
                ).toCreateMap()
            ).await()

            Result.success(docId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(
                e,
                if (existingChecklist == null) OperationType.CREATE else OperationType.UPDATE,
                path
            )
            Result.failure(e)
        }
    }

    suspend fun submitTenantReview(
        checklist: MoveInConditionChecklist,
        acknowledge: Boolean,
        tenantRemarksByItemId: Map<String, String>,
        tenantSummary: String?,
        actorName: String
    ): Result<Unit> {
        val path = "$COLLECTION_NAME/${checklist.id}"
        return try {
            val uid = requireUserId()
            if (uid != checklist.tenantUid) {
                return Result.failure(
                    SecurityException("Only the lease tenant can record remarks or sign off on this checklist.")
                )
            }
            if (checklist.statusEnum() != MoveInChecklistStatus.PENDING_TENANT_REVIEW) {
                return Result.failure(
                    IllegalStateException("This move-in condition checklist is not awaiting tenant review.")
                )
            }

            val cleanSummary = tenantSummary?.trim()?.take(800)?.takeIf { it.isNotBlank() }
            if (!acknowledge && (cleanSummary == null || cleanSummary.length < 3)) {
                return Result.failure(
                    IllegalArgumentException("Provide a summary note (at least 3 characters) when flagging discrepancies.")
                )
            }

            val updatedRooms = mergeTenantRemarksPreservingLandlordFields(
                existingRooms = checklist.rooms,
                tenantRemarksByItemId = tenantRemarksByItemId
            )

            val nextStatus = if (acknowledge) {
                MoveInChecklistStatus.ACKNOWLEDGED
            } else {
                MoveInChecklistStatus.DISPUTED
            }

            val updates = hashMapOf<String, Any>(
                "status" to nextStatus.name,
                "rooms" to updatedRooms.map { it.toMap() },
                "tenantSummary" to (cleanSummary ?: FieldValue.delete()),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (acknowledge) {
                updates["acknowledgedAtEpochMs"] = System.currentTimeMillis()
                updates["acknowledgedByUid"] = uid
            }
            db.collection(COLLECTION_NAME).document(checklist.id).update(updates).await()

            checklist.authorizedUids
                .filter { it.isNotBlank() && it != uid }
                .distinct()
                .forEach { recipient ->
                    val notif = AppNotification(
                        recipientUid = recipient,
                        senderUid = uid,
                        orgId = checklist.orgId,
                        type = NotificationType.LEASE.name,
                        title = if (acknowledge) {
                            "Condition Checklist Signed Off: Unit ${checklist.unitNumber}"
                        } else {
                            "Condition Discrepancy Flagged: Unit ${checklist.unitNumber}"
                        },
                        body = if (acknowledge) {
                            "${checklist.tenantName} acknowledged the room-by-room condition checklist for ${checklist.propertyName}."
                        } else {
                            "${checklist.tenantName} flagged items on ${checklist.propertyName}: ${cleanSummary.orEmpty()}"
                        }.take(500),
                        relatedEntityId = checklist.id,
                        read = false
                    )
                    db.collection("notifications").document().set(notif.toCreateMap()).await()
                }

            db.collection("audit_logs").document().set(
                AuditLog(
                    orgId = checklist.orgId,
                    actorUid = uid,
                    actorName = actorName,
                    action = if (acknowledge) {
                        "ACKNOWLEDGE_MOVE_IN_CONDITION_CHECKLIST"
                    } else {
                        "DISPUTE_MOVE_IN_CONDITION_CHECKLIST"
                    },
                    entityType = "MoveInConditionChecklist",
                    entityId = checklist.id,
                    details = "${nextStatus.label} by tenant for ${checklist.propertyName} Unit ${checklist.unitNumber}"
                ).toCreateMap()
            ).await()

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteDraftChecklist(
        checklist: MoveInConditionChecklist,
        actorName: String
    ): Result<Unit> {
        val path = "$COLLECTION_NAME/${checklist.id}"
        return try {
            val uid = requireUserId()
            if (checklist.id.isBlank()) {
                return Result.failure(IllegalArgumentException("Checklist ID is missing."))
            }
            if (checklist.ownerUid != uid) {
                return Result.failure(SecurityException("Only the property owner can delete a draft checklist."))
            }
            if (checklist.statusEnum() != MoveInChecklistStatus.DRAFT) {
                return Result.failure(IllegalStateException("Only DRAFT checklists can be deleted."))
            }

            db.collection(COLLECTION_NAME).document(checklist.id).delete().await()

            db.collection("audit_logs").document().set(
                AuditLog(
                    orgId = checklist.orgId,
                    actorUid = uid,
                    actorName = actorName,
                    action = "DELETE_MOVE_IN_CONDITION_CHECKLIST_DRAFT",
                    entityType = "MoveInConditionChecklist",
                    entityId = checklist.id,
                    details = "Deleted DRAFT condition checklist for ${checklist.propertyName} Unit ${checklist.unitNumber}"
                ).toCreateMap()
            ).await()

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    companion object {
        const val COLLECTION_NAME = "move_in_condition_checklists"
        const val MAX_SINGLE_PHOTO_CHARS = 260_000
        const val MAX_TOTAL_DOCUMENT_PHOTO_CHARS = MaintenancePhotoProcessor.MAX_TOTAL_IMAGE_REFS_CHARS

        fun checklistDocIdForLease(leaseId: String): String =
            "mcc_${leaseId.trim()}".take(120)

        /**
         * Validates room count, item counts, condition status values, per-item compressed inline
         * image ref limits (max 2 per item), and total document photo payload size so documents
         * remain well within Firestore's 1 MiB limit.
         */
        fun sanitizeAndValidateRooms(
            rooms: List<InspectionRoom>,
            preserveTenantRemarksFrom: List<InspectionRoom>? = null
        ): Result<List<InspectionRoom>> {
            if (rooms.isEmpty()) {
                return Result.failure(IllegalArgumentException("Add at least one inspection room."))
            }
            if (rooms.size > MoveInConditionChecklist.MAX_ROOMS) {
                return Result.failure(
                    IllegalArgumentException("A checklist supports up to ${MoveInConditionChecklist.MAX_ROOMS} rooms.")
                )
            }

            val existingTenantRemarksByItemId = preserveTenantRemarksFrom
                ?.flatMap { it.items }
                ?.associate { it.itemId to it.tenantRemarks }
                .orEmpty()

            var totalPhotoChars = 0
            val seenRoomIds = mutableSetOf<String>()
            val seenItemIds = mutableSetOf<String>()

            val sanitizedRooms = rooms.mapIndexed { roomIdx, room ->
                val cleanRoomName = room.roomName.trim().take(60)
                if (cleanRoomName.isBlank()) {
                    return Result.failure(
                        IllegalArgumentException("Room #${roomIdx + 1} needs a room name.")
                    )
                }
                val baseRoomId = room.roomId.trim().ifBlank { "room_${roomIdx + 1}" }.take(64)
                val uniqueRoomId = if (seenRoomIds.add(baseRoomId)) {
                    baseRoomId
                } else {
                    "${baseRoomId.take(56)}_${roomIdx + 1}"
                }

                if (room.items.isEmpty()) {
                    return Result.failure(
                        IllegalArgumentException("Room '$cleanRoomName' needs at least one inspection item.")
                    )
                }
                if (room.items.size > MoveInConditionChecklist.MAX_ITEMS_PER_ROOM) {
                    return Result.failure(
                        IllegalArgumentException(
                            "Room '$cleanRoomName' supports up to ${MoveInConditionChecklist.MAX_ITEMS_PER_ROOM} inspection items."
                        )
                    )
                }

                val sanitizedItems = room.items.mapIndexed { itemIdx, item ->
                    val cleanLabel = item.label.trim().take(100)
                    if (cleanLabel.isBlank()) {
                        return Result.failure(
                            IllegalArgumentException("Item #${itemIdx + 1} in '$cleanRoomName' needs a label.")
                        )
                    }
                    val baseItemId = item.itemId.trim().ifBlank { "${uniqueRoomId}_item_${itemIdx + 1}" }.take(64)
                    val uniqueItemId = if (seenItemIds.add(baseItemId)) {
                        baseItemId
                    } else {
                        "${baseItemId.take(54)}_${roomIdx + 1}_${itemIdx + 1}"
                    }

                    val cleanPhotos = item.imageRefs.map { it.trim() }.filter { it.isNotBlank() }
                    if (cleanPhotos.size > RoomInspectionItem.MAX_PHOTOS_PER_ITEM) {
                        return Result.failure(
                            IllegalArgumentException(
                                "'$cleanLabel' can attach at most ${RoomInspectionItem.MAX_PHOTOS_PER_ITEM} photos."
                            )
                        )
                    }
                    cleanPhotos.forEach { ref ->
                        if (!MaintenancePhotoProcessor.isDataImageUri(ref)) {
                            return Result.failure(
                                IllegalArgumentException(
                                    "Photos for '$cleanLabel' must be compressed inline image data URIs."
                                )
                            )
                        }
                        if (ref.length > MAX_SINGLE_PHOTO_CHARS) {
                            return Result.failure(
                                IllegalArgumentException(
                                    "A photo on '$cleanLabel' exceeds the compressed size limit."
                                )
                            )
                        }
                        totalPhotoChars += ref.length
                    }
                    if (totalPhotoChars > MAX_TOTAL_DOCUMENT_PHOTO_CHARS) {
                        return Result.failure(
                            IllegalArgumentException(
                                "Total inspection photos exceed the Firestore document size budget. Remove or re-compress photos."
                            )
                        )
                    }

                    val preservedTenantRemark = existingTenantRemarksByItemId[uniqueItemId]
                        ?.trim()
                        ?.take(400)
                        ?: item.tenantRemarks.trim().take(400)

                    RoomInspectionItem(
                        itemId = uniqueItemId,
                        label = cleanLabel,
                        status = item.statusEnum().name,
                        imageRefs = cleanPhotos,
                        landlordRemarks = item.landlordRemarks.trim().take(400),
                        tenantRemarks = preservedTenantRemark
                    )
                }

                InspectionRoom(
                    roomId = uniqueRoomId,
                    roomName = cleanRoomName,
                    roomRemarks = room.roomRemarks.trim().take(400),
                    items = sanitizedItems
                )
            }

            return Result.success(sanitizedRooms)
        }

        /**
         * Updates ONLY each item's `tenantRemarks` keyed by `itemId`, preserving landlord-authored
         * room titles, item labels, statuses, photos, and landlordRemarks intact.
         */
        fun mergeTenantRemarksPreservingLandlordFields(
            existingRooms: List<InspectionRoom>,
            tenantRemarksByItemId: Map<String, String>
        ): List<InspectionRoom> = existingRooms.map { room ->
            room.copy(
                items = room.items.map { item ->
                    val updatedRemark = tenantRemarksByItemId[item.itemId]
                        ?.trim()
                        ?.take(400)
                        ?: item.tenantRemarks.trim().take(400)
                    item.copy(tenantRemarks = updatedRemark)
                }
            )
        }
    }
}
