package murat.com.saasproject.ui.business

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import murat.com.saasproject.R
import murat.com.saasproject.databinding.FragmentBusinessHostBinding
import murat.com.saasproject.ui.common.viewBinding

/**
 * iOS `MainTabBarController` karşılığı.
 * Sekme sırası: Anasayfa → Oluştur → Ödüller → Profil.
 */
class BusinessHostFragment : Fragment(R.layout.fragment_business_host) {

    private val binding by viewBinding(FragmentBusinessHostBinding::bind)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val navHost = childFragmentManager.findFragmentById(R.id.businessNavHost) as NavHostFragment
        val navController = navHost.navController
        binding.businessBottomNav.setupWithNavController(navController)

        navController.addOnDestinationChangedListener { _, destination, _ ->
            binding.businessBottomNav.isVisible = destination.id in TOP_LEVEL
        }
    }

    fun switchToCreate() {
        binding.businessBottomNav.selectedItemId = R.id.createFragment
    }

    fun switchToRewards() {
        binding.businessBottomNav.selectedItemId = R.id.adminPanelFragment
    }

    companion object {
        private val TOP_LEVEL = setOf(
            R.id.adminMainFragment,
            R.id.createFragment,
            R.id.adminPanelFragment,
            R.id.adminProfileFragment
        )
    }
}
