package murat.com.saasproject.ui.customer

import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.NavHostFragment
import murat.com.saasproject.R
import murat.com.saasproject.databinding.FragmentCustomerHostBinding
import murat.com.saasproject.ui.common.installNestedBackHandler
import murat.com.saasproject.ui.common.showSnackbar
import murat.com.saasproject.ui.common.viewBinding
import murat.com.saasproject.ui.customer.qr.QrScannerActivity

/**
 * iOS `UserTabbarController` karşılığı.
 * QR sekmesi seçilmez; iOS'taki gibi tam ekran tarayıcı açılır.
 */
class CustomerHostFragment : Fragment(R.layout.fragment_customer_host) {

    private val binding by viewBinding(FragmentCustomerHostBinding::bind)

    private val qrLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != AppCompatActivity.RESULT_OK) return@registerForActivityResult
        val points = result.data?.getIntExtra(QrScannerActivity.EXTRA_NEW_POINTS, -1) ?: -1
        if (points >= 0) {
            showSnackbar(getString(R.string.qr_success_message, points))
            findUserHome()?.refreshAfterQrScan()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        installNestedBackHandler(R.id.customerNavHost)

        val navHost = childFragmentManager.findFragmentById(R.id.customerNavHost) as NavHostFragment
        val navController = navHost.navController

        binding.customerBottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.userHomeFragment -> {
                    navController.popBackStack(R.id.userHomeFragment, false)
                    true
                }
                R.id.qrScannerAction -> {
                    qrLauncher.launch(QrScannerActivity.intent(requireContext()))
                    false
                }
                else -> false
            }
        }

        navController.addOnDestinationChangedListener { _, destination, _ ->
            binding.customerBottomNav.isVisible = destination.id == R.id.userHomeFragment
        }
    }

    private fun findUserHome(): UserHomeFragment? {
        val navHost = childFragmentManager.findFragmentById(R.id.customerNavHost) as? NavHostFragment
        return navHost?.childFragmentManager?.fragments?.filterIsInstance<UserHomeFragment>()?.firstOrNull()
    }
}
