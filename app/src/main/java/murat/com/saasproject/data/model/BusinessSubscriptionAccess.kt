package murat.com.saasproject.data.model

/**
 * iOS `BusinessSubscriptionAccess.swift` karşılığı.
 *
 * İşletmenin uygulamayı kullanma yetkisini tek bir yerde toplar:
 * `canUseApp = isActive && isSubscriptionValid`
 *
 * Gösterim metinleri (`"3 gün kaldı"` vb.) `strings.xml` üzerinden üretilir; burada sadece
 * karar mantığı ve ham sayılar tutulur.
 */
data class BusinessSubscriptionAccess(
    val business: Business,
    val canUseApp: Boolean,
    val remainingDays: Int?
) {
    val hasSubscriptionDate: Boolean get() = business.subscriptionExpiresAt != null

    companion object {
        fun from(business: Business): BusinessSubscriptionAccess = BusinessSubscriptionAccess(
            business = business,
            canUseApp = business.isActive && business.isSubscriptionValid,
            remainingDays = business.remainingDays
        )
    }
}

/**
 * iOS `BusinessProfileStats.swift` karşılığı — işletme profilindeki 12 metrik.
 * Tümü `point_logs` + `reward_logs` üzerinden client-side hesaplanır (iOS ile aynı).
 */
data class BusinessProfileStats(
    val totalDistributedPoints: Int,
    val uniqueRecipientsCount: Int,
    val totalRewardsRedeemed: Int,
    val topRewardRecipientName: String?,
    val topRewardRecipientCount: Int,
    val newCustomersThisMonth: Int,
    val returningCustomersThisMonth: Int,
    val qrScansAfterLastNotification48h: Int?,
    val hasSentNotifications: Boolean,
    val activeCustomersLast30Days: Int,
    val averageReturnIntervalDays: Double?,
    val customersOnePointAway: Int
) {
    companion object {
        val EMPTY = BusinessProfileStats(
            totalDistributedPoints = 0,
            uniqueRecipientsCount = 0,
            totalRewardsRedeemed = 0,
            topRewardRecipientName = null,
            topRewardRecipientCount = 0,
            newCustomersThisMonth = 0,
            returningCustomersThisMonth = 0,
            qrScansAfterLastNotification48h = null,
            hasSentNotifications = false,
            activeCustomersLast30Days = 0,
            averageReturnIntervalDays = null,
            customersOnePointAway = 0
        )
    }
}

/** iOS `RewardsScreenMode.swift` — ödül listesinin hangi amaçla açıldığı. */
enum class RewardsScreenMode {
    /** İşletme kendi ödüllerini yönetir (silme, oluşturma yönlendirmesi). */
    MANAGE,

    /** İşletme müşteriye ödül vermek için seçim yapar (QR üretimi). */
    SELECT,

    /** Müşteri işletmenin ödüllerini sadece görüntüler. */
    VIEW_ONLY
}
