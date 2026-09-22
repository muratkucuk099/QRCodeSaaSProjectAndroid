package murat.com.saasproject.data.repository

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await
import murat.com.saasproject.data.cache.FirebaseDataCache
import murat.com.saasproject.data.model.BusinessProfileStats
import murat.com.saasproject.domain.config.FirestorePaths
import java.util.Calendar
import java.util.Date

/**
 * İşletme profili istatistikleri. iOS `FirebaseService.fetchBusinessProfileStats` +
 * `computeProfileStats` fonksiyonlarının birebir karşılığı.
 *
 * Beş koleksiyon paralel çekilir (iOS `DispatchGroup` -> Kotlin `async`), ardından tüm
 * metrikler bellekte hesaplanır. Sonuç cache'lenir; ekran her açıldığında yeniden
 * hesaplanmaz, yalnızca pull-to-refresh veya puan hareketi sonrası tazelenir.
 */
class StatsRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val cache: FirebaseDataCache = FirebaseDataCache
) {

    suspend fun fetchBusinessProfileStats(
        businessId: String,
        forceRefresh: Boolean = false
    ): Result<BusinessProfileStats> = runCatching {
        if (!forceRefresh) {
            cache.profileStats(businessId)?.let { return@runCatching it }
        }

        val businessRef = db.collection(FirestorePaths.BUSINESSES).document(businessId)

        val (pointLogs, rewardLogs, notifications, businessUsers, rewards) = coroutineScope {
            val pointLogsTask = async {
                businessRef.collection(FirestorePaths.SUB_POINT_LOGS).get().await().documents
            }
            val rewardLogsTask = async {
                businessRef.collection(FirestorePaths.SUB_REWARD_LOGS).get().await().documents
            }
            val notificationsTask = async {
                businessRef.collection(FirestorePaths.SUB_NOTIFICATIONS)
                    .orderBy("createdAt", Query.Direction.DESCENDING)
                    .get().await().documents
            }
            val businessUsersTask = async {
                businessRef.collection(FirestorePaths.SUB_USERS).get().await().documents
            }
            val rewardsTask = async {
                db.collection(FirestorePaths.REWARDS)
                    .whereEqualTo("businessId", businessId)
                    .get().await().documents
            }

            StatsSources(
                pointLogsTask.await(),
                rewardLogsTask.await(),
                notificationsTask.await(),
                businessUsersTask.await(),
                rewardsTask.await()
            )
        }

        val computed = compute(pointLogs, rewardLogs, notifications, businessUsers, rewards)

        // En çok ödül alan kişinin adı ayrı bir okuma gerektiriyor; yalnızca gerçekten
        // bir kazanan varsa yapılır (iOS ile aynı koşullu okuma).
        val stats = if (computed.topUserId != null && computed.topCount > 0) {
            val userName = runCatching {
                businessRef.collection(FirestorePaths.SUB_USERS)
                    .document(computed.topUserId)
                    .get()
                    .await()
                    .getString("userName")
            }.getOrNull()
            computed.stats.copy(topRewardRecipientName = userName)
        } else {
            computed.stats
        }

        cache.storeProfileStats(stats, businessId)
        stats
    }

    private data class StatsSources(
        val pointLogs: List<DocumentSnapshot>,
        val rewardLogs: List<DocumentSnapshot>,
        val notifications: List<DocumentSnapshot>,
        val businessUsers: List<DocumentSnapshot>,
        val rewards: List<DocumentSnapshot>
    )

    private data class Computed(
        val stats: BusinessProfileStats,
        val topUserId: String?,
        val topCount: Int
    )

    private fun compute(
        pointLogs: List<DocumentSnapshot>,
        rewardLogs: List<DocumentSnapshot>,
        notifications: List<DocumentSnapshot>,
        businessUsers: List<DocumentSnapshot>,
        rewards: List<DocumentSnapshot>
    ): Computed {
        val now = Date()
        val monthStart = startOfCurrentMonth(now)
        val thirtyDaysAgo = Calendar.getInstance().apply {
            time = now
            add(Calendar.DAY_OF_YEAR, -30)
        }.time

        // Yalnızca POZİTİF puan hareketleri "ziyaret" sayılır; ödül harcamaları (negatif) hariç.
        val visitsByUser = mutableMapOf<String, MutableList<Date>>()
        var totalDistributedPoints = 0

        for (doc in pointLogs) {
            val points = (doc.get("points") as? Number)?.toInt() ?: 0
            if (points <= 0) continue
            val userId = doc.getString("userId")?.takeIf { it.isNotEmpty() } ?: continue

            totalDistributedPoints += points
            visitsByUser.getOrPut(userId) { mutableListOf() }
                .add(doc.getTimestamp("createdAt")?.toDate() ?: now)
        }

        val uniqueRecipientsCount = visitsByUser.size

        var newCustomersThisMonth = 0
        var returningCustomersThisMonth = 0
        var activeCustomersLast30Days = 0
        val intervalSamples = mutableListOf<Double>()

        for (dates in visitsByUser.values) {
            val sorted = dates.sorted()
            val firstVisit = sorted.firstOrNull() ?: continue

            // Bu ay gelen müşteri: ilk ziyareti de bu aysa "yeni", değilse "geri dönen".
            if (sorted.any { !it.before(monthStart) }) {
                if (!firstVisit.before(monthStart)) newCustomersThisMonth++ else returningCustomersThisMonth++
            }

            if (sorted.any { !it.before(thirtyDaysAgo) }) activeCustomersLast30Days++

            // Ortalama geri dönüş aralığı: ardışık ziyaretler arası gün farkları.
            for (index in 1 until sorted.size) {
                val dayGap = (sorted[index].time - sorted[index - 1].time) / MILLIS_PER_DAY
                if (dayGap > 0) intervalSamples.add(dayGap)
            }
        }

        val averageReturnIntervalDays =
            if (intervalSamples.isEmpty()) null else intervalSamples.average()

        val redemptionCountsByUser = mutableMapOf<String, Int>()
        for (doc in rewardLogs) {
            val userId = doc.getString("userId")?.takeIf { it.isNotEmpty() } ?: continue
            redemptionCountsByUser[userId] = (redemptionCountsByUser[userId] ?: 0) + 1
        }

        val topEntry = redemptionCountsByUser.maxByOrNull { it.value }

        val notificationDates = notifications
            .mapNotNull { it.getTimestamp("createdAt")?.toDate() }
            .sortedDescending()

        val hasSentNotifications = notificationDates.isNotEmpty()

        // Son bildirimden sonraki 48 saat içinde okutulan QR sayısı — kampanya etkisi ölçümü.
        val qrScansAfterLastNotification48h = notificationDates.firstOrNull()?.let { lastDate ->
            val windowEnd = Date(lastDate.time + FORTY_EIGHT_HOURS_MILLIS)
            pointLogs.count { doc ->
                val points = (doc.get("points") as? Number)?.toInt() ?: 0
                if (points <= 0) return@count false
                val createdAt = doc.getTimestamp("createdAt")?.toDate() ?: return@count false
                createdAt.after(lastDate) && !createdAt.after(windowEnd)
            }
        }

        val rewardThresholds = rewards.mapNotNull { doc ->
            (doc.get("requiredPoints") as? Number)?.toInt()?.takeIf { it > 0 }
        }

        // "Ödülüne 1 puan kala" — bir sonraki gelişinde ödül alacak müşteri sayısı.
        val customersOnePointAway = businessUsers.count { doc ->
            val points = (doc.get("points") as? Number)?.toInt() ?: 0
            if (points <= 0) return@count false
            rewardThresholds.any { it - points == 1 }
        }

        val stats = BusinessProfileStats(
            totalDistributedPoints = totalDistributedPoints,
            uniqueRecipientsCount = uniqueRecipientsCount,
            totalRewardsRedeemed = rewardLogs.size,
            topRewardRecipientName = null,
            topRewardRecipientCount = topEntry?.value ?: 0,
            newCustomersThisMonth = newCustomersThisMonth,
            returningCustomersThisMonth = returningCustomersThisMonth,
            qrScansAfterLastNotification48h = qrScansAfterLastNotification48h,
            hasSentNotifications = hasSentNotifications,
            activeCustomersLast30Days = activeCustomersLast30Days,
            averageReturnIntervalDays = averageReturnIntervalDays,
            customersOnePointAway = customersOnePointAway
        )

        return Computed(stats, topEntry?.key, topEntry?.value ?: 0)
    }

    private fun startOfCurrentMonth(now: Date): Date = Calendar.getInstance().apply {
        time = now
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.time

    private companion object {
        const val MILLIS_PER_DAY = 86_400_000.0
        const val FORTY_EIGHT_HOURS_MILLIS = 48L * 60L * 60L * 1000L
    }
}
