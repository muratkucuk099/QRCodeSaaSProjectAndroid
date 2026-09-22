package murat.com.saasproject.ui.mainadmin

import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import murat.com.saasproject.R
import murat.com.saasproject.databinding.FragmentMainAdminBinding
import murat.com.saasproject.ui.account.AccountMenuBottomSheet
import murat.com.saasproject.ui.common.collectEvents
import murat.com.saasproject.ui.common.collectWhileStarted
import murat.com.saasproject.ui.common.copyToClipboard
import murat.com.saasproject.ui.common.resolveErrorMessage
import murat.com.saasproject.ui.common.showSnackbar
import murat.com.saasproject.ui.common.viewBinding

class MainAdminFragment : Fragment(R.layout.fragment_main_admin) {

    private val binding by viewBinding(FragmentMainAdminBinding::bind)
    private val viewModel: MainAdminViewModel by viewModels()
    private val adapter = MainAdminBusinessAdapter { business ->
        findNavController().navigate(
            R.id.action_mainAdmin_to_businessDetail,
            bundleOf("businessId" to business.id)
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.toolbar.setNavigationOnClickListener { AccountMenuBottomSheet.show(this) }
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter
        binding.stateLayout.attachContent(binding.recyclerView)

        binding.generateInviteButton.setOnClickListener { viewModel.createInvite() }
        binding.copyInviteButton.setOnClickListener {
            val code = viewModel.inviteCode.value
            if (code.isNullOrBlank()) {
                showSnackbar(R.string.main_admin_invite_nothing_to_copy)
            } else {
                requireContext().copyToClipboard(getString(R.string.invite_code_title), code)
                showSnackbar(R.string.main_admin_invite_copied)
            }
        }

        collectWhileStarted(viewModel.inviteCode) { code ->
            binding.inviteCodeTextView.text = code ?: getString(R.string.empty_value)
        }
        collectWhileStarted(viewModel.isCreating) { creating ->
            binding.generateInviteButton.isEnabled = !creating
        }
        collectWhileStarted(viewModel.businesses) { state ->
            binding.stateLayout.bind(
                state = state,
                emptyTitle = getString(R.string.main_admin_businesses_empty_title),
                emptyDescription = getString(R.string.main_admin_businesses_empty_message),
                emptyIcon = R.drawable.ic_business,
                onRetry = viewModel::load
            ) { list -> adapter.submitList(list) }
        }
        collectEvents(viewModel.events) { showSnackbar(requireContext().resolveErrorMessage(it)) }

        viewModel.load()
    }

    override fun onResume() {
        super.onResume()
        viewModel.load()
    }
}
