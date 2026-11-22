package com.example.domain.finance

import com.example.domain.model.Lease
import com.example.domain.model.LeaseStatus
import com.example.domain.model.MpesaReceipt
import com.example.domain.model.MpesaReceiptDirection
import com.example.domain.model.MpesaReceiptStatus
import com.example.domain.model.Payment
import com.example.domain.model.PaymentStatus

enum class ReceiptMatchOutcome {
    EXACT_MATCH,
    DISCREPANCY_REVIEW,
    UNMATCHED_LEASE_SUGGESTION,
    UNMATCHED,
    ALREADY_VERIFIED,
    OUTGOING_TENANT_ONLY
}

data class MpesaReceiptMatchEvaluation(
    val receipt: MpesaReceipt,
    val outcome: ReceiptMatchOutcome,
    val matchedPayment: Payment? = null,
    val suggestedLease: Lease? = null,
    val matchSummary: String,
    val discrepancyDetails: String? = null
)

/**
 * Pure domain matching engine evaluating captured M-Pesa confirmation receipts against
 * pending rent payments and active leases for 1-tap landlord approval or unmatched review.
 */
object MpesaReceiptMatcher {

    fun evaluateAllForLandlord(
        receipts: List<MpesaReceipt>,
        payments: List<Payment>,
        leases: List<Lease>
    ): List<MpesaReceiptMatchEvaluation> =
        receipts.map { evaluateForLandlord(it, payments, leases) }

    fun evaluateForLandlord(
        receipt: MpesaReceipt,
        payments: List<Payment>,
        leases: List<Lease>
    ): MpesaReceiptMatchEvaluation {
        val txCode = receipt.transactionCode.trim().uppercase()
        val pendingPayments = payments.filter { it.statusEnum() == PaymentStatus.PENDING_VERIFICATION }
        val verifiedPaymentByCode = payments.firstOrNull {
            it.statusEnum() == PaymentStatus.VERIFIED && it.referenceCode.trim().equals(txCode, ignoreCase = true)
        }
        if (verifiedPaymentByCode != null && verifiedPaymentByCode.amountCents == receipt.amountCents) {
            return MpesaReceiptMatchEvaluation(
                receipt = receipt,
                outcome = ReceiptMatchOutcome.ALREADY_VERIFIED,
                matchedPayment = verifiedPaymentByCode,
                suggestedLease = leases.firstOrNull { it.id == verifiedPaymentByCode.leaseId },
                matchSummary = "Already verified on ${verifiedPaymentByCode.tenantName} (${verifiedPaymentByCode.unitNumber})"
            )
        }

        // 1. Exact transaction code + amount match against pending payment
        val pendingSameCode = pendingPayments.firstOrNull {
            it.referenceCode.trim().equals(txCode, ignoreCase = true)
        }
        if (pendingSameCode != null) {
            val linkedLease = leases.firstOrNull { it.id == pendingSameCode.leaseId }
            return if (pendingSameCode.amountCents == receipt.amountCents) {
                MpesaReceiptMatchEvaluation(
                    receipt = receipt,
                    outcome = ReceiptMatchOutcome.EXACT_MATCH,
                    matchedPayment = pendingSameCode,
                    suggestedLease = linkedLease,
                    matchSummary = "Exact Tx & KES match: ${pendingSameCode.tenantName} • ${pendingSameCode.propertyName} (${pendingSameCode.unitNumber})"
                )
            } else {
                val diffDesc = "SMS shows ${CurrencyUtils.formatKes(receipt.amountCents, true)} vs pending ${CurrencyUtils.formatKes(pendingSameCode.amountCents, true)}"
                MpesaReceiptMatchEvaluation(
                    receipt = receipt,
                    outcome = ReceiptMatchOutcome.DISCREPANCY_REVIEW,
                    matchedPayment = pendingSameCode,
                    suggestedLease = linkedLease,
                    matchSummary = "Code $txCode matches ${pendingSameCode.tenantName}, but amount differs",
                    discrepancyDetails = diffDesc
                )
            }
        }

        // 2. Pending payment with exact amount + matching tenant phone/name/account ref (e.g. typo in tenant's submitted code)
        val fuzzyPendingMatch = pendingPayments.firstOrNull { pay ->
            val lease = leases.firstOrNull { it.id == pay.leaseId }
            val sameAmount = pay.amountCents == receipt.amountCents
            val phoneMatch = phoneMatches(receipt.counterpartyPhone, lease?.tenantPhone)
            val nameMatch = nameTokensOverlap(receipt.counterpartyName, pay.tenantName)
            val unitMatch = accountRefMatchesUnit(receipt.accountReference, pay.unitNumber)
            (sameAmount && (phoneMatch || nameMatch || unitMatch)) || (phoneMatch && nameMatch)
        }
        if (fuzzyPendingMatch != null) {
            val linkedLease = leases.firstOrNull { it.id == fuzzyPendingMatch.leaseId }
            val reasons = buildList {
                if (fuzzyPendingMatch.referenceCode != txCode) {
                    add("Submitted Ref ${fuzzyPendingMatch.referenceCode} vs SMS $txCode")
                }
                if (fuzzyPendingMatch.amountCents != receipt.amountCents) {
                    add("KES ${CurrencyUtils.centsToInputString(fuzzyPendingMatch.amountCents)} vs ${CurrencyUtils.centsToInputString(receipt.amountCents)}")
                }
            }.joinToString(" • ").ifBlank { "Pending payment reference review required" }
            return MpesaReceiptMatchEvaluation(
                receipt = receipt,
                outcome = ReceiptMatchOutcome.DISCREPANCY_REVIEW,
                matchedPayment = fuzzyPendingMatch,
                suggestedLease = linkedLease,
                matchSummary = "Possible match to pending payment from ${fuzzyPendingMatch.tenantName} (${fuzzyPendingMatch.unitNumber})",
                discrepancyDetails = reasons
            )
        }

        // Outgoing transfers are typically landlord payouts unless manually matched; flag separately if no pending payment matched
        if (receipt.directionEnum() == MpesaReceiptDirection.OUTGOING_SENT) {
            return MpesaReceiptMatchEvaluation(
                receipt = receipt,
                outcome = ReceiptMatchOutcome.OUTGOING_TENANT_ONLY,
                matchSummary = "Outgoing M-Pesa confirmation (${receipt.counterpartyName ?: receipt.shortcode ?: "Sent"})"
            )
        }

        // 3. No pending payment matched — look for a strong active lease suggestion by phone, account ref, or name
        val activeLeases = leases.filter { it.statusEnum() == LeaseStatus.ACTIVE }
        val leaseByStrongSignal = activeLeases.firstOrNull { lease ->
            phoneMatches(receipt.counterpartyPhone, lease.tenantPhone) ||
                accountRefMatchesUnit(receipt.accountReference, lease.unitNumber) ||
                nameTokensOverlap(receipt.counterpartyName, lease.tenantName)
        }
        if (leaseByStrongSignal != null) {
            val amountNote = if (receipt.amountCents != leaseByStrongSignal.rentCents) {
                "Received ${CurrencyUtils.formatKes(receipt.amountCents, true)} vs monthly rent ${CurrencyUtils.formatKes(leaseByStrongSignal.rentCents, true)}"
            } else {
                null
            }
            return MpesaReceiptMatchEvaluation(
                receipt = receipt,
                outcome = ReceiptMatchOutcome.UNMATCHED_LEASE_SUGGESTION,
                suggestedLease = leaseByStrongSignal,
                matchSummary = "No pending submission; matches tenant ${leaseByStrongSignal.tenantName} (${leaseByStrongSignal.propertyName} ${leaseByStrongSignal.unitNumber})",
                discrepancyDetails = amountNote
            )
        }

        val uniqueRentLease = activeLeases.filter { it.rentCents == receipt.amountCents }
            .takeIf { it.size == 1 }
            ?.firstOrNull()
        if (uniqueRentLease != null) {
            return MpesaReceiptMatchEvaluation(
                receipt = receipt,
                outcome = ReceiptMatchOutcome.UNMATCHED_LEASE_SUGGESTION,
                suggestedLease = uniqueRentLease,
                matchSummary = "Amount matches monthly rent for ${uniqueRentLease.tenantName} (${uniqueRentLease.unitNumber})"
            )
        }

        return MpesaReceiptMatchEvaluation(
            receipt = receipt,
            outcome = ReceiptMatchOutcome.UNMATCHED,
            matchSummary = "No matching pending payment or active lease found"
        )
    }

