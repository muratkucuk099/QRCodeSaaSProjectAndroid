package murat.com.saasproject.ui.viewmodel

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import murat.com.saasproject.data.FirebaseRepository
import murat.com.saasproject.data.model.Reward
import java.io.ByteArrayOutputStream
import java.util.UUID

enum class CreateRewardError {
    NOT_LOGGED_IN,
    INVALID_INPUT
}

data class CreateRewardUiState(
    val isLoading: Boolean = false,
    val success: Boolean = false,
    val errorMessage: String? = null
)

class CreateRewardViewModel : ViewModel() {

    private var selectedImage: Bitmap? = null

    private val _uiState = MutableStateFlow(CreateRewardUiState())
    val uiState: StateFlow<CreateRewardUiState> = _uiState.asStateFlow()

    fun setSelectedImage(bitmap: Bitmap?) {
        selectedImage = bitmap
    }

    fun createReward(name: String?, description: String?, pointsText: String?) {
        val businessId = FirebaseRepository.currentUserId()
        if (businessId == null) {
            _uiState.update { it.copy(errorMessage = CreateRewardError.NOT_LOGGED_IN.name) }
            return
        }

        val validation = validate(name, description, pointsText, selectedImage != null)
        if (validation == null) {
            _uiState.update { it.copy(errorMessage = CreateRewardError.INVALID_INPUT.name) }
            return
        }

        val image = selectedImage ?: return
        val rewardId = UUID.randomUUID().toString()

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, success = false) }

            val bytes = image.toJpegBytes()
            FirebaseRepository.uploadRewardImage(bytes, businessId, rewardId)
                .onFailure { error ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = error.message) }
                }
                .onSuccess { imageUrl ->
                    val reward = Reward(
                        rewardId = rewardId,
                        businessId = businessId,
                        name = name!!.trim(),
                        requiredPoints = validation,
                        imageUrl = imageUrl,
                        description = description!!.trim()
                    )
                    FirebaseRepository.createReward(reward)
                        .onSuccess {
                            selectedImage = null
                            _uiState.update { it.copy(isLoading = false, success = true) }
                        }
                        .onFailure { error ->
                            _uiState.update {
                                it.copy(isLoading = false, errorMessage = error.message)
                            }
                        }
                }
        }
    }

    fun resetSuccess() {
        _uiState.update { it.copy(success = false) }
    }

    companion object {
        fun validate(
            name: String?,
            description: String?,
            pointsText: String?,
            hasImage: Boolean
        ): Int? {
            if (name.isNullOrBlank() || description.isNullOrBlank() || pointsText.isNullOrBlank() || !hasImage) {
                return null
            }
            return pointsText.toIntOrNull()
        }
    }

    private fun Bitmap.toJpegBytes(): ByteArray {
        val stream = ByteArrayOutputStream()
        compress(Bitmap.CompressFormat.JPEG, 80, stream)
        return stream.toByteArray()
    }
}
