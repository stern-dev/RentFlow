package com.example.domain.finance

import com.example.domain.model.Expense
import com.example.domain.model.Invoice
import com.example.domain.model.InvoiceStatus
import com.example.domain.model.Lease
import com.example.domain.model.LeaseStatus
import com.example.domain.model.MaintenanceRequest
import com.example.domain.model.MaintenanceStatus
import com.example.domain.model.Payment
import com.example.domain.model.PaymentStatus
import com.example.domain.model.Property
import com.example.domain.model.RentalUnit
import com.example.domain.model.UnitStatus
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToInt

data class PortfolioSummary(
    val totalProperties: Int = 0,
    val totalUnits: Int = 0,
    val occupiedUnits: Int = 0,
    val vacantUnits: Int = 0,
    val activeTenants: Int = 0,
    val expectedMonthlyRentCents: Long = 0L,
    val totalCollectedCents: Long = 0L,
    val outstandingRentCents: Long = 0L,
    val totalExpensesCents: Long = 0L,
    val netRentalIncomeCents: Long = 0L,
    val occupancyRatePercent: Float = 0f,
    val upcomingLeaseExpiries: List<Lease> = emptyList(),
    val overdueInvoices: List<Invoice> = emptyList(),
    val pendingMaintenanceCount: Int = 0,
    val pendingPaymentsCount: Int = 0
)

data class TenantAccountSummary(
    val monthlyRentCents: Long = 0L,
    val amountPaidThisPeriodCents: Long = 0L,
    val totalOutstandingCents: Long = 0L,
    val accountCreditCents: Long = 0L,
    val nextDueDateEpochMs: Long = 0L,
    val paymentStatusLabel: String = "Up to date"
)

enum class TenantMonthTrendStatus(val label: String) {
    NO_INVOICE("No Invoice"),
    PAID_ON_TIME("Paid"),
    PAID_LATE("Settled"),
    PARTIAL("Partial"),
    DUE("Balance Due"),
    OVERDUE("Overdue")
}

data class TenantPaymentTrendPoint(
    val billingPeriod: String,
    val shortLabel: String,
    val fullLabel: String,
    val billedCents: Long,
    val paidCents: Long,
    val outstandingCents: Long,
    val verifiedPaymentsCount: Int,
    val status: TenantMonthTrendStatus
)

data class TenantPaymentTrendsSummary(
    val points: List<TenantPaymentTrendPoint> = emptyList(),
    val currentStreakMonths: Int = 0,
    val bestStreakMonths: Int = 0,
    val consistencyPercent: Int = 0,
    val billedMonthsCount: Int = 0,
    val settledMonthsCount: Int = 0,
    val totalBilled12MonthsCents: Long = 0L,
    val totalPaid12MonthsCents: Long = 0L,
    val hasHistoricalActivity: Boolean = false
)

enum class PaymentHistoryStatus(val label: String) {
    PAID("Paid"),
    LATE("Late"),
    PARTIAL("Partial")
}

data class PaymentHistoryEntry(
    val invoice: Invoice,
    val status: PaymentHistoryStatus,
    val historyYear: Int,
    val effectiveActivityEpochMs: Long,
    val daysLate: Int = 0
)

data class AnnualRentPaidSummary(
    val year: Int,
    val totalPaidCents: Long,
    val totalBilledCents: Long,
    val totalOutstandingCents: Long,
    val invoiceCount: Int,
    val paidOnTimeCount: Int,
    val lateCount: Int,
    val partialCount: Int
)

data class TenantPaymentHistorySummary(
    val entries: List<PaymentHistoryEntry> = emptyList(),
    val annualSummaries: List<AnnualRentPaidSummary> = emptyList(),
    val overallTotalPaidCents: Long = 0L,
    val overallPaidCount: Int = 0,
    val overallLateCount: Int = 0,
    val overallPartialCount: Int = 0
)

data class InvoiceAllocationUpdate(
    val invoiceId: String,
    val invoiceNumber: String,
    val appliedCents: Long,
    val newPaidCents: Long,
    val newOutstandingCents: Long,
    val newStatus: InvoiceStatus
)

