package com.example.domain.finance

import com.example.domain.model.Organization
import com.example.domain.model.Property
import com.example.domain.model.RentalUnit
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

enum class MpesaInstructionChannel(val displayName: String) {
    PAYBILL("Lipa na M-Pesa Paybill"),
    TILL("Lipa na M-Pesa Buy Goods Till")
}

data class FormattedMpesaInstruction(
    val channel: MpesaInstructionChannel,
    val businessNumberRaw: String,
    val businessNumberFormatted: String,
    val accountReference: String? = null,
    val amountKesFormatted: String? = null,
    val recipientName: String? = null,
    val steps: List<String>,
    val alignedRows: List<String>,
    val summaryLine: String,
    val copyableText: String,
    val qrPayload: String = ""
)

/**
 * Standardized Kenyan Safaricom M-Pesa Paybill and Buy Goods Till formatting helpers.
 * Ensures consistent digit grouping, Safaricom-compliant account references, and
 * column-aligned tenant instructions across screens, dialogs, and printable receipts.
 */
object PaymentFormattingUtils {

    private val shortcodeRegex = Regex("^[1-9][0-9]{4,6}$")
    private val disallowedAccountCharsRegex = Regex("[^A-Z0-9._\\-/]")

    const val DEFAULT_LABEL_WIDTH: Int = 16
    const val MAX_ACCOUNT_REFERENCE_LENGTH: Int = 24

    /**
     * Strips spaces, hyphens, and leading '#' markers from a raw M-Pesa Paybill or Till input.
     */
    fun normalizeShortcode(raw: String?): String =
        raw?.trim()
            ?.removePrefix("#")
            ?.replace(Regex("[\\s-]+"), "")
            .orEmpty()

    /**
     * Safaricom M-Pesa Paybill and Till shortcodes are 5 to 7 digits and cannot begin with '0'.
     */
    fun isValidMpesaShortcode(raw: String?): Boolean {
        val clean = normalizeShortcode(raw)
        return shortcodeRegex.matches(clean)
    }

