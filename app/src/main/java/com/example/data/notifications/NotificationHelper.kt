package com.example.data.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R

data class PushDeepLink(
    val targetRoute: String,
    val requestId: String? = null,
    val unitId: String? = null
)

object NotificationHelper {
    const val CHANNEL_MAINTENANCE = "rentflow_maintenance"
    const val CHANNEL_RENT = "rentflow_rent"
    const val CHANNEL_GENERAL = "rentflow_general"

    const val EXTRA_TARGET_ROUTE = "rentflow_target_route"
    const val EXTRA_MAINTENANCE_REQUEST_ID = "rentflow_maintenance_request_id"
    const val EXTRA_UNIT_ID = "rentflow_unit_id"

    const val ROUTE_TENANT_REPAIRS = "tenant_repairs"
    const val ROUTE_TENANT_PAY = "tenant_pay"
    const val ROUTE_LANDLORD_OPERATIONS = "operations"
    const val ROUTE_LANDLORD_FINANCE = "finance"
    const val ROUTE_STAFF_JOBS = "staff_jobs"
    const val ROUTE_NOTIFICATIONS = "notifications"

    const val EVENT_MAINTENANCE_CREATED = "MAINTENANCE_CREATED"
    const val EVENT_MAINTENANCE_STATUS_CHANGED = "MAINTENANCE_STATUS_CHANGED"
    const val EVENT_RENT_REMINDER = "RENT_REMINDER"

    private const val DEDUP_WINDOW_MS = 30_000L
    private const val MAX_DEDUP_ENTRIES = 64
    private val recentNotifications = LinkedHashMap<String, Long>()

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        val maintId = context.getString(R.string.fcm_maintenance_channel_id)
        val maintName = context.getString(R.string.fcm_maintenance_channel_name)
        val maintChannel = NotificationChannel(
            maintId,
            maintName,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Real-time push alerts for new maintenance requests and repair status updates."
            enableVibration(true)
        }

        val rentId = context.getString(R.string.fcm_rent_channel_id)
        val rentName = context.getString(R.string.fcm_rent_channel_name)
        val rentChannel = NotificationChannel(
            rentId,
            rentName,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Automated rent due dates and overdue balance reminders."
            enableVibration(true)
        }

        val generalId = context.getString(R.string.fcm_general_channel_id)
        val generalName = context.getString(R.string.fcm_general_channel_name)
        val generalChannel = NotificationChannel(
            generalId,
            generalName,
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "General RentFlow workspace alerts."
        }

        manager.createNotificationChannel(maintChannel)
        manager.createNotificationChannel(rentChannel)
        manager.createNotificationChannel(generalChannel)
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun resolveTargetRoute(
        eventType: String?,
        explicitRoute: String?,
        recipientRole: String?
    ): String {
        val cleanExplicit = explicitRoute?.trim().orEmpty()
        if (cleanExplicit in setOf(
                ROUTE_TENANT_REPAIRS,
                ROUTE_TENANT_PAY,
                ROUTE_LANDLORD_OPERATIONS,
                ROUTE_LANDLORD_FINANCE,
                ROUTE_STAFF_JOBS,
                ROUTE_NOTIFICATIONS
            )
        ) {
            return cleanExplicit
        }
        return when (eventType?.trim()?.uppercase()) {
            EVENT_MAINTENANCE_CREATED -> ROUTE_LANDLORD_OPERATIONS
            EVENT_MAINTENANCE_STATUS_CHANGED -> ROUTE_TENANT_REPAIRS
            EVENT_RENT_REMINDER -> when (recipientRole?.trim()?.uppercase()) {
                "LANDLORD", "PROPERTY_MANAGER" -> ROUTE_LANDLORD_FINANCE
                else -> ROUTE_TENANT_PAY
            }
            else -> when (recipientRole?.trim()?.uppercase()) {
                "LANDLORD", "PROPERTY_MANAGER" -> ROUTE_LANDLORD_OPERATIONS
                "MAINTENANCE_STAFF" -> ROUTE_STAFF_JOBS
                "TENANT" -> ROUTE_TENANT_REPAIRS
                else -> ROUTE_NOTIFICATIONS
            }
        }
    }

    fun parseDeepLinkIntent(intent: Intent?): PushDeepLink? {
        if (intent == null) return null
        val route = intent.getStringExtra(EXTRA_TARGET_ROUTE)?.trim().orEmpty()
        val requestId = intent.getStringExtra(EXTRA_MAINTENANCE_REQUEST_ID)?.trim()?.takeIf { it.isNotBlank() }
        val unitId = intent.getStringExtra(EXTRA_UNIT_ID)?.trim()?.takeIf { it.isNotBlank() }
        if (route.isBlank() && requestId == null) return null
        return PushDeepLink(
            targetRoute = route.ifBlank { ROUTE_NOTIFICATIONS },
            requestId = requestId,
            unitId = unitId
        )
    }

