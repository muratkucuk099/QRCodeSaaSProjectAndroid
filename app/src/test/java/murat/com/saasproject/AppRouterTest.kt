package murat.com.saasproject

import murat.com.saasproject.ui.SessionRoot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppRouterTest {

    @Test
    fun splashStaysUntilRoleResolved() {
        assertNull(AppRouter.destinationFor(SessionRoot.Undetermined))
    }

    @Test
    fun loginCustomerBusinessMainAdminAndGateHaveDistinctDestinations() {
        val login = AppRouter.destinationFor(SessionRoot.Login)
        val customer = AppRouter.destinationFor(SessionRoot.Customer)
        val business = AppRouter.destinationFor(SessionRoot.Business)
        val mainAdmin = AppRouter.destinationFor(SessionRoot.MainAdmin)
        val gate = AppRouter.destinationFor(SessionRoot.SubscriptionGate)

        assertEquals(R.id.loginFragment, login)
        assertEquals(R.id.customerHostFragment, customer)
        assertEquals(R.id.businessHostFragment, business)
        assertEquals(R.id.mainAdminFragment, mainAdmin)
        assertEquals(R.id.subscriptionGateFragment, gate)

        val destinations = setOf(login, customer, business, mainAdmin, gate)
        assertEquals(5, destinations.size)
    }
}
