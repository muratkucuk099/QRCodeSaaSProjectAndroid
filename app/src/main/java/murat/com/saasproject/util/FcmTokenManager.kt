package murat.com.saasproject.util

import android.content.Context
import android.provider.Settings
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await
import murat.com.saasproject.data.FirebaseRepository

object FcmTokenManager {

    fun deviceId(context: Context): String {
        return Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            ?: "unknown-device"
    }

    suspend fun syncTokenForCurrentUser(context: Context): Result<Unit> {
        val userId = FirebaseRepository.currentUserId()
            ?: return Result.failure(IllegalStateException("Oturum bulunamadı"))

        return runCatching {
            val token = FirebaseMessaging.getInstance().token.await()
            FirebaseRepository.syncFCMToken(token, deviceId(context)).getOrThrow()
        }
    }

    suspend fun removeTokenForCurrentUser(context: Context): Result<Unit> {
        if (FirebaseRepository.currentUserId() == null) {
            return Result.success(Unit)
        }

        return runCatching {
            FirebaseRepository.removeFCMToken(deviceId(context)).getOrThrow()
            FirebaseMessaging.getInstance().deleteToken().await()
        }
    }
}
