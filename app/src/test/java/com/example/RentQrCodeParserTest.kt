package com.example

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.example.domain.finance.MpesaInstructionChannel
import com.example.domain.finance.MpesaQrGenerator
import com.example.domain.finance.PaymentFormattingUtils
import com.example.domain.finance.RentQrCodeParser
import com.example.domain.finance.RentQrSourceFormat
import com.example.domain.model.Invoice
import com.example.domain.model.InvoiceStatus
import com.example.domain.model.Lease
import com.example.domain.model.LeaseStatus
import com.example.domain.model.RentalUnit
import com.example.ui.media.QrBitmapDecoder
import com.example.ui.screens.TenantPaymentSubmissionDialog
import com.example.ui.theme.RentFlowTheme
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
@Config(sdk = [36])
class RentQrCodeParserTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `parses mpesa paybill and till URIs accurately`() {
        val paybillUri = "mpesa://paybill?shortcode=522522&account=KP-A102&amount=28000"
        val parsedPaybill = RentQrCodeParser.parse(paybillUri)
        assertNotNull(parsedPaybill)
        assertEquals(MpesaInstructionChannel.PAYBILL, parsedPaybill!!.channel)
        assertEquals("522522", parsedPaybill.shortcode)
        assertEquals("522 522", parsedPaybill.formattedShortcode)
        assertEquals("KP-A102", parsedPaybill.accountReference)
        assertEquals(2_800_000L, parsedPaybill.amountCents)
        assertEquals(RentQrSourceFormat.MPESA_URI, parsedPaybill.sourceFormat)

