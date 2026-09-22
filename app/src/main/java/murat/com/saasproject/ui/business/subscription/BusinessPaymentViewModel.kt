package murat.com.saasproject.ui.business.subscription

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import murat.com.saasproject.data.ServiceLocator
import murat.com.saasproject.data.model.AppPublicConfig
import murat.com.saasproject.data.model.BusinessSubscriptionAccess
import murat.com.saasproject.data.repository.AuthRepository
import murat.com.saasproject.data.repository.BusinessRepository
import murat.com.saasproject.ui.common.Event
import murat.com.saasproject.ui.common.readableMessage

data class PaymentUi(
    val access: BusinessSubscriptionAccess? = null,
    val config: AppPublicConfig? = null
)

sealed interface PaymentEvent {
    data object AccessGranted : PaymentEvent
    data class Failure(val message: String?) : PaymentEvent
}

class BusinessPaymentViewModel(
    private val authRepository: AuthRepository = ServiceLocator.authRepository,
    private val businessRepository: BusinessRepository = ServiceLocator.businessRepository
) : ViewModel() {

    private val _ui = MutableStateFlow(PaymentUi())
    val ui: StateFlow<PaymentUi> = _ui.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _events = MutableStateFlow<Event<PaymentEvent>?>(null)
    val events: StateFlow<Event<PaymentEvent>?> = _events.asStateFlow()

    fun load() {
        val businessId = authRepository.currentUserId ?: return
        viewModelScope.launch {
            _isLoading.value = true
            val access = businessRepository.ensureSubscriptionAccess(businessId).getOrNull()
            val config = businessRepository.fetchAppPublicConfig()
            _ui.value = PaymentUi(access, config)
            _isLoading.value = false
        }
    }

    fun reloadPublicConfig() {
        viewModelScope.launch {
            val config = businessRepository.fetchAppPublicConfig(forceRefresh = true)
            _ui.value = _ui.value.copy(config = config)
        }
    }

    /**
     * iOS `refreshAccess`: yalnızca daha önce erişim yokken erişim açılırsa
     * `onAccessGranted` yayınlanır.
     */
    fun refreshStatus() {
        val businessId = authRepository.currentUserId ?: return
        viewModelScope.launch {
            _isLoading.value = true
            val wasInactive = _ui.value.access?.canUseApp != true
            businessRepository.ensureSubscriptionAccess(businessId, forceRefresh = true)
                .onSuccess { access ->
                    val config = businessRepository.fetchAppPublicConfig()
                    _ui.value = PaymentUi(access, config)
                    if (access.canUseApp && wasInactive) {
                        _events.value = Event(PaymentEvent.AccessGranted)
                    }
                }
                .onFailure { _events.value = Event(PaymentEvent.Failure(it.readableMessage())) }
            _isLoading.value = false
        }
    }
}
