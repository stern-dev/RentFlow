package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.firebase.configureAppCheck
import com.example.data.notifications.NotificationHelper
import com.example.data.notifications.PushDeepLink
import com.example.data.notifications.RentReminderWorker
import com.example.data.repository.SecurityPreferences
import com.example.data.repository.SecurityPreferencesRepository
import com.example.data.repository.ThemeMode
import com.example.data.repository.ThemePreferencesRepository
import com.example.data.security.BiometricAuthHelper
import com.example.data.security.BiometricAvailability
import com.example.ui.RentFlowApp
import com.example.ui.theme.RentFlowTheme
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {
    private var pendingPushDeepLink by mutableStateOf<PushDeepLink?>(null)
    private var pendingSharedSmsText by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureAppCheck(this, intent)
        NotificationHelper.ensureChannels(this)
        RentReminderWorker.scheduleDaily(applicationContext)
        pendingPushDeepLink = NotificationHelper.parseDeepLinkIntent(intent)
        pendingSharedSmsText = extractSharedSmsText(intent)
        enableEdgeToEdge()
        setContent {
            val themeRepo = remember { ThemePreferencesRepository(applicationContext) }
            val securityRepo = remember { SecurityPreferencesRepository(applicationContext) }
            val themeMode by themeRepo.themeMode.collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)
            val securityPrefs by securityRepo.securityPreferences.collectAsStateWithLifecycle(initialValue = SecurityPreferences())
            val scope = rememberCoroutineScope()
            val lifecycleOwner = LocalLifecycleOwner.current

            var isAppLocked by remember { mutableStateOf(false) }
            var lockError by remember { mutableStateOf<String?>(null) }
            var evaluatedColdStartLock by remember { mutableStateOf(false) }
            var wentToBackground by remember { mutableStateOf(false) }
            val biometricAvailability = remember { BiometricAuthHelper.checkAvailability(this@MainActivity) }

            LaunchedEffect(securityPrefs.biometricLockEnabled) {
                if (!evaluatedColdStartLock && securityPrefs.biometricLockEnabled) {
                    evaluatedColdStartLock = true
                    if (FirebaseAuth.getInstance().currentUser != null) {
                        isAppLocked = true
                    }
                }
            }

            DisposableEffect(lifecycleOwner, securityPrefs) {
                val observer = LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_STOP -> {
                            wentToBackground = true
                            scope.launch { securityRepo.recordBackgroundTimestamp() }
                        }
                        Lifecycle.Event.ON_START -> {
                            if (wentToBackground) {
                                wentToBackground = false
                                val hasSignedInUser = FirebaseAuth.getInstance().currentUser != null
                                if (SecurityPreferencesRepository.shouldRequireUnlock(securityPrefs, hasSignedInUser)) {
                                    isAppLocked = true
                                }
                            }
                        }
                        else -> Unit
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }

            val darkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            RentFlowTheme(darkTheme = darkTheme) {
                RentFlowApp(
                    currentTheme = themeMode,
                    onThemeChange = { mode ->
                        scope.launch { themeRepo.setThemeMode(mode) }
                    },
                    pendingPushDeepLink = pendingPushDeepLink,
                    onConsumePushDeepLink = { pendingPushDeepLink = null },
                    pendingSharedSmsText = pendingSharedSmsText,
                    onConsumeSharedSms = { pendingSharedSmsText = null },
                    securityPrefs = securityPrefs,
                    biometricAvailability = biometricAvailability,
                    isAppLocked = isAppLocked,
                    lockError = lockError,
                    onUnlockRequested = {
                        BiometricAuthHelper.authenticate(
                            activity = this@MainActivity,
                            onSuccess = {
                                isAppLocked = false
                                lockError = null
                                scope.launch { securityRepo.clearBackgroundTimestamp() }
                            },
                            onError = { err ->
                                lockError = err
                            }
                        )
                    },
                    onToggleBiometricLock = { enabled ->
                        if (!enabled) {
                            isAppLocked = false
                            lockError = null
                            scope.launch {
                                securityRepo.setBiometricLockEnabled(false)
                                securityRepo.clearBackgroundTimestamp()
                            }
                        } else if (biometricAvailability != BiometricAvailability.AVAILABLE) {
                            lockError = "Set up a screen lock PIN or fingerprint in Android Settings first."
                        } else {
                            BiometricAuthHelper.authenticate(
                                activity = this@MainActivity,
                                title = "Enable RentFlow App Lock",
                                subtitle = "Verify your identity to protect your account",
                                onSuccess = {
                                    lockError = null
                                    scope.launch {
                                        securityRepo.setBiometricLockEnabled(true)
                                        securityRepo.clearBackgroundTimestamp()
                                    }
                                },
                                onError = { err ->
                                    lockError = err
                                }
                            )
                        }
                    },
                    onSessionTimeoutChange = { minutes ->
                        scope.launch { securityRepo.setSessionTimeoutMinutes(minutes) }
                    },
                    onClearAppLockOnSignOut = {
                        isAppLocked = false
                        lockError = null
                        scope.launch { securityRepo.clearBackgroundTimestamp() }
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        configureAppCheck(this, intent)
        NotificationHelper.parseDeepLinkIntent(intent)?.let { deepLink ->
            pendingPushDeepLink = deepLink
        }
        extractSharedSmsText(intent)?.let { shared ->
            pendingSharedSmsText = shared
        }
    }

    private fun extractSharedSmsText(intent: Intent?): String? {
        if (intent?.action != Intent.ACTION_SEND) return null
        val type = intent.type ?: return null
        if (!type.startsWith("text/")) return null
        return intent.getStringExtra(Intent.EXTRA_TEXT)?.trim()?.takeIf { it.isNotBlank() }
    }
}
