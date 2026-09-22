package murat.com.saasproject.ui.customer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import murat.com.saasproject.data.ServiceLocator
import murat.com.saasproject.data.model.Business
import murat.com.saasproject.data.model.Reward
import murat.com.saasproject.data.repository.BusinessRepository
import murat.com.saasproject.data.repository.RewardRepository
import murat.com.saasproject.ui.common.UiState
import murat.com.saasproject.ui.common.readableMessage

class CustomerRewardListViewModel(
    private val rewardRepository: RewardRepository = ServiceLocator.rewardRepository,
    private val businessRepository: BusinessRepository = ServiceLocator.businessRepository
) : ViewModel() {

    private val _state = MutableStateFlow<UiState<List<Reward>>>(UiState.Loading)
    val state: StateFlow<UiState<List<Reward>>> = _state.asStateFlow()

    private val _business = MutableStateFlow<Business?>(null)
    val business: StateFlow<Business?> = _business.asStateFlow()

    fun load(businessId: String) {
        viewModelScope.launch {
            businessRepository.fetchBusiness(businessId)
                .onSuccess { _business.value = it }
            _state.value = UiState.Loading
            rewardRepository.fetchRewards(businessId)
                .onSuccess { list ->
                    _state.value = if (list.isEmpty()) UiState.Empty else UiState.Success(list)
                }
                .onFailure { _state.value = UiState.Error(it.readableMessage().orEmpty()) }
        }
    }
}