data class AllocationPlan(
    val updates: List<InvoiceAllocationUpdate>,
    val remainingCreditCents: Long,
    val summaryText: String
)

data class MonthlyCashFlowPoint(
    val periodLabel: String,
    val incomeCents: Long,
    val expenseCents: Long
)

object FinancialEngine {

    fun computePortfolioSummary(
        properties: List<Property>,
        units: List<RentalUnit>,
        leases: List<Lease>,
        invoices: List<Invoice>,
        payments: List<Payment>,
        expenses: List<Expense>,
        maintenanceRequests: List<MaintenanceRequest>,
        nowMs: Long = System.currentTimeMillis()
    ): PortfolioSummary {
        val activeProperties = properties.filter { !it.archived }
        val totalUnits = units.size
        val occupiedUnits = units.count { it.statusEnum() == UnitStatus.OCCUPIED }
        val vacantUnits = units.count { it.statusEnum() == UnitStatus.AVAILABLE }
        val activeLeases = leases.filter { it.statusEnum() == LeaseStatus.ACTIVE }
        val activeTenants = activeLeases.map { it.tenantUid }.distinct().size
        val expectedMonthlyRent = activeLeases.sumOf { it.rentCents }
        val totalCollected = payments
            .filter { it.statusEnum() == PaymentStatus.VERIFIED }
            .sumOf { it.amountCents }
        val outstanding = invoices
            .filter { val st = it.statusEnum(); st != InvoiceStatus.CANCELLED && st != InvoiceStatus.DRAFT }
            .sumOf { it.outstandingCents }
        val totalExpenses = expenses.sumOf { it.amountCents }
        val netIncome = totalCollected - totalExpenses
        val occupancyRate = if (totalUnits > 0) (occupiedUnits.toFloat() / totalUnits.toFloat()) * 100f else 0f

        val thirtyDaysMs = 30L * 86_400_000L
        val upcomingExpiries = activeLeases.filter {
            it.endDateEpochMs in nowMs..(nowMs + thirtyDaysMs)
        }
        val overdueInvoices = invoices.filter {
            val st = it.statusEnum()
            (st == InvoiceStatus.OVERDUE) ||
                ((st == InvoiceStatus.ISSUED || st == InvoiceStatus.UNPAID || st == InvoiceStatus.PARTIALLY_PAID) && it.dueDateEpochMs < nowMs && it.outstandingCents > 0L)
        }
        val pendingMaintenance = maintenanceRequests.count {
            val st = it.statusEnum()
            st != MaintenanceStatus.COMPLETED && st != MaintenanceStatus.REJECTED
        }
        val pendingPayments = payments.count { it.statusEnum() == PaymentStatus.PENDING_VERIFICATION }

        return PortfolioSummary(
            totalProperties = activeProperties.size,
            totalUnits = totalUnits,
            occupiedUnits = occupiedUnits,
            vacantUnits = vacantUnits,
            activeTenants = activeTenants,
            expectedMonthlyRentCents = expectedMonthlyRent,
            totalCollectedCents = totalCollected,
            outstandingRentCents = outstanding,
            totalExpensesCents = totalExpenses,
            netRentalIncomeCents = netIncome,
            occupancyRatePercent = occupancyRate,
            upcomingLeaseExpiries = upcomingExpiries,
            overdueInvoices = overdueInvoices,
            pendingMaintenanceCount = pendingMaintenance,
            pendingPaymentsCount = pendingPayments
        )
    }

