package com.example.domain.finance

import com.example.domain.model.EscalationType
import com.example.domain.model.Invoice
import com.example.domain.model.InvoiceStatus
import com.example.domain.model.Lease
import com.example.domain.model.LeaseStatus
import com.example.domain.model.RenewalMode
import com.example.domain.model.RenewalOfferStatus
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

object LeaseAutomationEngine {
    const val DAY_MS = 86_400_000L
    const val MONTH_DAYS = 30L

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

    fun evaluateLeaseRenewals(
        leases: List<Lease>,
        nowMs: Long = System.currentTimeMillis()
    ): List<RenewalSweepCandidate> {
        return leases.mapNotNull { lease ->
            if (lease.statusEnum() != LeaseStatus.ACTIVE) return@mapNotNull null
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
            if (st == InvoiceStatus.PAID || st == InvoiceStatus.CANCELLED) return@mapNotNull null
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
