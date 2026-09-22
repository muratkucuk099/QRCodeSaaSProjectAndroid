package murat.com.saasproject.ui.customer.qr

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import murat.com.saasproject.data.ServiceLocator
import murat.com.saasproject.data.model.PointsError
import murat.com.saasproject.data.model.QrError
import murat.com.saasproject.data.model.QrPayload
import murat.com.saasproject.data.model.RedeemException
import murat.com.saasproject.data.model.RewardLog
import murat.com.saasproject.data.repository.AuthRepository
import murat.com.saasproject.data.repository.PointsRepository
import murat.com.saasproject.ui.common.Event
import murat.com.saasproject.ui.common.readableMessage
import java.util.Date

sealed interface QrScanEvent {
    data class Success(val newPoints: Int) : QrScanEvent
    data class Failure(val messageRes: Int?, val message: String?) : QrScanEvent
}

class QrScannerViewModel(
    private val authRepository: AuthRepository = ServiceLocator.authRepository,
    private val pointsRepository: PointsRepository = ServiceLocator.pointsRepository
) : ViewModel() {

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _events = MutableStateFlow<Event<QrScanEvent>?>(null)
    val events: StateFlow<Event<QrScanEvent>?> = _events.asStateFlow()

    private var processing = false

    fun process(payload: QrPayload) {
        if (processing) return
        val userId = authRepository.currentUserId
        if (userId == null) {
            _events.value = Event(QrScanEvent.Failure(murat.com.saasproject.R.string.error_user_not_found, null))
            return
        }
        processing = true
        _isLoading.value = true

        val rewardLog = if (payload.rewardId != null && payload.rewardName != null) {
            RewardLog(
                id = payload.qrCode,
                userId = userId,
                rewardId = payload.rewardId,
                rewardName = payload.rewardName,
                usedPoints = kotlin.math.abs(payload.points),
                qrCode = payload.qrCode,
                createdAt = Date()
            )
        } else {
            null
        }

        viewModelScope.launch {
            pointsRepository.redeemQrCode(payload, userId, rewardLog)
                .onSuccess { points ->
                    _events.value = Event(QrScanEvent.Success(points))
                }
                .onFailure { error ->
                    _events.value = Event(mapError(error))
                }
            _isLoading.value = false
            processing = false
        }
    }

    private fun mapError(error: Throwable): QrScanEvent.Failure = when (error) {
        is RedeemException.Qr -> when (error.error) {
            QrError.INVALID, QrError.MISMATCH ->
                QrScanEvent.Failure(murat.com.saasproject.R.string.qr_error_invalid, null)
            QrError.ALREADY_USED ->
                QrScanEvent.Failure(murat.com.saasproject.R.string.qr_error_already_used, null)
            QrError.EXPIRED ->
                QrScanEvent.Failure(murat.com.saasproject.R.string.qr_error_expired, null)
        }
        is RedeemException.Points -> when (error.error) {
            PointsError.NEGATIVE_POINTS ->
                QrScanEvent.Failure(murat.com.saasproject.R.string.qr_error_insufficient_points, null)
            PointsError.USER_NOT_FOUND ->
                QrScanEvent.Failure(murat.com.saasproject.R.string.error_user_not_found, null)
        }
        else -> QrScanEvent.Failure(null, error.readableMessage())
    }
}