    fun computeTenantAccountSummary(
        activeLease: Lease?,
        invoices: List<Invoice>,
        payments: List<Payment>,
        nowMs: Long = System.currentTimeMillis()
    ): TenantAccountSummary {
        if (activeLease == null) return TenantAccountSummary()
        val leaseInvoices = invoices
            .filter {
                it.leaseId == activeLease.id &&
                    it.statusEnum() != InvoiceStatus.CANCELLED &&
                    it.statusEnum() != InvoiceStatus.DRAFT
            }
            .sortedBy { it.dueDateEpochMs }
        val totalOutstanding = leaseInvoices.sumOf { it.outstandingCents }
        val currentPeriod = CurrencyUtils.currentBillingPeriod()
        val currentPeriodInvoice = leaseInvoices.firstOrNull { it.billingPeriod == currentPeriod }
            ?: leaseInvoices.lastOrNull()
        val paidThisPeriod = currentPeriodInvoice?.paidCents ?: payments
            .filter { it.leaseId == activeLease.id && it.statusEnum() == PaymentStatus.VERIFIED }
            .sumOf { it.amountCents }

        val nextUnpaid = leaseInvoices.firstOrNull { it.outstandingCents > 0L }
        val nextDueDate = nextUnpaid?.dueDateEpochMs
            ?: CurrencyUtils.computeDueDateEpochMs(currentPeriod, activeLease.dueDayOfMonth.toInt())

        val pendingVerification = payments.any {
            it.leaseId == activeLease.id && it.statusEnum() == PaymentStatus.PENDING_VERIFICATION
        }
        val statusLabel = when {
            totalOutstanding <= 0L -> "Settled"
            pendingVerification -> "Verification Pending"
            nextUnpaid != null && nextUnpaid.dueDateEpochMs < nowMs -> "Overdue"
            (currentPeriodInvoice?.paidCents ?: 0L) > 0L -> "Partially Paid"
            else -> "Balance Due"
        }

        return TenantAccountSummary(
            monthlyRentCents = activeLease.rentCents,
            amountPaidThisPeriodCents = paidThisPeriod,
            totalOutstandingCents = totalOutstanding,
            accountCreditCents = activeLease.creditBalanceCents,
            nextDueDateEpochMs = nextDueDate,
            paymentStatusLabel = statusLabel
        )
    }

    fun planInvoiceAllocation(
        paymentAmountCents: Long,
        existingCreditCents: Long,
        openInvoices: List<Invoice>,
        nowMs: Long = System.currentTimeMillis()
    ): AllocationPlan {
        require(paymentAmountCents > 0L) { "Payment amount must be positive" }
        var available = paymentAmountCents + existingCreditCents.coerceAtLeast(0L)
        val sortedOpen = openInvoices
            .filter {
                val st = it.statusEnum()
                st != InvoiceStatus.CANCELLED && st != InvoiceStatus.DRAFT && it.outstandingCents > 0L
            }
            .sortedBy { it.dueDateEpochMs }

        val updates = mutableListOf<InvoiceAllocationUpdate>()
        val summaryParts = mutableListOf<String>()

        for (inv in sortedOpen) {
            if (available <= 0L) break
            val apply = minOf(available, inv.outstandingCents)
            val newPaid = inv.paidCents + apply
            val newOutstanding = (inv.outstandingCents - apply).coerceAtLeast(0L)
            val newStatus = when {
                newOutstanding == 0L -> InvoiceStatus.PAID
                inv.dueDateEpochMs < nowMs -> InvoiceStatus.OVERDUE
                else -> InvoiceStatus.PARTIALLY_PAID
            }
            updates += InvoiceAllocationUpdate(
                invoiceId = inv.id,
                invoiceNumber = inv.invoiceNumber,
                appliedCents = apply,
                newPaidCents = newPaid,
                newOutstandingCents = newOutstanding,
                newStatus = newStatus
            )
            summaryParts += "${inv.invoiceNumber}: ${CurrencyUtils.formatKes(apply, true)}"
            available -= apply
        }

        if (available > 0L) {
            summaryParts += "Credit: ${CurrencyUtils.formatKes(available, true)}"
        }

        return AllocationPlan(
            updates = updates,
            remainingCreditCents = available,
            summaryText = summaryParts.joinToString(" | ").ifBlank {
                "Account Credit: ${CurrencyUtils.formatKes(available, true)}"
            }
        )
    }

