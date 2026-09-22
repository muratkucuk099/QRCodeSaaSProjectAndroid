package murat.com.saasproject.data.model

import com.google.firebase.firestore.DocumentSnapshot
import java.util.Date

/**
 * iOS `BusinessNotification.swift` — `businesses/{id}/notifications/{id}`.
 * Dokümanlar Cloud Function (`sendBusinessPush`) tarafından yazılır.
 *
 * Gösterim metinleri `strings.xml` üzerinden üretildiği için burada sadece ham veri tutulur.
 */
data class BusinessNotification(
    val id: String,
    val title: String,
    val body: String,
    val targetUserCount: Int = 0,
    val sentCount: Int = 0,
    val failedCount: Int = 0,
    val status: String = STATUS_SENT,
    val createdAt: Date
) {
    companion object {
        const val STATUS_SENT = "sent"
        const val STATUS_NO_RECIPIENTS = "no_recipients"
        const val STATUS_NO_TOKENS = "no_tokens"

        fun fromSnapshot(snapshot: DocumentSnapshot): BusinessNotification? {
            val data = snapshot.data ?: return null
            val title = data["title"] as? String ?: return null
            val body = data["body"] as? String ?: return null

            return BusinessNotification(
                id = snapshot.id,
                title = title,
                body = body,
                targetUserCount = (data["targetUserCount"] as? Number)?.toInt() ?: 0,
                sentCount = (data["sentCount"] as? Number)?.toInt() ?: 0,
                failedCount = (data["failedCount"] as? Number)?.toInt() ?: 0,
                status = data["status"] as? String ?: STATUS_SENT,
                createdAt = data.dateOrNow("createdAt")
            )
        }
    }
}

/**
 * iOS `UserNotification.swift` — `users/{uid}/notifications/{id}`.
 * Cloud Function bildirimi gönderirken bu dokümanları batch olarak yazar.
 */
data class UserNotification(
    val id: String,
    val businessId: String,
    val businessName: String,
    val businessLogoURL: String?,
    val title: String,
    val body: String,
    val createdAt: Date
) {
    companion object {
        fun fromSnapshot(snapshot: DocumentSnapshot, fallbackBusinessName: String): UserNotification? {
            val data = snapshot.data ?: return null
            val businessId = data["businessId"] as? String ?: return null
            val title = data["title"] as? String ?: return null
            val body = data["body"] as? String ?: return null

            // iOS: businessName boşsa "İşletme" fallback'i kullanılır.
            val businessName = (data["businessName"] as? String)
                ?.takeIf { it.isNotEmpty() }
                ?: fallbackBusinessName

            return UserNotification(
                id = snapshot.id,
                businessId = businessId,
                businessName = businessName,
                businessLogoURL = (data["businessLogoURL"] as? String)?.takeIf { it.isNotEmpty() },
                title = title,
                body = body,
                createdAt = data.dateOrNow("createdAt")
            )
        }
    }
}

/** iOS `PushNotificationDraft.swift`. */
data class PushNotificationDraft(
    val businessId: String,
    val title: String,
    val body: String
)

/** iOS `PushNotificationSendResult.swift` — Cloud Function yanıtı. */
data class PushNotificationSendResult(
    val draft: PushNotificationDraft,
    val targetUserCount: Int,
    val sentCount: Int,
    val failedCount: Int,
    val message: String
)

/** iOS `AppPublicConfig.swift` — `app_config/public`. */
data class AppPublicConfig(
    val phone: String,
    val price: String
) {
    companion object {
        fun fromMap(data: Map<String, Any?>?): AppPublicConfig? {
            if (data == null) return null
            val phone = data.stringValue("phone")?.takeIf { it.isNotEmpty() } ?: return null
            return AppPublicConfig(
                phone = phone,
                price = data.stringValue("price") ?: return null
            )
        }

        /** iOS `stringValue(from:)` — sayı da olabilecek alanları String'e çevirir. */
        private fun Map<String, Any?>.stringValue(key: String): String? = when (val v = this[key]) {
            is String -> v.trim().takeIf { it.isNotEmpty() }
            is Number -> v.toString()
            else -> null
        }
    }
}
