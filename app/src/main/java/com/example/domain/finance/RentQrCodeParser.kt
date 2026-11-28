package com.example.domain.finance

import com.example.domain.model.Invoice
import com.example.domain.model.InvoiceStatus
import com.example.domain.model.Lease
import com.example.domain.model.LeaseStatus
import com.example.domain.model.PaymentChannelType
import com.example.domain.model.RentalUnit
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

enum class RentQrSourceFormat(val label: String) {
    MPESA_URI("M-Pesa QR"),
    RENTFLOW_NOTICE("RentFlow Notice"),
    POSTER_TEXT("Property Rent Notice")
}

data class ParsedRentQrNotice(
    val channel: MpesaInstructionChannel,
    val shortcode: String,
    val accountReference: String? = null,
    val amountCents: Long? = null,
    val propertyHint: String? = null,
    val unitHint: String? = null,
    val sourceFormat: RentQrSourceFormat,
    val rawPayload: String
) {
    val formattedShortcode: String
        get() = PaymentFormattingUtils.formatShortcodeForDisplay(shortcode)

    val paymentChannelType: PaymentChannelType
        get() = when (channel) {
            MpesaInstructionChannel.PAYBILL -> PaymentChannelType.MPESA_PAYBILL
            MpesaInstructionChannel.TILL -> PaymentChannelType.MPESA_TILL
        }
}

data class MatchedQrLeaseTarget(
    val notice: ParsedRentQrNotice,
    val matchedLease: Lease?,
    val matchedUnit: RentalUnit?,
    val targetInvoice: Invoice?,
    val isExactLeaseMatch: Boolean,
    val resolvedAmountCents: Long
)

/**
 * Pure-Kotlin parser and lease/invoice matcher for physical rent collection posters,
 * property notices, and Safaricom M-Pesa Paybill/Till QR payloads.
 */
object RentQrCodeParser {

    private val paybillKeywordRegex = Regex(
        "\\b(?:pay\\s*bill|paybill|business\\s*(?:no\\.?|number))\\b",
        RegexOption.IGNORE_CASE
    )
    private val tillKeywordRegex = Regex(
        "\\b(?:buy\\s*goods(?:\\s*and\\s*services)?|till(?:\\s*(?:no\\.?|number))?)\\b",
        RegexOption.IGNORE_CASE
    )
    private val genericMpesaKeywordRegex = Regex(
        "\\b(?:m-?pesa|lipa\\s*na\\s*m-?pesa|rentflow)\\b",
        RegexOption.IGNORE_CASE
    )
    private val explicitPaybillCodeRegex = Regex(
        "(?:pay\\s*bill|paybill|business\\s*(?:no\\.?|number))\\s*(?:is|:|#|-)?\\s*([1-9][0-9\\s-]{3,8}[0-9])",
        RegexOption.IGNORE_CASE
    )
    private val explicitTillCodeRegex = Regex(
        "(?:till(?:\\s*(?:no\\.?|number))?|buy\\s*goods(?:\\s*till)?)\\s*(?:is|:|#|-)?\\s*([1-9][0-9\\s-]{3,8}[0-9])",
        RegexOption.IGNORE_CASE
    )
    private val accountLabelRegex = Regex(
        "(?:account\\s*(?:no\\.?|number|ref(?:erence)?)?|acc(?:ount)?\\s*[:#-]|ref(?:erence)?\\s*[:#-])\\s*[:#-]?\\s*([A-Za-z0-9._\\-/]{1,30})",
        RegexOption.IGNORE_CASE
    )
    private val amountLabelRegex = Regex(
        "(?:(?:amount|rent|balance|due|kes|ksh)\\.?\\s*[:#-]?\\s*(?:kes|ksh)?\\.?\\s*|\\b(?:kes|ksh)\\.?\\s*)([0-9][0-9,]*(?:\\.[0-9]{1,2})?)",
        RegexOption.IGNORE_CASE
    )
    private val unitLabelRegex = Regex(
        "\\b(?:unit|house|apt|apartment|room)\\s*(?:no\\.?|number|#|:)?\\s*([A-Za-z0-9\\-]{1,16})",
        RegexOption.IGNORE_CASE
    )
    private val propertyLabelRegex = Regex(
        "(?:property|building|apartments?|estate)\\s*[:#-]\\s*([^\\n|;,]{2,60})",
        RegexOption.IGNORE_CASE
    )
    private val kenyanPhoneRegex = Regex("(?:\\+254|254|0)[17][0-9]{8}")
    private val jsonPairRegex = Regex(
        "\"([A-Za-z0-9_\\-]+)\"\\s*:\\s*(?:\"((?:[^\"\\\\]|\\\\.)*)\"|([0-9]+(?:\\.[0-9]+)?))"
    )

