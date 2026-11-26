package com.example.domain.finance

import com.example.domain.model.EscalationType
import com.example.domain.model.Invoice
import com.example.domain.model.InvoiceStatus
import com.example.domain.model.Lease
import com.example.domain.model.LeaseStatus
import com.example.domain.model.MoveOutNoticeStatus
import com.example.domain.model.RecurringInvoiceSchedule
import com.example.domain.model.RecurringIssueMode
import com.example.domain.model.RecurringScheduleStatus
import com.example.domain.model.RenewalMode
import com.example.domain.model.RenewalOfferStatus
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToLong

enum class RenewalActionType(val label: String) {
    AUTO_RENEW("Auto-Renew"),
    SEND_OFFER("Send Renewal Offer")
}

data class RenewalSweepCandidate(
    val lease: Lease,
    val actionType: RenewalActionType,
    val projectedRentCents: Long,
    val projectedEndDateEpochMs: Long,
    val daysUntilExpiry: Int
)

data class LateFeeSweepCandidate(
    val invoice: Invoice,
    val lease: Lease,
    val lateFeeCents: Long,
    val lateFeeRatePercent: Double,
    val gracePeriodEndsEpochMs: Long,
    val daysPastGrace: Int,
    val newTotalDueCents: Long,
    val newOutstandingCents: Long
)

data class LateFeeWaiverResult(
    val waivedFeeCents: Long,
    val newTotalDueCents: Long,
    val newPaidCents: Long,
    val newOutstandingCents: Long,
    val creditRefundCents: Long,
    val newStatus: InvoiceStatus
)

data class MoveOutNoticeCompliance(
    val isCompliant: Boolean,
    val noticeDaysGiven: Int,
    val requiredNoticeDays: Int,
    val isEarlyTermination: Boolean
)

data class ProratedRentResult(
    val rentCents: Long,
    val prorated: Boolean,
    val occupiedDays: Int,
    val daysInMonth: Int,
    val prorationNote: String?
)

data class AutoBillingCandidate(
    val lease: Lease,
    val billingPeriod: String,
    val rentCents: Long,
    val previousBalanceCents: Long,
    val creditToApplyCents: Long,
    val totalDueCents: Long,
    val outstandingCents: Long,
    val dueDateEpochMs: Long,
    val gracePeriodEndsEpochMs: Long,
    val prorated: Boolean,
    val prorationNote: String?,
    val scheduleId: String? = null,
    val extraChargesCents: Long = 0L,
    val extraChargesNote: String? = null,
    val billingDateEpochMs: Long = dueDateEpochMs,
    val targetStatus: InvoiceStatus = InvoiceStatus.ISSUED,
    val issuedAtEpochMs: Long? = null,
    val paidAtEpochMs: Long? = null
)

data class DraftIssuanceResult(
    val totalDueCents: Long,
    val creditToApplyCents: Long,
    val newPaidCents: Long,
    val newOutstandingCents: Long,
    val gracePeriodEndsEpochMs: Long,
    val issuedAtEpochMs: Long,
    val paidAtEpochMs: Long?,
    val newStatus: InvoiceStatus
)

object LeaseAutomationEngine {
    const val DAY_MS = 86_400_000L
    const val MONTH_DAYS = 30L
    private val nairobiTz: TimeZone = TimeZone.getTimeZone("Africa/Nairobi")

    fun computeEscalatedRentCents(
        currentRentCents: Long,
        escalationType: EscalationType,
        escalationPercent: Double,
        escalationFixedCents: Long
    ): Long {
        val base = currentRentCents.coerceAtLeast(1L)
        return when (escalationType) {
            EscalationType.NONE -> base
            EscalationType.PERCENT -> {
                val boundedPct = escalationPercent.coerceIn(0.0, 100.0)
                val increase = (base.toDouble() * (boundedPct / 100.0)).roundToLong().coerceAtLeast(0L)
                (base + increase).coerceAtMost(1_000_000_000L)
            }
            EscalationType.FIXED_KES -> {
                val fixed = escalationFixedCents.coerceAtLeast(0L)
                (base + fixed).coerceAtMost(1_000_000_000L)
            }
        }
    }

    fun computeEscalatedRentCents(lease: Lease): Long = computeEscalatedRentCents(
        currentRentCents = lease.rentCents,
        escalationType = lease.escalationTypeEnum(),
        escalationPercent = lease.escalationPercent,
        escalationFixedCents = lease.escalationFixedCents
    )

