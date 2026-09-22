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
import murat.com.saasproject.R
import murat.com.saasproject.databinding.FragmentCreateBinding
import murat.com.saasproject.ui.common.businessHost
import murat.com.saasproject.ui.common.collectEvents
import murat.com.saasproject.ui.common.collectWhileStarted
import murat.com.saasproject.ui.common.hideKeyboard
import murat.com.saasproject.ui.common.resolveErrorMessage
import murat.com.saasproject.ui.common.showAlert
import murat.com.saasproject.ui.common.showSnackbar
import murat.com.saasproject.ui.common.viewBinding

class CreateFragment : Fragment(R.layout.fragment_create) {

    private val binding by viewBinding(FragmentCreateBinding::bind)
    private val rewardViewModel: CreateRewardViewModel by viewModels()
    private val notificationViewModel: CreateNotificationViewModel by viewModels()

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
            rewardViewModel.selectedImage = bitmap
            binding.rewardImageView.setImageBitmap(bitmap)
            binding.imageHintTextView.setText(R.string.create_reward_image_hint_change)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.modeToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val rewardMode = checkedId == R.id.rewardModeButton
            binding.rewardForm.isVisible = rewardMode
            binding.notificationForm.isVisible = !rewardMode
        }

        binding.imageCard.setOnClickListener {
            imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        binding.createRewardButton.setOnClickListener {
            hideKeyboard()
            rewardViewModel.create(
                binding.nameEditText.text?.toString(),
                binding.descriptionEditText.text?.toString(),
                binding.pointsEditText.text?.toString()
            )
        }
        binding.sendNotificationButton.setOnClickListener {
            hideKeyboard()
            notificationViewModel.send(
                binding.notificationTitleEditText.text?.toString(),
                binding.notificationBodyEditText.text?.toString()
            )
        }

        collectWhileStarted(rewardViewModel.isLoading) { updateLoading() }
        collectWhileStarted(notificationViewModel.isLoading) { updateLoading() }
        collectEvents(rewardViewModel.events) { event ->
            when (event) {
                CreateRewardEvent.Success -> {
                    binding.nameEditText.text = null
                    binding.descriptionEditText.text = null
                    binding.pointsEditText.text = null
                    binding.rewardImageView.setImageResource(R.drawable.ic_photo)
                    binding.imageHintTextView.setText(R.string.create_reward_image_hint_add)
                    showSnackbar(R.string.create_reward_success)
                    businessHost()?.switchToRewards()
                }
                is CreateRewardEvent.Failure -> showAlert(
                    getString(R.string.create_reward_error_title),
                    event.messageRes?.let(::getString)
                        ?: requireContext().resolveErrorMessage(event.message)
                )
            }
        }
        collectEvents(notificationViewModel.events) { event ->
            when (event) {
                is CreateNotificationEvent.Success -> {
                    binding.notificationTitleEditText.text = null
                    binding.notificationBodyEditText.text = null
                    showSnackbar(event.message)
                }
                is CreateNotificationEvent.Failure -> showAlert(
                    getString(R.string.create_notification_error_title),
                    event.messageRes?.let(::getString)
                        ?: requireContext().resolveErrorMessage(event.message)
                )
            }
        }
    }

    private fun updateLoading() {
        binding.loadingOverlay.root.isVisible =
            rewardViewModel.isLoading.value || notificationViewModel.isLoading.value
    }
}
