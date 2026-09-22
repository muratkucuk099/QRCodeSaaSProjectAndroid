package murat.com.saasproject.ui.business

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import murat.com.saasproject.data.ServiceLocator
import murat.com.saasproject.data.model.BusinessNotification
import murat.com.saasproject.data.model.Reward
import murat.com.saasproject.data.model.RewardsScreenMode
import murat.com.saasproject.data.repository.AuthRepository
import murat.com.saasproject.data.repository.NotificationRepository
import murat.com.saasproject.data.repository.RewardRepository
import murat.com.saasproject.ui.common.Event
import murat.com.saasproject.ui.common.UiState
import murat.com.saasproject.ui.common.readableMessage

enum class AdminPanelContent { REWARDS, NOTIFICATIONS }

class AdminPanelViewModel(
    private val authRepository: AuthRepository = ServiceLocator.authRepository,
    private val rewardRepository: RewardRepository = ServiceLocator.rewardRepository,
    private val notificationRepository: NotificationRepository = ServiceLocator.notificationRepository
) : ViewModel() {

    var mode: RewardsScreenMode = RewardsScreenMode.MANAGE

    private val _content = MutableStateFlow(AdminPanelContent.REWARDS)
    val content: StateFlow<AdminPanelContent> = _content.asStateFlow()

    private val _rewards = MutableStateFlow<UiState<List<Reward>>>(UiState.Loading)
    val rewards: StateFlow<UiState<List<Reward>>> = _rewards.asStateFlow()

    private val _notifications = MutableStateFlow<UiState<List<BusinessNotification>>>(UiState.Loading)
    val notifications: StateFlow<UiState<List<BusinessNotification>>> = _notifications.asStateFlow()

    private val _events = MutableStateFlow<Event<String>?>(null)
    val events: StateFlow<Event<String>?> = _events.asStateFlow()

    fun selectContent(content: AdminPanelContent) {
        _content.value = content
        refresh(force = false)
    }

    fun refresh(force: Boolean) {
        when (_content.value) {
            AdminPanelContent.REWARDS -> loadRewards(force)
            AdminPanelContent.NOTIFICATIONS -> loadNotifications(force)
        }
    }

    fun deleteReward(reward: Reward) {
        viewModelScope.launch {
            rewardRepository.deleteReward(reward.rewardId, reward.businessId)
                .onSuccess { loadRewards(forceRefresh = true) }
                .onFailure { _events.value = Event(it.readableMessage().orEmpty()) }
        }
    }

    fun deleteNotification(notification: BusinessNotification) {
        val businessId = authRepository.currentUserId ?: return
        viewModelScope.launch {
            notificationRepository.deleteBusinessNotification(businessId, notification.id)
                .onSuccess { loadNotifications(forceRefresh = true) }
                .onFailure { _events.value = Event(it.readableMessage().orEmpty()) }
        }
    }

    private fun loadRewards(forceRefresh: Boolean) {
        val businessId = authRepository.currentUserId
        if (businessId == null) {
            _rewards.value = UiState.Error("")
            return
        }
        viewModelScope.launch {
            if (_rewards.value !is UiState.Success) _rewards.value = UiState.Loading
            rewardRepository.fetchRewards(businessId, forceRefresh)
                .onSuccess { list ->
                    _rewards.value = if (list.isEmpty()) UiState.Empty else UiState.Success(list)
                }
                .onFailure { _rewards.value = UiState.Error(it.readableMessage().orEmpty()) }
        }
    }

    private fun loadNotifications(forceRefresh: Boolean) {
        val businessId = authRepository.currentUserId
        if (businessId == null) {
            _notifications.value = UiState.Error("")
            return
        }
        viewModelScope.launch {
            if (_notifications.value !is UiState.Success) _notifications.value = UiState.Loading
            notificationRepository.fetchBusinessNotifications(businessId, forceRefresh)
                .onSuccess { list ->
                    _notifications.value = if (list.isEmpty()) UiState.Empty else UiState.Success(list)
                }
                .onFailure { _notifications.value = UiState.Error(it.readableMessage().orEmpty()) }
        }
    }
}
