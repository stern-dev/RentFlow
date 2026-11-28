package com.example.domain.finance

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class DownloadedPdfResult(
    val displayName: String,
    val uri: Uri?,
    val locationLabel: String
)

object PdfDownloadHelper {

    fun normalizePdfFileName(rawName: String): String {
        val cleaned = rawName.trim()
            .replace(Regex("[^a-zA-Z0-9._\\-]"), "_")
            .replace(Regex("_+"), "_")
            .trim('_')
            .ifBlank { "rentflow_document_${System.currentTimeMillis()}" }
        return if (cleaned.endsWith(".pdf", ignoreCase = true)) cleaned else "$cleaned.pdf"
    }

    suspend fun savePdfToDownloads(
        context: Context,
        sourceFile: File,
        suggestedFileName: String
    ): Result<DownloadedPdfResult> = withContext(Dispatchers.IO) {
        runCatching {
            require(sourceFile.exists() && sourceFile.length() > 0L) {
                "Prepared PDF file is missing or empty."
            }
            val safeName = normalizePdfFileName(suggestedFileName)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveViaMediaStoreDownloads(context, sourceFile, safeName)
            } else {
                val targetDir = File(
                    context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.cacheDir,
                    "RentFlow"
                ).apply { if (!exists()) mkdirs() }
                val destFile = File(targetDir, safeName)
                sourceFile.copyTo(destFile, overwrite = true)
                val uri = runCatching {
                    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", destFile)
                }.getOrNull()
                DownloadedPdfResult(
                    displayName = safeName,
                    uri = uri,
                    locationLabel = "RentFlow Documents"
                )
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun saveViaMediaStoreDownloads(
        context: Context,
        sourceFile: File,
        safeName: String
    ): DownloadedPdfResult {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, safeName)
            put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
            put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/RentFlow")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("Unable to create file in Downloads.")
        try {
            val outStream = resolver.openOutputStream(uri)
                ?: error("Unable to open Downloads destination stream.")
            outStream.use { out ->
                FileInputStream(sourceFile).use { input ->
                    input.copyTo(out)
                }
                out.flush()
            }
            val publishValues = ContentValues().apply {
                put(MediaStore.Downloads.IS_PENDING, 0)
            }
            resolver.update(uri, publishValues, null, null)
            return DownloadedPdfResult(
                displayName = safeName,
                uri = uri,
                locationLabel = "Downloads/RentFlow"
            )
        } catch (t: Throwable) {
            runCatching { resolver.delete(uri, null, null) }
            throw t
        }
    }

    suspend fun copyPdfToUri(
        context: Context,
        sourceFile: File,
        targetUri: Uri
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            require(sourceFile.exists() && sourceFile.length() > 0L) {
                "Prepared PDF file is missing or empty."
            }
            val out = context.contentResolver.openOutputStream(targetUri)
                ?: error("Unable to open selected save location.")
            out.use { outputStream ->
                FileInputStream(sourceFile).use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
                outputStream.flush()
            }
            Unit
        }
    }

    fun openPdfDocument(
        context: Context,
        uri: Uri?,
        fallbackFile: File
    ) {
        val resolvedUri = uri ?: FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            fallbackFile
        )
        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(resolvedUri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        if (viewIntent.resolveActivity(context.packageManager) != null) {
            runCatching { context.startActivity(viewIntent) }
                .onFailure {
                    PdfPrintAndShareHelper.sharePdfFile(
                        context = context,
                        file = fallbackFile,
                        chooserTitle = "Open or Share PDF"
                    )
                }
        } else {
            PdfPrintAndShareHelper.sharePdfFile(
                context = context,
                file = fallbackFile,
                chooserTitle = "Open or Share PDF"
            )
        }
    }
}
