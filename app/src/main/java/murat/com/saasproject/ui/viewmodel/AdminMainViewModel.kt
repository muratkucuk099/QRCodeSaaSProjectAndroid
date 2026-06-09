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
import murat.com.saasproject.data.model.Business
import murat.com.saasproject.data.model.QRPayload
import murat.com.saasproject.util.QrUtils
import java.util.UUID

data class AdminMainUiState(
    val business: Business? = null,
    val isLoading: Boolean = false,
    val isApproved: Boolean = false,
    val qrJson: String? = null,
    val qrBitmap: Bitmap? = null,
    val approvalRequired: Boolean = false,
    val errorMessage: String? = null
)

class AdminMainViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(AdminMainUiState())
    val uiState: StateFlow<AdminMainUiState> = _uiState.asStateFlow()

    fun loadBusiness() {
        val uid = FirebaseRepository.currentUserId()
        if (uid == null) {
            _uiState.update { it.copy(errorMessage = "Kullanıcı bulunamadı") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            FirebaseRepository.fetchBusiness(uid)
                .onSuccess { business ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            business = business,
                            isApproved = business.isApproved
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = error.message)
                    }
                }
        }
    }

    fun generateQRCode(points: Int, rewardId: String? = null, rewardName: String? = null) {
        val business = _uiState.value.business
        if (business == null) {
            _uiState.update { it.copy(errorMessage = "İşletme bilgisi yüklenmedi") }
            return
        }

        if (!business.isApproved) {
            _uiState.update { it.copy(approvalRequired = true) }
            return
        }

        val qrUUID = UUID.randomUUID().toString()
        val payload = QRPayload(
            qrCode = qrUUID,
            businessId = business.id,
            points = points,
            userId = null,
            rewardId = rewardId,
            rewardName = rewardName
        )
        val jsonString = QrUtils.encodePayload(payload)

        viewModelScope.launch {
            FirebaseRepository.createActiveQRCode(
                businessId = business.id,
                qrCode = qrUUID,
                points = points,
                rewardId = rewardId,
                rewardName = rewardName
            ).onSuccess {
                val bitmap = QrUtils.generateQrBitmap(jsonString)
                _uiState.update {
                    it.copy(qrJson = jsonString, qrBitmap = bitmap, approvalRequired = false)
                }
            }.onFailure { error ->
                _uiState.update { it.copy(errorMessage = error.message) }
            }
        }
    }

    fun clearQr() {
        _uiState.update { it.copy(qrJson = null, qrBitmap = null) }
    }

    fun dismissApprovalRequired() {
        _uiState.update { it.copy(approvalRequired = false) }
    }
}
