package murat.com.saasproject.data.cache

import murat.com.saasproject.data.model.AppPublicConfig
import murat.com.saasproject.data.model.Business
import murat.com.saasproject.data.model.BusinessNotification
import murat.com.saasproject.data.model.BusinessProfileStats
import murat.com.saasproject.data.model.LoginResult
import murat.com.saasproject.data.model.Reward
import murat.com.saasproject.data.model.UserBusiness
import murat.com.saasproject.data.model.UserNotification
import java.util.Collections

/**
 * iOS `FirebaseDataCache.swift` karşılığı.
 *
 * IOS davranışı:
 *   Bellek + disk (`Application Support/FirebaseDataCache/snapshot.json`), TTL YOK.
 *   Veri yalnızca yazma işlemlerinde invalidate edilir, çıkışta `clearAll()` ile tamamen silinir.
 *
 * ANDROID karşılığı:
 *   Aynı invalidation semantiği ile process-ömürlü bellek cache'i. Uygulama yeniden
 *   başladığında disk katmanı yerine Firestore'un kendi kalıcı disk cache'i devreye girer
 *   (`KumbaramApplication` içinde `setLocalCacheSettings` ile açılır). Böylece iOS'taki
 *   "aynı veriyi tekrar tekrar çekme" davranışı korunur, ancak elle JSON serileştirme
 *   yapmak yerine SDK'nın kendi kalıcılığı kullanılır.
 *
 * Tüm koleksiyonlar thread-safe (`synchronizedMap`), çünkü Firestore callback'leri ile
 * ViewModel coroutine'leri farklı thread'lerden erişebilir.
 */
object FirebaseDataCache {

    private val businesses = Collections.synchronizedMap(mutableMapOf<String, Business>())
    private val rewardsByBusiness = Collections.synchronizedMap(mutableMapOf<String, List<Reward>>())
    private val businessNotifications =
        Collections.synchronizedMap(mutableMapOf<String, List<BusinessNotification>>())
    private val userNotifications =
        Collections.synchronizedMap(mutableMapOf<String, List<UserNotification>>())
    private val profileStats =
        Collections.synchronizedMap(mutableMapOf<String, BusinessProfileStats>())
    private val userBusinesses =
        Collections.synchronizedMap(mutableMapOf<String, List<UserBusiness>>())
    private val loginRoles = Collections.synchronizedMap(mutableMapOf<String, LoginResult>())

    @Volatile
    private var activeBusinesses: List<Business>? = null

    @Volatile
    private var appPublicConfig: AppPublicConfig? = null

    // region Business

    fun business(id: String): Business? = businesses[id]

    fun storeBusiness(business: Business) {
        businesses[business.id] = business
    }

    fun invalidateBusiness(id: String) {
        businesses.remove(id)
    }

    fun activeBusinesses(): List<Business>? = activeBusinesses

    fun storeActiveBusinesses(list: List<Business>) {
        activeBusinesses = list
    }

    fun invalidateActiveBusinesses() {
        activeBusinesses = null
    }

    // endregion

    // region Rewards

    fun rewards(businessId: String): List<Reward>? = rewardsByBusiness[businessId]

    fun storeRewards(rewards: List<Reward>, businessId: String) {
        rewardsByBusiness[businessId] = rewards
    }

    fun invalidateRewards(businessId: String) {
        rewardsByBusiness.remove(businessId)
    }

    // endregion

    // region Notifications

    fun businessNotifications(businessId: String): List<BusinessNotification>? =
        businessNotifications[businessId]

    fun storeBusinessNotifications(list: List<BusinessNotification>, businessId: String) {
        businessNotifications[businessId] = list
    }

    fun invalidateBusinessNotifications(businessId: String) {
        businessNotifications.remove(businessId)
    }

    fun userNotifications(userId: String): List<UserNotification>? = userNotifications[userId]

    fun storeUserNotifications(list: List<UserNotification>, userId: String) {
        userNotifications[userId] = list
    }

    fun invalidateUserNotifications(userId: String) {
        userNotifications.remove(userId)
    }

    // endregion

    // region Stats

    fun profileStats(businessId: String): BusinessProfileStats? = profileStats[businessId]

    fun storeProfileStats(stats: BusinessProfileStats, businessId: String) {
        profileStats[businessId] = stats
    }

    fun invalidateProfileStats(businessId: String) {
        profileStats.remove(businessId)
    }

    // endregion

    // region User businesses

    fun userBusinesses(userId: String): List<UserBusiness>? = userBusinesses[userId]

    fun storeUserBusinesses(list: List<UserBusiness>, userId: String) {
        userBusinesses[userId] = list
    }

    fun invalidateUserBusinesses(userId: String) {
        userBusinesses.remove(userId)
    }

    // endregion

    // region Login role

    fun loginRole(uid: String): LoginResult? = loginRoles[uid]

    fun storeLoginRole(role: LoginResult, uid: String) {
        loginRoles[uid] = role
    }

    // endregion

    // region App config

    fun appPublicConfig(): AppPublicConfig? = appPublicConfig

    fun storeAppPublicConfig(config: AppPublicConfig) {
        appPublicConfig = config
    }

    // endregion

    /** iOS `clearAll()` — çıkış / hesap silme sonrası tüm oturum verisini temizler. */
    fun clearAll() {
        businesses.clear()
        rewardsByBusiness.clear()
        businessNotifications.clear()
        userNotifications.clear()
        profileStats.clear()
        userBusinesses.clear()
        loginRoles.clear()
        activeBusinesses = null
        appPublicConfig = null
    }
}
