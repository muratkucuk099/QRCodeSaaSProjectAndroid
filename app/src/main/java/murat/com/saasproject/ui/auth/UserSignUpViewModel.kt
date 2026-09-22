package murat.com.saasproject.ui.auth

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import murat.com.saasproject.R
import murat.com.saasproject.data.ServiceLocator
import murat.com.saasproject.data.model.UserModel
import murat.com.saasproject.data.repository.AuthRepository
import murat.com.saasproject.data.repository.BusinessRepository
import murat.com.saasproject.domain.config.KvkkPolicy
import murat.com.saasproject.domain.validation.AuthEmailInput
import murat.com.saasproject.ui.common.AuthErrorMapper
import murat.com.saasproject.ui.common.Event
import murat.com.saasproject.ui.common.readableMessage
import java.util.Date

/** Kayıt ekranının doğrulama hatasının hangi alana ait olduğunu belirtir. */
enum class SignUpField { NAME, EMAIL, PASSWORD, PASSWORD_CONFIRM, PHONE, BUSINESS_TYPE, NONE }

sealed interface SignUpEvent {
    /** Kayıt tamamlandı; kullanıcı bilgilendirilip giriş ekranına döner. */
    data object Registered : SignUpEvent

    data class Failure(val messageRes: Int? = null, val message: String? = null) : SignUpEvent

    data class Invalid(@StringRes val messageRes: Int, val field: SignUpField) : SignUpEvent

    /**
     * Davet kodu doğrulandı ve Cloud Function içinde tüketildi;
     * işletme kaydı ekranı açılır.
     */
    data object InviteCodeAccepted : SignUpEvent
}

/**
 * iOS `UserSignUpViewModel` karşılığı.
 *
 * Doğrulama sırası iOS ile birebir aynı: önce boş alan kontrolü, sonra şifre eşleşmesi,
 * en son KVKK onayı. Böylece kullanıcı iki platformda aynı geri bildirimi alır.
 */
class UserSignUpViewModel(
    private val authRepository: AuthRepository = ServiceLocator.authRepository,
    private val businessRepository: BusinessRepository = ServiceLocator.businessRepository
) : ViewModel() {

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _events = MutableStateFlow<Event<SignUpEvent>?>(null)
    val events: StateFlow<Event<SignUpEvent>?> = _events.asStateFlow()

    fun signUp(
        name: String,
        email: String,
        password: String,
        passwordConfirmation: String,
        hasAcceptedKvkk: Boolean
    ) {
        val normalizedEmail = AuthEmailInput.normalize(email)
        val trimmedName = name.trim()

        validate(
            name = trimmedName,
            email = normalizedEmail,
            password = password,
            passwordConfirmation = passwordConfirmation,
            hasAcceptedKvkk = hasAcceptedKvkk
        )?.let {
            _events.value = Event(it)
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            try {
                val uid = authRepository.createUser(normalizedEmail, password).getOrElse { error ->
                    _events.value = Event(failureOf(error))
                    return@launch
                }

                val user = UserModel(
                    id = uid,
                    name = trimmedName,
                    email = normalizedEmail,
                    createdAt = Date(),
                    businesses = emptyList(),
                    kvkkAcceptedAt = Date(),
                    kvkkPolicyVersion = KvkkPolicy.VERSION
                )

                authRepository.saveUser(user)
                    .onSuccess { _events.value = Event(SignUpEvent.Registered) }
                    .onFailure { _events.value = Event(failureOf(it)) }
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Davet kodu Cloud Function ile doğrulanır — istemci `invite_codes` koleksiyonunu
     * okuyamaz (security rules reddeder). Kod geçerliyse işletme kaydına geçilir.
     */
    fun submitInviteCode(code: String) {
        val trimmed = code.trim()
        if (trimmed.isEmpty()) {
            _events.value = Event(SignUpEvent.Invalid(R.string.invite_code_empty, SignUpField.NONE))
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            try {
                businessRepository.validateInviteCode(trimmed)
                    .onSuccess { _events.value = Event(SignUpEvent.InviteCodeAccepted) }
                    .onFailure { _events.value = Event(failureOf(it)) }
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun validate(
        name: String,
        email: String,
        password: String,
        passwordConfirmation: String,
        hasAcceptedKvkk: Boolean
    ): SignUpEvent.Invalid? {
        val firstEmptyField = when {
            name.isEmpty() -> SignUpField.NAME
            email.isEmpty() -> SignUpField.EMAIL
            password.isEmpty() -> SignUpField.PASSWORD
            passwordConfirmation.isEmpty() -> SignUpField.PASSWORD_CONFIRM
            else -> null
        }

        if (firstEmptyField != null) {
            return SignUpEvent.Invalid(R.string.validation_all_fields_required, firstEmptyField)
        }

        if (password != passwordConfirmation) {
            return SignUpEvent.Invalid(
                R.string.validation_passwords_do_not_match,
                SignUpField.PASSWORD_CONFIRM
            )
        }

        if (!hasAcceptedKvkk) {
            return SignUpEvent.Invalid(R.string.kvkk_consent_required, SignUpField.NONE)
        }

        return null
    }

    private fun failureOf(error: Throwable): SignUpEvent.Failure =
        SignUpEvent.Failure(
            messageRes = AuthErrorMapper.stringRes(error),
            message = error.readableMessage()
        )
}
