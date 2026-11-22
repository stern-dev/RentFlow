package com.example.domain.finance

import com.example.domain.model.MpesaReceiptDirection
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.Locale

data class ParsedMpesaSms(
    val transactionCode: String,
    val amountCents: Long,
    val recipientOrAccount: String? = null,
    val rawSnippet: String = "",
    val direction: MpesaReceiptDirection = MpesaReceiptDirection.UNKNOWN,
    val counterpartyName: String? = null,
    val counterpartyPhone: String? = null,
    val accountReference: String? = null,
    val shortcode: String? = null,
    val transactionTimestampMs: Long = System.currentTimeMillis()
)

/**
 * Zero-permission Safaricom M-Pesa confirmation parser & Kenya rental tax helpers.
 * Supports notification auto-capture, Android Share intent, and pasted SMS text without READ_SMS.
 */
object MpesaSmsParser {
    // Safaricom codes are 10 uppercase alphanumeric characters, e.g., SJK45AB89C / UAF29KL30M
    private val confirmedCodeRegex = Regex("\\b([A-Z0-9]{10})\\b\\s+Confirmed\\b", RegexOption.IGNORE_CASE)
    private val fallbackCodeRegex = Regex("^\\s*([A-Z0-9]{10})\\b")
    private val amountRegex = Regex("(?:Ksh|KES)\\.?\\s*([0-9][0-9,]*(?:\\.[0-9]{1,2})?)", RegexOption.IGNORE_CASE)

    private val incomingMarkerRegex = Regex("\\b(?:you have received|received|credited to your|sent you)\\b", RegexOption.IGNORE_CASE)
    private val outgoingMarkerRegex = Regex("\\b(?:sent to|paid to|bought|transferred to|give)\\b", RegexOption.IGNORE_CASE)

    private val incomingFromRegex = Regex(
        "received\\s+(?:Ksh|KES)\\.?\\s*[0-9,]+(?:\\.[0-9]{1,2})?\\s+from\\s+([^.\\n]+?)(?:\\s+on\\s+\\d{1,2}/\\d{1,2}/\\d{2,4}|\\.|$)",
        RegexOption.IGNORE_CASE
    )
    private val outgoingRecipientRegex = Regex(
        "(?:paid to|sent to)\\s+([^.\\n]+?)(?:\\s+for account|\\s+on\\s+\\d{1,2}/\\d{1,2}/\\d{2,4}|\\.|$)",
        RegexOption.IGNORE_CASE
    )
    private val accountRefRegex = Regex(
        "(?:for\\s+account|account\\s*(?:no\\.?|number)?)\\s+([A-Za-z0-9\\-_./]{1,40})(?:\\s+on\\s+|\\.|\\s|$)",
        RegexOption.IGNORE_CASE
    )
    private val phoneRegex = Regex("((?:\\+254|254|0)[17][0-9]{8})")
    private val shortcodeRegex = Regex("(?:paybill|till(?:\\s+number)?|business(?:\\s+number)?)\\s*[:#]?\\s*([1-9][0-9]{4,6})\\b", RegexOption.IGNORE_CASE)
    private val standaloneShortcodeRegex = Regex("\\b([1-9][0-9]{4,6})\\b")
    private val safaricomDateTimeRegex = Regex("\\bon\\s+(\\d{1,2}/\\d{1,2}/\\d{2,4})(?:\\s+at\\s+(\\d{1,2}:\\d{2}\\s*(?:AM|PM)?))?", RegexOption.IGNORE_CASE)
    private val mpesaBrandKeywords = listOf("mpesa", "m-pesa", "safaricom", "confirmed", "paybill", "buy goods")

    fun looksLikeMpesaNotification(senderOrTitle: String?, body: String): Boolean {
        val combined = listOfNotNull(senderOrTitle, body).joinToString(" ").trim()
        if (combined.length < 12) return false
        val hasKeyword = mpesaBrandKeywords.any { combined.contains(it, ignoreCase = true) }
        val hasCode = confirmedCodeRegex.containsMatchIn(combined)
        val hasAmount = amountRegex.containsMatchIn(combined)
        return (hasCode && hasAmount) || (hasKeyword && hasAmount && fallbackCodeRegex.containsMatchIn(body))
    }