    fun computeExtendedEndDateMs(
        currentEndDateEpochMs: Long,
        renewalDurationMonths: Long,
        nowMs: Long = System.currentTimeMillis()
    ): Long {
        val base = maxOf(currentEndDateEpochMs, nowMs)
        val months = renewalDurationMonths.coerceIn(1L, 60L)
        return base + months * MONTH_DAYS * DAY_MS
    }

    fun computeGracePeriodEndsMs(
        dueDateEpochMs: Long,
        gracePeriodDays: Long
    ): Long {
        val days = gracePeriodDays.coerceIn(0L, 30L)
        return dueDateEpochMs + days * DAY_MS
    }

    fun computeLateFeeCents(
        overdueBalanceCents: Long,
        lateFeePercent: Double
    ): Long {
        if (overdueBalanceCents <= 0L) return 0L
        val boundedRate = lateFeePercent.coerceIn(0.0, 50.0)
        if (boundedRate <= 0.0) return 0L
        return (overdueBalanceCents.toDouble() * (boundedRate / 100.0))
            .roundToLong()
            .coerceIn(0L, 500_000_000L)
    }

    fun evaluateMoveOutNotice(
        lease: Lease,
        requestedMoveOutEpochMs: Long,
        nowMs: Long = System.currentTimeMillis()
    ): MoveOutNoticeCompliance {
        val requiredDays = lease.noticePeriodDays.coerceIn(1L, 90L).toInt()
        val noticeDaysGiven = ((requestedMoveOutEpochMs - nowMs) / DAY_MS).toInt().coerceAtLeast(0)
        val isCompliant = requestedMoveOutEpochMs >= nowMs + requiredDays * DAY_MS
        val isEarly = lease.endDateEpochMs > 0L && requestedMoveOutEpochMs < lease.endDateEpochMs
        return MoveOutNoticeCompliance(
            isCompliant = isCompliant,
            noticeDaysGiven = noticeDaysGiven,
            requiredNoticeDays = requiredDays,
            isEarlyTermination = isEarly
        )
    }

