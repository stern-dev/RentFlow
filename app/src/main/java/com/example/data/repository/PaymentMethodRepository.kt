package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.firebase.OperationType
import com.example.data.firebase.guardFirestoreReads
import com.example.data.firebase.handleFirestoreError
import com.example.domain.finance.CurrencyUtils
import com.example.domain.finance.KenyanPaymentValidator
import com.example.domain.model.PaymentChannelType
import com.example.domain.model.PaymentRoleScope
import com.example.domain.model.SavedPaymentMethod
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class PaymentMethodRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in before managing payment methods.")

    private fun collectionFor(userId: String) =
        db.collection("users").document(userId).collection("payment_methods")

    fun observePaymentMethods(userId: String): Flow<List<SavedPaymentMethod>> {
        val cleanUid = userId.trim()
        if (cleanUid.isBlank()) return flowOf(emptyList())
        return collectionFor(cleanUid)
            .snapshots()
            .map { snap ->
                snap.toObjects(
                    SavedPaymentMethod::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedWith(
                    compareByDescending<SavedPaymentMethod> { it.defaultMethod }
                        .thenByDescending { it.smsVerified }
                        .thenBy { it.label.lowercase() }
                )
            }
            .guardFirestoreReads(OperationType.LIST, "users/$cleanUid/payment_methods")
    }

    suspend fun savePaymentMethod(
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
        smsSampleRef: String? = null
    ): Result<String> {
        val cleanLabel = label.trim().take(60)
        val cleanPaybill = if (channelType == PaymentChannelType.MPESA_PAYBILL) {
            KenyanPaymentValidator.normalizeShortcode(paybillNumber.orEmpty()).takeIf { it.isNotBlank() }
        } else null
        val cleanAccountRef = if (channelType == PaymentChannelType.MPESA_PAYBILL) {
            accountReference?.trim()?.take(24)?.takeIf { it.isNotBlank() }
        } else null
        val cleanTill = if (channelType == PaymentChannelType.MPESA_TILL) {
            KenyanPaymentValidator.normalizeShortcode(tillNumber.orEmpty()).takeIf { it.isNotBlank() }
        } else null
        val cleanPhone = if (channelType == PaymentChannelType.MPESA_STK) {
            phoneNumber?.trim()?.takeIf { it.isNotBlank() }?.let { CurrencyUtils.normalizeKenyanPhone(it) }
        } else null
        val cleanBankName = if (channelType == PaymentChannelType.BANK_TRANSFER) {
            bankName?.trim()?.take(60)?.takeIf { it.isNotBlank() }
        } else null
        val cleanBankAccount = if (channelType == PaymentChannelType.BANK_TRANSFER) {
            bankAccountNumber?.trim()?.replace(" ", "")?.take(30)?.takeIf { it.isNotBlank() }
        } else null
        val cleanSmsSampleRef = smsSampleRef?.trim()?.uppercase()?.take(30)?.takeIf { it.isNotBlank() }

        val validation = KenyanPaymentValidator.validatePaymentMethod(
            channelType = channelType,
            label = cleanLabel,
            paybillNumber = cleanPaybill,
            accountReference = cleanAccountRef,
            tillNumber = cleanTill,
            phoneNumber = cleanPhone,
            bankName = cleanBankName,
            bankAccountNumber = cleanBankAccount
        )
        if (!validation.isValid) {
            return Result.failure(IllegalArgumentException(validation.firstError ?: "Invalid payment method details"))
        }

        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }

        val methodsRef = collectionFor(uid)
        val targetRef = existingMethodId?.trim()?.takeIf { it.isNotBlank() }?.let { methodsRef.document(it) }
            ?: methodsRef.document()
        val path = targetRef.path
        val isCreate = existingMethodId.isNullOrBlank()

        return try {
            val existingDocs = methodsRef.get().await().documents
            val makeDefault = defaultMethod || (isCreate && existingDocs.isEmpty())
            val batch = db.batch()

            if (makeDefault) {
                existingDocs.forEach { doc ->
                    if (doc.id != targetRef.id && doc.getBoolean("defaultMethod") == true) {
                        batch.update(
                            doc.reference,
                            mapOf(
                                "defaultMethod" to false,
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        )
                    }
                }
            }

            if (isCreate) {
                val model = SavedPaymentMethod(
                    userId = uid,
                    label = cleanLabel,
                    channelType = channelType.name,
                    roleScope = roleScope.name,
                    paybillNumber = cleanPaybill,
                    accountReference = cleanAccountRef,
                    tillNumber = cleanTill,
                    phoneNumber = cleanPhone,
                    bankName = cleanBankName,
                    bankAccountNumber = cleanBankAccount,
                    defaultMethod = makeDefault,
                    smsVerified = smsVerified,
                    smsSampleRef = cleanSmsSampleRef
                )
                batch.set(targetRef, model.toCreateMap())
            } else {
                val updateMap = mutableMapOf<String, Any>(
                    "label" to cleanLabel,
                    "channelType" to channelType.name,
                    "roleScope" to roleScope.name,
                    "paybillNumber" to (cleanPaybill ?: FieldValue.delete()),
                    "accountReference" to (cleanAccountRef ?: FieldValue.delete()),
                    "tillNumber" to (cleanTill ?: FieldValue.delete()),
                    "phoneNumber" to (cleanPhone ?: FieldValue.delete()),
                    "bankName" to (cleanBankName ?: FieldValue.delete()),
                    "bankAccountNumber" to (cleanBankAccount ?: FieldValue.delete()),
                    "defaultMethod" to makeDefault,
                    "smsVerified" to smsVerified,
                    "smsSampleRef" to (cleanSmsSampleRef ?: FieldValue.delete()),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                batch.update(targetRef, updateMap)
            }

            batch.commit().await()
            Result.success(targetRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(
                e,
                if (isCreate) OperationType.CREATE else OperationType.UPDATE,
                path
            )
            Result.failure(e)
        }
    }

    suspend fun setDefaultPaymentMethod(userId: String, methodId: String): Result<Unit> {
        val cleanUid = userId.trim()
        val cleanMethodId = methodId.trim()
        val path = "users/$cleanUid/payment_methods/$cleanMethodId"
        return try {
            val uid = requireUserId()
            require(cleanUid == uid) { "Cannot update payment methods belonging to another user." }
            require(cleanMethodId.isNotBlank()) { "Payment method ID is required." }

            val methodsRef = collectionFor(uid)
            val snap = methodsRef.get().await()
            val batch = db.batch()
            snap.documents.forEach { doc ->
                val targetDefault = doc.id == cleanMethodId
                val currentDefault = doc.getBoolean("defaultMethod") == true
                if (targetDefault || currentDefault) {
                    batch.update(
                        doc.reference,
                        mapOf(
                            "defaultMethod" to targetDefault,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deletePaymentMethod(userId: String, methodId: String): Result<Unit> {
        val cleanUid = userId.trim()
        val cleanMethodId = methodId.trim()
        val path = "users/$cleanUid/payment_methods/$cleanMethodId"
        return try {
            val uid = requireUserId()
            require(cleanUid == uid) { "Cannot delete payment methods belonging to another user." }
            require(cleanMethodId.isNotBlank()) { "Payment method ID is required." }

            collectionFor(uid).document(cleanMethodId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }
}
