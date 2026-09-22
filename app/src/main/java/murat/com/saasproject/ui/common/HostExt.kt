package murat.com.saasproject.ui.common

import androidx.activity.OnBackPressedCallback
import androidx.annotation.IdRes
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.NavHostFragment
import murat.com.saasproject.R
import murat.com.saasproject.data.model.BusinessSubscriptionAccess
import murat.com.saasproject.ui.business.BusinessHostFragment
import murat.com.saasproject.util.Formatters
import java.util.Date

fun Fragment.businessHost(): BusinessHostFragment? =
    generateSequence(parentFragment) { it.parentFragment }
        .filterIsInstance<BusinessHostFragment>()
        .firstOrNull()

/**
 * İç içe NavHost'ta sistem geri tuşu önce child stack'i pop eder.
 * Nested `defaultNavHost` kapalı tutulduğu için kök graph yanlışlıkla pop edilmez.
 */
fun Fragment.installNestedBackHandler(@IdRes navHostId: Int) {
    requireActivity().onBackPressedDispatcher.addCallback(
        viewLifecycleOwner,
        object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val navController = (childFragmentManager.findFragmentById(navHostId) as? NavHostFragment)
                    ?.navController
                if (navController != null && navController.popBackStack()) return
                isEnabled = false
                requireActivity().onBackPressedDispatcher.onBackPressed()
                isEnabled = true
            }
        }
    )
}

fun Fragment.remainingDaysText(access: BusinessSubscriptionAccess): String {
    val days = access.remainingDays ?: return getString(R.string.subscription_no_date)
    return when {
        days > 0 -> getString(R.string.subscription_days_remaining, days)
        days == 0 -> getString(R.string.subscription_last_day)
        else -> getString(R.string.subscription_expired_short)
    }
}

fun Fragment.expiryDateText(date: Date?): String =
    getString(R.string.subscription_expiry_date, date?.let(Formatters::longDate) ?: getString(R.string.empty_value))
