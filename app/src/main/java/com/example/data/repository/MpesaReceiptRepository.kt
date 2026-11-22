package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.firebase.OperationType
import com.example.data.firebase.guardFirestoreReads
import com.example.data.firebase.handleFirestoreError
import com.example.domain.finance.MpesaSmsParser
import com.example.domain.model.MpesaCaptureSource
import com.example.domain.model.MpesaReceipt
import com.example.domain.model.MpesaReceiptStatus
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

class MpesaReceiptRepository(
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
            ?: throw IllegalStateException("User must be signed in before accessing M-Pesa receipts.")

    private fun collectionFor(userId: String) =
        db.collection("users").document(userId).collection("mpesa_receipts")

    fun observeReceipts(userId: String): Flow<List<MpesaReceipt>> {
        val cleanUid = userId.trim()
        if (cleanUid.isBlank()) return flowOf(emptyList())
        return collectionFor(cleanUid)
            .snapshots()
            .map { snap ->
                snap.toObjects(
                    MpesaReceipt::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedWith(
                    compareBy<MpesaReceipt> {
                        when (it.statusEnum()) {
                            MpesaReceiptStatus.STAGED -> 0
                            MpesaReceiptStatus.RECONCILED -> 1
                            MpesaReceiptStatus.DISMISSED -> 2
                        }
                    }.thenByDescending { it.transactionTimestampMs }
                )
            }
            .guardFirestoreReads(OperationType.LIST, "users/$cleanUid/mpesa_receipts")
    }

    suspend fun stageParsedSms(
        userId: String,
        rawText: String,
        source: MpesaCaptureSource
    ): Result<MpesaReceipt?> {
        val batchRes = stageParsedSmsBatch(userId, rawText, source)
        return batchRes.map { it.firstOrNull() }
    }

    suspend fun stageParsedSmsBatch(
        userId: String,
        rawText: String,
        source: MpesaCaptureSource
    ): Result<List<MpesaReceipt>> {
        val cleanUid = userId.trim()
        val parsedList = MpesaSmsParser.parseAll(rawText)
        if (parsedList.isEmpty()) {
            return Result.failure(
                IllegalArgumentException("No valid Safaricom M-Pesa confirmation (10-char Tx code + KES amount) found.")
            )
        }

        val signedInUid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        if (cleanUid != signedInUid) {
            return Result.failure(SecurityException("Cannot stage M-Pesa receipts for another user."))
        }

        val staged = mutableListOf<MpesaReceipt>()
        for (parsed in parsedList) {
            val docId = "mpesa_${parsed.transactionCode}"
            val docRef = collectionFor(signedInUid).document(docId)
            val path = docRef.path
            try {
                val existingSnap = docRef.get().await()
                if (!existingSnap.exists()) {
                    val receipt = MpesaReceipt(
                        id = docId,
                        userId = signedInUid,
                        transactionCode = parsed.transactionCode,
                        amountCents = parsed.amountCents,
                        direction = parsed.direction.name,
                        counterpartyName = parsed.counterpartyName,
                        counterpartyPhone = parsed.counterpartyPhone,
                        accountReference = parsed.accountReference,
                        shortcode = parsed.shortcode,
                        captureSource = source.name,
                        status = MpesaReceiptStatus.STAGED.name,
                        rawSnippet = parsed.rawSnippet,
                        transactionTimestampMs = parsed.transactionTimestampMs
                    )
                    docRef.set(receipt.toCreateMap()).await()
                    staged += receipt
                } else {
                    val existing = existingSnap.toObject(MpesaReceipt::class.java)
                    if (existing != null) {
                        val existingStatus = existing.statusEnum()
                        if (existingStatus == MpesaReceiptStatus.DISMISSED && source != MpesaCaptureSource.NOTIFICATION) {
                            docRef.update(
                                mapOf(
                                    "status" to MpesaReceiptStatus.STAGED.name,
                                    "captureSource" to source.name,
                                    "updatedAt" to FieldValue.serverTimestamp()
                                )
                            ).await()
                            staged += existing.copy(
                                id = docId,
                                status = MpesaReceiptStatus.STAGED.name,
                                captureSource = source.name
                            )
                        } else {
                            staged += existing.copy(id = docId)
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.WRITE, path)
                return Result.failure(e)
            }
        }
        return Result.success(staged)
    }

    suspend fun markReconciled(
        userId: String,
        receiptId: String,
        matchedPaymentId: String,
        matchedLeaseId: String? = null
    ): Result<Unit> {
        val cleanUid = userId.trim()
        val cleanReceiptId = receiptId.trim()
        val path = "users/$cleanUid/mpesa_receipts/$cleanReceiptId"
        return try {
            val uid = requireUserId()
            require(cleanUid == uid) { "Cannot update receipts belonging to another user." }
            require(cleanReceiptId.isNotBlank()) { "Receipt ID is required." }

            val updates = mutableMapOf<String, Any>(
                "status" to MpesaReceiptStatus.RECONCILED.name,
                "matchedPaymentId" to matchedPaymentId.trim().take(128),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            matchedLeaseId?.trim()?.take(128)?.takeIf { it.isNotBlank() }?.let { leaseId ->
                updates["matchedLeaseId"] = leaseId
            }
            collectionFor(uid).document(cleanReceiptId).update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun dismissReceipt(userId: String, receiptId: String): Result<Unit> {
        val cleanUid = userId.trim()
        val cleanReceiptId = receiptId.trim()
        val path = "users/$cleanUid/mpesa_receipts/$cleanReceiptId"
        return try {
            val uid = requireUserId()
            require(cleanUid == uid) { "Cannot dismiss receipts belonging to another user." }
            require(cleanReceiptId.isNotBlank()) { "Receipt ID is required." }

            collectionFor(uid).document(cleanReceiptId).update(
                mapOf(
                    "status" to MpesaReceiptStatus.DISMISSED.name,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun reopenReceipt(userId: String, receiptId: String): Result<Unit> {
        val cleanUid = userId.trim()
        val cleanReceiptId = receiptId.trim()
        val path = "users/$cleanUid/mpesa_receipts/$cleanReceiptId"
        return try {
            val uid = requireUserId()
            require(cleanUid == uid) { "Cannot reopen receipts belonging to another user." }
            require(cleanReceiptId.isNotBlank()) { "Receipt ID is required." }

            collectionFor(uid).document(cleanReceiptId).update(
                mapOf(
                    "status" to MpesaReceiptStatus.STAGED.name,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }
}
