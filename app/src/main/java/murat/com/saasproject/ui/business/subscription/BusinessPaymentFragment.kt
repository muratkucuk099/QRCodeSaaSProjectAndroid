package murat.com.saasproject.ui.business.subscription

import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import kotlinx.coroutines.launch
import murat.com.saasproject.R
import murat.com.saasproject.data.model.BusinessSubscriptionAccess
import murat.com.saasproject.databinding.FragmentBusinessPaymentBinding
import murat.com.saasproject.domain.validation.WhatsAppLink
import murat.com.saasproject.ui.SessionViewModel
import murat.com.saasproject.ui.common.collectEvents
import murat.com.saasproject.ui.common.collectWhileStarted
import murat.com.saasproject.ui.common.openUrl
import murat.com.saasproject.ui.common.resolveErrorMessage
import murat.com.saasproject.ui.common.showAlert
import murat.com.saasproject.ui.common.showConfirmDialog
import murat.com.saasproject.ui.common.viewBinding
import murat.com.saasproject.util.Formatters
import murat.com.saasproject.util.PushTokenManager

class BusinessPaymentFragment : Fragment(R.layout.fragment_business_payment) {

    private val binding by viewBinding(FragmentBusinessPaymentBinding::bind)
    private val viewModel: BusinessPaymentViewModel by viewModels()
    private val sessionViewModel: SessionViewModel by activityViewModels()

    private val gateMode: Boolean
        get() = arguments?.getBoolean(ARG_GATE_MODE, true) ?: true

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (gateMode) {
            binding.toolbar.navigationIcon = null
            requireActivity().onBackPressedDispatcher.addCallback(
                viewLifecycleOwner,
                object : OnBackPressedCallback(true) {
                    override fun handleOnBackPressed() {
                        requireActivity().finish()
                    }
                }
            )
        } else {
            binding.toolbar.setNavigationIcon(R.drawable.ic_arrow_back)
            binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        }

        binding.signOutButton.isVisible = gateMode
        binding.signOutButton.setOnClickListener {
            showConfirmDialog(
                title = getString(R.string.sign_out),
                message = getString(R.string.sign_out_confirm_message),
                confirmRes = R.string.sign_out
            ) {
                viewLifecycleOwner.lifecycleScope.launch {
                    PushTokenManager.removeTokenForCurrentUser(requireContext())
                    sessionViewModel.signOut()
                }
            }
        }
        binding.whatsappButton.setOnClickListener {
            val phone = viewModel.ui.value.config?.phone ?: return@setOnClickListener
            if (!requireContext().openUrl(WhatsAppLink.chatUrl(phone))) {
                showAlert(R.string.whatsapp_not_found_title, R.string.whatsapp_not_found_message)
            }
        }
        binding.checkStatusButton.setOnClickListener { viewModel.refreshStatus() }

        collectWhileStarted(viewModel.isLoading) { binding.loadingOverlay.root.isVisible = it }
        collectWhileStarted(viewModel.ui, ::render)
        collectEvents(viewModel.events) { event ->
            when (event) {
                PaymentEvent.AccessGranted -> {
                    if (gateMode) sessionViewModel.refreshBusinessAccess()
                    else findNavController().navigateUp()
                }
                is PaymentEvent.Failure -> showAlert(
                    getString(R.string.generic_error_title),
                    requireContext().resolveErrorMessage(event.message)
                )
            }
        }

        viewModel.load()
    }

    override fun onResume() {
        super.onResume()
        viewModel.reloadPublicConfig()
    }

    private fun render(ui: PaymentUi) {
        val access = ui.access
        val canUse = access?.canUseApp == true
        binding.statusTitleTextView.setText(
            if (canUse) R.string.subscription_active_title else R.string.subscription_expired_title
        )
        binding.statusMessageTextView.setText(
            if (canUse) R.string.subscription_active_message else R.string.subscription_expired_message
        )
        binding.remainingDaysTextView.text = paymentRemainingText(access)
        val expiry = access?.business?.subscriptionExpiresAt
        binding.expiryDateTextView.text = getString(
            R.string.subscription_expiry_date,
            expiry?.let(Formatters::longDate) ?: getString(R.string.empty_value)
        )
        binding.phoneTextView.text = ui.config?.phone.orEmpty()
        binding.priceTextView.text = ui.config?.price.orEmpty()
    }

    /** iOS `BusinessPaymentViewModel.remainingDaysLabel`. */
    private fun paymentRemainingText(access: BusinessSubscriptionAccess?): String {
        val days = access?.remainingDays ?: return getString(R.string.subscription_no_date)
        return when {
            days > 0 -> getString(R.string.subscription_days_remaining, days)
            days == 0 -> getString(R.string.subscription_last_day)
            else -> getString(R.string.subscription_expired_days_ago, kotlin.math.abs(days))
        }
    }

    companion object {
        const val ARG_GATE_MODE = "gateMode"
    }
}
