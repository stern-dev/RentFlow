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
        val title = data["title"]?.takeIf { it.isNotBlank() }
            ?: message.notification?.title
            ?: "RentFlow Maintenance Update"
        val body = data["body"]?.takeIf { it.isNotBlank() }
            ?: message.notification?.body
            ?: "Open RentFlow to inspect the latest repair update."

        val eventType = data["eventType"]
        val requestId = data["requestId"]?.takeIf { it.isNotBlank() }
        val unitId = data["unitId"]?.takeIf { it.isNotBlank() }
        val status = data["status"]?.takeIf { it.isNotBlank() }
        val targetRoute = NotificationHelper.resolveTargetRoute(
            eventType = eventType,
            explicitRoute = data["targetRoute"],
            recipientRole = data["recipientRole"]
        )
        val dedupKey = NotificationHelper.buildMaintenanceDedupKey(
            requestId = requestId,
            status = status,
            eventType = eventType,
            title = title,
            fallbackId = message.messageId
        )

        if (eventType?.trim()?.uppercase() == NotificationHelper.EVENT_RENT_REMINDER) {
            NotificationHelper.showRentReminderNotification(
                context = applicationContext,
                title = title,
                body = body,
                targetRoute = targetRoute,
                dedupKey = data["invoiceId"] ?: message.messageId
            )
        } else {
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

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "RentFlowFCM"
    }
}