    fun parse(rawSms: String, nowMs: Long = System.currentTimeMillis()): ParsedMpesaSms? {
        val trimmed = rawSms.trim()
        if (trimmed.isBlank()) return null

        val codeMatch = confirmedCodeRegex.find(trimmed) ?: fallbackCodeRegex.find(trimmed)
        val amountMatch = amountRegex.find(trimmed)
        if (codeMatch == null || amountMatch == null) return null

        val code = codeMatch.groupValues[1].uppercase()
        if (!code.matches(Regex("^[A-Z0-9]{10}$"))) return null

        val amountClean = amountMatch.groupValues[1].replace(",", "")
        val amountCents = CurrencyUtils.parseKesToCents(amountClean) ?: return null
        if (amountCents <= 0L) return null

        val isIncoming = incomingMarkerRegex.containsMatchIn(trimmed)
        val isOutgoing = outgoingMarkerRegex.containsMatchIn(trimmed)
        val direction = when {
            isIncoming && !isOutgoing -> MpesaReceiptDirection.INCOMING_RECEIVED
            isOutgoing && !isIncoming -> MpesaReceiptDirection.OUTGOING_SENT
            isIncoming -> MpesaReceiptDirection.INCOMING_RECEIVED
            isOutgoing -> MpesaReceiptDirection.OUTGOING_SENT
            else -> MpesaReceiptDirection.UNKNOWN
        }

        val rawCounterpartyBlock = when (direction) {
            MpesaReceiptDirection.INCOMING_RECEIVED ->
                incomingFromRegex.find(trimmed)?.groupValues?.getOrNull(1)?.trim()
            else ->
                outgoingRecipientRegex.find(trimmed)?.groupValues?.getOrNull(1)?.trim()
                    ?: incomingFromRegex.find(trimmed)?.groupValues?.getOrNull(1)?.trim()
        }

        val detectedPhone = (rawCounterpartyBlock?.let { phoneRegex.find(it) } ?: phoneRegex.find(trimmed))
            ?.groupValues?.getOrNull(1)
            ?.let { CurrencyUtils.normalizeKenyanPhone(it) }

        val accountRef = accountRefRegex.find(trimmed)?.groupValues?.getOrNull(1)
            ?.trim()
            ?.take(40)
            ?.takeIf { it.isNotBlank() }

        val shortcode = shortcodeRegex.find(trimmed)?.groupValues?.getOrNull(1)
            ?: rawCounterpartyBlock?.let { block ->
                val blockWithoutPhone = phoneRegex.replace(block, " ")
                standaloneShortcodeRegex.find(blockWithoutPhone)?.groupValues?.getOrNull(1)
            }

        val cleanedCounterpartyName = rawCounterpartyBlock?.let { block ->
            var cleaned = phoneRegex.replace(block, " ")
            if (!shortcode.isNullOrBlank()) {
                cleaned = cleaned.replace(Regex("\\b${Regex.escape(shortcode)}\\b"), " ")
            }
            cleaned = cleaned
                .replace(Regex("\\bfor\\s+account.*$", RegexOption.IGNORE_CASE), "")
                .replace(Regex("\\s+"), " ")
                .trim(' ', '-', ',', '.')
            cleaned.take(100).takeIf { it.length >= 2 }
        }

        val parsedTimestampMs = parseSafaricomTimestamp(trimmed) ?: nowMs
        val normalizedSnippet = trimmed.replace(Regex("\\s+"), " ").take(220)

        return ParsedMpesaSms(
            transactionCode = code,
            amountCents = amountCents,
            recipientOrAccount = rawCounterpartyBlock ?: accountRef,
            rawSnippet = normalizedSnippet,
            direction = direction,
            counterpartyName = cleanedCounterpartyName,
            counterpartyPhone = detectedPhone,
            accountReference = accountRef,
            shortcode = shortcode,
            transactionTimestampMs = parsedTimestampMs
        )
    }

    /**
     * Splits multi-SMS pasted blocks (e.g. multiple Safaricom confirmations pasted together)
     * and parses each unique transaction code in order of appearance.
     */
    fun parseAll(rawBatch: String, nowMs: Long = System.currentTimeMillis()): List<ParsedMpesaSms> {
        val trimmed = rawBatch.trim()
        if (trimmed.isBlank()) return emptyList()

        val matches = confirmedCodeRegex.findAll(trimmed).toList()
        if (matches.size <= 1) {
            return listOfNotNull(parse(trimmed, nowMs))
        }

        val results = mutableListOf<ParsedMpesaSms>()
        val seenCodes = mutableSetOf<String>()
        for (i in matches.indices) {
            val start = matches[i].range.first
            val end = if (i + 1 < matches.size) matches[i + 1].range.first else trimmed.length
            val chunk = trimmed.substring(start, end).trim()
            val parsed = parse(chunk, nowMs)
            if (parsed != null && seenCodes.add(parsed.transactionCode)) {
                results += parsed
            }
        }
        return results
    }

    private fun parseSafaricomTimestamp(raw: String): Long? {
        val m = safaricomDateTimeRegex.find(raw) ?: return null
        val dateStr = m.groupValues[1].trim()
        val timeStr = m.groupValues.getOrNull(2)?.trim().orEmpty()
        val candidates = if (timeStr.isNotBlank()) {
            listOf(
                "d/M/yy h:mm a" to "$dateStr $timeStr",
                "d/M/yyyy h:mm a" to "$dateStr $timeStr",
                "d/M/yy HH:mm" to "$dateStr $timeStr",
                "d/M/yyyy HH:mm" to "$dateStr $timeStr"
            )
        } else {
            listOf(
                "d/M/yy" to dateStr,
                "d/M/yyyy" to dateStr
            )
        }
        for ((pattern, value) in candidates) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.US).apply { isLenient = false }
                val parsed = sdf.parse(value)
                if (parsed != null) return parsed.time
            } catch (_: Exception) {
            }
        }
        return null
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
