package murat.com.saasproject.ui.mainadmin

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import murat.com.saasproject.R
import murat.com.saasproject.databinding.FragmentMainAdminDetailBinding
import murat.com.saasproject.ui.common.collectEvents
import murat.com.saasproject.ui.common.collectWhileStarted
import murat.com.saasproject.ui.common.loadRemoteImage
import murat.com.saasproject.ui.common.remainingDaysText
import murat.com.saasproject.ui.common.resolveErrorMessage
import murat.com.saasproject.util.Formatters
import murat.com.saasproject.ui.common.showAlert
import murat.com.saasproject.ui.common.showConfirmDialog
import murat.com.saasproject.ui.common.viewBinding

class MainAdminBusinessDetailFragment : Fragment(R.layout.fragment_main_admin_detail) {

    private val binding by viewBinding(FragmentMainAdminDetailBinding::bind)
    private val viewModel: MainAdminBusinessDetailViewModel by viewModels()

    private val businessId: String
        get() = requireArguments().getString(ARG_BUSINESS_ID).orEmpty()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        binding.extendButton.setOnClickListener { viewModel.extendSubscription() }
        binding.shortenButton.setOnClickListener { viewModel.shortenSubscription() }
        binding.cancelButton.setOnClickListener {
            showConfirmDialog(
                title = getString(R.string.main_admin_cancel_subscription),
                message = getString(R.string.main_admin_cancel_confirm_message),
                confirmRes = R.string.main_admin_cancel_confirm_action,
                destructive = true
            ) { viewModel.cancelSubscription() }
        }

        collectWhileStarted(viewModel.isLoading) { binding.loadingOverlay.root.isVisible = it }
        collectWhileStarted(viewModel.ui, ::render)
        collectEvents(viewModel.events) { message ->
            showAlert(getString(R.string.generic_error_title), requireContext().resolveErrorMessage(message))
        }

        viewModel.load(businessId)
    }

    private fun render(ui: MainAdminDetailUi) {
        val business = ui.business ?: return
        binding.toolbar.title = business.name
        binding.nameTextView.text = business.name
        binding.emailTextView.text = business.email
        binding.phoneTextView.text = business.phone
        binding.logoImageView.loadRemoteImage(business.logoURL)
        binding.statusTextView.setText(
            if (business.isActive) R.string.subscription_status_active else R.string.subscription_status_inactive
        )
        val access = ui.access
        if (access != null) {
            binding.remainingTextView.text = remainingDaysText(access)
            val expiry = access.business.subscriptionExpiresAt
            binding.expiryTextView.text = getString(
                R.string.subscription_end_date,
                expiry?.let(Formatters::longDate) ?: getString(R.string.empty_value)
            )
        }
    }

    companion object {
        const val ARG_BUSINESS_ID = "businessId"
    }
}
