package com.example.data.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.R
import com.example.domain.finance.RentReminderEngine
import com.example.domain.finance.ScheduledReminderStage
import com.example.domain.model.Invoice
import com.example.domain.model.UserProfile
import com.example.domain.model.UserRole
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Exact-delay per-invoice worker responsible for delivering 3-day-prior and due-day rent
 * reminder notifications, linking directly to the Tenant Payment Invoices tab with the
 * target invoice highlighted.
 */
class ScheduledRentReminderWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val invoiceId = inputData.getString(KEY_INVOICE_ID)?.trim().orEmpty()
        if (invoiceId.isBlank()) return Result.success()

        val userId = inputData.getString(KEY_USER_ID)?.trim().orEmpty()
        val unitId = inputData.getString(KEY_UNIT_ID)?.trim().orEmpty()
        val fallbackTitle = inputData.getString(KEY_TITLE)?.trim().orEmpty()
        val fallbackBody = inputData.getString(KEY_BODY)?.trim().orEmpty()
        val dueDateEpochMs = inputData.getLong(KEY_DUE_DATE_EPOCH_MS, 0L)
        val stageName = inputData.getString(KEY_STAGE)?.trim().orEmpty()
        val stage = ScheduledReminderStage.entries.firstOrNull { it.name == stageName }
            ?: ScheduledReminderStage.THREE_DAYS_BEFORE
        val bypassRemoteCheck = inputData.getBoolean(KEY_BYPASS_REMOTE_CHECK_FOR_TEST, false)

        if (!NotificationHelper.hasNotificationPermission(applicationContext)) {
            return Result.success()
        }

        var resolvedTitle = fallbackTitle
        var resolvedBody = fallbackBody
        var resolvedUnitId = unitId
        var resolvedDueDateMs = dueDateEpochMs
        var resolvedUrgency = stage.urgency

        if (!bypassRemoteCheck && FirebaseApp.getApps(applicationContext).isNotEmpty()) {
            val authUser = FirebaseAuth.getInstance().currentUser ?: return Result.success()
            if (userId.isNotBlank() && authUser.uid != userId) {
                return Result.success()
            }

            // Best-effort live Firestore verification so already-paid or paused reminders are suppressed.
            val remoteCheck = withTimeoutOrNull(4_500L) {
                runCatching {
                    val dbId = applicationContext.getString(R.string.firestore_database_id)
                    val db = FirebaseFirestore.getInstance(FirebaseApp.getInstance(), dbId)
                    val profileSnap = db.collection("users").document(authUser.uid).get().await()
                    val profile = profileSnap.toObject(UserProfile::class.java)
                    if (profile != null && (profile.suspended || !profile.notifyRentDue || profile.activeRoleEnum() != UserRole.TENANT)) {
                        return@runCatching RemoteVerification.SUPPRESS
                    }

                    val invoiceSnap = db.collection("invoices").document(invoiceId).get().await()
                    if (!invoiceSnap.exists()) {
                        return@runCatching RemoteVerification.SUPPRESS
                    }
                    val freshInvoice = invoiceSnap.toObject(Invoice::class.java)?.copy(id = invoiceSnap.id)
                    if (freshInvoice != null) {
                        if (!RentReminderEngine.isEligibleForReminder(freshInvoice)) {
                            return@runCatching RemoteVerification.SUPPRESS
                        }
                        val candidate = RentReminderEngine.buildCandidateForStage(freshInvoice, stage)
                        resolvedTitle = candidate.title
                        resolvedBody = candidate.body
                        resolvedUnitId = freshInvoice.unitId.ifBlank { resolvedUnitId }
                        resolvedDueDateMs = freshInvoice.dueDateEpochMs
                        resolvedUrgency = candidate.urgency
                    }
                    RemoteVerification.PROCEED
                }.getOrDefault(RemoteVerification.PROCEED)
            } ?: RemoteVerification.PROCEED

            if (remoteCheck == RemoteVerification.SUPPRESS) {
                return Result.success()
            }
        }

        if (resolvedTitle.isBlank() || resolvedBody.isBlank()) {
            return Result.success()
        }

        if (!RentReminderDeliveryLedger.shouldSend(
                context = applicationContext,
                invoiceId = invoiceId,
                urgency = resolvedUrgency,
                dueDateEpochMs = resolvedDueDateMs
            )
        ) {
            return Result.success()
        }

        val delivered = NotificationHelper.showRentReminderNotification(
            context = applicationContext,
            title = resolvedTitle,
            body = resolvedBody,
            targetRoute = NotificationHelper.ROUTE_TENANT_PAY,
            targetTab = NotificationHelper.TAB_TENANT_INVOICES,
            invoiceId = invoiceId,
            unitId = resolvedUnitId.takeIf { it.isNotBlank() },
            dedupKey = "rent_reminder_${invoiceId}_${resolvedUrgency.name}"
        )

        if (delivered) {
            RentReminderDeliveryLedger.markSent(
                context = applicationContext,
                invoiceId = invoiceId,
                urgency = resolvedUrgency,
                dueDateEpochMs = resolvedDueDateMs
            )
        }

        return Result.success()
    }

    private enum class RemoteVerification {
        PROCEED,
        SUPPRESS
    }

    companion object {
        const val KEY_USER_ID = "user_id"
        const val KEY_INVOICE_ID = "invoice_id"
        const val KEY_UNIT_ID = "unit_id"
        const val KEY_TITLE = "title"
        const val KEY_BODY = "body"
        const val KEY_DUE_DATE_EPOCH_MS = "due_date_epoch_ms"
        const val KEY_STAGE = "stage"
        const val KEY_BYPASS_REMOTE_CHECK_FOR_TEST = "bypass_remote_check_for_test"
    }
}
