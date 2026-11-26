package com.example

import com.example.data.notifications.NotificationHelper
import com.example.domain.finance.FinancialEngine
import com.example.domain.finance.LeaseAutomationEngine
import com.example.domain.finance.RenewalActionType
import com.example.domain.finance.RentReminderEngine
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
import com.example.domain.model.UserRole
import com.example.domain.security.PermissionPolicy
import java.util.Calendar
import java.util.TimeZone
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

    @Test
    fun `evaluateMoveOutNotice validates required notice period and early termination`() {
        val nowMs = 1_760_000_000_000L
        val lease = Lease(
            id = "lease_notice",
            ownerUid = "owner_1",
            tenantUid = "tenant_1",
            endDateEpochMs = nowMs + 60 * dayMs,
            noticePeriodDays = 30L
        )

        val compliantOnEnd = LeaseAutomationEngine.evaluateMoveOutNotice(
            lease = lease,
            requestedMoveOutEpochMs = nowMs + 60 * dayMs,
            nowMs = nowMs
        )
        assertTrue(compliantOnEnd.isCompliant)
        assertEquals(60, compliantOnEnd.noticeDaysGiven)
        assertEquals(30, compliantOnEnd.requiredNoticeDays)
        assertFalse(compliantOnEnd.isEarlyTermination)

        val compliantEarly = LeaseAutomationEngine.evaluateMoveOutNotice(
            lease = lease,
            requestedMoveOutEpochMs = nowMs + 35 * dayMs,
            nowMs = nowMs
        )
        assertTrue(compliantEarly.isCompliant)
        assertTrue(compliantEarly.isEarlyTermination)

        val shortNotice = LeaseAutomationEngine.evaluateMoveOutNotice(
            lease = lease,
            requestedMoveOutEpochMs = nowMs + 14 * dayMs,
            nowMs = nowMs
        )
        assertFalse(shortNotice.isCompliant)
        assertEquals(14, shortNotice.noticeDaysGiven)
        assertTrue(shortNotice.isEarlyTermination)
    }

    @Test
    fun `evaluateLeaseRenewals skips submitted or acknowledged move-out notices but allows declined or withdrawn`() {
        val nowMs = 1_760_000_000_000L
        val baseLease = Lease(
            id = "lease_base",
            ownerUid = "owner_1",
            tenantUid = "tenant_1",
            rentCents = 4_000_000L,
            startDateEpochMs = nowMs - 300 * dayMs,
            endDateEpochMs = nowMs + 15 * dayMs,
            status = LeaseStatus.ACTIVE.name,
            renewalMode = RenewalMode.AUTO_RENEW.name,
            renewalLeadDays = 30L,
            renewalDurationMonths = 12L
        )

        val submittedNotice = baseLease.copy(
            id = "lease_submitted_notice",
            moveOutNoticeStatus = MoveOutNoticeStatus.SUBMITTED.name
        )
        val acknowledgedNotice = baseLease.copy(
            id = "lease_ack_notice",
            moveOutNoticeStatus = MoveOutNoticeStatus.ACKNOWLEDGED.name
        )
        val declinedNotice = baseLease.copy(
            id = "lease_declined_notice",
            moveOutNoticeStatus = MoveOutNoticeStatus.DECLINED.name
        )
        val withdrawnNotice = baseLease.copy(
            id = "lease_withdrawn_notice",
            moveOutNoticeStatus = MoveOutNoticeStatus.WITHDRAWN.name
        )

        val candidates = LeaseAutomationEngine.evaluateLeaseRenewals(
            leases = listOf(submittedNotice, acknowledgedNotice, declinedNotice, withdrawnNotice),
            nowMs = nowMs
        )
        assertEquals(listOf("lease_declined_notice", "lease_withdrawn_notice"), candidates.map { it.lease.id })
    }

    @Test
    fun `computeBillingPeriodRentCents prorates acknowledged vacate month and skips subsequent months`() {
        val nairobiTz = TimeZone.getTimeZone("Africa/Nairobi")
        val nov15Ms = Calendar.getInstance(nairobiTz).apply {
            clear()
            set(2026, Calendar.NOVEMBER, 15, 10, 0, 0)
        }.timeInMillis
        val nov30Ms = Calendar.getInstance(nairobiTz).apply {
            clear()
            set(2026, Calendar.NOVEMBER, 30, 10, 0, 0)
        }.timeInMillis

        val midMonthLease = Lease(
            id = "lease_prorate",
            rentCents = 3_000_000L,
            moveOutNoticeStatus = MoveOutNoticeStatus.ACKNOWLEDGED.name,
            requestedMoveOutEpochMs = nov15Ms
        )

        val novResult = LeaseAutomationEngine.computeBillingPeriodRentCents(midMonthLease, "2026-11")
        assertTrue(novResult.prorated)
        assertEquals(15, novResult.occupiedDays)
        assertEquals(30, novResult.daysInMonth)
        assertEquals(1_500_000L, novResult.rentCents)
        assertTrue(novResult.prorationNote?.contains("15/30") == true)

        val decResult = LeaseAutomationEngine.computeBillingPeriodRentCents(midMonthLease, "2026-12")
        assertTrue(decResult.prorated)
        assertEquals(0, decResult.occupiedDays)
        assertEquals(0L, decResult.rentCents)

        val endOfMonthLease = midMonthLease.copy(requestedMoveOutEpochMs = nov30Ms)
        val fullNovResult = LeaseAutomationEngine.computeBillingPeriodRentCents(endOfMonthLease, "2026-11")
        assertFalse(fullNovResult.prorated)
        assertEquals(3_000_000L, fullNovResult.rentCents)
        assertNull(fullNovResult.prorationNote)
    }

    @Test
    fun `evaluateAutoBillingCandidates respects lead window, deduplication, credits, and move-out proration`() {
        val nairobiTz = TimeZone.getTimeZone("Africa/Nairobi")
        // 2026-11-02 09:00 EAT (3 days before Nov 5 due date)
        val nov2Ms = Calendar.getInstance(nairobiTz).apply {
            clear()
            set(2026, Calendar.NOVEMBER, 2, 9, 0, 0)
        }.timeInMillis
        val nov15Ms = Calendar.getInstance(nairobiTz).apply {
            clear()
            set(2026, Calendar.NOVEMBER, 15, 12, 0, 0)
        }.timeInMillis
        val oct20Ms = Calendar.getInstance(nairobiTz).apply {
            clear()
            set(2026, Calendar.OCTOBER, 20, 12, 0, 0)
        }.timeInMillis

        val eligibleProratedLease = Lease(
            id = "lease_bill_1",
            ownerUid = "owner_1",
            tenantUid = "tenant_1",
            rentCents = 3_000_000L,
            dueDayOfMonth = 5L,
            gracePeriodDays = 5L,
            billingLeadDays = 5L,
            autoBillingEnabled = true,
            creditBalanceCents = 200_000L,
            status = LeaseStatus.ACTIVE.name,
            moveOutNoticeStatus = MoveOutNoticeStatus.ACKNOWLEDGED.name,
            requestedMoveOutEpochMs = nov15Ms
        )
        val outsideLeadLease = eligibleProratedLease.copy(
            id = "lease_early_due",
            dueDayOfMonth = 15L,
            billingLeadDays = 5L,
            moveOutNoticeStatus = MoveOutNoticeStatus.NONE.name,
            requestedMoveOutEpochMs = null
        )
        val autoBillingDisabled = eligibleProratedLease.copy(
            id = "lease_disabled_bill",
            autoBillingEnabled = false
        )
        val alreadyBilledLease = eligibleProratedLease.copy(
            id = "lease_billed",
            moveOutNoticeStatus = MoveOutNoticeStatus.NONE.name,
            requestedMoveOutEpochMs = null
        )
        val vacatedPriorMonth = eligibleProratedLease.copy(
            id = "lease_vacated",
            requestedMoveOutEpochMs = oct20Ms
        )

        val existingInvoices = listOf(
            // Arrears from prior month for lease_bill_1
            Invoice(
                id = "lease_bill_1_2026-10",
                leaseId = "lease_bill_1",
                billingPeriod = "2026-10",
                outstandingCents = 500_000L,
                status = InvoiceStatus.OVERDUE.name
            ),
            // Existing Nov invoice for lease_billed
            Invoice(
                id = "lease_billed_2026-11",
                leaseId = "lease_billed",
                billingPeriod = "2026-11",
                outstandingCents = 3_000_000L,
                status = InvoiceStatus.UNPAID.name
            )
        )

        val candidates = LeaseAutomationEngine.evaluateAutoBillingCandidates(
            leases = listOf(
                eligibleProratedLease,
                outsideLeadLease,
                autoBillingDisabled,
                alreadyBilledLease,
                vacatedPriorMonth
            ),
            existingInvoices = existingInvoices,
            nowMs = nov2Ms
        )

        assertEquals(1, candidates.size)
        val candidate = candidates.single()
        assertEquals("lease_bill_1", candidate.lease.id)
        assertEquals("2026-11", candidate.billingPeriod)
        assertEquals(1_500_000L, candidate.rentCents)
        assertEquals(500_000L, candidate.previousBalanceCents)
        assertEquals(200_000L, candidate.creditToApplyCents)
        assertEquals(2_000_000L, candidate.totalDueCents)
        assertEquals(1_800_000L, candidate.outstandingCents)
        assertTrue(candidate.prorated)
        assertTrue(candidate.prorationNote?.contains("15/30") == true)
    }

    @Test
    fun `PermissionPolicy and NotificationHelper enforce notice and auto-billing access and routing`() {
        val lease = Lease(
            id = "lease_perm_notice",
            ownerUid = "owner_1",
            managerUids = listOf("mgr_1"),
            authorizedUids = listOf("owner_1", "mgr_1"),
            tenantUid = "tenant_1",
            status = LeaseStatus.ACTIVE.name
        )

        assertTrue(PermissionPolicy.canEditLeaseNoticeAndBillingSettings("owner_1", UserRole.LANDLORD, lease))
        assertFalse(PermissionPolicy.canEditLeaseNoticeAndBillingSettings("mgr_1", UserRole.PROPERTY_MANAGER, lease))
        assertFalse(PermissionPolicy.canEditLeaseNoticeAndBillingSettings("tenant_1", UserRole.TENANT, lease))

        assertTrue(PermissionPolicy.canReviewMoveOutNotice("owner_1", UserRole.LANDLORD, lease))
        assertTrue(PermissionPolicy.canReviewMoveOutNotice("mgr_1", UserRole.PROPERTY_MANAGER, lease))
        assertFalse(PermissionPolicy.canReviewMoveOutNotice("outsider", UserRole.PROPERTY_MANAGER, lease))

        assertTrue(PermissionPolicy.canRunAutoBillingSweep("owner_1", UserRole.LANDLORD, lease))
        assertTrue(PermissionPolicy.canRunAutoBillingSweep("mgr_1", UserRole.PROPERTY_MANAGER, lease))
        assertFalse(PermissionPolicy.canRunAutoBillingSweep("tenant_1", UserRole.TENANT, lease))

        assertTrue(PermissionPolicy.canSubmitMoveOutNotice("tenant_1", UserRole.TENANT, lease))
        assertFalse(PermissionPolicy.canSubmitMoveOutNotice("tenant_2", UserRole.TENANT, lease))
        assertFalse(
            PermissionPolicy.canSubmitMoveOutNotice(
                "tenant_1",
                UserRole.TENANT,
                lease.copy(status = LeaseStatus.TERMINATED.name)
            )
        )

        assertEquals(
            NotificationHelper.ROUTE_LANDLORD_PORTFOLIO,
            NotificationHelper.resolveTargetRoute(NotificationHelper.EVENT_MOVE_OUT_NOTICE_SUBMITTED, null, "PROPERTY_MANAGER")
        )
        assertEquals(
            NotificationHelper.TAB_LANDLORD_LEASES,
            NotificationHelper.resolveTargetTab(NotificationHelper.EVENT_MOVE_OUT_NOTICE_SUBMITTED, null)
        )
        assertEquals(
            NotificationHelper.ROUTE_TENANT_HOME,
            NotificationHelper.resolveTargetRoute(NotificationHelper.EVENT_MOVE_OUT_NOTICE_REVIEWED, null, "TENANT")
        )
        assertNull(NotificationHelper.resolveTargetTab(NotificationHelper.EVENT_MOVE_OUT_NOTICE_REVIEWED, null))
    }

    @Test
    fun `recurring schedule builders and date helpers compute billing periods and checkpoints accurately`() {
        val nairobiTz = TimeZone.getTimeZone("Africa/Nairobi")
        val nov2Ms = Calendar.getInstance(nairobiTz).apply {
            clear()
            set(2026, Calendar.NOVEMBER, 2, 9, 0, 0)
        }.timeInMillis

        assertEquals("2026-12", LeaseAutomationEngine.nextBillingPeriod("2026-11"))
        assertEquals("2027-01", LeaseAutomationEngine.nextBillingPeriod("2026-12"))

        val cycleBillingDateMs = LeaseAutomationEngine.computeCycleBillingDateMs(
            billingPeriod = "2026-11",
            dueDayOfMonth = 5,
            leadDays = 3
        )
        val expectedBillingCal = Calendar.getInstance(nairobiTz).apply { timeInMillis = cycleBillingDateMs }
        assertEquals(2026, expectedBillingCal.get(Calendar.YEAR))
        assertEquals(Calendar.NOVEMBER, expectedBillingCal.get(Calendar.MONTH))
        assertEquals(2, expectedBillingCal.get(Calendar.DAY_OF_MONTH))

        val nextBillingMs = LeaseAutomationEngine.computeNextBillingEpochMs(
            afterPeriod = "2026-11",
            dueDayOfMonth = 5,
            leadDays = 3
        )
        val expectedNextCal = Calendar.getInstance(nairobiTz).apply { timeInMillis = nextBillingMs }
        assertEquals(2026, expectedNextCal.get(Calendar.YEAR))
        assertEquals(Calendar.DECEMBER, expectedNextCal.get(Calendar.MONTH))
        assertEquals(2, expectedNextCal.get(Calendar.DAY_OF_MONTH))

        val lease = Lease(
            id = "lease_sched_1",
            orgId = "org_1",
            propertyId = "prop_1",
            propertyName = "Kilimani Heights",
            unitId = "unit_1",
            unitNumber = "A1",
            ownerUid = "owner_1",
            managerUids = listOf("mgr_1"),
            authorizedUids = listOf("owner_1", "mgr_1"),
            tenantUid = "tenant_1",
            tenantName = "Amina",
            rentCents = 3_000_000L,
            dueDayOfMonth = 5L,
            billingLeadDays = 3L,
            gracePeriodDays = 4L,
            lateFeeEnabled = true,
            lateFeePercent = 5.0,
            autoBillingEnabled = true,
            status = LeaseStatus.ACTIVE.name
        )

        val schedule = LeaseAutomationEngine.buildScheduleForLease(
            lease = lease,
            nowMs = nov2Ms,
            recurringExtraCents = 150_000L,
            recurringExtraNote = "Water & garbage",
            issueMode = RecurringIssueMode.DRAFT_FOR_REVIEW
        )
        assertEquals("lease_sched_1", schedule.id)
        assertEquals("lease_sched_1", schedule.leaseId)
        assertEquals(3_000_000L, schedule.amountCents)
        assertEquals(150_000L, schedule.recurringExtraCents)
        assertEquals("Water & garbage", schedule.recurringExtraNote)
        assertEquals(5L, schedule.billingDayOfMonth)
        assertEquals(3L, schedule.leadDays)
        assertEquals(RecurringScheduleStatus.ACTIVE.name, schedule.scheduleStatus)
        assertEquals(RecurringIssueMode.DRAFT_FOR_REVIEW.name, schedule.issueMode)
        assertEquals(cycleBillingDateMs, schedule.nextBillingEpochMs)

        // Ensure grace period fields are NOT duplicated onto RecurringInvoiceSchedule Firestore payload
        val createMap = schedule.toCreateMap()
        assertFalse(createMap.containsKey("gracePeriodDays"))
        assertFalse(createMap.containsKey("lateFeeEnabled"))
        assertFalse(createMap.containsKey("lateFeePercent"))
    }

    @Test
    fun `evaluateAutoBillingCandidates honors recurring schedule modes, paused status, draft credit deferral, and lease grace period`() {
        val nairobiTz = TimeZone.getTimeZone("Africa/Nairobi")
        val nov2Ms = Calendar.getInstance(nairobiTz).apply {
            clear()
            set(2026, Calendar.NOVEMBER, 2, 9, 0, 0)
        }.timeInMillis

        val baseLease = Lease(
            id = "lease_draft_mode",
            orgId = "org_1",
            propertyId = "prop_1",
            propertyName = "Kilimani Heights",
            unitId = "unit_1",
            unitNumber = "B1",
            ownerUid = "owner_1",
            managerUids = listOf("mgr_1"),
            authorizedUids = listOf("owner_1", "mgr_1"),
            tenantUid = "tenant_1",
            tenantName = "Amina",
            rentCents = 2_500_000L,
            creditBalanceCents = 500_000L,
            dueDayOfMonth = 5L,
            billingLeadDays = 3L,
            gracePeriodDays = 6L,
            autoBillingEnabled = true,
            status = LeaseStatus.ACTIVE.name
        )
        val autoLease = baseLease.copy(
            id = "lease_auto_mode",
            unitNumber = "B2",
            creditBalanceCents = 1_000_000L
        )
        val pausedLease = baseLease.copy(
            id = "lease_paused_sched",
            unitNumber = "B3"
        )

        val draftSchedule = RecurringInvoiceSchedule(
            id = "lease_draft_mode",
            leaseId = "lease_draft_mode",
            amountCents = 2_800_000L,
            recurringExtraCents = 200_000L,
            recurringExtraNote = "Garbage fee",
            billingDayOfMonth = 5L,
            leadDays = 3L,
            scheduleStatus = RecurringScheduleStatus.ACTIVE.name,
            issueMode = RecurringIssueMode.DRAFT_FOR_REVIEW.name
        )
        val autoSchedule = RecurringInvoiceSchedule(
            id = "lease_auto_mode",
            leaseId = "lease_auto_mode",
            amountCents = 2_500_000L,
            recurringExtraCents = 100_000L,
            recurringExtraNote = "Water",
            billingDayOfMonth = 5L,
            leadDays = 3L,
            scheduleStatus = RecurringScheduleStatus.ACTIVE.name,
            issueMode = RecurringIssueMode.AUTO_ISSUE.name
        )
        val pausedSchedule = RecurringInvoiceSchedule(
            id = "lease_paused_sched",
            leaseId = "lease_paused_sched",
            amountCents = 2_500_000L,
            billingDayOfMonth = 5L,
            leadDays = 3L,
            scheduleStatus = RecurringScheduleStatus.PAUSED.name,
            issueMode = RecurringIssueMode.AUTO_ISSUE.name
        )

        // Existing prior DRAFT invoice must NOT be pulled into previousBalanceCents
        val existingInvoices = listOf(
            Invoice(
                id = "lease_draft_mode_2026-10",
                leaseId = "lease_draft_mode",
                billingPeriod = "2026-10",
                outstandingCents = 2_000_000L,
                status = InvoiceStatus.DRAFT.name
            ),
            Invoice(
                id = "lease_auto_mode_2026-10",
                leaseId = "lease_auto_mode",
                billingPeriod = "2026-10",
                outstandingCents = 400_000L,
                status = InvoiceStatus.OVERDUE.name
            )
        )

        val candidates = LeaseAutomationEngine.evaluateAutoBillingCandidates(
            leases = listOf(baseLease, autoLease, pausedLease),
            existingInvoices = existingInvoices,
            nowMs = nov2Ms,
            schedules = listOf(draftSchedule, autoSchedule, pausedSchedule)
        )

        assertEquals(2, candidates.size)
        val draftCandidate = candidates.first { it.lease.id == "lease_draft_mode" }
        assertEquals(InvoiceStatus.DRAFT, draftCandidate.targetStatus)
        assertEquals(2_800_000L, draftCandidate.rentCents)
        assertEquals(200_000L, draftCandidate.extraChargesCents)
        assertEquals(0L, draftCandidate.previousBalanceCents)
        assertEquals(3_000_000L, draftCandidate.totalDueCents)
        assertEquals(0L, draftCandidate.creditToApplyCents)
        assertEquals(3_000_000L, draftCandidate.outstandingCents)
        assertNull(draftCandidate.issuedAtEpochMs)
        assertNull(draftCandidate.paidAtEpochMs)
        assertEquals(
            LeaseAutomationEngine.computeGracePeriodEndsMs(draftCandidate.dueDateEpochMs, 6L),
            draftCandidate.gracePeriodEndsEpochMs
        )

        val autoCandidate = candidates.first { it.lease.id == "lease_auto_mode" }
        assertEquals(InvoiceStatus.PARTIALLY_PAID, autoCandidate.targetStatus)
        assertEquals(2_500_000L, autoCandidate.rentCents)
        assertEquals(100_000L, autoCandidate.extraChargesCents)
        assertEquals(400_000L, autoCandidate.previousBalanceCents)
        assertEquals(3_000_000L, autoCandidate.totalDueCents)
        assertEquals(1_000_000L, autoCandidate.creditToApplyCents)
        assertEquals(2_000_000L, autoCandidate.outstandingCents)
        assertEquals(nov2Ms, autoCandidate.issuedAtEpochMs)
        assertNull(autoCandidate.paidAtEpochMs)
    }

    @Test
    fun `evaluateDraftIssuance applies live lease credit, updated extras, and lease grace period`() {
        val nairobiTz = TimeZone.getTimeZone("Africa/Nairobi")
        val nov3Ms = Calendar.getInstance(nairobiTz).apply {
            clear()
            set(2026, Calendar.NOVEMBER, 3, 10, 0, 0)
        }.timeInMillis
        val nov5Ms = Calendar.getInstance(nairobiTz).apply {
            clear()
            set(2026, Calendar.NOVEMBER, 5, 23, 59, 59)
        }.timeInMillis

        val lease = Lease(
            id = "lease_issue_1",
            rentCents = 2_500_000L,
            creditBalanceCents = 2_700_000L,
            gracePeriodDays = 5L
        )
        val draftInvoice = Invoice(
            id = "lease_issue_1_2026-11",
            leaseId = "lease_issue_1",
            billingPeriod = "2026-11",
            rentCents = 2_500_000L,
            extraChargesCents = 0L,
            previousBalanceCents = 0L,
            totalDueCents = 2_500_000L,
            paidCents = 0L,
            outstandingCents = 2_500_000L,
            dueDateEpochMs = nov5Ms,
            status = InvoiceStatus.DRAFT.name
        )

        val settledResult = LeaseAutomationEngine.evaluateDraftIssuance(
            draftInvoice = draftInvoice,
            lease = lease,
            extraChargesCents = 200_000L,
            nowMs = nov3Ms
        )
        assertEquals(2_700_000L, settledResult.totalDueCents)
        assertEquals(2_700_000L, settledResult.creditToApplyCents)
        assertEquals(2_700_000L, settledResult.newPaidCents)
        assertEquals(0L, settledResult.newOutstandingCents)
        assertEquals(InvoiceStatus.PAID, settledResult.newStatus)
        assertEquals(nov3Ms, settledResult.issuedAtEpochMs)
        assertEquals(nov3Ms, settledResult.paidAtEpochMs)
        assertEquals(
            LeaseAutomationEngine.computeGracePeriodEndsMs(nov5Ms, 5L),
            settledResult.gracePeriodEndsEpochMs
        )

        val issuedResult = LeaseAutomationEngine.evaluateDraftIssuance(
            draftInvoice = draftInvoice,
            lease = lease.copy(creditBalanceCents = 0L),
            extraChargesCents = 100_000L,
            nowMs = nov3Ms
        )
        assertEquals(2_600_000L, issuedResult.totalDueCents)
        assertEquals(0L, issuedResult.creditToApplyCents)
        assertEquals(2_600_000L, issuedResult.newOutstandingCents)
        assertEquals(InvoiceStatus.ISSUED, issuedResult.newStatus)
        assertEquals(nov3Ms, issuedResult.issuedAtEpochMs)
        assertNull(issuedResult.paidAtEpochMs)
    }

    @Test
    fun `DRAFT invoices are excluded from FinancialEngine, RentReminderEngine, and late fees while ISSUED behaves as open receivable`() {
        val nowMs = 1_793_800_000_000L
        val pastDueMs = nowMs - 10L * dayMs

        val lease = Lease(
            id = "lease_excl_1",
            ownerUid = "owner_1",
            tenantUid = "tenant_1",
            rentCents = 2_000_000L,
            gracePeriodDays = 3L,
            lateFeeEnabled = true,
            lateFeePercent = 5.0,
            status = LeaseStatus.ACTIVE.name
        )

        val draftInvoice = Invoice(
            id = "inv_draft",
            leaseId = "lease_excl_1",
            invoiceNumber = "INV-DRAFT",
            billingPeriod = "2026-11",
            rentCents = 2_000_000L,
            totalDueCents = 2_000_000L,
            outstandingCents = 2_000_000L,
            dueDateEpochMs = pastDueMs,
            status = InvoiceStatus.DRAFT.name
        )
        val issuedInvoice = Invoice(
            id = "inv_issued",
            leaseId = "lease_excl_1",
            invoiceNumber = "INV-ISSUED",
            billingPeriod = "2026-10",
            rentCents = 2_000_000L,
            totalDueCents = 2_000_000L,
            outstandingCents = 2_000_000L,
            dueDateEpochMs = pastDueMs,
            issuedAtEpochMs = pastDueMs - 2L * dayMs,
            status = InvoiceStatus.ISSUED.name
        )

        val invoices = listOf(draftInvoice, issuedInvoice)

        val portfolio = FinancialEngine.computePortfolioSummary(
            properties = emptyList(),
            units = emptyList(),
            leases = listOf(lease),
            invoices = invoices,
            payments = emptyList(),
            expenses = emptyList(),
            maintenanceRequests = emptyList(),
            nowMs = nowMs
        )
        assertEquals(2_000_000L, portfolio.outstandingRentCents)
        assertEquals(1, portfolio.overdueInvoices.size)
        assertEquals("inv_issued", portfolio.overdueInvoices.single().id)

        val tenantSummary = FinancialEngine.computeTenantAccountSummary(
            activeLease = lease,
            invoices = invoices,
            payments = emptyList(),
            nowMs = nowMs
        )
        assertEquals(2_000_000L, tenantSummary.totalOutstandingCents)
        assertEquals(2_000_000L, FinancialEngine.computeLeaseUnpaidArrearsCents("lease_excl_1", invoices))

        val allocation = FinancialEngine.planInvoiceAllocation(
            paymentAmountCents = 2_500_000L,
            existingCreditCents = 0L,
            openInvoices = invoices,
            nowMs = nowMs
        )
        assertEquals(1, allocation.updates.size)
        assertEquals("inv_issued", allocation.updates.single().invoiceId)
        assertEquals(500_000L, allocation.remainingCreditCents)

        val reminders = RentReminderEngine.evaluateInvoices(
            invoices = invoices,
            nowMs = nowMs,
            respectCooldown = false
        )
        assertEquals(1, reminders.size)
        assertEquals("inv_issued", reminders.single().invoice.id)

        val lateFeeCandidates = LeaseAutomationEngine.evaluateOverdueLateFees(
            invoices = invoices,
            leases = listOf(lease),
            nowMs = nowMs
        )
        assertEquals(1, lateFeeCandidates.size)
        assertEquals("inv_issued", lateFeeCandidates.single().invoice.id)

        val waiverBeforeDue = LeaseAutomationEngine.computeLateFeeWaiver(
            invoice = issuedInvoice.copy(
                dueDateEpochMs = nowMs + 2L * dayMs,
                lateFeeCents = 100_000L,
                totalDueCents = 2_100_000L,
                outstandingCents = 2_100_000L
            ),
            nowMs = nowMs
        )
        assertEquals(InvoiceStatus.ISSUED, waiverBeforeDue.newStatus)
    }

    @Test
    fun `PermissionPolicy enforces Owner-only recurring pricing and Landlord or Manager status and draft issuance`() {
        val lease = Lease(
            id = "lease_rbac_sched",
            ownerUid = "owner_1",
            managerUids = listOf("mgr_1"),
            authorizedUids = listOf("owner_1", "mgr_1"),
            tenantUid = "tenant_1",
            status = LeaseStatus.ACTIVE.name
        )
        val schedule = RecurringInvoiceSchedule(
            id = "lease_rbac_sched",
            leaseId = "lease_rbac_sched",
            ownerUid = "owner_1",
            managerUids = listOf("mgr_1"),
            authorizedUids = listOf("owner_1", "mgr_1"),
            tenantUid = "tenant_1"
        )
        val draftInvoice = Invoice(
            id = "lease_rbac_sched_2026-11",
            leaseId = "lease_rbac_sched",
            ownerUid = "owner_1",
            authorizedUids = listOf("owner_1", "mgr_1"),
            tenantUid = "tenant_1",
            status = InvoiceStatus.DRAFT.name
        )

        assertTrue(PermissionPolicy.canEditRecurringSchedulePricing("owner_1", UserRole.LANDLORD, lease))
        assertTrue(PermissionPolicy.canEditRecurringSchedulePricing("owner_1", UserRole.LANDLORD, schedule))
        assertFalse(PermissionPolicy.canEditRecurringSchedulePricing("mgr_1", UserRole.PROPERTY_MANAGER, lease))
        assertFalse(PermissionPolicy.canEditRecurringSchedulePricing("mgr_1", UserRole.PROPERTY_MANAGER, schedule))
        assertFalse(PermissionPolicy.canEditRecurringSchedulePricing("tenant_1", UserRole.TENANT, schedule))

        assertTrue(PermissionPolicy.canManageRecurringScheduleStatus("owner_1", UserRole.LANDLORD, schedule))
        assertTrue(PermissionPolicy.canManageRecurringScheduleStatus("mgr_1", UserRole.PROPERTY_MANAGER, schedule))
        assertFalse(PermissionPolicy.canManageRecurringScheduleStatus("outsider", UserRole.PROPERTY_MANAGER, schedule))
        assertFalse(PermissionPolicy.canManageRecurringScheduleStatus("tenant_1", UserRole.TENANT, schedule))

        assertTrue(PermissionPolicy.canIssueOrCancelDraftInvoice("owner_1", UserRole.LANDLORD, draftInvoice))
        assertTrue(PermissionPolicy.canIssueOrCancelDraftInvoice("mgr_1", UserRole.PROPERTY_MANAGER, draftInvoice))
        assertFalse(PermissionPolicy.canIssueOrCancelDraftInvoice("tenant_1", UserRole.TENANT, draftInvoice))
    }
}

