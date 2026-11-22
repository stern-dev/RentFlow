package com.example

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.example.data.notifications.NotificationHelper
import com.example.domain.finance.CurrencyUtils
import com.example.domain.finance.FinancialEngine
import com.example.domain.model.ChecklistCondition
import com.example.domain.model.ChecklistItem
import com.example.domain.model.DeductionCategory
import com.example.domain.model.DepositDeductionItem
import com.example.domain.model.DepositSettlement
import com.example.domain.model.DepositSettlementStatus
import com.example.domain.model.Invoice
import com.example.domain.model.InvoiceStatus
import com.example.domain.model.Lease
import com.example.domain.model.LeaseStatus
import com.example.domain.model.MoveInChecklist
import com.example.domain.model.MoveInChecklistStatus
import com.example.domain.model.RefundMethod
import com.example.domain.model.UserRole
import com.example.domain.security.PermissionPolicy
import com.example.ui.components.DepositSettlementSummaryCard
import com.example.ui.components.MoveInChecklistSummaryCard
import com.example.ui.theme.RentFlowTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class HandoverAndDepositTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        NotificationHelper.clearDedupCacheForTesting()
    }

    @Test
    fun `FinancialEngine computes KES deposit refund, exact zero, and balance owed accurately`() {
        val deductions = listOf(
            DepositDeductionItem(
                id = "d1",
                category = DeductionCategory.DAMAGE_REPAIR.name,
                description = "Repaint bedroom wall",
                amountCents = 450_000L
            ),
            DepositDeductionItem(
                id = "d2",
                category = DeductionCategory.UTILITIES.name,
                description = "Final water bill",
                amountCents = 150_000L
            )
        )

        // Scenario 1: Refund due to tenant (30,000 + 2,000 credit - 6,000 deductions - 4,000 arrears = 22,000 refund)
        val refundCase = FinancialEngine.computeDepositReconciliation(
            depositHeldCents = 3_000_000L,
            creditAppliedCents = 200_000L,
            arrearsIncludedCents = 400_000L,
            deductions = deductions
        )
        assertEquals(600_000L, refundCase.deductionsTotalCents)
        assertEquals(3_200_000L, refundCase.totalAvailableCents)
        assertEquals(1_000_000L, refundCase.totalChargesCents)
        assertEquals(2_200_000L, refundCase.netRefundCents)
        assertEquals(0L, refundCase.balanceOwedCents)

        // Scenario 2: Exact zero balance (10,000 deposit - 6,000 deductions - 4,000 arrears = 0)
        val zeroCase = FinancialEngine.computeDepositReconciliation(
            depositHeldCents = 1_000_000L,
            creditAppliedCents = 0L,
            arrearsIncludedCents = 400_000L,
            deductions = deductions
        )
        assertEquals(0L, zeroCase.netRefundCents)
        assertEquals(0L, zeroCase.balanceOwedCents)

        // Scenario 3: Charges exceed deposit -> Balance owed by tenant
        val owedCase = FinancialEngine.computeDepositReconciliation(
            depositHeldCents = 1_000_000L,
            creditAppliedCents = 0L,
            arrearsIncludedCents = 900_000L,
            deductions = deductions
        )
        assertEquals(0L, owedCase.netRefundCents)
        assertEquals(500_000L, owedCase.balanceOwedCents)
    }

    @Test
    fun `FinancialEngine computes lease unpaid arrears from active invoices only`() {
        val invoices = listOf(
            Invoice(id = "i1", leaseId = "lease_1", status = InvoiceStatus.UNPAID.name, outstandingCents = 2_500_000L),
            Invoice(id = "i2", leaseId = "lease_1", status = InvoiceStatus.PARTIALLY_PAID.name, outstandingCents = 500_000L),
            Invoice(id = "i3", leaseId = "lease_1", status = InvoiceStatus.OVERDUE.name, outstandingCents = 1_000_000L),
            Invoice(id = "i4", leaseId = "lease_1", status = InvoiceStatus.PAID.name, outstandingCents = 999_999L),
            Invoice(id = "i5", leaseId = "lease_1", status = InvoiceStatus.CANCELLED.name, outstandingCents = 888_888L),
            Invoice(id = "i6", leaseId = "lease_other", status = InvoiceStatus.UNPAID.name, outstandingCents = 3_000_000L)
        )
        assertEquals(4_000_000L, FinancialEngine.computeLeaseUnpaidArrearsCents("lease_1", invoices))
        assertEquals("", CurrencyUtils.centsToWholeKesInput(0L))
        assertEquals("25000", CurrencyUtils.centsToWholeKesInput(2_500_000L))
    }

    @Test
    fun `PermissionPolicy authorizes landlord and assigned managers for checklists and settlements`() {
        val lease = Lease(
            id = "lease_1",
            ownerUid = "owner_1",
            managerUids = listOf("mgr_1"),
            authorizedUids = listOf("owner_1", "mgr_1"),
            tenantUid = "tenant_1",
            status = LeaseStatus.ACTIVE.name
        )
        assertTrue(PermissionPolicy.canManageLeaseChecklist("owner_1", UserRole.LANDLORD, lease))
        assertTrue(PermissionPolicy.canManageLeaseChecklist("mgr_1", UserRole.PROPERTY_MANAGER, lease))
        assertFalse(PermissionPolicy.canManageLeaseChecklist("mgr_other", UserRole.PROPERTY_MANAGER, lease))
        assertFalse(PermissionPolicy.canManageLeaseChecklist("tenant_1", UserRole.TENANT, lease))

        assertTrue(PermissionPolicy.canManageDepositSettlement("owner_1", UserRole.LANDLORD, lease))
        assertTrue(PermissionPolicy.canManageDepositSettlement("mgr_1", UserRole.PROPERTY_MANAGER, lease))
        assertFalse(PermissionPolicy.canManageDepositSettlement("tenant_1", UserRole.TENANT, lease))

        assertTrue(PermissionPolicy.canFinalizeDepositSettlement("owner_1", UserRole.LANDLORD, lease))
        assertTrue(PermissionPolicy.canFinalizeDepositSettlement("mgr_1", UserRole.PROPERTY_MANAGER, lease))
        assertFalse(PermissionPolicy.canFinalizeDepositSettlement("tenant_1", UserRole.TENANT, lease))
    }

    @Test
    fun `NotificationHelper resolves handover deep link routes, tabs, and dedup keys`() {
        assertEquals(
            NotificationHelper.ROUTE_TENANT_HOME,
            NotificationHelper.resolveTargetRoute(NotificationHelper.EVENT_CHECKLIST_PENDING_REVIEW, null, "TENANT")
        )
        assertEquals(
            NotificationHelper.ROUTE_TENANT_HOME,
            NotificationHelper.resolveTargetRoute(NotificationHelper.EVENT_SETTLEMENT_ISSUED, null, "TENANT")
        )
        assertEquals(
            NotificationHelper.ROUTE_LANDLORD_PORTFOLIO,
            NotificationHelper.resolveTargetRoute(NotificationHelper.EVENT_CHECKLIST_DISPUTED, null, "LANDLORD")
        )
        assertEquals(
            NotificationHelper.TAB_LANDLORD_LEASES,
            NotificationHelper.resolveTargetTab(NotificationHelper.EVENT_SETTLEMENT_DISPUTED, null)
        )

        val checklistIntent = Intent(context, MainActivity::class.java).apply {
            putExtra(NotificationHelper.EXTRA_TARGET_ROUTE, NotificationHelper.ROUTE_TENANT_HOME)
            putExtra(NotificationHelper.EXTRA_CHECKLIST_ID, "cl_lease_1")
            putExtra(NotificationHelper.EXTRA_LEASE_ID, "lease_1")
        }
        val parsedChecklist = NotificationHelper.parseDeepLinkIntent(checklistIntent)
        assertNotNull(parsedChecklist)
        assertEquals(NotificationHelper.ROUTE_TENANT_HOME, parsedChecklist!!.targetRoute)
        assertEquals("cl_lease_1", parsedChecklist.checklistId)
        assertEquals("lease_1", parsedChecklist.leaseId)

        val settlementIntent = Intent(context, MainActivity::class.java).apply {
            putExtra(NotificationHelper.EXTRA_TARGET_ROUTE, NotificationHelper.ROUTE_LANDLORD_PORTFOLIO)
            putExtra(NotificationHelper.EXTRA_TARGET_TAB, NotificationHelper.TAB_LANDLORD_LEASES)
            putExtra(NotificationHelper.EXTRA_SETTLEMENT_ID, "ds_lease_1")
            putExtra(NotificationHelper.EXTRA_LEASE_ID, "lease_1")
        }
        val parsedSettlement = NotificationHelper.parseDeepLinkIntent(settlementIntent)
        assertNotNull(parsedSettlement)
        assertEquals(NotificationHelper.ROUTE_LANDLORD_PORTFOLIO, parsedSettlement!!.targetRoute)
        assertEquals(NotificationHelper.TAB_LANDLORD_LEASES, parsedSettlement.targetTab)
        assertEquals("ds_lease_1", parsedSettlement.settlementId)

        val keyA = NotificationHelper.buildHandoverDedupKey(
            eventType = NotificationHelper.EVENT_SETTLEMENT_ISSUED,
            checklistId = null,
            settlementId = "ds_lease_1"
        )
        val keyB = NotificationHelper.buildHandoverDedupKey(
            eventType = NotificationHelper.EVENT_SETTLEMENT_ISSUED,
            checklistId = null,
            settlementId = "ds_lease_1"
        )
        assertEquals(keyA, keyB)
        assertTrue(NotificationHelper.shouldDeliverAndMark(keyA, nowMs = 1_000L))
        assertFalse(NotificationHelper.shouldDeliverAndMark(keyB, nowMs = 5_000L))
    }

    @Test
    fun `MoveInChecklistSummaryCard renders waiting state and pending sign-off review CTA`() {
        composeTestRule.setContent {
            RentFlowTheme {
                MoveInChecklistSummaryCard(
                    checklist = null,
                    isTenantView = true,
                    propertyUnitLabel = "Kilimani Ridge • Unit A1",
                    onOpenChecklist = {}
                )
            }
        }
        composeTestRule.onNodeWithTag("tenant_move_in_checklist_card").assertIsDisplayed()
        composeTestRule.onNodeWithText("Not Started").assertIsDisplayed()
        composeTestRule.onNodeWithTag("tenant_open_checklist_button").assertIsNotEnabled()
    }

    @Test
    fun `MoveInChecklistSummaryCard and DepositSettlementSummaryCard display active handover actions`() {
        val checklist = MoveInChecklist(
            id = "cl_1",
            leaseId = "lease_1",
            propertyName = "Kilimani Ridge",
            unitNumber = "A1",
            status = MoveInChecklistStatus.PENDING_TENANT_REVIEW.name,
            keysHandedOverCount = 3L,
            items = listOf(
                ChecklistItem("w1", "Living Room", "Walls & Paint", ChecklistCondition.GOOD.name),
                ChecklistItem("p1", "Kitchen", "Sink Tap", ChecklistCondition.NEEDS_REPAIR.name)
            )
        )
        val lease = Lease(
            id = "lease_1",
            propertyName = "Kilimani Ridge",
            unitNumber = "A1",
            depositCents = 3_000_000L
        )
        val settlement = DepositSettlement(
            id = "ds_1",
            leaseId = "lease_1",
            propertyName = "Kilimani Ridge",
            unitNumber = "A1",
            depositHeldCents = 3_000_000L,
            deductionsTotalCents = 500_000L,
            netRefundCents = 2_500_000L,
            status = DepositSettlementStatus.ISSUED.name,
            refundMethod = RefundMethod.MPESA.name
        )

        composeTestRule.setContent {
            RentFlowTheme {
                androidx.compose.foundation.layout.Column {
                    MoveInChecklistSummaryCard(
                        checklist = checklist,
                        isTenantView = true,
                        propertyUnitLabel = "Kilimani Ridge • Unit A1",
                        onOpenChecklist = {},
                        onExportPdf = {}
                    )
                    DepositSettlementSummaryCard(
                        settlement = settlement,
                        lease = lease,
                        isTenantView = true,
                        onOpenSettlement = {},
                        onExportPdf = {}
                    )
                }
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Review & Sign Off").assertIsDisplayed()
        composeTestRule.onNodeWithTag("tenant_open_checklist_button").assertIsEnabled()
        composeTestRule.onNodeWithTag("tenant_deposit_settlement_card").assertIsDisplayed()
        composeTestRule.onNodeWithText("Review Statement").assertIsDisplayed()
        composeTestRule.onNodeWithTag("tenant_open_settlement_button").assertIsEnabled()
    }
}
