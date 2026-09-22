package murat.com.saasproject.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import murat.com.saasproject.data.ServiceLocator
import murat.com.saasproject.data.repository.AccountDeletionException
import murat.com.saasproject.data.repository.AccountDeletionRepository
import murat.com.saasproject.data.repository.AuthRepository
import murat.com.saasproject.ui.common.Event
import murat.com.saasproject.ui.common.readableMessage
import murat.com.saasproject.util.PushTokenManager

sealed interface AccountMenuEvent {
    /** Oturum kapatıldı; kök giriş ekranına döner. */
    data object SignedOut : AccountMenuEvent

    /** Hesap ve ilişkili veriler silindi. */
    data object AccountDeleted : AccountMenuEvent

    /** Yeniden giriş gerekiyor (Firebase `RequiresRecentLogin`). */
    data object RequiresRecentLogin : AccountMenuEvent

    /** Ana yönetici hesabı silinemez. */
    data object MainAdminNotDeletable : AccountMenuEvent

    data class Failure(val message: String?) : AccountMenuEvent
}

/**
 * Hesap menüsü işlemleri: çıkış ve hesap silme (KVKK unutulma hakkı).
 *
 * Çıkışta cihazın FCM token'ı sunucudan kaldırılır; aksi halde kullanıcı çıkış
 * yaptıktan sonra da bildirim almaya devam eder.
 */
class AccountMenuViewModel(
    private val authRepository: AuthRepository = ServiceLocator.authRepository,
    private val accountDeletionRepository: AccountDeletionRepository =
        ServiceLocator.accountDeletionRepository
) : ViewModel() {

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _events = MutableStateFlow<Event<AccountMenuEvent>?>(null)
    val events: StateFlow<Event<AccountMenuEvent>?> = _events.asStateFlow()

    val currentUserEmail: String? get() = authRepository.currentUserEmail

    /**
     * @param deviceId bu cihazın FCM kaydını sunucudan kaldırmak için; Context tutmamak
     *   adına view katmanından geçirilir.
     */
    fun signOut(deviceId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // Token temizliği başarısız olsa bile çıkış engellenmez.
                PushTokenManager.removeToken(deviceId)
                _events.value = Event(AccountMenuEvent.SignedOut)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun deleteAccount() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                accountDeletionRepository.deleteAccount()
                    .onSuccess { _events.value = Event(AccountMenuEvent.AccountDeleted) }
                    .onFailure { error ->
                        _events.value = Event(
                            when (error) {
                                AccountDeletionException.MainAdminNotDeletable ->
                                    AccountMenuEvent.MainAdminNotDeletable

                                AccountDeletionException.RequiresRecentLogin ->
                                    AccountMenuEvent.RequiresRecentLogin

                                else -> AccountMenuEvent.Failure(error.readableMessage())
                            }
                        )
                    }
            } finally {
                _isLoading.value = false
            }
        }
    }
}
