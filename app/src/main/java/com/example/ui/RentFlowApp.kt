package com.example.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Surface
import com.example.data.notifications.FcmTokenRepository
import com.example.data.notifications.NotificationHelper
import com.example.data.notifications.PushDeepLink
import com.example.data.repository.FinanceRepository
import com.example.data.repository.HandoverRepository
import com.example.data.repository.MpesaCapturePreferencesRepository
import com.example.data.repository.MpesaReceiptRepository
import com.example.data.repository.OperationsRepository
import com.example.data.repository.PaymentMethodRepository
import com.example.data.repository.PropertyRepository
import com.example.data.repository.SecurityPreferences
import com.example.data.repository.ThemeMode
import com.example.data.repository.UserOrgRepository
import com.example.data.repository.UserRepository
import com.example.data.security.BiometricAvailability
import com.example.domain.finance.MpesaReceiptMatcher
import com.example.domain.finance.MpesaSmsParser
import com.example.domain.model.InviteStatus
import com.example.domain.model.MpesaCaptureSource
import com.example.domain.model.MpesaReceipt
import com.example.domain.model.NotificationType
import com.example.domain.model.Organization
import com.example.domain.model.PaymentChannelType
import com.example.domain.model.UserProfile
import com.example.domain.model.UserRole
import com.example.domain.security.PermissionPolicy
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.ErrorBannerCard
import com.example.ui.components.LoadingStateBox
import com.example.ui.screens.AdminPortalScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.ConversationsScreen
import com.example.ui.screens.EmailVerificationScreen
import com.example.ui.screens.LandlordDashboardScreen
import com.example.ui.screens.LandlordFinanceScreen
import com.example.ui.screens.LandlordOperationsScreen
import com.example.ui.screens.LandlordPortfolioScreen
import com.example.ui.screens.MpesaReconcileScreen
import com.example.ui.screens.NotificationsScreen
import com.example.ui.screens.PaymentMethodSelectionScreen
import com.example.ui.screens.ProfileSettingsScreen
import com.example.ui.screens.PropertyDiscoveryScreen
import com.example.ui.screens.PropertyMpesaSuggestion
import com.example.ui.screens.RoleOnboardingScreen
import com.example.ui.screens.StaffMaintenanceQueueScreen
import com.example.ui.screens.TenantDashboardScreen
import com.example.ui.screens.TenantMaintenanceScreen
import com.example.ui.screens.TenantPaymentsScreen
import com.example.ui.viewmodel.AdminViewModel
import com.example.ui.viewmodel.AuthSessionState
import com.example.ui.viewmodel.AuthViewModel
import com.example.ui.viewmodel.DiscoveryViewModel
import com.example.ui.viewmodel.LandlordViewModel
import com.example.ui.viewmodel.MpesaReconcileViewModel
import com.example.ui.viewmodel.OperationsViewModel
import com.example.ui.viewmodel.PaymentMethodViewModel
import com.example.ui.viewmodel.TenantViewModel
import com.example.ui.viewmodel.UiState

private data class NavItem(
    val route: String,
    val label: String,
    val icon: ImageVector
)

@Composable
fun RentFlowApp(
    currentTheme: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
    pendingPushDeepLink: PushDeepLink? = null,
    onConsumePushDeepLink: () -> Unit = {},
    pendingSharedSmsText: String? = null,
    onConsumeSharedSms: () -> Unit = {},
    securityPrefs: SecurityPreferences = SecurityPreferences(),
    biometricAvailability: BiometricAvailability = BiometricAvailability.AVAILABLE,
    isAppLocked: Boolean = false,
    lockError: String? = null,
    onUnlockRequested: () -> Unit = {},
    onToggleBiometricLock: (Boolean) -> Unit = {},
    onSessionTimeoutChange: (Int) -> Unit = {},
    onClearAppLockOnSignOut: () -> Unit = {}
) {
    val context = LocalContext.current
    val userRepository = remember(context) { UserRepository(context) }
    val userOrgRepo = remember(context) { UserOrgRepository(context) }

    val sessionAuthViewModel: AuthViewModel = viewModel(
        key = "session_auth",
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return AuthViewModel(userRepository, userOrgRepo, currentUserId = null) as T
            }
        }
    )

    val authSessionState by sessionAuthViewModel.authSessionState.collectAsStateWithLifecycle()
    val firebaseUser by sessionAuthViewModel.currentUser.collectAsStateWithLifecycle()
    val isEmailVerified by sessionAuthViewModel.isEmailVerified.collectAsStateWithLifecycle()
    val authInfoMessage by sessionAuthViewModel.authInfoMessage.collectAsStateWithLifecycle()
    val verificationBusy by sessionAuthViewModel.busy.collectAsStateWithLifecycle()
    val verificationError by sessionAuthViewModel.authError.collectAsStateWithLifecycle()
    var browsingPublicListings by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        sessionAuthViewModel.attemptAutoSignIn(context)
    }

    val user = firebaseUser
    if (user == null) {
        if (browsingPublicListings) {
            BackHandler { browsingPublicListings = false }
            val publicDiscoveryVm: DiscoveryViewModel = viewModel(
                key = "public_discovery",
                factory = object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        return DiscoveryViewModel(PropertyRepository(context), null) as T
                    }
                }
            )
            PropertyDiscoveryScreen(
                viewModel = publicDiscoveryVm,
                currentUser = null,
                isPublicMode = true,
                onBackToSignIn = { browsingPublicListings = false },
                onRequireAuthToApply = { browsingPublicListings = false }
            )
        } else {
            AuthScreen(
                onAuthSuccess = { browsingPublicListings = false },
                onBrowsePublicListings = { browsingPublicListings = true },
                isSigningIn = authSessionState is AuthSessionState.SigningIn,
                externalError = (authSessionState as? AuthSessionState.Error)?.message,
                infoMessage = authInfoMessage,
                onSignInClick = {
                    sessionAuthViewModel.signInWithGoogle(context) {
                        browsingPublicListings = false
                    }
                },
                onEmailSignIn = { email, password ->
                    sessionAuthViewModel.signInWithEmail(email, password) {
                        browsingPublicListings = false
                    }
                },
                onEmailRegister = { fullName, email, password ->
                    sessionAuthViewModel.registerWithEmail(fullName, email, password) {
                        browsingPublicListings = false
                    }
                },
                onSendPasswordReset = { email ->
                    sessionAuthViewModel.sendPasswordReset(email)
                },
                onClearMessages = { sessionAuthViewModel.clearAuthMessages() }
            )
        }
    } else if (!isEmailVerified) {
        EmailVerificationScreen(
            email = user.email.orEmpty(),
            isBusy = verificationBusy,
            errorText = verificationError,
            infoText = authInfoMessage,
            onCheckVerified = { sessionAuthViewModel.refreshEmailVerification() },
            onResendEmail = { sessionAuthViewModel.resendVerificationEmail() },
            onSignOut = { sessionAuthViewModel.signOut(context) }
        )
    } else {
        key(user.uid) {
            AuthenticatedRentFlowShell(
                uid = user.uid,
                userRepository = userRepository,
                userOrgRepo = userOrgRepo,
                currentTheme = currentTheme,
                onThemeChange = onThemeChange,
                pendingPushDeepLink = pendingPushDeepLink,
                onConsumePushDeepLink = onConsumePushDeepLink,
                pendingSharedSmsText = pendingSharedSmsText,
                onConsumeSharedSms = onConsumeSharedSms,
                securityPrefs = securityPrefs,
                biometricAvailability = biometricAvailability,
                isAppLocked = isAppLocked,
                lockError = lockError,
                onUnlockRequested = onUnlockRequested,
                onToggleBiometricLock = onToggleBiometricLock,
                onSessionTimeoutChange = onSessionTimeoutChange,
                onSignOut = {
                    onClearAppLockOnSignOut()
                    sessionAuthViewModel.signOut(context)
                }
            )
        }
    }
}

