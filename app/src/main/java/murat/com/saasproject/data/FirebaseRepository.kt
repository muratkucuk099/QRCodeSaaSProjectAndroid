package murat.com.saasproject.data

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import murat.com.saasproject.data.model.Business
import murat.com.saasproject.data.model.BusinessNotification
import murat.com.saasproject.data.model.BusinessProfileStats
import murat.com.saasproject.data.model.LoginResult
import murat.com.saasproject.data.model.PushNotificationDraft
import murat.com.saasproject.data.model.PushNotificationSendResult
import murat.com.saasproject.data.model.Reward
import murat.com.saasproject.data.model.RewardLog
import murat.com.saasproject.data.model.UserBusiness
import murat.com.saasproject.data.model.UserModel
import murat.com.saasproject.data.model.UserNotification
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.Date
import java.util.UUID
import java.util.concurrent.TimeUnit

object FirebaseRepository {
    const val MAIN_ADMIN_UID = "puDrOcA8HLae65OChJu1N8j11lc2"
    private const val CLOUD_FUNCTION_BASE =
        "https://us-central1-saasproject-23a1f.cloudfunctions.net/"

    private val auth: FirebaseAuth get() = FirebaseAuth.getInstance()
    private val db: FirebaseFirestore get() = FirebaseFirestore.getInstance()
    private val storage: FirebaseStorage get() = FirebaseStorage.getInstance()
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private var userBusinessesListener: ListenerRegistration? = null

    suspend fun createUser(email: String, password: String): Result<String> = runCatching {
        val result = auth.createUserWithEmailAndPassword(email, password).await()
        result.user?.uid ?: throw IllegalStateException("Kullanıcı oluşturulamadı")
    }

    suspend fun login(email: String, password: String): Result<LoginResult> = runCatching {
        val result = auth.signInWithEmailAndPassword(email, password).await()
        val uid = result.user?.uid ?: throw IllegalStateException("Giriş başarısız")
        checkRole(uid).getOrThrow()
    }

    suspend fun checkRole(uid: String): Result<LoginResult> = runCatching {
        if (uid == MAIN_ADMIN_UID) {
            return@runCatching LoginResult.MAIN_ADMIN
        }

        val userDoc = db.collection("users").document(uid).get().await()
        if (userDoc.exists()) {
            return@runCatching LoginResult.USER
        }

        val businessDoc = db.collection("businesses").document(uid).get().await()
        if (businessDoc.exists()) {
            return@runCatching LoginResult.SUB_ADMIN
        }

        throw IllegalStateException("Yetkisiz kullanıcı")
    }

    suspend fun saveUser(user: UserModel): Result<Unit> = runCatching {
        db.collection("users").document(user.id).set(user.toMap()).await()
    }

    suspend fun saveBusiness(business: Business): Result<Unit> = runCatching {
        db.collection("businesses").document(business.id).set(business.toMap()).await()
    }

    suspend fun fetchBusiness(businessId: String): Result<Business> = runCatching {
        val snapshot = db.collection("businesses").document(businessId).get().await()
        Business.fromSnapshot(snapshot)
            ?: throw IllegalStateException("İşletme bulunamadı")
    }

    suspend fun fetchApprovedBusinesses(): Result<List<Business>> = runCatching {
        val snapshot = db.collection("businesses")
            .whereEqualTo("isApproved", true)
            .get()
            .await()

        snapshot.documents.mapNotNull { doc ->
            Business.fromSnapshot(doc)
        }.sortedBy { it.name.lowercase() }
    }

    suspend fun fetchRewards(businessId: String): List<Reward> {
        val snapshot = db.collection("rewards")
            .whereEqualTo("businessId", businessId)
            .get()
            .await()

        return snapshot.documents.mapNotNull { doc ->
            val data = doc.data ?: return@mapNotNull null
            Reward(
                rewardId = data["rewardId"] as? String ?: doc.id,
                businessId = data["businessId"] as? String ?: businessId,
                name = data["name"] as? String ?: "",
                requiredPoints = (data["requiredPoints"] as? Number)?.toInt() ?: 0,
                imageUrl = data["imageUrl"] as? String ?: "",
                description = data["description"] as? String ?: ""
            )
        }
    }

