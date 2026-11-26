package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.example.data.ai.AiAssistantRepository
import com.example.domain.model.DocumentType
import com.example.domain.model.ScanFilterMode
import com.example.domain.model.ScannedDocument
import com.example.domain.model.UserRole
import com.example.ui.media.DocumentScanProcessor
import com.example.ui.media.NormalizedCropRect
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DocumentScannerFeatureTest {

    private fun createSyntheticPageBitmap(width: Int = 900, height: Int = 1200): Bitmap {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.WHITE)
        for (y in 100 until height step 40) {
            for (x in 80 until (width - 80)) {
                bmp.setPixel(x, y, Color.rgb(30, 45, 70))
            }
        }
        return bmp
    }

    @Test
    fun `normalized crop rect clamps out-of-bounds values and preserves minimum frame`() {
        val raw = NormalizedCropRect(left = -0.3f, top = 0.95f, right = 0.05f, bottom = 1.4f)
        val norm = raw.normalized()

        assertEquals(0f, norm.left, 0.001f)
        assertEquals(0.9f, norm.top, 0.001f)
        assertTrue(norm.right >= norm.left + 0.1f)
        assertEquals(1.0f, norm.bottom, 0.001f)
        assertFalse(norm.isFullFrame())
        assertTrue(NormalizedCropRect().isFullFrame())
    }

    @Test
    fun `applyCropAndRotation crops proportions and rotates 90 degrees accurately`() {
        val src = Bitmap.createBitmap(400, 200, Bitmap.Config.ARGB_8888)
        src.eraseColor(Color.LTGRAY)

        val cropped = DocumentScanProcessor.applyCropAndRotation(
            source = src,
            cropRect = NormalizedCropRect(left = 0.25f, top = 0.0f, right = 0.75f, bottom = 1.0f),
            rotationDegrees = 90
        )

        // 400 * 0.5 = 200 wide, 200 tall -> rotated 90 deg stays 200x200
        assertEquals(200, cropped.width)
        assertEquals(200, cropped.height)

        val rectSrc = Bitmap.createBitmap(400, 200, Bitmap.Config.ARGB_8888)
        val rotatedFull = DocumentScanProcessor.applyCropAndRotation(
            source = rectSrc,
            cropRect = NormalizedCropRect(),
            rotationDegrees = 90
        )
        assertEquals(200, rotatedFull.width)
        assertEquals(400, rotatedFull.height)
    }

    @Test
    fun `applyScanFilter produces grayscale output for black and white mode`() {
        val src = Bitmap.createBitmap(40, 40, Bitmap.Config.ARGB_8888)
        src.eraseColor(Color.rgb(40, 120, 210))

        val crisp = DocumentScanProcessor.applyScanFilter(src, ScanFilterMode.CRISP_DOCUMENT)
        assertEquals(40, crisp.width)
        assertEquals(40, crisp.height)

        val bw = DocumentScanProcessor.applyScanFilter(src, ScanFilterMode.BLACK_AND_WHITE)
        val pixel = bw.getPixel(20, 20)
        assertEquals(Color.red(pixel), Color.green(pixel))
        assertEquals(Color.green(pixel), Color.blue(pixel))
    }

    @Test
    fun `compressPagesToBudget fits up to 4 pages within Firestore character limits and rejects more than 4`() = runBlocking {
        val pages = List(4) { createSyntheticPageBitmap(800, 1100) }
        val result = DocumentScanProcessor.compressPagesToBudget(pages)

        assertTrue(result.isSuccess)
        val refs = result.getOrThrow()
        assertEquals(4, refs.size)
        assertTrue(refs.all { DocumentScanProcessor.isJpegDataUri(it) })
        assertTrue(refs.all { it.length <= ScannedDocument.MAX_SINGLE_PAGE_CHARS })
        assertTrue(refs.sumOf { it.length } <= ScannedDocument.MAX_TOTAL_PAYLOAD_CHARS)

        val tooManyResult = DocumentScanProcessor.compressPagesToBudget(List(5) { createSyntheticPageBitmap(200, 200) })
        assertTrue(tooManyResult.isFailure)
    }

    @Test
    fun `PDF metadata builder, aspect-fit image bounds, and data URI round-trip decode cleanly`() = runBlocking {
        val encoded = DocumentScanProcessor.compressPagesToBudget(
            listOf(createSyntheticPageBitmap(400, 560), createSyntheticPageBitmap(400, 560))
        ).getOrThrow()

        val doc = ScannedDocument(
            id = "doc_pdf_test_1",
            orgId = "org_1",
            propertyId = "prop_1",
            propertyName = "Kilimani Heights",
            unitId = "unit_1",
            unitNumber = "B-204",
            ownerUid = "owner_1",
            authorizedUids = listOf("owner_1"),
            tenantName = "Amina Hassan",
            documentType = DocumentType.LEASE_AGREEMENT.name,
            title = "Signed 2026 Residential Lease",
            notes = "Two-page countersigned copy",
            filterMode = ScanFilterMode.CRISP_DOCUMENT.name,
            pageImageRefs = encoded,
            extractedReference = "LSE-2026-B204",
            extractedAmountCents = 4_500_000L,
            extractedDateText = "2026-11-01",
            uploadedByUid = "owner_1",
            uploadedByName = "Wanjiku Kamau",
            uploadedByRole = UserRole.LANDLORD.name
        )

        val metaParts = DocumentScanProcessor.buildPdfMetadataParts(doc)
        assertEquals(4, metaParts.size)
        assertTrue(metaParts.any { it.contains("Amina Hassan") })
        assertTrue(metaParts.any { it.contains("LSE-2026-B204") })
        assertTrue(metaParts.any { it.contains("45,000") })
        assertTrue(metaParts.any { it.contains("2026-11-01") })

        val bounds = DocumentScanProcessor.computePdfImageRect(
            bmpWidth = 400,
            bmpHeight = 800,
            margin = 32f,
            topY = 120f,
            availW = 531f,
            availH = 680f
        )
        assertEquals(340f, bounds.width(), 0.5f)
        assertEquals(680f, bounds.height(), 0.5f)

        val decodedPage = DocumentScanProcessor.decodeDataUriToBitmap(encoded.first())
        assertNotNull(decodedPage)
        assertTrue(DocumentScanProcessor.estimatePayloadKb(encoded) >= 1)
    }

    @Test
    fun `ScannedDocument toCreateMap normalizes strings and omits null optional fields`() {
        val doc = ScannedDocument(
            id = "ignored_doc_id",
            orgId = " org_1 ",
            propertyId = " prop_1 ",
            propertyName = "  Kilimani Court  ",
            unitId = " unit_1 ",
            unitNumber = " A1 ",
            ownerUid = " owner_1 ",
            managerUids = listOf("mgr_1", "mgr_1"),
            authorizedUids = listOf("mgr_1"),
            leaseId = "   ",
            tenantUid = null,
            documentType = DocumentType.PAYMENT_RECEIPT.name,
            title = "  October Rent Slip  ",
            notes = "   ",
            filterMode = ScanFilterMode.CRISP_DOCUMENT.name,
            pageImageRefs = listOf("data:image/jpeg;base64,abc123"),
            extractedReference = " ubk7712abc ",
            extractedAmountCents = 0L,
            extractedDateText = " 2026-11-05 ",
            aiExtracted = true,
            tenantVisible = true,
            uploadedByUid = " owner_1 ",
            uploadedByName = " ",
            uploadedByRole = UserRole.LANDLORD.name
        )

        val map = doc.toCreateMap()
        assertFalse(map.containsKey("id"))
        assertFalse(map.containsKey("leaseId"))
        assertFalse(map.containsKey("tenantUid"))
        assertFalse(map.containsKey("notes"))
        assertFalse(map.containsKey("extractedAmountCents"))
        assertEquals("org_1", map["orgId"])
        assertEquals("UBK7712ABC", map["extractedReference"])
        assertEquals("RentFlow User", map["uploadedByName"])
        assertEquals(listOf("mgr_1", "owner_1"), map["authorizedUids"])
        assertNotNull(map["createdAt"])
        assertNotNull(map["updatedAt"])
    }

    @Test
    fun `AiAssistantRepository parseDocumentOcrJson extracts structured fields and safely falls back`() {
        val validJson = """
            {
              "title": "M-Pesa Paybill Receipt UBK8192XYZ",
              "documentType": "PAYMENT_RECEIPT",
              "referenceCode": "ubk8192xyz",
              "amountKes": 35000.50,
              "documentDate": "2026-11-05",
              "summary": "Confirmed KES 35,000.50 rent transfer for Unit A1."
            }
        """.trimIndent()

        val parsed = AiAssistantRepository.parseDocumentOcrJson(validJson, DocumentType.OTHER)
        assertEquals("M-Pesa Paybill Receipt UBK8192XYZ", parsed.title)
        assertEquals(DocumentType.PAYMENT_RECEIPT, parsed.documentType)
        assertEquals("UBK8192XYZ", parsed.referenceCode)
        assertEquals(3_500_050L, parsed.amountCents)
        assertEquals("2026-11-05", parsed.documentDate)
        assertTrue(parsed.summary.contains("Unit A1"))

        val sparseJson = """
            {
              "title": "",
              "documentType": "UNKNOWN_ENUM",
              "referenceCode": null,
              "amountKes": -100,
              "documentDate": null,
              "summary": ""
            }
        """.trimIndent()

        val fallback = AiAssistantRepository.parseDocumentOcrJson(sparseJson, DocumentType.LEASE_AGREEMENT)
        assertEquals(DocumentType.LEASE_AGREEMENT, fallback.documentType)
        assertEquals(DocumentType.LEASE_AGREEMENT.label, fallback.title)
        assertNull(fallback.referenceCode)
        assertNull(fallback.amountCents)
        assertNull(fallback.documentDate)
    }
}
