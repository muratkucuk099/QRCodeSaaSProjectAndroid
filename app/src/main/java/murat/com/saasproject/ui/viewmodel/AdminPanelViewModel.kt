package murat.com.saasproject.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import murat.com.saasproject.data.FirebaseRepository
import murat.com.saasproject.data.model.BusinessNotification
import murat.com.saasproject.data.model.Reward
import murat.com.saasproject.util.FcmTokenManager

enum class AdminPanelContent {
    REWARDS,
    NOTIFICATIONS
}

data class AdminPanelUiState(
    val selectedContent: AdminPanelContent = AdminPanelContent.REWARDS,
    val rewards: List<Reward> = emptyList(),
    val notifications: List<BusinessNotification> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val signedOut: Boolean = false
)

class AdminPanelViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(AdminPanelUiState())
    val uiState: StateFlow<AdminPanelUiState> = _uiState.asStateFlow()

    fun selectContent(content: AdminPanelContent) {
        _uiState.update { it.copy(selectedContent = content) }
        refresh(content)
    }

    fun refresh(content: AdminPanelContent = _uiState.value.selectedContent) {
        when (content) {
            AdminPanelContent.REWARDS -> fetchRewards()
            AdminPanelContent.NOTIFICATIONS -> fetchNotifications()
        }
    }

    fun fetchRewards() {
        val businessId = FirebaseRepository.currentUserId()
        if (businessId == null) {
            _uiState.update { it.copy(errorMessage = "Business bulunamadı") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val rewards = FirebaseRepository.fetchRewards(businessId)
            _uiState.update { it.copy(isLoading = false, rewards = rewards) }
        }
    }

    fun fetchNotifications() {
        val businessId = FirebaseRepository.currentUserId()
        if (businessId == null) {
            _uiState.update { it.copy(errorMessage = "Business bulunamadı") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            FirebaseRepository.fetchBusinessNotifications(businessId)
                .onSuccess { notifications ->
                    _uiState.update { it.copy(isLoading = false, notifications = notifications) }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = error.message)
                    }
                }
        }
    }

    fun deleteReward(reward: Reward) {
        viewModelScope.launch {
            FirebaseRepository.deleteReward(reward.rewardId, reward.businessId)
                .onSuccess {
                    _uiState.update { state ->
                        state.copy(rewards = state.rewards.filter { it.rewardId != reward.rewardId })
                    }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(errorMessage = error.message) }
                }
        }
    }

    fun deleteNotification(notification: BusinessNotification) {
        val businessId = FirebaseRepository.currentUserId() ?: return

        viewModelScope.launch {
            FirebaseRepository.deleteBusinessNotification(businessId, notification.id)
                .onSuccess {
                    _uiState.update { state ->
                        state.copy(
                            notifications = state.notifications.filter { it.id != notification.id }
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(errorMessage = error.message) }
                }
        }
    }

    fun signOut(context: Context) {
        viewModelScope.launch {
            FcmTokenManager.removeTokenForCurrentUser(context)
            FirebaseRepository.signOut()
            _uiState.update { it.copy(signedOut = true) }
        }
    }
}
