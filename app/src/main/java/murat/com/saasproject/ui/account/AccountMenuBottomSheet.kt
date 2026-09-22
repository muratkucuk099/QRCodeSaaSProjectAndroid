package murat.com.saasproject.ui.account

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import murat.com.saasproject.R
import murat.com.saasproject.databinding.SheetAccountMenuBinding
import murat.com.saasproject.ui.SessionViewModel
import murat.com.saasproject.ui.common.collectEvents
import murat.com.saasproject.ui.common.collectWhileStarted
import murat.com.saasproject.ui.common.resolveErrorMessage
import murat.com.saasproject.ui.common.showAlert
import murat.com.saasproject.ui.common.showConfirmDialog
import murat.com.saasproject.ui.common.showSnackbar
import murat.com.saasproject.util.DeviceId

/**
 * Hesap menüsü: çıkış ve hesap silme.
 *
 * IOS davranışı: `AccountSideMenuViewController` soldan açılan 300pt genişlikte bir panel.
 * ANDROID karşılığı: bottom sheet — Android'de bu tür kısa eylem listeleri için beklenen
 * kalıptır ve yan menü ana navigasyona ayrılmıştır.
 *
 * Her rol ekranından `AccountMenuBottomSheet.show(fragment)` ile açılır.
 */
class AccountMenuBottomSheet : BottomSheetDialogFragment() {

    private var _binding: SheetAccountMenuBinding? = null
    private val binding get() = requireNotNull(_binding)

    private val viewModel: AccountMenuViewModel by viewModels()
    private val sessionViewModel: SessionViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = SheetAccountMenuBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.currentUserEmail?.let { email ->
            binding.accountEmailTextView.isVisible = true
            binding.accountEmailTextView.text = email
        }

        binding.signOutButton.setOnClickListener {
            showConfirmDialog(
                title = getString(R.string.sign_out),
                message = getString(R.string.sign_out_confirm_message),
                confirmRes = R.string.sign_out
            ) {
                viewModel.signOut(DeviceId.get(requireContext()))
            }
        }

        binding.deleteAccountButton.setOnClickListener {
            showConfirmDialog(
                title = getString(R.string.delete_account),
                message = getString(R.string.delete_account_confirm_message),
                confirmRes = R.string.delete,
                destructive = true
            ) {
                viewModel.deleteAccount()
            }
        }

        observeViewModel()
    }

    private fun observeViewModel() {
        collectWhileStarted(viewModel.isLoading) { isLoading ->
            binding.accountProgress.isVisible = isLoading
            binding.signOutButton.isEnabled = !isLoading
            binding.deleteAccountButton.isEnabled = !isLoading
            // Yükleme sırasında sayfa kaydırılarak kapatılamaz; yarım kalmış silme olmaz.
            isCancelable = !isLoading
        }

        collectEvents(viewModel.events) { event ->
            when (event) {
                AccountMenuEvent.SignedOut -> {
                    sessionViewModel.signOut()
                    dismiss()
                }

                AccountMenuEvent.AccountDeleted -> {
                    sessionViewModel.onAccountDeleted()
                    dismiss()
                }

                AccountMenuEvent.MainAdminNotDeletable -> showAlert(
                    titleRes = R.string.delete_account,
                    messageRes = R.string.delete_account_main_admin
                )

                AccountMenuEvent.RequiresRecentLogin -> showAlert(
                    titleRes = R.string.delete_account,
                    messageRes = R.string.delete_account_requires_recent_login
                )

                is AccountMenuEvent.Failure ->
                    showSnackbar(requireContext().resolveErrorMessage(event.message))
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val TAG = "AccountMenuBottomSheet"

        /** Hesap menüsünü açar. Aynı menü iki kez açılmaz. */
        fun show(fragment: Fragment) {
            val manager = fragment.childFragmentManager
            if (manager.findFragmentByTag(TAG) != null) return
            AccountMenuBottomSheet().show(manager, TAG)
        }
    }
}
