package com.example

import com.example.data.notifications.NotificationHelper
import com.example.domain.finance.LeaseAutomationEngine
import com.example.domain.finance.RenewalActionType
import com.example.domain.model.EscalationType
import com.example.domain.model.Invoice
import com.example.domain.model.InvoiceStatus
import com.example.domain.model.Lease
import com.example.domain.model.LeaseStatus
import com.example.domain.model.RenewalMode
import com.example.domain.model.RenewalOfferStatus
import com.example.domain.model.UserRole
import com.example.domain.security.PermissionPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LeaseAutomationFeatureTest {

    private val dayMs = LeaseAutomationEngine.DAY_MS

    @Test
    fun `computeEscalatedRentCents calculates none, percentage, fixed KES, and caps accurately`() {
        assertEquals(
            3_500_000L,
            LeaseAutomationEngine.computeEscalatedRentCents(
                currentRentCents = 3_500_000L,
                escalationType = EscalationType.NONE,
                escalationPercent = 10.0,
                escalationFixedCents = 500_000L
            )
        )

        // 5% of KES 40,000 = KES 42,000
        assertEquals(
            4_200_000L,
            LeaseAutomationEngine.computeEscalatedRentCents(
                currentRentCents = 4_000_000L,
                escalationType = EscalationType.PERCENT,
                escalationPercent = 5.0,
                escalationFixedCents = 0L
            )
        )

        // Fixed +KES 2,500
        assertEquals(
            4_250_000L,
            LeaseAutomationEngine.computeEscalatedRentCents(
                currentRentCents = 4_000_000L,
                escalationType = EscalationType.FIXED_KES,
                escalationPercent = 0.0,
                escalationFixedCents = 250_000L
            )
        )

        // Cap at 1_000_000_000L
        assertEquals(
            1_000_000_000L,
            LeaseAutomationEngine.computeEscalatedRentCents(
                currentRentCents = 990_000_000L,
                escalationType = EscalationType.FIXED_KES,
                escalationPercent = 0.0,
                escalationFixedCents = 50_000_000L
            )
        )
    }

    @Test
    fun `computeExtendedEndDateMs extends from future end date or nowMs when already expired`() {
        val nowMs = 1_750_000_000_000L
        val futureEnd = nowMs + 10 * dayMs
        val extendedFuture = LeaseAutomationEngine.computeExtendedEndDateMs(
            currentEndDateEpochMs = futureEnd,
            renewalDurationMonths = 12L,
            nowMs = nowMs
        )
        assertEquals(futureEnd + 12L * 30L * dayMs, extendedFuture)

        val pastEnd = nowMs - 20 * dayMs
        val extendedFromNow = LeaseAutomationEngine.computeExtendedEndDateMs(
            currentEndDateEpochMs = pastEnd,
            renewalDurationMonths = 6L,
            nowMs = nowMs
        )
        assertEquals(nowMs + 6L * 30L * dayMs, extendedFromNow)
    }

    @Test
    fun `evaluateLeaseRenewals filters modes, lead window boundaries, and idempotency`() {
        val nowMs = 1_760_000_000_000L
        val baseLease = Lease(
            id = "lease_base",
            orgId = "org_1",
            ownerUid = "owner_1",
            managerUids = listOf("mgr_1"),
            authorizedUids = listOf("owner_1", "mgr_1"),
            tenantUid = "tenant_1",
            rentCents = 3_000_000L,
            startDateEpochMs = nowMs - 300 * dayMs,
            endDateEpochMs = nowMs + 20 * dayMs,
            status = LeaseStatus.ACTIVE.name,
            renewalLeadDays = 30L,
            renewalDurationMonths = 12L,
            escalationType = EscalationType.PERCENT.name,
            escalationPercent = 10.0,
            renewalOfferStatus = RenewalOfferStatus.NONE.name
        )

        val autoRenewDue = baseLease.copy(
            id = "lease_auto",
            renewalMode = RenewalMode.AUTO_RENEW.name,
            endDateEpochMs = nowMs + 15 * dayMs
        )
        val offerDue = baseLease.copy(
            id = "lease_offer",
            renewalMode = RenewalMode.OFFER.name,
            endDateEpochMs = nowMs + 25 * dayMs
        )
        val outsideLeadWindow = baseLease.copy(
            id = "lease_early",
            renewalMode = RenewalMode.AUTO_RENEW.name,
            endDateEpochMs = nowMs + 45 * dayMs
        )
        val manualLease = baseLease.copy(
            id = "lease_manual",
            renewalMode = RenewalMode.MANUAL.name
        )
        val alreadyProcessed = baseLease.copy(
            id = "lease_done",
            renewalMode = RenewalMode.AUTO_RENEW.name,
            lastRenewalProcessedForEndMs = baseLease.endDateEpochMs
        )
        val pendingOffer = baseLease.copy(
            id = "lease_pending",
            renewalMode = RenewalMode.OFFER.name,
            renewalOfferStatus = RenewalOfferStatus.PENDING.name
        )
        val declinedOffer = baseLease.copy(
            id = "lease_declined",
            renewalMode = RenewalMode.OFFER.name,
            renewalOfferStatus = RenewalOfferStatus.DECLINED.name
        )
        val terminatedLease = baseLease.copy(
            id = "lease_term",
            renewalMode = RenewalMode.AUTO_RENEW.name,
            status = LeaseStatus.TERMINATED.name
        )

        val candidates = LeaseAutomationEngine.evaluateLeaseRenewals(
            leases = listOf(
                offerDue,
                autoRenewDue,
                outsideLeadWindow,
                manualLease,
                alreadyProcessed,
                pendingOffer,
                declinedOffer,
                terminatedLease
            ),
            nowMs = nowMs
        )

        assertEquals(2, candidates.size)
        // Sorted by earliest endDateEpochMs
        assertEquals("lease_auto", candidates[0].lease.id)
        assertEquals(RenewalActionType.AUTO_RENEW, candidates[0].actionType)
        assertEquals(3_300_000L, candidates[0].projectedRentCents)

        assertEquals("lease_offer", candidates[1].lease.id)
        assertEquals(RenewalActionType.SEND_OFFER, candidates[1].actionType)
        assertEquals(3_300_000L, candidates[1].projectedRentCents)
    }

    @Test
    fun `evaluateOverdueLateFees applies non-compounding fee after grace period only`() {
        val nowMs = 1_760_000_000_000L
        val leaseEnabled = Lease(
            id = "lease_1",
            ownerUid = "owner_1",
            authorizedUids = listOf("owner_1", "mgr_1"),
            gracePeriodDays = 5L,
            lateFeePercent = 5.0,
            lateFeeEnabled = true
        )
        val leaseDisabled = Lease(
            id = "lease_2",
            ownerUid = "owner_1",
            authorizedUids = listOf("owner_1", "mgr_1"),
            gracePeriodDays = 5L,
            lateFeePercent = 5.0,
            lateFeeEnabled = false
        )

        // Past grace period by 2 days -> eligible (5% of 2,000,000 = 100,000)
        val eligibleInvoice = Invoice(
            id = "inv_eligible",
            leaseId = "lease_1",
            totalDueCents = 4_000_000L,
            paidCents = 2_000_000L,
            outstandingCents = 2_000_000L,
            dueDateEpochMs = nowMs - 7 * dayMs,
            status = InvoiceStatus.PARTIALLY_PAID.name
        )
        // Still inside grace period -> excluded
        val inGraceInvoice = Invoice(
            id = "inv_grace",
            leaseId = "lease_1",
            totalDueCents = 4_000_000L,
            paidCents = 0L,
            outstandingCents = 4_000_000L,
            dueDateEpochMs = nowMs - 3 * dayMs,
            status = InvoiceStatus.UNPAID.name
        )
        // Already assessed -> excluded (non-compounding)
        val alreadyAssessed = eligibleInvoice.copy(
            id = "inv_assessed",
            lateFeeCents = 100_000L,
            lateFeeAppliedAtEpochMs = nowMs - dayMs,
            status = InvoiceStatus.OVERDUE.name
        )
        // Waived -> excluded
        val waivedInvoice = eligibleInvoice.copy(
            id = "inv_waived",
            lateFeeWaived = true
        )
        // Lease has lateFeeEnabled = false -> excluded
        val disabledLeaseInvoice = eligibleInvoice.copy(
            id = "inv_disabled",
            leaseId = "lease_2"
        )
        // Already paid -> excluded
        val paidInvoice = eligibleInvoice.copy(
            id = "inv_paid",
            outstandingCents = 0L,
            status = InvoiceStatus.PAID.name
        )

        val candidates = LeaseAutomationEngine.evaluateOverdueLateFees(
            invoices = listOf(
                eligibleInvoice,
                inGraceInvoice,
                alreadyAssessed,
                waivedInvoice,
                disabledLeaseInvoice,
                paidInvoice
            ),
            leases = listOf(leaseEnabled, leaseDisabled),
            nowMs = nowMs
        )

        assertEquals(1, candidates.size)
        val candidate = candidates.single()
        assertEquals("inv_eligible", candidate.invoice.id)
        assertEquals(100_000L, candidate.lateFeeCents)
        assertEquals(5.0, candidate.lateFeeRatePercent, 0.001)
        assertEquals(4_100_000L, candidate.newTotalDueCents)
        assertEquals(2_100_000L, candidate.newOutstandingCents)
        assertEquals(3, candidate.daysPastGrace)
    }

    @Test
    fun `computeLateFeeWaiver restores original totals and handles overpayment credit refund`() {
        val nowMs = 1_760_000_000_000L
        // Unpaid overdue invoice with 250,000 late fee waived
        val unpaidOverdue = Invoice(
            id = "inv_1",
            totalDueCents = 5_250_000L,
            paidCents = 0L,
            outstandingCents = 5_250_000L,
            lateFeeCents = 250_000L,
            dueDateEpochMs = nowMs - 10 * dayMs,
            status = InvoiceStatus.OVERDUE.name
        )
        val waiveUnpaid = LeaseAutomationEngine.computeLateFeeWaiver(unpaidOverdue, nowMs)
        assertEquals(250_000L, waiveUnpaid.waivedFeeCents)
        assertEquals(5_000_000L, waiveUnpaid.newTotalDueCents)
        assertEquals(5_000_000L, waiveUnpaid.newOutstandingCents)
        assertEquals(0L, waiveUnpaid.creditRefundCents)
        assertEquals(InvoiceStatus.OVERDUE, waiveUnpaid.newStatus)

        // Invoice where tenant already paid 5,100,000 against 5,250,000 -> waiving 250,000 produces 100,000 credit refund
        val overpaidAfterWaive = Invoice(
            id = "inv_2",
            totalDueCents = 5_250_000L,
            paidCents = 5_100_000L,
            outstandingCents = 150_000L,
            lateFeeCents = 250_000L,
            dueDateEpochMs = nowMs - 10 * dayMs,
            status = InvoiceStatus.OVERDUE.name
        )
        val waiveOverpaid = LeaseAutomationEngine.computeLateFeeWaiver(overpaidAfterWaive, nowMs)
        assertEquals(5_000_000L, waiveOverpaid.newTotalDueCents)
        assertEquals(5_000_000L, waiveOverpaid.newPaidCents)
        assertEquals(0L, waiveOverpaid.newOutstandingCents)
        assertEquals(100_000L, waiveOverpaid.creditRefundCents)
        assertEquals(InvoiceStatus.PAID, waiveOverpaid.newStatus)
    }

    @Test
    fun `PermissionPolicy enforces owner-only pricing and late fee waivers while permitting manager sweeps`() {
        val lease = Lease(
            id = "lease_perm",
            ownerUid = "owner_1",
            managerUids = listOf("mgr_1"),
            authorizedUids = listOf("owner_1", "mgr_1")
        )

        assertTrue(PermissionPolicy.canEditLeaseAutomationPricing("owner_1", UserRole.LANDLORD, lease))
        assertFalse(PermissionPolicy.canEditLeaseAutomationPricing("mgr_1", UserRole.PROPERTY_MANAGER, lease))
        assertFalse(PermissionPolicy.canEditLeaseAutomationPricing("tenant_1", UserRole.TENANT, lease))

        assertTrue(PermissionPolicy.canRunLeaseRenewalSweep("owner_1", UserRole.LANDLORD, lease))
        assertTrue(PermissionPolicy.canRunLeaseRenewalSweep("mgr_1", UserRole.PROPERTY_MANAGER, lease))
        assertFalse(PermissionPolicy.canRunLeaseRenewalSweep("outsider", UserRole.PROPERTY_MANAGER, lease))

        assertTrue(PermissionPolicy.canRunLateFeeSweep("owner_1", UserRole.LANDLORD, lease))
        assertTrue(PermissionPolicy.canRunLateFeeSweep("mgr_1", UserRole.PROPERTY_MANAGER, lease))
        assertFalse(PermissionPolicy.canRunLateFeeSweep("tenant_1", UserRole.TENANT, lease))

        assertTrue(PermissionPolicy.canWaiveLateFee("owner_1", UserRole.LANDLORD, "owner_1"))
        assertFalse(PermissionPolicy.canWaiveLateFee("mgr_1", UserRole.PROPERTY_MANAGER, "owner_1"))
        assertTrue(PermissionPolicy.canWaiveLateFee("admin_1", UserRole.ADMIN, "owner_1"))
    }

    @Test
    fun `NotificationHelper maps renewal and late fee event routes and tabs accurately`() {
        assertEquals(
            NotificationHelper.ROUTE_TENANT_HOME,
            NotificationHelper.resolveTargetRoute(NotificationHelper.EVENT_LEASE_RENEWAL_OFFER, null, "TENANT")
        )
        assertNull(NotificationHelper.resolveTargetTab(NotificationHelper.EVENT_LEASE_RENEWAL_OFFER, null))

        assertEquals(
            NotificationHelper.ROUTE_LANDLORD_PORTFOLIO,
            NotificationHelper.resolveTargetRoute(NotificationHelper.EVENT_LEASE_AUTO_RENEWED, null, "LANDLORD")
        )
        assertEquals(
            NotificationHelper.ROUTE_TENANT_HOME,
            NotificationHelper.resolveTargetRoute(NotificationHelper.EVENT_LEASE_AUTO_RENEWED, null, "TENANT")
        )
        assertEquals(
            NotificationHelper.TAB_LANDLORD_LEASES,
            NotificationHelper.resolveTargetTab(NotificationHelper.EVENT_LEASE_AUTO_RENEWED, null)
        )

        assertEquals(
            NotificationHelper.ROUTE_LANDLORD_PORTFOLIO,
            NotificationHelper.resolveTargetRoute(NotificationHelper.EVENT_LEASE_RENEWAL_RESPONSE, null, "PROPERTY_MANAGER")
        )
        assertEquals(
            NotificationHelper.TAB_LANDLORD_LEASES,
            NotificationHelper.resolveTargetTab(NotificationHelper.EVENT_LEASE_RENEWAL_RESPONSE, null)
        )

        assertEquals(
            NotificationHelper.ROUTE_TENANT_PAY,
            NotificationHelper.resolveTargetRoute(NotificationHelper.EVENT_LATE_FEE_ASSESSED, null, "TENANT")
        )
        assertEquals(
            NotificationHelper.TAB_TENANT_INVOICES,
            NotificationHelper.resolveTargetTab(NotificationHelper.EVENT_LATE_FEE_ASSESSED, null)
        )

        assertEquals("5%", LeaseAutomationEngine.formatPercent(5.0))
        assertEquals("5.5%", LeaseAutomationEngine.formatPercent(5.5))
    }
}
