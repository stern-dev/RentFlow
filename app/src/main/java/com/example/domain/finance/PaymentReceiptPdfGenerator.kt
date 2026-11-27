package com.example.domain.finance

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.util.Base64
import androidx.core.content.FileProvider
import com.example.domain.model.Invoice
import com.example.domain.model.Payment
import com.example.domain.model.PaymentReceiptPdf
import com.example.domain.model.PaymentReceiptStatus
import java.io.ByteArrayOutputStream
import java.io.File

data class ReceiptLineItem(
    val label: String,
    val value: String,
    val emphasize: Boolean = false
)

data class PaymentReceiptPdfContent(
    val receiptNumber: String,
    val invoiceId: String,
    val invoiceNumber: String,
    val billingPeriodLabel: String,
    val propertyName: String,
    val unitNumber: String,
    val tenantName: String,
    val dueDateText: String,
    val paidDateText: String,
    val generatedDateText: String,
    val paymentReferenceText: String,
    val paymentMethodText: String,
    val issuedByName: String,
    val totalDueText: String,
    val totalPaidText: String,
    val lineItems: List<ReceiptLineItem>,
    val fileName: String
)

object PaymentReceiptPdfGenerator {

    fun buildReceiptNumber(invoice: Invoice, receiptNumberOverride: String? = null): String {
        val overrideClean = receiptNumberOverride?.trim()?.takeIf { it.length >= 3 }
        if (overrideClean != null) return overrideClean.take(50)
        val periodCompact = invoice.billingPeriod.replace("-", "").ifBlank { "RENT" }
        val unitClean = invoice.unitNumber
            .replace(Regex("[^a-zA-Z0-9]"), "")
            .uppercase()
            .ifBlank { "UNIT" }
            .take(8)
        val suffix = invoice.id
            .replace(Regex("[^a-zA-Z0-9]"), "")
            .uppercase()
            .takeLast(4)
            .ifBlank { "0001" }
        return "RCP-$periodCompact-$unitClean-$suffix".take(50)
    }

    fun buildFileName(receiptNumber: String, invoice: Invoice): String {
        val safeReceipt = receiptNumber
            .replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
            .ifBlank { invoice.id.replace(Regex("[^a-zA-Z0-9_\\-]"), "_") }
        return "rentflow_receipt_${safeReceipt}.pdf".take(140)
    }

    fun buildReceiptContent(
        invoice: Invoice,
        payment: Payment? = null,
        actorName: String = "RentFlow Management",
        receiptNumberOverride: String? = null,
        nowMs: Long = System.currentTimeMillis()
    ): PaymentReceiptPdfContent {
        val receiptNo = buildReceiptNumber(invoice, receiptNumberOverride ?: payment?.receiptNumber)
        val settledMs = invoice.paidAtEpochMs?.takeIf { it > 0L }
            ?: payment?.paymentDateEpochMs?.takeIf { it > 0L }
            ?: nowMs.coerceAtLeast(1L)
        val dueMs = invoice.dueDateEpochMs.takeIf { it > 0L } ?: settledMs
        val periodLabel = CurrencyUtils.billingPeriodLabel(invoice.billingPeriod)
        val totalPaid = invoice.paidCents.takeIf { it > 0L } ?: invoice.totalDueCents

        val items = buildList {
            add(
                ReceiptLineItem(
                    label = if (invoice.prorated) "Prorated Monthly Rent" else "Monthly Rent",
                    value = CurrencyUtils.formatKes(invoice.rentCents, true)
                )
            )
            if (invoice.extraChargesCents > 0L || !invoice.extraChargesNote.isNullOrBlank()) {
                val note = invoice.extraChargesNote?.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: ""
                add(
                    ReceiptLineItem(
                        label = "Extra Charges$note",
                        value = CurrencyUtils.formatKes(invoice.extraChargesCents, true)
                    )
                )
            }
            if (invoice.previousBalanceCents > 0L) {
                add(
                    ReceiptLineItem(
                        label = "Previous Arrears Settled",
                        value = CurrencyUtils.formatKes(invoice.previousBalanceCents, true)
                    )
                )
            }
            if (invoice.lateFeeCents > 0L) {
                add(
                    ReceiptLineItem(
                        label = "Late Fee (${LeaseAutomationEngine.formatPercent(invoice.lateFeeRatePercent)})",
                        value = CurrencyUtils.formatKes(invoice.lateFeeCents, true)
                    )
                )
            } else if (invoice.lateFeeWaived) {
                add(
                    ReceiptLineItem(
                        label = "Late Fee Waived",
                        value = "KES 0.00"
                    )
                )
            }
            add(
                ReceiptLineItem(
                    label = "Total Invoice Billed",
                    value = CurrencyUtils.formatKes(invoice.totalDueCents, true),
                    emphasize = true
                )
            )
            add(
                ReceiptLineItem(
                    label = "Total Paid / Applied",
                    value = CurrencyUtils.formatKes(totalPaid, true),
                    emphasize = true
                )
            )
            add(
                ReceiptLineItem(
                    label = "Outstanding Balance",
                    value = CurrencyUtils.formatKes(0L, true),
                    emphasize = true
                )
            )
        }

        val methodLabel = payment?.channelTypeEnum()?.label
            ?: payment?.methodEnum()?.label
            ?: if (invoice.paidCents > 0L && payment == null) "Credit / Ledger Settlement" else "Verified Settlement"
        val refCode = payment?.referenceCode?.takeIf { it.isNotBlank() }
            ?: "AUTO-${invoice.invoiceNumber}"

        return PaymentReceiptPdfContent(
            receiptNumber = receiptNo,
            invoiceId = invoice.id,
            invoiceNumber = invoice.invoiceNumber,
            billingPeriodLabel = periodLabel,
            propertyName = invoice.propertyName,
            unitNumber = invoice.unitNumber,
            tenantName = invoice.tenantName,
            dueDateText = CurrencyUtils.formatDate(dueMs),
            paidDateText = CurrencyUtils.formatDateTime(settledMs),
            generatedDateText = CurrencyUtils.formatDateTime(nowMs),
            paymentReferenceText = refCode,
            paymentMethodText = methodLabel,
            issuedByName = actorName.trim().ifBlank { "RentFlow Management" }.take(100),
            totalDueText = CurrencyUtils.formatKes(invoice.totalDueCents, true),
            totalPaidText = CurrencyUtils.formatKes(totalPaid, true),
            lineItems = items,
            fileName = buildFileName(receiptNo, invoice)
        )
    }

