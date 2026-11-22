package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.hasTestTag
import com.example.domain.model.InspectionItemStatus
import com.example.domain.model.InspectionRoom
import com.example.domain.model.Lease
import com.example.domain.model.MoveInChecklistStatus
import com.example.domain.model.MoveInConditionChecklist
import com.example.domain.model.RoomInspectionItem
import com.example.ui.components.MoveInConditionChecklistFullScreen
import com.example.ui.components.MoveInConditionChecklistSummaryCard
import com.example.ui.theme.RentFlowTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
class MoveInConditionChecklistScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val sampleLease = Lease(
        id = "lease_nrb_1",
        orgId = "org_1",
        propertyId = "prop_1",
        propertyName = "Kilimani Ridge",
        unitId = "unit_1",
        unitNumber = "A1",
        ownerUid = "owner_1",
        authorizedUids = listOf("owner_1"),
        tenantUid = "tenant_1",
        tenantName = "Brian Ochieng",
        tenantPhone = "+254712345678"
    )

    private fun samplePendingChecklist(): MoveInConditionChecklist = MoveInConditionChecklist(
        id = "mcc_lease_nrb_1",
        leaseId = "lease_nrb_1",
        orgId = "org_1",
        propertyId = "prop_1",
        propertyName = "Kilimani Ridge",
        unitId = "unit_1",
        unitNumber = "A1",
        ownerUid = "owner_1",
        authorizedUids = listOf("owner_1"),
        tenantUid = "tenant_1",
        tenantName = "Brian Ochieng",
        status = MoveInChecklistStatus.PENDING_TENANT_REVIEW.name,
        inspectionDateEpochMs = 1_760_000_000_000L,
        waterMeterReading = "WM-104",
        electricityMeterReading = "KPLC-998",
        keysHandedOverCount = 3L,
        rooms = listOf(
            InspectionRoom(
                roomId = "room_living",
                roomName = "Living Room",
                items = listOf(
                    RoomInspectionItem(
                        itemId = "living_walls",
                        label = "Walls, Paint & Ceiling",
                        status = InspectionItemStatus.GOOD.name,
                        landlordRemarks = "Freshly painted cream emulsion"
                    )
                )
            ),
            InspectionRoom(
                roomId = "room_kitchen",
                roomName = "Kitchen",
                items = listOf(
                    RoomInspectionItem(
                        itemId = "kitchen_sink",
                        label = "Sink, Taps & Drainage Trap",
                        status = InspectionItemStatus.FAIR.name,
                        landlordRemarks = "Minor scuff under sink"
                    )
                )
            )
        ),
        landlordSummary = "Ready for move-in"
    )

    @Test
    fun `summary card disables tenant CTA before landlord publishes and enables review when pending`() {
        var opened = false
        composeTestRule.setContent {
            RentFlowTheme {
                MoveInConditionChecklistSummaryCard(
                    checklist = null,
                    isTenantView = true,
                    propertyUnitLabel = "Kilimani Ridge • Unit A1",
                    onOpenChecklist = { opened = true }
                )
            }
        }
        composeTestRule.onNodeWithTag("tenant_condition_checklist_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("tenant_open_condition_checklist_button").assertIsNotEnabled()
        assertFalse(opened)
    }

    @Test
    fun `summary card opens review when pending tenant sign-off`() {
        var opened = false
        composeTestRule.setContent {
            RentFlowTheme {
                MoveInConditionChecklistSummaryCard(
                    checklist = samplePendingChecklist(),
                    isTenantView = true,
                    propertyUnitLabel = "Kilimani Ridge • Unit A1",
                    onOpenChecklist = { opened = true }
                )
            }
        }
        composeTestRule.onNodeWithTag("tenant_open_condition_checklist_button")
            .assertIsEnabled()
            .performClick()
        assertTrue(opened)
    }

    @Test
    fun `landlord full screen toggles inspected checkbox, condition chip, inline remarks, collapsible photos, and submits`() {
        var capturedRooms: List<InspectionRoom> = emptyList()
        var capturedSubmitFlag = false

        composeTestRule.setContent {
            RentFlowTheme {
                MoveInConditionChecklistFullScreen(
                    lease = sampleLease,
                    existingChecklist = null,
                    isTenantView = false,
                    canDeleteDraft = false,
                    isBusy = false,
                    onDismiss = {},
                    onSaveLandlordChecklist = { _, _, _, rooms, _, submitToTenant, _ ->
                        capturedRooms = rooms
                        capturedSubmitFlag = submitToTenant
                    }
                )
            }
        }

        // Scroll LazyColumn to Living Room header & item card
        composeTestRule.onNodeWithTag("condition_checklist_lazy_column")
            .performScrollToNode(hasTestTag("inspection_item_card_living_walls"))

        // Toggle inspected checkbox on living_walls
        composeTestRule.onNodeWithTag("item_inspected_checkbox_living_walls").performClick()
        composeTestRule.onNodeWithTag("condition_checklist_progress_label")
            .assertTextContains("1 / 11 items inspected")

        // Select NEEDS_REPAIR condition grade chip and type inline remark
        composeTestRule.onNodeWithTag("item_status_chip_living_walls_NEEDS_REPAIR").performClick()
        composeTestRule.onNodeWithTag("item_landlord_remarks_living_walls")
            .performTextInput("Hairline crack near window")

        // Expand collapsible photo section
        composeTestRule.onNodeWithTag("condition_checklist_lazy_column")
            .performScrollToNode(hasTestTag("item_photo_toggle_living_walls"))
        composeTestRule.onNodeWithTag("item_photo_toggle_living_walls").performClick()
        composeTestRule.onNodeWithTag("condition_checklist_lazy_column")
            .performScrollToNode(hasTestTag("item_add_photo_button_living_walls"))
        composeTestRule.onNodeWithTag("item_photo_section_living_walls").assertIsDisplayed()
        composeTestRule.onNodeWithTag("item_add_photo_button_living_walls").assertIsDisplayed()

        // Submit for tenant sign-off
        composeTestRule.onNodeWithTag("condition_checklist_submit_button").performClick()

        assertTrue(capturedSubmitFlag)
        val savedLivingWalls = capturedRooms
            .flatMap { it.items }
            .first { it.itemId == "living_walls" }
        assertEquals(InspectionItemStatus.NEEDS_REPAIR.name, savedLivingWalls.status)
        assertEquals("Hairline crack near window", savedLivingWalls.landlordRemarks)
    }

    @Test
    fun `tenant review captures inline per-item remarks and discrepancy summary`() {
        var capturedAck = true
        var capturedRemarksByItemId: Map<String, String> = emptyMap()
        var capturedSummary: String? = null

        composeTestRule.setContent {
            RentFlowTheme {
                MoveInConditionChecklistFullScreen(
                    lease = sampleLease,
                    existingChecklist = samplePendingChecklist(),
                    isTenantView = true,
                    canDeleteDraft = false,
                    isBusy = false,
                    onDismiss = {},
                    onSubmitTenantReview = { _, ack, remarksMap, summary, _ ->
                        capturedAck = ack
                        capturedRemarksByItemId = remarksMap
                        capturedSummary = summary
                    }
                )
            }
        }

        composeTestRule.onNodeWithTag("condition_checklist_lazy_column")
            .performScrollToNode(hasTestTag("condition_tenant_summary_input"))
        composeTestRule.onNodeWithTag("condition_tenant_summary_input")
            .performTextInput("Paint mark near entrance")

        composeTestRule.onNodeWithTag("condition_checklist_lazy_column")
            .performScrollToNode(hasTestTag("item_tenant_remarks_living_walls"))
        composeTestRule.onNodeWithTag("item_tenant_remarks_living_walls")
            .performTextInput("Confirmed scuff on south wall")

        composeTestRule.onNodeWithTag("condition_checklist_flag_button").performClick()

        assertFalse(capturedAck)
        assertEquals("Paint mark near entrance", capturedSummary)
        assertEquals("Confirmed scuff on south wall", capturedRemarksByItemId["living_walls"])
    }
}
