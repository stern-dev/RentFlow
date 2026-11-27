package com.example.data.notifications

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.domain.finance.RentReminderEngine
import com.example.domain.finance.ScheduledRentReminderPlan
import com.example.domain.model.Invoice
import java.util.concurrent.TimeUnit

/**
 * Coordinates RentFlow's hybrid rent notification schedule:
 * 1) Exact per-invoice OneTimeWorkRequests for 3 days before due date and due day (at 9:00 AM local time).
 * 2) Daily PeriodicWorkRequest via [RentReminderWorker] for overdue escalations and cross-device sync.
 */
object RentReminderScheduler {
    const val TAG_SCHEDULED_RENT_REMINDER = "rentflow_scheduled_rent_reminder"

    fun syncTenantSchedule(
        context: Context,
        userId: String,
        notifyRentDue: Boolean,
        invoices: List<Invoice>,
        nowMs: Long = System.currentTimeMillis()
    ): List<ScheduledRentReminderPlan> {
        val appContext = context.applicationContext
        val workManager = WorkManager.getInstance(appContext)

        if (!notifyRentDue || userId.isBlank()) {
            cancelAllTenantScheduledReminders(appContext)
            return emptyList()
        }

        // Keep the daily periodic safety net armed while alerts are enabled.
        RentReminderWorker.scheduleDaily(appContext)

        val candidatePlans = RentReminderEngine.buildSchedulePlans(invoices, nowMs)
        val activePlans = candidatePlans.filter { plan ->
            RentReminderDeliveryLedger.shouldSend(
                context = appContext,
                invoiceId = plan.invoiceId,
                urgency = plan.stage.urgency,
                dueDateEpochMs = plan.dueDateEpochMs,
                nowMs = nowMs
            )
        }

        val previouslyActiveWorkNames = RentReminderDeliveryLedger.getActiveWorkNames(appContext)
        val desiredWorkNames = activePlans.map { it.uniqueWorkName }.toSet()

        // Cancel stale jobs for paid, cancelled, or already-notified invoices/stages.
        (previouslyActiveWorkNames - desiredWorkNames).forEach { staleName ->
            workManager.cancelUniqueWork(staleName)
        }

        activePlans.forEach { plan ->
            val inputData = Data.Builder()
                .putString(ScheduledRentReminderWorker.KEY_USER_ID, userId)
                .putString(ScheduledRentReminderWorker.KEY_INVOICE_ID, plan.invoiceId)
                .putString(ScheduledRentReminderWorker.KEY_UNIT_ID, plan.unitId)
                .putString(ScheduledRentReminderWorker.KEY_TITLE, plan.title)
                .putString(ScheduledRentReminderWorker.KEY_BODY, plan.body)
                .putLong(ScheduledRentReminderWorker.KEY_DUE_DATE_EPOCH_MS, plan.dueDateEpochMs)
                .putString(ScheduledRentReminderWorker.KEY_STAGE, plan.stage.name)
                .build()

            val request = OneTimeWorkRequestBuilder<ScheduledRentReminderWorker>()
                .setInitialDelay(plan.delayMs, TimeUnit.MILLISECONDS)
                .setInputData(inputData)
                .addTag(TAG_SCHEDULED_RENT_REMINDER)
                .addTag(invoiceTag(plan.invoiceId))
                .build()

            workManager.enqueueUniqueWork(
                plan.uniqueWorkName,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }

        RentReminderDeliveryLedger.setActiveWorkNames(appContext, desiredWorkNames)
        return activePlans
    }

    fun cancelAllTenantScheduledReminders(context: Context) {
        val appContext = context.applicationContext
        val workManager = WorkManager.getInstance(appContext)
        workManager.cancelAllWorkByTag(TAG_SCHEDULED_RENT_REMINDER)
        RentReminderDeliveryLedger.getActiveWorkNames(appContext).forEach { workName ->
            workManager.cancelUniqueWork(workName)
        }
        RentReminderDeliveryLedger.setActiveWorkNames(appContext, emptySet())
    }

    fun invoiceTag(invoiceId: String): String = "rentflow_invoice_${invoiceId.trim()}"
}
