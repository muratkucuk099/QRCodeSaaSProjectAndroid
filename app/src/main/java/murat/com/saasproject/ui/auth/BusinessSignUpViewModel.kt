package murat.com.saasproject.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import murat.com.saasproject.R
import murat.com.saasproject.data.ServiceLocator
import murat.com.saasproject.data.model.Business
import murat.com.saasproject.data.repository.AuthRepository
import murat.com.saasproject.data.repository.BusinessRepository
import murat.com.saasproject.domain.validation.AuthEmailInput
import murat.com.saasproject.ui.common.AuthErrorMapper
import murat.com.saasproject.ui.common.Event
import murat.com.saasproject.ui.common.readableMessage
import murat.com.saasproject.util.Formatters

/**
 * iOS `AdminSignUpViewModel` karşılığı: işletme hesabı oluşturma.
 *
 * İşletme adı iOS'ta olduğu gibi büyük harfe çevrilerek kaydedilir; iki platformda
 * aynı doküman biçimi korunur.
 */
class BusinessSignUpViewModel(
    private val authRepository: AuthRepository = ServiceLocator.authRepository,
    private val businessRepository: BusinessRepository = ServiceLocator.businessRepository
) : ViewModel() {

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _events = MutableStateFlow<Event<SignUpEvent>?>(null)
    val events: StateFlow<Event<SignUpEvent>?> = _events.asStateFlow()

    fun signUp(
        name: String,
        businessType: String,
        phone: String,
        email: String,
        password: String,
        passwordConfirmation: String,
        hasAcceptedKvkk: Boolean
    ) {
        // iOS: `name.uppercased()`. Türkçe'ye özgü i/İ dönüşümü için tr locale kullanılır.
        val normalizedName = name.trim().uppercase(Formatters.TURKISH)
        val normalizedEmail = AuthEmailInput.normalize(email)
        val trimmedPhone = phone.trim()
        val trimmedType = businessType.trim()

        validate(
            name = normalizedName,
            phone = trimmedPhone,
            email = normalizedEmail,
            password = password,
            passwordConfirmation = passwordConfirmation,
            businessType = trimmedType,
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

                val business = Business.forRegistration(
                    id = uid,
                    name = normalizedName,
                    phone = trimmedPhone,
                    email = normalizedEmail,
                    businessType = trimmedType
                )

                businessRepository.saveBusiness(business)
                    .onSuccess { _events.value = Event(SignUpEvent.Registered) }
                    .onFailure { _events.value = Event(failureOf(it)) }
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun validate(
        name: String,
        phone: String,
        email: String,
        password: String,
        passwordConfirmation: String,
        businessType: String,
        hasAcceptedKvkk: Boolean
    ): SignUpEvent.Invalid? {
        val firstEmptyField = when {
            name.isEmpty() -> SignUpField.NAME
            businessType.isEmpty() -> SignUpField.BUSINESS_TYPE
            phone.isEmpty() -> SignUpField.PHONE
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
