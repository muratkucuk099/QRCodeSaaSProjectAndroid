package murat.com.saasproject.ui.common

import murat.com.saasproject.R
import murat.com.saasproject.data.repository.UnauthorizedUserException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AuthErrorMapperTest {

    @Test
    fun unauthorizedUserMapsToKnownString() {
        assertEquals(
            R.string.error_unauthorized_user,
            AuthErrorMapper.stringRes(UnauthorizedUserException())
        )
    }

    @Test
    fun invalidCredentialCodesMapToLoginError() {
        listOf(
            "ERROR_WRONG_PASSWORD",
            "ERROR_USER_NOT_FOUND",
            "ERROR_INVALID_CREDENTIAL",
            "ERROR_INVALID_EMAIL"
        ).forEach { code ->
            assertEquals(code, R.string.login_invalid_credentials, AuthErrorMapper.stringResForAuthCode(code))
        }
    }

    @Test
    fun emailAlreadyInUseMapsToSignUpError() {
        assertEquals(
            R.string.sign_up_email_in_use,
            AuthErrorMapper.stringResForAuthCode("ERROR_EMAIL_ALREADY_IN_USE")
        )
    }

    @Test
    fun unknownCodeReturnsNull() {
        assertNull(AuthErrorMapper.stringResForAuthCode("ERROR_SOMETHING_NEW"))
    }
}
