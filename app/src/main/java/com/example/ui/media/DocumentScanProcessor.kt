package com.example.ui.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.media.ExifInterface
import android.net.Uri
import android.util.Base64
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.example.domain.finance.CurrencyUtils
import com.example.domain.model.ScanFilterMode
import com.example.domain.model.ScannedDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

data class NormalizedCropRect(
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 1f,
    val bottom: Float = 1f
) {
    fun normalized(): NormalizedCropRect {
        val l = left.coerceIn(0f, 0.9f)
        val t = top.coerceIn(0f, 0.9f)
        val r = right.coerceIn(l + 0.1f, 1f)
        val b = bottom.coerceIn(t + 0.1f, 1f)
        return NormalizedCropRect(l, t, r, b)
    }

    fun isFullFrame(): Boolean =
        left <= 0.01f && top <= 0.01f && right >= 0.99f && bottom >= 0.99f
}

object DocumentScanProcessor {
    private const val JPEG_DATA_PREFIX = "data:image/jpeg;base64,"
    private const val GENERIC_DATA_PREFIX = "data:image/"
    private const val DEFAULT_DECODE_MAX_DIM = 1440

    suspend fun decodeUriForEditing(
        context: Context,
        uri: Uri,
        maxDimPx: Int = DEFAULT_DECODE_MAX_DIM
    ): Bitmap? = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, boundsOpts)
        } ?: return@withContext null

        val srcWidth = boundsOpts.outWidth
        val srcHeight = boundsOpts.outHeight
        if (srcWidth <= 0 || srcHeight <= 0) return@withContext null

        val orientation = runCatching {
            resolver.openInputStream(uri)?.use { stream ->
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(srcWidth, srcHeight, maxDimPx)
        }
        val decoded = resolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOpts)
        } ?: return@withContext null

        val oriented = applyExifOrientation(decoded, orientation)
        val scaled = scaleDownIfNeeded(oriented, maxDimPx)
        if (oriented !== decoded && scaled !== oriented) {
            oriented.recycle()
        }
        if (scaled !== decoded) {
            decoded.recycle()
        }
        scaled
    }

    suspend fun decodeFileForEditing(
        file: File,
        maxDimPx: Int = DEFAULT_DECODE_MAX_DIM
    ): Bitmap? = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext null
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, boundsOpts)
        val srcWidth = boundsOpts.outWidth
        val srcHeight = boundsOpts.outHeight
        if (srcWidth <= 0 || srcHeight <= 0) return@withContext null

        val orientation = runCatching {
            ExifInterface(file.absolutePath).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(srcWidth, srcHeight, maxDimPx)
        }
        val decoded = BitmapFactory.decodeFile(file.absolutePath, decodeOpts) ?: return@withContext null
        val oriented = applyExifOrientation(decoded, orientation)
        val scaled = scaleDownIfNeeded(oriented, maxDimPx)
        if (oriented !== decoded && scaled !== oriented) {
            oriented.recycle()
        }
        if (scaled !== decoded) {
            decoded.recycle()
        }
        scaled
    }

    fun applyCropAndRotation(
        source: Bitmap,
        cropRect: NormalizedCropRect,
        rotationDegrees: Int
    ): Bitmap {
        val norm = cropRect.normalized()
        val cropX = (norm.left * source.width).roundToInt().coerceIn(0, source.width - 1)
        val cropY = (norm.top * source.height).roundToInt().coerceIn(0, source.height - 1)
        val cropW = ((norm.right - norm.left) * source.width).roundToInt().coerceIn(1, source.width - cropX)
        val cropH = ((norm.bottom - norm.top) * source.height).roundToInt().coerceIn(1, source.height - cropY)

        val normalizedRot = ((rotationDegrees % 360) + 360) % 360
        return if (normalizedRot == 0) {
            Bitmap.createBitmap(source, cropX, cropY, cropW, cropH)
        } else {
            val matrix = Matrix().apply { postRotate(normalizedRot.toFloat()) }
            Bitmap.createBitmap(source, cropX, cropY, cropW, cropH, matrix, true)
        }
    }

    fun applyScanFilter(source: Bitmap, mode: ScanFilterMode): Bitmap {
        if (mode == ScanFilterMode.ORIGINAL) {
            return source.copy(Bitmap.Config.ARGB_8888, false)
        }
        val width = source.width
        val height = source.height
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)

        when (mode) {
            ScanFilterMode.ORIGINAL -> Unit
            ScanFilterMode.CRISP_DOCUMENT -> {
                val contrast = 1.38f
                val offset = (-0.5f * contrast + 0.5f) * 255f + 14f
                for (i in pixels.indices) {
                    val p = pixels[i]
                    val a = p ushr 24
                    val r = (((p shr 16) and 0xFF) * contrast + offset).roundToInt().coerceIn(0, 255)
                    val g = (((p shr 8) and 0xFF) * contrast + offset).roundToInt().coerceIn(0, 255)
                    val b = ((p and 0xFF) * contrast + offset).roundToInt().coerceIn(0, 255)
                    pixels[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
                }
            }
            ScanFilterMode.BLACK_AND_WHITE -> {
                val contrast = 1.85f
                val offset = (-0.5f * contrast + 0.5f) * 255f + 22f
                for (i in pixels.indices) {
                    val p = pixels[i]
                    val a = p ushr 24
                    val r = (p shr 16) and 0xFF
                    val g = (p shr 8) and 0xFF
                    val b = p and 0xFF
                    val luma = 0.299f * r + 0.587f * g + 0.114f * b
                    val v = (luma * contrast + offset).roundToInt().coerceIn(0, 255)
                    pixels[i] = (a shl 24) or (v shl 16) or (v shl 8) or v
                }
            }
        }

        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        output.setPixels(pixels, 0, width, 0, 0, width, height)
        return output
    }

    fun processPagePreview(
        source: Bitmap,
        cropRect: NormalizedCropRect,
        rotationDegrees: Int,
        filterMode: ScanFilterMode,
        previewMaxDimPx: Int = 960
    ): Bitmap {
        val cropped = applyCropAndRotation(source, cropRect, rotationDegrees)
        val scaled = scaleDownIfNeeded(cropped, previewMaxDimPx)
        val filtered = applyScanFilter(scaled, filterMode)
        if (scaled !== cropped) cropped.recycle()
        if (filtered !== scaled) scaled.recycle()
        return filtered
    }

    suspend fun compressPagesToBudget(
        pages: List<Bitmap>,
        maxTotalChars: Int = ScannedDocument.MAX_TOTAL_PAYLOAD_CHARS,
        maxPerPageChars: Int = ScannedDocument.MAX_SINGLE_PAGE_CHARS
    ): Result<List<String>> = withContext(Dispatchers.Default) {
        if (pages.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Capture at least one document page."))
        }
        if (pages.size > ScannedDocument.MAX_PAGES) {
            return@withContext Result.failure(IllegalArgumentException("Maximum ${ScannedDocument.MAX_PAGES} pages per document."))
        }

        val perPageBudget = minOf(maxPerPageChars, (maxTotalChars / pages.size).coerceAtLeast(80_000))
        val dimCandidates = if (pages.size <= 2) intArrayOf(1120, 880, 680) else intArrayOf(960, 760, 600)
        val qualityCandidates = intArrayOf(82, 72, 62, 52, 42, 34)

        val encoded = mutableListOf<String>()
        for (page in pages) {
            var bestUri: String? = null
            for (maxDim in dimCandidates) {
                val scaled = scaleDownIfNeeded(page, maxDim)
                try {
                    for (quality in qualityCandidates) {
                        val out = ByteArrayOutputStream()
                        scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
                        val candidate = "$JPEG_DATA_PREFIX${Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)}"
                        bestUri = candidate
                        if (candidate.length <= perPageBudget) {
                            break
                        }
                    }
                    if (bestUri != null && bestUri.length <= perPageBudget) {
                        break
                    }
                } finally {
                    if (scaled !== page) {
                        scaled.recycle()
                    }
                }
            }
            val finalUri = bestUri
                ?: return@withContext Result.failure(IllegalStateException("Unable to encode scanned page."))
            if (finalUri.length > maxPerPageChars) {
                return@withContext Result.failure(
                    IllegalArgumentException("One scanned page is too large even after compression. Try tightening crop or fewer details.")
                )
            }
            encoded.add(finalUri)
        }

        val totalChars = encoded.sumOf { it.length }
        if (totalChars > maxTotalChars) {
            return@withContext Result.failure(
                IllegalArgumentException("Combined pages exceed Firestore size budget (${totalChars / 1024} KB). Remove a page or crop tighter.")
            )
        }
        Result.success(encoded)
    }

    fun isJpegDataUri(ref: String): Boolean {
        val trimmed = ref.trim()
        return trimmed.startsWith(JPEG_DATA_PREFIX, ignoreCase = true) && trimmed.length > JPEG_DATA_PREFIX.length
    }

    fun decodeDataUriBytes(ref: String): ByteArray? {
        val trimmed = ref.trim()
        if (!trimmed.startsWith(GENERIC_DATA_PREFIX, ignoreCase = true)) return null
        val commaIndex = trimmed.indexOf(";base64,", ignoreCase = true)
        if (commaIndex < 0) return null
        val payload = trimmed.substring(commaIndex + ";base64,".length)
        if (payload.isBlank()) return null
        return runCatching { Base64.decode(payload, Base64.DEFAULT) }.getOrNull()
    }

    fun decodeDataUriToBitmap(ref: String): Bitmap? {
        val bytes = decodeDataUriBytes(ref) ?: return null
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    fun decodeDataUriToImageBitmap(ref: String): ImageBitmap? =
        decodeDataUriToBitmap(ref)?.asImageBitmap()

    fun estimatePayloadKb(pageRefs: List<String>): Int {
        val totalBytes = pageRefs.sumOf { decodeDataUriBytes(it)?.size ?: 0 }
        return ((totalBytes + 1023) / 1024).coerceAtLeast(if (pageRefs.isEmpty()) 0 else 1)
    }

    fun scaleForAiOcr(bitmap: Bitmap, maxDimPx: Int = 1280): Bitmap =
        scaleDownIfNeeded(bitmap, maxDimPx)

    internal fun buildPdfMetadataParts(document: ScannedDocument): List<String> = buildList {
        if (!document.tenantName.isNullOrBlank()) add("Tenant: ${document.tenantName}")
        if (!document.extractedReference.isNullOrBlank()) add("Ref: ${document.extractedReference}")
        if (document.extractedAmountCents != null && document.extractedAmountCents > 0L) {
            add("Amount: ${CurrencyUtils.formatKes(document.extractedAmountCents, true)}")
        }
        if (!document.extractedDateText.isNullOrBlank()) add("Date: ${document.extractedDateText}")
    }

    internal fun computePdfImageRect(
        bmpWidth: Int,
        bmpHeight: Int,
        margin: Float,
        topY: Float,
        availW: Float,
        availH: Float
    ): RectF {
        val safeW = bmpWidth.coerceAtLeast(1).toFloat()
        val safeH = bmpHeight.coerceAtLeast(1).toFloat()
        val scale = minOf(availW / safeW, availH / safeH)
        val drawW = safeW * scale
        val drawH = safeH * scale
        val left = margin + (availW - drawW) / 2f
        return RectF(left, topY, left + drawW, topY + drawH)
    }

    suspend fun exportScannedDocumentPdf(
        context: Context,
        document: ScannedDocument
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val exportDir = File(context.cacheDir, "exports").apply { if (!exists()) mkdirs() }
            val safeId = document.id.ifBlank { "scan_${System.currentTimeMillis()}" }
                .replace(Regex("[^a-zA-Z0-9_-]"), "_")
            val outputFile = File(exportDir, "rentflow_document_${safeId}.pdf")
            val pdfDoc = PdfDocument()

            try {
                val pageWidth = 595
                val pageHeight = 842
                val margin = 32f

                val headerPaint = Paint().apply { color = AndroidColor.rgb(15, 30, 54) }
                val accentPaint = Paint().apply { color = AndroidColor.rgb(45, 91, 143) }
                val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = AndroidColor.WHITE
                    textSize = 15f
                    isFakeBoldText = true
                }
                val subHeaderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = AndroidColor.rgb(203, 213, 225)
                    textSize = 10f
                }
                val metaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = AndroidColor.rgb(30, 41, 59)
                    textSize = 10.5f
                }
                val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = AndroidColor.rgb(100, 116, 139)
                    textSize = 9f
                }
                val borderPaint = Paint().apply {
                    color = AndroidColor.rgb(201, 214, 230)
                    style = Paint.Style.STROKE
                    strokeWidth = 1f
                }

                val totalPages = document.pageImageRefs.size.coerceAtLeast(1)
                for (idx in 0 until totalPages) {
                    val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, idx + 1).create()
                    val page = pdfDoc.startPage(pageInfo)
                    val canvas = page.canvas
                    canvas.drawColor(AndroidColor.WHITE)

                    var topY = margin
                    if (idx == 0) {
                        canvas.drawRect(0f, 0f, pageWidth.toFloat(), 74f, headerPaint)
                        canvas.drawRect(0f, 70f, pageWidth.toFloat(), 74f, accentPaint)
                        canvas.drawText(document.title.ifBlank { document.typeEnum().label }.take(64), margin, 30f, titlePaint)
                        val subtitle = buildString {
                            append(document.typeEnum().label)
                            append(" • ")
                            append(document.propertyName)
                            if (document.unitNumber.isNotBlank()) {
                                append(" (Unit ${document.unitNumber})")
                            }
                        }
                        canvas.drawText(subtitle.take(86), margin, 50f, subHeaderPaint)

                        var metaY = 94f
                        val metaParts = buildPdfMetadataParts(document)
                        if (metaParts.isNotEmpty()) {
                            canvas.drawText(metaParts.joinToString("   |   ").take(92), margin, metaY, metaPaint)
                            metaY += 16f
                        }
                        if (!document.notes.isNullOrBlank()) {
                            canvas.drawText("Notes: ${document.notes}".take(92), margin, metaY, metaPaint)
                            metaY += 16f
                        }
                        topY = metaY + 8f
                    } else {
                        canvas.drawRect(0f, 0f, pageWidth.toFloat(), 44f, headerPaint)
                        canvas.drawText(
                            "${document.title.ifBlank { document.typeEnum().label }.take(54)} — Page ${idx + 1}",
                            margin,
                            27f,
                            titlePaint.apply { textSize = 12f }
                        )
                        topY = 60f
                    }

                    val bottomY = pageHeight - 36f
                    val availW = (pageWidth - margin * 2).coerceAtLeast(100f)
                    val availH = (bottomY - topY).coerceAtLeast(100f)

                    val pageRef = document.pageImageRefs.getOrNull(idx)
                    val bmp = pageRef?.let { decodeDataUriToBitmap(it) }
                    if (bmp != null) {
                        try {
                            val destRect = computePdfImageRect(
                                bmpWidth = bmp.width,
                                bmpHeight = bmp.height,
                                margin = margin,
                                topY = topY,
                                availW = availW,
                                availH = availH
                            )
                            canvas.drawBitmap(bmp, Rect(0, 0, bmp.width, bmp.height), destRect, null)
                            canvas.drawRect(destRect, borderPaint)
                        } finally {
                            bmp.recycle()
                        }
                    } else {
                        canvas.drawText("Scanned image unavailable for page ${idx + 1}.", margin, topY + 24f, metaPaint)
                    }

                    val uploadedEpoch = document.createdAt?.toDate()?.time ?: System.currentTimeMillis()
                    canvas.drawText(
                        "RentFlow Document Vault • Uploaded by ${document.uploadedByName.ifBlank { "User" }} on ${CurrencyUtils.formatDate(uploadedEpoch)}",
                        margin,
                        pageHeight - 16f,
                        footerPaint
                    )
                    canvas.drawText(
                        "Page ${idx + 1} of $totalPages",
                        pageWidth - margin - 58f,
                        pageHeight - 16f,
                        footerPaint
                    )
                    pdfDoc.finishPage(page)
                }

                FileOutputStream(outputFile).use { fos ->
                    pdfDoc.writeTo(fos)
                }
            } finally {
                pdfDoc.close()
            }
            outputFile
        }
    }

    private fun applyExifOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun calculateInSampleSize(width: Int, height: Int, maxDim: Int): Int {
        var inSampleSize = 1
        val longest = max(width, height)
        while ((longest / inSampleSize) > maxDim * 2) {
            inSampleSize *= 2
        }
        return inSampleSize.coerceAtLeast(1)
    }

    internal fun scaleDownIfNeeded(bitmap: Bitmap, maxDim: Int): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val longest = max(w, h)
        if (longest <= maxDim) return bitmap
        val ratio = maxDim.toFloat() / longest.toFloat()
        val targetW = (w * ratio).roundToInt().coerceAtLeast(1)
        val targetH = (h * ratio).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
    }
}
