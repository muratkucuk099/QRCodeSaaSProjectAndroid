package murat.com.saasproject.data.model

import org.json.JSONObject

/**
 * iOS `QRPayload.swift` karşılığı. QR kodunun içine gömülen JSON gövdesi.
 *
 * KRİTİK: Bu format iOS ile birebir aynı kalmak zorunda — Android'de üretilen QR iOS'ta,
 * iOS'ta üretilen QR Android'de okunabilmeli.
 *
 * Swift `JSONEncoder` optional alanları `encodeIfPresent` ile yazar, yani null alanlar
 * JSON'a hiç eklenmez. Aynı davranışı taklit ediyoruz.
 *
 * Puan verme:  {"qrCode":"<uuid>","businessId":"<uid>","points":10}
 * Ödül verme:  {"qrCode":"<uuid>","businessId":"<uid>","points":-50,
 *               "rewardId":"<id>","rewardName":"<ad>"}
 *
 * `points` mutlaka JSON integer olarak yazılmalı; Swift `Int` olarak decode ettiği için
 * ondalıklı yazım (`10.0`) iOS tarafında decode hatasına yol açar. `JSONObject.put(String, Int)`
 * bunu garanti eder.
 */
data class QrPayload(
    val qrCode: String,
    val businessId: String,
    val points: Int,
    val userId: String? = null,
    val rewardId: String? = null,
    val rewardName: String? = null
) {

    /** Bu payload bir ödül kullanımı mı (negatif puan + ödül bilgisi). */
    val isRewardRedemption: Boolean
        get() = rewardId != null && rewardName != null

    fun toJson(): String = JSONObject().apply {
        put(KEY_QR_CODE, qrCode)
        put(KEY_BUSINESS_ID, businessId)
        put(KEY_POINTS, points)
        userId?.let { put(KEY_USER_ID, it) }
        rewardId?.let { put(KEY_REWARD_ID, it) }
        rewardName?.let { put(KEY_REWARD_NAME, it) }
    }.toString()

    companion object {
        private const val KEY_QR_CODE = "qrCode"
        private const val KEY_BUSINESS_ID = "businessId"
        private const val KEY_POINTS = "points"
        private const val KEY_USER_ID = "userId"
        private const val KEY_REWARD_ID = "rewardId"
        private const val KEY_REWARD_NAME = "rewardName"

        /**
         * Taranan QR içeriğini parse eder. Geçersiz JSON veya eksik zorunlu alan -> null.
         * iOS `JSONDecoder` davranışıyla aynı: zorunlu alanlar qrCode, businessId, points.
         */
        fun fromJson(raw: String): QrPayload? {
            val json = try {
                JSONObject(raw)
            } catch (_: Exception) {
                return null
            }

            val qrCode = json.optString(KEY_QR_CODE).takeIf { it.isNotEmpty() } ?: return null
            val businessId = json.optString(KEY_BUSINESS_ID).takeIf { it.isNotEmpty() } ?: return null
            if (!json.has(KEY_POINTS)) return null
            val points = json.optInt(KEY_POINTS, Int.MIN_VALUE)
            if (points == Int.MIN_VALUE) return null

            return QrPayload(
                qrCode = qrCode,
                businessId = businessId,
                points = points,
                userId = json.optStringOrNull(KEY_USER_ID),
                rewardId = json.optStringOrNull(KEY_REWARD_ID),
                rewardName = json.optStringOrNull(KEY_REWARD_NAME)
            )
        }

        private fun JSONObject.optStringOrNull(key: String): String? =
            if (isNull(key)) null else optString(key).takeIf { it.isNotEmpty() }
    }
}

/**
 * iOS `QRError.swift` — Firestore transaction'ından fırlatılan NSError kodlarının karşılığı.
 * Kodlar iOS ile aynı tutuldu ki hata eşleme mantığı bire bir izlenebilsin.
 */
enum class QrError(val code: Int) {
    /** iOS -1: `active_qr_codes` dokümanı yok. */
    INVALID(-1),

    /** iOS -2: businessId veya points uyuşmuyor. */
    MISMATCH(-2),

    /** iOS -3: `isUsed == true`. */
    ALREADY_USED(-3),

    /** iOS -4: `expiresAt` geçmiş. */
    EXPIRED(-4)
}

/** iOS `PointsError.swift`. */
enum class PointsError {
    /** Yeni toplam 0'ın altına düşüyor. */
    NEGATIVE_POINTS,
    USER_NOT_FOUND
}

/**
 * QR okuma/puan işleme sırasında oluşan alan hataları.
 * iOS'ta `QRError` + `PointsError` + generic Error üçlüsünün `QRViewError`'a eşlenmesine karşılık gelir.
 */
sealed class RedeemException(message: String) : Exception(message) {
    class Qr(val error: QrError, message: String) : RedeemException(message)
    class Points(val error: PointsError, message: String) : RedeemException(message)
}
