package com.example.domain.finance

import android.content.Context
import android.content.Intent
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

object PdfPrintAndShareHelper {

    fun sharePdfFile(
        context: Context,
        file: File,
        chooserTitle: String = "Share PDF Document",
        subject: String? = null
    ) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            if (!subject.isNullOrBlank()) {
                putExtra(Intent.EXTRA_SUBJECT, subject)
            }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, chooserTitle))
    }

    fun printPdfFile(
        context: Context,
        file: File,
        jobName: String,
        onUnavailableOrError: (String) -> Unit = {}
    ) {
        if (!file.exists()) {
            onUnavailableOrError("Generated PDF file is no longer available.")
            return
        }
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
        if (printManager == null) {
            onUnavailableOrError("Printing service is unavailable on this device.")
            return
        }
        runCatching {
            printManager.print(
                jobName,
                FilePdfPrintDocumentAdapter(file = file, documentName = file.name),
                PrintAttributes.Builder().build()
            )
        }.onFailure { err ->
            onUnavailableOrError(err.localizedMessage ?: "Unable to start print job.")
        }
    }
}

internal class FilePdfPrintDocumentAdapter(
    private val file: File,
    private val documentName: String
) : PrintDocumentAdapter() {

    private fun resolvePageCount(): Int {
        if (!file.exists()) return PrintDocumentInfo.PAGE_COUNT_UNKNOWN
        return runCatching {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer -> renderer.pageCount.coerceAtLeast(1) }
            }
        }.getOrDefault(PrintDocumentInfo.PAGE_COUNT_UNKNOWN)
    }

    override fun onLayout(
        oldAttributes: PrintAttributes?,
        newAttributes: PrintAttributes,
        cancellationSignal: CancellationSignal?,
        callback: LayoutResultCallback,
        extras: Bundle?
    ) {
        if (cancellationSignal?.isCanceled == true) {
            callback.onLayoutCancelled()
            return
        }
        val info = PrintDocumentInfo.Builder(documentName)
            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
            .setPageCount(resolvePageCount())
            .build()
        callback.onLayoutFinished(info, true)
    }

    override fun onWrite(
        pages: Array<out PageRange>,
        destination: ParcelFileDescriptor,
        cancellationSignal: CancellationSignal?,
        callback: WriteResultCallback
    ) {
        if (cancellationSignal?.isCanceled == true) {
            callback.onWriteCancelled()
            return
        }
        try {
            FileInputStream(file).use { input ->
                FileOutputStream(destination.fileDescriptor).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        if (cancellationSignal?.isCanceled == true) {
                            callback.onWriteCancelled()
                            return
                        }
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                    }
                    output.flush()
                }
            }
            callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
        } catch (t: Throwable) {
            callback.onWriteFailed(t.localizedMessage ?: "Failed to write PDF to print spooler.")
        }
    }
}
