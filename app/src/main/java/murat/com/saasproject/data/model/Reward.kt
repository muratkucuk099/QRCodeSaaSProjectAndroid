package murat.com.saasproject.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import java.util.Date

/** iOS `Reward.swift` karşılığı — Firestore `rewards/{rewardId}`. */
data class Reward(
    val rewardId: String,
    val businessId: String,
    val name: String,
    val requiredPoints: Int,
    val imageUrl: String,
    val description: String
) {
    fun toFirestoreMap(): Map<String, Any> = mapOf(
        "rewardId" to rewardId,
        "businessId" to businessId,
        "name" to name,
        "requiredPoints" to requiredPoints,
        "imageUrl" to imageUrl,
        "description" to description
    )

    companion object {
        fun fromSnapshot(snapshot: DocumentSnapshot, fallbackBusinessId: String? = null): Reward? {
            val data = snapshot.data ?: return null
            return Reward(
                rewardId = data["rewardId"] as? String ?: snapshot.id,
                businessId = data["businessId"] as? String ?: fallbackBusinessId ?: return null,
                name = data["name"] as? String ?: "",
                requiredPoints = (data["requiredPoints"] as? Number)?.toInt() ?: 0,
                imageUrl = data["imageUrl"] as? String ?: "",
                description = data["description"] as? String ?: ""
            )
        }
    }
}

/** iOS `PointLog.swift` — `businesses/{id}/point_logs/{id}`. */
data class PointLog(
    val id: String,
    val userId: String,
    val points: Int,
    val rewardId: String? = null,
    val createdAt: Date
) {
    companion object {
        fun fromSnapshot(snapshot: DocumentSnapshot): PointLog? {
            val data = snapshot.data ?: return null
            return PointLog(
                id = data["id"] as? String ?: snapshot.id,
                userId = data["userId"] as? String ?: return null,
                points = (data["points"] as? Number)?.toInt() ?: 0,
                rewardId = data["rewardId"] as? String,
                createdAt = data.dateOrNow("createdAt")
            )
        }
    }
}

/** iOS `RewardLog.swift` — `businesses/{id}/reward_logs/{id}`. */
data class RewardLog(
    val id: String,
    val userId: String,
    val rewardId: String,
    val rewardName: String,
    val usedPoints: Int,
    val qrCode: String,
    val createdAt: Date = Date()
) {
    fun toFirestoreMap(): Map<String, Any> = mapOf(
        "id" to id,
        "userId" to userId,
        "rewardId" to rewardId,
        "rewardName" to rewardName,
        "usedPoints" to usedPoints,
        "qrCode" to qrCode,
        "createdAt" to Timestamp(createdAt)
    )

    companion object {
        fun fromSnapshot(snapshot: DocumentSnapshot): RewardLog? {
            val data = snapshot.data ?: return null
            return RewardLog(
                id = data["id"] as? String ?: snapshot.id,
                userId = data["userId"] as? String ?: return null,
                rewardId = data["rewardId"] as? String ?: return null,
                rewardName = data["rewardName"] as? String ?: "",
                usedPoints = (data["usedPoints"] as? Number)?.toInt() ?: 0,
                qrCode = data["qrCode"] as? String ?: "",
                createdAt = data.dateOrNow("createdAt")
            )
        }
    }
}
