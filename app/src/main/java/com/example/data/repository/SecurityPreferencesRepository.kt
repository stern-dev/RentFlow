package com.example.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.securityDataStore by preferencesDataStore(name = "rentflow_security_prefs")

enum class SessionTimeoutOption(val minutes: Int, val label: String) {
    IMMEDIATE(0, "Immediate"),
    ONE_MINUTE(1, "1 min"),
    FIVE_MINUTES(5, "5 min"),
    FIFTEEN_MINUTES(15, "15 min")
}

data class SecurityPreferences(
    val biometricLockEnabled: Boolean = false,
    val sessionTimeoutMinutes: Int = SessionTimeoutOption.FIVE_MINUTES.minutes,
    val lastBackgroundEpochMs: Long = 0L
)

class SecurityPreferencesRepository(private val context: Context) {
    private val biometricLockKey = booleanPreferencesKey("biometric_lock_enabled")
    private val timeoutMinutesKey = intPreferencesKey("session_timeout_minutes")
    private val lastBackgroundKey = longPreferencesKey("last_background_epoch_ms")

    val securityPreferences: Flow<SecurityPreferences> = context.securityDataStore.data.map { prefs ->
        SecurityPreferences(
            biometricLockEnabled = prefs[biometricLockKey] ?: false,
            sessionTimeoutMinutes = prefs[timeoutMinutesKey] ?: SessionTimeoutOption.FIVE_MINUTES.minutes,
            lastBackgroundEpochMs = prefs[lastBackgroundKey] ?: 0L
        )
    }

    suspend fun setBiometricLockEnabled(enabled: Boolean) {
        context.securityDataStore.edit { prefs ->
            prefs[biometricLockKey] = enabled
        }
    }

    suspend fun setSessionTimeoutMinutes(minutes: Int) {
        context.securityDataStore.edit { prefs ->
            prefs[timeoutMinutesKey] = minutes
        }
    }

    suspend fun recordBackgroundTimestamp(epochMs: Long = System.currentTimeMillis()) {
        context.securityDataStore.edit { prefs ->
            prefs[lastBackgroundKey] = epochMs
        }
    }

    suspend fun clearBackgroundTimestamp() {
        context.securityDataStore.edit { prefs ->
            prefs[lastBackgroundKey] = 0L
        }
    }

    companion object {
        fun shouldRequireUnlock(
            prefs: SecurityPreferences,
            isAuthenticated: Boolean,
            nowEpochMs: Long = System.currentTimeMillis()
        ): Boolean {
            if (!isAuthenticated || !prefs.biometricLockEnabled) return false
            if (prefs.lastBackgroundEpochMs <= 0L) return true
            if (prefs.sessionTimeoutMinutes <= 0) return true
            val elapsedMs = (nowEpochMs - prefs.lastBackgroundEpochMs).coerceAtLeast(0L)
            return elapsedMs >= prefs.sessionTimeoutMinutes * 60_000L
        }
    }
}