    suspend fun createReward(reward: Reward): Result<Unit> = runCatching {
        val batch = db.batch()
        val rewardRef = db.collection("rewards").document(reward.rewardId)
        batch.set(rewardRef, reward.toMap)
        val businessRef = db.collection("businesses").document(reward.businessId)
        batch.update(businessRef, "rewards", FieldValue.arrayUnion(reward.rewardId))
        batch.commit().await()
    }

    suspend fun deleteReward(rewardId: String, businessId: String): Result<Unit> = runCatching {
        val batch = db.batch()
        batch.delete(db.collection("rewards").document(rewardId))
        batch.update(
            db.collection("businesses").document(businessId),
            "rewards",
            FieldValue.arrayRemove(rewardId)
        )
        batch.commit().await()
    }

    suspend fun updatePointsForUser(
        businessId: String,
        userId: String,
        points: Int,
        rewardLog: RewardLog? = null
    ): Result<Int> = runCatching {
        val userRef = db.collection("users").document(userId)
        val businessUserRef = db.collection("businesses")
            .document(businessId)
            .collection("users")
            .document(userId)
        val logId = UUID.randomUUID().toString()
        val logRef = db.collection("businesses")
            .document(businessId)
            .collection("point_logs")
            .document(logId)
        val rewardLogRef = db.collection("businesses")
            .document(businessId)
            .collection("reward_logs")
            .document(logId)

        var newPoints = points

        db.runTransaction { transaction ->
            val userSnapshot = transaction.get(userRef)
            if (!userSnapshot.exists()) {
                throw IllegalStateException("Kullanıcı bulunamadı")
            }

            @Suppress("UNCHECKED_CAST")
            val businesses = (userSnapshot.get("businesses") as? List<Map<String, Any>>)?.toMutableList()
                ?: mutableListOf()

            val index = businesses.indexOfFirst { it["businessId"] == businessId }
            if (index >= 0) {
                val currentPoints = (businesses[index]["points"] as? Number)?.toInt() ?: 0
                newPoints = currentPoints + points
                if (newPoints < 0) {
                    throw IllegalStateException("Puan yetersiz veya 0'ın altına düşemez")
                }
                businesses[index] = mapOf("businessId" to businessId, "points" to newPoints)
            } else {
                if (points < 0) {
                    throw IllegalStateException("Puan yetersiz veya 0'ın altına düşemez")
                }
                businesses.add(mapOf("businessId" to businessId, "points" to points))
                newPoints = points
            }

            transaction.update(userRef, "businesses", businesses)
            transaction.set(
                businessUserRef,
                mapOf("userId" to userId, "points" to newPoints),
                com.google.firebase.firestore.SetOptions.merge()
            )

            val logData = mutableMapOf<String, Any>(
                "id" to logId,
                "userId" to userId,
                "points" to points,
                "createdAt" to Timestamp(Date())
            )
            rewardLog?.let { logData["rewardId"] = it.rewardId }
            transaction.set(logRef, logData)

            rewardLog?.let { reward ->
                transaction.set(rewardLogRef, reward.copy(id = logId).toMap())
            }

            null
        }.await()

        newPoints
    }

    suspend fun createActiveQRCode(
        businessId: String,
        qrCode: String,
        points: Int,
        rewardId: String? = null,
        rewardName: String? = null
    ): Result<Unit> = runCatching {
        val now = Date()
        val expiresAt = Date(now.time + 10 * 60 * 1000)
        val data = mutableMapOf<String, Any>(
            "qrCode" to qrCode,
            "businessId" to businessId,
            "points" to points,
            "generatedAt" to Timestamp(now),
            "expiresAt" to Timestamp(expiresAt)
        )
        rewardId?.let { data["rewardId"] = it }
        rewardName?.let { data["rewardName"] = it }

        db.collection("active_qr_codes").document(businessId).set(data).await()
    }

    suspend fun validateInviteCode(enteredCode: String): Result<Unit> = runCatching {
        val docRef = db.collection("invite_codes").document("code")

        db.runTransaction { transaction ->
            val snapshot = transaction.get(docRef)
            val data = snapshot.data
                ?: throw IllegalStateException("Davet kodu bulunamadı")

            val storedCode = data["code"] as? String
                ?: throw IllegalStateException("Davet kodu bulunamadı")
            val isUsed = data["isUsed"] as? Boolean
                ?: throw IllegalStateException("Davet kodu bulunamadı")

            if (storedCode != enteredCode.trim()) {
                throw IllegalStateException("Davet kodu yanlış")
            }
            if (isUsed) {
                throw IllegalStateException("Davet kodu zaten kullanılmış")
            }

            transaction.update(docRef, "isUsed", true)
            null
        }.await()
    }

