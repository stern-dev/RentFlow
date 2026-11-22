package com.example.data.notifications

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class RentFlowMessagingService : FirebaseMessagingService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "FCM registration token refreshed")
        serviceScope.launch {
            FcmTokenRepository(applicationContext).registerRefreshedToken(token)
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val data = message.data
        val eventType = data["eventType"]?.trim()?.uppercase()
        val defaultTitle = when (eventType) {
            NotificationHelper.EVENT_INVOICE_GENERATED -> "New Rent Invoice Ready"
            NotificationHelper.EVENT_PAYMENT_CONFIRMED -> "Rent Payment Confirmed"
            NotificationHelper.EVENT_RENT_REMINDER -> "RentFlow Rent Reminder"
            else -> "RentFlow Update"
        }
        val defaultBody = when (eventType) {
            NotificationHelper.EVENT_INVOICE_GENERATED -> "Open RentFlow to view your new invoice and pay via M-Pesa."
            NotificationHelper.EVENT_PAYMENT_CONFIRMED -> "Your rent payment has been verified. Tap to view your receipt."
            NotificationHelper.EVENT_RENT_REMINDER -> "Open RentFlow to review your rent due date."
            else -> "Open RentFlow to inspect the latest update."
        }
        val title = data["title"]?.takeIf { it.isNotBlank() }
            ?: message.notification?.title
            ?: defaultTitle
        val body = data["body"]?.takeIf { it.isNotBlank() }
            ?: message.notification?.body
            ?: defaultBody

        val requestId = data["requestId"]?.takeIf { it.isNotBlank() }
        val invoiceId = data["invoiceId"]?.takeIf { it.isNotBlank() }
        val paymentId = data["paymentId"]?.takeIf { it.isNotBlank() }
        val checklistId = data["checklistId"]?.takeIf { it.isNotBlank() }
        val settlementId = data["settlementId"]?.takeIf { it.isNotBlank() }
        val leaseId = data["leaseId"]?.takeIf { it.isNotBlank() }
        val unitId = data["unitId"]?.takeIf { it.isNotBlank() }
        val status = data["status"]?.takeIf { it.isNotBlank() }
        val targetRoute = NotificationHelper.resolveTargetRoute(
            eventType = eventType,
            explicitRoute = data["targetRoute"],
            recipientRole = data["recipientRole"]
        )
        val targetTab = NotificationHelper.resolveTargetTab(
            eventType = eventType,
            explicitTab = data["targetTab"]
        )

        when (eventType) {
            NotificationHelper.EVENT_INVOICE_GENERATED -> {
                NotificationHelper.showBillingPushNotification(
                    context = applicationContext,
                    title = title,
                    body = body,
                    targetRoute = targetRoute,
                    targetTab = targetTab ?: NotificationHelper.TAB_TENANT_INVOICES,
                    invoiceId = invoiceId,
                    unitId = unitId,
                    dedupKey = NotificationHelper.buildInvoiceGeneratedDedupKey(invoiceId, message.messageId)
                )
            }
            NotificationHelper.EVENT_PAYMENT_CONFIRMED -> {
                NotificationHelper.showBillingPushNotification(
                    context = applicationContext,
                    title = title,
                    body = body,
                    targetRoute = targetRoute,
                    targetTab = targetTab ?: NotificationHelper.TAB_TENANT_PAYMENTS,
                    invoiceId = invoiceId,
                    paymentId = paymentId,
                    unitId = unitId,
                    dedupKey = NotificationHelper.buildPaymentConfirmedDedupKey(paymentId, message.messageId)
                )
            }
            NotificationHelper.EVENT_RENT_REMINDER -> {
                NotificationHelper.showBillingPushNotification(
                    context = applicationContext,
                    title = title,
                    body = body,
                    targetRoute = targetRoute,
                    targetTab = targetTab,
                    invoiceId = invoiceId,
                    unitId = unitId,
                    dedupKey = invoiceId ?: message.messageId
                )
            }
            NotificationHelper.EVENT_CHECKLIST_PENDING_REVIEW,
            NotificationHelper.EVENT_CHECKLIST_ACKNOWLEDGED,
            NotificationHelper.EVENT_CHECKLIST_DISPUTED,
            NotificationHelper.EVENT_SETTLEMENT_ISSUED,
            NotificationHelper.EVENT_SETTLEMENT_APPROVED,
            NotificationHelper.EVENT_SETTLEMENT_DISPUTED,
            NotificationHelper.EVENT_SETTLEMENT_SETTLED -> {
                NotificationHelper.showHandoverPushNotification(
                    context = applicationContext,
                    title = title,
                    body = body,
                    targetRoute = targetRoute,
                    targetTab = targetTab,
                    leaseId = leaseId,
                    checklistId = checklistId,
                    settlementId = settlementId,
                    unitId = unitId,
                    dedupKey = NotificationHelper.buildHandoverDedupKey(
                        eventType = eventType,
                        checklistId = checklistId,
                        settlementId = settlementId,
                        status = status,
                        fallbackId = message.messageId
                    )
                )
            }
            else -> {
                val dedupKey = NotificationHelper.buildMaintenanceDedupKey(
                    requestId = requestId,
                    status = status,
                    eventType = eventType,
                    title = title,
                    fallbackId = message.messageId
                )
                NotificationHelper.showMaintenancePushNotification(
                    context = applicationContext,
                    title = title,
                    body = body,
                    requestId = requestId,
                    unitId = unitId,
                    targetRoute = targetRoute,
                    dedupKey = dedupKey
                )
            }
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "RentFlowFCM"
    }
}
