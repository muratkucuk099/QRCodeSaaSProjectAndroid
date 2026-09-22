package murat.com.saasproject.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import murat.com.saasproject.data.cache.FirebaseDataCache
import murat.com.saasproject.data.model.UserBusiness
import murat.com.saasproject.data.model.UserModel
import murat.com.saasproject.domain.config.FirestorePaths

/**
 * Müşteri dokümanı ve puan durumunun canlı takibi.
 * iOS `FirebaseService.startUserBusinessesListener` bölümünün karşılığı.
 */
class UserRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val cache: FirebaseDataCache = FirebaseDataCache
) {

    private var userListener: ListenerRegistration? = null

    suspend fun fetchUser(userId: String): Result<UserModel> = runCatching {
        val snapshot = db.collection(FirestorePaths.USERS).document(userId).get().await()
        UserModel.fromMap(snapshot.data ?: error("Kullanıcı bulunamadı"), snapshot.id)
            ?: error("Kullanıcı bulunamadı")
    }

    /**
     * Kullanıcının işletme/puan listesini canlı izler. QR okutulduğunda puan anında güncellenir.
     *
     * Cache'te veri varsa listener bağlanmadan önce hemen yayınlanır; böylece ekran açılışta
     * boş görünmez (iOS'taki aynı davranış).
     */
    fun observeUserBusinesses(): Flow<List<UserBusiness>> = callbackFlow {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        cache.userBusinesses(userId)?.let { trySend(it) }

        userListener?.remove()
        userListener = db.collection(FirestorePaths.USERS)
            .document(userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                val mapped = UserBusiness.listFromRaw(snapshot?.get("businesses"))
                cache.storeUserBusinesses(mapped, userId)
                trySend(mapped)
            }

        awaitClose { stopObservingUserBusinesses() }
    }

    fun stopObservingUserBusinesses() {
        userListener?.remove()
        userListener = null
    }
}
