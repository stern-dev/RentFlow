package com.example

import com.example.domain.finance.CurrencyUtils
import com.example.domain.finance.FinancialEngine
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FinancialEngineTest {

    @Test
    fun `currency utils parses formats and validates kenyan phones`() {
        assertEquals(2550050L, CurrencyUtils.parseKesToCents("KES 25,500.50"))
        assertEquals("KES 25,500", CurrencyUtils.formatKes(2550000L, includeDecimals = false))
        assertEquals("KES 25,500.50", CurrencyUtils.formatKes(2550050L, includeDecimals = true))

        assertTrue(CurrencyUtils.isValidKenyanPhone("0712345678"))
        assertTrue(CurrencyUtils.isValidKenyanPhone("+254712345678"))
        assertTrue(CurrencyUtils.isValidKenyanPhone("254110123456"))
        assertFalse(CurrencyUtils.isValidKenyanPhone("12345"))
        assertEquals("+254712345678", CurrencyUtils.normalizeKenyanPhone("0712 345 678"))
    }

    @Test
    fun `planInvoiceAllocation handles partial payment multi invoice settlement and overpayment credit`() {
        val inv1 = Invoice(
            id = "inv1",
            invoiceNumber = "INV-2026-01",
            leaseId = "l1",
            totalDueCents = 1500000L,
            paidCents = 0L,
            outstandingCents = 1500000L,
            dueDateEpochMs = 1000L,
            status = InvoiceStatus.UNPAID.name
        )
        val inv2 = Invoice(
            id = "inv2",
            invoiceNumber = "INV-2026-02",
            leaseId = "l1",
            totalDueCents = 1500000L,
            paidCents = 0L,
            outstandingCents = 1500000L,
            dueDateEpochMs = 2000L,
            status = InvoiceStatus.UNPAID.name
        )

        // Partial payment on inv1
        val partial = FinancialEngine.planInvoiceAllocation(
            paymentAmountCents = 500000L,
            existingCreditCents = 0L,
            openInvoices = listOf(inv1, inv2),
            nowMs = 500L
        )
        assertEquals(1, partial.updates.size)
        assertEquals(1000000L, partial.updates.first().newOutstandingCents)
        assertEquals(InvoiceStatus.PARTIALLY_PAID, partial.updates.first().newStatus)
        assertEquals(0L, partial.remainingCreditCents)

        // Overpayment across both invoices + existing credit
        val overpay = FinancialEngine.planInvoiceAllocation(
            paymentAmountCents = 3200000L,
            existingCreditCents = 100000L,
            openInvoices = listOf(inv1, inv2),
            nowMs = 3000L
        )
        assertEquals(2, overpay.updates.size)
        assertEquals(InvoiceStatus.PAID, overpay.updates[0].newStatus)
        assertEquals(InvoiceStatus.PAID, overpay.updates[1].newStatus)
        assertEquals(300000L, overpay.remainingCreditCents)
    }

    @Test
    fun `computePortfolioSummary aggregates live metrics accurately`() {
        val props = listOf(
            Property(id = "p1", name = "Acacia Court", archived = false),
            Property(id = "p2", name = "Old Block", archived = true)
        )
        val units = listOf(
            RentalUnit(id = "u1", status = UnitStatus.OCCUPIED.name),
            RentalUnit(id = "u2", status = UnitStatus.AVAILABLE.name)
        )
        val leases = listOf(
            Lease(
                id = "l1",
                tenantUid = "t1",
                rentCents = 2500000L,
                startDateEpochMs = 1000L,
                endDateEpochMs = 10_000_000L,
                status = LeaseStatus.ACTIVE.name
            )
        )
        val invoices = listOf(
            Invoice(
                id = "inv1",
                leaseId = "l1",
                totalDueCents = 2500000L,
                paidCents = 1500000L,
                outstandingCents = 1000000L,
                dueDateEpochMs = 5000L,
                status = InvoiceStatus.PARTIALLY_PAID.name
            )
        )
        val payments = listOf(
            Payment(id = "pay1", amountCents = 1500000L, status = PaymentStatus.VERIFIED.name),
            Payment(id = "pay2", amountCents = 500000L, status = PaymentStatus.PENDING_VERIFICATION.name)
        )
        val expenses = listOf(
            Expense(id = "exp1", amountCents = 300000L)
        )
        val maintenance = listOf(
            MaintenanceRequest(id = "m1", status = MaintenanceStatus.IN_PROGRESS.name),
            MaintenanceRequest(id = "m2", status = MaintenanceStatus.COMPLETED.name)
        )

        val summary = FinancialEngine.computePortfolioSummary(
            properties = props,
            units = units,
            leases = leases,
            invoices = invoices,
            payments = payments,
            expenses = expenses,
            maintenanceRequests = maintenance,
            nowMs = 8000L
        )

        assertEquals(1, summary.totalProperties)
        assertEquals(2, summary.totalUnits)
        assertEquals(1, summary.occupiedUnits)
        assertEquals(1, summary.vacantUnits)
        assertEquals(1, summary.activeTenants)
        assertEquals(2500000L, summary.expectedMonthlyRentCents)
        assertEquals(1500000L, summary.totalCollectedCents)
        assertEquals(1000000L, summary.outstandingRentCents)
        assertEquals(300000L, summary.totalExpensesCents)
        assertEquals(1200000L, summary.netRentalIncomeCents)
        assertEquals(50f, summary.occupancyRatePercent, 0.01f)
        assertEquals(1, summary.pendingMaintenanceCount)
        assertEquals(1, summary.pendingPaymentsCount)
        assertEquals(1, summary.overdueInvoices.size)
    }

    @Test
    fun `computeTenantPaymentTrends returns 12 rolling months with accurate streak and consistency`() {
        val tz = java.util.TimeZone.getTimeZone("Africa/Nairobi")
        val cal = java.util.Calendar.getInstance(tz, java.util.Locale.US).apply {
            set(2026, java.util.Calendar.NOVEMBER, 10, 12, 0, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val nowMs = cal.timeInMillis

        val empty = FinancialEngine.computeTenantPaymentTrends(
            invoices = emptyList(),
            payments = emptyList(),
            nowMs = nowMs
        )
        assertEquals(12, empty.points.size)
        assertEquals("2025-12", empty.points.first().billingPeriod)
        assertEquals("2026-11", empty.points.last().billingPeriod)
        assertFalse(empty.hasHistoricalActivity)
        assertEquals(0, empty.currentStreakMonths)
        assertEquals(0, empty.consistencyPercent)

        fun dueMs(year: Int, monthZeroBased: Int, day: Int): Long =
            java.util.Calendar.getInstance(tz, java.util.Locale.US).apply {
                set(year, monthZeroBased, day, 12, 0, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }.timeInMillis

        val invoices = listOf(
            // Jul 2026: Paid late
            Invoice(
                id = "inv_jul",
                leaseId = "l1",
                billingPeriod = "2026-07",
                totalDueCents = 2500000L,
                paidCents = 2500000L,
                outstandingCents = 0L,
                dueDateEpochMs = dueMs(2026, java.util.Calendar.JULY, 5),
                status = InvoiceStatus.PAID.name
            ),
            // Aug 2026: Overdue/Partial breaks streak
            Invoice(
                id = "inv_aug",
                leaseId = "l1",
                billingPeriod = "2026-08",
                totalDueCents = 2500000L,
                paidCents = 1000000L,
                outstandingCents = 1500000L,
                dueDateEpochMs = dueMs(2026, java.util.Calendar.AUGUST, 5),
                status = InvoiceStatus.OVERDUE.name
            ),
            // Sep 2026: Paid on time (streak 1)
            Invoice(
                id = "inv_sep",
                leaseId = "l1",
                billingPeriod = "2026-09",
                totalDueCents = 2500000L,
                paidCents = 2500000L,
                outstandingCents = 0L,
                dueDateEpochMs = dueMs(2026, java.util.Calendar.SEPTEMBER, 5),
                status = InvoiceStatus.PAID.name
            ),
            // Oct 2026: Paid on time (streak 2)
            Invoice(
                id = "inv_oct",
                leaseId = "l1",
                billingPeriod = "2026-10",
                totalDueCents = 2500000L,
                paidCents = 2500000L,
                outstandingCents = 0L,
                dueDateEpochMs = dueMs(2026, java.util.Calendar.OCTOBER, 5),
                status = InvoiceStatus.PAID.name
            ),
            // Nov 2026: Current month invoice not yet due (should NOT break current 2-month streak)
            Invoice(
                id = "inv_nov",
                leaseId = "l1",
                billingPeriod = "2026-11",
                totalDueCents = 2500000L,
                paidCents = 0L,
                outstandingCents = 2500000L,
                dueDateEpochMs = dueMs(2026, java.util.Calendar.NOVEMBER, 25),
                status = InvoiceStatus.UNPAID.name
            ),
            // Cancelled invoice must be ignored
            Invoice(
                id = "inv_cancelled",
                leaseId = "l1",
                billingPeriod = "2026-06",
                totalDueCents = 2500000L,
                paidCents = 0L,
                outstandingCents = 2500000L,
                dueDateEpochMs = dueMs(2026, java.util.Calendar.JUNE, 5),
                status = InvoiceStatus.CANCELLED.name
            )
        )

        val payments = listOf(
            Payment(
                id = "pay_sep",
                leaseId = "l1",
                invoiceId = "inv_sep",
                amountCents = 2500000L,
                paymentDateEpochMs = dueMs(2026, java.util.Calendar.SEPTEMBER, 3),
                status = PaymentStatus.VERIFIED.name
            ),
            Payment(
                id = "pay_oct",
                leaseId = "l1",
                invoiceId = "inv_oct",
                amountCents = 2500000L,
                paymentDateEpochMs = dueMs(2026, java.util.Calendar.OCTOBER, 4),
                status = PaymentStatus.VERIFIED.name
            )
        )

        val trends = FinancialEngine.computeTenantPaymentTrends(
            invoices = invoices,
            payments = payments,
            nowMs = nowMs
        )

        assertTrue(trends.hasHistoricalActivity)
        assertEquals(12, trends.points.size)
        assertEquals(2, trends.currentStreakMonths)
        assertEquals(2, trends.bestStreakMonths)
        // Evaluated closed/due months = Jul, Aug, Sep, Oct (3 settled out of 4 = 75%)
        assertEquals(75, trends.consistencyPercent)
        assertEquals(5, trends.billedMonthsCount)
        assertEquals(3, trends.settledMonthsCount)
        assertEquals(12500000L, trends.totalBilled12MonthsCents)
        assertEquals(8500000L, trends.totalPaid12MonthsCents)

        val augPoint = trends.points.first { it.billingPeriod == "2026-08" }
        assertEquals(com.example.domain.finance.TenantMonthTrendStatus.PARTIAL, augPoint.status)
        assertEquals(1500000L, augPoint.outstandingCents)

        val novPoint = trends.points.last()
        assertEquals(com.example.domain.finance.TenantMonthTrendStatus.DUE, novPoint.status)
    }
}