    fun computeMonthlyCashFlow(
        payments: List<Payment>,
        expenses: List<Expense>
    ): List<MonthlyCashFlowPoint> {
        val fmtKey = SimpleDateFormat("yyyy-MM", Locale.US)
        val fmtLabel = SimpleDateFormat("MMM", Locale.US)
        val incomeByMonth = mutableMapOf<String, Long>()
        val expenseByMonth = mutableMapOf<String, Long>()

        payments.filter { it.statusEnum() == PaymentStatus.VERIFIED }.forEach { p ->
            val key = fmtKey.format(Date(p.paymentDateEpochMs))
            incomeByMonth[key] = (incomeByMonth[key] ?: 0L) + p.amountCents
        }
        expenses.forEach { e ->
            val key = fmtKey.format(Date(e.expenseDateEpochMs))
            expenseByMonth[key] = (expenseByMonth[key] ?: 0L) + e.amountCents
        }

        val allKeys = (incomeByMonth.keys + expenseByMonth.keys).distinct().sorted().takeLast(6)
        if (allKeys.isEmpty()) {
            val current = CurrencyUtils.currentBillingPeriod()
            val label = try {
                fmtLabel.format(fmtKey.parse(current) ?: Date())
            } catch (_: Exception) {
                current
            }
            return listOf(MonthlyCashFlowPoint(label, 0L, 0L))
        }
        return allKeys.map { key ->
            val label = try {
                fmtLabel.format(fmtKey.parse(key) ?: Date())
            } catch (_: Exception) {
                key
            }
            MonthlyCashFlowPoint(
                periodLabel = label,
                incomeCents = incomeByMonth[key] ?: 0L,
                expenseCents = expenseByMonth[key] ?: 0L
            )
        }
    }