@Composable
private fun AuthenticatedRentFlowShell(
    uid: String,
    userRepository: UserRepository,
    userOrgRepo: UserOrgRepository,
    currentTheme: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
    pendingPushDeepLink: PushDeepLink?,
    onConsumePushDeepLink: () -> Unit,
    pendingSharedSmsText: String?,
    onConsumeSharedSms: () -> Unit,
    securityPrefs: SecurityPreferences,
    biometricAvailability: BiometricAvailability,
    isAppLocked: Boolean,
    lockError: String?,
    onUnlockRequested: () -> Unit,
    onToggleBiometricLock: (Boolean) -> Unit,
    onSessionTimeoutChange: (Int) -> Unit,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current

    val propertyRepo = remember(context) { PropertyRepository(context) }
    val financeRepo = remember(context) { FinanceRepository(context) }
    val opsRepo = remember(context) { OperationsRepository(context) }
    val handoverRepo = remember(context) { HandoverRepository(context) }
    val paymentMethodRepo = remember(context) { PaymentMethodRepository(context) }
    val mpesaReceiptRepo = remember(context) { MpesaReceiptRepository(context) }
    val mpesaPrefsRepo = remember(context) { MpesaCapturePreferencesRepository(context) }

    val authViewModel: AuthViewModel = viewModel(
        key = "auth_$uid",
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return AuthViewModel(userRepository, userOrgRepo, uid) as T
            }
        }
    )

    val profileState by authViewModel.userProfile.collectAsStateWithLifecycle()
    val orgsState by authViewModel.organizations.collectAsStateWithLifecycle()
    val incomingInvitesState by authViewModel.incomingInvites.collectAsStateWithLifecycle()
    val busy by authViewModel.busy.collectAsStateWithLifecycle()
    val authFeedback by authViewModel.feedback.collectAsStateWithLifecycle()

    when (val st = profileState) {
        is UiState.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                LoadingStateBox("Loading your RentFlow profile...")
            }
        }
        is UiState.Error -> {
            Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    ErrorBannerCard(st.message)
                    OutlinedButton(onClick = onSignOut) { Text("Sign Out") }
                }
            }
        }
        is UiState.Success -> {
            val profile = st.data
            if (profile == null) {
                val detectedInvites = (incomingInvitesState as? UiState.Success)?.data ?: emptyList()
                RoleOnboardingScreen(
                    isBusy = busy,
                    errorText = authFeedback,
                    pendingInvites = detectedInvites,
                    onSubmit = { name, email, phone, roles, activeRole, orgName, invitesToAccept ->
                        authViewModel.completeOnboarding(name, email, phone, roles, activeRole, orgName, invitesToAccept)
                    },
                    onSignOut = onSignOut
                )
            } else if (profile.suspended) {
                Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    EmptyStateCard(
                        title = "Account Suspended",
                        message = "Your RentFlow account (${profile.email}) is currently suspended by platform administration. Contact support for assistance.",
                        ctaLabel = "Sign Out",
                        onCtaClick = onSignOut
                    )
                }
            } else {
                val organizations = (orgsState as? UiState.Success)?.data ?: emptyList()
                Box(modifier = Modifier.fillMaxSize()) {
                    RoleRoutedWorkspace(
                        uid = uid,
                        profile = profile,
                        organizations = organizations,
                        authViewModel = authViewModel,
                        userOrgRepo = userOrgRepo,
                        propertyRepo = propertyRepo,
                        financeRepo = financeRepo,
                        opsRepo = opsRepo,
                        handoverRepo = handoverRepo,
                        paymentMethodRepo = paymentMethodRepo,
                        mpesaReceiptRepo = mpesaReceiptRepo,
                        mpesaPrefsRepo = mpesaPrefsRepo,
                        currentTheme = currentTheme,
                        onThemeChange = onThemeChange,
                        pendingPushDeepLink = pendingPushDeepLink,
                        onConsumePushDeepLink = onConsumePushDeepLink,
                        pendingSharedSmsText = pendingSharedSmsText,
                        onConsumeSharedSms = onConsumeSharedSms,
                        securityPrefs = securityPrefs,
                        biometricAvailability = biometricAvailability,
                        onToggleBiometricLock = onToggleBiometricLock,
                        onSessionTimeoutChange = onSessionTimeoutChange,
                        onSignOut = onSignOut
                    )
                    if (isAppLocked) {
                        BiometricAppLockOverlay(
                            userName = profile.fullName,
                            lockError = lockError,
                            onUnlockClick = onUnlockRequested,
                            onSignOutClick = onSignOut
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoleRoutedWorkspace(
    uid: String,
    profile: UserProfile,
    organizations: List<Organization>,
    authViewModel: AuthViewModel,
    userOrgRepo: UserOrgRepository,
    propertyRepo: PropertyRepository,
    financeRepo: FinanceRepository,
    opsRepo: OperationsRepository,
    handoverRepo: HandoverRepository,
    paymentMethodRepo: PaymentMethodRepository,
    mpesaReceiptRepo: MpesaReceiptRepository,
    mpesaPrefsRepo: MpesaCapturePreferencesRepository,
    currentTheme: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
    pendingPushDeepLink: PushDeepLink?,
    onConsumePushDeepLink: () -> Unit,
    pendingSharedSmsText: String?,
    onConsumeSharedSms: () -> Unit,
    securityPrefs: SecurityPreferences,
    biometricAvailability: BiometricAvailability,
    onToggleBiometricLock: (Boolean) -> Unit,
    onSessionTimeoutChange: (Int) -> Unit,
    onSignOut: () -> Unit
) {
    val discoveryVm: DiscoveryViewModel = viewModel(
        key = "disc_$uid",
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = DiscoveryViewModel(propertyRepo, uid) as T
        }
    )
    val landlordVm: LandlordViewModel = viewModel(
        key = "landlord_$uid",
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                LandlordViewModel(propertyRepo, financeRepo, opsRepo, uid, handoverRepo = handoverRepo) as T
        }
    )
    val resolvedOrgId = remember(profile.activeOrgId, organizations) {
        profile.activeOrgId?.takeIf { id -> organizations.any { it.id == id } }
            ?: organizations.firstOrNull()?.id
    }
    LaunchedEffect(resolvedOrgId) {
        landlordVm.setActiveOrgId(resolvedOrgId)
    }
    val activeOrgId by landlordVm.activeOrgId.collectAsStateWithLifecycle()
    val activeOrganization = remember(activeOrgId, organizations) {
        organizations.firstOrNull { it.id == activeOrgId } ?: organizations.firstOrNull()
    }
    LaunchedEffect(activeOrganization?.id) {
        authViewModel.setActiveInviteOrgId(activeOrganization?.id)
    }
    val tenantVm: TenantViewModel = viewModel(
        key = "tenant_$uid",
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                TenantViewModel(propertyRepo, financeRepo, opsRepo, uid, handoverRepo = handoverRepo) as T
        }
    )
    val opsVm: OperationsViewModel = viewModel(
        key = "ops_$uid",
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = OperationsViewModel(opsRepo, uid) as T
        }
    )
    val adminVm: AdminViewModel = viewModel(
        key = "admin_$uid",
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = AdminViewModel(userOrgRepo, uid, authViewModel.isBootstrappedAdmin) as T
        }
    )
    val paymentMethodVm: PaymentMethodViewModel = viewModel(
        key = "pm_$uid",
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = PaymentMethodViewModel(paymentMethodRepo, uid) as T
        }
    )
    val mpesaReconcileVm: MpesaReconcileViewModel = viewModel(
        key = "mpesa_$uid",
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                MpesaReconcileViewModel(mpesaReceiptRepo, financeRepo, mpesaPrefsRepo, uid) as T
        }
    )

    val activeRole = profile.activeRoleEnum()
    val navItems = remember(activeRole) {
        when (activeRole) {
            UserRole.LANDLORD, UserRole.PROPERTY_MANAGER -> listOf(
                NavItem("overview", "Overview", Icons.Filled.Dashboard),
                NavItem("portfolio", "Portfolio", Icons.Filled.Apartment),
                NavItem("finance", "Finance", Icons.Filled.AccountBalanceWallet),
                NavItem("operations", "Ops", Icons.Filled.Build),
                NavItem("more", "More", Icons.Filled.Person)
            )
            UserRole.TENANT -> listOf(
                NavItem("tenant_home", "Home", Icons.Filled.Home),
                NavItem("tenant_pay", "Rent & Pay", Icons.Filled.Payments),
                NavItem("tenant_repairs", "Repairs", Icons.Filled.Build),
                NavItem("discover", "Discover", Icons.Filled.Search),
                NavItem("inbox_profile", "Account", Icons.Filled.Person)
            )
            UserRole.MAINTENANCE_STAFF -> listOf(
                NavItem("staff_jobs", "Jobs", Icons.Filled.Build),
                NavItem("messages", "Messages", Icons.AutoMirrored.Filled.Chat),
                NavItem("notifications", "Alerts", Icons.Filled.Notifications),
                NavItem("profile", "Profile", Icons.Filled.Person)
            )
            UserRole.ADMIN -> listOf(
                NavItem("admin_hub", "Admin", Icons.Filled.AdminPanelSettings),
                NavItem("admin_audit", "Audit", Icons.Filled.History),
                NavItem("admin_issues", "Issues", Icons.Filled.ReportProblem),
                NavItem("profile", "Profile", Icons.Filled.Person)
            )
        }
    }

    var currentRoute by remember(activeRole) { mutableStateOf(navItems.first().route) }
    var showNotificationsOverlay by remember { mutableStateOf(false) }
    var showPaymentMethodsOverlay by remember { mutableStateOf(false) }
    var showMpesaReconcileOverlay by remember { mutableStateOf(false) }
    var prefilledTenantReceipt by remember { mutableStateOf<MpesaReceipt?>(null) }
    var activeTenantPayDeepLink by remember { mutableStateOf<PushDeepLink?>(null) }
    var activeTenantHomeDeepLink by remember { mutableStateOf<PushDeepLink?>(null) }
    var activePortfolioDeepLink by remember { mutableStateOf<PushDeepLink?>(null) }
    var showCreateOrgDialog by remember { mutableStateOf(false) }
    var triggerAddProperty by remember { mutableStateOf(false) }
    var roleMenuExpanded by remember { mutableStateOf(false) }
    var moreSubTab by remember { mutableStateOf(0) } // 0=Profile, 1=Discover, 2=Messages
    var tenantAccountSubTab by remember { mutableStateOf(0) } // 0=Profile, 1=Messages

    val context = LocalContext.current
    val fcmTokenRepo = remember(context) { FcmTokenRepository(context.applicationContext) }
    var requestedNotificationPermission by remember(uid) { mutableStateOf(false) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { /* Permission status updates reactively via NotificationHelper.hasNotificationPermission */ }

    LaunchedEffect(uid, profile.notifyMaintenance, profile.notifyRentDue) {
        NotificationHelper.ensureChannels(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            (profile.notifyMaintenance || profile.notifyRentDue) &&
            !requestedNotificationPermission &&
            !NotificationHelper.hasNotificationPermission(context)
        ) {
            requestedNotificationPermission = true
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(uid, profile.notifyMaintenance, profile.notifyRentDue, profile.activeRole) {
        fcmTokenRepo.syncCurrentToken(profile)
    }

    LaunchedEffect(pendingPushDeepLink, activeRole) {
        val deepLink = pendingPushDeepLink ?: return@LaunchedEffect
        val route = deepLink.targetRoute.trim()
        if (route.isNotBlank()) {
            when (route) {
                NotificationHelper.ROUTE_NOTIFICATIONS -> showNotificationsOverlay = true
                NotificationHelper.ROUTE_TENANT_HOME -> {
                    showNotificationsOverlay = false
                    if (activeRole == UserRole.TENANT) {
                        currentRoute = "tenant_home"
                        activeTenantHomeDeepLink = deepLink
                    }
                }
                NotificationHelper.ROUTE_LANDLORD_PORTFOLIO -> {
                    showNotificationsOverlay = false
                    if (activeRole == UserRole.LANDLORD || activeRole == UserRole.PROPERTY_MANAGER) {
                        currentRoute = "portfolio"
                        activePortfolioDeepLink = deepLink
                    }
                }
                NotificationHelper.ROUTE_TENANT_PAY -> {
                    showNotificationsOverlay = false
                    if (activeRole == UserRole.TENANT) {
                        currentRoute = "tenant_pay"
                        activeTenantPayDeepLink = deepLink
                    }
                }
                NotificationHelper.ROUTE_LANDLORD_FINANCE -> {
                    showNotificationsOverlay = false
                    if (activeRole == UserRole.LANDLORD || activeRole == UserRole.PROPERTY_MANAGER) {
                        currentRoute = "finance"
                    }
                }
                NotificationHelper.ROUTE_TENANT_REPAIRS,
                NotificationHelper.ROUTE_LANDLORD_OPERATIONS,
                NotificationHelper.ROUTE_STAFF_JOBS -> {
                    showNotificationsOverlay = false
                    currentRoute = when (activeRole) {
                        UserRole.TENANT -> "tenant_repairs"
                        UserRole.LANDLORD, UserRole.PROPERTY_MANAGER -> "operations"
                        UserRole.MAINTENANCE_STAFF -> "staff_jobs"
                        UserRole.ADMIN -> "admin_hub"
                    }
                }
            }
        }
        onConsumePushDeepLink()
    }

    val notificationsState by opsVm.notifications.collectAsStateWithLifecycle()
    var initializedNotifSnapshot by remember(uid) { mutableStateOf(false) }
    val seenNotifIds = remember(uid) { mutableSetOf<String>() }

    LaunchedEffect(notificationsState, profile.notifyMaintenance, profile.notifyRentDue, activeRole) {
        val items = (notificationsState as? UiState.Success)?.data ?: return@LaunchedEffect
        if (!initializedNotifSnapshot) {
            seenNotifIds.addAll(items.map { it.id })
            initializedNotifSnapshot = true
            return@LaunchedEffect
        }
        val newlyArrived = items.filter { it.id !in seenNotifIds }
        seenNotifIds.addAll(items.map { it.id })

        if (profile.notifyMaintenance) {
            val roleMaintenanceRoute = when (activeRole) {
                UserRole.TENANT -> NotificationHelper.ROUTE_TENANT_REPAIRS
                UserRole.LANDLORD, UserRole.PROPERTY_MANAGER -> NotificationHelper.ROUTE_LANDLORD_OPERATIONS
                UserRole.MAINTENANCE_STAFF -> NotificationHelper.ROUTE_STAFF_JOBS
                UserRole.ADMIN -> NotificationHelper.ROUTE_NOTIFICATIONS
            }
            newlyArrived
                .filter { !it.read && it.typeEnum() == NotificationType.MAINTENANCE }
                .forEach { notif ->
                    val dedupKey = NotificationHelper.buildMaintenanceDedupKey(
                        requestId = notif.relatedEntityId,
                        title = notif.title,
                        fallbackId = notif.id
                    )
                    NotificationHelper.showMaintenancePushNotification(
                        context = context,
                        title = notif.title,
                        body = notif.body,
                        requestId = notif.relatedEntityId,
                        targetRoute = roleMaintenanceRoute,
                        dedupKey = dedupKey
                    )
                }
        }

        if (profile.notifyRentDue && activeRole == UserRole.TENANT) {
            newlyArrived
                .filter { !it.read && it.typeEnum() == NotificationType.INVOICE }
                .forEach { notif ->
                    val dedupKey = NotificationHelper.buildInvoiceGeneratedDedupKey(
                        invoiceId = notif.relatedEntityId,
                        fallbackId = notif.id
                    )
                    NotificationHelper.showBillingPushNotification(
                        context = context,
                        title = notif.title,
                        body = notif.body,
                        targetRoute = NotificationHelper.ROUTE_TENANT_PAY,
                        targetTab = NotificationHelper.TAB_TENANT_INVOICES,
                        invoiceId = notif.relatedEntityId,
                        dedupKey = dedupKey
                    )
                }

            newlyArrived
                .filter {
                    !it.read &&
                        it.typeEnum() == NotificationType.PAYMENT &&
                        it.title.startsWith("Payment Verified", ignoreCase = true)
                }
                .forEach { notif ->
                    val dedupKey = NotificationHelper.buildPaymentConfirmedDedupKey(
                        paymentId = notif.relatedEntityId,
                        fallbackId = notif.id
                    )
                    NotificationHelper.showBillingPushNotification(
                        context = context,
                        title = notif.title,
                        body = notif.body,
                        targetRoute = NotificationHelper.ROUTE_TENANT_PAY,
                        targetTab = NotificationHelper.TAB_TENANT_PAYMENTS,
                        paymentId = notif.relatedEntityId,
                        dedupKey = dedupKey
                    )
                }
        }

        if (profile.notifyRentDue) {
            newlyArrived
                .filter { !it.read && it.typeEnum() == NotificationType.LEASE }
                .forEach { notif ->
                    val entityId = notif.relatedEntityId?.trim().takeIf { !it.isNullOrBlank() }
                    val isChecklist = notif.title.contains("Checklist", ignoreCase = true)
                    val isSettlement = notif.title.contains("Deposit", ignoreCase = true)
                    if (isChecklist || isSettlement) {
                        val isLandlordSide = activeRole == UserRole.LANDLORD || activeRole == UserRole.PROPERTY_MANAGER
                        val eventHint = when {
                            isChecklist && notif.title.contains("Signed Off", ignoreCase = true) -> NotificationHelper.EVENT_CHECKLIST_ACKNOWLEDGED
                            isChecklist && notif.title.contains("Discrepancy", ignoreCase = true) -> NotificationHelper.EVENT_CHECKLIST_DISPUTED
                            isChecklist -> NotificationHelper.EVENT_CHECKLIST_PENDING_REVIEW
                            notif.title.contains("Approved", ignoreCase = true) -> NotificationHelper.EVENT_SETTLEMENT_APPROVED
                            notif.title.contains("Review Requested", ignoreCase = true) -> NotificationHelper.EVENT_SETTLEMENT_DISPUTED
                            notif.title.contains("Settled", ignoreCase = true) -> NotificationHelper.EVENT_SETTLEMENT_SETTLED
                            else -> NotificationHelper.EVENT_SETTLEMENT_ISSUED
                        }
                        val checklistId = if (isChecklist) entityId else null
                        val settlementId = if (isSettlement) entityId else null
                        NotificationHelper.showHandoverPushNotification(
                            context = context,
                            title = notif.title,
                            body = notif.body,
                            targetRoute = if (isLandlordSide) NotificationHelper.ROUTE_LANDLORD_PORTFOLIO else NotificationHelper.ROUTE_TENANT_HOME,
                            targetTab = if (isLandlordSide) NotificationHelper.TAB_LANDLORD_LEASES else null,
                            checklistId = checklistId,
                            settlementId = settlementId,
                            dedupKey = NotificationHelper.buildHandoverDedupKey(
                                eventType = eventHint,
                                checklistId = checklistId,
                                settlementId = settlementId,
                                fallbackId = notif.id
                            )
                        )
                    }
                }
        }
    }

    val unreadCount by opsVm.unreadNotificationCount.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val authFb by authViewModel.feedback.collectAsStateWithLifecycle()
    val discFb by discoveryVm.feedback.collectAsStateWithLifecycle()
    val landFb by landlordVm.feedback.collectAsStateWithLifecycle()
    val tenFb by tenantVm.feedback.collectAsStateWithLifecycle()
    val opsFb by opsVm.feedback.collectAsStateWithLifecycle()
    val admFb by adminVm.feedback.collectAsStateWithLifecycle()
    val pmFb by paymentMethodVm.feedback.collectAsStateWithLifecycle()
    val mpesaFb by mpesaReconcileVm.feedback.collectAsStateWithLifecycle()

    LaunchedEffect(authFb, discFb, landFb, tenFb, opsFb, admFb, pmFb, mpesaFb) {
        val msg = authFb ?: discFb ?: landFb ?: tenFb ?: opsFb ?: admFb ?: pmFb ?: mpesaFb
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            authViewModel.clearFeedback()
            discoveryVm.clearFeedback()
            landlordVm.clearFeedback()
            tenantVm.clearFeedback()
            opsViewModelClear(opsVm)
            adminVm.clearFeedback()
            paymentMethodVm.clearFeedback()
            mpesaReconcileVm.clearFeedback()
        }
    }

    LaunchedEffect(pendingSharedSmsText, activeRole) {
        val shared = pendingSharedSmsText?.trim().orEmpty()
        if (shared.isNotBlank()) {
            mpesaReconcileVm.stageSmsText(shared, MpesaCaptureSource.SHARE_INTENT) { staged ->
                if (activeRole == UserRole.LANDLORD || activeRole == UserRole.PROPERTY_MANAGER) {
                    showNotificationsOverlay = false
                    showPaymentMethodsOverlay = false
                    showMpesaReconcileOverlay = true
                } else if (activeRole == UserRole.TENANT) {
                    showNotificationsOverlay = false
                    showPaymentMethodsOverlay = false
                    showMpesaReconcileOverlay = false
                    currentRoute = "tenant_pay"
                    prefilledTenantReceipt = staged.firstOrNull()
                }
            }
            onConsumeSharedSms()
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    var mpesaListenerEnabled by remember {
        mutableStateOf(MpesaCapturePreferencesRepository.isNotificationListenerEnabled(context))
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                mpesaListenerEnabled = MpesaCapturePreferencesRepository.isNotificationListenerEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (showNotificationsOverlay) {
        BackHandler { showNotificationsOverlay = false }
    }
    if (showPaymentMethodsOverlay) {
        BackHandler { showPaymentMethodsOverlay = false }
    }
    if (showMpesaReconcileOverlay) {
        BackHandler { showMpesaReconcileOverlay = false }
    }

    val paymentMethodsState by paymentMethodVm.methods.collectAsStateWithLifecycle()
    val selectedPaymentMethodId by paymentMethodVm.selectedMethodId.collectAsStateWithLifecycle()
    val paymentMethodBusy by paymentMethodVm.busy.collectAsStateWithLifecycle()
    val savedMethodsCount = ((paymentMethodsState as? UiState.Success)?.data ?: emptyList()).size

    val tenantActiveUnit by tenantVm.activeUnit.collectAsStateWithLifecycle()
    val tenantActiveLease by tenantVm.activeLease.collectAsStateWithLifecycle()
    val landlordPropertiesState by landlordVm.properties.collectAsStateWithLifecycle()

    val propertyMpesaSuggestions = remember(activeRole, tenantActiveUnit, tenantActiveLease, landlordPropertiesState, activeOrganization) {
        buildList {
            if (activeRole == UserRole.TENANT) {
                val unit = tenantActiveUnit
                val unitNum = tenantActiveLease?.unitNumber ?: unit?.unitNumber.orEmpty()
                if (!unit?.mpesaPaybill.isNullOrBlank()) {
                    add(
                        PropertyMpesaSuggestion(
                            title = "${unit!!.propertyName} Paybill",
                            channelType = PaymentChannelType.MPESA_PAYBILL,
                            paybillNumber = unit.mpesaPaybill,
                            accountReference = MpesaSmsParser.buildMpesaAccountReference(unit.mpesaAccountPrefix, unitNum)
                        )
                    )
                }
                if (!unit?.mpesaTill.isNullOrBlank()) {
                    add(
                        PropertyMpesaSuggestion(
                            title = "${unit!!.propertyName} Till",
                            channelType = PaymentChannelType.MPESA_TILL,
                            tillNumber = unit.mpesaTill
                        )
                    )
                }
            } else if (activeRole == UserRole.LANDLORD || activeRole == UserRole.PROPERTY_MANAGER) {
                val props = (landlordPropertiesState as? UiState.Success)?.data ?: emptyList()
                props.forEach { prop ->
                    if (!prop.mpesaPaybill.isNullOrBlank()) {
                        add(
                            PropertyMpesaSuggestion(
                                title = "${prop.name} Paybill",
                                channelType = PaymentChannelType.MPESA_PAYBILL,
                                paybillNumber = prop.mpesaPaybill,
                                accountReference = prop.mpesaAccountPrefix?.takeIf { it.isNotBlank() } ?: "RENT"
                            )
                        )
                    }
                    if (!prop.mpesaTill.isNullOrBlank()) {
                        add(
                            PropertyMpesaSuggestion(
                                title = "${prop.name} Till",
                                channelType = PaymentChannelType.MPESA_TILL,
                                tillNumber = prop.mpesaTill
                            )
                        )
                    }
                }
                if (isEmpty() && activeOrganization != null) {
                    if (!activeOrganization.mpesaPaybill.isNullOrBlank()) {
                        add(
                            PropertyMpesaSuggestion(
                                title = "${activeOrganization.name} Paybill",
                                channelType = PaymentChannelType.MPESA_PAYBILL,
                                paybillNumber = activeOrganization.mpesaPaybill,
                                accountReference = "RENT"
                            )
                        )
                    }
                    if (!activeOrganization.mpesaTill.isNullOrBlank()) {
                        add(
                            PropertyMpesaSuggestion(
                                title = "${activeOrganization.name} Till",
                                channelType = PaymentChannelType.MPESA_TILL,
                                tillNumber = activeOrganization.mpesaTill
                            )
                        )
                    }
                }
            }
        }
    }

    val tenantLeases by tenantVm.leases.collectAsStateWithLifecycle()
    val landlordLeases by landlordVm.leases.collectAsStateWithLifecycle()
    val knownLeases = when (activeRole) {
        UserRole.TENANT -> (tenantLeases as? UiState.Success)?.data ?: emptyList()
        else -> (landlordLeases as? UiState.Success)?.data ?: emptyList()
    }

    val mpesaReceiptsState by mpesaReconcileVm.receipts.collectAsStateWithLifecycle()
    val mpesaCapturePrefs by mpesaReconcileVm.capturePrefs.collectAsStateWithLifecycle()
    val mpesaReconcileBusy by mpesaReconcileVm.busy.collectAsStateWithLifecycle()
    val landlordPaymentsState by landlordVm.payments.collectAsStateWithLifecycle()
    val landlordInvoicesState by landlordVm.invoices.collectAsStateWithLifecycle()
    val tenantPaymentsState by tenantVm.payments.collectAsStateWithLifecycle()

    val allMpesaReceipts = (mpesaReceiptsState as? UiState.Success)?.data ?: emptyList()
    val landlordPaymentList = (landlordPaymentsState as? UiState.Success)?.data ?: emptyList()
    val landlordInvoiceList = (landlordInvoicesState as? UiState.Success)?.data ?: emptyList()
    val landlordLeaseList = (landlordLeases as? UiState.Success)?.data ?: emptyList()
    val tenantPaymentList = (tenantPaymentsState as? UiState.Success)?.data ?: emptyList()

    val stagedMpesaEvaluations = remember(allMpesaReceipts, landlordPaymentList, landlordLeaseList, activeRole) {
        if (activeRole == UserRole.LANDLORD || activeRole == UserRole.PROPERTY_MANAGER) {
            MpesaReceiptMatcher.evaluateAllForLandlord(allMpesaReceipts, landlordPaymentList, landlordLeaseList)
        } else {
            emptyList()
        }
    }
    val tenantAutofillCandidates = remember(allMpesaReceipts, tenantPaymentList, activeRole) {
        if (activeRole == UserRole.TENANT) {
            MpesaReceiptMatcher.filterTenantAutofillCandidates(allMpesaReceipts, tenantPaymentList)
        } else {
            emptyList()
        }
    }

    val incomingInvitesState by authViewModel.incomingInvites.collectAsStateWithLifecycle()
    val orgInvitesState by authViewModel.orgInvites.collectAsStateWithLifecycle()
    val incomingInvites = (incomingInvitesState as? UiState.Success)?.data ?: emptyList()
    val orgInvites = (orgInvitesState as? UiState.Success)?.data ?: emptyList()
    val pendingInviteCount = remember(incomingInvites) {
        incomingInvites.count { it.statusEnum() == InviteStatus.PENDING }
    }

    val availableRoles = remember(profile, authViewModel.isBootstrappedAdmin) {
        PermissionPolicy.assignableRolesForProfile(profile, authViewModel.isBootstrappedAdmin)
    }
    val canCreateProperty = activeRole == UserRole.LANDLORD || activeRole == UserRole.ADMIN

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = when {
                                    showMpesaReconcileOverlay -> "M-Pesa SMS Auto-Verify"
                                    showPaymentMethodsOverlay -> "Payment Methods"
                                    showNotificationsOverlay -> "Notifications"
                                    else -> "RentFlow"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${activeRole.label}${activeOrganization?.let { " • ${it.name}" } ?: ""}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                    actions = {
                        Box {
                            TextButton(
                                onClick = { roleMenuExpanded = true },
                                modifier = Modifier.testTag("switch_role_button")
                            ) {
                                BadgedBox(
                                    badge = {
                                        if (pendingInviteCount > 0) {
                                            Badge { Text(pendingInviteCount.toString()) }
                                        }
                                    }
                                ) {
                                    Text("Switch Role", maxLines = 1)
                                }
                            }
                            DropdownMenu(expanded = roleMenuExpanded, onDismissRequest = { roleMenuExpanded = false }) {
                                availableRoles.forEach { role ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                if (role == activeRole) "${role.label} (Active)" else role.label
                                            )
                                        },
                                        onClick = {
                                            roleMenuExpanded = false
                                            showNotificationsOverlay = false
                                            showMpesaReconcileOverlay = false
                                            authViewModel.switchRole(role, profile.roles, profile.fullName)
                                        }
                                    )
                                }
                                if (availableRoles.size <= 1) {
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                "New roles require an invite or Admin grant",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        },
                                        enabled = false,
                                        onClick = {}
                                    )
                                }
                                if (pendingInviteCount > 0) {
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                "Review $pendingInviteCount pending invite(s) in Profile",
                                                color = MaterialTheme.colorScheme.secondary
                                            )
                                        },
                                        onClick = {
                                            roleMenuExpanded = false
                                            showNotificationsOverlay = false
                                            moreSubTab = 0
                                            tenantAccountSubTab = 0
                                            currentRoute = when (activeRole) {
                                                UserRole.LANDLORD, UserRole.PROPERTY_MANAGER -> "more"
                                                UserRole.TENANT -> "inbox_profile"
                                                UserRole.MAINTENANCE_STAFF, UserRole.ADMIN -> "profile"
                                            }
                                        }
                                    )
                                }
                            }
                        }
                        IconButton(
                            onClick = { showNotificationsOverlay = !showNotificationsOverlay },
                            modifier = Modifier.testTag("notifications_bell_button")
                        ) {
                            BadgedBox(
                                badge = {
                                    if (unreadCount > 0) {
                                        Badge { Text(unreadCount.toString()) }
                                    }
                                }
                            ) {
                                Icon(Icons.Filled.Notifications, contentDescription = "Notifications")
                            }
                        }
                    }
                )
            },
            bottomBar = {
                if (!useRail && !showNotificationsOverlay && !showPaymentMethodsOverlay && !showMpesaReconcileOverlay) {
                    NavigationBar {
                        navItems.forEach { item ->
                            NavigationBarItem(
                                selected = currentRoute == item.route,
                                onClick = { currentRoute = item.route },
                                icon = { Icon(item.icon, contentDescription = item.label) },
                                label = { Text(item.label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                if (useRail && !showNotificationsOverlay && !showPaymentMethodsOverlay && !showMpesaReconcileOverlay) {
                    NavigationRail {
                        Spacer(Modifier.height(8.dp))
                        navItems.forEach { item ->
                            NavigationRailItem(
                                selected = currentRoute == item.route,
                                onClick = { currentRoute = item.route },
                                icon = { Icon(item.icon, contentDescription = item.label) },
                                label = { Text(item.label, maxLines = 1) }
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier.weight(1f).fillMaxSize(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Box(modifier = Modifier.fillMaxSize().widthIn(max = 960.dp)) {
                        if (showMpesaReconcileOverlay) {
                            MpesaReconcileScreen(
                                evaluations = stagedMpesaEvaluations,
                                leases = landlordLeaseList,
                                autoCaptureEnabled = mpesaCapturePrefs.autoCaptureEnabled,
                                listenerEnabled = mpesaListenerEnabled,
                                isBusy = mpesaReconcileBusy,
                                onToggleAutoCapture = { mpesaReconcileVm.setAutoCaptureEnabled(it) },
                                onOpenNotificationSettings = {
                                    MpesaCapturePreferencesRepository.openNotificationListenerSettings(context)
                                },
                                onStageSmsBatch = { rawBatch, onDone ->
                                    mpesaReconcileVm.stageSmsText(rawBatch, MpesaCaptureSource.MANUAL_PASTE) {
                                        onDone()
                                    }
                                },
                                onApproveMatched = { eval ->
                                    mpesaReconcileVm.approveMatchedReceipt(
                                        evaluation = eval,
                                        invoices = landlordInvoiceList,
                                        actorName = profile.fullName
                                    )
                                },
                                onReconcileToLease = { receipt, lease ->
                                    mpesaReconcileVm.reconcileUnmatchedToLease(
                                        receipt = receipt,
                                        lease = lease,
                                        invoices = landlordInvoiceList,
                                        actorName = profile.fullName
                                    )
                                },
                                onMarkAlreadyVerified = { eval ->
                                    mpesaReconcileVm.markAlreadyVerifiedReconciled(eval)
                                },
                                onDismissReceipt = { receipt ->
                                    mpesaReconcileVm.dismissReceipt(receipt)
                                },
                                onReopenReceipt = { receipt ->
                                    mpesaReconcileVm.reopenReceipt(receipt)
                                },
                                onBack = { showMpesaReconcileOverlay = false }
                            )
                        } else if (showPaymentMethodsOverlay) {
                            PaymentMethodSelectionScreen(
                                methodsState = paymentMethodsState,
                                selectedMethodId = selectedPaymentMethodId,
                                userRole = activeRole,
                                propertySuggestions = propertyMpesaSuggestions,
                                isBusy = paymentMethodBusy,
                                onBack = { showPaymentMethodsOverlay = false },
                                onSelectMethod = { paymentMethodVm.selectMethod(it.id) },
                                onConfirmUseMethod = { chosen ->
                                    paymentMethodVm.selectMethod(chosen.id)
                                    showPaymentMethodsOverlay = false
                                },
                                onSaveMethod = { existingId, label, channel, scope, pb, acct, till, phone, bankName, bankAcct, makeDef, smsVer, smsRef, onDone ->
                                    paymentMethodVm.saveMethod(
                                        existingMethodId = existingId,
                                        label = label,
                                        channelType = channel,
                                        roleScope = scope,
                                        paybillNumber = pb,
                                        accountReference = acct,
                                        tillNumber = till,
                                        phoneNumber = phone,
                                        bankName = bankName,
                                        bankAccountNumber = bankAcct,
                                        defaultMethod = makeDef,
                                        smsVerified = smsVer,
                                        smsSampleRef = smsRef
                                    ) {
                                        onDone()
                                    }
                                },
                                onSetDefaultMethod = { paymentMethodVm.setDefaultMethod(it) },
                                onDeleteMethod = { paymentMethodVm.deleteMethod(it) }
                            )
                        } else if (showNotificationsOverlay) {
                            NotificationsScreen(opsViewModel = opsVm)
                        } else {
                            when (currentRoute) {
                                "overview" -> LandlordDashboardScreen(
                                    viewModel = landlordVm,
                                    organizations = organizations,
                                    onNavigatePortfolio = { currentRoute = "portfolio" },
                                    onNavigateFinance = { currentRoute = "finance" },
                                    onNavigateOperations = { currentRoute = "operations" },
                                    onOpenCreateOrg = { if (canCreateProperty) showCreateOrgDialog = true else currentRoute = "portfolio" },
                                    onOpenAddProperty = {
                                        if (!canCreateProperty) currentRoute = "portfolio"
                                        else if (organizations.isEmpty()) showCreateOrgDialog = true
                                        else {
                                            triggerAddProperty = true
                                            currentRoute = "portfolio"
                                        }
                                    },
                                    canCreateProperty = canCreateProperty
                                )
                                "portfolio" -> LandlordPortfolioScreen(
                                    viewModel = landlordVm,
                                    currentUser = profile,
                                    organizations = organizations,
                                    onCreateOrgRequest = { showCreateOrgDialog = true },
                                    openAddPropertyImmediately = triggerAddProperty,
                                    onConsumeAddPropertyTrigger = { triggerAddProperty = false },
                                    activeOrganization = activeOrganization,
                                    orgInvites = orgInvites,
                                    onCreateRoleInvite = { org, email, role, propertyIds, onSuccess ->
                                        authViewModel.createRoleInvite(
                                            org = org,
                                            inviteeEmail = email,
                                            role = role,
                                            inviterName = profile.fullName,
                                            propertyIds = propertyIds,
                                            onSuccess = onSuccess
                                        )
                                    },
                                    onRevokeRoleInvite = { invite ->
                                        authViewModel.revokeRoleInvite(invite, profile.fullName)
                                    },
                                    isRecentlyAuthenticated = { authViewModel.isRecentlySignedIn() },
                                    onReauthenticate = { ctx -> authViewModel.verifyRecentGoogleAuth(ctx) },
                                    deepLinkTarget = activePortfolioDeepLink,
                                    onConsumeDeepLinkTarget = { activePortfolioDeepLink = null }
                                )
                                "finance" -> LandlordFinanceScreen(
                                    viewModel = landlordVm,
                                    currentUser = profile,
                                    paymentMethodViewModel = paymentMethodVm,
                                    onOpenPaymentMethods = { showPaymentMethodsOverlay = true },
                                    stagedMpesaEvaluations = stagedMpesaEvaluations,
                                    mpesaReconcileBusy = mpesaReconcileBusy,
                                    onOpenMpesaReconcile = { showMpesaReconcileOverlay = true },
                                    onStageMpesaSmsText = { rawSms, onDone ->
                                        mpesaReconcileVm.stageSmsText(rawSms, MpesaCaptureSource.MANUAL_PASTE) {
                                            onDone()
                                        }
                                    },
                                    isRecentlyAuthenticated = { authViewModel.isRecentlySignedIn() },
                                    onReauthenticate = { ctx -> authViewModel.verifyRecentGoogleAuth(ctx) }
                                )
                                "operations" -> LandlordOperationsScreen(
                                    landlordViewModel = landlordVm,
                                    opsViewModel = opsVm,
                                    currentUser = profile
                                )
                                "more" -> Column(modifier = Modifier.fillMaxSize()) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                                    ) {
                                        FilterChip(selected = moreSubTab == 0, onClick = { moreSubTab = 0 }, label = { Text("Profile & Settings") })
                                        FilterChip(selected = moreSubTab == 1, onClick = { moreSubTab = 1 }, label = { Text("Public Listings") })
                                    }
                                    if (moreSubTab == 0) {
                                        ProfileSettingsScreen(
                                            authViewModel = authViewModel,
                                            opsViewModel = opsVm,
                                            currentUser = profile,
                                            currentTheme = currentTheme,
                                            onThemeChange = onThemeChange,
                                            onSignOut = onSignOut,
                                            incomingInvites = incomingInvites,
                                            onAcceptInvite = { invite -> authViewModel.acceptRoleInvite(invite, profile) },
                                            onDeclineInvite = { invite -> authViewModel.declineRoleInvite(invite, profile.fullName) },
                                            securityPrefs = securityPrefs,
                                            biometricAvailability = biometricAvailability,
                                            onToggleBiometricLock = onToggleBiometricLock,
                                            onSessionTimeoutChange = onSessionTimeoutChange,
                                            savedMethodsCount = savedMethodsCount,
                                            onOpenPaymentMethods = { showPaymentMethodsOverlay = true },
                                            mpesaAutoCaptureEnabled = mpesaCapturePrefs.autoCaptureEnabled,
                                            mpesaListenerEnabled = mpesaListenerEnabled,
                                            onToggleMpesaAutoCapture = { mpesaReconcileVm.setAutoCaptureEnabled(it) },
                                            onOpenMpesaListenerSettings = {
                                                MpesaCapturePreferencesRepository.openNotificationListenerSettings(context)
                                            },
                                            onOpenMpesaReconcile = { showMpesaReconcileOverlay = true }
                                        )
                                    } else {
                                        PropertyDiscoveryScreen(viewModel = discoveryVm, currentUser = profile, isPublicMode = false)
                                    }
                                }

                                "tenant_home" -> TenantDashboardScreen(
                                    viewModel = tenantVm,
                                    currentUser = profile,
                                    onNavigatePayRent = { currentRoute = "tenant_pay" },
                                    onNavigateMaintenance = { currentRoute = "tenant_repairs" },
                                    onNavigateDiscovery = { currentRoute = "discover" },
                                    deepLinkTarget = activeTenantHomeDeepLink,
                                    onConsumeDeepLinkTarget = { activeTenantHomeDeepLink = null }
                                )
                                "tenant_pay" -> TenantPaymentsScreen(
                                    viewModel = tenantVm,
                                    currentUser = profile,
                                    paymentMethodViewModel = paymentMethodVm,
                                    onOpenPaymentMethods = { showPaymentMethodsOverlay = true },
                                    detectedMpesaReceipts = tenantAutofillCandidates,
                                    initialPrefilledReceipt = prefilledTenantReceipt,
                                    onConsumePrefilledReceipt = { prefilledTenantReceipt = null },
                                    deepLinkTarget = activeTenantPayDeepLink,
                                    onConsumeDeepLinkTarget = { activeTenantPayDeepLink = null },
                                    onReceiptSubmittedForPayment = { receipt, paymentId, leaseId ->
                                        mpesaReconcileVm.markTenantReceiptSubmitted(receipt, paymentId, leaseId)
                                    },
                                    onDismissDetectedReceipt = { receipt ->
                                        mpesaReconcileVm.dismissReceipt(receipt)
                                    }
                                )
                                "tenant_repairs" -> TenantMaintenanceScreen(viewModel = tenantVm, currentUser = profile)
                                "discover" -> PropertyDiscoveryScreen(viewModel = discoveryVm, currentUser = profile, isPublicMode = false)
                                "inbox_profile" -> Column(modifier = Modifier.fillMaxSize()) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                                    ) {
                                        FilterChip(selected = tenantAccountSubTab == 0, onClick = { tenantAccountSubTab = 0 }, label = { Text("Profile") })
                                        FilterChip(selected = tenantAccountSubTab == 1, onClick = { tenantAccountSubTab = 1 }, label = { Text("Messages") })
                                    }
                                    if (tenantAccountSubTab == 0) {
                                        ProfileSettingsScreen(
                                            authViewModel = authViewModel,
                                            opsViewModel = opsVm,
                                            currentUser = profile,
                                            currentTheme = currentTheme,
                                            onThemeChange = onThemeChange,
                                            onSignOut = onSignOut,
                                            incomingInvites = incomingInvites,
                                            onAcceptInvite = { invite -> authViewModel.acceptRoleInvite(invite, profile) },
                                            onDeclineInvite = { invite -> authViewModel.declineRoleInvite(invite, profile.fullName) },
                                            securityPrefs = securityPrefs,
                                            biometricAvailability = biometricAvailability,
                                            onToggleBiometricLock = onToggleBiometricLock,
                                            onSessionTimeoutChange = onSessionTimeoutChange,
                                            savedMethodsCount = savedMethodsCount,
                                            onOpenPaymentMethods = { showPaymentMethodsOverlay = true },
                                            mpesaAutoCaptureEnabled = mpesaCapturePrefs.autoCaptureEnabled,
                                            mpesaListenerEnabled = mpesaListenerEnabled,
                                            onToggleMpesaAutoCapture = { mpesaReconcileVm.setAutoCaptureEnabled(it) },
                                            onOpenMpesaListenerSettings = {
                                                MpesaCapturePreferencesRepository.openNotificationListenerSettings(context)
                                            }
                                        )
                                    } else {
                                        ConversationsScreen(opsViewModel = opsVm, currentUser = profile, knownLeases = knownLeases)
                                    }
                                }

                                "staff_jobs" -> StaffMaintenanceQueueScreen(opsViewModel = opsVm)
                                "messages" -> ConversationsScreen(opsViewModel = opsVm, currentUser = profile, knownLeases = knownLeases)
                                "notifications" -> NotificationsScreen(opsViewModel = opsVm)
                                "profile" -> ProfileSettingsScreen(
                                    authViewModel = authViewModel,
                                    opsViewModel = opsVm,
                                    currentUser = profile,
                                    currentTheme = currentTheme,
                                    onThemeChange = onThemeChange,
                                    onSignOut = onSignOut,
                                    incomingInvites = incomingInvites,
                                    onAcceptInvite = { invite -> authViewModel.acceptRoleInvite(invite, profile) },
                                    onDeclineInvite = { invite -> authViewModel.declineRoleInvite(invite, profile.fullName) },
                                    securityPrefs = securityPrefs,
                                    biometricAvailability = biometricAvailability,
                                    onToggleBiometricLock = onToggleBiometricLock,
                                    onSessionTimeoutChange = onSessionTimeoutChange,
                                    savedMethodsCount = savedMethodsCount,
                                    onOpenPaymentMethods = { showPaymentMethodsOverlay = true },
                                    mpesaAutoCaptureEnabled = mpesaCapturePrefs.autoCaptureEnabled,
                                    mpesaListenerEnabled = mpesaListenerEnabled,
                                    onToggleMpesaAutoCapture = { mpesaReconcileVm.setAutoCaptureEnabled(it) },
                                    onOpenMpesaListenerSettings = {
                                        MpesaCapturePreferencesRepository.openNotificationListenerSettings(context)
                                    }
                                )

                                "admin_hub" -> AdminPortalScreen(
                                    adminViewModel = adminVm,
                                    initialSection = 0,
                                    actorName = profile.fullName,
                                    isRecentlyAuthenticated = { authViewModel.isRecentlySignedIn() },
                                    onReauthenticate = { ctx -> authViewModel.verifyRecentGoogleAuth(ctx) }
                                )
                                "admin_issues" -> AdminPortalScreen(
                                    adminViewModel = adminVm,
                                    initialSection = 2,
                                    actorName = profile.fullName,
                                    isRecentlyAuthenticated = { authViewModel.isRecentlySignedIn() },
                                    onReauthenticate = { ctx -> authViewModel.verifyRecentGoogleAuth(ctx) }
                                )
                                "admin_audit" -> AdminPortalScreen(
                                    adminViewModel = adminVm,
                                    initialSection = 3,
                                    actorName = profile.fullName,
                                    isRecentlyAuthenticated = { authViewModel.isRecentlySignedIn() },
                                    onReauthenticate = { ctx -> authViewModel.verifyRecentGoogleAuth(ctx) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateOrgDialog) {
        var orgName by remember { mutableStateOf("") }
        var dueDay by remember { mutableStateOf("5") }
        var err by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { showCreateOrgDialog = false },
            title = { Text("Create Property Organization") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (!err.isNullOrBlank()) ErrorBannerCard(err!!)
                    OutlinedTextField(
                        value = orgName,
                        onValueChange = { orgName = it },
                        label = { Text("Portfolio / Company Name") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = dueDay,
                        onValueChange = { dueDay = it },
                        label = { Text("Default Rent Due Day (1-28)") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val d = dueDay.toLongOrNull() ?: 5L
                    if (orgName.trim().length < 2) err = "Enter a valid organization name"
                    else {
                        authViewModel.createOrganization(orgName, d.coerceIn(1L, 28L))
                        showCreateOrgDialog = false
                    }
                }) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { showCreateOrgDialog = false }) { Text("Cancel") } }
        )
    }
}

private fun opsViewModelClear(opsVm: OperationsViewModel) {
    opsVm.clearFeedback()
}

@Composable
fun BiometricAppLockOverlay(
    userName: String,
    lockError: String?,
    onUnlockClick: () -> Unit,
    onSignOutClick: () -> Unit
) {
    LaunchedEffect(Unit) {
        onUnlockClick()
    }
    Surface(
        modifier = Modifier.fillMaxSize().testTag("biometric_lock_overlay"),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 32.dp)
                .widthIn(max = 420.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = "RentFlow locked",
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = "RentFlow is Locked",
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Verify your fingerprint, face, or device PIN to resume as $userName.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!lockError.isNullOrBlank()) {
                Spacer(Modifier.height(12.dp))
                ErrorBannerCard(lockError)
            }
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onUnlockClick,
                modifier = Modifier.fillMaxWidth().testTag("biometric_unlock_button")
            ) {
                Icon(Icons.Filled.Fingerprint, contentDescription = null)
                Spacer(Modifier.padding(horizontal = 4.dp))
                Text("Unlock RentFlow")
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = onSignOutClick,
                modifier = Modifier.fillMaxWidth().testTag("biometric_sign_out_button")
            ) {
                Text("Sign Out Instead")
            }
        }
    }
}

