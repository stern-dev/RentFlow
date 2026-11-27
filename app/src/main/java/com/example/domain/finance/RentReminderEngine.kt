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

enum class ScheduledReminderStage(
    val daysBeforeDue: Long,
    val urgency: RentReminderUrgency,
    val label: String
) {
    THREE_DAYS_BEFORE(
        daysBeforeDue = 3L,
        urgency = RentReminderUrgency.DUE_SOON,
        label = "3 Days Before Due"
    ),
    DUE_TODAY(
        daysBeforeDue = 0L,
        urgency = RentReminderUrgency.DUE_TODAY,
        label = "Due Today"
    )
}

data class RentReminderCandidate(
    val invoice: Invoice,
    val urgency: RentReminderUrgency,
    val daysDelta: Long,
    val title: String,
    val body: String
)

data class ScheduledRentReminderPlan(
    val invoiceId: String,
    val tenantUid: String,
    val unitId: String,
    val unitNumber: String,
    val propertyName: String,
    val billingPeriod: String,
    val outstandingCents: Long,
    val dueDateEpochMs: Long,
    val stage: ScheduledReminderStage,
    val triggerAtEpochMs: Long,
    val delayMs: Long,
    val title: String,
    val body: String,
    val uniqueWorkName: String
)

object RentReminderEngine {
    const val COOLDOWN_MS: Long = 20L * 60L * 60L * 1000L // 20 hours between automated reminders
    const val DUE_SOON_WINDOW_DAYS: Long = 3L
    const val DEFAULT_REMINDER_HOUR_OF_DAY: Int = 9
    const val DEFAULT_REMINDER_MINUTE: Int = 0