    fun generatePdfBytes(content: PaymentReceiptPdfContent): ByteArray {
        val doc = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        val margin = 40f
        val page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create())
        val canvas: Canvas = page.canvas

        val navyPaint = Paint().apply {
            color = Color.rgb(11, 31, 58)
            style = Paint.Style.FILL
        }
        val tealAccentPaint = Paint().apply {
            color = Color.rgb(13, 148, 136)
            style = Paint.Style.FILL
        }
        val titlePaint = Paint().apply {
            color = Color.WHITE
            textSize = 18f
            isFakeBoldText = true
            isAntiAlias = true
        }
        val headerSubPaint = Paint().apply {
            color = Color.rgb(204, 251, 241)
            textSize = 11f
            isAntiAlias = true
        }
        val badgeBgPaint = Paint().apply {
            color = Color.rgb(220, 252, 231)
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        val badgeTextPaint = Paint().apply {
            color = Color.rgb(21, 128, 61)
            textSize = 10f
            isFakeBoldText = true
            isAntiAlias = true
        }
        val cardFillPaint = Paint().apply {
            color = Color.rgb(248, 250, 252)
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        val borderPaint = Paint().apply {
            color = Color.rgb(203, 213, 225)
            style = Paint.Style.STROKE
            strokeWidth = 1f
            isAntiAlias = true
        }
        val sectionTitlePaint = Paint().apply {
            color = Color.rgb(11, 31, 58)
            textSize = 11.5f
            isFakeBoldText = true
            isAntiAlias = true
        }
        val labelPaint = Paint().apply {
            color = Color.rgb(71, 85, 105)
            textSize = 10.5f
            isAntiAlias = true
        }
        val valuePaint = Paint().apply {
            color = Color.rgb(15, 23, 42)
            textSize = 10.5f
            isFakeBoldText = true
            isAntiAlias = true
        }
        val amountHeroPaint = Paint().apply {
            color = Color.rgb(21, 128, 61)
            textSize = 22f
            isFakeBoldText = true
            isAntiAlias = true
        }
        val dividerPaint = Paint().apply {
            color = Color.rgb(226, 232, 240)
            strokeWidth = 1f
        }
        val footerPaint = Paint().apply {
            color = Color.rgb(100, 116, 139)
            textSize = 9f
            isAntiAlias = true
        }

        // Top Branded Header
        canvas.drawRect(0f, 0f, pageWidth.toFloat(), 102f, navyPaint)
        canvas.drawRect(0f, 102f, pageWidth.toFloat(), 106f, tealAccentPaint)
        canvas.drawText("RENTFLOW", margin, 36f, titlePaint)
        canvas.drawText("OFFICIAL PAYMENT RECEIPT", margin, 56f, headerSubPaint)
        canvas.drawText(
            "Receipt #${content.receiptNumber}  •  Invoice ${content.invoiceNumber}",
            margin,
            78f,
            headerSubPaint
        )

        val badgeRect = RectF(pageWidth - margin - 102f, 28f, pageWidth - margin, 54f)
        canvas.drawRoundRect(badgeRect, 13f, 13f, badgeBgPaint)
        canvas.drawText("PAID IN FULL", badgeRect.left + 16f, badgeRect.top + 17f, badgeTextPaint)

        // Hero Amount Card
        var y = 128f
        val heroRect = RectF(margin, y, pageWidth - margin, y + 76f)
        canvas.drawRoundRect(heroRect, 8f, 8f, cardFillPaint)
        canvas.drawRoundRect(heroRect, 8f, 8f, borderPaint)
        canvas.drawText("TOTAL AMOUNT PAID (KES)", margin + 16f, y + 24f, labelPaint)
        canvas.drawText(content.totalPaidText, margin + 16f, y + 54f, amountHeroPaint)
        canvas.drawText(
            "Billing Period: ${content.billingPeriodLabel}",
            pageWidth - margin - 185f,
            y + 32f,
            valuePaint
        )
        canvas.drawText(
            "Settled: ${content.paidDateText}",
            pageWidth - margin - 185f,
            y + 52f,
            labelPaint
        )

        // Tenancy & Payment Metadata Box
        y += 96f
        val metaRect = RectF(margin, y, pageWidth - margin, y + 148f)
        canvas.drawRoundRect(metaRect, 8f, 8f, cardFillPaint)
        canvas.drawRoundRect(metaRect, 8f, 8f, borderPaint)
        canvas.drawText("TENANCY & PAYMENT DETAILS", margin + 16f, y + 22f, sectionTitlePaint)

        var metaY = y + 44f
        val metaRows = listOf(
            "Tenant Name" to content.tenantName,
            "Property & Unit" to "${content.propertyName} • Unit ${content.unitNumber}",
            "Invoice Due Date" to content.dueDateText,
            "Payment Method" to content.paymentMethodText,
            "Payment Reference" to content.paymentReferenceText
        )
        for ((k, v) in metaRows) {
            canvas.drawText(k, margin + 16f, metaY, labelPaint)
            canvas.drawText(v.take(48), margin + 170f, metaY, valuePaint)
            metaY += 20f
        }

        // Itemized KES Breakdown
        y += 172f
        canvas.drawText("ITEMIZED KES BREAKDOWN", margin, y, sectionTitlePaint)
        y += 12f
        canvas.drawLine(margin, y, pageWidth - margin, y, dividerPaint)
        y += 22f

        for (item in content.lineItems) {
            val rowLabelPaint = if (item.emphasize) sectionTitlePaint else labelPaint
            val rowValuePaint = if (item.emphasize) sectionTitlePaint else valuePaint
            canvas.drawText(item.label.take(56), margin + 4f, y, rowLabelPaint)
            val valWidth = rowValuePaint.measureText(item.value)
            canvas.drawText(item.value, pageWidth - margin - valWidth - 4f, y, rowValuePaint)
            y += 14f
            canvas.drawLine(margin, y, pageWidth - margin, y, dividerPaint)
            y += 20f
        }

        // Verification Footer
        val footerTop = pageHeight - 96f
        canvas.drawLine(margin, footerTop, pageWidth - margin, footerTop, dividerPaint)
        canvas.drawText(
            "Issued / Confirmed By: ${content.issuedByName}",
            margin,
            footerTop + 20f,
            footerPaint
        )
        canvas.drawText(
            "Generated Automatically on ${content.generatedDateText} • Stored in RentFlow Firestore",
            margin,
            footerTop + 36f,
            footerPaint
        )
        canvas.drawText(
            "Document ID: ${content.invoiceId} • Electronically verified rent receipt.",
            margin,
            footerTop + 52f,
            footerPaint
        )

        doc.finishPage(page)
        val out = ByteArrayOutputStream()
        doc.writeTo(out)
        doc.close()
        return out.toByteArray()
    }

    fun buildStoredReceipt(
        invoice: Invoice,
        payment: Payment? = null,
        generatedByUid: String,
        generatedByName: String,
        receiptNumberOverride: String? = null,
        nowMs: Long = System.currentTimeMillis()
    ): PaymentReceiptPdf {
        val content = buildReceiptContent(
            invoice = invoice,
            payment = payment,
            actorName = generatedByName,
            receiptNumberOverride = receiptNumberOverride,
            nowMs = nowMs
        )
        val pdfBytes = generatePdfBytes(content)
        val encoded = Base64.encodeToString(pdfBytes, Base64.NO_WRAP)
        val dataUri = PaymentReceiptPdf.PDF_DATA_URI_PREFIX + encoded
        require(dataUri.length <= PaymentReceiptPdf.MAX_PDF_DATA_URI_CHARS) {
            "Generated receipt PDF exceeds Firestore document size budget."
        }
        val settledMs = invoice.paidAtEpochMs?.takeIf { it > 0L }
            ?: payment?.paymentDateEpochMs?.takeIf { it > 0L }
            ?: nowMs.coerceAtLeast(1L)

        return PaymentReceiptPdf(
            id = invoice.id,
            receiptNumber = content.receiptNumber,
            invoiceId = invoice.id,
            invoiceNumber = invoice.invoiceNumber,
            leaseId = invoice.leaseId,
            orgId = invoice.orgId,
            propertyId = invoice.propertyId,
            propertyName = invoice.propertyName,
            unitId = invoice.unitId,
            unitNumber = invoice.unitNumber,
            ownerUid = invoice.ownerUid,
            managerUids = invoice.managerUids,
            authorizedUids = (invoice.authorizedUids + invoice.ownerUid).distinct(),
            tenantUid = invoice.tenantUid,
            tenantName = invoice.tenantName,
            billingPeriod = invoice.billingPeriod,
            paymentId = payment?.id?.takeIf { it.isNotBlank() },
            paymentReferenceCode = payment?.referenceCode?.takeIf { it.isNotBlank() },
            paymentMethod = payment?.channelTypeEnum()?.label ?: payment?.methodEnum()?.label,
            totalPaidCents = invoice.paidCents.takeIf { it > 0L } ?: invoice.totalDueCents,
            totalDueCents = invoice.totalDueCents,
            paidAtEpochMs = settledMs,
            generatedAtEpochMs = nowMs.coerceAtLeast(1L),
            generatedByUid = generatedByUid.trim(),
            generatedByName = content.issuedByName,
            fileName = content.fileName,
            mimeType = "application/pdf",
            fileSizeBytes = pdfBytes.size.toLong(),
            pdfDataUri = dataUri,
            status = PaymentReceiptStatus.ISSUED.name
        )
    }

    fun decodePdfBytes(pdfDataUri: String): ByteArray? {
        val trimmed = pdfDataUri.trim()
        if (!trimmed.startsWith(PaymentReceiptPdf.PDF_DATA_URI_PREFIX, ignoreCase = true)) {
            return null
        }
        val payload = trimmed.substring(PaymentReceiptPdf.PDF_DATA_URI_PREFIX.length)
        if (payload.isBlank()) return null
        return runCatching {
            Base64.decode(payload, Base64.DEFAULT)
        }.getOrNull()
    }

    fun writeReceiptToCacheFile(context: Context, receipt: PaymentReceiptPdf): File {
        val exportDir = File(context.cacheDir, "exports")
        if (!exportDir.exists()) exportDir.mkdirs()
        val safeName = receipt.fileName
            .replace(Regex("[^a-zA-Z0-9._\\-]"), "_")
            .let { if (it.endsWith(".pdf", ignoreCase = true)) it else "$it.pdf" }
        val file = File(exportDir, safeName)
        val bytes = decodePdfBytes(receipt.pdfDataUri)
            ?: error("Stored receipt PDF payload is corrupted or empty.")
        file.writeBytes(bytes)
        return file
    }

    fun shareReceiptPdf(context: Context, receipt: PaymentReceiptPdf) {
        val file = writeReceiptToCacheFile(context, receipt)
        PdfPrintAndShareHelper.sharePdfFile(
            context = context,
            file = file,
            chooserTitle = "Share Official Payment Receipt",
            subject = "RentFlow Receipt ${receipt.receiptNumber} (${receipt.propertyName} Unit ${receipt.unitNumber})"
        )
    }

    fun openExternalPdf(context: Context, receipt: PaymentReceiptPdf) {
        val file = writeReceiptToCacheFile(context, receipt)
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val resolved = viewIntent.resolveActivity(context.packageManager)
        if (resolved != null) {
            runCatching { context.startActivity(viewIntent) }
                .onFailure { shareReceiptPdf(context, receipt) }
        } else {
            shareReceiptPdf(context, receipt)
        }
    }

    fun printReceiptPdf(
        context: Context,
        receipt: PaymentReceiptPdf,
        onError: (String) -> Unit
    ) {
        runCatching {
            val file = writeReceiptToCacheFile(context, receipt)
            PdfPrintAndShareHelper.printPdfFile(
                context = context,
                file = file,
                jobName = "RentFlow_Receipt_${receipt.receiptNumber}",
                onUnavailableOrError = onError
            )
        }.onFailure { err ->
            onError(err.localizedMessage ?: "Unable to prepare receipt PDF for printing.")
        }
    }
}
