package murat.com.saasproject.util

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * QR görseli üretimi.
 *
 * IOS davranışı: `CIFilter.qrCodeGenerator` + 10x ölçekleme.
 * ANDROID karşılığı: ZXing `QRCodeWriter`. Hata düzeltme seviyesi M (iOS varsayılanı ile
 * aynı) ve kenar boşluğu 1 modül tutuldu; böylece iki platformda üretilen QR'lar görsel
 * olarak da benzer ve aynı tarayıcılarla okunabilir oluyor.
 */
object QrGenerator {

    private const val DEFAULT_SIZE_PX = 720

    /**
     * @param content QR içine gömülecek JSON gövdesi
     * @param sizePx kare görselin kenar uzunluğu
     * @return üretilen bitmap, hata durumunda null
     */
    fun generate(
        content: String,
        sizePx: Int = DEFAULT_SIZE_PX,
        foregroundColor: Int = Color.BLACK,
        backgroundColor: Int = Color.WHITE
    ): Bitmap? = runCatching {
        val hints = mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to 1,
            EncodeHintType.CHARACTER_SET to "UTF-8"
        )

        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
        val width = matrix.width
        val height = matrix.height
        val pixels = IntArray(width * height)

        for (y in 0 until height) {
            val offset = y * width
            for (x in 0 until width) {
                pixels[offset + x] = if (matrix.get(x, y)) foregroundColor else backgroundColor
            }
        }

        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, width, 0, 0, width, height)
        }
    }.getOrNull()
}
