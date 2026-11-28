package com.example.domain.finance

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.util.Base64
import androidx.core.content.FileProvider
import com.example.domain.model.Announcement
import com.example.domain.model.DigitalSignature
import com.example.domain.model.Expense
import com.example.domain.model.Invoice
import com.example.domain.model.Lease
import com.example.domain.model.Payment
import java.io.File
import java.io.FileOutputStream

data class PdfSignatureBlock(
    val roleTitle: String,
    val signature: DigitalSignature?
)

object ReportExporter {

    private fun ensureExportDir(context: Context): File {
        val dir = File(context.cacheDir, "exports")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun shareFile(context: Context, file: File, mimeType: String, chooserTitle: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, chooserTitle))
    }

    private fun csvEscape(value: String): String {
        val escaped = value.replace("\"", "\"\"")
        return "\"$escaped\""
    }

    fun exportLandlordFinancialCsv(
        context: Context,
        summary: PortfolioSummary,
        invoices: List<Invoice>,
        payments: List<Payment>,
        expenses: List<Expense>
    ) {
        val sb = StringBuilder()
        sb.appendLine("RENTFLOW PORTFOLIO FINANCIAL REPORT (KES)")
        sb.appendLine("Generated,${csvEscape(CurrencyUtils.formatDateTime(System.currentTimeMillis()))}")
        sb.appendLine("Total Properties,${summary.totalProperties}")
        sb.appendLine("Total Units,${summary.totalUnits}")
        sb.appendLine("Occupied Units,${summary.occupiedUnits}")
        sb.appendLine("Expected Monthly Rent,${CurrencyUtils.formatKes(summary.expectedMonthlyRentCents, true)}")
        sb.appendLine("Total Collected,${CurrencyUtils.formatKes(summary.totalCollectedCents, true)}")
        sb.appendLine("Outstanding Arrears,${CurrencyUtils.formatKes(summary.outstandingRentCents, true)}")
        sb.appendLine("Total Expenses,${CurrencyUtils.formatKes(summary.totalExpensesCents, true)}")
        sb.appendLine("Net Rental Income,${CurrencyUtils.formatKes(summary.netRentalIncomeCents, true)}")
        sb.appendLine()
        sb.appendLine("INVOICES")
        sb.appendLine("Invoice Number,Property,Unit,Tenant,Period,Total Due (KES),Paid (KES),Outstanding (KES),Status")
        invoices.forEach { inv ->
            sb.appendLine(
                listOf(
                    csvEscape(inv.invoiceNumber),
                    csvEscape(inv.propertyName),
                    csvEscape(inv.unitNumber),
                    csvEscape(inv.tenantName),
                    csvEscape(inv.billingPeriod),
                    csvEscape(CurrencyUtils.centsToInputString(inv.totalDueCents)),
                    csvEscape(CurrencyUtils.centsToInputString(inv.paidCents)),
                    csvEscape(CurrencyUtils.centsToInputString(inv.outstandingCents)),
                    csvEscape(inv.status)
                ).joinToString(",")
            )
        }
        sb.appendLine()
        sb.appendLine("PAYMENTS")
        sb.appendLine("Reference,Receipt,Property,Unit,Tenant,Method,Simulated,Amount (KES),Status,Date")
        payments.forEach { pay ->
            sb.appendLine(
                listOf(
                    csvEscape(pay.referenceCode),
                    csvEscape(pay.receiptNumber ?: "-"),
                    csvEscape(pay.propertyName),
                    csvEscape(pay.unitNumber),
                    csvEscape(pay.tenantName),
                    csvEscape(pay.method),
                    csvEscape(pay.simulatedCheckout.toString()),
                    csvEscape(CurrencyUtils.centsToInputString(pay.amountCents)),
                    csvEscape(pay.status),
                    csvEscape(CurrencyUtils.formatDate(pay.paymentDateEpochMs))
                ).joinToString(",")
            )
        }
        sb.appendLine()
        sb.appendLine("EXPENSES")
        sb.appendLine("Title,Property,Category,Amount (KES),Date")
        expenses.forEach { exp ->
            sb.appendLine(
                listOf(
                    csvEscape(exp.title),
                    csvEscape(exp.propertyName),
                    csvEscape(exp.category),
                    csvEscape(CurrencyUtils.centsToInputString(exp.amountCents)),
                    csvEscape(CurrencyUtils.formatDate(exp.expenseDateEpochMs))
                ).joinToString(",")
            )
        }

        val file = File(ensureExportDir(context), "rentflow_financial_report.csv")
        file.writeText(sb.toString())
        shareFile(context, file, "text/csv", "Export Financial Report (CSV)")
    }

    fun exportTenantStatementCsv(
        context: Context,
        lease: Lease,
        invoices: List<Invoice>,
        payments: List<Payment>
    ) {
        val sb = StringBuilder()
        sb.appendLine("RENTFLOW TENANT RENT STATEMENT (KES)")
        sb.appendLine("Tenant,${csvEscape(lease.tenantName)}")
        sb.appendLine("Property,${csvEscape(lease.propertyName)}")
        sb.appendLine("Unit,${csvEscape(lease.unitNumber)}")
        sb.appendLine("Monthly Rent,${csvEscape(CurrencyUtils.formatKes(lease.rentCents, true))}")
        sb.appendLine("Account Credit,${csvEscape(CurrencyUtils.formatKes(lease.creditBalanceCents, true))}")
        sb.appendLine()
        sb.appendLine("INVOICES")
        sb.appendLine("Invoice,Period,Total Due,Paid,Outstanding,Status")
        invoices.forEach { inv ->
            sb.appendLine(
                listOf(
                    csvEscape(inv.invoiceNumber),
                    csvEscape(inv.billingPeriod),
                    csvEscape(CurrencyUtils.formatKes(inv.totalDueCents, true)),
                    csvEscape(CurrencyUtils.formatKes(inv.paidCents, true)),
                    csvEscape(CurrencyUtils.formatKes(inv.outstandingCents, true)),
                    csvEscape(inv.status)
                ).joinToString(",")
            )
        }
        sb.appendLine()
        sb.appendLine("PAYMENTS")
        sb.appendLine("Reference,Receipt,Method,Amount,Status,Date")
        payments.forEach { pay ->
            sb.appendLine(
                listOf(
                    csvEscape(pay.referenceCode),
                    csvEscape(pay.receiptNumber ?: "-"),
                    csvEscape(pay.method),
                    csvEscape(CurrencyUtils.formatKes(pay.amountCents, true)),
                    csvEscape(pay.status),
                    csvEscape(CurrencyUtils.formatDate(pay.paymentDateEpochMs))
                ).joinToString(",")
            )
        }

        val file = File(ensureExportDir(context), "rentflow_tenant_statement.csv")
        file.writeText(sb.toString())
        shareFile(context, file, "text/csv", "Export Rent Statement (CSV)")
    }

    internal fun buildSignedLeaseSignatureBlocks(
        lease: Lease,
        landlordFallbackSignature: DigitalSignature? = null,
        tenantFallbackSignature: DigitalSignature? = null
    ): List<PdfSignatureBlock> {
        val landlordSig = lease.landlordSignature ?: landlordFallbackSignature
        val tenantSig = lease.tenantSignature ?: tenantFallbackSignature
        return listOf(
            PdfSignatureBlock("LANDLORD / AUTHORIZED AGENT", landlordSig),
            PdfSignatureBlock("TENANT", tenantSig)
        )
    }

    internal fun buildSignedLeaseAgreementLines(
        lease: Lease,
        landlordFallbackSignature: DigitalSignature? = null,
        tenantFallbackSignature: DigitalSignature? = null
    ): List<String> {
        val landlordSig = lease.landlordSignature ?: landlordFallbackSignature
        val tenantSig = lease.tenantSignature ?: tenantFallbackSignature
        val execStatus = when {
            landlordSig != null && tenantSig != null -> "Mutually Signed & Locked"
            landlordSig != null -> "Signed by Landlord / Pending Tenant Signature"
            else -> "Unsigned"
        }
        return listOf(
            "[H]TENANCY PARTIES & TERM",
            "Tenant: ${lease.tenantName} (${lease.tenantPhone})",
            "Lease Status: ${lease.statusEnum().label}",
            "Execution Status: $execStatus",
            "Term: ${CurrencyUtils.formatDate(lease.startDateEpochMs)} to ${CurrencyUtils.formatDate(lease.endDateEpochMs)}",
            "---",
            "[H]FINANCIAL TERMS (KES)",
            "Monthly Rent: ${CurrencyUtils.formatKes(lease.rentCents, true)}",
            "Security Deposit: ${CurrencyUtils.formatKes(lease.depositCents, true)}",
            "Rent Due Day: Day ${lease.dueDayOfMonth} of each month",
            "Account Credit Balance: ${CurrencyUtils.formatKes(lease.creditBalanceCents, true)}",
            "---",
            "[H]AGREEMENT SUMMARY",
            lease.termsSummary.ifBlank { "Standard Kenyan residential tenancy terms apply." }
        )
    }

    fun generateSignedLeaseAgreementPdfFile(
        context: Context,
        lease: Lease,
        landlordFallbackSignature: DigitalSignature? = null,
        tenantFallbackSignature: DigitalSignature? = null
    ): File = generatePdfDocumentFile(
        context = context,
        fileName = "lease_${lease.unitNumber}.pdf",
        title = "RentFlow Tenancy Agreement",
        subtitle = "${lease.propertyName} • Unit ${lease.unitNumber}",
        lines = buildSignedLeaseAgreementLines(lease, landlordFallbackSignature, tenantFallbackSignature),
        signatureBlocks = buildSignedLeaseSignatureBlocks(lease, landlordFallbackSignature, tenantFallbackSignature)
    )

    internal fun buildPropertyNoticeLines(announcement: Announcement): List<String> {
        val postedEpoch = announcement.createdAt?.toDate()?.time
            ?: announcement.updatedAt?.toDate()?.time
            ?: System.currentTimeMillis()
        return buildList {
            add("[H]PROPERTY NOTICE DETAILS")
            add("Property: ${announcement.propertyName.ifBlank { "RentFlow Residence" }}")
            add("Issued By: ${announcement.authorName.ifBlank { "Property Management" }}")
            add("Posted On: ${CurrencyUtils.formatDateTime(postedEpoch)}")
            add("---")
            add("[H]${announcement.title.ifBlank { "Property Announcement" }}")
            val bodyParagraphs = announcement.body.trim()
                .ifBlank { "No additional notice details provided." }
                .split('\n')
            addAll(bodyParagraphs)
        }
    }

    fun generatePropertyNoticePdfFile(
        context: Context,
        announcement: Announcement
    ): File {
        val postedEpoch = announcement.createdAt?.toDate()?.time
            ?: announcement.updatedAt?.toDate()?.time
            ?: System.currentTimeMillis()
        val safeProp = announcement.propertyName
            .replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
            .ifBlank { "property" }
        val safeId = announcement.id
            .replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
            .take(16)
            .ifBlank { "notice" }
        return generatePdfDocumentFile(
            context = context,
            fileName = "notice_${safeProp}_${safeId}.pdf",
            title = "RentFlow Property Notice",
            subtitle = "${announcement.propertyName.ifBlank { "Property Management" }} • ${CurrencyUtils.formatDate(postedEpoch)}",
            lines = buildPropertyNoticeLines(announcement)
        )
    }

    internal fun buildPaymentConfirmationLines(pay: Payment): List<String> = listOf(
        "[H]PAYMENT CONFIRMATION",
        "Tenant: ${pay.tenantName}",
        "Property & Unit: ${pay.propertyName} • Unit ${pay.unitNumber}",
        "Amount Paid: ${CurrencyUtils.formatKes(pay.amountCents, true)}",
        "Method: ${pay.methodEnum().label}${if (pay.simulatedCheckout) " (Simulated Checkout)" else ""}",
        "Transaction Reference: ${pay.referenceCode}",
        "Status: ${pay.statusEnum().label}",
        "---",
        "[H]INVOICE ALLOCATION",
        pay.allocationsSummary ?: "Credited to lease ledger",
        "Verification Notes: ${pay.verificationNotes ?: "Verified"}"
    )

    fun generatePaymentConfirmationPdfFile(
        context: Context,
        pay: Payment
    ): File {
        val safeRef = (pay.receiptNumber ?: pay.referenceCode)
            .replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
            .ifBlank { pay.id.take(12) }
        return generatePdfDocumentFile(
            context = context,
            fileName = "receipt_${safeRef}.pdf",
            title = "RentFlow Payment Receipt",
            subtitle = "Receipt #${pay.receiptNumber ?: pay.id.take(8)} • ${CurrencyUtils.formatDateTime(pay.paymentDateEpochMs)}",
            lines = buildPaymentConfirmationLines(pay)
        )
    }

    fun exportSignedLeaseAgreementPdf(
        context: Context,
        lease: Lease,
        landlordFallbackSignature: DigitalSignature? = null,
        tenantFallbackSignature: DigitalSignature? = null
    ) {
        val file = generateSignedLeaseAgreementPdfFile(
            context = context,
            lease = lease,
            landlordFallbackSignature = landlordFallbackSignature,
            tenantFallbackSignature = tenantFallbackSignature
        )
        shareFile(context, file, "application/pdf", "Share PDF Document")
    }

    fun exportPdfDocument(
        context: Context,
        fileName: String,
        title: String,
        subtitle: String,
        lines: List<String>,
        signatureBlocks: List<PdfSignatureBlock> = emptyList()
    ) {
        val file = generatePdfDocumentFile(
            context = context,
            fileName = fileName,
            title = title,
            subtitle = subtitle,
            lines = lines,
            signatureBlocks = signatureBlocks
        )
        shareFile(context, file, "application/pdf", "Share PDF Document")
    }

    internal fun wrapPdfLines(lines: List<String>, maxCharsPerLine: Int = 82): List<String> {
        val result = mutableListOf<String>()
        for (raw in lines) {
            if (raw.startsWith("---") || raw.startsWith("[H]")) {
                result.add(raw)
                continue
            }
            val trimmed = raw.trimEnd()
            if (trimmed.isEmpty()) {
                result.add("")
                continue
            }
            if (trimmed.length <= maxCharsPerLine) {
                result.add(trimmed)
                continue
            }
            val words = trimmed.split(Regex("\\s+"))
            var current = StringBuilder()
            for (word in words) {
                if (word.length > maxCharsPerLine) {
                    if (current.isNotEmpty()) {
                        result.add(current.toString())
                        current = StringBuilder()
                    }
                    var idx = 0
                    while (idx < word.length) {
                        val end = (idx + maxCharsPerLine).coerceAtMost(word.length)
                        result.add(word.substring(idx, end))
                        idx = end
                    }
                } else if (current.isEmpty()) {
                    current.append(word)
                } else if (current.length + 1 + word.length <= maxCharsPerLine) {
                    current.append(' ').append(word)
                } else {
                    result.add(current.toString())
                    current = StringBuilder(word)
                }
            }
            if (current.isNotEmpty()) {
                result.add(current.toString())
            }
        }
        return result
    }

    fun generatePdfDocumentFile(
        context: Context,
        fileName: String,
        title: String,
        subtitle: String,
        lines: List<String>,
        signatureBlocks: List<PdfSignatureBlock> = emptyList()
    ): File {
        val doc = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        val margin = 40f
        var pageNum = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create())
        var canvas: Canvas = page.canvas

        val headerPaint = Paint().apply {
            color = Color.rgb(11, 31, 58)
            style = Paint.Style.FILL
        }
        val titlePaint = Paint().apply {
            color = Color.WHITE
            textSize = 18f
            isFakeBoldText = true
            isAntiAlias = true
        }
        val subPaint = Paint().apply {
            color = Color.rgb(204, 251, 241)
            textSize = 11f
            isAntiAlias = true
        }
        val bodyPaint = Paint().apply {
            color = Color.rgb(15, 23, 42)
            textSize = 11f
            isAntiAlias = true
        }

        canvas.drawRect(0f, 0f, pageWidth.toFloat(), 88f, headerPaint)
        canvas.drawText(title.take(60), margin, 38f, titlePaint)
        canvas.drawText(subtitle.take(80), margin, 62f, subPaint)

        var y = 116f
        val wrappedLines = wrapPdfLines(lines)
        for (line in wrappedLines) {
            if (y > pageHeight - 48f) {
                doc.finishPage(page)
                pageNum++
                page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create())
                canvas = page.canvas
                y = 48f
            }
            if (line.startsWith("---")) {
                val dividerPaint = Paint().apply { color = Color.LTGRAY; strokeWidth = 1f }
                canvas.drawLine(margin, y - 4f, pageWidth - margin, y - 4f, dividerPaint)
                y += 12f
            } else if (line.isBlank()) {
                y += 10f
            } else {
                bodyPaint.isFakeBoldText = line.startsWith("[H]")
                val text = line.removePrefix("[H]")
                canvas.drawText(text, margin, y, bodyPaint)
                y += 18f
            }
        }

        if (signatureBlocks.isNotEmpty()) {
            val blockHeight = 148f
            if (y > pageHeight - blockHeight - 36f) {
                doc.finishPage(page)
                pageNum++
                page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create())
                canvas = page.canvas
                y = 48f
            } else {
                y += 10f
            }

            val dividerPaint = Paint().apply { color = Color.LTGRAY; strokeWidth = 1f }
            canvas.drawLine(margin, y, pageWidth - margin, y, dividerPaint)
            y += 18f

            bodyPaint.isFakeBoldText = true
            bodyPaint.textSize = 11f
            canvas.drawText("DIGITAL EXECUTION & SIGNATURES", margin, y, bodyPaint)
            y += 12f

            val gap = 14f
            val columns = 2
            val boxWidth = (pageWidth - (2f * margin) - gap) / columns
            val boxHeight = 120f

            val cardBgPaint = Paint().apply {
                color = Color.rgb(248, 250, 252)
                style = Paint.Style.FILL
            }
            val cardBorderPaint = Paint().apply {
                color = Color.rgb(203, 213, 225)
                style = Paint.Style.STROKE
                strokeWidth = 1f
            }
            val sigPaperPaint = Paint().apply {
                color = Color.WHITE
                style = Paint.Style.FILL
            }
            val rolePaint = Paint().apply {
                color = Color.rgb(11, 31, 58)
                textSize = 9.5f
                isFakeBoldText = true
            }
            val metaPaint = Paint().apply {
                color = Color.rgb(51, 65, 85)
                textSize = 9f
            }
            val pendingPaint = Paint().apply {
                color = Color.rgb(148, 163, 184)
                textSize = 9.5f
            }

            signatureBlocks.chunked(columns).forEach { rowBlocks ->
                if (y > pageHeight - boxHeight - 24f) {
                    doc.finishPage(page)
                    pageNum++
                    page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create())
                    canvas = page.canvas
                    y = 48f
                }
                rowBlocks.forEachIndexed { colIndex, block ->
                    val left = margin + colIndex * (boxWidth + gap)
                    val top = y
                    val right = left + boxWidth
                    val bottom = top + boxHeight

                    val cardRect = RectF(left, top, right, bottom)
                    canvas.drawRoundRect(cardRect, 6f, 6f, cardBgPaint)
                    canvas.drawRoundRect(cardRect, 6f, 6f, cardBorderPaint)
                    canvas.drawText(block.roleTitle.take(36), left + 10f, top + 16f, rolePaint)

                    val sigRect = RectF(left + 10f, top + 24f, right - 10f, top + 74f)
                    canvas.drawRect(sigRect, sigPaperPaint)
                    canvas.drawRect(sigRect, cardBorderPaint)

                    val sig = block.signature
                    if (sig != null) {
                        val bmp = decodeSignatureDataUri(sig.signatureImageRef)
                        if (bmp != null) {
                            val srcRect = Rect(0, 0, bmp.width, bmp.height)
                            canvas.drawBitmap(bmp, srcRect, sigRect, null)
                            bmp.recycle()
                        } else {
                            canvas.drawText("[Signed Digitally]", left + 18f, top + 53f, metaPaint)
                        }
                        canvas.drawText(
                            "Signed by: ${sig.signerName.take(34)}",
                            left + 10f,
                            top + 90f,
                            metaPaint
                        )
                        val dateText = CurrencyUtils.formatDateTime(sig.signedAtEpochMs)
                        val scopeText = if (sig.coversInspectionReport && sig.coversLeaseReport) {
                            "Scope: Inspection & Lease"
                        } else {
                            "Scope: Condition Report"
                        }
                        canvas.drawText(
                            "$dateText • $scopeText".take(42),
                            left + 10f,
                            top + 106f,
                            metaPaint
                        )
                    } else {
                        canvas.drawText("Pending Signature", left + 18f, top + 53f, pendingPaint)
                        canvas.drawText("Name: ________________________", left + 10f, top + 90f, pendingPaint)
                        canvas.drawText("Date: Pending execution", left + 10f, top + 106f, pendingPaint)
                    }
                }
                y += boxHeight + 12f
            }
        }

        doc.finishPage(page)

        val safeFileName = PdfDownloadHelper.normalizePdfFileName(fileName)
        val file = File(ensureExportDir(context), safeFileName)
        FileOutputStream(file).use { out -> doc.writeTo(out) }
        doc.close()
        return file
    }

    internal fun decodeSignatureDataUri(dataUri: String): android.graphics.Bitmap? {
        val trimmed = dataUri.trim()
        val marker = ";base64,"
        val idx = trimmed.indexOf(marker, ignoreCase = true)
        if (!trimmed.startsWith("data:image/", ignoreCase = true) || idx < 0) return null
        val payload = trimmed.substring(idx + marker.length)
        if (payload.isBlank()) return null
        return runCatching {
            val bytes = Base64.decode(payload, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }.getOrNull()
    }
}

