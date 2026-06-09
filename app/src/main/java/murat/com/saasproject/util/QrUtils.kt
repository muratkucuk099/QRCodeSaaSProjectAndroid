package murat.com.saasproject.util

import android.graphics.Bitmap
import android.graphics.Color
import com.google.gson.Gson
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import murat.com.saasproject.data.model.QRPayload

object QrUtils {
    private val gson = Gson()

    fun encodePayload(payload: QRPayload): String = gson.toJson(payload)

    fun parsePayload(json: String): QRPayload? {
        return runCatching { gson.fromJson(json, QRPayload::class.java) }.getOrNull()
    }

    fun generateQrBitmap(content: String, size: Int = 512): Bitmap? {
        return runCatching {
            val hints = mapOf(EncodeHintType.MARGIN to 1)
            val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)
            val width = matrix.width
            val height = matrix.height
            val pixels = IntArray(width * height)
            for (y in 0 until height) {
                for (x in 0 until width) {
                    pixels[y * width + x] = if (matrix[x, y]) Color.BLACK else Color.WHITE
                }
            }
            Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
        }.getOrNull()
    }
}
