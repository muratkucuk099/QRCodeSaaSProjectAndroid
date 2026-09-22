package murat.com.saasproject.ui.mainadmin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import murat.com.saasproject.data.ServiceLocator
import murat.com.saasproject.data.model.Business
import murat.com.saasproject.data.repository.BusinessRepository
import murat.com.saasproject.ui.common.Event
import murat.com.saasproject.ui.common.UiState
import murat.com.saasproject.ui.common.readableMessage

class MainAdminViewModel(
    private val businessRepository: BusinessRepository = ServiceLocator.businessRepository
) : ViewModel() {

    private val _businesses = MutableStateFlow<UiState<List<Business>>>(UiState.Loading)
    val businesses: StateFlow<UiState<List<Business>>> = _businesses.asStateFlow()

    private val _inviteCode = MutableStateFlow<String?>(null)
    val inviteCode: StateFlow<String?> = _inviteCode.asStateFlow()

    private val _isCreating = MutableStateFlow(false)
    val isCreating: StateFlow<Boolean> = _isCreating.asStateFlow()

    private val _events = MutableStateFlow<Event<String>?>(null)
    val events: StateFlow<Event<String>?> = _events.asStateFlow()

    fun load() {
        viewModelScope.launch {
            if (_businesses.value !is UiState.Success) _businesses.value = UiState.Loading
            businessRepository.fetchAllBusinesses()
                .onSuccess { list ->
                    _businesses.value = if (list.isEmpty()) UiState.Empty else UiState.Success(list)
                }
                .onFailure { _businesses.value = UiState.Error(it.readableMessage().orEmpty()) }
        }
    }

    fun createInvite() {
        viewModelScope.launch {
            _isCreating.value = true
            businessRepository.createInviteCode()
                .onSuccess { _inviteCode.value = it }
                .onFailure { _events.value = Event(it.readableMessage().orEmpty()) }
            _isCreating.value = false
        }
    }

    fun replaceBusiness(business: Business) {
        val current = (_businesses.value as? UiState.Success)?.data ?: return
        _businesses.value = UiState.Success(current.map { if (it.id == business.id) business else it })
    }
}
