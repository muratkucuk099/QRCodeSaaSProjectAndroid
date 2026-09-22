package murat.com.saasproject.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import murat.com.saasproject.MainActivity
import murat.com.saasproject.R
import murat.com.saasproject.data.ServiceLocator
import murat.com.saasproject.util.DeviceId

/**
 * FCM mesajlarını karşılar ve token yenilemelerini Firestore'a yazar.
 *
 * Sunucu (`sendBusinessPush`) hem `notification` hem `data` bloğu gönderiyor:
 *   notification: { title, body }
 *   data:         { businessId, type: "business_notification" }
 *
 * Uygulama arka planda ise sistem `notification` bloğunu kendisi gösterir ve
 * [onMessageReceived] çağrılmaz. Ön planda ise burada elle bildirim oluşturuyoruz —
 * iOS'un `willPresent` ile banner göstermesinin karşılığı.
 */
class KumbaramMessagingService : FirebaseMessagingService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // Kullanıcı giriş yapmamışsa repository sessizce başarısız olur; giriş sonrası
        // PushTokenManager tekrar senkronlar.
        scope.launch {
            ServiceLocator.notificationRepository.saveFcmToken(
                token = token,
                deviceId = DeviceId.get(applicationContext)
            )
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        val title = message.notification?.title
            ?: message.data["title"]
            ?: getString(R.string.app_name)
        val body = message.notification?.body ?: message.data["body"] ?: return

        showNotification(title, body)
    }

    private fun showNotification(title: String, body: String) {
        // iOS bildirime dokununca deep link yok; yalnızca uygulamayı öne getirir.
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            NOTIFICATION_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .build()

        getSystemService(NotificationManager::class.java)
            ?.notify(System.currentTimeMillis().toInt(), notification)
    }

    companion object {
        const val CHANNEL_ID = "kumbaram_campaigns"
        private const val NOTIFICATION_REQUEST_CODE = 1001
    }
}
