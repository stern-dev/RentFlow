package com.example.data.notifications

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.example.data.repository.MpesaCapturePreferencesRepository
import com.example.data.repository.MpesaReceiptRepository
import com.example.domain.finance.MpesaSmsParser
import com.example.domain.model.MpesaCaptureSource
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Play-compliant M-Pesa confirmation notification listener.
 * Users opt in via Android Notification Access Settings without requiring READ_SMS/RECEIVE_SMS.
 * Extracts only Safaricom M-Pesa transaction alerts and stages them into the signed-in user's receipt queue.
 */
class MpesaNotificationListenerService : NotificationListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null || sbn.packageName == packageName) return
        val extras = sbn.notification?.extras ?: return

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
        val body = bigText.ifBlank { text }
        if (body.isBlank()) return

        if (!MpesaSmsParser.looksLikeMpesaNotification(title, body)) return

        serviceScope.launch {
            try {
                val prefs = MpesaCapturePreferencesRepository(applicationContext)
                    .capturePreferences
                    .first()
                if (!prefs.autoCaptureEnabled) return@launch

                val user = FirebaseAuth.getInstance().currentUser ?: return@launch
                if (!user.isEmailVerified) return@launch

                MpesaReceiptRepository(applicationContext)
                    .stageParsedSmsBatch(
                        userId = user.uid,
                        rawText = body,
                        source = MpesaCaptureSource.NOTIFICATION
                    )
            } catch (e: Exception) {
                Log.w("MpesaListener", "Skipped notification staging: ${e.message}")
            }
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}
