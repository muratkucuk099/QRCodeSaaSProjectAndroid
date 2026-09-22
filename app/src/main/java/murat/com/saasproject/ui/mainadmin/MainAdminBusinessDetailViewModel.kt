package murat.com.saasproject.ui.mainadmin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import murat.com.saasproject.data.ServiceLocator
import murat.com.saasproject.data.model.Business
import murat.com.saasproject.data.model.BusinessSubscriptionAccess
import murat.com.saasproject.data.repository.BusinessRepository
import murat.com.saasproject.domain.config.MainAdminConfig
import murat.com.saasproject.ui.common.Event
import murat.com.saasproject.ui.common.readableMessage

data class MainAdminDetailUi(
    val business: Business? = null,
    val access: BusinessSubscriptionAccess? = null
)

class MainAdminBusinessDetailViewModel(
    private val businessRepository: BusinessRepository = ServiceLocator.businessRepository
) : ViewModel() {

    private val _ui = MutableStateFlow(MainAdminDetailUi())
    val ui: StateFlow<MainAdminDetailUi> = _ui.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _events = MutableStateFlow<Event<String>?>(null)
    val events: StateFlow<Event<String>?> = _events.asStateFlow()

    fun load(businessId: String) {
        viewModelScope.launch {
            _isLoading.value = _ui.value.business == null
            businessRepository.fetchBusiness(businessId, forceRefresh = true)
                .onSuccess { publish(it) }
                .onFailure { _events.value = Event(it.readableMessage().orEmpty()) }
            _isLoading.value = false
        }
    }

    fun extendSubscription() {
        val business = _ui.value.business ?: return
        applySubscriptionUpdate(
            expiry = business.subscriptionExpiryByAddingDays(MainAdminConfig.SUBSCRIPTION_EXTEND_DAYS),
            isActive = true
        )
    }

    fun shortenSubscription() {
        val business = _ui.value.business ?: return
        if (business.subscriptionExpiresAt == null) {
            _events.value = Event("Abonelik tarihi tanımlı değil")
            return
        }
        val newExpiry = business.subscriptionExpiryByAddingDays(-MainAdminConfig.SUBSCRIPTION_SHORTEN_DAYS)
        applySubscriptionUpdate(
            expiry = newExpiry,
            isActive = business.withSubscriptionExpiresAt(newExpiry).isSubscriptionValid
        )
    }

    fun cancelSubscription() {
        val business = _ui.value.business ?: return
        applySubscriptionUpdate(
            expiry = business.cancelledSubscriptionExpiry(),
            isActive = false
        )
    }

    private fun applySubscriptionUpdate(expiry: java.util.Date, isActive: Boolean) {
        val businessId = _ui.value.business?.id ?: return
        viewModelScope.launch {
            _isLoading.value = true
            businessRepository.updateSubscription(businessId, expiry, isActive)
                .onSuccess { publish(it) }
                .onFailure { _events.value = Event(it.readableMessage().orEmpty()) }
            _isLoading.value = false
        }
    }

    private fun publish(business: Business) {
        _ui.value = MainAdminDetailUi(
            business = business,
            access = BusinessSubscriptionAccess.from(business)
        )
    }
}
