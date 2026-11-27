package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.firebase.OperationType
import com.example.data.firebase.guardFirestoreReads
import com.example.data.firebase.handleFirestoreError
import com.example.domain.finance.PaymentReceiptPdfGenerator
import com.example.domain.model.Invoice
import com.example.domain.model.InvoiceStatus
import com.example.domain.model.Payment
import com.example.domain.model.PaymentReceiptPdf
import com.example.domain.model.PaymentReceiptStatus
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

class PaymentReceiptRepository(
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
            ?: throw IllegalStateException("User must be signed in with Google before accessing payment receipts.")

    fun observeReceiptsForLandlord(userId: String): Flow<List<PaymentReceiptPdf>> {
        val cleanUid = userId.trim()
        if (cleanUid.isBlank()) return flowOf(emptyList())
        return db.collection("payment_receipts")
            .whereArrayContains("authorizedUids", cleanUid)
            .snapshots()
            .map { snap ->
                snap.toObjects(PaymentReceiptPdf::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.generatedAtEpochMs }
            }
            .guardFirestoreReads(OperationType.LIST, "payment_receipts")
    }

    fun observeMyReceipts(userId: String): Flow<List<PaymentReceiptPdf>> {
        val cleanUid = userId.trim()
        if (cleanUid.isBlank()) return flowOf(emptyList())
        return db.collection("payment_receipts")
            .whereEqualTo("tenantUid", cleanUid)
            .whereEqualTo("status", PaymentReceiptStatus.ISSUED.name)
            .snapshots()
            .map { snap ->
                snap.toObjects(PaymentReceiptPdf::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.generatedAtEpochMs }
            }
            .guardFirestoreReads(OperationType.LIST, "payment_receipts")
    }

    suspend fun generateAndStoreReceiptForPaidInvoice(
        invoice: Invoice,
        payment: Payment? = null,
        receiptNumberOverride: String? = null,
        actorName: String = "RentFlow Management",
        nowMs: Long = System.currentTimeMillis()
    ): Result<PaymentReceiptPdf> {
        val cleanInvoiceId = invoice.id.trim()
        val path = "payment_receipts/$cleanInvoiceId"
        return try {
            val uid = requireUserId()
            require(cleanInvoiceId.isNotBlank()) { "Invoice ID is required to generate a PDF receipt." }
            require(invoice.statusEnum() == InvoiceStatus.PAID) {
                "PDF receipts are generated only when an invoice reaches Paid status."
            }

            val docRef = db.collection("payment_receipts").document(cleanInvoiceId)
            val existingSnap = docRef.get().await()
            if (existingSnap.exists()) {
                val existing = existingSnap.toObject(
                    PaymentReceiptPdf::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
                if (existing != null &&
                    existing.statusEnum() == PaymentReceiptStatus.ISSUED &&
                    (payment == null || !existing.paymentId.isNullOrBlank())
                ) {
                    return Result.success(existing)
                }
            }

            val receipt = PaymentReceiptPdfGenerator.buildStoredReceipt(
                invoice = invoice,
                payment = payment,
                generatedByUid = uid,
                generatedByName = actorName,
                receiptNumberOverride = receiptNumberOverride,
                nowMs = nowMs
            )

            if (!existingSnap.exists()) {
                docRef.set(receipt.toCreateMap()).await()
            } else {
                val updates = mutableMapOf<String, Any>(
                    "receiptNumber" to receipt.receiptNumber,
                    "totalPaidCents" to receipt.totalPaidCents,
                    "totalDueCents" to receipt.totalDueCents,
                    "paidAtEpochMs" to receipt.paidAtEpochMs,
                    "generatedAtEpochMs" to receipt.generatedAtEpochMs,
                    "generatedByUid" to receipt.generatedByUid,
                    "generatedByName" to receipt.generatedByName,
                    "fileName" to receipt.fileName,
                    "mimeType" to receipt.mimeType,
                    "fileSizeBytes" to receipt.fileSizeBytes,
                    "pdfDataUri" to receipt.pdfDataUri,
                    "status" to PaymentReceiptStatus.ISSUED.name,
                    "voidReason" to FieldValue.delete(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                receipt.paymentId?.let { updates["paymentId"] = it }
                receipt.paymentReferenceCode?.let { updates["paymentReferenceCode"] = it }
                receipt.paymentMethod?.let { updates["paymentMethod"] = it }
                docRef.update(updates).await()
            }

            Result.success(receipt)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(
                e,
                OperationType.WRITE,
                path
            )
            Result.failure(e)
        }
    }

    suspend fun voidReceiptIfExists(
        invoiceId: String,
        reason: String
    ): Result<Unit> {
        val cleanId = invoiceId.trim()
        if (cleanId.isBlank()) return Result.success(Unit)
        val path = "payment_receipts/$cleanId"
        return try {
            requireUserId()
            val docRef = db.collection("payment_receipts").document(cleanId)
            val snap = docRef.get().await()
            if (!snap.exists()) return Result.success(Unit)
            if (snap.getString("status") == PaymentReceiptStatus.VOIDED.name) {
                return Result.success(Unit)
            }
            docRef.update(
                mapOf(
                    "status" to PaymentReceiptStatus.VOIDED.name,
                    "voidReason" to reason.trim().ifBlank { "Payment reversed" }.take(300),
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
