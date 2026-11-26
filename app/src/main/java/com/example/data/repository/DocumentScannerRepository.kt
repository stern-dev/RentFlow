package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.firebase.OperationType
import com.example.data.firebase.guardFirestoreReads
import com.example.data.firebase.handleFirestoreError
import com.example.domain.model.AppNotification
import com.example.domain.model.AuditLog
import com.example.domain.model.DocumentType
import com.example.domain.model.Lease
import com.example.domain.model.NotificationType
import com.example.domain.model.RentalUnit
import com.example.domain.model.ScanFilterMode
import com.example.domain.model.ScannedDocument
import com.example.domain.model.UserRole
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

class DocumentScannerRepository(
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
            ?: throw IllegalStateException("User must be signed in with Google before accessing document records.")

    fun observeDocumentsForOrganization(orgId: String, userId: String): Flow<List<ScannedDocument>> {
        val cleanOrgId = orgId.trim()
        val cleanUid = userId.trim()
        if (cleanOrgId.isBlank() || cleanUid.isBlank()) return flowOf(emptyList())
        return db.collection("scanned_documents")
            .whereEqualTo("orgId", cleanOrgId)
            .whereArrayContains("authorizedUids", cleanUid)
            .snapshots()
            .map { snap ->
                snap.toObjects(ScannedDocument::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
            }
            .guardFirestoreReads(OperationType.LIST, "scanned_documents")
    }

    fun observeDocumentsForTenant(userId: String): Flow<List<ScannedDocument>> {
        val cleanUid = userId.trim()
        if (cleanUid.isBlank()) return flowOf(emptyList())
        return db.collection("scanned_documents")
            .whereEqualTo("tenantUid", cleanUid)
            .whereEqualTo("tenantVisible", true)
            .snapshots()
            .map { snap ->
                snap.toObjects(ScannedDocument::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
            }
            .guardFirestoreReads(OperationType.LIST, "scanned_documents")
    }

    suspend fun uploadScannedDocument(
        unit: RentalUnit,
        lease: Lease? = null,
        documentType: DocumentType,
        title: String,
        notes: String?,
        filterMode: ScanFilterMode,
        pageImageRefs: List<String>,
        extractedReference: String? = null,
        extractedAmountCents: Long? = null,
        extractedDateText: String? = null,
        extractedSummary: String? = null,
        aiExtracted: Boolean = false,
        tenantVisible: Boolean = true,
        paymentId: String? = null,
        expenseId: String? = null,
        uploaderName: String,
        uploaderRole: UserRole
    ): Result<String> {
        val docRef = db.collection("scanned_documents").document()
        return try {
            val uid = requireUserId()
            val cleanTitle = title.trim().ifBlank { documentType.label }
            require(cleanTitle.length in 2..140) { "Document title must be 2–140 characters." }
            require(unit.id.isNotBlank() && unit.propertyId.isNotBlank() && unit.orgId.isNotBlank()) {
                "A valid property unit is required to attach this scan."
            }
            require(pageImageRefs.isNotEmpty() && pageImageRefs.size <= ScannedDocument.MAX_PAGES) {
                "Attach between 1 and ${ScannedDocument.MAX_PAGES} scanned pages."
            }
            val totalChars = pageImageRefs.sumOf { it.length }
            require(totalChars <= ScannedDocument.MAX_TOTAL_PAYLOAD_CHARS) {
                "Scanned document pages exceed the Firestore document size budget."
            }
            require(
                pageImageRefs.all {
                    it.startsWith("data:image/jpeg;base64,") && it.length <= ScannedDocument.MAX_SINGLE_PAGE_CHARS
                }
            ) {
                "All scanned pages must be compressed JPEG data URIs."
            }

            val resolvedTenantUid = when {
                uploaderRole == UserRole.TENANT -> uid
                !lease?.tenantUid.isNullOrBlank() -> lease?.tenantUid
                !unit.currentTenantUid.isNullOrBlank() -> unit.currentTenantUid
                else -> null
            }
            val resolvedTenantName = lease?.tenantName ?: if (uploaderRole == UserRole.TENANT) uploaderName else null
            val resolvedLeaseId = lease?.id ?: unit.currentLeaseId
            val resolvedTenantVisible = if (uploaderRole == UserRole.TENANT) true else tenantVisible

            if (uploaderRole == UserRole.TENANT) {
                require(documentType == DocumentType.PAYMENT_RECEIPT || documentType == DocumentType.OTHER) {
                    "Tenants can upload payment receipts or supporting documents only."
                }
                require(!resolvedLeaseId.isNullOrBlank() && resolvedTenantUid == uid) {
                    "An active lease linked to your account is required before uploading documents."
                }
            }

            val doc = ScannedDocument(
                id = docRef.id,
                orgId = unit.orgId,
                propertyId = unit.propertyId,
                propertyName = unit.propertyName,
                unitId = unit.id,
                unitNumber = unit.unitNumber,
                ownerUid = unit.ownerUid,
                managerUids = unit.managerUids,
                authorizedUids = (unit.authorizedUids + unit.ownerUid).distinct(),
                leaseId = resolvedLeaseId,
                tenantUid = resolvedTenantUid,
                tenantName = resolvedTenantName,
                paymentId = paymentId,
                expenseId = expenseId,
                documentType = documentType.name,
                title = cleanTitle,
                notes = notes,
                filterMode = filterMode.name,
                pageImageRefs = pageImageRefs,
                extractedReference = extractedReference,
                extractedAmountCents = extractedAmountCents,
                extractedDateText = extractedDateText,
                extractedSummary = extractedSummary,
                aiExtracted = aiExtracted,
                tenantVisible = resolvedTenantVisible,
                uploadedByUid = uid,
                uploadedByName = uploaderName,
                uploadedByRole = uploaderRole.name
            )

            docRef.set(doc.toCreateMap()).await()

            runCatching {
                val audit = AuditLog(
                    orgId = unit.orgId,
                    actorUid = uid,
                    actorName = uploaderName.trim().ifBlank { "RentFlow User" }.take(100),
                    action = "SCANNED_DOCUMENT_UPLOADED",
                    entityType = "SCANNED_DOCUMENT",
                    entityId = docRef.id,
                    details = "${documentType.label} \"$cleanTitle\" (${pageImageRefs.size} page(s)) attached to ${unit.propertyName} Unit ${unit.unitNumber}."
                        .take(500)
                )
                db.collection("audit_logs").document().set(audit.toCreateMap()).await()
            }

            runCatching {
                val notifyRecipient = when {
                    uploaderRole == UserRole.TENANT -> unit.ownerUid
                    resolvedTenantVisible && !resolvedTenantUid.isNullOrBlank() && resolvedTenantUid != uid -> resolvedTenantUid
                    else -> null
                }
                if (!notifyRecipient.isNullOrBlank()) {
                    val notif = AppNotification(
                        recipientUid = notifyRecipient,
                        senderUid = uid,
                        orgId = unit.orgId,
                        type = if (documentType == DocumentType.PAYMENT_RECEIPT) NotificationType.PAYMENT.name else NotificationType.LEASE.name,
                        title = if (uploaderRole == UserRole.TENANT) {
                            "Scanned Receipt Uploaded • Unit ${unit.unitNumber}"
                        } else {
                            "Document Shared • $cleanTitle"
                        }.take(120),
                        body = if (uploaderRole == UserRole.TENANT) {
                            "${uploaderName.ifBlank { "Tenant" }} uploaded ${documentType.label}: $cleanTitle (${unit.propertyName} Unit ${unit.unitNumber})."
                        } else {
                            "${uploaderName.ifBlank { "Landlord" }} added ${documentType.label} for ${unit.propertyName} Unit ${unit.unitNumber}."
                        }.take(400),
                        relatedEntityId = docRef.id
                    )
                    db.collection("notifications").document().set(notif.toCreateMap()).await()
                }
            }

            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun updateDocumentMetadata(
        document: ScannedDocument,
        title: String,
        notes: String?,
        tenantVisible: Boolean
    ): Result<Unit> {
        val docRef = db.collection("scanned_documents").document(document.id)
        return try {
            requireUserId()
            val cleanTitle = title.trim()
            require(cleanTitle.length in 2..140) { "Document title must be 2–140 characters." }
            val cleanNotes = notes?.trim()?.take(800)?.takeIf { it.isNotBlank() }
            val updates = mutableMapOf<String, Any>(
                "title" to cleanTitle,
                "tenantVisible" to tenantVisible,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (cleanNotes != null) {
                updates["notes"] = cleanNotes
            } else if (!document.notes.isNullOrBlank()) {
                updates["notes"] = FieldValue.delete()
            }
            docRef.update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteDocument(documentId: String): Result<Unit> {
        val cleanId = documentId.trim()
        val docRef = db.collection("scanned_documents").document(cleanId)
        return try {
            requireUserId()
            require(cleanId.isNotBlank()) { "Document ID is required." }
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }
}
