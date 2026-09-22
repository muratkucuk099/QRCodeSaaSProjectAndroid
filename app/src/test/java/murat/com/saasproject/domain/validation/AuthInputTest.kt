package murat.com.saasproject.domain.validation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthInputTest {

    @Test
    fun normalizeTrimsRemovesSpacesAndLowercases() {
        assertEquals("user@example.com", AuthEmailInput.normalize("  User @Example.com "))
    }

    @Test
    fun plausibleEmailRequiresAtAndDot() {
        assertTrue(AuthEmailInput.isPlausible("a@b.c"))
        assertFalse(AuthEmailInput.isPlausible("not-an-email"))
        assertFalse(AuthEmailInput.isPlausible("missing-dot@local"))
    }

    @Test
    fun inviteCodeNormalizeTrimsAndUppercases() {
        assertEquals("ABCD2345", InviteCodeGenerator.normalize("  abcd2345  "))
    }
}
