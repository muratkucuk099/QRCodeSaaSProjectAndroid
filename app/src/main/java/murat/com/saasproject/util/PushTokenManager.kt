package murat.com.saasproject.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await
import murat.com.saasproject.data.ServiceLocator

/**
 * FCM token yaşam döngüsü. iOS `PushNotificationManager` karşılığı.
 *
 * IOS davranışı: `UNUserNotificationCenter` yetkisi istenir, APNs kaydı sonrası FCM token
 * Firestore'a yazılır.
 *
 * ANDROID karşılığı: Android 13+ (API 33) için `POST_NOTIFICATIONS` runtime izni gerekir;
 * izin verilmemişse token yine kaydedilir (bildirim gösterilemez ama uygulama içi bildirim
 * geçmişi çalışmaya devam eder). Bu, iOS'ta izin reddedilse bile Firestore'daki
 * `users/{uid}/notifications` kayıtlarının okunabilmesiyle aynı davranıştır.
 */
object PushTokenManager {

    /** Android 13 öncesinde bildirim izni derleme zamanında verilmiş sayılır. */
    fun isNotificationPermissionRequired(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    fun hasNotificationPermission(context: Context): Boolean {
        if (!isNotificationPermissionRequired()) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Giriş yapmış kullanıcı için token'ı alıp Firestore'a yazar.
     * Başarısızlık sessizce yutulur — bildirim kaydı kritik bir akışı bloklamamalı.
     */
    suspend fun syncTokenForCurrentUser(context: Context): Result<Unit> = runCatching {
        if (ServiceLocator.authRepository.currentUserId == null) return@runCatching

        val token = FirebaseMessaging.getInstance().token.await()
        ServiceLocator.notificationRepository
            .saveFcmToken(token = token, deviceId = DeviceId.get(context))
            .getOrThrow()
    }

    /** Çıkış öncesi çağrılmalı; aksi halde cihaz eski kullanıcının bildirimlerini almaya devam eder. */
    suspend fun removeTokenForCurrentUser(context: Context): Result<Unit> =
        removeToken(DeviceId.get(context))

    /**
     * Cihaz kimliği bilinen çağrılar için (ör. Context tutmayan ViewModel'lar).
     * Token silinemese bile çıkış akışı bloklanmaz; hata yalnızca rapor edilir.
     */
    suspend fun removeToken(deviceId: String): Result<Unit> = runCatching {
        ServiceLocator.notificationRepository.removeFcmToken(deviceId).getOrThrow()
        runCatching { FirebaseMessaging.getInstance().deleteToken().await() }
        Unit
    }
}