    fun computeTenantPaymentTrends(
        invoices: List<Invoice>,
        payments: List<Payment>,
        activeLeaseId: String? = null,
        nowMs: Long = System.currentTimeMillis()
    ): TenantPaymentTrendsSummary {
        val nairobiTz = TimeZone.getTimeZone("Africa/Nairobi")
        val keyFmt = SimpleDateFormat("yyyy-MM", Locale.US).apply { timeZone = nairobiTz }
        val shortFmt = SimpleDateFormat("MMM", Locale.US).apply { timeZone = nairobiTz }
        val fullFmt = SimpleDateFormat("MMM yyyy", Locale.US).apply { timeZone = nairobiTz }

        val periodKeys = buildRollingMonthKeys(nowMs = nowMs, months = 12, timeZone = nairobiTz, keyFmt = keyFmt)
        val currentPeriodKey = periodKeys.lastOrNull().orEmpty()

        val activeInvoices = invoices.filter {
            val st = it.statusEnum()
            st != InvoiceStatus.CANCELLED &&
                st != InvoiceStatus.DRAFT &&
                (activeLeaseId == null || it.leaseId == activeLeaseId)
        }
        val verifiedPayments = payments.filter {
            it.statusEnum() == PaymentStatus.VERIFIED &&
                (activeLeaseId == null || it.leaseId == activeLeaseId)
        }

        val invoicesByPeriod = activeInvoices.groupBy { inv ->
            inv.billingPeriod.trim().takeIf { CurrencyUtils.isValidBillingPeriod(it) }
                ?: keyFmt.format(Date(inv.dueDateEpochMs.takeIf { it > 0L } ?: nowMs))
        }
        val paymentsByPeriod = verifiedPayments.groupBy { pay ->
            keyFmt.format(Date(pay.paymentDateEpochMs.takeIf { it > 0L } ?: nowMs))
        }

        val points = periodKeys.map { key ->
            val parsedDate = try {
                keyFmt.parse(key) ?: Date(nowMs)
            } catch (_: Exception) {
                Date(nowMs)
            }
            val shortLabel = shortFmt.format(parsedDate)
            val fullLabel = fullFmt.format(parsedDate)

            val monthInvoices = invoicesByPeriod[key].orEmpty()
            val monthPayments = paymentsByPeriod[key].orEmpty()
            val monthInvoiceIds = monthInvoices.map { it.id }.toSet()
            val linkedPaymentSum = verifiedPayments
                .filter { it.invoiceId != null && it.invoiceId in monthInvoiceIds }
                .sumOf { it.amountCents.coerceAtLeast(0L) }
            val monthPaymentSum = monthPayments.sumOf { it.amountCents.coerceAtLeast(0L) }

            val billedCents = monthInvoices.sumOf { it.totalDueCents.coerceAtLeast(0L) }
            val paidCents = if (monthInvoices.isNotEmpty()) {
                maxOf(
                    monthInvoices.sumOf { it.paidCents.coerceAtLeast(0L) },
                    linkedPaymentSum
                )
            } else {
                monthPaymentSum
            }
            val outstandingCents = if (monthInvoices.isNotEmpty()) {
                val rawOutstanding = monthInvoices.sumOf { it.outstandingCents.coerceAtLeast(0L) }
                minOf(rawOutstanding, (billedCents - paidCents).coerceAtLeast(0L))
            } else {
                0L
            }

            val status = when {
                monthInvoices.isEmpty() && paidCents == 0L -> TenantMonthTrendStatus.NO_INVOICE
                monthInvoices.isEmpty() && paidCents > 0L -> TenantMonthTrendStatus.PAID_ON_TIME
                outstandingCents == 0L -> {
                    val latestDue = monthInvoices.maxOfOrNull { it.dueDateEpochMs } ?: nowMs
                    val latestVerifiedPayTime = verifiedPayments
                        .filter { (it.invoiceId != null && it.invoiceId in monthInvoiceIds) || keyFmt.format(Date(it.paymentDateEpochMs)) == key }
                        .maxOfOrNull { it.paymentDateEpochMs }
                    if (latestVerifiedPayTime != null && latestVerifiedPayTime > latestDue + 86_400_000L) {
                        TenantMonthTrendStatus.PAID_LATE
                    } else {
                        TenantMonthTrendStatus.PAID_ON_TIME
                    }
                }
                monthInvoices.any { it.statusEnum() == InvoiceStatus.OVERDUE || it.dueDateEpochMs < nowMs } -> {
                    if (paidCents > 0L) TenantMonthTrendStatus.PARTIAL else TenantMonthTrendStatus.OVERDUE
                }
                paidCents > 0L -> TenantMonthTrendStatus.PARTIAL
                else -> TenantMonthTrendStatus.DUE
            }

            TenantPaymentTrendPoint(
                billingPeriod = key,
                shortLabel = shortLabel,
                fullLabel = fullLabel,
                billedCents = billedCents,
                paidCents = paidCents,
                outstandingCents = outstandingCents,
                verifiedPaymentsCount = monthPayments.size,
                status = status
            )
        }

        var runningStreak = 0
        var bestStreak = 0
        var settledCount = 0
        var billedCount = 0
        var evaluatedConsistencyCount = 0

        points.forEach { pt ->
            val isSettled = pt.status == TenantMonthTrendStatus.PAID_ON_TIME ||
                pt.status == TenantMonthTrendStatus.PAID_LATE
            val isBilled = pt.billedCents > 0L || pt.status != TenantMonthTrendStatus.NO_INVOICE
            if (isBilled) {
                billedCount++
                val isUndueCurrentInvoice = pt.billingPeriod == currentPeriodKey && pt.status == TenantMonthTrendStatus.DUE
                if (!isUndueCurrentInvoice) {
                    evaluatedConsistencyCount++
                }
                if (isSettled) {
                    settledCount++
                    runningStreak++
                    if (runningStreak > bestStreak) bestStreak = runningStreak
                } else if (!isUndueCurrentInvoice) {
                    runningStreak = 0
                }
            }
        }

        val consistencyPercent = when {
            evaluatedConsistencyCount > 0 -> ((settledCount.toFloat() / evaluatedConsistencyCount.toFloat()) * 100f).roundToInt().coerceIn(0, 100)
            billedCount > 0 -> 100
            else -> 0
        }

        return TenantPaymentTrendsSummary(
            points = points,
            currentStreakMonths = runningStreak,
            bestStreakMonths = bestStreak,
            consistencyPercent = consistencyPercent,
            billedMonthsCount = billedCount,
            settledMonthsCount = settledCount,
            totalBilled12MonthsCents = points.sumOf { it.billedCents },
            totalPaid12MonthsCents = points.sumOf { it.paidCents },
            hasHistoricalActivity = points.any { it.billedCents > 0L || it.paidCents > 0L }
        )
    }

