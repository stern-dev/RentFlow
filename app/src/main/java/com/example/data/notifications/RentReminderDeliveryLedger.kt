package com.example.data.notifications

import android.content.Context
import android.content.SharedPreferences
import com.example.domain.finance.RentReminderEngine
import com.example.domain.finance.RentReminderUrgency

/**
 * Persistent SharedPreferences ledger tracking delivered rent reminder notifications per invoice
 * and urgency stage so exact 3-day/due-day WorkManager jobs and daily periodic checks never
 * double-notify a tenant on the same stage or within the cooldown window.
 */
object RentReminderDeliveryLedger {
    private const val PREFS_NAME = "rentflow_rent_reminder_ledger"
    private const val KEY_ACTIVE_WORK_NAMES = "active_scheduled_work_names"

    private fun prefs(context: Context): SharedPreferences {
        return context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun shouldSend(
        context: Context,
        invoiceId: String,
        urgency: RentReminderUrgency,
        dueDateEpochMs: Long,
        nowMs: Long = System.currentTimeMillis(),
        cooldownMs: Long = RentReminderEngine.COOLDOWN_MS
    ): Boolean {
        val cleanId = invoiceId.trim()
        if (cleanId.isBlank()) return false
        val sp = prefs(context)

        // Prevent same-day cross-stage spam if an invoice was just created/notified within cooldown
        val lastAny = sp.getLong(anyStageKey(cleanId), -1L)
        if (lastAny > 0L && (nowMs - lastAny) < cooldownMs) {
            return false
        }

        return when (urgency) {
            RentReminderUrgency.DUE_SOON -> {
                // Fire once per invoice due-date cycle for the 3-day lead window
                !sp.contains(stageDueKey(cleanId, urgency, dueDateEpochMs))
            }
            RentReminderUrgency.DUE_TODAY -> {
                // Fire once on due day for that due-date cycle
                !sp.contains(stageDueKey(cleanId, urgency, dueDateEpochMs))
            }
            RentReminderUrgency.OVERDUE -> {
                // Escalate at most once per cooldownMs (20 hours)
                val lastOverdue = sp.getLong(overdueKey(cleanId), -1L)
                lastOverdue <= 0L || (nowMs - lastOverdue) >= cooldownMs
            }
        }
    }

    fun markSent(
        context: Context,
        invoiceId: String,
        urgency: RentReminderUrgency,
        dueDateEpochMs: Long,
        nowMs: Long = System.currentTimeMillis()
    ) {
        val cleanId = invoiceId.trim()
        if (cleanId.isBlank()) return
        val editor = prefs(context).edit()
        editor.putLong(anyStageKey(cleanId), nowMs)
        when (urgency) {
            RentReminderUrgency.DUE_SOON,
            RentReminderUrgency.DUE_TODAY -> {
                editor.putLong(stageDueKey(cleanId, urgency, dueDateEpochMs), nowMs)
            }
            RentReminderUrgency.OVERDUE -> {
                editor.putLong(overdueKey(cleanId), nowMs)
            }
        }
        editor.apply()
    }

    fun getActiveWorkNames(context: Context): Set<String> {
        return prefs(context).getStringSet(KEY_ACTIVE_WORK_NAMES, emptySet())?.toSet() ?: emptySet()
    }

    fun setActiveWorkNames(context: Context, workNames: Set<String>) {
        prefs(context).edit()
            .putStringSet(KEY_ACTIVE_WORK_NAMES, workNames.toSet())
            .apply()
    }

    fun clearAll(context: Context) {
        prefs(context).edit().clear().apply()
    }

    private fun anyStageKey(invoiceId: String): String = "last_any_$invoiceId"

    private fun stageDueKey(
        invoiceId: String,
        urgency: RentReminderUrgency,
        dueDateEpochMs: Long
    ): String = "stage_${invoiceId}_${urgency.name}_$dueDateEpochMs"

    private fun overdueKey(invoiceId: String): String = "overdue_$invoiceId"
}
