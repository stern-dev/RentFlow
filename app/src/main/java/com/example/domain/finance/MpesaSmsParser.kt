package com.example.domain.finance

import java.math.BigDecimal
import java.math.RoundingMode

data class ParsedMpesaSms(
    val transactionCode: String,
    val amountCents: Long,
    val recipientOrAccount: String? = null,
    val rawSnippet: String = ""
)

/**
 * Zero-permission Safaricom M-Pesa SMS parser & Kenya rental tax helpers.
 * Tenants paste confirmation SMS text directly — no READ_SMS runtime permission required.
 */
object MpesaSmsParser {
    // Safaricom codes are 10 uppercase alphanumeric characters, e.g., SJK45AB89C / UAF29KL30M
    private val codeRegex = Regex("\\b([A-Z0-9]{10})\\b\\s+Confirmed", RegexOption.IGNORE_CASE)
    private val fallbackCodeRegex = Regex("^\\s*([A-Z0-9]{10})\\b")
    private val amountRegex = Regex("(?:Ksh|KES)\\.?\\s*([0-9][0-9,]*(?:\\.[0-9]{1,2})?)", RegexOption.IGNORE_CASE)
    private val recipientRegex = Regex("(?:paid to|sent to)\\s+([^.\\n]+)", RegexOption.IGNORE_CASE)

    fun parse(rawSms: String): ParsedMpesaSms? {
        val trimmed = rawSms.trim()
        if (trimmed.isBlank()) return null

        val codeMatch = codeRegex.find(trimmed) ?: fallbackCodeRegex.find(trimmed)
        val amountMatch = amountRegex.find(trimmed)
        if (codeMatch == null || amountMatch == null) return null

        val code = codeMatch.groupValues[1].uppercase()
        val amountClean = amountMatch.groupValues[1].replace(",", "")
        val amountCents = CurrencyUtils.parseKesToCents(amountClean) ?: return null
        if (amountCents <= 0L) return null

        val recipient = recipientRegex.find(trimmed)?.groupValues?.getOrNull(1)?.trim()
        return ParsedMpesaSms(
            transactionCode = code,
            amountCents = amountCents,
            recipientOrAccount = recipient,
            rawSnippet = trimmed.take(120)
        )
    }

    /**
     * Kenya KRA Monthly Rental Income (MRI) tax rate is 7.5% on gross residential rent collected
     * (applies to residential rental income between KES 288,000 and KES 15,000,000 per annum).
     */
    fun computeKenyaMriTaxCents(grossCollectedCents: Long): Long {
        if (grossCollectedCents <= 0L) return 0L
        return BigDecimal.valueOf(grossCollectedCents)
            .multiply(BigDecimal("0.075"))
            .setScale(0, RoundingMode.HALF_UP)
            .longValueExact()
    }

    fun buildMpesaAccountReference(prefix: String?, unitNumber: String): String {
        val cleanPrefix = prefix?.trim().orEmpty()
        val cleanUnit = unitNumber.trim().replace(" ", "")
        return if (cleanPrefix.isNotBlank()) "$cleanPrefix$cleanUnit" else "UNIT-$cleanUnit"
    }
}
