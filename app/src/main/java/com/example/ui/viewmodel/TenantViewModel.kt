package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.repository.FinanceRepository
import com.example.data.repository.HandoverRepository
import com.example.data.repository.MoveInChecklistRepository
import com.example.data.repository.OperationsRepository
import com.example.data.repository.PropertyRepository
import com.example.domain.finance.FinancialEngine
import com.example.domain.finance.TenantAccountSummary
import com.example.domain.finance.TenantPaymentTrendsSummary
import com.example.domain.location.BuildingLocationResolver
import com.example.domain.location.ResolvedBuildingLocation
import com.example.domain.model.Announcement
import com.example.domain.model.DepositSettlement
import com.example.domain.model.Invoice
import com.example.domain.model.Lease
import com.example.domain.model.LeaseStatus
import com.example.domain.model.MaintenanceCategory
import com.example.domain.model.MaintenancePriority
import com.example.domain.model.MaintenanceRequest
import com.example.domain.model.MoveInChecklist
import com.example.domain.model.MoveInConditionChecklist
import com.example.domain.model.Payment
import com.example.domain.model.PaymentChannelType
import com.example.domain.model.PaymentMethod
import com.example.domain.model.PaymentVerificationSource
import com.example.domain.model.RentalUnit
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TenantViewModel(
    private val propertyRepo: PropertyRepository,
    private val financeRepo: FinanceRepository,
    private val opsRepo: OperationsRepository,
    private val currentUserId: String,
    private val handoverRepo: HandoverRepository? = null,
    private val conditionChecklistRepo: MoveInChecklistRepository? = null
) : ViewModel() {

    val leases: StateFlow<UiState<List<Lease>>> = propertyRepo.observeMyLeases(currentUserId)
        .asUiState("TenantVM", "Failed to load tenancy")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val invoices: StateFlow<UiState<List<Invoice>>> = financeRepo.observeMyInvoices(currentUserId)
        .asUiState("TenantVM", "Failed to load invoices")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val payments: StateFlow<UiState<List<Payment>>> = financeRepo.observeMyPayments(currentUserId)
        .asUiState("TenantVM", "Failed to load payments")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val maintenanceRequests: StateFlow<UiState<List<MaintenanceRequest>>> = opsRepo.observeMaintenanceForTenant(currentUserId)
        .asUiState("TenantVM", "Failed to load maintenance requests")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val announcements: StateFlow<UiState<List<Announcement>>> = opsRepo.observeAnnouncementsForTenant(currentUserId)
        .asUiState("TenantVM", "Failed to load announcements")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val moveInChecklists: StateFlow<UiState<List<MoveInChecklist>>> = (handoverRepo?.observeChecklistsForTenant(currentUserId) ?: flowOf(emptyList()))
        .asUiState("TenantVM", "Failed to load move-in checklists")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val conditionChecklists: StateFlow<UiState<List<MoveInConditionChecklist>>> = (conditionChecklistRepo?.observeChecklistsForTenant(currentUserId) ?: flowOf(emptyList()))
        .asUiState("TenantVM", "Failed to load room-by-room checklists")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val depositSettlements: StateFlow<UiState<List<DepositSettlement>>> = (handoverRepo?.observeSettlementsForTenant(currentUserId) ?: flowOf(emptyList()))
        .asUiState("TenantVM", "Failed to load deposit settlements")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val linkedLeases: StateFlow<List<Lease>> = leases
        .map { leaseState ->
            val list = (leaseState as? UiState.Success)?.data ?: emptyList()
            list.sortedBy {
                when (it.statusEnum()) {
                    LeaseStatus.ACTIVE -> 0
                    LeaseStatus.RENEWED -> 1
                    else -> 2
                }
            }.distinctBy { it.unitId }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val activeLease: StateFlow<Lease?> = leases
        .combine(MutableStateFlow(Unit)) { leaseState, _ ->
            val list = (leaseState as? UiState.Success)?.data ?: emptyList()
            list.firstOrNull { it.statusEnum() == LeaseStatus.ACTIVE } ?: list.firstOrNull()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val activeUnit: StateFlow<RentalUnit?> = activeLease
        .flatMapLatest { lease ->
            val unitId = lease?.unitId.orEmpty()
            if (unitId.isBlank()) flowOf(null) else propertyRepo.observeUnit(unitId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    val activeBuildingLocation: StateFlow<ResolvedBuildingLocation?> = combine(activeLease, activeUnit) { lease, unit ->
        when {
            unit != null -> BuildingLocationResolver.resolve(unit)
            lease != null -> BuildingLocationResolver.resolve(
                city = "",
                neighborhood = "",
                addressLine = lease.propertyName,
                latitude = null,
                longitude = null,
                formattedAddress = "${lease.propertyName} • Unit ${lease.unitNumber}",
                locationVerified = false
            )
            else -> null
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    val accountSummary: StateFlow<TenantAccountSummary> = combine(activeLease, invoices, payments) { lease, invState, payState ->
        val invs = (invState as? UiState.Success)?.data ?: emptyList()
        val pays = (payState as? UiState.Success)?.data ?: emptyList()
        FinancialEngine.computeTenantAccountSummary(lease, invs, pays)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), TenantAccountSummary())

    val paymentTrends: StateFlow<TenantPaymentTrendsSummary> = combine(invoices, payments) { invState, payState ->
        val invs = (invState as? UiState.Success)?.data ?: emptyList()
        val pays = (payState as? UiState.Success)?.data ?: emptyList()
        FinancialEngine.computeTenantPaymentTrends(invs, pays)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), TenantPaymentTrendsSummary())

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _feedback = MutableStateFlow<String?>(null)
    val feedback: StateFlow<String?> = _feedback.asStateFlow()

    fun clearFeedback() {
        _feedback.value = null
    }

    fun submitPayment(
        lease: Lease,
        invoiceId: String?,
        referenceCode: String,
        amountCents: Long,
        method: PaymentMethod,
        simulatedCheckout: Boolean,
        paymentMethodId: String? = null,
        paymentChannelType: PaymentChannelType? = null,
        paymentShortcode: String? = null,
        verificationSource: PaymentVerificationSource? = null,
        matchedReceiptId: String? = null,
        onPaymentCreated: (String) -> Unit = {},
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            _busy.value = true
            val res = financeRepo.submitPayment(
                lease = lease,
                invoiceId = invoiceId,
                referenceCode = referenceCode,
                amountCents = amountCents,
                method = method,
                simulatedCheckout = simulatedCheckout,
                paymentMethodId = paymentMethodId,
                paymentChannelType = paymentChannelType,
                paymentShortcode = paymentShortcode,
                verificationSource = verificationSource,
                matchedReceiptId = matchedReceiptId
            )
            _busy.value = false
            res.fold(
                onSuccess = { paymentId ->
                    _feedback.value = if (simulatedCheckout) {
                        "Simulated M-Pesa payment submitted for landlord verification"
                    } else {
                        "Payment reference submitted for verification"
                    }
                    onPaymentCreated(paymentId)
                    onSuccess()
                },
                onFailure = {
                    _feedback.value = it.localizedMessage ?: "Failed to submit payment"
                }
            )
        }
    }

    fun submitMaintenance(
        lease: Lease,
        title: String,
        description: String,
        category: MaintenanceCategory,
        priority: MaintenancePriority,
        imageRefs: List<String>,
        onSuccess: () -> Unit
    ) {
        submitMaintenanceForUnit(
            unitId = lease.unitId,
            lease = lease,
            title = title,
            description = description,
            category = category,
            priority = priority,
            imageRefs = imageRefs,
            submitterName = lease.tenantName,
            onSuccess = onSuccess
        )
    }

    fun submitMaintenanceForUnit(
        unitId: String,
        lease: Lease?,
        title: String,
        description: String,
        category: MaintenanceCategory,
        priority: MaintenancePriority,
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
                    _feedback.value = "Maintenance request submitted & linked to unit"
                    onSuccess()
                },
                onFailure = {
                    _feedback.value = it.localizedMessage ?: "Failed to submit ticket"
                }
            )
        }
    }

    fun appendMaintenanceFollowUp(
        request: MaintenanceRequest,
        appendedNote: String,
        updatedImageRefs: List<String>,
        actorName: String,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            _busy.value = true
            val res = opsRepo.appendTenantMaintenanceUpdate(
                request = request,
                appendedNote = appendedNote,
                updatedImageRefs = updatedImageRefs,
                actorName = actorName
            )
            _busy.value = false
            res.fold(
                onSuccess = {
                    _feedback.value = "Follow-up details added to ticket"
                    onSuccess()
                },
                onFailure = {
                    _feedback.value = it.localizedMessage ?: "Failed to update ticket"
                }
            )
        }
    }

    fun respondToMoveInChecklist(
        checklist: MoveInChecklist,
        acknowledge: Boolean,
        responseNote: String?,
        actorName: String,
        onSuccess: () -> Unit
    ) {
        val repo = handoverRepo ?: run {
            _feedback.value = "Checklist repository unavailable"
            return
        }
        viewModelScope.launch {
            _busy.value = true
            val res = repo.respondToMoveInChecklist(
                checklist = checklist,
                acknowledge = acknowledge,
                responseNote = responseNote,
                actorName = actorName
            )
            _busy.value = false
            res.fold(
                onSuccess = {
                    _feedback.value = if (acknowledge) {
                        "Move-in checklist signed off & locked"
                    } else {
                        "Discrepancy notes sent to your landlord"
                    }
                    onSuccess()
                },
                onFailure = {
                    _feedback.value = it.localizedMessage ?: "Failed to submit checklist response"
                }
            )
        }
    }

    fun submitConditionChecklistReview(
        checklist: MoveInConditionChecklist,
        acknowledge: Boolean,
        tenantRemarksByItemId: Map<String, String>,
        tenantSummary: String?,
        actorName: String,
        onSuccess: () -> Unit
    ) {
        val repo = conditionChecklistRepo ?: run {
            _feedback.value = "Room-by-room checklist repository unavailable"
            return
        }
        viewModelScope.launch {
            _busy.value = true
            val res = repo.submitTenantReview(
                checklist = checklist,
                acknowledge = acknowledge,
                tenantRemarksByItemId = tenantRemarksByItemId,
                tenantSummary = tenantSummary,
                actorName = actorName
            )
            _busy.value = false
            res.fold(
                onSuccess = {
                    _feedback.value = if (acknowledge) {
                        "Room-by-room checklist signed off & locked"
                    } else {
                        "Discrepancy remarks sent to your landlord"
                    }
                    onSuccess()
                },
                onFailure = {
                    _feedback.value = it.localizedMessage ?: "Failed to submit checklist review"
                }
            )
        }
    }

    fun respondToDepositSettlement(
        settlement: DepositSettlement,
        approve: Boolean,
        responseNote: String?,
        actorName: String,
        onSuccess: () -> Unit
    ) {
        val repo = handoverRepo ?: run {
            _feedback.value = "Deposit settlement repository unavailable"
            return
        }
        viewModelScope.launch {
            _busy.value = true
            val res = repo.respondToDepositSettlement(
                settlement = settlement,
                approve = approve,
                responseNote = responseNote,
                actorName = actorName
            )
            _busy.value = false
            res.fold(
                onSuccess = {
                    _feedback.value = if (approve) {
                        "Deposit settlement statement approved"
                    } else {
                        "Deposit review request sent to your landlord"
                    }
                    onSuccess()
                },
                onFailure = {
                    _feedback.value = it.localizedMessage ?: "Failed to submit deposit response"
                }
            )
        }
    }
}
