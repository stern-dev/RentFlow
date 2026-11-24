package com.example

import android.graphics.Bitmap
import android.graphics.RectF
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.example.domain.finance.ChecklistPdfBlock
import com.example.domain.finance.GeneratedChecklistPdf
import com.example.domain.finance.MoveInChecklistPdfService
import com.example.domain.finance.MoveInChecklistPdfSpecBuilder
import com.example.domain.model.DigitalSignature
import com.example.domain.model.InspectionItemStatus
import com.example.domain.model.InspectionRoom
import com.example.domain.model.Lease
import com.example.domain.model.MoveInChecklistStatus
import com.example.domain.model.MoveInConditionChecklist
import com.example.domain.model.RoomInspectionItem
import com.example.domain.model.UserRole
import com.example.ui.components.PdfViewerFullScreen
import com.example.ui.components.PdfViewerUiState
import com.example.ui.theme.RentFlowTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
class MoveInChecklistPdfServiceAndViewerTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val samplePngDataUri =
        "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg=="

    private val sampleLease = Lease(
        id = "lease_pdf_1",
        orgId = "org_1",
        propertyId = "prop_1",
        propertyName = "Westlands Court",
        unitId = "unit_4b",
        unitNumber = "4B",
        ownerUid = "owner_1",
        tenantUid = "tenant_1",
        tenantName = "Amina Hassan",
        rentCents = 4800000L,
        depositCents = 4800000L,
        dueDayOfMonth = 5L,
        startDateEpochMs = 1_750_000_000_000L,
        endDateEpochMs = 1_781_536_000_000L
    )

    private val landlordSig = DigitalSignature(
        signerUid = "owner_1",
        signerName = "Peter Njoroge",
        signerRole = UserRole.LANDLORD.name,
        signedAtEpochMs = 1_760_000_100_000L,
        signatureImageRef = samplePngDataUri,
        coversInspectionReport = true,
        coversLeaseReport = true
    )

    private val tenantSig = DigitalSignature(
        signerUid = "tenant_1",
        signerName = "Amina Hassan",
        signerRole = UserRole.TENANT.name,
        signedAtEpochMs = 1_760_000_200_000L,
        signatureImageRef = samplePngDataUri,
        coversInspectionReport = true,
        coversLeaseReport = true
    )

    private fun buildRichChecklist(
        status: MoveInChecklistStatus,
        withLandlordSig: Boolean,
        withTenantSig: Boolean,
        roomCount: Int = 2
    ): MoveInConditionChecklist {
        val rooms = (1..roomCount).map { roomIndex ->
            InspectionRoom(
                roomId = "room_$roomIndex",
                roomName = "Room $roomIndex",
                roomRemarks = "Checked lighting, paint finish, and windows in room $roomIndex.",
                items = listOf(
                    RoomInspectionItem(
                        itemId = "item_${roomIndex}_a",
                        label = "Walls & Paintwork ($roomIndex)",
                        status = InspectionItemStatus.FAIR.name,
                        imageRefs = listOf(samplePngDataUri, samplePngDataUri, samplePngDataUri),
                        landlordRemarks = "Fresh coat applied before handover; minor mark near door frame documented.",
                        tenantRemarks = "Confirmed minor mark on south corner."
                    ),
                    RoomInspectionItem(
                        itemId = "item_${roomIndex}_b",
                        label = "Locks & Sockets ($roomIndex)",
                        status = InspectionItemStatus.NEEDS_REPAIR.name,
                        imageRefs = listOf(samplePngDataUri),
                        landlordRemarks = "Loose hinge scheduled for carpenter adjustment within 48 hours.",
                        tenantRemarks = "Agreed to scheduled repair window."
                    )
                )
            )
        }
        return MoveInConditionChecklist(
            id = "mcc_lease_pdf_1",
            leaseId = "lease_pdf_1",
            propertyName = "Westlands Court",
            unitNumber = "4B",
            ownerUid = "owner_1",
            tenantUid = "tenant_1",
            tenantName = "Amina Hassan",
            status = status.name,
            inspectionDateEpochMs = 1_760_000_000_000L,
            waterMeterReading = "WM-441",
            electricityMeterReading = "KPLC-88201",
            keysHandedOverCount = 3L,
            rooms = rooms,
            landlordSummary = "Unit handed over clean with 3 front door keys.",
            tenantSummary = "Verified kitchen & living room fixtures.",
            landlordSignature = if (withLandlordSig) landlordSig else null,
            tenantSignature = if (withTenantSig) tenantSig else null,
            acknowledgedAtEpochMs = if (status == MoveInChecklistStatus.ACKNOWLEDGED) 1_760_000_200_000L else null
        )
    }

    @Test
    fun `spec builder marks mutually signed acknowledged report finalized without watermark and caps item photos to 2`() {
        val checklist = buildRichChecklist(
            status = MoveInChecklistStatus.ACKNOWLEDGED,
            withLandlordSig = true,
            withTenantSig = true
        )

        val spec = MoveInChecklistPdfSpecBuilder.buildSpec(checklist, sampleLease)

        assertTrue(spec.isFinalizedSigned)
        assertNull(spec.watermarkText)
        assertEquals("FINALIZED & SIGNED", spec.statusLabel)
        assertTrue(spec.suggestedFileName.endsWith("_signed.pdf"))
        assertEquals(2, spec.roomSections.size)
        // First item had 3 imageRefs; spec caps each item strictly to 2 inline thumbnails
        assertEquals(2, spec.roomSections.first().items.first().photoDataUris.size)
        assertEquals(6, spec.totalPhotos)
        assertEquals(2, spec.flaggedIssues)
        assertNotNull(spec.leaseSummaryLine)
        assertTrue(spec.leaseSummaryLine!!.contains("Due Day 5"))
        assertEquals("Peter Njoroge", spec.signatureBlocks[0].signature?.signerName)
        assertEquals("Amina Hassan", spec.signatureBlocks[1].signature?.signerName)
    }

    @Test
    fun `spec builder applies draft watermark whenever status is non-finalized or missing a signature`() {
        val draftChecklist = buildRichChecklist(
            status = MoveInChecklistStatus.DRAFT,
            withLandlordSig = false,
            withTenantSig = false
        )
        val draftSpec = MoveInChecklistPdfSpecBuilder.buildSpec(draftChecklist, sampleLease)
        assertFalse(draftSpec.isFinalizedSigned)
        assertEquals(MoveInChecklistPdfSpecBuilder.DRAFT_WATERMARK_TEXT, draftSpec.watermarkText)
        assertTrue(draftSpec.suggestedFileName.endsWith("_draft.pdf"))

        val pendingChecklist = buildRichChecklist(
            status = MoveInChecklistStatus.PENDING_TENANT_REVIEW,
            withLandlordSig = true,
            withTenantSig = false
        )
        val pendingSpec = MoveInChecklistPdfSpecBuilder.buildSpec(pendingChecklist, sampleLease)
        assertFalse(pendingSpec.isFinalizedSigned)
        assertEquals(MoveInChecklistPdfSpecBuilder.DRAFT_WATERMARK_TEXT, pendingSpec.watermarkText)

        // ACKNOWLEDGED missing tenant signature even with no fallback -> still watermarked
        val incompleteAck = buildRichChecklist(
            status = MoveInChecklistStatus.ACKNOWLEDGED,
            withLandlordSig = true,
            withTenantSig = false
        )
        val incompleteAckSpec = MoveInChecklistPdfSpecBuilder.buildSpec(incompleteAck, lease = null)
        assertFalse(incompleteAckSpec.isFinalizedSigned)
        assertNotNull(incompleteAckSpec.watermarkText)
    }

    @Test
    fun `inline image decoder handles valid base64 image URIs and aspect-fit helper preserves proportions`() {
        val decoded = MoveInChecklistPdfSpecBuilder.decodeInlineImageDataUri(samplePngDataUri)
        assertNotNull(decoded)
        assertNull(MoveInChecklistPdfSpecBuilder.decodeInlineImageDataUri("https://example.com/photo.png"))
        assertNull(MoveInChecklistPdfSpecBuilder.decodeInlineImageDataUri(""))

        val wideDest = MoveInChecklistPdfService.computeAspectFitRect(
            bmpWidth = 400,
            bmpHeight = 100,
            bounds = RectF(10f, 10f, 110f, 110f)
        )
        assertEquals(100f, wideDest.width(), 0.5f)
        assertEquals(25f, wideDest.height(), 0.5f)
    }

    @Test
    fun `pagination planner splits large room checklists across multiple pages cleanly`() {
        val multiRoomChecklist = buildRichChecklist(
            status = MoveInChecklistStatus.ACKNOWLEDGED,
            withLandlordSig = true,
            withTenantSig = true,
            roomCount = 6
        )
        val spec = MoveInChecklistPdfSpecBuilder.buildSpec(multiRoomChecklist, sampleLease)
        val pages = MoveInChecklistPdfService.buildPagedBlocksForSpec(spec)

        assertTrue("Expected multi-page pagination for 6 detailed rooms with thumbnails", pages.size >= 2)
        assertTrue(pages.first().contains(ChecklistPdfBlock.MetadataAndLeaseBlock))
        assertTrue(pages.last().contains(ChecklistPdfBlock.SignatureExecutionBlock))
    }

    @Test
    fun `pdf viewer displays loading, error retry, draft watermark banner, zoom controls, share and print triggers`() {
        val fakePageBitmap = Bitmap.createBitmap(120, 170, Bitmap.Config.ARGB_8888).asImageBitmap()
        val draftDoc = GeneratedChecklistPdf(
            file = File("draft_test.pdf"),
            title = "RentFlow Move-In Condition Report",
            subtitle = "Westlands Court • Unit 4B",
            isFinalizedSigned = false,
            statusLabel = "DRAFT • PENDING TENANT REVIEW",
            pageCount = 2
        )

        var viewerState by mutableStateOf<PdfViewerUiState>(PdfViewerUiState.Generating)
        var retried = false
        var sharedDoc: GeneratedChecklistPdf? = null
        var printedDoc: GeneratedChecklistPdf? = null

        composeTestRule.setContent {
            RentFlowTheme {
                PdfViewerFullScreen(
                    documentTitle = "Move-In Inspection Report",
                    documentSubtitle = "Westlands Court • Unit 4B",
                    uiState = viewerState,
                    onRetry = { retried = true },
                    onDismiss = {},
                    onShare = { sharedDoc = it },
                    onPrint = { printedDoc = it },
                    customPageRenderer = { _, _ -> fakePageBitmap }
                )
            }
        }

        // Generating state
        composeTestRule.onNodeWithTag("pdf_viewer_loading_box").assertIsDisplayed()

        // Error + Retry state
        composeTestRule.runOnUiThread {
            viewerState = PdfViewerUiState.Error("Failed to format report")
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("pdf_viewer_error_box").assertIsDisplayed()
        composeTestRule.onNodeWithTag("pdf_viewer_retry_button").performClick()
        assertTrue(retried)

        // Ready (Draft Preview) state
        composeTestRule.runOnUiThread {
            viewerState = PdfViewerUiState.Ready(draftDoc)
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("pdf_viewer_status_pill").assertTextContains("Draft Preview")
        composeTestRule.onNodeWithTag("pdf_viewer_draft_banner").assertIsDisplayed()
        composeTestRule.onNodeWithTag("pdf_viewer_page_card_0").assertIsDisplayed()
        composeTestRule.onNodeWithTag("pdf_viewer_page_zoom_badge").assertTextContains("Page 1 of 2 • 100%")

        // Zoom In -> 150%, then Reset -> 100%
        composeTestRule.onNodeWithTag("pdf_viewer_zoom_in_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("pdf_viewer_zoom_reset_button").assertTextContains("150%")

        composeTestRule.onNodeWithTag("pdf_viewer_zoom_reset_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("pdf_viewer_zoom_reset_button").assertTextContains("100%")

        // Print and Share buttons dispatch callbacks
        composeTestRule.onNodeWithTag("pdf_viewer_print_button").performClick()
        assertEquals(draftDoc, printedDoc)

        composeTestRule.onNodeWithTag("pdf_viewer_share_button").performClick()
        assertEquals(draftDoc, sharedDoc)
    }

    @Test
    fun `pdf viewer displays finalized signed badge and hides draft watermark banner`() {
        val fakePageBitmap = Bitmap.createBitmap(120, 170, Bitmap.Config.ARGB_8888).asImageBitmap()
        val signedDoc = GeneratedChecklistPdf(
            file = File("signed_test.pdf"),
            title = "RentFlow Move-In Condition Report",
            subtitle = "Westlands Court • Unit 4B",
            isFinalizedSigned = true,
            statusLabel = "FINALIZED & SIGNED",
            pageCount = 1
        )

        composeTestRule.setContent {
            RentFlowTheme {
                PdfViewerFullScreen(
                    documentTitle = "Move-In Inspection Report",
                    documentSubtitle = "Westlands Court • Unit 4B",
                    uiState = PdfViewerUiState.Ready(signedDoc),
                    onRetry = {},
                    onDismiss = {},
                    onShare = {},
                    onPrint = {},
                    customPageRenderer = { _, _ -> fakePageBitmap }
                )
            }
        }

        composeTestRule.onNodeWithTag("pdf_viewer_status_pill").assertTextContains("Finalized Signed")
        composeTestRule.onNodeWithTag("pdf_viewer_draft_banner").assertDoesNotExist()
        composeTestRule.onNodeWithTag("pdf_viewer_page_card_0").assertIsDisplayed()
    }
}
