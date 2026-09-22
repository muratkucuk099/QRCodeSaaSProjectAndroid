package murat.com.saasproject.ui.business

import android.graphics.ImageDecoder
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import murat.com.saasproject.R
import murat.com.saasproject.data.model.BusinessProfileStats
import murat.com.saasproject.databinding.FragmentAdminProfileBinding
import murat.com.saasproject.databinding.ItemStatRowBinding
import murat.com.saasproject.ui.account.AccountMenuBottomSheet
import murat.com.saasproject.ui.common.collectEvents
import murat.com.saasproject.ui.common.collectWhileStarted
import murat.com.saasproject.ui.common.expiryDateText
import murat.com.saasproject.ui.common.loadRemoteImage
import murat.com.saasproject.ui.common.remainingDaysText
import murat.com.saasproject.ui.common.resolveErrorMessage
import murat.com.saasproject.ui.common.showSnackbar
import murat.com.saasproject.ui.common.viewBinding
import murat.com.saasproject.util.Formatters

class AdminProfileFragment : Fragment(R.layout.fragment_admin_profile) {

    private val binding by viewBinding(FragmentAdminProfileBinding::bind)
    private val viewModel: AdminProfileViewModel by viewModels()
    private var editing = false

    private val imagePicker = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        val bitmap = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(requireContext().contentResolver, uri))
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(requireContext().contentResolver, uri)
            }
        }.getOrNull()
        if (bitmap != null) {
            viewModel.pendingLogo = bitmap
            binding.logoImageView.setImageBitmap(bitmap)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.toolbar.setNavigationOnClickListener { AccountMenuBottomSheet.show(this) }
        binding.editSaveButton.setOnClickListener { onEditSave() }
        binding.changeLogoButton.setOnClickListener {
            imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        binding.openPaymentButton.setOnClickListener {
            findNavController().navigate(R.id.action_profile_to_payment)
        }
        binding.swipeRefresh.setOnRefreshListener { viewModel.load(forceRefresh = true) }

        collectWhileStarted(viewModel.ui, ::render)
        collectWhileStarted(viewModel.isLoading) { binding.loadingOverlay.root.isVisible = it }
        collectWhileStarted(viewModel.isRefreshing) { binding.swipeRefresh.isRefreshing = it }
        collectEvents(viewModel.events) { showSnackbar(requireContext().resolveErrorMessage(it)) }

        viewModel.load()
    }

    private fun onEditSave() {
        if (!editing) {
            editing = true
            applyEditMode()
            return
        }
        val name = binding.nameEditText.text?.toString()?.trim().orEmpty()
        val phone = binding.phoneEditText.text?.toString()?.trim().orEmpty()
        if (name.isEmpty() || phone.isEmpty()) {
            if (name.isEmpty()) binding.nameInputLayout.error = getString(R.string.validation_all_fields_required)
            if (phone.isEmpty()) binding.phoneInputLayout.error = getString(R.string.validation_all_fields_required)
            return
        }
        editing = false
        applyEditMode()
        viewModel.save(name, phone, binding.rulesEditText.text?.toString())
    }

    private fun applyEditMode() {
        binding.editSaveButton.setText(if (editing) R.string.save else R.string.edit)
        binding.nameTextView.isVisible = !editing
        binding.phoneTextView.isVisible = !editing
        binding.rulesTextView.isVisible = !editing
        binding.nameInputLayout.isVisible = editing
        binding.phoneInputLayout.isVisible = editing
        binding.rulesInputLayout.isVisible = editing
        binding.changeLogoButton.isVisible = editing
    }

    private fun render(ui: AdminProfileUi) {
        val business = ui.business ?: return
        binding.nameTextView.text = business.name
        binding.phoneTextView.text = business.phone
        if (!editing) {
            binding.nameEditText.setText(business.name)
            binding.phoneEditText.setText(business.phone)
            binding.rulesEditText.setText(business.pointEarningRules.orEmpty())
            binding.logoImageView.loadRemoteImage(business.logoURL)
        }
        binding.rulesTextView.text =
            business.displayPointEarningRules ?: getString(R.string.point_earning_rules_empty)

        val access = ui.access
        if (access != null) {
            binding.subscriptionTitleTextView.setText(
                if (access.canUseApp) R.string.subscription_status_active else R.string.subscription_status_inactive
            )
            binding.subscriptionRemainingTextView.text = remainingDaysText(access)
            binding.subscriptionExpiryTextView.text = expiryDateText(access.business.subscriptionExpiresAt)
        }

        bindStats(ui.stats)
    }

    private fun bindStats(stats: BusinessProfileStats) {
        binding.monthSummaryTitle.text =
            getString(R.string.admin_profile_month_summary, Formatters.monthName())
        bindRow(binding.statNewCustomer, R.string.admin_profile_new_customer, stats.newCustomersThisMonth.toString())
        bindRow(binding.statReturningCustomer, R.string.admin_profile_returning_customer, stats.returningCustomersThisMonth.toString())
        val postValue = if (stats.hasSentNotifications) {
            Formatters.groupedNumber(stats.qrScansAfterLastNotification48h ?: 0)
        } else {
            getString(R.string.empty_value)
        }
        bindRow(binding.statPostNotification, R.string.admin_profile_post_notification, postValue)

        bindRow(binding.statActiveCustomers, R.string.admin_profile_active_customers, Formatters.groupedNumber(stats.activeCustomersLast30Days))
        bindRow(binding.statTotalCustomers, R.string.admin_profile_total_customers, Formatters.groupedNumber(stats.uniqueRecipientsCount))
        val avg = stats.averageReturnIntervalDays?.takeIf { it > 0 }?.let {
            getString(R.string.stat_days, it.toInt())
        } ?: getString(R.string.empty_value)
        bindRow(binding.statAverageReturn, R.string.admin_profile_average_return, avg)
        bindRow(binding.statRewardsGiven, R.string.admin_profile_total_rewards_given, Formatters.groupedNumber(stats.totalRewardsRedeemed))
        val oneAway = if (stats.customersOnePointAway > 0) {
            getString(R.string.stat_customers, Formatters.groupedNumber(stats.customersOnePointAway))
        } else {
            "0"
        }
        bindRow(binding.statOnePointAway, R.string.admin_profile_one_point_away, oneAway)
        bindRow(binding.statTotalPoints, R.string.admin_profile_total_points, Formatters.groupedNumber(stats.totalDistributedPoints))
        bindRow(binding.statQrScanners, R.string.admin_profile_qr_scanners, Formatters.groupedNumber(stats.uniqueRecipientsCount))
        bindRow(binding.statRewardsDistributed, R.string.admin_profile_rewards_distributed, Formatters.groupedNumber(stats.totalRewardsRedeemed))
        val top = when {
            stats.topRewardRecipientCount <= 0 -> "0"
            !stats.topRewardRecipientName.isNullOrBlank() ->
                getString(R.string.stat_top_recipient, stats.topRewardRecipientName, stats.topRewardRecipientCount)
            else -> getString(R.string.stat_rewards, stats.topRewardRecipientCount)
        }
        bindRow(binding.statTopRecipient, R.string.admin_profile_top_recipient, top)
    }

    private fun bindRow(row: ItemStatRowBinding, labelRes: Int, value: String) {
        row.statLabelTextView.setText(labelRes)
        row.statValueTextView.text = value
    }
}
