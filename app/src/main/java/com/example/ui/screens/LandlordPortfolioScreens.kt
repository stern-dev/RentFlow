package com.example.ui.screens

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EditLocationAlt
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.ai.AiTaskState
import com.example.domain.finance.CurrencyUtils
import com.example.domain.finance.ReportExporter
import com.example.domain.location.BuildingLocationResolver
import com.example.domain.model.ApplicationStatus
import com.example.domain.model.InviteStatus
import com.example.domain.model.KenyaCounties
import com.example.domain.model.Lease
import com.example.domain.model.LeaseStatus
import com.example.domain.model.Organization
import com.example.domain.model.Property
import com.example.domain.model.PropertyCategory
import com.example.domain.model.RentalApplication
import com.example.domain.model.RentalUnit
import com.example.domain.model.RoleInvite
import com.example.domain.model.UnitStatus
import com.example.domain.model.UnitType
import com.example.domain.model.UserProfile
import com.example.domain.model.UserRole
import com.example.domain.security.PermissionPolicy
import com.example.domain.validation.PropertyUnitSchemas
import com.example.domain.validation.ValidationResult
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Checklist
import com.example.data.notifications.NotificationHelper
import com.example.data.notifications.PushDeepLink
import com.example.domain.model.DepositSettlement
import com.example.domain.model.DepositSettlementStatus
import com.example.domain.model.MoveInChecklist
import com.example.domain.model.MoveInChecklistStatus
import com.example.ui.components.BuildingLocationPickerDialog
import com.example.ui.components.CriticalActionConfirmationDialog
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.ErrorBannerCard
import com.example.ui.components.LandlordDepositSettlementDialog
import com.example.ui.components.LandlordMoveInChecklistDialog
import com.example.ui.components.LoadingStateBox
import com.example.ui.components.PillTone
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusPill
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusInfo
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusWarning
import com.example.ui.theme.StatusWarningBg
import com.example.ui.theme.TabularMoneyStyle
import com.example.ui.viewmodel.LandlordViewModel
import com.example.ui.viewmodel.UiState
import com.example.ui.viewmodel.UnitMatrixFilterState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LandlordPortfolioScreen(
    viewModel: LandlordViewModel,
    currentUser: UserProfile,
    organizations: List<Organization>,
    onCreateOrgRequest: () -> Unit,
    openAddPropertyImmediately: Boolean = false,
    onConsumeAddPropertyTrigger: () -> Unit = {},
    activeOrganization: Organization? = null,
    orgInvites: List<RoleInvite> = emptyList(),
    onCreateRoleInvite: (Organization, String, UserRole, List<String>, () -> Unit) -> Unit = { _, _, _, _, _ -> },
    onRevokeRoleInvite: (RoleInvite) -> Unit = {},
    isRecentlyAuthenticated: () -> Boolean = { true },
    onReauthenticate: suspend (Context) -> Result<Unit> = { Result.success(Unit) },
    deepLinkTarget: PushDeepLink? = null,
    onConsumeDeepLinkTarget: () -> Unit = {}
) {
    val context = LocalContext.current
    val propsState by viewModel.properties.collectAsStateWithLifecycle()
    val unitsState by viewModel.units.collectAsStateWithLifecycle()
    val filteredUnitsState by viewModel.filteredUnits.collectAsStateWithLifecycle()
    val unitFilterState by viewModel.unitFilterState.collectAsStateWithLifecycle()
    val aiUnitDraftState by viewModel.aiUnitDraftState.collectAsStateWithLifecycle()
    val appsState by viewModel.applications.collectAsStateWithLifecycle()
    val leasesState by viewModel.leases.collectAsStateWithLifecycle()
    val invoicesState by viewModel.invoices.collectAsStateWithLifecycle()
    val checklistsState by viewModel.moveInChecklists.collectAsStateWithLifecycle()
    val settlementsState by viewModel.depositSettlements.collectAsStateWithLifecycle()
    val activeOrgId by viewModel.activeOrgId.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()

    val properties = (propsState as? UiState.Success)?.data ?: emptyList()
    val units = (unitsState as? UiState.Success)?.data ?: emptyList()
    val applications = (appsState as? UiState.Success)?.data ?: emptyList()
    val leases = (leasesState as? UiState.Success)?.data ?: emptyList()
    val invoices = (invoicesState as? UiState.Success)?.data ?: emptyList()
    val checklists = (checklistsState as? UiState.Success)?.data ?: emptyList()
    val settlements = (settlementsState as? UiState.Success)?.data ?: emptyList()

    val uid = currentUser.userId
    val activeRole = currentUser.activeRoleEnum()
    val selectedOrg = activeOrganization ?: organizations.firstOrNull { it.id == activeOrgId } ?: organizations.firstOrNull()
    val canInviteTeam = PermissionPolicy.canInviteTeamMembers(uid, activeRole, selectedOrg)
    val canCreateProperty = activeRole == UserRole.ADMIN || activeRole == UserRole.LANDLORD
    val creatableProperties = remember(properties, uid, activeRole) {
        properties.filter { !it.archived && PermissionPolicy.canCreateUnit(uid, activeRole, it) }
    }

    var selectedTab by remember { mutableIntStateOf(0) }
    var showAddPropertyDialog by remember { mutableStateOf(openAddPropertyImmediately && canCreateProperty) }
    var showInviteTeamDialog by remember { mutableStateOf(false) }
    var propertyToEdit by remember { mutableStateOf<Property?>(null) }
    var openLocationPickerOnEdit by remember { mutableStateOf(false) }
    var propertyForUnitDialog by remember { mutableStateOf<Property?>(null) }
    var propertyForManagerDialog by remember { mutableStateOf<Property?>(null) }
    var propertyToDelete by remember { mutableStateOf<Property?>(null) }
    var unitForLeaseDialog by remember { mutableStateOf<Pair<RentalUnit, RentalApplication?>?>(null) }
    var leaseToTerminate by remember { mutableStateOf<Lease?>(null) }
    var leaseToRenew by remember { mutableStateOf<Lease?>(null) }
    var leaseForChecklistDialog by remember { mutableStateOf<Lease?>(null) }
    var leaseForSettlementDialog by remember { mutableStateOf<Lease?>(null) }

    LaunchedEffect(deepLinkTarget, leases, checklists, settlements, leasesState, checklistsState, settlementsState) {
        val dl = deepLinkTarget ?: return@LaunchedEffect
        if (dl.targetTab == NotificationHelper.TAB_LANDLORD_LEASES ||
            dl.checklistId != null ||
            dl.settlementId != null ||
            dl.leaseId != null
        ) {
            selectedTab = NotificationHelper.TAB_LANDLORD_LEASES
        }
        val targetChecklist = dl.checklistId?.let { cid -> checklists.firstOrNull { it.id == cid } }
        val targetSettlement = dl.settlementId?.let { sid -> settlements.firstOrNull { it.id == sid } }
        val resolvedLeaseId = dl.leaseId ?: targetChecklist?.leaseId ?: targetSettlement?.leaseId
        val matchedLease = resolvedLeaseId?.let { lid -> leases.firstOrNull { it.id == lid } }
        if (matchedLease != null) {
            if (dl.checklistId != null || targetChecklist != null) {
                leaseForChecklistDialog = matchedLease
                onConsumeDeepLinkTarget()
            } else if (dl.settlementId != null || targetSettlement != null) {
                leaseForSettlementDialog = matchedLease
                onConsumeDeepLinkTarget()
            } else if (leasesState is UiState.Success) {
                onConsumeDeepLinkTarget()
            }
        } else if (leasesState is UiState.Success && checklistsState is UiState.Success && settlementsState is UiState.Success) {
            onConsumeDeepLinkTarget()
        }
    }

    if (openAddPropertyImmediately) {
        onConsumeAddPropertyTrigger()
    }

    val tabs = listOf(
        "Properties (${properties.size})",
        "Units (${units.size})",
        "Applications (${applications.count { it.statusEnum() in listOf(ApplicationStatus.SUBMITTED, ApplicationStatus.UNDER_REVIEW) }})",
        "Leases (${leases.count { it.statusEnum() == LeaseStatus.ACTIVE }})"
    )

    Scaffold(
        floatingActionButton = {
            val showFab = (selectedTab == 0 && (canCreateProperty || organizations.isEmpty())) ||
                (selectedTab == 1 && (creatableProperties.isNotEmpty() || (organizations.isEmpty() && canCreateProperty)))
            if (showFab) {
                FloatingActionButton(
                    onClick = {
                        if (organizations.isEmpty()) onCreateOrgRequest()
                        else if (selectedTab == 0 || properties.isEmpty()) showAddPropertyDialog = true
                        else propertyForUnitDialog = creatableProperties.firstOrNull()
                    },
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = Color.White,
                    modifier = Modifier.testTag("add_portfolio_fab")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = if (selectedTab == 0) "Add Property" else "Add Unit"
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            SecondaryTabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { idx, label ->
                    Tab(
                        selected = selectedTab == idx,
                        onClick = { selectedTab = idx },
                        text = { Text(label, maxLines = 1) }
                    )
                }
            }

            when (selectedTab) {
                0 -> PropertiesTab(
                    propsState = propsState,
                    units = units,
                    organizations = organizations,
                    activeOrgId = activeOrgId,
                    canInviteTeam = canInviteTeam,
                    orgInvites = orgInvites,
                    canCreateProperty = canCreateProperty,
                    canManageProperty = { prop -> PermissionPolicy.canManagePropertyStructure(uid, activeRole, prop) },
                    canAssignManager = { prop -> PermissionPolicy.canAssignPropertyManager(uid, activeRole, prop) },
                    canCreateUnit = { prop -> PermissionPolicy.canCreateUnit(uid, activeRole, prop) },
                    canDeleteProperty = { prop -> PermissionPolicy.canDeleteProperty(uid, activeRole, prop) },
                    onSelectOrg = { orgId -> viewModel.setActiveOrgId(orgId) },
                    onCreateOrgRequest = onCreateOrgRequest,
                    onOpenInviteTeam = { showInviteTeamDialog = true },
                    onRevokeInvite = onRevokeRoleInvite,
                    onAddProperty = {
                        if (organizations.isEmpty()) onCreateOrgRequest() else showAddPropertyDialog = true
                    },
                    onEditProperty = { prop ->
                        openLocationPickerOnEdit = false
                        propertyToEdit = prop
                    },
                    onConfirmLocationPin = { prop ->
                        openLocationPickerOnEdit = true
                        propertyToEdit = prop
                    },
                    onAddUnit = { prop -> propertyForUnitDialog = prop },
                    onAssignManager = { prop -> propertyForManagerDialog = prop },
                    onToggleArchive = { prop -> viewModel.toggleArchiveProperty(prop) },
                    onDeleteProperty = { prop -> propertyToDelete = prop }
                )
                1 -> UnitsMatrixTab(
                    allUnits = units,
                    filteredUnitsState = filteredUnitsState,
                    filterState = unitFilterState,
                    properties = properties,
                    isBusy = busy,
                    aiDraftState = aiUnitDraftState,
                    canCreateUnit = { prop -> PermissionPolicy.canCreateUnit(uid, activeRole, prop) },
                    canEditPricingOrDelete = { unit -> PermissionPolicy.canEditUnitPricingOrDelete(uid, activeRole, unit) },
                    isRecentlyAuthenticated = isRecentlyAuthenticated,
                    onReauthenticate = onReauthenticate,
                    onStatusFilterToggle = { st -> viewModel.setUnitStatusFilter(st) },
                    onPropertyFilterToggle = { propId -> viewModel.setUnitPropertyFilter(propId) },
                    onSearchQueryChange = { q -> viewModel.setUnitSearchQuery(q) },
                    onClearFilters = { viewModel.clearUnitFilters() },
                    onAddUnit = { prop -> propertyForUnitDialog = prop },
                    onUpdateUnitFull = { unit, unitNum, unitType, rentCents, depCents, status, amenities, desc, imgs, pub, onDone ->
                        viewModel.updateUnit(
                            unit = unit,
                            unitNumber = unitNum,
                            unitType = unitType,
                            rentCents = rentCents,
                            depositCents = depCents,
                            status = status,
                            amenities = amenities,
                            description = desc,
                            imageRefs = imgs,
                            published = pub,
                            onSuccess = onDone
                        )
                    },
                    onDraftDescriptionWithAi = { propName, city, hood, unitNum, unitTypeLabel, rentCents, depCents, amenities ->
                        viewModel.draftUnitDescriptionWithAi(
                            propertyName = propName,
                            city = city,
                            neighborhood = hood,
                            unitNumber = unitNum,
                            unitTypeLabel = unitTypeLabel,
                            rentCents = rentCents,
                            depositCents = depCents,
                            amenities = amenities
                        )
                    },
                    onClearAiDraft = { viewModel.clearUnitDraft() },
                    onDeleteUnit = { unit -> viewModel.deleteUnit(unit) },
                    onAssignTenant = { unit -> unitForLeaseDialog = unit to null }
                )
                2 -> ApplicationsTab(
                    appsState = appsState,
                    units = units,
                    onReview = { app, st, notes -> viewModel.reviewApplication(app, st, notes) },
                    onCreateLeaseFromApp = { app ->
                        val matchingUnit = units.firstOrNull { it.id == app.unitId }
                        if (matchingUnit != null) {
                            unitForLeaseDialog = matchingUnit to app
                        }
                    }
                )
                3 -> LeasesTab(
                    leasesState = leasesState,
                    checklists = checklists,
                    settlements = settlements,
                    canTerminateLease = { lease -> PermissionPolicy.canTerminateLease(uid, activeRole, lease) },
                    canManageChecklist = { lease -> PermissionPolicy.canManageLeaseChecklist(uid, activeRole, lease) },
                    canManageSettlement = { lease -> PermissionPolicy.canManageDepositSettlement(uid, activeRole, lease) },
                    onExportPdf = { lease ->
                        ReportExporter.exportPdfDocument(
                            context = context,
                            fileName = "lease_${lease.unitNumber}.pdf",
                            title = "RentFlow Tenancy Agreement",
                            subtitle = "${lease.propertyName} • Unit ${lease.unitNumber}",
                            lines = listOf(
                                "[H]TENANCY PARTIES & TERM",
                                "Tenant: ${lease.tenantName} (${lease.tenantPhone})",
                                "Lease Status: ${lease.statusEnum().label}",
                                "Term: ${CurrencyUtils.formatDate(lease.startDateEpochMs)} to ${CurrencyUtils.formatDate(lease.endDateEpochMs)}",
                                "---",
                                "[H]FINANCIAL TERMS (KES)",
                                "Monthly Rent: ${CurrencyUtils.formatKes(lease.rentCents, true)}",
                                "Security Deposit: ${CurrencyUtils.formatKes(lease.depositCents, true)}",
                                "Rent Due Day: Day ${lease.dueDayOfMonth} of each month",
                                "Account Credit Balance: ${CurrencyUtils.formatKes(lease.creditBalanceCents, true)}",
                                "---",
                                "[H]AGREEMENT SUMMARY",
                                lease.termsSummary.ifBlank { "Standard Kenyan residential tenancy terms apply." }
                            )
                        )
                    },
                    onOpenChecklist = { lease -> leaseForChecklistDialog = lease },
                    onOpenSettlement = { lease -> leaseForSettlementDialog = lease },
                    onRenew = { lease -> leaseToRenew = lease },
                    onTerminate = { lease -> leaseToTerminate = lease }
                )
            }
        }
    }

    if (showInviteTeamDialog && selectedOrg != null) {
        InviteTeamMemberDialog(
            org = selectedOrg,
            properties = properties.filter { it.orgId == selectedOrg.id && !it.archived },
            isBusy = busy,
            onDismiss = { showInviteTeamDialog = false },
            onSendInvite = { email, role, propertyIds ->
                onCreateRoleInvite(selectedOrg, email, role, propertyIds) {
                    showInviteTeamDialog = false
                }
            }
        )
    }

    if (showAddPropertyDialog) {
        if (selectedOrg != null) {
            PropertyEditorDialog(
                organizations = organizations,
                initialOrgId = selectedOrg.id,
                ownerUid = uid,
                existingProperty = null,
                isBusy = busy,
                onDismiss = { showAddPropertyDialog = false },
                onSave = { orgId, name, cat, city, county, hood, addr, desc, waterMeter, powerMeter, notes, imgs, paybill, till, prefix, lat, lng, fmtAddr, pid, locVerified ->
                    viewModel.setActiveOrgId(orgId)
                    viewModel.createProperty(
                        orgId = orgId,
                        name = name,
                        category = cat,
                        city = city,
                        county = county,
                        neighborhood = hood,
                        addressLine = addr,
                        description = desc,
                        waterMeterNumber = waterMeter,
                        electricityMeterNumber = powerMeter,
                        notes = notes,
                        imageRefs = imgs,
                        mpesaPaybill = paybill,
                        mpesaTill = till,
                        mpesaAccountPrefix = prefix,
                        latitude = lat,
                        longitude = lng,
                        formattedAddress = fmtAddr,
                        placeId = pid,
                        locationVerified = locVerified
                    ) {
                        showAddPropertyDialog = false
                    }
                }
            )
        }
    }

    propertyToEdit?.let { prop ->
        PropertyEditorDialog(
            organizations = organizations,
            initialOrgId = prop.orgId,
            ownerUid = prop.ownerUid,
            existingProperty = prop,
            openLocationPickerInitially = openLocationPickerOnEdit,
            isBusy = busy,
            onDismiss = {
                openLocationPickerOnEdit = false
                propertyToEdit = null
            },
            onSave = { _, name, cat, city, county, hood, addr, desc, waterMeter, powerMeter, notes, imgs, paybill, till, prefix, lat, lng, fmtAddr, pid, locVerified ->
                viewModel.updateProperty(
                    property = prop,
                    name = name,
                    category = cat,
                    city = city,
                    county = county,
                    neighborhood = hood,
                    addressLine = addr,
                    description = desc,
                    waterMeterNumber = waterMeter,
                    electricityMeterNumber = powerMeter,
                    notes = notes,
                    imageRefs = imgs,
                    mpesaPaybill = paybill,
                    mpesaTill = till,
                    mpesaAccountPrefix = prefix,
                    latitude = lat,
                    longitude = lng,
                    formattedAddress = fmtAddr,
                    placeId = pid,
                    locationVerified = locVerified
                ) {
                    openLocationPickerOnEdit = false
                    propertyToEdit = null
                }
            }
        )
    }

    propertyToDelete?.let { prop ->
        CriticalActionConfirmationDialog(
            title = "Delete ${prop.name}?",
            impactSummary = "Permanently deletes ${prop.name} from your organization and removes access for all assigned managers.",
            confirmButtonText = "Delete",
            requiredPhrase = "DELETE",
            isRecentlyAuthenticated = isRecentlyAuthenticated(),
            onReauthenticate = onReauthenticate,
            onDismiss = { propertyToDelete = null },
            onConfirm = {
                viewModel.deleteProperty(prop.id)
                propertyToDelete = null
            }
        )
    }

    propertyForUnitDialog?.let { prop ->
        AddUnitDialog(
            property = prop,
            isBusy = busy,
            aiDraftState = aiUnitDraftState,
            onDraftWithAi = { unitNum, unitTypeLabel, rentCents, depCents, amenities ->
                viewModel.draftUnitDescriptionWithAi(
                    propertyName = prop.name,
                    city = prop.city,
                    neighborhood = prop.neighborhood,
                    unitNumber = unitNum,
                    unitTypeLabel = unitTypeLabel,
                    rentCents = rentCents,
                    depositCents = depCents,
                    amenities = amenities
                )
            },
            onClearAiDraft = { viewModel.clearUnitDraft() },
            onDismiss = {
                viewModel.clearUnitDraft()
                propertyForUnitDialog = null
            },
            onSave = { num, type, rent, dep, status, amenities, desc, imgs, pub ->
                viewModel.createUnit(prop, num, type, rent, dep, status, amenities, desc, imgs, pub) {
                    viewModel.clearUnitDraft()
                    propertyForUnitDialog = null
                }
            }
        )
    }

    propertyForManagerDialog?.let { prop ->
        AssignManagerDialog(
            property = prop,
            isBusy = busy,
            onDismiss = { propertyForManagerDialog = null },
            onAssign = { managerUid ->
                viewModel.assignManager(prop, managerUid) { propertyForManagerDialog = null }
            }
        )
    }

    unitForLeaseDialog?.let { (unit, app) ->
        CreateLeaseDialog(
            unit = unit,
            preselectedApp = app,
            isBusy = busy,
            onDismiss = { unitForLeaseDialog = null },
            onCreate = { tenantUid, tenantName, tenantPhone, rentCents, depCents, startMs, endMs, dueDay, terms ->
                viewModel.createLease(
                    unit, tenantUid, tenantName, tenantPhone, rentCents, depCents,
                    startMs, endMs, dueDay, terms, currentUser.fullName
                ) {
                    unitForLeaseDialog = null
                }
            }
        )
    }

    leaseToRenew?.let { lease ->
        RenewLeaseDialog(
            lease = lease,
            canEditRent = PermissionPolicy.canEditLeaseTerms(uid, activeRole, lease),
            onDismiss = { leaseToRenew = null },
            onConfirm = { newEndMs, newRentCents ->
                viewModel.renewLease(lease, newEndMs, newRentCents, currentUser.fullName)
                leaseToRenew = null
            }
        )
    }

    leaseToTerminate?.let { lease ->
        CriticalActionConfirmationDialog(
            title = "Terminate Lease?",
            impactSummary = "Ends ${lease.tenantName}'s active tenancy for Unit ${lease.unitNumber}, releases the unit as Available, and writes an immutable audit log.",
            confirmButtonText = "Terminate",
            requiredPhrase = "TERMINATE",
            reasonLabel = "Termination Notice / Reason",
            requireReasonMinLen = 3,
            isRecentlyAuthenticated = isRecentlyAuthenticated(),
            onReauthenticate = onReauthenticate,
            onDismiss = { leaseToTerminate = null },
            onConfirm = { reason ->
                viewModel.terminateLease(lease, reason, currentUser.fullName)
                leaseToTerminate = null
                val existingSettle = settlements.firstOrNull { it.leaseId == lease.id }
                if (existingSettle?.statusEnum() != DepositSettlementStatus.SETTLED &&
                    PermissionPolicy.canManageDepositSettlement(uid, activeRole, lease)
                ) {
                    leaseForSettlementDialog = lease
                }
            }
        )
    }

    leaseForChecklistDialog?.let { lease ->
        val existingChecklist = checklists.firstOrNull { it.leaseId == lease.id }
        LandlordMoveInChecklistDialog(
            lease = lease,
            existingChecklist = existingChecklist,
            isBusy = busy,
            onDismiss = { leaseForChecklistDialog = null },
            onSave = { waterMeter, powerMeter, keysCount, items, imageRefs, landlordNotes, submitToTenant ->
                viewModel.saveMoveInChecklist(
                    lease = lease,
                    existingChecklist = existingChecklist,
                    inspectionDateEpochMs = existingChecklist?.inspectionDateEpochMs ?: System.currentTimeMillis(),
                    waterMeterReading = waterMeter,
                    electricityMeterReading = powerMeter,
                    keysHandedOverCount = keysCount,
                    items = items,
                    imageRefs = imageRefs,
                    landlordNotes = landlordNotes,
                    submitForTenantSignOff = submitToTenant,
                    actorName = currentUser.fullName,
                    onSuccess = { leaseForChecklistDialog = null }
                )
            }
        )
    }

    leaseForSettlementDialog?.let { lease ->
        val existingSettlement = settlements.firstOrNull { it.leaseId == lease.id }
        val leaseInvoices = remember(invoices, lease.id) { invoices.filter { it.leaseId == lease.id } }
        LandlordDepositSettlementDialog(
            lease = lease,
            existingSettlement = existingSettlement,
            leaseInvoices = leaseInvoices,
            isBusy = busy,
            onDismiss = { leaseForSettlementDialog = null },
            onSave = { depositHeldCents, arrearsCents, creditCents, deductions, waterFinal, powerFinal, keysReturned, imageRefs, landlordNotes, targetStatus, refundMethod, refundRef ->
                viewModel.saveDepositSettlement(
                    lease = lease,
                    existingSettlement = existingSettlement,
                    depositHeldCents = depositHeldCents,
                    arrearsIncludedCents = arrearsCents,
                    creditAppliedCents = creditCents,
                    deductions = deductions,
                    moveOutDateEpochMs = existingSettlement?.moveOutDateEpochMs ?: System.currentTimeMillis(),
                    waterMeterFinal = waterFinal,
                    electricityMeterFinal = powerFinal,
                    keysReturnedCount = keysReturned,
                    imageRefs = imageRefs,
                    landlordNotes = landlordNotes,
                    targetStatus = targetStatus,
                    refundMethod = refundMethod,
                    refundReferenceCode = refundRef,
                    actorName = currentUser.fullName,
                    onSuccess = { leaseForSettlementDialog = null }
                )
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PropertiesTab(
    propsState: UiState<List<Property>>,
    units: List<RentalUnit>,
    organizations: List<Organization>,
    activeOrgId: String?,
    canInviteTeam: Boolean,
    orgInvites: List<RoleInvite>,
    canCreateProperty: Boolean,
    canManageProperty: (Property) -> Boolean,
    canAssignManager: (Property) -> Boolean,
    canCreateUnit: (Property) -> Boolean,
    canDeleteProperty: (Property) -> Boolean,
    onSelectOrg: (String) -> Unit,
    onCreateOrgRequest: () -> Unit,
    onOpenInviteTeam: () -> Unit,
    onRevokeInvite: (RoleInvite) -> Unit,
    onAddProperty: () -> Unit,
    onEditProperty: (Property) -> Unit,
    onConfirmLocationPin: (Property) -> Unit,
    onAddUnit: (Property) -> Unit,
    onAssignManager: (Property) -> Unit,
    onToggleArchive: (Property) -> Unit,
    onDeleteProperty: (Property) -> Unit
) {
    if (organizations.isEmpty()) {
        Box(Modifier.padding(16.dp)) {
            EmptyStateCard(
                title = if (canCreateProperty) "Create an Organization First" else "No Organization Assigned",
                message = if (canCreateProperty) {
                    "Properties in RentFlow belong to an organization portfolio. Create your organization before registering properties."
                } else {
                    "Ask a Landlord / Organization Owner to invite you or assign you as Property Manager."
                },
                ctaLabel = if (canCreateProperty) "Create Organization" else null,
                onCtaClick = if (canCreateProperty) onCreateOrgRequest else null
            )
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (organizations.size > 1) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(organizations, key = { it.id }) { org ->
                    FilterChip(
                        selected = org.id == activeOrgId,
                        onClick = { onSelectOrg(org.id) },
                        label = { Text(org.name, maxLines = 1) }
                    )
                }
            }
        }

        if (canInviteTeam) {
            val pendingTeamInvites = orgInvites.filter { it.statusEnum() == InviteStatus.PENDING }
            Card(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Organization Team & Invites", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "${pendingTeamInvites.size} pending invite(s)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        OutlinedButton(
                            onClick = onOpenInviteTeam,
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("invite_team_button")
                        ) {
                            Icon(Icons.Filled.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Invite Team")
                        }
                    }
                    pendingTeamInvites.forEach { invite ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(invite.inviteeEmail, style = MaterialTheme.typography.bodySmall)
                                Text(
                                    "${invite.roleEnum().label} • Pending acceptance",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            TextButton(
                                onClick = { onRevokeInvite(invite) },
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("revoke_invite_${invite.id}")
                            ) {
                                Text("Revoke", color = StatusError)
                            }
                        }
                    }
                }
            }
        }

        when (propsState) {
            is UiState.Loading -> LoadingStateBox("Loading properties...")
            is UiState.Error -> Box(Modifier.padding(16.dp)) { ErrorBannerCard(propsState.message) }
            is UiState.Success -> {
                val props = propsState.data
                if (props.isEmpty()) {
                    Box(Modifier.padding(16.dp)) {
                        EmptyStateCard(
                            title = "No Properties Registered",
                            message = if (canCreateProperty) {
                                "Register your first apartment block, bedsitter court, or commercial building to begin adding rental units."
                            } else {
                                "Properties you are assigned to manage will appear here."
                            },
                            ctaLabel = if (canCreateProperty) "Register Property" else null,
                            onCtaClick = if (canCreateProperty) onAddProperty else null
                        )
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(props, key = { it.id }) { prop ->
                            val propUnits = units.filter { it.propertyId == prop.id }
                            val occupied = propUnits.count { it.statusEnum() == UnitStatus.OCCUPIED }
                            val countySuffix = if (prop.county.isNotBlank()) " • ${prop.county} County" else ""
                            Card(
                                shape = MaterialTheme.shapes.medium,
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    if (prop.imageRefs.isNotEmpty()) {
                                        AsyncImage(
                                            model = prop.imageRefs.first(),
                                            contentDescription = prop.name,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxWidth().height(130.dp).clip(MaterialTheme.shapes.small)
                                        )
                                    }
                                    Row(
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.Top,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(prop.name, style = MaterialTheme.typography.titleMedium)
                                            Text(
                                                "${prop.categoryEnum().label} • ${prop.neighborhood}, ${prop.city}$countySuffix",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            StatusPill(
                                                label = if (prop.archived) "Archived" else "$occupied/${propUnits.size} Occupied",
                                                tone = if (prop.archived) PillTone.NEUTRAL else PillTone.SUCCESS
                                            )
                                            if (prop.landlordVerified) {
                                                StatusPill("Verified Landlord", PillTone.SUCCESS)
                                            }
                                        }
                                    }
                                    if (!prop.mpesaPaybill.isNullOrBlank() || !prop.mpesaTill.isNullOrBlank()) {
                                        val mpesaText = listOfNotNull(
                                            prop.mpesaPaybill?.let { "M-Pesa Paybill: $it" },
                                            prop.mpesaTill?.let { "Till: $it" },
                                            prop.mpesaAccountPrefix?.let { "Prefix: $it" }
                                        ).joinToString(" • ")
                                        Text(
                                            text = mpesaText,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                    }
                                    if (!prop.waterMeterNumber.isNullOrBlank() || !prop.electricityMeterNumber.isNullOrBlank()) {
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            if (!prop.waterMeterNumber.isNullOrBlank()) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                                ) {
                                                    Icon(Icons.Filled.WaterDrop, contentDescription = null, modifier = Modifier.size(14.dp), tint = StatusInfo)
                                                    Spacer(Modifier.width(4.dp))
                                                    Text("Water: ${prop.waterMeterNumber}", style = MaterialTheme.typography.labelSmall)
                                                }
                                            }
                                            if (!prop.electricityMeterNumber.isNullOrBlank()) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                                ) {
                                                    Icon(Icons.Filled.ElectricBolt, contentDescription = null, modifier = Modifier.size(14.dp), tint = StatusWarning)
                                                    Spacer(Modifier.width(4.dp))
                                                    Text("Power: ${prop.electricityMeterNumber}", style = MaterialTheme.typography.labelSmall)
                                                }
                                            }
                                        }
                                    }
                                    if (prop.description.isNotBlank()) {
                                        Text(prop.description, style = MaterialTheme.typography.bodySmall)
                                    }
                                    if (!prop.notes.isNullOrBlank()) {
                                        Text(
                                            "Note: ${prop.notes}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    val allowManage = canManageProperty(prop)
                                    val allowAssignMgr = canAssignManager(prop)
                                    val allowCreateUnit = canCreateUnit(prop)
                                    val allowDelete = canDeleteProperty(prop)
                                    val resolvedPropLocation = BuildingLocationResolver.resolve(prop)
                                    if (resolvedPropLocation.isExactPin) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Icon(
                                                Icons.Filled.LocationOn,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.secondary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text(
                                                text = "Exact Map Pin • ${resolvedPropLocation.addressSummary}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.secondary,
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    } else {
                                        androidx.compose.material3.Surface(
                                            color = StatusWarningBg,
                                            shape = MaterialTheme.shapes.small,
                                            border = BorderStroke(1.dp, StatusWarning.copy(alpha = 0.35f)),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("confirm_location_prompt_${prop.id}")
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = "Approximate Map Location (${prop.city})",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = StatusWarning
                                                    )
                                                    Text(
                                                        text = "Set an exact GPS pin so renters get accurate Google Maps directions.",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                                if (allowManage) {
                                                    TextButton(
                                                        onClick = { onConfirmLocationPin(prop) },
                                                        modifier = Modifier
                                                            .heightIn(min = 48.dp)
                                                            .testTag("confirm_location_button_${prop.id}")
                                                    ) {
                                                        Text("Confirm Pin")
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    if (!allowManage) {
                                        Text(
                                            "Managed Access • Structure & rent pricing controlled by Owner",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                    }
                                    if (allowCreateUnit || allowManage || allowAssignMgr || (propUnits.isEmpty() && allowDelete)) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            if (allowCreateUnit) {
                                                OutlinedButton(
                                                    onClick = { onAddUnit(prop) },
                                                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                                                ) {
                                                    Icon(Icons.Filled.MeetingRoom, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    Spacer(Modifier.width(4.dp))
                                                    Text("Unit", maxLines = 1)
                                                }
                                            }
                                            if (allowManage) {
                                                OutlinedButton(
                                                    onClick = { onEditProperty(prop) },
                                                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("edit_property_button_${prop.id}")
                                                ) {
                                                    Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    Spacer(Modifier.width(4.dp))
                                                    Text("Edit", maxLines = 1)
                                                }
                                            }
                                            if (allowAssignMgr) {
                                                IconButton(
                                                    onClick = { onAssignManager(prop) },
                                                    modifier = Modifier.size(48.dp)
                                                ) {
                                                    Icon(Icons.Filled.PersonAdd, contentDescription = "Assign Manager")
                                                }
                                            }
                                            if (allowManage) {
                                                IconButton(
                                                    onClick = { onToggleArchive(prop) },
                                                    modifier = Modifier.size(48.dp)
                                                ) {
                                                    Icon(Icons.Filled.Archive, contentDescription = if (prop.archived) "Restore Property" else "Archive Property")
                                                }
                                            }
                                            if (propUnits.isEmpty() && allowDelete) {
                                                IconButton(
                                                    onClick = { onDeleteProperty(prop) },
                                                    modifier = Modifier.size(48.dp).testTag("delete_property_button_${prop.id}")
                                                ) {
                                                    Icon(
                                                        Icons.Filled.DeleteOutline,
                                                        contentDescription = "Delete Property",
                                                        tint = StatusError
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private val COMMON_KENYAN_AMENITIES = listOf(
    "Wi-Fi",
    "Borehole Water",
    "Security / CCTV",
    "Parking",
    "Backup Generator",
    "Prepaid KPLC",
    "Balcony",
    "Solar Hot Water"
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UnitsMatrixTab(
    allUnits: List<RentalUnit>,
    filteredUnitsState: UiState<List<RentalUnit>>,
    filterState: UnitMatrixFilterState,
    properties: List<Property>,
    isBusy: Boolean,
    aiDraftState: AiTaskState,
    canCreateUnit: (Property) -> Boolean,
    canEditPricingOrDelete: (RentalUnit) -> Boolean,
    isRecentlyAuthenticated: () -> Boolean,
    onReauthenticate: suspend (Context) -> Result<Unit>,
    onStatusFilterToggle: (UnitStatus?) -> Unit,
    onPropertyFilterToggle: (String?) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onClearFilters: () -> Unit,
    onAddUnit: (Property) -> Unit,
    onUpdateUnitFull: (
        unit: RentalUnit,
        unitNumber: String,
        unitType: UnitType,
        rentCents: Long,
        depositCents: Long,
        status: UnitStatus,
        amenities: List<String>,
        description: String,
        imageRefs: List<String>,
        published: Boolean,
        onDone: () -> Unit
    ) -> Unit,
    onDraftDescriptionWithAi: (
        propertyName: String,
        city: String,
        neighborhood: String,
        unitNumber: String,
        unitTypeLabel: String,
        rentCents: Long,
        depositCents: Long,
        amenities: List<String>
    ) -> Unit,
    onClearAiDraft: () -> Unit,
    onDeleteUnit: (RentalUnit) -> Unit,
    onAssignTenant: (RentalUnit) -> Unit
) {
    var selectedUnit by remember { mutableStateOf<RentalUnit?>(null) }
    var unitToDelete by remember { mutableStateOf<RentalUnit?>(null) }

    when (filteredUnitsState) {
        is UiState.Loading -> LoadingStateBox("Loading units...")
        is UiState.Error -> Box(Modifier.padding(16.dp)) { ErrorBannerCard(filteredUnitsState.message) }
        is UiState.Success -> {
            val units = filteredUnitsState.data
            if (allUnits.isEmpty()) {
                val creatableProp = properties.firstOrNull { !it.archived && canCreateUnit(it) }
                Box(Modifier.padding(16.dp)) {
                    EmptyStateCard(
                        title = "No Rental Units Added",
                        message = "Add units to your properties to set KES rent, deposit, amenities, and publish vacant units for applications.",
                        ctaLabel = if (creatableProp != null) "Add First Unit" else null,
                        onCtaClick = creatableProp?.let { p -> { onAddUnit(p) } }
                    )
                }
            } else {
                val hasActiveFilters = filterState.status != null || !filterState.propertyId.isNullOrBlank() || filterState.searchQuery.isNotBlank()
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    item {
                        SectionHeader(
                            title = if (hasActiveFilters) "Unit Matrix (${units.size} of ${allUnits.size})" else "Visual Unit Matrix (${allUnits.size})",
                            actionLabel = if (hasActiveFilters) "Clear Filters" else null,
                            onActionClick = if (hasActiveFilters) onClearFilters else null
                        )
                    }
                    item {
                        OutlinedTextField(
                            value = filterState.searchQuery,
                            onValueChange = onSearchQueryChange,
                            label = { Text("Search unit #, property, or type") },
                            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Search units") },
                            trailingIcon = {
                                if (filterState.searchQuery.isNotBlank()) {
                                    IconButton(onClick = { onSearchQueryChange("") }) {
                                        Icon(Icons.Filled.Clear, contentDescription = "Clear search")
                                    }
                                }
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("unit_search_input")
                        )
                    }
                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item {
                                FilterChip(
                                    selected = filterState.status == null,
                                    onClick = { onStatusFilterToggle(null) },
                                    label = { Text("All (${allUnits.size})") },
                                    modifier = Modifier.testTag("unit_filter_all")
                                )
                            }
                            items(UnitStatus.entries) { st ->
                                val count = allUnits.count { it.statusEnum() == st }
                                FilterChip(
                                    selected = filterState.status == st,
                                    onClick = { onStatusFilterToggle(st) },
                                    label = { Text("${st.label} ($count)") },
                                    modifier = Modifier.testTag("unit_filter_${st.name.lowercase()}")
                                )
                            }
                        }
                    }
                    if (properties.size > 1) {
                        item {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                item {
                                    FilterChip(
                                        selected = filterState.propertyId == null,
                                        onClick = { onPropertyFilterToggle(null) },
                                        label = { Text("All Properties") }
                                    )
                                }
                                items(properties, key = { it.id }) { prop ->
                                    FilterChip(
                                        selected = filterState.propertyId == prop.id,
                                        onClick = { onPropertyFilterToggle(prop.id) },
                                        label = { Text(prop.name, maxLines = 1) }
                                    )
                                }
                            }
                        }
                    }
                    if (units.isEmpty()) {
                        item {
                            EmptyStateCard(
                                title = "No Matching Units",
                                message = "No units match the current status or property filters.",
                                ctaLabel = "Reset Filters",
                                onCtaClick = onClearFilters,
                                showIllustration = false
                            )
                        }
                    } else {
                        item {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                units.forEach { unit ->
                                    val (bg, fg) = when (unit.statusEnum()) {
                                        UnitStatus.AVAILABLE -> StatusSuccess.copy(alpha = 0.12f) to StatusSuccess
                                        UnitStatus.RESERVED -> StatusWarning.copy(alpha = 0.14f) to StatusWarning
                                        UnitStatus.OCCUPIED -> StatusInfo.copy(alpha = 0.12f) to StatusInfo
                                        UnitStatus.UNDER_MAINTENANCE -> StatusError.copy(alpha = 0.12f) to StatusError
                                    }
                                    Card(
                                        shape = MaterialTheme.shapes.medium,
                                        colors = CardDefaults.cardColors(containerColor = bg),
                                        border = BorderStroke(1.dp, fg.copy(alpha = 0.4f)),
                                        modifier = Modifier
                                            .width(158.dp)
                                            .clickable {
                                                onClearAiDraft()
                                                selectedUnit = unit
                                            }
                                            .testTag("unit_card_${unit.id}")
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Row(
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text(unit.unitNumber, style = MaterialTheme.typography.titleMedium, color = fg)
                                                Text(unit.unitTypeEnum().label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            Text(unit.propertyName, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                                            Text(
                                                CurrencyUtils.formatKes(unit.rentCents),
                                                style = TabularMoneyStyle.copy(fontSize = MaterialTheme.typography.bodyMedium.fontSize)
                                            )
                                            Text(
                                                "${unit.statusEnum().label}${if (unit.published) " • Listed" else ""}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = fg
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    selectedUnit?.let { unit ->
        val parentProp = properties.firstOrNull { it.id == unit.propertyId }
        UnitEditorDialog(
            unit = unit,
            parentProperty = parentProp,
            canEditPricingOrDelete = canEditPricingOrDelete(unit),
            isBusy = isBusy,
            aiDraftState = aiDraftState,
            onDismiss = {
                onClearAiDraft()
                selectedUnit = null
            },
            onDraftWithAi = { unitNum, unitTypeLabel, rentCents, depCents, amenities ->
                onDraftDescriptionWithAi(
                    unit.propertyName,
                    parentProp?.city ?: unit.propertyCity,
                    parentProp?.neighborhood ?: unit.propertyCity,
                    unitNum,
                    unitTypeLabel,
                    rentCents,
                    depCents,
                    amenities
                )
            },
            onSave = { unitNum, unitType, rentCents, depCents, status, amenities, desc, imgs, pub ->
                onUpdateUnitFull(unit, unitNum, unitType, rentCents, depCents, status, amenities, desc, imgs, pub) {
                    onClearAiDraft()
                    selectedUnit = null
                }
            },
            onAssignTenant = {
                onClearAiDraft()
                selectedUnit = null
                onAssignTenant(unit)
            },
            onRequestDelete = {
                onClearAiDraft()
                selectedUnit = null
                unitToDelete = unit
            }
        )
    }

    unitToDelete?.let { unit ->
        CriticalActionConfirmationDialog(
            title = "Delete Unit ${unit.unitNumber}?",
            impactSummary = "Permanently deletes Unit ${unit.unitNumber} from ${unit.propertyName} and updates property unit counts.",
            confirmButtonText = "Delete",
            requiredPhrase = "DELETE",
            isRecentlyAuthenticated = isRecentlyAuthenticated(),
            onReauthenticate = onReauthenticate,
            onDismiss = { unitToDelete = null },
            onConfirm = {
                onDeleteUnit(unit)
                unitToDelete = null
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun UnitEditorDialog(
    unit: RentalUnit,
    parentProperty: Property?,
    canEditPricingOrDelete: Boolean,
    isBusy: Boolean,
    aiDraftState: AiTaskState,
    onDismiss: () -> Unit,
    onDraftWithAi: (String, String, Long, Long, List<String>) -> Unit,
    onSave: (String, UnitType, Long, Long, UnitStatus, List<String>, String, List<String>, Boolean) -> Unit,
    onAssignTenant: () -> Unit,
    onRequestDelete: () -> Unit
) {
    var unitNumber by remember(unit.id) { mutableStateOf(unit.unitNumber) }
    var unitType by remember(unit.id) { mutableStateOf(unit.unitTypeEnum()) }
    var typeExpanded by remember { mutableStateOf(false) }
    var rentText by remember(unit.id) { mutableStateOf(CurrencyUtils.centsToInputString(unit.rentCents)) }
    var depositText by remember(unit.id) { mutableStateOf(CurrencyUtils.centsToInputString(unit.depositCents)) }
    var status by remember(unit.id) { mutableStateOf(unit.statusEnum()) }
    var selectedAmenities by remember(unit.id) { mutableStateOf(unit.amenities.toSet()) }
    var customAmenitiesText by remember(unit.id) {
        mutableStateOf(unit.amenities.filter { it !in COMMON_KENYAN_AMENITIES }.joinToString(", "))
    }
    var description by remember(unit.id) { mutableStateOf(unit.description) }
    var published by remember(unit.id) { mutableStateOf(unit.published) }
    var err by remember { mutableStateOf<String?>(null) }
    var fieldErrors by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    LaunchedEffect(aiDraftState) {
        when (aiDraftState) {
            is AiTaskState.Streaming -> description = aiDraftState.text
            is AiTaskState.Success -> description = aiDraftState.text
            else -> {}
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Unit • ${unit.propertyName}") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                if (!err.isNullOrBlank()) ErrorBannerCard(err!!)
                if (aiDraftState is AiTaskState.Error) ErrorBannerCard(aiDraftState.message)
                OutlinedTextField(
                    value = unitNumber,
                    onValueChange = { unitNumber = it; fieldErrors = fieldErrors - "unitNumber"; err = null },
                    label = { Text("Unit Number") },
                    isError = fieldErrors.containsKey("unitNumber"),
                    supportingText = fieldErrors["unitNumber"]?.let { msg -> { Text(msg) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("edit_unit_number_input")
                )
                ExposedDropdownMenuBox(expanded = typeExpanded, onExpandedChange = { typeExpanded = !typeExpanded }) {
                    OutlinedTextField(
                        value = unitType.label,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Unit Type") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(typeExpanded) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true).fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = typeExpanded, onDismissRequest = { typeExpanded = false }) {
                        UnitType.entries.forEach { t ->
                            DropdownMenuItem(text = { Text(t.label) }, onClick = { unitType = t; typeExpanded = false })
                        }
                    }
                }
                OutlinedTextField(
                    value = rentText,
                    onValueChange = { rentText = it; fieldErrors = fieldErrors - "rentCents"; err = null },
                    label = { Text("Monthly Rent (KES)") },
                    enabled = canEditPricingOrDelete,
                    isError = fieldErrors.containsKey("rentCents"),
                    supportingText = when {
                        fieldErrors.containsKey("rentCents") -> { { Text(fieldErrors["rentCents"]!!) } }
                        !canEditPricingOrDelete -> { { Text("Owner only") } }
                        else -> null
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("edit_unit_rent_input")
                )
                OutlinedTextField(
                    value = depositText,
                    onValueChange = { depositText = it; fieldErrors = fieldErrors - "depositCents"; err = null },
                    label = { Text("Security Deposit (KES)") },
                    enabled = canEditPricingOrDelete,
                    isError = fieldErrors.containsKey("depositCents"),
                    supportingText = when {
                        fieldErrors.containsKey("depositCents") -> { { Text(fieldErrors["depositCents"]!!) } }
                        !canEditPricingOrDelete -> { { Text("Owner only") } }
                        else -> null
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("edit_unit_deposit_input")
                )
                Text("Occupancy Status", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    UnitStatus.entries.forEach { st ->
                        FilterChip(
                            selected = status == st,
                            onClick = {
                                status = st
                                if (st != UnitStatus.AVAILABLE) published = false
                                fieldErrors = fieldErrors - "published"
                            },
                            label = { Text(st.label) }
                        )
                    }
                }
                Text("Amenities", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    COMMON_KENYAN_AMENITIES.forEach { amenity ->
                        val active = selectedAmenities.contains(amenity)
                        FilterChip(
                            selected = active,
                            onClick = {
                                selectedAmenities = if (active) selectedAmenities - amenity else selectedAmenities + amenity
                            },
                            label = { Text(amenity) }
                        )
                    }
                }
                OutlinedTextField(
                    value = customAmenitiesText,
                    onValueChange = { customAmenitiesText = it; fieldErrors = fieldErrors - "amenities" },
                    label = { Text("Other Amenities (comma separated)") },
                    isError = fieldErrors.containsKey("amenities"),
                    supportingText = fieldErrors["amenities"]?.let { msg -> { Text(msg) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Listing Description", style = MaterialTheme.typography.labelMedium)
                    TextButton(
                        onClick = {
                            val combinedAmenities = (selectedAmenities.toList() + customAmenitiesText.split(",").map { it.trim() }).filter { it.isNotBlank() }.distinct()
                            val rentCents = CurrencyUtils.parseKesToCents(rentText) ?: unit.rentCents
                            val depCents = CurrencyUtils.parseKesToCents(depositText) ?: unit.depositCents
                            onDraftWithAi(unitNumber.ifBlank { unit.unitNumber }, unitType.label, rentCents, depCents, combinedAmenities)
                        },
                        enabled = aiDraftState !is AiTaskState.Loading,
                        modifier = Modifier.heightIn(min = 48.dp).testTag("draft_unit_description_ai_button")
                    ) {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (aiDraftState is AiTaskState.Loading) "Drafting..." else "Draft with AI")
                    }
                }
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it; fieldErrors = fieldErrors - "description" },
                    label = { Text("Description") },
                    isError = fieldErrors.containsKey("description"),
                    supportingText = fieldErrors["description"]?.let { msg -> { Text(msg) } },
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth().testTag("edit_unit_description_input")
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Publish in Discovery")
                    Switch(
                        checked = published && status == UnitStatus.AVAILABLE,
                        onCheckedChange = { published = it },
                        enabled = status == UnitStatus.AVAILABLE
                    )
                }
                if (unit.statusEnum() in listOf(UnitStatus.AVAILABLE, UnitStatus.RESERVED)) {
                    OutlinedButton(
                        onClick = onAssignTenant,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) {
                        Text("Onboard Tenant & Create Lease")
                    }
                }
                if (canEditPricingOrDelete && unit.statusEnum() != UnitStatus.OCCUPIED && unit.currentLeaseId.isNullOrBlank()) {
                    TextButton(
                        onClick = onRequestDelete,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("delete_unit_button")
                    ) {
                        Icon(Icons.Filled.DeleteOutline, contentDescription = null, tint = StatusError)
                        Spacer(Modifier.width(6.dp))
                        Text("Delete Unit", color = StatusError)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val rentCents = if (canEditPricingOrDelete) CurrencyUtils.parseKesToCents(rentText) else unit.rentCents
                    val depCents = if (canEditPricingOrDelete) {
                        if (depositText.isBlank()) 0L else CurrencyUtils.parseKesToCents(depositText)
                    } else unit.depositCents

                    if (rentCents == null) {
                        fieldErrors = mapOf("rentCents" to "Enter a valid monthly rent in KES")
                        err = "Enter a valid monthly rent in KES"
                        return@Button
                    }
                    if (depCents == null) {
                        fieldErrors = mapOf("depositCents" to "Enter a valid security deposit in KES")
                        err = "Enter a valid security deposit in KES"
                        return@Button
                    }

                    val combinedAmenities = (selectedAmenities.filter { it in COMMON_KENYAN_AMENITIES } +
                        customAmenitiesText.split(",").map { it.trim() })
                        .filter { it.isNotBlank() }
                        .distinct()

                    val input = PropertyUnitSchemas.RentalUnitUpdateInput(
                        propertyId = unit.propertyId,
                        unitId = unit.id,
                        unitNumber = unitNumber,
                        unitType = unitType.name,
                        rentCents = rentCents,
                        depositCents = depCents,
                        status = status.name,
                        amenities = combinedAmenities,
                        description = description,
                        imageRefs = unit.imageRefs,
                        published = published && status == UnitStatus.AVAILABLE
                    )
                    when (val validation = PropertyUnitSchemas.unitUpdateSchema.parse(input)) {
                        is ValidationResult.Invalid -> {
                            fieldErrors = validation.fieldMessages()
                            err = validation.errors.firstOrNull()?.message
                        }
                        is ValidationResult.Valid -> {
                            fieldErrors = emptyMap()
                            err = null
                            val clean = validation.value
                            onSave(
                                clean.unitNumber,
                                unitType,
                                clean.rentCents,
                                clean.depositCents,
                                status,
                                clean.amenities,
                                clean.description,
                                clean.imageRefs,
                                clean.published
                            )
                        }
                    }
                },
                enabled = !isBusy,
                modifier = Modifier.testTag("save_unit_changes_button")
            ) { Text("Save Changes") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
private fun ApplicationsTab(
    appsState: UiState<List<RentalApplication>>,
    units: List<RentalUnit>,
    onReview: (RentalApplication, ApplicationStatus, String?) -> Unit,
    onCreateLeaseFromApp: (RentalApplication) -> Unit
) {
    when (appsState) {
        is UiState.Loading -> LoadingStateBox("Loading applications...")
        is UiState.Error -> Box(Modifier.padding(16.dp)) { ErrorBannerCard(appsState.message) }
        is UiState.Success -> {
            val apps = appsState.data
            if (apps.isEmpty()) {
                Box(Modifier.padding(16.dp)) {
                    EmptyStateCard(
                        title = "No Rental Applications Yet",
                        message = "Published available units receive tenant applications directly here for review and lease onboarding."
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(apps, key = { it.id }) { app ->
                        val tone = when (app.statusEnum()) {
                            ApplicationStatus.APPROVED -> PillTone.SUCCESS
                            ApplicationStatus.REJECTED, ApplicationStatus.WITHDRAWN -> PillTone.ERROR
                            ApplicationStatus.UNDER_REVIEW -> PillTone.WARNING
                            ApplicationStatus.SUBMITTED -> PillTone.INFO
                        }
                        Card(
                            shape = MaterialTheme.shapes.medium,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(app.applicantName, style = MaterialTheme.typography.titleMedium)
                                        Text("${app.propertyName} • Unit ${app.unitNumber}", style = MaterialTheme.typography.bodySmall)
                                        Text("${app.applicantPhone} • Move-in ${app.desiredMoveInDate}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    StatusPill(app.statusEnum().label, tone)
                                }
                                if (!app.notes.isNullOrBlank()) {
                                    Text("\"${app.notes}\"", style = MaterialTheme.typography.bodySmall)
                                }
                                if (app.statusEnum() in listOf(ApplicationStatus.SUBMITTED, ApplicationStatus.UNDER_REVIEW)) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(
                                            onClick = { onReview(app, ApplicationStatus.APPROVED, "Approved & unit reserved") },
                                            colors = ButtonDefaults.buttonColors(containerColor = StatusSuccess),
                                            modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                                        ) { Text("Approve") }
                                        OutlinedButton(
                                            onClick = { onReview(app, ApplicationStatus.REJECTED, "Not selected at this time") },
                                            modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                                        ) { Text("Reject") }
                                    }
                                } else if (app.statusEnum() == ApplicationStatus.APPROVED) {
                                    val unitAvailable = units.any { it.id == app.unitId && it.statusEnum() != UnitStatus.OCCUPIED }
                                    if (unitAvailable) {
                                        Button(
                                            onClick = { onCreateLeaseFromApp(app) },
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                        ) { Text("Create Lease & Onboard Tenant") }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LeasesTab(
    leasesState: UiState<List<Lease>>,
    checklists: List<MoveInChecklist>,
    settlements: List<DepositSettlement>,
    canTerminateLease: (Lease) -> Boolean,
    canManageChecklist: (Lease) -> Boolean,
    canManageSettlement: (Lease) -> Boolean,
    onExportPdf: (Lease) -> Unit,
    onOpenChecklist: (Lease) -> Unit,
    onOpenSettlement: (Lease) -> Unit,
    onRenew: (Lease) -> Unit,
    onTerminate: (Lease) -> Unit
) {
    when (leasesState) {
        is UiState.Loading -> LoadingStateBox("Loading leases...")
        is UiState.Error -> Box(Modifier.padding(16.dp)) { ErrorBannerCard(leasesState.message) }
        is UiState.Success -> {
            val leases = leasesState.data
            if (leases.isEmpty()) {
                Box(Modifier.padding(16.dp)) {
                    EmptyStateCard(
                        title = "No Active Leases",
                        message = "Approve a rental application or select an available unit in the Units tab to create a lease agreement."
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(leases, key = { it.id }) { lease ->
                        val checklist = checklists.firstOrNull { it.leaseId == lease.id }
                        val settlement = settlements.firstOrNull { it.leaseId == lease.id }
                        val tone = when (lease.statusEnum()) {
                            LeaseStatus.ACTIVE, LeaseStatus.RENEWED -> PillTone.SUCCESS
                            LeaseStatus.EXPIRED -> PillTone.WARNING
                            LeaseStatus.TERMINATED -> PillTone.ERROR
                        }
                        Card(
                            shape = MaterialTheme.shapes.medium,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("${lease.tenantName} • ${lease.propertyName} (${lease.unitNumber})", style = MaterialTheme.typography.titleMedium)
                                        Text(
                                            "${CurrencyUtils.formatKes(lease.rentCents)}/mo • Deposit ${CurrencyUtils.formatKes(lease.depositCents)} • Due Day ${lease.dueDayOfMonth}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                        Text(
                                            "${CurrencyUtils.formatDate(lease.startDateEpochMs)} -> ${CurrencyUtils.formatDate(lease.endDateEpochMs)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    StatusPill(lease.statusEnum().label, tone)
                                }
                                if (checklist != null || settlement != null) {
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        checklist?.let { cl ->
                                            val clTone = when (cl.statusEnum()) {
                                                MoveInChecklistStatus.ACKNOWLEDGED -> PillTone.SUCCESS
                                                MoveInChecklistStatus.PENDING_TENANT_REVIEW -> PillTone.WARNING
                                                MoveInChecklistStatus.DISPUTED -> PillTone.ERROR
                                                MoveInChecklistStatus.DRAFT -> PillTone.INFO
                                            }
                                            StatusPill("Checklist: ${cl.statusEnum().label}", clTone)
                                        }
                                        settlement?.let { ds ->
                                            val dsTone = when (ds.statusEnum()) {
                                                DepositSettlementStatus.SETTLED, DepositSettlementStatus.APPROVED -> PillTone.SUCCESS
                                                DepositSettlementStatus.ISSUED -> PillTone.WARNING
                                                DepositSettlementStatus.DISPUTED -> PillTone.ERROR
                                                DepositSettlementStatus.DRAFT -> PillTone.INFO
                                            }
                                            val amountTxt = if (ds.balanceOwedCents > 0L) {
                                                "Owed ${CurrencyUtils.formatKes(ds.balanceOwedCents)}"
                                            } else {
                                                "Refund ${CurrencyUtils.formatKes(ds.netRefundCents)}"
                                            }
                                            StatusPill("Deposit: ${ds.statusEnum().label} • $amountTxt", dsTone)
                                        }
                                    }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(
                                        onClick = { onExportPdf(lease) },
                                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                                    ) {
                                        Icon(Icons.Filled.Description, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("PDF")
                                    }
                                    if (lease.statusEnum() == LeaseStatus.ACTIVE) {
                                        OutlinedButton(
                                            onClick = { onRenew(lease) },
                                            modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                                        ) { Text("Renew") }
                                        if (canTerminateLease(lease)) {
                                            TextButton(
                                                onClick = { onTerminate(lease) },
                                                modifier = Modifier.heightIn(min = 48.dp).testTag("terminate_lease_button_${lease.id}")
                                            ) { Text("End", color = StatusError) }
                                        }
                                    }
                                }
                                if (canManageChecklist(lease) || canManageSettlement(lease)) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                        if (canManageChecklist(lease)) {
                                            OutlinedButton(
                                                onClick = { onOpenChecklist(lease) },
                                                modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("lease_checklist_button_${lease.id}")
                                            ) {
                                                Icon(Icons.Filled.Checklist, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text(
                                                    when (checklist?.statusEnum()) {
                                                        MoveInChecklistStatus.DISPUTED -> "Resolve Checklist"
                                                        MoveInChecklistStatus.ACKNOWLEDGED -> "Signed Checklist"
                                                        MoveInChecklistStatus.PENDING_TENANT_REVIEW -> "Pending Sign-Off"
                                                        MoveInChecklistStatus.DRAFT -> "Edit Checklist"
                                                        null -> "Move-In Checklist"
                                                    },
                                                    maxLines = 1
                                                )
                                            }
                                        }
                                        if (canManageSettlement(lease)) {
                                            OutlinedButton(
                                                onClick = { onOpenSettlement(lease) },
                                                modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("lease_settlement_button_${lease.id}")
                                            ) {
                                                Icon(Icons.Filled.AccountBalanceWallet, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text(
                                                    when (settlement?.statusEnum()) {
                                                        DepositSettlementStatus.DISPUTED -> "Resolve Deposit"
                                                        DepositSettlementStatus.APPROVED -> "Disburse Refund"
                                                        DepositSettlementStatus.SETTLED -> "Settled Ledger"
                                                        DepositSettlementStatus.ISSUED -> "Issued Deposit"
                                                        DepositSettlementStatus.DRAFT -> "Edit Settlement"
                                                        null -> "Settle Deposit"
                                                    },
                                                    maxLines = 1
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun PropertyEditorDialog(
    organizations: List<Organization>,
    initialOrgId: String,
    ownerUid: String,
    existingProperty: Property?,
    openLocationPickerInitially: Boolean = false,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onSave: (
        orgId: String,
        name: String,
        category: PropertyCategory,
        city: String,
        county: String,
        neighborhood: String,
        addressLine: String,
        description: String,
        waterMeterNumber: String?,
        electricityMeterNumber: String?,
        notes: String?,
        imageRefs: List<String>,
        mpesaPaybill: String?,
        mpesaTill: String?,
        mpesaAccountPrefix: String?,
        latitude: Double?,
        longitude: Double?,
        formattedAddress: String?,
        placeId: String?,
        locationVerified: Boolean
    ) -> Unit
) {
    val isEditing = existingProperty != null
    var selectedOrgId by remember(initialOrgId) { mutableStateOf(existingProperty?.orgId ?: initialOrgId) }
    var orgExpanded by remember { mutableStateOf(false) }
    var name by remember(existingProperty?.id) { mutableStateOf(existingProperty?.name ?: "") }
    var category by remember(existingProperty?.id) { mutableStateOf(existingProperty?.categoryEnum() ?: PropertyCategory.APARTMENTS) }
    var city by remember(existingProperty?.id) { mutableStateOf(existingProperty?.city ?: "Nairobi") }
    var county by remember(existingProperty?.id) { mutableStateOf(existingProperty?.county ?: "Nairobi") }
    var countyExpanded by remember { mutableStateOf(false) }
    var neighborhood by remember(existingProperty?.id) { mutableStateOf(existingProperty?.neighborhood ?: "") }
    var address by remember(existingProperty?.id) { mutableStateOf(existingProperty?.addressLine ?: "") }
    var latitude by remember(existingProperty?.id) { mutableStateOf(existingProperty?.latitude) }
    var longitude by remember(existingProperty?.id) { mutableStateOf(existingProperty?.longitude) }
    var formattedAddress by remember(existingProperty?.id) { mutableStateOf(existingProperty?.formattedAddress) }
    var placeId by remember(existingProperty?.id) { mutableStateOf(existingProperty?.placeId) }
    var locationVerified by remember(existingProperty?.id) { mutableStateOf(existingProperty?.locationVerified ?: false) }
    var showLocationPicker by remember(existingProperty?.id) { mutableStateOf(openLocationPickerInitially) }
    var waterMeter by remember(existingProperty?.id) { mutableStateOf(existingProperty?.waterMeterNumber ?: "") }
    var powerMeter by remember(existingProperty?.id) { mutableStateOf(existingProperty?.electricityMeterNumber ?: "") }
    var mpesaPaybill by remember(existingProperty?.id) { mutableStateOf(existingProperty?.mpesaPaybill ?: "") }
    var mpesaTill by remember(existingProperty?.id) { mutableStateOf(existingProperty?.mpesaTill ?: "") }
    var mpesaAccountPrefix by remember(existingProperty?.id) { mutableStateOf(existingProperty?.mpesaAccountPrefix ?: "") }
    var desc by remember(existingProperty?.id) { mutableStateOf(existingProperty?.description ?: "") }
    var notes by remember(existingProperty?.id) { mutableStateOf(existingProperty?.notes ?: "") }
    val imageUris = remember(existingProperty?.id) {
        mutableStateListOf<String>().apply {
            existingProperty?.imageRefs?.let { addAll(it) }
        }
    }
    var catExpanded by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    var fieldErrors by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    val selectedOrg = organizations.firstOrNull { it.id == selectedOrgId } ?: organizations.firstOrNull()

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null && imageUris.size < 6) imageUris.add(uri.toString())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEditing) "Edit ${existingProperty?.name}" else "Register Property") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                if (!err.isNullOrBlank()) ErrorBannerCard(err!!)
                if (!isEditing && organizations.size > 1) {
                    ExposedDropdownMenuBox(expanded = orgExpanded, onExpandedChange = { orgExpanded = !orgExpanded }) {
                        OutlinedTextField(
                            value = selectedOrg?.name.orEmpty(),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Organization") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(orgExpanded) },
                            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true).fillMaxWidth()
                        )
                        ExposedDropdownMenu(expanded = orgExpanded, onDismissRequest = { orgExpanded = false }) {
                            organizations.forEach { org ->
                                DropdownMenuItem(
                                    text = { Text(org.name) },
                                    onClick = { selectedOrgId = org.id; orgExpanded = false }
                                )
                            }
                        }
                    }
                } else if (selectedOrg != null) {
                    Text(
                        text = "Organization: ${selectedOrg.name}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; fieldErrors = fieldErrors - "name"; err = null },
                    label = { Text("Property Name") },
                    isError = fieldErrors.containsKey("name"),
                    supportingText = fieldErrors["name"]?.let { msg -> { Text(msg) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("property_name_input")
                )
                ExposedDropdownMenuBox(expanded = catExpanded, onExpandedChange = { catExpanded = !catExpanded }) {
                    OutlinedTextField(
                        value = category.label,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Category") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(catExpanded) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true).fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = catExpanded, onDismissRequest = { catExpanded = false }) {
                        PropertyCategory.entries.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat.label) },
                                onClick = { category = cat; catExpanded = false }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = city,
                    onValueChange = { city = it; fieldErrors = fieldErrors - "city"; err = null },
                    label = { Text("City (e.g. Nairobi, Eldoret, Kisumu)") },
                    isError = fieldErrors.containsKey("city"),
                    supportingText = fieldErrors["city"]?.let { msg -> { Text(msg) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("property_city_input")
                )
                ExposedDropdownMenuBox(expanded = countyExpanded, onExpandedChange = { countyExpanded = !countyExpanded }) {
                    OutlinedTextField(
                        value = county,
                        onValueChange = { county = it; fieldErrors = fieldErrors - "county" },
                        label = { Text("Kenyan County") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(countyExpanded) },
                        isError = fieldErrors.containsKey("county"),
                        supportingText = fieldErrors["county"]?.let { msg -> { Text(msg) } },
                        singleLine = true,
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryEditable, enabled = true)
                            .fillMaxWidth()
                            .testTag("property_county_input")
                    )
                    ExposedDropdownMenu(expanded = countyExpanded, onDismissRequest = { countyExpanded = false }) {
                        KenyaCounties.MAJOR_COUNTIES.forEach { preset ->
                            DropdownMenuItem(
                                text = { Text(preset) },
                                onClick = {
                                    county = preset
                                    fieldErrors = fieldErrors - "county"
                                    countyExpanded = false
                                }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = neighborhood,
                    onValueChange = { neighborhood = it; fieldErrors = fieldErrors - "neighborhood" },
                    label = { Text("Estate / Neighborhood") },
                    isError = fieldErrors.containsKey("neighborhood"),
                    supportingText = fieldErrors["neighborhood"]?.let { msg -> { Text(msg) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("property_neighborhood_input")
                )
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it; fieldErrors = fieldErrors - "addressLine" },
                    label = { Text("Street / Road") },
                    isError = fieldErrors.containsKey("addressLine"),
                    supportingText = fieldErrors["addressLine"]?.let { msg -> { Text(msg) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("property_address_input")
                )
                Card(
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Google Maps Building Pin", style = MaterialTheme.typography.labelLarge)
                            StatusPill(
                                label = if (locationVerified && BuildingLocationResolver.hasValidCoordinates(latitude, longitude)) {
                                    "Exact Map Pin"
                                } else {
                                    "Approximate"
                                },
                                tone = if (locationVerified && BuildingLocationResolver.hasValidCoordinates(latitude, longitude)) {
                                    PillTone.SUCCESS
                                } else {
                                    PillTone.WARNING
                                }
                            )
                        }
                        if (BuildingLocationResolver.hasValidCoordinates(latitude, longitude)) {
                            Text(
                                text = formattedAddress ?: BuildingLocationResolver.formatCoordinates(latitude!!, longitude!!),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "GPS: ${BuildingLocationResolver.formatCoordinates(latitude!!, longitude!!)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        } else {
                            Text(
                                text = "No exact GPS pin saved yet. Renters will see the $city center until you pin the building.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        OutlinedButton(
                            onClick = { showLocationPicker = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("open_location_picker_button")
                        ) {
                            Icon(Icons.Filled.EditLocationAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(if (BuildingLocationResolver.hasValidCoordinates(latitude, longitude)) "Update Building Pin on Map" else "Set Building Pin on Map")
                        }
                    }
                }
                OutlinedTextField(
                    value = waterMeter,
                    onValueChange = { waterMeter = it; fieldErrors = fieldErrors - "waterMeterNumber" },
                    label = { Text("Water Meter No. (Optional)") },
                    isError = fieldErrors.containsKey("waterMeterNumber"),
                    supportingText = fieldErrors["waterMeterNumber"]?.let { msg -> { Text(msg) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("property_water_meter_input")
                )
                OutlinedTextField(
                    value = powerMeter,
                    onValueChange = { powerMeter = it; fieldErrors = fieldErrors - "electricityMeterNumber" },
                    label = { Text("Electricity / KPLC Meter No. (Optional)") },
                    isError = fieldErrors.containsKey("electricityMeterNumber"),
                    supportingText = fieldErrors["electricityMeterNumber"]?.let { msg -> { Text(msg) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("property_power_meter_input")
                )
                Text("Kenya M-Pesa Rent Collection (Optional)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                OutlinedTextField(
                    value = mpesaPaybill,
                    onValueChange = { mpesaPaybill = it.take(20) },
                    label = { Text("M-Pesa Paybill No.") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("property_mpesa_paybill_input")
                )
                OutlinedTextField(
                    value = mpesaTill,
                    onValueChange = { mpesaTill = it.take(20) },
                    label = { Text("M-Pesa Till / Buy Goods No.") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("property_mpesa_till_input")
                )
                OutlinedTextField(
                    value = mpesaAccountPrefix,
                    onValueChange = { mpesaAccountPrefix = it.take(24) },
                    label = { Text("Account Ref Prefix (e.g. KH-)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("property_mpesa_prefix_input")
                )
                OutlinedTextField(
                    value = desc,
                    onValueChange = { desc = it; fieldErrors = fieldErrors - "description" },
                    label = { Text("Description") },
                    isError = fieldErrors.containsKey("description"),
                    supportingText = fieldErrors["description"]?.let { msg -> { Text(msg) } },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth().testTag("property_description_input")
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it; fieldErrors = fieldErrors - "notes" },
                    label = { Text("Operational Notes (Optional)") },
                    isError = fieldErrors.containsKey("notes"),
                    supportingText = fieldErrors["notes"]?.let { msg -> { Text(msg) } },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth().testTag("property_notes_input")
                )
                OutlinedButton(
                    onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Filled.PhotoLibrary, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Attach Photo (${imageUris.size}/6)")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val targetOrgId = selectedOrg?.id.orEmpty()
                    if (existingProperty != null) {
                        val input = PropertyUnitSchemas.PropertyUpdateInput(
                            propertyId = existingProperty.id,
                            name = name,
                            category = category.name,
                            city = city,
                            county = county,
                            neighborhood = neighborhood,
                            addressLine = address,
                            description = desc,
                            waterMeterNumber = waterMeter.takeIf { it.isNotBlank() },
                            electricityMeterNumber = powerMeter.takeIf { it.isNotBlank() },
                            notes = notes.takeIf { it.isNotBlank() },
                            imageRefs = imageUris.toList(),
                            archived = existingProperty.archived,
                            latitude = latitude,
                            longitude = longitude,
                            formattedAddress = formattedAddress,
                            placeId = placeId,
                            locationVerified = locationVerified
                        )
                        when (val validation = PropertyUnitSchemas.propertyUpdateSchema.parse(input)) {
                            is ValidationResult.Invalid -> {
                                fieldErrors = validation.fieldMessages()
                                err = validation.errors.firstOrNull()?.message
                            }
                            is ValidationResult.Valid -> {
                                fieldErrors = emptyMap()
                                err = null
                                val clean = validation.value
                                onSave(
                                    existingProperty.orgId,
                                    clean.name,
                                    category,
                                    clean.city,
                                    clean.county,
                                    clean.neighborhood,
                                    clean.addressLine,
                                    clean.description,
                                    clean.waterMeterNumber,
                                    clean.electricityMeterNumber,
                                    clean.notes,
                                    clean.imageRefs,
                                    mpesaPaybill.trim().takeIf { it.isNotBlank() },
                                    mpesaTill.trim().takeIf { it.isNotBlank() },
                                    mpesaAccountPrefix.trim().takeIf { it.isNotBlank() },
                                    clean.latitude,
                                    clean.longitude,
                                    clean.formattedAddress,
                                    clean.placeId,
                                    clean.locationVerified
                                )
                            }
                        }
                    } else {
                        val candidate = Property(
                            orgId = targetOrgId,
                            ownerUid = ownerUid,
                            authorizedUids = listOf(ownerUid),
                            name = name,
                            category = category.name,
                            city = city,
                            county = county,
                            neighborhood = neighborhood,
                            addressLine = address,
                            description = desc,
                            waterMeterNumber = waterMeter.takeIf { it.isNotBlank() },
                            electricityMeterNumber = powerMeter.takeIf { it.isNotBlank() },
                            notes = notes.takeIf { it.isNotBlank() },
                            imageRefs = imageUris.toList(),
                            latitude = latitude,
                            longitude = longitude,
                            formattedAddress = formattedAddress,
                            placeId = placeId,
                            locationVerified = locationVerified
                        )
                        when (val validation = PropertyUnitSchemas.propertyCreateSchema.parse(candidate)) {
                            is ValidationResult.Invalid -> {
                                fieldErrors = validation.fieldMessages()
                                err = validation.errors.firstOrNull()?.message
                            }
                            is ValidationResult.Valid -> {
                                fieldErrors = emptyMap()
                                err = null
                                val clean = validation.value
                                onSave(
                                    clean.orgId,
                                    clean.name,
                                    category,
                                    clean.city,
                                    clean.county,
                                    clean.neighborhood,
                                    clean.addressLine,
                                    clean.description,
                                    clean.waterMeterNumber,
                                    clean.electricityMeterNumber,
                                    clean.notes,
                                    clean.imageRefs,
                                    mpesaPaybill.trim().takeIf { it.isNotBlank() },
                                    mpesaTill.trim().takeIf { it.isNotBlank() },
                                    mpesaAccountPrefix.trim().takeIf { it.isNotBlank() },
                                    clean.latitude,
                                    clean.longitude,
                                    clean.formattedAddress,
                                    clean.placeId,
                                    clean.locationVerified
                                )
                            }
                        }
                    }
                },
                enabled = !isBusy,
                modifier = Modifier.testTag("register_property_submit_button")
            ) { Text(if (isEditing) "Save Changes" else "Register") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )

    if (showLocationPicker) {
        BuildingLocationPickerDialog(
            initialPropertyName = name,
            initialCity = city,
            initialCounty = county,
            initialNeighborhood = neighborhood,
            initialAddressLine = address,
            initialLatitude = latitude,
            initialLongitude = longitude,
            initialFormattedAddress = formattedAddress,
            initialPlaceId = placeId,
            onDismiss = { showLocationPicker = false },
            onConfirmLocation = { picked ->
                latitude = picked.latitude
                longitude = picked.longitude
                formattedAddress = picked.formattedAddress
                placeId = picked.placeId
                locationVerified = true
                if (!picked.city.isNullOrBlank()) {
                    city = picked.city
                    fieldErrors = fieldErrors - "city"
                }
                if (!picked.county.isNullOrBlank()) {
                    county = picked.county
                    fieldErrors = fieldErrors - "county"
                }
                if (!picked.neighborhood.isNullOrBlank() && neighborhood.isBlank()) {
                    neighborhood = picked.neighborhood
                    fieldErrors = fieldErrors - "neighborhood"
                }
                if (!picked.addressLine.isNullOrBlank() && address.isBlank()) {
                    address = picked.addressLine
                    fieldErrors = fieldErrors - "addressLine"
                }
                showLocationPicker = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AddUnitDialog(
    property: Property,
    isBusy: Boolean,
    aiDraftState: AiTaskState,
    onDraftWithAi: (String, String, Long, Long, List<String>) -> Unit,
    onClearAiDraft: () -> Unit,
    onDismiss: () -> Unit,
    onSave: (String, UnitType, Long, Long, UnitStatus, List<String>, String, List<String>, Boolean) -> Unit
) {
    var unitNumber by remember { mutableStateOf("") }
    var unitType by remember { mutableStateOf(UnitType.ONE_BEDROOM) }
    var rentText by remember { mutableStateOf("") }
    var depositText by remember { mutableStateOf("") }
    var selectedAmenities by remember { mutableStateOf(setOf("Wi-Fi", "Borehole Water", "Security / CCTV")) }
    var customAmenitiesText by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var published by remember { mutableStateOf(true) }
    var typeExpanded by remember { mutableStateOf(false) }
    val imageUris = remember { mutableStateListOf<String>() }
    var err by remember { mutableStateOf<String?>(null) }
    var fieldErrors by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    LaunchedEffect(aiDraftState) {
        when (aiDraftState) {
            is AiTaskState.Streaming -> desc = aiDraftState.text
            is AiTaskState.Success -> desc = aiDraftState.text
            else -> {}
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null && imageUris.size < 6) imageUris.add(uri.toString())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Unit • ${property.name}") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                if (!err.isNullOrBlank()) ErrorBannerCard(err!!)
                if (aiDraftState is AiTaskState.Error) ErrorBannerCard(aiDraftState.message)
                OutlinedTextField(
                    value = unitNumber,
                    onValueChange = { unitNumber = it; fieldErrors = fieldErrors - "unitNumber"; err = null },
                    label = { Text("Unit Number (e.g. A1, B-204)") },
                    isError = fieldErrors.containsKey("unitNumber"),
                    supportingText = fieldErrors["unitNumber"]?.let { msg -> { Text(msg) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("add_unit_number_input")
                )
                ExposedDropdownMenuBox(expanded = typeExpanded, onExpandedChange = { typeExpanded = !typeExpanded }) {
                    OutlinedTextField(
                        value = unitType.label,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Unit Type") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(typeExpanded) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true).fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = typeExpanded, onDismissRequest = { typeExpanded = false }) {
                        UnitType.entries.forEach { t ->
                            DropdownMenuItem(text = { Text(t.label) }, onClick = { unitType = t; typeExpanded = false })
                        }
                    }
                }
                OutlinedTextField(
                    value = rentText,
                    onValueChange = { rentText = it; fieldErrors = fieldErrors - "rentCents"; err = null },
                    label = { Text("Monthly Rent (KES)") },
                    isError = fieldErrors.containsKey("rentCents"),
                    supportingText = fieldErrors["rentCents"]?.let { msg -> { Text(msg) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("add_unit_rent_input")
                )
                OutlinedTextField(
                    value = depositText,
                    onValueChange = { depositText = it; fieldErrors = fieldErrors - "depositCents"; err = null },
                    label = { Text("Deposit (KES)") },
                    isError = fieldErrors.containsKey("depositCents"),
                    supportingText = fieldErrors["depositCents"]?.let { msg -> { Text(msg) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("add_unit_deposit_input")
                )
                Text("Amenities", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    COMMON_KENYAN_AMENITIES.forEach { amenity ->
                        val active = selectedAmenities.contains(amenity)
                        FilterChip(
                            selected = active,
                            onClick = {
                                selectedAmenities = if (active) selectedAmenities - amenity else selectedAmenities + amenity
                            },
                            label = { Text(amenity) }
                        )
                    }
                }
                OutlinedTextField(
                    value = customAmenitiesText,
                    onValueChange = { customAmenitiesText = it; fieldErrors = fieldErrors - "amenities" },
                    label = { Text("Other Amenities (comma separated)") },
                    isError = fieldErrors.containsKey("amenities"),
                    supportingText = fieldErrors["amenities"]?.let { msg -> { Text(msg) } },
                    singleLine = true
                )
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Description", style = MaterialTheme.typography.labelMedium)
                    TextButton(
                        onClick = {
                            val amenities = (selectedAmenities.toList() + customAmenitiesText.split(",").map { it.trim() }).filter { it.isNotBlank() }.distinct()
                            val rentCents = CurrencyUtils.parseKesToCents(rentText) ?: 0L
                            val depCents = CurrencyUtils.parseKesToCents(depositText) ?: 0L
                            onDraftWithAi(unitNumber.ifBlank { "New Unit" }, unitType.label, rentCents, depCents, amenities)
                        },
                        enabled = aiDraftState !is AiTaskState.Loading,
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (aiDraftState is AiTaskState.Loading) "Drafting..." else "Draft with AI")
                    }
                }
                OutlinedTextField(
                    value = desc,
                    onValueChange = { desc = it; fieldErrors = fieldErrors - "description" },
                    label = { Text("Description") },
                    isError = fieldErrors.containsKey("description"),
                    supportingText = fieldErrors["description"]?.let { msg -> { Text(msg) } },
                    maxLines = 3
                )
                OutlinedButton(
                    onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Filled.PhotoLibrary, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Add Unit Photo (${imageUris.size}/6)")
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("Publish to Public Discovery")
                    Switch(checked = published, onCheckedChange = { published = it })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val rentCents = CurrencyUtils.parseKesToCents(rentText)
                    val depCents = if (depositText.isBlank()) 0L else CurrencyUtils.parseKesToCents(depositText)
                    if (rentCents == null) {
                        fieldErrors = mapOf("rentCents" to "Enter valid monthly rent in KES")
                        err = "Enter valid monthly rent in KES"
                        return@Button
                    }
                    if (depCents == null) {
                        fieldErrors = mapOf("depositCents" to "Enter valid security deposit in KES")
                        err = "Enter valid security deposit in KES"
                        return@Button
                    }
                    val amenities = (selectedAmenities.toList() + customAmenitiesText.split(",").map { it.trim() })
                        .filter { it.isNotBlank() }
                        .distinct()
                    val candidate = RentalUnit(
                        propertyId = property.id,
                        propertyName = property.name,
                        propertyCity = property.city,
                        propertyCategory = property.category,
                        orgId = property.orgId,
                        ownerUid = property.ownerUid,
                        managerUids = property.managerUids,
                        authorizedUids = property.authorizedUids,
                        unitNumber = unitNumber,
                        unitType = unitType.name,
                        rentCents = rentCents,
                        depositCents = depCents,
                        status = UnitStatus.AVAILABLE.name,
                        amenities = amenities,
                        description = desc,
                        imageRefs = imageUris.toList(),
                        published = published
                    )
                    when (val validation = PropertyUnitSchemas.unitCreateSchema.parse(candidate)) {
                        is ValidationResult.Invalid -> {
                            fieldErrors = validation.fieldMessages()
                            err = validation.errors.firstOrNull()?.message
                        }
                        is ValidationResult.Valid -> {
                            fieldErrors = emptyMap()
                            err = null
                            val clean = validation.value
                            onClearAiDraft()
                            onSave(
                                clean.unitNumber,
                                unitType,
                                clean.rentCents,
                                clean.depositCents,
                                UnitStatus.AVAILABLE,
                                clean.amenities,
                                clean.description,
                                clean.imageRefs,
                                clean.published
                            )
                        }
                    }
                },
                enabled = !isBusy,
                modifier = Modifier.testTag("add_unit_submit_button")
            ) { Text("Add Unit") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun AssignManagerDialog(
    property: Property,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onAssign: (String) -> Unit
) {
    var managerUid by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Assign Property Manager") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Enter the Property Manager's RentFlow User ID (found in their Profile screen) to grant operational access to ${property.name}.")
                if (!err.isNullOrBlank()) ErrorBannerCard(err!!)
                OutlinedTextField(
                    value = managerUid,
                    onValueChange = { managerUid = it },
                    label = { Text("Manager User ID (UID)") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (managerUid.trim().length < 4) err = "Enter a valid Manager UID"
                    else onAssign(managerUid.trim())
                },
                enabled = !isBusy
            ) { Text("Assign") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun CreateLeaseDialog(
    unit: RentalUnit,
    preselectedApp: RentalApplication?,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onCreate: (String, String, String, Long, Long, Long, Long, Long, String) -> Unit
) {
    var tenantUid by remember { mutableStateOf(preselectedApp?.applicantUid ?: "") }
    var tenantName by remember { mutableStateOf(preselectedApp?.applicantName ?: "") }
    var tenantPhone by remember { mutableStateOf(preselectedApp?.applicantPhone ?: "+254") }
    var rentText by remember { mutableStateOf(CurrencyUtils.centsToInputString(unit.rentCents)) }
    var depositText by remember { mutableStateOf(CurrencyUtils.centsToInputString(unit.depositCents)) }
    var durationMonths by remember { mutableStateOf("12") }
    var dueDay by remember { mutableStateOf("5") }
    var terms by remember { mutableStateOf("Rent payable by the 5th of each month via M-Pesa or bank transfer. 1 month notice required before vacation.") }
    var err by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Lease • Unit ${unit.unitNumber}") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                if (!err.isNullOrBlank()) ErrorBannerCard(err!!)
                OutlinedTextField(value = tenantUid, onValueChange = { tenantUid = it }, label = { Text("Tenant User ID (UID)") }, singleLine = true)
                OutlinedTextField(value = tenantName, onValueChange = { tenantName = it }, label = { Text("Tenant Full Name") }, singleLine = true)
                OutlinedTextField(value = tenantPhone, onValueChange = { tenantPhone = it }, label = { Text("Tenant Phone (+254...)") }, singleLine = true)
                OutlinedTextField(value = rentText, onValueChange = { rentText = it }, label = { Text("Monthly Rent (KES)") }, singleLine = true)
                OutlinedTextField(value = depositText, onValueChange = { depositText = it }, label = { Text("Deposit (KES)") }, singleLine = true)
                OutlinedTextField(value = durationMonths, onValueChange = { durationMonths = it }, label = { Text("Lease Duration (Months)") }, singleLine = true)
                OutlinedTextField(value = dueDay, onValueChange = { dueDay = it }, label = { Text("Rent Due Day (1-28)") }, singleLine = true)
                OutlinedTextField(value = terms, onValueChange = { terms = it }, label = { Text("Tenancy Terms") }, maxLines = 3)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val rentCents = CurrencyUtils.parseKesToCents(rentText)
                    val depCents = CurrencyUtils.parseKesToCents(depositText) ?: 0L
                    val months = durationMonths.toIntOrNull()?.coerceIn(1, 120) ?: 12
                    val day = dueDay.toLongOrNull()?.coerceIn(1L, 28L) ?: 5L
                    val startMs = System.currentTimeMillis()
                    val endMs = startMs + months * 30L * 86_400_000L
                    when {
                        tenantUid.trim().length < 4 -> err = "Enter Tenant User ID (found in Tenant's Profile)"
                        tenantName.trim().length < 2 -> err = "Enter Tenant Name"
                        !CurrencyUtils.isValidKenyanPhone(tenantPhone) -> err = "Enter valid Kenyan phone"
                        rentCents == null || rentCents <= 0L -> err = "Enter valid rent amount"
                        else -> onCreate(
                            tenantUid.trim(),
                            tenantName.trim(),
                            CurrencyUtils.normalizeKenyanPhone(tenantPhone),
                            rentCents,
                            depCents,
                            startMs,
                            endMs,
                            day,
                            terms
                        )
                    }
                },
                enabled = !isBusy
            ) { Text("Activate Lease") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun RenewLeaseDialog(
    lease: Lease,
    canEditRent: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Long, Long) -> Unit
) {
    var monthsText by remember { mutableStateOf("12") }
    var rentText by remember { mutableStateOf(CurrencyUtils.centsToInputString(lease.rentCents)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Renew Lease • ${lease.unitNumber}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = monthsText, onValueChange = { monthsText = it }, label = { Text("Extend By (Months)") }, singleLine = true)
                OutlinedTextField(
                    value = rentText,
                    onValueChange = { rentText = it },
                    label = { Text("New Monthly Rent (KES)") },
                    enabled = canEditRent,
                    supportingText = if (!canEditRent) { { Text("Owner only") } } else null,
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val months = monthsText.toIntOrNull()?.coerceIn(1, 120) ?: 12
                val rent = if (canEditRent) (CurrencyUtils.parseKesToCents(rentText) ?: lease.rentCents) else lease.rentCents
                val baseEnd = maxOf(lease.endDateEpochMs, System.currentTimeMillis())
                onConfirm(baseEnd + months * 30L * 86_400_000L, rent)
            }) { Text("Renew Lease") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InviteTeamMemberDialog(
    org: Organization,
    properties: List<Property>,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onSendInvite: (email: String, role: UserRole, propertyIds: List<String>) -> Unit
) {
    var email by remember { mutableStateOf("") }
    var selectedRole by remember { mutableStateOf(UserRole.PROPERTY_MANAGER) }
    var selectedPropIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var err by remember { mutableStateOf<String?>(null) }

    val invitableRoles = remember {
        listOf(UserRole.PROPERTY_MANAGER, UserRole.MAINTENANCE_STAFF, UserRole.TENANT)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Invite Team Member • ${org.name}") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Text(
                    "The invited user will see this invitation in their Profile screen and can accept it using their verified Google email.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!err.isNullOrBlank()) ErrorBannerCard(err!!)
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it; err = null },
                    label = { Text("Invitee Google Email") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("invite_email_input")
                )
                Text("Role to Grant", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    invitableRoles.forEach { role ->
                        FilterChip(
                            selected = selectedRole == role,
                            onClick = { selectedRole = role },
                            label = { Text(role.label) },
                            modifier = Modifier.testTag("invite_role_${role.name.lowercase()}")
                        )
                    }
                }
                if (properties.isNotEmpty() && selectedRole == UserRole.PROPERTY_MANAGER) {
                    Text("Property Scope (Optional)", style = MaterialTheme.typography.labelMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        properties.forEach { prop ->
                            val selected = prop.id in selectedPropIds
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    selectedPropIds = if (selected) selectedPropIds - prop.id else selectedPropIds + prop.id
                                },
                                label = { Text(prop.name, maxLines = 1) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val clean = email.trim().lowercase()
                    if (clean.length < 4 || "@" !in clean || "." !in clean.substringAfter("@")) {
                        err = "Enter a valid Google email address"
                    } else {
                        onSendInvite(clean, selectedRole, selectedPropIds.toList())
                    }
                },
                enabled = !isBusy,
                modifier = Modifier.testTag("send_invite_submit_button")
            ) { Text("Send Invite") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
