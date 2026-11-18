package com.example.domain.finance

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.example.domain.model.Expense
import com.example.domain.model.Invoice
import com.example.domain.model.Lease
import com.example.domain.model.Payment
import java.io.File
import java.io.FileOutputStream

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

    fun exportPdfDocument(
        context: Context,
        fileName: String,
        title: String,
        subtitle: String,
        lines: List<String>
    ) {
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
        }
        val subPaint = Paint().apply {
            color = Color.rgb(204, 251, 241)
            textSize = 11f
        }
        val bodyPaint = Paint().apply {
            color = Color.rgb(15, 23, 42)
            textSize = 11f
        }

        canvas.drawRect(0f, 0f, pageWidth.toFloat(), 88f, headerPaint)
        canvas.drawText(title, margin, 38f, titlePaint)
        canvas.drawText(subtitle, margin, 62f, subPaint)

        var y = 116f
        for (line in lines) {
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
            } else {
                bodyPaint.isFakeBoldText = line.startsWith("[H]")
                val text = line.removePrefix("[H]")
                canvas.drawText(text.take(90), margin, y, bodyPaint)
                y += 18f
            }
        }
        doc.finishPage(page)

        val file = File(ensureExportDir(context), fileName)
        FileOutputStream(file).use { out -> doc.writeTo(out) }
        doc.close()
        shareFile(context, file, "application/pdf", "Share PDF Document")
    }
}
