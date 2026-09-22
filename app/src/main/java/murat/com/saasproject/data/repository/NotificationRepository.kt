package murat.com.saasproject.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import murat.com.saasproject.data.cache.FirebaseDataCache
import murat.com.saasproject.data.model.BusinessNotification
import murat.com.saasproject.data.model.PushNotificationDraft
import murat.com.saasproject.data.model.PushNotificationSendResult
import murat.com.saasproject.data.model.UserNotification
import murat.com.saasproject.data.remote.CloudFunctionsApi
import murat.com.saasproject.domain.config.CloudFunctionsConfig
import murat.com.saasproject.domain.config.FirestorePaths

/**
 * Bildirim geçmişi ve FCM token yönetimi.
 * iOS `FirebaseService`'in "Push Notifications" bölümünün karşılığı.
 *
 * Bildirim gönderimi tamamen sunucu tarafında (`sendBusinessPush` Cloud Function) yapılır;
 * istemci yalnızca başlık/mesaj gönderir. Böylece bir işletme başka bir işletmenin
 * müşterilerine bildirim gönderemez.
 */
class NotificationRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val cache: FirebaseDataCache = FirebaseDataCache
) {

    /** İşletmenin gönderdiği bildirimlerin geçmişi. */
    suspend fun fetchBusinessNotifications(
        businessId: String,
        forceRefresh: Boolean = false
    ): Result<List<BusinessNotification>> = runCatching {
        if (!forceRefresh) {
            cache.businessNotifications(businessId)?.let { return@runCatching it }
        }

        val snapshot = db.collection(FirestorePaths.BUSINESSES)
            .document(businessId)
            .collection(FirestorePaths.SUB_NOTIFICATIONS)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .get()
            .await()

        val list = snapshot.documents.mapNotNull { BusinessNotification.fromSnapshot(it) }
        cache.storeBusinessNotifications(list, businessId)
        list
    }

    suspend fun deleteBusinessNotification(
        businessId: String,
        notificationId: String
    ): Result<Unit> = runCatching {
        db.collection(FirestorePaths.BUSINESSES)
            .document(businessId)
            .collection(FirestorePaths.SUB_NOTIFICATIONS)
            .document(notificationId)
            .delete()
            .await()

        cache.invalidateBusinessNotifications(businessId)
    }

    /**
     * Müşterinin aldığı bildirimler. Cloud Function bildirimi gönderirken bu dokümanları
     * yazar, dolayısıyla push izni olmasa bile geçmiş görüntülenebilir.
     *
     * @param fallbackBusinessName `businessName` alanı boş gelirse kullanılacak metin
     */
    suspend fun fetchUserNotifications(
        userId: String,
        fallbackBusinessName: String,
        forceRefresh: Boolean = false
    ): Result<List<UserNotification>> = runCatching {
        if (!forceRefresh) {
            cache.userNotifications(userId)?.let { return@runCatching it }
        }

        val snapshot = db.collection(FirestorePaths.USERS)
            .document(userId)
            .collection(FirestorePaths.SUB_NOTIFICATIONS)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .get()
            .await()

        val list = snapshot.documents.mapNotNull {
            UserNotification.fromSnapshot(it, fallbackBusinessName)
        }
        cache.storeUserNotifications(list, userId)
        list
    }

    /** İşletmenin tüm müşterilerine push bildirimi gönderir (sunucu taraflı). */
    suspend fun sendBusinessNotification(
        title: String,
        body: String
    ): Result<PushNotificationSendResult> = runCatching {
        val businessId = auth.currentUser?.uid ?: error("Oturum bulunamadı")

        val json = CloudFunctionsApi.callAuthenticated(
            functionName = CloudFunctionsConfig.SEND_BUSINESS_PUSH,
            payload = mapOf("title" to title, "body" to body)
        )

        cache.invalidateBusinessNotifications(businessId)

        PushNotificationSendResult(
            draft = PushNotificationDraft(businessId, title, body),
            targetUserCount = json.optInt("targetUserCount", 0),
            sentCount = json.optInt("sentCount", 0),
            failedCount = json.optInt("failedCount", 0),
            message = json.optString("message").takeIf { it.isNotEmpty() }
                ?: "Bildirim gönderildi."
        )
    }

    /**
     * FCM token'ını kaydeder. iOS ile aynı iki aşamalı akış:
     *   1. `users/{uid}/fcmTokens/{deviceId}` dokümanına yaz (rules sahibine izin verir)
     *   2. `syncFCMToken` fonksiyonunu çağır — aynı token'ı başka kullanıcılardan temizler
     *
     * 2. adım başarısız olursa token yine de kayıtlıdır, bu yüzden hata yutulur.
     */
    suspend fun saveFcmToken(token: String, deviceId: String): Result<Unit> = runCatching {
        val userId = auth.currentUser?.uid ?: error("Oturum bulunamadı")

        db.collection(FirestorePaths.USERS)
            .document(userId)
            .collection(FirestorePaths.SUB_FCM_TOKENS)
            .document(deviceId)
            .set(
                mapOf(
                    "token" to token,
                    "platform" to PLATFORM_ANDROID,
                    "userId" to userId,
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            )
            .await()

        runCatching {
            CloudFunctionsApi.callAuthenticated(
                functionName = CloudFunctionsConfig.SYNC_FCM_TOKEN,
                payload = mapOf(
                    "token" to token,
                    "deviceId" to deviceId,
                    "platform" to PLATFORM_ANDROID
                )
            )
        }
        Unit
    }

    /** Çıkış öncesi token'ı siler, böylece cihaz eski kullanıcının bildirimlerini almaz. */
    suspend fun removeFcmToken(deviceId: String): Result<Unit> = runCatching {
        val userId = auth.currentUser?.uid ?: return@runCatching
        db.collection(FirestorePaths.USERS)
            .document(userId)
            .collection(FirestorePaths.SUB_FCM_TOKENS)
            .document(deviceId)
            .delete()
            .await()
    }

    companion object {
        /** iOS `"ios"` yazar; platform ayrımı için Android `"android"` yazıyor. */
        const val PLATFORM_ANDROID = "android"
    }
}
