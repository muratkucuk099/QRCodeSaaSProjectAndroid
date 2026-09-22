package murat.com.saasproject

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings
import murat.com.saasproject.domain.config.AuthEmailConfig
import murat.com.saasproject.service.KumbaramMessagingService

class KumbaramApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
        FirebaseAuth.getInstance().setLanguageCode(AuthEmailConfig.PREFERRED_LANGUAGE_CODE)
        configureFirestore()
        createNotificationChannel()
    }

    /**
     * Firestore'un kalıcı disk cache'ini açar.
     *
     * iOS tarafında `FirebaseDataCache` verileri elle diske yazıyor. Android'de aynı amaca
     * SDK'nın kendi kalıcı cache'i ile ulaşıyoruz: uygulama yeniden açıldığında okumalar
     * önce diskten karşılanır, gereksiz ağ isteği yapılmaz.
     */
    private fun configureFirestore() {
        runCatching {
            FirebaseFirestore.getInstance().firestoreSettings =
                FirebaseFirestoreSettings.Builder()
                    .setLocalCacheSettings(PersistentCacheSettings.newBuilder().build())
                    .build()
        }
    }

    /**
     * Android 8+ bildirim kanalı. FCM bildirimleri bu kanala düşer; kanal yoksa
     * bildirimler sessizce yok sayılır.
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel = NotificationChannel(
            KumbaramMessagingService.CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = getString(R.string.notification_channel_description)
        }

        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }
}
