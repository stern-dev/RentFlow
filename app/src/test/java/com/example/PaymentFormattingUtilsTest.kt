package com.example

import com.example.domain.finance.MpesaInstructionChannel
import com.example.domain.finance.PaymentFormattingUtils
import com.example.domain.model.Organization
import com.example.domain.model.Property
import com.example.domain.model.RentalUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PaymentFormattingUtilsTest {

    @Test
    fun `normalizeShortcode strips spaces, hyphens, and hash prefix cleanly`() {
        assertEquals("522522", PaymentFormattingUtils.normalizeShortcode(" 522 522 "))
        assertEquals("5643210", PaymentFormattingUtils.normalizeShortcode("#564-3210"))
        assertEquals("12345", PaymentFormattingUtils.normalizeShortcode("12 345"))
        assertEquals("", PaymentFormattingUtils.normalizeShortcode(null))
        assertEquals("", PaymentFormattingUtils.normalizeShortcode("   "))
    }

    @Test
    fun `isValidMpesaShortcode accepts 5 to 7 non-zero-leading digits and rejects invalid numbers`() {
        assertTrue(PaymentFormattingUtils.isValidMpesaShortcode("12345"))
        assertTrue(PaymentFormattingUtils.isValidMpesaShortcode("522522"))
        assertTrue(PaymentFormattingUtils.isValidMpesaShortcode("5643210"))
        assertTrue(PaymentFormattingUtils.isValidMpesaShortcode(" 522-522 "))

        assertFalse(PaymentFormattingUtils.isValidMpesaShortcode(null))
        assertFalse(PaymentFormattingUtils.isValidMpesaShortcode(""))
        assertFalse(PaymentFormattingUtils.isValidMpesaShortcode("052252"))
        assertFalse(PaymentFormattingUtils.isValidMpesaShortcode("1234"))
        assertFalse(PaymentFormattingUtils.isValidMpesaShortcode("12345678"))
        assertFalse(PaymentFormattingUtils.isValidMpesaShortcode("52A522"))
    }

    @Test
    fun `formatPaybillNumber and formatTillNumber group 5, 6, and 7 digit numbers for readability`() {
        assertEquals("12 345", PaymentFormattingUtils.formatPaybillNumber("12345"))
        assertEquals("522 522", PaymentFormattingUtils.formatPaybillNumber("522522"))
        assertEquals("522 522", PaymentFormattingUtils.formatPaybillNumber(" 522-522 "))
        assertEquals("564 3210", PaymentFormattingUtils.formatPaybillNumber("5643210"))

        assertEquals("12 345", PaymentFormattingUtils.formatTillNumber("12345"))
        assertEquals("882 211", PaymentFormattingUtils.formatTillNumber("882211"))
        assertEquals("564 3210", PaymentFormattingUtils.formatTillNumber(" 564 3210 "))

        // Invalid inputs are preserved safely for error display or rejected by *OrNull helpers
        assertEquals("", PaymentFormattingUtils.formatPaybillNumber("   "))
        assertEquals("", PaymentFormattingUtils.formatTillNumber(null))
        assertEquals("052252", PaymentFormattingUtils.formatPaybillNumber(" 052252 "))
        assertNull(PaymentFormattingUtils.formatValidPaybillOrNull("052252"))
        assertNull(PaymentFormattingUtils.formatValidTillOrNull("1234"))
        assertEquals("522 522", PaymentFormattingUtils.formatValidPaybillOrNull("522522"))
        assertEquals("882 211", PaymentFormattingUtils.formatValidTillOrNull("882211"))
    }

    @Test
    fun `formatAccountReference produces Safaricom-compliant uppercase references within 24 chars`() {
        assertEquals(
            "KP-A102",
            PaymentFormattingUtils.formatAccountReference(prefix = " kp- ", unitNumber = "a 102")
        )
        assertEquals(
            "UNIT-B4",
            PaymentFormattingUtils.formatAccountReference(prefix = null, unitNumber = " b 4 ")
        )
        assertEquals(
            "RENT",
            PaymentFormattingUtils.formatAccountReference(prefix = "", unitNumber = "   ")
        )
        assertEquals(
            "RIVERSIDE/C12",
            PaymentFormattingUtils.formatAccountReference(prefix = "riverside/", unitNumber = "c12@#")
        )

        val longRef = PaymentFormattingUtils.formatAccountReference(
            prefix = "KILIMANI-HEIGHTS-TOWER-",
            unitNumber = "PENTHOUSE-99"
        )
        assertEquals(PaymentFormattingUtils.MAX_ACCOUNT_REFERENCE_LENGTH, longRef.length)
        assertFalse(longRef.contains(" "))
    }

    @Test
    fun `formatAlignedRow aligns colons at identical columns across Paybill and Till rows`() {
        val paybillInstruction = PaymentFormattingUtils.buildPaybillInstruction(
            paybillNumber = "522522",
            prefix = "KP-",
            unitNumber = "A102",
            amountCents = 2_800_000L,
            recipientName = "Kilimani Heights"
        )
        assertNotNull(paybillInstruction)
        paybillInstruction!!

        assertEquals(MpesaInstructionChannel.PAYBILL, paybillInstruction.channel)
        assertEquals("522522", paybillInstruction.businessNumberRaw)
        assertEquals("522 522", paybillInstruction.businessNumberFormatted)
        assertEquals("KP-A102", paybillInstruction.accountReference)
        assertEquals("KES 28,000", paybillInstruction.amountKesFormatted)
        assertEquals("Paybill: 522 522 • Account: KP-A102 • KES 28,000", paybillInstruction.summaryLine)

        // Every aligned row must place ':' at index DEFAULT_LABEL_WIDTH + 1 for crisp column alignment
        val colonColumns = paybillInstruction.alignedRows.map { it.indexOf(':') }.distinct()
        assertEquals(listOf(PaymentFormattingUtils.DEFAULT_LABEL_WIDTH + 1), colonColumns)

        assertTrue(paybillInstruction.steps.any { it.contains("Pay Bill") })
        assertTrue(paybillInstruction.steps.any { it.contains("522 522") })
        assertTrue(paybillInstruction.steps.any { it.contains("KP-A102") })
        assertTrue(paybillInstruction.steps.any { it.contains("KES 28,000") })
        assertTrue(paybillInstruction.copyableText.contains("Paybill No.      : 522 522"))
        assertTrue(paybillInstruction.copyableText.contains("Account No.      : KP-A102"))
    }

    @Test
    fun `buildTillInstruction omits account reference, formats fractional KES, and rejects invalid Tills`() {
        assertNull(PaymentFormattingUtils.buildTillInstruction("088221"))
        assertNull(PaymentFormattingUtils.buildTillInstruction("9999"))
        assertNull(PaymentFormattingUtils.buildPaybillInstruction("1234", "KP-", "A1"))

        val tillInstruction = PaymentFormattingUtils.buildTillInstruction(
            tillNumber = "5643210",
            amountCents = 1_550_050L,
            recipientName = "Westlands Court"
        )
        assertNotNull(tillInstruction)
        tillInstruction!!

        assertEquals(MpesaInstructionChannel.TILL, tillInstruction.channel)
        assertEquals("5643210", tillInstruction.businessNumberRaw)
        assertEquals("564 3210", tillInstruction.businessNumberFormatted)
        assertNull(tillInstruction.accountReference)
        assertEquals("KES 15,500.50", tillInstruction.amountKesFormatted)
        assertEquals("Buy Goods Till: 564 3210 • KES 15,500.50", tillInstruction.summaryLine)

        val colonColumns = tillInstruction.alignedRows.map { it.indexOf(':') }.distinct()
        assertEquals(listOf(PaymentFormattingUtils.DEFAULT_LABEL_WIDTH + 1), colonColumns)
        assertTrue(tillInstruction.steps.any { it.contains("Buy Goods and Services") })
        assertTrue(tillInstruction.steps.any { it.contains("564 3210") })
    }

    @Test
    fun `resolveInstructionsFromSettings applies Unit then Property then Organization precedence`() {
        val org = Organization(
            id = "org_1",
            name = "Savannah Estates",
            mpesaPaybill = "400200",
            mpesaTill = "777888"
        )
        val property = Property(
            id = "prop_1",
            name = "Riverside Court",
            mpesaPaybill = "522522",
            mpesaTill = "5643210",
            mpesaAccountPrefix = "RC-"
        )
        val unitWithOverride = RentalUnit(
            id = "unit_1",
            propertyId = "prop_1",
            propertyName = "Riverside Court",
            unitNumber = "B12",
            mpesaPaybill = "247247",
            mpesaTill = null,
            mpesaAccountPrefix = "UNITB-"
        )

        // Unit Paybill overrides Property/Org Paybill; Till falls back to Property Till
        val unitResolved = PaymentFormattingUtils.resolveInstructionsFromSettings(
            unit = unitWithOverride,
            property = property,
            organization = org,
            amountCents = 3_500_000L
        )
        assertEquals(2, unitResolved.size)
        val paybill = unitResolved[0]
        val till = unitResolved[1]
        assertEquals(MpesaInstructionChannel.PAYBILL, paybill.channel)
        assertEquals("247 247", paybill.businessNumberFormatted)
        assertEquals("UNITB-B12", paybill.accountReference)
        assertEquals(MpesaInstructionChannel.TILL, till.channel)
        assertEquals("564 3210", till.businessNumberFormatted)

        // When unit and property have no M-Pesa codes, falls back cleanly to Organization
        val orgFallback = PaymentFormattingUtils.resolveInstructionsFromSettings(
            unit = RentalUnit(id = "unit_2", unitNumber = "C3"),
            property = Property(id = "prop_2", name = "Acacia Villas"),
            organization = org
        )
        assertEquals(2, orgFallback.size)
        assertEquals("400 200", orgFallback[0].businessNumberFormatted)
        assertEquals("UNIT-C3", orgFallback[0].accountReference)
        assertEquals("777 888", orgFallback[1].businessNumberFormatted)
        assertNull(orgFallback[0].amountKesFormatted)

        // Invalid shortcodes at unit level are skipped in favor of valid property shortcode
        val invalidUnitFallback = PaymentFormattingUtils.resolveInstructionsFromSettings(
            unit = RentalUnit(id = "unit_3", unitNumber = "D1", mpesaPaybill = "00012", mpesaTill = "12"),
            property = property,
            organization = org
        )
        assertEquals("522 522", invalidUnitFallback[0].businessNumberFormatted)
        assertEquals("564 3210", invalidUnitFallback[1].businessNumberFormatted)
    }

    @Test
    fun `buildMpesaQrUri builds compact URIs for Paybill and Till with optional amount`() {
        val paybillWithAmount = PaymentFormattingUtils.buildPaybillInstruction(
            paybillNumber = "522 522",
            prefix = "KP-",
            unitNumber = "A102",
            amountCents = 2_800_000L
        )!!
        assertEquals(
            "mpesa://paybill?shortcode=522522&account=KP-A102&amount=28000",
            paybillWithAmount.qrPayload
        )

        val paybillNoAmount = PaymentFormattingUtils.buildPaybillInstruction(
            paybillNumber = "522522",
            prefix = "KP-",
            unitNumber = "A102",
            amountCents = null
        )!!
        assertEquals(
            "mpesa://paybill?shortcode=522522&account=KP-A102",
            paybillNoAmount.qrPayload
        )

        val tillWithFraction = PaymentFormattingUtils.buildTillInstruction(
            tillNumber = "564 3210",
            amountCents = 1_550_050L
        )!!
        assertEquals(
            "mpesa://till?shortcode=5643210&amount=15500.50",
            tillWithFraction.qrPayload
        )
    }

    @Test
    fun `MpesaQrGenerator encodes compact M-Pesa URI into valid decodable QR matrix`() {
        val payload = "mpesa://paybill?shortcode=522522&account=KP-A102&amount=28000"
        val qrData = com.example.domain.finance.MpesaQrGenerator.encodeQrMatrix(payload)
        assertNotNull(qrData)
        qrData!!
        assertTrue(qrData.size >= 21)

        // Round-trip decode using ZXing QRCodeReader (render at 6px per module for HybridBinarizer)
        val scale = 6
        val quietMargin = 4
        val totalModules = qrData.size + quietMargin * 2
        val totalPx = totalModules * scale
        val pixels = IntArray(totalPx * totalPx) { idx ->
            val mx = (idx % totalPx) / scale - quietMargin
            val my = (idx / totalPx) / scale - quietMargin
            if (qrData.isDarkAt(my, mx)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        }
        val reader = com.google.zxing.qrcode.QRCodeReader()
        val binaryBitmap = com.google.zxing.BinaryBitmap(
            com.google.zxing.common.HybridBinarizer(
                com.google.zxing.RGBLuminanceSource(totalPx, totalPx, pixels)
            )
        )
        val decoded = reader.decode(binaryBitmap)
        assertEquals(payload, decoded.text)
        assertNull(com.example.domain.finance.MpesaQrGenerator.encodeQrMatrix("   "))
    }
}