    private fun buildRollingMonthKeys(
        nowMs: Long,
        months: Int,
        timeZone: TimeZone,
        keyFmt: SimpleDateFormat
    ): List<String> {
        val cal = Calendar.getInstance(timeZone, Locale.US).apply {
            timeInMillis = nowMs
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.MONTH, -(months - 1))
        }
        val keys = ArrayList<String>(months)
        repeat(months) {
            keys.add(keyFmt.format(cal.time))
            cal.add(Calendar.MONTH, 1)
        }
        return keys
    }

    data class DepositReconciliationResult(
        val depositHeldCents: Long,
        val creditAppliedCents: Long,
        val arrearsIncludedCents: Long,
        val deductionsTotalCents: Long,
        val totalAvailableCents: Long,
        val totalChargesCents: Long,
        val netRefundCents: Long,
        val balanceOwedCents: Long
    )

    fun computeDepositReconciliation(
        depositHeldCents: Long,
        creditAppliedCents: Long,
        arrearsIncludedCents: Long,
        deductions: List<com.example.domain.model.DepositDeductionItem>
    ): DepositReconciliationResult {
        val held = depositHeldCents.coerceAtLeast(0L)
        val credit = creditAppliedCents.coerceAtLeast(0L)
        val arrears = arrearsIncludedCents.coerceAtLeast(0L)
        val deductionsTotal = deductions.sumOf { it.amountCents.coerceAtLeast(0L) }
        val totalAvailable = held + credit
        val totalCharges = deductionsTotal + arrears
        val netRefund = (totalAvailable - totalCharges).coerceAtLeast(0L)
        val balanceOwed = (totalCharges - totalAvailable).coerceAtLeast(0L)
        return DepositReconciliationResult(
            depositHeldCents = held,
            creditAppliedCents = credit,
            arrearsIncludedCents = arrears,
            deductionsTotalCents = deductionsTotal,
            totalAvailableCents = totalAvailable,
            totalChargesCents = totalCharges,
            netRefundCents = netRefund,
            balanceOwedCents = balanceOwed
        )
    }

    fun computeLeaseUnpaidArrearsCents(leaseId: String, invoices: List<Invoice>): Long {
        if (leaseId.isBlank()) return 0L
        return invoices
            .filter {
                it.leaseId == leaseId &&
                    it.statusEnum() != InvoiceStatus.CANCELLED &&
                    it.statusEnum() != InvoiceStatus.DRAFT &&
                    it.statusEnum() != InvoiceStatus.PAID
            }
            .sumOf { it.outstandingCents.coerceAtLeast(0L) }
    }

    /**
     * Aggregates settled and partially paid Firestore invoices into a chronological
     * payment history ledger with Paid, Late, and Partial badges plus per-year KES totals.
     */
    fun computeTenantPaymentHistory(
        invoices: List<Invoice>,
        nowMs: Long = System.currentTimeMillis()
    ): TenantPaymentHistorySummary {
        val nairobiTz = TimeZone.getTimeZone("Africa/Nairobi")
        val dayMs = 86_400_000L

        fun yearFromEpoch(epochMs: Long): Int =
            Calendar.getInstance(nairobiTz, Locale.US).apply {
                timeInMillis = epochMs
            }.get(Calendar.YEAR)

        val eligible = invoices.filter { inv ->
            val st = inv.statusEnum()
            st != InvoiceStatus.DRAFT &&
                st != InvoiceStatus.CANCELLED &&
                (st == InvoiceStatus.PAID || st == InvoiceStatus.PARTIALLY_PAID || inv.paidCents > 0L)
        }

        if (eligible.isEmpty()) {
            return TenantPaymentHistorySummary()
        }

        val entries = eligible.map { inv ->
            val st = inv.statusEnum()
            val isPartial = inv.outstandingCents > 0L || st == InvoiceStatus.PARTIALLY_PAID
            val paidAt = inv.paidAtEpochMs?.takeIf { it > 0L }
            val dueAt = inv.dueDateEpochMs.takeIf { it > 0L }
            val settledAfterDue = !isPartial && paidAt != null && dueAt != null && paidAt > dueAt
            val hasLateFee = inv.lateFeeCents > 0L

            val historyStatus = when {
                isPartial -> PaymentHistoryStatus.PARTIAL
                settledAfterDue || hasLateFee -> PaymentHistoryStatus.LATE
                else -> PaymentHistoryStatus.PAID
            }

            val daysLate = when {
                isPartial && dueAt != null && nowMs > dueAt ->
                    ((nowMs - dueAt) / dayMs).toInt().coerceAtLeast(1)
                settledAfterDue ->
                    (((paidAt ?: 0L) - (dueAt ?: 0L)) / dayMs).toInt().coerceAtLeast(1)
                else -> 0
            }

            val resolvedYear = when {
                paidAt != null -> yearFromEpoch(paidAt)
                inv.billingPeriod.trim().length >= 4 -> {
                    inv.billingPeriod.trim().substring(0, 4).toIntOrNull()
                        ?.takeIf { it in 2000..2100 }
                        ?: yearFromEpoch(dueAt ?: inv.issuedAtEpochMs?.takeIf { it > 0L } ?: nowMs)
                }
                dueAt != null -> yearFromEpoch(dueAt)
                inv.issuedAtEpochMs != null && inv.issuedAtEpochMs > 0L -> yearFromEpoch(inv.issuedAtEpochMs)
                else -> yearFromEpoch(nowMs)
            }

            val effectiveActivity = paidAt
                ?: dueAt
                ?: inv.issuedAtEpochMs?.takeIf { it > 0L }
                ?: nowMs

            PaymentHistoryEntry(
                invoice = inv,
                status = historyStatus,
                historyYear = resolvedYear,
                effectiveActivityEpochMs = effectiveActivity,
                daysLate = daysLate
            )
        }.sortedWith(
            compareByDescending<PaymentHistoryEntry> { it.effectiveActivityEpochMs }
                .thenByDescending { it.invoice.billingPeriod }
                .thenByDescending { it.invoice.invoiceNumber }
        )

        val annualSummaries = entries
            .groupBy { it.historyYear }
            .entries
            .sortedByDescending { it.key }
            .map { (year, yearEntries) ->
                AnnualRentPaidSummary(
                    year = year,
                    totalPaidCents = yearEntries.sumOf { it.invoice.paidCents.coerceAtLeast(0L) },
                    totalBilledCents = yearEntries.sumOf { it.invoice.totalDueCents.coerceAtLeast(0L) },
                    totalOutstandingCents = yearEntries.sumOf { it.invoice.outstandingCents.coerceAtLeast(0L) },
                    invoiceCount = yearEntries.size,
                    paidOnTimeCount = yearEntries.count { it.status == PaymentHistoryStatus.PAID },
                    lateCount = yearEntries.count { it.status == PaymentHistoryStatus.LATE },
                    partialCount = yearEntries.count { it.status == PaymentHistoryStatus.PARTIAL }
                )
            }

        return TenantPaymentHistorySummary(
            entries = entries,
            annualSummaries = annualSummaries,
            overallTotalPaidCents = entries.sumOf { it.invoice.paidCents.coerceAtLeast(0L) },
            overallPaidCount = entries.count { it.status == PaymentHistoryStatus.PAID },
            overallLateCount = entries.count { it.status == PaymentHistoryStatus.LATE },
            overallPartialCount = entries.count { it.status == PaymentHistoryStatus.PARTIAL }
        )
    }
}
