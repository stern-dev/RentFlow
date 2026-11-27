package com.example.data.notifications

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.R
import com.example.domain.finance.CurrencyUtils
import com.example.domain.finance.RentReminderEngine
import com.example.domain.finance.RentReminderUrgency
import com.example.domain.model.Invoice
import com.example.domain.model.InvoiceStatus
import com.example.domain.model.UserProfile
import com.example.domain.model.UserRole
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

class RentReminderWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val currentUser = FirebaseAuth.getInstance().currentUser ?: return Result.success()
        if (!NotificationHelper.hasNotificationPermission(applicationContext)) {
            return Result.success()
        }

        return try {
            val dbId = applicationContext.getString(R.string.firestore_database_id)
            val db = FirebaseFirestore.getInstance(FirebaseApp.getInstance(), dbId)
            val profileSnap = db.collection("users").document(currentUser.uid).get().await()
            val profile = profileSnap.toObject(UserProfile::class.java) ?: return Result.success()
            if (profile.suspended || !profile.notifyRentDue) return Result.success()

            val role = profile.activeRoleEnum()
            if (role == UserRole.TENANT) {
                val invSnap = db.collection("invoices")
                    .whereEqualTo("tenantUid", currentUser.uid)
                    .whereIn(
                        "status",
                        listOf(
                            InvoiceStatus.ISSUED.name,
                            InvoiceStatus.UNPAID.name,
                            InvoiceStatus.PARTIALLY_PAID.name,
                            InvoiceStatus.OVERDUE.name
                        )
                    )
                    .get()
                    .await()
                val invoices = invSnap.documents.mapNotNull { doc ->
                    doc.toObject(Invoice::class.java)?.copy(id = doc.id)
                }
                val candidates = RentReminderEngine.evaluateInvoices(invoices)
                    .filter { candidate ->
                        RentReminderDeliveryLedger.shouldSend(
                            context = applicationContext,
                            invoiceId = candidate.invoice.id,
                            urgency = candidate.urgency,
                            dueDateEpochMs = candidate.invoice.dueDateEpochMs
                        )
                    }
                if (candidates.isNotEmpty()) {
                    val primary = candidates.first()
                    val title = if (candidates.size == 1) {
                        primary.title
                    } else {
                        "${candidates.size} Rent Invoices Need Attention"
                    }
                    val body = if (candidates.size == 1) {
                        primary.body
                    } else {
                        val totalOutstanding = candidates.sumOf { it.invoice.outstandingCents }
                        "Total outstanding: ${CurrencyUtils.formatKes(totalOutstanding)}. ${primary.body}"
                    }
                    val delivered = NotificationHelper.showRentReminderNotification(
                        context = applicationContext,
                        title = title,
                        body = body,
                        targetRoute = NotificationHelper.ROUTE_TENANT_PAY,
                        targetTab = NotificationHelper.TAB_TENANT_INVOICES,
                        invoiceId = primary.invoice.id,
                        unitId = primary.invoice.unitId.takeIf { it.isNotBlank() },
                        dedupKey = "rent_reminder_${primary.invoice.id}_${primary.urgency.name}"
                    )
                    if (delivered) {
                        RentReminderDeliveryLedger.markSent(
                            context = applicationContext,
                            invoiceId = primary.invoice.id,
                            urgency = primary.urgency,
                            dueDateEpochMs = primary.invoice.dueDateEpochMs
                        )
                    }
                }
            } else if (role == UserRole.LANDLORD || role == UserRole.PROPERTY_MANAGER) {
                val invSnap = db.collection("invoices")
                    .whereArrayContains("authorizedUids", currentUser.uid)
                    .get()
                    .await()
                val invoices = invSnap.documents.mapNotNull { doc ->
                    doc.toObject(Invoice::class.java)?.copy(id = doc.id)
                }
                val candidates = RentReminderEngine.evaluateInvoices(invoices)
                    .filter { it.urgency == RentReminderUrgency.OVERDUE || it.urgency == RentReminderUrgency.DUE_TODAY }
                if (candidates.isNotEmpty()) {
                    val totalArrears = candidates.sumOf { it.invoice.outstandingCents }
                    NotificationHelper.showRentReminderNotification(
                        context = applicationContext,
                        title = "${candidates.size} Rent Invoice(s) Due or Overdue",
                        body = "Pending collection of ${CurrencyUtils.formatKes(totalArrears)}. Open Finance to send tenant reminders.",
                        targetRoute = NotificationHelper.ROUTE_LANDLORD_FINANCE,
                        dedupKey = "worker_landlord_${currentUser.uid}_${candidates.size}"
                    )
                }
            }
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "rentflow_daily_rent_reminders"

        fun scheduleDaily(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = PeriodicWorkRequestBuilder<RentReminderWorker>(24, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun cancelDaily(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
        }
    }
}
