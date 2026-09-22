package murat.com.saasproject.ui.auth

import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.FrameLayout
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch
import murat.com.saasproject.R
import murat.com.saasproject.databinding.FragmentLoginBinding
import murat.com.saasproject.ui.SessionViewModel
import murat.com.saasproject.ui.common.collectEvents
import murat.com.saasproject.ui.common.collectWhileStarted
import murat.com.saasproject.ui.common.doOnTextChange
import murat.com.saasproject.ui.common.hideKeyboard
import murat.com.saasproject.ui.common.resolveErrorMessage
import murat.com.saasproject.ui.common.setFieldError
import murat.com.saasproject.ui.common.showAlert
import murat.com.saasproject.ui.common.showSnackbar
import murat.com.saasproject.ui.common.trimmedText
import murat.com.saasproject.ui.common.viewBinding

/**
 * iOS `LoginViewController` karşılığı.
 *
 * IOS davranışı: hata ve bilgi mesajları `UIAlertController` ile veriliyor.
 * ANDROID karşılığı: alan doğrulama hataları ilgili alanın altında (TextInputLayout error),
 * sunucu hataları Snackbar ile, kullanıcı kararı gerektiren akışlar dialog ile gösterilir.
 */
class LoginFragment : Fragment(R.layout.fragment_login) {

    private val binding by viewBinding(FragmentLoginBinding::bind)
    private val viewModel: LoginViewModel by viewModels()
    private val sessionViewModel: SessionViewModel by activityViewModels()
    private var oauthConsentDialog: AlertDialog? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupActions()
        observeViewModel()
    }

    private fun setupActions() = with(binding) {
        emailEditText.doOnTextChange { emailInputLayout.setFieldError(null) }
        passwordEditText.doOnTextChange { passwordInputLayout.setFieldError(null) }

        loginButton.setOnClickListener {
            hideKeyboard()
            viewModel.login(
                email = emailEditText.text?.toString().orEmpty(),
                password = passwordEditText.text?.toString().orEmpty()
            )
        }

        forgotPasswordButton.setOnClickListener { onForgotPasswordClicked() }

        googleSignInButton.setOnClickListener { startGoogleSignIn() }

        signUpButton.setOnClickListener {
            findNavController().navigate(R.id.action_login_to_userSignUp)
        }
    }

    private fun observeViewModel() {
        collectWhileStarted(viewModel.isLoading) { isLoading ->
            binding.loadingOverlay.isVisible = isLoading
        }

        collectEvents(viewModel.events) { event -> handleEvent(event) }
    }

    override fun onResume() {
        super.onResume()
        if (viewModel.isOAuthKvkkPending) showOAuthConsentDialog()
    }

    override fun onDestroyView() {
        oauthConsentDialog?.dismiss()
        oauthConsentDialog = null
        super.onDestroyView()
    }

    private fun handleEvent(event: LoginEvent) = when (event) {
        is LoginEvent.Authenticated -> sessionViewModel.onRoleResolved(event.role)

        is LoginEvent.Failure -> {
            val text = event.messageRes?.let(::getString)
                ?: requireContext().resolveErrorMessage(event.message)
            showSnackbar(text)
        }

        is LoginEvent.ValidationFailure -> showValidationError(event.messageRes)

        LoginEvent.NeedsKvkkConsent -> showOAuthConsentDialog()

        is LoginEvent.PasswordResetSent -> showAlert(
            title = getString(R.string.password_reset_sent_title),
            message = getString(R.string.password_reset_sent_message) + "\n\n" + event.email
        )


        LoginEvent.GoogleUnavailable -> showAlert(
            titleRes = R.string.generic_error_title,
            messageRes = R.string.login_google_unavailable
        )
    }

    /**
     * Doğrulama hatasını mümkünse ilgili alanın altında gösterir; alanla
     * ilişkilendirilemeyen mesajlar Snackbar ile verilir.
     */
    private fun showValidationError(messageRes: Int) {
        val message = getString(messageRes)
        when (messageRes) {
            R.string.login_empty_credentials -> {
                if (binding.emailInputLayout.trimmedText == null) {
                    binding.emailInputLayout.setFieldError(message)
                }
                if (binding.passwordInputLayout.trimmedText == null) {
                    binding.passwordInputLayout.setFieldError(message)
                }
            }

            R.string.password_reset_empty_email,
            R.string.password_reset_invalid_email,
            R.string.password_reset_user_not_found -> showSnackbar(message)

            else -> showSnackbar(message)
        }
    }

    // region Google

    private fun startGoogleSignIn() {
        viewModel.setGoogleSignInStarted()
        viewLifecycleOwner.lifecycleScope.launch {
            val outcome = GoogleSignInHelper.requestCredential(requireContext())
            viewModel.onGoogleSignInResult(outcome)
        }
    }

    /**
     * Google ile giren yeni kullanıcıdan KVKK onayı alınır. Onay verilmeden
     * Firestore'a kullanıcı dokümanı yazılmaz; vazgeçilirse oturum kapatılır.
     */
    private fun showOAuthConsentDialog() {
        if (oauthConsentDialog?.isShowing == true) return
        oauthConsentDialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.kvkk_policy_title)
            .setMessage(R.string.kvkk_oauth_message)
            .setCancelable(false)
            .setNeutralButton(R.string.kvkk_link_text) { _, _ ->
                findNavController().navigate(R.id.kvkkPolicyFragment)
            }
            .setNegativeButton(R.string.cancel) { _, _ ->
                viewModel.cancelPendingOAuthRegistration()
            }
            .setPositiveButton(R.string.kvkk_oauth_complete) { _, _ ->
                viewModel.completeOAuthRegistrationAfterKvkkConsent()
            }
            .show()
    }

    // endregion

    // region Şifre sıfırlama

    /**
     * iOS davranışı: e-posta alanı doluysa doğrudan onay alert'i, boşsa metin
     * girişli alert gösteriliyor. Aynı ayrım korunuyor.
     */
    private fun onForgotPasswordClicked() {
        hideKeyboard()
        val typedEmail = binding.emailInputLayout.trimmedText

        if (typedEmail == null) {
            showPasswordResetEmailPrompt()
        } else {
            showPasswordResetConfirmation(typedEmail)
        }
    }

    private fun showPasswordResetConfirmation(email: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.password_reset_title)
            .setMessage(getString(R.string.password_reset_confirm_message, email))
            .setNegativeButton(R.string.dismiss, null)
            .setPositiveButton(R.string.send) { _, _ -> viewModel.resetPassword(email) }
            .show()
    }

    private fun showPasswordResetEmailPrompt() {
        val inputLayout = buildDialogEmailInput()

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.password_reset_title)
            .setMessage(R.string.password_reset_prompt)
            .setView(inputLayout)
            .setNegativeButton(R.string.dismiss, null)
            .setPositiveButton(R.string.send) { _, _ ->
                viewModel.resetPassword(inputLayout.trimmedText.orEmpty())
            }
            .show()
    }

    private fun buildDialogEmailInput(): TextInputLayout {
        val horizontalPadding = resources.getDimensionPixelSize(R.dimen.space_lg)

        val editText = TextInputEditText(requireContext()).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            setSingleLine()
        }

        return TextInputLayout(requireContext()).apply {
            hint = getString(R.string.login_email_hint)
            addView(editText)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(horizontalPadding, 0, horizontalPadding, 0) }
        }
    }

    // endregion
}
