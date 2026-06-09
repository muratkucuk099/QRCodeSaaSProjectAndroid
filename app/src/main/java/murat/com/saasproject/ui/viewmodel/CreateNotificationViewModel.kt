package murat.com.saasproject.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import murat.com.saasproject.data.FirebaseRepository
import murat.com.saasproject.data.model.PushNotificationSendResult

enum class CreateNotificationError {
    NOT_LOGGED_IN,
    INVALID_INPUT
}

data class CreateNotificationUiState(
    val isLoading: Boolean = false,
    val result: PushNotificationSendResult? = null,
    val errorMessage: String? = null
)

class CreateNotificationViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(CreateNotificationUiState())
    val uiState: StateFlow<CreateNotificationUiState> = _uiState.asStateFlow()

    fun createNotification(title: String?, body: String?) {
        if (FirebaseRepository.currentUserId() == null) {
            _uiState.update { it.copy(errorMessage = "Oturum bulunamadı") }
            return
        }

        val validated = validateInput(title, body)
        if (validated == null) {
            _uiState.update { it.copy(errorMessage = "Başlık ve mesaj alanlarını doldurun") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, result = null) }
            FirebaseRepository.sendBusinessNotification(validated.first, validated.second)
                .onSuccess { result ->
                    _uiState.update { it.copy(isLoading = false, result = result) }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = error.message)
                    }
                }
        }
    }

    fun resetResult() {
        _uiState.update { it.copy(result = null) }
    }

    companion object {
        fun validateInput(title: String?, body: String?): Pair<String, String>? {
            val trimmedTitle = title?.trim().orEmpty()
            val trimmedBody = body?.trim().orEmpty()
            if (trimmedTitle.isEmpty() || trimmedBody.isEmpty()) return null
            return trimmedTitle to trimmedBody
        }
    }
}
