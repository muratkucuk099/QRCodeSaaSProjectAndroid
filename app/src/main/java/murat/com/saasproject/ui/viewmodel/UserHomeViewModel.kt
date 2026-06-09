package murat.com.saasproject.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import murat.com.saasproject.data.FirebaseRepository
import murat.com.saasproject.data.model.Business
import murat.com.saasproject.data.model.UserBusiness

enum class UserHomeSection(val title: String, val emptyMessage: String) {
    MY_BUSINESSES("İşletmelerim", "Henüz kayıtlı olduğun bir işletme yok. QR kod okutarak puan kazanabilirsin."),
    ALL_BUSINESSES("Tüm İşletmeler", "Şu an listelenecek onaylı işletme yok.")
}

data class UserHomeUiState(
    val myBusinesses: List<UserBusiness> = emptyList(),
    val discoverBusinesses: List<Business> = emptyList(),
    val businessDetails: Map<String, Business> = emptyMap(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class UserHomeViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(UserHomeUiState())
    val uiState: StateFlow<UserHomeUiState> = _uiState.asStateFlow()

    init {
        startListening()
    }

    fun startListening() {
        loadApprovedBusinesses()
        viewModelScope.launch {
            FirebaseRepository.startUserBusinessesListener().collect { businesses ->
                _uiState.update { state ->
                    state.copy(
                        myBusinesses = businesses,
                        discoverBusinesses = refreshDiscover(cachedApproved, businesses)
                    )
                }
                fetchMissingDetails(businesses.map { it.businessId })
            }
        }
    }

    private var cachedApproved: List<Business> = emptyList()

    private fun loadApprovedBusinesses() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            FirebaseRepository.fetchApprovedBusinesses()
                .onSuccess { businesses ->
                    cachedApproved = businesses
                    val details = businesses.associateBy { it.id }
                    _uiState.update { state ->
                        state.copy(
                            isLoading = false,
                            businessDetails = state.businessDetails + details,
                            discoverBusinesses = refreshDiscover(businesses, state.myBusinesses)
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = error.message)
                    }
                }
        }
    }

    private fun fetchMissingDetails(businessIds: List<String>) {
        val missing = businessIds.filter { _uiState.value.businessDetails[it] == null }
        if (missing.isEmpty()) return

        viewModelScope.launch {
            for (businessId in missing) {
                FirebaseRepository.fetchBusiness(businessId)
                    .onSuccess { business ->
                        _uiState.update { state ->
                            state.copy(businessDetails = state.businessDetails + (business.id to business))
                        }
                    }
            }
        }
    }

    private fun refreshDiscover(
        allApproved: List<Business>,
        myBusinesses: List<UserBusiness>
    ): List<Business> {
        val enrolledIds = myBusinesses.map { it.businessId }.toSet()
        return allApproved.filter { it.id !in enrolledIds }
    }

    fun getBusinessInfo(businessId: String): Business? {
        val state = _uiState.value
        return state.businessDetails[businessId]
            ?: state.discoverBusinesses.firstOrNull { it.id == businessId }
            ?: cachedApproved.firstOrNull { it.id == businessId }
    }

    fun pointsFor(businessId: String): Int {
        return _uiState.value.myBusinesses.firstOrNull { it.businessId == businessId }?.points ?: 0
    }

    override fun onCleared() {
        FirebaseRepository.stopUserBusinessesListener()
        super.onCleared()
    }
}