    /**
     * Groups valid 5–7 digit shortcodes for tenant legibility:
     * - 5 digits: `XX XXX` (e.g. `12 345`)
     * - 6 digits: `XXX XXX` (e.g. `522 522`)
     * - 7 digits: `XXX XXXX` (e.g. `564 3210`)
     * Invalid non-blank values are returned trimmed without synthetic grouping; blank returns `""`.
     */
    fun formatShortcodeForDisplay(raw: String?): String {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty()) return ""
        val clean = normalizeShortcode(trimmed)
        if (!shortcodeRegex.matches(clean)) return trimmed
        return when (clean.length) {
            5 -> "${clean.substring(0, 2)} ${clean.substring(2)}"
            6 -> "${clean.substring(0, 3)} ${clean.substring(3)}"
            7 -> "${clean.substring(0, 3)} ${clean.substring(3)}"
            else -> clean
        }
    }

    fun formatPaybillNumber(rawPaybill: String?): String =
        formatShortcodeForDisplay(rawPaybill)

    fun formatTillNumber(rawTill: String?): String =
        formatShortcodeForDisplay(rawTill)

    fun formatValidPaybillOrNull(rawPaybill: String?): String? =
        normalizeShortcode(rawPaybill)
            .takeIf { shortcodeRegex.matches(it) }
            ?.let(::formatShortcodeForDisplay)

    fun formatValidTillOrNull(rawTill: String?): String? =
        normalizeShortcode(rawTill)
            .takeIf { shortcodeRegex.matches(it) }
            ?.let(::formatShortcodeForDisplay)

    /**
     * Builds a Safaricom Paybill Account Reference (uppercase, no whitespace, 1..24 chars).
     * Joins [prefix] and [unitNumber] when [prefix] is provided, otherwise defaults to `UNIT-<UNIT>`.
     */
    fun formatAccountReference(
        prefix: String?,
        unitNumber: String,
        fallbackLabel: String = "RENT"
    ): String {
        val cleanPrefix = sanitizeAccountToken(prefix)
        val cleanUnit = sanitizeAccountToken(unitNumber)
        val rawCombined = when {
            cleanPrefix.isNotBlank() && cleanUnit.isNotBlank() -> "$cleanPrefix$cleanUnit"
            cleanUnit.isNotBlank() -> "UNIT-$cleanUnit"
            cleanPrefix.isNotBlank() -> cleanPrefix
            else -> sanitizeAccountToken(fallbackLabel).ifBlank { "RENT" }
        }
        return rawCombined.take(MAX_ACCOUNT_REFERENCE_LENGTH)
    }

    fun sanitizeAccountToken(raw: String?): String =
        raw?.trim()
            ?.uppercase()
            ?.replace(Regex("\\s+"), "")
            ?.replace(disallowedAccountCharsRegex, "")
            .orEmpty()

    /**
     * Formats a single label-value row padded to [labelWidth] so colons align vertically
     * in monospace cards, SMS templates, and copyable receipts.
     */
    fun formatAlignedRow(
        label: String,
        value: String,
        labelWidth: Int = DEFAULT_LABEL_WIDTH
    ): String {
        val cleanLabel = label.trim()
        val width = labelWidth.coerceAtLeast(cleanLabel.length)
        return "${cleanLabel.padEnd(width, ' ')} : ${value.trim()}"
    }

    fun formatAmountForMpesa(amountCents: Long?): String? {
        if (amountCents == null || amountCents <= 0L) return null
        val hasFraction = (amountCents % 100L) != 0L
        return CurrencyUtils.formatKes(amountCents, includeDecimals = hasFraction)
    }

    /**
     * Builds a compact M-Pesa URI string for QR encoding:
     * - Paybill: `mpesa://paybill?shortcode=522522&account=KP-A102&amount=28000`
     * - Till: `mpesa://till?shortcode=5643210&amount=28000`
     * Optional `amount` is included only when [amountCents] > 0.
     */
    fun buildMpesaQrUri(
        channel: MpesaInstructionChannel,
        shortcode: String,
        accountReference: String? = null,
        amountCents: Long? = null
    ): String {
        val cleanShortcode = normalizeShortcode(shortcode)
        val path = when (channel) {
            MpesaInstructionChannel.PAYBILL -> "paybill"
            MpesaInstructionChannel.TILL -> "till"
        }
        val params = buildList {
            add("shortcode=$cleanShortcode")
            if (channel == MpesaInstructionChannel.PAYBILL) {
                val cleanAccount = sanitizeAccountToken(accountReference)
                    .ifBlank { "RENT" }
                    .take(MAX_ACCOUNT_REFERENCE_LENGTH)
                add("account=${URLEncoder.encode(cleanAccount, StandardCharsets.UTF_8.name())}")
            }
            if (amountCents != null && amountCents > 0L) {
                val amountValue = CurrencyUtils.centsToInputString(amountCents)
                if (amountValue.isNotBlank()) {
                    add("amount=$amountValue")
                }
            }
        }
        return "mpesa://$path?${params.joinToString("&")}"
    }

    fun buildPaybillInstruction(
        paybillNumber: String?,
        prefix: String?,
        unitNumber: String,
        amountCents: Long? = null,
        recipientName: String? = null
    ): FormattedMpesaInstruction? {
        val accountRef = formatAccountReference(prefix = prefix, unitNumber = unitNumber)
        return buildPaybillInstructionFromReference(
            paybillNumber = paybillNumber,
            accountReference = accountRef,
            amountCents = amountCents,
            recipientName = recipientName
        )
    }

    fun buildPaybillInstructionFromReference(
        paybillNumber: String?,
        accountReference: String?,
        amountCents: Long? = null,
        recipientName: String? = null
    ): FormattedMpesaInstruction? {
        val raw = normalizeShortcode(paybillNumber)
        if (!shortcodeRegex.matches(raw)) return null
        val formattedNumber = formatShortcodeForDisplay(raw)
        val cleanAccount = sanitizeAccountToken(accountReference)
            .ifBlank { "RENT" }
            .take(MAX_ACCOUNT_REFERENCE_LENGTH)
        val amountLabel = formatAmountForMpesa(amountCents)
        val cleanRecipient = recipientName?.trim()?.takeIf { it.isNotBlank() }

        val rows = buildList {
            cleanRecipient?.let { add(formatAlignedRow("Recipient", it)) }
            add(formatAlignedRow("Paybill No.", formattedNumber))
            add(formatAlignedRow("Account No.", cleanAccount))
            amountLabel?.let { add(formatAlignedRow("Amount", it)) }
        }

        val steps = buildList {
            add("1. Open M-Pesa > Lipa na M-Pesa > Pay Bill")
            add("2. Enter Business Number: $formattedNumber")
            add("3. Enter Account Number: $cleanAccount")
            if (amountLabel != null) {
                add("4. Enter Amount: $amountLabel")
                add("5. Enter your M-Pesa PIN and confirm")
            } else {
                add("4. Enter the invoice amount, then your M-Pesa PIN and confirm")
            }
        }

        val summary = buildString {
            append("Paybill: $formattedNumber • Account: $cleanAccount")
            if (amountLabel != null) append(" • $amountLabel")
        }

        val copyable = buildString {
            appendLine(MpesaInstructionChannel.PAYBILL.displayName)
            rows.forEach { appendLine(it) }
        }.trimEnd()

        val qrUri = buildMpesaQrUri(
            channel = MpesaInstructionChannel.PAYBILL,
            shortcode = raw,
            accountReference = cleanAccount,
            amountCents = amountCents
        )

        return FormattedMpesaInstruction(
            channel = MpesaInstructionChannel.PAYBILL,
            businessNumberRaw = raw,
            businessNumberFormatted = formattedNumber,
            accountReference = cleanAccount,
            amountKesFormatted = amountLabel,
            recipientName = cleanRecipient,
            steps = steps,
            alignedRows = rows,
            summaryLine = summary,
            copyableText = copyable,
            qrPayload = qrUri
        )
    }

    fun buildTillInstruction(
        tillNumber: String?,
        amountCents: Long? = null,
        recipientName: String? = null
    ): FormattedMpesaInstruction? {
        val raw = normalizeShortcode(tillNumber)
        if (!shortcodeRegex.matches(raw)) return null
        val formattedNumber = formatShortcodeForDisplay(raw)
        val amountLabel = formatAmountForMpesa(amountCents)
        val cleanRecipient = recipientName?.trim()?.takeIf { it.isNotBlank() }

        val rows = buildList {
            cleanRecipient?.let { add(formatAlignedRow("Recipient", it)) }
            add(formatAlignedRow("Till Number", formattedNumber))
            amountLabel?.let { add(formatAlignedRow("Amount", it)) }
        }

        val steps = buildList {
            add("1. Open M-Pesa > Lipa na M-Pesa > Buy Goods and Services")
            add("2. Enter Till Number: $formattedNumber")
            if (amountLabel != null) {
                add("3. Enter Amount: $amountLabel")
                add("4. Enter your M-Pesa PIN and confirm")
            } else {
                add("3. Enter the invoice amount, then your M-Pesa PIN and confirm")
            }
        }

        val summary = buildString {
            append("Buy Goods Till: $formattedNumber")
            if (amountLabel != null) append(" • $amountLabel")
        }

        val copyable = buildString {
            appendLine(MpesaInstructionChannel.TILL.displayName)
            rows.forEach { appendLine(it) }
        }.trimEnd()

        val qrUri = buildMpesaQrUri(
            channel = MpesaInstructionChannel.TILL,
            shortcode = raw,
            accountReference = null,
            amountCents = amountCents
        )

        return FormattedMpesaInstruction(
            channel = MpesaInstructionChannel.TILL,
            businessNumberRaw = raw,
            businessNumberFormatted = formattedNumber,
            accountReference = null,
            amountKesFormatted = amountLabel,
            recipientName = cleanRecipient,
            steps = steps,
            alignedRows = rows,
            summaryLine = summary,
            copyableText = copyable,
            qrPayload = qrUri
        )
    }

    /**
     * Resolves standardized Paybill and Till instructions from landlord settings using
     * precedence: RentalUnit -> Property -> Organization.
     */
    fun resolveInstructionsFromSettings(
        unit: RentalUnit?,
        property: Property?,
        organization: Organization?,
        unitNumberFallback: String = "",
        amountCents: Long? = null
    ): List<FormattedMpesaInstruction> {
        val unitNumber = unit?.unitNumber?.takeIf { it.isNotBlank() } ?: unitNumberFallback
        val recipientName = property?.name?.takeIf { it.isNotBlank() }
            ?: unit?.propertyName?.takeIf { it.isNotBlank() }
            ?: organization?.name?.takeIf { it.isNotBlank() }

        val resolvedPaybill = listOf(unit?.mpesaPaybill, property?.mpesaPaybill, organization?.mpesaPaybill)
            .firstOrNull { isValidMpesaShortcode(it) }
        val resolvedPrefix = listOf(unit?.mpesaAccountPrefix, property?.mpesaAccountPrefix)
            .firstOrNull { !it.isNullOrBlank() }
        val resolvedTill = listOf(unit?.mpesaTill, property?.mpesaTill, organization?.mpesaTill)
            .firstOrNull { isValidMpesaShortcode(it) }

        return buildList {
            buildPaybillInstruction(
                paybillNumber = resolvedPaybill,
                prefix = resolvedPrefix,
                unitNumber = unitNumber,
                amountCents = amountCents,
                recipientName = recipientName
            )?.let(::add)

            buildTillInstruction(
                tillNumber = resolvedTill,
                amountCents = amountCents,
                recipientName = recipientName
            )?.let(::add)
        }
    }
}
