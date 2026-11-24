package com.example

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import com.example.domain.model.ChecklistOverviewBucket
import com.example.domain.model.ChecklistOverviewEngine
import com.example.domain.model.ChecklistWorkflowStage
import com.example.domain.model.DigitalSignature
import com.example.domain.model.InspectionRoom
import com.example.domain.model.Lease
import com.example.domain.model.LeaseStatus
import com.example.domain.model.MoveInChecklistStatus
import com.example.domain.model.MoveInConditionChecklist
import com.example.domain.model.RoomInspectionItem
import com.example.domain.model.UserRole
import com.example.ui.components.ChecklistOverviewTab
import com.example.ui.theme.RentFlowTheme
import com.example.ui.viewmodel.ChecklistOverviewFilterState
import com.example.ui.viewmodel.UiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
class ChecklistOverviewDashboardTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val sampleSig = DigitalSignature(
        signerUid = "uid_1",
        signerName = "Signer",
        signerRole = UserRole.LANDLORD.name,
        signedAtEpochMs = 1_760_000_000_000L,
        signatureImageRef = "data:image/png;base64,abc",
        coversInspectionReport = true,
        coversLeaseReport = true
    )

    private fun lease(
        id: String,
        propId: String,
        propName: String,
        unit: String,
        tenant: String,
        status: LeaseStatus = LeaseStatus.ACTIVE
    ) = Lease(
        id = id,
        orgId = "org_1",
        propertyId = propId,
        propertyName = propName,
        unitId = "unit_$id",
        unitNumber = unit,
        ownerUid = "owner_1",
        tenantUid = "tenant_$id",
        tenantName = tenant,
        status = status.name,
        startDateEpochMs = 1_750_000_000_000L
    )

    private fun checklistFor(
        lease: Lease,
        status: MoveInChecklistStatus,
        tenantSummary: String? = null,
        mutuallySigned: Boolean = false
    ) = MoveInConditionChecklist(
        id = "mcc_${lease.id}",
        leaseId = lease.id,
        propertyId = lease.propertyId,
        propertyName = lease.propertyName,
        unitId = lease.unitId,
        unitNumber = lease.unitNumber,
        ownerUid = lease.ownerUid,
        tenantUid = lease.tenantUid,
        tenantName = lease.tenantName,
        status = status.name,
        inspectionDateEpochMs = 1_760_000_000_000L,
        keysHandedOverCount = 2L,
        rooms = listOf(
            InspectionRoom(
                roomId = "r1",
                roomName = "Living Room",
                items = listOf(RoomInspectionItem(itemId = "i1", label = "Walls"))
            )
        ),
        tenantSummary = tenantSummary,
        landlordSignature = if (status != MoveInChecklistStatus.DRAFT) sampleSig else null,
        tenantSignature = if (mutuallySigned || status == MoveInChecklistStatus.ACKNOWLEDGED) {
            sampleSig.copy(signerUid = lease.tenantUid, signerRole = UserRole.TENANT.name)
        } else null
    )

    @Test
    fun `engine categorizes stages, excludes terminated leases without checklists, computes metrics and sorts priority`() {
        val lPendingStart = lease("l_start", "p1", "Kileleshwa Heights", "A1", "Grace Wambui")
        val lAwaitingTenant = lease("l_await", "p1", "Kileleshwa Heights", "A2", "David Kiprop")
        val lDraft = lease("l_draft", "p2", "Lavington Greens", "B1", "Faith Njeri")
        val lDisputed = lease("l_dispute", "p2", "Lavington Greens", "B2", "Kevin Omondi")
        val lDone = lease("l_done", "p1", "Kileleshwa Heights", "A3", "Mercy Akinyi")
        val lTerminatedNoChecklist = lease("l_term", "p1", "Kileleshwa Heights", "A4", "Old Tenant", LeaseStatus.TERMINATED)

        val checklists = listOf(
            checklistFor(lAwaitingTenant, MoveInChecklistStatus.PENDING_TENANT_REVIEW),
            checklistFor(lDraft, MoveInChecklistStatus.DRAFT),
            checklistFor(lDisputed, MoveInChecklistStatus.DISPUTED, tenantSummary = "Broken balcony latch"),
            checklistFor(lDone, MoveInChecklistStatus.ACKNOWLEDGED, mutuallySigned = true)
        )

        val summary = ChecklistOverviewEngine.buildSummary(
            leases = listOf(lDone, lPendingStart, lAwaitingTenant, lDraft, lDisputed, lTerminatedNoChecklist),
            checklists = checklists
        )

        assertEquals(5, summary.totalCount)
        assertEquals(2, summary.pendingCount) // Pending Start + Awaiting Tenant
        assertEquals(2, summary.inProgressCount) // Draft + Disputed
        assertEquals(1, summary.completedCount)
        assertEquals(1, summary.pendingStartCount)
        assertEquals(1, summary.awaitingTenantCount)
        assertEquals(1, summary.draftCount)
        assertEquals(1, summary.disputedCount)
        assertEquals(20, summary.completionRatePercent)

        // Priority sort: DISPUTED -> PENDING_START -> DRAFT -> PENDING_TENANT_SIGNOFF -> COMPLETED
        assertEquals(ChecklistWorkflowStage.DISPUTED, summary.items[0].stage)
        assertEquals(ChecklistWorkflowStage.PENDING_START, summary.items[1].stage)
        assertEquals(ChecklistWorkflowStage.DRAFT, summary.items[2].stage)
        assertEquals(ChecklistWorkflowStage.PENDING_TENANT_SIGNOFF, summary.items[3].stage)
        assertEquals(ChecklistWorkflowStage.COMPLETED, summary.items[4].stage)

        // Filter tests
        val completedOnly = ChecklistOverviewEngine.filterItems(
            summary = summary,
            bucketFilter = ChecklistOverviewBucket.COMPLETED
        )
        assertEquals(1, completedOnly.size)
        assertEquals("l_done", completedOnly.first().lease.id)

        val propertyP2Search = ChecklistOverviewEngine.filterItems(
            summary = summary,
            propertyIdFilter = "p2",
            searchQuery = "omondi"
        )
        assertEquals(1, propertyP2Search.size)
        assertEquals("l_dispute", propertyP2Search.first().lease.id)
    }

    @Test
    fun `dashboard renders KPI tiles, filters via tile taps and search, and triggers checklist and PDF actions`() {
        val lDisputed = lease("l_dispute", "p1", "Kilimani Ridge", "B2", "Kevin Omondi")
        val lPendingStart = lease("l_start", "p1", "Kilimani Ridge", "A1", "Grace Wambui")
        val lCompleted = lease("l_done", "p2", "Westlands Court", "C3", "Mercy Akinyi")

        val checklists = listOf(
            checklistFor(lDisputed, MoveInChecklistStatus.DISPUTED, tenantSummary = "Window latch loose"),
            checklistFor(lCompleted, MoveInChecklistStatus.ACKNOWLEDGED, mutuallySigned = true)
        )
        val summary = ChecklistOverviewEngine.buildSummary(
            leases = listOf(lDisputed, lPendingStart, lCompleted),
            checklists = checklists
        )

        var filterState by mutableStateOf(ChecklistOverviewFilterState())
        var openedLease: Lease? = null
        var openedPdfChecklist: MoveInConditionChecklist? = null

        composeTestRule.setContent {
            RentFlowTheme {
                ChecklistOverviewTab(
                    overviewState = UiState.Success(summary),
                    filterState = filterState,
                    canManageChecklist = { true },
                    onBucketFilterToggle = { bucket ->
                        filterState = filterState.copy(
                            bucket = if (filterState.bucket == bucket) null else bucket
                        )
                    },
                    onPropertyFilterToggle = { propId ->
                        filterState = filterState.copy(
                            propertyId = if (filterState.propertyId == propId) null else propId
                        )
                    },
                    onSearchQueryChange = { q -> filterState = filterState.copy(searchQuery = q) },
                    onClearFilters = { filterState = ChecklistOverviewFilterState() },
                    onOpenChecklist = { openedLease = it },
                    onOpenPdfPreview = { mcc, _ -> openedPdfChecklist = mcc }
                )
            }
        }

        // Hero & KPI counts
        composeTestRule.onNodeWithTag("checklist_overview_health_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("checklist_kpi_pending").assertTextContains("1")
        composeTestRule.onNodeWithTag("checklist_kpi_in_progress").assertTextContains("1")
        composeTestRule.onNodeWithTag("checklist_kpi_completed").assertTextContains("1")

        // Filter to Completed via KPI tile click
        composeTestRule.onNodeWithTag("checklist_kpi_completed").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("checklist_overview_lazy_column")
            .performScrollToNode(hasTestTag("checklist_overview_card_l_done"))
        composeTestRule.onNodeWithTag("checklist_overview_card_l_done").assertIsDisplayed()
        composeTestRule.onNodeWithTag("checklist_overview_card_l_dispute").assertDoesNotExist()

        // Tap PDF button on completed card
        composeTestRule.onNodeWithTag("checklist_overview_pdf_button_l_done").performClick()
        assertEquals("mcc_l_done", openedPdfChecklist?.id)

        // Reset filter back to All by clicking Completed KPI tile again
        composeTestRule.onNodeWithTag("checklist_overview_lazy_column")
            .performScrollToNode(hasTestTag("checklist_kpi_completed"))
        composeTestRule.onNodeWithTag("checklist_kpi_completed").performClick()
        composeTestRule.waitForIdle()

        // Search for pending-start tenant and click Start Checklist
        composeTestRule.onNodeWithTag("checklist_search_input").performTextInput("Wambui")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("checklist_overview_lazy_column")
            .performScrollToNode(hasTestTag("checklist_overview_action_button_l_start"))
        composeTestRule.onNodeWithTag("checklist_overview_action_button_l_start").performClick()
        assertEquals("l_start", openedLease?.id)
        assertTrue(summary.completedCount == 1)
    }
}
