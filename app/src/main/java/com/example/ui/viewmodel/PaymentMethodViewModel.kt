package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.repository.PaymentMethodRepository
import com.example.domain.model.PaymentChannelType
import com.example.domain.model.PaymentRoleScope
import com.example.domain.model.SavedPaymentMethod
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PaymentMethodViewModel(
    private val repo: PaymentMethodRepository,
    private val currentUserId: String
) : ViewModel() {

    val methods: StateFlow<UiState<List<SavedPaymentMethod>>> = repo.observePaymentMethods(currentUserId)
        .asUiState("PaymentMethodVM", "Failed to load saved payment methods")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    private val _selectedMethodId = MutableStateFlow<String?>(null)
    val selectedMethodId: StateFlow<String?> = _selectedMethodId.asStateFlow()

    val effectiveSelectedMethod: StateFlow<SavedPaymentMethod?> = combine(methods, _selectedMethodId) { state, selectedId ->
        val list = (state as? UiState.Success)?.data ?: emptyList()
        list.firstOrNull { it.id == selectedId }
            ?: list.firstOrNull { it.defaultMethod }
            ?: list.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _feedback = MutableStateFlow<String?>(null)
    val feedback: StateFlow<String?> = _feedback.asStateFlow()

    fun clearFeedback() {
        _feedback.value = null
    }

    fun selectMethod(methodId: String?) {
        _selectedMethodId.value = methodId?.trim()?.takeIf { it.isNotBlank() }
    }

    fun saveMethod(
        existingMethodId: String? = null,
        label: String,
        channelType: PaymentChannelType,
        roleScope: PaymentRoleScope = PaymentRoleScope.ALL,
        paybillNumber: String? = null,
        accountReference: String? = null,
        tillNumber: String? = null,
        phoneNumber: String? = null,
        bankName: String? = null,
        bankAccountNumber: String? = null,
        defaultMethod: Boolean = false,
        smsVerified: Boolean = false,
        smsSampleRef: String? = null,
        onSuccess: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            _busy.value = true
            val res = repo.savePaymentMethod(
                existingMethodId = existingMethodId,
                label = label,
                channelType = channelType,
                roleScope = roleScope,
                paybillNumber = paybillNumber,
                accountReference = accountReference,
                tillNumber = tillNumber,
                phoneNumber = phoneNumber,
                bankName = bankName,
                bankAccountNumber = bankAccountNumber,
                defaultMethod = defaultMethod,
                smsVerified = smsVerified,
                smsSampleRef = smsSampleRef
            )
            _busy.value = false
            res.fold(
                onSuccess = { savedId ->
                    _selectedMethodId.value = savedId
                    _feedback.value = if (existingMethodId.isNullOrBlank()) {
                        "${channelType.label} '${label.trim()}' saved"
                    } else {
                        "${channelType.label} '${label.trim()}' updated"
                    }
                    onSuccess(savedId)
                },
                onFailure = { err ->
                    _feedback.value = err.localizedMessage ?: "Failed to save payment method"
                }
            )
        }
    }

    fun setDefaultMethod(methodId: String) {
        viewModelScope.launch {
            _busy.value = true
            val res = repo.setDefaultPaymentMethod(currentUserId, methodId)
            _busy.value = false
            res.fold(
                onSuccess = {
                    _selectedMethodId.value = methodId
                    _feedback.value = "Default payment method updated"
                },
                onFailure = { err ->
                    _feedback.value = err.localizedMessage ?: "Failed to update default payment method"
                }
            )
        }
    }

    fun deleteMethod(methodId: String) {
        viewModelScope.launch {
            _busy.value = true
            val res = repo.deletePaymentMethod(currentUserId, methodId)
            _busy.value = false
            res.fold(
                onSuccess = {
                    if (_selectedMethodId.value == methodId) {
                        _selectedMethodId.value = null
                    }
                    _feedback.value = "Payment method removed"
                },
                onFailure = { err ->
                    _feedback.value = err.localizedMessage ?: "Failed to delete payment method"
                }
            )
        }
    }
}
