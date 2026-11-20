package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.repository.FinanceRepository
import com.example.data.repository.OperationsRepository
import com.example.data.repository.PropertyRepository
import com.example.domain.finance.FinancialEngine
import com.example.domain.finance.TenantAccountSummary
import com.example.domain.model.Announcement
import com.example.domain.model.Invoice
import com.example.domain.model.Lease
import com.example.domain.model.LeaseStatus
import com.example.domain.model.MaintenanceCategory
import com.example.domain.model.MaintenancePriority
import com.example.domain.model.MaintenanceRequest
import com.example.domain.model.Payment
import com.example.domain.model.PaymentMethod
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
    private val currentUserId: String
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

    val accountSummary: StateFlow<TenantAccountSummary> = combine(activeLease, invoices, payments) { lease, invState, payState ->
        val invs = (invState as? UiState.Success)?.data ?: emptyList()
        val pays = (payState as? UiState.Success)?.data ?: emptyList()
        FinancialEngine.computeTenantAccountSummary(lease, invs, pays)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), TenantAccountSummary())

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
                simulatedCheckout = simulatedCheckout
            )
            _busy.value = false
            res.fold(
                onSuccess = {
                    _feedback.value = if (simulatedCheckout) {
                        "Simulated M-Pesa payment submitted for landlord verification"
                    } else {
                        "Payment reference submitted for verification"
                    }
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
}
