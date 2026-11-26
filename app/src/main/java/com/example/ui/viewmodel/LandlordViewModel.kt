package com.example.ui.viewmodel

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ai.AiAssistantRepository
import com.example.data.ai.AiTaskState
import com.example.data.ai.MaintenanceTriageSuggestion
import com.example.data.ai.ScanOcrUiState
import com.example.data.repository.DocumentScannerRepository
import com.example.data.repository.FinanceRepository
import com.example.data.repository.HandoverRepository
import com.example.data.repository.MoveInChecklistRepository
import com.example.data.repository.OperationsRepository
import com.example.data.repository.PropertyRepository
import com.example.domain.model.ChecklistItem
import com.example.domain.model.ChecklistOverviewBucket
import com.example.domain.model.ChecklistOverviewEngine
import com.example.domain.model.ChecklistOverviewSummary
import com.example.domain.model.DepositDeductionItem
import com.example.domain.model.DepositSettlement
import com.example.domain.model.DepositSettlementStatus
import com.example.domain.model.DigitalSignature
import com.example.domain.model.DocumentType
import com.example.domain.model.InspectionRoom
import com.example.domain.model.MoveInChecklist
import com.example.domain.model.MoveInConditionChecklist
import com.example.domain.model.RefundMethod
import com.example.domain.model.ScanFilterMode
import com.example.domain.model.ScannedDocument
import com.example.domain.model.UserRole
import com.example.domain.finance.CurrencyUtils
import com.example.domain.finance.FinancialEngine
import com.example.domain.finance.LeaseAutomationEngine
import com.example.domain.finance.MonthlyCashFlowPoint
import com.example.domain.finance.PortfolioSummary
import com.example.domain.model.Announcement
import com.example.domain.model.ApplicationStatus
import com.example.domain.model.EscalationType
import com.example.domain.model.Expense
import com.example.domain.model.ExpenseCategory
import com.example.domain.model.Invoice
import com.example.domain.model.Lease
import com.example.domain.model.MaintenanceRequest
import com.example.domain.model.MaintenanceStatus
import com.example.domain.model.Payment
import com.example.domain.model.PaymentChannelType
import com.example.domain.model.PaymentMethod
import com.example.domain.model.Property
import com.example.domain.model.PropertyCategory
import com.example.domain.model.RenewalMode
import com.example.domain.model.RentalApplication
import com.example.domain.model.RentalUnit
import com.example.domain.model.UnitStatus
import com.example.domain.model.UnitType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UnitMatrixFilterState(
    val status: UnitStatus? = null,
    val propertyId: String? = null,
    val searchQuery: String = ""
)

data class ChecklistOverviewFilterState(
    val bucket: ChecklistOverviewBucket? = null,
    val propertyId: String? = null,
    val searchQuery: String = ""
)

sealed interface AiTriageUiState {
    data object Idle : AiTriageUiState
    data object Loading : AiTriageUiState
    data class Success(val suggestion: MaintenanceTriageSuggestion) : AiTriageUiState
    data class Error(val message: String) : AiTriageUiState
}

