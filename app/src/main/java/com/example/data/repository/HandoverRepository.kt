package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.firebase.OperationType
import com.example.data.firebase.guardFirestoreReads
import com.example.data.firebase.handleFirestoreError
import com.example.domain.finance.CurrencyUtils
import com.example.domain.finance.FinancialEngine
import com.example.domain.model.AppNotification
import com.example.domain.model.AuditLog
import com.example.domain.model.ChecklistItem
import com.example.domain.model.DepositDeductionItem
import com.example.domain.model.DepositSettlement
import com.example.domain.model.DepositSettlementStatus
import com.example.domain.model.Lease
import com.example.domain.model.MoveInChecklist
import com.example.domain.model.MoveInChecklistStatus
import com.example.domain.model.NotificationType
import com.example.domain.model.RefundMethod
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

class HandoverRepository(
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
            ?: throw IllegalStateException("User must be signed in before performing this action.")

    fun observeChecklistsForLandlord(userId: String): Flow<List<MoveInChecklist>> {
        val cleanUid = userId.trim()
        if (cleanUid.isBlank()) return flowOf(emptyList())
        return db.collection("move_in_checklists")
            .whereArrayContains("authorizedUids", cleanUid)
            .snapshots()
            .map { snap ->
                snap.toObjects(MoveInChecklist::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.updatedAt?.seconds ?: it.inspectionDateEpochMs / 1000L }
            }
            .guardFirestoreReads(OperationType.LIST, "move_in_checklists")
    }

    fun observeChecklistsForTenant(userId: String): Flow<List<MoveInChecklist>> {
        val cleanUid = userId.trim()
        if (cleanUid.isBlank()) return flowOf(emptyList())
        return db.collection("move_in_checklists")
            .whereEqualTo("tenantUid", cleanUid)
            .snapshots()
            .map { snap ->
                snap.toObjects(MoveInChecklist::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.updatedAt?.seconds ?: it.inspectionDateEpochMs / 1000L }
            }
            .guardFirestoreReads(OperationType.LIST, "move_in_checklists")
    }

    fun observeSettlementsForLandlord(userId: String): Flow<List<DepositSettlement>> {
        val cleanUid = userId.trim()
        if (cleanUid.isBlank()) return flowOf(emptyList())
        return db.collection("deposit_settlements")
            .whereArrayContains("authorizedUids", cleanUid)
            .snapshots()
            .map { snap ->
                snap.toObjects(DepositSettlement::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.updatedAt?.seconds ?: it.moveOutDateEpochMs / 1000L }
            }
            .guardFirestoreReads(OperationType.LIST, "deposit_settlements")
    }

    fun observeSettlementsForTenant(userId: String): Flow<List<DepositSettlement>> {
        val cleanUid = userId.trim()
        if (cleanUid.isBlank()) return flowOf(emptyList())
        return db.collection("deposit_settlements")
            .whereEqualTo("tenantUid", cleanUid)
            .snapshots()
            .map { snap ->
                snap.toObjects(DepositSettlement::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.updatedAt?.seconds ?: it.moveOutDateEpochMs / 1000L }
            }
            .guardFirestoreReads(OperationType.LIST, "deposit_settlements")
    }

    suspend fun saveMoveInChecklist(
        lease: Lease,
        existingChecklist: MoveInChecklist?,
        inspectionDateEpochMs: Long,
        waterMeterReading: String?,
        electricityMeterReading: String?,
        keysHandedOverCount: Long,
        items: List<ChecklistItem>,
        imageRefs: List<String>,
        landlordNotes: String?,
        submitForTenantSignOff: Boolean,
        actorName: String
    ): Result<String> {
        val docId = existingChecklist?.id?.takeIf { it.isNotBlank() } ?: "cl_${lease.id}".take(120)
        val path = "move_in_checklists/$docId"
        return try {
            val uid = requireUserId()
            require(items.isNotEmpty()) { "Add at least one room or fixture inspection item." }
            require(items.size <= 30) { "A move-in checklist supports up to 30 inspection items." }
            val validatedImages = MaintenancePhotoProcessor.validateImageRefsPayload(imageRefs).getOrThrow()
            if (existingChecklist?.statusEnum() == MoveInChecklistStatus.ACKNOWLEDGED) {
                return Result.failure(IllegalStateException("Signed-off move-in checklists are locked and cannot be edited."))
            }

            val cleanItems = items.mapIndexed { idx, item ->
                val cleanLabel = item.label.trim().take(100)
                val cleanArea = item.area.trim().ifBlank { "General" }.take(60)
                require(cleanLabel.isNotBlank()) { "Inspection item #${idx + 1} needs a label." }
                item.copy(
                    itemId = item.itemId.ifBlank { "item_${idx + 1}" }.take(64),
                    area = cleanArea,
                    label = cleanLabel,
                    notes = item.notes.trim().take(300)
                )
            }
            val nextStatus = if (submitForTenantSignOff) {
                MoveInChecklistStatus.PENDING_TENANT_REVIEW
            } else {
                MoveInChecklistStatus.DRAFT
            }
            val cleanWater = waterMeterReading?.trim()?.take(40)?.takeIf { it.isNotBlank() }
            val cleanPower = electricityMeterReading?.trim()?.take(40)?.takeIf { it.isNotBlank() }
            val cleanNotes = landlordNotes?.trim()?.take(800)?.takeIf { it.isNotBlank() }
            val cleanDateMs = inspectionDateEpochMs.takeIf { it > 0L } ?: System.currentTimeMillis()
            val cleanKeys = keysHandedOverCount.coerceIn(0L, 50L)

            if (existingChecklist == null) {
                val checklist = MoveInChecklist(
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
                    items = cleanItems,
                    imageRefs = validatedImages,
                    landlordNotes = cleanNotes,
                    createdByUid = uid
                )
                db.collection("move_in_checklists").document(docId).set(checklist.toCreateMap()).await()
            } else {
                val updates = hashMapOf<String, Any>(
                    "inspectionDateEpochMs" to cleanDateMs,
                    "waterMeterReading" to (cleanWater ?: FieldValue.delete()),
                    "electricityMeterReading" to (cleanPower ?: FieldValue.delete()),
                    "keysHandedOverCount" to cleanKeys,
                    "items" to cleanItems.map { it.toMap() },
                    "imageRefs" to validatedImages,
                    "landlordNotes" to (cleanNotes ?: FieldValue.delete()),
                    "status" to nextStatus.name,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                db.collection("move_in_checklists").document(docId).update(updates).await()
            }

            if (nextStatus == MoveInChecklistStatus.PENDING_TENANT_REVIEW && lease.tenantUid.isNotBlank()) {
                val notif = AppNotification(
                    recipientUid = lease.tenantUid,
                    senderUid = uid,
                    orgId = lease.orgId,
                    type = NotificationType.LEASE.name,
                    title = "Move-In Checklist Ready: Unit ${lease.unitNumber}",
                    body = "${lease.propertyName} • Unit ${lease.unitNumber}: Review your move-in condition checklist and sign off.",
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
                    action = if (submitForTenantSignOff) "SUBMIT_MOVE_IN_CHECKLIST" else "SAVE_MOVE_IN_CHECKLIST_DRAFT",
                    entityType = "MoveInChecklist",
                    entityId = docId,
                    details = "${nextStatus.label} for ${lease.propertyName} Unit ${lease.unitNumber} (${cleanItems.size} items)"
                ).toCreateMap()
            ).await()

            Result.success(docId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, if (existingChecklist == null) OperationType.CREATE else OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun respondToMoveInChecklist(
        checklist: MoveInChecklist,
        acknowledge: Boolean,
        responseNote: String?,
        actorName: String
    ): Result<Unit> {
        val path = "move_in_checklists/${checklist.id}"
        return try {
            val uid = requireUserId()
            if (uid != checklist.tenantUid) {
                return Result.failure(SecurityException("Only the lease tenant can sign off or flag this move-in checklist."))
            }
            if (checklist.statusEnum() != MoveInChecklistStatus.PENDING_TENANT_REVIEW) {
                return Result.failure(IllegalStateException("This checklist is not awaiting tenant sign-off."))
            }
            val cleanNote = responseNote?.trim()?.take(800)?.takeIf { it.isNotBlank() }
            if (!acknowledge && (cleanNote == null || cleanNote.length < 3)) {
                return Result.failure(IllegalArgumentException("Describe the discrepancy before flagging this checklist."))
            }

            val nextStatus = if (acknowledge) MoveInChecklistStatus.ACKNOWLEDGED else MoveInChecklistStatus.DISPUTED
            val updates = hashMapOf<String, Any>(
                "status" to nextStatus.name,
                "tenantResponseNote" to (cleanNote ?: FieldValue.delete()),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (acknowledge) {
                updates["acknowledgedAtEpochMs"] = System.currentTimeMillis()
                updates["acknowledgedByUid"] = uid
            }
            db.collection("move_in_checklists").document(checklist.id).update(updates).await()

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
                            "Checklist Signed Off: Unit ${checklist.unitNumber}"
                        } else {
                            "Checklist Discrepancy Flagged: Unit ${checklist.unitNumber}"
                        },
                        body = if (acknowledge) {
                            "${checklist.tenantName} acknowledged the move-in checklist for ${checklist.propertyName}."
                        } else {
                            "${checklist.tenantName} flagged items on ${checklist.propertyName}: ${cleanNote.orEmpty()}"
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
                    action = if (acknowledge) "ACKNOWLEDGE_MOVE_IN_CHECKLIST" else "DISPUTE_MOVE_IN_CHECKLIST",
                    entityType = "MoveInChecklist",
                    entityId = checklist.id,
                    details = "${nextStatus.label} by ${checklist.tenantName} for Unit ${checklist.unitNumber}"
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

    suspend fun saveDepositSettlement(
        lease: Lease,
        existingSettlement: DepositSettlement?,
        depositHeldCents: Long,
        arrearsIncludedCents: Long,
        creditAppliedCents: Long,
        deductions: List<DepositDeductionItem>,
        moveOutDateEpochMs: Long,
        waterMeterFinal: String?,
        electricityMeterFinal: String?,
        keysReturnedCount: Long,
        imageRefs: List<String>,
        landlordNotes: String?,
        targetStatus: DepositSettlementStatus,
        refundMethod: RefundMethod,
        refundReferenceCode: String?,
        actorName: String
    ): Result<String> {
        val docId = existingSettlement?.id?.takeIf { it.isNotBlank() } ?: "ds_${lease.id}".take(120)
        val path = "deposit_settlements/$docId"
        return try {
            val uid = requireUserId()
            if (existingSettlement?.statusEnum() == DepositSettlementStatus.SETTLED) {
                return Result.failure(IllegalStateException("Settled deposit statements are locked and immutable."))
            }
            require(deductions.size <= 30) { "Up to 30 itemized deductions are supported per settlement." }
            val validatedImages = MaintenancePhotoProcessor.validateImageRefsPayload(imageRefs).getOrThrow()

            val cleanDeductions = deductions.mapIndexed { idx, item ->
                val desc = item.description.trim().take(200)
                require(desc.isNotBlank()) { "Deduction #${idx + 1} requires a description." }
                require(item.amountCents >= 0L) { "Deduction amounts cannot be negative." }
                item.copy(
                    id = item.id.ifBlank { "ded_${idx + 1}" }.take(64),
                    description = desc,
                    amountCents = item.amountCents
                )
            }

            val recon = FinancialEngine.computeDepositReconciliation(
                depositHeldCents = depositHeldCents,
                creditAppliedCents = creditAppliedCents,
                arrearsIncludedCents = arrearsIncludedCents,
                deductions = cleanDeductions
            )

            val cleanRef = refundReferenceCode?.trim()?.uppercase()?.take(60)?.takeIf { it.isNotBlank() }
            val effectiveMethod = if (recon.netRefundCents == 0L && targetStatus == DepositSettlementStatus.SETTLED) {
                RefundMethod.NONE
            } else {
                refundMethod
            }

            if (targetStatus == DepositSettlementStatus.SETTLED) {
                if (existingSettlement == null) {
                    return Result.failure(IllegalStateException("Issue the deposit statement to the tenant before marking refund settled."))
                }
                if (recon.netRefundCents > 0L) {
                    require(effectiveMethod != RefundMethod.NONE) { "Select a refund payout method (M-Pesa, Bank, or Cash)." }
                    require(cleanRef != null && cleanRef.length >= 3) { "Enter a valid payout reference code before settling." }
                }
            }

            val effectiveCreateStatus = if (existingSettlement == null && targetStatus == DepositSettlementStatus.SETTLED) {
                DepositSettlementStatus.ISSUED
            } else {
                targetStatus
            }

            val cleanWater = waterMeterFinal?.trim()?.take(40)?.takeIf { it.isNotBlank() }
            val cleanPower = electricityMeterFinal?.trim()?.take(40)?.takeIf { it.isNotBlank() }
            val cleanNotes = landlordNotes?.trim()?.take(800)?.takeIf { it.isNotBlank() }
            val cleanMoveOutMs = moveOutDateEpochMs.takeIf { it > 0L } ?: System.currentTimeMillis()
            val cleanKeys = keysReturnedCount.coerceIn(0L, 50L)

            if (existingSettlement == null) {
                val settlement = DepositSettlement(
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
                    depositHeldCents = recon.depositHeldCents,
                    arrearsIncludedCents = recon.arrearsIncludedCents,
                    creditAppliedCents = recon.creditAppliedCents,
                    deductions = cleanDeductions,
                    deductionsTotalCents = recon.deductionsTotalCents,
                    netRefundCents = recon.netRefundCents,
                    balanceOwedCents = recon.balanceOwedCents,
                    moveOutDateEpochMs = cleanMoveOutMs,
                    waterMeterFinal = cleanWater,
                    electricityMeterFinal = cleanPower,
                    keysReturnedCount = cleanKeys,
                    imageRefs = validatedImages,
                    status = effectiveCreateStatus.name,
                    landlordNotes = cleanNotes,
                    refundMethod = effectiveMethod.name,
                    refundReferenceCode = cleanRef,
                    createdByUid = uid
                )
                db.collection("deposit_settlements").document(docId).set(settlement.toCreateMap()).await()
            } else {
                val updates = hashMapOf<String, Any>(
                    "depositHeldCents" to recon.depositHeldCents,
                    "arrearsIncludedCents" to recon.arrearsIncludedCents,
                    "creditAppliedCents" to recon.creditAppliedCents,
                    "deductions" to cleanDeductions.map { it.toMap() },
                    "deductionsTotalCents" to recon.deductionsTotalCents,
                    "netRefundCents" to recon.netRefundCents,
                    "balanceOwedCents" to recon.balanceOwedCents,
                    "moveOutDateEpochMs" to cleanMoveOutMs,
                    "waterMeterFinal" to (cleanWater ?: FieldValue.delete()),
                    "electricityMeterFinal" to (cleanPower ?: FieldValue.delete()),
                    "keysReturnedCount" to cleanKeys,
                    "imageRefs" to validatedImages,
                    "landlordNotes" to (cleanNotes ?: FieldValue.delete()),
                    "status" to targetStatus.name,
                    "refundMethod" to effectiveMethod.name,
                    "refundReferenceCode" to (cleanRef ?: FieldValue.delete()),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (targetStatus == DepositSettlementStatus.SETTLED) {
                    updates["settledAtEpochMs"] = System.currentTimeMillis()
                    updates["settledByUid"] = uid
                }
                db.collection("deposit_settlements").document(docId).update(updates).await()
            }

            if ((targetStatus == DepositSettlementStatus.ISSUED || targetStatus == DepositSettlementStatus.SETTLED) &&
                lease.tenantUid.isNotBlank()
            ) {
                val outcomeLabel = if (recon.balanceOwedCents > 0L) {
                    "Balance owed ${CurrencyUtils.formatKes(recon.balanceOwedCents)}"
                } else {
                    "Net refund ${CurrencyUtils.formatKes(recon.netRefundCents)}"
                }
                val notif = AppNotification(
                    recipientUid = lease.tenantUid,
                    senderUid = uid,
                    orgId = lease.orgId,
                    type = NotificationType.LEASE.name,
                    title = if (targetStatus == DepositSettlementStatus.SETTLED) {
                        "Deposit Refund Settled: Unit ${lease.unitNumber}"
                    } else {
                        "Deposit Settlement Issued: Unit ${lease.unitNumber}"
                    },
                    body = "${lease.propertyName} • Unit ${lease.unitNumber}: $outcomeLabel.${cleanRef?.let { " Ref: $it" } ?: ""}".take(500),
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
                    action = when (targetStatus) {
                        DepositSettlementStatus.DRAFT -> "SAVE_DEPOSIT_SETTLEMENT_DRAFT"
                        DepositSettlementStatus.ISSUED -> "ISSUE_DEPOSIT_SETTLEMENT"
                        DepositSettlementStatus.SETTLED -> "SETTLE_DEPOSIT_REFUND"
                        else -> "UPDATE_DEPOSIT_SETTLEMENT"
                    },
                    entityType = "DepositSettlement",
                    entityId = docId,
                    details = "Deposit ${CurrencyUtils.formatKes(recon.depositHeldCents)}, Deductions ${CurrencyUtils.formatKes(recon.deductionsTotalCents)}, Net Refund ${CurrencyUtils.formatKes(recon.netRefundCents)}"
                ).toCreateMap()
            ).await()

            Result.success(docId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, if (existingSettlement == null) OperationType.CREATE else OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun respondToDepositSettlement(
        settlement: DepositSettlement,
        approve: Boolean,
        responseNote: String?,
        actorName: String
    ): Result<Unit> {
        val path = "deposit_settlements/${settlement.id}"
        return try {
            val uid = requireUserId()
            if (uid != settlement.tenantUid) {
                return Result.failure(SecurityException("Only the lease tenant can respond to this deposit settlement."))
            }
            if (settlement.statusEnum() != DepositSettlementStatus.ISSUED) {
                return Result.failure(IllegalStateException("This deposit statement is not open for tenant review."))
            }
            val cleanNote = responseNote?.trim()?.take(800)?.takeIf { it.isNotBlank() }
            if (!approve && (cleanNote == null || cleanNote.length < 3)) {
                return Result.failure(IllegalArgumentException("Explain what you are disputing before requesting review."))
            }

            val nextStatus = if (approve) DepositSettlementStatus.APPROVED else DepositSettlementStatus.DISPUTED
            val updates = hashMapOf<String, Any>(
                "status" to nextStatus.name,
                "tenantResponseNote" to (cleanNote ?: FieldValue.delete()),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            db.collection("deposit_settlements").document(settlement.id).update(updates).await()

            settlement.authorizedUids
                .filter { it.isNotBlank() && it != uid }
                .distinct()
                .forEach { recipient ->
                    val notif = AppNotification(
                        recipientUid = recipient,
                        senderUid = uid,
                        orgId = settlement.orgId,
                        type = NotificationType.LEASE.name,
                        title = if (approve) {
                            "Deposit Statement Approved: Unit ${settlement.unitNumber}"
                        } else {
                            "Deposit Review Requested: Unit ${settlement.unitNumber}"
                        },
                        body = if (approve) {
                            "${settlement.tenantName} approved the deposit settlement for ${settlement.propertyName}."
                        } else {
                            "${settlement.tenantName} disputed deductions on ${settlement.propertyName}: ${cleanNote.orEmpty()}"
                        }.take(500),
                        relatedEntityId = settlement.id,
                        read = false
                    )
                    db.collection("notifications").document().set(notif.toCreateMap()).await()
                }

            db.collection("audit_logs").document().set(
                AuditLog(
                    orgId = settlement.orgId,
                    actorUid = uid,
                    actorName = actorName,
                    action = if (approve) "APPROVE_DEPOSIT_SETTLEMENT" else "DISPUTE_DEPOSIT_SETTLEMENT",
                    entityType = "DepositSettlement",
                    entityId = settlement.id,
                    details = "${nextStatus.label} by ${settlement.tenantName} for Unit ${settlement.unitNumber}"
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
}
