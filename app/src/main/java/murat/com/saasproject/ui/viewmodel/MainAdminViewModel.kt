package murat.com.saasproject.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import murat.com.saasproject.data.FirebaseRepository
import murat.com.saasproject.util.FcmTokenManager
import murat.com.saasproject.util.InviteCodeGenerator

data class MainAdminUiState(
    val inviteCode: String? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val signedOut: Boolean = false
)

class MainAdminViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(MainAdminUiState())
    val uiState: StateFlow<MainAdminUiState> = _uiState.asStateFlow()

    fun createInviteCode() {
        val code = InviteCodeGenerator.generate()

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            FirebaseRepository.saveInviteCode(code)
                .onSuccess {
                    _uiState.update { it.copy(isLoading = false, inviteCode = code) }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = error.message)
                    }
                }
        }
    }

    fun signOut(context: Context) {
        viewModelScope.launch {
            FcmTokenManager.removeTokenForCurrentUser(context)
            FirebaseRepository.signOut()
            _uiState.update { it.copy(signedOut = true) }
        }
    }
}
