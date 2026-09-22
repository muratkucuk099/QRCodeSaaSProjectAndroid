package murat.com.saasproject.ui.common

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthException
import murat.com.saasproject.R
import murat.com.saasproject.data.repository.UnauthorizedUserException

/**
 * Firebase / ağ hatalarını `strings.xml` kaynaklarına eşler.
 * Bilinmeyen kodlarda `null` döner; view katmanı genel mesajı kullanır.
 */
object AuthErrorMapper {

    fun stringRes(error: Throwable): Int? = when (error) {
        is UnauthorizedUserException -> R.string.error_unauthorized_user
        is FirebaseNetworkException -> R.string.no_connection
        is FirebaseAuthException -> stringResForAuthCode(error.errorCode)
        else -> null
    }

    internal fun stringResForAuthCode(errorCode: String): Int? = when (errorCode) {
        "ERROR_WRONG_PASSWORD",
        "ERROR_USER_NOT_FOUND",
        "ERROR_INVALID_CREDENTIAL",
        "ERROR_INVALID_EMAIL" -> R.string.login_invalid_credentials

        "ERROR_USER_DISABLED" -> R.string.login_user_disabled
        "ERROR_TOO_MANY_REQUESTS" -> R.string.login_too_many_requests
        "ERROR_NETWORK_REQUEST_FAILED" -> R.string.no_connection
        "ERROR_EMAIL_ALREADY_IN_USE" -> R.string.sign_up_email_in_use
        "ERROR_WEAK_PASSWORD" -> R.string.sign_up_weak_password
        else -> null
    }
}