    fun nairobiBillingPeriodForEpochMs(epochMs: Long): String {
        val cal = Calendar.getInstance(nairobiTz).apply { timeInMillis = epochMs }
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH) + 1
        return String.format(Locale.US, "%04d-%02d", year, month)
    }

    fun nextBillingPeriod(billingPeriod: String): String {
        val parts = billingPeriod.trim().split("-")
        val year = parts.getOrNull(0)?.toIntOrNull()
        val month = parts.getOrNull(1)?.toIntOrNull()
        if (year == null || month == null || month !in 1..12) {
            return nairobiBillingPeriodForEpochMs(System.currentTimeMillis() + 31L * DAY_MS)
        }
        val cal = Calendar.getInstance(nairobiTz).apply {
            clear()
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month - 1)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 12)
            add(Calendar.MONTH, 1)
        }
        return String.format(Locale.US, "%04d-%02d", cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1)
    }

    fun computeCycleBillingDateMs(
        billingPeriod: String,
        dueDayOfMonth: Int,
        leadDays: Int
    ): Long {
        val parts = billingPeriod.trim().split("-")
        val year = parts.getOrNull(0)?.toIntOrNull()
        val month = parts.getOrNull(1)?.toIntOrNull()
        if (year == null || month == null || month !in 1..12) {
            return System.currentTimeMillis()
        }
        return Calendar.getInstance(nairobiTz).apply {
            clear()
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month - 1)
            val maxDay = getActualMaximum(Calendar.DAY_OF_MONTH)
            set(Calendar.DAY_OF_MONTH, dueDayOfMonth.coerceIn(1, maxDay))
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_MONTH, -leadDays.coerceIn(0, 15))
        }.timeInMillis
    }

    fun computeNextBillingEpochMs(
        afterPeriod: String,
        dueDayOfMonth: Int,
        leadDays: Int
    ): Long = computeCycleBillingDateMs(
        billingPeriod = nextBillingPeriod(afterPeriod),
        dueDayOfMonth = dueDayOfMonth,
        leadDays = leadDays
    )

    fun buildScheduleForLease(
        lease: Lease,
        nowMs: Long = System.currentTimeMillis(),
        recurringExtraCents: Long = 0L,
        recurringExtraNote: String? = null,
        issueMode: RecurringIssueMode = RecurringIssueMode.AUTO_ISSUE,
        existingSchedule: RecurringInvoiceSchedule? = null
    ): RecurringInvoiceSchedule {
        val billingDay = lease.dueDayOfMonth.coerceIn(1L, 28L)
        val lead = lease.billingLeadDays.coerceIn(0L, 15L)
        val currentPeriod = nairobiBillingPeriodForEpochMs(nowMs)
        val computedNextBilling = if (existingSchedule?.lastBilledPeriod == currentPeriod) {
            computeNextBillingEpochMs(currentPeriod, billingDay.toInt(), lead.toInt())
        } else {
            computeCycleBillingDateMs(currentPeriod, billingDay.toInt(), lead.toInt())
        }
        val status = when {
            lease.statusEnum() != LeaseStatus.ACTIVE -> RecurringScheduleStatus.ENDED
            !lease.autoBillingEnabled -> RecurringScheduleStatus.PAUSED
            existingSchedule?.scheduleStatusEnum() == RecurringScheduleStatus.PAUSED -> RecurringScheduleStatus.PAUSED
            else -> RecurringScheduleStatus.ACTIVE
        }
        return RecurringInvoiceSchedule(
            id = existingSchedule?.id?.takeIf { it.isNotBlank() } ?: lease.id,
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
            amountCents = lease.rentCents.coerceAtLeast(1L),
            recurringExtraCents = recurringExtraCents.coerceAtLeast(0L),
            recurringExtraNote = recurringExtraNote?.trim()?.take(200)?.takeIf { it.isNotBlank() },
            billingDayOfMonth = billingDay,
            leadDays = lead,
            nextBillingEpochMs = existingSchedule?.nextBillingEpochMs?.takeIf { it > 0L && existingSchedule.lastBilledPeriod == currentPeriod }
                ?: computedNextBilling,
            lastBilledPeriod = existingSchedule?.lastBilledPeriod,
            lastInvoiceId = existingSchedule?.lastInvoiceId,
            lastBilledAtEpochMs = existingSchedule?.lastBilledAtEpochMs,
            scheduleStatus = status.name,
            issueMode = issueMode.name
        )
    }

    fun computeBillingPeriodRentCents(
        lease: Lease,
        billingPeriod: String,
        baseRentOverrideCents: Long? = null
    ): ProratedRentResult {
        val monthlyBaseRent = (baseRentOverrideCents ?: lease.rentCents).coerceAtLeast(0L)
        val parts = billingPeriod.trim().split("-")
        if (parts.size != 2) {
            return ProratedRentResult(monthlyBaseRent, false, 30, 30, null)
        }
        val year = parts[0].toIntOrNull() ?: return ProratedRentResult(monthlyBaseRent, false, 30, 30, null)
        val monthIndex = (parts[1].toIntOrNull() ?: return ProratedRentResult(monthlyBaseRent, false, 30, 30, null)) - 1

        val startCal = Calendar.getInstance(nairobiTz).apply {
            clear()
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, monthIndex)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val daysInMonth = startCal.getActualMaximum(Calendar.DAY_OF_MONTH).coerceAtLeast(28)
        val monthStartMs = startCal.timeInMillis
        val monthEndCal = (startCal.clone() as Calendar).apply {
            add(Calendar.MONTH, 1)
        }
        val monthEndExclusiveMs = monthEndCal.timeInMillis

        val moveOutStatus = lease.moveOutNoticeStatusEnum()
        val moveOutMs = lease.requestedMoveOutEpochMs
        if (moveOutStatus == MoveOutNoticeStatus.ACKNOWLEDGED && moveOutMs != null && moveOutMs > 0L) {
            if (moveOutMs < monthStartMs) {
                return ProratedRentResult(
                    rentCents = 0L,
                    prorated = true,
                    occupiedDays = 0,
                    daysInMonth = daysInMonth,
                    prorationNote = "Vacated prior to ${CurrencyUtils.billingPeriodLabel(billingPeriod)}"
                )
            }
            if (moveOutMs < monthEndExclusiveMs) {
                val moveOutCal = Calendar.getInstance(nairobiTz).apply { timeInMillis = moveOutMs }
                val occupiedDays = moveOutCal.get(Calendar.DAY_OF_MONTH).coerceIn(1, daysInMonth)
                if (occupiedDays < daysInMonth) {
                    val proratedRent = (monthlyBaseRent.toDouble() * (occupiedDays.toDouble() / daysInMonth.toDouble()))
                        .roundToLong()
                        .coerceIn(1L, monthlyBaseRent.coerceAtLeast(1L))
                    return ProratedRentResult(
                        rentCents = proratedRent,
                        prorated = true,
                        occupiedDays = occupiedDays,
                        daysInMonth = daysInMonth,
                        prorationNote = "Prorated $occupiedDays/$daysInMonth days (move-out ${CurrencyUtils.formatDate(moveOutMs)})"
                    )
                }
            }
        }
        return ProratedRentResult(
            rentCents = monthlyBaseRent,
            prorated = false,
            occupiedDays = daysInMonth,
            daysInMonth = daysInMonth,
            prorationNote = null
        )
    }

    fun evaluateAutoBillingCandidates(
        leases: List<Lease>,
        existingInvoices: List<Invoice>,
        nowMs: Long = System.currentTimeMillis(),
        schedules: List<RecurringInvoiceSchedule> = emptyList()
    ): List<AutoBillingCandidate> {
        val billingPeriod = nairobiBillingPeriodForEpochMs(nowMs)
        val scheduleByLeaseId = schedules.associateBy { it.leaseId }
        return leases.mapNotNull { lease ->
            if (lease.statusEnum() != LeaseStatus.ACTIVE) return@mapNotNull null
            val schedule = scheduleByLeaseId[lease.id]
            if (schedule != null) {
                if (schedule.scheduleStatusEnum() != RecurringScheduleStatus.ACTIVE) return@mapNotNull null
            } else if (!lease.autoBillingEnabled) {
                return@mapNotNull null
            }

            val baseRentCents = schedule?.amountCents?.takeIf { it > 0L } ?: lease.rentCents
            if (baseRentCents <= 0L) return@mapNotNull null

            val deterministicId = "${lease.id}_$billingPeriod".replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
            val alreadyExists = existingInvoices.any { inv ->
                inv.id == deterministicId ||
                    (inv.leaseId == lease.id && inv.billingPeriod == billingPeriod && inv.statusEnum() != InvoiceStatus.CANCELLED)
            }
            if (alreadyExists) return@mapNotNull null

            val dueDay = (schedule?.billingDayOfMonth ?: lease.dueDayOfMonth).coerceIn(1L, 28L).toInt()
            val leadDays = (schedule?.leadDays ?: lease.billingLeadDays).coerceIn(0L, 15L).toInt()
            val dueDateMs = CurrencyUtils.computeDueDateEpochMs(billingPeriod, dueDay)
            val billingDateMs = computeCycleBillingDateMs(billingPeriod, dueDay, leadDays)
            if (nowMs < billingDateMs) return@mapNotNull null

            val rentResult = computeBillingPeriodRentCents(lease, billingPeriod, baseRentCents)
            if (rentResult.rentCents <= 0L) return@mapNotNull null

            val recurringExtras = schedule?.recurringExtraCents?.coerceAtLeast(0L) ?: 0L
            val recurringExtrasNote = schedule?.recurringExtraNote?.takeIf { recurringExtras > 0L }

            val prevOutstanding = existingInvoices
                .filter {
                    it.leaseId == lease.id &&
                        it.billingPeriod != billingPeriod &&
                        it.statusEnum() != InvoiceStatus.CANCELLED &&
                        it.statusEnum() != InvoiceStatus.DRAFT &&
                        it.outstandingCents > 0L
                }
                .sumOf { it.outstandingCents }

            val totalDue = (rentResult.rentCents + recurringExtras + prevOutstanding).coerceAtMost(2_000_000_000L)
            // Grace period settings are always referenced from the Lease document
            val graceEndsMs = computeGracePeriodEndsMs(dueDateMs, lease.gracePeriodDays)

            val issueMode = schedule?.issueModeEnum() ?: RecurringIssueMode.AUTO_ISSUE
            val isDraft = issueMode == RecurringIssueMode.DRAFT_FOR_REVIEW
            val creditToApply = if (isDraft) {
                0L
            } else {
                minOf(lease.creditBalanceCents.coerceAtLeast(0L), totalDue)
            }
            val outstanding = (totalDue - creditToApply).coerceAtLeast(0L)
            val targetStatus = when {
                isDraft -> InvoiceStatus.DRAFT
                outstanding == 0L -> InvoiceStatus.PAID
                creditToApply > 0L -> InvoiceStatus.PARTIALLY_PAID
                else -> InvoiceStatus.ISSUED
            }
            val issuedAtMs = if (isDraft) null else nowMs
            val paidAtMs = if (!isDraft && outstanding == 0L) nowMs else null

            AutoBillingCandidate(
                lease = lease,
                billingPeriod = billingPeriod,
                rentCents = rentResult.rentCents,
                previousBalanceCents = prevOutstanding,
                creditToApplyCents = creditToApply,
                totalDueCents = totalDue,
                outstandingCents = outstanding,
                dueDateEpochMs = dueDateMs,
                gracePeriodEndsEpochMs = graceEndsMs,
                prorated = rentResult.prorated,
                prorationNote = rentResult.prorationNote,
                scheduleId = schedule?.id?.takeIf { it.isNotBlank() } ?: lease.id,
                extraChargesCents = recurringExtras,
                extraChargesNote = recurringExtrasNote,
                billingDateEpochMs = billingDateMs,
                targetStatus = targetStatus,
                issuedAtEpochMs = issuedAtMs,
                paidAtEpochMs = paidAtMs
            )
        }.sortedBy { it.dueDateEpochMs }
    }

    fun evaluateDraftIssuance(
        draftInvoice: Invoice,
        lease: Lease,
        extraChargesCents: Long = draftInvoice.extraChargesCents,
        nowMs: Long = System.currentTimeMillis()
    ): DraftIssuanceResult {
        val extras = extraChargesCents.coerceAtLeast(0L)
        val totalDue = (
            draftInvoice.rentCents.coerceAtLeast(0L) +
                extras +
                draftInvoice.previousBalanceCents.coerceAtLeast(0L) +
                draftInvoice.lateFeeCents.coerceAtLeast(0L)
            ).coerceAtMost(2_000_000_000L)
        val priorPaid = draftInvoice.paidCents.coerceAtLeast(0L).coerceAtMost(totalDue)
        val remainingBeforeCredit = (totalDue - priorPaid).coerceAtLeast(0L)
        val creditToApply = minOf(lease.creditBalanceCents.coerceAtLeast(0L), remainingBeforeCredit)
        val newPaid = (priorPaid + creditToApply).coerceAtMost(totalDue)
        val newOutstanding = (totalDue - newPaid).coerceAtLeast(0L)
        val graceEndsMs = computeGracePeriodEndsMs(draftInvoice.dueDateEpochMs, lease.gracePeriodDays)
        val newStatus = when {
            newOutstanding == 0L -> InvoiceStatus.PAID
            nowMs > draftInvoice.dueDateEpochMs -> InvoiceStatus.OVERDUE
            newPaid > 0L -> InvoiceStatus.PARTIALLY_PAID
            else -> InvoiceStatus.ISSUED
        }
        return DraftIssuanceResult(
            totalDueCents = totalDue,
            creditToApplyCents = creditToApply,
            newPaidCents = newPaid,
            newOutstandingCents = newOutstanding,
            gracePeriodEndsEpochMs = graceEndsMs,
            issuedAtEpochMs = nowMs,
            paidAtEpochMs = if (newOutstanding == 0L) nowMs else null,
            newStatus = newStatus
        )
    }

    fun evaluateLeaseRenewals(
        leases: List<Lease>,
        nowMs: Long = System.currentTimeMillis()
    ): List<RenewalSweepCandidate> {
        return leases.mapNotNull { lease ->
            if (lease.statusEnum() != LeaseStatus.ACTIVE) return@mapNotNull null
            val noticeStatus = lease.moveOutNoticeStatusEnum()
            if (noticeStatus == MoveOutNoticeStatus.SUBMITTED || noticeStatus == MoveOutNoticeStatus.ACKNOWLEDGED) {
                return@mapNotNull null
            }
            val mode = lease.renewalModeEnum()
            if (mode == RenewalMode.MANUAL) return@mapNotNull null

            // Skip leases already processed for the current term end date
            if (lease.lastRenewalProcessedForEndMs != null &&
                lease.lastRenewalProcessedForEndMs == lease.endDateEpochMs
            ) {
                return@mapNotNull null
            }

            // Do not re-offer while one is already pending or declined for this term
            val offerStatus = lease.renewalOfferStatusEnum()
            if (offerStatus == RenewalOfferStatus.PENDING || offerStatus == RenewalOfferStatus.DECLINED) {
                return@mapNotNull null
            }

            val leadDays = lease.renewalLeadDays.coerceIn(1L, 180L)
            val triggerAtMs = lease.endDateEpochMs - (leadDays * DAY_MS)
            if (nowMs < triggerAtMs) return@mapNotNull null

            val projectedRent = computeEscalatedRentCents(lease)
            val projectedEnd = computeExtendedEndDateMs(
                currentEndDateEpochMs = lease.endDateEpochMs,
                renewalDurationMonths = lease.renewalDurationMonths,
                nowMs = nowMs
            )
            val daysUntilExpiry = ((lease.endDateEpochMs - nowMs) / DAY_MS).toInt()
            val actionType = when (mode) {
                RenewalMode.AUTO_RENEW -> RenewalActionType.AUTO_RENEW
                RenewalMode.OFFER -> RenewalActionType.SEND_OFFER
                RenewalMode.MANUAL -> return@mapNotNull null
            }

            RenewalSweepCandidate(
                lease = lease,
                actionType = actionType,
                projectedRentCents = projectedRent,
                projectedEndDateEpochMs = projectedEnd,
                daysUntilExpiry = daysUntilExpiry
            )
        }.sortedBy { it.lease.endDateEpochMs }
    }

    fun evaluateOverdueLateFees(
        invoices: List<Invoice>,
        leases: List<Lease>,
        nowMs: Long = System.currentTimeMillis()
    ): List<LateFeeSweepCandidate> {
        val leaseById = leases.associateBy { it.id }
        return invoices.mapNotNull { inv ->
            val st = inv.statusEnum()
            if (st == InvoiceStatus.DRAFT || st == InvoiceStatus.PAID || st == InvoiceStatus.CANCELLED) {
                return@mapNotNull null
            }
            if (inv.outstandingCents <= 0L) return@mapNotNull null
            if (inv.lateFeeAppliedAtEpochMs != null || inv.lateFeeCents > 0L || inv.lateFeeWaived) {
                return@mapNotNull null
            }

            val lease = leaseById[inv.leaseId] ?: return@mapNotNull null
            if (!lease.lateFeeEnabled || lease.lateFeePercent <= 0.0) return@mapNotNull null

            val graceEndsMs = inv.gracePeriodEndsEpochMs
                ?: computeGracePeriodEndsMs(inv.dueDateEpochMs, lease.gracePeriodDays)
            if (nowMs <= graceEndsMs) return@mapNotNull null

            val feeCents = computeLateFeeCents(inv.outstandingCents, lease.lateFeePercent)
            if (feeCents <= 0L) return@mapNotNull null

            val daysPastGrace = (((nowMs - graceEndsMs) / DAY_MS) + 1L).toInt().coerceAtLeast(1)
            val newTotal = (inv.totalDueCents + feeCents).coerceAtMost(2_000_000_000L)
            val newOutstanding = (inv.outstandingCents + feeCents).coerceAtMost(2_000_000_000L)

            LateFeeSweepCandidate(
                invoice = inv,
                lease = lease,
                lateFeeCents = feeCents,
                lateFeeRatePercent = lease.lateFeePercent.coerceIn(0.0, 50.0),
                gracePeriodEndsEpochMs = graceEndsMs,
                daysPastGrace = daysPastGrace,
                newTotalDueCents = newTotal,
                newOutstandingCents = newOutstanding
            )
        }.sortedBy { it.invoice.dueDateEpochMs }
    }

    fun computeLateFeeWaiver(
        invoice: Invoice,
        nowMs: Long = System.currentTimeMillis()
    ): LateFeeWaiverResult {
        val fee = invoice.lateFeeCents.coerceAtLeast(0L)
        val newTotal = (invoice.totalDueCents - fee).coerceAtLeast(0L)
        val creditRefund = (invoice.paidCents - newTotal).coerceAtLeast(0L)
        val newPaid = minOf(invoice.paidCents.coerceAtLeast(0L), newTotal)
        val newOutstanding = (newTotal - newPaid).coerceAtLeast(0L)
        val newStatus = when {
            newOutstanding == 0L -> InvoiceStatus.PAID
            nowMs > invoice.dueDateEpochMs -> InvoiceStatus.OVERDUE
            newPaid > 0L -> InvoiceStatus.PARTIALLY_PAID
            invoice.issuedAtEpochMs != null -> InvoiceStatus.ISSUED
            else -> InvoiceStatus.UNPAID
        }
        return LateFeeWaiverResult(
            waivedFeeCents = fee,
            newTotalDueCents = newTotal,
            newPaidCents = newPaid,
            newOutstandingCents = newOutstanding,
            creditRefundCents = creditRefund,
            newStatus = newStatus
        )
    }

    fun formatPercent(value: Double): String {
        val rounded = (value * 10.0).roundToLong() / 10.0
        return if (rounded % 1.0 == 0.0) {
            "${rounded.toInt()}%"
        } else {
            "$rounded%"
        }
    }
}
