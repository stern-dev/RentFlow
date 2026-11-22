package com.example.domain.finance

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object CurrencyUtils {
    private val kenyaLocale: Locale = Locale.Builder().setLanguage("en").setRegion("KE").build()
    private val billingPeriodRegex = Regex("^\\d{4}-(0[1-9]|1[0-2])$")
    private val referenceRegex = Regex("^[A-Za-z0-9\\-]{4,30}$")

    fun formatKes(cents: Long, includeDecimals: Boolean = false): String {
        val bd = BigDecimal.valueOf(cents).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
        val formatter = NumberFormat.getNumberInstance(kenyaLocale).apply {
            minimumFractionDigits = if (includeDecimals) 2 else 0
            maximumFractionDigits = if (includeDecimals) 2 else 0
        }
        return "KES ${formatter.format(bd)}"
    }

    fun formatKesCompact(cents: Long): String {
        val amount = cents / 100.0
        return when {
            amount >= 1_000_000 -> String.format(Locale.US, "KES %.1fM", amount / 1_000_000.0)
            amount >= 1_000 -> String.format(Locale.US, "KES %.1fK", amount / 1_000.0)
            else -> formatKes(cents)
        }
    }

    fun parseKesToCents(input: String): Long? {
        val sanitized = input.replace(",", "").replace("KES", "", ignoreCase = true).trim()
        if (sanitized.isBlank()) return null
        return try {
            val bd = BigDecimal(sanitized)
            if (bd < BigDecimal.ZERO) return null
            bd.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).longValueExact()
        } catch (_: Exception) {
            null
        }
    }

    fun centsToInputString(cents: Long): String {
        if (cents <= 0L) return ""
        val bd = BigDecimal.valueOf(cents).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
        return if (cents % 100L == 0L) bd.toBigInteger().toString() else bd.toPlainString()
    }

    fun centsToWholeKesInput(cents: Long): String {
        if (cents <= 0L) return ""
        return (cents / 100L).toString()
    }

    fun formatDate(epochMs: Long): String {
        if (epochMs <= 0L) return "N/A"
        val fmt = SimpleDateFormat("d MMM yyyy", kenyaLocale)
        return fmt.format(Date(epochMs))
    }

    fun formatDateTime(epochMs: Long): String {
        if (epochMs <= 0L) return "N/A"
        val fmt = SimpleDateFormat("d MMM yyyy, HH:mm", kenyaLocale)
        return fmt.format(Date(epochMs))
    }

    fun currentBillingPeriod(): String {
        val cal = Calendar.getInstance()
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH) + 1
        return String.format(Locale.US, "%04d-%02d", year, month)
    }

    fun isValidBillingPeriod(period: String): Boolean {
        return billingPeriodRegex.matches(period.trim())
    }

    fun isValidMpesaReference(code: String): Boolean {
        return referenceRegex.matches(code.trim())
    }

    fun billingPeriodLabel(period: String): String {
        return try {
            val parser = SimpleDateFormat("yyyy-MM", Locale.US)
            val date = parser.parse(period) ?: return period
            SimpleDateFormat("MMMM yyyy", kenyaLocale).format(date)
        } catch (_: Exception) {
            period
        }
    }

    fun computeDueDateEpochMs(billingPeriod: String, dueDay: Int): Long {
        return try {
            val parts = billingPeriod.split("-")
            val year = parts[0].toInt()
            val month = parts[1].toInt() - 1
            Calendar.getInstance().apply {
                set(Calendar.YEAR, year)
                set(Calendar.MONTH, month)
                val maxDay = getActualMaximum(Calendar.DAY_OF_MONTH)
                set(Calendar.DAY_OF_MONTH, dueDay.coerceIn(1, maxDay))
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 0)
            }.timeInMillis
        } catch (_: Exception) {
            System.currentTimeMillis() + 5L * 86_400_000L
        }
    }

    fun isValidKenyanPhone(phone: String): Boolean {
        val cleaned = phone.replace(" ", "").replace("-", "")
        return Regex("^(\\+254|254|0)[17][0-9]{8}$").matches(cleaned)
    }

    fun normalizeKenyanPhone(phone: String): String {
        val cleaned = phone.replace(" ", "").replace("-", "")
        return when {
            cleaned.startsWith("0") && cleaned.length == 10 -> "+254${cleaned.substring(1)}"
            cleaned.startsWith("254") && cleaned.length == 12 -> "+$cleaned"
            else -> cleaned
        }
    }
}
