package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.firebase.OperationType
import com.example.data.firebase.guardFirestoreReads
import com.example.data.firebase.handleFirestoreError
import com.example.domain.model.Announcement
import com.example.domain.model.AppNotification
import com.example.domain.model.ChatMessage
import com.example.domain.model.Conversation
import com.example.domain.model.Expense
import com.example.domain.model.ExpenseCategory
import com.example.domain.model.Lease
import com.example.domain.model.MaintenanceCategory
import com.example.domain.model.MaintenancePriority
import com.example.domain.model.MaintenanceRequest
import com.example.domain.model.MaintenanceStatus
import com.example.domain.model.NotificationType
import com.example.domain.model.Property
import com.example.domain.model.ReportedIssue
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

class OperationsRepository(
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
            ?: throw IllegalStateException("User must be signed in with Google.")

    fun observeMaintenanceForLandlord(userId: String): Flow<List<MaintenanceRequest>> {
        val cleanUid = userId.trim()
        if (cleanUid.isBlank()) return flowOf(emptyList())
        return db.collection("maintenance_requests")
            .whereArrayContains("authorizedUids", cleanUid)
            .snapshots()
            .map { snap ->
                snap.toObjects(MaintenanceRequest::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
            }
            .guardFirestoreReads(OperationType.LIST, "maintenance_requests")
    }

    fun observeMaintenanceForTenant(userId: String): Flow<List<MaintenanceRequest>> {
        val cleanUid = userId.trim()
        if (cleanUid.isBlank()) return flowOf(emptyList())
        return db.collection("maintenance_requests")
            .whereEqualTo("tenantUid", cleanUid)
            .snapshots()
            .map { snap ->
                snap.toObjects(MaintenanceRequest::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
            }
            .guardFirestoreReads(OperationType.LIST, "maintenance_requests")
    }

    fun observeMaintenanceForStaff(userId: String): Flow<List<MaintenanceRequest>> {
        val cleanUid = userId.trim()
        if (cleanUid.isBlank()) return flowOf(emptyList())
        return db.collection("maintenance_requests")
            .whereEqualTo("assignedStaffUid", cleanUid)
            .snapshots()
            .map { snap ->
                snap.toObjects(MaintenanceRequest::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
            }
            .guardFirestoreReads(OperationType.LIST, "maintenance_requests")
    }

    fun observeConversations(userId: String): Flow<List<Conversation>> {
        val cleanUid = userId.trim()
        if (cleanUid.isBlank()) return flowOf(emptyList())
        return db.collection("conversations")
            .whereArrayContains("participantUids", cleanUid)
            .snapshots()
            .map { snap ->
                snap.toObjects(Conversation::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.updatedAt?.seconds ?: 0L }
            }
            .guardFirestoreReads(OperationType.LIST, "conversations")
    }

    fun observeMessages(convId: String): Flow<List<ChatMessage>> {
        val cleanConvId = convId.trim()
        if (cleanConvId.isBlank()) return flowOf(emptyList())
        return db.collection("conversations").document(cleanConvId).collection("messages")
            .snapshots()
            .map { snap ->
                snap.toObjects(ChatMessage::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedBy { it.createdAt?.seconds ?: Long.MAX_VALUE }
            }
            .guardFirestoreReads(OperationType.LIST, "conversations/$cleanConvId/messages")
    }

    fun observeAnnouncementsForLandlord(userId: String): Flow<List<Announcement>> {
        val cleanUid = userId.trim()
        if (cleanUid.isBlank()) return flowOf(emptyList())
        return db.collection("announcements")
            .whereArrayContains("authorizedUids", cleanUid)
            .snapshots()
            .map { snap ->
                snap.toObjects(Announcement::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
            }
            .guardFirestoreReads(OperationType.LIST, "announcements")
    }

    fun observeAnnouncementsForTenant(userId: String): Flow<List<Announcement>> {
        val cleanUid = userId.trim()
        if (cleanUid.isBlank()) return flowOf(emptyList())
        return db.collection("announcements")
            .whereArrayContains("targetUids", cleanUid)
            .snapshots()
            .map { snap ->
                snap.toObjects(Announcement::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
            }
            .guardFirestoreReads(OperationType.LIST, "announcements")
    }

    fun observeNotifications(userId: String): Flow<List<AppNotification>> {
        val cleanUid = userId.trim()
        if (cleanUid.isBlank()) return flowOf(emptyList())
        return db.collection("notifications")
            .whereEqualTo("recipientUid", cleanUid)
            .snapshots()
            .map { snap ->
                snap.toObjects(AppNotification::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
            }
            .guardFirestoreReads(OperationType.LIST, "notifications")
    }

    suspend fun submitMaintenanceRequest(
        lease: Lease,
        title: String,
        description: String,
        category: MaintenanceCategory,
        priority: MaintenancePriority,
        imageRefs: List<String>
    ): Result<String> {
        val docRef = db.collection("maintenance_requests").document()
        return try {
            val uid = requireUserId()
            require(title.trim().length in 2..140) { "Title must be 2-140 characters" }
            require(description.trim().length in 2..1000) { "Description must be 2-1000 characters" }
            val req = MaintenanceRequest(
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
                title = title.trim(),
                description = description.trim(),
                category = category.name,
                priority = priority.name,
                status = MaintenanceStatus.SUBMITTED.name,
                repairCostCents = 0L,
                imageRefs = imageRefs.take(4)
            )
            docRef.set(req.toCreateMap()).await()

            db.collection("notifications").document().set(
                AppNotification(
                    recipientUid = lease.ownerUid,
                    senderUid = uid,
                    orgId = lease.orgId,
                    type = NotificationType.MAINTENANCE.name,
                    title = "New Maintenance Ticket (${priority.label})",
                    body = "${lease.propertyName} • Unit ${lease.unitNumber}: ${title.trim()}",
                    relatedEntityId = docRef.id,
                    read = false
                ).toCreateMap()
            ).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun updateMaintenanceProgress(
        request: MaintenanceRequest,
        newStatus: MaintenanceStatus,
        repairCostCents: Long,
        updateNote: String,
        assignedStaffUid: String?,
        assignedStaffName: String?,
        recordAsExpense: Boolean
    ): Result<Unit> {
        val path = "maintenance_requests/${request.id}"
        return try {
            val uid = requireUserId()
            val updates = mutableMapOf<String, Any>(
                "status" to newStatus.name,
                "repairCostCents" to repairCostCents.coerceAtLeast(0L),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (updateNote.isNotBlank()) updates["latestUpdateNote"] = updateNote.trim().take(500)
            if (!assignedStaffUid.isNullOrBlank()) updates["assignedStaffUid"] = assignedStaffUid.trim()
            if (!assignedStaffName.isNullOrBlank()) updates["assignedStaffName"] = assignedStaffName.trim()

            db.collection("maintenance_requests").document(request.id).update(updates).await()

            if (recordAsExpense && repairCostCents > 0L) {
                val exp = Expense(
                    orgId = request.orgId,
                    propertyId = request.propertyId,
                    propertyName = request.propertyName,
                    unitId = request.unitId,
                    ownerUid = request.ownerUid,
                    managerUids = request.managerUids,
                    authorizedUids = request.authorizedUids,
                    category = ExpenseCategory.REPAIRS.name,
                    title = "Repair: ${request.title.take(100)}",
                    description = updateNote.ifBlank { request.description }.take(600),
                    amountCents = repairCostCents,
                    expenseDateEpochMs = System.currentTimeMillis(),
                    recordedByUid = uid
                )
                db.collection("expenses").document().set(exp.toCreateMap()).await()
            }

            if (uid != request.tenantUid) {
                db.collection("notifications").document().set(
                    AppNotification(
                        recipientUid = request.tenantUid,
                        senderUid = uid,
                        orgId = request.orgId,
                        type = NotificationType.MAINTENANCE.name,
                        title = "Maintenance Update: ${newStatus.label}",
                        body = "${request.title}: ${updateNote.ifBlank { "Status changed to ${newStatus.label}" }}",
                        relatedEntityId = request.id,
                        read = false
                    ).toCreateMap()
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun createConversation(
        orgId: String,
        propertyId: String,
        propertyName: String,
        unitNumber: String?,
        participantUids: List<String>,
        subject: String,
        initialMessage: String,
        senderName: String
    ): Result<String> {
        val convRef = db.collection("conversations").document()
        return try {
            val uid = requireUserId()
            val participants = (participantUids + uid).filter { it.isNotBlank() }.distinct().take(10)
            val conv = Conversation(
                orgId = orgId,
                propertyId = propertyId,
                propertyName = propertyName,
                unitNumber = unitNumber,
                participantUids = participants,
                subject = subject.trim().take(140),
                lastMessagePreview = initialMessage.trim().take(300),
                lastSenderUid = uid
            )
            convRef.set(conv.toCreateMap()).await()

            val msg = ChatMessage(
                convId = convRef.id,
                participantUids = participants,
                senderUid = uid,
                senderName = senderName,
                body = initialMessage.trim().take(1500),
                readByUids = listOf(uid)
            )
            convRef.collection("messages").document().set(msg.toCreateMap()).await()

            participants.filter { it != uid }.forEach { recipient ->
                db.collection("notifications").document().set(
                    AppNotification(
                        recipientUid = recipient,
                        senderUid = uid,
                        orgId = orgId,
                        type = NotificationType.MESSAGE.name,
                        title = "New Message from $senderName",
                        body = "${subject.trim()}: ${initialMessage.trim().take(120)}",
                        relatedEntityId = convRef.id,
                        read = false
                    ).toCreateMap()
                ).await()
            }
            Result.success(convRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, convRef.path)
            Result.failure(e)
        }
    }

    suspend fun sendMessage(
        conversation: Conversation,
        body: String,
        senderName: String
    ): Result<Unit> {
        val path = "conversations/${conversation.id}/messages"
        return try {
            val uid = requireUserId()
            require(body.trim().isNotBlank()) { "Message cannot be empty" }
            val msg = ChatMessage(
                convId = conversation.id,
                participantUids = conversation.participantUids,
                senderUid = uid,
                senderName = senderName,
                body = body.trim().take(1500),
                readByUids = listOf(uid)
            )
            db.collection("conversations").document(conversation.id)
                .collection("messages").document().set(msg.toCreateMap()).await()

            db.collection("conversations").document(conversation.id).update(
                mapOf(
                    "lastMessagePreview" to body.trim().take(300),
                    "lastSenderUid" to uid,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun publishAnnouncement(
        property: Property,
        targetTenantUids: List<String>,
        title: String,
        body: String,
        authorName: String
    ): Result<String> {
        val docRef = db.collection("announcements").document()
        return try {
            val uid = requireUserId()
            val targets = targetTenantUids.distinct().take(50)
            val ann = Announcement(
                orgId = property.orgId,
                propertyId = property.id,
                propertyName = property.name,
                ownerUid = property.ownerUid,
                authorizedUids = property.authorizedUids,
                targetUids = targets,
                title = title.trim().take(140),
                body = body.trim().take(1500),
                authorUid = uid,
                authorName = authorName
            )
            docRef.set(ann.toCreateMap()).await()

            targets.filter { it != uid }.forEach { tenantUid ->
                db.collection("notifications").document().set(
                    AppNotification(
                        recipientUid = tenantUid,
                        senderUid = uid,
                        orgId = property.orgId,
                        type = NotificationType.ANNOUNCEMENT.name,
                        title = "${property.name}: ${title.trim().take(80)}",
                        body = body.trim().take(200),
                        relatedEntityId = docRef.id,
                        read = false
                    ).toCreateMap()
                ).await()
            }
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun markNotificationRead(notifId: String): Result<Unit> {
        val path = "notifications/$notifId"
        return try {
            requireUserId()
            db.collection("notifications").document(notifId).update(
                mapOf("read" to true, "updatedAt" to FieldValue.serverTimestamp())
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun submitReportedIssue(
        orgId: String,
        category: String,
        title: String,
        description: String,
        reporterName: String
    ): Result<String> {
        val docRef = db.collection("reported_issues").document()
        return try {
            val uid = requireUserId()
            val issue = ReportedIssue(
                reporterUid = uid,
                reporterName = reporterName,
                orgId = orgId.ifBlank { "platform" },
                category = category.trim().take(60),
                title = title.trim().take(140),
                description = description.trim().take(1000),
                status = "OPEN"
            )
            docRef.set(issue.toCreateMap()).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }
}
