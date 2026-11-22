package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.repository.FinanceRepository
import com.example.data.repository.MpesaCapturePreferences
import com.example.data.repository.MpesaCapturePreferencesRepository
import com.example.data.repository.MpesaReceiptRepository
import com.example.domain.finance.CurrencyUtils
import com.example.domain.finance.MpesaReceiptMatchEvaluation
import com.example.domain.finance.ReceiptMatchOutcome
import com.example.domain.model.Invoice
import com.example.domain.model.InvoiceStatus
import com.example.domain.model.Lease
import com.example.domain.model.MpesaCaptureSource
import com.example.domain.model.MpesaReceipt
import com.example.domain.model.Payment
import com.example.domain.model.PaymentMethod
import com.example.domain.model.PaymentStatus
import com.example.domain.model.PaymentVerificationSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MpesaReconcileViewModel(
    private val receiptRepo: MpesaReceiptRepository,
    private val financeRepo: FinanceRepository,
    private val prefsRepo: MpesaCapturePreferencesRepository,
    private val currentUserId: String
) : ViewModel() {

    val receipts: StateFlow<UiState<List<MpesaReceipt>>> = receiptRepo.observeReceipts(currentUserId)
        .asUiState("MpesaReconcileVM", "Failed to load captured M-Pesa receipts")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val capturePrefs: StateFlow<MpesaCapturePreferences> = prefsRepo.capturePreferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), MpesaCapturePreferences())

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _feedback = MutableStateFlow<String?>(null)
    val feedback: StateFlow<String?> = _feedback.asStateFlow()

    fun clearFeedback() {
        _feedback.value = null
    }

    fun setAutoCaptureEnabled(enabled: Boolean) {
        viewModelScope.launch {
            prefsRepo.setAutoCaptureEnabled(enabled)
            _feedback.value = if (enabled) {
                "M-Pesa notification auto-capture enabled"
            } else {
                "M-Pesa notification auto-capture paused"
            }
        }
    }

    fun stageSmsText(
        rawText: String,
        source: MpesaCaptureSource,
        onResult: (List<MpesaReceipt>) -> Unit = {}
    ) {
        viewModelScope.launch {
            _busy.value = true
            val res = receiptRepo.stageParsedSmsBatch(currentUserId, rawText, source)
            _busy.value = false
            res.fold(
                onSuccess = { staged ->
                    _feedback.value = if (staged.size == 1) {
                        val first = staged.first()
                        "Staged M-Pesa ${first.transactionCode} (${CurrencyUtils.formatKes(first.amountCents, true)})"
                    } else {
                        "Staged ${staged.size} M-Pesa receipts for review"
                    }
                    onResult(staged)
                },
                onFailure = { err ->
                    _feedback.value = err.localizedMessage ?: "Could not parse M-Pesa confirmation SMS"
                }
            )
        }
    }

    fun approveMatchedReceipt(
        evaluation: MpesaReceiptMatchEvaluation,
        invoices: List<Invoice>,
        actorName: String,
        onSuccess: () -> Unit = {}
    ) {
        val matchedPayment = evaluation.matchedPayment
        val lease = evaluation.suggestedLease
        val receipt = evaluation.receipt
        if (matchedPayment == null || lease == null) {
            _feedback.value = "Link a lease before reconciling ${receipt.transactionCode}"
            return
        }

        viewModelScope.launch {
            _busy.value = true
            val leaseInvoices = invoices.filter { it.leaseId == lease.id }
            if (evaluation.outcome == ReceiptMatchOutcome.ALREADY_VERIFIED) {
                val markRes = receiptRepo.markReconciled(currentUserId, receipt.id, matchedPayment.id, lease.id)
                _busy.value = false
                markRes.fold(
                    onSuccess = {
                        _feedback.value = "Marked ${receipt.transactionCode} reconciled with existing verification"
                        onSuccess()
                    },
                    onFailure = { _feedback.value = it.localizedMessage ?: "Failed to update receipt status" }
                )
                return@launch
            }

            if (matchedPayment.amountCents == receipt.amountCents) {
                val verifyRes = financeRepo.verifyAndAllocatePayment(
                    payment = matchedPayment,
                    lease = lease,
                    leaseInvoices = leaseInvoices,
                    verificationNotes = "Auto-matched Safaricom SMS ${receipt.transactionCode}",
                    actorName = actorName,
                    verificationSource = PaymentVerificationSource.MPESA_SMS_MATCH,
                    matchedReceiptId = receipt.id
                )
                verifyRes.fold(
                    onSuccess = { receiptNum ->
                        val recRes = receiptRepo.markReconciled(currentUserId, receipt.id, matchedPayment.id, lease.id)
                        _busy.value = false
                        recRes.fold(
                            onSuccess = {
                                _feedback.value = "Verified ${receipt.transactionCode} • $receiptNum"
                                onSuccess()
                            },
                            onFailure = {
                                _feedback.value = "Payment verified ($receiptNum), but receipt state update failed"
                            }
                        )
                    },
                    onFailure = { err ->
                        _busy.value = false
                        _feedback.value = err.localizedMessage ?: "Failed to verify matched payment"
                    }
                )
            } else {
                // Firestore rules protect Payment.amountCents from mutation; replace mismatched pending submission
                val rejectRes = financeRepo.rejectPayment(
                    payment = matchedPayment,
                    reason = "Replaced by Safaricom SMS ${receipt.transactionCode} (${CurrencyUtils.formatKes(receipt.amountCents, true)})",
                    actorName = actorName
                )
                if (rejectRes.isFailure) {
                    _busy.value = false
                    _feedback.value = rejectRes.exceptionOrNull()?.localizedMessage
                        ?: "Could not replace mismatched pending payment"
                    return@launch
                }
                val result = reconcileUnmatchedToLeaseInternal(receipt, lease, invoices, actorName)
                _busy.value = false
                result.fold(
                    onSuccess = { receiptNum ->
                        _feedback.value = "Reconciled ${receipt.transactionCode} with SMS amount • $receiptNum"
                        onSuccess()
                    },
                    onFailure = { err ->
                        _feedback.value = err.localizedMessage ?: "Failed to reconcile SMS amount"
                    }
                )
            }
        }
    }

    fun reconcileUnmatchedToLease(
        receipt: MpesaReceipt,
        lease: Lease,
        invoices: List<Invoice>,
        actorName: String,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _busy.value = true
            val result = reconcileUnmatchedToLeaseInternal(receipt, lease, invoices, actorName)
            _busy.value = false
            result.fold(
                onSuccess = { receiptNum ->
                    _feedback.value = "Recorded & verified ${receipt.transactionCode} on ${lease.unitNumber} • $receiptNum"
                    onSuccess()
                },
                onFailure = { err ->
                    _feedback.value = err.localizedMessage ?: "Failed to reconcile receipt to lease"
                }
            )
        }
    }

    private suspend fun reconcileUnmatchedToLeaseInternal(
        receipt: MpesaReceipt,
        lease: Lease,
        invoices: List<Invoice>,
        actorName: String
    ): Result<String> {
        val leaseInvoices = invoices.filter { it.leaseId == lease.id }
        val openInvoiceId = leaseInvoices
            .firstOrNull { it.statusEnum() != InvoiceStatus.CANCELLED && it.outstandingCents > 0L }
            ?.id

        val submitRes = financeRepo.submitPayment(
            lease = lease,
            invoiceId = openInvoiceId,
            referenceCode = receipt.transactionCode,
            amountCents = receipt.amountCents,
            method = PaymentMethod.MPESA,
            simulatedCheckout = false,
            verificationSource = PaymentVerificationSource.MPESA_SMS_MATCH,
            matchedReceiptId = receipt.id
        )
        val paymentId = submitRes.getOrElse { return Result.failure(it) }

        val pendingPayment = Payment(
            id = paymentId,
            referenceCode = receipt.transactionCode.trim().uppercase(),
            leaseId = lease.id,
            invoiceId = openInvoiceId,
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
            amountCents = receipt.amountCents,
            method = PaymentMethod.MPESA.name,
            simulatedCheckout = false,
            paymentDateEpochMs = receipt.transactionTimestampMs,
            status = PaymentStatus.PENDING_VERIFICATION.name,
            verificationSource = PaymentVerificationSource.MPESA_SMS_MATCH.name,
            matchedReceiptId = receipt.id
        )

        val receiptNum = financeRepo.verifyAndAllocatePayment(
            payment = pendingPayment,
            lease = lease,
            leaseInvoices = leaseInvoices,
            verificationNotes = "Reconciled from Safaricom SMS ${receipt.transactionCode}",
            actorName = actorName,
            verificationSource = PaymentVerificationSource.MPESA_SMS_MATCH,
            matchedReceiptId = receipt.id
        ).getOrElse { return Result.failure(it) }

        receiptRepo.markReconciled(currentUserId, receipt.id, paymentId, lease.id)
            .getOrElse { return Result.failure(it) }

        return Result.success(receiptNum)
    }

    fun markAlreadyVerifiedReconciled(evaluation: MpesaReceiptMatchEvaluation) {
        val matched = evaluation.matchedPayment ?: return
        viewModelScope.launch {
            _busy.value = true
            val res = receiptRepo.markReconciled(
                userId = currentUserId,
                receiptId = evaluation.receipt.id,
                matchedPaymentId = matched.id,
                matchedLeaseId = evaluation.suggestedLease?.id
            )
            _busy.value = false
            res.fold(
                onSuccess = {
                    _feedback.value = "Linked ${evaluation.receipt.transactionCode} to verified payment"
                },
                onFailure = {
                    _feedback.value = it.localizedMessage ?: "Failed to update receipt"
                }
            )
        }
    }

    fun dismissReceipt(receipt: MpesaReceipt) {
        viewModelScope.launch {
            _busy.value = true
            val res = receiptRepo.dismissReceipt(currentUserId, receipt.id)
            _busy.value = false
            res.fold(
                onSuccess = { _feedback.value = "Dismissed ${receipt.transactionCode}" },
                onFailure = { _feedback.value = it.localizedMessage ?: "Failed to dismiss receipt" }
            )
        }
    }

    fun reopenReceipt(receipt: MpesaReceipt) {
        viewModelScope.launch {
            _busy.value = true
            val res = receiptRepo.reopenReceipt(currentUserId, receipt.id)
            _busy.value = false
            res.fold(
                onSuccess = { _feedback.value = "Moved ${receipt.transactionCode} back to review queue" },
                onFailure = { _feedback.value = it.localizedMessage ?: "Failed to reopen receipt" }
            )
        }
    }

    fun markTenantReceiptSubmitted(receipt: MpesaReceipt, paymentId: String, leaseId: String) {
        viewModelScope.launch {
            receiptRepo.markReconciled(
                userId = currentUserId,
                receiptId = receipt.id,
                matchedPaymentId = paymentId,
                matchedLeaseId = leaseId
            )
        }
    }
}