    fun buildMaintenanceDedupKey(
        requestId: String?,
        status: String? = null,
        eventType: String? = null,
        title: String? = null,
        fallbackId: String? = null
    ): String? {
        val cleanReqId = requestId?.trim()?.takeIf { it.isNotBlank() }
        if (cleanReqId == null) return fallbackId?.trim()?.takeIf { it.isNotBlank() }

        val cleanStatus = status?.trim()?.takeIf { it.isNotBlank() }?.uppercase()
        if (cleanStatus != null && eventType?.trim()?.uppercase() != EVENT_MAINTENANCE_CREATED) {
            return "${cleanReqId}_$cleanStatus"
        }

        val cleanEvent = eventType?.trim()?.uppercase()
        if (cleanEvent == EVENT_MAINTENANCE_CREATED) {
            return "${cleanReqId}_$EVENT_MAINTENANCE_CREATED"
        }

        val cleanTitle = title?.trim().orEmpty()
        if (cleanTitle.startsWith("New Maintenance Ticket", ignoreCase = true)) {
            return "${cleanReqId}_$EVENT_MAINTENANCE_CREATED"
        }
        if (cleanTitle.startsWith("Maintenance Status:", ignoreCase = true)) {
            val extractedStatus = cleanTitle.substringAfter(":")
                .trim()
                .uppercase()
                .replace(' ', '_')
            if (extractedStatus.isNotBlank()) {
                return "${cleanReqId}_$extractedStatus"
            }
        }
        return "${cleanReqId}_${cleanTitle.ifBlank { fallbackId.orEmpty() }}"
    }

    internal fun shouldDeliverAndMark(dedupKey: String?, nowMs: Long = System.currentTimeMillis()): Boolean {
        if (dedupKey.isNullOrBlank()) return true
        synchronized(recentNotifications) {
            val iter = recentNotifications.entries.iterator()
            while (iter.hasNext()) {
                val entry = iter.next()
                if (nowMs - entry.value > DEDUP_WINDOW_MS) {
                    iter.remove()
                }
            }
            val lastSeen = recentNotifications[dedupKey]
            if (lastSeen != null && (nowMs - lastSeen) <= DEDUP_WINDOW_MS) {
                return false
            }
            if (recentNotifications.size >= MAX_DEDUP_ENTRIES) {
                val firstKey = recentNotifications.keys.firstOrNull()
                if (firstKey != null) recentNotifications.remove(firstKey)
            }
            recentNotifications[dedupKey] = nowMs
            return true
        }
    }

    internal fun clearDedupCacheForTesting() {
        synchronized(recentNotifications) {
            recentNotifications.clear()
        }
    }

    @SuppressLint("MissingPermission")
    fun showMaintenancePushNotification(
        context: Context,
        title: String,
        body: String,
        requestId: String? = null,
        unitId: String? = null,
        targetRoute: String = ROUTE_TENANT_REPAIRS,
        dedupKey: String? = null
    ): Boolean {
        ensureChannels(context)
        if (!hasNotificationPermission(context)) return false
        if (!shouldDeliverAndMark(dedupKey)) return false

        val cleanTitle = title.trim().ifBlank { "RentFlow Maintenance Update" }
        val cleanBody = body.trim().ifBlank { "Tap to view maintenance request details." }

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_TARGET_ROUTE, targetRoute)
            requestId?.takeIf { it.isNotBlank() }?.let { putExtra(EXTRA_MAINTENANCE_REQUEST_ID, it) }
            unitId?.takeIf { it.isNotBlank() }?.let { putExtra(EXTRA_UNIT_ID, it) }
        }

        val notificationId = (dedupKey ?: "${requestId.orEmpty()}_${cleanTitle}_${cleanBody}").hashCode()
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val channelId = context.getString(R.string.fcm_maintenance_channel_id)
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(cleanTitle)
            .setContentText(cleanBody)
            .setStyle(NotificationCompat.BigTextStyle().bigText(cleanBody))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        return try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
            true
        } catch (_: SecurityException) {
            false
        }
    }

    @SuppressLint("MissingPermission")
    fun showRentReminderNotification(
        context: Context,
        title: String,
        body: String,
        targetRoute: String = ROUTE_TENANT_PAY,
        dedupKey: String? = null
    ): Boolean {
        ensureChannels(context)
        if (!hasNotificationPermission(context)) return false
        if (!shouldDeliverAndMark(dedupKey)) return false

        val cleanTitle = title.trim().ifBlank { "RentFlow Rent Reminder" }
        val cleanBody = body.trim().ifBlank { "Tap to view your rent balance and M-Pesa payment details." }

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_TARGET_ROUTE, targetRoute)
        }

        val notificationId = (dedupKey ?: "${cleanTitle}_${cleanBody}").hashCode()
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val channelId = context.getString(R.string.fcm_rent_channel_id)
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(cleanTitle)
            .setContentText(cleanBody)
            .setStyle(NotificationCompat.BigTextStyle().bigText(cleanBody))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        return try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
            true
        } catch (_: SecurityException) {
            false
        }
    }
}

