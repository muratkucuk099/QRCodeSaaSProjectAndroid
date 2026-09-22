package murat.com.saasproject.domain.auth

import murat.com.saasproject.data.model.LoginResult
import murat.com.saasproject.domain.config.MainAdminConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AuthRoleResolverTest {

    @Test
    fun mainAdminUidWinsEvenIfDocumentsExist() {
        val role = AuthRoleResolver.resolve(
            uid = MainAdminConfig.UID,
            userDocumentExists = true,
            businessDocumentExists = true
        )
        assertEquals(LoginResult.MAIN_ADMIN, role)
    }

    @Test
    fun userDocumentMapsToCustomer() {
        val role = AuthRoleResolver.resolve(
            uid = "customer-uid",
            userDocumentExists = true,
            businessDocumentExists = false
        )
        assertEquals(LoginResult.USER, role)
    }

    @Test
    fun userDocumentPreferredOverBusinessDocument() {
        val role = AuthRoleResolver.resolve(
            uid = "both-uid",
            userDocumentExists = true,
            businessDocumentExists = true
        )
        assertEquals(LoginResult.USER, role)
    }

    @Test
    fun businessDocumentMapsToSubAdmin() {
        val role = AuthRoleResolver.resolve(
            uid = "business-uid",
            userDocumentExists = false,
            businessDocumentExists = true
        )
        assertEquals(LoginResult.SUB_ADMIN, role)
    }

    @Test
    fun missingDocumentsAreUnauthorized() {
        assertNull(
            AuthRoleResolver.resolve(
                uid = "unknown-uid",
                userDocumentExists = false,
                businessDocumentExists = false
            )
        )
    }
}
