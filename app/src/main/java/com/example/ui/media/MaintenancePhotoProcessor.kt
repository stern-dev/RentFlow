package com.example.ui.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Compresses user-selected gallery photos into compact JPEG data URIs
 * so maintenance ticket attachments are viewable across Tenant, Landlord,
 * Manager, and Maintenance Staff devices directly from Firestore without
 * requiring external Storage permissions or buckets.
 */
object MaintenancePhotoProcessor {
    const val MAX_PHOTOS = 4
    private const val MAX_DIMENSION_PX = 720
    private const val TARGET_MAX_RAW_BYTES = 95_000
    const val MAX_TOTAL_IMAGE_REFS_CHARS = 650_000
    private const val JPEG_DATA_PREFIX = "data:image/jpeg;base64,"
    private const val GENERIC_DATA_PREFIX = "data:image/"

    suspend fun compressUrisToDataUris(
        context: Context,
        uris: List<Uri>,
        maxCount: Int = MAX_PHOTOS
    ): List<String> = withContext(Dispatchers.IO) {
        uris.take(maxCount.coerceIn(0, MAX_PHOTOS))
            .mapNotNull { uri -> runCatching { compressUriToDataUri(context, uri) }.getOrNull() }
    }

    fun compressUriToDataUri(context: Context, uri: Uri): String? {
        val resolver = context.contentResolver
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, boundsOpts)
        } ?: return null

        val srcWidth = boundsOpts.outWidth
        val srcHeight = boundsOpts.outHeight
        if (srcWidth <= 0 || srcHeight <= 0) return null

        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(srcWidth, srcHeight, MAX_DIMENSION_PX)
        }
        val decoded = resolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOpts)
        } ?: return null

        val scaled = scaleDownIfNeeded(decoded, MAX_DIMENSION_PX)
        try {
            val jpegBytes = compressToTargetSize(scaled)
            return encodeJpegBytesToDataUri(jpegBytes)
        } finally {
            if (scaled !== decoded) {
                scaled.recycle()
            }
            decoded.recycle()
        }
    }

    fun encodeJpegBytesToDataUri(bytes: ByteArray): String {
        val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
        return "$JPEG_DATA_PREFIX$b64"
    }

    fun isDataImageUri(ref: String): Boolean =
        ref.trim().startsWith(GENERIC_DATA_PREFIX, ignoreCase = true) && ref.contains(";base64,", ignoreCase = true)

    fun decodeDataUriBytes(ref: String): ByteArray? {
        val trimmed = ref.trim()
        if (!isDataImageUri(trimmed)) return null
        val commaIndex = trimmed.indexOf(";base64,", ignoreCase = true)
        if (commaIndex < 0) return null
        val payload = trimmed.substring(commaIndex + ";base64,".length)
        if (payload.isBlank()) return null
        return runCatching { Base64.decode(payload, Base64.DEFAULT) }.getOrNull()
    }

    fun decodeDataUriToImageBitmap(ref: String): ImageBitmap? {
        val bytes = decodeDataUriBytes(ref) ?: return null
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        return bitmap.asImageBitmap()
    }

    fun estimateDataUriKb(ref: String): Int? {
        val bytes = decodeDataUriBytes(ref) ?: return null
        return ((bytes.size + 1023) / 1024).coerceAtLeast(1)
    }

    fun validateImageRefsPayload(refs: List<String>): Result<List<String>> {
        val clean = refs.map { it.trim() }.filter { it.isNotBlank() }
        if (clean.size > MAX_PHOTOS) {
            return Result.failure(IllegalArgumentException("You can attach at most $MAX_PHOTOS photos per request."))
        }
        val totalChars = clean.sumOf { it.length }
        if (totalChars > MAX_TOTAL_IMAGE_REFS_CHARS) {
            return Result.failure(
                IllegalArgumentException("Attached photos exceed Firestore ticket size limit. Remove a photo or choose smaller images.")
            )
        }
        return Result.success(clean)
    }

    private fun calculateInSampleSize(width: Int, height: Int, maxDim: Int): Int {
        var inSampleSize = 1
        val longest = max(width, height)
        while ((longest / inSampleSize) > maxDim * 2) {
            inSampleSize *= 2
        }
        return inSampleSize.coerceAtLeast(1)
    }

    private fun scaleDownIfNeeded(bitmap: Bitmap, maxDim: Int): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val longest = max(w, h)
        if (longest <= maxDim) return bitmap
        val ratio = maxDim.toFloat() / longest.toFloat()
        val targetW = (w * ratio).roundToInt().coerceAtLeast(1)
        val targetH = (h * ratio).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
    }

    private fun compressToTargetSize(bitmap: Bitmap): ByteArray {
        val qualities = intArrayOf(74, 62, 50, 38)
        var latest = ByteArray(0)
        for (q in qualities) {
            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, q, out)
            latest = out.toByteArray()
            if (latest.size <= TARGET_MAX_RAW_BYTES) return latest
        }
        return latest
    }
}
