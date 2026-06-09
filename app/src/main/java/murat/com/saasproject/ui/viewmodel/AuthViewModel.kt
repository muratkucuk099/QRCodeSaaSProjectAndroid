package murat.com.saasproject.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import murat.com.saasproject.data.FirebaseRepository
import murat.com.saasproject.data.model.LoginResult

data class AuthUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val loginResult: LoginResult? = null,
    val inviteValidated: Boolean = false
)

class AuthViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun clearNavigation() {
        _uiState.update { it.copy(loginResult = null, inviteValidated = false) }
    }

    fun login(email: String, password: String) {
        val validationError = validateCredentials(email, password)
        if (validationError != null) {
            _uiState.update { it.copy(errorMessage = validationError) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            FirebaseRepository.login(email.trim(), password)
                .onSuccess { role ->
                    _uiState.update { it.copy(isLoading = false, loginResult = role) }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = error.message ?: "Giriş başarısız")
                    }
                }
        }
    }

    fun checkExistingSession() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            val uid = FirebaseRepository.currentUserId()
            if (uid == null) {
                _uiState.update { it.copy(isLoading = false, loginResult = null) }
                return@launch
            }

            FirebaseRepository.checkRole(uid)
                .onSuccess { role ->
                    _uiState.update { it.copy(isLoading = false, loginResult = role) }
                }
                .onFailure {
                    _uiState.update { it.copy(isLoading = false, loginResult = null) }
                }
        }
    }

    fun submitInviteCode(code: String) {
        val validationError = validateInviteCodeInput(code)
        if (validationError != null) {
            _uiState.update { it.copy(errorMessage = validationError) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            FirebaseRepository.validateInviteCode(code.trim())
                .onSuccess {
                    _uiState.update { it.copy(isLoading = false, inviteValidated = true) }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = error.message ?: "Davet kodu geçersiz")
                    }
                }
        }
    }

    fun signUpUser(name: String, email: String, password: String, onSuccess: () -> Unit) {
        val validationError = validateSignUp(name, email, password)
        if (validationError != null) {
            _uiState.update { it.copy(errorMessage = validationError) }
            return
        }

        val normalizedEmail = normalizeEmail(email)

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            FirebaseRepository.createUser(normalizedEmail, password)
                .onSuccess { uid ->
                    val user = murat.com.saasproject.data.model.UserModel(
                        id = uid,
                        name = name.trim(),
                        email = normalizedEmail
                    )
                    FirebaseRepository.saveUser(user)
                        .onSuccess {
                            _uiState.update { it.copy(isLoading = false, loginResult = LoginResult.USER) }
                            onSuccess()
                        }
                        .onFailure { error ->
                            _uiState.update {
                                it.copy(isLoading = false, errorMessage = error.message ?: "Kayıt başarısız")
                            }
                        }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = error.message ?: "Kayıt başarısız")
                    }
                }
        }
    }

    fun signUpAdmin(
        name: String,
        phone: String,
        email: String,
        password: String,
        businessType: String,
        onSuccess: () -> Unit
    ) {
        val validationError = validateAdminSignUp(name, phone, email, password, businessType)
        if (validationError != null) {
            _uiState.update { it.copy(errorMessage = validationError) }
            return
        }

        val normalizedEmail = normalizeEmail(email)
        val normalizedName = name.trim().uppercase()

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            FirebaseRepository.createUser(normalizedEmail, password)
                .onSuccess { uid ->
                    val business = murat.com.saasproject.data.model.Business(
                        id = uid,
                        name = normalizedName,
                        logoURL = "",
                        phone = phone.trim(),
                        email = normalizedEmail,
                        businessType = businessType.trim(),
                        isApproved = false
                    )
                    FirebaseRepository.saveBusiness(business)
                        .onSuccess {
                            _uiState.update { it.copy(isLoading = false, loginResult = LoginResult.SUB_ADMIN) }
                            onSuccess()
                        }
                        .onFailure { error ->
                            _uiState.update {
                                it.copy(isLoading = false, errorMessage = error.message ?: "Kayıt başarısız")
                            }
                        }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = error.message ?: "Kayıt başarısız")
                    }
                }
        }
    }

    companion object {
        fun validateCredentials(email: String, password: String): String? {
            if (email.isBlank() || password.isBlank()) {
                return "E-posta ve şifre boş olamaz"
            }
            return null
        }

        fun validateInviteCodeInput(code: String): String? {
            if (code.trim().isEmpty()) {
                return "Lütfen davet kodu giriniz."
            }
            return null
        }

        fun normalizeEmail(email: String): String {
            return email.trim().replace(" ", "").lowercase()
        }

        private fun validateSignUp(name: String, email: String, password: String): String? {
            if (name.isBlank() || normalizeEmail(email).isBlank() || password.isBlank()) {
                return "Tüm alanlar zorunlu"
            }
            return null
        }

        private fun validateAdminSignUp(
            name: String,
            phone: String,
            email: String,
            password: String,
            businessType: String
        ): String? {
            if (name.isBlank() || phone.isBlank() || normalizeEmail(email).isBlank() ||
                password.isBlank() || businessType.isBlank()
            ) {
                return "Tüm alanlar zorunlu"
            }
            return null
        }
    }
}
