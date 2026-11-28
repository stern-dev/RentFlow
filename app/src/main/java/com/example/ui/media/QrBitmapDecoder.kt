package com.example.ui.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.camera.core.ImageProxy
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.LuminanceSource
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Decodes QR payloads offline using ZXing Core from CameraX YUV_420_888 frames,
 * in-memory Bitmaps, and Android Photo Picker URIs (zero storage permissions).
 */
object QrBitmapDecoder {

    private val decodeHints: Map<DecodeHintType, Any> = mapOf(
        DecodeHintType.TRY_HARDER to true,
        DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
        DecodeHintType.CHARACTER_SET to "UTF-8"
    )

    fun decodeFromImageProxy(imageProxy: ImageProxy): String? {
        val plane = imageProxy.planes.firstOrNull() ?: return null
        val width = imageProxy.width
        val height = imageProxy.height
        if (width <= 0 || height <= 0) return null

        val yBytes = extractCompactYPlane(
            buffer = plane.buffer,
            width = width,
            height = height,
            rowStride = plane.rowStride,
            pixelStride = plane.pixelStride
        )

        val source = PlanarYUVLuminanceSource(
            yBytes,
            width,
            height,
            0,
            0,
            width,
            height,
            false
        )
        return decodeLuminanceSource(source)
    }

    fun decodeFromBitmap(bitmap: Bitmap): String? {
        val safeBitmap = downsampleIfNeeded(bitmap, maxDimPx = 1440)
        val width = safeBitmap.width
        val height = safeBitmap.height
        if (width <= 0 || height <= 0) return null

        val pixels = IntArray(width * height)
        safeBitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val source = RGBLuminanceSource(width, height, pixels)
        return decodeLuminanceSource(source)
    }

    suspend fun decodeFromUri(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, bounds)
        }
        val maxDim = maxOf(bounds.outWidth, bounds.outHeight)
        var sampleSize = 1
        while (maxDim / sampleSize > 1600) {
            sampleSize *= 2
        }
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, opts)
        } ?: return@withContext null

        val result = decodeFromBitmap(decoded)
        if (!decoded.isRecycled) {
            decoded.recycle()
        }
        result
    }

    private fun decodeLuminanceSource(source: LuminanceSource): String? {
        val reader = QRCodeReader()
        val normalResult = runCatching {
            reader.decode(BinaryBitmap(HybridBinarizer(source)), decodeHints).text
        }.getOrNull()
        if (!normalResult.isNullOrBlank()) return normalResult

        reader.reset()
        return runCatching {
            reader.decode(BinaryBitmap(HybridBinarizer(source.invert())), decodeHints).text
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    private fun extractCompactYPlane(
        buffer: java.nio.ByteBuffer,
        width: Int,
        height: Int,
        rowStride: Int,
        pixelStride: Int
    ): ByteArray {
        val dup = buffer.duplicate()
        dup.rewind()
        if (pixelStride == 1 && rowStride == width && dup.remaining() >= width * height) {
            val bytes = ByteArray(width * height)
            dup.get(bytes, 0, bytes.size)
            return bytes
        }
        val compact = ByteArray(width * height)
        val rowBuffer = ByteArray(rowStride.coerceAtLeast(width))
        for (row in 0 until height) {
            val remaining = dup.remaining()
            if (remaining <= 0) break
            val toRead = minOf(rowStride, remaining)
            dup.get(rowBuffer, 0, toRead)
            if (pixelStride == 1) {
                val copyLen = minOf(width, toRead)
                System.arraycopy(rowBuffer, 0, compact, row * width, copyLen)
            } else {
                var col = 0
                var srcIdx = 0
                while (col < width && srcIdx < toRead) {
                    compact[row * width + col] = rowBuffer[srcIdx]
                    col++
                    srcIdx += pixelStride
                }
            }
        }
        return compact
    }

    private fun downsampleIfNeeded(bitmap: Bitmap, maxDimPx: Int): Bitmap {
        val maxSide = maxOf(bitmap.width, bitmap.height)
        if (maxSide <= maxDimPx) return bitmap
        val scale = maxDimPx.toFloat() / maxSide.toFloat()
        val targetW = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val targetH = (bitmap.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
    }
}
