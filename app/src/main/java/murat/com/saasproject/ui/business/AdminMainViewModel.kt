package murat.com.saasproject.ui.business

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import murat.com.saasproject.data.ServiceLocator
import murat.com.saasproject.data.model.QrPayload
import murat.com.saasproject.data.repository.AuthRepository
import murat.com.saasproject.data.repository.BusinessRepository
import murat.com.saasproject.data.repository.PointsRepository
import murat.com.saasproject.ui.common.Event
import murat.com.saasproject.ui.common.readableMessage
import java.util.UUID

sealed interface AdminMainEvent {
    data class QrReady(val json: String) : AdminMainEvent
    data object SubscriptionRequired : AdminMainEvent
    data class Failure(val message: String?) : AdminMainEvent
}

class AdminMainViewModel(
    private val authRepository: AuthRepository = ServiceLocator.authRepository,
    private val businessRepository: BusinessRepository = ServiceLocator.businessRepository,
    private val pointsRepository: PointsRepository = ServiceLocator.pointsRepository
) : ViewModel() {

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _events = MutableStateFlow<Event<AdminMainEvent>?>(null)
    val events: StateFlow<Event<AdminMainEvent>?> = _events.asStateFlow()

    fun generate(points: Int, rewardId: String?, rewardName: String?) {
        val businessId = authRepository.currentUserId
        if (businessId == null) {
            _events.value = Event(AdminMainEvent.Failure(null))
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            val access = businessRepository.ensureSubscriptionAccess(businessId, forceRefresh = true)
                .getOrElse {
                    _isLoading.value = false
                    _events.value = Event(AdminMainEvent.Failure(it.readableMessage()))
                    return@launch
                }

            if (!access.canUseApp) {
                _isLoading.value = false
                _events.value = Event(AdminMainEvent.SubscriptionRequired)
                return@launch
            }

            val qrCode = UUID.randomUUID().toString()
            val payload = QrPayload(
                qrCode = qrCode,
                businessId = access.business.id,
                points = points,
                rewardId = rewardId,
                rewardName = rewardName
            )
            val json = payload.toJson()

            pointsRepository.createActiveQrCode(
                businessId = access.business.id,
                qrCode = qrCode,
                points = points,
                rewardId = rewardId,
                rewardName = rewardName
            ).onSuccess {
                _events.value = Event(AdminMainEvent.QrReady(json))
            }.onFailure {
                _events.value = Event(AdminMainEvent.Failure(it.readableMessage()))
            }
            _isLoading.value = false
        }
    }
}
