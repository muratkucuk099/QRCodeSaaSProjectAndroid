package murat.com.saasproject.service

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import murat.com.saasproject.util.FcmTokenManager

class SaasFirebaseMessagingService : FirebaseMessagingService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        serviceScope.launch {
            FcmTokenManager.syncTokenForCurrentUser(applicationContext)
                .onFailure { error ->
                    Log.w(TAG, "FCM token sync failed: ${error.message}")
                }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        Log.d(TAG, "Push received from: ${message.from}")
    }

    companion object {
        private const val TAG = "SaasFCMService"
    }
}
