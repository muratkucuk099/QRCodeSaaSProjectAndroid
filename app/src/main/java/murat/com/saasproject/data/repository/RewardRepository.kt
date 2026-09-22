package murat.com.saasproject.data.repository

import android.graphics.Bitmap
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import kotlinx.coroutines.tasks.await
import murat.com.saasproject.data.cache.FirebaseDataCache
import murat.com.saasproject.data.model.Reward
import murat.com.saasproject.domain.config.FirestorePaths
import murat.com.saasproject.domain.config.StoragePaths

/**
 * Ödül CRUD işlemleri. iOS `FirebaseService`'in ödül bölümünün karşılığı.
 *
 * Ödüller iki yerde tutulur ve tutarlı kalmaları gerekir:
 *   - `rewards/{rewardId}` dokümanı (ödülün kendisi)
 *   - `businesses/{businessId}.rewards` dizisi (işletmenin ödül kimlikleri)
 * Bu yüzden oluşturma/silme işlemleri batch ile atomik yapılır.
 */
class RewardRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val storage: FirebaseStorage = FirebaseStorage.getInstance(),
    private val cache: FirebaseDataCache = FirebaseDataCache
) {

    /** İşletmenin ödülleri — `rewards` koleksiyonunda `businessId` filtresiyle. */
    suspend fun fetchRewards(
        businessId: String,
        forceRefresh: Boolean = false
    ): Result<List<Reward>> = runCatching {
        if (!forceRefresh) {
            cache.rewards(businessId)?.let { return@runCatching it }
        }

        val snapshot = db.collection(FirestorePaths.REWARDS)
            .whereEqualTo("businessId", businessId)
            .get()
            .await()

        val rewards = snapshot.documents
            .mapNotNull { Reward.fromSnapshot(it, businessId) }
            .sortedBy { it.requiredPoints }

        cache.storeRewards(rewards, businessId)
        rewards
    }

    suspend fun createReward(reward: Reward): Result<Unit> = runCatching {
        val batch = db.batch()
        batch.set(
            db.collection(FirestorePaths.REWARDS).document(reward.rewardId),
            reward.toFirestoreMap()
        )
        batch.update(
            db.collection(FirestorePaths.BUSINESSES).document(reward.businessId),
            "rewards",
            FieldValue.arrayUnion(reward.rewardId)
        )
        batch.commit().await()

        cache.invalidateRewards(reward.businessId)
        cache.invalidateBusiness(reward.businessId)
    }

    suspend fun deleteReward(rewardId: String, businessId: String): Result<Unit> = runCatching {
        val batch = db.batch()
        batch.delete(db.collection(FirestorePaths.REWARDS).document(rewardId))
        batch.update(
            db.collection(FirestorePaths.BUSINESSES).document(businessId),
            "rewards",
            FieldValue.arrayRemove(rewardId)
        )
        batch.commit().await()

        cache.invalidateRewards(businessId)
        cache.invalidateBusiness(businessId)
        cache.invalidateProfileStats(businessId)

        // Görseli de temizle; başarısız olursa ödül silme işlemini geçersiz saymıyoruz.
        runCatching {
            storage.reference
                .child(StoragePaths.REWARDS)
                .child(businessId)
                .child("$rewardId.jpg")
                .delete()
                .await()
        }
        Unit
    }

    /** Storage `rewards/{businessId}/{rewardId}.jpg`, JPEG %80 (iOS ile aynı). */
    suspend fun uploadRewardImage(
        bitmap: Bitmap,
        businessId: String,
        rewardId: String
    ): Result<String> = runCatching {
        val ref = storage.reference
            .child(StoragePaths.REWARDS)
            .child(businessId)
            .child("$rewardId.jpg")

        ref.putBytes(
            bitmap.toJpegBytes(StoragePaths.REWARD_JPEG_QUALITY),
            StorageMetadata.Builder().setContentType("image/jpeg").build()
        ).await()

        ref.downloadUrl.await().toString()
    }
}