    fun evaluateInvoices(
        invoices: List<Invoice>,
        nowMs: Long = System.currentTimeMillis(),
        respectCooldown: Boolean = true
    ): List<RentReminderCandidate> {
        val startOfToday = startOfDayMs(nowMs)
        return invoices.mapNotNull { inv ->
            if (!isEligibleForReminder(inv)) return@mapNotNull null

            if (respectCooldown && inv.lastReminderSentEpochMs != null) {
                if ((nowMs - inv.lastReminderSentEpochMs) < COOLDOWN_MS) return@mapNotNull null
            }

            val dueStart = startOfDayMs(inv.dueDateEpochMs)
            val daysUntilDue = TimeUnit.MILLISECONDS.toDays(dueStart - startOfToday)
            val status = inv.statusEnum()

            when {
                daysUntilDue < 0L || status == InvoiceStatus.OVERDUE -> {
                    val overdueDays = (-daysUntilDue).coerceAtLeast(1L)
                    buildCandidate(inv, RentReminderUrgency.OVERDUE, overdueDays)
                }
                daysUntilDue == 0L -> {
                    buildCandidate(inv, RentReminderUrgency.DUE_TODAY, 0L)
                }
                daysUntilDue <= DUE_SOON_WINDOW_DAYS -> {
                    buildCandidate(inv, RentReminderUrgency.DUE_SOON, daysUntilDue)
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

    fun buildSchedulePlans(
        invoices: List<Invoice>,
        nowMs: Long = System.currentTimeMillis(),
        reminderHour: Int = DEFAULT_REMINDER_HOUR_OF_DAY,
        reminderMinute: Int = DEFAULT_REMINDER_MINUTE
    ): List<ScheduledRentReminderPlan> {
        val todayStart = startOfDayMs(nowMs)
        val plans = mutableListOf<ScheduledRentReminderPlan>()

        invoices.filter { isEligibleForReminder(it) && it.id.isNotBlank() }.forEach { inv ->
            val dueStart = startOfDayMs(inv.dueDateEpochMs)
            val daysUntilDue = TimeUnit.MILLISECONDS.toDays(dueStart - todayStart)

            // 3-day-prior exact reminder (or immediate catch-up when already within 1..3 days before due date)
            if (daysUntilDue > 0L) {
                val threeDaysTriggerMs = computeTriggerTimeMs(
                    dueDateEpochMs = inv.dueDateEpochMs,
                    daysBeforeDue = ScheduledReminderStage.THREE_DAYS_BEFORE.daysBeforeDue,
                    hourOfDay = reminderHour,
                    minute = reminderMinute
                )
                val shouldScheduleThreeDay = daysUntilDue > DUE_SOON_WINDOW_DAYS || daysUntilDue in 1L..DUE_SOON_WINDOW_DAYS
                if (shouldScheduleThreeDay) {
                    val effectiveTriggerMs = maxOf(threeDaysTriggerMs, nowMs)
                    val deltaDays = daysUntilDue.coerceIn(1L, DUE_SOON_WINDOW_DAYS)
                    val candidate = buildCandidate(inv, RentReminderUrgency.DUE_SOON, deltaDays)
                    plans += ScheduledRentReminderPlan(
                        invoiceId = inv.id,
                        tenantUid = inv.tenantUid,
                        unitId = inv.unitId,
                        unitNumber = inv.unitNumber,
                        propertyName = inv.propertyName,
                        billingPeriod = inv.billingPeriod,
                        outstandingCents = inv.outstandingCents,
                        dueDateEpochMs = inv.dueDateEpochMs,
                        stage = ScheduledReminderStage.THREE_DAYS_BEFORE,
                        triggerAtEpochMs = effectiveTriggerMs,
                        delayMs = (effectiveTriggerMs - nowMs).coerceAtLeast(0L),
                        title = candidate.title,
                        body = candidate.body,
                        uniqueWorkName = uniqueWorkName(inv.id, ScheduledReminderStage.THREE_DAYS_BEFORE)
                    )
                }
            }

            // Due-day reminder at 9:00 AM on due date (or immediate catch-up if due today)
            if (daysUntilDue >= 0L) {
                val dueDayTriggerMs = computeTriggerTimeMs(
                    dueDateEpochMs = inv.dueDateEpochMs,
                    daysBeforeDue = ScheduledReminderStage.DUE_TODAY.daysBeforeDue,
                    hourOfDay = reminderHour,
                    minute = reminderMinute
                )
                val effectiveTriggerMs = maxOf(dueDayTriggerMs, nowMs)
                val candidate = buildCandidate(inv, RentReminderUrgency.DUE_TODAY, 0L)
                plans += ScheduledRentReminderPlan(
                    invoiceId = inv.id,
                    tenantUid = inv.tenantUid,
                    unitId = inv.unitId,
                    unitNumber = inv.unitNumber,
                    propertyName = inv.propertyName,
                    billingPeriod = inv.billingPeriod,
                    outstandingCents = inv.outstandingCents,
                    dueDateEpochMs = inv.dueDateEpochMs,
                    stage = ScheduledReminderStage.DUE_TODAY,
                    triggerAtEpochMs = effectiveTriggerMs,
                    delayMs = (effectiveTriggerMs - nowMs).coerceAtLeast(0L),
                    title = candidate.title,
                    body = candidate.body,
                    uniqueWorkName = uniqueWorkName(inv.id, ScheduledReminderStage.DUE_TODAY)
                )
            }
        }

        return plans.sortedWith(
            compareBy<ScheduledRentReminderPlan> { it.triggerAtEpochMs }
                .thenBy { it.stage.ordinal }
                .thenBy { it.invoiceId }
        )
    }

    fun buildCandidateForStage(
        invoice: Invoice,
        stage: ScheduledReminderStage,
        nowMs: Long = System.currentTimeMillis()
    ): RentReminderCandidate {
        val dueStart = startOfDayMs(invoice.dueDateEpochMs)
        val todayStart = startOfDayMs(nowMs)
        val daysUntilDue = TimeUnit.MILLISECONDS.toDays(dueStart - todayStart)
        return when {
            daysUntilDue < 0L || invoice.statusEnum() == InvoiceStatus.OVERDUE -> {
                buildCandidate(invoice, RentReminderUrgency.OVERDUE, (-daysUntilDue).coerceAtLeast(1L))
            }
            stage == ScheduledReminderStage.DUE_TODAY || daysUntilDue == 0L -> {
                buildCandidate(invoice, RentReminderUrgency.DUE_TODAY, 0L)
            }
            else -> {
                buildCandidate(
                    invoice,
                    RentReminderUrgency.DUE_SOON,
                    daysUntilDue.coerceIn(1L, DUE_SOON_WINDOW_DAYS)
                )
            }
        }
    }

    fun nextScheduledReminder(
        plans: List<ScheduledRentReminderPlan>,
        nowMs: Long = System.currentTimeMillis()
    ): ScheduledRentReminderPlan? {
        return plans
            .filter { it.triggerAtEpochMs >= nowMs || it.delayMs == 0L }
            .minByOrNull { it.triggerAtEpochMs }
    }

    fun uniqueWorkName(invoiceId: String, stage: ScheduledReminderStage): String {
        return "rentflow_due_reminder_${invoiceId.trim()}_${stage.name}"
    }

    fun computeTriggerTimeMs(
        dueDateEpochMs: Long,
        daysBeforeDue: Long,
        hourOfDay: Int = DEFAULT_REMINDER_HOUR_OF_DAY,
        minute: Int = DEFAULT_REMINDER_MINUTE
    ): Long {
        return Calendar.getInstance().apply {
            timeInMillis = dueDateEpochMs
            add(Calendar.DAY_OF_YEAR, (-daysBeforeDue).toInt())
            set(Calendar.HOUR_OF_DAY, hourOfDay.coerceIn(0, 23))
            set(Calendar.MINUTE, minute.coerceIn(0, 59))
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    fun isEligibleForReminder(invoice: Invoice): Boolean {
        if (invoice.outstandingCents <= 0L) return false
        val status = invoice.statusEnum()
        return status != InvoiceStatus.DRAFT &&
            status != InvoiceStatus.PAID &&
            status != InvoiceStatus.CANCELLED
    }

    private fun buildCandidate(
        inv: Invoice,
        urgency: RentReminderUrgency,
        daysDelta: Long
    ): RentReminderCandidate {
        val amountLabel = CurrencyUtils.formatKes(inv.outstandingCents)
        return when (urgency) {
            RentReminderUrgency.OVERDUE -> RentReminderCandidate(
                invoice = inv,
                urgency = RentReminderUrgency.OVERDUE,
                daysDelta = daysDelta,
                title = "Overdue Rent Notice • Unit ${inv.unitNumber}",
                body = "${inv.propertyName} (${inv.billingPeriod}): $amountLabel is $daysDelta day(s) overdue. Tap to pay via M-Pesa."
            )
            RentReminderUrgency.DUE_TODAY -> RentReminderCandidate(
                invoice = inv,
                urgency = RentReminderUrgency.DUE_TODAY,
                daysDelta = 0L,
                title = "Rent Due Today • Unit ${inv.unitNumber}",
                body = "${inv.propertyName} (${inv.billingPeriod}): $amountLabel is due today. Tap to open the Payment tab and pay via M-Pesa."
            )
            RentReminderUrgency.DUE_SOON -> RentReminderCandidate(
                invoice = inv,
                urgency = RentReminderUrgency.DUE_SOON,
                daysDelta = daysDelta,
                title = "Rent Due in $daysDelta Day(s) • Unit ${inv.unitNumber}",
                body = "${inv.propertyName} (${inv.billingPeriod}): $amountLabel is due on ${CurrencyUtils.formatDate(inv.dueDateEpochMs)}. Tap to pay via M-Pesa."
            )
        }
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
