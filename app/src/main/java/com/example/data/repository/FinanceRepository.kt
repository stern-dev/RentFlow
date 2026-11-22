package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.firebase.OperationType
import com.example.data.firebase.guardFirestoreReads
import com.example.data.firebase.handleFirestoreError
import com.example.domain.finance.CurrencyUtils
import com.example.domain.finance.FinancialEngine
import com.example.domain.finance.RentReminderEngine
import com.example.domain.model.AppNotification
import com.example.domain.model.AuditLog
import com.example.domain.model.Expense
import com.example.domain.model.ExpenseCategory
import com.example.domain.model.Invoice
import com.example.domain.model.InvoiceStatus
import com.example.domain.model.Lease
import com.example.domain.model.LeaseStatus
import com.example.domain.model.NotificationType
import com.example.domain.model.Payment
import com.example.domain.model.PaymentChannelType
import com.example.domain.model.PaymentMethod
import com.example.domain.model.PaymentStatus
import com.example.domain.model.PaymentVerificationSource
import com.example.domain.model.Property
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
import java.util.UUID

class FinanceRepository(
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
            ?: throw IllegalStateException("User must be signed in with Google before accessing financial records.")

    fun observeInvoicesForLandlord(userId: String): Flow<List<Invoice>> {
        val cleanUid = userId.trim()
        if (cleanUid.isBlank()) return flowOf(emptyList())
        return db.collection("invoices")
            .whereArrayContains("authorizedUids", cleanUid)
            .snapshots()
            .map { snap ->
                snap.toObjects(Invoice::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.dueDateEpochMs }
            }
            .guardFirestoreReads(OperationType.LIST, "invoices")
    }

    fun observeMyInvoices(userId: String): Flow<List<Invoice>> {
        val cleanUid = userId.trim()
        if (cleanUid.isBlank()) return flowOf(emptyList())
        return db.collection("invoices")
            .whereEqualTo("tenantUid", cleanUid)
            .snapshots()
            .map { snap ->
                snap.toObjects(Invoice::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.dueDateEpochMs }
            }
            .guardFirestoreReads(OperationType.LIST, "invoices")
    }

    fun observePaymentsForLandlord(userId: String): Flow<List<Payment>> {
        val cleanUid = userId.trim()
        if (cleanUid.isBlank()) return flowOf(emptyList())
        return db.collection("payments")
            .whereArrayContains("authorizedUids", cleanUid)
            .snapshots()
            .map { snap ->
                snap.toObjects(Payment::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.paymentDateEpochMs }
            }
            .guardFirestoreReads(OperationType.LIST, "payments")
    }

    fun observeMyPayments(userId: String): Flow<List<Payment>> {
        val cleanUid = userId.trim()
        if (cleanUid.isBlank()) return flowOf(emptyList())
        return db.collection("payments")
            .whereEqualTo("tenantUid", cleanUid)
            .snapshots()
            .map { snap ->
                snap.toObjects(Payment::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.paymentDateEpochMs }
            }
            .guardFirestoreReads(OperationType.LIST, "payments")
    }

    fun observeExpensesForLandlord(userId: String): Flow<List<Expense>> {
        val cleanUid = userId.trim()
        if (cleanUid.isBlank()) return flowOf(emptyList())
        return db.collection("expenses")
            .whereArrayContains("authorizedUids", cleanUid)
            .snapshots()
            .map { snap ->
                snap.toObjects(Expense::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.expenseDateEpochMs }
            }
            .guardFirestoreReads(OperationType.LIST, "expenses")
    }

    suspend fun generateMonthlyInvoices(
        leases: List<Lease>,
        existingInvoices: List<Invoice>,
        billingPeriod: String,
        extraChargesCents: Long,
        extraChargesNote: String?,
        actorName: String
    ): Result<Int> {
        return try {
            val uid = requireUserId()
            val cleanPeriod = billingPeriod.trim()
            require(CurrencyUtils.isValidBillingPeriod(cleanPeriod)) {
                "Billing period must be in YYYY-MM format (e.g. 2026-11)"
            }
            require(extraChargesCents >= 0L) { "Extra charges cannot be negative" }
            val activeLeases = leases.filter { it.statusEnum() == LeaseStatus.ACTIVE }
            var createdCount = 0

            for (lease in activeLeases) {
                val deterministicId = "${lease.id}_$billingPeriod".replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
                val docRef = db.collection("invoices").document(deterministicId)
                val exists = docRef.get().await().exists()
                if (exists) continue

                val prevOutstanding = existingInvoices
                    .filter {
                        it.leaseId == lease.id &&
                            it.billingPeriod != billingPeriod &&
                            it.statusEnum() != InvoiceStatus.CANCELLED &&
                            it.outstandingCents > 0L
                    }
                    .sumOf { it.outstandingCents }

                val totalDue = lease.rentCents + extraChargesCents.coerceAtLeast(0L) + prevOutstanding
                val creditToApply = minOf(lease.creditBalanceCents.coerceAtLeast(0L), totalDue)
                val outstanding = (totalDue - creditToApply).coerceAtLeast(0L)
                val status = when {
                    outstanding == 0L -> InvoiceStatus.PAID
                    creditToApply > 0L -> InvoiceStatus.PARTIALLY_PAID
                    else -> InvoiceStatus.UNPAID
                }

                val invoiceNumber = "INV-${billingPeriod.replace("-", "")}-${lease.unitNumber.take(6).uppercase()}"
                val dueDateMs = CurrencyUtils.computeDueDateEpochMs(billingPeriod, lease.dueDayOfMonth.toInt())

                val invoice = Invoice(
                    invoiceNumber = invoiceNumber,
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
                    billingPeriod = billingPeriod,
                    rentCents = lease.rentCents,
                    extraChargesCents = extraChargesCents.coerceAtLeast(0L),
                    extraChargesNote = extraChargesNote?.trim()?.take(300),
                    previousBalanceCents = prevOutstanding,
                    totalDueCents = totalDue,
                    paidCents = creditToApply,
                    outstandingCents = outstanding,
                    dueDateEpochMs = dueDateMs,
                    status = status.name
                )

                db.runTransaction { tx ->
                    if (tx.get(docRef).exists()) return@runTransaction
                    tx.set(docRef, invoice.toCreateMap())
                    if (creditToApply > 0L) {
                        val leaseRef = db.collection("leases").document(lease.id)
                        val remainingCredit = (lease.creditBalanceCents - creditToApply).coerceAtLeast(0L)
                        tx.update(
                            leaseRef,
                            mapOf(
                                "creditBalanceCents" to remainingCredit,
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        )
                    }
                }.await()

                db.collection("notifications").document().set(
                    AppNotification(
                        recipientUid = lease.tenantUid,
                        senderUid = uid,
                        orgId = lease.orgId,
                        type = NotificationType.INVOICE.name,
                        title = "New Rent Invoice (${CurrencyUtils.billingPeriodLabel(billingPeriod)})",
                        body = "$invoiceNumber: ${CurrencyUtils.formatKes(outstanding, true)} due ${CurrencyUtils.formatDate(dueDateMs)}",
                        relatedEntityId = deterministicId,
                        read = false
                    ).toCreateMap()
                ).await()

                createdCount++
            }

            if (createdCount > 0 && activeLeases.isNotEmpty()) {
                db.collection("audit_logs").document().set(
                    AuditLog(
                        orgId = activeLeases.first().orgId,
                        actorUid = uid,
                        actorName = actorName,
                        action = "GENERATE_INVOICES",
                        entityType = "Invoice",
                        entityId = billingPeriod,
                        details = "Generated $createdCount monthly invoice(s) for $billingPeriod"
                    ).toCreateMap()
                ).await()
            }
            Result.success(createdCount)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "invoices")
            Result.failure(e)
        }
    }

    suspend fun submitPayment(
        lease: Lease,
        invoiceId: String?,
        referenceCode: String,
        amountCents: Long,
        method: PaymentMethod,
        simulatedCheckout: Boolean,
        paymentMethodId: String? = null,
        paymentChannelType: PaymentChannelType? = null,
        paymentShortcode: String? = null,
        verificationSource: PaymentVerificationSource? = null,
        matchedReceiptId: String? = null
    ): Result<String> {
        val docRef = db.collection("payments").document()
        return try {
            val uid = requireUserId()
            val cleanRef = referenceCode.trim()
            require(CurrencyUtils.isValidMpesaReference(cleanRef)) {
                "Reference code must be 4-30 alphanumeric characters or hyphens"
            }
            require(amountCents > 0L) { "Payment amount must be greater than zero" }

            val payment = Payment(
                referenceCode = cleanRef.uppercase().take(60),
                leaseId = lease.id,
                invoiceId = invoiceId?.takeIf { it.isNotBlank() },
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
                amountCents = amountCents,
                method = method.name,
                paymentMethodId = paymentMethodId?.trim()?.take(128)?.takeIf { it.isNotBlank() },
                paymentChannelType = paymentChannelType?.name,
                paymentShortcode = paymentShortcode?.trim()?.take(40)?.takeIf { it.isNotBlank() },
                simulatedCheckout = simulatedCheckout,
                paymentDateEpochMs = System.currentTimeMillis(),
                status = PaymentStatus.PENDING_VERIFICATION.name,
                recordedByUid = uid,
                verificationSource = verificationSource?.name,
                matchedReceiptId = matchedReceiptId?.trim()?.take(128)?.takeIf { it.isNotBlank() }
            )
            docRef.set(payment.toCreateMap()).await()

            if (uid == lease.tenantUid) {
                db.collection("notifications").document().set(
                    AppNotification(
                        recipientUid = lease.ownerUid,
                        senderUid = uid,
                        orgId = lease.orgId,
                        type = NotificationType.PAYMENT.name,
                        title = "Payment Submitted for Verification",
                        body = "${lease.tenantName} submitted ${CurrencyUtils.formatKes(amountCents, true)} (${payment.referenceCode})",
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

    suspend fun verifyAndAllocatePayment(
        payment: Payment,
        lease: Lease,
        leaseInvoices: List<Invoice>,
        verificationNotes: String,
        actorName: String,
        verificationSource: PaymentVerificationSource = PaymentVerificationSource.MANUAL,
        matchedReceiptId: String? = null
    ): Result<String> {
        val paymentPath = "payments/${payment.id}"
        return try {
            val uid = requireUserId()
            require(payment.statusEnum() == PaymentStatus.PENDING_VERIFICATION) {
                "Only pending payments can be verified"
            }

            val plan = FinancialEngine.planInvoiceAllocation(
                paymentAmountCents = payment.amountCents,
                existingCreditCents = lease.creditBalanceCents,
                openInvoices = leaseInvoices
            )
            val receiptNum = "RCP-${UUID.randomUUID().toString().take(8).uppercase()}"

            db.runTransaction { tx ->
                val paymentRef = db.collection("payments").document(payment.id)
                val leaseRef = db.collection("leases").document(lease.id)
                val freshPay = tx.get(paymentRef)
                check(freshPay.getString("status") == PaymentStatus.PENDING_VERIFICATION.name) {
                    "Payment already processed"
                }

                for (upd in plan.updates) {
                    val invRef = db.collection("invoices").document(upd.invoiceId)
                    tx.update(
                        invRef,
                        mapOf(
                            "paidCents" to upd.newPaidCents,
                            "outstandingCents" to upd.newOutstandingCents,
                            "status" to upd.newStatus.name,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }

                tx.update(
                    leaseRef,
                    mapOf(
                        "creditBalanceCents" to plan.remainingCreditCents,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )

                val payUpdates = mutableMapOf<String, Any>(
                    "status" to PaymentStatus.VERIFIED.name,
                    "verifiedByUid" to uid,
                    "receiptNumber" to receiptNum,
                    "allocationsSummary" to plan.summaryText.take(600),
                    "verificationSource" to verificationSource.name,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                matchedReceiptId?.trim()?.take(128)?.takeIf { it.isNotBlank() }?.let { recId ->
                    payUpdates["matchedReceiptId"] = recId
                }
                if (verificationNotes.isNotBlank()) {
                    payUpdates["verificationNotes"] = verificationNotes.trim().take(400)
                }
                tx.update(paymentRef, payUpdates)
            }.await()

            db.collection("notifications").document().set(
                AppNotification(
                    recipientUid = payment.tenantUid,
                    senderUid = uid,
                    orgId = payment.orgId,
                    type = NotificationType.PAYMENT.name,
                    title = "Payment Verified • $receiptNum",
                    body = "${CurrencyUtils.formatKes(payment.amountCents, true)} verified (${plan.summaryText.take(180)})",
                    relatedEntityId = payment.id,
                    read = false
                ).toCreateMap()
            ).await()

            db.collection("audit_logs").document().set(
                AuditLog(
                    orgId = payment.orgId,
                    actorUid = uid,
                    actorName = actorName,
                    action = "VERIFY_PAYMENT",
                    entityType = "Payment",
                    entityId = payment.id,
                    details = "Verified ${payment.referenceCode} (${CurrencyUtils.formatKes(payment.amountCents, true)}) -> $receiptNum"
                ).toCreateMap()
            ).await()

            Result.success(receiptNum)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, paymentPath)
            Result.failure(e)
        }
    }

    suspend fun rejectPayment(
        payment: Payment,
        reason: String,
        actorName: String
    ): Result<Unit> {
        val path = "payments/${payment.id}"
        return try {
            val uid = requireUserId()
            db.collection("payments").document(payment.id).update(
                mapOf(
                    "status" to PaymentStatus.REJECTED.name,
                    "verifiedByUid" to uid,
                    "verificationNotes" to reason.trim().take(400).ifBlank { "Reference not confirmed" },
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            db.collection("notifications").document().set(
                AppNotification(
                    recipientUid = payment.tenantUid,
                    senderUid = uid,
                    orgId = payment.orgId,
                    type = NotificationType.PAYMENT.name,
                    title = "Payment Verification Declined",
                    body = "Ref ${payment.referenceCode}: ${reason.ifBlank { "Could not match reference" }}",
                    relatedEntityId = payment.id,
                    read = false
                ).toCreateMap()
            ).await()

            db.collection("audit_logs").document().set(
                AuditLog(
                    orgId = payment.orgId,
                    actorUid = uid,
                    actorName = actorName,
                    action = "REJECT_PAYMENT",
                    entityType = "Payment",
                    entityId = payment.id,
                    details = "Rejected ${payment.referenceCode}: $reason"
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

    suspend fun reverseVerifiedPayment(
        payment: Payment,
        lease: Lease,
        leaseInvoices: List<Invoice>,
        reason: String,
        actorName: String
    ): Result<Unit> {
        val path = "payments/${payment.id}"
        return try {
            val uid = requireUserId()
            if (payment.ownerUid != uid) {
                return Result.failure(SecurityException("Only the property owner can reverse a verified payment."))
            }
            require(payment.statusEnum() == PaymentStatus.VERIFIED) { "Only verified payments can be reversed" }
            require(reason.trim().length >= 3) { "Reversal reason is required" }

            var remainingToClawBack = payment.amountCents
            val creditDeduction = minOf(lease.creditBalanceCents, remainingToClawBack)
            remainingToClawBack -= creditDeduction
            val newCredit = (lease.creditBalanceCents - creditDeduction).coerceAtLeast(0L)

            val invoiceRollbacks = mutableListOf<Triple<String, Long, Long>>()
            val paidInvoices = leaseInvoices
                .filter { it.paidCents > 0L && it.statusEnum() != InvoiceStatus.CANCELLED }
                .sortedByDescending { it.dueDateEpochMs }

            for (inv in paidInvoices) {
                if (remainingToClawBack <= 0L) break
                val revert = minOf(remainingToClawBack, inv.paidCents)
                val newPaid = (inv.paidCents - revert).coerceAtLeast(0L)
                val newOut = (inv.totalDueCents - newPaid).coerceAtLeast(0L)
                invoiceRollbacks += Triple(inv.id, newPaid, newOut)
                remainingToClawBack -= revert
            }

            db.runTransaction { tx ->
                val paymentRef = db.collection("payments").document(payment.id)
                val leaseRef = db.collection("leases").document(lease.id)
                for ((invId, newPaid, newOut) in invoiceRollbacks) {
                    val newStatus = if (newPaid == 0L) InvoiceStatus.UNPAID else InvoiceStatus.PARTIALLY_PAID
                    tx.update(
                        db.collection("invoices").document(invId),
                        mapOf(
                            "paidCents" to newPaid,
                            "outstandingCents" to newOut,
                            "status" to newStatus.name,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
                tx.update(
                    leaseRef,
                    mapOf(
                        "creditBalanceCents" to newCredit,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                tx.update(
                    paymentRef,
                    mapOf(
                        "status" to PaymentStatus.REVERSED.name,
                        "reversalReason" to reason.trim().take(400),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }.await()

            db.collection("notifications").document().set(
                AppNotification(
                    recipientUid = payment.tenantUid,
                    senderUid = uid,
                    orgId = payment.orgId,
                    type = NotificationType.PAYMENT.name,
                    title = "Payment Reversed",
                    body = "Payment ${payment.referenceCode} was reversed: ${reason.trim()}",
                    relatedEntityId = payment.id,
                    read = false
                ).toCreateMap()
            ).await()

            db.collection("audit_logs").document().set(
                AuditLog(
                    orgId = payment.orgId,
                    actorUid = uid,
                    actorName = actorName,
                    action = "REVERSE_PAYMENT",
                    entityType = "Payment",
                    entityId = payment.id,
                    details = "Reversed ${payment.referenceCode} (${CurrencyUtils.formatKes(payment.amountCents, true)}): $reason"
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

    suspend fun recordExpense(
        property: Property,
        category: ExpenseCategory,
        title: String,
        description: String,
        amountCents: Long,
        expenseDateEpochMs: Long,
        documentRef: String?,
        actorName: String
    ): Result<String> {
        val docRef = db.collection("expenses").document()
        return try {
            val uid = requireUserId()
            require(title.trim().length in 2..120) { "Expense title must be 2-120 characters" }
            require(amountCents > 0L) { "Amount must be greater than zero" }
            val expense = Expense(
                orgId = property.orgId,
                propertyId = property.id,
                propertyName = property.name,
                ownerUid = property.ownerUid,
                managerUids = property.managerUids,
                authorizedUids = property.authorizedUids,
                category = category.name,
                title = title.trim(),
                description = description.trim().take(600),
                amountCents = amountCents,
                expenseDateEpochMs = expenseDateEpochMs,
                documentRef = documentRef?.trim()?.take(300),
                recordedByUid = uid
            )
            docRef.set(expense.toCreateMap()).await()
            db.collection("audit_logs").document().set(
                AuditLog(
                    orgId = property.orgId,
                    actorUid = uid,
                    actorName = actorName,
                    action = "RECORD_EXPENSE",
                    entityType = "Expense",
                    entityId = docRef.id,
                    details = "${property.name}: ${title.trim()} (${CurrencyUtils.formatKes(amountCents, true)})"
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

    suspend fun sendRentDueAndOverdueReminders(
        invoices: List<Invoice>,
        actorName: String,
        respectCooldown: Boolean = true
    ): Result<Int> {
        return try {
            val uid = requireUserId()
            val nowMs = System.currentTimeMillis()
            val candidates = RentReminderEngine.evaluateInvoices(
                invoices = invoices,
                nowMs = nowMs,
                respectCooldown = respectCooldown
            ).take(50)

            if (candidates.isEmpty()) {
                return Result.success(0)
            }

            candidates.chunked(100).forEach { chunk ->
                val batch = db.batch()
                for (candidate in chunk) {
                    val inv = candidate.invoice
                    val notifRef = db.collection("notifications").document()
                    batch.set(
                        notifRef,
                        AppNotification(
                            recipientUid = inv.tenantUid,
                            senderUid = uid,
                            orgId = inv.orgId,
                            type = NotificationType.INVOICE.name,
                            title = candidate.title.take(140),
                            body = candidate.body.take(500),
                            relatedEntityId = inv.id,
                            read = false
                        ).toCreateMap()
                    )
                    val invRef = db.collection("invoices").document(inv.id)
                    batch.update(
                        invRef,
                        mapOf(
                            "lastReminderSentEpochMs" to nowMs,
                            "reminderCount" to (inv.reminderCount + 1L),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
                batch.commit().await()
            }

            val orgId = candidates.first().invoice.orgId
            db.collection("audit_logs").document().set(
                AuditLog(
                    orgId = orgId,
                    actorUid = uid,
                    actorName = actorName,
                    action = "SEND_RENT_REMINDERS",
                    entityType = "Invoice",
                    entityId = "batch_${candidates.size}",
                    details = "Dispatched ${candidates.size} rent due/overdue reminder(s) to tenants"
                ).toCreateMap()
            ).await()

            Result.success(candidates.size)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "invoices")
            Result.failure(e)
        }
    }
}

