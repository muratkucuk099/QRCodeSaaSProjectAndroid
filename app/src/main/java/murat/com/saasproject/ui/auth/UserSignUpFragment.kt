package murat.com.saasproject.ui.auth

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.textfield.TextInputLayout
import murat.com.saasproject.R
import murat.com.saasproject.databinding.FragmentUserSignUpBinding
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
 * iOS `UserSignUpViewController` karşılığı: müşteri hesabı oluşturma.
 *
 * IOS davranışı: doğrulama hataları tek bir alert'te gösteriliyor.
 * ANDROID karşılığı: hata ilgili alanın altında gösterilir ve odak o alana taşınır;
 * alanla eşleşmeyen hatalar (KVKK onayı gibi) Snackbar ile verilir.
 */
class UserSignUpFragment : Fragment(R.layout.fragment_user_sign_up) {

    private val binding by viewBinding(FragmentUserSignUpBinding::bind)
    private val viewModel: UserSignUpViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupActions()
        observeViewModel()
    }

    private fun setupActions() = with(binding) {
        toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        listOf(
            nameEditText to nameInputLayout,
            emailEditText to emailInputLayout,
            passwordEditText to passwordInputLayout,
            passwordConfirmEditText to passwordConfirmInputLayout
        ).forEach { (editText, inputLayout) ->
            editText.doOnTextChange { inputLayout.setFieldError(null) }
        }

        kvkkConsent.kvkkPolicyButton.setOnClickListener {
            findNavController().navigate(R.id.kvkkPolicyFragment)
        }

        signUpButton.setOnClickListener {
            hideKeyboard()
            viewModel.signUp(
                name = nameEditText.text?.toString().orEmpty(),
                email = emailEditText.text?.toString().orEmpty(),
                password = passwordEditText.text?.toString().orEmpty(),
                passwordConfirmation = passwordConfirmEditText.text?.toString().orEmpty(),
                hasAcceptedKvkk = kvkkConsent.kvkkCheckBox.isChecked
            )
        }

        inviteCodeButton.setOnClickListener {
            hideKeyboard()
            InviteCodeDialog.show(this@UserSignUpFragment) { code ->
                viewModel.submitInviteCode(code)
            }
        }
    }

    private fun observeViewModel() {
        collectWhileStarted(viewModel.isLoading) { binding.loadingOverlay.isVisible = it }

        collectEvents(viewModel.events) { event ->
            when (event) {
                SignUpEvent.Registered -> showAlert(
                    titleRes = R.string.success_title,
                    messageRes = R.string.sign_up_success_message
                ) {
                    // iOS'ta kayıt sonrası giriş ekranına dönülür; oturum otomatik açılmaz.
                    findNavController().navigateUp()
                }

                is SignUpEvent.Failure -> showSnackbar(
                    event.messageRes?.let(::getString)
                        ?: requireContext().resolveErrorMessage(event.message)
                )

                is SignUpEvent.Invalid -> showFieldError(event)

                // Kod Cloud Function içinde tüketildi; işletme kayıt formuna geçilir.
                SignUpEvent.InviteCodeAccepted ->
                    findNavController().navigate(R.id.action_userSignUp_to_businessSignUp)
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
        SignUpField.EMAIL -> binding.emailInputLayout
        SignUpField.PASSWORD -> binding.passwordInputLayout
        SignUpField.PASSWORD_CONFIRM -> binding.passwordConfirmInputLayout
        SignUpField.PHONE, SignUpField.BUSINESS_TYPE, SignUpField.NONE -> null
    }
}
