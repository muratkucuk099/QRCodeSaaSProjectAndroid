package murat.com.saasproject.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import murat.com.saasproject.data.ServiceLocator
import murat.com.saasproject.data.model.LoginResult
import murat.com.saasproject.data.repository.AuthRepository
import murat.com.saasproject.data.repository.BusinessRepository

/**
 * Uygulamanın hangi kökte olması gerektiğini anlatan durum.
 * iOS'ta bu karar `AppRootRouter` içinde imperatif olarak veriliyor.
 */
sealed interface SessionRoot {
    /** Rol henüz çözülmedi (Splash). */
    data object Undetermined : SessionRoot

    data object Login : SessionRoot

    data object Customer : SessionRoot

    data object MainAdmin : SessionRoot

    /** Abonelik erişimi geçerli işletme. */
    data object Business : SessionRoot

    /** Abonelik erişimi yok; işletme ödeme ekranına kilitlenir. */
    data object SubscriptionGate : SessionRoot
}

/**
 * Oturum ve rol durumunu tek yerde tutar; Activity ömrü boyunca yaşar.
 *
 * IOS davranışı: `AppRootRouter.startBusinessSubscriptionListener` işletme dokümanını
 * dinler ve abonelik değiştiğinde `window.rootViewController`'ı anında değiştirir.
 *
 * ANDROID karşılığı: aynı listener bir [StateFlow] besler, MainActivity bu akışı
 * gözleyip navigasyon köküne uygular. Böylece yapılandırma değişimi (ekran döndürme,
 * tema değişimi) sonrası durum kaybolmaz ve mantık view katmanından ayrık kalır.
 */
class SessionViewModel(
    private val authRepository: AuthRepository = ServiceLocator.authRepository,
    private val businessRepository: BusinessRepository = ServiceLocator.businessRepository
) : ViewModel() {

    private val _root = MutableStateFlow<SessionRoot>(SessionRoot.Undetermined)
    val root: StateFlow<SessionRoot> = _root.asStateFlow()

    /** Aktif abonelik dinleyicisi; rol değiştiğinde iptal edilir. */
    private var subscriptionJob: Job? = null

    /**
     * Kök henüz belirlenmediyse oturumu çözer. Process death sonrası Splash
     * `savedInstanceState` ile geri gelse bile kullanıcı splash'te kalmaz.
     */
    fun resolveSessionIfNeeded() {
        if (_root.value == SessionRoot.Undetermined) resolveSession()
    }

    /**
     * iOS `SplashViewController.checkUser()` karşılığı: kayıtlı oturum varsa rolü
     * çözer, yoksa giriş ekranına yönlendirir.
     */
    fun resolveSession() {
        val uid = authRepository.currentUserId
        if (uid == null) {
            applyRoot(SessionRoot.Login)
            return
        }

        viewModelScope.launch {
            authRepository.loginWithUid(uid)
                .onSuccess { onRoleResolved(it) }
                .onFailure { applyRoot(SessionRoot.Login) }
        }
    }

    /** Giriş/kayıt başarılı olduğunda UI katmanı tarafından çağrılır. */
    fun onRoleResolved(role: LoginResult) {
        when (role) {
            LoginResult.MAIN_ADMIN -> {
                stopSubscriptionMonitoring()
                applyRoot(SessionRoot.MainAdmin)
            }

            LoginResult.USER -> {
                stopSubscriptionMonitoring()
                applyRoot(SessionRoot.Customer)
            }

            LoginResult.SUB_ADMIN -> resolveBusinessRoot()
        }
    }

    /**
     * iOS `showBusinessRoot` karşılığı. Abonelik erişimi çözülemezse kullanıcı
     * giriş ekranına düşer — geçersiz durumda panele erişim açılmaz.
     */
    private fun resolveBusinessRoot() {
        val businessId = authRepository.currentUserId
        if (businessId == null) {
            applyRoot(SessionRoot.Login)
            return
        }

        viewModelScope.launch {
            businessRepository.ensureSubscriptionAccess(businessId)
                .onSuccess { access ->
                    applyRoot(if (access.canUseApp) SessionRoot.Business else SessionRoot.SubscriptionGate)
                    startSubscriptionMonitoring(businessId)
                }
                .onFailure { applyRoot(SessionRoot.Login) }
        }
    }

    /**
     * İşletme dokümanını dinler; ana yönetici aboneliği uzatır/iptal ederse kök ekran
     * kullanıcı hiçbir şey yapmadan güncellenir (iOS ile aynı davranış).
     */
    private fun startSubscriptionMonitoring(businessId: String) {
        subscriptionJob?.cancel()
        subscriptionJob = viewModelScope.launch {
            businessRepository.observeSubscription(businessId).collect { access ->
                applyRoot(if (access.canUseApp) SessionRoot.Business else SessionRoot.SubscriptionGate)
            }
        }
    }

    private fun stopSubscriptionMonitoring() {
        subscriptionJob?.cancel()
        subscriptionJob = null
        businessRepository.stopObservingSubscription()
    }

    /** Ödeme ekranındaki "Durumu Kontrol Et" aksiyonu — sunucudan zorla tazeler. */
    fun refreshBusinessAccess() {
        val businessId = authRepository.currentUserId ?: return
        viewModelScope.launch {
            businessRepository.ensureSubscriptionAccess(businessId, forceRefresh = true)
                .onSuccess { access ->
                    applyRoot(if (access.canUseApp) SessionRoot.Business else SessionRoot.SubscriptionGate)
                }
        }
    }

    fun signOut() {
        stopSubscriptionMonitoring()
        authRepository.signOut()
        authRepository.resetSessionState()
        applyRoot(SessionRoot.Login)
    }

    /** Hesap silindikten sonra oturum zaten kapalıdır; yalnızca kök sıfırlanır. */
    fun onAccountDeleted() {
        stopSubscriptionMonitoring()
        authRepository.resetSessionState()
        applyRoot(SessionRoot.Login)
    }

    private fun applyRoot(root: SessionRoot) {
        if (_root.value != root) _root.value = root
    }

    override fun onCleared() {
        stopSubscriptionMonitoring()
        super.onCleared()
    }
}
