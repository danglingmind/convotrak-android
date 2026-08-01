package com.danglingmind.convotrack.ui.common

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * Generates a crisp black-on-white QR [ImageBitmap] for [content] (the Android analogue of
 * iOS QRCodeModal's CIQRCodeGenerator). Cached per content so recomposition is cheap.
 */
@Composable
fun rememberQrBitmap(content: String, sizePx: Int = 512): ImageBitmap? = remember(content, sizePx) {
    if (content.isBlank()) return@remember null
    runCatching { encodeQr(content, sizePx) }.getOrNull()
}

private fun encodeQr(content: String, size: Int): ImageBitmap {
    val hints = mapOf(
        EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.H,
        EncodeHintType.MARGIN to 1,
    )
    val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    for (x in 0 until size) {
        for (y in 0 until size) {
            bmp.setPixel(x, y, if (matrix[x, y]) AndroidColor.BLACK else AndroidColor.WHITE)
        }
    }
    return bmp.asImageBitmap()
}
