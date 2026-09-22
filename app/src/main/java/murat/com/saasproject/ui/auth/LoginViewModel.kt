package murat.com.saasproject.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import murat.com.saasproject.R
import murat.com.saasproject.data.ServiceLocator
import murat.com.saasproject.data.model.GoogleSignInOutcome
import murat.com.saasproject.data.model.LoginResult
import murat.com.saasproject.data.remote.CloudFunctionException
import murat.com.saasproject.data.repository.AuthRepository
import murat.com.saasproject.data.repository.OAuthNeedsKvkkConsentException
import murat.com.saasproject.domain.validation.AuthEmailInput
import murat.com.saasproject.ui.common.AuthErrorMapper
import murat.com.saasproject.ui.common.Event
import murat.com.saasproject.ui.common.readableMessage

/**
 * Giriş ekranının tek seferlik olayları. Snackbar/dialog/navigasyon gibi
 * tekrar tetiklenmemesi gereken işler için [Event] ile sarılır.
 */
sealed interface LoginEvent {
    /** Rol çözüldü; SessionViewModel köke yerleştirecek. */
    data class Authenticated(val role: LoginResult) : LoginEvent

    /** Hata; [messageRes] varsa o metin, yoksa [message] / genel hata kullanılır. */
    data class Failure(val messageRes: Int? = null, val message: String? = null) : LoginEvent

    /** Alan doğrulama hatası (Snackbar yerine alan altında gösterilir). */
    data class ValidationFailure(val messageRes: Int) : LoginEvent

    /** Google ile giren yeni kullanıcı KVKK onayı vermeli. */
    data object NeedsKvkkConsent : LoginEvent

    /** Şifre sıfırlama e-postası gönderildi. */
    data class PasswordResetSent(val email: String) : LoginEvent


    /** Google ile giriş yapılandırılmamış. */
    data object GoogleUnavailable : LoginEvent
}

/**
 * iOS `AdminLoginViewModel` karşılığı.
 *
 * Doğrulama kuralları ve hata metinleri iOS ile birebir aynı tutuldu; yalnızca
 * metinler `strings.xml`'e taşındı.
 */
class LoginViewModel(
    private val authRepository: AuthRepository = ServiceLocator.authRepository
) : ViewModel() {

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _events = MutableStateFlow<Event<LoginEvent>?>(null)
    val events: StateFlow<Event<LoginEvent>?> = _events.asStateFlow()

    /**
     * Google ile gelen yeni kullanıcı KVKK ekranındayken `true`.
     * Politika metni açılınca oturum iptal edilmez; Login'e dönüşte diyalog yeniden gösterilir.
     */
    var isOAuthKvkkPending: Boolean = false
        private set

    // region E-posta / şifre

    fun login(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            emit(LoginEvent.ValidationFailure(R.string.login_empty_credentials))
            return
        }

        runWithLoading {
            authRepository.login(AuthEmailInput.normalize(email), password)
                .onSuccess { emit(LoginEvent.Authenticated(it)) }
                .onFailure { emit(failureOf(it)) }
        }
    }

    // endregion

    // region Şifre sıfırlama

    /**
     * iOS `validatePasswordResetEmail` ile aynı sıra: boşsa "e-posta girin",
     * biçimi hatalıysa "geçerli e-posta girin".
     */
    fun validateResetEmail(email: String): Int? {
        val normalized = AuthEmailInput.normalize(email)
        return when {
            normalized.isEmpty() -> R.string.password_reset_empty_email
            !AuthEmailInput.isPlausible(normalized) ->
                R.string.password_reset_invalid_email
            else -> null
        }
    }

    fun resetPassword(email: String) {
        validateResetEmail(email)?.let {
            emit(LoginEvent.ValidationFailure(it))
            return
        }

        val normalized = AuthEmailInput.normalize(email)
        runWithLoading {
            authRepository.sendPasswordReset(normalized)
                .onSuccess { emit(LoginEvent.PasswordResetSent(normalized)) }
                .onFailure { error ->
                    // 404: bu e-postayla kayıtlı hesap yok (iOS ile aynı ayrım).
                    val isUnknownAccount =
                        error is CloudFunctionException && error.statusCode == AuthRepository.HTTP_NOT_FOUND
                    emit(
                        if (isUnknownAccount) {
                            LoginEvent.ValidationFailure(
                                R.string.password_reset_user_not_found
                            )
                        } else {
                            failureOf(error)
                        }
                    )
                }
        }
    }

    // endregion


    // region Google

    fun onGoogleSignInResult(outcome: GoogleSignInOutcome) {
        when (outcome) {
            // Kullanıcı vazgeçti: iOS'ta da hata gösterilmiyor.
            GoogleSignInOutcome.Cancelled -> _isLoading.value = false

            GoogleSignInOutcome.Unavailable -> {
                _isLoading.value = false
                emit(LoginEvent.GoogleUnavailable)
            }

            is GoogleSignInOutcome.Failure -> {
                _isLoading.value = false
                emit(LoginEvent.Failure(messageRes = R.string.login_google_failed, message = outcome.message))
            }

            is GoogleSignInOutcome.Success -> runWithLoading {
                authRepository.signInWithCredential(outcome.credential, outcome.displayName)
                    .onSuccess { emit(LoginEvent.Authenticated(it)) }
                    .onFailure { handleOAuthFailure(it) }
            }
        }
    }

    fun setGoogleSignInStarted() {
        _isLoading.value = true
    }

    fun completeOAuthRegistrationAfterKvkkConsent() {
        isOAuthKvkkPending = false
        runWithLoading {
            authRepository.completeOAuthRegistrationAfterKvkkConsent()
                .onSuccess { emit(LoginEvent.Authenticated(it)) }
                .onFailure { emit(failureOf(it)) }
        }
    }

    fun cancelPendingOAuthRegistration() {
        isOAuthKvkkPending = false
        authRepository.cancelPendingOAuthRegistration()
    }

    private fun handleOAuthFailure(error: Throwable) {
        if (error is OAuthNeedsKvkkConsentException) {
            isOAuthKvkkPending = true
            emit(LoginEvent.NeedsKvkkConsent)
            return
        }
        emit(failureOf(error))
    }

    private fun failureOf(error: Throwable): LoginEvent.Failure =
        LoginEvent.Failure(
            messageRes = AuthErrorMapper.stringRes(error),
            message = error.readableMessage()
        )

    // endregion

    private fun runWithLoading(block: suspend () -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                block()
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun emit(event: LoginEvent) {
        _events.value = Event(event)
    }
}
