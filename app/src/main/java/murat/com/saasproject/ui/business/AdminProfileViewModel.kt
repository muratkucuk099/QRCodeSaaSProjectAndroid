package murat.com.saasproject.ui.business

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import murat.com.saasproject.data.ServiceLocator
import murat.com.saasproject.data.model.Business
import murat.com.saasproject.data.model.BusinessProfileStats
import murat.com.saasproject.data.model.BusinessSubscriptionAccess
import murat.com.saasproject.data.repository.AuthRepository
import murat.com.saasproject.data.repository.BusinessRepository
import murat.com.saasproject.data.repository.StatsRepository
import murat.com.saasproject.ui.common.Event
import murat.com.saasproject.ui.common.readableMessage

data class AdminProfileUi(
    val business: Business? = null,
    val access: BusinessSubscriptionAccess? = null,
    val stats: BusinessProfileStats = BusinessProfileStats.EMPTY
)

class AdminProfileViewModel(
    private val authRepository: AuthRepository = ServiceLocator.authRepository,
    private val businessRepository: BusinessRepository = ServiceLocator.businessRepository,
    private val statsRepository: StatsRepository = ServiceLocator.statsRepository
) : ViewModel() {

    private val _ui = MutableStateFlow(AdminProfileUi())
    val ui: StateFlow<AdminProfileUi> = _ui.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _events = MutableStateFlow<Event<String>?>(null)
    val events: StateFlow<Event<String>?> = _events.asStateFlow()

    var pendingLogo: Bitmap? = null

    fun load(forceRefresh: Boolean = false) {
        val businessId = authRepository.currentUserId ?: return
        viewModelScope.launch {
            if (!forceRefresh) _isLoading.value = _ui.value.business == null
            else _isRefreshing.value = true

            businessRepository.fetchBusiness(businessId, forceRefresh)
                .onSuccess { business ->
                    val access = BusinessSubscriptionAccess.from(business)
                    _ui.value = _ui.value.copy(business = business, access = access)
                }
                .onFailure { _events.value = Event(it.readableMessage().orEmpty()) }

            statsRepository.fetchBusinessProfileStats(businessId, forceRefresh)
                .onSuccess { _ui.value = _ui.value.copy(stats = it) }
                .onFailure { _events.value = Event(it.readableMessage().orEmpty()) }

            _isLoading.value = false
            _isRefreshing.value = false
        }
    }

    fun save(name: String, phone: String, rules: String?) {
        val businessId = authRepository.currentUserId ?: return
        viewModelScope.launch {
            _isLoading.value = true
            val logoUrl = pendingLogo?.let { bitmap ->
                businessRepository.uploadBusinessLogo(bitmap, businessId).getOrElse {
                    _isLoading.value = false
                    _events.value = Event(it.readableMessage().orEmpty())
                    return@launch
                }
            }
            businessRepository.updateBusinessProfile(
                businessId = businessId,
                name = name,
                phone = phone,
                logoURL = logoUrl,
                pointEarningRules = rules
            ).onSuccess {
                pendingLogo = null
                load(forceRefresh = true)
            }.onFailure {
                _events.value = Event(it.readableMessage().orEmpty())
            }
            _isLoading.value = false
        }
    }
}
