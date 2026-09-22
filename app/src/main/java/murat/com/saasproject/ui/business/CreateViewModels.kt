package murat.com.saasproject.ui.business

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import murat.com.saasproject.data.ServiceLocator
import murat.com.saasproject.data.model.Reward
import murat.com.saasproject.data.repository.AuthRepository
import murat.com.saasproject.data.repository.NotificationRepository
import murat.com.saasproject.data.repository.RewardRepository
import murat.com.saasproject.ui.common.Event
import murat.com.saasproject.ui.common.readableMessage
import java.util.UUID

sealed interface CreateRewardEvent {
    data object Success : CreateRewardEvent
    data class Failure(val messageRes: Int?, val message: String?) : CreateRewardEvent
}

class CreateRewardViewModel(
    private val authRepository: AuthRepository = ServiceLocator.authRepository,
    private val rewardRepository: RewardRepository = ServiceLocator.rewardRepository
) : ViewModel() {

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _events = MutableStateFlow<Event<CreateRewardEvent>?>(null)
    val events: StateFlow<Event<CreateRewardEvent>?> = _events.asStateFlow()

    var selectedImage: Bitmap? = null

    fun create(name: String?, description: String?, pointsText: String?) {
        val businessId = authRepository.currentUserId
        if (businessId == null) {
            _events.value = Event(CreateRewardEvent.Failure(murat.com.saasproject.R.string.error_no_session, null))
            return
        }
        val trimmedName = name?.trim().orEmpty()
        val trimmedDescription = description?.trim().orEmpty()
        val points = pointsText?.trim()?.toIntOrNull()
        val image = selectedImage
        if (trimmedName.isEmpty() || trimmedDescription.isEmpty() || points == null || image == null) {
            _events.value = Event(
                CreateRewardEvent.Failure(murat.com.saasproject.R.string.create_reward_invalid_input, null)
            )
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            val rewardId = UUID.randomUUID().toString()
            val upload = rewardRepository.uploadRewardImage(image, businessId, rewardId)
            upload.onFailure {
                _isLoading.value = false
                _events.value = Event(CreateRewardEvent.Failure(null, it.readableMessage()))
                return@launch
            }
            val reward = Reward(
                rewardId = rewardId,
                businessId = businessId,
                name = trimmedName,
                requiredPoints = points,
                imageUrl = upload.getOrThrow(),
                description = trimmedDescription
            )
            rewardRepository.createReward(reward)
                .onSuccess {
                    selectedImage = null
                    _events.value = Event(CreateRewardEvent.Success)
                }
                .onFailure {
                    _events.value = Event(CreateRewardEvent.Failure(null, it.readableMessage()))
                }
            _isLoading.value = false
        }
    }
}

sealed interface CreateNotificationEvent {
    data class Success(val message: String) : CreateNotificationEvent
    data class Failure(val messageRes: Int?, val message: String?) : CreateNotificationEvent
}

class CreateNotificationViewModel(
    private val authRepository: AuthRepository = ServiceLocator.authRepository,
    private val notificationRepository: NotificationRepository = ServiceLocator.notificationRepository
) : ViewModel() {

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _events = MutableStateFlow<Event<CreateNotificationEvent>?>(null)
    val events: StateFlow<Event<CreateNotificationEvent>?> = _events.asStateFlow()

    fun send(title: String?, body: String?) {
        if (authRepository.currentUserId == null) {
            _events.value = Event(
                CreateNotificationEvent.Failure(murat.com.saasproject.R.string.error_no_session, null)
            )
            return
        }
        val trimmedTitle = title?.trim().orEmpty()
        val trimmedBody = body?.trim().orEmpty()
        if (trimmedTitle.isEmpty() || trimmedBody.isEmpty()) {
            _events.value = Event(
                CreateNotificationEvent.Failure(murat.com.saasproject.R.string.create_notification_invalid_input, null)
            )
            return
        }
        viewModelScope.launch {
            _isLoading.value = true
            notificationRepository.sendBusinessNotification(trimmedTitle, trimmedBody)
                .onSuccess { _events.value = Event(CreateNotificationEvent.Success(it.message)) }
                .onFailure { _events.value = Event(CreateNotificationEvent.Failure(null, it.readableMessage())) }
            _isLoading.value = false
        }
    }
}
