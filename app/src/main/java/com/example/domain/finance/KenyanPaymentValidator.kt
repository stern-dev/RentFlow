package com.example.domain.finance

import com.example.domain.model.PaymentChannelType

data class PaymentMethodValidationResult(
    val labelError: String? = null,
    val paybillError: String? = null,
    val accountReferenceError: String? = null,
    val tillError: String? = null,
    val phoneError: String? = null,
    val bankNameError: String? = null,
    val bankAccountError: String? = null
) {
    val isValid: Boolean
        get() = labelError == null &&
            paybillError == null &&
            accountReferenceError == null &&
            tillError == null &&
            phoneError == null &&
            bankNameError == null &&
            bankAccountError == null

    val firstError: String?
        get() = listOfNotNull(
            labelError,
            paybillError,
            accountReferenceError,
            tillError,
            phoneError,
            bankNameError,
            bankAccountError
        ).firstOrNull()
}

sealed interface MpesaSmsVerificationResult {
    data object NotProvided : MpesaSmsVerificationResult
    data class InvalidFormat(val message: String) : MpesaSmsVerificationResult
    data class Verified(
        val transactionCode: String,
        val amountCents: Long,
        val recipientOrAccount: String?,
        val detailMessage: String
    ) : MpesaSmsVerificationResult

    data class MismatchWarning(
        val transactionCode: String,
        val amountCents: Long,
        val recipientOrAccount: String?,
        val reason: String
    ) : MpesaSmsVerificationResult
}

object KenyanPaymentValidator {
    private val shortcodeRegex = Regex("^[1-9][0-9]{4,6}$")
    private val accountRefRegex = Regex("^[A-Za-z0-9._\\- /]{1,24}$")
    private val bankAccountRegex = Regex("^[A-Za-z0-9\\-]{6,30}$")

    fun normalizeShortcode(raw: String): String =
        raw.trim().replace(" ", "")

    fun isValidShortcode(raw: String): Boolean =
        shortcodeRegex.matches(normalizeShortcode(raw))

    fun isValidAccountReference(raw: String): Boolean {
        val clean = raw.trim()
        return clean.length in 1..24 && accountRefRegex.matches(clean)
    }

    fun validatePaymentMethod(
        channelType: PaymentChannelType,
        label: String,
        paybillNumber: String?,
        accountReference: String?,
        tillNumber: String?,
        phoneNumber: String?,
        bankName: String?,
        bankAccountNumber: String?
    ): PaymentMethodValidationResult {
        val cleanLabel = label.trim()
        val labelError = when {
            cleanLabel.length < 2 -> "Enter a method nickname (2–60 characters)."
            cleanLabel.length > 60 -> "Method nickname cannot exceed 60 characters."
            else -> null
        }

        var paybillError: String? = null
        var accountRefError: String? = null
        var tillError: String? = null
        var phoneError: String? = null
        var bankNameError: String? = null
        var bankAccountError: String? = null

        when (channelType) {
            PaymentChannelType.MPESA_PAYBILL -> {
                val pb = normalizeShortcode(paybillNumber.orEmpty())
                if (pb.isBlank()) {
                    paybillError = "Enter a 5–7 digit Safaricom Paybill number."
                } else if (!shortcodeRegex.matches(pb)) {
                    paybillError = "Paybill must be 5–7 digits and cannot start with 0 (e.g. 522522)."
                }

                val acct = accountReference.orEmpty().trim()
                if (acct.isBlank()) {
                    accountRefError = "Paybill payments require an Account Reference (e.g. UNIT-A102)."
                } else if (!isValidAccountReference(acct)) {
                    accountRefError = "Account Reference must be 1–24 alphanumeric, '-', '_', '.', or '/' characters."
                }
            }

            PaymentChannelType.MPESA_TILL -> {
                val till = normalizeShortcode(tillNumber.orEmpty())
                if (till.isBlank()) {
                    tillError = "Enter a 5–7 digit M-Pesa Buy Goods Till number."
                } else if (!shortcodeRegex.matches(till)) {
                    tillError = "Till number must be 5–7 digits and cannot start with 0 (e.g. 5643210)."
                }
            }

            PaymentChannelType.MPESA_STK -> {
                val phone = phoneNumber.orEmpty().trim()
                if (phone.isBlank()) {
                    phoneError = "Enter a Kenyan Safaricom/Airtel phone number (+2547... or 07...)."
                } else if (!CurrencyUtils.isValidKenyanPhone(phone)) {
                    phoneError = "Enter a valid Kenyan mobile number starting with +254, 254, or 07/01."
                }
            }

            PaymentChannelType.BANK_TRANSFER -> {
                val cleanBank = bankName.orEmpty().trim()
                val cleanAcct = bankAccountNumber.orEmpty().trim().replace(" ", "")
                if (cleanBank.length !in 2..60) {
                    bankNameError = "Enter bank name (2–60 characters, e.g. KCB Kenya, Equity Bank)."
                }
                if (cleanAcct.isBlank()) {
                    bankAccountError = "Enter bank account number (6–30 characters)."
                } else if (!bankAccountRegex.matches(cleanAcct)) {
                    bankAccountError = "Account number must be 6–30 alphanumeric or '-' characters."
                }
            }

            PaymentChannelType.CASH -> Unit
        }

        return PaymentMethodValidationResult(
            labelError = labelError,
            paybillError = paybillError,
            accountReferenceError = accountRefError,
            tillError = tillError,
            phoneError = phoneError,
            bankNameError = bankNameError,
            bankAccountError = bankAccountError
        )
    }

