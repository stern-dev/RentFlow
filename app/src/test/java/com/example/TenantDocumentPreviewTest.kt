package com.example

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.domain.finance.PdfDownloadHelper
import com.example.domain.finance.ReportExporter
import com.example.domain.model.Announcement
import com.example.domain.model.DocumentType
import com.example.domain.model.Lease
import com.example.domain.model.Payment
import com.example.domain.model.PaymentMethod
import com.example.domain.model.PaymentStatus
import com.example.domain.model.ScannedDocument
import com.example.ui.components.DocumentPreviewRenderState
import com.example.ui.components.TenantDocumentCategory
import com.example.ui.components.TenantDocumentPreviewSheet
import com.example.ui.components.TenantPreviewDocument
import com.example.ui.theme.RentFlowTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
class TenantDocumentPreviewTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val sampleLease = Lease(
        id = "lease_prev_1",
        propertyName = "Kilimani Heights",
        unitNumber = "A12",
        tenantName = "Grace Wanjiku",
        tenantPhone = "+254712345678",
        rentCents = 4_500_000L,
        depositCents = 4_500_000L,
        dueDayOfMonth = 5L,
        startDateEpochMs = 1_750_000_000_000L,
        endDateEpochMs = 1_781_536_000_000L,
        termsSummary = "Rent is due by the 5th of every month via M-Pesa Paybill 247247. Quiet hours begin at 10:00 PM."
    )

    private val sampleNotice = Announcement(
        id = "ann_water_1",
        propertyName = "Kilimani Heights",
        title = "Scheduled Water Tank Maintenance",
        body = "Dear residents, borehole and rooftop tank servicing will run on Saturday from 9:00 AM to 2:00 PM. Please store sufficient water in advance for household use.",
        authorName = "Building Management"
    )

    private val samplePayment = Payment(
        id = "pay_prev_1",
        propertyName = "Kilimani Heights",
        unitNumber = "A12",
        tenantName = "Grace Wanjiku",
        amountCents = 4_500_000L,
        method = PaymentMethod.MPESA.name,
        referenceCode = "UBK9M12P88",
        receiptNumber = "RCP-202611-A12",
        status = PaymentStatus.VERIFIED.name,
        paymentDateEpochMs = 1_760_000_000_000L
    )

    private val sampleVaultDoc = ScannedDocument(
        id = "vault_doc_1",
        propertyName = "Kilimani Heights",
        unitNumber = "A12",
        documentType = DocumentType.PAYMENT_RECEIPT.name,
        title = "October Water & Rent Slip",
        extractedReference = "UBK9M12P88",
        extractedAmountCents = 4_500_000L,
        pageImageRefs = listOf("data:image/jpeg;base64,abc", "data:image/jpeg;base64,def")
    )

    @Test
    fun `pdf filename normalizer and line wrapper format documents cleanly`() {
        assertEquals(
            "lease_Unit_A12.pdf",
            PdfDownloadHelper.normalizePdfFileName("  lease Unit A12 ")
        )
        assertEquals(
            "official_receipt.pdf",
            PdfDownloadHelper.normalizePdfFileName("official_receipt.pdf")
        )

        val wrapped = ReportExporter.wrapPdfLines(
            lines = listOf(
                "[H]SECTION HEADER",
                "---",
                "This is a very long property notice paragraph that should wrap cleanly across multiple PDF lines without dropping words at ninety characters."
            ),
            maxCharsPerLine = 50
        )
        assertEquals("[H]SECTION HEADER", wrapped[0])
        assertEquals("---", wrapped[1])
        assertTrue(wrapped.size >= 5)
        assertTrue(wrapped.drop(2).all { it.length <= 50 })
    }

    @Test
    fun `preview document descriptors expose accurate categories and file names for all four tenant types`() {
        val vault = TenantPreviewDocument.VaultDoc(sampleVaultDoc)
        val receipt = TenantPreviewDocument.PaymentReceipt(payment = samplePayment)
        val lease = TenantPreviewDocument.TenancyLease(sampleLease)
        val notice = TenantPreviewDocument.PropertyNotice(sampleNotice)

        assertEquals(TenantDocumentCategory.VAULT, vault.category)
        assertEquals(TenantDocumentCategory.RECEIPT, receipt.category)
        assertEquals(TenantDocumentCategory.LEASE, lease.category)
        assertEquals(TenantDocumentCategory.NOTICE, notice.category)

        assertTrue(vault.suggestedFileName.endsWith(".pdf"))
        assertTrue(receipt.suggestedFileName.endsWith(".pdf"))
        assertTrue(lease.suggestedFileName.endsWith(".pdf"))
        assertTrue(notice.suggestedFileName.endsWith(".pdf"))
    }

    @Test
    fun `line builders and download helper prepare structured notice lease and receipt output`() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val noticeLines = ReportExporter.buildPropertyNoticeLines(sampleNotice)
        assertTrue(noticeLines.any { it.contains("Scheduled Water Tank Maintenance") })
        assertTrue(noticeLines.any { it.contains("borehole and rooftop tank servicing") })

        val receiptLines = ReportExporter.buildPaymentConfirmationLines(samplePayment)
        assertTrue(receiptLines.any { it.contains("UBK9M12P88") })
        assertTrue(receiptLines.any { it.contains("Grace Wanjiku") })

        val leaseLines = ReportExporter.buildSignedLeaseAgreementLines(sampleLease)
        assertTrue(leaseLines.any { it.contains("Grace Wanjiku") })
        assertTrue(leaseLines.any { it.contains("Paybill 247247") })

        val srcFile = File(context.cacheDir, "sample_download_test.pdf").apply {
            writeText("%PDF-1.4\n1 0 obj\n<<>>\nendobj\n%%EOF")
        }
        val destUri = android.net.Uri.fromFile(File(context.cacheDir, "copied_download_test.pdf"))
        val copyResult = kotlinx.coroutines.runBlocking {
            PdfDownloadHelper.copyPdfToUri(context, srcFile, destUri)
        }
        assertTrue(copyResult.isSuccess)
    }

    @Test
    fun `preview sheet supports retry paging zoom fullscreen toggle download and share`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val tempFile = File(context.cacheDir, "preview_test.pdf").apply { writeText("%PDF-1.4") }
        val fakePage = Bitmap.createBitmap(120, 170, Bitmap.Config.ARGB_8888).asImageBitmap()

        var failFirstAttempt by mutableStateOf(true)
        var downloadedReady: DocumentPreviewRenderState.Ready? = null
        var sharedReady: DocumentPreviewRenderState.Ready? = null

        composeTestRule.setContent {
            RentFlowTheme {
                TenantDocumentPreviewSheet(
                    document = TenantPreviewDocument.PropertyNotice(sampleNotice),
                    onDismiss = {},
                    customPdfPreparer = {
                        if (failFirstAttempt) {
                            Result.failure(IllegalStateException("Temporary render error"))
                        } else {
                            Result.success(DocumentPreviewRenderState.Ready(pdfFile = tempFile, pageCount = 3))
                        }
                    },
                    customPageRenderer = { _, _ -> fakePage },
                    onDownloadPdfOverride = { downloadedReady = it },
                    onSharePdfOverride = { sharedReady = it }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("doc_preview_bottom_sheet").assertIsDisplayed()
        composeTestRule.onNodeWithTag("doc_preview_category_pill").assertTextContains("Property Notice")
        composeTestRule.onNodeWithTag("doc_preview_error_box").assertIsDisplayed()

        // Retry preparation succeeds
        failFirstAttempt = false
        composeTestRule.onNodeWithTag("doc_preview_retry_button").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("doc_preview_page_card_0").assertIsDisplayed()
        composeTestRule.onNodeWithTag("doc_preview_page_indicator").assertTextContains("1 / 3")

        // Navigate pages
        composeTestRule.onNodeWithTag("doc_preview_next_page_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("doc_preview_page_indicator").assertTextContains("2 / 3")

        composeTestRule.onNodeWithTag("doc_preview_page_chip_2").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("doc_preview_page_indicator").assertTextContains("3 / 3")

        composeTestRule.onNodeWithTag("doc_preview_prev_page_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("doc_preview_page_indicator").assertTextContains("2 / 3")

        // Zoom in -> 150%, Reset -> 100%
        composeTestRule.onNodeWithTag("doc_preview_zoom_in_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("doc_preview_zoom_reset_button").assertTextContains("150%")

        composeTestRule.onNodeWithTag("doc_preview_zoom_reset_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("doc_preview_zoom_reset_button").assertTextContains("100%")

        // Expand to fullscreen and collapse back to bottom sheet
        composeTestRule.onNodeWithTag("doc_preview_fullscreen_toggle").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("doc_preview_fullscreen_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("doc_preview_download_button").assertIsEnabled()

        composeTestRule.onNodeWithTag("doc_preview_fullscreen_toggle").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("doc_preview_bottom_sheet").assertIsDisplayed()

        // Download PDF and Share dispatch callbacks
        composeTestRule.onNodeWithTag("doc_preview_download_button").performClick()
        assertEquals(tempFile, downloadedReady?.pdfFile)

        composeTestRule.onNodeWithTag("doc_preview_share_button").performClick()
        assertEquals(tempFile, sharedReady?.pdfFile)
    }
}
