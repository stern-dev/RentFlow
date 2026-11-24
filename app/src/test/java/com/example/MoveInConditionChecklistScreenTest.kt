package com.example

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import com.example.data.repository.MoveInChecklistRepository
import com.example.domain.finance.ReportExporter
import com.example.domain.model.DigitalSignature
import com.example.domain.model.InspectionItemStatus
import com.example.domain.model.InspectionRoom
import com.example.domain.model.Lease
import com.example.domain.model.MoveInChecklistStatus
import com.example.domain.model.MoveInConditionChecklist
import com.example.domain.model.RoomInspectionItem
import com.example.domain.model.UserRole
import com.example.ui.components.DigitalSignatureCodec
import com.example.ui.components.MoveInConditionChecklistFullScreen
import com.example.ui.components.MoveInConditionChecklistSummaryCard
import com.example.ui.components.SignatureDraftState
import com.example.ui.components.SignaturePoint
import com.example.ui.components.buildMoveInConditionChecklistLines
import com.example.ui.components.buildMoveInConditionSignatureBlocks
import com.example.ui.theme.RentFlowTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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

    private val samplePngDataUri =
        "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg=="

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
        tenantPhone = "+254712345678",
        rentCents = 3500000L,
        depositCents = 3500000L,
        startDateEpochMs = 1_750_000_000_000L,
        endDateEpochMs = 1_780_000_000_000L,
        termsSummary = "12-month standard residential lease with KES 35,000 deposit."
    )

    private fun samplePendingChecklist(
        includeLandlordSignature: Boolean = true
    ): MoveInConditionChecklist = MoveInConditionChecklist(
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
        landlordSummary = "Ready for move-in",
        landlordSignature = if (includeLandlordSignature) {
            DigitalSignature(
                signerUid = "owner_1",
                signerName = "Wanjiku Kamau",
                signerRole = UserRole.LANDLORD.name,
                signedAtEpochMs = 1_760_000_001_000L,
                signatureImageRef = samplePngDataUri,
                coversInspectionReport = true,
                coversLeaseReport = true
            )
        } else {
            null
        }
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
    fun `landlord full screen toggles inspected checkbox, draws signature, attests, and submits`() {
        var capturedRooms: List<InspectionRoom> = emptyList()
        var capturedSubmitFlag = false
        var capturedSignature: DigitalSignature? = null

        composeTestRule.setContent {
            RentFlowTheme {
                MoveInConditionChecklistFullScreen(
                    lease = sampleLease,
                    existingChecklist = null,
                    isTenantView = false,
                    canDeleteDraft = false,
                    isBusy = false,
                    currentUserUid = "owner_1",
                    currentUserName = "Wanjiku Kamau",
                    currentUserRole = UserRole.LANDLORD,
                    onDismiss = {},
                    onSaveLandlordChecklist = { _, _, _, rooms, _, submitToTenant, landlordSig, _ ->
                        capturedRooms = rooms
                        capturedSubmitFlag = submitToTenant
                        capturedSignature = landlordSig
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

        // Scroll to landlord digital signature pad, verify name, draw signature stroke, and attest
        composeTestRule.onNodeWithTag("condition_checklist_lazy_column")
            .performScrollToNode(hasTestTag("signature_name_input_landlord"))
        composeTestRule.onNodeWithTag("signature_name_input_landlord")
            .performTextReplacement("Wanjiku Kamau")

        composeTestRule.onNodeWithTag("condition_checklist_lazy_column")
            .performScrollToNode(hasTestTag("signature_canvas_landlord"))
        composeTestRule.onNodeWithTag("signature_canvas_landlord").performTouchInput {
            down(Offset(40f, 60f))
            moveBy(Offset(140f, 30f))
            up()
        }

        composeTestRule.onNodeWithTag("condition_checklist_lazy_column")
            .performScrollToNode(hasTestTag("signature_attestation_checkbox_landlord"))
        composeTestRule.onNodeWithTag("signature_attestation_checkbox_landlord").performClick()

        // Sign & submit for tenant counter-sign
        composeTestRule.onNodeWithTag("condition_checklist_submit_button").performClick()

        assertTrue(capturedSubmitFlag)
        assertNotNull(capturedSignature)
        assertEquals("owner_1", capturedSignature?.signerUid)
        assertEquals("Wanjiku Kamau", capturedSignature?.signerName)
        assertEquals(UserRole.LANDLORD.name, capturedSignature?.signerRole)
        assertTrue(capturedSignature?.signatureImageRef?.startsWith("data:image/") == true)
        assertTrue(capturedSignature?.coversInspectionReport == true)
        assertTrue(capturedSignature?.coversLeaseReport == true)

        val savedLivingWalls = capturedRooms
            .flatMap { it.items }
            .first { it.itemId == "living_walls" }
        assertEquals(InspectionItemStatus.NEEDS_REPAIR.name, savedLivingWalls.status)
        assertEquals("Hairline crack near window", savedLivingWalls.landlordRemarks)
    }

    @Test
    fun `unsigned landlord submit is blocked without drawn signature and attestation`() {
        var submitted = false

        composeTestRule.setContent {
            RentFlowTheme {
                MoveInConditionChecklistFullScreen(
                    lease = sampleLease,
                    existingChecklist = null,
                    isTenantView = false,
                    canDeleteDraft = false,
                    isBusy = false,
                    currentUserUid = "owner_1",
                    currentUserName = "Wanjiku Kamau",
                    currentUserRole = UserRole.LANDLORD,
                    onDismiss = {},
                    onSaveLandlordChecklist = { _, _, _, _, _, _, _, _ ->
                        submitted = true
                    }
                )
            }
        }

        composeTestRule.onNodeWithTag("condition_checklist_submit_button").performClick()
        composeTestRule.waitForIdle()
        assertFalse(submitted)
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
                    currentUserUid = "tenant_1",
                    currentUserName = "Brian Ochieng",
                    currentUserRole = UserRole.TENANT,
                    onDismiss = {},
                    onSubmitTenantReview = { _, ack, remarksMap, summary, _, _ ->
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

    @Test
    fun `tenant expands lease accordion, counter-signs pad, confirms dialog, and locks both reports`() {
        var capturedAck = false
        var capturedTenantSig: DigitalSignature? = null

        composeTestRule.setContent {
            RentFlowTheme {
                MoveInConditionChecklistFullScreen(
                    lease = sampleLease,
                    existingChecklist = samplePendingChecklist(includeLandlordSignature = true),
                    isTenantView = true,
                    canDeleteDraft = false,
                    isBusy = false,
                    currentUserUid = "tenant_1",
                    currentUserName = "Brian Ochieng",
                    currentUserRole = UserRole.TENANT,
                    onDismiss = {},
                    onSubmitTenantReview = { _, ack, _, _, tenantSig, _ ->
                        capturedAck = ack
                        capturedTenantSig = tenantSig
                    }
                )
            }
        }

        // Verify linked lease accordion expands to show lease summary terms
        composeTestRule.onNodeWithTag("condition_checklist_lazy_column")
            .performScrollToNode(hasTestTag("combined_lease_summary_card"))
        composeTestRule.onNodeWithTag("toggle_lease_terms_button").performClick()
        composeTestRule.onNodeWithTag("expanded_lease_terms_content").assertIsDisplayed()

        // Verify landlord preview card is displayed while tenant signature pad is active
        composeTestRule.onNodeWithTag("condition_checklist_lazy_column")
            .performScrollToNode(hasTestTag("landlord_signature_preview_card"))
        composeTestRule.onNodeWithTag("landlord_signature_preview_card").assertIsDisplayed()

        composeTestRule.onNodeWithTag("condition_checklist_lazy_column")
            .performScrollToNode(hasTestTag("signature_name_input_tenant"))
        composeTestRule.onNodeWithTag("signature_name_input_tenant")
            .performTextReplacement("Brian Ochieng")

        composeTestRule.onNodeWithTag("condition_checklist_lazy_column")
            .performScrollToNode(hasTestTag("signature_canvas_tenant"))
        composeTestRule.onNodeWithTag("signature_canvas_tenant").performTouchInput {
            down(Offset(35f, 70f))
            moveBy(Offset(120f, -20f))
            up()
        }

        composeTestRule.onNodeWithTag("condition_checklist_lazy_column")
            .performScrollToNode(hasTestTag("signature_attestation_checkbox_tenant"))
        composeTestRule.onNodeWithTag("signature_attestation_checkbox_tenant").performClick()

        // Trigger sign-off -> confirm dialog
        composeTestRule.onNodeWithTag("condition_checklist_signoff_button").performClick()
        composeTestRule.onNodeWithTag("confirm_signoff_checklist_button").performClick()

        assertTrue(capturedAck)
        assertNotNull(capturedTenantSig)
        assertEquals("tenant_1", capturedTenantSig?.signerUid)
        assertEquals("Brian Ochieng", capturedTenantSig?.signerName)
        assertEquals(UserRole.TENANT.name, capturedTenantSig?.signerRole)
        assertTrue(capturedTenantSig?.signatureImageRef?.startsWith("data:image/") == true)
        assertTrue(capturedTenantSig?.coversInspectionReport == true)
        assertTrue(capturedTenantSig?.coversLeaseReport == true)
    }

    @Test
    fun `mutually signed locked checklist shows both signature preview cards and hides input pads`() {
        val lockedChecklist = samplePendingChecklist(includeLandlordSignature = true).copy(
            status = MoveInChecklistStatus.ACKNOWLEDGED.name,
            acknowledgedAtEpochMs = 1_760_000_500_000L,
            acknowledgedByUid = "tenant_1",
            tenantSignature = DigitalSignature(
                signerUid = "tenant_1",
                signerName = "Brian Ochieng",
                signerRole = UserRole.TENANT.name,
                signedAtEpochMs = 1_760_000_500_000L,
                signatureImageRef = samplePngDataUri,
                coversInspectionReport = true,
                coversLeaseReport = true
            )
        )

        composeTestRule.setContent {
            RentFlowTheme {
                MoveInConditionChecklistFullScreen(
                    lease = sampleLease.copy(
                        landlordSignature = lockedChecklist.landlordSignature,
                        tenantSignature = lockedChecklist.tenantSignature,
                        signedChecklistId = lockedChecklist.id
                    ),
                    existingChecklist = lockedChecklist,
                    isTenantView = true,
                    canDeleteDraft = false,
                    isBusy = false,
                    currentUserUid = "tenant_1",
                    currentUserName = "Brian Ochieng",
                    currentUserRole = UserRole.TENANT,
                    onDismiss = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("condition_checklist_lazy_column")
            .performScrollToNode(hasTestTag("landlord_signature_preview_card"))
        composeTestRule.onNodeWithTag("landlord_signature_preview_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("landlord_signature_signer_name")
            .assertTextContains("Wanjiku Kamau", substring = true)

        composeTestRule.onNodeWithTag("condition_checklist_lazy_column")
            .performScrollToNode(hasTestTag("tenant_signature_preview_card"))
        composeTestRule.onNodeWithTag("tenant_signature_preview_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("tenant_signature_signer_name")
            .assertTextContains("Brian Ochieng", substring = true)

        composeTestRule.onNodeWithTag("signature_canvas_landlord").assertDoesNotExist()
        composeTestRule.onNodeWithTag("signature_canvas_tenant").assertDoesNotExist()
    }

    @Test
    fun `signature codec and repository sanitizer validate strokes, data URI, and dual attestation`() {
        val draft = SignatureDraftState(
            strokes = listOf(listOf(SignaturePoint(20f, 80f), SignaturePoint(180f, 60f))),
            canvasWidth = 480f,
            canvasHeight = 180f,
            typedName = "Wanjiku Kamau",
            attested = true
        )
        val built = DigitalSignatureCodec.buildDigitalSignature(
            draft = draft,
            signerUid = "owner_1",
            signerRole = UserRole.LANDLORD
        ).getOrThrow()

        assertTrue(
            built.signatureImageRef.startsWith("data:image/png;base64,") ||
                built.signatureImageRef.startsWith("data:image/jpeg;base64,")
        )
        assertTrue(built.signatureImageRef.length <= DigitalSignature.MAX_SIGNATURE_IMAGE_CHARS)

        val validated = MoveInChecklistRepository.sanitizeAndValidateSignature(
            signature = built,
            expectedUid = "owner_1",
            allowedRoles = setOf(UserRole.LANDLORD, UserRole.PROPERTY_MANAGER),
            roleOverride = UserRole.LANDLORD
        ).getOrThrow()
        assertEquals("Wanjiku Kamau", validated.signerName)

        // Reject missing dual-report attestation
        val unattested = MoveInChecklistRepository.sanitizeAndValidateSignature(
            signature = built.copy(coversLeaseReport = false),
            expectedUid = "owner_1",
            allowedRoles = setOf(UserRole.LANDLORD)
        )
        assertTrue(unattested.isFailure)

        // Reject external URL instead of inline PNG/JPEG data URI
        val externalUri = MoveInChecklistRepository.sanitizeAndValidateSignature(
            signature = built.copy(signatureImageRef = "https://example.com/sig.png"),
            expectedUid = "owner_1",
            allowedRoles = setOf(UserRole.LANDLORD)
        )
        assertTrue(externalUri.isFailure)
    }

    @Test
    fun `pdf builders assemble signature blocks, decode signature bitmaps, and render report lines`() {
        val landlordSig = DigitalSignature(
            signerUid = "owner_1",
            signerName = "Wanjiku Kamau",
            signerRole = UserRole.LANDLORD.name,
            signedAtEpochMs = 1_760_000_001_000L,
            signatureImageRef = samplePngDataUri,
            coversInspectionReport = true,
            coversLeaseReport = true
        )
        val tenantSig = DigitalSignature(
            signerUid = "tenant_1",
            signerName = "Brian Ochieng",
            signerRole = UserRole.TENANT.name,
            signedAtEpochMs = 1_760_000_500_000L,
            signatureImageRef = samplePngDataUri,
            coversInspectionReport = true,
            coversLeaseReport = true
        )
        val signedLease = sampleLease.copy(
            landlordSignature = landlordSig,
            tenantSignature = tenantSig,
            signedChecklistId = "mcc_lease_nrb_1"
        )
        val signedChecklist = samplePendingChecklist(includeLandlordSignature = true).copy(
            status = MoveInChecklistStatus.ACKNOWLEDGED.name,
            tenantSignature = tenantSig,
            acknowledgedAtEpochMs = 1_760_000_500_000L
        )

        val checklistLines = buildMoveInConditionChecklistLines(signedChecklist)
        val checklistBlocks = buildMoveInConditionSignatureBlocks(signedChecklist, signedLease)
        assertTrue(checklistLines.any { it.contains("Kilimani Ridge • Unit A1") })
        assertTrue(checklistLines.any { it.contains("LIVING ROOM") })
        assertEquals(2, checklistBlocks.size)
        assertEquals("Wanjiku Kamau", checklistBlocks[0].signature?.signerName)
        assertEquals("Brian Ochieng", checklistBlocks[1].signature?.signerName)

        val leaseLines = ReportExporter.buildSignedLeaseAgreementLines(signedLease)
        val leaseBlocks = ReportExporter.buildSignedLeaseSignatureBlocks(signedLease)
        assertTrue(leaseLines.any { it.contains("Mutually Signed & Locked") })
        assertTrue(leaseLines.any { it.contains("12-month standard residential lease") })
        assertEquals(2, leaseBlocks.size)
        assertNotNull(ReportExporter.decodeSignatureDataUri(landlordSig.signatureImageRef))
        assertNotNull(ReportExporter.decodeSignatureDataUri(tenantSig.signatureImageRef))
    }
}
