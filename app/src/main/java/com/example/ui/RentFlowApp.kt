package com.example.ui

import androidx.activity.compose.BackHandler
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.repository.FinanceRepository
import com.example.data.repository.OperationsRepository
import com.example.data.repository.PropertyRepository
import com.example.data.repository.ThemeMode
import com.example.data.repository.UserOrgRepository
import com.example.data.repository.UserRepository
import com.example.domain.model.Organization
import com.example.domain.model.UserProfile
import com.example.domain.model.UserRole
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.ErrorBannerCard
import com.example.ui.components.LoadingStateBox
import com.example.ui.screens.AdminPortalScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.ConversationsScreen
import com.example.ui.screens.LandlordDashboardScreen
import com.example.ui.screens.LandlordFinanceScreen
import com.example.ui.screens.LandlordOperationsScreen
import com.example.ui.screens.LandlordPortfolioScreen
import com.example.ui.screens.NotificationsScreen
import com.example.ui.screens.ProfileSettingsScreen
import com.example.ui.screens.PropertyDiscoveryScreen
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
import com.example.ui.viewmodel.OperationsViewModel
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
    onThemeChange: (ThemeMode) -> Unit
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
                onSignInClick = {
                    sessionAuthViewModel.signInWithGoogle(context) {
                        browsingPublicListings = false
                    }
                }
            )
        }
    } else {
        key(user.uid) {
            AuthenticatedRentFlowShell(
                uid = user.uid,
                userRepository = userRepository,
                userOrgRepo = userOrgRepo,
                currentTheme = currentTheme,
                onThemeChange = onThemeChange,
                onSignOut = { sessionAuthViewModel.signOut(context) }
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
    onSignOut: () -> Unit
) {
    val context = LocalContext.current

    val propertyRepo = remember(context) { PropertyRepository(context) }
    val financeRepo = remember(context) { FinanceRepository(context) }
    val opsRepo = remember(context) { OperationsRepository(context) }

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
                RoleOnboardingScreen(
                    isBusy = busy,
                    errorText = authFeedback,
                    onSubmit = { name, email, phone, roles, activeRole, orgName ->
                        authViewModel.completeOnboarding(name, email, phone, roles, activeRole, orgName)
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
                RoleRoutedWorkspace(
                    uid = uid,
                    profile = profile,
                    organizations = organizations,
                    authViewModel = authViewModel,
                    userOrgRepo = userOrgRepo,
                    propertyRepo = propertyRepo,
                    financeRepo = financeRepo,
                    opsRepo = opsRepo,
                    currentTheme = currentTheme,
                    onThemeChange = onThemeChange,
                    onSignOut = onSignOut
                )
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
    currentTheme: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
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
            override fun <T : ViewModel> create(modelClass: Class<T>): T = LandlordViewModel(propertyRepo, financeRepo, opsRepo, uid) as T
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
    val tenantVm: TenantViewModel = viewModel(
        key = "tenant_$uid",
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = TenantViewModel(propertyRepo, financeRepo, opsRepo, uid) as T
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
    var showCreateOrgDialog by remember { mutableStateOf(false) }
    var triggerAddProperty by remember { mutableStateOf(false) }
    var roleMenuExpanded by remember { mutableStateOf(false) }
    var moreSubTab by remember { mutableStateOf(0) } // 0=Profile, 1=Discover, 2=Messages
    var tenantAccountSubTab by remember { mutableStateOf(0) } // 0=Profile, 1=Messages

    val unreadCount by opsVm.unreadNotificationCount.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val authFb by authViewModel.feedback.collectAsStateWithLifecycle()
    val discFb by discoveryVm.feedback.collectAsStateWithLifecycle()
    val landFb by landlordVm.feedback.collectAsStateWithLifecycle()
    val tenFb by tenantVm.feedback.collectAsStateWithLifecycle()
    val opsFb by opsVm.feedback.collectAsStateWithLifecycle()
    val admFb by adminVm.feedback.collectAsStateWithLifecycle()

    LaunchedEffect(authFb, discFb, landFb, tenFb, opsFb, admFb) {
        val msg = authFb ?: discFb ?: landFb ?: tenFb ?: opsFb ?: admFb
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            authViewModel.clearFeedback()
            discoveryVm.clearFeedback()
            landlordVm.clearFeedback()
            tenantVm.clearFeedback()
            opsViewModelClear(opsVm)
            adminVm.clearFeedback()
        }
    }

    if (showNotificationsOverlay) {
        BackHandler { showNotificationsOverlay = false }
    }

    val tenantLeases by tenantVm.leases.collectAsStateWithLifecycle()
    val landlordLeases by landlordVm.leases.collectAsStateWithLifecycle()
    val knownLeases = when (activeRole) {
        UserRole.TENANT -> (tenantLeases as? UiState.Success)?.data ?: emptyList()
        else -> (landlordLeases as? UiState.Success)?.data ?: emptyList()
    }

    val availableRoles = remember(profile.roles, authViewModel.isBootstrappedAdmin) {
        buildList {
            add(UserRole.LANDLORD)
            add(UserRole.PROPERTY_MANAGER)
            add(UserRole.TENANT)
            add(UserRole.MAINTENANCE_STAFF)
            if (UserRole.ADMIN.name in profile.roles || authViewModel.isBootstrappedAdmin) add(UserRole.ADMIN)
        }
    }

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
                                text = if (showNotificationsOverlay) "Notifications" else "RentFlow",
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
                            TextButton(onClick = { roleMenuExpanded = true }) {
                                Text("Switch Role", maxLines = 1)
                            }
                            DropdownMenu(expanded = roleMenuExpanded, onDismissRequest = { roleMenuExpanded = false }) {
                                availableRoles.forEach { role ->
                                    DropdownMenuItem(
                                        text = { Text(role.label) },
                                        onClick = {
                                            roleMenuExpanded = false
                                            showNotificationsOverlay = false
                                            authViewModel.switchRole(role, profile.roles)
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
                if (!useRail && !showNotificationsOverlay) {
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
                if (useRail && !showNotificationsOverlay) {
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
                        if (showNotificationsOverlay) {
                            NotificationsScreen(opsViewModel = opsVm)
                        } else {
                            when (currentRoute) {
                                "overview" -> LandlordDashboardScreen(
                                    viewModel = landlordVm,
                                    organizations = organizations,
                                    onNavigatePortfolio = { currentRoute = "portfolio" },
                                    onNavigateFinance = { currentRoute = "finance" },
                                    onNavigateOperations = { currentRoute = "operations" },
                                    onOpenCreateOrg = { showCreateOrgDialog = true },
                                    onOpenAddProperty = {
                                        if (organizations.isEmpty()) showCreateOrgDialog = true
                                        else {
                                            triggerAddProperty = true
                                            currentRoute = "portfolio"
                                        }
                                    }
                                )
                                "portfolio" -> LandlordPortfolioScreen(
                                    viewModel = landlordVm,
                                    currentUser = profile,
                                    organizations = organizations,
                                    onCreateOrgRequest = { showCreateOrgDialog = true },
                                    openAddPropertyImmediately = triggerAddProperty,
                                    onConsumeAddPropertyTrigger = { triggerAddProperty = false }
                                )
                                "finance" -> LandlordFinanceScreen(viewModel = landlordVm, currentUser = profile)
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
                                            onSignOut = onSignOut
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
                                    onNavigateDiscovery = { currentRoute = "discover" }
                                )
                                "tenant_pay" -> TenantPaymentsScreen(viewModel = tenantVm, currentUser = profile)
                                "tenant_repairs" -> TenantMaintenanceScreen(viewModel = tenantVm)
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
                                            onSignOut = onSignOut
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
                                    onSignOut = onSignOut
                                )

                                "admin_hub" -> AdminPortalScreen(adminViewModel = adminVm, initialSection = 0)
                                "admin_issues" -> AdminPortalScreen(adminViewModel = adminVm, initialSection = 2)
                                "admin_audit" -> AdminPortalScreen(adminViewModel = adminVm, initialSection = 3)
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
