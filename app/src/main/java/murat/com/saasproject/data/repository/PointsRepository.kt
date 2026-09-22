package murat.com.saasproject.data.repository

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import murat.com.saasproject.data.cache.FirebaseDataCache
import murat.com.saasproject.data.model.PointsError
import murat.com.saasproject.data.model.QrError
import murat.com.saasproject.data.model.QrPayload
import murat.com.saasproject.data.model.RedeemException
import murat.com.saasproject.data.model.RewardLog
import murat.com.saasproject.data.model.UserBusiness
import murat.com.saasproject.domain.config.FirestorePaths
import murat.com.saasproject.domain.config.QrConfig
import java.util.Date
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * QR üretimi ve puan/ödül işleme. iOS `FirebaseService`'in "QR Code Flow" bölümünün karşılığı.
 *
 * Puan hareketleri tek bir Firestore transaction'ında yürütülür; böylece QR'ın tek kullanımlık
 * olması, kullanıcı puanının güncellenmesi ve log yazımı ya tümüyle olur ya da hiç olmaz.
 */
class PointsRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val cache: FirebaseDataCache = FirebaseDataCache
) {

    /**
     * İşletmenin ürettiği QR'ı `active_qr_codes/{qrCode}` altına yazar.
     *
     * Doküman kimliği QR'ın kendi UUID'sidir — iOS ile aynı. Firestore rules bu dokümanın
     * yalnızca `businessId == auth.uid` olduğunda oluşturulmasına izin verir.
     */
    suspend fun createActiveQrCode(
        businessId: String,
        qrCode: String,
        points: Int,
        rewardId: String? = null,
        rewardName: String? = null
    ): Result<Unit> = runCatching {
        val now = Date()
        val expiresAt = Date(now.time + TimeUnit.MINUTES.toMillis(QrConfig.VALIDITY_MINUTES))

        val data = mutableMapOf<String, Any>(
            "qrCode" to qrCode,
            "businessId" to businessId,
            "points" to points,
            "generatedAt" to Timestamp(now),
            "expiresAt" to Timestamp(expiresAt),
            // Rules `isUsed == false` şartına dayandığı için bu alan mutlaka yazılmalı.
            "isUsed" to false
        )
        rewardId?.let { data["rewardId"] = it }
        rewardName?.let { data["rewardName"] = it }

        db.collection(FirestorePaths.ACTIVE_QR_CODES)
            .document(qrCode)
            .set(data)
            .await()
    }

    /**
     * Müşterinin taradığı QR'ı kullanır ve puanı işler. iOS `redeemQRCode` ile aynı doğrulama
     * sırası ve aynı hata türleri:
     *
     *   1. QR dokümanı yok            -> QrError.INVALID
     *   2. Kullanıcı dokümanı yok     -> PointsError.USER_NOT_FOUND
     *   3. businessId/points uyuşmaz  -> QrError.MISMATCH
     *   4. isUsed == true             -> QrError.ALREADY_USED
     *   5. expiresAt geçmiş           -> QrError.EXPIRED
     *   6. yeni toplam < 0            -> PointsError.NEGATIVE_POINTS
     *
     * 3. adım kritik güvenlik kontrolü: istemciden gelen payload'a güvenilmez, puan değeri
     * Firestore'daki doküman ile karşılaştırılır. Böylece kullanıcı QR içeriğini değiştirip
     * kendine fazla puan yazamaz.
     *
     * @return işlem sonrası kullanıcının bu işletmedeki yeni toplam puanı
     */
    suspend fun redeemQrCode(
        payload: QrPayload,
        userId: String,
        rewardLog: RewardLog?
    ): Result<Int> = runCatching {
        val qrRef = db.collection(FirestorePaths.ACTIVE_QR_CODES).document(payload.qrCode)
        val userRef = db.collection(FirestorePaths.USERS).document(userId)
        val businessUserRef = db.collection(FirestorePaths.BUSINESSES)
            .document(payload.businessId)
            .collection(FirestorePaths.SUB_USERS)
            .document(userId)

        val logId = UUID.randomUUID().toString()
        val logRef = db.collection(FirestorePaths.BUSINESSES)
            .document(payload.businessId)
            .collection(FirestorePaths.SUB_POINT_LOGS)
            .document(logId)
        val rewardLogRef = db.collection(FirestorePaths.BUSINESSES)
            .document(payload.businessId)
            .collection(FirestorePaths.SUB_REWARD_LOGS)
            .document(logId)

        val newPoints = db.runTransaction { transaction ->
            // Firestore Android SDK: tüm okumalar yazmalardan önce yapılmalı.
            val qrSnapshot = transaction.get(qrRef)
            val userSnapshot = transaction.get(userRef)

            if (!qrSnapshot.exists()) {
                throw RedeemException.Qr(QrError.INVALID, "Geçersiz QR kod")
            }
            if (!userSnapshot.exists()) {
                throw RedeemException.Points(PointsError.USER_NOT_FOUND, "Kullanıcı bulunamadı")
            }

            val storedBusinessId = qrSnapshot.getString("businessId").orEmpty()
            val storedPoints = (qrSnapshot.get("points") as? Number)?.toInt() ?: 0
            val isUsed = qrSnapshot.getBoolean("isUsed") ?: false
            val expiresAt = qrSnapshot.getTimestamp("expiresAt")

            if (storedBusinessId != payload.businessId || storedPoints != payload.points) {
                throw RedeemException.Qr(QrError.MISMATCH, "QR kod bilgileri eşleşmiyor")
            }
            if (isUsed) {
                throw RedeemException.Qr(QrError.ALREADY_USED, "Bu QR kod daha önce kullanılmış")
            }
            if (expiresAt != null && expiresAt.toDate().before(Date())) {
                throw RedeemException.Qr(QrError.EXPIRED, "QR kodunun süresi dolmuş")
            }

            val businesses = UserBusiness.listFromRaw(userSnapshot.get("businesses")).toMutableList()
            val index = businesses.indexOfFirst { it.businessId == payload.businessId }

            val updatedTotal: Int
            if (index >= 0) {
                updatedTotal = businesses[index].points + payload.points
                if (updatedTotal < 0) {
                    throw RedeemException.Points(
                        PointsError.NEGATIVE_POINTS,
                        "Puan yetersiz veya 0'ın altına düşemez"
                    )
                }
                businesses[index] = businesses[index].copy(points = updatedTotal)
            } else {
                if (payload.points < 0) {
                    throw RedeemException.Points(
                        PointsError.NEGATIVE_POINTS,
                        "Puan yetersiz veya 0'ın altına düşemez"
                    )
                }
                updatedTotal = payload.points
                businesses.add(UserBusiness(payload.businessId, updatedTotal))
            }

            // QR'ı kullanılmış işaretle — rules yalnızca false -> true geçişine ve
            // usedBy == auth.uid olmasına izin verir.
            transaction.update(
                qrRef,
                mapOf(
                    "isUsed" to true,
                    "usedBy" to userId,
                    "usedAt" to Timestamp(Date())
                )
            )

            transaction.update(userRef, "businesses", businesses.map { it.toMap() })

            val userName = userSnapshot.getString("name").orEmpty()
            transaction.set(
                businessUserRef,
                mapOf(
                    "userId" to userId,
                    "points" to updatedTotal,
                    "userName" to userName
                ),
                SetOptions.merge()
            )

            val logData = mutableMapOf<String, Any>(
                "id" to logId,
                "userId" to userId,
                "points" to payload.points,
                "qrCode" to payload.qrCode,
                "createdAt" to Timestamp(Date())
            )
            // Ödül kullanımıysa point_log'a da rewardId eklenir (istatistiklerde kullanılıyor).
            rewardLog?.let { logData["rewardId"] = it.rewardId }
            transaction.set(logRef, logData)

            // Aynı logId ile reward_logs'a yazılır; iOS ile aynı eşleştirme.
            rewardLog?.let { transaction.set(rewardLogRef, it.copy(id = logId).toFirestoreMap()) }

            updatedTotal
        }.await()

        cache.invalidateProfileStats(payload.businessId)
        cache.invalidateUserBusinesses(userId)
        newPoints
    }
}
