package murat.com.saasproject.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import murat.com.saasproject.data.cache.FirebaseDataCache
import murat.com.saasproject.data.model.LoginResult
import murat.com.saasproject.data.model.UserBusiness
import murat.com.saasproject.domain.config.FirestorePaths
import murat.com.saasproject.domain.config.StoragePaths

/** Hesap silme akışında kullanıcıya gösterilecek özel durumlar. */
sealed class AccountDeletionException(message: String) : Exception(message) {
    /** Ana yönetici hesabı uygulama üzerinden silinemez. */
    object MainAdminNotDeletable :
        AccountDeletionException("Ana yönetici hesabı uygulama üzerinden silinemez.")

    /** Firebase son girişin yakın zamanda olmasını istiyor. */
    object RequiresRecentLogin : AccountDeletionException(
        "Güvenlik nedeniyle hesabını silmek için önce çıkış yapıp tekrar giriş yapman gerekiyor."
    )

    object NoSession : AccountDeletionException("Oturum bulunamadı")
}

/**
 * KVKK "unutulma hakkı" gereği hesap ve ilişkili tüm verilerin silinmesi.
 * iOS `FirebaseService.deleteAccount` ve alt fonksiyonlarının karşılığı.
 *
 * Silme sırası önemli: alt veriler önce, ana doküman sonra, Firebase Auth kaydı en son.
 * Böylece yarı silinmiş bir hesapla oturum açılıp erişilemeyen veri kalmaz.
 */
class AccountDeletionRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val storage: FirebaseStorage = FirebaseStorage.getInstance(),
    private val cache: FirebaseDataCache = FirebaseDataCache,
    private val authRepository: AuthRepository = AuthRepository()
) {

    suspend fun deleteAccount(): Result<Unit> = runCatching {
        val uid = auth.currentUser?.uid ?: throw AccountDeletionException.NoSession

        when (authRepository.loginWithUid(uid).getOrThrow()) {
            LoginResult.MAIN_ADMIN -> throw AccountDeletionException.MainAdminNotDeletable
            LoginResult.USER -> deleteEndUserAccount(uid)
            LoginResult.SUB_ADMIN -> deleteBusinessAccount(uid)
        }

        deleteAuthenticatedUser()
    }

    /** Müşteri hesabı: token'lar, bildirimler, işletme üyelikleri, ardından kullanıcı dokümanı. */
    private suspend fun deleteEndUserAccount(uid: String) {
        val userRef = db.collection(FirestorePaths.USERS).document(uid)
        val snapshot = userRef.get().await()
        val businessIds = UserBusiness.listFromRaw(snapshot.get("businesses")).map { it.businessId }

        deleteAllDocuments(userRef.collection(FirestorePaths.SUB_FCM_TOKENS))
        deleteAllDocuments(userRef.collection(FirestorePaths.SUB_NOTIFICATIONS))

        // Müşterinin puan kayıtlarını işletmelerin müşteri listelerinden de kaldır.
        if (businessIds.isNotEmpty()) {
            val batch = db.batch()
            businessIds.forEach { businessId ->
                batch.delete(
                    db.collection(FirestorePaths.BUSINESSES)
                        .document(businessId)
                        .collection(FirestorePaths.SUB_USERS)
                        .document(uid)
                )
            }
            batch.commit().await()
        }

        userRef.delete().await()
        cache.invalidateUserBusinesses(uid)
        cache.invalidateUserNotifications(uid)
    }

    /** İşletme hesabı: ödüller, aktif QR'lar, tüm alt koleksiyonlar, görseller, ardından doküman. */
    private suspend fun deleteBusinessAccount(uid: String) {
        val businessRef = db.collection(FirestorePaths.BUSINESSES).document(uid)
        val snapshot = businessRef.get().await()
        val rewardIds = (snapshot.get("rewards") as? List<*>)?.filterIsInstance<String>().orEmpty()

        if (rewardIds.isNotEmpty()) {
            val batch = db.batch()
            rewardIds.forEach { batch.delete(db.collection(FirestorePaths.REWARDS).document(it)) }
            batch.commit().await()
        }

        deleteActiveQrCodes(uid)

        listOf(
            FirestorePaths.SUB_USERS,
            FirestorePaths.SUB_NOTIFICATIONS,
            FirestorePaths.SUB_POINT_LOGS,
            FirestorePaths.SUB_REWARD_LOGS
        ).forEach { deleteAllDocuments(businessRef.collection(it)) }

        businessRef.delete().await()

        cache.invalidateBusiness(uid)
        cache.invalidateRewards(uid)
        cache.invalidateBusinessNotifications(uid)
        cache.invalidateProfileStats(uid)
        cache.invalidateActiveBusinesses()

        deleteBusinessStorageAssets(uid)
    }

    private suspend fun deleteActiveQrCodes(businessId: String) {
        val documents = db.collection(FirestorePaths.ACTIVE_QR_CODES)
            .whereEqualTo("businessId", businessId)
            .get()
            .await()
            .documents

        if (documents.isEmpty()) return

        val batch = db.batch()
        documents.forEach { batch.delete(it.reference) }
        batch.commit().await()
    }

    /**
     * Koleksiyonu sayfa sayfa siler (batch limiti 500, iOS gibi 100'lük gruplar).
     * Firestore alt koleksiyonları ana doküman silinince otomatik silinmediği için gerekli.
     */
    private suspend fun deleteAllDocuments(
        collection: CollectionReference,
        batchSize: Long = DELETE_BATCH_SIZE
    ) {
        while (true) {
            val documents = collection.limit(batchSize).get().await().documents
            if (documents.isEmpty()) return

            val batch = db.batch()
            documents.forEach { batch.delete(it.reference) }
            batch.commit().await()

            if (documents.size < batchSize) return
        }
    }

    /** Görsel silme başarısız olsa bile hesap silme işlemi geçersiz sayılmaz. */
    private suspend fun deleteBusinessStorageAssets(businessId: String) {
        runCatching {
            storage.reference
                .child(StoragePaths.BUSINESS_LOGOS)
                .child(businessId)
                .child(StoragePaths.LOGO_FILE)
                .delete()
                .await()
        }

        runCatching {
            storage.reference
                .child(StoragePaths.REWARDS)
                .child(businessId)
                .listAll()
                .await()
                .items
                .forEach { runCatching { it.delete().await() } }
        }

        // iOS `rewardImages/{businessId}` — yeni yükleme bu yolu kullanmaz; yalnızca temizlik.
        runCatching {
            storage.reference
                .child(StoragePaths.REWARD_IMAGES)
                .child(businessId)
                .listAll()
                .await()
                .items
                .forEach { runCatching { it.delete().await() } }
        }
    }

    private suspend fun deleteAuthenticatedUser() {
        val user = auth.currentUser ?: return
        try {
            user.delete().await()
        } catch (_: FirebaseAuthRecentLoginRequiredException) {
            throw AccountDeletionException.RequiresRecentLogin
        }
    }

    private companion object {
        const val DELETE_BATCH_SIZE = 100L
    }
}