    suspend fun saveInviteCode(code: String): Result<Unit> = runCatching {
        db.collection("invite_codes").document("code").set(
            mapOf(
                "code" to code,
                "isUsed" to false,
                "createdAt" to Timestamp(Date())
            )
        ).await()
    }

    suspend fun fetchBusinessNotifications(businessId: String): Result<List<BusinessNotification>> =
        runCatching {
            val snapshot = db.collection("businesses")
                .document(businessId)
                .collection("notifications")
                .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .get()
                .await()

            snapshot.documents.mapNotNull { doc ->
                BusinessNotification.fromMap(doc.id, doc.data ?: emptyMap())
            }
        }

    suspend fun deleteBusinessNotification(
        businessId: String,
        notificationId: String
    ): Result<Unit> = runCatching {
        db.collection("businesses")
            .document(businessId)
            .collection("notifications")
            .document(notificationId)
            .delete()
            .await()
    }

    suspend fun fetchUserNotifications(userId: String): Result<List<UserNotification>> = runCatching {
        val snapshot = db.collection("users")
            .document(userId)
            .collection("notifications")
            .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .get()
            .await()

        snapshot.documents.mapNotNull { doc ->
            UserNotification.fromMap(doc.id, doc.data ?: emptyMap())
        }
    }

    suspend fun fetchBusinessProfileStats(businessId: String): Result<BusinessProfileStats> =
        runCatching {
            val pointLogsRef = db.collection("businesses")
                .document(businessId)
                .collection("point_logs")
            val rewardLogsRef = db.collection("businesses")
                .document(businessId)
                .collection("reward_logs")

            val pointSnapshot = pointLogsRef.get().await()
            val rewardSnapshot = rewardLogsRef.get().await()

            var totalDistributedPoints = 0
            val uniqueRecipients = mutableSetOf<String>()
            var totalRewardsRedeemed = 0
            val redemptionCountsByUser = mutableMapOf<String, Int>()

            for (document in pointSnapshot.documents) {
                val data = document.data ?: continue
                val pts = (data["points"] as? Number)?.toInt() ?: 0
                if (pts <= 0) continue
                totalDistributedPoints += pts
                (data["userId"] as? String)?.takeIf { it.isNotEmpty() }?.let {
                    uniqueRecipients.add(it)
                }
            }

            for (document in rewardSnapshot.documents) {
                totalRewardsRedeemed++
                (document.data?.get("userId") as? String)?.takeIf { it.isNotEmpty() }?.let { userId ->
                    redemptionCountsByUser[userId] = (redemptionCountsByUser[userId] ?: 0) + 1
                }
            }

            val topEntry = redemptionCountsByUser.maxByOrNull { it.value }
            val topUserId = topEntry?.key
            val topCount = topEntry?.value ?: 0

            if (topUserId == null || topCount <= 0) {
                return@runCatching BusinessProfileStats(
                    totalDistributedPoints = totalDistributedPoints,
                    uniqueRecipientsCount = uniqueRecipients.size,
                    totalRewardsRedeemed = totalRewardsRedeemed
                )
            }

            val userSnapshot = db.collection("users").document(topUserId).get().await()
            val userName = userSnapshot.data?.get("name") as? String

            BusinessProfileStats(
                totalDistributedPoints = totalDistributedPoints,
                uniqueRecipientsCount = uniqueRecipients.size,
                totalRewardsRedeemed = totalRewardsRedeemed,
                topRewardRecipientName = userName,
                topRewardRecipientCount = topCount
            )
        }

    suspend fun updateBusinessProfile(
        businessId: String,
        name: String,
        phone: String,
        logoURL: String?
    ): Result<Unit> = runCatching {
        val data = mutableMapOf<String, Any>(
            "name" to name,
            "phone" to phone
        )
        logoURL?.let { data["logoURL"] = it }
        db.collection("businesses").document(businessId).update(data).await()
    }