    fun filterTenantAutofillCandidates(
        receipts: List<MpesaReceipt>,
        existingTenantPayments: List<Payment>
    ): List<MpesaReceipt> {
        val activeOrVerifiedCodes = existingTenantPayments
            .filter {
                it.statusEnum() == PaymentStatus.PENDING_VERIFICATION ||
                    it.statusEnum() == PaymentStatus.VERIFIED
            }
            .map { it.referenceCode.trim().uppercase() }
            .toSet()

        return receipts.filter { receipt ->
            receipt.statusEnum() == MpesaReceiptStatus.STAGED &&
                receipt.directionEnum() != MpesaReceiptDirection.INCOMING_RECEIVED &&
                receipt.transactionCode.trim().uppercase() !in activeOrVerifiedCodes
        }
    }

    private fun phoneMatches(a: String?, b: String?): Boolean {
        if (a.isNullOrBlank() || b.isNullOrBlank()) return false
        val normA = CurrencyUtils.normalizeKenyanPhone(a).takeLast(9)
        val normB = CurrencyUtils.normalizeKenyanPhone(b).takeLast(9)
        return normA.length == 9 && normA == normB
    }

    private fun nameTokensOverlap(a: String?, b: String?): Boolean {
        if (a.isNullOrBlank() || b.isNullOrBlank()) return false
        val tokensA = a.uppercase().split(Regex("[^A-Z0-9]+")).filter { it.length >= 3 }.toSet()
        val tokensB = b.uppercase().split(Regex("[^A-Z0-9]+")).filter { it.length >= 3 }.toSet()
        if (tokensA.isEmpty() || tokensB.isEmpty()) return false
        val overlap = tokensA.intersect(tokensB)
        return overlap.size >= minOf(2, tokensB.size)
    }

    private fun accountRefMatchesUnit(accountRef: String?, unitNumber: String): Boolean {
        if (accountRef.isNullOrBlank() || unitNumber.isBlank()) return false
        val cleanRef = accountRef.uppercase().replace(Regex("[^A-Z0-9]"), "")
        val cleanUnit = unitNumber.uppercase().replace(Regex("[^A-Z0-9]"), "")
        if (cleanUnit.isBlank()) return false
        return cleanRef == cleanUnit ||
            cleanRef.endsWith(cleanUnit) ||
            cleanRef == "UNIT$cleanUnit"
    }
}
