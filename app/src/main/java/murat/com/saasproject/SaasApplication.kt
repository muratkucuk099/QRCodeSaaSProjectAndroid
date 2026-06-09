package murat.com.saasproject

import android.app.Application
import com.google.firebase.FirebaseApp

class SaasApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (FirebaseApp.getApps(this).isEmpty()) {
            FirebaseApp.initializeApp(this)
        }
    }
}