    suspend fun uploadBusinessLogo(imageBytes: ByteArray, businessId: String): Result<String> =
        runCatching {
            val ref = storage.reference
                .child("businessLogos")
                .child(businessId)
                .child("logo.jpg")

            val metadata = com.google.firebase.storage.StorageMetadata.Builder()
                .setContentType("image/jpeg")
                .build()
            ref.putBytes(imageBytes, metadata).await()

            ref.downloadUrl.await().toString()
        }

    suspend fun uploadRewardImage(
        imageBytes: ByteArray,
        businessId: String,
        rewardId: String
    ): Result<String> = runCatching {
        val ref = storage.reference
            .child("rewards")
            .child(businessId)
            .child("$rewardId.jpg")

        val metadata = com.google.firebase.storage.StorageMetadata.Builder()
            .setContentType("image/jpeg")
            .build()
        ref.putBytes(imageBytes, metadata).await()

        ref.downloadUrl.await().toString()
    }

    suspend fun sendBusinessNotification(
        title: String,
        body: String
    ): Result<PushNotificationSendResult> = runCatching {
        val user = auth.currentUser ?: throw IllegalStateException("Oturum bulunamadı")
        val businessId = user.uid

        val json = performAuthenticatedCloudRequest(
            functionName = "sendBusinessPush",
            payload = mapOf("title" to title, "body" to body)
        ).getOrThrow()

        PushNotificationSendResult(
            draft = PushNotificationDraft(businessId = businessId, title = title, body = body),
            targetUserCount = json.optInt("targetUserCount", 0),
            sentCount = json.optInt("sentCount", 0),
            failedCount = json.optInt("failedCount", 0),
            message = json.optString("message", "Bildirim gönderildi.")
        )
    }

    suspend fun syncFCMToken(token: String, deviceId: String): Result<Unit> = runCatching {
        performAuthenticatedCloudRequest(
            functionName = "syncFCMToken",
            payload = mapOf(
                "token" to token,
                "deviceId" to deviceId,
                "platform" to "android"
            )
        ).getOrThrow()
        Unit
    }

    suspend fun removeFCMToken(deviceId: String): Result<Unit> = runCatching {
        val userId = auth.currentUser?.uid ?: return@runCatching
        db.collection("users")
            .document(userId)
            .collection("fcmTokens")
            .document(deviceId)
            .delete()
            .await()
    }

    fun startUserBusinessesListener(): Flow<List<UserBusiness>> = callbackFlow {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        userBusinessesListener?.remove()
        userBusinessesListener = db.collection("users")
            .document(userId)
            .addSnapshotListener { snapshot, _ ->
                @Suppress("UNCHECKED_CAST")
                val raw = snapshot?.get("businesses") as? List<Map<String, Any>> ?: emptyList()
                val mapped = raw.mapNotNull { item ->
                    val businessId = item["businessId"] as? String ?: return@mapNotNull null
                    val pts = (item["points"] as? Number)?.toInt() ?: return@mapNotNull null
                    UserBusiness(businessId = businessId, points = pts)
                }
                trySend(mapped)
            }

        awaitClose {
            userBusinessesListener?.remove()
            userBusinessesListener = null
        }
    }

    fun stopUserBusinessesListener() {
        userBusinessesListener?.remove()
        userBusinessesListener = null
    }

    fun currentUserId(): String? = auth.currentUser?.uid

    suspend fun signOut() {
        stopUserBusinessesListener()
        auth.signOut()
    }

    private suspend fun performAuthenticatedCloudRequest(
        functionName: String,
        payload: Map<String, Any>
    ): Result<JSONObject> = withContext(Dispatchers.IO) {
        runCatching {
        val user = auth.currentUser ?: throw IllegalStateException("Oturum bulunamadı")
        val token = user.getIdToken(true).await().token
            ?: throw IllegalStateException("Oturum token'ı alınamadı")

        val url = CLOUD_FUNCTION_BASE + functionName
        val body = JSONObject(payload).toString()
            .toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url(url)
            .post(body)
            .addHeader("Authorization", "Bearer $token")
            .addHeader("Content-Type", "application/json")
            .build()

        httpClient.newCall(request).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            val json = if (responseBody.isNotEmpty()) JSONObject(responseBody) else JSONObject()

            if (!response.isSuccessful) {
                val message = json.optString("message", "Sunucu hatası (${response.code})")
                throw IllegalStateException(message)
            }

            json
        }
        }
    }
}
