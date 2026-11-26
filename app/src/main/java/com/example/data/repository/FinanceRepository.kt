package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.firebase.OperationType
import com.example.data.firebase.guardFirestoreReads
import com.example.data.firebase.handleFirestoreError
import com.example.domain.finance.CurrencyUtils
import com.example.domain.finance.FinancialEngine
import com.example.domain.finance.LeaseAutomationEngine
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
import com.example.domain.model.RecurringInvoiceSchedule
import com.example.domain.model.RecurringIssueMode
import com.example.domain.model.RecurringScheduleStatus
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
                    .filter { it.statusEnum() != InvoiceStatus.DRAFT }
                    .sortedByDescending { it.dueDateEpochMs }
            }
            .guardFirestoreReads(OperationType.LIST, "invoices")
    }

    fun observeRecurringSchedulesForLandlord(userId: String): Flow<List<RecurringInvoiceSchedule>> {
        val cleanUid = userId.trim()
        if (cleanUid.isBlank()) return flowOf(emptyList())
        return db.collection("recurring_invoices")
            .whereArrayContains("authorizedUids", cleanUid)
            .snapshots()
            .map { snap ->
                snap.toObjects(
                    RecurringInvoiceSchedule::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedBy { it.nextBillingEpochMs }
            }
            .guardFirestoreReads(OperationType.LIST, "recurring_invoices")
    }

    suspend fun upsertRecurringSchedule(
        lease: Lease,
        existingSchedule: RecurringInvoiceSchedule?,
        amountCents: Long,
        recurringExtraCents: Long,
        recurringExtraNote: String?,
        billingDayOfMonth: Long,
        leadDays: Long,
        scheduleStatus: RecurringScheduleStatus,
        issueMode: RecurringIssueMode,
        actorName: String
    ): Result<Unit> {
        val scheduleId = existingSchedule?.id?.takeIf { it.isNotBlank() } ?: lease.id
        val path = "recurring_invoices/$scheduleId"
        return try {
            val uid = requireUserId()
            val isOwner = lease.ownerUid == uid
            val clampedDay = billingDayOfMonth.coerceIn(1L, 28L)
            val clampedLead = leadDays.coerceIn(0L, 15L)
            val clampedAmount = amountCents.coerceAtLeast(1L)
            val clampedExtra = recurringExtraCents.coerceAtLeast(0L)
            val cleanExtraNote = recurringExtraNote?.trim()?.take(200)?.takeIf { it.isNotBlank() && clampedExtra > 0L }

            val currentPeriod = LeaseAutomationEngine.nairobiBillingPeriodForEpochMs(System.currentTimeMillis())
            val effectiveDay = if (isOwner) clampedDay else (existingSchedule?.billingDayOfMonth ?: lease.dueDayOfMonth).coerceIn(1L, 28L)
            val nextBillingMs = if (existingSchedule?.lastBilledPeriod == currentPeriod) {
                LeaseAutomationEngine.computeNextBillingEpochMs(currentPeriod, effectiveDay.toInt(), clampedLead.toInt())
            } else {
                LeaseAutomationEngine.computeCycleBillingDateMs(currentPeriod, effectiveDay.toInt(), clampedLead.toInt())
            }

            if (existingSchedule == null) {
                if (!isOwner) {
                    return Result.failure(SecurityException("Only the property owner can create recurring billing schedules."))
                }
                val newSchedule = RecurringInvoiceSchedule(
                    id = scheduleId,
                    orgId = lease.orgId,
                    propertyId = lease.propertyId,
                    propertyName = lease.propertyName,
                    unitId = lease.unitId,
                    unitNumber = lease.unitNumber,
                    leaseId = lease.id,
                    ownerUid = lease.ownerUid,
                    managerUids = lease.managerUids,
                    authorizedUids = lease.authorizedUids,
                    tenantUid = lease.tenantUid,
                    tenantName = lease.tenantName,
                    amountCents = clampedAmount,
                    recurringExtraCents = clampedExtra,
                    recurringExtraNote = cleanExtraNote,
                    billingDayOfMonth = clampedDay,
                    leadDays = clampedLead,
                    nextBillingEpochMs = nextBillingMs,
                    scheduleStatus = scheduleStatus.name,
                    issueMode = issueMode.name
                )
                db.collection("recurring_invoices").document(scheduleId).set(newSchedule.toCreateMap()).await()
            } else {
                val pricingChanged = clampedAmount != existingSchedule.amountCents ||
                    clampedExtra != existingSchedule.recurringExtraCents ||
                    cleanExtraNote != existingSchedule.recurringExtraNote ||
                    clampedDay != existingSchedule.billingDayOfMonth
                if (pricingChanged && !isOwner) {
                    return Result.failure(SecurityException("Only the property owner can edit recurring amount or billing day."))
                }
                val updates = mutableMapOf<String, Any>(
                    "leadDays" to clampedLead,
                    "nextBillingEpochMs" to nextBillingMs,
                    "scheduleStatus" to scheduleStatus.name,
                    "issueMode" to issueMode.name,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (isOwner) {
                    updates["amountCents"] = clampedAmount
                    updates["recurringExtraCents"] = clampedExtra
                    updates["recurringExtraNote"] = cleanExtraNote ?: FieldValue.delete()
                    updates["billingDayOfMonth"] = clampedDay
                }
                db.collection("recurring_invoices").document(scheduleId).update(updates).await()
            }

            db.collection("audit_logs").document().set(
                AuditLog(
                    orgId = lease.orgId,
                    actorUid = uid,
                    actorName = actorName,
                    action = "UPSERT_RECURRING_SCHEDULE",
                    entityType = "RecurringInvoiceSchedule",
                    entityId = scheduleId,
                    details = "Saved recurring schedule for Unit ${lease.unitNumber}: ${scheduleStatus.label}, ${issueMode.label}"
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
        actorName: String,
        schedules: List<RecurringInvoiceSchedule> = emptyList(),
        issueAsDraft: Boolean = false
    ): Result<Int> {
        return try {
            val uid = requireUserId()
            val cleanPeriod = billingPeriod.trim()
            require(CurrencyUtils.isValidBillingPeriod(cleanPeriod)) {
                "Billing period must be in YYYY-MM format (e.g. 2026-11)"
            }
            require(extraChargesCents >= 0L) { "Extra charges cannot be negative" }
            val activeLeases = leases.filter { it.statusEnum() == LeaseStatus.ACTIVE }
            val scheduleByLease = schedules.associateBy { it.leaseId }
            val nowMs = System.currentTimeMillis()
            var createdCount = 0

            for (lease in activeLeases) {
                val schedule = scheduleByLease[lease.id]
                val baseRent = schedule?.amountCents?.takeIf { it > 0L } ?: lease.rentCents
                val rentResult = LeaseAutomationEngine.computeBillingPeriodRentCents(lease, cleanPeriod, baseRent)
                if (rentResult.rentCents <= 0L) continue

                val deterministicId = "${lease.id}_$cleanPeriod".replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
                val docRef = db.collection("invoices").document(deterministicId)
                val schedRef = db.collection("recurring_invoices").document(schedule?.id?.takeIf { it.isNotBlank() } ?: lease.id)
                val exists = docRef.get().await().exists()
                if (exists) continue

                val prevOutstanding = existingInvoices
                    .filter {
                        it.leaseId == lease.id &&
                            it.billingPeriod != cleanPeriod &&
                            it.statusEnum() != InvoiceStatus.CANCELLED &&
                            it.statusEnum() != InvoiceStatus.DRAFT &&
                            it.outstandingCents > 0L
                    }
                    .sumOf { it.outstandingCents }

                val totalExtras = extraChargesCents.coerceAtLeast(0L) +
                    (schedule?.recurringExtraCents?.coerceAtLeast(0L) ?: 0L)
                val combinedExtraNote = listOfNotNull(
                    extraChargesNote?.trim()?.takeIf { it.isNotBlank() },
                    schedule?.recurringExtraNote?.trim()?.takeIf { it.isNotBlank() && (schedule.recurringExtraCents > 0L) }
                ).joinToString(" • ").take(300).takeIf { it.isNotBlank() }

                val totalDue = (rentResult.rentCents + totalExtras + prevOutstanding).coerceAtMost(2_000_000_000L)
                val creditToApply = if (issueAsDraft) 0L else minOf(lease.creditBalanceCents.coerceAtLeast(0L), totalDue)
                val outstanding = (totalDue - creditToApply).coerceAtLeast(0L)
                val status = when {
                    issueAsDraft -> InvoiceStatus.DRAFT
                    outstanding == 0L -> InvoiceStatus.PAID
                    creditToApply > 0L -> InvoiceStatus.PARTIALLY_PAID
                    else -> InvoiceStatus.ISSUED
                }

                val dueDay = (schedule?.billingDayOfMonth ?: lease.dueDayOfMonth).coerceIn(1L, 28L).toInt()
                val leadDays = (schedule?.leadDays ?: lease.billingLeadDays).coerceIn(0L, 15L).toInt()
                val invoiceNumber = "INV-${cleanPeriod.replace("-", "")}-${lease.unitNumber.take(6).uppercase()}"
                val dueDateMs = CurrencyUtils.computeDueDateEpochMs(cleanPeriod, dueDay)
                val billingDateMs = LeaseAutomationEngine.computeCycleBillingDateMs(cleanPeriod, dueDay, leadDays)
                val graceEndsMs = LeaseAutomationEngine.computeGracePeriodEndsMs(dueDateMs, lease.gracePeriodDays)

                val invoice = Invoice(
                    invoiceNumber = invoiceNumber,
                    scheduleId = schedule?.id?.takeIf { it.isNotBlank() } ?: lease.id,
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
                    billingPeriod = cleanPeriod,
                    billingDateEpochMs = billingDateMs,
                    rentCents = rentResult.rentCents,
                    extraChargesCents = totalExtras,
                    extraChargesNote = combinedExtraNote,
                    previousBalanceCents = prevOutstanding,
                    totalDueCents = totalDue,
                    paidCents = creditToApply,
                    outstandingCents = outstanding,
                    dueDateEpochMs = dueDateMs,
                    issuedAtEpochMs = if (issueAsDraft) null else nowMs,
                    paidAtEpochMs = if (!issueAsDraft && outstanding == 0L) nowMs else null,
                    status = status.name,
                    gracePeriodEndsEpochMs = graceEndsMs,
                    prorated = rentResult.prorated,
                    prorationNote = rentResult.prorationNote
                )

                val created = db.runTransaction { tx ->
                    if (tx.get(docRef).exists()) return@runTransaction false
                    val schedSnap = tx.get(schedRef)
                    tx.set(docRef, invoice.toCreateMap())
                    if (!issueAsDraft && creditToApply > 0L) {
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
                    if (schedSnap.exists()) {
                        val nextBillingMs = LeaseAutomationEngine.computeNextBillingEpochMs(cleanPeriod, dueDay, leadDays)
                        tx.update(
                            schedRef,
                            mapOf(
                                "lastBilledPeriod" to cleanPeriod,
                                "lastInvoiceId" to deterministicId,
                                "lastBilledAtEpochMs" to nowMs,
                                "nextBillingEpochMs" to nextBillingMs,
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        )
                    }
                    true
                }.await()
                if (!created) continue

                if (!issueAsDraft) {
                    db.collection("notifications").document().set(
                        AppNotification(
                            recipientUid = lease.tenantUid,
                            senderUid = uid,
                            orgId = lease.orgId,
                            type = NotificationType.INVOICE.name,
                            title = "New Rent Invoice (${CurrencyUtils.billingPeriodLabel(cleanPeriod)})",
                            body = "$invoiceNumber: ${CurrencyUtils.formatKes(outstanding, true)} due ${CurrencyUtils.formatDate(dueDateMs)}",
                            relatedEntityId = deterministicId,
                            read = false
                        ).toCreateMap()
                    ).await()
                }

                createdCount++
            }

            if (createdCount > 0 && activeLeases.isNotEmpty()) {
                db.collection("audit_logs").document().set(
                    AuditLog(
                        orgId = activeLeases.first().orgId,
                        actorUid = uid,
                        actorName = actorName,
                        action = if (issueAsDraft) "STAGE_DRAFT_INVOICES" else "GENERATE_INVOICES",
                        entityType = "Invoice",
                        entityId = cleanPeriod,
                        details = "${if (issueAsDraft) "Staged" else "Generated"} $createdCount monthly invoice(s) for $cleanPeriod"
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

    data class AutoBillingSweepSummary(
        val generatedCount: Int,
        val proratedCount: Int,
        val totalBilledCents: Long,
        val draftCount: Int = 0,
        val issuedCount: Int = 0
    )

    suspend fun runAutoBillingSweep(
        leases: List<Lease>,
        existingInvoices: List<Invoice>,
        actorName: String,
        schedules: List<RecurringInvoiceSchedule> = emptyList()
    ): Result<AutoBillingSweepSummary> {
        return try {
            val uid = requireUserId()
            val nowMs = System.currentTimeMillis()
            val candidates = LeaseAutomationEngine.evaluateAutoBillingCandidates(
                leases = leases,
                existingInvoices = existingInvoices,
                nowMs = nowMs,
                schedules = schedules
            )
            if (candidates.isEmpty()) {
                return Result.success(AutoBillingSweepSummary(0, 0, 0L, 0, 0))
            }

            var generatedCount = 0
            var proratedCount = 0
            var draftCount = 0
            var issuedCount = 0
            var totalBilledCents = 0L

            for (candidate in candidates) {
                val lease = candidate.lease
                val cleanPeriod = candidate.billingPeriod
                val deterministicId = "${lease.id}_$cleanPeriod".replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
                val docRef = db.collection("invoices").document(deterministicId)
                val leaseRef = db.collection("leases").document(lease.id)
                val scheduleId = candidate.scheduleId?.takeIf { it.isNotBlank() } ?: lease.id
                val schedRef = db.collection("recurring_invoices").document(scheduleId)
                val invoiceNumber = "INV-${cleanPeriod.replace("-", "")}-${lease.unitNumber.take(6).uppercase()}"

                val sweepResult = db.runTransaction { tx ->
                    if (tx.get(docRef).exists()) return@runTransaction null
                    val leaseSnap = tx.get(leaseRef)
                    val schedSnap = tx.get(schedRef)
                    if (leaseSnap.getString("status") != LeaseStatus.ACTIVE.name) return@runTransaction null

                    val freshMode = schedSnap.getString("issueMode")
                    val isDraft = freshMode == RecurringIssueMode.DRAFT_FOR_REVIEW.name ||
                        candidate.targetStatus == InvoiceStatus.DRAFT
                    if (schedSnap.exists() && schedSnap.getString("scheduleStatus") != RecurringScheduleStatus.ACTIVE.name) {
                        return@runTransaction null
                    }

                    val freshGraceDays = leaseSnap.getLong("gracePeriodDays") ?: lease.gracePeriodDays
                    val graceEndsMs = LeaseAutomationEngine.computeGracePeriodEndsMs(candidate.dueDateEpochMs, freshGraceDays)
                    val currentCredit = (leaseSnap.getLong("creditBalanceCents") ?: lease.creditBalanceCents).coerceAtLeast(0L)
                    val totalDue = (candidate.rentCents + candidate.extraChargesCents + candidate.previousBalanceCents)
                        .coerceAtMost(2_000_000_000L)

                    val creditToApply = if (isDraft) 0L else minOf(currentCredit, totalDue)
                    val outstanding = (totalDue - creditToApply).coerceAtLeast(0L)
                    val status = when {
                        isDraft -> InvoiceStatus.DRAFT
                        outstanding == 0L -> InvoiceStatus.PAID
                        creditToApply > 0L -> InvoiceStatus.PARTIALLY_PAID
                        else -> InvoiceStatus.ISSUED
                    }

                    val invoice = Invoice(
                        invoiceNumber = invoiceNumber,
                        scheduleId = scheduleId,
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
                        billingPeriod = cleanPeriod,
                        billingDateEpochMs = candidate.billingDateEpochMs,
                        rentCents = candidate.rentCents,
                        extraChargesCents = candidate.extraChargesCents,
                        extraChargesNote = candidate.extraChargesNote,
                        previousBalanceCents = candidate.previousBalanceCents,
                        totalDueCents = totalDue,
                        paidCents = creditToApply,
                        outstandingCents = outstanding,
                        dueDateEpochMs = candidate.dueDateEpochMs,
                        issuedAtEpochMs = if (isDraft) null else nowMs,
                        paidAtEpochMs = if (!isDraft && outstanding == 0L) nowMs else null,
                        status = status.name,
                        gracePeriodEndsEpochMs = graceEndsMs,
                        prorated = candidate.prorated,
                        prorationNote = candidate.prorationNote
                    )
                    tx.set(docRef, invoice.toCreateMap())
                    if (!isDraft && creditToApply > 0L) {
                        tx.update(
                            leaseRef,
                            mapOf(
                                "creditBalanceCents" to (currentCredit - creditToApply).coerceAtLeast(0L),
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        )
                    }
                    if (schedSnap.exists()) {
                        val schedDay = (schedSnap.getLong("billingDayOfMonth") ?: lease.dueDayOfMonth).coerceIn(1L, 28L).toInt()
                        val schedLead = (schedSnap.getLong("leadDays") ?: lease.billingLeadDays).coerceIn(0L, 15L).toInt()
                        val nextBillingMs = LeaseAutomationEngine.computeNextBillingEpochMs(cleanPeriod, schedDay, schedLead)
                        tx.update(
                            schedRef,
                            mapOf(
                                "lastBilledPeriod" to cleanPeriod,
                                "lastInvoiceId" to deterministicId,
                                "lastBilledAtEpochMs" to nowMs,
                                "nextBillingEpochMs" to nextBillingMs,
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        )
                    }
                    Triple(totalDue, outstanding, isDraft)
                }.await() ?: continue

                generatedCount++
                if (candidate.prorated) proratedCount++
                totalBilledCents += sweepResult.first
                if (sweepResult.third) {
                    draftCount++
                } else {
                    issuedCount++
                    val outstandingAfterCredit = sweepResult.second
                    val prorationPrefix = if (candidate.prorated) "Prorated " else ""
                    db.collection("notifications").document().set(
                        AppNotification(
                            recipientUid = lease.tenantUid,
                            senderUid = uid,
                            orgId = lease.orgId,
                            type = NotificationType.INVOICE.name,
                            title = "${prorationPrefix}Rent Invoice (${CurrencyUtils.billingPeriodLabel(cleanPeriod)})",
                            body = "$invoiceNumber: ${CurrencyUtils.formatKes(outstandingAfterCredit, true)} due ${CurrencyUtils.formatDate(candidate.dueDateEpochMs)}",
                            relatedEntityId = deterministicId,
                            read = false
                        ).toCreateMap()
                    ).await()
                }
            }

            if (generatedCount > 0) {
                db.collection("audit_logs").document().set(
                    AuditLog(
                        orgId = candidates.first().lease.orgId,
                        actorUid = uid,
                        actorName = actorName,
                        action = "AUTO_BILL_MONTHLY_RENT",
                        entityType = "Invoice",
                        entityId = candidates.first().billingPeriod,
                        details = "Auto-billed $generatedCount invoice(s) ($issuedCount issued, $draftCount draft, $proratedCount prorated) totaling ${CurrencyUtils.formatKes(totalBilledCents, true)}"
                    ).toCreateMap()
                ).await()
            }

            Result.success(
                AutoBillingSweepSummary(
                    generatedCount = generatedCount,
                    proratedCount = proratedCount,
                    totalBilledCents = totalBilledCents,
                    draftCount = draftCount,
                    issuedCount = issuedCount
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "invoices")
            Result.failure(e)
        }
    }

    suspend fun issueDraftInvoice(
        invoice: Invoice,
        lease: Lease,
        extraChargesCents: Long,
        extraChargesNote: String?,
        actorName: String
    ): Result<Unit> {
        val path = "invoices/${invoice.id}"
        return try {
            val uid = requireUserId()
            require(invoice.statusEnum() == InvoiceStatus.DRAFT) { "Only draft invoices can be issued." }
            require(extraChargesCents >= 0L) { "Extra charges cannot be negative." }

            val nowMs = System.currentTimeMillis()
            val invRef = db.collection("invoices").document(invoice.id)
            val leaseRef = db.collection("leases").document(lease.id)
            val cleanNote = extraChargesNote?.trim()?.take(300)?.takeIf { it.isNotBlank() && extraChargesCents > 0L }

            val result = db.runTransaction { tx ->
                val invSnap = tx.get(invRef)
                val leaseSnap = tx.get(leaseRef)
                check(invSnap.getString("status") == InvoiceStatus.DRAFT.name) {
                    "This invoice has already been issued or updated."
                }
                val liveCredit = (leaseSnap.getLong("creditBalanceCents") ?: lease.creditBalanceCents).coerceAtLeast(0L)
                val liveGraceDays = leaseSnap.getLong("gracePeriodDays") ?: lease.gracePeriodDays
                val liveLease = lease.copy(
                    creditBalanceCents = liveCredit,
                    gracePeriodDays = liveGraceDays
                )
                val eval = LeaseAutomationEngine.evaluateDraftIssuance(
                    draftInvoice = invoice.copy(
                        rentCents = invSnap.getLong("rentCents") ?: invoice.rentCents,
                        previousBalanceCents = invSnap.getLong("previousBalanceCents") ?: invoice.previousBalanceCents,
                        lateFeeCents = invSnap.getLong("lateFeeCents") ?: invoice.lateFeeCents,
                        paidCents = invSnap.getLong("paidCents") ?: invoice.paidCents,
                        dueDateEpochMs = invSnap.getLong("dueDateEpochMs") ?: invoice.dueDateEpochMs
                    ),
                    lease = liveLease,
                    extraChargesCents = extraChargesCents,
                    nowMs = nowMs
                )

                val invUpdates = mutableMapOf<String, Any>(
                    "extraChargesCents" to extraChargesCents,
                    "totalDueCents" to eval.totalDueCents,
                    "paidCents" to eval.newPaidCents,
                    "outstandingCents" to eval.newOutstandingCents,
                    "gracePeriodEndsEpochMs" to eval.gracePeriodEndsEpochMs,
                    "issuedAtEpochMs" to eval.issuedAtEpochMs,
                    "status" to eval.newStatus.name,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (cleanNote != null) {
                    invUpdates["extraChargesNote"] = cleanNote
                } else {
                    invUpdates["extraChargesNote"] = FieldValue.delete()
                }
                if (eval.paidAtEpochMs != null) {
                    invUpdates["paidAtEpochMs"] = eval.paidAtEpochMs
                }
                tx.update(invRef, invUpdates)

                if (eval.creditToApplyCents > 0L) {
                    tx.update(
                        leaseRef,
                        mapOf(
                            "creditBalanceCents" to (liveCredit - eval.creditToApplyCents).coerceAtLeast(0L),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
                eval
            }.await()

            db.collection("notifications").document().set(
                AppNotification(
                    recipientUid = invoice.tenantUid,
                    senderUid = uid,
                    orgId = invoice.orgId,
                    type = NotificationType.INVOICE.name,
                    title = "Rent Invoice Issued (${CurrencyUtils.billingPeriodLabel(invoice.billingPeriod)})",
                    body = "${invoice.invoiceNumber}: ${CurrencyUtils.formatKes(result.newOutstandingCents, true)} due ${CurrencyUtils.formatDate(invoice.dueDateEpochMs)}",
                    relatedEntityId = invoice.id,
                    read = false
                ).toCreateMap()
            ).await()

            db.collection("audit_logs").document().set(
                AuditLog(
                    orgId = invoice.orgId,
                    actorUid = uid,
                    actorName = actorName,
                    action = "ISSUE_DRAFT_INVOICE",
                    entityType = "Invoice",
                    entityId = invoice.id,
                    details = "Issued draft ${invoice.invoiceNumber} (${CurrencyUtils.formatKes(result.totalDueCents, true)}) -> ${result.newStatus.label}"
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

    suspend fun cancelDraftInvoice(
        invoice: Invoice,
        reason: String?,
        actorName: String
    ): Result<Unit> {
        val path = "invoices/${invoice.id}"
        return try {
            val uid = requireUserId()
            require(invoice.statusEnum() == InvoiceStatus.DRAFT) { "Only draft invoices can be cancelled directly." }
            db.collection("invoices").document(invoice.id).update(
                mapOf(
                    "status" to InvoiceStatus.CANCELLED.name,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            db.collection("audit_logs").document().set(
                AuditLog(
                    orgId = invoice.orgId,
                    actorUid = uid,
                    actorName = actorName,
                    action = "CANCEL_DRAFT_INVOICE",
                    entityType = "Invoice",
                    entityId = invoice.id,
                    details = "Cancelled draft ${invoice.invoiceNumber}${reason?.trim()?.takeIf { it.isNotBlank() }?.let { ": $it" } ?: ""}"
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

                val paidAtNow = System.currentTimeMillis()
                for (upd in plan.updates) {
                    val invRef = db.collection("invoices").document(upd.invoiceId)
                    val invUpdates = mutableMapOf<String, Any>(
                        "paidCents" to upd.newPaidCents,
                        "outstandingCents" to upd.newOutstandingCents,
                        "status" to upd.newStatus.name,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                    if (upd.newStatus == InvoiceStatus.PAID) {
                        invUpdates["paidAtEpochMs"] = paidAtNow
                    }
                    tx.update(invRef, invUpdates)
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

            val invoiceRollbacks = mutableListOf<Triple<Invoice, Long, Long>>()
            val paidInvoices = leaseInvoices
                .filter {
                    it.paidCents > 0L &&
                        it.statusEnum() != InvoiceStatus.CANCELLED &&
                        it.statusEnum() != InvoiceStatus.DRAFT
                }
                .sortedByDescending { it.dueDateEpochMs }

            for (inv in paidInvoices) {
                if (remainingToClawBack <= 0L) break
                val revert = minOf(remainingToClawBack, inv.paidCents)
                val newPaid = (inv.paidCents - revert).coerceAtLeast(0L)
                val newOut = (inv.totalDueCents - newPaid).coerceAtLeast(0L)
                invoiceRollbacks += Triple(inv, newPaid, newOut)
                remainingToClawBack -= revert
            }

            db.runTransaction { tx ->
                val paymentRef = db.collection("payments").document(payment.id)
                val leaseRef = db.collection("leases").document(lease.id)
                for ((inv, newPaid, newOut) in invoiceRollbacks) {
                    val newStatus = when {
                        newPaid > 0L -> InvoiceStatus.PARTIALLY_PAID
                        inv.issuedAtEpochMs != null -> InvoiceStatus.ISSUED
                        else -> InvoiceStatus.UNPAID
                    }
                    val rollbackMap = mutableMapOf<String, Any>(
                        "paidCents" to newPaid,
                        "outstandingCents" to newOut,
                        "status" to newStatus.name,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                    if (newOut > 0L) {
                        rollbackMap["paidAtEpochMs"] = FieldValue.delete()
                    }
                    tx.update(db.collection("invoices").document(inv.id), rollbackMap)
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

    data class LateFeeSweepSummary(
        val assessedCount: Int,
        val totalFeeCents: Long
    )

    suspend fun runLateFeeAssessmentSweep(
        invoices: List<Invoice>,
        leases: List<Lease>,
        actorName: String
    ): Result<LateFeeSweepSummary> {
        return try {
            val uid = requireUserId()
            val nowMs = System.currentTimeMillis()
            val candidates = LeaseAutomationEngine.evaluateOverdueLateFees(invoices, leases, nowMs)
            if (candidates.isEmpty()) {
                return Result.success(LateFeeSweepSummary(0, 0L))
            }

            var assessedCount = 0
            var totalFeeCents = 0L

            for (candidate in candidates) {
                val inv = candidate.invoice
                val invRef = db.collection("invoices").document(inv.id)
                val appliedFee = db.runTransaction { tx ->
                    val snap = tx.get(invRef)
                    val freshStatus = snap.getString("status") ?: return@runTransaction 0L
                    if (freshStatus == InvoiceStatus.DRAFT.name ||
                        freshStatus == InvoiceStatus.PAID.name ||
                        freshStatus == InvoiceStatus.CANCELLED.name
                    ) {
                        return@runTransaction 0L
                    }
                    if (snap.getLong("lateFeeAppliedAtEpochMs") != null ||
                        snap.getBoolean("lateFeeWaived") == true ||
                        (snap.getLong("lateFeeCents") ?: 0L) > 0L
                    ) {
                        return@runTransaction 0L
                    }
                    val freshOut = snap.getLong("outstandingCents") ?: 0L
                    val freshTotal = snap.getLong("totalDueCents") ?: 0L
                    if (freshOut <= 0L) return@runTransaction 0L

                    val feeCents = LeaseAutomationEngine.computeLateFeeCents(freshOut, candidate.lateFeeRatePercent)
                    if (feeCents <= 0L) return@runTransaction 0L

                    val newTotal = (freshTotal + feeCents).coerceAtMost(2_000_000_000L)
                    val newOut = (freshOut + feeCents).coerceAtMost(2_000_000_000L)

                    tx.update(
                        invRef,
                        mapOf(
                            "lateFeeCents" to feeCents,
                            "lateFeeRatePercent" to candidate.lateFeeRatePercent,
                            "lateFeeAppliedAtEpochMs" to nowMs,
                            "gracePeriodEndsEpochMs" to candidate.gracePeriodEndsEpochMs,
                            "totalDueCents" to newTotal,
                            "outstandingCents" to newOut,
                            "status" to InvoiceStatus.OVERDUE.name,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                    feeCents
                }.await()

                if (appliedFee <= 0L) continue
                assessedCount++
                totalFeeCents += appliedFee

                db.collection("notifications").document().set(
                    AppNotification(
                        recipientUid = inv.tenantUid,
                        senderUid = uid,
                        orgId = inv.orgId,
                        type = NotificationType.INVOICE.name,
                        title = "Late Fee Assessed (${inv.invoiceNumber})",
                        body = "A ${LeaseAutomationEngine.formatPercent(candidate.lateFeeRatePercent)} late fee (${CurrencyUtils.formatKes(appliedFee, true)}) was added to ${inv.invoiceNumber} after the ${candidate.lease.gracePeriodDays}-day grace period.",
                        relatedEntityId = inv.id,
                        read = false
                    ).toCreateMap()
                ).await()
            }

            if (assessedCount > 0) {
                val orgId = candidates.first().invoice.orgId
                db.collection("audit_logs").document().set(
                    AuditLog(
                        orgId = orgId,
                        actorUid = uid,
                        actorName = actorName,
                        action = "ASSESS_LATE_FEES",
                        entityType = "Invoice",
                        entityId = "sweep_$assessedCount",
                        details = "Assessed late fees on $assessedCount overdue invoice(s) totaling ${CurrencyUtils.formatKes(totalFeeCents, true)}"
                    ).toCreateMap()
                ).await()
            }

            Result.success(LateFeeSweepSummary(assessedCount, totalFeeCents))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "invoices")
            Result.failure(e)
        }
    }

    suspend fun waiveInvoiceLateFee(
        invoice: Invoice,
        reason: String,
        actorName: String
    ): Result<Unit> {
        val path = "invoices/${invoice.id}"
        return try {
            val uid = requireUserId()
            if (invoice.ownerUid != uid) {
                return Result.failure(SecurityException("Only the property owner can waive late fees."))
            }
            val cleanReason = reason.trim().take(300)
            require(cleanReason.length >= 3) { "Please provide an audit reason (at least 3 characters)." }

            val nowMs = System.currentTimeMillis()
            val invRef = db.collection("invoices").document(invoice.id)
            val leaseRef = db.collection("leases").document(invoice.leaseId)

            val waivedAmount = db.runTransaction { tx ->
                val snap = tx.get(invRef)
                val freshWaived = snap.getBoolean("lateFeeWaived") == true
                val freshFee = snap.getLong("lateFeeCents") ?: 0L
                check(!freshWaived && freshFee > 0L) {
                    "No active late fee found to waive on this invoice."
                }
                val freshTotal = snap.getLong("totalDueCents") ?: invoice.totalDueCents
                val freshPaid = snap.getLong("paidCents") ?: invoice.paidCents
                val freshDueMs = snap.getLong("dueDateEpochMs") ?: invoice.dueDateEpochMs

                val waiver = LeaseAutomationEngine.computeLateFeeWaiver(
                    invoice = invoice.copy(
                        lateFeeCents = freshFee,
                        totalDueCents = freshTotal,
                        paidCents = freshPaid,
                        dueDateEpochMs = freshDueMs
                    ),
                    nowMs = nowMs
                )

                if (waiver.creditRefundCents > 0L) {
                    val leaseSnap = tx.get(leaseRef)
                    val currentCredit = leaseSnap.getLong("creditBalanceCents") ?: 0L
                    val updatedCredit = (currentCredit + waiver.creditRefundCents).coerceAtMost(1_000_000_000L)
                    tx.update(
                        leaseRef,
                        mapOf(
                            "creditBalanceCents" to updatedCredit,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }

                tx.update(
                    invRef,
                    mapOf(
                        "lateFeeCents" to 0L,
                        "lateFeeWaived" to true,
                        "lateFeeWaivedReason" to cleanReason,
                        "lateFeeWaivedAtEpochMs" to nowMs,
                        "totalDueCents" to waiver.newTotalDueCents,
                        "paidCents" to waiver.newPaidCents,
                        "outstandingCents" to waiver.newOutstandingCents,
                        "status" to waiver.newStatus.name,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                waiver.waivedFeeCents
            }.await()

            db.collection("notifications").document().set(
                AppNotification(
                    recipientUid = invoice.tenantUid,
                    senderUid = uid,
                    orgId = invoice.orgId,
                    type = NotificationType.INVOICE.name,
                    title = "Late Fee Waived (${invoice.invoiceNumber})",
                    body = "${CurrencyUtils.formatKes(waivedAmount, true)} late fee on ${invoice.invoiceNumber} was waived: $cleanReason",
                    relatedEntityId = invoice.id,
                    read = false
                ).toCreateMap()
            ).await()

            db.collection("audit_logs").document().set(
                AuditLog(
                    orgId = invoice.orgId,
                    actorUid = uid,
                    actorName = actorName,
                    action = "WAIVE_LATE_FEE",
                    entityType = "Invoice",
                    entityId = invoice.id,
                    details = "Waived ${CurrencyUtils.formatKes(waivedAmount, true)} late fee on ${invoice.invoiceNumber}: $cleanReason"
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