        val tillUri = "mpesa://till?shortcode=5643210&amount=15500.50"
        val parsedTill = RentQrCodeParser.parse(tillUri)
        assertNotNull(parsedTill)
        assertEquals(MpesaInstructionChannel.TILL, parsedTill!!.channel)
        assertEquals("5643210", parsedTill.shortcode)
        assertNull(parsedTill.accountReference)
        assertEquals(1_550_050L, parsedTill.amountCents)
    }

    @Test
    fun `parses RentFlow notice links, JSON notices, and physical poster text`() {
        val rentFlowUrl = "https://rentflow.app/pay?paybill=400200&account=KH-4B&unit=4B&property=Kilimani%20Heights&amount=32000"
        val parsedUrl = RentQrCodeParser.parse(rentFlowUrl)
        assertNotNull(parsedUrl)
        assertEquals(MpesaInstructionChannel.PAYBILL, parsedUrl!!.channel)
        assertEquals("400200", parsedUrl.shortcode)
        assertEquals("KH-4B", parsedUrl.accountReference)
        assertEquals("4B", parsedUrl.unitHint)
        assertEquals("Kilimani Heights", parsedUrl.propertyHint)
        assertEquals(3_200_000L, parsedUrl.amountCents)
        assertEquals(RentQrSourceFormat.RENTFLOW_NOTICE, parsedUrl.sourceFormat)

        val jsonNotice = """{"channel":"till","till":"882211","property":"Westlands Courts","amount":45000}"""
        val parsedJson = RentQrCodeParser.parse(jsonNotice)
        assertNotNull(parsedJson)
        assertEquals(MpesaInstructionChannel.TILL, parsedJson!!.channel)
        assertEquals("882211", parsedJson.shortcode)
        assertEquals("Westlands Courts", parsedJson.propertyHint)
        assertEquals(4_500_000L, parsedJson.amountCents)

        val posterText = """
            KILIMANI PALMS RENT NOTICE
            Lipa na M-Pesa Paybill: 522 522
            Account No: KP-A102
            Rent Due: KES 28,000
            Enquiries: 0712345678
        """.trimIndent()
        val parsedPoster = RentQrCodeParser.parse(posterText)
        assertNotNull(parsedPoster)
        assertEquals(MpesaInstructionChannel.PAYBILL, parsedPoster!!.channel)
        assertEquals("522522", parsedPoster.shortcode)
        assertEquals("KP-A102", parsedPoster.accountReference)
        assertEquals(2_800_000L, parsedPoster.amountCents)
        assertEquals(RentQrSourceFormat.POSTER_TEXT, parsedPoster.sourceFormat)
    }

    @Test
    fun `rejects unrelated QR payloads and raw phone numbers`() {
        assertNull(RentQrCodeParser.parse(""))
        assertNull(RentQrCodeParser.parse("WIFI:T:WPA;S:LobbyGuest;P:pass123;;"))
        assertNull(RentQrCodeParser.parse("https://example.com/menu?table=12"))
        assertNull(RentQrCodeParser.parse("Call caretaker on 0712345678 for keys"))
        assertNull(RentQrCodeParser.parse("mpesa://paybill?shortcode=012345"))
    }

    @Test
    fun `matches scanned notice to correct multi-unit lease and overdue invoice`() {
        val unitA = RentalUnit(
            id = "u_a102",
            propertyId = "prop_1",
            propertyName = "Kilimani Palms",
            unitNumber = "A102",
            mpesaPaybill = "522522",
            mpesaAccountPrefix = "KP-"
        )
        val unitB = RentalUnit(
            id = "u_b204",
            propertyId = "prop_2",
            propertyName = "Westlands Suites",
            unitNumber = "B204",
            mpesaPaybill = "400200",
            mpesaAccountPrefix = "WS-"
        )
        val leaseA = Lease(
            id = "lease_a",
            propertyId = "prop_1",
            propertyName = "Kilimani Palms",
            unitId = "u_a102",
            unitNumber = "A102",
            rentCents = 2_800_000L,
            status = LeaseStatus.ACTIVE.name
        )
        val leaseB = Lease(
            id = "lease_b",
            propertyId = "prop_2",
            propertyName = "Westlands Suites",
            unitId = "u_b204",
            unitNumber = "B204",
            rentCents = 3_500_000L,
            status = LeaseStatus.ACTIVE.name
        )
        val invoiceB = Invoice(
            id = "inv_b_overdue",
            invoiceNumber = "INV-B-001",
            leaseId = "lease_b",
            unitId = "u_b204",
            unitNumber = "B204",
            outstandingCents = 3_500_000L,
            dueDateEpochMs = 1000L,
            status = InvoiceStatus.OVERDUE.name
        )

        val notice = RentQrCodeParser.parse("mpesa://paybill?shortcode=400200&account=WS-B204")!!
        val matched = RentQrCodeParser.matchLeaseAndInvoice(
            notice = notice,
            leases = listOf(leaseA, leaseB),
            unitsById = mapOf(unitA.id to unitA, unitB.id to unitB),
            invoices = listOf(invoiceB)
        )

        assertTrue(matched.isExactLeaseMatch)
        assertEquals("lease_b", matched.matchedLease?.id)
        assertEquals("u_b204", matched.matchedUnit?.id)
        assertEquals("inv_b_overdue", matched.targetInvoice?.id)
        assertEquals(3_500_000L, matched.resolvedAmountCents)

        // Generic poster without matching prefix still falls back to primary active lease cleanly
        val genericNotice = RentQrCodeParser.parse("Buy Goods Till: 998877")!!
        val genericMatch = RentQrCodeParser.matchLeaseAndInvoice(
            notice = genericNotice,
            leases = listOf(leaseA),
            unitsById = mapOf(unitA.id to unitA),
            invoices = emptyList()
        )
        assertFalse(genericMatch.isExactLeaseMatch)
        assertEquals("lease_a", genericMatch.matchedLease?.id)
        assertEquals(2_800_000L, genericMatch.resolvedAmountCents)
    }

    @Test
    fun `round trips encoded M-Pesa QR matrix through Bitmap decoder and parser`() {
        val uri = PaymentFormattingUtils.buildMpesaQrUri(
            channel = MpesaInstructionChannel.PAYBILL,
            shortcode = "522522",
            accountReference = "KP-A102",
            amountCents = 2_800_000L
        )
        val matrix = MpesaQrGenerator.encodeQrMatrix(uri)!!
        val scale = 8
        val quiet = 4
        val dim = (matrix.size + quiet * 2) * scale
        val bitmap = Bitmap.createBitmap(dim, dim, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        for (row in 0 until matrix.size) {
            for (col in 0 until matrix.size) {
                if (matrix.isDarkAt(row, col)) {
                    val startX = (col + quiet) * scale
                    val startY = (row + quiet) * scale
                    for (dy in 0 until scale) {
                        for (dx in 0 until scale) {
                            bitmap.setPixel(startX + dx, startY + dy, Color.BLACK)
                        }
                    }
                }
            }
        }

        val decoded = QrBitmapDecoder.decodeFromBitmap(bitmap)
        assertEquals(uri, decoded)
        val parsed = RentQrCodeParser.parse(decoded)
        assertNotNull(parsed)
        assertEquals("522522", parsed!!.shortcode)
        assertEquals("KP-A102", parsed.accountReference)
        assertEquals(2_800_000L, parsed.amountCents)
    }

    @Test
    fun `TenantPaymentSubmissionDialog prefills scanned Paybill QR details and amount`() {
        val lease = Lease(
            id = "lease_1",
            propertyName = "Kilimani Palms",
            unitId = "u_1",
            unitNumber = "A102",
            rentCents = 2_800_000L,
            tenantPhone = "+254712345678",
            status = LeaseStatus.ACTIVE.name
        )
        val notice = RentQrCodeParser.parse("mpesa://paybill?shortcode=522522&account=KP-A102&amount=28000")!!

        composeTestRule.setContent {
            RentFlowTheme {
                TenantPaymentSubmissionDialog(
                    lease = lease,
                    unit = null,
                    targetInvoice = null,
                    defaultPhone = "+254712345678",
                    isBusy = false,
                    initialQrNotice = notice,
                    isExactQrLeaseMatch = true,
                    onDismiss = {},
                    onSubmit = { _, _, _, _, _, _, _, _, _ -> }
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("scanned_qr_prefill_card").assertIsDisplayed()
        composeTestRule.onNodeWithText("Scanned M-Pesa QR").assertIsDisplayed()
        composeTestRule.onNodeWithTag("payment_amount_input").assertTextContains("28000")
    }
}
