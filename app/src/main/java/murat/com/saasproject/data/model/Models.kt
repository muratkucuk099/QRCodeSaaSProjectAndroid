package murat.com.saasproject.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import java.util.Date
import java.util.Locale

enum class LoginResult {
    MAIN_ADMIN,
    SUB_ADMIN,
    USER
}

data class Business(
    val id: String,
    val name: String,
    val logoURL: String? = null,
    val phone: String,
    val email: String,
    val businessType: String,
    val createdAt: Date = Date(),
    val isApproved: Boolean = false,
    val rewards: List<String> = emptyList()
) {
    fun toMap(): Map<String, Any> = buildMap {
        put("id", id)
        put("name", name)
        put("logoURL", logoURL.orEmpty())
        put("phone", phone)
        put("email", email)
        put("businessType", businessType)
        put("createdAt", Timestamp(createdAt))
        put("isApproved", isApproved)
        put("rewards", rewards)
    }

    companion object {
        fun fromMap(data: Map<String, Any>, documentId: String? = null): Business? {
            val id = (data["id"] as? String) ?: documentId ?: return null
            val name = data["name"] as? String ?: return null
            val phone = data["phone"] as? String ?: return null
            val email = data["email"] as? String ?: return null
            val businessType = data["businessType"] as? String ?: return null
            val isApproved = data["isApproved"] as? Boolean ?: return null
            val logoURL = (data["logoURL"] as? String)?.takeIf { it.isNotEmpty() }
            val rewards = (data["rewards"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
            val createdAt = when (val value = data["createdAt"]) {
                is Timestamp -> value.toDate()
                is Date -> value
                else -> Date()
            }
            return Business(
                id = id,
                name = name,
                logoURL = logoURL,
                phone = phone,
                email = email,
                businessType = businessType,
                createdAt = createdAt,
                isApproved = isApproved,
                rewards = rewards
            )
        }

        fun fromSnapshot(snapshot: DocumentSnapshot): Business? {
            val data = snapshot.data ?: return null
            return fromMap(data, snapshot.id)
        }
    }
}

data class UserModel(
    val id: String,
    val name: String,
    val email: String,
    val createdAt: Date = Date(),
    val businesses: List<Map<String, Any>> = emptyList()
) {
    fun toMap(): Map<String, Any> = mapOf(
        "id" to id,
        "name" to name,
        "email" to email,
        "createdAt" to Timestamp(createdAt),
        "businesses" to businesses
    )
}

data class UserBusiness(
    val businessId: String,
    val points: Int
)

data class Reward(
    val rewardId: String,
    val businessId: String,
    val name: String,
    val requiredPoints: Int,
    val imageUrl: String,
    val description: String
) {
    val toMap: Map<String, Any>
        get() = mapOf(
            "rewardId" to rewardId,
            "businessId" to businessId,
            "name" to name,
            "requiredPoints" to requiredPoints,
            "imageUrl" to imageUrl,
            "description" to description
        )
}

data class QRPayload(
    val qrCode: String,
    val businessId: String,
    val points: Int,
    val userId: String? = null,
    val rewardId: String? = null,
    val rewardName: String? = null
)

data class RewardLog(
    val id: String,
    val userId: String,
    val rewardId: String,
    val rewardName: String,
    val usedPoints: Int,
    val qrCode: String,
    val createdAt: Date = Date()
) {
    fun toMap(): Map<String, Any> = mapOf(
        "id" to id,
        "userId" to userId,
        "rewardId" to rewardId,
        "rewardName" to rewardName,
        "usedPoints" to usedPoints,
        "qrCode" to qrCode,
        "createdAt" to Timestamp(createdAt)
    )
}

data class BusinessProfileStats(
    val totalDistributedPoints: Int,
    val uniqueRecipientsCount: Int,
    val totalRewardsRedeemed: Int,
    val topRewardRecipientName: String? = null,
    val topRewardRecipientCount: Int = 0
) {
    val topRewardRecipientDisplay: String
        get() {
            if (topRewardRecipientCount <= 0) return "—"
            val name = topRewardRecipientName
            return if (!name.isNullOrEmpty()) {
                "$name ($topRewardRecipientCount)"
            } else {
                "$topRewardRecipientCount ödül"
            }
        }
}

data class BusinessNotification(
    val id: String,
    val title: String,
    val body: String,
    val targetUserCount: Int = 0,
    val sentCount: Int = 0,
    val failedCount: Int = 0,
    val status: String = "sent",
    val createdAt: Date = Date()
) {
    val summaryText: String
        get() = when (status) {
            "no_recipients" -> "Kayıtlı kullanıcı yok"
            "no_tokens" -> "Cihaz token'ı yok"
            "sent" -> "$sentCount cihaza gönderildi"
            else -> "$sentCount gönderim"
        }

    val detailText: String
        get() {
            val formatter = java.text.SimpleDateFormat("d MMM yyyy HH:mm", Locale("tr", "TR"))
            return "${formatter.format(createdAt)} • $summaryText"
        }

    companion object {
        fun fromMap(id: String, data: Map<String, Any>): BusinessNotification? {
            val title = data["title"] as? String ?: return null
            val body = data["body"] as? String ?: return null
            val createdAt = when (val value = data["createdAt"]) {
                is Timestamp -> value.toDate()
                is Date -> value
                else -> Date()
            }
            return BusinessNotification(
                id = id,
                title = title,
                body = body,
                targetUserCount = (data["targetUserCount"] as? Number)?.toInt() ?: 0,
                sentCount = (data["sentCount"] as? Number)?.toInt() ?: 0,
                failedCount = (data["failedCount"] as? Number)?.toInt() ?: 0,
                status = data["status"] as? String ?: "sent",
                createdAt = createdAt
            )
        }
    }
}

data class UserNotification(
    val id: String,
    val businessId: String,
    val businessName: String,
    val businessLogoURL: String? = null,
    val title: String,
    val body: String,
    val createdAt: Date = Date()
) {
    val detailText: String
        get() {
            val formatter = java.text.SimpleDateFormat("d MMM yyyy HH:mm", Locale("tr", "TR"))
            return "$businessName • ${formatter.format(createdAt)}"
        }

    companion object {
        fun fromMap(id: String, data: Map<String, Any>): UserNotification? {
            val businessId = data["businessId"] as? String ?: return null
            val title = data["title"] as? String ?: return null
            val body = data["body"] as? String ?: return null
            val businessNameRaw = data["businessName"] as? String
            val businessName = if (businessNameRaw.isNullOrEmpty()) "İşletme" else businessNameRaw
            val createdAt = when (val value = data["createdAt"]) {
                is Timestamp -> value.toDate()
                is Date -> value
                else -> Date()
            }
            return UserNotification(
                id = id,
                businessId = businessId,
                businessName = businessName,
                businessLogoURL = data["businessLogoURL"] as? String,
                title = title,
                body = body,
                createdAt = createdAt
            )
        }
    }
}

data class PushNotificationDraft(
    val businessId: String,
    val title: String,
    val body: String
)

data class PushNotificationSendResult(
    val draft: PushNotificationDraft,
    val targetUserCount: Int,
    val sentCount: Int,
    val failedCount: Int,
    val message: String
)
