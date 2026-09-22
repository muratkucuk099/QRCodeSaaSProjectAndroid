package murat.com.saasproject.data.model

import com.google.firebase.Timestamp
import java.util.Date

/** iOS `LoginResult.swift`. Raw değerler cache'e yazıldığı için iOS ile aynı. */
enum class LoginResult(val rawValue: String) {
    MAIN_ADMIN("mainAdmin"),
    SUB_ADMIN("subAdmin"),
    USER("user");

    companion object {
        fun fromRaw(raw: String?): LoginResult? = entries.firstOrNull { it.rawValue == raw }
    }
}

/**
 * iOS `UserModel.swift` karşılığı — Firestore `users/{uid}`.
 *
 * `businesses` alanı Firestore'da `[{businessId, points}]` dizisi olarak tutulur.
 */
data class UserModel(
    val id: String,
    val name: String,
    val email: String,
    val createdAt: Date,
    val businesses: List<UserBusiness> = emptyList(),
    val kvkkAcceptedAt: Date? = null,
    val kvkkPolicyVersion: String? = null
) {
    fun toFirestoreMap(): Map<String, Any> = buildMap {
        put("id", id)
        put("name", name)
        put("email", email)
        put("createdAt", Timestamp(createdAt))
        put("businesses", businesses.map { it.toMap() })
        kvkkAcceptedAt?.let { put("kvkkAcceptedAt", Timestamp(it)) }
        kvkkPolicyVersion?.let { put("kvkkPolicyVersion", it) }
    }

    companion object {
        fun fromMap(data: Map<String, Any?>, documentId: String? = null): UserModel? {
            val id = (data["id"] as? String)?.takeIf { it.isNotEmpty() } ?: documentId ?: return null
            val name = data["name"] as? String ?: return null
            val email = data["email"] as? String ?: return null

            return UserModel(
                id = id,
                name = name,
                email = email,
                createdAt = data.dateOrNow("createdAt"),
                businesses = UserBusiness.listFromRaw(data["businesses"]),
                kvkkAcceptedAt = data.dateOrNull("kvkkAcceptedAt"),
                kvkkPolicyVersion = data["kvkkPolicyVersion"] as? String
            )
        }
    }
}

/** iOS `UserBusiness.swift` — kullanıcının bir işletmedeki puan kaydı. */
data class UserBusiness(
    val businessId: String,
    val points: Int
) {
    fun toMap(): Map<String, Any> = mapOf(
        "businessId" to businessId,
        "points" to points
    )

    companion object {
        fun listFromRaw(raw: Any?): List<UserBusiness> {
            val list = raw as? List<*> ?: return emptyList()
            return list.mapNotNull { item ->
                val map = item as? Map<*, *> ?: return@mapNotNull null
                val businessId = map["businessId"] as? String ?: return@mapNotNull null
                val points = (map["points"] as? Number)?.toInt() ?: return@mapNotNull null
                UserBusiness(businessId, points)
            }
        }
    }
}

/** iOS `BusinessUser.swift` — işletmenin bir müşterisi (`businesses/{id}/users/{userId}`). */
data class BusinessUser(
    val userId: String,
    val points: Int,
    val userName: String = ""
)