    /**
     * Zero-permission Safaricom M-Pesa confirmation SMS check.
     * Parses the pasted SMS and cross-checks transaction details against the entered
     * Paybill, Till, Account Reference, or STK phone number.
     */
    fun verifyWithMpesaSms(
        rawSms: String,
        channelType: PaymentChannelType,
        paybillNumber: String? = null,
        tillNumber: String? = null,
        accountReference: String? = null,
        phoneNumber: String? = null
    ): MpesaSmsVerificationResult {
        val trimmed = rawSms.trim()
        if (trimmed.isBlank()) return MpesaSmsVerificationResult.NotProvided

        val parsed = MpesaSmsParser.parse(trimmed)
            ?: return MpesaSmsVerificationResult.InvalidFormat(
                "Could not recognize a valid M-Pesa confirmation code (e.g. UAF29KL30M) and KES amount."
            )

        val lowerSms = trimmed.lowercase()
        val recipientLower = parsed.recipientOrAccount?.lowercase().orEmpty()
        val formattedKes = CurrencyUtils.formatKes(parsed.amountCents, includeDecimals = true)

        return when (channelType) {
            PaymentChannelType.MPESA_PAYBILL -> {
                val pb = normalizeShortcode(paybillNumber.orEmpty())
                val acct = accountReference.orEmpty().trim()
                val hasPb = pb.isNotBlank() && (lowerSms.contains(pb.lowercase()) || recipientLower.contains(pb.lowercase()))
                val hasAcct = acct.isNotBlank() && (lowerSms.contains(acct.lowercase()) || recipientLower.contains(acct.lowercase()))

                when {
                    pb.isNotBlank() && !isValidShortcode(pb) ->
                        MpesaSmsVerificationResult.MismatchWarning(
                            parsed.transactionCode,
                            parsed.amountCents,
                            parsed.recipientOrAccount,
                            "Parsed ${parsed.transactionCode} ($formattedKes), but Paybill '$pb' is invalid."
                        )
                    hasPb && (acct.isBlank() || hasAcct) ->
                        MpesaSmsVerificationResult.Verified(
                            parsed.transactionCode,
                            parsed.amountCents,
                            parsed.recipientOrAccount,
                            "Verified ${parsed.transactionCode} ($formattedKes) matching Paybill $pb" +
                                if (acct.isNotBlank()) " & Account $acct" else ""
                        )
                    hasAcct ->
                        MpesaSmsVerificationResult.Verified(
                            parsed.transactionCode,
                            parsed.amountCents,
                            parsed.recipientOrAccount,
                            "Verified ${parsed.transactionCode} ($formattedKes) for Account $acct"
                        )
                    hasPb ->
                        MpesaSmsVerificationResult.MismatchWarning(
                            parsed.transactionCode,
                            parsed.amountCents,
                            parsed.recipientOrAccount,
                            "SMS matched Paybill $pb (${parsed.transactionCode}), but Account '$acct' was not found in the pasted SMS."
                        )
                    else ->
                        MpesaSmsVerificationResult.MismatchWarning(
                            parsed.transactionCode,
                            parsed.amountCents,
                            parsed.recipientOrAccount,
                            "Parsed ${parsed.transactionCode} ($formattedKes), but Paybill '${pb.ifBlank { "—" }}' / Account '${acct.ifBlank { "—" }}' was not found in the SMS."
                        )
                }
            }

            PaymentChannelType.MPESA_TILL -> {
                val till = normalizeShortcode(tillNumber.orEmpty())
                val hasTill = till.isNotBlank() && (lowerSms.contains(till.lowercase()) || recipientLower.contains(till.lowercase()))
                when {
                    till.isNotBlank() && !isValidShortcode(till) ->
                        MpesaSmsVerificationResult.MismatchWarning(
                            parsed.transactionCode,
                            parsed.amountCents,
                            parsed.recipientOrAccount,
                            "Parsed ${parsed.transactionCode} ($formattedKes), but Till '$till' is invalid."
                        )
                    hasTill ->
                        MpesaSmsVerificationResult.Verified(
                            parsed.transactionCode,
                            parsed.amountCents,
                            parsed.recipientOrAccount,
                            "Verified ${parsed.transactionCode} ($formattedKes) matching Till $till"
                        )
                    else ->
                        MpesaSmsVerificationResult.MismatchWarning(
                            parsed.transactionCode,
                            parsed.amountCents,
                            parsed.recipientOrAccount,
                            "Parsed ${parsed.transactionCode} ($formattedKes), but Till '${till.ifBlank { "—" }}' did not appear in the SMS."
                        )
                }
            }

            PaymentChannelType.MPESA_STK -> {
                val normalizedPhone = if (CurrencyUtils.isValidKenyanPhone(phoneNumber.orEmpty())) {
                    CurrencyUtils.normalizeKenyanPhone(phoneNumber.orEmpty())
                } else ""
                val localTen = if (normalizedPhone.startsWith("+254")) "0${normalizedPhone.removePrefix("+254")}" else ""
                val hasPhone = normalizedPhone.isNotBlank() &&
                    (lowerSms.contains(normalizedPhone.lowercase()) ||
                        (localTen.isNotBlank() && lowerSms.contains(localTen)))

                if (hasPhone || parsed.recipientOrAccount != null) {
                    MpesaSmsVerificationResult.Verified(
                        parsed.transactionCode,
                        parsed.amountCents,
                        parsed.recipientOrAccount,
                        "Parsed M-Pesa confirmation ${parsed.transactionCode} ($formattedKes)" +
                            (parsed.recipientOrAccount?.let { " to $it" } ?: "")
                    )
                } else {
                    MpesaSmsVerificationResult.MismatchWarning(
                        parsed.transactionCode,
                        parsed.amountCents,
                        parsed.recipientOrAccount,
                        "Parsed ${parsed.transactionCode} ($formattedKes); confirm recipient details match your M-Pesa STK prompt."
                    )
                }
            }

            PaymentChannelType.BANK_TRANSFER,
            PaymentChannelType.CASH -> MpesaSmsVerificationResult.Verified(
                parsed.transactionCode,
                parsed.amountCents,
                parsed.recipientOrAccount,
                "Parsed reference ${parsed.transactionCode} ($formattedKes)"
            )
        }
    }
}