    fun parse(rawPayload: String?): ParsedRentQrNotice? {
        val trimmed = rawPayload?.trim().orEmpty()
        if (trimmed.isBlank()) return null

        parseUriNotice(trimmed)?.let { return it }
        parseJsonNotice(trimmed)?.let { return it }
        parsePosterTextNotice(trimmed)?.let { return it }

        return null
    }

    private fun parseUriNotice(raw: String): ParsedRentQrNotice? {
        if (!raw.contains("://") && !raw.startsWith("mpesa:", ignoreCase = true)) {
            return null
        }
        val uri = runCatching { URI(raw) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase().orEmpty()
        val host = uri.host?.lowercase().orEmpty()
        val path = uri.path?.lowercase().orEmpty()
        val params = parseQueryParams(uri.rawQuery)

        val paybillParam = params["paybill"] ?: params["businessnumber"] ?: params["business_number"]
        val tillParam = params["till"] ?: params["tillnumber"] ?: params["till_number"]
        val genericShortcode = params["shortcode"] ?: params["business"] ?: params["code"]
        val channelHint = (params["channel"] ?: params["type"] ?: params["method"]).orEmpty().lowercase()

        val isMpesaScheme = scheme == "mpesa"
        val isRentFlowNotice = scheme == "rentflow" ||
            host.contains("rentflow", ignoreCase = true) ||
            path.contains("pay") ||
            path.contains("rent")

        val resolvedChannel: MpesaInstructionChannel?
        val rawShortcode: String?

        when {
            isMpesaScheme && (host == "paybill" || path.contains("paybill")) -> {
                resolvedChannel = MpesaInstructionChannel.PAYBILL
                rawShortcode = paybillParam ?: genericShortcode
            }
            isMpesaScheme && (host == "till" || path.contains("till") || path.contains("buygoods")) -> {
                resolvedChannel = MpesaInstructionChannel.TILL
                rawShortcode = tillParam ?: genericShortcode
            }
            PaymentFormattingUtils.isValidMpesaShortcode(paybillParam) -> {
                resolvedChannel = MpesaInstructionChannel.PAYBILL
                rawShortcode = paybillParam
            }
            PaymentFormattingUtils.isValidMpesaShortcode(tillParam) -> {
                resolvedChannel = MpesaInstructionChannel.TILL
                rawShortcode = tillParam
            }
            isRentFlowNotice && PaymentFormattingUtils.isValidMpesaShortcode(genericShortcode) -> {
                resolvedChannel = if (channelHint.contains("till") || channelHint.contains("buy")) {
                    MpesaInstructionChannel.TILL
                } else {
                    MpesaInstructionChannel.PAYBILL
                }
                rawShortcode = genericShortcode
            }
            else -> {
                resolvedChannel = null
                rawShortcode = null
            }
        }

        val cleanShortcode = PaymentFormattingUtils.normalizeShortcode(rawShortcode)
        if (resolvedChannel == null || !PaymentFormattingUtils.isValidMpesaShortcode(cleanShortcode)) {
            return null
        }

        val accountRaw = params["account"]
            ?: params["accountref"]
            ?: params["account_ref"]
            ?: params["accountreference"]
            ?: params["ref"]
        val accountRef = accountRaw
            ?.let { PaymentFormattingUtils.sanitizeAccountToken(it) }
            ?.take(PaymentFormattingUtils.MAX_ACCOUNT_REFERENCE_LENGTH)
            ?.takeIf { it.isNotBlank() }

        val amountCents = (params["amount"] ?: params["rent"] ?: params["kes"])
            ?.let { CurrencyUtils.parseKesToCents(it) }
            ?.takeIf { it > 0L }

        val unitHint = (params["unit"] ?: params["unitnumber"] ?: params["unit_number"])
            ?.trim()
            ?.take(20)
            ?.takeIf { it.isNotBlank() }

        val propertyHint = (params["property"] ?: params["building"] ?: params["estate"])
            ?.trim()
            ?.take(60)
            ?.takeIf { it.isNotBlank() }

        return ParsedRentQrNotice(
            channel = resolvedChannel,
            shortcode = cleanShortcode,
            accountReference = if (resolvedChannel == MpesaInstructionChannel.PAYBILL) accountRef else null,
            amountCents = amountCents,
            propertyHint = propertyHint,
            unitHint = unitHint,
            sourceFormat = if (isMpesaScheme) RentQrSourceFormat.MPESA_URI else RentQrSourceFormat.RENTFLOW_NOTICE,
            rawPayload = raw
        )
    }

    private fun parseJsonNotice(raw: String): ParsedRentQrNotice? {
        if (!raw.startsWith("{") || !raw.endsWith("}")) return null
        val fields = mutableMapOf<String, String>()
        jsonPairRegex.findAll(raw).forEach { m ->
            val key = m.groupValues[1].trim().lowercase()
            val strVal = m.groupValues[2].replace("\\\"", "\"").replace("\\\\", "\\")
            val numVal = m.groupValues[3]
            val value = strVal.ifBlank { numVal }.trim()
            if (key.isNotEmpty() && value.isNotEmpty()) {
                fields[key] = value
            }
        }
        if (fields.isEmpty()) return null

        val paybill = fields["paybill"] ?: fields["paybillnumber"] ?: fields["paybill_number"]
        val till = fields["till"] ?: fields["tillnumber"] ?: fields["till_number"]
        val generic = fields["shortcode"] ?: fields["businessnumber"] ?: fields["business_number"]
        val channelHint = (fields["channel"] ?: fields["type"] ?: fields["paymentchannel"]).orEmpty().lowercase()

        val (channel, rawCode) = when {
            PaymentFormattingUtils.isValidMpesaShortcode(paybill) -> MpesaInstructionChannel.PAYBILL to paybill
            PaymentFormattingUtils.isValidMpesaShortcode(till) -> MpesaInstructionChannel.TILL to till
            PaymentFormattingUtils.isValidMpesaShortcode(generic) -> {
                val ch = if (channelHint.contains("till") || channelHint.contains("buy")) {
                    MpesaInstructionChannel.TILL
                } else {
                    MpesaInstructionChannel.PAYBILL
                }
                ch to generic
            }
            else -> return null
        }

        val cleanShortcode = PaymentFormattingUtils.normalizeShortcode(rawCode)
        val accountRef = (fields["account"] ?: fields["accountreference"] ?: fields["account_reference"] ?: fields["accountref"] ?: fields["ref"])
            ?.let { PaymentFormattingUtils.sanitizeAccountToken(it) }
            ?.take(PaymentFormattingUtils.MAX_ACCOUNT_REFERENCE_LENGTH)
            ?.takeIf { it.isNotBlank() }

        val amountCents = (fields["amount"] ?: fields["amountkes"] ?: fields["rent"])
            ?.let { CurrencyUtils.parseKesToCents(it) }
            ?.takeIf { it > 0L }

        val unitHint = (fields["unit"] ?: fields["unitnumber"] ?: fields["unit_number"])?.takeIf { it.isNotBlank() }
        val propertyHint = (fields["property"] ?: fields["propertyname"] ?: fields["building"])?.takeIf { it.isNotBlank() }

        return ParsedRentQrNotice(
            channel = channel,
            shortcode = cleanShortcode,
            accountReference = if (channel == MpesaInstructionChannel.PAYBILL) accountRef else null,
            amountCents = amountCents,
            propertyHint = propertyHint,
            unitHint = unitHint,
            sourceFormat = RentQrSourceFormat.RENTFLOW_NOTICE,
            rawPayload = raw
        )
    }

    private fun parsePosterTextNotice(raw: String): ParsedRentQrNotice? {
        val cleanedText = kenyanPhoneRegex.replace(raw, " ")
        val paybillMatch = explicitPaybillCodeRegex.find(cleanedText)
            ?.groupValues?.getOrNull(1)
            ?.let { PaymentFormattingUtils.normalizeShortcode(it) }
            ?.takeIf { PaymentFormattingUtils.isValidMpesaShortcode(it) }

        val tillMatch = explicitTillCodeRegex.find(cleanedText)
            ?.groupValues?.getOrNull(1)
            ?.let { PaymentFormattingUtils.normalizeShortcode(it) }
            ?.takeIf { PaymentFormattingUtils.isValidMpesaShortcode(it) }

        val hasPaymentKeyword = paybillKeywordRegex.containsMatchIn(cleanedText) ||
            tillKeywordRegex.containsMatchIn(cleanedText) ||
            genericMpesaKeywordRegex.containsMatchIn(cleanedText)
        if (!hasPaymentKeyword) return null

        val channel: MpesaInstructionChannel
        val shortcode: String
        when {
            paybillMatch != null -> {
                channel = MpesaInstructionChannel.PAYBILL
                shortcode = paybillMatch
            }
            tillMatch != null -> {
                channel = MpesaInstructionChannel.TILL
                shortcode = tillMatch
            }
            else -> return null
        }

        val accountRef = if (channel == MpesaInstructionChannel.PAYBILL) {
            accountLabelRegex.find(raw)?.groupValues?.getOrNull(1)
                ?.let { PaymentFormattingUtils.sanitizeAccountToken(it) }
                ?.take(PaymentFormattingUtils.MAX_ACCOUNT_REFERENCE_LENGTH)
                ?.takeIf { it.isNotBlank() }
        } else {
            null
        }

        val amountCents = amountLabelRegex.find(raw)?.groupValues?.getOrNull(1)
            ?.replace(",", "")
            ?.let { CurrencyUtils.parseKesToCents(it) }
            ?.takeIf { it > 0L }

        val unitHint = unitLabelRegex.find(raw)?.groupValues?.getOrNull(1)
            ?.trim()
            ?.takeIf { it.isNotBlank() }

        val propertyHint = propertyLabelRegex.find(raw)?.groupValues?.getOrNull(1)
            ?.trim()
            ?.takeIf { it.isNotBlank() }

        return ParsedRentQrNotice(
            channel = channel,
            shortcode = shortcode,
            accountReference = accountRef,
            amountCents = amountCents,
            propertyHint = propertyHint,
            unitHint = unitHint,
            sourceFormat = RentQrSourceFormat.POSTER_TEXT,
            rawPayload = raw
        )
    }

    /**
     * Resolves the best matching tenant lease, unit, open invoice, and KES amount
     * for a scanned QR notice. Falls back to the primary active lease when a poster
     * omits unit-specific identifiers (e.g. a generic estate Till placard).
     */
    fun matchLeaseAndInvoice(
        notice: ParsedRentQrNotice,
        leases: List<Lease>,
        unitsById: Map<String, RentalUnit>,
        invoices: List<Invoice>
    ): MatchedQrLeaseTarget {
        val activeLeases = leases.filter {
            it.statusEnum() == LeaseStatus.ACTIVE || it.statusEnum() == LeaseStatus.RENEWED
        }.ifEmpty { leases }

        var bestLease: Lease? = null
        var bestScore = 0
        for (lease in activeLeases) {
            val unit = unitsById[lease.unitId]
            var score = 0

            val configuredShortcode = when (notice.channel) {
                MpesaInstructionChannel.PAYBILL -> PaymentFormattingUtils.normalizeShortcode(unit?.mpesaPaybill)
                MpesaInstructionChannel.TILL -> PaymentFormattingUtils.normalizeShortcode(unit?.mpesaTill)
            }
            if (configuredShortcode.isNotBlank() && configuredShortcode == notice.shortcode) {
                score += 30
            }

            val expectedRef = PaymentFormattingUtils.formatAccountReference(
                prefix = unit?.mpesaAccountPrefix,
                unitNumber = lease.unitNumber
            )
            val scannedRef = notice.accountReference
            val cleanUnitToken = PaymentFormattingUtils.sanitizeAccountToken(lease.unitNumber)
            if (!scannedRef.isNullOrBlank()) {
                if (scannedRef.equals(expectedRef, ignoreCase = true)) {
                    score += 50
                } else if (cleanUnitToken.isNotBlank() && scannedRef.endsWith(cleanUnitToken, ignoreCase = true)) {
                    score += 25
                }
            }

            if (!notice.unitHint.isNullOrBlank()) {
                val cleanHint = PaymentFormattingUtils.sanitizeAccountToken(notice.unitHint)
                if (cleanHint.isNotBlank() && cleanHint.equals(cleanUnitToken, ignoreCase = true)) {
                    score += 40
                }
            }

            if (!notice.propertyHint.isNullOrBlank() &&
                lease.propertyName.contains(notice.propertyHint, ignoreCase = true)
            ) {
                score += 20
            }

            if (score > 0 && lease.statusEnum() == LeaseStatus.ACTIVE) {
                score += 5
            }

            if (score > bestScore) {
                bestScore = score
                bestLease = lease
            }
        }

        val fallbackLease = bestLease ?: activeLeases.firstOrNull()
        val matchedUnit = fallbackLease?.let { unitsById[it.unitId] }

        val targetInvoice = fallbackLease?.let { lease ->
            invoices
                .filter {
                    it.leaseId == lease.id &&
                        it.outstandingCents > 0L &&
                        it.statusEnum() != InvoiceStatus.DRAFT &&
                        it.statusEnum() != InvoiceStatus.CANCELLED
                }
                .sortedWith(
                    compareBy<Invoice>(
                        { if (it.statusEnum() == InvoiceStatus.OVERDUE) 0 else 1 },
                        { it.dueDateEpochMs }
                    )
                )
                .firstOrNull()
        }

        val resolvedAmount = notice.amountCents?.takeIf { it > 0L }
            ?: targetInvoice?.outstandingCents?.takeIf { it > 0L }
            ?: fallbackLease?.rentCents?.takeIf { it > 0L }
            ?: 0L

        return MatchedQrLeaseTarget(
            notice = notice,
            matchedLease = fallbackLease,
            matchedUnit = matchedUnit,
            targetInvoice = targetInvoice,
            isExactLeaseMatch = bestScore > 0,
            resolvedAmountCents = resolvedAmount
        )
    }

    private fun parseQueryParams(rawQuery: String?): Map<String, String> {
        if (rawQuery.isNullOrBlank()) return emptyMap()
        val result = mutableMapOf<String, String>()
        rawQuery.split('&').forEach { pair ->
            if (pair.isBlank()) return@forEach
            val idx = pair.indexOf('=')
            val rawKey = if (idx >= 0) pair.substring(0, idx) else pair
            val rawValue = if (idx >= 0) pair.substring(idx + 1) else ""
            val key = decodeQueryComponent(rawKey).trim().lowercase()
            val value = decodeQueryComponent(rawValue).trim()
            if (key.isNotEmpty() && value.isNotEmpty()) {
                result[key] = value
            }
        }
        return result
    }

    private fun decodeQueryComponent(value: String): String =
        runCatching { URLDecoder.decode(value, StandardCharsets.UTF_8.name()) }.getOrDefault(value)
}
