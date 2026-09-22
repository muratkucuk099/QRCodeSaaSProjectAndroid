package murat.com.saasproject.ui.business

import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import murat.com.saasproject.R
import murat.com.saasproject.databinding.FragmentAdminMainBinding
import murat.com.saasproject.domain.config.QrConfig
import murat.com.saasproject.ui.SessionViewModel
import murat.com.saasproject.ui.account.AccountMenuBottomSheet
import murat.com.saasproject.ui.common.TextInputDialog
import murat.com.saasproject.ui.common.collectEvents
import murat.com.saasproject.ui.common.collectWhileStarted
import murat.com.saasproject.ui.common.resolveErrorMessage
import murat.com.saasproject.ui.common.showAlert
import murat.com.saasproject.ui.common.viewBinding
import murat.com.saasproject.util.QrGenerator
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class AdminMainFragment : Fragment(R.layout.fragment_admin_main) {

    private val binding by viewBinding(FragmentAdminMainBinding::bind)
    private val viewModel: AdminMainViewModel by viewModels()
    private val sessionViewModel: SessionViewModel by activityViewModels()

    private var selectedLabel: String? = null
    private var selectedPoints: Int = 0
    private var selectedRewardId: String? = null
    private var selectedRewardName: String? = null
    private var qrActive = false
    private var remainingSeconds = 0
    private var timer: CountDownTimer? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.toolbar.setNavigationOnClickListener { AccountMenuBottomSheet.show(this) }
        binding.selectionCard.setOnClickListener { showActionSheet() }
        binding.generateButton.setOnClickListener {
            if (selectedLabel == null || qrActive) return@setOnClickListener
            viewModel.generate(selectedPoints, selectedRewardId, selectedRewardName)
        }

        parentFragmentManager.setFragmentResultListener(REQUEST_REWARD, viewLifecycleOwner) { _, bundle ->
            selectedRewardId = bundle.getString(KEY_REWARD_ID)
            selectedRewardName = bundle.getString(KEY_REWARD_NAME)
            selectedPoints = bundle.getInt(KEY_POINTS)
            selectedLabel = getString(
                R.string.admin_home_selection_reward,
                selectedRewardName.orEmpty(),
                -selectedPoints
            )
            updateSelectionUi()
        }

        collectWhileStarted(viewModel.isLoading) { binding.loadingOverlay.root.isVisible = it }
        collectEvents(viewModel.events) { event ->
            when (event) {
                is AdminMainEvent.QrReady -> showQr(event.json)
                AdminMainEvent.SubscriptionRequired ->
                    sessionViewModel.refreshBusinessAccess()
                is AdminMainEvent.Failure -> showAlert(
                    getString(R.string.admin_home_qr_failed_title),
                    requireContext().resolveErrorMessage(event.message)
                )
            }
        }
        updateSelectionUi()
    }

    override fun onDestroyView() {
        timer?.cancel()
        super.onDestroyView()
    }

    private fun showActionSheet() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.admin_home_action_title)
            .setMessage(R.string.admin_home_action_message)
            .setItems(
                arrayOf(
                    getString(R.string.admin_home_action_give_reward),
                    getString(R.string.admin_home_action_manual_points)
                )
            ) { _, which ->
                if (which == 0) {
                    findNavController().navigate(R.id.action_home_to_rewardSelect)
                } else {
                    TextInputDialog.show(
                        this,
                        titleRes = R.string.admin_home_points_dialog_title,
                        messageRes = R.string.admin_home_points_dialog_message,
                        hintRes = R.string.admin_home_points_dialog_hint,
                        inputType = android.text.InputType.TYPE_CLASS_NUMBER
                    ) { raw ->
                        val points = raw.trim().toIntOrNull()
                        if (points == null || points <= 0) {
                            showAlert(
                                R.string.admin_home_points_invalid_title,
                                R.string.admin_home_points_invalid_message
                            )
                            return@show
                        }
                        selectedRewardId = null
                        selectedRewardName = null
                        selectedPoints = points
                        selectedLabel = getString(R.string.admin_home_selection_points, points)
                        updateSelectionUi()
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showQr(json: String) {
        val bitmap = QrGenerator.generate(json)
        if (bitmap == null) {
            showAlert(R.string.admin_home_qr_failed_title, R.string.admin_home_qr_image_failed)
            return
        }
        binding.qrImageView.setImageBitmap(bitmap)
        binding.qrImageView.isVisible = true
        binding.qrPlaceholder.isVisible = false
        startCountdown()
    }

    private fun startCountdown() {
        timer?.cancel()
        remainingSeconds = QrConfig.DISPLAY_COUNTDOWN_SECONDS.toInt()
        qrActive = true
        updateSelectionUi()
        timer = object : CountDownTimer(remainingSeconds * 1000L, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                remainingSeconds = (millisUntilFinished / 1000L).toInt()
                updateSelectionUi()
            }

            override fun onFinish() {
                resetQr()
            }
        }.start()
    }

    private fun resetQr() {
        timer?.cancel()
        qrActive = false
        remainingSeconds = 0
        binding.qrImageView.setImageDrawable(null)
        binding.qrImageView.isVisible = false
        binding.qrPlaceholder.isVisible = true
        updateSelectionUi()
    }

    private fun updateSelectionUi() {
        val hasSelection = !selectedLabel.isNullOrBlank()
        binding.selectionTextView.text = selectedLabel ?: getString(R.string.admin_home_selection_empty)
        binding.selectionTextView.setTextColor(
            requireContext().getColor(if (hasSelection) R.color.primary_text else R.color.secondary_text)
        )
        binding.generateButton.isEnabled = hasSelection && !qrActive
        binding.generateButton.text = if (qrActive) {
            getString(R.string.admin_home_qr_active, remainingSeconds)
        } else {
            getString(R.string.admin_home_generate_qr)
        }
        binding.placeholderTitle.setText(
            if (hasSelection) R.string.admin_home_qr_ready_title else R.string.admin_home_qr_placeholder_title
        )
        binding.placeholderMessage.setText(
            if (hasSelection) R.string.admin_home_qr_ready_message else R.string.admin_home_qr_placeholder_message
        )
    }

    companion object {
        const val REQUEST_REWARD = "reward_selected"
        const val KEY_REWARD_ID = "rewardId"
        const val KEY_REWARD_NAME = "rewardName"
        const val KEY_POINTS = "points"
    }
}
