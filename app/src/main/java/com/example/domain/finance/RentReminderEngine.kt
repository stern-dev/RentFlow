package com.example.domain.finance

import com.example.domain.model.Invoice
import com.example.domain.model.InvoiceStatus
import java.util.Calendar
import java.util.concurrent.TimeUnit

enum class RentReminderUrgency(val label: String) {
    OVERDUE("Overdue"),
    DUE_TODAY("Due Today"),
    DUE_SOON("Due Soon")
}

data class RentReminderCandidate(
    val invoice: Invoice,
    val urgency: RentReminderUrgency,
    val daysDelta: Long,
    val title: String,
    val body: String
)

object RentReminderEngine {
    const val COOLDOWN_MS: Long = 20L * 60L * 60L * 1000L // 20 hours between automated reminders
    const val DUE_SOON_WINDOW_DAYS: Long = 3L

    fun evaluateInvoices(
        invoices: List<Invoice>,
        nowMs: Long = System.currentTimeMillis(),
        respectCooldown: Boolean = true
    ): List<RentReminderCandidate> {
        val startOfToday = startOfDayMs(nowMs)
        return invoices.mapNotNull { inv ->
            if (inv.outstandingCents <= 0L) return@mapNotNull null
            val status = inv.statusEnum()
            if (status == InvoiceStatus.PAID || status == InvoiceStatus.CANCELLED) return@mapNotNull null

            if (respectCooldown && inv.lastReminderSentEpochMs != null) {
                if ((nowMs - inv.lastReminderSentEpochMs) < COOLDOWN_MS) return@mapNotNull null
            }

            val dueStart = startOfDayMs(inv.dueDateEpochMs)
            val daysUntilDue = TimeUnit.MILLISECONDS.toDays(dueStart - startOfToday)
            val amountLabel = CurrencyUtils.formatKes(inv.outstandingCents)

            when {
                daysUntilDue < 0L || status == InvoiceStatus.OVERDUE -> {
                    val overdueDays = (-daysUntilDue).coerceAtLeast(1L)
                    RentReminderCandidate(
                        invoice = inv,
                        urgency = RentReminderUrgency.OVERDUE,
                        daysDelta = overdueDays,
                        title = "Overdue Rent Notice • Unit ${inv.unitNumber}",
                        body = "${inv.propertyName} (${inv.billingPeriod}): $amountLabel is $overdueDays day(s) overdue. Tap to pay via M-Pesa."
                    )
                }
                daysUntilDue == 0L -> {
                    RentReminderCandidate(
                        invoice = inv,
                        urgency = RentReminderUrgency.DUE_TODAY,
                        daysDelta = 0L,
                        title = "Rent Due Today • Unit ${inv.unitNumber}",
                        body = "${inv.propertyName} (${inv.billingPeriod}): $amountLabel is due today. Submit your M-Pesa receipt to stay current."
                    )
                }
                daysUntilDue <= DUE_SOON_WINDOW_DAYS -> {
                    RentReminderCandidate(
                        invoice = inv,
                        urgency = RentReminderUrgency.DUE_SOON,
                        daysDelta = daysUntilDue,
                        title = "Rent Due in $daysUntilDue Day(s) • Unit ${inv.unitNumber}",
                        body = "${inv.propertyName} (${inv.billingPeriod}): $amountLabel is due on ${CurrencyUtils.formatDate(inv.dueDateEpochMs)}."
                    )
                }
                else -> null
            }
        }.sortedWith(
            compareBy<RentReminderCandidate> {
                when (it.urgency) {
                    RentReminderUrgency.OVERDUE -> 0
                    RentReminderUrgency.DUE_TODAY -> 1
                    RentReminderUrgency.DUE_SOON -> 2
                }
            }.thenBy { it.invoice.dueDateEpochMs }
        )
    }

    private fun startOfDayMs(epochMs: Long): Long {
        return Calendar.getInstance().apply {
            timeInMillis = epochMs
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
}
