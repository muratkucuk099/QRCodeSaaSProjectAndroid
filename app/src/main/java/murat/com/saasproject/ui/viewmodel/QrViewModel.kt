package murat.com.saasproject.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import murat.com.saasproject.data.FirebaseRepository
import murat.com.saasproject.data.model.QRPayload
import murat.com.saasproject.data.model.RewardLog

enum class QrViewError {
    NEGATIVE_POINTS,
    USER_NOT_FOUND,
    UNKNOWN
}

data class QrUiState(
    val isProcessing: Boolean = false,
    val isLoading: Boolean = false,
    val successPoints: Int? = null,
    val error: QrViewError? = null,
    val errorMessage: String? = null
)

class QrViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(QrUiState())
    val uiState: StateFlow<QrUiState> = _uiState.asStateFlow()

    fun clearResult() {
        _uiState.update { it.copy(successPoints = null, error = null, errorMessage = null) }
    }

    fun processScannedQR(payload: QRPayload) {
        if (_uiState.value.isProcessing) return

        val currentUserId = FirebaseRepository.currentUserId()
        if (currentUserId == null) {
            _uiState.update { it.copy(error = QrViewError.USER_NOT_FOUND) }
            return
        }

        _uiState.update { it.copy(isProcessing = true, isLoading = true, error = null) }

        val rewardLog = if (payload.rewardId != null && payload.rewardName != null) {
            RewardLog(
                id = payload.qrCode,
                userId = currentUserId,
                rewardId = payload.rewardId,
                rewardName = payload.rewardName,
                usedPoints = kotlin.math.abs(payload.points),
                qrCode = payload.qrCode
            )
        } else {
            null
        }

        viewModelScope.launch {
            FirebaseRepository.updatePointsForUser(
                businessId = payload.businessId,
                userId = currentUserId,
                points = payload.points,
                rewardLog = rewardLog
            ).onSuccess { newPoints ->
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        isLoading = false,
                        successPoints = newPoints
                    )
                }
            }.onFailure { error ->
                val message = error.message.orEmpty()
                val qrError = when {
                    message.contains("Puan yetersiz", ignoreCase = true) -> QrViewError.NEGATIVE_POINTS
                    message.contains("Kullanıcı bulunamadı", ignoreCase = true) -> QrViewError.USER_NOT_FOUND
                    else -> QrViewError.UNKNOWN
                }
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        isLoading = false,
                        error = qrError,
                        errorMessage = message
                    )
                }
            }
        }
    }
}
