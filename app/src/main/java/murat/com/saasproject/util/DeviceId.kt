package murat.com.saasproject.util

import android.content.Context
import androidx.core.content.edit
import java.util.UUID

/**
 * FCM token'larını cihaz başına ayırt etmek için kalıcı kimlik.
 *
 * IOS davranışı: `UIDevice.identifierForVendor` (uygulama kaldırılınca sıfırlanır).
 * ANDROID karşılığı: `SharedPreferences`'ta saklanan UUID. `ANDROID_ID` bilinçli olarak
 * kullanılmadı — kalıcı donanım kimliği olduğu için gereksiz bir gizlilik yükü getirir ve
 * uygulama kaldırıldığında sıfırlanmaz.
 */
object DeviceId {

    private const val PREFS_NAME = "kumbaram_device"
    private const val KEY_DEVICE_ID = "push_device_id"

    fun get(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.getString(KEY_DEVICE_ID, null)?.let { return it }

        return UUID.randomUUID().toString().also { generated ->
            prefs.edit { putString(KEY_DEVICE_ID, generated) }
        }
    }
}
