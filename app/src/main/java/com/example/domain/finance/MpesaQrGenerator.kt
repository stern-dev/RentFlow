package com.example.domain.finance

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * Immutable QR matrix representation generated offline from an M-Pesa URI payload.
 * [size] is the side length in QR modules; [darkModules] stores row-major module states.
 */
data class QrMatrixData(
    val size: Int,
    val darkModules: BooleanArray
) {
    fun isDarkAt(row: Int, col: Int): Boolean {
        if (row !in 0 until size || col !in 0 until size) return false
        return darkModules[row * size + col]
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is QrMatrixData) return false
        return size == other.size && darkModules.contentEquals(other.darkModules)
    }

    override fun hashCode(): Int {
        var result = size
        result = 31 * result + darkModules.contentHashCode()
        return result
    }
}

/**
 * Generates deterministic, error-corrected QR matrices for M-Pesa Paybill and Till URIs
 * using ZXing's pure-JVM QRCodeWriter.
 */
object MpesaQrGenerator {

    fun encodeQrMatrix(payload: String): QrMatrixData? {
        val cleanPayload = payload.trim()
        if (cleanPayload.isEmpty()) return null
        return try {
            val hints = mapOf(
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
                EncodeHintType.CHARACTER_SET to "UTF-8",
                EncodeHintType.MARGIN to 0
            )
            val matrix = QRCodeWriter().encode(cleanPayload, BarcodeFormat.QR_CODE, 0, 0, hints)
            val side = matrix.width.coerceAtLeast(matrix.height)
            val modules = BooleanArray(side * side)
            for (y in 0 until matrix.height) {
                for (x in 0 until matrix.width) {
                    modules[y * side + x] = matrix.get(x, y)
                }
            }
            QrMatrixData(size = side, darkModules = modules)
        } catch (_: Exception) {
            null
        }
    }
}
