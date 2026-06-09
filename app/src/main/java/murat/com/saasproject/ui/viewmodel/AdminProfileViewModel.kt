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
import murat.com.saasproject.data.model.BusinessProfileStats
import java.io.ByteArrayOutputStream

data class AdminProfileUiState(
    val business: Business? = null,
    val stats: BusinessProfileStats? = null,
    val isLoading: Boolean = false,
    val successMessage: String? = null,
    val errorMessage: String? = null
)

class AdminProfileViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(AdminProfileUiState())
    val uiState: StateFlow<AdminProfileUiState> = _uiState.asStateFlow()

    fun loadBusiness(loadStats: Boolean = false) {
        val uid = FirebaseRepository.currentUserId()
        if (uid == null) {
            _uiState.update { it.copy(errorMessage = "Kullanıcı bulunamadı") }
            return
        }

        viewModelScope.launch {
            FirebaseRepository.fetchBusiness(uid)
                .onSuccess { business ->
                    _uiState.update { it.copy(business = business) }
                    if (loadStats) {
                        loadReports(business.id)
                    }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(errorMessage = error.message) }
                }
        }
    }

    fun updateBusinessProfile(
        name: String,
        phone: String,
        logoBitmap: Bitmap?,
        businessId: String
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, successMessage = null) }

            if (logoBitmap != null) {
                val bytes = logoBitmap.toJpegBytes()
                FirebaseRepository.uploadBusinessLogo(bytes, businessId)
                    .onFailure { error ->
                        _uiState.update { it.copy(isLoading = false, errorMessage = error.message) }
                    }
                    .onSuccess { logoUrl ->
                        saveProfile(name, phone, logoUrl, businessId)
                    }
            } else {
                saveProfile(name, phone, null, businessId)
            }
        }
    }

    fun loadReports(businessId: String) {
        viewModelScope.launch {
            FirebaseRepository.fetchBusinessProfileStats(businessId)
                .onSuccess { stats ->
                    _uiState.update { it.copy(stats = stats) }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(errorMessage = error.message) }
                }
        }
    }

    private suspend fun saveProfile(
        name: String,
        phone: String,
        logoURL: String?,
        businessId: String
    ) {
        FirebaseRepository.updateBusinessProfile(
            businessId = businessId,
            name = name,
            phone = phone,
            logoURL = logoURL
        ).onSuccess {
            _uiState.update {
                it.copy(isLoading = false, successMessage = "Profil güncellendi")
            }
            loadBusiness()
        }.onFailure { error ->
            _uiState.update {
                it.copy(isLoading = false, errorMessage = error.message)
            }
        }
    }

    private fun Bitmap.toJpegBytes(): ByteArray {
        val stream = ByteArrayOutputStream()
        compress(Bitmap.CompressFormat.JPEG, 70, stream)
        return stream.toByteArray()
    }
}
