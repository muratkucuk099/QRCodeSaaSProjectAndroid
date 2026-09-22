package murat.com.saasproject.ui.auth

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.textfield.TextInputLayout
import murat.com.saasproject.R
import murat.com.saasproject.databinding.FragmentBusinessSignUpBinding
import murat.com.saasproject.ui.common.collectEvents
import murat.com.saasproject.ui.common.collectWhileStarted
import murat.com.saasproject.ui.common.doOnTextChange
import murat.com.saasproject.ui.common.hideKeyboard
import murat.com.saasproject.ui.common.resolveErrorMessage
import murat.com.saasproject.ui.common.setFieldError
import murat.com.saasproject.ui.common.showAlert
import murat.com.saasproject.ui.common.showSnackbar
import murat.com.saasproject.ui.common.viewBinding

/**
 * iOS `AdminSignUpViewController` karşılığı: işletme hesabı oluşturma.
 *
 * Bu ekrana yalnızca davet kodu Cloud Function tarafından doğrulandıktan sonra
 * gelinir; kod argüman olarak taşınır.
 */
class BusinessSignUpFragment : Fragment(R.layout.fragment_business_sign_up) {

    private val binding by viewBinding(FragmentBusinessSignUpBinding::bind)
    private val viewModel: BusinessSignUpViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupActions()
        observeViewModel()
    }

    private fun setupActions() = with(binding) {
        toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        fieldPairs().forEach { (editText, inputLayout) ->
            editText.doOnTextChange { inputLayout.setFieldError(null) }
        }

        kvkkConsent.kvkkPolicyButton.setOnClickListener {
            findNavController().navigate(R.id.kvkkPolicyFragment)
        }

        signUpButton.setOnClickListener {
            hideKeyboard()
            viewModel.signUp(
                name = nameEditText.text?.toString().orEmpty(),
                businessType = businessTypeEditText.text?.toString().orEmpty(),
                phone = phoneEditText.text?.toString().orEmpty(),
                email = emailEditText.text?.toString().orEmpty(),
                password = passwordEditText.text?.toString().orEmpty(),
                passwordConfirmation = passwordConfirmEditText.text?.toString().orEmpty(),
                hasAcceptedKvkk = kvkkConsent.kvkkCheckBox.isChecked
            )
        }
    }

    private fun fieldPairs() = with(binding) {
        listOf(
            nameEditText to nameInputLayout,
            businessTypeEditText to businessTypeInputLayout,
            phoneEditText to phoneInputLayout,
            emailEditText to emailInputLayout,
            passwordEditText to passwordInputLayout,
            passwordConfirmEditText to passwordConfirmInputLayout
        )
    }

    private fun observeViewModel() {
        collectWhileStarted(viewModel.isLoading) { binding.loadingOverlay.isVisible = it }

        collectEvents(viewModel.events) { event ->
            when (event) {
                SignUpEvent.Registered -> showAlert(
                    titleRes = R.string.success_title,
                    messageRes = R.string.sign_up_success_message
                ) {
                    // Kayıt sonrası giriş ekranına dönülür (iOS ile aynı akış).
                    findNavController().popBackStack(R.id.loginFragment, /* inclusive = */ false)
                }

                is SignUpEvent.Failure -> showSnackbar(
                    event.messageRes?.let(::getString)
                        ?: requireContext().resolveErrorMessage(event.message)
                )

                is SignUpEvent.Invalid -> showFieldError(event)

                // Davet kodu bu ekranda tekrar sorulmaz.
                SignUpEvent.InviteCodeAccepted -> Unit
            }
        }
    }

    private fun showFieldError(event: SignUpEvent.Invalid) {
        val message = getString(event.messageRes)
        val inputLayout = inputLayoutFor(event.field)

        if (inputLayout == null) {
            showSnackbar(message)
            return
        }

        inputLayout.setFieldError(message)
        inputLayout.editText?.requestFocus()
    }

    private fun inputLayoutFor(field: SignUpField): TextInputLayout? = when (field) {
        SignUpField.NAME -> binding.nameInputLayout
        SignUpField.BUSINESS_TYPE -> binding.businessTypeInputLayout
        SignUpField.PHONE -> binding.phoneInputLayout
        SignUpField.EMAIL -> binding.emailInputLayout
        SignUpField.PASSWORD -> binding.passwordInputLayout
        SignUpField.PASSWORD_CONFIRM -> binding.passwordConfirmInputLayout
        SignUpField.NONE -> null
    }

}