class LandlordViewModel(
    private val propertyRepo: PropertyRepository,
    private val financeRepo: FinanceRepository,
    private val opsRepo: OperationsRepository,
    private val currentUserId: String,
    private val aiRepo: AiAssistantRepository = AiAssistantRepository(),
    private val handoverRepo: HandoverRepository? = null,
    private val conditionChecklistRepo: MoveInChecklistRepository? = null,
    private val documentRepo: DocumentScannerRepository? = null
) : ViewModel() {

    private val _activeOrgId = MutableStateFlow<String?>(null)
    val activeOrgId: StateFlow<String?> = _activeOrgId.asStateFlow()

    fun setActiveOrgId(orgId: String?) {
        val clean = orgId?.trim()?.takeIf { it.isNotBlank() }
        if (_activeOrgId.value != clean) {
            _activeOrgId.value = clean
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val properties: StateFlow<UiState<List<Property>>> = _activeOrgId
        .flatMapLatest { orgId ->
            if (orgId.isNullOrBlank()) {
                flowOf(emptyList())
            } else {
                propertyRepo.observePropertiesForOrganization(orgId, currentUserId)
            }
        }
        .asUiState("LandlordVM", "Failed to load properties")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    @OptIn(ExperimentalCoroutinesApi::class)
    val units: StateFlow<UiState<List<RentalUnit>>> = _activeOrgId
        .flatMapLatest { orgId ->
            if (orgId.isNullOrBlank()) {
                flowOf(emptyList())
            } else {
                propertyRepo.observeUnitsForOrganization(orgId, currentUserId)
            }
        }
        .asUiState("LandlordVM", "Failed to load units")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    private val _unitFilterState = MutableStateFlow(UnitMatrixFilterState())
    val unitFilterState: StateFlow<UnitMatrixFilterState> = _unitFilterState.asStateFlow()

    val filteredUnits: StateFlow<UiState<List<RentalUnit>>> = combine(units, _unitFilterState) { unitState, filter ->
        when (unitState) {
            is UiState.Loading -> UiState.Loading
            is UiState.Error -> unitState
            is UiState.Success -> {
                val query = filter.searchQuery.trim().lowercase()
                val filtered = unitState.data.filter { unit ->
                    val statusMatches = filter.status == null || unit.statusEnum() == filter.status
                    val propMatches = filter.propertyId.isNullOrBlank() || unit.propertyId == filter.propertyId
                    val queryMatches = query.isBlank() ||
                        unit.unitNumber.lowercase().contains(query) ||
                        unit.propertyName.lowercase().contains(query) ||
                        unit.unitTypeEnum().label.lowercase().contains(query)
                    statusMatches && propMatches && queryMatches
                }
                UiState.Success(filtered)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    fun setUnitStatusFilter(status: UnitStatus?) {
        _unitFilterState.update { it.copy(status = if (it.status == status) null else status) }
    }

    fun setUnitPropertyFilter(propertyId: String?) {
        _unitFilterState.update { it.copy(propertyId = if (it.propertyId == propertyId) null else propertyId) }
    }

    fun setUnitSearchQuery(query: String) {
        _unitFilterState.update { it.copy(searchQuery = query) }
    }

    fun clearUnitFilters() {
        _unitFilterState.value = UnitMatrixFilterState()
    }

    val applications: StateFlow<UiState<List<RentalApplication>>> = propertyRepo.observeApplicationsForLandlord(currentUserId)
        .asUiState("LandlordVM", "Failed to load applications")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val leases: StateFlow<UiState<List<Lease>>> = propertyRepo.observeLeasesForLandlord(currentUserId)
        .asUiState("LandlordVM", "Failed to load leases")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val invoices: StateFlow<UiState<List<Invoice>>> = financeRepo.observeInvoicesForLandlord(currentUserId)
        .asUiState("LandlordVM", "Failed to load invoices")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val payments: StateFlow<UiState<List<Payment>>> = financeRepo.observePaymentsForLandlord(currentUserId)
        .asUiState("LandlordVM", "Failed to load payments")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val expenses: StateFlow<UiState<List<Expense>>> = financeRepo.observeExpensesForLandlord(currentUserId)
        .asUiState("LandlordVM", "Failed to load expenses")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val maintenanceRequests: StateFlow<UiState<List<MaintenanceRequest>>> = opsRepo.observeMaintenanceForLandlord(currentUserId)
        .asUiState("LandlordVM", "Failed to load maintenance requests")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val announcements: StateFlow<UiState<List<Announcement>>> = opsRepo.observeAnnouncementsForLandlord(currentUserId)
        .asUiState("LandlordVM", "Failed to load announcements")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val moveInChecklists: StateFlow<UiState<List<MoveInChecklist>>> = (handoverRepo?.observeChecklistsForLandlord(currentUserId) ?: flowOf(emptyList()))
        .asUiState("LandlordVM", "Failed to load move-in checklists")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val conditionChecklists: StateFlow<UiState<List<MoveInConditionChecklist>>> = (conditionChecklistRepo?.observeChecklistsForLandlord(currentUserId) ?: flowOf(emptyList()))
        .asUiState("LandlordVM", "Failed to load room-by-room move-in checklists")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val depositSettlements: StateFlow<UiState<List<DepositSettlement>>> = (handoverRepo?.observeSettlementsForLandlord(currentUserId) ?: flowOf(emptyList()))
        .asUiState("LandlordVM", "Failed to load deposit settlements")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    @OptIn(ExperimentalCoroutinesApi::class)
    val scannedDocuments: StateFlow<UiState<List<ScannedDocument>>> = _activeOrgId
        .flatMapLatest { orgId ->
            if (orgId.isNullOrBlank() || documentRepo == null) {
                flowOf(emptyList())
            } else {
                documentRepo.observeDocumentsForOrganization(orgId, currentUserId)
            }
        }
        .asUiState("LandlordVM", "Failed to load scanned documents")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    private val _checklistOverviewFilter = MutableStateFlow(ChecklistOverviewFilterState())
    val checklistOverviewFilter: StateFlow<ChecklistOverviewFilterState> = _checklistOverviewFilter.asStateFlow()

    val checklistOverviewState: StateFlow<UiState<ChecklistOverviewSummary>> = combine(
        leases,
        conditionChecklists
    ) { leasesUi, checklistsUi ->
        when {
            leasesUi is UiState.Loading || checklistsUi is UiState.Loading -> UiState.Loading
            leasesUi is UiState.Error -> UiState.Error(leasesUi.message)
            checklistsUi is UiState.Error -> UiState.Error(checklistsUi.message)
            leasesUi is UiState.Success && checklistsUi is UiState.Success -> {
                UiState.Success(
                    ChecklistOverviewEngine.buildSummary(
                        leases = leasesUi.data,
                        checklists = checklistsUi.data
                    )
                )
            }
            else -> UiState.Loading
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    fun setChecklistBucketFilter(bucket: ChecklistOverviewBucket?) {
        _checklistOverviewFilter.update { current ->
            current.copy(bucket = if (current.bucket == bucket) null else bucket)
        }
    }

    fun setChecklistPropertyFilter(propertyId: String?) {
        _checklistOverviewFilter.update { current ->
            current.copy(propertyId = if (current.propertyId == propertyId) null else propertyId)
        }
    }

    fun setChecklistSearchQuery(query: String) {
        _checklistOverviewFilter.update { it.copy(searchQuery = query) }
    }

    fun clearChecklistFilters() {
        _checklistOverviewFilter.value = ChecklistOverviewFilterState()
    }

    val portfolioSummary: StateFlow<PortfolioSummary> = combine(
        listOf(properties, units, leases, invoices, payments, expenses, maintenanceRequests)
    ) { states ->
        @Suppress("UNCHECKED_CAST")
        val props = (states[0] as? UiState.Success<List<Property>>)?.data ?: emptyList()
        @Suppress("UNCHECKED_CAST")
        val unitList = (states[1] as? UiState.Success<List<RentalUnit>>)?.data ?: emptyList()
        @Suppress("UNCHECKED_CAST")
        val leaseList = (states[2] as? UiState.Success<List<Lease>>)?.data ?: emptyList()
        @Suppress("UNCHECKED_CAST")
        val invList = (states[3] as? UiState.Success<List<Invoice>>)?.data ?: emptyList()
        @Suppress("UNCHECKED_CAST")
        val payList = (states[4] as? UiState.Success<List<Payment>>)?.data ?: emptyList()
        @Suppress("UNCHECKED_CAST")
        val expList = (states[5] as? UiState.Success<List<Expense>>)?.data ?: emptyList()
        @Suppress("UNCHECKED_CAST")
        val maintList = (states[6] as? UiState.Success<List<MaintenanceRequest>>)?.data ?: emptyList()
        FinancialEngine.computePortfolioSummary(props, unitList, leaseList, invList, payList, expList, maintList)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), PortfolioSummary())

    val cashFlowPoints: StateFlow<List<MonthlyCashFlowPoint>> = combine(payments, expenses) { payState, expState ->
        val pays = (payState as? UiState.Success)?.data ?: emptyList()
        val exps = (expState as? UiState.Success)?.data ?: emptyList()
        FinancialEngine.computeMonthlyCashFlow(pays, exps)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _feedback = MutableStateFlow<String?>(null)
    val feedback: StateFlow<String?> = _feedback.asStateFlow()

    fun clearFeedback() {
        _feedback.value = null
    }

    fun createProperty(
        orgId: String = _activeOrgId.value.orEmpty(),
        name: String,
        category: PropertyCategory,
        city: String,
        county: String = "",
        neighborhood: String = "",
        addressLine: String = "",
        description: String = "",
        waterMeterNumber: String? = null,
        electricityMeterNumber: String? = null,
        notes: String? = null,
        imageRefs: List<String> = emptyList(),
        mpesaPaybill: String? = null,
        mpesaTill: String? = null,
        mpesaAccountPrefix: String? = null,
        latitude: Double? = null,
        longitude: Double? = null,
        formattedAddress: String? = null,
        placeId: String? = null,
        locationVerified: Boolean = false,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            val targetOrgId = orgId.trim().ifBlank { _activeOrgId.value.orEmpty() }
            if (targetOrgId.isBlank()) {
                _feedback.value = "Create or select an organization before adding a property"
                return@launch
            }
            _busy.value = true
            val res = propertyRepo.createProperty(
                orgId = targetOrgId,
                name = name,
                category = category,
                city = city,
                county = county,
                neighborhood = neighborhood,
                addressLine = addressLine,
                description = description,
                waterMeterNumber = waterMeterNumber,
                electricityMeterNumber = electricityMeterNumber,
                notes = notes,
                imageRefs = imageRefs,
                mpesaPaybill = mpesaPaybill,
                mpesaTill = mpesaTill,
                mpesaAccountPrefix = mpesaAccountPrefix,
                latitude = latitude,
                longitude = longitude,
                formattedAddress = formattedAddress,
                placeId = placeId,
                locationVerified = locationVerified
            )
            _busy.value = false
            res.fold(
                onSuccess = {
                    _feedback.value = "Property '${name.trim()}' registered"
                    onSuccess()
                },
                onFailure = { _feedback.value = it.localizedMessage ?: "Failed to create property" }
            )
        }
    }

    fun updateProperty(
        property: Property,
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
        mpesaPaybill: String? = property.mpesaPaybill,
        mpesaTill: String? = property.mpesaTill,
        mpesaAccountPrefix: String? = property.mpesaAccountPrefix,
        latitude: Double? = property.latitude,
        longitude: Double? = property.longitude,
        formattedAddress: String? = property.formattedAddress,
        placeId: String? = property.placeId,
        locationVerified: Boolean = property.locationVerified,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            _busy.value = true
            val res = propertyRepo.updateProperty(
                propertyId = property.id,
                name = name,
                category = category,
                city = city,
                county = county,
                neighborhood = neighborhood,
                addressLine = addressLine,
                description = description,
                waterMeterNumber = waterMeterNumber,
                electricityMeterNumber = electricityMeterNumber,
                notes = notes,
                imageRefs = imageRefs,
                archived = property.archived,
                mpesaPaybill = mpesaPaybill,
                mpesaTill = mpesaTill,
                mpesaAccountPrefix = mpesaAccountPrefix,
                latitude = latitude,
                longitude = longitude,
                formattedAddress = formattedAddress,
                placeId = placeId,
                locationVerified = locationVerified
            )
            _busy.value = false
            res.fold(
                onSuccess = {
                    _feedback.value = "Property '${name.trim()}' updated"
                    onSuccess()
                },
                onFailure = { _feedback.value = it.localizedMessage ?: "Failed to update property" }
            )
        }
    }

    fun toggleArchiveProperty(property: Property) {
        viewModelScope.launch {
            _busy.value = true
            val res = propertyRepo.updateProperty(
                propertyId = property.id,
                name = property.name,
                category = property.categoryEnum(),
                city = property.city,
                county = property.county,
                neighborhood = property.neighborhood,
                addressLine = property.addressLine,
                description = property.description,
                waterMeterNumber = property.waterMeterNumber,
                electricityMeterNumber = property.electricityMeterNumber,
                notes = property.notes,
                imageRefs = property.imageRefs,
                archived = !property.archived,
                mpesaPaybill = property.mpesaPaybill,
                mpesaTill = property.mpesaTill,
                mpesaAccountPrefix = property.mpesaAccountPrefix,
                latitude = property.latitude,
                longitude = property.longitude,
                formattedAddress = property.formattedAddress,
                placeId = property.placeId,
                locationVerified = property.locationVerified
            )
            _busy.value = false
            _feedback.value = res.fold(
                onSuccess = { if (!property.archived) "Property archived" else "Property restored" },
                onFailure = { it.localizedMessage ?: "Update failed" }
            )
        }
    }

    fun deleteProperty(propertyId: String) {
        viewModelScope.launch {
            _busy.value = true
            val res = propertyRepo.deleteProperty(propertyId)
            _busy.value = false
            _feedback.value = res.fold(
                onSuccess = { "Property deleted" },
                onFailure = { it.localizedMessage ?: "Failed to delete property" }
            )
        }
    }

    fun assignManager(property: Property, managerUid: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _busy.value = true
            val res = propertyRepo.assignManagerToProperty(property, managerUid)
            _busy.value = false
            res.fold(
                onSuccess = {
                    _feedback.value = "Property manager assigned"
                    onSuccess()
                },
                onFailure = { _feedback.value = it.localizedMessage ?: "Failed to assign manager" }
            )
        }
    }

    fun createUnit(
        property: Property,
        unitNumber: String,
        unitType: UnitType,
        rentCents: Long,
        depositCents: Long,
        status: UnitStatus,
        amenities: List<String>,
        description: String,
        imageRefs: List<String>,
        published: Boolean,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            _busy.value = true
            val res = propertyRepo.createUnit(
                property, unitNumber, unitType, rentCents, depositCents, status, amenities, description, imageRefs, published
            )
            _busy.value = false
            res.fold(
                onSuccess = {
                    _feedback.value = "Unit $unitNumber added"
                    onSuccess()
                },
                onFailure = { _feedback.value = it.localizedMessage ?: "Failed to add unit" }
            )
        }
    }

    fun updateUnitStatus(unit: RentalUnit, status: UnitStatus, published: Boolean) {
        viewModelScope.launch {
            _busy.value = true
            val res = propertyRepo.updateUnitStatusAndPublish(unit, status, published)
            _busy.value = false
            _feedback.value = res.fold(
                onSuccess = { "Unit ${unit.unitNumber} updated" },
                onFailure = { it.localizedMessage ?: "Unit update failed" }
            )
        }
    }

    fun updateUnit(
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
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            _busy.value = true
            val res = propertyRepo.updateUnit(
                unit = unit,
                unitNumber = unitNumber,
                unitType = unitType,
                rentCents = rentCents,
                depositCents = depositCents,
                status = status,
                amenities = amenities,
                description = description,
                imageRefs = imageRefs,
                published = published
            )
            _busy.value = false
            res.fold(
                onSuccess = {
                    _feedback.value = "Unit ${unitNumber.trim()} saved"
                    onSuccess()
                },
                onFailure = { _feedback.value = it.localizedMessage ?: "Failed to update unit" }
            )
        }
    }

    fun deleteUnit(unit: RentalUnit) {
        viewModelScope.launch {
            _busy.value = true
            val res = propertyRepo.deleteUnit(unit)
            _busy.value = false
            _feedback.value = res.fold(
                onSuccess = { "Unit ${unit.unitNumber} deleted" },
                onFailure = { it.localizedMessage ?: "Failed to delete unit" }
            )
        }
    }

    fun reviewApplication(application: RentalApplication, newStatus: ApplicationStatus, notes: String?) {
        viewModelScope.launch {
            _busy.value = true
            val res = propertyRepo.reviewApplication(application, newStatus, notes)
            _busy.value = false
            _feedback.value = res.fold(
                onSuccess = { "Application marked ${newStatus.label}" },
                onFailure = { it.localizedMessage ?: "Failed to review application" }
            )
        }
    }

    fun createLease(
        unit: RentalUnit,
        tenantUid: String,
        tenantName: String,
        tenantPhone: String,
        rentCents: Long,
        depositCents: Long,
        startDateEpochMs: Long,
        endDateEpochMs: Long,
        dueDayOfMonth: Long,
        termsSummary: String,
        actorName: String,
        renewalMode: RenewalMode = RenewalMode.OFFER,
        renewalLeadDays: Long = 30L,
        renewalDurationMonths: Long = 12L,
        escalationType: EscalationType = EscalationType.NONE,
        escalationPercent: Double = 0.0,
        escalationFixedCents: Long = 0L,
        gracePeriodDays: Long = 5L,
        lateFeePercent: Double = 5.0,
        lateFeeEnabled: Boolean = true,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            _busy.value = true
            val res = propertyRepo.createLease(
                unit = unit,
                tenantUid = tenantUid,
                tenantName = tenantName,
                tenantPhone = tenantPhone,
                rentCents = rentCents,
                depositCents = depositCents,
                startDateEpochMs = startDateEpochMs,
                endDateEpochMs = endDateEpochMs,
                dueDayOfMonth = dueDayOfMonth,
                termsSummary = termsSummary,
                actorName = actorName,
                renewalMode = renewalMode,
                renewalLeadDays = renewalLeadDays,
                renewalDurationMonths = renewalDurationMonths,
                escalationType = escalationType,
                escalationPercent = escalationPercent,
                escalationFixedCents = escalationFixedCents,
                gracePeriodDays = gracePeriodDays,
                lateFeePercent = lateFeePercent,
                lateFeeEnabled = lateFeeEnabled
            )
            _busy.value = false
            res.fold(
                onSuccess = {
                    _feedback.value = "Lease created for $tenantName"
                    onSuccess()
                },
                onFailure = { _feedback.value = it.localizedMessage ?: "Failed to create lease" }
            )
        }
    }

    fun renewLease(lease: Lease, newEndEpochMs: Long, newRentCents: Long, actorName: String) {
        viewModelScope.launch {
            _busy.value = true
            val res = propertyRepo.renewLease(lease, newEndEpochMs, newRentCents, actorName)
            _busy.value = false
            _feedback.value = res.fold(
                onSuccess = { "Lease renewed" },
                onFailure = { it.localizedMessage ?: "Renewal failed" }
            )
        }
    }

    fun updateLeaseAutomationPolicy(
        lease: Lease,
        renewalMode: RenewalMode,
        renewalLeadDays: Long,
        renewalDurationMonths: Long,
        escalationType: EscalationType,
        escalationPercent: Double,
        escalationFixedCents: Long,
        gracePeriodDays: Long,
        lateFeePercent: Double,
        lateFeeEnabled: Boolean,
        actorName: String,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _busy.value = true
            val res = propertyRepo.updateLeaseAutomationPolicy(
                lease = lease,
                renewalMode = renewalMode,
                renewalLeadDays = renewalLeadDays,
                renewalDurationMonths = renewalDurationMonths,
                escalationType = escalationType,
                escalationPercent = escalationPercent,
                escalationFixedCents = escalationFixedCents,
                gracePeriodDays = gracePeriodDays,
                lateFeePercent = lateFeePercent,
                lateFeeEnabled = lateFeeEnabled,
                actorName = actorName
            )
            _busy.value = false
            res.fold(
                onSuccess = {
                    _feedback.value = "Automation policy saved for Unit ${lease.unitNumber}"
                    onSuccess()
                },
                onFailure = {
                    _feedback.value = it.localizedMessage ?: "Failed to update automation policy"
                }
            )
        }
    }

    fun runLeaseRenewalSweep(
        actorName: String,
        onComplete: (PropertyRepository.LeaseRenewalSweepSummary) -> Unit = {}
    ) {
        viewModelScope.launch {
            val leaseList = (leases.value as? UiState.Success)?.data ?: emptyList()
            _busy.value = true
            val res = propertyRepo.runLeaseRenewalSweep(leaseList, actorName)
            _busy.value = false
            res.fold(
                onSuccess = { summary ->
                    _feedback.value = when {
                        summary.totalProcessedCount == 0 && summary.skippedOwnerOnlyCount > 0 ->
                            "Skipped ${summary.skippedOwnerOnlyCount} lease(s) with rent increases (requires Owner)"
                        summary.totalProcessedCount == 0 ->
                            "No leases are due for renewal or offer dispatch right now"
                        else -> {
                            val parts = buildList {
                                if (summary.autoRenewedCount > 0) add("${summary.autoRenewedCount} auto-renewed")
                                if (summary.offersSentCount > 0) add("${summary.offersSentCount} offer(s) sent")
                                if (summary.skippedOwnerOnlyCount > 0) add("${summary.skippedOwnerOnlyCount} owner-only skipped")
                            }
                            "Renewal sweep complete: ${parts.joinToString(", ")}"
                        }
                    }
                    onComplete(summary)
                },
                onFailure = {
                    _feedback.value = it.localizedMessage ?: "Renewal sweep failed"
                }
            )
        }
    }

    fun terminateLease(lease: Lease, reason: String, actorName: String) {
        viewModelScope.launch {
            _busy.value = true
            val res = propertyRepo.terminateLease(lease, reason, actorName)
            _busy.value = false
            _feedback.value = res.fold(
                onSuccess = { "Lease terminated & unit released" },
                onFailure = { it.localizedMessage ?: "Termination failed" }
            )
        }
    }

    fun saveMoveInChecklist(
        lease: Lease,
        existingChecklist: MoveInChecklist?,
        inspectionDateEpochMs: Long,
        waterMeterReading: String?,
        electricityMeterReading: String?,
        keysHandedOverCount: Long,
        items: List<ChecklistItem>,
        imageRefs: List<String>,
        landlordNotes: String?,
        submitForTenantSignOff: Boolean,
        actorName: String,
        onSuccess: () -> Unit
    ) {
        val repo = handoverRepo ?: run {
            _feedback.value = "Checklist repository unavailable"
            return
        }
        viewModelScope.launch {
            _busy.value = true
            val res = repo.saveMoveInChecklist(
                lease = lease,
                existingChecklist = existingChecklist,
                inspectionDateEpochMs = inspectionDateEpochMs,
                waterMeterReading = waterMeterReading,
                electricityMeterReading = electricityMeterReading,
                keysHandedOverCount = keysHandedOverCount,
                items = items,
                imageRefs = imageRefs,
                landlordNotes = landlordNotes,
                submitForTenantSignOff = submitForTenantSignOff,
                actorName = actorName
            )
            _busy.value = false
            res.fold(
                onSuccess = {
                    _feedback.value = if (submitForTenantSignOff) {
                        "Move-in checklist sent to ${lease.tenantName} for sign-off"
                    } else {
                        "Move-in checklist draft saved"
                    }
                    onSuccess()
                },
                onFailure = {
                    _feedback.value = it.localizedMessage ?: "Failed to save move-in checklist"
                }
            )
        }
    }

    fun saveMoveInConditionChecklist(
        lease: Lease,
        existingChecklist: MoveInConditionChecklist?,
        inspectionDateEpochMs: Long,
        waterMeterReading: String?,
        electricityMeterReading: String?,
        keysHandedOverCount: Long,
        rooms: List<InspectionRoom>,
        landlordSummary: String?,
        landlordSignature: DigitalSignature? = null,
        submitForTenantSignOff: Boolean,
        actorName: String,
        onSuccess: () -> Unit
    ) {
        val repo = conditionChecklistRepo ?: run {
            _feedback.value = "Room-by-room checklist repository unavailable"
            return
        }
        viewModelScope.launch {
            _busy.value = true
            val res = repo.saveChecklist(
                lease = lease,
                existingChecklist = existingChecklist,
                inspectionDateEpochMs = inspectionDateEpochMs,
                waterMeterReading = waterMeterReading,
                electricityMeterReading = electricityMeterReading,
                keysHandedOverCount = keysHandedOverCount,
                rooms = rooms,
                landlordSummary = landlordSummary,
                landlordSignature = landlordSignature,
                submitForTenantSignOff = submitForTenantSignOff,
                actorName = actorName
            )
            _busy.value = false
            res.fold(
                onSuccess = {
                    _feedback.value = if (submitForTenantSignOff) {
                        "Room-by-room checklist sent to ${lease.tenantName} for sign-off"
                    } else {
                        "Room-by-room checklist draft saved"
                    }
                    onSuccess()
                },
                onFailure = {
                    _feedback.value = it.localizedMessage ?: "Failed to save room-by-room checklist"
                }
            )
        }
    }

    fun deleteDraftMoveInConditionChecklist(
        checklist: MoveInConditionChecklist,
        actorName: String,
        onSuccess: () -> Unit
    ) {
        val repo = conditionChecklistRepo ?: run {
            _feedback.value = "Room-by-room checklist repository unavailable"
            return
        }
        viewModelScope.launch {
            _busy.value = true
            val res = repo.deleteDraftChecklist(checklist, actorName)
            _busy.value = false
            res.fold(
                onSuccess = {
                    _feedback.value = "Draft move-in checklist deleted"
                    onSuccess()
                },
                onFailure = {
                    _feedback.value = it.localizedMessage ?: "Failed to delete draft checklist"
                }
            )
        }
    }

    fun saveDepositSettlement(
        lease: Lease,
        existingSettlement: DepositSettlement?,
        depositHeldCents: Long,
        arrearsIncludedCents: Long,
        creditAppliedCents: Long,
        deductions: List<DepositDeductionItem>,
        moveOutDateEpochMs: Long,
        waterMeterFinal: String?,
        electricityMeterFinal: String?,
        keysReturnedCount: Long,
        imageRefs: List<String>,
        landlordNotes: String?,
        targetStatus: DepositSettlementStatus,
        refundMethod: RefundMethod,
        refundReferenceCode: String?,
        actorName: String,
        onSuccess: () -> Unit
    ) {
        val repo = handoverRepo ?: run {
            _feedback.value = "Deposit settlement repository unavailable"
            return
        }
        viewModelScope.launch {
            _busy.value = true
            val res = repo.saveDepositSettlement(
                lease = lease,
                existingSettlement = existingSettlement,
                depositHeldCents = depositHeldCents,
                arrearsIncludedCents = arrearsIncludedCents,
                creditAppliedCents = creditAppliedCents,
                deductions = deductions,
                moveOutDateEpochMs = moveOutDateEpochMs,
                waterMeterFinal = waterMeterFinal,
                electricityMeterFinal = electricityMeterFinal,
                keysReturnedCount = keysReturnedCount,
                imageRefs = imageRefs,
                landlordNotes = landlordNotes,
                targetStatus = targetStatus,
                refundMethod = refundMethod,
                refundReferenceCode = refundReferenceCode,
                actorName = actorName
            )
            _busy.value = false
            res.fold(
                onSuccess = {
                    _feedback.value = when (targetStatus) {
                        DepositSettlementStatus.DRAFT -> "Deposit settlement draft saved"
                        DepositSettlementStatus.ISSUED -> "Deposit settlement issued to ${lease.tenantName}"
                        DepositSettlementStatus.SETTLED -> "Deposit settlement finalized"
                        else -> "Deposit settlement updated"
                    }
                    onSuccess()
                },
                onFailure = {
                    _feedback.value = it.localizedMessage ?: "Failed to save deposit settlement"
                }
            )
        }
    }

    fun generateMonthlyInvoices(
        billingPeriod: String,
        extraChargesCents: Long,
        extraChargesNote: String?,
        actorName: String
    ) {
        viewModelScope.launch {
            val leaseList = (leases.value as? UiState.Success)?.data ?: emptyList()
            val invList = (invoices.value as? UiState.Success)?.data ?: emptyList()
            _busy.value = true
            val res = financeRepo.generateMonthlyInvoices(
                leaseList, invList, billingPeriod, extraChargesCents, extraChargesNote, actorName
            )
            _busy.value = false
            _feedback.value = res.fold(
                onSuccess = { count ->
                    if (count == 0) "All active leases already invoiced for $billingPeriod"
                    else "Generated $count invoice(s) for $billingPeriod"
                },
                onFailure = { it.localizedMessage ?: "Failed to generate invoices" }
            )
        }
    }

    fun sendBatchRentReminders(actorName: String) {
        viewModelScope.launch {
            val invList = (invoices.value as? UiState.Success)?.data ?: emptyList()
            _busy.value = true
            val res = financeRepo.sendRentDueAndOverdueReminders(
                invoices = invList,
                actorName = actorName,
                respectCooldown = true
            )
            _busy.value = false
            _feedback.value = res.fold(
                onSuccess = { count ->
                    if (count == 0) "No new due or overdue reminders eligible right now"
                    else "Dispatched $count rent due/overdue reminder(s) to tenants"
                },
                onFailure = { it.localizedMessage ?: "Failed to dispatch reminders" }
            )
        }
    }

    fun runLateFeeAssessmentSweep(
        actorName: String,
        onComplete: (FinanceRepository.LateFeeSweepSummary) -> Unit = {}
    ) {
        viewModelScope.launch {
            val invList = (invoices.value as? UiState.Success)?.data ?: emptyList()
            val leaseList = (leases.value as? UiState.Success)?.data ?: emptyList()
            _busy.value = true
            val res = financeRepo.runLateFeeAssessmentSweep(
                invoices = invList,
                leases = leaseList,
                actorName = actorName
            )
            _busy.value = false
            res.fold(
                onSuccess = { summary ->
                    _feedback.value = if (summary.assessedCount == 0) {
                        "No overdue invoices past grace period require late fees right now"
                    } else {
                        "Assessed late fees on ${summary.assessedCount} invoice(s) totaling ${CurrencyUtils.formatKes(summary.totalFeeCents, true)}"
                    }
                    onComplete(summary)
                },
                onFailure = {
                    _feedback.value = it.localizedMessage ?: "Late fee assessment failed"
                }
            )
        }
    }

    fun waiveInvoiceLateFee(
        invoice: Invoice,
        reason: String,
        actorName: String,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _busy.value = true
            val res = financeRepo.waiveInvoiceLateFee(
                invoice = invoice,
                reason = reason,
                actorName = actorName
            )
            _busy.value = false
            res.fold(
                onSuccess = {
                    _feedback.value = "Late fee waived on ${invoice.invoiceNumber}"
                    onSuccess()
                },
                onFailure = {
                    _feedback.value = it.localizedMessage ?: "Failed to waive late fee"
                }
            )
        }
    }

    fun recordLandlordPayment(
        lease: Lease,
        referenceCode: String,
        amountCents: Long,
        method: PaymentMethod,
        verifyImmediately: Boolean,
        actorName: String,
        paymentMethodId: String? = null,
        paymentChannelType: PaymentChannelType? = null,
        paymentShortcode: String? = null,
        onPaymentCreated: (String) -> Unit = {},
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            _busy.value = true
            val subRes = financeRepo.submitPayment(
                lease = lease,
                invoiceId = null,
                referenceCode = referenceCode,
                amountCents = amountCents,
                method = method,
                simulatedCheckout = false,
                paymentMethodId = paymentMethodId,
                paymentChannelType = paymentChannelType,
                paymentShortcode = paymentShortcode
            )
            if (subRes.isSuccess && verifyImmediately) {
                val payId = subRes.getOrThrow()
                val invList = ((invoices.value as? UiState.Success)?.data ?: emptyList()).filter { it.leaseId == lease.id }
                val tempPay = Payment(
                    id = payId,
                    referenceCode = referenceCode.trim().uppercase(),
                    leaseId = lease.id,
                    orgId = lease.orgId,
                    propertyId = lease.propertyId,
                    propertyName = lease.propertyName,
                    unitId = lease.unitId,
                    unitNumber = lease.unitNumber,
                    ownerUid = lease.ownerUid,
                    managerUids = lease.managerUids,
                    authorizedUids = lease.authorizedUids,
                    tenantUid = lease.tenantUid,
                    tenantName = lease.tenantName,
                    amountCents = amountCents,
                    method = method.name,
                    paymentMethodId = paymentMethodId,
                    paymentChannelType = paymentChannelType?.name,
                    paymentShortcode = paymentShortcode,
                    simulatedCheckout = false,
                    paymentDateEpochMs = System.currentTimeMillis(),
                    status = com.example.domain.model.PaymentStatus.PENDING_VERIFICATION.name,
                    recordedByUid = currentUserId
                )
                val verRes = financeRepo.verifyAndAllocatePayment(tempPay, lease, invList, "Recorded by management", actorName)
                _busy.value = false
                verRes.fold(
                    onSuccess = { receipt ->
                        _feedback.value = "Payment recorded & verified ($receipt)"
                        onPaymentCreated(payId)
                        onSuccess()
                    },
                    onFailure = { _feedback.value = it.localizedMessage ?: "Recorded, pending verification" }
                )
            } else {
                _busy.value = false
                subRes.fold(
                    onSuccess = { payId ->
                        _feedback.value = "Payment recorded"
                        onPaymentCreated(payId)
                        onSuccess()
                    },
                    onFailure = { _feedback.value = it.localizedMessage ?: "Failed to record payment" }
                )
            }
        }
    }

    fun verifyPayment(payment: Payment, notes: String, actorName: String) {
        viewModelScope.launch {
            val leaseList = (leases.value as? UiState.Success)?.data ?: emptyList()
            val invList = (invoices.value as? UiState.Success)?.data ?: emptyList()
            val lease = leaseList.firstOrNull { it.id == payment.leaseId }
            if (lease == null) {
                _feedback.value = "Matching lease not found"
                return@launch
            }
            _busy.value = true
            val res = financeRepo.verifyAndAllocatePayment(
                payment, lease, invList.filter { it.leaseId == lease.id }, notes, actorName
            )
            _busy.value = false
            _feedback.value = res.fold(
                onSuccess = { rcp -> "Verified • Receipt $rcp issued" },
                onFailure = { it.localizedMessage ?: "Verification failed" }
            )
        }
    }

    fun rejectPayment(payment: Payment, reason: String, actorName: String) {
        viewModelScope.launch {
            _busy.value = true
            val res = financeRepo.rejectPayment(payment, reason, actorName)
            _busy.value = false
            _feedback.value = res.fold(
                onSuccess = { "Payment marked rejected" },
                onFailure = { it.localizedMessage ?: "Rejection failed" }
            )
        }
    }

    fun reversePayment(payment: Payment, reason: String, actorName: String) {
        viewModelScope.launch {
            val leaseList = (leases.value as? UiState.Success)?.data ?: emptyList()
            val invList = (invoices.value as? UiState.Success)?.data ?: emptyList()
            val lease = leaseList.firstOrNull { it.id == payment.leaseId }
            if (lease == null) {
                _feedback.value = "Matching lease not found"
                return@launch
            }
            _busy.value = true
            val res = financeRepo.reverseVerifiedPayment(
                payment, lease, invList.filter { it.leaseId == lease.id }, reason, actorName
            )
            _busy.value = false
            _feedback.value = res.fold(
                onSuccess = { "Payment reversed & ledger restored" },
                onFailure = { it.localizedMessage ?: "Reversal failed" }
            )
        }
    }

    fun recordExpense(
        property: Property,
        category: ExpenseCategory,
        title: String,
        description: String,
        amountCents: Long,
        documentRef: String?,
        actorName: String,
        onExpenseCreated: (String) -> Unit = {},
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            _busy.value = true
            val res = financeRepo.recordExpense(
                property, category, title, description, amountCents, System.currentTimeMillis(), documentRef, actorName
            )
            _busy.value = false
            res.fold(
                onSuccess = { expenseId ->
                    _feedback.value = "Expense logged"
                    onExpenseCreated(expenseId)
                    onSuccess()
                },
                onFailure = { _feedback.value = it.localizedMessage ?: "Failed to record expense" }
            )
        }
    }

    fun submitMaintenanceForUnit(
        unitId: String,
        lease: Lease?,
        title: String,
        description: String,
        category: com.example.domain.model.MaintenanceCategory,
        priority: com.example.domain.model.MaintenancePriority,
        imageRefs: List<String>,
        submitterName: String,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            _busy.value = true
            val res = opsRepo.submitMaintenanceRequestForUnit(
                unitId = unitId,
                lease = lease,
                title = title,
                description = description,
                category = category,
                priority = priority,
                imageRefs = imageRefs,
                submitterName = submitterName
            )
            _busy.value = false
            res.fold(
                onSuccess = {
                    _feedback.value = "Maintenance request logged for unit"
                    onSuccess()
                },
                onFailure = {
                    _feedback.value = it.localizedMessage ?: "Failed to submit maintenance ticket"
                }
            )
        }
    }

    fun updateMaintenance(
        request: MaintenanceRequest,
        newStatus: MaintenanceStatus,
        repairCostCents: Long,
        note: String,
        assignedStaffUid: String?,
        assignedStaffName: String?,
        recordAsExpense: Boolean
    ) {
        viewModelScope.launch {
            _busy.value = true
            val res = opsRepo.updateMaintenanceProgress(
                request, newStatus, repairCostCents, note, assignedStaffUid, assignedStaffName, recordAsExpense
            )
            _busy.value = false
            _feedback.value = res.fold(
                onSuccess = { "Maintenance ticket updated" },
                onFailure = { it.localizedMessage ?: "Update failed" }
            )
        }
    }

    fun publishAnnouncement(property: Property, title: String, body: String, authorName: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            val leaseList = (leases.value as? UiState.Success)?.data ?: emptyList()
            val targetTenants = leaseList.filter { it.propertyId == property.id }.map { it.tenantUid }
            _busy.value = true
            val res = opsRepo.publishAnnouncement(property, targetTenants, title, body, authorName)
            _busy.value = false
            res.fold(
                onSuccess = {
                    _feedback.value = "Announcement sent to ${targetTenants.size} tenant(s)"
                    onSuccess()
                },
                onFailure = { _feedback.value = it.localizedMessage ?: "Failed to publish announcement" }
            )
        }
    }

    // --- Firebase AI Logic Smart Portfolio Insights & Assisted Drafting ---

    private val _aiInsightsState = MutableStateFlow<AiTaskState>(AiTaskState.Idle)
    val aiInsightsState: StateFlow<AiTaskState> = _aiInsightsState.asStateFlow()
    private var insightsJob: Job? = null

    private val _aiUnitDraftState = MutableStateFlow<AiTaskState>(AiTaskState.Idle)
    val aiUnitDraftState: StateFlow<AiTaskState> = _aiUnitDraftState.asStateFlow()
    private var unitDraftJob: Job? = null

    private val _aiAnnouncementDraftState = MutableStateFlow<AiTaskState>(AiTaskState.Idle)
    val aiAnnouncementDraftState: StateFlow<AiTaskState> = _aiAnnouncementDraftState.asStateFlow()
    private var announcementDraftJob: Job? = null

    private val _aiTriageState = MutableStateFlow<AiTriageUiState>(AiTriageUiState.Idle)
    val aiTriageState: StateFlow<AiTriageUiState> = _aiTriageState.asStateFlow()
    private var triageJob: Job? = null

    fun generatePortfolioInsights() {
        insightsJob?.cancel()
        insightsJob = viewModelScope.launch {
            val sum = portfolioSummary.value
            val props = (properties.value as? UiState.Success)?.data ?: emptyList()
            val unitList = (units.value as? UiState.Success)?.data ?: emptyList()
            aiRepo.streamPortfolioInsights(sum, props, unitList, sum.overdueInvoices).collect { state ->
                _aiInsightsState.value = state
            }
        }
    }

    fun clearAiInsights() {
        insightsJob?.cancel()
        _aiInsightsState.value = AiTaskState.Idle
    }

    fun draftUnitDescriptionWithAi(
        propertyName: String,
        city: String,
        neighborhood: String,
        unitNumber: String,
        unitTypeLabel: String,
        rentCents: Long,
        depositCents: Long,
        amenities: List<String>
    ) {
        unitDraftJob?.cancel()
        unitDraftJob = viewModelScope.launch {
            aiRepo.streamUnitListingDraft(
                propertyName = propertyName,
                city = city,
                neighborhood = neighborhood,
                unitNumber = unitNumber,
                unitTypeLabel = unitTypeLabel,
                rentCents = rentCents,
                depositCents = depositCents,
                amenities = amenities
            ).collect { state ->
                _aiUnitDraftState.value = state
            }
        }
    }

    fun clearUnitDraft() {
        unitDraftJob?.cancel()
        _aiUnitDraftState.value = AiTaskState.Idle
    }

    fun draftAnnouncementWithAi(
        propertyName: String,
        topic: String,
        extraContext: String
    ) {
        announcementDraftJob?.cancel()
        announcementDraftJob = viewModelScope.launch {
            aiRepo.streamAnnouncementDraft(
                propertyName = propertyName,
                topic = topic,
                extraContext = extraContext
            ).collect { state ->
                _aiAnnouncementDraftState.value = state
            }
        }
    }

    fun clearAnnouncementDraft() {
        announcementDraftJob?.cancel()
        _aiAnnouncementDraftState.value = AiTaskState.Idle
    }

    fun triageMaintenanceWithAi(request: MaintenanceRequest) {
        triageJob?.cancel()
        triageJob = viewModelScope.launch {
            _aiTriageState.value = AiTriageUiState.Loading
            try {
                val res = aiRepo.triageMaintenanceRequest(
                    propertyName = request.propertyName,
                    unitNumber = request.unitNumber,
                    title = request.title,
                    description = request.description,
                    currentCategory = request.categoryEnum(),
                    currentPriority = request.priorityEnum()
                )
                _aiTriageState.value = res.fold(
                    onSuccess = { AiTriageUiState.Success(it) },
                    onFailure = { AiTriageUiState.Error(it.localizedMessage ?: "AI triage failed.") }
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _aiTriageState.value = AiTriageUiState.Error(e.localizedMessage ?: "AI triage failed.")
            }
        }
    }

    fun clearAiTriage() {
        triageJob?.cancel()
        _aiTriageState.value = AiTriageUiState.Idle
    }

    // --- Integrated Document Scanner & OCR ---

    private val _scanOcrState = MutableStateFlow<ScanOcrUiState>(ScanOcrUiState.Idle)
    val scanOcrState: StateFlow<ScanOcrUiState> = _scanOcrState.asStateFlow()
    private var ocrJob: Job? = null

    fun extractDocumentWithAi(bitmap: Bitmap, hintType: DocumentType? = null) {
        ocrJob?.cancel()
        ocrJob = viewModelScope.launch {
            _scanOcrState.value = ScanOcrUiState.Loading
            val res = aiRepo.extractDocumentFields(bitmap, hintType)
            _scanOcrState.value = res.fold(
                onSuccess = { ScanOcrUiState.Success(it) },
                onFailure = { ScanOcrUiState.Error(it.localizedMessage ?: "Smart extraction failed.") }
            )
        }
    }

    fun clearDocumentOcr() {
        ocrJob?.cancel()
        _scanOcrState.value = ScanOcrUiState.Idle
    }

    fun uploadScannedDocument(
        unit: RentalUnit,
        lease: Lease? = null,
        documentType: DocumentType,
        title: String,
        notes: String?,
        filterMode: ScanFilterMode,
        pageImageRefs: List<String>,
        extractedReference: String? = null,
        extractedAmountCents: Long? = null,
        extractedDateText: String? = null,
        extractedSummary: String? = null,
        aiExtracted: Boolean = false,
        tenantVisible: Boolean = true,
        paymentId: String? = null,
        expenseId: String? = null,
        uploaderName: String,
        uploaderRole: UserRole,
        onSuccess: (String) -> Unit = {}
    ) {
        val repo = documentRepo ?: run {
            _feedback.value = "Document scanner repository unavailable"
            return
        }
        viewModelScope.launch {
            _busy.value = true
            val res = repo.uploadScannedDocument(
                unit = unit,
                lease = lease,
                documentType = documentType,
                title = title,
                notes = notes,
                filterMode = filterMode,
                pageImageRefs = pageImageRefs,
                extractedReference = extractedReference,
                extractedAmountCents = extractedAmountCents,
                extractedDateText = extractedDateText,
                extractedSummary = extractedSummary,
                aiExtracted = aiExtracted,
                tenantVisible = tenantVisible,
                paymentId = paymentId,
                expenseId = expenseId,
                uploaderName = uploaderName,
                uploaderRole = uploaderRole
            )
            _busy.value = false
            res.fold(
                onSuccess = { docId ->
                    _feedback.value = "Document uploaded to unit vault"
                    onSuccess(docId)
                },
                onFailure = {
                    _feedback.value = it.localizedMessage ?: "Failed to upload scanned document"
                }
            )
        }
    }

    fun updateScannedDocumentMetadata(
        document: ScannedDocument,
        title: String,
        notes: String?,
        tenantVisible: Boolean,
        onSuccess: () -> Unit = {}
    ) {
        val repo = documentRepo ?: return
        viewModelScope.launch {
            _busy.value = true
            val res = repo.updateDocumentMetadata(document, title, notes, tenantVisible)
            _busy.value = false
            res.fold(
                onSuccess = {
                    _feedback.value = "Document details updated"
                    onSuccess()
                },
                onFailure = {
                    _feedback.value = it.localizedMessage ?: "Failed to update document"
                }
            )
        }
    }

    fun deleteScannedDocument(documentId: String, onSuccess: () -> Unit = {}) {
        val repo = documentRepo ?: return
        viewModelScope.launch {
            _busy.value = true
            val res = repo.deleteDocument(documentId)
            _busy.value = false
            res.fold(
                onSuccess = {
                    _feedback.value = "Scanned document removed"
                    onSuccess()
                },
                onFailure = {
                    _feedback.value = it.localizedMessage ?: "Failed to delete document"
                }
            )
        }
    }
}
