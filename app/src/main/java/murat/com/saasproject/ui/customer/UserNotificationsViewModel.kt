package murat.com.saasproject.ui.customer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import murat.com.saasproject.data.ServiceLocator
import murat.com.saasproject.data.model.UserNotification
import murat.com.saasproject.data.repository.AuthRepository
import murat.com.saasproject.data.repository.NotificationRepository
import murat.com.saasproject.ui.common.UiState
import murat.com.saasproject.ui.common.readableMessage

class UserNotificationsViewModel(
    private val authRepository: AuthRepository = ServiceLocator.authRepository,
    private val notificationRepository: NotificationRepository = ServiceLocator.notificationRepository
) : ViewModel() {

    private val _state = MutableStateFlow<UiState<List<UserNotification>>>(UiState.Loading)
    val state: StateFlow<UiState<List<UserNotification>>> = _state.asStateFlow()

    fun load(fallbackBusinessName: String, forceRefresh: Boolean = false) {
        val userId = authRepository.currentUserId
        if (userId == null) {
            _state.value = UiState.Error("")
            return
        }
        if (!forceRefresh && _state.value is UiState.Success) return

        viewModelScope.launch {
            if (_state.value !is UiState.Success) _state.value = UiState.Loading
            notificationRepository.fetchUserNotifications(userId, fallbackBusinessName, forceRefresh)
                .onSuccess { list ->
                    _state.value = if (list.isEmpty()) UiState.Empty else UiState.Success(list)
                }
                .onFailure { _state.value = UiState.Error(it.readableMessage().orEmpty()) }
        }
    }
}
