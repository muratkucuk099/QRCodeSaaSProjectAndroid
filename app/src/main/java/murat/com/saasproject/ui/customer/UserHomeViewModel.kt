package murat.com.saasproject.ui.customer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import murat.com.saasproject.data.ServiceLocator
import murat.com.saasproject.data.model.Business
import murat.com.saasproject.data.model.UserBusiness
import murat.com.saasproject.data.repository.BusinessRepository
import murat.com.saasproject.data.repository.UserRepository
import murat.com.saasproject.ui.common.Event
import murat.com.saasproject.ui.common.readableMessage
import java.util.Date

data class UserHomeContent(
    val myBusinesses: List<UserHomeRow.BusinessRow> = emptyList(),
    val discoverBusinesses: List<UserHomeRow.BusinessRow> = emptyList()
)

class UserHomeViewModel(
    private val userRepository: UserRepository = ServiceLocator.userRepository,
    private val businessRepository: BusinessRepository = ServiceLocator.businessRepository
) : ViewModel() {

    private val _content = MutableStateFlow(UserHomeContent())
    val content: StateFlow<UserHomeContent> = _content.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _events = MutableStateFlow<Event<String>?>(null)
    val events: StateFlow<Event<String>?> = _events.asStateFlow()

    private var myBusinesses: List<UserBusiness> = emptyList()
    private var details: Map<String, Business> = emptyMap()
    private var activeBusinesses: List<Business> = emptyList()
    private var started = false

    fun start() {
        if (started) return
        started = true
        viewModelScope.launch {
            loadActive(forceRefresh = false)
            userRepository.observeUserBusinesses().collect { businesses ->
                myBusinesses = businesses
                fetchMissingDetails(businesses.map { it.businessId })
                publish()
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            loadActive(forceRefresh = true)
            _isRefreshing.value = false
        }
    }

    private suspend fun loadActive(forceRefresh: Boolean) {
        businessRepository.fetchActiveBusinesses(forceRefresh)
            .onSuccess { list ->
                activeBusinesses = list
                details = details + list.associateBy { it.id }
                publish()
            }
            .onFailure { _events.value = Event(it.readableMessage().orEmpty()) }
    }

    private suspend fun fetchMissingDetails(ids: List<String>) {
        val missing = ids.filter { it !in details }
        if (missing.isEmpty()) {
            publish()
            return
        }
        missing.forEach { id ->
            businessRepository.fetchBusiness(id)
                .onSuccess { details = details + (it.id to it) }
        }
        publish()
    }

    private fun publish() {
        val myIds = myBusinesses.map { it.businessId }.toSet()
        val myRows = myBusinesses.map { ub ->
            val business = details[ub.businessId] ?: placeholderBusiness(ub.businessId)
            UserHomeRow.BusinessRow(business, ub.points, enrolled = true)
        }
        val discover = activeBusinesses
            .filter { it.id !in myIds }
            .map { UserHomeRow.BusinessRow(it, points = null, enrolled = false) }
        _content.value = UserHomeContent(myRows, discover)
    }

    private fun placeholderBusiness(id: String) = Business(
        id = id,
        name = "",
        logoURL = null,
        phone = "",
        email = "",
        businessType = "",
        createdAt = Date()
    )

    override fun onCleared() {
        userRepository.stopObservingUserBusinesses()
        super.onCleared()
    }
}
