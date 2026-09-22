package murat.com.saasproject

import androidx.navigation.NavController
import androidx.navigation.NavOptions
import murat.com.saasproject.ui.SessionRoot

/**
 * Kök yönlendirme. iOS `AppRootRouter.transition(to:)` karşılığı.
 *
 * IOS davranışı: rol belirlendiğinde `window.rootViewController` cross-dissolve ile
 * değiştirilir; navigasyon geçmişi tamamen sıfırlanır.
 *
 * ANDROID karşılığı: `popUpTo(graph, inclusive = true)` ile geçmiş temizlenerek hedefe
 * gidilir. Böylece kullanıcı giriş yaptıktan sonra geri tuşuyla login ekranına dönemez;
 * kökteyken geri tuşu uygulamadan çıkar — Android'de beklenen davranış.
 */
object AppRouter {

    /**
     * [root] durumunu navigasyon hedefine çevirir.
     * @return hedef id, ya da rol henüz çözülmediyse null (Splash'te kalınır).
     */
    internal fun destinationFor(root: SessionRoot): Int? = when (root) {
        SessionRoot.Undetermined -> null
        SessionRoot.Login -> R.id.loginFragment
        SessionRoot.Customer -> R.id.customerHostFragment
        SessionRoot.MainAdmin -> R.id.mainAdminFragment
        SessionRoot.Business -> R.id.businessHostFragment
        SessionRoot.SubscriptionGate -> R.id.subscriptionGateFragment
    }

    /**
     * Kökü uygular. Zaten o kökteyse hiçbir şey yapmaz; böylece abonelik listener'ı
     * aynı durumu tekrar yayınladığında ekran yeniden oluşturulmaz.
     */
    fun apply(navController: NavController, root: SessionRoot) {
        val destinationId = destinationFor(root) ?: return
        if (navController.currentDestination?.id == destinationId) return

        val options = NavOptions.Builder()
            .setPopUpTo(navController.graph.id, /* inclusive = */ true)
            .setLaunchSingleTop(true)
            .build()
        navController.navigate(destinationId, null, options)
    }
}
