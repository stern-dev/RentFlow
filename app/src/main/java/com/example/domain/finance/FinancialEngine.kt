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
import java.util.Date
import java.util.Locale

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
            .filter { it.statusEnum() != InvoiceStatus.CANCELLED }
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
                ((st == InvoiceStatus.UNPAID || st == InvoiceStatus.PARTIALLY_PAID) && it.dueDateEpochMs < nowMs && it.outstandingCents > 0L)
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
            .filter { it.leaseId == activeLease.id && it.statusEnum() != InvoiceStatus.CANCELLED }
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
            .filter { it.statusEnum() != InvoiceStatus.CANCELLED && it.outstandingCents > 0L }
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
}
