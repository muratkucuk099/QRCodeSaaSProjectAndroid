package murat.com.saasproject.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import murat.com.saasproject.data.FirebaseRepository
import murat.com.saasproject.data.model.UserNotification

data class UserNotificationsUiState(
    val notifications: List<UserNotification> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class UserNotificationsViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(UserNotificationsUiState())
    val uiState: StateFlow<UserNotificationsUiState> = _uiState.asStateFlow()

    fun loadNotifications() {
        val userId = FirebaseRepository.currentUserId()
        if (userId == null) {
            _uiState.update { it.copy(errorMessage = "Kullanıcı bulunamadı") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            FirebaseRepository.fetchUserNotifications(userId)
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
}
