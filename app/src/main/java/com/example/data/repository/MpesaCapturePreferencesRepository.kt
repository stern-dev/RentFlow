package com.example.data.repository

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.notifications.MpesaNotificationListenerService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.mpesaCaptureDataStore by preferencesDataStore(name = "rentflow_mpesa_capture_prefs")

data class MpesaCapturePreferences(
    val autoCaptureEnabled: Boolean = true
)

class MpesaCapturePreferencesRepository(private val context: Context) {
    private val autoCaptureKey = booleanPreferencesKey("auto_capture_enabled")

    val capturePreferences: Flow<MpesaCapturePreferences> = context.mpesaCaptureDataStore.data.map { prefs ->
        MpesaCapturePreferences(
            autoCaptureEnabled = prefs[autoCaptureKey] ?: true
        )
    }

    suspend fun setAutoCaptureEnabled(enabled: Boolean) {
        context.mpesaCaptureDataStore.edit { prefs ->
            prefs[autoCaptureKey] = enabled
        }
    }

    companion object {
        fun isNotificationListenerEnabled(context: Context): Boolean {
            return try {
                val componentName = ComponentName(context, MpesaNotificationListenerService::class.java)
                val flat = Settings.Secure.getString(
                    context.contentResolver,
                    "enabled_notification_listeners"
                ) ?: return false
                flat.split(":").any { entry ->
                    val parsed = ComponentName.unflattenFromString(entry)
                    parsed == componentName
                }
            } catch (_: Exception) {
                false
            }
        }

        fun openNotificationListenerSettings(context: Context): Boolean {
            return try {
                val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                true
            } catch (_: Exception) {
                false
            }
        }
    }
}
