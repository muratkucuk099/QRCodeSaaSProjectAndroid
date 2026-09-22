package murat.com.saasproject.data.repository

import android.graphics.Bitmap
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import murat.com.saasproject.data.cache.FirebaseDataCache
import murat.com.saasproject.data.model.AppPublicConfig
import murat.com.saasproject.data.model.Business
import murat.com.saasproject.data.model.BusinessSubscriptionAccess
import murat.com.saasproject.data.remote.CloudFunctionsApi
import murat.com.saasproject.domain.config.BusinessPaymentConfig
import murat.com.saasproject.domain.config.CloudFunctionsConfig
import murat.com.saasproject.domain.config.FirestorePaths
import murat.com.saasproject.domain.config.StoragePaths
import murat.com.saasproject.domain.validation.InviteCodeGenerator
import java.io.ByteArrayOutputStream
import java.util.Date

/**
 * İşletme dokümanı, abonelik erişimi, logo yükleme, davet kodu ve genel uygulama
 * yapılandırmasını yöneten katman. iOS `FirebaseService`'in ilgili bölümlerinin karşılığı.
 */
class BusinessRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val storage: FirebaseStorage = FirebaseStorage.getInstance(),
    private val cache: FirebaseDataCache = FirebaseDataCache
) {

    private var subscriptionListener: ListenerRegistration? = null

    // region Business document

    suspend fun saveBusiness(business: Business): Result<Unit> = runCatching {
        val toSave = business.ensuringDefaultSubscriptionIfNeeded()
        db.collection(FirestorePaths.BUSINESSES)
            .document(toSave.id)
            .set(toSave.toFirestoreMap())
            .await()
        cache.storeBusiness(toSave)
    }

    /**
     * iOS `fetchBusiness` — cache'i önceler. `forceRefresh` yalnızca kullanıcı pull-to-refresh
     * yaptığında veya yazma sonrası veri tazelenmesi gerektiğinde true olmalı.
     */
    suspend fun fetchBusiness(
        businessId: String,
        forceRefresh: Boolean = false
    ): Result<Business> = runCatching {
        if (!forceRefresh) {
            cache.business(businessId)?.let { return@runCatching it }
        }

        val snapshot = db.collection(FirestorePaths.BUSINESSES).document(businessId).get().await()
        val business = Business.fromSnapshot(snapshot) ?: error("İşletme bulunamadı")
        cache.storeBusiness(business)
        business
    }

    /**
     * Müşteri anasayfasındaki "Tüm İşletmeler" listesi.
     * iOS `fetchActiveBusinesses`: `isActive == true` filtresi + isme göre sıralama.
     */
    suspend fun fetchActiveBusinesses(forceRefresh: Boolean = false): Result<List<Business>> =
        runCatching {
            if (!forceRefresh) {
                cache.activeBusinesses()?.let { return@runCatching it }
            }

            val snapshot = db.collection(FirestorePaths.BUSINESSES)
                .whereEqualTo("isActive", true)
                .get()
                .await()

            val list = snapshot.documents
                .mapNotNull { Business.fromSnapshot(it) }
                .sortedBy { it.name.lowercase() }

            cache.storeActiveBusinesses(list)
            list.forEach { cache.storeBusiness(it) }
            list
        }

    /** Ana admin ekranı — tüm işletmeler (aktiflik filtresi yok). */
    suspend fun fetchAllBusinesses(): Result<List<Business>> = runCatching {
        val snapshot = db.collection(FirestorePaths.BUSINESSES).get().await()
        val list = snapshot.documents
            .mapNotNull { Business.fromSnapshot(it) }
            .sortedBy { it.name.lowercase() }
        list.forEach { cache.storeBusiness(it) }
        list
    }

    // endregion

    // region Subscription access

    /**
     * iOS `ensureBusinessSubscriptionAccess` zincirinin karşılığı:
     *   1. İşletmeyi getir
     *   2. Abonelik alanları eksikse deneme süresini backfill et (`ensuringDefaultSubscriptionIfNeeded`)
     *   3. `isActive` alanını hesaplanan geçerlilikle senkronla
     *   4. Erişim kararını döndür
     *
     * 2. ve 3. adımlar Firestore'a yazma yapabilir; bu yüzden yalnızca gerçekten
     * değişiklik varsa yazılır (gereksiz write engellenir).
     */
    suspend fun ensureSubscriptionAccess(
        businessId: String,
        forceRefresh: Boolean = false
    ): Result<BusinessSubscriptionAccess> = runCatching {
        val business = fetchBusiness(businessId, forceRefresh).getOrThrow()
        val resolved = ensureSubscriptionFields(business)
        val synced = persistActiveFlagIfNeeded(resolved)
        BusinessSubscriptionAccess.from(synced)
    }

    /** Abonelik tarihi hiç yoksa `createdAt + 30 gün` deneme süresi yazılır. */
    private suspend fun ensureSubscriptionFields(business: Business): Business {
        val resolved = business.ensuringDefaultSubscriptionIfNeeded()
        if (resolved.subscriptionExpiresAt == business.subscriptionExpiresAt) return resolved

        db.collection(FirestorePaths.BUSINESSES)
            .document(business.id)
            .set(
                mapOf(
                    "subscriptionExpiresAt" to Timestamp(resolved.subscriptionExpiresAt!!),
                    "isActive" to resolved.isActive
                ),
                SetOptions.merge()
            )
            .await()

        cache.invalidateBusiness(business.id)
        cache.storeBusiness(resolved)
        cache.invalidateActiveBusinesses()
        return resolved
    }

    /**
     * `isActive` alanı hesaplanan abonelik geçerliliğiyle uyuşmuyorsa düzeltir.
     * Süresi dolan işletmeler böylece "Tüm İşletmeler" listesinden de düşer.
     */
    private suspend fun persistActiveFlagIfNeeded(business: Business): Business {
        val shouldBeActive = business.isSubscriptionValid
        if (business.isActive == shouldBeActive) return business

        db.collection(FirestorePaths.BUSINESSES)
            .document(business.id)
            .set(mapOf("isActive" to shouldBeActive), SetOptions.merge())
            .await()

        val updated = business.copy(isActive = shouldBeActive)
        cache.invalidateBusiness(business.id)
        cache.storeBusiness(updated)
        cache.invalidateActiveBusinesses()
        return updated
    }

    /**
     * İşletme dokümanını canlı izler. Ana admin aboneliği iptal ederse kullanıcı
     * çalışırken ödeme ekranına yönlendirilir (iOS `startBusinessSubscriptionListener`).
     */
    fun observeSubscription(businessId: String): Flow<BusinessSubscriptionAccess> = callbackFlow {
        subscriptionListener?.remove()
        subscriptionListener = db.collection(FirestorePaths.BUSINESSES)
            .document(businessId)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                val business = Business.fromSnapshot(snapshot) ?: return@addSnapshotListener

                cache.invalidateBusiness(businessId)
                cache.storeBusiness(business)
                cache.invalidateActiveBusinesses()

                trySend(BusinessSubscriptionAccess.from(business))
            }

        awaitClose { stopObservingSubscription() }
    }

    fun stopObservingSubscription() {
        subscriptionListener?.remove()
        subscriptionListener = null
    }

    /** Ana adminin abonelik uzatma / kısaltma / iptal işlemi. */
    suspend fun updateSubscription(
        businessId: String,
        expiresAt: Date,
        isActive: Boolean
    ): Result<Business> = runCatching {
        db.collection(FirestorePaths.BUSINESSES)
            .document(businessId)
            .set(
                mapOf(
                    "subscriptionExpiresAt" to Timestamp(expiresAt),
                    "isActive" to isActive
                ),
                SetOptions.merge()
            )
            .await()

        cache.invalidateBusiness(businessId)
        cache.invalidateActiveBusinesses()
        fetchBusiness(businessId, forceRefresh = true).getOrThrow()
    }

    // endregion

    // region Profile update

    /** iOS `updateBusinessProfile` — ad, telefon, logo ve puan kazanma kurallarını günceller. */
    suspend fun updateBusinessProfile(
        businessId: String,
        name: String,
        phone: String,
        logoURL: String?,
        pointEarningRules: String?
    ): Result<Unit> = runCatching {
        val data = mutableMapOf<String, Any>(
            "name" to name,
            "phone" to phone,
            // iOS boş kuralı boş string olarak yazar; null yazıp alanı silmiyoruz.
            "pointEarningRules" to pointEarningRules.orEmpty()
        )
        logoURL?.let { data["logoURL"] = it }

        db.collection(FirestorePaths.BUSINESSES)
            .document(businessId)
            .set(data, SetOptions.merge())
            .await()

        cache.invalidateBusiness(businessId)
        cache.invalidateActiveBusinesses()
    }

    /** Storage `businessLogos/{businessId}/logo.jpg`, JPEG %70 (iOS ile aynı). */
    suspend fun uploadBusinessLogo(bitmap: Bitmap, businessId: String): Result<String> =
        runCatching {
            val ref = storage.reference
                .child(StoragePaths.BUSINESS_LOGOS)
                .child(businessId)
                .child(StoragePaths.LOGO_FILE)

            ref.putBytes(
                bitmap.toJpegBytes(StoragePaths.LOGO_JPEG_QUALITY),
                StorageMetadata.Builder().setContentType("image/jpeg").build()
            ).await()

            ref.downloadUrl.await().toString()
        }

    // endregion

    // region Invite codes

    /**
     * Davet kodu oluşturma. Firestore rules `invite_codes` yolunu tamamen kapattığı için
     * yalnızca Cloud Function üzerinden yapılabilir; fonksiyon çağıranın ana admin
     * olduğunu sunucu tarafında doğrular.
     */
    suspend fun createInviteCode(): Result<String> = runCatching {
        val json = CloudFunctionsApi.callAuthenticated(
            functionName = CloudFunctionsConfig.CREATE_INVITE_CODE,
            payload = emptyMap()
        )
        json.optString("code").takeIf { it.isNotEmpty() } ?: error("Davet kodu alınamadı.")
    }

    /**
     * Davet kodu doğrulama. Sunucu kodu transaction içinde kullanılmış olarak işaretler,
     * böylece aynı kod ikinci kez kullanılamaz. Hata mesajları sunucudan gelir
     * ("Davet kodu yanlış.", "Davet kodu zaten kullanılmış." vb.).
     */
    suspend fun validateInviteCode(enteredCode: String): Result<Unit> = runCatching {
        CloudFunctionsApi.callPublic(
            functionName = CloudFunctionsConfig.VALIDATE_INVITE_CODE,
            payload = mapOf("code" to InviteCodeGenerator.normalize(enteredCode))
        )
        Unit
    }

    // endregion

    // region App public config

    /**
     * iOS `fetchAppPublicConfig` — ödeme iletişim bilgisi. Doküman yoksa veya okuma
     * başarısızsa hata döndürmek yerine yerel fallback kullanılır (iOS ile aynı davranış),
     * böylece ödeme ekranı hiçbir zaman boş görünmez.
     */
    suspend fun fetchAppPublicConfig(forceRefresh: Boolean = false): AppPublicConfig {
        if (!forceRefresh) {
            cache.appPublicConfig()?.let { return it }
        }

        val fallback = AppPublicConfig(
            phone = BusinessPaymentConfig.FALLBACK_PHONE,
            price = BusinessPaymentConfig.FALLBACK_PRICE
        )

        return try {
            val snapshot = db.collection(FirestorePaths.APP_CONFIG)
                .document(FirestorePaths.APP_CONFIG_PUBLIC_DOC)
                .get()
                .await()

            val config = AppPublicConfig.fromMap(snapshot.data) ?: fallback
            cache.storeAppPublicConfig(config)
            config
        } catch (_: Exception) {
            fallback
        }
    }

    // endregion

    /** İşletmenin müşteri listesi — `businesses/{id}/users`, puana göre azalan. */
    suspend fun fetchBusinessUsers(businessId: String): Result<List<murat.com.saasproject.data.model.BusinessUser>> =
        runCatching {
            db.collection(FirestorePaths.BUSINESSES)
                .document(businessId)
                .collection(FirestorePaths.SUB_USERS)
                .orderBy("points", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .get()
                .await()
                .documents
                .map { doc ->
                    murat.com.saasproject.data.model.BusinessUser(
                        userId = doc.getString("userId") ?: doc.id,
                        points = (doc.get("points") as? Number)?.toInt() ?: 0,
                        userName = doc.getString("userName").orEmpty()
                    )
                }
        }
}

/** Bitmap'i JPEG byte dizisine çevirir (Storage yüklemeleri için). */
internal fun Bitmap.toJpegBytes(quality: Int): ByteArray = ByteArrayOutputStream().use { stream ->
    compress(Bitmap.CompressFormat.JPEG, quality, stream)
    stream.toByteArray()
}
